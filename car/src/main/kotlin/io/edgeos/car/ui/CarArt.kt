package io.edgeos.car.ui

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RadialGradient
import android.graphics.RectF
import android.graphics.Shader
import android.view.View

/**
 * Carrocerías del constructor, vistas desde arriba con el morro hacia arriba, en una rejilla de 100×220.
 * [w]: media anchura; [hood]..[rear]: dónde empiezan parabrisas, techo, luneta y su final; [inset]: cuánto más
 * estrecha es la cabina que la carrocería.
 */
enum class CarBody(
    val title: String,
    val w: Float, val frontR: Float, val hood: Float, val shield: Float, val roof: Float, val rear: Float,
    val tail: Float, val rearR: Float, val inset: Float, val rails: Boolean = false, val bed: Boolean = false,
) {
    COMPACT("Compacto", 40f, 22f, 58f, 88f, 150f, 170f, 200f, 18f, 5f),
    SEDAN("Berlina", 41f, 22f, 62f, 94f, 142f, 166f, 214f, 18f, 6f),
    WAGON("Familiar", 41f, 22f, 60f, 92f, 182f, 198f, 214f, 14f, 5f),
    SUV("SUV", 44f, 16f, 58f, 88f, 182f, 198f, 214f, 12f, 4f, rails = true),
    MPV("Monovolumen", 43f, 24f, 42f, 86f, 186f, 202f, 214f, 12f, 4f),
    SPORT("Deportivo", 43f, 26f, 82f, 112f, 140f, 168f, 212f, 22f, 9f),
    PICKUP("Pick-up", 44f, 16f, 60f, 90f, 124f, 132f, 216f, 8f, 4f, bed = true),
}

/** Colores de carrocería: nombre y color base. */
val CAR_COLORS = listOf(
    "Blanco" to Color.parseColor("#EDEEF0"), "Plata" to Color.parseColor("#B9BEC5"),
    "Gris" to Color.parseColor("#6B7078"), "Negro" to Color.parseColor("#1E2024"),
    "Rojo" to Color.parseColor("#C8102E"), "Granate" to Color.parseColor("#6E1423"),
    "Azul" to Color.parseColor("#1F5FBF"), "Azul noche" to Color.parseColor("#1B2A4A"),
    "Verde" to Color.parseColor("#2E6B4F"), "Naranja" to Color.parseColor("#E8742A"),
    "Amarillo" to Color.parseColor("#F2C230"), "Beige" to Color.parseColor("#CDBA96"),
)

private fun mix(a: Int, b: Int, t: Float) = Color.rgb(
    (Color.red(a) + (Color.red(b) - Color.red(a)) * t).toInt(),
    (Color.green(a) + (Color.green(b) - Color.green(a)) * t).toInt(),
    (Color.blue(a) + (Color.blue(b) - Color.blue(a)) * t).toInt(),
)

/**
 * El coche de Inicio: el dibujo del constructor o una foto del usuario. Se dibuja con trazados (sin imágenes
 * grandes): así se veía bien en la radio, y no depende de cómo trate cada GPU las texturas.
 */
class CarView(context: Context) : View(context) {
    private var body = CarBody.WAGON
    private var color = CAR_COLORS[1].second
    private var photo: Bitmap? = null
    private var fitted: Bitmap? = null

    private val p = Paint(Paint.ANTI_ALIAS_FLAG)
    private val line = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE; strokeCap = Paint.Cap.ROUND }
    private val bitmapPaint = Paint(Paint.FILTER_BITMAP_FLAG or Paint.ANTI_ALIAS_FLAG)
    private val path = Path()
    private val r = RectF()

    fun draw(body: CarBody, color: Int) {
        this.body = body
        this.color = color
        photo = null
        fitted = null
        invalidate()
    }

    fun photo(bitmap: Bitmap) {
        photo = bitmap
        fitted = null
        invalidate()
    }

    fun describe() = if (photo != null) "foto ${photo!!.width}×${photo!!.height} en un hueco de ${width}×$height"
        else "dibujo ${body.title.lowercase()} en un hueco de ${width}×$height"

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        fitted = null
    }

    override fun onDraw(c: Canvas) {
        if (width <= 0 || height <= 0) return
        photo?.let { return drawPhoto(c, it) }
        val s = minOf(width / 110f, height / 230f)
        c.save()
        c.translate((width - 100 * s) / 2, (height - 220 * s) / 2)
        c.scale(s, s)
        paint(c)
        c.restore()
    }

    private fun drawPhoto(c: Canvas, src: Bitmap) {
        val f = fitted ?: run {
            val scale = minOf(width.toFloat() / src.width, height.toFloat() / src.height)
            val w = (src.width * scale).toInt().coerceAtLeast(1)
            val h = (src.height * scale).toInt().coerceAtLeast(1)
            Bitmap.createScaledBitmap(src, w, h, true).also { fitted = it }
        }
        c.drawBitmap(f, (width - f.width) / 2f, (height - f.height) / 2f, bitmapPaint)
    }

    private fun paint(c: Canvas) {
        val b = body
        val left = 50 - b.w
        val right = 50 + b.w
        val top = 4f

        // Sombra en el suelo.
        c.save()
        c.scale(1f, 2.25f, 50f, (top + b.tail) / 2)
        p.shader = RadialGradient(50f, (top + b.tail) / 2, b.w + 10, intArrayOf(Color.argb(if (Ui.dark) 120 else 70, 0, 0, 0), 0), null, Shader.TileMode.CLAMP)
        c.drawCircle(50f, (top + b.tail) / 2, b.w + 10, p)
        c.restore()
        p.shader = null

        // Ruedas, asomando por los lados.
        p.color = Color.parseColor("#141518")
        val front = b.hood - 32
        val back = b.tail - 62
        for (y in listOf(front, back)) {
            r.set(left - 3, y, left + 7, y + 32); c.drawRoundRect(r, 4f, 4f, p)
            r.set(right - 7, y, right + 3, y + 32); c.drawRoundRect(r, 4f, 4f, p)
        }

        // Carrocería: más oscura en los costados y con un brillo en el centro.
        path.reset()
        path.moveTo(50f, top)
        path.cubicTo(50 + b.w * 0.72f, top, right, top + b.frontR * 0.35f, right, top + b.frontR)
        path.lineTo(right, b.tail - b.rearR)
        path.cubicTo(right, b.tail - b.rearR * 0.3f, 50 + b.w * 0.8f, b.tail, 50f, b.tail)
        path.cubicTo(50 - b.w * 0.8f, b.tail, left, b.tail - b.rearR * 0.3f, left, b.tail - b.rearR)
        path.lineTo(left, top + b.frontR)
        path.cubicTo(left, top + b.frontR * 0.35f, 50 - b.w * 0.72f, top, 50f, top)
        path.close()
        val dark = mix(color, Color.BLACK, 0.45f)
        val light = mix(color, Color.WHITE, 0.28f)
        p.shader = LinearGradient(left, 0f, right, 0f, intArrayOf(dark, color, light, color, dark),
            floatArrayOf(0f, 0.24f, 0.5f, 0.76f, 1f), Shader.TileMode.CLAMP)
        c.drawPath(path, p)
        p.shader = null

        val cabin = b.w - b.inset
        // Nervios del capó.
        line.strokeWidth = 0.8f
        line.color = Color.argb(60, 255, 255, 255)
        c.drawLine(50 - cabin * 0.45f, top + 12, 50 - cabin * 0.5f, b.hood - 6, line)
        c.drawLine(50 + cabin * 0.45f, top + 12, 50 + cabin * 0.5f, b.hood - 6, line)

        // Retrovisores.
        p.color = mix(color, Color.BLACK, 0.25f)
        r.set(left - 9, b.hood + 6, left + 3, b.hood + 15); c.drawOval(r, p)
        r.set(right - 3, b.hood + 6, right + 9, b.hood + 15); c.drawOval(r, p)

        // Lunas laterales: dos franjas oscuras junto al techo.
        p.color = Color.parseColor("#16181C")
        r.set(50 - cabin - 1, b.shield - 2, 50 - cabin + 4, b.roof + 2); c.drawRoundRect(r, 2f, 2f, p)
        r.set(50 + cabin - 4, b.shield - 2, 50 + cabin + 1, b.roof + 2); c.drawRoundRect(r, 2f, 2f, p)

        // Parabrisas.
        path.reset()
        path.moveTo(50 - cabin + 4, b.hood + 4)
        path.cubicTo(50 - cabin * 0.4f, b.hood - 3, 50 + cabin * 0.4f, b.hood - 3, 50 + cabin - 4, b.hood + 4)
        path.lineTo(50 + cabin - 1, b.shield)
        path.cubicTo(50 + cabin * 0.4f, b.shield - 4, 50 - cabin * 0.4f, b.shield - 4, 50 - cabin + 1, b.shield)
        path.close()
        p.shader = LinearGradient(0f, b.hood, 0f, b.shield, Color.parseColor("#0C0D0F"), Color.parseColor("#2E3238"), Shader.TileMode.CLAMP)
        c.drawPath(path, p)

        // Techo, del color de la carrocería algo más oscuro, con un reflejo.
        r.set(50 - cabin + 3, b.shield, 50 + cabin - 3, b.roof)
        p.shader = LinearGradient(r.left, 0f, r.right, 0f,
            intArrayOf(mix(color, Color.BLACK, 0.35f), mix(color, Color.WHITE, 0.12f), mix(color, Color.BLACK, 0.35f)), null, Shader.TileMode.CLAMP)
        c.drawRoundRect(r, 8f, 8f, p)
        p.shader = null
        line.color = Color.argb(70, 255, 255, 255)
        line.strokeWidth = 1.1f
        c.drawLine(50 - cabin * 0.45f, b.shield + 6, 50 - cabin * 0.5f, b.roof - 6, line)
        if (b.rails) {
            line.color = Color.argb(150, 20, 20, 24)
            line.strokeWidth = 2f
            c.drawLine(50 - cabin + 7, b.shield + 8, 50 - cabin + 7, b.roof - 8, line)
            c.drawLine(50 + cabin - 7, b.shield + 8, 50 + cabin - 7, b.roof - 8, line)
        }

        // Luneta.
        path.reset()
        path.moveTo(50 - cabin + 2, b.roof)
        path.cubicTo(50 - cabin * 0.4f, b.roof - 3, 50 + cabin * 0.4f, b.roof - 3, 50 + cabin - 2, b.roof)
        path.lineTo(50 + cabin - 6, b.rear)
        path.cubicTo(50 + cabin * 0.4f, b.rear + 3, 50 - cabin * 0.4f, b.rear + 3, 50 - cabin + 6, b.rear)
        path.close()
        p.shader = LinearGradient(0f, b.roof, 0f, b.rear, Color.parseColor("#2E3238"), Color.parseColor("#0C0D0F"), Shader.TileMode.CLAMP)
        c.drawPath(path, p)
        p.shader = null

        // Caja del pick-up.
        if (b.bed) {
            r.set(left + 6, b.rear + 6, right - 6, b.tail - 8)
            p.color = mix(color, Color.BLACK, 0.6f)
            c.drawRoundRect(r, 3f, 3f, p)
            line.color = Color.argb(50, 255, 255, 255)
            line.strokeWidth = 0.8f
            var y = r.top + 10
            while (y < r.bottom - 4) { c.drawLine(r.left + 4, y, r.right - 4, y, line); y += 12 }
        }

        // Faros y pilotos.
        line.strokeWidth = 2.4f
        line.color = Color.parseColor("#EEF4FF")
        c.drawLine(left + 8, top + 12, left + 22, top + 6, line)
        c.drawLine(right - 8, top + 12, right - 22, top + 6, line)
        line.color = Color.parseColor("#E5383B")
        c.drawLine(left + 8, b.tail - 5, left + 26, b.tail - 3, line)
        c.drawLine(right - 8, b.tail - 5, right - 26, b.tail - 3, line)
    }
}
