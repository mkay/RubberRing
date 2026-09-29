// SPDX-License-Identifier: GPL-3.0-only

package de.singular.looper

import org.junit.Assert.assertEquals
import org.junit.Test

/** Checks for [loopExportName]: readable names, and nothing a common file system would refuse. */
class ExportNameTest {

    @Test
    fun fullSpeedUnsavedRegionIsJustTheTrack() =
        assertEquals("Song.wav", loopExportName("Song.mp3", null, 1f))

    @Test
    fun savedLoopAndSpeedAreNamed() =
        assertEquals("Song - Chorus (0.8x).wav", loopExportName("Song.mp3", "Chorus", 0.8f))

    @Test
    fun speedDropsTrailingZerosAndIgnoresLocale() {
        val saved = java.util.Locale.getDefault()
        try {
            java.util.Locale.setDefault(java.util.Locale.GERMANY) // would print "0,75"
            assertEquals("Song (0.75x).wav", loopExportName("Song", null, 0.75f))
            assertEquals("Song (1.5x).wav", loopExportName("Song", null, 1.5f))
        } finally {
            java.util.Locale.setDefault(saved)
        }
    }

    @Test
    fun unsafeCharactersAreReplaced() =
        assertEquals(
            "AC_DC - Verse 1_2 _slow_.wav",
            loopExportName("AC/DC.flac", "Verse 1/2 \"slow\"", 1f),
        )

    @Test
    fun onlyAudioExtensionsAreStripped() =
        assertEquals("Live at St. Mary's.wav", loopExportName("Live at St. Mary's", null, 1f))

    @Test
    fun blankTrackNameFallsBack() =
        assertEquals("loop - Intro.wav", loopExportName("  ", "Intro", 1f))
}
