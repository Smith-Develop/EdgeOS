package io.edgeos.car

import android.content.Context

/** Lo que el launcher recuerda entre arranques. */
class Prefs(context: Context) {
    private val sp = context.getSharedPreferences("edgeos_car", Context.MODE_PRIVATE)

    /** true: mapa de OpenStreetMap; false: panel de conducción (sin internet). */
    var showMap: Boolean
        get() = sp.getBoolean("show_map", true)
        set(v) = sp.edit().putBoolean("show_map", v).apply()

    /** Corrección de la velocidad en %, para GPS que marcan de más o de menos. */
    var speedOffset: Int
        get() = sp.getInt("speed_offset", 0)
        set(v) = sp.edit().putInt("speed_offset", v.coerceIn(-15, 15)).apply()

    /** Apps de los accesos rápidos, como "paquete/actividad". Vacío: se busca una automáticamente. */
    fun app(slot: String): String? = sp.getString("app_$slot", null)
    fun setApp(slot: String, component: String?) = sp.edit().putString("app_$slot", component).apply()

    // El viaje sobrevive a apagar el coche: estas radios se apagan con el contacto.
    var tripMeters: Double
        get() = java.lang.Double.longBitsToDouble(sp.getLong("trip_m", 0L))
        set(v) = sp.edit().putLong("trip_m", java.lang.Double.doubleToRawLongBits(v)).apply()
    var tripSeconds: Long
        get() = sp.getLong("trip_s", 0L)
        set(v) = sp.edit().putLong("trip_s", v).apply()
    var tripMax: Float
        get() = sp.getFloat("trip_max", 0f)
        set(v) = sp.edit().putFloat("trip_max", v).apply()

    /** Paquetes que desactivó Optimizar: "Restaurar todo" los vuelve a activar. */
    var disabledByUs: Set<String>
        get() = sp.getStringSet("disabled", emptySet())!!.toSet()
        set(v) = sp.edit().putStringSet("disabled", v).apply()

    /** Escalas de animación antes de "Animaciones rápidas", para devolverlas. */
    var savedAnimScales: String?
        get() = sp.getString("anim_scales", null)
        set(v) = sp.edit().putString("anim_scales", v).apply()
}
