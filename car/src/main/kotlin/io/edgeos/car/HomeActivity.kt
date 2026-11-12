package io.edgeos.car

import android.Manifest
import android.app.Activity
import android.app.AlertDialog
import android.content.Intent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.IntentFilter
import android.app.DownloadManager
import io.edgeos.car.maps.Maps
import io.edgeos.car.ui.MapsUi
import android.content.pm.PackageManager
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.os.Build
import android.window.OnBackInvokedDispatcher
import android.annotation.SuppressLint
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.view.Gravity
import android.view.View
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.Toast
import android.util.Log
import io.edgeos.car.drive.Drive
import io.edgeos.car.drive.Media
import io.edgeos.car.system.Brightness
import io.edgeos.car.system.Optimizer
import io.edgeos.car.ui.AppsPage
import io.edgeos.car.ui.CarBody
import io.edgeos.car.ui.CarCard
import io.edgeos.car.ui.Sun
import io.edgeos.car.ui.Glyph
import io.edgeos.car.ui.ImagePicker
import io.edgeos.car.ui.MapCard
import io.edgeos.car.ui.MediaCard
import io.edgeos.car.ui.MusicPage
import io.edgeos.car.ui.Onboarding
import io.edgeos.car.music.LocalPlayer
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
    private lateinit var musicPage: MusicPage
    private lateinit var player: LocalPlayer
    private lateinit var carCard: CarCard
    private lateinit var mediaCard: MediaCard
    private lateinit var quick: QuickCard
    private var mapCard: MapCard? = null
    private var panelCard: PanelCard? = null

    private lateinit var topBar: TopBar
    private lateinit var rail: Rail
    private val main = Handler(Looper.getMainLooper())
    private var resumed = false

    private enum class Page { HOME, APPS, MUSIC, SYSTEM }
    private var current = Page.HOME

    private val tick = object : Runnable {
        override fun run() {
            val now = Date()
            val es = Locale.forLanguageTag("es")
            topBar.set(
                SimpleDateFormat("EEEE, d 'de' MMMM", es).format(now).replaceFirstChar { it.uppercase() },
                SimpleDateFormat("HH:mm", es).format(now),
                online(),
            )
            // Modo automático: cambia de tema al amanecer y al atardecer.
            if (wantDark() != Ui.dark) applyTheme()
            autoBrightness()
            main.postDelayed(this, 60_000 - System.currentTimeMillis() % 60_000)
        }
    }

    @SuppressLint("UnspecifiedRegisterReceiverFlag") // La rama sin flag solo corre antes de Android 13.
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // Android 16 ya no llama a onBackPressed en las apps que lo tienen como destino.
        if (Build.VERSION.SDK_INT >= 33) onBackInvokedDispatcher.registerOnBackInvokedCallback(OnBackInvokedDispatcher.PRIORITY_DEFAULT) { goBack() }
        Ui.fonts(this)
        prefs = Prefs(this)
        drive = Drive(applicationContext, prefs)
        media = Media(applicationContext)
        player = LocalPlayer(applicationContext)
        media.local = player.controller
        apps = Apps(applicationContext)
        optimizer = Optimizer(applicationContext, prefs)

        // La interfaz se construye con el tamaño REAL de la ventana, no con el de la pantalla: en radios que
        // mantienen una barra del sistema, la ventana es más baja y con el de la pantalla no cabía todo (la
        // imagen del coche, que se queda con el hueco sobrante, desaparecía). Si la ventana cambia, se rehace.
        host = FrameLayout(this)
        Ui.theme(wantDark())
        host.setBackgroundColor(Ui.BG)
        setContentView(host)
        host.addOnLayoutChangeListener { _, l, t, r, b, _, _, _, _ ->
            val w = r - l
            val h = b - t
            if (w > 0 && h > 0 && (Math.abs(w - builtW) > 4 || Math.abs(h - builtH) > 4)) main.post { rebuild(w, h) }
        }

        drive.listen { s ->
            if (!built) return@listen
            carCard.update(s)
            mapCard?.update(s)
            panelCard?.update(s)
            topBar.gps(s.hasFix)
        }
        media.onChange = {
            if (built) {
                mediaCard.update(it, media.hasAccess())
                musicPage.update(it)
            }
        }
        apps.listen { if (built) showApps() }
        apps.start()
        askLocation()
        // Desde Android 13 hay que decir si la recibe de otras apps: el aviso llega desde el gestor de descargas.
        if (Build.VERSION.SDK_INT >= 33) registerReceiver(mapDone, IntentFilter(DownloadManager.ACTION_DOWNLOAD_COMPLETE), RECEIVER_EXPORTED)
        else registerReceiver(mapDone, IntentFilter(DownloadManager.ACTION_DOWNLOAD_COMPLETE))
    }

    private lateinit var host: FrameLayout
    private var built = false
    private var builtW = 0
    private var builtH = 0

    private fun wantDark(): Boolean = when (prefs.theme) {
        "dark" -> true
        "light" -> false
        else -> isNight()
    }

    private fun isNight() = drive.state.location?.let { Sun.isNight(it.latitude, it.longitude) } ?: Sun.isNight(prefs.lastLat, prefs.lastLon)

    private val brightness by lazy { Brightness(this) }
    private var brightNight: Boolean? = null

    /** Brillo automático propio (la radio no tiene sensor de luz): cambia solo al amanecer y al atardecer. */
    private fun autoBrightness() {
        if (!prefs.autoBrightness || brightness.hasSensor) { brightNight = null; return }
        val night = isNight()
        if (night == brightNight) return
        if (brightness.set(if (night) prefs.nightBrightness else prefs.dayBrightness)) brightNight = night
    }

    /** Aplica el tema elegido y rehace la interfaz con sus colores. */
    private fun applyTheme() {
        Ui.theme(wantDark())
        host.setBackgroundColor(Ui.BG)
        window.decorView.setBackgroundColor(Ui.BG)
        if (built) rebuild(builtW, builtH, force = true)
    }

    private fun rebuild(w: Int, h: Int, force: Boolean = false) {
        if (!force && Math.abs(w - builtW) <= 4 && Math.abs(h - builtH) <= 4) return
        Log.i(TAG, "ventana ${w}x$h (pantalla ${resources.displayMetrics.widthPixels}x${resources.displayMetrics.heightPixels})")
        builtW = w
        builtH = h
        val page = if (built) current else Page.HOME
        mapCard?.onPause()
        mapCard?.release()
        host.removeAllViews()
        Ui.init(w, h)
        host.addView(build(), FrameLayout.LayoutParams(MATCH, MATCH))
        built = true
        show(page)
        if (resumed) mapCard?.onResume()
        loadCar()
        drive.state.let { s -> carCard.update(s); mapCard?.update(s); panelCard?.update(s); topBar.gps(s.hasFix) }
        mediaCard.update(media.now, media.hasAccess())
        musicPage.update(media.now)
        showApps()
        main.removeCallbacks(tick)
        main.post(tick)
        if (!prefs.onboarded || onboarding != null) showOnboarding(onboarding?.page ?: 0)
    }

    // MARK: presentación del primer arranque

    private var onboarding: Onboarding? = null

    private fun showOnboarding(page: Int = 0) {
        onboarding?.let { host.removeView(it) }
        val o = Onboarding(this,
            car = { v -> v.draw(runCatching { CarBody.valueOf(prefs.carBody) }.getOrDefault(CarBody.WAGON), prefs.carColor) },
            hasLocation = { checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED },
            hasMedia = { media.hasAccess() },
            isDefaultHome = ::isDefaultHome,
            askLocation = { askLocation(fromIntro = true) },
            askMedia = ::askMediaAccess,
            makeDefault = ::makeDefaultHome,
            onDone = ::finishOnboarding,
            startPage = page,
        )
        onboarding = o
        host.addView(o, FrameLayout.LayoutParams(MATCH, MATCH))
    }

    private fun finishOnboarding() {
        prefs.onboarded = true
        onboarding?.let { v -> v.animate().alpha(0f).setDuration(200).withEndAction { host.removeView(v) }.start() }
        onboarding = null
        show(Page.HOME)
        askLocation() // por si se saltó la página de permisos: sin ubicación no hay velocidad
    }

    private fun isDefaultHome(): Boolean =
        packageManager.resolveActivity(Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME), PackageManager.MATCH_DEFAULT_ONLY)
            ?.activityInfo?.packageName == packageName

    /** Ajustes → Aplicación de inicio; si la radio no lo tiene, el selector de inicio del sistema. */
    private fun makeDefaultHome() {
        val tries = listOf(Intent(Settings.ACTION_HOME_SETTINGS), Intent(Settings.ACTION_MANAGE_DEFAULT_APPS_SETTINGS),
            Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME))
        for (i in tries) if (runCatching { startActivity(i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }.isSuccess) return
        Toast.makeText(this, "Ajustes → Apps → Aplicación de inicio → EdgeOS Car", Toast.LENGTH_LONG).show()
    }

    /** El coche del constructor o, si se eligió, la foto propia (si no se puede leer, vuelve al dibujo). */
    private fun loadCar() {
        drawCar()
        if (!prefs.carPhoto) return
        Thread {
            val b = CarImage.load(this)
            Log.i(TAG, "foto del coche: ${b?.width}x${b?.height}")
            main.post { if (b != null) carCard.car.photo(b) }
        }.start()
    }

    private fun drawCar() {
        val body = runCatching { CarBody.valueOf(prefs.carBody) }.getOrDefault(CarBody.WAGON)
        carCard.car.draw(body, prefs.carColor)
        Log.i(TAG, "coche: ${carCard.car.describe()}")
    }

    private fun showApps() {
        appsPage.set(apps.list)
        quick.setApps(Slot.QUICK.map { apps.forSlot(it, prefs) })
    }

    private fun build(): View {
        val root = LinearLayout(this)
        root.setBackgroundColor(Ui.BG)
        rail = Rail(this)
        rail.item(Glyph.HOME, Page.HOME) { show(Page.HOME) }
        rail.item(Glyph.APPS, Page.APPS) { show(Page.APPS) }
        rail.item(Glyph.AUTO) { openSlot(Slot.AUTO) }
        rail.item(Glyph.PHONE) { openSlot(Slot.PHONE) }
        rail.item(Glyph.MUSIC, Page.MUSIC) { show(Page.MUSIC) }
        rail.spacer()
        rail.item(Glyph.SYSTEM, Page.SYSTEM) { show(Page.SYSTEM) }
        root.addView(rail, lp(px(84), MATCH))

        val content = Ui.column(this)
        // Mismo margen a los dos lados: las tarjetas no se pegan a la barra lateral.
        content.setPadding(px(16), 0, px(16), px(16))
        topBar = TopBar(this)
        topBar.onWifi { show(Page.SYSTEM) }
        content.addView(topBar, lp(MATCH, px(52)))
        pages = FrameLayout(this)
        content.addView(pages, lp(MATCH, 0, 1f))
        root.addView(content, lp(0, MATCH, 1f))

        // Inicio: coche | mapa o panel | música y accesos.
        home = Ui.row(this)
        home.gravity = Gravity.TOP
        carCard = CarCard(this)
        carCard.car.setOnClickListener { pickCar() }
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
                prefs.carPhoto = false
                CarImage.reset(this)
                drawCar()
            },
            onCarChanged = ::drawCar,
            onThemeChanged = ::applyTheme,
            onMapsChanged = { if (prefs.showMap) showCenter() },
            onShowIntro = { showOnboarding(0) },
            isNight = { isNight() },
            askBluetooth = { requestPermissions(arrayOf(Manifest.permission.BLUETOOTH_CONNECT), BLUETOOTH) },
        )
        musicPage = MusicPage(this, media, player,
            hasStorage = { checkSelfPermission(AUDIO_PERMISSION) == PackageManager.PERMISSION_GRANTED },
            askStorage = { requestPermissions(arrayOf(AUDIO_PERMISSION), MUSIC_STORAGE) },
            openApp = { pkg -> packageManager.getLaunchIntentForPackage(pkg)?.let { runCatching { startActivity(it) } } },
        )
        // Tocar la tarjeta de música de Inicio abre la pantalla Música.
        mediaCard.setOnClickListener { show(Page.MUSIC) }
        for (v in listOf(home, appsPage, musicPage, systemPage)) pages.addView(v, FrameLayout.LayoutParams(MATCH, MATCH))
        show(Page.HOME)
        return root
    }

    private fun show(page: Page) {
        current = page
        home.visibility = if (page == Page.HOME) View.VISIBLE else View.GONE
        appsPage.visibility = if (page == Page.APPS) View.VISIBLE else View.GONE
        systemPage.visibility = if (page == Page.SYSTEM) View.VISIBLE else View.GONE
        musicPage.visibility = if (page == Page.MUSIC) View.VISIBLE else View.GONE
        if (page == Page.MUSIC) musicPage.onShow()
        rail.select(page)
        if (page == Page.SYSTEM) systemPage.onShow()
    }

    /** En el centro, el mapa o el panel de conducción, según Ajustes. */
    private fun showCenter() {
        center.removeAllViews()
        mapCard?.onPause()
        mapCard?.release()
        mapCard = null
        panelCard = null
        if (prefs.showMap) {
            mapCard = MapCard(this, { openSlot(Slot.NAV) }, Maps.installed(this), onGetMaps = { MapsUi.offerMadrid(this) },
                headingUp = prefs.mapHeadingUp, onHeadingUpChanged = { prefs.mapHeadingUp = it })
                .also { center.addView(it) }
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
        Ui.dialog(this)
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
        Ui.dialog(this)
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

    /** Primero el selector propio (muchas radios no traen gestor de archivos); el del sistema, como opción. */
    private fun pickCar() {
        if (checkSelfPermission(IMAGE_PERMISSION) != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(arrayOf(IMAGE_PERMISSION), STORAGE)
            return
        }
        Thread {
            val files = runCatching { ImagePicker.find() }.getOrDefault(emptyList())
            Log.i(TAG, "imágenes encontradas: ${files.size}")
            main.post {
                ImagePicker.show(this, files, onPick = { f ->
                    Thread { applyCar(runCatching { CarImage.save(this, f) }.onFailure { Log.w(TAG, "no se pudo copiar $f", it) }.getOrNull()) }.start()
                }, onSystem = ::systemPicker)
            }
        }.start()
    }

    private fun systemPicker() {
        val open = Intent(Intent.ACTION_OPEN_DOCUMENT).addCategory(Intent.CATEGORY_OPENABLE).setType("image/*")
        val get = Intent(Intent.ACTION_GET_CONTENT).addCategory(Intent.CATEGORY_OPENABLE).setType("image/*")
        for (intent in listOf(open, get)) {
            if (runCatching { startActivityForResult(intent, PICK_CAR) }.isSuccess) return
        }
        Toast.makeText(this, "Esta radio no tiene gestor de archivos: usa la lista de imágenes.", Toast.LENGTH_LONG).show()
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        val uri = data?.data
        if (requestCode != PICK_CAR || resultCode != RESULT_OK || uri == null) return
        Thread { applyCar(runCatching { CarImage.save(this, uri) }.onFailure { Log.w(TAG, "no se pudo copiar $uri", it) }.getOrNull()) }.start()
    }

    private fun applyCar(bitmap: android.graphics.Bitmap?) = main.post {
        if (bitmap == null) return@post Toast.makeText(this, "No se pudo leer esa imagen.", Toast.LENGTH_LONG).show()
        prefs.carPhoto = true
        carCard.car.photo(bitmap)
        if (built && current == Page.SYSTEM) systemPage.onShow()
        Toast.makeText(this, "Foto del coche puesta.", Toast.LENGTH_SHORT).show()
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

    /** Al arrancar solo si ya vio la presentación (si no, se pide en ella, explicando para qué). */
    private fun askLocation(fromIntro: Boolean = false) {
        if (!fromIntro && !prefs.onboarded) return
        if (checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(arrayOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION), 1)
        }
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, results: IntArray) {
        if (requestCode == MUSIC_STORAGE) {
            musicPage.scan(force = true)
            return
        }
        if (requestCode == BLUETOOTH) {
            systemPage.refreshBluetooth()
            return
        }
        if (requestCode == STORAGE) {
            if (results.firstOrNull() == PackageManager.PERMISSION_GRANTED) pickCar() else systemPicker()
            return
        }
        onboarding?.refresh()
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
        onboarding?.refresh()
        resumed = true
        drive.start()
        media.start()
        mapCard?.onResume()
        if (built) main.post(tick)
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

    /** Al terminar de bajarse un mapa, el mapa de Inicio pasa a dibujarse sin conexión. */
    private val mapDone = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            if (!built) return
            if (prefs.showMap) showCenter()
            systemPage.refreshMaps()
        }
    }

    override fun onDestroy() {
        runCatching { unregisterReceiver(mapDone) }
        player.release()
        apps.stop()
        super.onDestroy()
    }

    /** Inicio con el launcher delante: volver a la página principal. */
    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        if (!built) return
        if (intent.getStringExtra(EXTRA_PAGE) == "music") show(Page.MUSIC)
        else if (intent.hasCategory(Intent.CATEGORY_HOME)) show(Page.HOME)
    }

    companion object {
        const val EXTRA_PAGE = "page"
        private const val TAG = "EdgeOSCar"
        private const val PICK_CAR = 7
        private const val STORAGE = 8
        private const val MUSIC_STORAGE = 9
        private const val BLUETOOTH = 10

        // Android 13 separó el permiso de almacenamiento por tipo de archivo.
        private val AUDIO_PERMISSION =
            if (Build.VERSION.SDK_INT >= 33) Manifest.permission.READ_MEDIA_AUDIO else Manifest.permission.READ_EXTERNAL_STORAGE
        private val IMAGE_PERMISSION =
            if (Build.VERSION.SDK_INT >= 33) Manifest.permission.READ_MEDIA_IMAGES else Manifest.permission.READ_EXTERNAL_STORAGE
    }

    @Deprecated("Deprecated in Java")
    @SuppressLint("GestureBackNavigation") // Desde Android 13 se usa el callback de onCreate.
    override fun onBackPressed() = goBack()

    // Atrás: página anterior de la presentación, o volver a Inicio. Un launcher nunca se cierra.
    private fun goBack() {
        onboarding?.let { if (it.page > 0) it.go(it.page - 1); return }
        if (built && current != Page.HOME) show(Page.HOME)
    }
}
