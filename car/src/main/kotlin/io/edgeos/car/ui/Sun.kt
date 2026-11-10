package io.edgeos.car.ui

import java.util.Calendar
import java.util.TimeZone
import kotlin.math.acos
import kotlin.math.asin
import kotlin.math.atan
import kotlin.math.cos
import kotlin.math.floor
import kotlin.math.sin
import kotlin.math.tan

/** Amanecer y atardecer (algoritmo del Almanaque Náutico), para el modo oscuro automático. */
object Sun {
    private fun rad(d: Double) = Math.toRadians(d)
    private fun deg(r: Double) = Math.toDegrees(r)
    private fun norm(v: Double, max: Double) = ((v % max) + max) % max

    /** Hora UTC (0–24) de salida o puesta; null si ese día no sale o no se pone. */
    private fun utcHour(dayOfYear: Int, lat: Double, lon: Double, rising: Boolean): Double? {
        val lngHour = lon / 15
        val t = dayOfYear + ((if (rising) 6 else 18) - lngHour) / 24
        val m = 0.9856 * t - 3.289
        val l = norm(m + 1.916 * sin(rad(m)) + 0.020 * sin(rad(2 * m)) + 282.634, 360.0)
        var ra = norm(deg(atan(0.91764 * tan(rad(l)))), 360.0)
        ra += floor(l / 90) * 90 - floor(ra / 90) * 90
        ra /= 15
        val sinDec = 0.39782 * sin(rad(l))
        val cosDec = cos(asin(sinDec))
        val cosH = (cos(rad(90.833)) - sinDec * sin(rad(lat))) / (cosDec * cos(rad(lat)))
        if (cosH > 1 || cosH < -1) return null
        val h = (if (rising) 360 - deg(acos(cosH)) else deg(acos(cosH))) / 15
        return norm(h + ra - 0.06571 * t - 6.622 - lngHour, 24.0)
    }

    /** ¿Es de noche ahora en ese lugar? */
    fun isNight(lat: Double, lon: Double, now: Long = System.currentTimeMillis()): Boolean {
        val c = Calendar.getInstance(TimeZone.getTimeZone("UTC")).apply { timeInMillis = now }
        val day = c.get(Calendar.DAY_OF_YEAR)
        val hour = c.get(Calendar.HOUR_OF_DAY) + c.get(Calendar.MINUTE) / 60.0
        val rise = utcHour(day, lat, lon, true)
        val set = utcHour(day, lat, lon, false)
        if (rise == null || set == null) {
            // Día o noche polar: se decide por la declinación (verano en el hemisferio del lugar).
            val summer = (day in 80..265) == (lat >= 0)
            return !summer
        }
        return if (rise < set) hour < rise || hour > set else hour > set && hour < rise
    }
}
