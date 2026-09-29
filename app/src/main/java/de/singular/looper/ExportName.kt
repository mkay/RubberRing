// SPDX-License-Identifier: GPL-3.0-only

package de.singular.looper

import java.util.Locale

/** Characters that at least one of Windows, macOS, Linux or Android storage refuses in a name. */
private val UNSAFE = Regex("""[\\/:*?"<>|\p{Cntrl}]""")

/** An audio extension on an imported file name, which the exported WAV must not carry over. */
private val AUDIO_EXTENSION = Regex(
    """\.(mp3|m4a|aac|ogg|oga|opus|flac|wav|wave|aif|aiff|wma|mp4|3gp|amr|mka|webm)$""",
    RegexOption.IGNORE_CASE,
)

/**
 * The suggested file name for an exported loop: `<track> - <loop> (0.8x).wav`. The loop part is
 * left out when the region isn't a saved loop, and the speed part at 1×. Plain ASCII `x` rather
 * than `×`, so the name survives old zip tools and file shares, and can be typed in a search.
 */
fun loopExportName(trackName: String, loopLabel: String?, speed: Float): String {
    val track = clean(trackName.trim().replace(AUDIO_EXTENSION, "")).ifEmpty { "loop" }
    val loop = loopLabel?.let(::clean)?.takeIf { it.isNotEmpty() }
    val speedTag = if (speed == 1f) null
    else "(" + "%.2f".format(Locale.US, speed).trimEnd('0').trimEnd('.') + "x)"
    return listOfNotNull(track, loop?.let { "- $it" }, speedTag).joinToString(" ") + ".wav"
}

/** Swap unsafe characters for `_`, and trim the dots and spaces Windows drops from a name's end. */
private fun clean(s: String): String = s.replace(UNSAFE, "_").trim().trimEnd('.', ' ')
