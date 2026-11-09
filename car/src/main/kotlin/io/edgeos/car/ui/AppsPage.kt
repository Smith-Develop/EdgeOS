package io.edgeos.car.ui

import android.content.Context
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.BaseAdapter
import android.widget.GridView
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import io.edgeos.car.App
import io.edgeos.car.ui.Ui.MATCH
import io.edgeos.car.ui.Ui.WRAP
import io.edgeos.car.ui.Ui.lp
import io.edgeos.car.ui.Ui.px

/** Todas las apps en una cuadrícula. Tocar: abrir. Mantener pulsada: opciones. */
class AppsPage(context: Context, private val onOpen: (App) -> Unit, private val onMenu: (App) -> Unit) : LinearLayout(context) {
    private var apps: List<App> = emptyList()
    private val count = Ui.text(context, "", 13f, Ui.TEXT2)
    private val adapter = object : BaseAdapter() {
        override fun getCount() = apps.size
        override fun getItem(position: Int) = apps[position]
        override fun getItemId(position: Int) = position.toLong()

        override fun getView(position: Int, convert: View?, parent: ViewGroup): View {
            val cell = (convert as? LinearLayout) ?: Ui.column(context).apply {
                gravity = Gravity.CENTER_HORIZONTAL
                setPadding(px(4), px(12), px(4), px(10))
                background = Ui.pressable(android.graphics.Color.TRANSPARENT, 16f)
                addView(ImageView(context), lp(px(60), px(60)))
                addView(Ui.text(context, "", 12f, Ui.TEXT, Ui.medium, Gravity.CENTER), lp(MATCH, WRAP).apply { topMargin = px(8) })
            }
            val app = apps[position]
            (cell.getChildAt(0) as ImageView).setImageDrawable(app.icon)
            (cell.getChildAt(1) as TextView).text = app.label
            return cell
        }
    }

    init {
        orientation = VERTICAL
        Ui.card(this)
        setPadding(px(20), px(16), px(20), px(8))
        val head = Ui.row(context)
        head.addView(Ui.text(context, "Apps", 20f, Ui.TEXT, Ui.semibold))
        head.addView(count, lp(WRAP, WRAP).apply { marginStart = px(10) })
        addView(head)
        val grid = GridView(context).apply {
            numColumns = 7
            stretchMode = GridView.STRETCH_COLUMN_WIDTH
            verticalSpacing = px(4)
            selector = android.graphics.drawable.ColorDrawable(android.graphics.Color.TRANSPARENT)
            adapter = this@AppsPage.adapter
            setOnItemClickListener { _, _, i, _ -> onOpen(apps[i]) }
            setOnItemLongClickListener { _, _, i, _ -> onMenu(apps[i]); true }
        }
        addView(grid, lp(MATCH, 0, 1f).apply { topMargin = px(8) })
    }

    fun set(list: List<App>) {
        apps = list
        count.text = "${list.size}"
        adapter.notifyDataSetChanged()
    }
}
