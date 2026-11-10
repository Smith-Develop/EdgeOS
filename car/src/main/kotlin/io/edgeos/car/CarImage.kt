package io.edgeos.car

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import java.io.File
import java.io.InputStream

/**
 * La imagen del coche de Inicio: la que eligió el usuario (copiada a la memoria privada, así funciona aunque se
 * quite la memoria USB) o, si no hay, la de serie. Vista desde arriba, morro hacia arriba, proporción 1:2.
 */
object CarImage {
    const val DEFAULT = R.drawable.car_default
    private const val MAX_HEIGHT = 1200

    private fun file(context: Context) = File(context.filesDir, "car.png")

    /** Qué se cargó la última vez (de serie, la tuya o un error), para el diagnóstico de Sistema. */
    @Volatile
    var status = "sin cargar"
        private set

    /** La del usuario si hay, y si no la de serie. Llamar fuera del hilo principal. */
    fun load(context: Context): Bitmap? {
        val own = file(context).takeIf { it.exists() }?.let { BitmapFactory.decodeFile(it.path) }
        if (own != null) return own.also { status = "la tuya" }
        val stock = runCatching { BitmapFactory.decodeResource(context.resources, DEFAULT) }.getOrNull()
        status = if (stock != null) "la de serie" else "error: no se pudo leer la de serie"
        return stock
    }

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
