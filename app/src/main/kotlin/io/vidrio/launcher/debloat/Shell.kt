package io.vidrio.launcher.debloat

import android.content.Context
import android.content.pm.PackageManager
import rikka.shizuku.Shizuku

/**
 * Comandos de shell (uid 2000, el mismo que adb) a través de Shizuku: sin PC y sin root.
 * Shizuku se activa una vez con Depuración inalámbrica y tras cada reinicio.
 */
object Shell {
    const val SHIZUKU_PACKAGE = "moe.shizuku.privileged.api"
    const val PERMISSION_REQUEST = 7

    enum class Status { NotInstalled, NotRunning, NoPermission, Ready }

    class Result(val code: Int, val output: String)

    fun status(context: Context): Status = when {
        !Shizuku.pingBinder() || Shizuku.isPreV11() ->
            if (isShizukuInstalled(context)) Status.NotRunning else Status.NotInstalled
        Shizuku.checkSelfPermission() != PackageManager.PERMISSION_GRANTED -> Status.NoPermission
        else -> Status.Ready
    }

    fun isShizukuInstalled(context: Context) =
        runCatching { context.packageManager.getPackageInfo(SHIZUKU_PACKAGE, 0) }.isSuccess

    // Desde la API 13, Shizuku recomienda un UserService; para unos pocos comandos de pm, newProcess basta.
    private val newProcess by lazy {
        Shizuku::class.java.getDeclaredMethod(
            "newProcess", Array<String>::class.java, Array<String>::class.java, String::class.java
        ).apply { isAccessible = true }
    }

    /** Bloquea hasta que termina: llamar fuera del hilo principal. */
    fun run(command: String): Result {
        val process = newProcess.invoke(null, arrayOf("sh", "-c", "$command 2>&1"), null, null) as Process
        val output = process.inputStream.bufferedReader().use { it.readText() }
        return Result(process.waitFor(), output.trim())
    }
}
