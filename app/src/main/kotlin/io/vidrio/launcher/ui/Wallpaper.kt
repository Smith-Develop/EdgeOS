package io.vidrio.launcher.ui

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.ImageDecoder
import android.net.Uri
import android.content.Context
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Box
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import java.io.File
import kotlin.math.max

/**
 * El fondo lo dibuja el launcher, no el sistema: el vidrio solo puede desenfocar lo que está en su
 * propia ventana (Android no deja leer el fondo del sistema a apps normales desde Android 13).
 */
@Composable
fun Wallpaper(image: ImageBitmap?, modifier: Modifier = Modifier) {
    if (image != null) {
        Image(image, null, modifier, contentScale = ContentScale.Crop)
    } else {
        // Uno por defecto, en la línea de los de iOS: azul profundo, violeta y un brillo cálido.
        Box(
            modifier.drawBehind {
                drawRect(Brush.verticalGradient(listOf(Color(0xFF0B1E5B), Color(0xFF3A1C71), Color(0xFF12061F))))
                drawRect(
                    Brush.radialGradient(
                        listOf(Color(0xCCFF7A59), Color.Transparent),
                        center = Offset(size.width * 0.15f, size.height * 0.85f),
                        radius = size.maxDimension * 0.55f,
                    )
                )
                drawRect(
                    Brush.radialGradient(
                        listOf(Color(0x995AC8FA), Color.Transparent),
                        center = Offset(size.width * 0.95f, size.height * 0.1f),
                        radius = size.maxDimension * 0.45f,
                    )
                )
            }
        )
    }
}

object WallpaperStore {
    fun load(file: File): ImageBitmap? =
        if (file.exists()) BitmapFactory.decodeFile(file.path)?.asImageBitmap() else null

    /** Copia la imagen elegida, reducida al tamaño de la pantalla. Llamar fuera del hilo principal. */
    fun save(context: Context, uri: Uri, file: File): ImageBitmap {
        val metrics = context.resources.displayMetrics
        val target = max(metrics.widthPixels, metrics.heightPixels)
        val bitmap = ImageDecoder.decodeBitmap(ImageDecoder.createSource(context.contentResolver, uri)) { decoder, info, _ ->
            val longest = max(info.size.width, info.size.height)
            if (longest > target) {
                val scale = target.toFloat() / longest
                decoder.setTargetSize((info.size.width * scale).toInt(), (info.size.height * scale).toInt())
            }
            decoder.allocator = ImageDecoder.ALLOCATOR_SOFTWARE
        }
        val tmp = File(file.path + ".tmp")
        tmp.outputStream().use { bitmap.compress(Bitmap.CompressFormat.JPEG, 92, it) }
        tmp.renameTo(file)
        return bitmap.asImageBitmap()
    }
}
