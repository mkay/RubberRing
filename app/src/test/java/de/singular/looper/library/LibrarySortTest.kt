// SPDX-License-Identifier: GPL-3.0-only

package de.singular.looper.library

import org.junit.Assert.assertEquals
import org.junit.Test

class LibrarySortTest {

    private fun track(
        name: String,
        importedAt: Long = 0,
        lastOpenedAt: Long = 0,
        durationMs: Long = 0,
        starred: Boolean = false,
    ) = LibraryTrack(
        id = name,
        storedFileName = "$name.m4a",
        displayName = name,
        importedAt = importedAt,
        durationMs = durationMs,
        lastOpenedAt = lastOpenedAt,
        starred = starred,
    )

    @Test
    fun `recent puts the last opened or imported first`() {
        val old = track("old", importedAt = 1, lastOpenedAt = 2)
        val opened = track("opened", importedAt = 1, lastOpenedAt = 9)
        val imported = track("imported", importedAt = 5)
        assertEquals(listOf(opened, imported, old), LibrarySort.RECENT.sort(listOf(old, opened, imported)))
    }

    @Test
    fun `name sorts the way a person reads, not by character code`() {
        val tracks = listOf(track("zebra"), track("Übung"), track("apple"), track("Bass"))
        assertEquals(
            listOf("apple", "Bass", "Übung", "zebra"),
            LibrarySort.NAME.sort(tracks).map { it.name },
        )
    }

    @Test
    fun `length is shortest first, added is newest first`() {
        val a = track("a", importedAt = 3, durationMs = 300)
        val b = track("b", importedAt = 1, durationMs = 100)
        val c = track("c", importedAt = 2, durationMs = 200)
        assertEquals(listOf(b, c, a), LibrarySort.LENGTH.sort(listOf(a, b, c)))
        assertEquals(listOf(a, c, b), LibrarySort.ADDED.sort(listOf(a, b, c)))
    }

    @Test
    fun `an unknown stored sort falls back to recent`() {
        assertEquals(LibrarySort.RECENT, LibrarySort.from(null))
        assertEquals(LibrarySort.RECENT, LibrarySort.from("SIZE"))
        assertEquals(LibrarySort.NAME, LibrarySort.from("NAME"))
    }

    @Test
    fun `starred tracks come first in every order, each group in that order`() {
        val a = track("a", importedAt = 1, durationMs = 300, starred = true)
        val b = track("b", importedAt = 2, durationMs = 100)
        val c = track("c", importedAt = 3, durationMs = 200, starred = true)
        val d = track("d", importedAt = 4, durationMs = 400)
        val all = listOf(d, b, a, c)
        assertEquals(listOf(a, c, b, d), LibrarySort.NAME.sort(all))
        assertEquals(listOf(c, a, b, d), LibrarySort.LENGTH.sort(all))
        assertEquals(listOf(c, a, d, b), LibrarySort.ADDED.sort(all))
    }
}
