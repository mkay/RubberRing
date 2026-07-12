package de.singular.looper.library

/**
 * One imported track in the app's own library. The source file's bytes are copied into
 * app-private storage on import (see [LibraryRepository]), so this record points at a stable
 * internal file — nothing goes stale if the user later moves or deletes the original.
 *
 * @param id           stable unique id; also the base of the on-disk file name.
 * @param storedFileName file name within the library directory (e.g. "<id>.mp3").
 * @param displayName  the original file name; kept untouched as a fallback.
 * @param title        optional user-chosen display title; overrides [displayName] when set.
 * @param importedAt   epoch millis the file was imported.
 * @param durationMs   decoded duration, filled in after the first successful decode.
 * @param lastOpenedAt epoch millis the track was last opened (0 = never); drives the recents list.
 *
 * [startFrac]..[snap] are the last-used loop state, auto-saved as the user edits and restored
 * when the track is reopened. Defaults describe a fresh track: the whole file, grid off.
 * [zoom]/[offset] are the last waveform viewport, saved on exit only when the user opts in.
 * [savedLoops] are the user's explicitly named practice loops for this track.
 * [arrangement] is an ordered practice sequence built from those loops (see [ArrangementStep]).
 */
data class LibraryTrack(
    val id: String,
    val storedFileName: String,
    val displayName: String,
    val title: String? = null,
    val importedAt: Long,
    val durationMs: Long,
    val lastOpenedAt: Long = 0L,
    val startFrac: Float = 0f,
    val endFrac: Float = 1f,
    val bpm: Float = 120f,
    val downbeatFrac: Float = 0f,
    val snap: Boolean = false,
    val zoom: Float = 1f,
    val offset: Float = 0f,
    val savedLoops: List<SavedLoop> = emptyList(),
    val arrangement: List<ArrangementStep> = emptyList(),
    // Whether the Play button runs the arrangement (armed) rather than the single loop, and
    // whether that arrangement repeats from the top or stops after the last step.
    val arrangementActive: Boolean = false,
    val arrangementRepeat: Boolean = false,
    // Count-in settings: a long-press on Play sounds a metronome for [countInBars] bars of
    // [countInBeatsPerBar] beats first. The count-in inherits the track's [bpm]; accent on beat 1.
    val countInBeatsPerBar: Int = 4,
    val countInBars: Int = 1,
) {
    /** The name to show the user: the chosen [title] if set, otherwise the original file name. */
    val name: String get() = title?.takeIf { it.isNotBlank() } ?: displayName
}
