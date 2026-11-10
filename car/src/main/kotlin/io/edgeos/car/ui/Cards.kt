package io.edgeos.car.ui

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.view.Gravity
import android.view.View
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import io.edgeos.car.App
import android.graphics.Bitmap
import io.edgeos.car.drive.Drive
import io.edgeos.car.drive.DriveState
import io.edgeos.car.drive.NowPlaying
import io.edgeos.car.ui.Ui.MATCH
import io.edgeos.car.ui.Ui.WRAP
import io.edgeos.car.ui.Ui.lp
import io.edgeos.car.ui.Ui.px
import java.util.Locale

fun fmtKm(km: Double): String = if (km < 100) String.format(Locale.forLanguageTag("es"), "%.1f", km) else km.toInt().toString()
fun fmtTime(seconds: Long): String = if (seconds < 3600) "${seconds / 60} min" else "${seconds / 3600} h ${seconds % 3600 / 60} min"

/** Un dato pequeño: etiqueta gris arriba, valor grande abajo. */
class Stat(context: Context, label: String, valueSize: Float = 22f) : LinearLayout(context) {
    private val value: TextView

    init {
        orientation = VERTICAL
        addView(Ui.text(context, label, 12f, Ui.TEXT2))
        value = Ui.text(context, "—", valueSize, Ui.TEXT, Ui.medium)
        addView(value, lp(WRAP, WRAP).apply { topMargin = px(6) })
    }

    fun set(v: String) {
        if (value.text != v) value.text = v
    }
}

/** Tarjeta del coche: el dibujo, la velocidad y dos datos (rumbo y altitud). */
class CarCard(context: Context) : LinearLayout(context) {
    private val gps = Ui.text(context, "Buscando GPS", 12f, Ui.TEXT2, Ui.medium)
    private val speed = Ui.text(context, "0", 76f, Ui.TEXT, Ui.light, Gravity.CENTER)
    private val heading = Stat(context, "Rumbo", 18f)
    private val altitude = Stat(context, "Altitud", 18f)
    val car = CarPicture(context)
    private var shown = -1
    private val speedTween = Tween { v ->
        val n = Math.round(v)
        if (n != shown) { shown = n; speed.text = n.toString() }
    }

    init {
        orientation = VERTICAL
        Ui.card(this)
        setPadding(px(18), px(16), px(18), px(16))
        val head = Ui.row(context)
        head.addView(Ui.text(context, "Mi coche", 16f, Ui.TEXT, Ui.semibold), lp(0, WRAP, 1f))
        gps.background = Ui.rounded(Ui.TILE, 12f)
        gps.setPadding(px(10), px(5), px(10), px(5))
        head.addView(gps)
        addView(head)
        addView(car, lp(MATCH, 0, 1f).apply { topMargin = px(8); bottomMargin = px(6) })
        addView(Ui.text(context, "Velocidad", 12f, Ui.TEXT2, gravity = Gravity.CENTER), lp(MATCH, WRAP))
        addView(speed, lp(MATCH, WRAP).apply { topMargin = px(2) })
        addView(Ui.text(context, "km/h", 13f, Ui.TEXT2, gravity = Gravity.CENTER), lp(MATCH, WRAP))
        val tiles = Ui.row(context)
        for (s in listOf(heading, altitude)) {
            s.background = Ui.rounded(Ui.TILE, 14f)
            s.setPadding(px(12), px(10), px(12), px(10))
            tiles.addView(s, lp(0, WRAP, 1f).apply { if (s === altitude) marginStart = px(10) })
        }
        addView(tiles, lp(MATCH, WRAP).apply { topMargin = px(12) })
    }

    fun setCar(bitmap: Bitmap?) = car.set(bitmap)

    fun update(s: DriveState) {
        gps.text = when {
            s.hasFix -> "GPS · ${s.satellites} sat"
            s.satellites > 0 -> "Buscando · ${s.satellites} sat"
            else -> "Buscando GPS"
        }
        gps.setTextColor(if (s.hasFix) Ui.OK else Ui.TEXT2)
        if (s.hasFix) speedTween.to(s.kmh.toFloat(), 1000) else {
            speedTween.snap(0f)
            speed.text = "—"
        }
        heading.set(s.bearing?.let { "${Drive.compass(it)} ${it.toInt()}°" } ?: "—")
        altitude.set(s.altitude?.let { "$it m" } ?: "—")
    }
}

/** Velocímetro de aguja para el panel de conducción, de 0 a 200 km/h en 240°. */
class Gauge(context: Context) : View(context) {
    var kmh = 0f
        set(v) {
            if (field != v) { field = v; invalidate() }
        }
    private val track = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE; strokeCap = Paint.Cap.ROUND; color = Ui.TILE }
    private val arc = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE; strokeCap = Paint.Cap.ROUND; color = Ui.INK }
    private val tick = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Ui.TEXT2; strokeCap = Paint.Cap.ROUND }
    private val label = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Ui.TEXT2; textAlign = Paint.Align.CENTER; typeface = Ui.regular }
    private val big = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Ui.TEXT; textAlign = Paint.Align.CENTER; typeface = Ui.light }
    private val r = RectF()

    override fun onDraw(c: Canvas) {
        val size = minOf(width.toFloat(), height * 1.15f)
        val w = size * 0.07f
        val cx = width / 2f
        val cy = height * 0.55f
        val rad = size / 2f - w
        r.set(cx - rad, cy - rad, cx + rad, cy + rad)
        track.strokeWidth = w
        arc.strokeWidth = w
        c.drawArc(r, 150f, 240f, false, track)
        val sweep = 240f * (kmh.coerceIn(0f, 200f) / 200f)
        if (sweep > 0) c.drawArc(r, 150f, sweep, false, arc)
        tick.strokeWidth = size * 0.008f
        label.textSize = size * 0.045f
        for (v in 0..200 step 20) {
            val a = Math.toRadians(150.0 + 240.0 * v / 200)
            val cos = Math.cos(a).toFloat()
            val sin = Math.sin(a).toFloat()
            val r1 = rad - w * 0.9f
            val r2 = r1 - size * 0.03f
            c.drawLine(cx + cos * r1, cy + sin * r1, cx + cos * r2, cy + sin * r2, tick)
            if (v % 40 == 0) c.drawText(v.toString(), cx + cos * (r2 - size * 0.06f), cy + sin * (r2 - size * 0.06f) + label.textSize / 3, label)
        }
        big.textSize = size * 0.24f
        c.drawText(Math.round(kmh).toString(), cx, cy + big.textSize * 0.3f, big)
        label.textSize = size * 0.05f
        c.drawText("km/h", cx, cy + big.textSize * 0.3f + size * 0.09f, label)
    }
}

/** Panel de conducción: la alternativa al mapa, sin internet. */
class PanelCard(context: Context, onReset: () -> Unit) : LinearLayout(context) {
    private val gauge = Gauge(context)
    private val gaugeTween = Tween { gauge.kmh = it }
    private val distance = Stat(context, "Distancia", 18f)
    private val time = Stat(context, "En marcha", 18f)
    private val avg = Stat(context, "Media", 18f)
    private val max = Stat(context, "Máxima", 18f)

    init {
        orientation = VERTICAL
        Ui.card(this)
        setPadding(px(20), px(16), px(20), px(16))
        val head = Ui.row(context)
        head.addView(Ui.text(context, "Conducción", 16f, Ui.TEXT, Ui.semibold), lp(0, WRAP, 1f))
        val reset = Ui.text(context, "Reiniciar viaje", 13f, Ui.ACCENT, Ui.medium)
        reset.setPadding(px(10), px(6), px(4), px(6))
        reset.setOnClickListener { onReset() }
        head.addView(reset)
        addView(head)
        addView(gauge, lp(MATCH, 0, 1f))
        // En 2×2: con la tarjeta del centro a ~370 px, cuatro en fila no caben.
        for (pair in listOf(distance, time, avg, max).chunked(2)) {
            val stats = Ui.row(context)
            for ((i, s) in pair.withIndex()) {
                s.background = Ui.rounded(Ui.TILE, 14f)
                s.setPadding(px(12), px(10), px(12), px(10))
                stats.addView(s, lp(0, WRAP, 1f).apply { if (i > 0) marginStart = px(10) })
            }
            addView(stats, lp(MATCH, WRAP).apply { if (pair[0] !== distance) topMargin = px(10) })
        }
    }

    fun update(s: DriveState) {
        gaugeTween.to(if (s.hasFix) s.kmh.toFloat() else 0f, 1000)
        distance.set("${fmtKm(s.tripKm)} km")
        time.set(fmtTime(s.tripSeconds))
        avg.set("${s.avgKmh} km/h")
        max.set("${s.maxKmh} km/h")
    }
}

/** Lo que suena: carátula, título, artista y controles. */
class MediaCard(
    context: Context,
    onPrev: () -> Unit,
    onPlay: () -> Unit,
    onNext: () -> Unit,
    onAskAccess: () -> Unit,
) : LinearLayout(context) {
    private val art = ImageView(context)
    private val title = Ui.text(context, "Nada sonando", 17f, Ui.TEXT, Ui.semibold)
    private val artist = Ui.text(context, "", 13f, Ui.TEXT2)
    private val playIcon = GlyphDrawable(Glyph.PLAY, Color.WHITE)
    private val access = Ui.text(context, "Mostrar qué suena", 12f, Ui.ACCENT, Ui.medium)
    private val placeholder = GlyphDrawable(Glyph.MUSIC, Ui.TEXT2, 1.6f)

    init {
        orientation = VERTICAL
        Ui.card(this)
        setPadding(px(18), px(16), px(18), px(16))
        val head = Ui.row(context)
        head.addView(Ui.text(context, "Música", 16f, Ui.TEXT, Ui.semibold), lp(0, WRAP, 1f))
        access.setPadding(px(8), px(4), 0, px(4))
        access.setOnClickListener { onAskAccess() }
        head.addView(access)
        addView(head)

        val body = Ui.row(context)
        art.scaleType = ImageView.ScaleType.CENTER_CROP
        art.outlineProvider = android.view.ViewOutlineProvider.BACKGROUND
        art.background = Ui.rounded(Ui.TILE, 12f)
        art.clipToOutline = true
        art.setImageDrawable(placeholder)
        art.setPadding(px(18), px(18), px(18), px(18))
        body.addView(art, lp(px(76), px(76)))
        val texts = Ui.column(context)
        texts.addView(title)
        texts.addView(artist, lp(WRAP, WRAP).apply { topMargin = px(6) })
        body.addView(texts, lp(0, WRAP, 1f).apply { marginStart = px(14) })
        addView(body, lp(MATCH, 0, 1f).apply { topMargin = px(8) })

        val controls = Ui.row(context)
        controls.gravity = Gravity.CENTER
        controls.addView(button(context, GlyphDrawable(Glyph.PREV, Ui.TEXT), 0, onPrev), lp(px(52), px(52)))
        controls.addView(button(context, playIcon, Ui.INK, onPlay), lp(px(60), px(60)).apply { marginStart = px(24); marginEnd = px(24) })
        controls.addView(button(context, GlyphDrawable(Glyph.NEXT, Ui.TEXT), 0, onNext), lp(px(52), px(52)))
        addView(controls, lp(MATCH, WRAP))
    }

    fun update(now: NowPlaying, hasAccess: Boolean) {
        access.visibility = if (hasAccess) View.GONE else View.VISIBLE
        title.text = now.title ?: if (hasAccess) "Nada sonando" else "Controles de música"
        artist.text = now.artist ?: ""
        if (now.art != null) {
            art.setPadding(0, 0, 0, 0)
            art.setImageBitmap(now.art)
        } else {
            art.setPadding(px(18), px(18), px(18), px(18))
            art.setImageDrawable(placeholder)
        }
        playIcon.glyph(if (now.playing) Glyph.PAUSE else Glyph.PLAY)
    }
}

/** Accesos rápidos en 2×2: radio, teléfono, música, navegar. Mantener pulsado: elegir otra app. */
class QuickCard(context: Context) : LinearLayout(context) {
    private val tiles = mutableListOf<Pair<TextView, TextView>>()

    init {
        orientation = VERTICAL
        Ui.card(this)
        setPadding(px(14), px(14), px(14), px(14))
    }

    fun setup(items: List<Triple<Glyph, String, Pair<() -> Unit, () -> Unit>>>) {
        removeAllViews()
        tiles.clear()
        for (pair in items.chunked(2)) {
            val row = Ui.row(context)
            for ((i, item) in pair.withIndex()) {
                val (glyph, title, actions) = item
                val tile = Ui.column(context)
                tile.background = Ui.pressable(Ui.TILE, 16f)
                tile.setPadding(px(14), px(12), px(12), px(12))
                val icon = View(context)
                icon.background = GlyphDrawable(glyph, Ui.TEXT)
                tile.addView(icon, lp(px(26), px(26)))
                val name = Ui.text(context, title, 14f, Ui.TEXT, Ui.semibold)
                tile.addView(name, lp(WRAP, WRAP).apply { topMargin = px(10) })
                val sub = Ui.text(context, "", 11f, Ui.TEXT2)
                tile.addView(sub, lp(WRAP, WRAP).apply { topMargin = px(3) })
                tile.setOnClickListener { actions.first() }
                tile.setOnLongClickListener { actions.second(); true }
                row.addView(tile, lp(0, MATCH, 1f).apply { if (i > 0) marginStart = px(10) })
                tiles += name to sub
            }
            addView(row, lp(MATCH, 0, 1f).apply { if (childCount > 0) topMargin = px(10) })
        }
    }

    /** El nombre de la app que abre cada acceso, en gris debajo. */
    fun setApps(apps: List<App?>) {
        for ((i, app) in apps.withIndex()) tiles.getOrNull(i)?.second?.text = app?.label ?: "Elegir app"
    }
}

/** Botón redondo con un icono. color 0: sin fondo. */
fun button(context: Context, icon: GlyphDrawable, color: Int, onClick: () -> Unit) = FrameLayout(context).apply {
    background = if (color == 0) Ui.pressable(Color.TRANSPARENT, 40f) else Ui.pressable(color, 40f)
    val v = View(context)
    v.background = icon
    addView(v, FrameLayout.LayoutParams(px(24), px(24), Gravity.CENTER))
    setOnClickListener { onClick() }
}

/**
 * La imagen del coche. Se dibuja a mano, ya reducida al tamaño del hueco: en GPU antiguas (Mali-400) una imagen
 * grande dentro de una tarjeta recortada puede no pintarse, y a su tamaño justo además gasta menos memoria.
 * Sin imagen, dice qué hacer en vez de quedarse en blanco.
 */
class CarPicture(context: Context) : View(context) {
    private var source: Bitmap? = null
    private var fitted: Bitmap? = null
    private val paint = Paint(Paint.FILTER_BITMAP_FLAG or Paint.ANTI_ALIAS_FLAG)
    private val hint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Ui.TEXT2; textAlign = Paint.Align.CENTER; typeface = Ui.medium }

    fun set(bitmap: Bitmap?) {
        source = bitmap
        fitted = null
        invalidate()
    }

    /** Para el diagnóstico de Sistema: tamaño del hueco y de la imagen. */
    fun describe() = "hueco ${width}×$height, imagen ${source?.let { "${it.width}×${it.height}" } ?: "ninguna"}"

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        fitted = null
    }

    override fun onDraw(c: Canvas) {
        val src = source
        if (src == null || width <= 0 || height <= 0) {
            hint.textSize = Ui.pxf(13f)
            c.drawText("Toca para poner tu coche", width / 2f, height / 2f, hint)
            return
        }
        val f = fitted ?: run {
            val scale = minOf(width.toFloat() / src.width, height.toFloat() / src.height)
            val w = (src.width * scale).toInt().coerceAtLeast(1)
            val h = (src.height * scale).toInt().coerceAtLeast(1)
            (if (w == src.width && h == src.height) src else Bitmap.createScaledBitmap(src, w, h, true)).also { fitted = it }
        }
        c.drawBitmap(f, (width - f.width) / 2f, (height - f.height) / 2f, paint)
    }
}
