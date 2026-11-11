package io.edgeos.car.ui

import android.content.Context
import android.view.Gravity
import android.view.View
import android.widget.FrameLayout
import android.widget.LinearLayout
import io.edgeos.car.ui.Ui.MATCH
import io.edgeos.car.ui.Ui.WRAP
import io.edgeos.car.ui.Ui.lp
import io.edgeos.car.ui.Ui.px

/**
 * Presentación del primer arranque: qué es EdgeOS Car, sus ventajas, los permisos y cómo hacerlo el inicio
 * predeterminado. Se puede saltar y volver a ver desde Sistema → Ajustes.
 */
class Onboarding(
    context: Context,
    private val car: (CarView) -> Unit,
    private val hasLocation: () -> Boolean,
    private val hasMedia: () -> Boolean,
    private val isDefaultHome: () -> Boolean,
    private val askLocation: () -> Unit,
    private val askMedia: () -> Unit,
    private val makeDefault: () -> Unit,
    private val onDone: () -> Unit,
    startPage: Int = 0,
) : FrameLayout(context) {
    var page = startPage
        private set
    private val content = FrameLayout(context)
    private val dots = Ui.row(context)
    private val back = Ui.text(context, "Atrás", 15f, Ui.TEXT2, Ui.semibold, Gravity.CENTER)
    private val next = Ui.text(context, "Siguiente", 15f, Ui.ON_INK, Ui.semibold, Gravity.CENTER)
    private val statusViews = mutableListOf<() -> Unit>()

    private class Page(val title: String, val body: String, val visual: () -> View, val extra: (LinearLayout) -> Unit = {})

    private val pages = listOf(
        Page("Bienvenido a EdgeOS Car",
            "Tu radio, con una sola interfaz clara y rápida, pensada para mirarla de reojo mientras conduces.",
            { CarView(context).also(car) }) { box ->
            for (benefit in listOf(
                "Velocidad por GPS y mapa que funciona sin internet",
                "La música de cualquier fuente en una sola pantalla",
                "Más rápida: sin apps de fábrica estorbando",
            )) box.addView(check(benefit, true), lp(MATCH, WRAP).apply { topMargin = px(10) })
        },
        Page("Todo en un sitio",
            "Mapa, música, Android Auto y radio sin saltar de una app a otra. Los botones de la izquierda llevan a cada parte, y Sistema guarda los ajustes.",
            { tiles(listOf(Glyph.NAV to "Mapa", Glyph.MUSIC to "Música", Glyph.AUTO to "Android Auto", Glyph.RADIO to "Radio")) }),
        Page("A tu gusto",
            "En Sistema eliges tu coche y su color, el modo oscuro (se activa solo al anochecer), los mapas sin conexión de tu zona y qué hace cada botón del volante.",
            { tiles(listOf(Glyph.GAUGE to "Tu coche", Glyph.COMPASS to "Modo oscuro", Glyph.MAP to "Mapas", Glyph.SYSTEM to "Ajustes")) }),
        Page("Dos permisos",
            "Para que todo funcione, EdgeOS Car necesita esto. Puedes cambiarlo cuando quieras en Sistema.",
            { glyph(Glyph.GPS) }) { box ->
            box.addView(permission("Ubicación", "Para la velocidad, el viaje y el mapa.", hasLocation, askLocation), lp(MATCH, WRAP).apply { topMargin = px(14) })
            box.addView(permission("Ver qué suena", "Para mostrar y controlar la música de cualquier app.", hasMedia, askMedia), lp(MATCH, WRAP).apply { topMargin = px(10) })
        },
        Page("Hazlo tu pantalla de inicio",
            "Así la radio arranca en EdgeOS Car y el botón Inicio te trae aquí. El inicio de fábrica sigue instalado: puedes volver a él cuando quieras.",
            { glyph(Glyph.HOME) }) { box ->
            box.addView(permission("EdgeOS Car como inicio", "Elige EdgeOS Car y \"Siempre\".", isDefaultHome, makeDefault, "Elegir"),
                lp(MATCH, WRAP).apply { topMargin = px(14) })
        },
    )

    init {
        setBackgroundColor(Ui.BG)
        isClickable = true // que los toques no lleguen al inicio de debajo
        val column = Ui.column(context)
        column.setPadding(px(48), px(28), px(48), px(28))

        val top = Ui.row(context)
        top.addView(Ui.text(context, "EdgeOS Car", 15f, Ui.TEXT2, Ui.semibold), lp(0, WRAP, 1f))
        val skip = Ui.text(context, "Saltar", 15f, Ui.ACCENT, Ui.medium)
        skip.setPadding(px(12), px(8), 0, px(8))
        skip.setOnClickListener { onDone() }
        top.addView(skip)
        column.addView(top)

        column.addView(content, lp(MATCH, 0, 1f).apply { topMargin = px(8); bottomMargin = px(8) })

        val bottom = Ui.row(context)
        back.setPadding(px(22), px(14), px(22), px(14))
        back.background = Ui.pressable(Ui.TILE, 14f)
        back.setOnClickListener { go(page - 1) }
        bottom.addView(back)
        dots.gravity = Gravity.CENTER
        bottom.addView(dots, lp(0, WRAP, 1f))
        next.setPadding(px(28), px(14), px(28), px(14))
        next.background = Ui.pressable(Ui.INK, 14f)
        next.setOnClickListener { if (page == pages.lastIndex) onDone() else go(page + 1) }
        bottom.addView(next)
        column.addView(bottom)
        addView(column, LayoutParams(MATCH, MATCH))
        show(page, animate = false)
    }

    /** Al volver de Ajustes o de un permiso: actualiza las marcas de lo que ya está hecho. */
    fun refresh() = statusViews.forEach { it() }

    fun go(to: Int) {
        if (to < 0 || to > pages.lastIndex || to == page) return
        val forward = to > page
        page = to
        content.animate().alpha(0f).translationX(Ui.pxf(if (forward) -30f else 30f)).setDuration(140).withEndAction {
            show(to, animate = true, from = if (forward) 30f else -30f)
        }.start()
    }

    private fun show(index: Int, animate: Boolean, from: Float = 0f) {
        statusViews.clear()
        content.removeAllViews()
        val p = pages[index]
        val row = Ui.row(context)
        row.gravity = Gravity.CENTER_VERTICAL
        val visual = FrameLayout(context)
        visual.addView(p.visual(), LayoutParams(MATCH, MATCH))
        row.addView(visual, lp(0, px(360), 0.4f))
        val text = Ui.column(context)
        text.addView(Ui.text(context, p.title, 32f, Ui.TEXT, Ui.semibold).apply { maxLines = 2 })
        text.addView(Ui.text(context, p.body, 17f, Ui.TEXT2).apply { maxLines = 5; ellipsize = null; setLineSpacing(0f, 1.25f) },
            lp(MATCH, WRAP).apply { topMargin = px(14) })
        p.extra(text)
        row.addView(text, lp(0, WRAP, 0.6f).apply { marginStart = px(40) })
        content.addView(row, LayoutParams(MATCH, MATCH))

        dots.removeAllViews()
        for (i in pages.indices) dots.addView(View(context).apply {
            background = Ui.rounded(if (i == index) Ui.INK else Ui.LINE, 4f)
        }, lp(px(if (i == index) 22 else 8), px(8)).apply { marginStart = px(4); marginEnd = px(4) })
        back.visibility = if (index == 0) View.INVISIBLE else View.VISIBLE
        next.text = if (index == pages.lastIndex) "Empezar" else "Siguiente"

        if (animate) {
            content.alpha = 0f
            content.translationX = Ui.pxf(from)
            content.animate().alpha(1f).translationX(0f).setDuration(200).start()
        } else {
            content.alpha = 1f
            content.translationX = 0f
        }
    }

    private fun check(text: String, ok: Boolean) = Ui.row(context).apply {
        val icon = View(context)
        icon.background = GlyphDrawable(Glyph.CHECK, if (ok) Ui.OK else Ui.TEXT2, 2.6f)
        addView(icon, lp(px(22), px(22)))
        addView(Ui.text(context, text, 16f, Ui.TEXT, Ui.medium), lp(0, WRAP, 1f).apply { marginStart = px(12) })
    }

    /** Una fila con el estado (hecho o no) y un botón para hacerlo. */
    private fun permission(title: String, why: String, done: () -> Boolean, action: () -> Unit, button: String = "Permitir") =
        Ui.row(context).apply {
            background = Ui.rounded(Ui.CARD, 14f)
            setPadding(px(16), px(12), px(14), px(12))
            val texts = Ui.column(context)
            texts.addView(Ui.text(context, title, 16f, Ui.TEXT, Ui.semibold))
            texts.addView(Ui.text(context, why, 13f, Ui.TEXT2).apply { maxLines = 2; ellipsize = null }, lp(MATCH, WRAP).apply { topMargin = px(2) })
            addView(texts, lp(0, WRAP, 1f))
            val state = Ui.text(context, "", 14f, Ui.ON_INK, Ui.semibold, Gravity.CENTER)
            state.setPadding(px(16), px(10), px(16), px(10))
            addView(state, lp(WRAP, WRAP).apply { marginStart = px(12) })
            val paint = {
                if (done()) {
                    state.text = "✓ Hecho"
                    state.setTextColor(Ui.OK)
                    state.background = null
                    state.setOnClickListener(null)
                } else {
                    state.text = button
                    state.setTextColor(Ui.ON_INK)
                    state.background = Ui.pressable(Ui.INK, 12f)
                    state.setOnClickListener { action() }
                }
            }
            paint()
            statusViews += paint
        }

    private fun glyph(g: Glyph) = FrameLayout(context).apply {
        val circle = FrameLayout(context)
        circle.background = Ui.rounded(Ui.CARD, 120f)
        circle.elevation = Ui.pxf(3f)
        val icon = View(context)
        icon.background = GlyphDrawable(g, Ui.TEXT, 1.6f)
        circle.addView(icon, LayoutParams(px(96), px(96), Gravity.CENTER))
        addView(circle, LayoutParams(px(220), px(220), Gravity.CENTER))
    }

    private fun tiles(items: List<Pair<Glyph, String>>) = FrameLayout(context).apply {
        val grid = Ui.column(context)
        for (pair in items.chunked(2)) {
            val row = Ui.row(context)
            for ((i, item) in pair.withIndex()) {
                val tile = Ui.column(context)
                tile.gravity = Gravity.CENTER
                tile.background = Ui.rounded(Ui.CARD, 18f)
                tile.elevation = Ui.pxf(2f)
                val icon = View(context)
                icon.background = GlyphDrawable(item.first, Ui.TEXT, 1.8f)
                tile.addView(icon, lp(px(44), px(44)))
                tile.addView(Ui.text(context, item.second, 14f, Ui.TEXT, Ui.semibold, Gravity.CENTER), lp(WRAP, WRAP).apply { topMargin = px(10) })
                row.addView(tile, lp(px(150), px(130)).apply { if (i > 0) marginStart = px(14) })
            }
            grid.addView(row, lp(WRAP, WRAP).apply { if (grid.childCount > 0) topMargin = px(14) })
        }
        addView(grid, LayoutParams(WRAP, WRAP, Gravity.CENTER))
    }
}
