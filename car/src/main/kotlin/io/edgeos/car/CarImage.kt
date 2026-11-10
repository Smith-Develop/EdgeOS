package io.edgeos.car

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.os.Environment
import java.io.File
import java.io.InputStream

/**
 * La imagen del coche de Inicio: la que eligió el usuario (copiada a la memoria privada, así funciona aunque se
 * quite la memoria USB) o, si no hay, la de serie. Vista desde arriba, morro hacia arriba, proporción 1:2.
 */
object CarImage {
    const val DEFAULT = R.drawable.car_default
    private const val MAX_HEIGHT = 1200
    val NAMES = listOf("coche.png", "coche.webp", "coche.jpg", "coche.jpeg")

    private fun file(context: Context) = File(context.filesDir, "car.png")

    /** La del usuario, o null para la de serie. */
    fun load(context: Context): Bitmap? = file(context).takeIf { it.exists() }?.let { BitmapFactory.decodeFile(it.path) }

    fun reset(context: Context) {
        file(context).delete()
    }

    /** Copia la imagen elegida, reducida a 1200 px de alto como mucho. Llamar fuera del hilo principal. */
    fun save(context: Context, uri: Uri): Bitmap? = save(context) { context.contentResolver.openInputStream(uri) }

    fun save(context: Context, source: File): Bitmap? = save(context) { source.inputStream() }

    private fun save(context: Context, open: () -> InputStream?): Bitmap? {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        open()?.use { BitmapFactory.decodeStream(it, null, bounds) }
        if (bounds.outHeight <= 0) return null
        var sample = 1
        while (bounds.outHeight / (sample * 2) >= MAX_HEIGHT) sample *= 2
        val bitmap = open()?.use { BitmapFactory.decodeStream(it, null, BitmapFactory.Options().apply { inSampleSize = sample }) }
            ?: return null
        val tmp = File(file(context).path + ".tmp")
        tmp.outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it); it.fd.sync() }
        tmp.renameTo(file(context))
        return bitmap
    }

    /**
     * Para radios sin selector de archivos (algunas quitan DocumentsUI): busca "coche.png" en la raíz de la
     * memoria interna y de las memorias USB/SD. Necesita el permiso de almacenamiento.
     */
    fun findOnStorage(): File? {
        val roots = listOf(Environment.getExternalStorageDirectory()) +
            (File("/storage").listFiles()?.toList() ?: emptyList()) + (File("/mnt/media_rw").listFiles()?.toList() ?: emptyList())
        return roots.asSequence().flatMap { root -> NAMES.asSequence().map { File(root, it) } }.firstOrNull { it.canRead() }
    }
}
