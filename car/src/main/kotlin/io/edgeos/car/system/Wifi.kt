package io.edgeos.car.system

import android.content.Context
import android.net.wifi.ScanResult
import android.net.wifi.WifiConfiguration
import android.net.wifi.WifiManager
import android.os.Build

/** Una red del entorno: nombre, intensidad de 0 a 4 y si pide contraseña. */
data class WifiNetwork(val ssid: String, val level: Int, val secure: Boolean, val wep: Boolean, val saved: Boolean)

/**
 * El Wi-Fi gestionado desde el launcher. En Android 8 y 9 una app puede encenderlo, buscar redes y conectarse;
 * desde Android 10 Google lo prohibió y [canControl] es false: entonces se abre el panel del sistema.
 */
@Suppress("DEPRECATION") // Las API de Wi-Fi de Android 8, sustituidas en Android 10, que es justo lo que no tiene la radio.
class Wifi(context: Context) {
    private val wm by lazy { context.applicationContext.getSystemService(WifiManager::class.java) }

    val canControl: Boolean get() = Build.VERSION.SDK_INT < Build.VERSION_CODES.Q

    val enabled: Boolean get() = wm.isWifiEnabled

    fun setEnabled(on: Boolean): Boolean = wm.setWifiEnabled(on)

    /** La red a la que está conectada ahora (sin las comillas de Android), o null. */
    fun current(): String? {
        val info = wm.connectionInfo ?: return null
        if (info.networkId == -1) return null
        return info.ssid?.removeSurrounding("\"")?.takeUnless { it.isEmpty() || it == "<unknown ssid>" }
    }

    fun currentLevel(): Int = wm.connectionInfo?.let { WifiManager.calculateSignalLevel(it.rssi, 5) } ?: 0

    fun scan() {
        wm.startScan()
    }

    /** Las redes que se ven, una por nombre (la de mejor señal), las guardadas y las mejores primero. */
    fun networks(): List<WifiNetwork> {
        val saved = runCatching { wm.configuredNetworks?.mapNotNull { it.SSID?.removeSurrounding("\"") } }.getOrNull().orEmpty().toSet()
        val results: List<ScanResult> = runCatching { wm.scanResults }.getOrNull().orEmpty()
        return results.filter { !it.SSID.isNullOrBlank() }
            .groupBy { it.SSID }
            .map { (ssid, list) ->
                val best = list.maxByOrNull { it.level }!!
                val caps = best.capabilities ?: ""
                WifiNetwork(ssid, WifiManager.calculateSignalLevel(best.level, 5),
                    secure = caps.contains("WPA") || caps.contains("WEP") || caps.contains("PSK") || caps.contains("SAE"),
                    wep = caps.contains("WEP"), saved = ssid in saved)
            }
            .sortedWith(compareByDescending<WifiNetwork> { it.ssid == current() }.thenByDescending { it.saved }.thenByDescending { it.level })
    }

    /** Se conecta: a la red guardada si ya existe; si no, la crea (con contraseña si es segura). */
    fun connect(n: WifiNetwork, password: String?): Boolean {
        val quoted = "\"${n.ssid}\""
        val existing = runCatching { wm.configuredNetworks?.firstOrNull { it.SSID == quoted } }.getOrNull()
        val id = if (existing != null && password == null) existing.networkId else {
            existing?.let { wm.removeNetwork(it.networkId) }
            val config = WifiConfiguration().apply {
                SSID = quoted
                when {
                    !n.secure -> allowedKeyManagement.set(WifiConfiguration.KeyMgmt.NONE)
                    n.wep -> {
                        allowedKeyManagement.set(WifiConfiguration.KeyMgmt.NONE)
                        wepKeys[0] = "\"$password\""
                        wepTxKeyIndex = 0
                    }
                    else -> preSharedKey = "\"$password\""
                }
            }
            wm.addNetwork(config)
        }
        if (id == -1) return false
        wm.disconnect()
        val ok = wm.enableNetwork(id, true)
        wm.reconnect()
        return ok
    }
}
