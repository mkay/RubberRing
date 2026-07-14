package de.singular.looper

import android.app.Application
import android.content.Context
import androidx.core.content.edit
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import de.singular.looper.audio.AudioDecoder
import de.singular.looper.audio.BeatDetector
import de.singular.looper.audio.DecodedAudio
import de.singular.looper.audio.Gain
import de.singular.looper.audio.LoopPlayer
import de.singular.looper.audio.Metronome
import de.singular.looper.audio.NormalizeMode
import de.singular.looper.audio.TimeStretch
import de.singular.looper.audio.WaveformData
import de.singular.looper.library.ArrangementStep
import de.singular.looper.library.LibraryRepository
import de.singular.looper.library.LibraryTrack
import de.singular.looper.library.SavedLoop
import java.io.IOException
import java.util.UUID
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import org.json.JSONObject

sealed interface LooperUiState {
    data object Empty : LooperUiState
    data class Loading(val fileName: String?) : LooperUiState
    data class Loaded(val fileName: String?, val audio: DecodedAudio) : LooperUiState
    data class Error(val message: String) : LooperUiState
}

/** Maximum number of named loops a track may hold. */
const val MAX_SAVED_LOOPS = 4

/** Maximum number of steps in a practice arrangement. */
const val MAX_ARRANGEMENT_STEPS = 16

private const val KEY_FOLLOW_PLAYHEAD = "follow_playhead"
private const val KEY_KEEP_SCREEN_ON = "keep_screen_on"
private const val KEY_SAVE_ZOOM = "save_zoom"
private const val KEY_THEME_MODE = "theme_mode"
private const val KEY_EDGE_INSET = "edge_inset"

/** How many tracks the "Recent" list in the drawer shows. */
private const val RECENTS_LIMIT = 5

/** The selected loop region as fractions (0f..1f) of the whole file. */
data class LoopRegion(val startFrac: Float, val endFrac: Float)

/** The waveform viewport: [zoom] (1f = whole file) and [offset] (left edge, as a fraction). */
data class Viewport(val zoom: Float, val offset: Float)

/** One-shot outcome of a backup/restore, surfaced to the UI as a message. */
enum class BackupResult { EXPORTED, RESTORED, FAILED }

/** How the app picks its light/dark colours: follow the OS, or force one. */
enum class ThemeMode { SYSTEM, LIGHT, DARK }

/** Parse a stored [ThemeMode] name, falling back to [ThemeMode.SYSTEM] for null/unknown values. */
private fun readThemeMode(name: String?): ThemeMode =
    ThemeMode.entries.firstOrNull { it.name == name } ?: ThemeMode.SYSTEM

/**
 * The beat grid. [downbeatFrac] anchors the grid (position of a downbeat as a fraction of
 * the file); [subdivision] is grid lines per beat (1 = quarters, 2 = eighths, 4 = sixteenths).
 * [enabled] shows the grid and turns on magnetic snapping.
 */
data class BeatGridState(
    val bpm: Float = 120f,
    val downbeatFrac: Float = 0f,
    val subdivision: Int = 1,
    val enabled: Boolean = false,
)

/**
 * Per-track count-in settings: [bars] bars of [beatsPerBar] clicks at the track's tempo, with the
 * accent on beat 1 (3 = 3/4, 4 = 4/4). Triggered per-play by a long-press on Play, not a toggle.
 */
data class CountInState(
    val beatsPerBar: Int = 4,
    val bars: Int = 1,
)

class LooperViewModel(app: Application) : AndroidViewModel(app) {

    private val _state = MutableStateFlow<LooperUiState>(LooperUiState.Empty)
    val state: StateFlow<LooperUiState> = _state.asStateFlow()

    private val _region = MutableStateFlow(LoopRegion(0f, 1f))
    val region: StateFlow<LoopRegion> = _region.asStateFlow()

    private val _isPlaying = MutableStateFlow(false)
    val isPlaying: StateFlow<Boolean> = _isPlaying.asStateFlow()

    private val _playhead = MutableStateFlow(0f)
    val playhead: StateFlow<Float> = _playhead.asStateFlow()

    private val _grid = MutableStateFlow(BeatGridState())
    val grid: StateFlow<BeatGridState> = _grid.asStateFlow()

    private val _countIn = MutableStateFlow(CountInState())
    val countIn: StateFlow<CountInState> = _countIn.asStateFlow()

    // True while the count-in clicks are sounding but the audio hasn't started yet (drives the UI).
    private val _countingIn = MutableStateFlow(false)
    val countingIn: StateFlow<Boolean> = _countingIn.asStateFlow()

    private val _detecting = MutableStateFlow(false)
    val detecting: StateFlow<Boolean> = _detecting.asStateFlow()

    // How the open track's level is normalised, and the boost that currently implies (in dB, for
    // the UI to show — 0 when off, and near 0 when peak mode has nothing to give).
    private val _normalize = MutableStateFlow(NormalizeMode.OFF)
    val normalize: StateFlow<NormalizeMode> = _normalize.asStateFlow()

    private val _normalizeGainDb = MutableStateFlow(0f)
    val normalizeGainDb: StateFlow<Float> = _normalizeGainDb.asStateFlow()

    // Playback speed (pitch preserved). Transient per session — reset to 1× on each load.
    private val _speed = MutableStateFlow(1f)
    val speed: StateFlow<Float> = _speed.asStateFlow()

    // True while a WSOLA stretch is being computed off-thread (drives a small spinner).
    private val _stretching = MutableStateFlow(false)
    val stretching: StateFlow<Boolean> = _stretching.asStateFlow()

    // When the player holds a time-stretched buffer it contains only the region [stretchStart,
    // stretchEnd] of the file (stretched to a different length), so playhead/seek must map
    // between file fractions and the buffer. At 1× the player holds the full original file and
    // no mapping is needed.
    private var stretched = false
    private var stretchStartFrac = 0f
    private var stretchEndFrac = 1f

    /** Set playback speed (pitch preserved). Rebuilds the stretched region buffer off-thread. */
    fun setSpeed(value: Float) {
        _speed.value = value.coerceIn(LoopPlayer.MIN_SPEED, LoopPlayer.MAX_SPEED)
        rebuildPlayback()
    }

    /**
     * (Re)build the playback buffer for the current speed + region. At 1× the player loops the
     * original full-file PCM; otherwise it loops a WSOLA-stretched copy of just the loop region
     * (offline, so quality doesn't fight a real-time deadline). Resumes playback if it was on.
     */
    private fun rebuildPlayback() {
        val audio = (state.value as? LooperUiState.Loaded)?.audio ?: return
        val s = _speed.value
        val r = _region.value
        val wasPlaying = _isPlaying.value
        stretchJob?.cancel()

        if (s == 1f) {
            _stretching.value = false
            swapPlayer(audio.pcm, audio.channels, audio.sampleRate, isStretched = false, r, audio, wasPlaying)
            return
        }

        _stretching.value = true
        stretchJob = viewModelScope.launch {
            val startF = audio.fractionToFrame(r.startFrac)
            val endF = audio.fractionToFrame(r.endFrac).coerceAtLeast(startF + 1)
            val buffer = withContext(Dispatchers.Default) {
                val slice = audio.pcm.copyOfRange(startF * audio.channels, endF * audio.channels)
                TimeStretch.stretch(slice, audio.channels, audio.sampleRate, s)
            }
            if (!isActive) return@launch // a newer speed/region change superseded this one
            stretchStartFrac = r.startFrac
            stretchEndFrac = r.endFrac
            swapPlayer(buffer, audio.channels, audio.sampleRate, isStretched = true, r, audio, wasPlaying)
            _stretching.value = false
        }
    }

    /** Replace the player with one over [pcm], restore the loop region, and optionally resume. */
    private fun swapPlayer(
        pcm: ShortArray,
        channels: Int,
        sampleRate: Int,
        isStretched: Boolean,
        r: LoopRegion,
        audio: DecodedAudio,
        resume: Boolean,
    ) {
        stopPlayback()
        player?.release()
        stretched = isStretched
        val p = LoopPlayer(pcm, channels, sampleRate)
        player = p
        applyGain() // the new player starts at 1×; carry the track's normalization over
        if (isStretched) {
            p.setRegion(0, p.frameCount) // the whole stretched buffer *is* the loop
        } else {
            p.setRegion(audio.fractionToFrame(r.startFrac), audio.fractionToFrame(r.endFrac))
        }
        _playhead.value = r.startFrac
        if (resume) startPlayback(withCountIn = false)
    }

    private val library = LibraryRepository(app)
    private val _library = MutableStateFlow<List<LibraryTrack>>(emptyList())
    val libraryTracks: StateFlow<List<LibraryTrack>> = _library.asStateFlow()

    /** The most recently opened tracks, newest first — powers the drawer's "Recent" list. */
    val recentTracks: StateFlow<List<LibraryTrack>> = _library
        .map { tracks ->
            tracks.filter { it.lastOpenedAt > 0 }
                .sortedByDescending { it.lastOpenedAt }
                .take(RECENTS_LIMIT)
        }
        .stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    private val _savedLoops = MutableStateFlow<List<SavedLoop>>(emptyList())
    val savedLoops: StateFlow<List<SavedLoop>> = _savedLoops.asStateFlow()

    // The open track's practice arrangement, and — while one is playing — the index of the step
    // currently sounding (null when no arrangement is running).
    private val _arrangement = MutableStateFlow<List<ArrangementStep>>(emptyList())
    val arrangement: StateFlow<List<ArrangementStep>> = _arrangement.asStateFlow()

    private val _playingStep = MutableStateFlow<Int?>(null)
    val playingStep: StateFlow<Int?> = _playingStep.asStateFlow()

    // Whether the Play button runs the arrangement (armed) vs. the single loop, and whether the
    // sequence repeats from the top. Both persist on the track.
    private val _arrangementActive = MutableStateFlow(false)
    val arrangementActive: StateFlow<Boolean> = _arrangementActive.asStateFlow()

    private val _arrangementRepeat = MutableStateFlow(false)
    val arrangementRepeat: StateFlow<Boolean> = _arrangementRepeat.asStateFlow()

    // User preferences that persist across app launches.
    private val prefs = app.getSharedPreferences("settings", Context.MODE_PRIVATE)

    private val _followPlayhead = MutableStateFlow(prefs.getBoolean(KEY_FOLLOW_PLAYHEAD, true))
    val followPlayhead: StateFlow<Boolean> = _followPlayhead.asStateFlow()

    fun toggleFollowPlayhead() {
        _followPlayhead.value = !_followPlayhead.value
        prefs.edit { putBoolean(KEY_FOLLOW_PLAYHEAD, _followPlayhead.value) }
    }

    private val _keepScreenOn = MutableStateFlow(prefs.getBoolean(KEY_KEEP_SCREEN_ON, false))
    val keepScreenOn: StateFlow<Boolean> = _keepScreenOn.asStateFlow()

    fun toggleKeepScreenOn() {
        _keepScreenOn.value = !_keepScreenOn.value
        prefs.edit { putBoolean(KEY_KEEP_SCREEN_ON, _keepScreenOn.value) }
    }

    private val _saveZoom = MutableStateFlow(prefs.getBoolean(KEY_SAVE_ZOOM, false))
    val saveZoom: StateFlow<Boolean> = _saveZoom.asStateFlow()

    fun toggleSaveZoom() {
        _saveZoom.value = !_saveZoom.value
        prefs.edit { putBoolean(KEY_SAVE_ZOOM, _saveZoom.value) }
    }

    // Whether the waveform holds back from the system gesture strips. On by default — a marker
    // parked at the very edge otherwise sits under the back gesture — but it costs waveform width,
    // so anyone happy to reach past it (or on 3-button nav, where the inset is zero anyway) can
    // turn it off.
    private val _edgeInset = MutableStateFlow(prefs.getBoolean(KEY_EDGE_INSET, true))
    val edgeInset: StateFlow<Boolean> = _edgeInset.asStateFlow()

    fun toggleEdgeInset() {
        _edgeInset.value = !_edgeInset.value
        prefs.edit { putBoolean(KEY_EDGE_INSET, _edgeInset.value) }
    }

    private val _themeMode = MutableStateFlow(readThemeMode(prefs.getString(KEY_THEME_MODE, null)))
    val themeMode: StateFlow<ThemeMode> = _themeMode.asStateFlow()

    fun setThemeMode(mode: ThemeMode) {
        _themeMode.value = mode
        prefs.edit { putString(KEY_THEME_MODE, mode.name) }
    }

    // The live waveform viewport, mirrored up from the waveform so it can be saved on exit.
    private val _viewport = MutableStateFlow(Viewport(1f, 0f))
    val viewport: StateFlow<Viewport> = _viewport.asStateFlow()

    fun setViewport(zoom: Float, offset: Float) {
        _viewport.value = Viewport(zoom, offset)
    }

    // ---- Backup / restore ----

    // One-shot result of the last backup/restore; the UI shows it, then calls [consumeBackupResult].
    private val _backupResult = MutableStateFlow<BackupResult?>(null)
    val backupResult: StateFlow<BackupResult?> = _backupResult.asStateFlow()

    fun consumeBackupResult() { _backupResult.value = null }

    /** Write a full backup (library + settings) to the user-chosen [uri]. */
    fun exportLibrary(uri: Uri) {
        viewModelScope.launch {
            val ok = runCatching {
                withContext(Dispatchers.IO) {
                    // Fold the open track's latest edits into the index so the backup is current.
                    flushLoopState()
                    val resolver = getApplication<Application>().contentResolver
                    resolver.openOutputStream(uri)?.use { library.exportTo(it, settingsJson()) }
                        ?: throw IOException("Could not open the destination file")
                }
            }.isSuccess
            _backupResult.value = if (ok) BackupResult.EXPORTED else BackupResult.FAILED
        }
    }

    /** Replace the whole library from the backup at [uri], then return to the library list. */
    fun importLibrary(uri: Uri) {
        viewModelScope.launch {
            runCatching {
                withContext(Dispatchers.IO) {
                    val resolver = getApplication<Application>().contentResolver
                    resolver.openInputStream(uri)?.use { library.importFrom(it) }
                        ?: throw IOException("Could not open the backup file")
                }
            }.onSuccess { settings ->
                applySettingsJson(settings)
                // Drop any open track without writing it back (its file may no longer exist).
                discardOpenTrack()
                // Refresh synchronously — NOT the fire-and-forget refreshLibrary() — so the list is
                // repopulated before we report success. Otherwise the restored tracks don't
                // reappear until the next app launch (the detached refresh races the lifecycle
                // resume from the file picker and its emission is missed).
                _library.value = withContext(Dispatchers.IO) { library.list() }
                _backupResult.value = BackupResult.RESTORED
            }.onFailure {
                _backupResult.value = BackupResult.FAILED
            }
        }
    }

    private fun settingsJson(): String = JSONObject()
        .put(KEY_FOLLOW_PLAYHEAD, _followPlayhead.value)
        .put(KEY_KEEP_SCREEN_ON, _keepScreenOn.value)
        .put(KEY_SAVE_ZOOM, _saveZoom.value)
        .put(KEY_EDGE_INSET, _edgeInset.value)
        .put(KEY_THEME_MODE, _themeMode.value.name)
        .toString()

    private fun applySettingsJson(json: String?) {
        val o = runCatching { JSONObject(json ?: return) }.getOrNull() ?: return
        if (o.has(KEY_FOLLOW_PLAYHEAD)) _followPlayhead.value = o.getBoolean(KEY_FOLLOW_PLAYHEAD)
        if (o.has(KEY_KEEP_SCREEN_ON)) _keepScreenOn.value = o.getBoolean(KEY_KEEP_SCREEN_ON)
        if (o.has(KEY_SAVE_ZOOM)) _saveZoom.value = o.getBoolean(KEY_SAVE_ZOOM)
        if (o.has(KEY_EDGE_INSET)) _edgeInset.value = o.getBoolean(KEY_EDGE_INSET)
        if (o.has(KEY_THEME_MODE)) _themeMode.value = readThemeMode(o.getString(KEY_THEME_MODE))
        prefs.edit {
            putBoolean(KEY_FOLLOW_PLAYHEAD, _followPlayhead.value)
            putBoolean(KEY_KEEP_SCREEN_ON, _keepScreenOn.value)
            putBoolean(KEY_SAVE_ZOOM, _saveZoom.value)
            putBoolean(KEY_EDGE_INSET, _edgeInset.value)
            putString(KEY_THEME_MODE, _themeMode.value.name)
        }
    }

    /** Tear down the open track without persisting it (used after a restore replaces the library). */
    private fun discardOpenTrack() {
        loadJob?.cancel()
        detectJob?.cancel()
        stretchJob?.cancel()
        _stretching.value = false
        stretched = false
        stopPlayback()
        player?.release()
        player = null
        currentTrack = null
        _savedLoops.value = emptyList()
        _arrangement.value = emptyList()
        _arrangementActive.value = false
        _arrangementRepeat.value = false
        _countIn.value = CountInState()
        _normalize.value = NormalizeMode.OFF
        _normalizeGainDb.value = 0f
        _playhead.value = 0f
        _state.value = LooperUiState.Empty
    }

    private val tapTimes = mutableListOf<Long>()

    private var player: LoopPlayer? = null
    private val metronome by lazy { Metronome() }
    private var countInJob: Job? = null
    private var loadJob: Job? = null
    private var tickerJob: Job? = null
    private var detectJob: Job? = null
    private var stretchJob: Job? = null
    private var arrangementJob: Job? = null

    // The library track currently open, if any. Its loop state is kept in sync with the UI.
    private var currentTrack: LibraryTrack? = null

    init {
        refreshLibrary()
        // Auto-save the loop state a moment after the user stops editing.
        viewModelScope.launch {
            combine(_region, _grid) { r, g -> r to g }
                .drop(1)
                .debounce(350)
                // Don't persist the transient region changes the arrangement makes as it advances.
                .collect { (r, g) -> if (_playingStep.value == null) saveLoopState(r, g) }
        }
        // When stretched, the region defines what gets stretched — re-run WSOLA once the user
        // settles on new markers (debounced so a drag doesn't thrash the analyser).
        viewModelScope.launch {
            _region.drop(1).debounce(350).collect { if (stretched) rebuildPlayback() }
        }
    }

    /** Minimum region size, in frames, so the two handles can't cross or touch. */
    private val minGapFrames = 2000

    /**
     * Import a freshly picked file: copy its bytes into the library, decode, open it, and
     * (only on a successful decode) record it in the library index.
     */
    // Catch-all: any failure (or coroutine cancellation) during decode must delete the orphan
    // copy before propagating, so we intentionally catch Throwable and rethrow unchanged.
    @Suppress("TooGenericExceptionCaught")
    fun importAndOpen(uri: Uri, displayName: String?) {
        startLoad(displayName)
        loadJob = viewModelScope.launch {
            var added: LibraryTrack? = null
            val result = runCatching {
                withContext(Dispatchers.IO) {
                    val (track, file) = library.copyIn(
                        getApplication<Application>().contentResolver, uri, displayName,
                    )
                    val audio = try {
                        AudioDecoder.decode(getApplication(), Uri.fromFile(file))
                    } catch (e: Throwable) {
                        file.delete() // don't leave an orphan copy that never decoded
                        throw e
                    }
                    val full = track.copy(
                        durationMs = audio.durationMs,
                        lastOpenedAt = System.currentTimeMillis(),
                    )
                    library.add(full)
                    added = full
                    audio
                }
            }
            finishLoad(result, displayName) { audio ->
                currentTrack = added
                _savedLoops.value = added?.savedLoops ?: emptyList()
                _arrangement.value = added?.arrangement ?: emptyList()
                _arrangementActive.value = false
                _arrangementRepeat.value = false
                detectBeats(audio) // a fresh import: auto-detect the tempo
            }
            refreshLibrary()
        }
    }

    /** Open a track already in the library, restoring its saved loop state. */
    fun open(track: LibraryTrack) {
        startLoad(track.name)
        // Stamp the open time so the track floats to the top of the recents list.
        val opened = track.copy(lastOpenedAt = System.currentTimeMillis())
        loadJob = viewModelScope.launch {
            val result = runCatching {
                withContext(Dispatchers.IO) {
                    AudioDecoder.decode(getApplication(), Uri.fromFile(library.fileFor(opened)))
                }
            }
            finishLoad(result, opened.name) { audio ->
                currentTrack = opened
                _savedLoops.value = opened.savedLoops
                _arrangement.value = opened.arrangement
                _arrangementActive.value = opened.arrangementActive
                _arrangementRepeat.value = opened.arrangementRepeat
                // Restore the saved viewport only when the user opted in; otherwise start zoomed out.
                _viewport.value = if (_saveZoom.value) Viewport(opened.zoom, opened.offset)
                else Viewport(1f, 0f)
                applyLoop(opened, audio) // restore markers + grid instead of re-detecting
            }
            withContext(Dispatchers.IO) { library.add(opened) }
            refreshLibrary()
        }
    }

    /** Reset all per-file state and show the loading spinner. */
    private fun startLoad(displayName: String?) {
        loadJob?.cancel()
        detectJob?.cancel()
        stretchJob?.cancel()
        _detecting.value = false
        _stretching.value = false
        stretched = false
        stopPlayback()
        player?.release()
        player = null
        currentTrack = null // so resets below don't get auto-saved to the outgoing track
        _savedLoops.value = emptyList()
        _arrangement.value = emptyList()
        _arrangementActive.value = false
        _arrangementRepeat.value = false
        _region.value = LoopRegion(0f, 1f)
        _playhead.value = 0f
        _grid.value = BeatGridState()
        _viewport.value = Viewport(1f, 0f)
        _speed.value = 1f
        _normalize.value = NormalizeMode.OFF
        _normalizeGainDb.value = 0f
        tapTimes.clear()
        _state.value = LooperUiState.Loading(displayName)
    }

    /**
     * Publish the decode result, or an error. Cancellation (a newer load started) is ignored.
     * [onLoaded] runs after the player is ready — used to either auto-detect or restore markers.
     */
    private fun finishLoad(
        result: Result<DecodedAudio>,
        displayName: String?,
        onLoaded: (DecodedAudio) -> Unit,
    ) {
        val error = result.exceptionOrNull()
        if (error is CancellationException) return
        result.fold(
            onSuccess = { audio ->
                player = LoopPlayer(audio.pcm, audio.channels, audio.sampleRate).also {
                    it.setRegion(0, it.frameCount)
                }
                _state.value = LooperUiState.Loaded(displayName, audio)
                onLoaded(audio) // may restore a saved normalize mode
                applyGain()
            },
            onFailure = { _state.value = LooperUiState.Error(it.message ?: "Unknown error") },
        )
    }

    /** Restore a track's saved region + grid onto the freshly loaded audio. */
    private fun applyLoop(track: LibraryTrack, audio: DecodedAudio) {
        _grid.value = BeatGridState(
            bpm = track.bpm,
            downbeatFrac = track.downbeatFrac,
            subdivision = 1,
            enabled = track.snap,
        )
        _countIn.value = CountInState(
            beatsPerBar = track.countInBeatsPerBar,
            bars = track.countInBars,
        )
        _normalize.value = track.normalize
        val start = track.startFrac.coerceIn(0f, 1f)
        val end = track.endFrac.coerceIn(start, 1f)
        _region.value = LoopRegion(start, end)
        player?.setRegion(audio.fractionToFrame(start), audio.fractionToFrame(end))
        _playhead.value = start
    }

    // ---- Count-in ----

    /** Set the count-in's meter and length (the Options popup). */
    fun setCountIn(beatsPerBar: Int, bars: Int) {
        _countIn.value = CountInState(
            beatsPerBar = beatsPerBar.coerceIn(2, 8),
            bars = bars.coerceIn(1, 2),
        )
        val track = currentTrack ?: return
        val ci = _countIn.value
        currentTrack = track.copy(
            countInBeatsPerBar = ci.beatsPerBar,
            countInBars = ci.bars,
        ).also { persistTrack(it) }
    }

    // ---- Normalization ----

    /**
     * Set how the open track's playback level is normalised (the Options → Audio tab) and persist
     * it. Takes effect immediately, mid-playback: the imported file is never touched, the gain is
     * applied to the PCM on its way to the speaker.
     */
    fun setNormalize(mode: NormalizeMode) {
        _normalize.value = mode
        applyGain()
        val track = currentTrack ?: return
        currentTrack = track.copy(normalize = mode).also { persistTrack(it) }
    }

    /** Push the current mode's gain into the player; the feeder picks it up on its next chunk. */
    private fun applyGain() {
        val audio = (state.value as? LooperUiState.Loaded)?.audio ?: return
        val gain = Gain.linearFor(_normalize.value, audio.peak, audio.rms)
        _normalizeGainDb.value = Gain.linearToDb(gain)
        player?.setGain(gain, Gain.needsSoftClip(gain, audio.peak))
    }

    // ---- Named loops ----

    /** Save the current region + grid as a named loop (capped at [MAX_SAVED_LOOPS]). */
    fun saveCurrentLoop(label: String) {
        val track = currentTrack ?: return
        if (track.savedLoops.size >= MAX_SAVED_LOOPS) return
        val r = _region.value
        val g = _grid.value
        val name = label.trim().ifBlank { "Loop ${track.savedLoops.size + 1}" }
        val loop = SavedLoop(
            id = UUID.randomUUID().toString(),
            label = name,
            startFrac = r.startFrac,
            endFrac = r.endFrac,
            bpm = g.bpm,
            downbeatFrac = g.downbeatFrac,
            snap = g.enabled,
        )
        updateLoops(track.savedLoops + loop)
    }

    /** Recall a named loop: restore its region + grid onto the current audio. */
    fun applySavedLoop(loop: SavedLoop) {
        val audio = (state.value as? LooperUiState.Loaded)?.audio ?: return
        _grid.value = BeatGridState(
            bpm = loop.bpm,
            downbeatFrac = loop.downbeatFrac,
            subdivision = 1,
            enabled = loop.snap,
        )
        val start = loop.startFrac.coerceIn(0f, 1f)
        val end = loop.endFrac.coerceIn(start, 1f)
        _region.value = LoopRegion(start, end)
        player?.setRegion(audio.fractionToFrame(start), audio.fractionToFrame(end))
        _playhead.value = start
    }

    fun renameLoop(id: String, label: String) {
        val track = currentTrack ?: return
        val name = label.trim().ifBlank { return }
        updateLoops(track.savedLoops.map { if (it.id == id) it.copy(label = name) else it })
    }

    fun deleteLoop(id: String) {
        val track = currentTrack ?: return
        // Also drop any arrangement steps that pointed at this loop, so the sequence stays valid.
        val sorted = track.savedLoops.filterNot { it.id == id }.sortedBy { it.startFrac }
        val arr = track.arrangement.filterNot { it.loopId == id }
        val active = if (arr.isEmpty()) false else _arrangementActive.value
        val updated = track.copy(savedLoops = sorted, arrangement = arr, arrangementActive = active)
        currentTrack = updated
        _savedLoops.value = sorted
        _arrangement.value = arr
        _arrangementActive.value = active
        persistTrack(updated)
    }

    /** Replace the open track's loop list (sorted in song order) and persist. */
    private fun updateLoops(loops: List<SavedLoop>) {
        val track = currentTrack ?: return
        val sorted = loops.sortedBy { it.startFrac }
        val updated = track.copy(savedLoops = sorted)
        currentTrack = updated
        _savedLoops.value = sorted
        persistTrack(updated)
    }

    /** Upsert [track] into the library index off-thread, then refresh the list. */
    private fun persistTrack(track: LibraryTrack) {
        viewModelScope.launch {
            withContext(Dispatchers.IO) { library.add(track) }
            refreshLibrary()
        }
    }

    // ---- Practice arrangement ----

    /** Append a step that plays the saved loop [loopId] (capped at [MAX_ARRANGEMENT_STEPS]). */
    fun addArrangementStep(loopId: String) {
        if (_arrangement.value.size >= MAX_ARRANGEMENT_STEPS) return
        val step = ArrangementStep(UUID.randomUUID().toString(), loopId, 1)
        updateArrangement(_arrangement.value + step)
    }

    fun removeArrangementStep(stepId: String) =
        updateArrangement(_arrangement.value.filterNot { it.stepId == stepId })

    fun setArrangementRepeat(stepId: String, count: Int) =
        updateArrangement(_arrangement.value.map {
            if (it.stepId == stepId) it.copy(repeatCount = count.coerceIn(1, 99)) else it
        })

    /** Move a step up ([delta] = -1) or down ([delta] = +1) in the sequence. */
    fun moveArrangementStep(stepId: String, delta: Int) {
        val list = _arrangement.value.toMutableList()
        val i = list.indexOfFirst { it.stepId == stepId }
        if (i < 0) return
        val j = (i + delta).coerceIn(0, list.lastIndex)
        if (i == j) return
        list.add(j, list.removeAt(i))
        updateArrangement(list)
    }

    /** Arm/disarm the arrangement: when armed, the Play button runs it instead of the single loop. */
    fun setArrangementActive(active: Boolean) {
        val track = currentTrack ?: return
        _arrangementActive.value = active
        currentTrack = track.copy(arrangementActive = active).also { persistTrack(it) }
    }

    fun setArrangementRepeatWhole(repeat: Boolean) {
        val track = currentTrack ?: return
        _arrangementRepeat.value = repeat
        currentTrack = track.copy(arrangementRepeat = repeat).also { persistTrack(it) }
    }

    private fun updateArrangement(steps: List<ArrangementStep>) {
        val track = currentTrack ?: return
        // An empty sequence can't be armed, so disarm it to keep the Play button honest.
        val active = if (steps.isEmpty()) false else _arrangementActive.value
        _arrangement.value = steps
        _arrangementActive.value = active
        currentTrack = track.copy(arrangement = steps, arrangementActive = active)
            .also { persistTrack(it) }
    }

    /** Persist the current loop state onto the open track (debounced from the region/grid flows). */
    private suspend fun saveLoopState(r: LoopRegion, g: BeatGridState) {
        val track = currentTrack ?: return
        val updated = track.copy(
            startFrac = r.startFrac,
            endFrac = r.endFrac,
            bpm = g.bpm,
            downbeatFrac = g.downbeatFrac,
            snap = g.enabled,
        )
        currentTrack = updated
        withContext(Dispatchers.IO) { library.add(updated) }
    }

    private fun refreshLibrary() {
        viewModelScope.launch { _library.value = library.list() }
    }

    /**
     * Analyse the loaded audio for tempo and a downbeat, then populate the grid and turn on
     * snapping. Runs off the main thread; safe to re-trigger (e.g. an "Auto" button) — the
     * newest run wins. A failed/inconclusive analysis leaves the current grid untouched.
     */
    fun detectBeats(audio: DecodedAudio? = null) {
        val target = audio ?: (state.value as? LooperUiState.Loaded)?.audio ?: return
        detectJob?.cancel()
        _detecting.value = true
        detectJob = viewModelScope.launch {
            val estimate = withContext(Dispatchers.Default) { BeatDetector.detect(target) }
            if (estimate != null) {
                _grid.value = _grid.value.copy(
                    bpm = estimate.bpm,
                    downbeatFrac = estimate.downbeatFrac,
                    enabled = true,
                )
                resnapRegion()
            }
            _detecting.value = false
        }
    }

    fun setStart(frac: Float) = updateRegion(newStart = frac)
    fun setEnd(frac: Float) = updateRegion(newEnd = frac)

    private fun updateRegion(newStart: Float? = null, newEnd: Float? = null) {
        val audio = (state.value as? LooperUiState.Loaded)?.audio ?: return
        val minGap = if (audio.frameCount > 0) minGapFrames.toFloat() / audio.frameCount else 0.01f
        var start = newStart?.let { snapFrac(it, audio.durationMs) } ?: _region.value.startFrac
        var end = newEnd?.let { snapFrac(it, audio.durationMs) } ?: _region.value.endFrac
        if (newStart != null) start = start.coerceIn(0f, end - minGap)
        if (newEnd != null) end = end.coerceIn(start + minGap, 1f)
        _region.value = LoopRegion(start, end)
        // At 1× the player loops the full file, so just move the bounds. When stretched the buffer
        // holds only the old region, so a region change is picked up by the debounced rebuild below.
        if (!stretched) player?.setRegion(audio.fractionToFrame(start), audio.fractionToFrame(end))
    }

    /** Grid line spacing as a fraction of the file, or 0 if no usable grid. */
    private fun gridIntervalFrac(durationMs: Long): Float {
        val g = _grid.value
        if (g.bpm <= 0f || g.subdivision <= 0 || durationMs <= 0) return 0f
        val intervalMs = 60_000.0 / g.bpm / g.subdivision
        return (intervalMs / durationMs).toFloat()
    }

    /** Snap a fraction to the nearest grid line when the grid is enabled. */
    private fun snapFrac(frac: Float, durationMs: Long): Float {
        val g = _grid.value
        if (!g.enabled) return frac
        val interval = gridIntervalFrac(durationMs)
        if (interval <= 0f) return frac
        val k = Math.round((frac - g.downbeatFrac) / interval)
        return (g.downbeatFrac + k * interval).coerceIn(0f, 1f)
    }

    // ---- Beat grid controls ----

    fun toggleSnap() {
        _grid.value = _grid.value.copy(enabled = !_grid.value.enabled)
        resnapRegion()
    }

    fun nudgeBpm(delta: Float) {
        _grid.value = _grid.value.copy(
            bpm = (_grid.value.bpm + delta).coerceIn(20f, 400f),
            enabled = true,
        )
        resnapRegion()
    }

    fun setSubdivision(subdivision: Int) {
        _grid.value = _grid.value.copy(subdivision = subdivision.coerceAtLeast(1), enabled = true)
        resnapRegion()
    }

    /** Halve or double the tempo — the one-tap fix for an octave error in auto-detect. */
    fun halveTempo() = scaleTempo(0.5f)
    fun doubleTempo() = scaleTempo(2f)

    private fun scaleTempo(factor: Float) {
        _grid.value = _grid.value.copy(
            bpm = (_grid.value.bpm * factor).coerceIn(20f, 400f),
            enabled = true,
        )
        resnapRegion()
    }

    /** Tap this in time with the music to estimate BPM from the tap intervals. */
    fun tapTempo() {
        val now = System.currentTimeMillis()
        if (tapTimes.isNotEmpty() && now - tapTimes.last() > 2000) tapTimes.clear()
        tapTimes.add(now)
        if (tapTimes.size > 8) tapTimes.subList(0, tapTimes.size - 8).clear()
        if (tapTimes.size >= 2) {
            val intervals = tapTimes.zipWithNext { a, b -> (b - a).toDouble() }
            val avg = intervals.average()
            if (avg > 0) {
                val bpm = (60_000.0 / avg).toFloat().coerceIn(20f, 400f)
                _grid.value = _grid.value.copy(bpm = bpm, enabled = true)
            }
        }
        resnapRegion()
    }

    /** Re-apply snapping to the current markers after a grid change. */
    private fun resnapRegion() {
        val r = _region.value
        updateRegion(newStart = r.startFrac, newEnd = r.endFrac)
    }

    fun togglePlay(withCountIn: Boolean = false) {
        if (player == null) return
        // Use the UI's playing state, not the LoopPlayer's: during a count-in we're "playing" but
        // the audio hasn't started yet, and a tap must still stop (cancelling the count-in).
        if (_isPlaying.value) stopPlayback() else startPlayback(withCountIn)
    }

    /** Move the playhead to a fraction of the file (double-tap seek). */
    fun seek(frac: Float) {
        val audio = (state.value as? LooperUiState.Loaded)?.audio ?: return
        val p = player ?: return
        if (stretched) {
            // Map the file fraction into the region-only stretched buffer (clamped to the region).
            val f = frac.coerceIn(stretchStartFrac, stretchEndFrac)
            val span = (stretchEndFrac - stretchStartFrac).coerceAtLeast(1e-6f)
            p.seekTo((((f - stretchStartFrac) / span) * p.frameCount).toInt())
            _playhead.value = f
        } else {
            p.seekTo(audio.fractionToFrame(frac))
            _playhead.value = frac.coerceIn(0f, 1f)
        }
    }

    private fun startPlayback(withCountIn: Boolean) {
        val p = player ?: return
        // A plain play cancels any running arrangement and loops the single current region.
        arrangementJob?.cancel()
        _playingStep.value = null
        // Playback always begins at the start marker.
        p.rewind()
        _playhead.value = if (stretched) stretchStartFrac else _region.value.startFrac
        _isPlaying.value = true
        if (withCountIn) {
            // Sound the count-in first, then start the audio on the following downbeat. A Stop
            // during the count cancels this job, so p.play() is never reached.
            countInJob?.cancel()
            countInJob = viewModelScope.launch {
                playCountIn()
                p.play()
                launchTicker(p)
            }
        } else {
            p.play()
            launchTicker(p)
        }
    }

    /**
     * Sound the count-in and suspend until it finishes. Cancelling the calling coroutine (e.g. via
     * Stop) aborts the clicks and propagates, so the caller won't start audio. Counts in at the
     * tempo the user will actually hear (track BPM × current speed).
     */
    private suspend fun playCountIn() {
        val ci = _countIn.value
        _countingIn.value = true
        try {
            metronome.countIn(_grid.value.bpm * _speed.value, ci.beatsPerBar, ci.bars)
        } finally {
            _countingIn.value = false
        }
    }

    /** Drive [_playhead] from the player position until it stops. Used by both play modes. */
    private fun launchTicker(p: LoopPlayer) {
        tickerJob?.cancel()
        tickerJob = viewModelScope.launch {
            val frames = p.frameCount.coerceAtLeast(1)
            val span = stretchEndFrac - stretchStartFrac
            while (isActive && p.isPlaying) {
                val raw = p.positionFrame.toFloat() / frames
                // In stretched mode the buffer spans only the region, so map back to a file fraction.
                _playhead.value = if (stretched) stretchStartFrac + raw * span else raw
                delay(33)
            }
        }
    }

    private fun stopPlayback() {
        countInJob?.cancel()
        countInJob = null
        metronome.stop()
        _countingIn.value = false
        arrangementJob?.cancel()
        arrangementJob = null
        _playingStep.value = null
        tickerJob?.cancel()
        tickerJob = null
        player?.pause()
        _isPlaying.value = false
    }

    /**
     * Play the saved-loop arrangement: each step's region looped its repeat count, in order, then
     * stop (or restart if [repeatWhole]). Steps whose loop was deleted are skipped. Runs at 1×
     * (any active time-stretch is reset first), so playback stays on the simple full-file path.
     */
    fun playArrangement(withCountIn: Boolean = false) {
        val audio = (state.value as? LooperUiState.Loaded)?.audio ?: return
        val repeatWhole = _arrangementRepeat.value
        val byId = _savedLoops.value.associateBy { it.id }
        val steps = _arrangement.value.mapNotNull { st ->
            byId[st.loopId]?.let { it to st.repeatCount.coerceAtLeast(1) }
        }
        if (steps.isEmpty()) return

        if (_speed.value != 1f) setSpeed(1f) // rebuilds the full-file 1× player (stops playback)
        val p = player ?: return
        stopPlayback()

        _isPlaying.value = true
        arrangementJob = viewModelScope.launch {
            // Count in once, before the first loop only — not before every step.
            if (withCountIn) playCountIn()
            var index = 0
            while (isActive) {
                val (loop, count) = steps[index]
                _playingStep.value = index
                val startF = audio.fractionToFrame(loop.startFrac)
                val endF = audio.fractionToFrame(loop.endFrac).coerceAtLeast(startF + 1)
                _region.value = LoopRegion(loop.startFrac, loop.endFrac)
                p.setRegion(startF, endF)
                p.seekTo(startF)
                if (!p.isPlaying) { p.play(); launchTicker(p) }
                // Wait until the seek lands inside the new region, then count its full passes.
                while (isActive && (p.positionFrame < startF || p.positionFrame >= endF)) delay(10)
                val base = p.completedLoops
                while (isActive && p.completedLoops - base < count) delay(20)
                if (!isActive) break
                index++
                if (index >= steps.size) {
                    if (repeatWhole) index = 0 else break
                }
            }
            if (isActive) { // reached the natural end (not cancelled by Stop)
                _playingStep.value = null
                tickerJob?.cancel()
                player?.pause()
                _isPlaying.value = false
            }
        }
    }

    /** Synchronously persist the open track's current loop state (final save on close/clear). */
    private fun flushLoopState() {
        val track = currentTrack ?: return
        val r = _region.value
        val g = _grid.value
        // Save the viewport only when the option is on; otherwise clear it back to the default
        // so the next open starts zoomed out.
        val vp = if (_saveZoom.value) _viewport.value else Viewport(1f, 0f)
        runBlocking {
            library.add(
                track.copy(
                    startFrac = r.startFrac,
                    endFrac = r.endFrac,
                    bpm = g.bpm,
                    downbeatFrac = g.downbeatFrac,
                    snap = g.enabled,
                    zoom = vp.zoom,
                    offset = vp.offset,
                ),
            )
        }
    }

    /** Close the open track and return to the library list. */
    fun closeTrack() {
        flushLoopState()
        loadJob?.cancel()
        detectJob?.cancel()
        stretchJob?.cancel()
        _stretching.value = false
        stretched = false
        stopPlayback()
        player?.release()
        player = null
        currentTrack = null
        _savedLoops.value = emptyList()
        _arrangement.value = emptyList()
        _arrangementActive.value = false
        _arrangementRepeat.value = false
        _playhead.value = 0f
        _state.value = LooperUiState.Empty
        refreshLibrary()
    }

    /** Delete a track from the library (removes its file + saved loops). */
    fun deleteTrack(track: LibraryTrack) = deleteTracks(listOf(track))

    /** Delete several tracks at once — the library's selection mode. Irreversible, as with one. */
    fun deleteTracks(tracks: List<LibraryTrack>) {
        if (tracks.any { it.id == currentTrack?.id }) currentTrack = null
        viewModelScope.launch {
            withContext(Dispatchers.IO) { library.removeAll(tracks) }
            refreshLibrary()
        }
    }

    /** Set (or clear, when blank) a track's custom display title, leaving its file untouched. */
    fun renameTrack(track: LibraryTrack, title: String) {
        val clean = title.trim().takeIf { it.isNotBlank() }
        viewModelScope.launch {
            withContext(Dispatchers.IO) { library.add(track.copy(title = clean)) }
            refreshLibrary()
        }
    }

    override fun onCleared() {
        tickerJob?.cancel()
        detectJob?.cancel()
        stretchJob?.cancel()
        arrangementJob?.cancel()
        countInJob?.cancel()
        metronome.stop()
        // Best-effort final save so a quit within the debounce window doesn't lose the last edit.
        flushLoopState()
        player?.release()
        player = null
    }
}
