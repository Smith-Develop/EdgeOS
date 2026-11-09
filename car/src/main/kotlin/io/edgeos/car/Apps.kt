package io.edgeos.car

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.LauncherApps
import android.graphics.drawable.Drawable
import android.net.Uri
import android.os.Handler
import android.os.Looper
import android.os.Process
import android.os.UserHandle
import java.text.Collator
import java.util.concurrent.Executors

class App(val component: ComponentName, val label: String, val icon: Drawable) {
    val key: String get() = component.flattenToString()
}

/** Las apps instaladas, al día con LauncherApps. Los iconos se cargan fuera del hilo principal. */
class Apps(private val context: Context) {
    private val la = context.getSystemService(LauncherApps::class.java)
    private val main = Handler(Looper.getMainLooper())
    private val io = Executors.newSingleThreadExecutor()
    private val listeners = mutableListOf<() -> Unit>()

    var list: List<App> = emptyList()
        private set

    private val callback = object : LauncherApps.Callback() {
        override fun onPackageRemoved(p: String, u: UserHandle) = refresh()
        override fun onPackageAdded(p: String, u: UserHandle) = refresh()
        override fun onPackageChanged(p: String, u: UserHandle) = refresh()
        override fun onPackagesAvailable(p: Array<out String>, u: UserHandle, r: Boolean) = refresh()
        override fun onPackagesUnavailable(p: Array<out String>, u: UserHandle, r: Boolean) = refresh()
    }

    fun listen(l: () -> Unit) {
        listeners += l
    }

    fun start() {
        la.registerCallback(callback, main)
        refresh()
    }

    fun stop() = la.unregisterCallback(callback)

    fun refresh() {
        io.execute {
            val density = context.resources.displayMetrics.densityDpi
            val collator = Collator.getInstance()
            val loaded = la.getActivityList(null, Process.myUserHandle())
                .filter { it.componentName.packageName != context.packageName }
                .map { App(it.componentName, it.label.toString().trim(), it.getIcon(density)) }
                .sortedWith { a, b -> collator.compare(a.label, b.label) }
            main.post {
                list = loaded
                listeners.forEach { it() }
            }
        }
    }

    fun find(key: String?) = key?.let { k -> list.firstOrNull { it.key == k } }

    fun launch(app: App) {
        runCatching { la.startMainActivity(app.component, Process.myUserHandle(), null, null) }
    }

    fun info(app: App) {
        runCatching { la.startAppDetailsActivity(app.component, Process.myUserHandle(), null, null) }
    }

    /**
     * La app de cada acceso rápido: la que eligió el usuario o, si no, la primera cuyo paquete o nombre
     * encaja. Las radios chinas usan nombres muy dispares (com.syu.radio, com.android.fmradio, "收音机"…).
     */
    fun forSlot(slot: Slot, prefs: Prefs): App? =
        find(prefs.app(slot.key)) ?: list.firstOrNull { app ->
            val text = (app.component.packageName + " " + app.label).lowercase()
            slot.hints.any { text.contains(it) }
        }

    fun openNavigation() {
        val intent = Intent(Intent.ACTION_VIEW, Uri.parse("geo:0,0?q=")).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        runCatching { context.startActivity(intent) }
    }
}

enum class Slot(val key: String, val title: String, val hints: List<String>) {
    RADIO("radio", "Radio", listOf("fmradio", ".radio", "radio", " fm", "收音")),
    PHONE("phone", "Teléfono", listOf("bluetooth", ".bt", "btphone", "dialer", "phone", "teléfono", "telefono", "蓝牙")),
    MUSIC("music", "Música", listOf("music", "música", "musica", "player", "音乐")),
    NAV("nav", "Navegar", listOf("waze", "maps", "navi", "osmand", "sygic", "here")),
}
