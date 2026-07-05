package de.singular.looper

import android.app.Application
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import de.singular.looper.audio.AudioDecoder
import de.singular.looper.audio.BeatDetector
import de.singular.looper.audio.DecodedAudio
import de.singular.looper.audio.LoopPlayer
import de.singular.looper.audio.WaveformData
import de.singular.looper.library.LibraryRepository
import de.singular.looper.library.LibraryTrack
import de.singular.looper.library.SavedLoop
import java.util.UUID
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext

sealed interface LooperUiState {
    data object Empty : LooperUiState
    data class Loading(val fileName: String?) : LooperUiState
    data class Loaded(val fileName: String?, val audio: DecodedAudio) : LooperUiState
    data class Error(val message: String) : LooperUiState
}

/** Maximum number of named loops a track may hold. */
const val MAX_SAVED_LOOPS = 4

/** The selected loop region as fractions (0f..1f) of the whole file. */
data class LoopRegion(val startFrac: Float, val endFrac: Float)

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

    private val _detecting = MutableStateFlow(false)
    val detecting: StateFlow<Boolean> = _detecting.asStateFlow()

    private val library = LibraryRepository(app)
    private val _library = MutableStateFlow<List<LibraryTrack>>(emptyList())
    val libraryTracks: StateFlow<List<LibraryTrack>> = _library.asStateFlow()

    private val _savedLoops = MutableStateFlow<List<SavedLoop>>(emptyList())
    val savedLoops: StateFlow<List<SavedLoop>> = _savedLoops.asStateFlow()

    private val _followPlayhead = MutableStateFlow(true)
    val followPlayhead: StateFlow<Boolean> = _followPlayhead.asStateFlow()

    fun toggleFollowPlayhead() { _followPlayhead.value = !_followPlayhead.value }

    private val tapTimes = mutableListOf<Long>()

    private var player: LoopPlayer? = null
    private var loadJob: Job? = null
    private var tickerJob: Job? = null
    private var detectJob: Job? = null

    // The library track currently open, if any. Its loop state is kept in sync with the UI.
    private var currentTrack: LibraryTrack? = null

    init {
        refreshLibrary()
        // Auto-save the loop state a moment after the user stops editing.
        viewModelScope.launch {
            combine(_region, _grid) { r, g -> r to g }
                .drop(1)
                .debounce(350)
                .collect { (r, g) -> saveLoopState(r, g) }
        }
    }

    /** Minimum region size, in frames, so the two handles can't cross or touch. */
    private val minGapFrames = 2000

    /**
     * Import a freshly picked file: copy its bytes into the library, decode, open it, and
     * (only on a successful decode) record it in the library index.
     */
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
                    val full = track.copy(durationMs = audio.durationMs)
                    library.add(full)
                    added = full
                    audio
                }
            }
            finishLoad(result, displayName) { audio ->
                currentTrack = added
                _savedLoops.value = added?.savedLoops ?: emptyList()
                detectBeats(audio) // a fresh import: auto-detect the tempo
            }
            refreshLibrary()
        }
    }

    /** Open a track already in the library, restoring its saved loop state. */
    fun open(track: LibraryTrack) {
        startLoad(track.displayName)
        loadJob = viewModelScope.launch {
            val result = runCatching {
                withContext(Dispatchers.IO) {
                    AudioDecoder.decode(getApplication(), Uri.fromFile(library.fileFor(track)))
                }
            }
            finishLoad(result, track.displayName) { audio ->
                currentTrack = track
                _savedLoops.value = track.savedLoops
                applyLoop(track, audio) // restore markers + grid instead of re-detecting
            }
        }
    }

    /** Reset all per-file state and show the loading spinner. */
    private fun startLoad(displayName: String?) {
        loadJob?.cancel()
        detectJob?.cancel()
        _detecting.value = false
        stopPlayback()
        player?.release()
        player = null
        currentTrack = null // so resets below don't get auto-saved to the outgoing track
        _savedLoops.value = emptyList()
        _region.value = LoopRegion(0f, 1f)
        _playhead.value = 0f
        _grid.value = BeatGridState()
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
                onLoaded(audio)
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
        val start = track.startFrac.coerceIn(0f, 1f)
        val end = track.endFrac.coerceIn(start, 1f)
        _region.value = LoopRegion(start, end)
        player?.setRegion(audio.fractionToFrame(start), audio.fractionToFrame(end))
        _playhead.value = start
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
        updateLoops(track.savedLoops.filterNot { it.id == id })
    }

    /** Replace the open track's loop list (sorted in song order) and persist. */
    private fun updateLoops(loops: List<SavedLoop>) {
        val track = currentTrack ?: return
        val sorted = loops.sortedBy { it.startFrac }
        val updated = track.copy(savedLoops = sorted)
        currentTrack = updated
        _savedLoops.value = sorted
        viewModelScope.launch {
            withContext(Dispatchers.IO) { library.add(updated) }
            refreshLibrary()
        }
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
        player?.setRegion(audio.fractionToFrame(start), audio.fractionToFrame(end))
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

    fun setDownbeatToStart() {
        _grid.value = _grid.value.copy(downbeatFrac = _region.value.startFrac, enabled = true)
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

    fun togglePlay() {
        val p = player ?: return
        if (p.isPlaying) stopPlayback() else startPlayback()
    }

    /** Move the playhead to a fraction of the file (double-tap seek). */
    fun seek(frac: Float) {
        val audio = (state.value as? LooperUiState.Loaded)?.audio ?: return
        player?.seekTo(audio.fractionToFrame(frac))
        _playhead.value = frac.coerceIn(0f, 1f)
    }

    private fun startPlayback() {
        val p = player ?: return
        // Playback always begins at the start marker.
        p.rewind()
        _playhead.value = _region.value.startFrac
        p.play()
        _isPlaying.value = true
        tickerJob?.cancel()
        tickerJob = viewModelScope.launch {
            val frames = p.frameCount.coerceAtLeast(1)
            while (isActive && p.isPlaying) {
                _playhead.value = p.positionFrame.toFloat() / frames
                delay(33)
            }
        }
    }

    private fun stopPlayback() {
        tickerJob?.cancel()
        tickerJob = null
        player?.pause()
        _isPlaying.value = false
    }

    /** Synchronously persist the open track's current loop state (final save on close/clear). */
    private fun flushLoopState() {
        val track = currentTrack ?: return
        val r = _region.value
        val g = _grid.value
        runBlocking {
            library.add(
                track.copy(
                    startFrac = r.startFrac,
                    endFrac = r.endFrac,
                    bpm = g.bpm,
                    downbeatFrac = g.downbeatFrac,
                    snap = g.enabled,
                ),
            )
        }
    }

    /** Close the open track and return to the library list. */
    fun closeTrack() {
        flushLoopState()
        loadJob?.cancel()
        detectJob?.cancel()
        stopPlayback()
        player?.release()
        player = null
        currentTrack = null
        _savedLoops.value = emptyList()
        _playhead.value = 0f
        _state.value = LooperUiState.Empty
        refreshLibrary()
    }

    /** Delete a track from the library (removes its file + saved loops). */
    fun deleteTrack(track: LibraryTrack) {
        if (currentTrack?.id == track.id) currentTrack = null
        viewModelScope.launch {
            withContext(Dispatchers.IO) { library.remove(track) }
            refreshLibrary()
        }
    }

    override fun onCleared() {
        tickerJob?.cancel()
        detectJob?.cancel()
        // Best-effort final save so a quit within the debounce window doesn't lose the last edit.
        flushLoopState()
        player?.release()
        player = null
    }
}
