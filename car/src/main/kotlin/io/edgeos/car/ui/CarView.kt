package io.edgeos.car.ui

import android.content.Context
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
 * Un coche visto desde arriba, con el morro hacia arriba. Dibujado (no es una foto) en una rejilla de 100×220
 * que se escala a la vista. Sombras con degradados: BlurMaskFilter no funciona con aceleración por hardware
 * en Android 8.
 */
class CarView(context: Context) : View(context) {
    private val p = Paint(Paint.ANTI_ALIAS_FLAG)
    private val stroke = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE; strokeCap = Paint.Cap.ROUND }
    private val body = Path()
    private val glass = Path()
    private val r = RectF()
    private var built = false

    private fun build() {
        body.reset()
        // Silueta: morro estrecho y redondeado, caderas anchas detrás de las puertas, cola recta.
        body.moveTo(50f, 4f)
        body.cubicTo(72f, 4f, 86f, 10f, 88f, 30f)
        body.cubicTo(90f, 55f, 88f, 80f, 89f, 105f)
        body.cubicTo(90f, 140f, 94f, 165f, 92f, 190f)
        body.cubicTo(91f, 208f, 80f, 216f, 50f, 216f)
        body.cubicTo(20f, 216f, 9f, 208f, 8f, 190f)
        body.cubicTo(6f, 165f, 10f, 140f, 11f, 105f)
        body.cubicTo(12f, 80f, 10f, 55f, 12f, 30f)
        body.cubicTo(14f, 10f, 28f, 4f, 50f, 4f)
        body.close()
        built = true
    }

    override fun onDraw(c: Canvas) {
        if (!built) build()
        val s = minOf(width / 110f, height / 230f)
        c.save()
        c.translate((width - 100 * s) / 2, (height - 220 * s) / 2)
        c.scale(s, s)

        // Sombra en el suelo.
        c.save()
        c.scale(1f, 2.3f, 50f, 110f)
        p.shader = RadialGradient(50f, 110f, 52f, intArrayOf(Color.argb(70, 0, 0, 0), Color.argb(0, 0, 0, 0)), null, Shader.TileMode.CLAMP)
        c.drawCircle(50f, 110f, 52f, p)
        c.restore()
        p.shader = null

        // Ruedas, que asoman por los lados.
        p.color = Color.parseColor("#1A1B1E")
        for (y in listOf(38f, 160f)) {
            r.set(5f, y, 14f, y + 32f); c.drawRoundRect(r, 4f, 4f, p)
            r.set(86f, y, 95f, y + 32f); c.drawRoundRect(r, 4f, 4f, p)
        }

        // Carrocería grafito, más clara en el centro.
        p.shader = LinearGradient(8f, 0f, 92f, 0f,
            intArrayOf(Color.parseColor("#1F2023"), Color.parseColor("#4B4E54"), Color.parseColor("#5A5D63"), Color.parseColor("#4B4E54"), Color.parseColor("#1F2023")),
            floatArrayOf(0f, 0.3f, 0.5f, 0.7f, 1f), Shader.TileMode.CLAMP)
        c.drawPath(body, p)
        p.shader = null

        // Nervios del capó.
        stroke.strokeWidth = 0.8f
        stroke.color = Color.argb(70, 255, 255, 255)
        c.drawLine(36f, 14f, 38f, 62f, stroke)
        c.drawLine(64f, 14f, 62f, 62f, stroke)

        // Retrovisores.
        p.color = Color.parseColor("#2A2C30")
        r.set(1f, 70f, 13f, 80f); c.drawOval(r, p)
        r.set(87f, 70f, 99f, 80f); c.drawOval(r, p)

        // Parabrisas.
        glass.reset()
        glass.moveTo(22f, 66f); glass.cubicTo(35f, 58f, 65f, 58f, 78f, 66f)
        glass.lineTo(72f, 96f); glass.cubicTo(58f, 92f, 42f, 92f, 28f, 96f); glass.close()
        p.shader = LinearGradient(0f, 60f, 0f, 96f, Color.parseColor("#0C0D0F"), Color.parseColor("#2C2F34"), Shader.TileMode.CLAMP)
        c.drawPath(glass, p)

        // Techo (de cristal oscuro) con un reflejo.
        r.set(27f, 98f, 73f, 160f)
        p.shader = LinearGradient(27f, 0f, 73f, 0f,
            intArrayOf(Color.parseColor("#141518"), Color.parseColor("#2E3136"), Color.parseColor("#141518")), null, Shader.TileMode.CLAMP)
        c.drawRoundRect(r, 10f, 10f, p)
        p.shader = null
        stroke.color = Color.argb(60, 255, 255, 255)
        stroke.strokeWidth = 1.2f
        c.drawLine(36f, 104f, 33f, 152f, stroke)

        // Luneta.
        glass.reset()
        glass.moveTo(29f, 163f); glass.cubicTo(42f, 160f, 58f, 160f, 71f, 163f)
        glass.lineTo(75f, 182f); glass.cubicTo(60f, 186f, 40f, 186f, 25f, 182f); glass.close()
        p.shader = LinearGradient(0f, 160f, 0f, 186f, Color.parseColor("#2C2F34"), Color.parseColor("#0C0D0F"), Shader.TileMode.CLAMP)
        c.drawPath(glass, p)
        p.shader = null

        // Faros y pilotos.
        stroke.strokeWidth = 2.2f
        stroke.color = Color.parseColor("#E8F0FF")
        c.drawLine(20f, 14f, 32f, 9f, stroke); c.drawLine(80f, 14f, 68f, 9f, stroke)
        stroke.color = Color.parseColor("#E5383B")
        c.drawLine(16f, 209f, 84f, 209f, stroke)

        c.restore()
    }
}
