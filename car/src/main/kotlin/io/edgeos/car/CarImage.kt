package io.edgeos.car

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import java.io.File
import java.io.InputStream

/**
 * La foto propia del coche (la alternativa al constructor), copiada a la memoria privada: así funciona aunque se
 * quite la memoria USB. Vista desde arriba, morro hacia arriba, proporción 1:2.
 */
object CarImage {
    private const val MAX_HEIGHT = 1200

    private fun file(context: Context) = File(context.filesDir, "car.png")

    /** La foto del usuario, o null. Llamar fuera del hilo principal. */
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
}
