package io.edgeos.car.system

import android.app.ActivityManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Environment
import android.os.StatFs
import io.edgeos.car.Prefs
import org.json.JSONObject
import java.io.File
import java.net.HttpURLConnection
import java.net.URL

data class Service(
    val packageName: String,
    val name: String,
    val why: String,
    /** "Recomendada": nadie la echa de menos en un coche. "Opcional": puede que la uses. */
    val level: String,
    val enabled: Boolean,
)

/**
 * Desactiva servicios que sobran en una radio de coche con `pm disable-user`: siguen instalados y vuelven con
 * "Restaurar todo". Dos fuentes: una lista propia para radios MediaTek genéricas (como la k80_bsp) y las
 * "Recommended" de UAD-ng (GPL-3, se descarga al usarla). Nada que suene a coche se ofrece nunca.
 */
class Optimizer(private val context: Context, private val prefs: Prefs) {

    fun services(): List<Service> {
        val installed = packages("pm list packages --user 0")
        val disabled = packages("pm list packages -d --user 0")
        val ours = prefs.disabledByUs
        val protected = protectedPackages()
        val uad = runCatching { uadList() }.getOrNull()

        return installed.mapNotNull { pkg ->
            if (pkg in protected) return@mapNotNull null
            val own = HEAD_UNIT[pkg]
            val entry = uad?.optJSONObject(pkg)
            val fromUad = entry != null && entry.optString("removal") == "Recommended" && !isCarRelated(pkg)
            if (own == null && !fromUad && pkg !in ours) return@mapNotNull null
            val text = entry?.optString("description").orEmpty().trim()
            Service(
                packageName = pkg,
                name = own?.first ?: text.lineSequence().firstOrNull()?.substringBefore(" (http")?.trim()?.ifEmpty { null } ?: pkg,
                why = own?.second ?: text.lineSequence().drop(1).joinToString(" ").trim(),
                level = own?.third ?: "Recomendada",
                enabled = pkg !in disabled,
            )
        }.sortedWith(compareBy({ !it.enabled }, { it.level != "Recomendada" }, { it.name.lowercase() }))
    }

    fun disable(pkg: String): Boolean {
        require(PACKAGE.matches(pkg) && pkg !in protectedPackages())
        val ok = Shell.run("pm disable-user --user 0 $pkg").contains("disabled-user")
        if (ok) prefs.disabledByUs = prefs.disabledByUs + pkg
        return ok
    }

    fun enable(pkg: String): Boolean {
        require(PACKAGE.matches(pkg))
        val ok = Shell.run("pm enable --user 0 $pkg").contains("enabled")
        if (ok) prefs.disabledByUs = prefs.disabledByUs - pkg
        return ok
    }

    /** Devuelve los que no se pudieron reactivar. */
    fun restoreAll(): List<String> {
        val failed = prefs.disabledByUs.filterNot { enable(it) }
        prefs.savedAnimScales?.let { setAnimations(it.split(',')) ; prefs.savedAnimScales = null }
        return failed
    }

    // Animaciones a 0,5×: el MT6580 va más suelto y se nota al abrir apps. Se guardan las de antes.
    fun fastAnimations(on: Boolean) {
        if (on) {
            if (prefs.savedAnimScales == null) prefs.savedAnimScales = ANIM_KEYS.joinToString(",") {
                Shell.run("settings get global $it").takeIf { v -> v.toFloatOrNull() != null } ?: "1.0"
            }
            setAnimations(ANIM_KEYS.map { "0.5" })
        } else {
            setAnimations(prefs.savedAnimScales?.split(',') ?: ANIM_KEYS.map { "1.0" })
            prefs.savedAnimScales = null
        }
    }

    fun animationsFast() = prefs.savedAnimScales != null

    private fun setAnimations(values: List<String>) {
        ANIM_KEYS.zip(values).forEach { (key, value) ->
            if (value.toFloatOrNull() != null) Shell.run("settings put global $key $value")
        }
    }

    /**
     * Cierra lo que está en segundo plano, menos lo que suena, la radio y lo del coche. No necesita acceso al sistema.
     * Devuelve los MB que quedan libres.
     */
    fun freeMemory(keep: Set<String>): Long {
        val am = context.getSystemService(ActivityManager::class.java)
        val launchable = context.packageManager.queryIntentActivities(
            Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER), 0
        ).map { it.activityInfo.packageName }.toSet()
        for (pkg in launchable) {
            if (pkg == context.packageName || pkg in keep || isCarRelated(pkg)) continue
            am.killBackgroundProcesses(pkg)
        }
        return memory().first
    }

    /** RAM libre y total, en MB. */
    fun memory(): Pair<Long, Long> {
        val info = ActivityManager.MemoryInfo()
        context.getSystemService(ActivityManager::class.java).getMemoryInfo(info)
        return info.availMem / MB to info.totalMem / MB
    }

    /** Almacenamiento libre y total, en GB. */
    fun storage(): Pair<Double, Double> {
        val s = StatFs(Environment.getDataDirectory().path)
        return s.availableBytes / GB to s.totalBytes / GB
    }

    /** Temperatura del procesador en °C, si el sistema deja leerla (muchas radios sí). */
    fun cpuTemp(): Int? = (0..9).asSequence()
        .mapNotNull { runCatching { File("/sys/class/thermal/thermal_zone$it/temp").readText().trim().toInt() }.getOrNull() }
        .map { if (it > 1000) it / 1000 else it }
        .firstOrNull { it in 15..120 }

    private fun packages(command: String): Set<String> =
        Shell.run(command).lineSequence().map { it.removePrefix("package:").trim() }.filter { PACKAGE.matches(it) }.toSet()

    /** Los launchers (también el de fábrica), este launcher y las piezas del sistema. */
    private fun protectedPackages(): Set<String> {
        val home = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME)
        return context.packageManager.queryIntentActivities(home, PackageManager.MATCH_ALL)
            .map { it.activityInfo.packageName }.toSet() + setOf(
            context.packageName, "android", "com.android.systemui", "com.android.settings",
            "com.android.phone", "com.android.shell", "com.android.packageinstaller", "com.android.webview",
        )
    }

    private fun uadList(): JSONObject {
        val cache = File(context.filesDir, "uad_lists.json")
        if (!cache.exists() || System.currentTimeMillis() - cache.lastModified() > WEEK) runCatching {
            val c = URL(UAD_URL).openConnection() as HttpURLConnection
            c.connectTimeout = 15_000
            c.readTimeout = 30_000
            try {
                val text = c.inputStream.bufferedReader().use { it.readText() }
                JSONObject(text)
                File(cache.path + ".tmp").apply { writeText(text); renameTo(cache) }
            } finally {
                c.disconnect()
            }
        }
        return JSONObject(cache.readText())
    }

    companion object {
        private const val MB = 1024L * 1024
        private const val GB = 1024.0 * 1024 * 1024
        private const val WEEK = 7L * 24 * 3600 * 1000
        private val ANIM_KEYS = listOf("window_animation_scale", "transition_animation_scale", "animator_duration_scale")
        private val PACKAGE = Regex("^[A-Za-z0-9_]+(\\.[A-Za-z0-9_]+)+$")
        const val UAD_URL = "https://raw.githubusercontent.com/Universal-Debloater-Alliance/" +
            "universal-android-debloater-next-generation/main/resources/assets/uad_lists.json"

        /**
         * Lo que suena a coche nunca se toca: CAN bus, MCU, radio, Bluetooth, cámara de marcha atrás, mandos del
         * volante, audio, GPS, CarPlay/Android Auto… Mejor proteger de más que dejar la radio sin cámara.
         */
        private val CAR = Regex(
            "can(bus|box)|mcu|radio|fm|tuner|bt|blue|phone|dial|telecom|contacts|camera|cam\\b|reverse|backcar|avm|dvr|" +
                "swc|steer|wheel|key|input|ime|launcher|home|setting|setup|systemui|provider|audio|sound|music|eq|amp|dsp|" +
                "gps|gnss|location|navi|carplay|zlink|autokit|carlink|auto|projection|mirror|link|wifi|net|ims|tpms|obd|" +
                "syu|fyt|mtc|hct|microntek|ts10|car|vehicle|power|mediatek\\.(?!mtklogger)|webview|gms|gsf|vending|update(?!r)"
        )

        fun isCarRelated(pkg: String) = CAR.containsMatchIn(pkg.lowercase())

        /** Lista propia para radios MediaTek "alps" (k80_bsp y parecidas): nombre, por qué, nivel. */
        val HEAD_UNIT = mapOf(
            "com.adups.fota" to Triple("Adups FOTA", "Actualizador de firmware conocido por enviar datos del aparato a China. Estas radios casi nunca reciben actualizaciones por aquí.", "Recomendada"),
            "com.adups.fota.sysoper" to Triple("Adups FOTA (sistema)", "Parte del actualizador Adups.", "Recomendada"),
            "com.redstone.ota.ui" to Triple("Redstone OTA", "Otro actualizador de firmware de fábrica que se conecta en segundo plano.", "Recomendada"),
            "com.mediatek.mtklogger" to Triple("MTK Logger", "Registro de depuración de MediaTek: escribe logs en segundo plano y gasta almacenamiento.", "Recomendada"),
            "com.android.browser" to Triple("Navegador antiguo", "Navegador de Android sin actualizaciones de seguridad desde hace años.", "Recomendada"),
            "com.android.email" to Triple("Correo", "Cliente de correo de Android.", "Recomendada"),
            "com.android.exchange" to Triple("Exchange", "Cuentas de Exchange para Correo.", "Recomendada"),
            "com.android.quicksearchbox" to Triple("Búsqueda rápida", "Barra de búsqueda de Android.", "Recomendada"),
            "com.android.dreams.basic" to Triple("Salvapantallas básico", "Salvapantallas de colores.", "Recomendada"),
            "com.android.dreams.phototable" to Triple("Salvapantallas de fotos", "Salvapantallas con fotos.", "Recomendada"),
            "com.android.wallpaper.livepicker" to Triple("Fondos animados", "Selector de fondos animados: gastan procesador.", "Recomendada"),
            "com.android.galaxy4" to Triple("Fondo animado Galaxy", "Fondo animado.", "Recomendada"),
            "com.android.noisefield" to Triple("Fondo animado Noise Field", "Fondo animado.", "Recomendada"),
            "com.android.phasebeam" to Triple("Fondo animado Phase Beam", "Fondo animado.", "Recomendada"),
            "com.android.printspooler" to Triple("Impresión", "Servicio de impresión.", "Recomendada"),
            "com.android.bips" to Triple("Impresión predeterminada", "Imprimir por Wi-Fi.", "Recomendada"),
            "com.android.egg" to Triple("Huevo de Pascua", "El juego escondido de la versión de Android.", "Recomendada"),
            "com.android.bookmarkprovider" to Triple("Marcadores", "Marcadores del navegador antiguo.", "Recomendada"),
            "com.android.providers.partnerbookmarks" to Triple("Marcadores del fabricante", "Marcadores precargados.", "Recomendada"),
            "com.android.calendar" to Triple("Calendario", "Agenda de Android.", "Opcional"),
            "com.android.deskclock" to Triple("Reloj y alarmas", "Despertador y temporizador.", "Opcional"),
            "com.android.gallery3d" to Triple("Galería", "Visor de fotos y vídeos.", "Opcional"),
            "com.android.soundrecorder" to Triple("Grabadora", "Grabadora de voz.", "Opcional"),
            "com.android.calculator2" to Triple("Calculadora", "Calculadora de Android.", "Opcional"),
        )
    }
}
