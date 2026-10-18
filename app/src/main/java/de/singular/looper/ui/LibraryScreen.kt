// SPDX-License-Identifier: GPL-3.0-only

package de.singular.looper.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Sort
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.RadioButtonUnchecked
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.outlined.StarOutline
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import de.singular.looper.R
import de.singular.looper.formatMs
import de.singular.looper.library.LibrarySort
import de.singular.looper.library.LibraryTrack
import de.singular.looper.library.TrackQuery

/**
 * Every track in the library, in the order the user picked, with rename and delete.
 *
 * The home screen shows only the few most recent tracks under its welcome block; this is where
 * the rest live once there are more than fit there. Pulling the list down past its top opens a
 * search field (see [PullToReveal]), which narrows the list as words are typed.
 *
 * [library] is listed in [sort] order, narrowed by [query]. The search is hoisted because Back
 * closes it, and the back chain lives a level up. Shown over an open track, [returnTo] names it
 * in a strip across the top that goes back to it.
 */
@Composable
fun LibraryScreen(
    library: List<LibraryTrack>,
    sort: LibrarySort,
    selection: Set<String>,
    searchOpen: Boolean,
    query: String,
    onOpenSearch: () -> Unit,
    onQueryChange: (String) -> Unit,
    onCloseSearch: () -> Unit,
    onOpen: (LibraryTrack) -> Unit,
    onToggleSelect: (LibraryTrack) -> Unit,
    onRename: (LibraryTrack) -> Unit,
    onDelete: (LibraryTrack) -> Unit,
    onToggleStar: (LibraryTrack) -> Unit,
    modifier: Modifier = Modifier,
    returnTo: String? = null,
    onReturn: () -> Unit = {},
) {
    val selecting = selection.isNotEmpty()
    val hasTracks = library.isNotEmpty()
    val trackQuery = TrackQuery.of(query)
    val tracks = remember(library, sort, trackQuery) {
        sort.sort(library).filter { trackQuery?.matches(it) ?: true }
    }
    Column(modifier.fillMaxSize()) {
        if (returnTo != null && !selecting) {
            ReturnStrip(returnTo, onReturn)
            RowDivider()
        }
        // Out of the way while picking: the contextual bar has the top of the screen then.
        if (searchOpen && !selecting) SearchField(query, onQueryChange, onCloseSearch)
        PullToReveal(
            enabled = hasTracks && !searchOpen && !selecting,
            onReveal = onOpenSearch,
            hint = { Icon(Icons.Default.Search, contentDescription = null) },
            modifier = Modifier.weight(1f),
        ) {
            if (tracks.isEmpty()) {
                Hint(
                    stringResource(
                        if (hasTracks) R.string.search_no_match else R.string.library_empty,
                    ),
                )
            } else {
                LazyColumn(Modifier.fillMaxSize()) {
                    itemsIndexed(tracks, key = { _, track -> track.id }) { index, track ->
                        TrackRow(
                            track = track,
                            shaded = index % 2 == 0,
                            selected = track.id in selection,
                            selecting = selecting,
                            // While selecting, a tap picks rather than opens — one meaning per tap.
                            onClick = { if (selecting) onToggleSelect(track) else onOpen(track) },
                            onLongClick = { onToggleSelect(track) },
                            onRename = { onRename(track) },
                            onDelete = { onDelete(track) },
                            onToggleStar = { onToggleStar(track) },
                        )
                    }
                }
            }
        }
    }
}

/**
 * One track as a flat row: its name, then its length and saved-loop count. Lists shade every other
 * row ([shaded]) rather than drawing dividers, which lets a long list read as a list.
 *
 * The home screen's recents pass only [onClick], and a starred track there shows its star as a
 * mark; the library adds the star as a button, selection, and the row's menu. While selecting, the
 * star and the menu would compete with the selection's own actions, so they give way to a tick that
 * says whether this track is in the batch.
 */
@Composable
internal fun TrackRow(
    track: LibraryTrack,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    shaded: Boolean = false,
    selected: Boolean = false,
    selecting: Boolean = false,
    onLongClick: (() -> Unit)? = null,
    onRename: (() -> Unit)? = null,
    onDelete: (() -> Unit)? = null,
    onToggleStar: (() -> Unit)? = null,
) {
    val haptic = LocalHapticFeedback.current
    val colors = MaterialTheme.colorScheme
    Row(
        modifier
            .fillMaxWidth()
            .heightIn(min = 56.dp)
            .background(
                when {
                    selected -> colors.secondaryContainer
                    // A wash of the text colour, so it reads as a shade in either theme.
                    shaded -> colors.onSurface.copy(alpha = SHADE_ALPHA)
                    else -> Color.Transparent
                },
            )
            .combinedClickable(
                onClick = onClick,
                onLongClick = onLongClick?.let { longClick ->
                    {
                        // Confirm the grab in the hand — the gesture has no on-screen affordance.
                        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                        longClick()
                    }
                },
            )
            .padding(start = 16.dp, end = if (onRename != null || selecting) 4.dp else 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f).padding(vertical = 8.dp)) {
            Text(
                track.name,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                style = MaterialTheme.typography.bodyLarge,
            )
            val loops = track.savedLoops.size
            val loopCount =
                if (loops > 0) pluralStringResource(R.plurals.track_loop_count, loops, loops) else null
            val info = buildList {
                if (track.durationMs > 0) add(formatMs(track.durationMs))
                if (loopCount != null) add(loopCount)
            }.joinToString(" | ")
            if (info.isNotEmpty()) {
                Text(
                    info,
                    style = MaterialTheme.typography.labelSmall,
                    color = colors.onSurfaceVariant,
                )
            }
        }
        when {
            selecting -> Box(Modifier.padding(12.dp)) {
                Icon(
                    if (selected) Icons.Default.CheckCircle else Icons.Default.RadioButtonUnchecked,
                    contentDescription = stringResource(
                        if (selected) R.string.cd_selected else R.string.cd_not_selected,
                    ),
                    tint = if (selected) colors.onSecondaryContainer else colors.onSurfaceVariant,
                )
            }

            else -> {
                if (onToggleStar != null) {
                    StarButton(track.starred, onToggleStar)
                } else if (track.starred) {
                    Icon(
                        Icons.Default.Star,
                        contentDescription = stringResource(R.string.cd_starred),
                        tint = colors.onSurface,
                        modifier = Modifier.size(20.dp),
                    )
                }
                if (onRename != null && onDelete != null) RowMenu(onRename, onDelete)
            }
        }
    }
}

/**
 * A track's star as a button: filled in the text colour when set, a faint outline when not — there
 * on every row to be tapped, without every row shouting about it. From Title Track.
 */
@Composable
private fun StarButton(starred: Boolean, onToggle: () -> Unit) {
    IconButton(onClick = onToggle) {
        Icon(
            if (starred) Icons.Default.Star else Icons.Outlined.StarOutline,
            contentDescription = stringResource(
                if (starred) R.string.cd_unstar else R.string.cd_star,
            ),
            tint = if (starred) MaterialTheme.colorScheme.onSurface
            else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = UNSTARRED_ALPHA),
        )
    }
}

/** How faint an unset star's outline is. */
private const val UNSTARRED_ALPHA = 0.30f

@Composable
private fun RowMenu(onRename: () -> Unit, onDelete: () -> Unit) {
    var open by remember { mutableStateOf(false) }
    Box {
        IconButton(onClick = { open = true }) {
            Icon(Icons.Default.MoreVert, contentDescription = stringResource(R.string.cd_track_options))
        }
        DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
            DropdownMenuItem(
                text = { Text(stringResource(R.string.action_rename)) },
                leadingIcon = { Icon(Icons.Default.Edit, contentDescription = null) },
                onClick = { open = false; onRename() },
            )
            DropdownMenuItem(
                text = { Text(stringResource(R.string.action_delete)) },
                leadingIcon = { Icon(Icons.Default.Delete, contentDescription = null) },
                onClick = { open = false; onDelete() },
            )
        }
    }
}

/** The sort button for the library's app bar, and the menu of orders behind it. */
@Composable
fun LibrarySortButton(sort: LibrarySort, onSort: (LibrarySort) -> Unit) {
    var open by remember { mutableStateOf(false) }
    Box {
        IconButton(onClick = { open = true }) {
            Icon(Icons.AutoMirrored.Filled.Sort, contentDescription = stringResource(R.string.cd_sort))
        }
        DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
            LibrarySort.entries.forEach { entry ->
                DropdownMenuItem(
                    text = { Text(stringResource(entry.label)) },
                    // The tick's slot is kept on every row, so the labels line up.
                    leadingIcon = {
                        if (entry == sort) {
                            Icon(Icons.Default.Check, contentDescription = null)
                        } else {
                            Spacer(Modifier.size(24.dp))
                        }
                    },
                    onClick = { open = false; onSort(entry) },
                )
            }
        }
    }
}

private val LibrarySort.label: Int
    get() = when (this) {
        LibrarySort.RECENT -> R.string.sort_recent
        LibrarySort.NAME -> R.string.sort_name
        LibrarySort.LENGTH -> R.string.sort_length
        LibrarySort.ADDED -> R.string.sort_added
    }

/**
 * The search box the pull opens. Results arrive as words are typed, so there is nothing to submit.
 * Built on [BasicTextField] because the Material fields will not go below 56dp. From Title Track.
 */
@Composable
private fun SearchField(query: String, onQueryChange: (String) -> Unit, onClose: () -> Unit) {
    val focus = remember { FocusRequester() }
    val keyboard = LocalSoftwareKeyboardController.current
    LaunchedEffect(Unit) { focus.requestFocus() }
    val colors = MaterialTheme.colorScheme
    val text = MaterialTheme.typography.bodyLarge
    Row(
        Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp)
            .height(44.dp)
            .clip(CircleShape)
            .background(colors.surfaceContainerHigh)
            .padding(start = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            Icons.Default.Search,
            contentDescription = null,
            tint = colors.onSurfaceVariant,
            modifier = Modifier.size(20.dp),
        )
        Spacer(Modifier.width(10.dp))
        BasicTextField(
            value = query,
            onValueChange = onQueryChange,
            modifier = Modifier.weight(1f).focusRequester(focus),
            singleLine = true,
            textStyle = text.copy(color = colors.onSurface),
            cursorBrush = SolidColor(colors.primary),
            // The action key has nothing left to do but get the keyboard off the list.
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
            keyboardActions = KeyboardActions(onSearch = { keyboard?.hide() }),
            decorationBox = { field ->
                Box(contentAlignment = Alignment.CenterStart) {
                    if (query.isEmpty()) {
                        Text(
                            stringResource(R.string.search_hint),
                            style = text,
                            color = colors.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                    field()
                }
            },
        )
        IconButton(onClick = onClose) {
            Icon(
                Icons.Default.Close,
                stringResource(R.string.cd_close_search),
                tint = colors.onSurfaceVariant,
                modifier = Modifier.size(20.dp),
            )
        }
    }
}

/** The way back to the track the Library was opened over. */
@Composable
private fun ReturnStrip(track: String, onReturn: () -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .clickable(onClick = onReturn)
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            Icons.AutoMirrored.Filled.ArrowBack,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.primary,
            modifier = Modifier.size(20.dp),
        )
        Spacer(Modifier.width(12.dp))
        Text(
            stringResource(R.string.library_back_to, track),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.primary,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

/** How strongly a shaded row is washed with the text colour. */
private const val SHADE_ALPHA = 0.045f

@Composable
private fun RowDivider() {
    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f))
}

@Composable
private fun Hint(text: String) {
    Box(Modifier.fillMaxSize().padding(32.dp), contentAlignment = Alignment.Center) {
        Text(
            text,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )
    }
}
