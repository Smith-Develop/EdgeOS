package io.edgeos.car.ui

import android.graphics.Canvas
import android.graphics.ColorFilter
import android.graphics.Paint
import android.graphics.Path
import android.graphics.PixelFormat
import android.graphics.RectF
import android.graphics.drawable.Drawable

/** Iconos de trazo fino (como Feather/Lucide), dibujados sobre una rejilla de 24×24. */
enum class Glyph { COMPASS, AUTO, HOME, APPS, PHONE, MUSIC, SYSTEM, NAV, PLAY, PAUSE, NEXT, PREV, PLUS, MINUS, LOCATE, RADIO, WIFI, GPS, BOLT, RESET, MAP, GAUGE, CHECK, BACK }

class GlyphDrawable(private var glyph: Glyph, color: Int, private val stroke: Float = 2f) : Drawable() {
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        this.color = color
        style = Paint.Style.STROKE
        strokeCap = Paint.Cap.ROUND
        strokeJoin = Paint.Join.ROUND
    }
    private val fill = Paint(Paint.ANTI_ALIAS_FLAG).apply { this.color = color; style = Paint.Style.FILL }
    private val path = Path()
    private val r = RectF()

    fun glyph(g: Glyph) {
        if (g != glyph) { glyph = g; invalidateSelf() }
    }

    fun setColor(color: Int) {
        paint.color = color
        fill.color = color
        invalidateSelf()
    }

    override fun draw(c: Canvas) {
        val b = bounds
        val s = minOf(b.width(), b.height()) / 24f
        c.save()
        c.translate(b.left + (b.width() - 24 * s) / 2, b.top + (b.height() - 24 * s) / 2)
        c.scale(s, s)
        paint.strokeWidth = stroke
        path.reset()
        when (glyph) {
            Glyph.COMPASS -> {
                // Aguja: la mitad norte rellena (roja al pintarla) y la sur hueca.
                c.drawCircle(12f, 12f, 9.5f, paint)
                path.moveTo(12f, 4.5f); path.lineTo(15f, 12f); path.lineTo(9f, 12f); path.close()
                c.drawPath(path, fill)
                path.reset()
                path.moveTo(9f, 12f); path.lineTo(15f, 12f); path.lineTo(12f, 19.5f); path.close()
                c.drawPath(path, paint)
            }
            Glyph.AUTO -> {
                // Un móvil que proyecta en la pantalla del coche.
                r.set(2.5f, 7f, 9.5f, 20.5f); c.drawRoundRect(r, 1.8f, 1.8f, paint)
                c.drawLine(5f, 17.8f, 7f, 17.8f, paint)
                r.set(8f, 2f, 24f, 18f); c.drawArc(r, 200f, 70f, false, paint)
                r.set(11.5f, 5.5f, 20.5f, 14.5f); c.drawArc(r, 200f, 70f, false, paint)
                c.drawCircle(16f, 10f, 1.3f, fill)
            }
            Glyph.HOME -> {
                path.moveTo(3f, 10.5f); path.lineTo(12f, 3f); path.lineTo(21f, 10.5f)
                path.moveTo(5.5f, 9f); path.lineTo(5.5f, 20f); path.lineTo(18.5f, 20f); path.lineTo(18.5f, 9f)
                path.moveTo(10f, 20f); path.lineTo(10f, 14f); path.lineTo(14f, 14f); path.lineTo(14f, 20f)
                c.drawPath(path, paint)
            }
            Glyph.APPS -> for (x in listOf(3.5f, 13.5f)) for (y in listOf(3.5f, 13.5f)) {
                r.set(x, y, x + 7f, y + 7f); c.drawRoundRect(r, 2f, 2f, paint)
            }
            Glyph.PHONE -> {
                path.moveTo(6.5f, 3f); path.lineTo(9.5f, 3f); path.lineTo(11f, 7f); path.lineTo(9f, 8.5f)
                path.cubicTo(10f, 11f, 13f, 14f, 15.5f, 15f); path.lineTo(17f, 13f); path.lineTo(21f, 14.5f)
                path.lineTo(21f, 17.5f); path.cubicTo(21f, 19.5f, 19.5f, 21f, 17.5f, 21f)
                path.cubicTo(10f, 20.5f, 3.5f, 14f, 3f, 6.5f); path.cubicTo(3f, 4.5f, 4.5f, 3f, 6.5f, 3f)
                c.drawPath(path, paint)
            }
            Glyph.MUSIC -> {
                path.moveTo(9f, 18f); path.lineTo(9f, 5f); path.lineTo(20f, 3f); path.lineTo(20f, 16f)
                c.drawPath(path, paint)
                c.drawCircle(6.5f, 18f, 2.6f, paint); c.drawCircle(17.5f, 16f, 2.6f, paint)
            }
            Glyph.SYSTEM -> {
                for ((y, x) in listOf(6f to 15f, 12f to 8f, 18f to 13f)) {
                    c.drawLine(3f, y, 21f, y, paint)
                    c.drawCircle(x, y, 2.2f, fill)
                }
            }
            Glyph.NAV -> {
                path.moveTo(3f, 11f); path.lineTo(21f, 3f); path.lineTo(13f, 21f); path.lineTo(11f, 13f); path.close()
                c.drawPath(path, paint)
            }
            Glyph.PLAY -> {
                path.moveTo(7f, 4.5f); path.lineTo(19f, 12f); path.lineTo(7f, 19.5f); path.close()
                c.drawPath(path, fill)
            }
            Glyph.PAUSE -> {
                r.set(6f, 4.5f, 10f, 19.5f); c.drawRoundRect(r, 1.2f, 1.2f, fill)
                r.set(14f, 4.5f, 18f, 19.5f); c.drawRoundRect(r, 1.2f, 1.2f, fill)
            }
            Glyph.NEXT -> {
                path.moveTo(5f, 5f); path.lineTo(15f, 12f); path.lineTo(5f, 19f); path.close(); c.drawPath(path, fill)
                c.drawLine(18.5f, 5f, 18.5f, 19f, paint)
            }
            Glyph.PREV -> {
                path.moveTo(19f, 5f); path.lineTo(9f, 12f); path.lineTo(19f, 19f); path.close(); c.drawPath(path, fill)
                c.drawLine(5.5f, 5f, 5.5f, 19f, paint)
            }
            Glyph.PLUS -> { c.drawLine(12f, 5f, 12f, 19f, paint); c.drawLine(5f, 12f, 19f, 12f, paint) }
            Glyph.MINUS -> c.drawLine(5f, 12f, 19f, 12f, paint)
            Glyph.LOCATE -> {
                c.drawCircle(12f, 12f, 7f, paint); c.drawCircle(12f, 12f, 2.5f, fill)
                c.drawLine(12f, 1.5f, 12f, 4.5f, paint); c.drawLine(12f, 19.5f, 12f, 22.5f, paint)
                c.drawLine(1.5f, 12f, 4.5f, 12f, paint); c.drawLine(19.5f, 12f, 22.5f, 12f, paint)
            }
            Glyph.RADIO -> {
                r.set(3f, 8f, 21f, 20f); c.drawRoundRect(r, 2.5f, 2.5f, paint)
                c.drawLine(7f, 8f, 17f, 3f, paint)
                c.drawCircle(15.5f, 14f, 2.6f, paint)
                c.drawLine(6.5f, 12f, 10f, 12f, paint); c.drawLine(6.5f, 16f, 10f, 16f, paint)
            }
            Glyph.WIFI -> {
                r.set(2f, 6f, 22f, 26f); c.drawArc(r, 225f, 90f, false, paint)
                r.set(5.5f, 9.5f, 18.5f, 22.5f); c.drawArc(r, 225f, 90f, false, paint)
                r.set(9f, 13f, 15f, 19f); c.drawArc(r, 225f, 90f, false, paint)
                c.drawCircle(12f, 19.5f, 1.4f, fill)
            }
            Glyph.GPS -> {
                path.moveTo(12f, 21.5f)
                path.cubicTo(12f, 21.5f, 5f, 14.5f, 5f, 9.5f)
                path.cubicTo(5f, 5.4f, 8.1f, 2.5f, 12f, 2.5f)
                path.cubicTo(15.9f, 2.5f, 19f, 5.4f, 19f, 9.5f)
                path.cubicTo(19f, 14.5f, 12f, 21.5f, 12f, 21.5f)
                c.drawPath(path, paint); c.drawCircle(12f, 9.5f, 2.5f, paint)
            }
            Glyph.BOLT -> {
                path.moveTo(13f, 2.5f); path.lineTo(4.5f, 13.5f); path.lineTo(11.5f, 13.5f); path.lineTo(10.5f, 21.5f)
                path.lineTo(19.5f, 10f); path.lineTo(12.5f, 10f); path.close()
                c.drawPath(path, paint)
            }
            Glyph.RESET -> {
                r.set(4f, 4f, 20f, 20f); c.drawArc(r, -60f, 300f, false, paint)
                path.moveTo(20f, 3.5f); path.lineTo(20f, 8.5f); path.lineTo(15f, 8.5f)
                c.drawPath(path, paint)
            }
            Glyph.MAP -> {
                path.moveTo(3f, 6f); path.lineTo(9f, 3.5f); path.lineTo(15f, 6f); path.lineTo(21f, 3.5f)
                path.lineTo(21f, 18f); path.lineTo(15f, 20.5f); path.lineTo(9f, 18f); path.lineTo(3f, 20.5f); path.close()
                c.drawPath(path, paint)
                c.drawLine(9f, 3.5f, 9f, 18f, paint); c.drawLine(15f, 6f, 15f, 20.5f, paint)
            }
            Glyph.GAUGE -> {
                r.set(3f, 5f, 21f, 23f); c.drawArc(r, 160f, 220f, false, paint)
                c.drawLine(12f, 14f, 16.5f, 9f, paint); c.drawCircle(12f, 14f, 1.6f, fill)
            }
            Glyph.CHECK -> {
                path.moveTo(5f, 12.5f); path.lineTo(10f, 17.5f); path.lineTo(19f, 7f)
                c.drawPath(path, paint)
            }
            Glyph.BACK -> {
                path.moveTo(15f, 4f); path.lineTo(7f, 12f); path.lineTo(15f, 20f)
                c.drawPath(path, paint)
            }
        }
        c.restore()
    }

    override fun setAlpha(alpha: Int) {
        paint.alpha = alpha
        fill.alpha = alpha
    }

    override fun setColorFilter(colorFilter: ColorFilter?) {
        paint.colorFilter = colorFilter
        fill.colorFilter = colorFilter
    }

    @Deprecated("Deprecated in Java")
    override fun getOpacity() = PixelFormat.TRANSLUCENT
}
