package de.singular.looper.library

/**
 * A named loop within a track — a practice section like "Chorus" or "Bridge". Recalling one
 * restores its region and grid. Stored as a list on the owning [LibraryTrack].
 */
data class SavedLoop(
    val id: String,
    val label: String,
    val startFrac: Float,
    val endFrac: Float,
    val bpm: Float,
    val downbeatFrac: Float,
    val snap: Boolean,
)
