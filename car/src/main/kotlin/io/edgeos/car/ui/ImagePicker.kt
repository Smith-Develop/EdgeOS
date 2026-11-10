package io.edgeos.car.ui

import android.app.AlertDialog
import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.os.Environment
import android.os.Handler
import android.os.Looper
import android.view.View
import android.view.ViewGroup
import android.widget.BaseAdapter
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import io.edgeos.car.ui.Ui.WRAP
import io.edgeos.car.ui.Ui.lp
import io.edgeos.car.ui.Ui.px
import java.io.File
import java.util.concurrent.Executors

/**
 * Selector de imágenes propio: muchas radios no traen gestor de archivos, o el que traen no devuelve nada.
 * Busca fotos en la memoria interna (Descargas, Imágenes, DCIM) y en las memorias USB/SD. Necesita el permiso
 * de almacenamiento.
 */
object ImagePicker {
    private val EXT = setOf("png", "jpg", "jpeg", "webp")

    /** Carpetas donde estas radios montan la memoria interna, las USB y las SD. */
    private fun roots(): List<File> {
        val internal = Environment.getExternalStorageDirectory()
        val list = mutableListOf(internal)
        listOf("Download", "Pictures", "DCIM", "DCIM/Camera").forEach { list += File(internal, it) }
        for (base in listOf("/storage", "/mnt", "/mnt/media_rw", "/mnt/usb_storage")) {
            File(base).listFiles()?.filter { it.isDirectory && it.canRead() && it.name !in setOf("emulated", "self", "runtime", "secure", "asec", "obb", "user") }
                ?.forEach { usb -> list += usb; usb.listFiles()?.filter { it.isDirectory }?.forEach { list += it } }
        }
        return list.distinctBy { runCatching { it.canonicalPath }.getOrDefault(it.path) }
    }

    /** Imágenes encontradas, las más recientes primero. Llamar fuera del hilo principal. */
    fun find(): List<File> = roots().asSequence()
        .flatMap { (it.listFiles() ?: emptyArray()).asSequence() }
        .filter { it.isFile && it.extension.lowercase() in EXT && it.length() > 1024 }
        .distinctBy { it.absolutePath }
        .sortedByDescending { it.lastModified() }
        .take(300)
        .toList()

    fun show(context: Context, files: List<File>, onPick: (File) -> Unit, onSystem: () -> Unit) {
        val main = Handler(Looper.getMainLooper())
        val io = Executors.newSingleThreadExecutor()
        val thumbs = HashMap<String, Bitmap?>()
        val adapter = object : BaseAdapter() {
            override fun getCount() = files.size
            override fun getItem(i: Int) = files[i]
            override fun getItemId(i: Int) = i.toLong()
            override fun getView(i: Int, convert: View?, parent: ViewGroup): View {
                val row = (convert as? LinearLayout) ?: Ui.row(context).apply {
                    setPadding(px(16), px(8), px(16), px(8))
                    addView(ImageView(context).apply { scaleType = ImageView.ScaleType.FIT_CENTER; background = Ui.rounded(Ui.TILE, 8f) }, lp(px(56), px(56)))
                    val texts = Ui.column(context)
                    texts.addView(Ui.text(context, "", 15f, Ui.TEXT, Ui.medium))
                    texts.addView(Ui.text(context, "", 12f, Ui.TEXT2), lp(WRAP, WRAP).apply { topMargin = px(3) })
                    addView(texts, lp(0, WRAP, 1f).apply { marginStart = px(14) })
                }
                val f = files[i]
                val texts = row.getChildAt(1) as LinearLayout
                (texts.getChildAt(0) as TextView).text = f.name
                (texts.getChildAt(1) as TextView).text = f.parent
                val image = row.getChildAt(0) as ImageView
                image.tag = f.path
                val cached = thumbs[f.path]
                image.setImageBitmap(cached)
                if (!thumbs.containsKey(f.path)) io.execute {
                    val b = thumbnail(f)
                    main.post {
                        thumbs[f.path] = b
                        if (image.tag == f.path) image.setImageBitmap(b)
                    }
                }
                return row
            }
        }
        AlertDialog.Builder(context)
            .setTitle(if (files.isEmpty()) "No encontré imágenes" else "Elige la imagen del coche")
            .apply {
                if (files.isEmpty()) setMessage("Copia la imagen (PNG o JPG) a una memoria USB, conéctala y vuelve a intentarlo.")
                else setAdapter(adapter) { _, i -> onPick(files[i]) }
            }
            .setNeutralButton("Gestor de archivos del sistema") { _, _ -> onSystem() }
            .setNegativeButton("Cancelar", null)
            .setOnDismissListener { io.shutdownNow() }
            .show()
    }

    private fun thumbnail(f: File): Bitmap? = runCatching {
        val o = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeFile(f.path, o)
        var s = 1
        while (maxOf(o.outWidth, o.outHeight) / (s * 2) >= 128) s *= 2
        BitmapFactory.decodeFile(f.path, BitmapFactory.Options().apply { inSampleSize = s })
    }.getOrNull()
}
