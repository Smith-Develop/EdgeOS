package io.edgeos.car

import android.Manifest
import android.app.Activity
import android.app.AlertDialog
import android.content.Intent
import android.content.pm.PackageManager
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.view.Gravity
import android.view.View
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.Toast
import io.edgeos.car.drive.Drive
import io.edgeos.car.drive.Media
import io.edgeos.car.system.Optimizer
import io.edgeos.car.ui.AppsPage
import io.edgeos.car.ui.CarCard
import io.edgeos.car.ui.Glyph
import io.edgeos.car.ui.MapCard
import io.edgeos.car.ui.MediaCard
import io.edgeos.car.ui.PanelCard
import io.edgeos.car.ui.QuickCard
import io.edgeos.car.ui.Rail
import io.edgeos.car.ui.SystemPage
import io.edgeos.car.ui.TopBar
import io.edgeos.car.ui.Ui
import io.edgeos.car.ui.Ui.MATCH
import io.edgeos.car.ui.Ui.WRAP
import io.edgeos.car.ui.Ui.lp
import io.edgeos.car.ui.Ui.px
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class HomeActivity : Activity() {
    private lateinit var prefs: Prefs
    private lateinit var drive: Drive
    private lateinit var media: Media
    private lateinit var apps: Apps
    private lateinit var optimizer: Optimizer

    private lateinit var pages: FrameLayout
    private lateinit var home: LinearLayout
    private lateinit var center: FrameLayout
    private lateinit var appsPage: AppsPage
    private lateinit var systemPage: SystemPage
    private lateinit var carCard: CarCard
    private lateinit var mediaCard: MediaCard
    private lateinit var quick: QuickCard
    private var mapCard: MapCard? = null
    private var panelCard: PanelCard? = null

    private lateinit var topBar: TopBar
    private lateinit var rail: Rail
    private val main = Handler(Looper.getMainLooper())
    private var resumed = false

    private enum class Page { HOME, APPS, SYSTEM }

    private val tick = object : Runnable {
        override fun run() {
            val now = Date()
            val es = Locale.forLanguageTag("es")
            topBar.set(
                SimpleDateFormat("EEEE, d 'de' MMMM", es).format(now).replaceFirstChar { it.uppercase() },
                SimpleDateFormat("HH:mm", es).format(now),
                online(),
            )
            main.postDelayed(this, 60_000 - System.currentTimeMillis() % 60_000)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        Ui.init(this)
        Ui.fonts(this)
        prefs = Prefs(this)
        drive = Drive(applicationContext, prefs)
        media = Media(applicationContext)
        apps = Apps(applicationContext)
        optimizer = Optimizer(applicationContext, prefs)
        setContentView(build())
        Thread { CarImage.load(this)?.let { b -> main.post { carCard.setCar(b) } } }.start()

        drive.listen { s ->
            carCard.update(s)
            mapCard?.update(s)
            panelCard?.update(s)
            topBar.gps(s.hasFix)
        }
        media.onChange = { mediaCard.update(it, media.hasAccess()) }
        apps.listen {
            appsPage.set(apps.list)
            quick.setApps(Slot.QUICK.map { apps.forSlot(it, prefs) })
        }
        apps.start()
        askLocation()
    }

    private fun build(): View {
        val root = LinearLayout(this)
        root.setBackgroundColor(Ui.BG)
        rail = Rail(this)
        rail.item(Glyph.HOME, Page.HOME) { show(Page.HOME) }
        rail.item(Glyph.APPS, Page.APPS) { show(Page.APPS) }
        rail.item(Glyph.AUTO) { openSlot(Slot.AUTO) }
        rail.item(Glyph.PHONE) { openSlot(Slot.PHONE) }
        rail.item(Glyph.MUSIC) { openSlot(Slot.MUSIC) }
        rail.spacer()
        rail.item(Glyph.SYSTEM, Page.SYSTEM) { show(Page.SYSTEM) }
        root.addView(rail, lp(px(84), MATCH))

        val content = Ui.column(this)
        // Mismo margen a los dos lados: las tarjetas no se pegan a la barra lateral.
        content.setPadding(px(16), 0, px(16), px(16))
        topBar = TopBar(this)
        content.addView(topBar, lp(MATCH, px(52)))
        pages = FrameLayout(this)
        content.addView(pages, lp(MATCH, 0, 1f))
        root.addView(content, lp(0, MATCH, 1f))

        // Inicio: coche | mapa o panel | música y accesos.
        home = Ui.row(this)
        home.gravity = Gravity.TOP
        carCard = CarCard(this)
        home.addView(carCard, lp(px(250), MATCH))
        center = FrameLayout(this)
        home.addView(center, lp(0, MATCH, 1f).apply { marginStart = px(14); marginEnd = px(14) })
        val right = Ui.column(this)
        mediaCard = MediaCard(this, { media.previous() }, { media.playPause() }, { media.next() }, ::askMediaAccess)
        right.addView(mediaCard, lp(MATCH, 0, 1f))
        quick = QuickCard(this)
        quick.setup(Slot.QUICK.map { slot ->
            val glyph = when (slot) {
                Slot.AUTO -> Glyph.AUTO
                Slot.RADIO -> Glyph.RADIO
                Slot.PHONE -> Glyph.PHONE
                Slot.MUSIC -> Glyph.MUSIC
                Slot.NAV -> Glyph.NAV
            }
            Triple(glyph, slot.title, Pair({ openSlot(slot) }, { chooseApp(slot) }))
        })
        right.addView(quick, lp(MATCH, 0, 1f).apply { topMargin = px(14) })
        home.addView(right, lp(px(270), MATCH))
        showCenter()

        appsPage = AppsPage(this, { apps.launch(it) }, ::appMenu)
        systemPage = SystemPage(
            this, prefs, optimizer,
            keepRunning = { setOfNotNull(media.now.packageName) + Slot.entries.mapNotNull { apps.forSlot(it, prefs)?.component?.packageName } },
            onShowMap = { showCenter() },
            onResetTrip = { drive.resetTrip() },
            onAskMediaAccess = ::askMediaAccess,
            onPickCar = ::pickCar,
            onResetCar = {
                CarImage.reset(this)
                carCard.setCar(null)
                Toast.makeText(this, "Imagen de serie.", Toast.LENGTH_SHORT).show()
            },
        )
        for (v in listOf(home, appsPage, systemPage)) pages.addView(v, FrameLayout.LayoutParams(MATCH, MATCH))
        show(Page.HOME)
        return root
    }

    private fun show(page: Page) {
        home.visibility = if (page == Page.HOME) View.VISIBLE else View.GONE
        appsPage.visibility = if (page == Page.APPS) View.VISIBLE else View.GONE
        systemPage.visibility = if (page == Page.SYSTEM) View.VISIBLE else View.GONE
        rail.select(page)
        if (page == Page.SYSTEM) systemPage.onShow()
    }

    /** En el centro, el mapa o el panel de conducción, según Ajustes. */
    private fun showCenter() {
        center.removeAllViews()
        mapCard?.onPause()
        mapCard = null
        panelCard = null
        if (prefs.showMap) {
            mapCard = MapCard(this) { openSlot(Slot.NAV) }.also { center.addView(it) }
            if (resumed) mapCard?.onResume()
        } else {
            panelCard = PanelCard(this) { drive.resetTrip() }.also { center.addView(it) }
        }
        if (::drive.isInitialized) drive.state.let { s -> mapCard?.update(s); panelCard?.update(s) }
    }

    private fun openSlot(slot: Slot) {
        val app = apps.forSlot(slot, prefs)
        when {
            app != null -> apps.launch(app)
            slot == Slot.NAV -> apps.openNavigation()
            else -> {
                if (slot == Slot.AUTO) Toast.makeText(this, "No encontré la app de Android Auto de la radio (Zlink, AutoKit…): elígela.", Toast.LENGTH_LONG).show()
                chooseApp(slot)
            }
        }
    }

    private fun chooseApp(slot: Slot) {
        val list = apps.list
        if (list.isEmpty()) return
        AlertDialog.Builder(this)
            .setTitle("App para ${slot.title}")
            .setItems(list.map { it.label }.toTypedArray()) { _, i ->
                prefs.setApp(slot.key, list[i].key)
                quick.setApps(Slot.QUICK.map { apps.forSlot(it, prefs) })
            }
            .setNegativeButton("Cancelar", null)
            .show()
    }

    private fun appMenu(app: App) {
        val options = listOf("Información de la app") + Slot.entries.map { "Usar para ${it.title}" }
        AlertDialog.Builder(this)
            .setTitle(app.label)
            .setItems(options.toTypedArray()) { _, i ->
                if (i == 0) apps.info(app) else {
                    val slot = Slot.entries[i - 1]
                    prefs.setApp(slot.key, app.key)
                    quick.setApps(Slot.QUICK.map { apps.forSlot(it, prefs) })
                    Toast.makeText(this, "${app.label}: ${slot.title}", Toast.LENGTH_SHORT).show()
                }
            }
            .show()
    }

    private fun askMediaAccess() {
        runCatching { startActivity(Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS)) }
            .onFailure { Toast.makeText(this, "Esta radio no tiene ese ajuste.", Toast.LENGTH_LONG).show() }
    }

    // MARK: imagen del coche

    private fun pickCar() {
        val open = Intent(Intent.ACTION_OPEN_DOCUMENT).addCategory(Intent.CATEGORY_OPENABLE).setType("image/*")
        val get = Intent(Intent.ACTION_GET_CONTENT).addCategory(Intent.CATEGORY_OPENABLE).setType("image/*")
        for (intent in listOf(open, get)) {
            if (runCatching { startActivityForResult(intent, PICK_CAR) }.isSuccess) return
        }
        // Algunas radios no traen selector de archivos: se busca coche.png en la memoria USB.
        if (checkSelfPermission(Manifest.permission.READ_EXTERNAL_STORAGE) != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(arrayOf(Manifest.permission.READ_EXTERNAL_STORAGE), STORAGE)
        } else {
            carFromStorage()
        }
    }

    private fun carFromStorage() {
        val file = CarImage.findOnStorage()
            ?: return Toast.makeText(this, "Copia la imagen como coche.png en la raíz de una memoria USB, conéctala y vuelve a pulsar.", Toast.LENGTH_LONG).show()
        Thread { applyCar(runCatching { CarImage.save(this, file) }.getOrNull()) }.start()
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        val uri = data?.data
        if (requestCode != PICK_CAR || resultCode != RESULT_OK || uri == null) return
        Thread { applyCar(runCatching { CarImage.save(this, uri) }.getOrNull()) }.start()
    }

    private fun applyCar(bitmap: android.graphics.Bitmap?) = main.post {
        if (bitmap == null) return@post Toast.makeText(this, "No se pudo leer esa imagen.", Toast.LENGTH_LONG).show()
        carCard.setCar(bitmap)
        Toast.makeText(this, "Imagen del coche cambiada.", Toast.LENGTH_SHORT).show()
    }

    // MARK: pantalla completa

    /** Sin barra de estado ni de navegación; deslizando desde el borde aparecen un momento y se vuelven a ocultar. */
    @Suppress("DEPRECATION")
    private fun immersive() {
        window.decorView.systemUiVisibility = (View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY or View.SYSTEM_UI_FLAG_FULLSCREEN or
            View.SYSTEM_UI_FLAG_HIDE_NAVIGATION or View.SYSTEM_UI_FLAG_LAYOUT_STABLE or
            View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION or View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN)
    }

    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        if (hasFocus) immersive()
    }

    private fun askLocation() {
        if (checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(arrayOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION), 1)
        }
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, results: IntArray) {
        if (requestCode == STORAGE) {
            if (results.firstOrNull() == PackageManager.PERMISSION_GRANTED) carFromStorage()
            else Toast.makeText(this, "Sin permiso de almacenamiento no puedo leer la memoria USB.", Toast.LENGTH_LONG).show()
            return
        }
        if (results.firstOrNull() == PackageManager.PERMISSION_GRANTED && resumed) drive.start()
        else if (results.isNotEmpty()) Toast.makeText(this, "Sin permiso de ubicación no hay velocidad.", Toast.LENGTH_LONG).show()
    }

    private fun online(): Boolean {
        val cm = getSystemService(ConnectivityManager::class.java)
        val caps = cm.getNetworkCapabilities(cm.activeNetwork) ?: return false
        return caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
    }

    // El GPS va mientras el launcher está a la vista. Shortcut: con otra app delante, Android 8 limita la
    // ubicación en segundo plano; para contar el viaje siempre haría falta un servicio en primer plano.
    override fun onResume() {
        super.onResume()
        immersive()
        resumed = true
        drive.start()
        media.start()
        mapCard?.onResume()
        main.post(tick)
        apps.refresh()
    }

    override fun onPause() {
        resumed = false
        drive.stop()
        media.stop()
        mapCard?.onPause()
        main.removeCallbacks(tick)
        super.onPause()
    }

    override fun onDestroy() {
        apps.stop()
        super.onDestroy()
    }

    /** Inicio con el launcher delante: volver a la página principal. */
    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        if (intent.hasCategory(Intent.CATEGORY_HOME)) show(Page.HOME)
    }

    private companion object {
        const val PICK_CAR = 7
        const val STORAGE = 8
    }

    @Deprecated("Deprecated in Java")
    override fun onBackPressed() {
        if (home.visibility != View.VISIBLE) show(Page.HOME)
    }
}
