package io.edgeos.car.drive

import android.annotation.SuppressLint
import android.content.Context
import android.location.GnssStatus
import android.location.Location
import android.location.LocationListener
import android.location.LocationManager
import android.os.Bundle
import android.os.Handler
import android.os.Build
import android.os.Looper
import android.os.SystemClock
import io.edgeos.car.Prefs

/** Lo que muestra el launcher, recalculado con cada posición del GPS (una por segundo). */
data class DriveState(
    val kmh: Int = 0,
    val hasFix: Boolean = false,
    val satellites: Int = 0,
    val bearing: Float? = null,
    val altitude: Int? = null,
    val location: Location? = null,
    val tripKm: Double = 0.0,
    val tripSeconds: Long = 0,
    val maxKmh: Int = 0,
) {
    /** Media en marcha: solo cuenta el tiempo en movimiento, no los semáforos. */
    val avgKmh: Int get() = if (tripSeconds < 10) 0 else (tripKm / (tripSeconds / 3600.0)).toInt()
}

/**
 * Velocidad con el GPS de la radio. Usa la velocidad Doppler del propio GPS (la más fiable) y, si el chip no
 * la da, la calcula con la distancia entre dos posiciones. Por debajo de 3 km/h se muestra 0: parado, el GPS
 * "baila" unos metros y marcaría velocidad fantasma.
 */
class Drive(context: Context, private val prefs: Prefs) : LocationListener {
    private val lm = context.getSystemService(LocationManager::class.java)
    private val main = Handler(Looper.getMainLooper())
    private val listeners = mutableListOf<(DriveState) -> Unit>()

    var state = DriveState(tripKm = prefs.tripMeters / 1000, tripSeconds = prefs.tripSeconds, maxKmh = prefs.tripMax.toInt())
        private set

    private var last: Location? = null
    private var lastAt = 0L
    private var smooth = 0f
    private var tripM = prefs.tripMeters
    private var tripS = prefs.tripSeconds
    private var max = prefs.tripMax
    private var savedAt = 0L

    // Satélites en uso: solo desde Android 7; antes se queda sin el número (la velocidad funciona igual).
    private val gnss = if (Build.VERSION.SDK_INT < Build.VERSION_CODES.N) null else object : GnssStatus.Callback() {
        override fun onSatelliteStatusChanged(status: GnssStatus) {
            var used = 0
            for (i in 0 until status.satelliteCount) if (status.usedInFix(i)) used++
            if (used != state.satellites) publish(state.copy(satellites = used))
        }
    }

    // Sin posición en 5 s (túnel, garaje): se deja de mostrar velocidad en vez de congelar la última.
    private val watchdog = object : Runnable {
        override fun run() {
            if (state.hasFix && SystemClock.elapsedRealtime() - lastAt > 5000) {
                smooth = 0f
                publish(state.copy(hasFix = false, kmh = 0))
            }
            main.postDelayed(this, 1000)
        }
    }

    fun listen(l: (DriveState) -> Unit) {
        listeners += l
        l(state)
    }

    @SuppressLint("MissingPermission")
    fun start(): Boolean {
        if (lm == null || !lm.allProviders.contains(LocationManager.GPS_PROVIDER)) return false
        return try {
            lm.requestLocationUpdates(LocationManager.GPS_PROVIDER, 1000L, 0f, this, Looper.getMainLooper())
            if (gnss != null && Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) lm.registerGnssStatusCallback(gnss, main)
            main.removeCallbacks(watchdog)
            main.postDelayed(watchdog, 1000)
            true
        } catch (_: SecurityException) {
            false
        }
    }

    fun stop() {
        lm?.removeUpdates(this)
        if (gnss != null && Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) lm?.unregisterGnssStatusCallback(gnss)
        main.removeCallbacks(watchdog)
        save()
    }

    fun resetTrip() {
        tripM = 0.0; tripS = 0; max = 0f
        save()
        publish(state.copy(tripKm = 0.0, tripSeconds = 0, maxKmh = 0))
    }

    override fun onLocationChanged(loc: Location) {
        val now = SystemClock.elapsedRealtime()
        val prev = last
        val dt = if (prev != null) (loc.elapsedRealtimeNanos - prev.elapsedRealtimeNanos) / 1e9 else 0.0

        var kmh = when {
            loc.hasSpeed() -> loc.speed * 3.6f
            prev != null && dt > 0.2 -> (prev.distanceTo(loc) / dt * 3.6).toFloat()
            else -> 0f
        }
        kmh *= 1f + prefs.speedOffset / 100f
        smooth = if (smooth == 0f) kmh else smooth * 0.35f + kmh * 0.65f
        val shown = if (smooth < 3f) 0 else Math.round(smooth)

        // El viaje solo suma en marcha y con buena precisión, para que el GPS parado no "ande".
        val good = !loc.hasAccuracy() || loc.accuracy <= 35f
        if (prev != null && shown > 0 && good && dt in 0.2..10.0) {
            tripM += prev.distanceTo(loc)
            tripS += Math.round(dt)
            if (shown > max) max = shown.toFloat()
        }
        last = loc
        lastAt = now
        if (now - savedAt > 30_000) save()

        publish(
            state.copy(
                kmh = shown,
                hasFix = true,
                bearing = if (loc.hasBearing() && shown >= 5) loc.bearing else state.bearing,
                altitude = if (loc.hasAltitude()) Math.round(loc.altitude).toInt() else null,
                location = loc,
                tripKm = tripM / 1000,
                tripSeconds = tripS,
                maxKmh = max.toInt(),
            )
        )
    }

    private fun save() {
        savedAt = SystemClock.elapsedRealtime()
        prefs.tripMeters = tripM
        prefs.tripSeconds = tripS
        prefs.tripMax = max
        last?.let { prefs.lastLat = it.latitude; prefs.lastLon = it.longitude }
    }

    private fun publish(s: DriveState) {
        state = s
        for (l in listeners) l(s)
    }

    @Deprecated("Deprecated in Java")
    override fun onStatusChanged(provider: String?, status: Int, extras: Bundle?) {}
    override fun onProviderEnabled(provider: String) {}
    override fun onProviderDisabled(provider: String) {
        smooth = 0f
        publish(state.copy(hasFix = false, kmh = 0))
    }

    companion object {
        private val POINTS = arrayOf("N", "NE", "E", "SE", "S", "SO", "O", "NO")
        fun compass(bearing: Float) = POINTS[(((bearing % 360) + 360 + 22.5f) % 360 / 45).toInt()]
    }
}
