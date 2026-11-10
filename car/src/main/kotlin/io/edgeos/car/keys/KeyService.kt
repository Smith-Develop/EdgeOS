package io.edgeos.car.keys

import android.accessibilityservice.AccessibilityService
import android.os.Handler
import android.os.Looper
import android.view.KeyEvent
import android.view.accessibility.AccessibilityEvent
import io.edgeos.car.Prefs

/**
 * Recibe los botones del volante antes que ninguna app (también con Android Auto o el navegador delante) y hace lo
 * aprendido. Solo intercepta los botones aprendidos; los demás siguen su camino de siempre. No lee la pantalla.
 */
class KeyService : AccessibilityService() {
    private lateinit var prefs: Prefs
    private val main = Handler(Looper.getMainLooper())
    private var downId: String? = null
    private var longDone = false

    override fun onServiceConnected() {
        prefs = Prefs(this)
        instance = this
    }

    override fun onDestroy() {
        if (instance === this) instance = null
        super.onDestroy()
    }

    override fun onKeyEvent(e: KeyEvent): Boolean {
        val id = Keys.id(e)
        learning?.let { learn ->
            if (e.action == KeyEvent.ACTION_DOWN && e.repeatCount == 0) main.post { learn(id) }
            if (e.action == KeyEvent.ACTION_UP) learning = null
            return true
        }
        val short = Keys.short(prefs, id) ?: return false
        val long = Keys.long(prefs, id)
        when (e.action) {
            KeyEvent.ACTION_DOWN -> if (e.repeatCount == 0) {
                downId = id
                longDone = false
                if (long != null) main.postDelayed({
                    if (downId == id) {
                        longDone = true
                        Keys.perform(this, prefs, long)
                    }
                }, Keys.LONG_MS)
            }
            KeyEvent.ACTION_UP -> {
                main.removeCallbacksAndMessages(null)
                if (!longDone) Keys.perform(this, prefs, short)
                downId = null
            }
        }
        return true
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {}
    override fun onInterrupt() {}

    companion object {
        @Volatile
        var instance: KeyService? = null

        /** Mientras no sea null, el siguiente botón no hace nada: se le pasa aquí para aprenderlo. */
        @Volatile
        var learning: ((String) -> Unit)? = null
    }
}
