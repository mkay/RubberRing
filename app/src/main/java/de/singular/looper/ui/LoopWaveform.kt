package de.singular.looper.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.calculateCentroid
import androidx.compose.foundation.gestures.calculatePan
import androidx.compose.foundation.gestures.calculateZoom
import androidx.compose.foundation.systemGestureExclusion
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChange
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.unit.dp
import de.singular.looper.audio.WaveformData
import kotlinx.coroutines.withTimeoutOrNull
import kotlin.math.abs
import kotlin.math.max

private val WaveColor = Color(0xFF5A6472)
private val WaveColorActive = Color(0xFFD6D9DE) // very light neutral gray — in-region highlight
private val HandleColor = Color(0xFFFFFFFF) // marker line
private val HandleTabFill = Color(0x59FFFFFF) // translucent grip tab
private val HandleGrip = Color(0xCC3A3F47) // grip ridges inside the tab
private val PlayheadColor = Color(0xFFA62120) // brand primary
private val DimOverlay = Color(0x99101418)
private val MinimapTrack = Color(0x33FFFFFF)
private val MinimapWindow = Color(0xB3FFFFFF)
private val GridLine = Color(0x22FFFFFF)
private val DownbeatLine = Color(0x55FFFFFF)

private const val MAX_ZOOM = 80f

// Hold this long on a marker (finger still) to grab it, so a marker can't be nudged by accident.
private const val LONG_PRESS_MS = 450L

private const val NONE = 0
private const val HANDLE_START = 1
private const val HANDLE_END = 2
private const val PAN = 3
private const val PLAYHEAD = 4
private const val SCROLLBAR = 5

// Height of the bottom scroll strip (the minimap). Its touch band is taller for easy grabbing.
private val SCROLLBAR_HEIGHT = 18.dp
private val SCROLLBAR_TOUCH = 46.dp

// Early-exit reasons while waiting for a long-press to arm a handle.
private const val LP_UP = 1
private const val LP_MOVED = 2
private const val LP_MULTI = 3

/**
 * Zoomable waveform with a draggable loop region and a playhead.
 *
 * All gestures are handled in one detector so they don't compete:
 *  - **Tap** in open space moves the playhead to the tapped position (seeks; live if playing).
 *    Play/stop is a dedicated button, so a stray tap never starts or stops playback.
 *  - **Long-press a marker's grip tab (or its line), then drag** moves that marker; the hold
 *    prevents accidental nudges, a grab offset keeps the line steady under the finger, and a
 *    haptic confirms the grab.
 *  - **Drag near the playhead** scrubs it.
 *  - **One-finger drag** in open space scrolls the view (when zoomed in).
 *  - **Two fingers** pinch-zoom and pan.
 *
 * A viewport ([zoom], [offset]) maps file fractions to pixels: `x = (frac - offset) * width * zoom`.
 */
@Composable
fun LoopWaveform(
    waveform: WaveformData,
    startFrac: Float,
    endFrac: Float,
    playheadFrac: Float,
    showPlayhead: Boolean,
    followPlayhead: Boolean,
    gridOffsetFrac: Float,
    gridIntervalFrac: Float, // 0 = no grid
    gridLinesPerBeat: Int, // subdivision; every Nth line is a beat (drawn brighter)
    onStartChange: (Float) -> Unit,
    onEndChange: (Float) -> Unit,
    onSeek: (Float) -> Unit,
    modifier: Modifier = Modifier,
) {
    val start by rememberUpdatedState(startFrac)
    val end by rememberUpdatedState(endFrac)
    val playhead by rememberUpdatedState(playheadFrac)
    val seek by rememberUpdatedState(onSeek)
    val haptic = LocalHapticFeedback.current

    // Viewport state, reset when a new file is loaded.
    var zoom by remember(waveform) { mutableFloatStateOf(1f) }
    var offset by remember(waveform) { mutableFloatStateOf(0f) }

    // When following, scroll the view to bring the playhead back whenever it leaves the visible
    // window (e.g. it jumps to the loop start on play). Only matters when zoomed in.
    LaunchedEffect(followPlayhead, waveform) {
        if (!followPlayhead) return@LaunchedEffect
        snapshotFlow { playhead }.collect { ph ->
            val window = 1f / zoom
            if (zoom > 1f && (ph < offset || ph > offset + window)) {
                val maxOffset = (1f - window).coerceAtLeast(0f)
                offset = (ph - window * 0.1f).coerceIn(0f, maxOffset)
            }
        }
    }

    Canvas(
        // Exclude the waveform from Android's edge-swipe gestures so grabbing a marker near
        // the screen edge doesn't trigger system back/forward and quit the app.
        modifier = modifier
            .systemGestureExclusion()
            .pointerInput(waveform) {
            val handleThreshold = 48.dp.toPx()
            val slop = viewConfiguration.touchSlop

            fun clampOffset(o: Float) = o.coerceIn(0f, (1f - 1f / zoom).coerceAtLeast(0f))

            fun applyZoom(factor: Float, focalX: Float) {
                val w = size.width.toFloat()
                if (w <= 0f) return
                val newZoom = (zoom * factor).coerceIn(1f, MAX_ZOOM)
                val focalFrac = offset + (focalX / w) / zoom
                zoom = newZoom
                offset = clampOffset(focalFrac - (focalX / w) / newZoom)
            }

            fun applyPan(dxPixels: Float) {
                val w = size.width.toFloat()
                if (w <= 0f) return
                offset = clampOffset(offset - dxPixels / (w * zoom))
            }

            // Scrollbar drag: centre the visible window on the touched x (jump-to-position scroll).
            fun scrollTo(x: Float) {
                val w = size.width.toFloat()
                if (w <= 0f) return
                offset = clampOffset((x / w) - (1f / zoom) / 2f)
            }

            fun xToFrac(x: Float): Float {
                val w = size.width.toFloat()
                if (w <= 0f) return 0f
                return (offset + (x / w) / zoom).coerceIn(0f, 1f)
            }

            fun handleNear(x: Float): Int {
                val w = size.width.toFloat()
                val sx = (start - offset) * w * zoom
                val ex = (end - offset) * w * zoom
                val ds = abs(x - sx)
                val de = abs(x - ex)
                return when {
                    ds <= handleThreshold && ds <= de -> HANDLE_START
                    de <= handleThreshold -> HANDLE_END
                    else -> NONE
                }
            }

            fun playheadNear(x: Float): Boolean {
                val w = size.width.toFloat()
                val px = (playhead - offset) * w * zoom
                return abs(x - px) <= handleThreshold
            }

            awaitEachGesture {
                val down = awaitFirstDown(requireUnconsumed = false)
                val downX = down.position.x
                var mode = NONE
                var moved = false
                var multiTouch = false
                // Offset from the finger to the grabbed marker's line, so the line stays put
                // under the finger instead of jumping to it (the grip tab is drawn off to one side).
                var grabDx = 0f

                val w0 = size.width.toFloat()
                val inScrollbar = zoom > 1f && down.position.y >= size.height - SCROLLBAR_TOUCH.toPx()
                val nearHandle = if (inScrollbar) NONE else handleNear(downX)
                if (inScrollbar) {
                    mode = SCROLLBAR // grab the scroll strip; no hold needed
                    scrollTo(downX)
                } else if (nearHandle != NONE) {
                    // Require a still hold to arm a marker; a quick move/lift cancels and falls
                    // through (so a normal touch can't nudge a marker).
                    val exit = withTimeoutOrNull(LONG_PRESS_MS) {
                        var reason = 0
                        while (reason == 0) {
                            val e = awaitPointerEvent()
                            val c = e.changes.firstOrNull { it.id == down.id }
                            reason = when {
                                c == null || !c.pressed -> LP_UP
                                e.changes.count { it.pressed } >= 2 -> LP_MULTI
                                abs(c.position.x - downX) > slop -> LP_MOVED
                                else -> 0
                            }
                        }
                        reason
                    }
                    if (exit == null) { // held still long enough → armed
                        mode = nearHandle
                        val frac = if (nearHandle == HANDLE_START) start else end
                        grabDx = downX - (frac - offset) * w0 * zoom
                        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                    }
                } else if (playheadNear(downX)) {
                    mode = PLAYHEAD // scrubbing the playhead needs no hold
                }

                while (true) {
                    val event = awaitPointerEvent()
                    val pressed = event.changes.count { it.pressed }
                    if (pressed == 0) break

                    if (pressed >= 2) {
                        multiTouch = true
                        val zoomChange = event.calculateZoom()
                        val panChange = event.calculatePan()
                        val centroid = event.calculateCentroid(useCurrent = true)
                        if (zoomChange != 1f) applyZoom(zoomChange, centroid.x)
                        if (panChange.x != 0f) applyPan(panChange.x)
                        event.changes.forEach { it.consume() }
                    } else {
                        val change = event.changes.first { it.pressed }
                        if (abs(change.position.x - downX) > slop) moved = true
                        val dx = change.positionChange().x
                        when (mode) {
                            HANDLE_START -> { onStartChange(xToFrac(change.position.x - grabDx)); change.consume() }
                            HANDLE_END -> { onEndChange(xToFrac(change.position.x - grabDx)); change.consume() }
                            PLAYHEAD -> { seek(xToFrac(change.position.x)); change.consume() }
                            SCROLLBAR -> { scrollTo(change.position.x); change.consume() }
                            PAN -> { applyPan(dx); change.consume() }
                            NONE -> if (moved) { mode = PAN; applyPan(dx); change.consume() }
                        }
                    }
                }

                // A tap in open space (nothing else engaged) seeks the playhead to it.
                // Play/stop lives on a dedicated button, not here.
                if (!moved && !multiTouch && mode == NONE) {
                    seek(xToFrac(downX))
                }
            }
        },
    ) {
        val w = size.width
        val h = size.height
        val midY = h / 2f
        val buckets = waveform.bucketCount
        if (buckets == 0 || w <= 0f) return@Canvas

        fun fracToX(f: Float) = (f - offset) * w * zoom

        val columns = max(1, w.toInt())
        for (x in 0 until columns) {
            val fL = offset + (x.toFloat() / w) / zoom
            val fR = offset + ((x + 1).toFloat() / w) / zoom
            val bL = (fL * buckets).toInt().coerceIn(0, buckets - 1)
            val bR = (fR * buckets).toInt().coerceIn(bL + 1, buckets)
            var mn = Float.POSITIVE_INFINITY
            var mx = Float.NEGATIVE_INFINITY
            for (b in bL until bR) {
                if (waveform.minima[b] < mn) mn = waveform.minima[b]
                if (waveform.maxima[b] > mx) mx = waveform.maxima[b]
            }
            if (mn == Float.POSITIVE_INFINITY) { mn = 0f; mx = 0f }
            val inRegion = fL >= start && fR <= end
            drawLine(
                color = if (inRegion) WaveColorActive else WaveColor,
                start = Offset(x.toFloat(), midY - mx * midY),
                end = Offset(x.toFloat(), midY - mn * midY),
                strokeWidth = 1f,
            )
        }

        // Beat grid: draw the lines visible in the current window.
        if (gridIntervalFrac > 0f) {
            val visSpan = 1f / zoom
            val firstK = kotlin.math.floor((offset - gridOffsetFrac) / gridIntervalFrac).toInt()
            val lastK = kotlin.math.ceil((offset + visSpan - gridOffsetFrac) / gridIntervalFrac).toInt()
            // Guard against absurd counts when zoomed way out on a fine subdivision.
            if (lastK - firstK in 0..5000) {
                for (k in firstK..lastK) {
                    val f = gridOffsetFrac + k * gridIntervalFrac
                    if (f < 0f || f > 1f) continue
                    val gx = fracToX(f)
                    if (gx < 0f || gx > w) continue
                    val isBeat = gridLinesPerBeat <= 1 || k % gridLinesPerBeat == 0
                    drawLine(
                        color = if (isBeat) DownbeatLine else GridLine,
                        start = Offset(gx, 0f),
                        end = Offset(gx, h),
                        strokeWidth = if (isBeat) 2f else 1f,
                    )
                }
            }
        }

        val startX = fracToX(start).coerceIn(0f, w)
        val endX = fracToX(end).coerceIn(0f, w)
        if (startX > 0f) drawRect(DimOverlay, topLeft = Offset(0f, 0f), size = Size(startX, h))
        if (endX < w) drawRect(DimOverlay, topLeft = Offset(endX, 0f), size = Size(w - endX, h))

        // Tabs point into the loop region (start → right, end → left) so they stay on-screen
        // even when a marker is pushed against the edge.
        fracToX(start).let { if (it in 0f..w) drawHandle(it, h, towardRight = true) }
        fracToX(end).let { if (it in 0f..w) drawHandle(it, h, towardRight = false) }

        if (showPlayhead) {
            val px = fracToX(playheadFrac)
            if (px in 0f..w) drawLine(PlayheadColor, start = Offset(px, 0f), end = Offset(px, h), strokeWidth = 3f)
        }

        // Bottom scroll strip (only when zoomed): a tall, draggable scrollbar. The bright thumb
        // shows the visible window; dragging it scrolls fast — handy while playing.
        if (zoom > 1f) {
            val stripH = SCROLLBAR_HEIGHT.toPx()
            val y = h - stripH
            val radius = CornerRadius(stripH / 2f, stripH / 2f)
            drawRoundRect(MinimapTrack, topLeft = Offset(0f, y), size = Size(w, stripH), cornerRadius = radius)
            val thumbW = ((1f / zoom) * w).coerceAtLeast(stripH)
            val thumbX = (offset * w).coerceIn(0f, w - thumbW)
            drawRoundRect(MinimapWindow, topLeft = Offset(thumbX, y), size = Size(thumbW, stripH), cornerRadius = radius)
        }
    }
}

private fun androidx.compose.ui.graphics.drawscope.DrawScope.drawHandle(
    x: Float,
    h: Float,
    towardRight: Boolean,
) {
    drawLine(HandleColor, start = Offset(x, 0f), end = Offset(x, h), strokeWidth = 4f)

    // A tall translucent tab beside the line, centred vertically — a large, obvious grab
    // target. Only the two corners away from the line are rounded, so the tab reads as
    // attached to the marker (flush against it) rather than a floating pill.
    val tabW = 24.dp.toPx()
    val tabH = 64.dp.toPx()
    val r = CornerRadius(5.dp.toPx(), 5.dp.toPx())
    val flat = CornerRadius.Zero
    val left = if (towardRight) x else x - tabW
    val top = h / 2f - tabH / 2f
    val tab = Path().apply {
        addRoundRect(
            RoundRect(
                left = left, top = top, right = left + tabW, bottom = top + tabH,
                topLeftCornerRadius = if (towardRight) flat else r,
                bottomLeftCornerRadius = if (towardRight) flat else r,
                topRightCornerRadius = if (towardRight) r else flat,
                bottomRightCornerRadius = if (towardRight) r else flat,
            )
        )
    }
    drawPath(tab, HandleTabFill)

    // Two grip ridges centred in the tab.
    val cx = left + tabW / 2f
    val gripHalf = tabH * 0.2f
    val gap = 3.dp.toPx()
    val gripWidth = 2.dp.toPx()
    for (dx in floatArrayOf(-gap, gap)) {
        drawLine(
            HandleGrip,
            start = Offset(cx + dx, h / 2f - gripHalf),
            end = Offset(cx + dx, h / 2f + gripHalf),
            strokeWidth = gripWidth,
        )
    }
}
