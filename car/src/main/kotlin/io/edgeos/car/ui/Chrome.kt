package io.edgeos.car.ui

import android.content.Context
import android.graphics.Color
import android.view.Gravity
import android.view.View
import android.widget.FrameLayout
import android.widget.LinearLayout
import io.edgeos.car.ui.Ui.WRAP
import io.edgeos.car.ui.Ui.lp
import io.edgeos.car.ui.Ui.px

/** Barra lateral de iconos. Los de página se marcan al estar activos; los demás solo abren algo. */
class Rail(context: Context) : LinearLayout(context) {
    private val pages = mutableMapOf<Any, FrameLayout>()

    init {
        orientation = VERTICAL
        gravity = Gravity.CENTER_HORIZONTAL
        setBackgroundColor(Ui.CARD)
        elevation = Ui.pxf(2f)
        setPadding(0, px(18), 0, px(18))
    }

    fun item(glyph: Glyph, page: Any? = null, action: () -> Unit) {
        val f = FrameLayout(context)
        f.background = Ui.pressable(Color.TRANSPARENT, 16f)
        val icon = View(context)
        icon.background = GlyphDrawable(glyph, Ui.TEXT)
        f.addView(icon, FrameLayout.LayoutParams(px(26), px(26), Gravity.CENTER))
        f.setOnClickListener { action() }
        addView(f, lp(px(60), px(60)).apply { bottomMargin = px(14) })
        if (page != null) pages[page] = f
    }

    /** Empuja lo que venga después al fondo. */
    fun spacer() = addView(View(context), lp(1, 0, 1f))

    fun select(page: Any) {
        for ((p, v) in pages) v.background = if (p == page) Ui.rounded(Ui.TILE, 16f) else Ui.pressable(Color.TRANSPARENT, 16f)
    }
}

/** Fecha a la izquierda; GPS, Wi-Fi y hora a la derecha. */
class TopBar(context: Context) : LinearLayout(context) {
    private val date = Ui.text(context, "", 15f, Ui.TEXT, Ui.medium)
    private val clock = Ui.text(context, "", 17f, Ui.TEXT, Ui.semibold)
    private val gps = GlyphDrawable(Glyph.GPS, Ui.TEXT2)
    private val wifi = View(context).apply { background = GlyphDrawable(Glyph.WIFI, Ui.TEXT) }

    init {
        orientation = HORIZONTAL
        gravity = Gravity.CENTER_VERTICAL
        setPadding(px(4), 0, px(4), 0)
        addView(date, lp(0, WRAP, 1f))
        addView(View(context).apply { background = gps }, lp(px(20), px(20)))
        addView(wifi, lp(px(20), px(20)).apply { marginStart = px(14) })
        addView(clock, lp(WRAP, WRAP).apply { marginStart = px(16) })
    }

    fun set(date: String, time: String, online: Boolean) {
        this.date.text = date
        clock.text = time
        wifi.visibility = if (online) View.VISIBLE else View.GONE
    }

    fun gps(fix: Boolean) = gps.setColor(if (fix) Ui.OK else Ui.TEXT2)
}
