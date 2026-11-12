package io.edgeos.car.ui

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.os.Handler
import android.os.Looper
import android.view.Choreographer
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.widget.BaseAdapter
import android.widget.HorizontalScrollView
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ListView
import android.widget.TextView
import io.edgeos.car.drive.Media
import io.edgeos.car.drive.NowPlaying
import io.edgeos.car.music.Library
import io.edgeos.car.music.LocalPlayer
import io.edgeos.car.music.Track
import io.edgeos.car.ui.Ui.MATCH
import io.edgeos.car.ui.Ui.WRAP
import io.edgeos.car.ui.Ui.lp
import io.edgeos.car.ui.Ui.px
import java.util.concurrent.Executors

fun fmtMs(ms: Long): String {
    val s = (ms / 1000).coerceAtLeast(0)
    return if (s >= 3600) "%d:%02d:%02d".format(s / 3600, s % 3600 / 60, s % 60) else "%d:%02d".format(s / 60, s % 60)
}

/**
 * Barra de progreso que avanza en cada fotograma (calcula la posición, no la pregunta a la app) y se puede arrastrar
 * para saltar, si la fuente lo permite.
 */
class ProgressBar(context: Context, private val now: () -> NowPlaying, private val onSeek: (Long) -> Unit) :
    View(context), Choreographer.FrameCallback {
    private val track = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Ui.TILE; strokeCap = Paint.Cap.ROUND }
    private val fill = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Ui.INK; strokeCap = Paint.Cap.ROUND }
    private val knob = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Ui.INK }
    private var dragging: Float? = null
    var onTime: ((Long, Long) -> Unit)? = null
    private var running = false

    // Solo anima mientras se ve (ni con la pantalla oculta ni con otra app delante): en el MT6580 cada fotograma cuenta.
    override fun onVisibilityAggregated(isVisible: Boolean) {
        super.onVisibilityAggregated(isVisible)
        animate(isVisible)
    }

    // onVisibilityAggregated llega en Android 7; en Android 6 se usa esto.
    override fun onVisibilityChanged(changedView: View, visibility: Int) {
        super.onVisibilityChanged(changedView, visibility)
        if (android.os.Build.VERSION.SDK_INT < android.os.Build.VERSION_CODES.N) animate(isShown)
    }

    private fun animate(isVisible: Boolean) {
        if (isVisible == running) return
        running = isVisible
        if (isVisible) Choreographer.getInstance().postFrameCallback(this) else Choreographer.getInstance().removeFrameCallback(this)
    }

    override fun doFrame(frameTimeNanos: Long) {
        if (!running) return
        invalidate()
        val n = now()
        onTime?.invoke(n.positionNow(), n.duration)
        Choreographer.getInstance().postFrameCallback(this)
    }

    override fun onDraw(c: Canvas) {
        val n = now()
        val h = Ui.pxf(6f)
        val pad = Ui.pxf(8f)
        val y = height / 2f
        val w = width - 2 * pad
        track.strokeWidth = h
        fill.strokeWidth = h
        c.drawLine(pad, y, pad + w, y, track)
        if (n.duration <= 0) return
        val f = dragging ?: (n.positionNow().toFloat() / n.duration).coerceIn(0f, 1f)
        c.drawLine(pad, y, pad + w * f, y, fill)
        if (n.canSeek) c.drawCircle(pad + w * f, y, Ui.pxf(if (dragging != null) 10f else 7f), knob)
    }

    override fun onTouchEvent(e: MotionEvent): Boolean {
        val n = now()
        if (!n.canSeek || n.duration <= 0) return false
        val f = ((e.x - Ui.pxf(8f)) / (width - Ui.pxf(16f))).coerceIn(0f, 1f)
        when (e.actionMasked) {
            MotionEvent.ACTION_DOWN, MotionEvent.ACTION_MOVE -> dragging = f
            MotionEvent.ACTION_UP -> { dragging = null; onSeek((f * n.duration).toLong()) }
            MotionEvent.ACTION_CANCEL -> dragging = null
        }
        parent?.requestDisallowInterceptTouchEvent(true)
        return true
    }
}

/** Música: lo que suena de cualquier fuente, y las canciones de la radio y de los USB. */
class MusicPage(
    context: Context,
    private val media: Media,
    private val player: LocalPlayer,
    private val hasStorage: () -> Boolean,
    private val askStorage: () -> Unit,
    private val openApp: (String) -> Unit,
) : LinearLayout(context) {
    private val main = Handler(Looper.getMainLooper())
    private val io = Executors.newSingleThreadExecutor()

    private val sources = Ui.row(context)
    private val art = ImageView(context)
    private val placeholder = GlyphDrawable(Glyph.MUSIC, Ui.TEXT2, 1.4f)
    private val title = Ui.text(context, "Nada sonando", 26f, Ui.TEXT, Ui.semibold).apply { maxLines = 2 }
    private val artist = Ui.text(context, "", 17f, Ui.TEXT2)
    private val album = Ui.text(context, "", 14f, Ui.TEXT2)
    private val elapsed = Ui.text(context, "0:00", 13f, Ui.TEXT2)
    private val remaining = Ui.text(context, "", 13f, Ui.TEXT2, gravity = Gravity.END or Gravity.CENTER_VERTICAL)
    private val playIcon = GlyphDrawable(Glyph.PLAY, Ui.ON_INK)
    private val openSource = Ui.text(context, "Abrir la app", 13f, Ui.ACCENT, Ui.medium)

    private val count = Ui.text(context, "", 13f, Ui.TEXT2)
    private val libraryStatus = Ui.text(context, "", 14f, Ui.TEXT2, gravity = Gravity.CENTER).apply { maxLines = 4 }
    private val permission = Ui.text(context, "Permitir acceso a la música", 14f, Ui.ON_INK, Ui.semibold, Gravity.CENTER)
    private val list = ListView(context)
    private var tracks: List<Track> = emptyList()
    private var scanned = false

    private val adapter = object : BaseAdapter() {
        override fun getCount() = tracks.size
        override fun getItem(i: Int) = tracks[i]
        override fun getItemId(i: Int) = i.toLong()
        override fun getView(i: Int, convert: View?, parent: ViewGroup): View {
            val row = (convert as? LinearLayout) ?: Ui.row(context).apply {
                setPadding(px(12), px(10), px(12), px(10))
                val texts = Ui.column(context)
                texts.addView(Ui.text(context, "", 15f, Ui.TEXT, Ui.medium))
                texts.addView(Ui.text(context, "", 12f, Ui.TEXT2), lp(WRAP, WRAP).apply { topMargin = px(3) })
                addView(texts, lp(0, WRAP, 1f))
                addView(Ui.text(context, "", 12f, Ui.TEXT2), lp(WRAP, WRAP).apply { marginStart = px(10) })
            }
            val t = tracks[i]
            val texts = row.getChildAt(0) as LinearLayout
            val playing = player.current?.path == t.path
            (texts.getChildAt(0) as TextView).apply { text = t.title; setTextColor(if (playing) Ui.ACCENT else Ui.TEXT) }
            (texts.getChildAt(1) as TextView).text = listOf(t.artist, t.folder).filter { it.isNotEmpty() }.joinToString(" · ")
            (row.getChildAt(1) as TextView).text = if (t.duration > 0) fmtMs(t.duration) else ""
            row.background = if (playing) Ui.rounded(Ui.TILE, 12f) else null
            return row
        }
    }

    init {
        orientation = HORIZONTAL
        addView(nowCard(), lp(0, MATCH, 0.56f))
        addView(libraryCard(), lp(0, MATCH, 0.44f).apply { marginStart = px(14) })
    }

    private fun nowCard() = Ui.column(context).apply {
        Ui.card(this)
        setPadding(px(22), px(18), px(22), px(18))
        val head = Ui.row(context)
        head.addView(Ui.text(context, "Ahora suena", 16f, Ui.TEXT, Ui.semibold), lp(0, WRAP, 1f))
        openSource.setPadding(px(8), px(4), 0, px(4))
        head.addView(openSource)
        addView(head)
        val scroll = HorizontalScrollView(context).apply { isHorizontalScrollBarEnabled = false }
        scroll.addView(sources)
        addView(scroll, lp(MATCH, WRAP).apply { topMargin = px(10) })

        val body = Ui.row(context)
        body.gravity = Gravity.CENTER_VERTICAL
        art.scaleType = ImageView.ScaleType.CENTER_CROP
        art.background = Ui.rounded(Ui.TILE, 16f)
        art.outlineProvider = android.view.ViewOutlineProvider.BACKGROUND
        art.clipToOutline = true
        body.addView(art, lp(px(200), px(200)))
        val texts = Ui.column(context)
        texts.addView(title)
        texts.addView(artist, lp(MATCH, WRAP).apply { topMargin = px(8) })
        texts.addView(album, lp(MATCH, WRAP).apply { topMargin = px(4) })
        body.addView(texts, lp(0, WRAP, 1f).apply { marginStart = px(22) })
        addView(body, lp(MATCH, 0, 1f).apply { topMargin = px(14) })

        val bar = ProgressBar(context, { media.now }) { media.seekTo(it) }
        bar.onTime = { pos, dur ->
            val e = fmtMs(pos)
            if (elapsed.text != e) elapsed.text = e
            val r = if (dur > 0) "-" + fmtMs(dur - pos) else ""
            if (remaining.text != r) remaining.text = r
        }
        addView(bar, lp(MATCH, px(30)).apply { topMargin = px(8) })
        val times = Ui.row(context)
        times.addView(elapsed, lp(0, WRAP, 1f))
        times.addView(remaining, lp(0, WRAP, 1f))
        addView(times, lp(MATCH, WRAP).apply { setMargins(px(8), 0, px(8), 0) })

        val controls = Ui.row(context)
        controls.gravity = Gravity.CENTER
        controls.addView(button(context, GlyphDrawable(Glyph.PREV, Ui.TEXT), 0) { media.previous() }, lp(px(64), px(64)))
        controls.addView(button(context, playIcon, Ui.INK) { media.playPause() }, lp(px(78), px(78)).apply { marginStart = px(36); marginEnd = px(36) })
        controls.addView(button(context, GlyphDrawable(Glyph.NEXT, Ui.TEXT), 0) { media.next() }, lp(px(64), px(64)))
        addView(controls, lp(MATCH, WRAP).apply { topMargin = px(6) })
    }

    private fun libraryCard() = Ui.column(context).apply {
        Ui.card(this)
        setPadding(px(16), px(18), px(16), px(10))
        val head = Ui.row(context)
        head.addView(Ui.text(context, "Mis canciones", 16f, Ui.TEXT, Ui.semibold))
        head.addView(count, lp(0, WRAP, 1f).apply { marginStart = px(10) })
        val refresh = Ui.text(context, "Buscar de nuevo", 13f, Ui.ACCENT, Ui.medium)
        refresh.setPadding(px(8), px(4), px(6), px(4))
        refresh.setOnClickListener { scan(force = true) }
        head.addView(refresh)
        addView(head, lp(MATCH, WRAP).apply { setMargins(px(6), 0, 0, px(6)) })

        libraryStatus.setPadding(px(16), px(30), px(16), px(12))
        addView(libraryStatus, lp(MATCH, WRAP))
        permission.background = Ui.pressable(Ui.INK, 12f)
        permission.setPadding(px(18), px(12), px(18), px(12))
        permission.setOnClickListener { askStorage() }
        addView(permission, lp(WRAP, WRAP).apply { gravity = Gravity.CENTER_HORIZONTAL })

        list.adapter = adapter
        list.divider = null
        list.selector = android.graphics.drawable.ColorDrawable(Color.TRANSPARENT)
        list.setOnItemClickListener { _, _, i, _ ->
            player.play(tracks, i)
            media.select(context.packageName)
            adapter.notifyDataSetChanged()
        }
        addView(list, lp(MATCH, 0, 1f))
    }

    fun onShow() {
        update(media.now)
        if (!scanned) scan(force = false)
    }

    /** Busca canciones (la primera vez al abrir la pantalla, o con "Buscar de nuevo"). */
    fun scan(force: Boolean) {
        if (!hasStorage()) {
            libraryStatus.text = "Para tocar la música de la radio y de tus memorias USB, EdgeOS Car necesita permiso para leerlas."
            libraryStatus.visibility = View.VISIBLE
            permission.visibility = View.VISIBLE
            list.visibility = View.GONE
            return
        }
        if (scanned && !force) return
        scanned = true
        permission.visibility = View.GONE
        libraryStatus.text = "Buscando canciones…"
        libraryStatus.visibility = View.VISIBLE
        io.execute {
            val found = runCatching { Library.scan(context) }.getOrDefault(emptyList())
            main.post {
                tracks = found
                count.text = if (found.isEmpty()) "" else "${found.size}"
                libraryStatus.text = "No encontré canciones. Copia música (MP3, M4A, FLAC…) a una memoria USB, conéctala y pulsa \"Buscar de nuevo\"."
                libraryStatus.visibility = if (found.isEmpty()) View.VISIBLE else View.GONE
                list.visibility = if (found.isEmpty()) View.GONE else View.VISIBLE
                adapter.notifyDataSetChanged()
            }
        }
    }

    fun update(n: NowPlaying) {
        title.text = n.title ?: if (media.hasAccess()) "Nada sonando" else "Elige una canción"
        artist.text = n.artist ?: ""
        album.text = n.album ?: ""
        if (n.art != null) {
            art.setPadding(0, 0, 0, 0)
            art.setImageBitmap(n.art)
        } else {
            art.setPadding(px(54), px(54), px(54), px(54))
            art.setImageDrawable(placeholder)
        }
        playIcon.glyph(if (n.playing) Glyph.PAUSE else Glyph.PLAY)
        val pkg = n.packageName
        openSource.visibility = if (pkg != null && pkg != context.packageName) View.VISIBLE else View.GONE
        openSource.setOnClickListener { pkg?.let(openApp) }
        paintSources(pkg)
        adapter.notifyDataSetChanged()
    }

    /** Una pastilla por fuente; la que suena, resaltada. Tocar una la elige. */
    private fun paintSources(current: String?) {
        sources.removeAllViews()
        val all = media.sources.map { it.packageName }.distinct()
        if (all.isEmpty()) {
            sources.addView(Ui.text(context, if (media.hasAccess()) "Sin fuentes: pon música en el móvil o en una app"
                else "Para ver otras fuentes: Sistema → Ajustes → Mostrar qué suena", 12f, Ui.TEXT2))
            return
        }
        for (pkg in all) {
            val on = pkg == current
            val chip = Ui.text(context, label(pkg), 13f, if (on) Ui.ON_INK else Ui.TEXT, Ui.medium, Gravity.CENTER)
            chip.background = if (on) Ui.rounded(Ui.INK, 14f) else Ui.pressable(Ui.TILE, 14f)
            chip.setPadding(px(14), px(8), px(14), px(8))
            chip.setOnClickListener { media.select(pkg) }
            sources.addView(chip, lp(WRAP, WRAP).apply { marginEnd = px(8) })
        }
    }

    private fun label(pkg: String): String = when {
        pkg == context.packageName -> "Mis canciones"
        pkg == "com.android.bluetooth" || pkg.contains("bluetooth") -> "Bluetooth"
        else -> runCatching { context.packageManager.run { getApplicationLabel(getApplicationInfo(pkg, 0)).toString() } }.getOrDefault(pkg)
    }
}
