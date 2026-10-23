package io.vidrio.launcher.debloat

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import io.vidrio.launcher.Prefs
import org.json.JSONObject
import java.io.File
import java.net.HttpURLConnection
import java.net.URL

/** Una app que se puede desactivar, con lo que dice de ella la lista de UAD-ng. */
data class Candidate(
    val packageName: String,
    val name: String,
    val description: String,
    /** Recommended o Advanced; Expert y Unsafe nunca se ofrecen. */
    val removal: String,
    val enabled: Boolean,
    val disabledByUs: Boolean,
)

/**
 * Desactiva apps de fábrica con `pm disable-user`: siguen instaladas y vuelven con "Restaurar".
 * La lista es la de Universal Android Debloater Next Generation (GPL-3), que se descarga al usarla
 * en lugar de ir dentro del APK.
 */
class Debloater(private val context: Context, private val prefs: Prefs) {
    private val cache = File(context.filesDir, "uad_lists.json")

    /** Llamar fuera del hilo principal. */
    fun candidates(includeAdvanced: Boolean): List<Candidate> {
        val list = loadList()
        val installed = packages("pm list packages --user 0")
        val disabled = packages("pm list packages -d --user 0")
        val protected = protectedPackages()
        val ours = prefs.disabledByUs
        val allowed = if (includeAdvanced) setOf("Recommended", "Advanced") else setOf("Recommended")

        return installed.mapNotNull { pkg ->
            if (pkg in protected) return@mapNotNull null
            val entry = list.optJSONObject(pkg)
            val removal = entry?.optString("removal").orEmpty()
            // Lo que desactivamos nosotros se muestra siempre, para poder volver a activarlo.
            if (removal !in allowed && pkg !in ours) return@mapNotNull null
            val text = entry?.optString("description").orEmpty().trim()
            Candidate(
                packageName = pkg,
                name = text.lineSequence().firstOrNull()?.substringBefore(" (http")?.trim()
                    ?.takeIf { it.isNotEmpty() } ?: pkg,
                description = text.lineSequence().drop(1).joinToString(" ").trim(),
                removal = removal.ifEmpty { "Recommended" },
                enabled = pkg !in disabled,
                disabledByUs = pkg in ours,
            )
        }.sortedWith(compareBy({ it.enabled.not() }, { it.name.lowercase() }))
    }

    fun disable(pkg: String): Boolean {
        require(PACKAGE.matches(pkg))
        val ok = Shell.run("pm disable-user --user 0 $pkg").output.contains("disabled-user")
        if (ok) prefs.disabledByUs = prefs.disabledByUs + pkg
        return ok
    }

    fun enable(pkg: String): Boolean {
        require(PACKAGE.matches(pkg))
        val ok = Shell.run("pm enable --user 0 $pkg").output.contains("enabled")
        if (ok) prefs.disabledByUs = prefs.disabledByUs - pkg
        return ok
    }

    /** Vuelve a activar todo lo que desactivó Vidrio. Devuelve los que no se pudieron. */
    fun restoreAll(): List<String> = prefs.disabledByUs.filterNot { enable(it) }

    private fun packages(command: String): Set<String> =
        Shell.run(command).output.lineSequence()
            .map { it.removePrefix("package:").trim() }
            .filter { PACKAGE.matches(it) }
            .toSet()

    /** Nunca: este launcher, Shizuku, ni ningún otro launcher (sin inicio no hay forma de volver). */
    private fun protectedPackages(): Set<String> {
        val home = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME)
        val launchers = context.packageManager
            .queryIntentActivities(home, PackageManager.MATCH_ALL)
            .map { it.activityInfo.packageName }
        return launchers.toSet() + context.packageName + Shell.SHIZUKU_PACKAGE + "android" +
            "com.android.systemui" + "com.android.settings"
    }

    private fun loadList(): JSONObject {
        val fresh = cache.exists() && System.currentTimeMillis() - cache.lastModified() < WEEK_MS
        if (!fresh) runCatching { download() }
        return JSONObject(cache.readText())
    }

    private fun download() {
        val connection = URL(LIST_URL).openConnection() as HttpURLConnection
        connection.connectTimeout = 15_000
        connection.readTimeout = 30_000
        try {
            val text = connection.inputStream.bufferedReader().use { it.readText() }
            JSONObject(text) // Que sea JSON válido antes de sustituir la copia buena.
            val tmp = File(cache.path + ".tmp")
            tmp.writeText(text)
            tmp.renameTo(cache)
        } finally {
            connection.disconnect()
        }
    }

    companion object {
        const val LIST_URL = "https://raw.githubusercontent.com/Universal-Debloater-Alliance/" +
            "universal-android-debloater-next-generation/main/resources/assets/uad_lists.json"
        private const val WEEK_MS = 7L * 24 * 60 * 60 * 1000
        private val PACKAGE = Regex("^[A-Za-z0-9_]+(\\.[A-Za-z0-9_]+)+$")
    }
}
