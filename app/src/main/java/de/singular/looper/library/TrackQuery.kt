// SPDX-License-Identifier: GPL-3.0-only

package de.singular.looper.library

import java.text.Normalizer
import java.util.Locale

/**
 * What the library search box asks for, as a test on one track.
 *
 * A track matches when every word typed appears somewhere in its name or in the name of the file
 * it was imported from. A renamed track keeps its file name, and that is often the half of a name
 * that is remembered: "live" finds the bootleg that has since been called "Refrain, slow".
 *
 * Words rather than one phrase, in any order, because nobody remembers the order of a name they
 * half remember. Case and accents are ignored on both sides: a track called "Übung" is found by
 * "ubung", which is what a thumb on a keyboard without the umlaut to hand will type.
 *
 * Pure Kotlin, so the matching is a unit test rather than something checked by typing on a phone.
 * Ported from Title Track's TakeQuery.
 */
class TrackQuery private constructor(private val words: List<String>) {

    fun matches(track: LibraryTrack): Boolean = matches(track.name, track.displayName)

    fun matches(name: String, fileName: String): Boolean {
        val haystack = fold("$name\n$fileName")
        return words.all { it in haystack }
    }

    companion object {
        /** The query typed as [text], or null if there is nothing in it to search for. */
        fun of(text: String): TrackQuery? =
            fold(text).split(Regex("\\s+")).filter { it.isNotEmpty() }
                .takeIf { it.isNotEmpty() }
                ?.let(::TrackQuery)

        private val marks = Regex("\\p{Mn}+")

        /** Lower-cased, with accents taken off — "Übung" and "ubung" fold to the same thing. */
        private fun fold(s: String): String =
            marks.replace(Normalizer.normalize(s, Normalizer.Form.NFD), "").lowercase(Locale.ROOT)
    }
}
