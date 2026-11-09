package io.edgeos.car.system

import android.content.Context
import android.content.pm.PackageManager
import rikka.shizuku.Shizuku

/**
 * Comandos de shell (uid 2000, el de adb) a través de Shizuku. En Android 8.1 no hay depuración inalámbrica:
 * Shizuku se inicia desde un ordenador por USB (o en modo root, si la radio lo tiene) tras cada arranque.
 */
object Shell {
    const val SHIZUKU = "moe.shizuku.privileged.api"
    const val REQUEST = 11

    enum class Status { NotInstalled, NotRunning, NoPermission, Ready }

    fun status(context: Context): Status = when {
        !Shizuku.pingBinder() || Shizuku.isPreV11() ->
            if (runCatching { context.packageManager.getPackageInfo(SHIZUKU, 0) }.isSuccess) Status.NotRunning else Status.NotInstalled
        Shizuku.checkSelfPermission() != PackageManager.PERMISSION_GRANTED -> Status.NoPermission
        else -> Status.Ready
    }

    private val newProcess by lazy {
        Shizuku::class.java.getDeclaredMethod(
            "newProcess", Array<String>::class.java, Array<String>::class.java, String::class.java
        ).apply { isAccessible = true }
    }

    /** Bloquea hasta que termina: llamar fuera del hilo principal. */
    fun run(command: String): String {
        val process = newProcess.invoke(null, arrayOf("sh", "-c", "$command 2>&1"), null, null) as Process
        val out = process.inputStream.bufferedReader().use { it.readText() }
        process.waitFor()
        return out.trim()
    }
}
