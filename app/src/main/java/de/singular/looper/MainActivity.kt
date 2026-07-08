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
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material.icons.filled.HelpOutline
import androidx.compose.material.icons.filled.LibraryMusic
import androidx.compose.material.icons.filled.Lightbulb
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.MyLocation
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Restore
import androidx.compose.material.icons.filled.Save
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
import androidx.compose.material3.ModalDrawerSheet
import androidx.compose.material3.ModalNavigationDrawer
import androidx.compose.material3.NavigationDrawerItem
import androidx.compose.material3.NavigationDrawerItemDefaults
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.painterResource
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
import de.singular.looper.library.LibraryTrack
import de.singular.looper.library.SavedLoop
import de.singular.looper.ui.LoopWaveform
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
    val themeMode by viewModel.themeMode.collectAsStateWithLifecycle()
    val library by viewModel.libraryTracks.collectAsStateWithLifecycle()
    val recents by viewModel.recentTracks.collectAsStateWithLifecycle()
    var showHelp by remember { mutableStateOf(false) }
    var showSettings by remember { mutableStateOf(false) }
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
            themeMode = themeMode,
            onToggleFollow = viewModel::toggleFollowPlayhead,
            onToggleKeepScreenOn = viewModel::toggleKeepScreenOn,
            onToggleSaveZoom = viewModel::toggleSaveZoom,
            onSelectTheme = viewModel::setThemeMode,
            onBackup = startBackup,
            onRestore = startRestore,
            onClose = { showSettings = false },
        )
        return
    }

    val inLibrary = state is LooperUiState.Empty

    // The library is the home view. Backing out of the play view returns there rather than
    // quitting; a swipe-back with the drawer open just closes the drawer. In the library with the
    // drawer closed the handler is disabled, so back keeps its default behaviour of exiting.
    BackHandler(enabled = drawerState.isOpen || !inLibrary) {
        if (drawerState.isOpen) {
            scope.launch { drawerState.close() }
        } else {
            viewModel.closeTrack()
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
                TopAppBar(
                    navigationIcon = {
                        IconButton(onClick = { scope.launch { drawerState.open() } }) {
                            Icon(Icons.Default.Menu, contentDescription = "Open menu")
                        }
                    },
                    title = {
                        Text(
                            text = currentFileName(state) ?: "Rubber Ring",
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    },
                )
            },
        ) { innerPadding ->
            when (val s = state) {
                is LooperUiState.Empty -> LibraryContent(
                    library = library,
                    onImport = { openPicker() },
                    onOpen = viewModel::open,
                    onRename = viewModel::renameTrack,
                    onDelete = viewModel::deleteTrack,
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
    themeMode: ThemeMode,
    onToggleFollow: () -> Unit,
    onToggleKeepScreenOn: () -> Unit,
    onToggleSaveZoom: () -> Unit,
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
            SettingSwitchRow("Keep screen on", Icons.Default.Lightbulb, keepScreenOn, onToggleKeepScreenOn)
            SettingSwitchRow("Save zoom level", Icons.Default.ZoomIn, saveZoom, onToggleSaveZoom)

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

@Composable
private fun LibraryContent(
    library: List<LibraryTrack>,
    onImport: () -> Unit,
    onOpen: (LibraryTrack) -> Unit,
    onRename: (LibraryTrack, String) -> Unit,
    onDelete: (LibraryTrack) -> Unit,
    modifier: Modifier = Modifier,
) {
    var deleteTarget by remember { mutableStateOf<LibraryTrack?>(null) }
    var renameTarget by remember { mutableStateOf<LibraryTrack?>(null) }

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
        Spacer(Modifier.height(12.dp))
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
                        onClick = { onOpen(track) },
                        onRename = { renameTarget = track },
                        onDelete = { deleteTarget = track },
                    )
                }
            }
        }
    }

    deleteTarget?.let { target ->
        AlertDialog(
            onDismissRequest = { deleteTarget = null },
            title = { Text("Delete track?") },
            text = {
                Text(
                    "Remove \"${target.name}\" and its saved loops from your library? " +
                        "The original file on your device is not affected.",
                )
            },
            confirmButton = {
                TextButton(onClick = { onDelete(target); deleteTarget = null }) { Text("Delete") }
            },
            dismissButton = {
                TextButton(onClick = { deleteTarget = null }) { Text("Cancel") }
            },
        )
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
    onClick: () -> Unit,
    onRename: () -> Unit,
    onDelete: () -> Unit,
) {
    var menu by remember { mutableStateOf(false) }
    Surface(
        onClick = onClick,
        shape = ControlShape,
        color = MaterialTheme.colorScheme.surfaceVariant,
        modifier = Modifier.fillMaxWidth(),
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

@Composable
private fun LoadedContent(audio: DecodedAudio, viewModel: LooperViewModel) {
    val region by viewModel.region.collectAsStateWithLifecycle()
    val playhead by viewModel.playhead.collectAsStateWithLifecycle()
    val grid by viewModel.grid.collectAsStateWithLifecycle()
    val detecting by viewModel.detecting.collectAsStateWithLifecycle()
    val isPlaying by viewModel.isPlaying.collectAsStateWithLifecycle()
    val loops by viewModel.savedLoops.collectAsStateWithLifecycle()
    val following by viewModel.followPlayhead.collectAsStateWithLifecycle()
    val durationMs = audio.durationMs

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
            initialZoom = initialViewport.zoom,
            initialOffset = initialViewport.offset,
            onViewportChange = viewModel::setViewport,
            onStartChange = viewModel::setStart,
            onEndChange = viewModel::setEnd,
            onSeek = viewModel::seek,
            modifier = Modifier.fillMaxWidth().weight(1f),
        )

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

        Button(
            onClick = viewModel::togglePlay,
            modifier = Modifier.fillMaxWidth(),
            shape = ControlShape,
        ) {
            Icon(
                if (isPlaying) Icons.Filled.Stop else Icons.Filled.PlayArrow,
                contentDescription = null,
            )
            Spacer(Modifier.width(8.dp))
            Text(if (isPlaying) "Stop" else "Play")
        }

        Spacer(Modifier.height(8.dp))

        LoopChips(
            loops = loops,
            region = region,
            canAdd = loops.size < MAX_SAVED_LOOPS,
            onApply = viewModel::applySavedLoop,
            onSave = viewModel::saveCurrentLoop,
            onRename = viewModel::renameLoop,
            onDelete = viewModel::deleteLoop,
        )

        Spacer(Modifier.height(8.dp))

        BeatControls(grid, detecting, viewModel)

        Spacer(Modifier.height(8.dp))
    }
}

@Composable
private fun LoopChips(
    loops: List<SavedLoop>,
    region: LoopRegion,
    canAdd: Boolean,
    onApply: (SavedLoop) -> Unit,
    onSave: (String) -> Unit,
    onRename: (String, String) -> Unit,
    onDelete: (String) -> Unit,
) {
    var showSaveDialog by remember { mutableStateOf(false) }
    var renameTarget by remember { mutableStateOf<SavedLoop?>(null) }

    Row(
        Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
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
                onApply = { onApply(loop) },
                onRename = { renameTarget = loop },
                onDelete = { onDelete(loop.id) },
            )
        }
        if (canAdd) {
            AssistChip(
                onClick = { showSaveDialog = true },
                label = { Text("＋ Save") },
                shape = ControlShape,
            )
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

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun LoopChip(
    loop: SavedLoop,
    selected: Boolean,
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
    Box {
        Surface(
            shape = ControlShape,
            color = bg,
            contentColor = fg,
            modifier = Modifier.combinedClickable(onClick = onApply, onLongClick = { menu = true }),
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

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun BeatControls(grid: BeatGridState, detecting: Boolean, viewModel: LooperViewModel) {
    Column(
        Modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        FlowRow(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
            itemVerticalAlignment = Alignment.CenterVertically,
        ) {
            // Snap is a real toggle; Auto and Tap are momentary actions. Styling them all as
            // chips keeps the grid controls reading as one tidy row.
            FilterChip(
                selected = grid.enabled,
                onClick = { viewModel.toggleSnap() },
                label = { Text("Snap") },
                shape = ControlShape,
            )

            FilterChip(
                selected = false,
                enabled = !detecting,
                onClick = { viewModel.detectBeats() },
                label = { Text("Auto") },
                leadingIcon = if (detecting) {
                    { CircularProgressIndicator(Modifier.size(14.dp), strokeWidth = 2.dp) }
                } else null,
                shape = ControlShape,
            )

            FilterChip(
                selected = false,
                onClick = { viewModel.tapTempo() },
                label = { Text("Tap") },
                shape = ControlShape,
            )

            // Octave fix: auto-detect most often errs by a factor of two, which ±1 nudges can't
            // reach — one tap halves or doubles the tempo instead.
            FilterChip(
                selected = false,
                onClick = { viewModel.halveTempo() },
                label = { Text("½×") },
                shape = ControlShape,
            )
            FilterChip(
                selected = false,
                onClick = { viewModel.doubleTempo() },
                label = { Text("2×") },
                shape = ControlShape,
            )
        }

        // BPM stepper, centred below the grid controls.
        Row(verticalAlignment = Alignment.CenterVertically) {
            TextButton(onClick = { viewModel.nudgeBpm(-1f) }) { Text("−") }
            Text("${grid.bpm.roundToInt()} BPM", style = MaterialTheme.typography.titleSmall)
            TextButton(onClick = { viewModel.nudgeBpm(1f) }) { Text("+") }
        }
    }
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
