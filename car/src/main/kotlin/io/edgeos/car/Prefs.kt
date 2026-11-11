package io.edgeos.car

import android.content.Context

/** Lo que el launcher recuerda entre arranques. */
class Prefs(context: Context) {
    private val sp = context.getSharedPreferences("edgeos_car", Context.MODE_PRIVATE)

    /** true: mapa de OpenStreetMap; false: panel de conducción (sin internet). */
    var showMap: Boolean
        get() = sp.getBoolean("show_map", true)
        set(v) = sp.edit().putBoolean("show_map", v).apply()

    /** Mapa con el rumbo hacia arriba (lo que tienes delante, arriba) o con el norte arriba. */
    var mapHeadingUp: Boolean
        get() = sp.getBoolean("map_heading_up", true)
        set(v) = sp.edit().putBoolean("map_heading_up", v).apply()

    /** Brillo automático hecho por EdgeOS (sin sensor de luz): un nivel para el día y otro para la noche. */
    var autoBrightness: Boolean
        get() = sp.getBoolean("auto_brightness", false)
        set(v) = sp.edit().putBoolean("auto_brightness", v).apply()
    var dayBrightness: Int
        get() = sp.getInt("day_brightness", 230)
        set(v) = sp.edit().putInt("day_brightness", v).apply()
    var nightBrightness: Int
        get() = sp.getInt("night_brightness", 70)
        set(v) = sp.edit().putInt("night_brightness", v).apply()
    /** Ya vio la presentación del primer arranque. */
    var onboarded: Boolean
        get() = sp.getBoolean("onboarded", false)
        set(v) = sp.edit().putBoolean("onboarded", v).apply()

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

    /** Tema: "auto" (claro de día, oscuro de noche, según el sol), "light" o "dark". */
    var theme: String
        get() = sp.getString("theme", "auto")!!
        set(v) = sp.edit().putString("theme", v).apply()

    /** El coche del constructor, y si en su lugar se usa una foto propia. */
    var carBody: String
        get() = sp.getString("car_body", "WAGON")!!
        set(v) = sp.edit().putString("car_body", v).apply()
    var carColor: Int
        get() = sp.getInt("car_color", 0xFFB9BEC5.toInt())
        set(v) = sp.edit().putInt("car_color", v).apply()
    var carPhoto: Boolean
        get() = sp.getBoolean("car_photo", false)
        set(v) = sp.edit().putBoolean("car_photo", v).apply()

    /** Última posición conocida, para saber cuándo anochece aunque aún no haya GPS. Madrid por defecto. */
    var lastLat: Double
        get() = java.lang.Double.longBitsToDouble(sp.getLong("last_lat", java.lang.Double.doubleToRawLongBits(40.4168)))
        set(v) = sp.edit().putLong("last_lat", java.lang.Double.doubleToRawLongBits(v)).apply()
    var lastLon: Double
        get() = java.lang.Double.longBitsToDouble(sp.getLong("last_lon", java.lang.Double.doubleToRawLongBits(-3.7038)))
        set(v) = sp.edit().putLong("last_lon", java.lang.Double.doubleToRawLongBits(v)).apply()

    /** Botones del volante aprendidos (ver keys/Keys.kt). */
    var keyIds: Set<String>
        get() = sp.getStringSet("key_ids", emptySet())!!.toSet()
        set(v) = sp.edit().putStringSet("key_ids", v).apply()
    fun keyAction(id: String, long: Boolean): String? = sp.getString("key_${id}_${if (long) "long" else "short"}", null)
    fun setKeyAction(id: String, long: Boolean, action: String?) =
        sp.edit().putString("key_${id}_${if (long) "long" else "short"}", action).apply()

    /** Escalas de animación antes de "Animaciones rápidas", para devolverlas. */
    var savedAnimScales: String?
        get() = sp.getString("anim_scales", null)
        set(v) = sp.edit().putString("anim_scales", v).apply()
}
