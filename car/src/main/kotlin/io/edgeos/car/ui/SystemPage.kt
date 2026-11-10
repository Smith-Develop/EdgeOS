package io.edgeos.car.ui

import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.BaseAdapter
import android.widget.LinearLayout
import android.widget.ListView
import android.widget.ScrollView
import android.widget.Switch
import android.widget.TextView
import android.widget.Toast
import io.edgeos.car.Prefs
import io.edgeos.car.system.Optimizer
import io.edgeos.car.system.Service
import io.edgeos.car.system.Shell
import io.edgeos.car.ui.Ui.MATCH
import io.edgeos.car.ui.Ui.WRAP
import io.edgeos.car.ui.Ui.lp
import io.edgeos.car.ui.Ui.px
import java.util.Locale
import java.util.concurrent.Executors

/** Sistema: estado de la radio, optimización rápida, ajustes del launcher y servicios innecesarios. */
class SystemPage(
    context: Context,
    private val prefs: Prefs,
    private val optimizer: Optimizer,
    private val keepRunning: () -> Set<String>,
    private val onShowMap: (Boolean) -> Unit,
    private val onResetTrip: () -> Unit,
    private val onAskMediaAccess: () -> Unit,
) : LinearLayout(context) {
    private val main = Handler(Looper.getMainLooper())
    private val work = Executors.newSingleThreadExecutor()

    private val ram = Ui.text(context, "", 14f, Ui.TEXT, Ui.medium)
    private val storage = Ui.text(context, "", 14f, Ui.TEXT, Ui.medium)
    private val temp = Ui.text(context, "", 14f, Ui.TEXT, Ui.medium)
    private val anim = Switch(context)
    private val offset = Ui.text(context, "", 15f, Ui.TEXT, Ui.semibold, Gravity.CENTER)
    private val mapChoice = mutableListOf<TextView>()

    private val status = Ui.text(context, "Comprobando el acceso al sistema…", 13f, Ui.TEXT2).apply { maxLines = 6 }
    private val statusButton = Ui.text(context, "", 14f, android.graphics.Color.WHITE, Ui.semibold, Gravity.CENTER)
        .apply { visibility = View.GONE }
    private val list = ListView(context)
    private val restore = Ui.text(context, "Restaurar todo", 14f, Ui.TEXT, Ui.semibold, Gravity.CENTER)
    private val disable = Ui.text(context, "Desactivar (0)", 14f, android.graphics.Color.WHITE, Ui.semibold, Gravity.CENTER)
    private var services: List<Service> = emptyList()
    private val selected = mutableSetOf<String>()
    private var busy = false

    private val adapter = object : BaseAdapter() {
        override fun getCount() = services.size
        override fun getItem(position: Int) = services[position]
        override fun getItemId(position: Int) = position.toLong()
        override fun getView(position: Int, convert: View?, parent: ViewGroup) = serviceRow(services[position], convert)
    }


    init {
        orientation = HORIZONTAL
        val left = Ui.column(context)
        left.addView(stateCard())
        left.addView(quickCard(), lp(MATCH, WRAP).apply { topMargin = px(14) })
        left.addView(settingsCard(), lp(MATCH, WRAP).apply { topMargin = px(14) })
        val scroll = ScrollView(context).apply { isVerticalScrollBarEnabled = false; clipToPadding = false; setPadding(px(2), 0, px(2), px(4)) }
        scroll.addView(left)
        addView(scroll, lp(0, MATCH, 0.42f))
        addView(servicesCard(), lp(0, MATCH, 0.58f).apply { marginStart = px(14) })
    }

    fun onShow() {
        refreshStats()
        refreshServices()
    }

    private fun card(title: String) = Ui.column(context).apply {
        Ui.card(this)
        setPadding(px(18), px(16), px(18), px(14))
        addView(Ui.text(context, title, 16f, Ui.TEXT, Ui.semibold), lp(MATCH, WRAP).apply { bottomMargin = px(8) })
    }

    private fun line(label: String, value: View) = Ui.row(context).apply {
        setPadding(0, px(9), 0, px(9))
        addView(Ui.text(context, label, 14f, Ui.TEXT2), lp(0, WRAP, 1f))
        addView(value)
    }

    private fun stateCard() = card("Estado").apply {
        addView(line("Radio", Ui.text(context, "${Build.DEVICE} · Android ${Build.VERSION.RELEASE}", 14f, Ui.TEXT, Ui.medium)))
        addView(line("Memoria libre", ram))
        addView(line("Almacenamiento libre", storage))
        addView(line("Procesador", temp))
    }

    private fun quickCard() = card("Optimización rápida").apply {
        val free = Ui.text(context, "Liberar memoria", 14f, Ui.TEXT, Ui.semibold, Gravity.CENTER)
        free.background = Ui.pressable(Ui.TILE, 12f)
        free.setPadding(px(14), px(12), px(14), px(12))
        free.setOnClickListener {
            work.execute {
                val before = optimizer.memory().first
                val after = optimizer.freeMemory(keepRunning())
                main.post {
                    toast("Memoria libre: $after MB (+${(after - before).coerceAtLeast(0)} MB)")
                    refreshStats()
                }
            }
        }
        addView(free, lp(MATCH, WRAP).apply { topMargin = px(4) })
        val row = line("Animaciones rápidas (0,5×)", anim)
        anim.setOnCheckedChangeListener { _, on ->
            if (busy || on == optimizer.animationsFast()) return@setOnCheckedChangeListener
            if (Shell.access.state != Shell.State.Ready) {
                anim.isChecked = !on
                return@setOnCheckedChangeListener toast("Necesita acceso al sistema: mira \"Servicios innecesarios\".")
            }
            work.execute { runCatching { optimizer.fastAnimations(on) }; main.post { anim.isChecked = optimizer.animationsFast() } }
        }
        addView(row, lp(MATCH, WRAP).apply { topMargin = px(6) })
    }

    private fun settingsCard() = card("Ajustes").apply {
        val seg = Ui.row(context)
        seg.background = Ui.rounded(Ui.TILE, 12f)
        seg.setPadding(px(4), px(4), px(4), px(4))
        for ((i, name) in listOf("Mapa", "Panel").withIndex()) {
            val t = Ui.text(context, name, 14f, Ui.TEXT, Ui.semibold, Gravity.CENTER)
            t.setPadding(0, px(9), 0, px(9))
            t.setOnClickListener {
                prefs.showMap = i == 0
                paintChoice()
                onShowMap(prefs.showMap)
            }
            mapChoice += t
            seg.addView(t, lp(0, WRAP, 1f))
        }
        paintChoice()
        addView(Ui.text(context, "En el centro de Inicio", 13f, Ui.TEXT2))
        addView(seg, lp(MATCH, WRAP).apply { topMargin = px(8); bottomMargin = px(6) })

        val stepper = Ui.row(context)
        stepper.addView(button(context, GlyphDrawable(Glyph.MINUS, Ui.TEXT), Ui.TILE) { prefs.speedOffset -= 1; paintOffset() }, lp(px(40), px(40)))
        stepper.addView(offset, lp(px(64), WRAP))
        stepper.addView(button(context, GlyphDrawable(Glyph.PLUS, Ui.TEXT), Ui.TILE) { prefs.speedOffset += 1; paintOffset() }, lp(px(40), px(40)))
        paintOffset()
        addView(line("Corrección de velocidad", stepper))
        addView(link("Reiniciar viaje") { onResetTrip(); toast("Viaje a cero.") })
        addView(link("Mostrar qué suena (acceso a notificaciones)") { onAskMediaAccess() })
        addView(link("Pantalla de inicio predeterminada") {
            open(Intent(android.provider.Settings.ACTION_HOME_SETTINGS))
        })
    }

    private fun link(text: String, action: () -> Unit) = Ui.text(context, text, 14f, Ui.ACCENT, Ui.medium).apply {
        setPadding(0, px(11), 0, px(11))
        setOnClickListener { action() }
    }

    private fun paintChoice() {
        for ((i, t) in mapChoice.withIndex()) {
            val on = (i == 0) == prefs.showMap
            t.background = if (on) Ui.rounded(Ui.CARD, 10f) else null
            t.setTextColor(if (on) Ui.TEXT else Ui.TEXT2)
        }
    }

    private fun paintOffset() {
        val v = prefs.speedOffset
        offset.text = if (v > 0) "+$v %" else "$v %"
    }

    private fun servicesCard() = card("Servicios innecesarios").apply {
        addView(Ui.text(context, "Se desactivan sin borrarse. Nada del coche (radio, cámara, Bluetooth, CAN bus…) aparece aquí.", 12f, Ui.TEXT2).apply { maxLines = 2 })
        val box = Ui.column(context)
        box.background = Ui.rounded(Ui.TILE, 14f)
        box.setPadding(px(14), px(12), px(14), px(12))
        box.addView(status)
        statusButton.background = Ui.pressable(Ui.INK, 12f)
        statusButton.setPadding(px(16), px(10), px(16), px(10))
        box.addView(statusButton, lp(WRAP, WRAP).apply { topMargin = px(10) })
        addView(box, lp(MATCH, WRAP).apply { topMargin = px(10) })

        list.adapter = adapter
        list.divider = null
        list.selector = android.graphics.drawable.ColorDrawable(android.graphics.Color.TRANSPARENT)
        addView(list, lp(MATCH, 0, 1f).apply { topMargin = px(6) })

        val actions = Ui.row(context)
        restore.background = Ui.pressable(Ui.TILE, 14f)
        restore.setPadding(0, px(13), 0, px(13))
        restore.setOnClickListener { act { val f = optimizer.restoreAll(); if (f.isEmpty()) "Todo restaurado." else "No se pudieron activar: ${f.joinToString()}" } }
        disable.background = Ui.pressable(Ui.DANGER, 14f)
        disable.setPadding(0, px(13), 0, px(13))
        disable.setOnClickListener {
            if (selected.isEmpty()) return@setOnClickListener
            val chosen = selected.toList()
            act { val f = chosen.filterNot { optimizer.disable(it) }; if (f.isEmpty()) "Desactivados: ${chosen.size}." else "No se pudieron desactivar: ${f.joinToString()}" }
        }
        actions.addView(restore, lp(0, WRAP, 1f))
        actions.addView(disable, lp(0, WRAP, 1f).apply { marginStart = px(10) })
        addView(actions, lp(MATCH, WRAP).apply { topMargin = px(8) })
    }

    private fun act(action: () -> String) {
        if (busy) return
        busy = true
        work.execute {
            val msg = runCatching(action).getOrElse { "Error: ${it.message}" }
            main.post {
                busy = false
                selected.clear()
                toast(msg)
                refreshServices()
            }
        }
    }

    private fun serviceRow(s: Service, convert: View?): View {
        val row = (convert as? LinearLayout) ?: Ui.row(context).apply {
            setPadding(px(4), px(10), px(4), px(10))
            val texts = Ui.column(context)
            texts.addView(Ui.text(context, "", 15f, Ui.TEXT, Ui.medium))
            texts.addView(Ui.text(context, "", 11f, Ui.TEXT2), lp(WRAP, WRAP).apply { topMargin = px(3) })
            texts.addView(Ui.text(context, "", 12f, Ui.TEXT2).apply { maxLines = 2 }, lp(WRAP, WRAP).apply { topMargin = px(3) })
            addView(texts, lp(0, WRAP, 1f))
            addView(Ui.text(context, "", 14f, Ui.ACCENT, Ui.semibold, Gravity.CENTER), lp(px(76), px(36)).apply { marginStart = px(8) })
        }
        val texts = row.getChildAt(0) as LinearLayout
        (texts.getChildAt(0) as TextView).apply { text = s.name; alpha = if (s.enabled) 1f else 0.45f }
        (texts.getChildAt(1) as TextView).text = "${s.packageName} · ${s.level.lowercase(Locale.forLanguageTag("es"))}" + if (s.enabled) "" else " · desactivado"
        (texts.getChildAt(2) as TextView).apply { text = s.why; visibility = if (s.why.isEmpty()) View.GONE else View.VISIBLE }
        val right = row.getChildAt(1) as TextView
        if (s.enabled) {
            val on = s.packageName in selected
            right.text = if (on) "✓" else ""
            right.setTextColor(android.graphics.Color.WHITE)
            right.background = if (on) Ui.rounded(Ui.ACCENT, 18f) else Ui.rounded(android.graphics.Color.TRANSPARENT, 18f, Ui.TEXT2)
            right.layoutParams = (right.layoutParams as LayoutParams).apply { width = px(36) }
            row.setOnClickListener {
                if (!selected.remove(s.packageName)) selected += s.packageName
                disable.text = "Desactivar (${selected.size})"
                adapter.notifyDataSetChanged()
            }
            right.setOnClickListener(null)
            right.isClickable = false
        } else {
            right.text = "Activar"
            right.setTextColor(Ui.ACCENT)
            right.background = Ui.pressable(Ui.TILE, 12f)
            right.layoutParams = (right.layoutParams as LayoutParams).apply { width = px(76) }
            row.setOnClickListener(null)
            right.setOnClickListener { act { if (optimizer.enable(s.packageName)) "Activado: ${s.name}" else "No se pudo activar ${s.name}" } }
        }
        return row
    }

    fun refreshStats() {
        work.execute {
            val (free, total) = optimizer.memory()
            val (sf, st) = optimizer.storage()
            val t = optimizer.cpuTemp()
            main.post {
                ram.text = "$free de $total MB"
                storage.text = String.format(Locale.forLanguageTag("es"), "%.1f de %.1f GB", sf, st)
                temp.text = t?.let { "$it °C" } ?: "—"
            }
        }
    }

    /** Pide acceso (root o el ADB de la propia radio) y, con él, carga la lista. */
    private fun refreshServices() {
        busy = true
        anim.isChecked = optimizer.animationsFast()
        busy = false
        if (Shell.access.state != Shell.State.Ready) show("Comprobando el acceso al sistema…", "") {}
        work.execute {
            val access = Shell.connect(context) {
                main.post { show("En la pantalla aparece \"¿Permitir depuración USB?\": marca \"Permitir siempre\" y pulsa Aceptar.", "") {} }
            }
            main.post { onAccess(access) }
        }
    }

    private fun onAccess(access: Shell.Access) {
        when (access.state) {
            Shell.State.Ready -> {}
            Shell.State.AdbOff -> return show(
                "Para desactivar servicios, EdgeOS Car usa el ADB de la propia radio (no hace falta instalar nada). " +
                    "Actívalo en Ajustes → Opciones de desarrollador → Depuración USB. Si sigue cerrado, desde un ordenador " +
                    "con la radio por USB: adb tcpip 5555 (en muchas radios hay que repetirlo tras cada arranque).",
                "Abrir opciones de desarrollador",
            ) { open(Intent(android.provider.Settings.ACTION_APPLICATION_DEVELOPMENT_SETTINGS)) }
            Shell.State.Denied -> return show("No se aceptó el aviso de depuración en la radio.", "Reintentar") { refreshServices() }
            else -> return show("No se pudo conectar: ${access.detail}", "Reintentar") { refreshServices() }
        }
        val via = if (access.via == "root") "con root" else "con el ADB de la radio"
        show("Acceso listo ($via). Buscando servicios…", "") {}
        work.execute {
            val loaded = runCatching { optimizer.services() }
            main.post {
                services = loaded.getOrDefault(emptyList())
                selected.retainAll(services.filter { it.enabled }.map { it.packageName }.toSet())
                disable.text = "Desactivar (${selected.size})"
                status.text = loaded.exceptionOrNull()?.let { "No se pudo leer la lista: ${it.message}" }
                    ?: if (services.isEmpty()) "Nada que desactivar en esta radio." else
                        "Acceso listo ($via). Marca lo que quieras desactivar; empieza por las \"recomendadas\"."
                adapter.notifyDataSetChanged()
            }
        }
    }

    private fun show(text: String, button: String, action: () -> Unit) {
        status.text = text
        statusButton.text = button
        statusButton.visibility = if (button.isEmpty()) View.GONE else View.VISIBLE
        statusButton.setOnClickListener { action() }
        if (Shell.access.state != Shell.State.Ready) {
            services = emptyList()
            adapter.notifyDataSetChanged()
        }
    }

    private fun open(intent: Intent) {
        runCatching { context.startActivity(intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }
            .onFailure { toast("No se pudo abrir.") }
    }

    private fun toast(text: String) = Toast.makeText(context, text, Toast.LENGTH_LONG).show()
}
