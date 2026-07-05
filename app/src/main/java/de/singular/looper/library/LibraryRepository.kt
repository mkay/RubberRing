package de.singular.looper.library

import android.content.ContentResolver
import android.content.Context
import android.net.Uri
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.util.UUID

/**
 * The app's audio library: imported files live under `filesDir/library/`, and a small JSON
 * index next to it records their metadata.
 *
 * Internal storage is never touched by Android's media scanner, so imported files are
 * invisible to music players, galleries, and pickers — they are project data, not media.
 *
 * The index is a plain JSON file (no DataStore/Room dependency) — the data is a single short
 * list. All methods do file I/O; call them off the main thread. Index reads/writes are
 * serialised with a [Mutex]; the file copy in [copyIn] is independent and runs unlocked.
 */
class LibraryRepository(context: Context) {

    private val appContext = context.applicationContext
    private val dir = File(appContext.filesDir, "library").apply { mkdirs() }
    private val indexFile = File(appContext.filesDir, "library-index.json")
    private val mutex = Mutex()

    /** The on-disk file backing [track]. */
    fun fileFor(track: LibraryTrack): File = File(dir, track.storedFileName)

    /** All imported tracks, newest first. */
    suspend fun list(): List<LibraryTrack> =
        mutex.withLock { readIndex() }.sortedByDescending { it.importedAt }

    /**
     * Copy [uri]'s bytes into the library and return a not-yet-indexed [LibraryTrack] together
     * with its file. Caller decodes it, then calls [add] with the duration filled in (so a file
     * that fails to decode never lands in the index — delete its file instead).
     */
    fun copyIn(resolver: ContentResolver, uri: Uri, displayName: String?): Pair<LibraryTrack, File> {
        val id = UUID.randomUUID().toString()
        val ext = extensionOf(displayName)
        val storedFileName = if (ext.isEmpty()) id else "$id.$ext"
        val dest = File(dir, storedFileName)
        resolver.openInputStream(uri)?.use { input ->
            dest.outputStream().use { output -> input.copyTo(output) }
        } ?: throw IllegalStateException("Could not read the selected file")
        val track = LibraryTrack(
            id = id,
            storedFileName = storedFileName,
            displayName = displayName?.takeIf { it.isNotBlank() } ?: "Untitled",
            importedAt = System.currentTimeMillis(),
            durationMs = 0L,
        )
        return track to dest
    }

    /** Add or replace a track in the index. */
    suspend fun add(track: LibraryTrack) = mutex.withLock {
        val updated = readIndex().filterNot { it.id == track.id } + track
        writeIndex(updated)
    }

    /** Remove a track from the index and delete its file. */
    suspend fun remove(track: LibraryTrack) = mutex.withLock {
        writeIndex(readIndex().filterNot { it.id == track.id })
        runCatching { fileFor(track).delete() }
        Unit
    }

    private fun extensionOf(displayName: String?): String {
        val dot = displayName?.lastIndexOf('.') ?: -1
        if (displayName == null || dot <= 0 || dot == displayName.length - 1) return ""
        return displayName.substring(dot + 1).filter { it.isLetterOrDigit() }.take(5).lowercase()
    }

    private fun readIndex(): List<LibraryTrack> {
        if (!indexFile.exists()) return emptyList()
        return runCatching {
            val arr = JSONArray(indexFile.readText())
            (0 until arr.length()).map { i ->
                val o = arr.getJSONObject(i)
                LibraryTrack(
                    id = o.getString("id"),
                    storedFileName = o.getString("storedFileName"),
                    displayName = o.getString("displayName"),
                    importedAt = o.getLong("importedAt"),
                    durationMs = o.getLong("durationMs"),
                    // Loop state is optional so records written before it existed still load.
                    startFrac = o.optDouble("startFrac", 0.0).toFloat(),
                    endFrac = o.optDouble("endFrac", 1.0).toFloat(),
                    bpm = o.optDouble("bpm", 120.0).toFloat(),
                    downbeatFrac = o.optDouble("downbeatFrac", 0.0).toFloat(),
                    snap = o.optBoolean("snap", false),
                    savedLoops = readLoops(o.optJSONArray("savedLoops")),
                )
            }
        }.getOrDefault(emptyList())
    }

    private fun readLoops(arr: JSONArray?): List<SavedLoop> {
        if (arr == null) return emptyList()
        return (0 until arr.length()).map { j ->
            val o = arr.getJSONObject(j)
            SavedLoop(
                id = o.getString("id"),
                label = o.getString("label"),
                startFrac = o.optDouble("startFrac", 0.0).toFloat(),
                endFrac = o.optDouble("endFrac", 1.0).toFloat(),
                bpm = o.optDouble("bpm", 120.0).toFloat(),
                downbeatFrac = o.optDouble("downbeatFrac", 0.0).toFloat(),
                snap = o.optBoolean("snap", false),
            )
        }
    }

    private fun writeIndex(list: List<LibraryTrack>) {
        val arr = JSONArray()
        list.forEach { t ->
            arr.put(
                JSONObject()
                    .put("id", t.id)
                    .put("storedFileName", t.storedFileName)
                    .put("displayName", t.displayName)
                    .put("importedAt", t.importedAt)
                    .put("durationMs", t.durationMs)
                    .put("startFrac", t.startFrac.toDouble())
                    .put("endFrac", t.endFrac.toDouble())
                    .put("bpm", t.bpm.toDouble())
                    .put("downbeatFrac", t.downbeatFrac.toDouble())
                    .put("snap", t.snap)
                    .put("savedLoops", writeLoops(t.savedLoops)),
            )
        }
        // Write to a temp file then replace, so a crash mid-write can't corrupt the index.
        writeIndexFile(arr)
    }

    private fun writeLoops(loops: List<SavedLoop>): JSONArray {
        val arr = JSONArray()
        loops.forEach { l ->
            arr.put(
                JSONObject()
                    .put("id", l.id)
                    .put("label", l.label)
                    .put("startFrac", l.startFrac.toDouble())
                    .put("endFrac", l.endFrac.toDouble())
                    .put("bpm", l.bpm.toDouble())
                    .put("downbeatFrac", l.downbeatFrac.toDouble())
                    .put("snap", l.snap),
            )
        }
        return arr
    }

    private fun writeIndexFile(arr: JSONArray) {
        val tmp = File(indexFile.parentFile, "${indexFile.name}.tmp")
        tmp.writeText(arr.toString())
        if (indexFile.exists()) indexFile.delete()
        tmp.renameTo(indexFile)
    }
}
