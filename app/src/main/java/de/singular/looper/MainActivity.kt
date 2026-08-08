// SPDX-License-Identifier: GPL-3.0-only

package de.singular.looper

import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.OpenableColumns
import android.widget.Toast
import androidx.annotation.StringRes
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.app.AppCompatDelegate
import androidx.core.os.LocaleListCompat
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.gestures.scrollBy
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.calculateEndPadding
import androidx.compose.foundation.layout.calculateStartPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemGestures
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.GraphicEq
import androidx.compose.material.icons.filled.RadioButtonUnchecked
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material.icons.filled.Paid
import androidx.compose.material.icons.filled.HelpOutline
import androidx.compose.material.icons.filled.Language
import androidx.compose.material.icons.filled.LibraryMusic
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.MyLocation
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Restore
import androidx.compose.material.icons.filled.Save
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material.icons.filled.ZoomIn
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DrawerValue
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.MultiChoiceSegmentedButtonRow
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.ModalDrawerSheet
import androidx.compose.material3.ModalNavigationDrawer
import androidx.compose.material3.NavigationDrawerItem
import androidx.compose.material3.NavigationDrawerItemDefaults
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.PrimaryTabRow
import androidx.compose.material3.Scaffold
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.material3.rememberDrawerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.snapshotFlow
import androidx.compose.runtime.withFrameNanos
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onPlaced
import androidx.compose.ui.layout.positionInParent
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.res.vectorResource
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringArrayResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex
import androidx.compose.foundation.text.TextAutoSize
import java.util.Locale
import kotlin.math.abs
import kotlin.math.roundToInt
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.first
import androidx.browser.customtabs.CustomTabColorSchemeParams
import androidx.browser.customtabs.CustomTabsIntent
import androidx.core.content.IntentCompat
import androidx.core.view.WindowCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import de.singular.looper.audio.DecodedAudio
import de.singular.looper.audio.Gain
import de.singular.looper.audio.LoopPlayer
import de.singular.looper.audio.Chord
import de.singular.looper.audio.ClickPitch
import de.singular.looper.audio.NormalizeMode
import de.singular.looper.audio.NoteNaming
import de.singular.looper.audio.Quality
import de.singular.looper.audio.rootNames
import de.singular.looper.library.LibraryTrack
import de.singular.looper.library.ArrangementStep
import de.singular.looper.library.SavedLoop
import de.singular.looper.ui.LoopWaveform
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

// Brand colour used for buttons, chips, and the marker accents.
private val BrandPrimary = Color(0xFFA62120)

private val LooperDarkColors = darkColorScheme(
    primary = BrandPrimary,
    onPrimary = Color(0xFFFFFFFF),
    secondaryContainer = BrandPrimary,
    onSecondaryContainer = Color(0xFFFFFFFF),
)

private val LooperLightColors = lightColorScheme(
    primary = BrandPrimary,
    onPrimary = Color(0xFFFFFFFF),
    secondaryContainer = BrandPrimary,
    onSecondaryContainer = Color(0xFFFFFFFF),
)

// Controls use a gentle 5px corner rather than the fully-rounded Material default.
private val ControlShape = RoundedCornerShape(5.dp)

/** Segments in the beat-grid group: Snap, Auto, Tap. */
private const val GRID_SEGMENTS = 3

// How long the "long-press a marker" hint stays up on a freshly opened track, and how long it
// takes to fade. Long enough to read once, short enough to stay out of the way.
private const val HINT_VISIBLE_MS = 2250L
private const val HINT_FADE_MS = 400

// The hint sits on top of the waveform it's describing, so it stays see-through even at rest —
// it's a note laid over the audio, not a panel covering it.
private const val HINT_OPACITY = 0.5f

// How much of the system gesture inset the waveform keeps clear of. A full inset guarantees markers
// never overlap the back-gesture strip but eats real waveform width, so we take half and let the
// waveform's systemGestureExclusion() cover the remaining sliver.
private const val EDGE_INSET_FRACTION = 0.5f

/** Dim factor for controls locked out while an arrangement is enabled. */
private const val LOCKED_ALPHA = 0.4f

/** How far the loop-chip row fades out at an edge it can still be scrolled towards. */
private val CHIP_FADE = 20.dp

/** Gap between loop chips. Reordering needs it to know how far a chip must travel to pass one. */
private val CHIP_SPACING = 8.dp

/**
 * Movement after a long-press that counts as dragging a chip rather than opening its menu. Also
 * the dead zone the chip holds still through, so it is generous: a finger settling on a chip
 * travels further than a mouse would.
 */
private val DRAG_SLOP = 12.dp

/**
 * How close to the row's edge a dragging finger starts pulling the row along, and how fast it
 * scrolls at most (pixels per frame, reached at the very edge and ramped down across the zone).
 * Keyed to the finger rather than to the dragged chip: a chip that scrolls into view would stop
 * its own scrolling, which is what turns reaching a far position into a wiggling match.
 */
private val AUTOSCROLL_ZONE = 56.dp
private const val AUTOSCROLL_MAX = 26f

class MainActivity : AppCompatActivity() {
    /**
     * A track another app has handed us, waiting to be imported.
     *
     * Kept on the activity rather than read out of `intent` inside the composition, because the
     * intent stays on the activity for its whole life while this has to fire exactly once: it is
     * cleared the moment the import starts, so nothing re-imports when the composition restarts.
     */
    private var handedOver by mutableStateOf<Uri?>(null)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // Only on a fresh start. A recreation — a theme flip does it, so does coming back after
        // the process was killed — re-delivers the same intent, and acting on it again would put
        // a second copy of the same take in the library.
        if (savedInstanceState == null) handedOver = audioHandedOver(intent)
        enableEdgeToEdge()
        setContent {
            val viewModel: LooperViewModel = viewModel()
            // Read before the effect below clears it: whether this launch came in through a share
            // decides whether the release notes are allowed to interrupt it.
            val handedOverLaunch = remember { handedOver != null }
            LaunchedEffect(handedOver) {
                val uri = handedOver ?: return@LaunchedEffect
                handedOver = null
                viewModel.importAndOpen(uri, queryDisplayName(this@MainActivity, uri))
            }
            val themeMode by viewModel.themeMode.collectAsStateWithLifecycle()
            val dark = when (themeMode) {
                ThemeMode.SYSTEM -> isSystemInDarkTheme()
                ThemeMode.LIGHT -> false
                ThemeMode.DARK -> true
            }
            // Keep the system bar icons legible against whichever theme is in effect (the
            // enableEdgeToEdge default only tracks the OS setting, not our in-app override).
            val view = LocalView.current
            SideEffect {
                WindowCompat.getInsetsController(window, view).apply {
                    isAppearanceLightStatusBars = !dark
                    isAppearanceLightNavigationBars = !dark
                }
            }
            MaterialTheme(colorScheme = if (dark) LooperDarkColors else LooperLightColors) {
                Surface(modifier = Modifier.fillMaxSize()) {
                    LooperScreen(viewModel, handedOverLaunch)
                }
            }
        }
    }

    /** A second share while we are already running arrives here rather than through onCreate. */
    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        audioHandedOver(intent)?.let { handedOver = it }
    }
}

/** The audio a share or an "open with" carries, or null if this intent is neither. */
private fun audioHandedOver(intent: Intent): Uri? = when (intent.action) {
    Intent.ACTION_SEND -> IntentCompat.getParcelableExtra(intent, Intent.EXTRA_STREAM, Uri::class.java)
    Intent.ACTION_VIEW -> intent.data
    else -> null
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LooperScreen(viewModel: LooperViewModel = viewModel(), handedOverLaunch: Boolean = false) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val following by viewModel.followPlayhead.collectAsStateWithLifecycle()
    val keepScreenOn by viewModel.keepScreenOn.collectAsStateWithLifecycle()
    val saveZoom by viewModel.saveZoom.collectAsStateWithLifecycle()
    val edgeInset by viewModel.edgeInset.collectAsStateWithLifecycle()
    val themeMode by viewModel.themeMode.collectAsStateWithLifecycle()
    val noteNaming by viewModel.noteNaming.collectAsStateWithLifecycle()
    val library by viewModel.libraryTracks.collectAsStateWithLifecycle()
    val recents by viewModel.recentTracks.collectAsStateWithLifecycle()
    var showHelp by remember { mutableStateOf(false) }
    var showSettings by remember { mutableStateOf(false) }
    var showSupport by remember { mutableStateOf(false) }
    var showKeepAwakeInfo by remember { mutableStateOf(false) }
    // The library's selection mode: the ids of the picked tracks (empty = not selecting). Held here
    // rather than in LibraryContent because selecting takes over the app bar, which lives here.
    var selection by remember { mutableStateOf<Set<String>>(emptySet()) }
    var pendingDelete by remember { mutableStateOf<List<LibraryTrack>>(emptyList()) }
    val context = androidx.compose.ui.platform.LocalContext.current
    val drawerState = rememberDrawerState(DrawerValue.Closed)
    val scope = rememberCoroutineScope()

    // Hold the screen awake while the option is on; always release it when leaving the composition.
    val view = androidx.compose.ui.platform.LocalView.current
    DisposableEffect(keepScreenOn) {
        view.keepScreenOn = keepScreenOn
        onDispose { view.keepScreenOn = false }
    }

    val picker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument(),
    ) { uri ->
        // We copy the file into our own storage on import, so a one-time read grant is enough —
        // no persistable URI permission needed.
        if (uri != null) viewModel.importAndOpen(uri, queryDisplayName(context, uri))
    }
    val openPicker = { picker.launch(arrayOf("audio/*")) }

    // Backup: write a zip wherever the user points; restore: read one back (replacing everything).
    val exportLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("application/zip"),
    ) { uri -> if (uri != null) viewModel.exportLibrary(uri) }
    val importLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument(),
    ) { uri -> if (uri != null) viewModel.importLibrary(uri) }
    var confirmRestore by remember { mutableStateOf(false) }
    val backupName = remember {
        "rubberring-backup-" +
            java.text.SimpleDateFormat("yyyy-MM-dd", java.util.Locale.US).format(java.util.Date()) +
            ".zip"
    }
    val startBackup = { exportLauncher.launch(backupName); Unit }
    val startRestore = { confirmRestore = true }

    // Surface the outcome of the last backup/restore as a toast, then clear it.
    val backupResult by viewModel.backupResult.collectAsStateWithLifecycle()
    LaunchedEffect(backupResult) {
        val message = when (backupResult) {
            BackupResult.EXPORTED -> R.string.backup_exported
            BackupResult.RESTORED -> R.string.backup_restored
            BackupResult.FAILED -> R.string.backup_failed
            null -> return@LaunchedEffect
        }
        Toast.makeText(context, message, Toast.LENGTH_LONG).show()
        viewModel.consumeBackupResult()
    }

    if (confirmRestore) {
        AlertDialog(
            onDismissRequest = { confirmRestore = false },
            title = { Text(stringResource(R.string.restore_confirm_title)) },
            text = { Text(stringResource(R.string.restore_confirm_body)) },
            confirmButton = {
                TextButton(onClick = {
                    confirmRestore = false
                    importLauncher.launch(
                        arrayOf("application/zip", "application/octet-stream", "application/x-zip-compressed"),
                    )
                }) { Text(stringResource(R.string.restore_choose)) }
            },
            dismissButton = {
                TextButton(onClick = { confirmRestore = false }) {
                    Text(stringResource(R.string.action_cancel))
                }
            },
        )
    }

    // The release notes, on the first launch after an update. Composed before the settings block
    // below so they survive its early return — that is what lets Settings → About reopen them.
    val showWhatsNew by viewModel.showWhatsNew.collectAsStateWithLifecycle()
    LaunchedEffect(Unit) { viewModel.checkWhatsNew(handedOverLaunch) }
    if (showWhatsNew) {
        WhatsNewDialog(onDismiss = viewModel::dismissWhatsNew)
    }

    // Settings is a full screen shown over whatever's underneath; the confirm-restore dialog and
    // backup-result toast above stay in scope, so Backup/Restore works from here too.
    if (showSettings) {
        SettingsScreen(
            following = following,
            keepScreenOn = keepScreenOn,
            saveZoom = saveZoom,
            edgeInset = edgeInset,
            themeMode = themeMode,
            germanNotes = noteNaming == NoteNaming.GERMAN,
            onToggleGermanNotes = { viewModel.setGermanNotes(noteNaming != NoteNaming.GERMAN) },
            onToggleFollow = viewModel::toggleFollowPlayhead,
            onToggleKeepScreenOn = viewModel::toggleKeepScreenOn,
            onToggleSaveZoom = viewModel::toggleSaveZoom,
            onToggleEdgeInset = viewModel::toggleEdgeInset,
            onSelectTheme = viewModel::setThemeMode,
            onBackup = startBackup,
            onRestore = startRestore,
            onWhatsNew = viewModel::showWhatsNew,
            onClose = { showSettings = false },
        )
        return
    }

    val inLibrary = state is LooperUiState.Empty
    val selecting = selection.isNotEmpty()

    // Keep the selection honest: drop ids that are no longer in the library (deleted, or replaced
    // by a restore), and abandon it entirely on the way out of the library.
    LaunchedEffect(library, inLibrary) {
        selection = if (inLibrary) selection.intersect(library.map { it.id }.toSet()) else emptySet()
    }

    // The library is the home view. Backing out of the play view returns there rather than
    // quitting; a swipe-back with the drawer open just closes the drawer. In the library with the
    // drawer closed and nothing selected the handler is disabled, so back keeps its default
    // behaviour of exiting.
    BackHandler(enabled = drawerState.isOpen || !inLibrary || selecting) {
        when {
            drawerState.isOpen -> scope.launch { drawerState.close() }
            selecting -> selection = emptySet()
            else -> viewModel.closeTrack()
        }
    }

    // Close the drawer, then run an action — used by every navigating drawer entry.
    val closeThen: (() -> Unit) -> Unit = { action ->
        scope.launch { drawerState.close() }
        action()
    }

    ModalNavigationDrawer(
        drawerState = drawerState,
        drawerContent = {
            LooperDrawer(
                // In the library itself the "Library" destination is redundant, so hide it.
                showLibrary = !inLibrary,
                recents = recents,
                onLibrary = { closeThen { viewModel.closeTrack() } },
                onImport = { closeThen { openPicker() } },
                onSettings = { closeThen { showSettings = true } },
                onSupport = { closeThen { showSupport = true } },
                onOpenRecent = { track -> closeThen { viewModel.open(track) } },
                onQuickHelp = { closeThen { showHelp = true } },
            )
        },
    ) {
        Scaffold(
            topBar = {
                // While tracks are selected the bar becomes a contextual one — count, a way out,
                // and the delete action — tinted so the mode is obvious. Its own controls (drawer,
                // keep-awake notice) step aside until the selection is done with.
                TopAppBar(
                    colors = if (selecting) {
                        TopAppBarDefaults.topAppBarColors(
                            containerColor = MaterialTheme.colorScheme.secondaryContainer,
                            titleContentColor = MaterialTheme.colorScheme.onSecondaryContainer,
                            navigationIconContentColor = MaterialTheme.colorScheme.onSecondaryContainer,
                            actionIconContentColor = MaterialTheme.colorScheme.onSecondaryContainer,
                        )
                    } else {
                        TopAppBarDefaults.topAppBarColors()
                    },
                    navigationIcon = {
                        if (selecting) {
                            IconButton(onClick = { selection = emptySet() }) {
                                Icon(
                                    Icons.Default.Close,
                                    contentDescription = stringResource(R.string.cd_cancel_selection),
                                )
                            }
                        } else {
                            IconButton(onClick = { scope.launch { drawerState.open() } }) {
                                Icon(
                                    Icons.Default.Menu,
                                    contentDescription = stringResource(R.string.cd_open_menu),
                                )
                            }
                        }
                    },
                    title = {
                        // On the library home the wordmark banner already names the app, so the
                        // corner title steps aside there; it stays on a loaded track (its filename).
                        Text(
                            text = when {
                                selecting -> pluralStringResource(
                                    R.plurals.selection_count,
                                    selection.size,
                                    selection.size,
                                )

                                else -> currentFileName(state) ?: ""
                            },
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    },
                    actions = {
                        if (selecting) {
                            IconButton(
                                onClick = { pendingDelete = library.filter { it.id in selection } },
                            ) {
                                Icon(
                                    Icons.Default.Delete,
                                    contentDescription = stringResource(R.string.cd_delete_selected),
                                )
                            }
                        }
                        // A quiet notice that the display is being kept awake (it drains battery);
                        // tap for an explanation and a shortcut to turn it off.
                        if (keepScreenOn && !selecting) {
                            IconButton(onClick = { showKeepAwakeInfo = true }) {
                                // No explicit tint: inherits the app bar's content colour (white on
                                // the dark bar), matching the menu icon rather than an accent tone.
                                Icon(
                                    painter = painterResource(R.drawable.ic_brightness_alert),
                                    contentDescription = stringResource(R.string.cd_screen_kept_on),
                                )
                            }
                        }
                    },
                )
            },
        ) { innerPadding ->
            when (val s = state) {
                is LooperUiState.Empty -> LibraryContent(
                    library = library,
                    selection = selection,
                    onImport = { openPicker() },
                    onOpen = viewModel::open,
                    onToggleSelect = { track ->
                        selection = if (track.id in selection) selection - track.id
                        else selection + track.id
                    },
                    onRename = viewModel::renameTrack,
                    onRequestDelete = { track -> pendingDelete = listOf(track) },
                    modifier = Modifier.padding(innerPadding),
                )

                else -> Box(
                    Modifier.fillMaxSize().padding(innerPadding).padding(16.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    when (s) {
                        is LooperUiState.Loading -> Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                        ) {
                            RingSpinner()
                            Spacer(Modifier.height(16.dp))
                            Text(stringResource(R.string.state_decoding))
                        }

                        is LooperUiState.Loaded -> LoadedContent(s.audio, viewModel)

                        is LooperUiState.Error -> Text(
                            stringResource(R.string.error_format, stringResource(s.message)),
                            textAlign = TextAlign.Center,
                            color = MaterialTheme.colorScheme.error,
                        )

                        is LooperUiState.Empty -> Unit
                    }
                }
            }
        }
    }

    if (showSupport) {
        SupportDialog(onDismiss = { showSupport = false })
    }

    if (showHelp) {
        QuickHelpDialog(onDismiss = { showHelp = false })
    }

    // One dialog for both delete paths — a row's own menu (one track) and the selection's bulk
    // delete (several). There is no undo, so this is the last word before the files go.
    if (pendingDelete.isNotEmpty()) {
        val doomed = pendingDelete
        val single = doomed.singleOrNull()
        AlertDialog(
            onDismissRequest = { pendingDelete = emptyList() },
            title = {
                Text(
                    if (single != null) {
                        stringResource(R.string.delete_track_title)
                    } else {
                        pluralStringResource(
                            R.plurals.delete_tracks_title,
                            doomed.size,
                            doomed.size,
                        )
                    },
                )
            },
            text = {
                Text(
                    if (single != null) {
                        stringResource(R.string.delete_track_body, single.name)
                    } else {
                        pluralStringResource(
                            R.plurals.delete_tracks_body,
                            doomed.size,
                            doomed.size,
                        )
                    },
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        viewModel.deleteTracks(doomed)
                        pendingDelete = emptyList()
                        selection = emptySet()
                    },
                ) { Text(stringResource(R.string.action_delete)) }
            },
            dismissButton = {
                TextButton(onClick = { pendingDelete = emptyList() }) {
                    Text(stringResource(R.string.action_cancel))
                }
            },
        )
    }

    if (showKeepAwakeInfo) {
        AlertDialog(
            onDismissRequest = { showKeepAwakeInfo = false },
            icon = {
                Icon(
                    painter = painterResource(R.drawable.ic_brightness_alert),
                    contentDescription = null,
                )
            },
            title = { Text(stringResource(R.string.keep_awake_title)) },
            text = { Text(stringResource(R.string.keep_awake_body)) },
            confirmButton = {
                TextButton(onClick = { showKeepAwakeInfo = false; showSettings = true }) {
                    Text(stringResource(R.string.settings_title))
                }
            },
            dismissButton = {
                TextButton(onClick = { showKeepAwakeInfo = false }) {
                    Text(stringResource(R.string.action_got_it))
                }
            },
        )
    }
}

/**
 * The two halves of the settings, and the page that is not a setting at all.
 *
 * The split is by *when you come here*, not by what the settings technically are. **Playback** is
 * what the waveform does while a track is running — the things you change with an instrument in
 * hand, mid-practice. **System** is the app's own set-up: how it looks, what language it speaks,
 * and where the library goes. One is visited often and the other is visited twice.
 *
 * Keep-screen-on sits under System rather than Playback, which is the one placement worth arguing:
 * it is about a track being played, but it is a display setting that costs battery, set once for
 * how you use the device rather than adjusted per track.
 *
 * **About** is the odd one, and is here because the tab row is the only always-visible strip on
 * this screen. It was a row at the bottom of the settings, which is where a footer item belongs
 * right up until the settings acquire tabs — then it is a destination buried inside one of two
 * halves, with nothing about "System" that says the app's version and its bug tracker are in there.
 * A tab is one tap from either half and says its own name whichever half is showing.
 */
private enum class SettingsTab(@StringRes val title: Int) {
    PLAYBACK(R.string.settings_tab_playback),
    SYSTEM(R.string.settings_tab_system),
    ABOUT(R.string.settings_tab_about),
}

/**
 * Full-screen settings, reached from the drawer: the playback toggles, the app's own set-up
 * (appearance, backup/restore), and About, split across the tabs in [SettingsTab]. [onClose] backs
 * out to the previous screen.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SettingsScreen(
    following: Boolean,
    keepScreenOn: Boolean,
    saveZoom: Boolean,
    edgeInset: Boolean,
    themeMode: ThemeMode,
    germanNotes: Boolean,
    onToggleGermanNotes: () -> Unit,
    onToggleFollow: () -> Unit,
    onToggleKeepScreenOn: () -> Unit,
    onToggleSaveZoom: () -> Unit,
    onToggleEdgeInset: () -> Unit,
    onSelectTheme: (ThemeMode) -> Unit,
    onBackup: () -> Unit,
    onRestore: () -> Unit,
    onWhatsNew: () -> Unit,
    onClose: () -> Unit,
) {
    BackHandler(onBack = onClose)
    // Local state: back from here leaves the settings altogether, so nothing a level up needs to
    // know which tab was showing.
    var tab by rememberSaveable { mutableStateOf(SettingsTab.PLAYBACK) }
    Scaffold(
        topBar = {
            TopAppBar(
                navigationIcon = {
                    IconButton(onClick = onClose) {
                        Icon(
                            Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = stringResource(R.string.action_back),
                        )
                    }
                },
                title = { Text(stringResource(R.string.settings_title)) },
            )
        },
    ) { innerPadding ->
        Column(
            Modifier
                .fillMaxSize()
                .padding(innerPadding),
        ) {
            PrimaryTabRow(selectedTabIndex = tab.ordinal, containerColor = Color.Transparent) {
                SettingsTab.entries.forEach { entry ->
                    Tab(
                        selected = tab == entry,
                        onClick = { tab = entry },
                        text = { Text(stringResource(entry.title)) },
                    )
                }
            }
            // Each tab scrolls on its own rather than sharing one scroller: About brings its own
            // layout and its own scroll — it is a page, not a column of rows — and a shared scroll
            // state would carry its offset across a tab switch.
            when (tab) {
                SettingsTab.PLAYBACK -> SettingsPage {
                    SettingSwitchRow(
                        stringResource(R.string.setting_follow_playhead),
                        Icons.Default.MyLocation,
                        following,
                        onToggleFollow,
                    )
                    SettingSwitchRow(
                        stringResource(R.string.setting_save_zoom),
                        Icons.Default.ZoomIn,
                        saveZoom,
                        onToggleSaveZoom,
                    )
                    SettingSwitchRow(
                        stringResource(R.string.setting_edge_inset),
                        ImageVector.vectorResource(R.drawable.ic_fit_width),
                        edgeInset,
                        onToggleEdgeInset,
                    )
                    SettingSwitchRow(
                        stringResource(R.string.setting_german_notes),
                        Icons.Default.MusicNote,
                        germanNotes,
                        onToggleGermanNotes,
                    )
                }

                SettingsTab.SYSTEM -> SettingsPage {
                    DrawerSectionLabel(stringResource(R.string.section_appearance))
                    ThemeModeChips(
                        mode = themeMode,
                        onSelect = onSelectTheme,
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
                    )
                    DrawerSectionLabel(stringResource(R.string.section_general))
                    SettingSwitchRow(
                        stringResource(R.string.setting_keep_screen_on),
                        ImageVector.vectorResource(R.drawable.ic_brightness_alert),
                        keepScreenOn,
                        onToggleKeepScreenOn,
                    )
                    LanguageSetting()

                    DrawerSectionLabel(stringResource(R.string.section_backup))
                    ChoiceRow(
                        icon = Icons.Default.Save,
                        title = stringResource(R.string.backup_row_title),
                        subtitle = stringResource(R.string.backup_row_subtitle),
                        onClick = onBackup,
                    )
                    ChoiceRow(
                        icon = Icons.Default.Restore,
                        title = stringResource(R.string.restore_row_title),
                        subtitle = stringResource(R.string.restore_row_subtitle),
                        onClick = onRestore,
                    )
                }

                SettingsTab.ABOUT -> AboutPage(onWhatsNew = onWhatsNew)
            }
        }
    }
}

/**
 * One offered language: its BCP-47 [tag], or null for "follow the device".
 *
 * [label] is deliberately the language's name *in that language* — Deutsch, not German. Someone who
 * has landed in a language they can't read needs to find their way out, and the only word on the
 * screen they are sure to recognise is their own language's name for itself.
 */
private data class LanguageChoice(val tag: String?, val label: String)

/**
 * The languages on offer: the device default, then everything listed in `supported_locales`,
 * sorted by how each names itself.
 */
@Composable
private fun rememberLanguageChoices(): List<LanguageChoice> {
    val systemLabel = stringResource(R.string.language_system)
    val tags = stringArrayResource(R.array.supported_locales)
    return remember(systemLabel, tags) {
        val offered = tags.map { tag ->
            val locale = Locale.forLanguageTag(tag)
            // Ask the locale to name itself, then fix the case: several languages write their own
            // name lowercase mid-sentence (français, español) but expect a capital when it stands
            // alone as a label.
            val own = locale.getDisplayName(locale).replaceFirstChar { it.titlecase(locale) }
            LanguageChoice(tag, own)
        }.sortedBy { it.label }
        listOf(LanguageChoice(null, systemLabel)) + offered
    }
}

/** The current app language as a BCP-47 tag, or null when it is following the device. */
private fun currentLanguageTag(): String? =
    AppCompatDelegate.getApplicationLocales().toLanguageTags().takeIf { it.isNotEmpty() }
        ?.substringBefore(',')

/**
 * The language row and its picker.
 *
 * Applying a choice goes through [AppCompatDelegate], not a preference of our own: on Android 13+
 * that writes through to the system's per-app language, so this picker and the one in Android's
 * own Settings agree with each other instead of quietly disagreeing. Selecting recreates the
 * activity, which is what re-reads the resources — so there is nothing to do afterwards.
 */
@Composable
private fun LanguageSetting() {
    val choices = rememberLanguageChoices()
    // Read once per composition rather than held in state: the activity is recreated on change,
    // so this is re-read with the new value on the way back up.
    val currentTag = currentLanguageTag()
    val current = choices.firstOrNull { it.tag == currentTag } ?: choices.first()
    var picking by remember { mutableStateOf(false) }

    ChoiceRow(
        icon = Icons.Default.Language,
        title = stringResource(R.string.setting_language),
        subtitle = current.label,
        onClick = { picking = true },
    )

    if (picking) {
        AlertDialog(
            onDismissRequest = { picking = false },
            title = { Text(stringResource(R.string.setting_language)) },
            text = {
                Column(Modifier.verticalScroll(rememberScrollState())) {
                    choices.forEach { choice ->
                        Row(
                            Modifier
                                .fillMaxWidth()
                                .clip(ControlShape)
                                .clickable {
                                    picking = false
                                    AppCompatDelegate.setApplicationLocales(
                                        if (choice.tag == null) {
                                            LocaleListCompat.getEmptyLocaleList()
                                        } else {
                                            LocaleListCompat.forLanguageTags(choice.tag)
                                        },
                                    )
                                }
                                .padding(horizontal = 8.dp, vertical = 12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            RadioButton(
                                selected = choice.tag == current.tag,
                                onClick = null, // the whole row is the target
                            )
                            Spacer(Modifier.width(12.dp))
                            Text(choice.label, style = MaterialTheme.typography.bodyLarge)
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { picking = false }) {
                    Text(stringResource(R.string.action_cancel))
                }
            },
        )
    }
}

/** The ground a settings tab stands on: the whole tab, scrolling, with room at the bottom. */
@Composable
private fun SettingsPage(content: @Composable ColumnScope.() -> Unit) {
    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(top = 8.dp, bottom = 16.dp),
        content = content,
    )
}

private const val REPO_URL = "https://github.com/mkay/RubberRing"
private const val ISSUES_URL = "$REPO_URL/issues"
private const val CHANGELOG_URL = "$REPO_URL/blob/main/CHANGELOG.md"
private const val KOFI_URL = "https://ko-fi.com/s1ngular"

/**
 * The "easy way" out of [SupportDialog]: a page carrying a like button the visitor presses. The
 * count deliberately does not come from the visit itself — a Do-Not-Track browser suppresses a
 * plain pixel, so a silent hit counter would have quietly under-counted exactly the privacy-minded
 * audience this app has. A button press is an explicit act the browser has no reason to withhold,
 * and it is also the honest thing to ask for: nothing is recorded unless the visitor means it.
 *
 * The query string names the app, so one page serves all of them.
 */
private const val LIKE_URL = "https://singular.de/apps/feedback/?rubberring"

/**
 * About: what the app is, where it lives, and how to report a bug or chip in.
 *
 * A tab inside Settings rather than an item in the drawer — the drawer's bottom slot belongs to
 * Quick help, which is what you want mid-track; this is the page you visit once out of curiosity
 * and once when filing an issue. It was a screen of its own until the settings grew tabs and it
 * became a destination buried in one of them; see [SettingsTab] for why a tab is where it ended up.
 *
 * It sits inside the settings' own Scaffold rather than bringing one of its own — the bar above it
 * already carries the title and the way back out.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun AboutPage(onWhatsNew: () -> Unit, modifier: Modifier = Modifier) {
    val uriHandler = LocalUriHandler.current
    val clipboard = LocalClipboardManager.current
    val haptic = LocalHapticFeedback.current
    val context = LocalContext.current
    // versionCode rides along in the copied string: it's what pins a bug report to an exact
    // build when a version was re-released (0.3 → 0.4 taught us that the name alone can lie).
    val version = "${BuildConfig.VERSION_NAME} (${BuildConfig.VERSION_CODE})"

    Column(
        modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 24.dp)
            .padding(bottom = 24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Spacer(Modifier.height(16.dp))
        // The ring on the page's own background, not on a tile: it is a line drawing that carries
        // its own contrast either way round, so there is nothing for a tile to rescue.
        Image(
            painter = painterResource(R.drawable.ic_ring),
            contentDescription = null,
            modifier = Modifier.size(88.dp),
        )
        Spacer(Modifier.height(12.dp))
        Text(stringResource(R.string.app_name), style = MaterialTheme.typography.headlineSmall)
        // Long-press to copy, mirroring the library's press-and-hold idiom. Android 13 and up
        // pops its own clipboard confirmation, so only older versions get a toast.
        val versionClip = stringResource(R.string.about_version_clipboard, version)
        val copiedToast = stringResource(R.string.toast_version_copied)
        Text(
            stringResource(R.string.about_version, version),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier
                .clip(ControlShape)
                .combinedClickable(
                    onClick = {},
                    onLongClick = {
                        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                        clipboard.setText(AnnotatedString(versionClip))
                        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) {
                            Toast.makeText(context, copiedToast, Toast.LENGTH_SHORT).show()
                        }
                    },
                )
                .padding(horizontal = 12.dp, vertical = 6.dp),
        )

        Spacer(Modifier.height(28.dp))
        AboutSection(stringResource(R.string.about_section_about)) {
            AboutBody(stringResource(R.string.about_body_about))
        }
        // The notes appear on their own after an update; this is for reading them again once
        // dismissed, which is otherwise impossible without clearing the app's data.
        AboutSection(stringResource(R.string.about_section_whats_new)) {
            AboutBody(stringResource(R.string.about_body_whats_new))
            AboutLink(stringResource(R.string.about_whats_new_action), onWhatsNew)
        }
        AboutSection(stringResource(R.string.about_section_website)) {
            AboutBody(stringResource(R.string.about_body_website))
            AboutLink(REPO_URL) { uriHandler.openUri(REPO_URL) }
        }
        AboutSection(stringResource(R.string.about_section_bugs)) {
            AboutBody(stringResource(R.string.about_body_bugs))
            AboutLink(ISSUES_URL) { uriHandler.openUri(ISSUES_URL) }
        }
        AboutSection(stringResource(R.string.about_section_support)) {
            AboutBody(stringResource(R.string.about_body_support))
            AboutLink(KOFI_URL) { uriHandler.openUri(KOFI_URL) }
        }
        // GPL §5 requires a derivative to preserve legal notices, so a licence stated *in the
        // app* rather than only in the repo is worth more than it looks: a clone that stripped
        // this screen has done so deliberately, and the before-and-after is a screenshot.
        AboutSection(stringResource(R.string.about_section_license)) {
            AboutBody(stringResource(R.string.about_copyright))
            AboutBody(stringResource(R.string.about_license_body))
            AboutLink(REPO_URL) { uriHandler.openUri(REPO_URL) }
        }
    }
}

@Composable
private fun AboutSection(title: String, content: @Composable ColumnScope.() -> Unit) {
    Column(Modifier.fillMaxWidth().padding(bottom = 24.dp)) {
        Text(
            title,
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.primary,
        )
        Spacer(Modifier.height(4.dp))
        content()
    }
}

@Composable
private fun AboutBody(text: String) {
    Text(text, style = MaterialTheme.typography.bodyMedium)
}

/**
 * A tappable line in the About page. URLs are kept full-length rather than hidden behind link
 * text so they stay readable; the same styling carries the one action that stays in the app.
 */
@Composable
private fun AboutLink(text: String, onClick: () -> Unit) {
    Text(
        text,
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier
            .clip(ControlShape)
            .clickable(onClick = onClick)
            .padding(vertical = 6.dp),
    )
}

/**
 * The left navigation drawer, shared by the library and play views: primary destinations
 * (Library — hidden when [showLibrary] is false, i.e. already in the library — Import, Settings,
 * and Support), a recents list, and Quick help pinned to the bottom.
 */
@Composable
private fun LooperDrawer(
    showLibrary: Boolean,
    recents: List<LibraryTrack>,
    onLibrary: () -> Unit,
    onImport: () -> Unit,
    onSettings: () -> Unit,
    onSupport: () -> Unit,
    onOpenRecent: (LibraryTrack) -> Unit,
    onQuickHelp: () -> Unit,
) {
    // Take 4/5 of the screen width, leaving a strip of dimmed scrim on the right to tap-to-close.
    ModalDrawerSheet(Modifier.fillMaxWidth(0.8f)) {
        Column(Modifier.fillMaxSize()) {
            Text(
                stringResource(R.string.app_name),
                style = MaterialTheme.typography.titleLarge,
                modifier = Modifier.padding(start = 28.dp, top = 20.dp, bottom = 12.dp),
            )

            val itemPadding = Modifier.padding(NavigationDrawerItemDefaults.ItemPadding)
            if (showLibrary) {
                NavigationDrawerItem(
                    label = { Text(stringResource(R.string.drawer_library)) },
                    icon = { Icon(Icons.Default.LibraryMusic, contentDescription = null) },
                    selected = false,
                    onClick = onLibrary,
                    modifier = itemPadding,
                )
            }
            NavigationDrawerItem(
                label = { Text(stringResource(R.string.drawer_import)) },
                icon = { Icon(Icons.Default.FolderOpen, contentDescription = null) },
                selected = false,
                onClick = onImport,
                modifier = itemPadding,
            )
            NavigationDrawerItem(
                label = { Text(stringResource(R.string.settings_title)) },
                icon = { Icon(Icons.Default.Settings, contentDescription = null) },
                selected = false,
                onClick = onSettings,
                modifier = itemPadding,
            )
            NavigationDrawerItem(
                label = { Text(stringResource(R.string.drawer_support)) },
                icon = {
                    Icon(
                        ImageVector.vectorResource(R.drawable.ic_heart_smile),
                        contentDescription = null,
                    )
                },
                selected = false,
                onClick = onSupport,
                modifier = itemPadding,
            )

            if (recents.isNotEmpty()) {
                DrawerSectionLabel(stringResource(R.string.section_recent))
                recents.forEach { track ->
                    NavigationDrawerItem(
                        label = {
                            Text(track.name, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        },
                        icon = { Icon(Icons.Default.GraphicEq, contentDescription = null) },
                        selected = false,
                        onClick = { onOpenRecent(track) },
                        modifier = itemPadding,
                    )
                }
            }

            // Push Quick help to the bottom — the conventional spot for help/about.
            Spacer(Modifier.weight(1f))
            HorizontalDivider(Modifier.padding(horizontal = 16.dp))
            NavigationDrawerItem(
                label = { Text(stringResource(R.string.drawer_quick_help)) },
                icon = { Icon(Icons.Default.HelpOutline, contentDescription = null) },
                selected = false,
                onClick = onQuickHelp,
                modifier = Modifier.padding(NavigationDrawerItemDefaults.ItemPadding).padding(bottom = 8.dp),
            )
        }
    }
}

@Composable
private fun DrawerSectionLabel(text: String) {
    Text(
        text.uppercase(),
        style = MaterialTheme.typography.labelSmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(start = 28.dp, top = 16.dp, bottom = 4.dp),
    )
}

/** A single-select row of Follow system / Light / Dark chips, shared by the drawer and library. */
@Composable
private fun ThemeModeChips(
    mode: ThemeMode,
    onSelect: (ThemeMode) -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        val label = mapOf(
            ThemeMode.SYSTEM to R.string.theme_system,
            ThemeMode.LIGHT to R.string.theme_light,
            ThemeMode.DARK to R.string.theme_dark,
        )
        ThemeMode.entries.forEach { m ->
            FilterChip(
                selected = mode == m,
                onClick = { onSelect(m) },
                label = { Text(stringResource(label.getValue(m))) },
                shape = ControlShape,
            )
        }
    }
}

/** A settings row: icon + label with a trailing switch; tapping anywhere on the row toggles it. */
@Composable
private fun SettingSwitchRow(
    label: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    checked: Boolean,
    onToggle: () -> Unit,
) {
    Row(
        Modifier
            .fillMaxWidth()
            .clip(ControlShape)
            .clickable(onClick = onToggle)
            .padding(horizontal = 16.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(Modifier.width(16.dp))
        Text(label, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
        Switch(checked = checked, onCheckedChange = { onToggle() })
    }
}

/**
 * The three ways to signal that the app is worth keeping up, in ascending order of effort.
 *
 * Every one of them is a link out, which is the point: an app that says it doesn't track anything
 * shouldn't then open a socket to say so. The browser does the fetching in its own process, so the
 * manifest stays free of INTERNET and the no-tracking claim remains checkable from the manifest
 * alone — see [openCustomTab]. The price is that we never learn what happened in the tab, so
 * nothing here reports success; each page has to confirm for itself. That suits the easy way in
 * particular, where the act being counted is a button press on the page — see [LIKE_URL].
 */
@Composable
private fun SupportDialog(onDismiss: () -> Unit) {
    val context = LocalContext.current
    val toolbarColor = MaterialTheme.colorScheme.surface

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.support_title)) },
        text = {
            Column(
                Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(20.dp),
            ) {
                Text(
                    stringResource(R.string.support_intro),
                    style = MaterialTheme.typography.bodyMedium,
                )
                Text(
                    stringResource(R.string.support_question),
                    style = MaterialTheme.typography.bodyMedium,
                )
                Text(
                    stringResource(R.string.support_ways),
                    style = MaterialTheme.typography.bodyMedium,
                )

                SupportWay(
                    title = stringResource(R.string.support_easy_title),
                    action = stringResource(R.string.support_easy_action),
                    icon = Icons.Default.Favorite,
                    // The one that costs nothing, so it gets the filled button.
                    emphasised = true,
                    note = stringResource(R.string.support_easy_note),
                    onClick = { openCustomTab(context, LIKE_URL, toolbarColor) },
                )
                SupportWay(
                    title = stringResource(R.string.support_nerdy_title),
                    action = stringResource(R.string.support_nerdy_action),
                    icon = Icons.Default.Star,
                    note = stringResource(R.string.support_nerdy_note),
                    onClick = { openCustomTab(context, REPO_URL, toolbarColor) },
                )
                SupportWay(
                    title = stringResource(R.string.support_generous_title),
                    action = stringResource(R.string.support_generous_action),
                    icon = Icons.Default.Paid,
                    note = stringResource(R.string.support_generous_note),
                    onClick = { openCustomTab(context, KOFI_URL, toolbarColor) },
                )
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_close)) }
        },
    )
}

/** One row of [SupportDialog]: a heading, the button that does the thing, and the small print. */
@Composable
private fun SupportWay(
    title: String,
    action: String,
    icon: ImageVector,
    note: String,
    onClick: () -> Unit,
    emphasised: Boolean = false,
) {
    // The icon and label are the same either way; only the button's weight differs, so the
    // content is built once rather than duplicated into both branches.
    val content: @Composable RowScope.() -> Unit = {
        Icon(icon, contentDescription = null, modifier = Modifier.size(ButtonDefaults.IconSize))
        Spacer(Modifier.width(ButtonDefaults.IconSpacing))
        Text(action)
    }
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text(title, style = MaterialTheme.typography.titleSmall)
        if (emphasised) {
            Button(onClick = onClick, shape = ControlShape, content = content)
        } else {
            OutlinedButton(onClick = onClick, shape = ControlShape, content = content)
        }
        Text(
            note,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

/**
 * Hands [url] to the user's browser as a Custom Tab: it renders over our task with the app's own
 * surface colour in its toolbar, so it reads as part of the app, but the fetch happens in the
 * browser's process under the browser's UID. That is why none of this needs INTERNET — the
 * permission is a per-UID kernel check on whoever opens the socket, and that is never us.
 *
 * A device with no browser at all is rare but possible (and the Custom Tab degrades to a plain
 * ACTION_VIEW there), so the failure is caught rather than left to crash the app.
 */
private fun openCustomTab(context: android.content.Context, url: String, toolbarColor: Color) {
    val intent = CustomTabsIntent.Builder()
        .setShowTitle(true)
        .setDefaultColorSchemeParams(
            CustomTabColorSchemeParams.Builder()
                .setToolbarColor(toolbarColor.toArgb())
                .build(),
        )
        .build()
    try {
        intent.launchUrl(context, Uri.parse(url))
    } catch (_: android.content.ActivityNotFoundException) {
        Toast.makeText(context, R.string.toast_no_browser, Toast.LENGTH_SHORT).show()
    }
}

/**
 * The current release's notes, shown once after an update and reopenable from Settings → About.
 *
 * The text comes from `res/raw/whatsnew.md` rather than from a string resource: it is prose that
 * gets rewritten wholesale every release, and a plain file is far nicer to curate than escaped
 * XML. Being a resource and not an asset, it still picks up `res/raw-de/` on a German device and
 * falls back to the default file otherwise — the same rules `values-de/` follows, for free.
 *
 * It is rendered verbatim apart from an HTML comment at the top, which carries the note to
 * whoever edits it next. Nothing else is parsed, so the file must stay plain bullets: no
 * dependency here can render markdown, and adding one for a dialog this size isn't worth the
 * download size (or the F-Droid scrutiny).
 */
@Composable
private fun WhatsNewDialog(onDismiss: () -> Unit) {
    val context = LocalContext.current
    val toolbarColor = MaterialTheme.colorScheme.surface
    // Keyed on the context so a language change, which recreates the activity, re-reads the file
    // and picks up the other locale's copy.
    val notes = remember(context) {
        runCatching {
            context.resources.openRawResource(R.raw.whatsnew).bufferedReader().use { it.readText() }
        }.getOrDefault("").replace(EDITOR_NOTE, "").trim()
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        // The icon sits beside the title rather than in the dialog's own icon slot, which would
        // centre it above and push the heading down a line.
        title = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    painter = painterResource(R.drawable.ic_flare),
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(28.dp),
                )
                Spacer(Modifier.width(12.dp))
                Text(stringResource(R.string.whats_new_title, BuildConfig.VERSION_NAME))
            }
        },
        text = {
            Column(
                Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                Text(notes, style = MaterialTheme.typography.bodyMedium)
                // Only this release is described above, so anyone who skipped a version — the
                // norm on F-Droid, where updates are pulled rather than pushed — gets one link
                // to the whole history instead of the app carrying it around.
                Text(
                    stringResource(R.string.whats_new_changelog),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier
                        .clip(ControlShape)
                        .clickable { openCustomTab(context, CHANGELOG_URL, toolbarColor) }
                        .padding(vertical = 6.dp),
                )
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_got_it)) }
        },
    )
}

/** The editor's note at the top of `whatsnew.md`, stripped before the file is shown. */
private val EDITOR_NOTE = Regex("<!--.*?-->", RegexOption.DOT_MATCHES_ALL)

@Composable
private fun QuickHelpDialog(onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.help_title)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                HelpItem(
                    stringResource(R.string.help_marker_title),
                    stringResource(R.string.help_marker_body),
                )
                HelpItem(
                    stringResource(R.string.help_playhead_title),
                    stringResource(R.string.help_playhead_body),
                )
                HelpItem(
                    stringResource(R.string.help_countin_title),
                    stringResource(R.string.help_countin_body),
                )
                HelpItem(
                    stringResource(R.string.help_zoom_title),
                    stringResource(R.string.help_zoom_body),
                )
                HelpItem(
                    stringResource(R.string.help_save_loop_title),
                    stringResource(R.string.help_save_loop_body),
                )
                HelpItem(
                    stringResource(R.string.help_recall_loop_title),
                    stringResource(R.string.help_recall_loop_body),
                )
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_got_it)) }
        },
    )
}

@Composable
private fun HelpItem(title: String, body: String) {
    Column {
        Text(title, style = MaterialTheme.typography.titleSmall)
        Text(body, style = MaterialTheme.typography.bodyMedium)
    }
}

/** The wordmark's width-to-height, from its 1194×230 artwork. */
private const val CLAIM_ASPECT = 1194f / 230f

/** One full turn of the ring spinner, in milliseconds. */
private const val SPINNER_PERIOD_MS = 1100

/**
 * The app's ring mark spun as a loading indicator, in place of a generic progress circle. It keeps
 * its own four-tone artwork rather than tinting, so it reads as the brand mark; a linear, seamless
 * turn makes it clear the app is working without implying a measurable amount of progress.
 */
@Composable
private fun RingSpinner(modifier: Modifier = Modifier) {
    val transition = rememberInfiniteTransition(label = "ring-spinner")
    val angle by transition.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = SPINNER_PERIOD_MS, easing = LinearEasing),
        ),
        label = "ring-angle",
    )
    Image(
        painter = painterResource(R.drawable.spinner_ring),
        contentDescription = null,
        modifier = modifier.size(56.dp).rotate(angle),
    )
}

@Composable
private fun LibraryContent(
    library: List<LibraryTrack>,
    selection: Set<String>,
    onImport: () -> Unit,
    onOpen: (LibraryTrack) -> Unit,
    onToggleSelect: (LibraryTrack) -> Unit,
    onRename: (LibraryTrack, String) -> Unit,
    onRequestDelete: (LibraryTrack) -> Unit,
    modifier: Modifier = Modifier,
) {
    var renameTarget by remember { mutableStateOf<LibraryTrack?>(null) }
    val selecting = selection.isNotEmpty()

    Column(
        modifier.fillMaxSize().padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        // With no library yet, centre the welcome block vertically; otherwise pin it to the top
        // and let the list fill the space below.
        if (library.isEmpty()) Spacer(Modifier.weight(1f))

        // The ring logo on its own — no tile — so it reads cleanly on either theme.
        Image(
            painter = painterResource(R.drawable.ic_ring),
            contentDescription = null,
            modifier = Modifier.size(96.dp),
        )
        Spacer(Modifier.height(16.dp))
        // The "Rubber Ring" wordmark and its claim. The wordmark is a single-colour vector — the
        // type is already outlined to paths — so it is tinted here rather than carrying its own
        // colour, and reads in either theme. Sized to a fixed width keeping the artwork's aspect so
        // it never runs off a narrow phone. The claim below is live text, not more outlined paths,
        // so it stays legible at any size and is read aloud as words; the wordmark carries no
        // description of its own to keep the name from being spoken twice.
        Icon(
            painter = painterResource(R.drawable.claim),
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier
                .width(200.dp)
                .aspectRatio(CLAIM_ASPECT),
        )
        Spacer(Modifier.height(6.dp))
        Text(
            stringResource(R.string.app_tagline),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )
        Spacer(Modifier.height(20.dp))
        Text(
            stringResource(R.string.library_intro),
            style = MaterialTheme.typography.bodyMedium,
            textAlign = TextAlign.Center,
        )
        Spacer(Modifier.height(20.dp))
        Button(onClick = onImport, shape = ControlShape) {
            Text(stringResource(R.string.drawer_import))
        }
        Spacer(Modifier.height(10.dp))
        Text(
            stringResource(R.string.library_formats),
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )

        if (library.isEmpty()) {
            Spacer(Modifier.weight(1f))
        } else {
            Spacer(Modifier.height(24.dp))
            Text(
                stringResource(R.string.library_heading),
                style = MaterialTheme.typography.titleSmall,
                modifier = Modifier.fillMaxWidth(),
            )
            Spacer(Modifier.height(8.dp))
            LazyColumn(
                Modifier.fillMaxWidth().weight(1f),
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                items(library, key = { it.id }) { track ->
                    LibraryRow(
                        track = track,
                        selected = track.id in selection,
                        selecting = selecting,
                        // While selecting, a tap picks rather than opens — one meaning per tap.
                        onClick = { if (selecting) onToggleSelect(track) else onOpen(track) },
                        onLongClick = { onToggleSelect(track) },
                        onRename = { renameTarget = track },
                        onDelete = { onRequestDelete(track) },
                    )
                }
            }
        }
    }

    renameTarget?.let { target ->
        LoopNameDialog(
            title = stringResource(R.string.rename_track_title),
            initial = target.name,
            // Two imported files may legitimately share a name, so tracks aren't constrained.
            taken = emptyList(),
            onConfirm = { onRename(target, it); renameTarget = null },
            onDismiss = { renameTarget = null },
        )
    }
}

/** One selectable option: icon + title + subtitle, the whole row tappable. */
@Composable
private fun ChoiceRow(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    title: String,
    subtitle: String,
    onClick: () -> Unit,
) {
    Row(
        Modifier
            .fillMaxWidth()
            .clip(ControlShape)
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(Modifier.width(16.dp))
        Column {
            Text(title, style = MaterialTheme.typography.bodyLarge)
            Text(
                subtitle,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun LibraryRow(
    track: LibraryTrack,
    selected: Boolean,
    selecting: Boolean,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
    onRename: () -> Unit,
    onDelete: () -> Unit,
) {
    var menu by remember { mutableStateOf(false) }
    val haptic = LocalHapticFeedback.current
    Surface(
        shape = ControlShape,
        color = if (selected) MaterialTheme.colorScheme.secondaryContainer
        else MaterialTheme.colorScheme.surfaceVariant,
        modifier = Modifier
            .fillMaxWidth()
            .clip(ControlShape)
            .combinedClickable(
                onClick = onClick,
                onLongClick = {
                    // Confirm the grab in the hand — the gesture has no on-screen affordance.
                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                    onLongClick()
                },
            ),
    ) {
        Row(
            Modifier.padding(start = 12.dp, end = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(Modifier.weight(1f).padding(vertical = 10.dp)) {
                Text(
                    track.name,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    style = MaterialTheme.typography.bodyLarge,
                )
                val loops = track.savedLoops.size
                val loopCount = if (loops > 0) {
                    pluralStringResource(R.plurals.track_loop_count, loops, loops)
                } else {
                    null
                }
                val info = buildList {
                    if (track.durationMs > 0) add(formatMs(track.durationMs))
                    if (loopCount != null) add(loopCount)
                }.joinToString(" | ")
                // Show the metadata on the left; when a custom title is set, put the original
                // file name on the right (otherwise it would just repeat the label above).
                if (info.isNotEmpty() || track.title != null) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            info,
                            style = MaterialTheme.typography.labelSmall,
                            modifier = Modifier.weight(1f),
                        )
                        if (track.title != null) {
                            Text(
                                track.displayName,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                textAlign = TextAlign.End,
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
                                // Cap at half the row so a long file name truncates instead of
                                // crowding out the metadata on the left.
                                modifier = Modifier.weight(1f).padding(start = 8.dp),
                            )
                        }
                    }
                }
            }
            // While selecting, the row's own menu would compete with the selection's actions, so
            // it gives way to a tick that says whether this track is in the batch.
            if (selecting) {
                Box(Modifier.padding(12.dp)) {
                    Icon(
                        if (selected) Icons.Default.CheckCircle else Icons.Default.RadioButtonUnchecked,
                        contentDescription = stringResource(
                            if (selected) R.string.cd_selected else R.string.cd_not_selected,
                        ),
                        tint = if (selected) MaterialTheme.colorScheme.onSecondaryContainer
                        else MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            } else {
                Box {
                    IconButton(onClick = { menu = true }) {
                        Icon(
                            Icons.Default.MoreVert,
                            contentDescription = stringResource(R.string.cd_track_options),
                        )
                    }
                    DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                        DropdownMenuItem(
                            text = { Text(stringResource(R.string.action_rename)) },
                            leadingIcon = { Icon(Icons.Default.Edit, contentDescription = null) },
                            onClick = { menu = false; onRename() },
                        )
                        DropdownMenuItem(
                            text = { Text(stringResource(R.string.action_delete)) },
                            leadingIcon = { Icon(Icons.Default.Delete, contentDescription = null) },
                            onClick = { menu = false; onDelete() },
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun LoadedContent(audio: DecodedAudio, viewModel: LooperViewModel) {
    val region by viewModel.region.collectAsStateWithLifecycle()
    val playhead by viewModel.playhead.collectAsStateWithLifecycle()
    val grid by viewModel.grid.collectAsStateWithLifecycle()
    val chords by viewModel.chords.collectAsStateWithLifecycle()
    val currentChord by viewModel.currentChord.collectAsStateWithLifecycle()
    val chordEditMode by viewModel.chordEditMode.collectAsStateWithLifecycle()
    val showChords by viewModel.showChords.collectAsStateWithLifecycle()
    val noteNaming by viewModel.noteNaming.collectAsStateWithLifecycle()
    val detecting by viewModel.detecting.collectAsStateWithLifecycle()
    val isPlaying by viewModel.isPlaying.collectAsStateWithLifecycle()
    val loops by viewModel.savedLoops.collectAsStateWithLifecycle()
    val arrangement by viewModel.arrangement.collectAsStateWithLifecycle()
    val arrangementActive by viewModel.arrangementActive.collectAsStateWithLifecycle()
    val arrangementRepeat by viewModel.arrangementRepeat.collectAsStateWithLifecycle()
    val following by viewModel.followPlayhead.collectAsStateWithLifecycle()
    val speed by viewModel.speed.collectAsStateWithLifecycle()
    val stretching by viewModel.stretching.collectAsStateWithLifecycle()
    val countIn by viewModel.countIn.collectAsStateWithLifecycle()
    val clickThroughLoop by viewModel.clickThroughLoop.collectAsStateWithLifecycle()
    val clickVolume by viewModel.clickVolume.collectAsStateWithLifecycle()
    val clickPitch by viewModel.clickPitch.collectAsStateWithLifecycle()
    val countingIn by viewModel.countingIn.collectAsStateWithLifecycle()
    val normalize by viewModel.normalize.collectAsStateWithLifecycle()
    val normalizeGainDb by viewModel.normalizeGainDb.collectAsStateWithLifecycle()
    var showArrange by remember { mutableStateOf(false) }
    var showOptions by remember { mutableStateOf(false) }
    // (spanIndex, tappedFrac) of the chord being edited; non-null shows the chord picker.
    var chordEdit by remember { mutableStateOf<Pair<Int, Float>?>(null) }
    val durationMs = audio.durationMs

    // Once the arrangement is enabled, the Play button runs the sequence — so the single-loop and
    // grid controls no longer affect what you hear and would only confuse. Dim them for as long as
    // the arrangement is armed; a tap explains why instead of acting. Turn Arrange off to regain them.
    val context = androidx.compose.ui.platform.LocalContext.current
    // The Arrange button is icon-only (a numbered-list glyph), so the notice names it in words —
    // a real icon can't render in a toast on modern Android.
    val notifyLocked: () -> Unit = {
        Toast.makeText(context, R.string.toast_arrange_locked, Toast.LENGTH_SHORT).show()
    }
    // Save stays put once the track is full — a control that vanishes reads as a broken app, so it
    // dims instead and a tap says what the limit is and how to get under it.
    // Resolved here rather than inside the lambda so it follows the in-app language picker.
    val loopLimitMessage = stringResource(R.string.toast_loop_limit, MAX_SAVED_LOOPS)
    val notifyLoopLimit: () -> Unit = {
        Toast.makeText(context, loopLimitMessage, Toast.LENGTH_SHORT).show()
    }

    val intervalFrac = if (grid.bpm > 0f && durationMs > 0)
        ((60_000.0 / grid.bpm / grid.subdivision) / durationMs).toFloat() else 0f

    // Waveform flexes to fill remaining space so the controls stay pinned on screen in
    // both portrait and landscape.
    Column(
        Modifier.fillMaxSize(),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        // The saved viewport (or the reset default) is already in the ViewModel by the time this
        // composes; read it once to seed the waveform, which owns pan/zoom during the session and
        // mirrors changes back via onViewportChange.
        val initialViewport = remember(audio) { viewModel.viewport.value }

        // Long-pressing a marker is the one gesture nothing on screen can show, so the hint rides
        // along with every track that's opened. It fades on its own — never something to dismiss —
        // and retires early once a marker is actually grabbed, since it sits where the drag happens.
        var hintDone by remember(audio) { mutableStateOf(false) }
        LaunchedEffect(audio) {
            delay(HINT_VISIBLE_MS)
            hintDone = true
        }
        val hintAlpha by animateFloatAsState(
            targetValue = if (hintDone) 0f else 1f,
            animationSpec = tween(HINT_FADE_MS),
            label = "markerHintAlpha",
        )

        // Hold the waveform back from the system's back-gesture strips, so a marker parked at the
        // very start or end isn't sitting on top of one — grabbing it used to leave the app instead
        // of moving it. The inset is derived from the *reported* gesture insets, so it tracks the
        // user's back-sensitivity setting and collapses to zero on 3-button nav, where there is no
        // gesture to dodge. Half of it is deliberate: a full inset costs too much waveform width,
        // and the waveform's own systemGestureExclusion() covers the rest of the overlap. It still
        // costs some width, so Settings can turn it off for anyone who'd rather have the pixels.
        val edgeInset by viewModel.edgeInset.collectAsStateWithLifecycle()
        val gestureInsets = WindowInsets.systemGestures.asPaddingValues()
        val layoutDirection = LocalLayoutDirection.current
        val inset = if (edgeInset) EDGE_INSET_FRACTION else 0f
        if (chordEditMode) {
            ChordEditBar(onDone = { viewModel.setChordEditMode(false) })
        }
        Box(
            Modifier
                .fillMaxWidth()
                .weight(1f)
                .padding(
                    start = gestureInsets.calculateStartPadding(layoutDirection) * inset,
                    end = gestureInsets.calculateEndPadding(layoutDirection) * inset,
                )
        ) {
            LoopWaveform(
                waveform = audio.waveform,
                startFrac = region.startFrac,
                endFrac = region.endFrac,
                playheadFrac = playhead,
                showPlayhead = true,
                followPlayhead = following,
                gridOffsetFrac = grid.downbeatFrac,
                gridIntervalFrac = if (grid.enabled) intervalFrac else 0f,
                gridLinesPerBeat = grid.subdivision,
                chords = if (showChords) chords?.spans ?: emptyList() else emptyList(),
                noteNaming = noteNaming,
                chordEditMode = chordEditMode && showChords,
                onChordTap = { index, frac -> chordEdit = index to frac },
                onChordBoundaryMove = viewModel::moveChordBoundary,
                onChordAdd = { frac ->
                    val i = viewModel.addChordAt(frac)
                    if (i >= 0) chordEdit = i to frac // open the picker on the new chord
                },
                initialZoom = initialViewport.zoom,
                initialOffset = initialViewport.offset,
                gain = Gain.dbToLinear(normalizeGainDb),
                onViewportChange = viewModel::setViewport,
                onStartChange = viewModel::setStart,
                onEndChange = viewModel::setEnd,
                onSeek = viewModel::seek,
                modifier = Modifier.fillMaxSize(),
                onMarkerGrabbed = { hintDone = true },
            )

            // Sits above the waveform but takes no touches of its own — the gesture it describes has
            // to stay available through it, and a hint that ate the taps would be its own bug.
            // Neutral (the inverse-surface pair Material uses for snackbars) rather than brand red:
            // it has to read as a passing note over both waveform palettes, not as a control.
            if (hintAlpha > 0f) {
                Text(
                    stringResource(R.string.hint_long_press_marker),
                    color = MaterialTheme.colorScheme.inverseOnSurface,
                    style = MaterialTheme.typography.labelLarge,
                    modifier = Modifier
                        .align(Alignment.Center)
                        .alpha(hintAlpha * HINT_OPACITY)
                        .clip(ControlShape)
                        .background(MaterialTheme.colorScheme.inverseSurface)
                        .padding(horizontal = 12.dp, vertical = 6.dp),
                )
            }
        }

        Spacer(Modifier.height(8.dp))

        Row(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            TimeLabel(
                stringResource(R.string.time_start),
                (region.startFrac * durationMs).toLong(),
            )
            TimeLabel(
                stringResource(R.string.time_loop),
                ((region.endFrac - region.startFrac) * durationMs).toLong(),
            )
            TimeLabel(stringResource(R.string.time_end), (region.endFrac * durationMs).toLong())
        }

        Spacer(Modifier.height(8.dp))

        // At-a-glance readout of the chord under the playhead — the lane on the waveform shows the
        // whole timeline; this stays visible when zoomed in and a lane label is scrolled off. Height
        // is reserved so the transport doesn't shift as chords come and go (or before analysis lands).
        if (showChords) {
            Box(Modifier.fillMaxWidth().height(24.dp), contentAlignment = Alignment.Center) {
                val name = currentChord?.name(noteNaming).orEmpty()
                if (name.isNotEmpty()) {
                    Text(
                        name,
                        color = MaterialTheme.colorScheme.primary,
                        style = MaterialTheme.typography.titleMedium,
                    )
                }
            }
        }

        Spacer(Modifier.height(8.dp))

        // When the arrangement is armed and has playable steps, the Play button runs the sequence.
        val arrangeMode = arrangementActive && arrangement.any { st -> loops.any { it.id == st.loopId } }
        val haptics = LocalHapticFeedback.current
        // Full-width play control: tap starts plainly; a long-press starts with the count-in.
        Box(
            Modifier
                .fillMaxWidth()
                .clip(ControlShape)
                .background(MaterialTheme.colorScheme.primary)
                .combinedClickable(
                    onClick = {
                        when {
                            isPlaying -> viewModel.togglePlay() // stop (also cancels an arrangement)
                            arrangeMode -> viewModel.playArrangement()
                            else -> viewModel.togglePlay() // start the single loop
                        }
                    },
                    onLongClick = {
                        if (!isPlaying) {
                            haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                            if (arrangeMode) viewModel.playArrangement(withCountIn = true)
                            else viewModel.togglePlay(withCountIn = true)
                        }
                    },
                )
                .padding(vertical = 12.dp),
            contentAlignment = Alignment.Center,
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    if (isPlaying) Icons.Filled.Stop else Icons.Filled.PlayArrow,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onPrimary,
                )
                Spacer(Modifier.width(8.dp))
                Text(
                    stringResource(
                        when {
                            countingIn -> R.string.play_counting_in
                            isPlaying -> R.string.play_stop
                            arrangeMode -> R.string.play_arrangement
                            else -> R.string.play_play
                        },
                    ),
                    color = MaterialTheme.colorScheme.onPrimary,
                )
            }
        }

        Spacer(Modifier.height(8.dp))

        LoopChips(
            loops = loops,
            region = region,
            atLimit = loops.size >= MAX_SAVED_LOOPS,
            onLimit = notifyLoopLimit,
            canArrange = loops.isNotEmpty(),
            arrangeActive = arrangementActive,
            locked = arrangementActive,
            onLocked = notifyLocked,
            onArrange = { showArrange = true },
            onApply = viewModel::applySavedLoop,
            onSave = viewModel::saveCurrentLoop,
            onRename = viewModel::renameLoop,
            onDelete = viewModel::deleteLoop,
            onReorder = viewModel::reorderLoops,
        )

        Spacer(Modifier.height(8.dp))

        BeatControls(grid, detecting, speed, stretching, arrangementActive, notifyLocked, { showOptions = true }, viewModel)

        Spacer(Modifier.height(8.dp))
    }

    if (showArrange) {
        ArrangeSheet(
            steps = arrangement,
            loops = loops,
            active = arrangementActive,
            repeatWhole = arrangementRepeat,
            onAddStep = viewModel::addArrangementStep,
            onRemoveStep = viewModel::removeArrangementStep,
            onMoveStep = viewModel::moveArrangementStep,
            onSetRepeat = viewModel::setArrangementRepeat,
            onToggleActive = viewModel::setArrangementActive,
            onToggleRepeatWhole = viewModel::setArrangementRepeatWhole,
            onDismiss = { showArrange = false },
        )
    }

    if (showOptions) {
        OptionsSheet(
            countIn = countIn,
            bpm = grid.bpm,
            onSetCountIn = viewModel::setCountIn,
            clickThroughLoop = clickThroughLoop,
            onSetClickThroughLoop = viewModel::setClickThroughLoop,
            clickVolume = clickVolume,
            onSetClickVolume = viewModel::setClickVolume,
            clickPitch = clickPitch,
            onSetClickPitch = viewModel::setClickPitch,
            normalize = normalize,
            normalizeGainDb = normalizeGainDb,
            onSetNormalize = viewModel::setNormalize,
            showChords = showChords,
            onSetShowChords = viewModel::setShowChords,
            chordEditMode = chordEditMode,
            hasChords = (chords?.spans?.isNotEmpty() == true),
            onSetChordEditMode = viewModel::setChordEditMode,
            onReDetectChords = viewModel::reDetectChords,
            onDismiss = { showOptions = false },
        )
    }

    chordEdit?.let { (index, frac) ->
        ChordPickerDialog(
            current = chords?.spans?.getOrNull(index)?.chord,
            noteNaming = noteNaming,
            canMerge = index < (chords?.spans?.size ?: 0) - 1,
            onSet = { viewModel.setChordAt(index, it); chordEdit = null },
            onClear = { viewModel.setChordAt(index, Chord.NONE); chordEdit = null },
            onSplit = { viewModel.splitChordAt(index, frac); chordEdit = null },
            onMerge = { viewModel.mergeChordWithNext(index); chordEdit = null },
            onDismiss = { chordEdit = null },
        )
    }
}

@Composable
private fun LoopChips(
    loops: List<SavedLoop>,
    region: LoopRegion,
    atLimit: Boolean,
    onLimit: () -> Unit,
    canArrange: Boolean,
    arrangeActive: Boolean,
    locked: Boolean,
    onLocked: () -> Unit,
    onArrange: () -> Unit,
    onApply: (SavedLoop) -> Unit,
    onSave: (String) -> Unit,
    onRename: (String, String) -> Unit,
    onDelete: (String) -> Unit,
    onReorder: (List<String>) -> Unit,
) {
    var showSaveDialog by remember { mutableStateOf(false) }
    var renameTarget by remember { mutableStateOf<SavedLoop?>(null) }

    val chipScroll = rememberScrollState()
    val fadeWidth = with(LocalDensity.current) { CHIP_FADE.toPx() }

    // A newly saved loop appends to the row, which is often off screen once a track holds several,
    // so saving looked like it had done nothing. Each chip reports where it sits in the (unscrolled)
    // content, and the row scrolls whichever chip matters into view. The same bounds drive the drag
    // below, which needs to know how wide the neighbour it is passing is.
    val chipBounds = remember { mutableStateMapOf<String, IntRange>() }
    var scrollTarget by remember { mutableStateOf<String?>(null) }
    var knownIds by remember { mutableStateOf(emptySet<String>()) }

    val selectedId = loops.firstOrNull {
        abs(it.startFrac - region.startFrac) < 1e-3f && abs(it.endFrac - region.endFrac) < 1e-3f
    }?.id

    LaunchedEffect(loops) {
        val ids = loops.map { it.id }.toSet()
        chipBounds.keys.retainAll(ids)
        // A loop the row hasn't seen before is the one that was just saved.
        (ids - knownIds).firstOrNull()?.let { scrollTarget = it }
        knownIds = ids
    }
    // Recalling a loop from elsewhere — the arrangement, or a chip only half in view — should also
    // bring it into view. Tapping a fully visible chip resolves to a no-op below.
    LaunchedEffect(selectedId) { selectedId?.let { scrollTarget = it } }

    // ---- Drag to reorder ----
    // Long-press lifts a chip; moving it swaps past its neighbours as it goes, and releasing keeps
    // that order. Releasing without having moved is still the rename/delete menu, so the drag costs
    // no gesture and no extra affordance on the chip.
    val haptics = LocalHapticFeedback.current
    val spacingPx = with(LocalDensity.current) { CHIP_SPACING.toPx() }
    val autoScrollZone = with(LocalDensity.current) { AUTOSCROLL_ZONE.toPx() }
    var dragId by remember { mutableStateOf<String?>(null) }
    var dragOffset by remember { mutableFloatStateOf(0f) }
    var dragOrder by remember { mutableStateOf<List<String>>(emptyList()) }
    // Where the finger is across the visible row, which is what drives the auto-scroll. Scrolling
    // the content does not change it, so a finger held still keeps pulling at the same speed.
    var dragPointerX by remember { mutableFloatStateOf(0f) }
    // Armed by the first real movement: lifting a chip that already sits in the edge zone must not
    // start the row travelling on its own.
    var dragMoving by remember { mutableStateOf(false) }

    // While a chip is lifted the row renders from the live drag order; the committed list otherwise.
    val shown = if (dragId == null) loops else dragOrder.mapNotNull { id -> loops.find { it.id == id } }

    fun widthOf(id: String): Float =
        chipBounds[id]?.let { (it.last - it.first).toFloat() } ?: 0f

    // Step the dragged chip past every neighbour whose midpoint it has crossed, taking that
    // neighbour's width back out of the offset so the chip stays under the finger.
    fun settleDrag(id: String) {
        while (true) {
            val i = dragOrder.indexOf(id)
            if (dragOffset > 0 && i in 0 until dragOrder.lastIndex) {
                val step = widthOf(dragOrder[i + 1]) + spacingPx
                if (dragOffset <= step / 2f) return
                dragOrder = dragOrder.toMutableList().apply { add(i + 1, removeAt(i)) }
                dragOffset -= step
            } else if (dragOffset < 0 && i > 0) {
                val step = widthOf(dragOrder[i - 1]) + spacingPx
                if (-dragOffset <= step / 2f) return
                dragOrder = dragOrder.toMutableList().apply { add(i - 1, removeAt(i)) }
                dragOffset += step
            } else {
                return
            }
        }
    }

    // Holding the dragged chip near either end of the row pulls the row along, so a chip can be
    // taken somewhere off screen without letting go. Speed ramps up across the zone, so easing
    // towards the edge creeps and pressing right up to it travels fast. Whatever the row scrolls is
    // added back to the offset, keeping the chip under the finger, and swaps settle as it passes.
    LaunchedEffect(dragId) {
        if (dragId == null) return@LaunchedEffect
        while (true) {
            withFrameNanos { }
            val id = dragId ?: return@LaunchedEffect
            if (!dragMoving) continue
            val viewport = chipScroll.viewportSize.toFloat()
            val depth = when {
                dragPointerX > viewport - autoScrollZone -> dragPointerX - (viewport - autoScrollZone)
                dragPointerX < autoScrollZone -> dragPointerX - autoScrollZone
                else -> 0f
            }
            if (depth != 0f) {
                val ramp = (depth / autoScrollZone).coerceIn(-1f, 1f)
                dragOffset += chipScroll.scrollBy(ramp * AUTOSCROLL_MAX)
                settleDrag(id)
            }
        }
    }

    LaunchedEffect(scrollTarget) {
        val id = scrollTarget ?: return@LaunchedEffect
        // The chip may not have been placed yet when it is brand new, so wait for its bounds.
        val bounds = snapshotFlow { chipBounds[id] }.filterNotNull().first()
        val pad = fadeWidth.roundToInt()
        val viewport = chipScroll.viewportSize
        // Clear the fade as well as the edge, so the chip lands fully legible rather than half
        // dissolved into it.
        val target = when {
            bounds.first - pad < chipScroll.value -> bounds.first - pad
            bounds.last + pad > chipScroll.value + viewport -> bounds.last + pad - viewport
            else -> return@LaunchedEffect
        }
        chipScroll.animateScrollTo(target.coerceIn(0, chipScroll.maxValue))
    }

    Row(
        Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        // Chips take the remaining width and scroll sideways within it…
        Row(
            Modifier
                .weight(1f)
                // A chip cut flush at the edge reads as a rendering fault rather than as "there is
                // more this way", so the row fades out over its last few dp — the same trick as a
                // CSS mask-image gradient: paint the content, then multiply its alpha by a gradient
                // (DstIn), which needs the layer composited offscreen to have an alpha channel to
                // erase. Each edge only fades while there is actually something hidden past it.
                .graphicsLayer { compositingStrategy = CompositingStrategy.Offscreen }
                .drawWithContent {
                    drawContent()
                    if (chipScroll.canScrollBackward) {
                        drawRect(
                            brush = Brush.horizontalGradient(
                                listOf(Color.Transparent, Color.Black),
                                startX = 0f,
                                endX = fadeWidth,
                            ),
                            size = Size(fadeWidth, size.height),
                            blendMode = BlendMode.DstIn,
                        )
                    }
                    if (chipScroll.canScrollForward) {
                        drawRect(
                            brush = Brush.horizontalGradient(
                                listOf(Color.Black, Color.Transparent),
                                startX = size.width - fadeWidth,
                                endX = size.width,
                            ),
                            topLeft = Offset(size.width - fadeWidth, 0f),
                            size = Size(fadeWidth, size.height),
                            blendMode = BlendMode.DstIn,
                        )
                    }
                }
                // Dragging a chip drives the row itself (see the auto-scroll above), so hand-
                // scrolling is off for the duration — the two would otherwise fight over the same
                // finger. Programmatic scrolling is unaffected by this flag.
                .horizontalScroll(chipScroll, enabled = dragId == null),
            horizontalArrangement = Arrangement.spacedBy(CHIP_SPACING),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            // With Save pinned to the right, an empty row would leave it floating over blank space
            // with nothing to explain it. Name what the button is for until the first loop exists.
            if (loops.isEmpty()) {
                Text(
                    stringResource(R.string.loops_empty_hint),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                )
            }
            shown.forEach { loop ->
                // Keyed by loop, not by position: reordering during a drag must move the existing
                // chip, not hand a neighbour's slot a new loop. Without this the drag's pointer
                // handler restarts on the first swap and the rest of the gesture scrolls the row.
                key(loop.id) {
                LoopChip(
                    loop = loop,
                    // Highlight the loop whose span matches the current markers.
                    selected = loop.id == selectedId,
                    locked = locked,
                    dragging = loop.id == dragId,
                    dragOffset = if (loop.id == dragId) dragOffset else 0f,
                    onDragStart = {
                        dragOrder = loops.map { it.id }
                        dragId = loop.id
                        dragOffset = 0f
                        dragMoving = false
                        haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                    },
                    onDrag = { dx, pointerX ->
                        dragOffset += dx
                        dragMoving = true
                        // The chip's resting position plus how far it is held from it, plus where
                        // the finger sits on the chip, less the scroll: the finger across the row.
                        val restingX = chipBounds[loop.id]?.first?.toFloat() ?: 0f
                        dragPointerX = restingX + dragOffset + pointerX - chipScroll.value
                        settleDrag(loop.id)
                    },
                    onDragStop = { moved ->
                        if (moved) onReorder(dragOrder)
                        dragId = null
                        dragOffset = 0f
                        dragMoving = false
                    },
                    modifier = Modifier.onPlaced {
                        // Bounds are read while dragging, so record where the chip actually
                        // belongs in the row rather than where it is being held.
                        val x = it.positionInParent().x.roundToInt() -
                            (if (loop.id == dragId) dragOffset.roundToInt() else 0)
                        chipBounds[loop.id] = x..(x + it.size.width)
                    },
                    onLocked = onLocked,
                    onApply = { onApply(loop) },
                    onRename = { renameTarget = loop },
                    onDelete = { onDelete(loop.id) },
                )
                }
            }
        }
        // …while Save and Arrange stay pinned as icon-only buttons, so neither can scroll out of
        // reach once a track carries a full row of loops. They read as one pair of actions, so they
        // sit tight against each other — the 8dp of the outer row would drift them apart.
        Row(
            horizontalArrangement = Arrangement.spacedBy(0.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            PinnedLoopAction(
                icon = Icons.Filled.Add,
                contentDescription = stringResource(R.string.cd_save_loop),
                // Dimmed for two different reasons, each with its own explanation on tap: the
                // arrangement is armed, or the track already holds MAX_SAVED_LOOPS loops.
                dimmed = locked || atLimit,
                onClick = {
                    when {
                        locked -> onLocked()
                        atLimit -> onLimit()
                        else -> showSaveDialog = true
                    }
                },
            )
            // While the arrangement is armed Arrange fills brand-red with a white icon (like an
            // active loop chip); otherwise it's a plain icon on no background.
            if (canArrange) {
                PinnedLoopAction(
                    icon = ImageVector.vectorResource(R.drawable.ic_queue_music),
                    contentDescription = stringResource(R.string.cd_arrange),
                    active = arrangeActive,
                    onClick = onArrange,
                )
            }
        }
    }

    if (showSaveDialog) {
        LoopNameDialog(
            title = stringResource(R.string.save_loop_title),
            initial = defaultLoopName(loops),
            taken = loops.map { it.label },
            onConfirm = { onSave(it); showSaveDialog = false },
            onDismiss = { showSaveDialog = false },
        )
    }
    renameTarget?.let { target ->
        LoopNameDialog(
            title = stringResource(R.string.rename_loop_title),
            initial = target.label,
            // Keeping a loop's own name is not a clash, so it doesn't block the rename.
            taken = loops.filterNot { it.id == target.id }.map { it.label },
            onConfirm = { onRename(target.id, it); renameTarget = null },
            onDismiss = { renameTarget = null },
        )
    }
}

/**
 * The practice-arrangement editor: an ordered list of steps (each a saved loop played N times),
 * a palette of saved loops to append from, and a Play that runs the sequence. The same loop may
 * appear in several steps, so a step is removed by its own position, not by deleting the loop.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ArrangeSheet(
    steps: List<ArrangementStep>,
    loops: List<SavedLoop>,
    active: Boolean,
    repeatWhole: Boolean,
    onAddStep: (String) -> Unit,
    onRemoveStep: (String) -> Unit,
    onMoveStep: (String, Int) -> Unit,
    onSetRepeat: (String, Int) -> Unit,
    onToggleActive: (Boolean) -> Unit,
    onToggleRepeatWhole: (Boolean) -> Unit,
    onDismiss: () -> Unit,
) {
    val nameById = remember(loops) { loops.associate { it.id to it.label } }

    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(
            Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp)
                .padding(bottom = 32.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            // Arming the arrangement makes the main Play button run it instead of the single loop.
            // The toggle lives beside the header as a compact "enabled" switch.
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
                modifier = Modifier.fillMaxWidth(),
            ) {
                // The same glyph as the button that opens this sheet, so the two read as one place.
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        ImageVector.vectorResource(R.drawable.ic_queue_music),
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurface,
                    )
                    Spacer(Modifier.width(8.dp))
                    Text(
                        stringResource(R.string.arrange_title),
                        style = MaterialTheme.typography.titleLarge,
                    )
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        stringResource(R.string.arrange_enabled),
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Spacer(Modifier.width(8.dp))
                    Switch(
                        checked = active,
                        onCheckedChange = onToggleActive,
                        enabled = steps.isNotEmpty(),
                    )
                }
            }

            if (steps.isEmpty()) {
                Text(
                    stringResource(R.string.arrange_empty),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            } else {
                Column(
                    Modifier.heightIn(max = 260.dp).verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    steps.forEachIndexed { i, step ->
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(ControlShape)
                                .background(MaterialTheme.colorScheme.surfaceVariant),
                        ) {
                            Column {
                                IconButton(
                                    onClick = { onMoveStep(step.stepId, -1) },
                                    enabled = i > 0,
                                    modifier = Modifier.size(28.dp),
                                ) {
                                    Icon(
                                        Icons.Default.KeyboardArrowUp,
                                        stringResource(R.string.cd_move_up),
                                    )
                                }
                                IconButton(
                                    onClick = { onMoveStep(step.stepId, 1) },
                                    enabled = i < steps.lastIndex,
                                    modifier = Modifier.size(28.dp),
                                ) {
                                    Icon(
                                        Icons.Default.KeyboardArrowDown,
                                        stringResource(R.string.cd_move_down),
                                    )
                                }
                            }
                            Spacer(Modifier.width(4.dp))
                            Text(
                                nameById[step.loopId] ?: stringResource(R.string.arrange_deleted_loop),
                                Modifier.weight(1f),
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                            TextButton(onClick = { onSetRepeat(step.stepId, step.repeatCount - 1) }) {
                                Text(stringResource(R.string.button_decrement))
                            }
                            Text(
                                stringResource(R.string.arrange_repeat_count, step.repeatCount),
                                style = MaterialTheme.typography.titleSmall,
                            )
                            TextButton(onClick = { onSetRepeat(step.stepId, step.repeatCount + 1) }) {
                                Text(stringResource(R.string.button_increment))
                            }
                            IconButton(onClick = { onRemoveStep(step.stepId) }) {
                                Icon(Icons.Default.Close, stringResource(R.string.cd_remove_step))
                            }
                        }
                    }
                }
            }

            Text(
                stringResource(R.string.arrange_add_step),
                style = MaterialTheme.typography.labelMedium,
            )
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                loops.forEach { loop ->
                    AssistChip(
                        onClick = { onAddStep(loop.id) },
                        enabled = steps.size < MAX_ARRANGEMENT_STEPS,
                        label = { Text(loop.label, maxLines = 1) },
                        shape = ControlShape,
                    )
                }
            }

            Row(verticalAlignment = Alignment.CenterVertically) {
                Switch(checked = repeatWhole, onCheckedChange = onToggleRepeatWhole)
                Spacer(Modifier.width(8.dp))
                Text(stringResource(R.string.arrange_repeat_whole))
            }
        }
    }
}

/**
 * An icon-only action pinned to the right of the scrolling loop chips. Sized to the chip height so
 * the whole row lines up; [dimmed] marks it unavailable without removing it, and the click still
 * fires so the caller can explain why.
 */
@Composable
private fun PinnedLoopAction(
    icon: ImageVector,
    contentDescription: String,
    onClick: () -> Unit,
    active: Boolean = false,
    dimmed: Boolean = false,
) {
    Surface(
        onClick = onClick,
        shape = ControlShape,
        color = if (active) MaterialTheme.colorScheme.primary else Color.Transparent,
        contentColor = if (active) MaterialTheme.colorScheme.onPrimary
        else MaterialTheme.colorScheme.onSurface,
        modifier = Modifier.alpha(if (dimmed) LOCKED_ALPHA else 1f),
    ) {
        // Match the chip height (32dp) so it lines up with the loop chips beside it.
        Box(
            Modifier.height(32.dp).padding(horizontal = 6.dp),
            contentAlignment = Alignment.Center,
        ) {
            Icon(icon, contentDescription = contentDescription)
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun LoopChip(
    loop: SavedLoop,
    selected: Boolean,
    locked: Boolean,
    onLocked: () -> Unit,
    onApply: () -> Unit,
    onRename: () -> Unit,
    onDelete: () -> Unit,
    dragging: Boolean = false,
    dragOffset: Float = 0f,
    onDragStart: () -> Unit = {},
    onDrag: (dx: Float, pointerX: Float) -> Unit = { _, _ -> },
    onDragStop: (moved: Boolean) -> Unit = {},
    modifier: Modifier = Modifier,
) {
    var menu by remember { mutableStateOf(false) }
    // Only the active loop is fully coloured; the rest stay quiet.
    val bg = if (selected) MaterialTheme.colorScheme.secondaryContainer
    else MaterialTheme.colorScheme.surfaceVariant
    val fg = if (selected) MaterialTheme.colorScheme.onSecondaryContainer
    else MaterialTheme.colorScheme.onSurfaceVariant
    // A long-press that never moves is the menu, as it always was; one that moves is a drag. The
    // threshold keeps the tremor of a held finger from being read as an attempt to reorder.
    val slop = with(LocalDensity.current) { DRAG_SLOP.toPx() }
    var travelled by remember { mutableFloatStateOf(0f) }
    // pointerInput captures its lambdas once and keeps them for as long as its keys hold, so the
    // callbacks are read through rememberUpdatedState. Captured directly, a drag would start from
    // the loop order as it stood when the block began — i.e. before any earlier reorder.
    val startDrag by rememberUpdatedState(onDragStart)
    val drag by rememberUpdatedState(onDrag)
    val stopDrag by rememberUpdatedState(onDragStop)
    val notifyLocked by rememberUpdatedState(onLocked)
    Box(
        modifier
            .alpha(if (locked) LOCKED_ALPHA else 1f)
            // Lifted above its neighbours so it slides over them rather than under.
            .zIndex(if (dragging) 1f else 0f)
            .graphicsLayer { translationX = dragOffset },
    ) {
        Surface(
            shape = ControlShape,
            color = bg,
            contentColor = fg,
            // Raised while held, the one bit of feedback that says the chip is now movable.
            shadowElevation = if (dragging) 6.dp else 0.dp,
            // While the arrangement is armed, both tap and long-press just explain why rather than
            // applying/editing a loop that no longer affects what the Play button plays.
            modifier = Modifier
                .combinedClickable(onClick = { if (locked) onLocked() else onApply() })
                .pointerInput(loop.id, locked) {
                    detectDragGesturesAfterLongPress(
                        onDragStart = {
                            travelled = 0f
                            if (!locked) startDrag()
                        },
                        onDrag = { change, delta ->
                            change.consume()
                            if (!locked) {
                                travelled += abs(delta.x)
                                // The chip does not budge until the finger has clearly set off, so
                                // holding still — or shifting a little while reaching for the menu
                                // — leaves the row exactly as it was.
                                if (travelled > slop) drag(delta.x, change.position.x)
                            }
                        },
                        onDragEnd = {
                            when {
                                locked -> notifyLocked()
                                travelled > slop -> stopDrag(true)
                                // Held and let go without moving: the menu, as before.
                                else -> { stopDrag(false); menu = true }
                            }
                        },
                        onDragCancel = { if (!locked) stopDrag(travelled > slop) },
                    )
                },
        ) {
            Text(
                loop.label,
                style = MaterialTheme.typography.labelLarge,
                maxLines = 1,
                modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
            )
        }
        DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
            DropdownMenuItem(
                text = { Text(stringResource(R.string.action_rename)) },
                onClick = { menu = false; onRename() },
            )
            DropdownMenuItem(
                text = { Text(stringResource(R.string.action_delete)) },
                onClick = { menu = false; onDelete() },
            )
        }
    }
}

/**
 * The next free "Loop N" name. Counting from the loop total keeps the usual case ("Loop 4" on a
 * track holding three) while stepping over names already in use, so deleting and re-saving can't
 * mint a second loop with the same name.
 */
@Composable
private fun defaultLoopName(loops: List<SavedLoop>): String {
    val taken = loops.map { it.label.trim().lowercase() }.toSet()
    var n = loops.size + 1
    var name = stringResource(R.string.loop_default_name, n)
    while (name.lowercase() in taken) {
        n++
        name = stringResource(R.string.loop_default_name, n)
    }
    return name
}

@Composable
private fun LoopNameDialog(
    title: String,
    initial: String,
    taken: List<String>,
    onConfirm: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    var text by remember { mutableStateOf(initial) }
    // Two names that differ only in case or padding would be indistinguishable in the row, so
    // they count as the same name.
    val takenNames = remember(taken) { taken.map { it.trim().lowercase() }.toSet() }
    val trimmed = text.trim()
    val clash = trimmed.lowercase() in takenNames
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            OutlinedTextField(
                value = text,
                onValueChange = { text = it },
                singleLine = true,
                shape = ControlShape,
                isError = clash,
                supportingText = if (clash) {
                    { Text(stringResource(R.string.loop_name_taken)) }
                } else {
                    null
                },
                keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences),
            )
        },
        confirmButton = {
            TextButton(
                onClick = { onConfirm(text) },
                enabled = trimmed.isNotEmpty() && !clash,
            ) { Text(stringResource(R.string.action_save)) }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_cancel)) }
        },
    )
}

/**
 * Trim a speed to a compact label: 1.00 → "1", 0.80 → "0.8", 0.85 → "0.85". Strips the trailing
 * decimal separator too — which is locale-dependent (a comma in e.g. German), so trim both.
 */
private fun formatSpeed(speed: Float): String =
    "%.2f".format(speed).trimEnd('0').trimEnd('.', ',')

/**
 * A compact speed control shown as a text link with a speed icon (e.g. "Speed 0.8×"), tinted
 * when a slowdown is active. Tap opens a slider popup; long-press resets to 1× — mirroring the
 * loop chips' tap/long-press idiom. A spinner replaces the icon while a stretch computes.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun SpeedLink(
    speed: Float,
    stretching: Boolean,
    locked: Boolean,
    onLocked: () -> Unit,
    onSpeedChange: (Float) -> Unit,
) {
    var expanded by remember { mutableStateOf(false) }
    // Track the drag locally and only commit on release — each commit re-runs WSOLA off-thread,
    // so we don't want to fire on every pixel of the drag.
    var dragValue by remember { mutableStateOf<Float?>(null) }
    val shown = dragValue ?: speed
    val active = speed != 1f
    val haptic = LocalHapticFeedback.current
    val tint = if (active) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant

    Box(Modifier.alpha(if (locked) LOCKED_ALPHA else 1f)) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .combinedClickable(
                    // Speed doesn't affect arrangement playback, so lock it while Arrange is armed.
                    onClick = { if (locked) onLocked() else expanded = true },
                    onLongClick = {
                        if (locked) onLocked()
                        else if (active) {
                            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                            onSpeedChange(1f)
                        }
                    },
                )
                .padding(horizontal = 4.dp, vertical = 8.dp),
        ) {
            if (stretching) {
                CircularProgressIndicator(Modifier.size(16.dp), strokeWidth = 2.dp, color = tint)
                Spacer(Modifier.width(6.dp))
            }
            Text(
                stringResource(R.string.speed_label, formatSpeed(speed)),
                style = MaterialTheme.typography.labelLarge,
                color = tint,
                maxLines = 1,
            )
        }
        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            Column(Modifier.padding(horizontal = 16.dp).width(220.dp)) {
                Text(
                    stringResource(R.string.speed_popup_label, formatSpeed(shown)),
                    style = MaterialTheme.typography.labelMedium,
                )
                Slider(
                    value = shown,
                    onValueChange = { dragValue = it },
                    onValueChangeFinished = { dragValue?.let(onSpeedChange); dragValue = null },
                    valueRange = LoopPlayer.MIN_SPEED..LoopPlayer.MAX_SPEED,
                    steps = 19, // 0.5×..1.5× in 0.05 detents, so 1× is easy to land on
                )
            }
        }
    }
}

@Composable
private fun BeatControls(
    grid: BeatGridState,
    detecting: Boolean,
    speed: Float,
    stretching: Boolean,
    locked: Boolean,
    onLocked: () -> Unit,
    onOptions: () -> Unit,
    viewModel: LooperViewModel,
) {
    // With the arrangement armed, the Play button runs the sequence, so these grid/tempo controls no
    // longer change what's heard — dim them while it's enabled and let a tap explain why.
    val dim = Modifier.alpha(if (locked) LOCKED_ALPHA else 1f)
    Column(
        Modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        // Snap, Auto and Tap all act on the beat grid, so they share one outline: a segmented
        // group reads as a single control surface rather than as three neighbours. Options is not
        // one of them — it opens the track's settings sheet — so it sits apart, past a gap wide
        // enough to break the row, and as an icon rather than a fourth look-alike label. Its
        // staying lit while the other three dim under an armed arrangement is the same
        // distinction stated a second way.
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(24.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            MultiChoiceSegmentedButtonRow(Modifier.weight(1f).then(dim)) {
                SegmentedButton(
                    checked = grid.enabled,
                    onCheckedChange = { if (locked) onLocked() else viewModel.toggleSnap() },
                    shape = SegmentedButtonDefaults.itemShape(0, GRID_SEGMENTS, ControlShape),
                    label = { Text(stringResource(R.string.chip_snap), maxLines = 1) },
                )
                SegmentedButton(
                    checked = false,
                    onCheckedChange = { if (locked) onLocked() else viewModel.detectBeats() },
                    enabled = !detecting,
                    shape = SegmentedButtonDefaults.itemShape(1, GRID_SEGMENTS, ControlShape),
                    // The detect spinner goes in the segment's own icon slot, which is the space
                    // a checkmark would occupy — so the label doesn't shift when detection runs.
                    icon = {
                        if (detecting) {
                            CircularProgressIndicator(Modifier.size(14.dp), strokeWidth = 2.dp)
                        }
                    },
                    label = { Text(stringResource(R.string.chip_auto), maxLines = 1) },
                )
                SegmentedButton(
                    checked = false,
                    onCheckedChange = { if (locked) onLocked() else viewModel.tapTempo() },
                    shape = SegmentedButtonDefaults.itemShape(2, GRID_SEGMENTS, ControlShape),
                    label = { Text(stringResource(R.string.chip_tap), maxLines = 1) },
                )
            }
            // Tune, not a cog: these are this track's settings, not the app's.
            IconButton(onClick = onOptions) {
                Icon(
                    Icons.Default.Tune,
                    contentDescription = stringResource(R.string.cd_track_options),
                )
            }
        }

        // Bottom row: BPM stepper and speed control, each centred in its own half.
        Row(
            Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(Modifier.weight(1f), contentAlignment = Alignment.Center) {
                var octaveMenu by remember { mutableStateOf(false) }
                Row(dim, verticalAlignment = Alignment.CenterVertically) {
                    TextButton(onClick = { if (locked) onLocked() else viewModel.nudgeBpm(-1f) }) {
                        Text(stringResource(R.string.button_decrement))
                    }
                    // Tap the BPM to halve or double it — the octave fix for when auto-detect lands
                    // a factor of two off, which the ±1 nudges can't reach.
                    Box {
                        Text(
                            stringResource(R.string.bpm_value, grid.bpm.roundToInt()),
                            style = MaterialTheme.typography.titleSmall,
                            modifier = Modifier
                                .clip(ControlShape)
                                .clickable { if (locked) onLocked() else octaveMenu = true }
                                .padding(horizontal = 6.dp, vertical = 2.dp),
                        )
                        DropdownMenu(expanded = octaveMenu, onDismissRequest = { octaveMenu = false }) {
                            DropdownMenuItem(
                                text = { Text(stringResource(R.string.menu_double_tempo)) },
                                onClick = { viewModel.doubleTempo(); octaveMenu = false },
                            )
                            DropdownMenuItem(
                                text = { Text(stringResource(R.string.menu_halve_tempo)) },
                                onClick = { viewModel.halveTempo(); octaveMenu = false },
                            )
                        }
                    }
                    TextButton(onClick = { if (locked) onLocked() else viewModel.nudgeBpm(1f) }) {
                        Text(stringResource(R.string.button_increment))
                    }
                }
            }
            Box(Modifier.weight(1f), contentAlignment = Alignment.Center) {
                SpeedLink(speed, stretching, locked, onLocked, viewModel::setSpeed)
            }
        }
    }
}

/**
 * The track Options bottom sheet. Tabbed so more per-track option groups can slot in later; to add
 * one, append a title to [tabs] and a branch to the `when`.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun OptionsSheet(
    countIn: CountInState,
    bpm: Float,
    onSetCountIn: (beatsPerBar: Int, bars: Int) -> Unit,
    clickThroughLoop: Boolean,
    onSetClickThroughLoop: (Boolean) -> Unit,
    clickVolume: Float,
    onSetClickVolume: (Float) -> Unit,
    clickPitch: ClickPitch,
    onSetClickPitch: (ClickPitch) -> Unit,
    normalize: NormalizeMode,
    normalizeGainDb: Float,
    onSetNormalize: (NormalizeMode) -> Unit,
    showChords: Boolean,
    onSetShowChords: (Boolean) -> Unit,
    chordEditMode: Boolean,
    hasChords: Boolean,
    onSetChordEditMode: (Boolean) -> Unit,
    onReDetectChords: () -> Unit,
    onDismiss: () -> Unit,
) {
    ModalBottomSheet(onDismissRequest = onDismiss) {
        var tab by remember { mutableStateOf(0) }
        val tabs = listOf(
            R.string.options_tab_click,
            R.string.options_tab_audio,
            R.string.options_tab_chords,
        )
        PrimaryTabRow(selectedTabIndex = tab) {
            tabs.forEachIndexed { i, title ->
                Tab(
                    selected = tab == i,
                    onClick = { tab = i },
                    text = { Text(stringResource(title)) },
                )
            }
        }
        Column(
            Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp)
                .padding(top = 16.dp, bottom = 32.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            when (tab) {
                0 -> ClickTab(
                    countIn, bpm, onSetCountIn,
                    clickThroughLoop, onSetClickThroughLoop,
                    clickVolume, onSetClickVolume,
                    clickPitch, onSetClickPitch,
                )
                1 -> AudioTab(normalize, normalizeGainDb, onSetNormalize)
                2 -> ChordsTab(showChords, onSetShowChords, chordEditMode, hasChords, onSetChordEditMode, onReDetectChords)
            }
        }
    }
}

/** Inline banner shown while chord editing, so it can be turned off without reopening Options. */
@Composable
private fun ChordEditBar(onDone: () -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = 4.dp)
            .clip(ControlShape)
            .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.15f))
            .padding(start = 12.dp, end = 4.dp, top = 2.dp, bottom = 2.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            stringResource(R.string.chord_edit_bar),
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.weight(1f),
        )
        TextButton(onClick = onDone) { Text(stringResource(R.string.action_done)) }
    }
}

/** Pick a chord for a lane span: root × quality, plus clear / split / merge actions. */
@Composable
private fun ChordPickerDialog(
    current: Chord?,
    noteNaming: NoteNaming,
    canMerge: Boolean,
    onSet: (Chord) -> Unit,
    onClear: () -> Unit,
    onSplit: () -> Unit,
    onMerge: () -> Unit,
    onDismiss: () -> Unit,
) {
    val roots = rootNames(noteNaming)
    var root by remember { mutableStateOf(current?.root ?: 0) }
    var quality by remember {
        mutableStateOf(current?.quality?.takeIf { it != Quality.NONE } ?: Quality.MAJ)
    }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.chord_picker_title)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(stringResource(R.string.chord_root), style = MaterialTheme.typography.labelLarge)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    roots.forEachIndexed { i, name ->
                        FilterChip(
                            selected = root == i,
                            onClick = { root = i },
                            label = { Text(name) },
                            shape = ControlShape,
                        )
                    }
                }
                Text(
                    stringResource(R.string.chord_quality),
                    style = MaterialTheme.typography.labelLarge,
                )
                FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    listOf(
                        Quality.MAJ to R.string.quality_maj,
                        Quality.MIN to R.string.quality_min,
                        Quality.MAJ7 to R.string.quality_maj7,
                        Quality.MIN7 to R.string.quality_min7,
                    ).forEach { (q, lbl) ->
                        FilterChip(
                            selected = quality == q,
                            onClick = { quality = q },
                            label = { Text(stringResource(lbl)) },
                            shape = ControlShape,
                        )
                    }
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    TextButton(onClick = onClear) { Text(stringResource(R.string.action_clear)) }
                    TextButton(onClick = onSplit) { Text(stringResource(R.string.action_split)) }
                    if (canMerge) {
                        TextButton(onClick = onMerge) { Text(stringResource(R.string.action_merge)) }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = { onSet(Chord(root, quality)) }) {
                Text(stringResource(R.string.action_set))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_cancel)) }
        },
    )
}

/** Chord-lane settings: show/hide the lane, turn on editing, and re-detect (discarding hand edits). */
@Composable
private fun ChordsTab(
    showChords: Boolean,
    onSetShowChords: (Boolean) -> Unit,
    editMode: Boolean,
    hasChords: Boolean,
    onSetEditMode: (Boolean) -> Unit,
    onReDetect: () -> Unit,
) {
    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.weight(1f)) {
            Text(
                stringResource(R.string.chords_show_title),
                style = MaterialTheme.typography.titleSmall,
            )
            Text(
                stringResource(R.string.chords_show_body),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Switch(checked = showChords, onCheckedChange = onSetShowChords)
    }
    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.weight(1f)) {
            Text(
                stringResource(R.string.chords_edit_title),
                style = MaterialTheme.typography.titleSmall,
            )
            Text(
                stringResource(R.string.chords_edit_body),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Switch(checked = editMode, onCheckedChange = onSetEditMode, enabled = showChords)
    }
    Text(
        stringResource(R.string.chords_persist_note),
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
    TextButton(onClick = onReDetect, enabled = showChords && hasChords) {
        Text(stringResource(R.string.chords_redetect))
    }
}

/**
 * Everything the click does: whether it carries on through the loop, how loud and how high it
 * sounds, and the meter and length of the count-in that starts it.
 *
 * One tab rather than two, because it is one sound. The count-in is per track (it describes the
 * song's meter); the level, pitch and through-loop switch are global (they describe the player's
 * habit) — which is why the through-loop switch sits above the divider with them, and the meter
 * pickers below it.
 */
@Composable
private fun ClickTab(
    state: CountInState,
    bpm: Float,
    onSet: (beatsPerBar: Int, bars: Int) -> Unit,
    throughLoop: Boolean,
    onSetThroughLoop: (Boolean) -> Unit,
    volume: Float,
    onSetVolume: (Float) -> Unit,
    pitch: ClickPitch,
    onSetPitch: (ClickPitch) -> Unit,
) {
    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.weight(1f)) {
            Text(
                stringResource(R.string.click_through_title),
                style = MaterialTheme.typography.titleSmall,
            )
            Text(
                stringResource(R.string.click_through_body),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Switch(checked = throughLoop, onCheckedChange = onSetThroughLoop)
    }

    Text(
        stringResource(R.string.click_volume_label, (volume * 100).roundToInt()),
        style = MaterialTheme.typography.labelLarge,
    )
    Slider(
        value = volume,
        onValueChange = onSetVolume,
        valueRange = 0f..1f,
        modifier = Modifier.fillMaxWidth(),
    )
    // Says what zero means before the user has to work it out from the silence.
    if (volume <= 0f) {
        Text(
            stringResource(R.string.click_volume_muted),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }

    Text(stringResource(R.string.click_pitch_label), style = MaterialTheme.typography.labelLarge)
    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        listOf(
            ClickPitch.LOW to R.string.click_pitch_low,
            ClickPitch.MID to R.string.click_pitch_mid,
            ClickPitch.HIGH to R.string.click_pitch_high,
        ).forEach { (p, label) ->
            FilterChip(
                selected = pitch == p,
                onClick = { onSetPitch(p) },
                label = { Text(stringResource(label)) },
                shape = ControlShape,
            )
        }
    }

    HorizontalDivider()

    Text(
        stringResource(R.string.countin_intro, bpm.roundToInt()),
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
    Text(stringResource(R.string.countin_beats_per_bar), style = MaterialTheme.typography.labelLarge)
    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        listOf(2, 3, 4, 6).forEach { n ->
            FilterChip(
                selected = state.beatsPerBar == n,
                onClick = { onSet(n, state.bars) },
                label = { Text(n.toString()) },
                shape = ControlShape,
            )
        }
    }
    Text(stringResource(R.string.countin_bars), style = MaterialTheme.typography.labelLarge)
    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        listOf(1, 2).forEach { n ->
            FilterChip(
                selected = state.bars == n,
                onClick = { onSet(state.beatsPerBar, n) },
                label = { Text(n.toString()) },
                shape = ControlShape,
            )
        }
    }
}

/**
 * Audio settings: how this track's playback level is normalised. The imported file is never
 * rewritten — the boost is applied on the way to the speaker, so it is free to change or undo.
 */
@Composable
private fun AudioTab(
    mode: NormalizeMode,
    gainDb: Float,
    onSet: (NormalizeMode) -> Unit,
) {
    // One line per mode, each led by its own name, so the two are easy to tell apart at a glance.
    Text(
        stringResource(R.string.audio_intro),
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
    ModeDescription(
        stringResource(R.string.audio_peak_name),
        stringResource(R.string.audio_peak_desc),
    )
    ModeDescription(
        stringResource(R.string.audio_loudness_name),
        stringResource(R.string.audio_loudness_desc),
    )
    Text(stringResource(R.string.audio_normalize_label), style = MaterialTheme.typography.labelLarge)
    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        listOf(
            NormalizeMode.OFF to R.string.normalize_off,
            NormalizeMode.PEAK to R.string.audio_peak_name,
            NormalizeMode.LOUDNESS to R.string.audio_loudness_name,
        ).forEach { (m, label) ->
            FilterChip(
                selected = mode == m,
                onClick = { onSet(m) },
                label = { Text(stringResource(label)) },
                shape = ControlShape,
            )
        }
    }
    // Say what the chosen mode actually does to *this* track. Without this, a Peak normalize that
    // finds no headroom looks like a broken button rather than an honest "nothing to gain here".
    val boost = stringResource(R.string.audio_boost_value, gainDb)
    Text(
        when {
            mode == NormalizeMode.OFF -> stringResource(R.string.audio_status_off)
            gainDb < 0.5f && mode == NormalizeMode.PEAK ->
                stringResource(R.string.audio_status_peak_no_headroom)

            gainDb < 0.5f -> stringResource(R.string.audio_status_at_target)
            else -> stringResource(R.string.audio_status_boosting, boost)
        },
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
}

/**
 * One normalize mode explained: its name in bold, then what it does. The separating space is added
 * here rather than carried by the description — aapt trims leading whitespace off a resource, so a
 * translated string can't hold one.
 */
@Composable
private fun ModeDescription(mode: String, description: String) {
    Text(
        buildAnnotatedString {
            withStyle(SpanStyle(fontWeight = FontWeight.Bold)) { append(mode) }
            append(" ")
            append(description)
        },
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
}

@Composable
private fun TimeLabel(label: String, ms: Long) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(label, style = MaterialTheme.typography.labelSmall)
        Text(formatMs(ms), style = MaterialTheme.typography.titleMedium)
    }
}

private fun currentFileName(state: LooperUiState): String? = when (state) {
    is LooperUiState.Loading -> state.fileName
    is LooperUiState.Loaded -> state.fileName
    else -> null
}

private fun queryDisplayName(context: android.content.Context, uri: android.net.Uri): String? =
    runCatching {
        context.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { c ->
            if (c.moveToFirst()) c.getString(0) else null
        }
    }.getOrNull()
        // A file: uri has no provider to ask, and the name is right there in the path. Only
        // "open with" from an older file manager gets here — the document picker always answers.
        ?: uri.lastPathSegment?.substringAfterLast('/')?.takeIf { it.isNotBlank() }

private fun formatMs(ms: Long): String {
    val totalSec = ms / 1000
    val m = totalSec / 60
    val s = totalSec % 60
    val tenths = (ms % 1000) / 100
    return "%d:%02d.%d".format(m, s, tenths)
}
