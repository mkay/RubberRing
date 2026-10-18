// SPDX-License-Identifier: GPL-3.0-only

package de.singular.looper.library

import java.text.Collator

/**
 * The orders the library can list its tracks in. Whichever is chosen, starred tracks come first,
 * each group in that order: a star is how a track is kept at the top.
 */
enum class LibrarySort {
    /** Last opened or imported, newest first — the order the home screen's recents use. */
    RECENT,

    /** By name, as a person sorts words: case and accents don't push "Übung" past "Zebra". */
    NAME,

    /** Shortest first. */
    LENGTH,

    /** Imported, newest first. */
    ADDED;

    fun sort(tracks: List<LibraryTrack>): List<LibraryTrack> {
        val (starred, rest) = order(tracks).partition { it.starred }
        return starred + rest
    }

    private fun order(tracks: List<LibraryTrack>): List<LibraryTrack> = when (this) {
        RECENT -> tracks.sortedByDescending { maxOf(it.lastOpenedAt, it.importedAt) }
        NAME -> {
            val collator = Collator.getInstance().apply { strength = Collator.SECONDARY }
            tracks.sortedWith { a, b -> collator.compare(a.name, b.name) }
        }
        LENGTH -> tracks.sortedBy { it.durationMs }
        ADDED -> tracks.sortedByDescending { it.importedAt }
    }

    companion object {
        /** The sort stored as [name], or [RECENT] for anything unknown (or nothing stored yet). */
        fun from(name: String?): LibrarySort = entries.firstOrNull { it.name == name } ?: RECENT
    }
}
