// SPDX-License-Identifier: GPL-3.0-only

package de.singular.looper.ui

import androidx.compose.animation.core.animate
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Velocity
import androidx.compose.ui.unit.dp

/**
 * A list that, pulled down past its top, opens a gap above itself and calls [onReveal] if let go
 * far enough. The library's search field lives up there. Copied unchanged from Title Track.
 *
 * Built on nested scrolling, so it needs nothing from the list but that it scrolls: a pull reaches
 * here only as the drag the list could not use, which means only at the top, and only downward.
 * Pushing back up closes the gap before the list starts to scroll again, and a release short of
 * [threshold] springs it shut with nothing done — a pull you change your mind about costs nothing.
 *
 * Knows nothing about takes or searching, so it carries over to another app unchanged. [hint] is
 * drawn in the gap as it opens, faded in by how far the pull has come.
 *
 * Pulling down is refresh in many Android apps. Nothing in this one refreshes that way, so the
 * gesture is free to mean something else here.
 */
@Composable
fun PullToReveal(
    enabled: Boolean,
    onReveal: () -> Unit,
    hint: @Composable () -> Unit,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    val threshold = with(LocalDensity.current) { RevealAt.toPx() }
    var pull by remember { mutableFloatStateOf(0f) }
    val active by rememberUpdatedState(enabled)
    val reveal by rememberUpdatedState(onReveal)

    val connection = remember(threshold) {
        object : NestedScrollConnection {
            // Going back up eats into the gap first, so the list does not scroll while it is open.
            override fun onPreScroll(available: Offset, source: NestedScrollSource): Offset {
                if (pull <= 0f || available.y >= 0f) return Offset.Zero
                val taken = minOf(-available.y, pull)
                pull -= taken
                return Offset(0f, -taken)
            }

            // What the list could not use downward is the list already at its top, being pulled.
            // Halved, so the gap trails the finger the way an over-pulled edge does elsewhere.
            override fun onPostScroll(
                consumed: Offset,
                available: Offset,
                source: NestedScrollSource,
            ): Offset {
                if (!active || source != NestedScrollSource.UserInput || available.y <= 0f) {
                    return Offset.Zero
                }
                pull = (pull + available.y * Resistance).coerceAtMost(threshold * Overpull)
                return Offset(0f, available.y)
            }

            // Every release ends here, a slow one at zero velocity included.
            override suspend fun onPreFling(available: Velocity): Velocity {
                if (pull <= 0f) return Velocity.Zero
                if (pull >= threshold) reveal()
                animate(pull, 0f) { value, _ -> pull = value }
                return available
            }
        }
    }

    Column(modifier.nestedScroll(connection)) {
        val gap = with(LocalDensity.current) { pull.toDp() }
        Box(
            Modifier.fillMaxWidth().height(gap).clipToBounds()
                .alpha((pull / threshold).coerceIn(0f, 1f)),
            contentAlignment = Alignment.Center,
        ) { hint() }
        Box(Modifier.weight(1f)) { content() }
    }
}

/** How far a pull has to come, in the gap it opens, before letting go reveals. */
private val RevealAt = 72.dp

/** How much of a drag past the top turns into gap. */
private const val Resistance = 0.5f

/** How far past [RevealAt] the gap may be dragged open, as a multiple of it. */
private const val Overpull = 1.4f
