package de.singular.looper.library

/**
 * One imported track in the app's own library. The source file's bytes are copied into
 * app-private storage on import (see [LibraryRepository]), so this record points at a stable
 * internal file — nothing goes stale if the user later moves or deletes the original.
 *
 * @param id           stable unique id; also the base of the on-disk file name.
 * @param storedFileName file name within the library directory (e.g. "<id>.mp3").
 * @param displayName  the original, user-facing file name.
 * @param importedAt   epoch millis the file was imported.
 * @param durationMs   decoded duration, filled in after the first successful decode.
 *
 * [startFrac]..[snap] are the last-used loop state, auto-saved as the user edits and restored
 * when the track is reopened. Defaults describe a fresh track: the whole file, grid off.
 * [savedLoops] are the user's explicitly named practice loops for this track.
 */
data class LibraryTrack(
    val id: String,
    val storedFileName: String,
    val displayName: String,
    val importedAt: Long,
    val durationMs: Long,
    val startFrac: Float = 0f,
    val endFrac: Float = 1f,
    val bpm: Float = 120f,
    val downbeatFrac: Float = 0f,
    val snap: Boolean = false,
    val savedLoops: List<SavedLoop> = emptyList(),
)
