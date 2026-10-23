package io.vidrio.launcher

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.ApplicationInfo
import android.content.pm.LauncherApps
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Path
import android.graphics.RectF
import android.graphics.drawable.AdaptiveIconDrawable
import android.graphics.drawable.Drawable
import android.net.Uri
import android.os.Handler
import android.os.Looper
import android.os.Process
import android.os.UserHandle
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.Collator
import java.util.concurrent.ConcurrentHashMap

class AppEntry(
    /** "paquete/actividad/usuario": estable entre reinicios, se guarda en el dock. */
    val key: String,
    val label: String,
    val component: ComponentName,
    val user: UserHandle,
    val isSystem: Boolean,
    val icon: ImageBitmap,
) {
    val packageName: String get() = component.packageName
}

/** Las apps instaladas (de todos los perfiles), al día con LauncherApps. */
class AppsModel(private val context: Context, private val scope: CoroutineScope) {
    var apps by mutableStateOf<List<AppEntry>>(emptyList())
        private set

    private val launcherApps = context.getSystemService(LauncherApps::class.java)
    private val icons = ConcurrentHashMap<String, ImageBitmap>()
    private var loadJob: Job? = null

    private val callback = object : LauncherApps.Callback() {
        override fun onPackageRemoved(packageName: String, user: UserHandle) = refresh(packageName)
        override fun onPackageAdded(packageName: String, user: UserHandle) = refresh(packageName)
        override fun onPackageChanged(packageName: String, user: UserHandle) = refresh(packageName)
        override fun onPackagesAvailable(packageNames: Array<out String>, user: UserHandle, replacing: Boolean) =
            refresh(*packageNames)
        override fun onPackagesUnavailable(packageNames: Array<out String>, user: UserHandle, replacing: Boolean) =
            refresh(*packageNames)
    }

    fun start() {
        launcherApps.registerCallback(callback, Handler(Looper.getMainLooper()))
        refresh()
    }

    fun stop() = launcherApps.unregisterCallback(callback)

    fun refresh(vararg changed: String) {
        for (pkg in changed) icons.keys.removeIf { it.startsWith("$pkg/") }
        loadJob?.cancel()
        loadJob = scope.launch { apps = withContext(Dispatchers.Default) { load() } }
    }

    private fun load(): List<AppEntry> {
        val px = (ICON_DP * context.resources.displayMetrics.density).toInt()
        val collator = Collator.getInstance()
        return launcherApps.profiles.flatMap { user ->
            launcherApps.getActivityList(null, user).mapNotNull { info ->
                val component = info.componentName
                if (component.packageName == context.packageName) return@mapNotNull null
                val key = "${component.packageName}/${component.className}/${user.hashCode()}"
                val icon = icons.getOrPut(key) {
                    val drawable = info.getIcon(context.resources.displayMetrics.densityDpi)
                    val badged = if (user == Process.myUserHandle()) drawable
                        else context.packageManager.getUserBadgedIcon(drawable, user)
                    renderIcon(badged, px).asImageBitmap()
                }
                AppEntry(
                    key = key,
                    label = info.label.toString().trim(),
                    component = component,
                    user = user,
                    isSystem = info.applicationInfo.flags and ApplicationInfo.FLAG_SYSTEM != 0,
                    icon = icon,
                )
            }
        }.sortedWith { a, b -> collator.compare(a.label, b.label) }
    }

    fun launch(app: AppEntry) {
        runCatching { launcherApps.startMainActivity(app.component, app.user, null, null) }
    }

    fun openInfo(app: AppEntry) {
        runCatching { launcherApps.startAppDetailsActivity(app.component, app.user, null, null) }
    }

    fun uninstall(app: AppEntry) {
        val intent = Intent(Intent.ACTION_DELETE, Uri.parse("package:${app.packageName}"))
            .putExtra(Intent.EXTRA_USER, app.user)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        runCatching { context.startActivity(intent) }
    }

    /** Teléfono, mensajes, navegador y cámara: las apps que el sistema tiene por defecto. */
    fun defaultDock(): List<String> {
        val intents = listOf(
            Intent(Intent.ACTION_DIAL),
            Intent(Intent.ACTION_SENDTO, Uri.parse("smsto:")),
            Intent(Intent.ACTION_VIEW, Uri.parse("https://example.com")),
            Intent("android.media.action.STILL_IMAGE_CAMERA"),
        )
        val me = Process.myUserHandle()
        return intents.mapNotNull { intent ->
            val pkg = context.packageManager
                .resolveActivity(intent, PackageManager.MATCH_DEFAULT_ONLY)?.activityInfo?.packageName
                ?: return@mapNotNull null
            apps.firstOrNull { it.packageName == pkg && it.user == me }?.key
        }.distinct().take(Prefs.DOCK_SIZE)
    }

    companion object {
        const val ICON_DP = 62f

        /** El icono dentro de un cuadrado redondeado como los de iOS, sin la máscara de cada fabricante. */
        fun renderIcon(drawable: Drawable, px: Int): Bitmap {
            val bitmap = Bitmap.createBitmap(px, px, Bitmap.Config.ARGB_8888)
            val canvas = Canvas(bitmap)
            val radius = px * 0.225f
            canvas.clipPath(Path().apply { addRoundRect(RectF(0f, 0f, px.toFloat(), px.toFloat()), radius, radius, Path.Direction.CW) })
            canvas.drawColor(android.graphics.Color.WHITE)
            if (drawable is AdaptiveIconDrawable) {
                // Las capas miden 108dp y se ven 72dp: un 25 % sobra a cada lado.
                val inset = (px * 0.25f).toInt()
                for (layer in listOf(drawable.background, drawable.foreground)) {
                    layer ?: continue
                    layer.setBounds(-inset, -inset, px + inset, px + inset)
                    layer.draw(canvas)
                }
            } else {
                val pad = (px * 0.14f).toInt()
                drawable.setBounds(pad, pad, px - pad, px - pad)
                drawable.draw(canvas)
            }
            return bitmap
        }
    }
}
