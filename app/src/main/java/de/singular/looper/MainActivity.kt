package de.singular.looper

import android.os.Bundle
import android.provider.OpenableColumns
import androidx.activity.ComponentActivity
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
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material.icons.filled.HelpOutline
import androidx.compose.material.icons.filled.LibraryMusic
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.MyLocation
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import kotlin.math.abs
import kotlin.math.roundToInt
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import de.singular.looper.audio.DecodedAudio
import de.singular.looper.library.LibraryTrack
import de.singular.looper.library.SavedLoop
import de.singular.looper.ui.LoopWaveform

// Brand colour used for buttons, chips, and the marker accents.
private val BrandPrimary = Color(0xFFA62120)

private val LooperColors = darkColorScheme(
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
            MaterialTheme(colorScheme = LooperColors) {
                Surface(modifier = Modifier.fillMaxSize()) {
                    LooperScreen()
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
    var menuOpen by remember { mutableStateOf(false) }
    var showHelp by remember { mutableStateOf(false) }
    val context = androidx.compose.ui.platform.LocalContext.current

    val picker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument(),
    ) { uri ->
        // We copy the file into our own storage on import, so a one-time read grant is enough —
        // no persistable URI permission needed.
        if (uri != null) viewModel.importAndOpen(uri, queryDisplayName(context, uri))
    }
    val openPicker = { picker.launch(arrayOf("audio/*")) }

    if (state is LooperUiState.Empty) {
        val library by viewModel.libraryTracks.collectAsStateWithLifecycle()
        EmptyScreen(
            library,
            onImport = { openPicker() },
            onOpen = viewModel::open,
            onDelete = viewModel::deleteTrack,
        )
        return
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = currentFileName(state) ?: "Rubber Ring",
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                },
                actions = {
                    IconButton(onClick = { menuOpen = true }) {
                        Icon(Icons.Default.MoreVert, contentDescription = "Menu")
                    }
                    DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                        if (state is LooperUiState.Loaded) {
                            DropdownMenuItem(
                                text = { Text("Library") },
                                leadingIcon = { Icon(Icons.Default.LibraryMusic, contentDescription = null) },
                                onClick = { menuOpen = false; viewModel.closeTrack() },
                            )
                        }
                        DropdownMenuItem(
                            text = { Text("Import file") },
                            leadingIcon = { Icon(Icons.Default.FolderOpen, contentDescription = null) },
                            onClick = { menuOpen = false; openPicker() },
                        )
                        if (state is LooperUiState.Loaded) {
                            DropdownMenuItem(
                                text = { Text("Follow playhead") },
                                leadingIcon = { Icon(Icons.Default.MyLocation, contentDescription = null) },
                                trailingIcon = {
                                    if (following) Icon(Icons.Default.Check, contentDescription = null)
                                },
                                onClick = { viewModel.toggleFollowPlayhead(); menuOpen = false },
                            )
                        }
                        DropdownMenuItem(
                            text = { Text("Quick help") },
                            leadingIcon = { Icon(Icons.Default.HelpOutline, contentDescription = null) },
                            onClick = { menuOpen = false; showHelp = true },
                        )
                    }
                },
            )
        },
    ) { innerPadding ->
        Box(
            Modifier.fillMaxSize().padding(innerPadding).padding(16.dp),
            contentAlignment = Alignment.Center,
        ) {
            when (val s = state) {
                is LooperUiState.Loading -> Column(horizontalAlignment = Alignment.CenterHorizontally) {
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

    if (showHelp) {
        QuickHelpDialog(onDismiss = { showHelp = false })
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
private fun EmptyScreen(
    library: List<LibraryTrack>,
    onImport: () -> Unit,
    onOpen: (LibraryTrack) -> Unit,
    onDelete: (LibraryTrack) -> Unit,
) {
    var deleteTarget by remember { mutableStateOf<LibraryTrack?>(null) }

    Column(
        // This screen renders outside the Scaffold, so apply system-bar insets ourselves
        // (edge-to-edge is on) — otherwise the button slides under the status bar.
        Modifier.fillMaxSize().safeDrawingPadding().padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        // With no library yet, centre the welcome block vertically; otherwise pin it to the top
        // and let the list fill the space below.
        if (library.isEmpty()) Spacer(Modifier.weight(1f))

        // App icon in a rounded tile matching the launcher icon.
        Box(
            Modifier.size(96.dp).clip(RoundedCornerShape(22.dp)).background(Color(0xFF1A1A1A)),
            contentAlignment = Alignment.Center,
        ) {
            Image(
                painter = painterResource(R.drawable.ic_launcher_foreground),
                contentDescription = null,
                modifier = Modifier.fillMaxSize(),
            )
        }
        Spacer(Modifier.height(12.dp))
        Text("Rubber Ring", style = MaterialTheme.typography.headlineMedium)
        Spacer(Modifier.height(8.dp))
        Text(
            "Pick an audio file to mark and loop a section.",
            style = MaterialTheme.typography.bodyMedium,
            textAlign = TextAlign.Center,
        )
        Spacer(Modifier.height(20.dp))
        Button(onClick = onImport, shape = ControlShape) { Text("Select audio file") }
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
                    "Remove \"${target.displayName}\" and its saved loops from your library? " +
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
}

@Composable
private fun LibraryRow(track: LibraryTrack, onClick: () -> Unit, onDelete: () -> Unit) {
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
                    track.displayName,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    style = MaterialTheme.typography.bodyLarge,
                )
                if (track.durationMs > 0) {
                    Text(formatMs(track.durationMs), style = MaterialTheme.typography.labelSmall)
                }
            }
            Box {
                IconButton(onClick = { menu = true }) {
                    Icon(Icons.Default.MoreVert, contentDescription = "Track options")
                }
                DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
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

            // Anchor the grid to the current loop start — the manual phase fix when auto lands
            // the beats a fraction off.
            FilterChip(
                selected = false,
                onClick = { viewModel.setDownbeatToStart() },
                label = { Text("Downbeat = start") },
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
