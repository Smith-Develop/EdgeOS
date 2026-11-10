package io.edgeos.car.maps

import android.app.DownloadManager
import android.content.Context
import android.net.Uri
import android.os.StatFs
import java.io.File
import java.net.HttpURLConnection
import java.net.URL

/** Una entrada del catálogo: carpeta (región con subregiones) o archivo .map. */
data class MapEntry(val path: String, val name: String, val folder: Boolean, val size: String)

/** Un mapa bajándose ahora: progreso de 0 a 1 (o -1 si aún no se sabe el tamaño). */
data class MapDownload(val id: Long, val name: String, val progress: Float)

/**
 * Mapas vectoriales sin conexión (archivos .map de Mapsforge, datos de OpenStreetMap, ODbL). El catálogo lo lee la
 * propia radio del servidor de Mapsforge, así que se adapta a lo que haya: regiones donde existan, países donde no.
 * Se bajan con el gestor de descargas de Android a la carpeta privada de la app (sin permisos).
 */
object Maps {
    const val SERVER = "https://download.mapsforge.org/maps/v5/"

    fun dir(context: Context): File = File(context.getExternalFilesDir(null) ?: context.filesDir, "maps").apply { mkdirs() }

    /** Los mapas ya descargados (no los que se están bajando). */
    fun installed(context: Context): List<File> {
        val busy = downloads(context).map { it.name }.toSet()
        return dir(context).listFiles { f -> f.isFile && f.name.endsWith(".map") && f.length() > 0 && f.name !in busy }
            ?.sortedBy { it.name } ?: emptyList()
    }

    fun delete(file: File) = file.delete()

    fun freeBytes(context: Context): Long = runCatching { StatFs(dir(context).path).availableBytes }.getOrDefault(0)

    /** Lo que hay en una carpeta del servidor (por ejemplo "europe/spain/"). Llamar fuera del hilo principal. */
    fun catalog(path: String): List<MapEntry> {
        val c = URL(SERVER + path).openConnection() as HttpURLConnection
        c.connectTimeout = 15_000
        c.readTimeout = 20_000
        val html = try {
            c.inputStream.bufferedReader().use { it.readText() }
        } finally {
            c.disconnect()
        }
        return parse(path, html)
    }

    /** Índice de Apache, en tabla o en <pre>: el enlace y, en la misma fila, el tamaño ("380M", "1.2G"). */
    fun parse(path: String, html: String): List<MapEntry> {
        val entries = mutableListOf<MapEntry>()
        for (line in html.split("\n", "<tr>")) {
            val m = Regex("""<a href="([^"?/][^"]*)">""").find(line) ?: continue
            val href = java.net.URLDecoder.decode(m.groupValues[1], "UTF-8")
            if (href.startsWith("http") || href.startsWith("/") || href.startsWith(".")) continue
            val folder = href.endsWith("/")
            if (!folder && !href.endsWith(".map")) continue
            val size = Regex("""(\d+(?:\.\d+)?[KMG])\b""").findAll(line.substring(m.range.last)).lastOrNull()?.value ?: ""
            entries += MapEntry(path + href, pretty(href.removeSuffix("/").removeSuffix(".map")), folder, if (folder) "" else size)
        }
        return entries.distinctBy { it.path }
    }

    private fun pretty(s: String) = s.replace('-', ' ').replace('_', ' ').replaceFirstChar { it.uppercase() }

    /**
     * El mapa de la Comunidad de Madrid: la región si el servidor la tiene por separado y, si no, España entera.
     * Llamar fuera del hilo principal.
     */
    fun madrid(): MapEntry? {
        val regions = runCatching { catalog("europe/spain/") }.getOrDefault(emptyList())
        regions.firstOrNull { !it.folder && it.path.contains("madrid", ignoreCase = true) }?.let { return it }
        return catalog("europe/").firstOrNull { !it.folder && it.path.endsWith("/spain.map") }
    }

    fun download(context: Context, entry: MapEntry): Long {
        val name = entry.path.substringAfterLast('/')
        val request = DownloadManager.Request(Uri.parse(SERVER + entry.path))
            .setTitle("Mapa sin conexión: ${entry.name}")
            .setDescription("EdgeOS Car")
            .setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE)
            .setDestinationUri(Uri.fromFile(File(dir(context), name)))
        File(dir(context), name).delete()
        return context.getSystemService(DownloadManager::class.java).enqueue(request)
    }

    fun cancel(context: Context, id: Long) {
        context.getSystemService(DownloadManager::class.java).remove(id)
    }

    /** Las descargas de mapas en curso. */
    fun downloads(context: Context): List<MapDownload> {
        val dm = context.getSystemService(DownloadManager::class.java)
        val q = DownloadManager.Query().setFilterByStatus(
            DownloadManager.STATUS_PENDING or DownloadManager.STATUS_RUNNING or DownloadManager.STATUS_PAUSED)
        val out = mutableListOf<MapDownload>()
        runCatching {
            dm.query(q)?.use { c ->
                val iId = c.getColumnIndex(DownloadManager.COLUMN_ID)
                val iUri = c.getColumnIndex(DownloadManager.COLUMN_LOCAL_URI)
                val iDone = c.getColumnIndex(DownloadManager.COLUMN_BYTES_DOWNLOADED_SO_FAR)
                val iTotal = c.getColumnIndex(DownloadManager.COLUMN_TOTAL_SIZE_BYTES)
                val iUrl = c.getColumnIndex(DownloadManager.COLUMN_URI)
                while (c.moveToNext()) {
                    val url = c.getString(iUrl) ?: continue
                    if (!url.startsWith(SERVER)) continue
                    val name = (c.getString(iUri) ?: url).substringAfterLast('/')
                    val total = c.getLong(iTotal)
                    out += MapDownload(c.getLong(iId), name, if (total > 0) c.getLong(iDone).toFloat() / total else -1f)
                }
            }
        }
        return out
    }

    /** "380M" → bytes, para avisar si no cabe. */
    fun bytes(size: String): Long {
        val n = size.dropLast(1).toDoubleOrNull() ?: return 0
        return (n * when (size.lastOrNull()) { 'G' -> 1e9; 'M' -> 1e6; 'K' -> 1e3; else -> 1.0 }).toLong()
    }
}
