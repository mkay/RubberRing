package de.singular.looper

import android.os.Bundle
import android.provider.OpenableColumns
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
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
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemGestures
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.FormatListNumbered
import androidx.compose.material.icons.filled.RadioButtonUnchecked
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material.icons.filled.HelpOutline
import androidx.compose.material.icons.filled.LibraryMusic
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.MyLocation
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Restore
import androidx.compose.material.icons.filled.Save
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material.icons.filled.ZoomIn
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
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
import androidx.compose.material3.ModalDrawerSheet
import androidx.compose.material3.ModalNavigationDrawer
import androidx.compose.material3.NavigationDrawerItem
import androidx.compose.material3.NavigationDrawerItemDefaults
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.PrimaryTabRow
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
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
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.res.vectorResource
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import kotlin.math.abs
import kotlin.math.roundToInt
import androidx.core.view.WindowCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import de.singular.looper.audio.DecodedAudio
import de.singular.looper.audio.Gain
import de.singular.looper.audio.LoopPlayer
import de.singular.looper.audio.Chord
import de.singular.looper.audio.NormalizeMode
import de.singular.looper.audio.Quality
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

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            val viewModel: LooperViewModel = viewModel()
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
                    LooperScreen(viewModel)
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LooperScreen(viewModel: LooperViewModel = viewModel()) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val following by viewModel.followPlayhead.collectAsStateWithLifecycle()
    val keepScreenOn by viewModel.keepScreenOn.collectAsStateWithLifecycle()
    val saveZoom by viewModel.saveZoom.collectAsStateWithLifecycle()
    val edgeInset by viewModel.edgeInset.collectAsStateWithLifecycle()
    val themeMode by viewModel.themeMode.collectAsStateWithLifecycle()
    val library by viewModel.libraryTracks.collectAsStateWithLifecycle()
    val recents by viewModel.recentTracks.collectAsStateWithLifecycle()
    var showHelp by remember { mutableStateOf(false) }
    var showSettings by remember { mutableStateOf(false) }
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
            BackupResult.EXPORTED -> "Library backed up"
            BackupResult.RESTORED -> "Library restored"
            BackupResult.FAILED -> "Backup failed — check the file and try again"
            null -> return@LaunchedEffect
        }
        Toast.makeText(context, message, Toast.LENGTH_LONG).show()
        viewModel.consumeBackupResult()
    }

    if (confirmRestore) {
        AlertDialog(
            onDismissRequest = { confirmRestore = false },
            title = { Text("Restore backup?") },
            text = {
                Text(
                    "This replaces your entire current library — all imported tracks and saved " +
                        "loops — with the contents of the backup. This can't be undone.",
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    confirmRestore = false
                    importLauncher.launch(
                        arrayOf("application/zip", "application/octet-stream", "application/x-zip-compressed"),
                    )
                }) { Text("Choose backup") }
            },
            dismissButton = {
                TextButton(onClick = { confirmRestore = false }) { Text("Cancel") }
            },
        )
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
            onToggleFollow = viewModel::toggleFollowPlayhead,
            onToggleKeepScreenOn = viewModel::toggleKeepScreenOn,
            onToggleSaveZoom = viewModel::toggleSaveZoom,
            onToggleEdgeInset = viewModel::toggleEdgeInset,
            onSelectTheme = viewModel::setThemeMode,
            onBackup = startBackup,
            onRestore = startRestore,
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
                                Icon(Icons.Default.Close, contentDescription = "Cancel selection")
                            }
                        } else {
                            IconButton(onClick = { scope.launch { drawerState.open() } }) {
                                Icon(Icons.Default.Menu, contentDescription = "Open menu")
                            }
                        }
                    },
                    title = {
                        // On the library home the wordmark banner already names the app, so the
                        // corner title steps aside there; it stays on a loaded track (its filename).
                        Text(
                            text = when {
                                selecting -> "${selection.size} selected"
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
                                Icon(Icons.Default.Delete, contentDescription = "Delete selected")
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
                                    contentDescription = "Screen kept on",
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
                            CircularProgressIndicator()
                            Spacer(Modifier.height(8.dp))
                            Text("Decoding…")
                        }

                        is LooperUiState.Loaded -> LoadedContent(s.audio, viewModel)

                        is LooperUiState.Error -> Text(
                            "Error: ${s.message}",
                            textAlign = TextAlign.Center,
                            color = MaterialTheme.colorScheme.error,
                        )

                        is LooperUiState.Empty -> Unit
                    }
                }
            }
        }
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
            title = { Text(if (single != null) "Delete track?" else "Delete ${doomed.size} tracks?") },
            text = {
                Text(
                    if (single != null) {
                        "Remove \"${single.name}\" and its saved loops from your library? " +
                            "The original file on your device is not affected."
                    } else {
                        "Remove these ${doomed.size} tracks and their saved loops from your " +
                            "library? The original files on your device are not affected."
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
                ) { Text("Delete") }
            },
            dismissButton = {
                TextButton(onClick = { pendingDelete = emptyList() }) { Text("Cancel") }
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
            title = { Text("Screen stays on") },
            text = {
                Text(
                    "“Keep screen on” is enabled, so the display won't dim or lock while a track " +
                        "is open. Handy for practice, but it uses more battery.",
                )
            },
            confirmButton = {
                TextButton(onClick = { showKeepAwakeInfo = false; showSettings = true }) {
                    Text("Settings")
                }
            },
            dismissButton = {
                TextButton(onClick = { showKeepAwakeInfo = false }) { Text("Got it") }
            },
        )
    }
}

/**
 * Full-screen settings, reached from the drawer: the playback toggles, the appearance (theme)
 * choice, and the library backup/restore actions. [onClose] backs out to the previous screen.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SettingsScreen(
    following: Boolean,
    keepScreenOn: Boolean,
    saveZoom: Boolean,
    edgeInset: Boolean,
    themeMode: ThemeMode,
    onToggleFollow: () -> Unit,
    onToggleKeepScreenOn: () -> Unit,
    onToggleSaveZoom: () -> Unit,
    onToggleEdgeInset: () -> Unit,
    onSelectTheme: (ThemeMode) -> Unit,
    onBackup: () -> Unit,
    onRestore: () -> Unit,
    onClose: () -> Unit,
) {
    BackHandler(onBack = onClose)
    Scaffold(
        topBar = {
            TopAppBar(
                navigationIcon = {
                    IconButton(onClick = onClose) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                title = { Text("Settings") },
            )
        },
    ) { innerPadding ->
        Column(
            Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .verticalScroll(rememberScrollState())
                .padding(bottom = 16.dp),
        ) {
            DrawerSectionLabel("Playback")
            SettingSwitchRow("Follow playhead", Icons.Default.MyLocation, following, onToggleFollow)
            SettingSwitchRow(
                "Keep screen on",
                ImageVector.vectorResource(R.drawable.ic_brightness_alert),
                keepScreenOn,
                onToggleKeepScreenOn,
            )
            SettingSwitchRow("Save zoom level", Icons.Default.ZoomIn, saveZoom, onToggleSaveZoom)
            SettingSwitchRow(
                "Add padding to waveform",
                ImageVector.vectorResource(R.drawable.ic_fit_width),
                edgeInset,
                onToggleEdgeInset,
            )

            DrawerSectionLabel("Appearance")
            ThemeModeChips(
                mode = themeMode,
                onSelect = onSelectTheme,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
            )

            DrawerSectionLabel("Library")
            BackupChoiceRow(
                icon = Icons.Default.Save,
                title = "Back up library",
                subtitle = "Save all tracks and loops to a file",
                onClick = onBackup,
            )
            BackupChoiceRow(
                icon = Icons.Default.Restore,
                title = "Restore library",
                subtitle = "Replace everything from a backup file",
                onClick = onRestore,
            )
        }
    }
}

/**
 * The left navigation drawer, shared by the library and play views: primary destinations
 * (Library — hidden when [showLibrary] is false, i.e. already in the library — Import, and
 * Settings), a recents list, and Quick help pinned to the bottom.
 */
@Composable
private fun LooperDrawer(
    showLibrary: Boolean,
    recents: List<LibraryTrack>,
    onLibrary: () -> Unit,
    onImport: () -> Unit,
    onSettings: () -> Unit,
    onOpenRecent: (LibraryTrack) -> Unit,
    onQuickHelp: () -> Unit,
) {
    // Take 4/5 of the screen width, leaving a strip of dimmed scrim on the right to tap-to-close.
    ModalDrawerSheet(Modifier.fillMaxWidth(0.8f)) {
        Column(Modifier.fillMaxSize()) {
            Text(
                "Rubber Ring",
                style = MaterialTheme.typography.titleLarge,
                modifier = Modifier.padding(start = 28.dp, top = 20.dp, bottom = 12.dp),
            )

            val itemPadding = Modifier.padding(NavigationDrawerItemDefaults.ItemPadding)
            if (showLibrary) {
                NavigationDrawerItem(
                    label = { Text("Library") },
                    icon = { Icon(Icons.Default.LibraryMusic, contentDescription = null) },
                    selected = false,
                    onClick = onLibrary,
                    modifier = itemPadding,
                )
            }
            NavigationDrawerItem(
                label = { Text("Import new file") },
                icon = { Icon(Icons.Default.FolderOpen, contentDescription = null) },
                selected = false,
                onClick = onImport,
                modifier = itemPadding,
            )
            NavigationDrawerItem(
                label = { Text("Settings") },
                icon = { Icon(Icons.Default.Settings, contentDescription = null) },
                selected = false,
                onClick = onSettings,
                modifier = itemPadding,
            )

            if (recents.isNotEmpty()) {
                DrawerSectionLabel("Recent")
                recents.forEach { track ->
                    NavigationDrawerItem(
                        label = {
                            Text(track.name, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        },
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
                label = { Text("Quick help") },
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
            ThemeMode.SYSTEM to "System",
            ThemeMode.LIGHT to "Light",
            ThemeMode.DARK to "Dark",
        )
        ThemeMode.entries.forEach { m ->
            FilterChip(
                selected = mode == m,
                onClick = { onSelect(m) },
                label = { Text(label.getValue(m)) },
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

@Composable
private fun QuickHelpDialog(onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Quick help") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                HelpItem(
                    "Move a marker",
                    "Long-press a marker's grip tab (or its line) until it grabs, then drag it. " +
                        "The short hold prevents accidental nudges.",
                )
                HelpItem(
                    "Move the playhead",
                    "Tap anywhere in open space to jump the playhead there, or drag near the " +
                        "playhead line to scrub it.",
                )
                HelpItem(
                    "Start with a count-in",
                    "Long-press Play to hear a count-in before the audio comes in; a normal tap " +
                        "starts straight away. Set its meter and length under Options.",
                )
                HelpItem(
                    "Zoom & scroll",
                    "Pinch with two fingers to zoom the waveform in and out. When zoomed in, " +
                        "drag in open space to scroll, or use the minimap strip at the bottom.",
                )
                HelpItem(
                    "Save a loop",
                    "Set the start and end markers, then tap Save to store the region under a " +
                        "name. Saved loops belong to the current track.",
                )
                HelpItem(
                    "Recall a loop",
                    "Tap a saved loop to jump its markers back into place. Long-press it for " +
                        "rename and delete options.",
                )
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text("Got it") }
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
            "a play-along looper",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )
        Spacer(Modifier.height(20.dp))
        Text(
            "Pick an audio file to mark and loop a section.",
            style = MaterialTheme.typography.bodyMedium,
            textAlign = TextAlign.Center,
        )
        Spacer(Modifier.height(20.dp))
        Button(onClick = onImport, shape = ControlShape) { Text("Import new file") }
        Spacer(Modifier.height(10.dp))
        Text(
            "Supports MP3, AAC (M4A), FLAC, WAV, and OGG.",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )

        if (library.isEmpty()) {
            Spacer(Modifier.weight(1f))
        } else {
            Spacer(Modifier.height(24.dp))
            Text(
                "Library",
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
            title = "Rename track",
            initial = target.name,
            onConfirm = { onRename(target, it); renameTarget = null },
            onDismiss = { renameTarget = null },
        )
    }
}

/** One selectable option (icon + title + subtitle) in the backup/restore section. */
@Composable
private fun BackupChoiceRow(
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
                val info = buildList {
                    if (track.durationMs > 0) add(formatMs(track.durationMs))
                    val loops = track.savedLoops.size
                    if (loops > 0) add(if (loops == 1) "1 Loop" else "$loops Loops")
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
                        contentDescription = if (selected) "Selected" else "Not selected",
                        tint = if (selected) MaterialTheme.colorScheme.onSecondaryContainer
                        else MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            } else {
                Box {
                    IconButton(onClick = { menu = true }) {
                        Icon(Icons.Default.MoreVert, contentDescription = "Track options")
                    }
                    DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                        DropdownMenuItem(
                            text = { Text("Rename") },
                            leadingIcon = { Icon(Icons.Default.Edit, contentDescription = null) },
                            onClick = { menu = false; onRename() },
                        )
                        DropdownMenuItem(
                            text = { Text("Delete") },
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
        Toast.makeText(context, "Turn off Arrange to use this", Toast.LENGTH_SHORT).show()
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
                    "Long-press a marker to move it",
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
            TimeLabel("Start", (region.startFrac * durationMs).toLong())
            TimeLabel("Loop", ((region.endFrac - region.startFrac) * durationMs).toLong())
            TimeLabel("End", (region.endFrac * durationMs).toLong())
        }

        Spacer(Modifier.height(8.dp))

        // At-a-glance readout of the chord under the playhead — the lane on the waveform shows the
        // whole timeline; this stays visible when zoomed in and a lane label is scrolled off. Height
        // is reserved so the transport doesn't shift as chords come and go (or before analysis lands).
        if (showChords) {
            Box(Modifier.fillMaxWidth().height(24.dp), contentAlignment = Alignment.Center) {
                val name = currentChord?.name.orEmpty()
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
                    when {
                        countingIn -> "Counting in…"
                        isPlaying -> "Stop"
                        arrangeMode -> "Play arrangement"
                        else -> "Play"
                    },
                    color = MaterialTheme.colorScheme.onPrimary,
                )
            }
        }

        Spacer(Modifier.height(8.dp))

        LoopChips(
            loops = loops,
            region = region,
            canAdd = loops.size < MAX_SAVED_LOOPS,
            canArrange = loops.isNotEmpty(),
            arrangeActive = arrangementActive,
            locked = arrangementActive,
            onLocked = notifyLocked,
            onArrange = { showArrange = true },
            onApply = viewModel::applySavedLoop,
            onSave = viewModel::saveCurrentLoop,
            onRename = viewModel::renameLoop,
            onDelete = viewModel::deleteLoop,
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
    canAdd: Boolean,
    canArrange: Boolean,
    arrangeActive: Boolean,
    locked: Boolean,
    onLocked: () -> Unit,
    onArrange: () -> Unit,
    onApply: (SavedLoop) -> Unit,
    onSave: (String) -> Unit,
    onRename: (String, String) -> Unit,
    onDelete: (String) -> Unit,
) {
    var showSaveDialog by remember { mutableStateOf(false) }
    var renameTarget by remember { mutableStateOf<SavedLoop?>(null) }

    Row(
        Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        // Chips take the remaining width and scroll sideways within it…
        Row(
            Modifier.weight(1f).horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            loops.forEach { loop ->
                // Highlight the loop whose span matches the current markers.
                val selected = abs(loop.startFrac - region.startFrac) < 1e-3f &&
                    abs(loop.endFrac - region.endFrac) < 1e-3f
                LoopChip(
                    loop = loop,
                    selected = selected,
                    locked = locked,
                    onLocked = onLocked,
                    onApply = { onApply(loop) },
                    onRename = { renameTarget = loop },
                    onDelete = { onDelete(loop.id) },
                )
            }
            if (canAdd) {
                AssistChip(
                    onClick = { if (locked) onLocked() else showSaveDialog = true },
                    label = { Text("＋ Save") },
                    shape = ControlShape,
                    modifier = Modifier.alpha(if (locked) LOCKED_ALPHA else 1f),
                )
            }
        }
        // …the Arrange action stays pinned as an icon-only button so it can't scroll out of reach
        // and takes minimal width. While the arrangement is armed it fills brand-red with a white
        // icon (like an active loop chip); otherwise it's a plain icon on no background.
        if (canArrange) {
            Surface(
                onClick = onArrange,
                shape = ControlShape,
                color = if (arrangeActive) MaterialTheme.colorScheme.primary else Color.Transparent,
                contentColor = if (arrangeActive) MaterialTheme.colorScheme.onPrimary
                else MaterialTheme.colorScheme.onSurface,
            ) {
                // Match the chip height (32dp) so it lines up with the loop/save chips beside it.
                Box(
                    Modifier.height(32.dp).padding(horizontal = 6.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(Icons.Filled.FormatListNumbered, contentDescription = "Arrange")
                }
            }
        }
    }

    if (showSaveDialog) {
        LoopNameDialog(
            title = "Save loop",
            initial = "Loop ${loops.size + 1}",
            onConfirm = { onSave(it); showSaveDialog = false },
            onDismiss = { showSaveDialog = false },
        )
    }
    renameTarget?.let { target ->
        LoopNameDialog(
            title = "Rename loop",
            initial = target.label,
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
                Text("Arrange", style = MaterialTheme.typography.titleLarge)
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        "enabled",
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
                    "Add saved loops below to build a practice sequence.",
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
                                ) { Icon(Icons.Default.KeyboardArrowUp, "Move up") }
                                IconButton(
                                    onClick = { onMoveStep(step.stepId, 1) },
                                    enabled = i < steps.lastIndex,
                                    modifier = Modifier.size(28.dp),
                                ) { Icon(Icons.Default.KeyboardArrowDown, "Move down") }
                            }
                            Spacer(Modifier.width(4.dp))
                            Text(
                                nameById[step.loopId] ?: "(deleted)",
                                Modifier.weight(1f),
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                            TextButton(onClick = { onSetRepeat(step.stepId, step.repeatCount - 1) }) { Text("−") }
                            Text("×${step.repeatCount}", style = MaterialTheme.typography.titleSmall)
                            TextButton(onClick = { onSetRepeat(step.stepId, step.repeatCount + 1) }) { Text("+") }
                            IconButton(onClick = { onRemoveStep(step.stepId) }) {
                                Icon(Icons.Default.Close, "Remove step")
                            }
                        }
                    }
                }
            }

            Text("Add step", style = MaterialTheme.typography.labelMedium)
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
                Text("Repeat whole sequence")
            }
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
) {
    var menu by remember { mutableStateOf(false) }
    // Only the active loop is fully coloured; the rest stay quiet.
    val bg = if (selected) MaterialTheme.colorScheme.secondaryContainer
    else MaterialTheme.colorScheme.surfaceVariant
    val fg = if (selected) MaterialTheme.colorScheme.onSecondaryContainer
    else MaterialTheme.colorScheme.onSurfaceVariant
    Box(Modifier.alpha(if (locked) LOCKED_ALPHA else 1f)) {
        Surface(
            shape = ControlShape,
            color = bg,
            contentColor = fg,
            // While the arrangement is armed, both tap and long-press just explain why rather than
            // applying/editing a loop that no longer affects what the Play button plays.
            modifier = Modifier.combinedClickable(
                onClick = { if (locked) onLocked() else onApply() },
                onLongClick = { if (locked) onLocked() else menu = true },
            ),
        ) {
            Text(
                loop.label,
                style = MaterialTheme.typography.labelLarge,
                maxLines = 1,
                modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
            )
        }
        DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
            DropdownMenuItem(text = { Text("Rename") }, onClick = { menu = false; onRename() })
            DropdownMenuItem(text = { Text("Delete") }, onClick = { menu = false; onDelete() })
        }
    }
}

@Composable
private fun LoopNameDialog(
    title: String,
    initial: String,
    onConfirm: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    var text by remember { mutableStateOf(initial) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            OutlinedTextField(
                value = text,
                onValueChange = { text = it },
                singleLine = true,
                shape = ControlShape,
                keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences),
            )
        },
        confirmButton = { TextButton(onClick = { onConfirm(text) }) { Text("Save") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
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
                "Speed ${formatSpeed(speed)}×",
                style = MaterialTheme.typography.labelLarge,
                color = tint,
                maxLines = 1,
            )
        }
        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            Column(Modifier.padding(horizontal = 16.dp).width(220.dp)) {
                Text("Speed  ${formatSpeed(shown)}×", style = MaterialTheme.typography.labelMedium)
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
        // Four equal-width chips. Snap is a toggle; Auto/Tap set the tempo; Options opens the
        // track's extra settings (count-in for now). A centred label keeps them looking uniform.
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            val chipLabel: @Composable (String) -> Unit = { t ->
                Text(t, modifier = Modifier.fillMaxWidth(), textAlign = TextAlign.Center)
            }
            FilterChip(
                selected = grid.enabled,
                onClick = { if (locked) onLocked() else viewModel.toggleSnap() },
                label = { chipLabel("Snap") },
                shape = ControlShape,
                modifier = Modifier.weight(1f).then(dim),
            )
            FilterChip(
                selected = false,
                enabled = !detecting,
                onClick = { if (locked) onLocked() else viewModel.detectBeats() },
                label = {
                    Row(
                        Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.Center,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        if (detecting) {
                            CircularProgressIndicator(Modifier.size(14.dp), strokeWidth = 2.dp)
                            Spacer(Modifier.width(4.dp))
                        }
                        Text("Auto")
                    }
                },
                shape = ControlShape,
                modifier = Modifier.weight(1f).then(dim),
            )
            FilterChip(
                selected = false,
                onClick = { if (locked) onLocked() else viewModel.tapTempo() },
                label = { chipLabel("Tap") },
                shape = ControlShape,
                modifier = Modifier.weight(1f).then(dim),
            )
            FilterChip(
                selected = false,
                onClick = onOptions,
                label = { chipLabel("Options") },
                shape = ControlShape,
                modifier = Modifier.weight(1f),
            )
        }

        // Bottom row: BPM stepper and speed control, each centred in its own half.
        Row(
            Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(Modifier.weight(1f), contentAlignment = Alignment.Center) {
                var octaveMenu by remember { mutableStateOf(false) }
                Row(dim, verticalAlignment = Alignment.CenterVertically) {
                    TextButton(onClick = { if (locked) onLocked() else viewModel.nudgeBpm(-1f) }) { Text("−") }
                    // Tap the BPM to halve or double it — the octave fix for when auto-detect lands
                    // a factor of two off, which the ±1 nudges can't reach.
                    Box {
                        Text(
                            "${grid.bpm.roundToInt()} BPM",
                            style = MaterialTheme.typography.titleSmall,
                            modifier = Modifier
                                .clip(ControlShape)
                                .clickable { if (locked) onLocked() else octaveMenu = true }
                                .padding(horizontal = 6.dp, vertical = 2.dp),
                        )
                        DropdownMenu(expanded = octaveMenu, onDismissRequest = { octaveMenu = false }) {
                            DropdownMenuItem(
                                text = { Text("Double tempo") },
                                onClick = { viewModel.doubleTempo(); octaveMenu = false },
                            )
                            DropdownMenuItem(
                                text = { Text("Halve tempo") },
                                onClick = { viewModel.halveTempo(); octaveMenu = false },
                            )
                        }
                    }
                    TextButton(onClick = { if (locked) onLocked() else viewModel.nudgeBpm(1f) }) { Text("+") }
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
        val tabs = listOf("Count-in", "Audio", "Chords")
        PrimaryTabRow(selectedTabIndex = tab) {
            tabs.forEachIndexed { i, title ->
                Tab(selected = tab == i, onClick = { tab = i }, text = { Text(title) })
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
                0 -> CountInTab(countIn, bpm, onSetCountIn)
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
            "Editing chords — tap to relabel or split; drag a boundary to move",
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.weight(1f),
        )
        TextButton(onClick = onDone) { Text("Done") }
    }
}

/** Pick a chord for a lane span: root × quality, plus clear / split / merge actions. */
@Composable
private fun ChordPickerDialog(
    current: Chord?,
    canMerge: Boolean,
    onSet: (Chord) -> Unit,
    onClear: () -> Unit,
    onSplit: () -> Unit,
    onMerge: () -> Unit,
    onDismiss: () -> Unit,
) {
    val roots = listOf("C", "C#", "D", "D#", "E", "F", "F#", "G", "G#", "A", "A#", "B")
    var root by remember { mutableStateOf(current?.root ?: 0) }
    var quality by remember {
        mutableStateOf(current?.quality?.takeIf { it != Quality.NONE } ?: Quality.MAJ)
    }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Chord") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("Root", style = MaterialTheme.typography.labelLarge)
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
                Text("Quality", style = MaterialTheme.typography.labelLarge)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    listOf(
                        Quality.MAJ to "Maj",
                        Quality.MIN to "Min",
                        Quality.MAJ7 to "Maj7",
                        Quality.MIN7 to "m7",
                    ).forEach { (q, lbl) ->
                        FilterChip(
                            selected = quality == q,
                            onClick = { quality = q },
                            label = { Text(lbl) },
                            shape = ControlShape,
                        )
                    }
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    TextButton(onClick = onClear) { Text("Clear") }
                    TextButton(onClick = onSplit) { Text("Split") }
                    if (canMerge) TextButton(onClick = onMerge) { Text("Merge") }
                }
            }
        },
        confirmButton = { TextButton(onClick = { onSet(Chord(root, quality)) }) { Text("Set") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
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
            Text("Show chord lane", style = MaterialTheme.typography.titleSmall)
            Text(
                "Detect and display chords over the waveform. Turn off to hide them entirely.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Switch(checked = showChords, onCheckedChange = onSetShowChords)
    }
    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.weight(1f)) {
            Text("Edit chords", style = MaterialTheme.typography.titleSmall)
            Text(
                "Tap a chord in the lane to relabel or split it; long-press a boundary to drag it (snaps to the beat).",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Switch(checked = editMode, onCheckedChange = onSetEditMode, enabled = showChords)
    }
    Text(
        "Your edits are saved with the track and restored when you reopen it.",
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
    TextButton(onClick = onReDetect, enabled = showChords && hasChords) {
        Text("Re-detect chords (discard edits)")
    }
}

/** Count-in settings: how it's triggered, plus the meter and length pickers. */
@Composable
private fun CountInTab(
    state: CountInState,
    bpm: Float,
    onSet: (beatsPerBar: Int, bars: Int) -> Unit,
) {
    Text(
        "Long-press Play to start with a count-in: clicks at the track tempo " +
            "(${bpm.roundToInt()} BPM) before playback begins.",
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
    Text("Beats per bar", style = MaterialTheme.typography.labelLarge)
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
    Text("Bars", style = MaterialTheme.typography.labelLarge)
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
        "Boost a track that was mixed too quietly.",
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
    ModeDescription(
        "Peak",
        " raises it until its loudest moment is as loud as it can get, without altering the sound.",
    )
    ModeDescription(
        "Loudness",
        " goes by average level instead — it can lift a track Peak has nothing left to give, at " +
            "the cost of easing the sharpest peaks back a little.",
    )
    Text("Normalize", style = MaterialTheme.typography.labelLarge)
    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        listOf(
            NormalizeMode.OFF to "Off",
            NormalizeMode.PEAK to "Peak",
            NormalizeMode.LOUDNESS to "Loudness",
        ).forEach { (m, label) ->
            FilterChip(
                selected = mode == m,
                onClick = { onSet(m) },
                label = { Text(label) },
                shape = ControlShape,
            )
        }
    }
    // Say what the chosen mode actually does to *this* track. Without this, a Peak normalize that
    // finds no headroom looks like a broken button rather than an honest "nothing to gain here".
    val boost = "%+.1f dB".format(gainDb)
    Text(
        when {
            mode == NormalizeMode.OFF -> "Playing at the track's own level."
            gainDb < 0.5f && mode == NormalizeMode.PEAK ->
                "This track already peaks at full volume, so Peak has no headroom to use. " +
                    "Try Loudness."
            gainDb < 0.5f -> "This track is already at the target level."
            else -> "Boosting this track by $boost."
        },
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
}

/** One normalize mode explained: its name in bold, then what it does. */
@Composable
private fun ModeDescription(mode: String, description: String) {
    Text(
        buildAnnotatedString {
            withStyle(SpanStyle(fontWeight = FontWeight.Bold)) { append(mode) }
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

private fun formatMs(ms: Long): String {
    val totalSec = ms / 1000
    val m = totalSec / 60
    val s = totalSec % 60
    val tenths = (ms % 1000) / 100
    return "%d:%02d.%d".format(m, s, tenths)
}
