package io.edgeos.car.ui

import android.os.SystemClock
import android.view.Choreographer

/**
 * Lleva un valor a su nuevo destino fotograma a fotograma, en vez de saltar. El GPS da un dato por segundo:
 * si la animación dura lo mismo que el intervalo entre datos, la aguja y el mapa no se paran nunca.
 * Va con Choreographer y no con ValueAnimator, para que "Animaciones rápidas" (o desactivarlas) no le afecte.
 */
class Tween(private val onFrame: (Float) -> Unit) : Choreographer.FrameCallback {
    var value = 0f
        private set
    private var from = 0f
    private var to = 0f
    private var start = 0L
    private var duration = 1000L
    private var running = false

    /** Hacia [target] en [ms]; si ya iba hacia otro, sale desde donde está ahora (sin saltos). */
    fun to(target: Float, ms: Long) {
        from = value
        to = target
        start = SystemClock.uptimeMillis()
        duration = ms.coerceIn(1, 2000)
        if (!running) {
            running = true
            Choreographer.getInstance().postFrameCallback(this)
        }
    }

    fun snap(target: Float) {
        stop()
        value = target
        to = target
        onFrame(value)
    }

    fun stop() {
        if (running) Choreographer.getInstance().removeFrameCallback(this)
        running = false
    }

    override fun doFrame(frameTimeNanos: Long) {
        val t = ((SystemClock.uptimeMillis() - start).toFloat() / duration).coerceIn(0f, 1f)
        value = from + (to - from) * t
        onFrame(value)
        if (t < 1f) Choreographer.getInstance().postFrameCallback(this) else running = false
    }
}
