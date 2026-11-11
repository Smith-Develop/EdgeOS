package io.edgeos.car.system

import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager

/** Un móvil emparejado con la radio. */
data class BtDevice(val name: String, val connected: Boolean)

/**
 * El Bluetooth de la radio. Estas radios usan el Bluetooth normal de Android (la k80_bsp también), así que se
 * puede encender, apagar y ver qué móviles están emparejados y conectados. Emparejar se deja a la app de la radio,
 * que es la que gestiona las llamadas y la agenda.
 */
@Suppress("DEPRECATION", "MissingPermission") // enable()/disable() siguen funcionando en Android 8, que es lo que hay.
class Bluetooth(private val context: Context) {
    private val adapter: BluetoothAdapter? by lazy { runCatching { BluetoothAdapter.getDefaultAdapter() }.getOrNull() }

    val available: Boolean get() = adapter != null

    val enabled: Boolean get() = runCatching { adapter?.isEnabled == true }.getOrDefault(false)

    fun setEnabled(on: Boolean): Boolean = runCatching { if (on) adapter!!.enable() else adapter!!.disable() }.getOrDefault(false)

    /** El nombre con el que la ven los móviles. */
    val name: String? get() = runCatching { adapter?.name }.getOrNull()

    /** Los móviles emparejados, los conectados primero. */
    fun devices(): List<BtDevice> = runCatching {
        adapter?.bondedDevices.orEmpty().map { BtDevice(it.name ?: it.address, isConnected(it)) }
            .sortedWith(compareByDescending<BtDevice> { it.connected }.thenBy { it.name.lowercase() })
    }.getOrDefault(emptyList())

    // isConnected() existe desde Android 4.4 pero es oculto: se llama por reflexión, y si falla se da por no conectado.
    private fun isConnected(d: BluetoothDevice) =
        runCatching { d.javaClass.getMethod("isConnected").invoke(d) as Boolean }.getOrDefault(false)

    /** La app de Bluetooth de la radio (Reglink, FYT…) para emparejar y llamar; si no hay, los ajustes del sistema. */
    fun pairingIntent(): Intent {
        val pm = context.packageManager
        for (pkg in RADIO_APPS) pm.getLaunchIntentForPackage(pkg)?.let { return it }
        val launcher = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
        pm.queryIntentActivities(launcher, PackageManager.MATCH_ALL)
            .firstOrNull { it.activityInfo.packageName.contains("bluetooth", true) && it.activityInfo.packageName != "com.android.bluetooth" }
            ?.let { return pm.getLaunchIntentForPackage(it.activityInfo.packageName) ?: Intent() }
        return Intent(android.provider.Settings.ACTION_BLUETOOTH_SETTINGS)
    }

    private companion object {
        val RADIO_APPS = listOf("com.reglink.apps.bluetooth", "com.syu.bt", "com.microntek.bluetooth", "com.fyt.bluetooth")
    }
}
