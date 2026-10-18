// SPDX-License-Identifier: GPL-3.0-only

package de.singular.looper.library

import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class TrackQueryTest {

    private fun query(text: String) = TrackQuery.of(text)!!

    private fun track(name: String, title: String? = null) = LibraryTrack(
        id = name,
        storedFileName = "$name.m4a",
        displayName = name,
        title = title,
        importedAt = 0,
        durationMs = 0,
    )

    @Test
    fun `nothing typed is no query`() {
        assertNull(TrackQuery.of(""))
        assertNull(TrackQuery.of("   "))
    }

    @Test
    fun `every word has to match, in any order`() {
        val t = track("The Visitor - Mad Zach.m4a")
        assertTrue(query("zach visitor").matches(t))
        assertFalse(query("visitor remix").matches(t))
    }

    @Test
    fun `a renamed track is still found by its file name`() {
        val t = track("bootleg-live-2019.mp3", title = "Refrain, slow")
        assertTrue(query("refrain").matches(t))
        assertTrue(query("live").matches(t))
        assertTrue(query("slow 2019").matches(t))
    }

    @Test
    fun `case and accents are ignored on both sides`() {
        assertTrue(query("ubung").matches(track("Übung 3.wav")))
        assertTrue(query("ÜBUNG").matches(track("ubung 3.wav")))
    }
}
