package io.edgeos.car.system

import android.content.Context
import java.io.File
import java.io.IOException
import java.util.concurrent.TimeUnit

/**
 * Comandos con permisos de sistema, sin apps externas: con root si la radio lo tiene (muchas lo traen), y si no,
 * con el ADB de la propia radio por la red local ([LocalAdb]). Bloquea: llamar fuera del hilo principal.
 */
object Shell {
    enum class State { Unknown, Ready, AdbOff, Denied, Error }

    data class Access(val state: State, val via: String? = null, val detail: String? = null)

    @Volatile
    var access = Access(State.Unknown)
        private set

    private var adb: LocalAdb? = null
    private var context: Context? = null

    /** Root primero, luego el ADB local. [waiting]: la radio está mostrando "¿Permitir depuración USB?". */
    @Synchronized
    fun connect(context: Context, waiting: () -> Unit): Access {
        this.context = context.applicationContext
        if (access.state == State.Ready) return access
        access = when {
            hasRoot() -> Access(State.Ready, "root")
            !LocalAdb.listening() -> Access(State.AdbOff)
            else -> try {
                adb = LocalAdb.connect(context, 60_000, waiting)
                Access(State.Ready, "ADB")
            } catch (e: LocalAdb.NotApproved) {
                Access(State.Denied)
            } catch (e: IOException) {
                Access(State.Error, detail = e.message)
            }
        }
        return access
    }

    @Synchronized
    fun run(command: String): String = when (access.via) {
        "root" -> exec(arrayOf("su", "-c", command))
        "ADB" -> try {
            adb!!.shell("$command 2>&1").trim()
        } catch (e: IOException) {
            // La conexión se cae si la radio se suspende: una reconexión, ya sin aviso (la clave está aceptada).
            adb?.close()
            adb = LocalAdb.connect(context!!, 5000) {}
            adb!!.shell("$command 2>&1").trim()
        }
        else -> throw IOException("sin acceso al sistema")
    }

    private fun hasRoot(): Boolean {
        val found = listOf("/system/xbin/su", "/system/bin/su", "/sbin/su", "/su/bin/su", "/debug_ramdisk/su").any { File(it).exists() }
        return found && runCatching { exec(arrayOf("su", "-c", "id")).contains("uid=0") }.getOrDefault(false)
    }

    private fun exec(cmd: Array<String>): String {
        val p = ProcessBuilder(*cmd).redirectErrorStream(true).start()
        val out = p.inputStream.bufferedReader().use { it.readText() }
        if (!p.waitFor(20, TimeUnit.SECONDS)) p.destroy()
        return out.trim()
    }
}
