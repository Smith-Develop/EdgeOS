package io.edgeos.car.keys

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.media.AudioManager
import android.os.SystemClock
import android.provider.Settings
import android.view.KeyEvent
import io.edgeos.car.HomeActivity
import io.edgeos.car.Prefs
import io.edgeos.car.Slot
import io.edgeos.car.system.Shell

/** Lo que puede hacer un botón del volante. */
enum class KeyAction(val title: String) {
    PLAY_PAUSE("Reproducir / pausa"),
    NEXT("Siguiente"),
    PREVIOUS("Anterior"),
    VOLUME_UP("Subir volumen"),
    VOLUME_DOWN("Bajar volumen"),
    MUTE("Silencio"),
    HOME("Inicio de EdgeOS Car"),
    AUTO("Abrir Android Auto"),
    RADIO("Abrir la radio"),
    PHONE("Abrir el teléfono"),
    MUSIC("Abrir la música"),
    NAV("Abrir el navegador"),
    NONE("Nada"),
}

/**
 * Los botones aprendidos: para cada uno, una acción al pulsarlo y otra (opcional) al mantenerlo.
 * Un botón se identifica por su código de tecla; si la radio no le da código, por el de escaneo.
 */
object Keys {
    const val LONG_MS = 600L

    fun id(e: KeyEvent) = if (e.keyCode != KeyEvent.KEYCODE_UNKNOWN) "k${e.keyCode}" else "s${e.scanCode}"

    fun name(id: String): String {
        val code = id.drop(1).toIntOrNull() ?: return id
        if (id[0] == 's') return "Botón $code"
        return NAMES[code] ?: KeyEvent.keyCodeToString(code).removePrefix("KEYCODE_").lowercase().replace('_', ' ')
    }

    private val NAMES = mapOf(
        KeyEvent.KEYCODE_VOLUME_UP to "Volumen +", KeyEvent.KEYCODE_VOLUME_DOWN to "Volumen −",
        KeyEvent.KEYCODE_VOLUME_MUTE to "Silencio", KeyEvent.KEYCODE_MEDIA_NEXT to "Siguiente",
        KeyEvent.KEYCODE_MEDIA_PREVIOUS to "Anterior", KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE to "Reproducir / pausa",
        KeyEvent.KEYCODE_CALL to "Descolgar", KeyEvent.KEYCODE_ENDCALL to "Colgar", KeyEvent.KEYCODE_HOME to "Inicio",
        KeyEvent.KEYCODE_BACK to "Atrás", KeyEvent.KEYCODE_MENU to "Menú",
        KeyEvent.KEYCODE_MEDIA_STOP to "Stop", KeyEvent.KEYCODE_VOICE_ASSIST to "Voz", KeyEvent.KEYCODE_MUTE to "Micrófono",
    )

    fun learned(prefs: Prefs): List<String> = prefs.keyIds.sorted()
    fun short(prefs: Prefs, id: String) = prefs.keyAction(id, false)?.let { runCatching { KeyAction.valueOf(it) }.getOrNull() }
    fun long(prefs: Prefs, id: String) = prefs.keyAction(id, true)?.let { runCatching { KeyAction.valueOf(it) }.getOrNull() }

    fun save(prefs: Prefs, id: String, short: KeyAction, long: KeyAction?) {
        prefs.setKeyAction(id, false, short.name)
        prefs.setKeyAction(id, true, long?.takeIf { it != KeyAction.NONE }?.name)
        prefs.keyIds = prefs.keyIds + id
    }

    fun forget(prefs: Prefs, id: String) {
        prefs.setKeyAction(id, false, null)
        prefs.setKeyAction(id, true, null)
        prefs.keyIds = prefs.keyIds - id
    }

    fun perform(context: Context, prefs: Prefs, action: KeyAction) {
        val audio = context.getSystemService(AudioManager::class.java)
        when (action) {
            KeyAction.PLAY_PAUSE -> media(audio, KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE)
            KeyAction.NEXT -> media(audio, KeyEvent.KEYCODE_MEDIA_NEXT)
            KeyAction.PREVIOUS -> media(audio, KeyEvent.KEYCODE_MEDIA_PREVIOUS)
            KeyAction.VOLUME_UP -> audio.adjustStreamVolume(AudioManager.STREAM_MUSIC, AudioManager.ADJUST_RAISE, AudioManager.FLAG_SHOW_UI)
            KeyAction.VOLUME_DOWN -> audio.adjustStreamVolume(AudioManager.STREAM_MUSIC, AudioManager.ADJUST_LOWER, AudioManager.FLAG_SHOW_UI)
            KeyAction.MUTE -> audio.adjustStreamVolume(AudioManager.STREAM_MUSIC, AudioManager.ADJUST_TOGGLE_MUTE, AudioManager.FLAG_SHOW_UI)
            KeyAction.HOME -> start(context, Intent(context, HomeActivity::class.java))
            KeyAction.AUTO -> open(context, prefs, Slot.AUTO)
            KeyAction.RADIO -> open(context, prefs, Slot.RADIO)
            KeyAction.PHONE -> open(context, prefs, Slot.PHONE)
            KeyAction.MUSIC -> open(context, prefs, Slot.MUSIC)
            KeyAction.NAV -> open(context, prefs, Slot.NAV)
            KeyAction.NONE -> {}
        }
    }

    // Va directo a la sesión multimedia, no a la cola de teclas: no vuelve a pasar por el servicio.
    private fun media(audio: AudioManager, code: Int) {
        val t = SystemClock.uptimeMillis()
        audio.dispatchMediaKeyEvent(KeyEvent(t, t, KeyEvent.ACTION_DOWN, code, 0))
        audio.dispatchMediaKeyEvent(KeyEvent(t, t, KeyEvent.ACTION_UP, code, 0))
    }

    /** La app del acceso rápido (la elegida o la que encaja por nombre), sin esperar a que cargue la lista. */
    private fun open(context: Context, prefs: Prefs, slot: Slot) {
        val pm = context.packageManager
        val chosen = prefs.app(slot.key)?.let { ComponentName.unflattenFromString(it) }
        val intent = chosen?.let { Intent(Intent.ACTION_MAIN).setComponent(it) } ?: run {
            val all = pm.queryIntentActivities(Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER), 0)
            val hit = slot.match(all) { it.activityInfo.packageName + " " + it.loadLabel(pm) } ?: return
            Intent(Intent.ACTION_MAIN).setClassName(hit.activityInfo.packageName, hit.activityInfo.name)
        }
        start(context, intent.addCategory(Intent.CATEGORY_LAUNCHER))
    }

    private fun start(context: Context, intent: Intent) {
        runCatching { context.startActivity(intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED)) }
    }

    // MARK: el servicio de accesibilidad

    private fun component(context: Context) = ComponentName(context, KeyService::class.java)

    fun serviceEnabled(context: Context): Boolean {
        val list = Settings.Secure.getString(context.contentResolver, Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES) ?: return false
        return list.split(':').any { ComponentName.unflattenFromString(it) == component(context) }
    }

    /** Lo activa con el acceso al sistema (root o ADB local), sin pasar por Ajustes. Fuera del hilo principal. */
    fun enableWithShell(context: Context): Boolean {
        if (Shell.access.state != Shell.State.Ready) return false
        val me = component(context).flattenToString()
        val current = Shell.run("settings get secure enabled_accessibility_services").trim()
            .takeUnless { it.isEmpty() || it == "null" }
        val list = (current?.split(':') ?: emptyList()).filter { it.isNotBlank() } .let { if (me in it) it else it + me }
        Shell.run("settings put secure enabled_accessibility_services '${list.joinToString(":")}'")
        Shell.run("settings put secure accessibility_enabled 1")
        return true
    }
}
