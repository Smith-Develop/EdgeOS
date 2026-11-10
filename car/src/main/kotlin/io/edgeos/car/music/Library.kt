package io.edgeos.car.music

import android.content.Context
import android.os.Environment
import android.provider.MediaStore
import java.io.File

class Track(val path: String, val title: String, val artist: String, val album: String, val duration: Long) {
    val folder: String get() = File(path).parentFile?.name ?: ""
}

/**
 * Las canciones de la radio y de las memorias USB/SD. Primero lo que ya indexó Android (MediaStore); después se
 * recorren las memorias, porque muchas radios no indexan los USB. Necesita el permiso de almacenamiento.
 */
object Library {
    private val EXT = setOf("mp3", "m4a", "aac", "flac", "ogg", "opus", "wav", "wma", "ape")

    /** Llamar fuera del hilo principal. */
    fun scan(context: Context): List<Track> {
        val found = LinkedHashMap<String, Track>()
        runCatching {
            context.contentResolver.query(
                MediaStore.Audio.Media.EXTERNAL_CONTENT_URI,
                arrayOf(MediaStore.Audio.Media.DATA, MediaStore.Audio.Media.TITLE, MediaStore.Audio.Media.ARTIST,
                    MediaStore.Audio.Media.ALBUM, MediaStore.Audio.Media.DURATION),
                "${MediaStore.Audio.Media.IS_MUSIC} != 0", null, null,
            )?.use { c ->
                while (c.moveToNext()) {
                    val path = c.getString(0) ?: continue
                    if (!File(path).canRead()) continue
                    found[path] = Track(path, c.getString(1) ?: File(path).nameWithoutExtension,
                        clean(c.getString(2)), clean(c.getString(3)), c.getLong(4))
                }
            }
        }
        val roots = listOf(Environment.getExternalStorageDirectory()) +
            (File("/storage").listFiles()?.filter { it.name !in setOf("emulated", "self") } ?: emptyList()) +
            listOf(File("/mnt/usb_storage"), File("/mnt/media_rw"))
        for (root in roots) walk(root, 0, found)
        return found.values.sortedWith(compareBy({ it.folder.lowercase() }, { it.title.lowercase() }))
    }

    private fun walk(dir: File, depth: Int, found: MutableMap<String, Track>) {
        if (depth > 5 || found.size > 5000 || !dir.canRead()) return
        val files = dir.listFiles() ?: return
        for (f in files) {
            if (f.name.startsWith(".") || f.name == "Android") continue
            if (f.isDirectory) walk(f, depth + 1, found)
            else if (f.extension.lowercase() in EXT && f.path !in found && f.length() > 50_000)
                found[f.path] = Track(f.path, f.nameWithoutExtension, "", "", 0)
        }
    }

    private fun clean(s: String?) = s?.takeUnless { it.isBlank() || it == "<unknown>" } ?: ""
}
