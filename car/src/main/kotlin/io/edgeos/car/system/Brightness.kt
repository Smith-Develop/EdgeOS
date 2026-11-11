package io.edgeos.car.system

import android.content.Context
import android.content.Intent
import android.hardware.Sensor
import android.hardware.SensorManager
import android.net.Uri
import android.provider.Settings

/**
 * El brillo de la pantalla (0–255). Escribirlo pide el permiso "Modificar ajustes del sistema"; si no está, se usa
 * el acceso de sistema de EdgeOS (root o el ADB de la radio), igual que Servicios innecesarios.
 */
class Brightness(context: Context) {
    private val app = context.applicationContext
    private val cr = app.contentResolver

    /** Casi ninguna radio trae sensor de luz: entonces el modo automático lo hace EdgeOS con el amanecer y el atardecer. */
    val hasSensor: Boolean by lazy {
        runCatching { app.getSystemService(SensorManager::class.java).getDefaultSensor(Sensor.TYPE_LIGHT) != null }.getOrDefault(false)
    }

    val level: Int get() = runCatching { Settings.System.getInt(cr, Settings.System.SCREEN_BRIGHTNESS) }.getOrDefault(128)

    val systemAuto: Boolean
        get() = runCatching {
            Settings.System.getInt(cr, Settings.System.SCREEN_BRIGHTNESS_MODE) == Settings.System.SCREEN_BRIGHTNESS_MODE_AUTOMATIC
        }.getOrDefault(false)

    val canWrite: Boolean get() = Settings.System.canWrite(app)

    /** ¿Se puede cambiar, de una forma u otra? */
    val writable: Boolean get() = canWrite || Shell.access.state == Shell.State.Ready

    fun permissionIntent() = Intent(Settings.ACTION_MANAGE_WRITE_SETTINGS, Uri.parse("package:${app.packageName}"))

    fun set(value: Int) = put(Settings.System.SCREEN_BRIGHTNESS, value.coerceIn(MIN, 255))

    fun setSystemAuto(on: Boolean) = put(
        Settings.System.SCREEN_BRIGHTNESS_MODE,
        if (on) Settings.System.SCREEN_BRIGHTNESS_MODE_AUTOMATIC else Settings.System.SCREEN_BRIGHTNESS_MODE_MANUAL,
    )

    private fun put(key: String, value: Int): Boolean {
        if (canWrite && runCatching { Settings.System.putInt(cr, key, value) }.getOrDefault(false)) return true
        if (Shell.access.state != Shell.State.Ready) return false
        // El shell bloquea: en otro hilo, y el último valor gana.
        Thread { runCatching { Shell.run("settings put system $key $value") } }.start()
        return true
    }

    companion object {
        /** Nunca del todo a oscuras: en algunas radios 0 apaga la retroiluminación. */
        const val MIN = 8
    }
}
