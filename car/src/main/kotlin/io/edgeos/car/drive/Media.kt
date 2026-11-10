package io.edgeos.car.drive

import android.content.ComponentName
import android.content.Context
import android.graphics.Bitmap
import android.media.AudioManager
import android.media.MediaMetadata
import android.media.session.MediaController
import android.media.session.MediaSessionManager
import android.media.session.PlaybackState
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.provider.Settings
import android.service.notification.NotificationListenerService
import android.view.KeyEvent

/** Vacío a propósito: Android solo deja leer qué suena a apps con acceso a notificaciones. */
class MediaListener : NotificationListenerService()

data class NowPlaying(
    val title: String? = null,
    val artist: String? = null,
    val art: Bitmap? = null,
    val playing: Boolean = false,
    val packageName: String? = null,
    val album: String? = null,
    /** Duración en ms (0 si la fuente no la da: radio, Bluetooth sin metadatos…). */
    val duration: Long = 0,
    /** Posición en ms en el instante [positionAt] (SystemClock.elapsedRealtime). */
    val position: Long = 0,
    val positionAt: Long = 0,
    val speed: Float = 1f,
    val canSeek: Boolean = false,
) {
    /** La posición ahora mismo, avanzando sola mientras suena (para una barra fluida sin preguntar a la app). */
    fun positionNow(): Long {
        if (!playing || positionAt == 0L) return position
        val p = position + ((SystemClock.elapsedRealtime() - positionAt) * speed).toLong()
        return if (duration > 0) p.coerceIn(0, duration) else p
    }
}

/**
 * Lo que suena y sus controles, venga de donde venga: Bluetooth del móvil, Spotify, TuneIn, la radio, Android Auto o
 * el reproductor propio. Con acceso a notificaciones lee título, artista, carátula y posición de cualquier app; sin
 * él, los botones siguen funcionando como las teclas multimedia del volante.
 */
class Media(private val context: Context) {
    private val msm by lazy { context.getSystemService(MediaSessionManager::class.java) }
    private val audio = context.getSystemService(AudioManager::class.java)
    private val component = ComponentName(context, MediaListener::class.java)
    private val main = Handler(Looper.getMainLooper())
    private var controller: MediaController? = null
    /** Fuente elegida a mano: se respeta mientras siga existiendo, aunque otra empiece a sonar. */
    private var pinned: String? = null
    var onChange: ((NowPlaying) -> Unit)? = null
    var now = NowPlaying()
        private set

    /** Las apps con sesión multimedia ahora mismo (las fuentes entre las que elegir). */
    var sources: List<MediaController> = emptyList()
        private set

    /** El reproductor propio: siempre es una fuente, haya o no acceso a notificaciones. */
    var local: MediaController? = null
        set(v) { field = v; pick(sources.filter { it.packageName != context.packageName }) }

    private val callback = object : MediaController.Callback() {
        override fun onMetadataChanged(metadata: MediaMetadata?) = publish()
        override fun onPlaybackStateChanged(state: PlaybackState?) {
            // Si otra fuente se pone a sonar y la elegida está parada, se cambia a la que suena.
            publish()
        }
        override fun onSessionDestroyed() = pick(sources)
    }

    private val sessions = MediaSessionManager.OnActiveSessionsChangedListener { pick(it ?: emptyList()) }

    fun hasAccess(): Boolean {
        val enabled = Settings.Secure.getString(context.contentResolver, "enabled_notification_listeners") ?: return false
        return enabled.split(':').any { ComponentName.unflattenFromString(it) == component }
    }

    fun start() {
        if (!hasAccess()) return pick(emptyList())
        try {
            msm.addOnActiveSessionsChangedListener(sessions, component, main)
            pick(msm.getActiveSessions(component))
        } catch (_: SecurityException) {
            pick(emptyList())
        }
    }

    fun stop() {
        runCatching { msm.removeOnActiveSessionsChangedListener(sessions) }
        controller?.unregisterCallback(callback)
        controller = null
    }

    /** Elegir fuente a mano (desde la pantalla Música). */
    fun select(packageName: String) {
        pinned = packageName
        pick(sources)
    }

    /** La elegida a mano si sigue ahí; si no, la que suena; si ninguna suena, la última usada (la primera). */
    private fun pick(found: List<MediaController>) {
        val list = (found + listOfNotNull(local)).distinctBy { it.sessionToken }
        sources = list
        val next = list.firstOrNull { it.packageName == pinned }
            ?: list.firstOrNull { it.playbackState?.state == PlaybackState.STATE_PLAYING }
            ?: list.firstOrNull()
        if (next?.sessionToken != controller?.sessionToken) {
            controller?.unregisterCallback(callback)
            controller = next
            next?.registerCallback(callback, main)
        }
        publish()
    }

    private fun publish() {
        val c = controller
        val m = c?.metadata
        val st = c?.playbackState
        now = NowPlaying(
            title = m?.getString(MediaMetadata.METADATA_KEY_TITLE) ?: m?.getString(MediaMetadata.METADATA_KEY_DISPLAY_TITLE),
            artist = m?.getString(MediaMetadata.METADATA_KEY_ARTIST) ?: m?.getString(MediaMetadata.METADATA_KEY_ALBUM_ARTIST),
            album = m?.getString(MediaMetadata.METADATA_KEY_ALBUM),
            art = m?.getBitmap(MediaMetadata.METADATA_KEY_ALBUM_ART) ?: m?.getBitmap(MediaMetadata.METADATA_KEY_ART)
                ?: m?.getBitmap(MediaMetadata.METADATA_KEY_DISPLAY_ICON),
            playing = st?.state == PlaybackState.STATE_PLAYING,
            packageName = c?.packageName,
            duration = m?.getLong(MediaMetadata.METADATA_KEY_DURATION)?.coerceAtLeast(0) ?: 0,
            position = st?.position?.coerceAtLeast(0) ?: 0,
            positionAt = st?.lastPositionUpdateTime ?: 0,
            speed = st?.playbackSpeed?.takeIf { it > 0 } ?: 1f,
            canSeek = st != null && st.actions and PlaybackState.ACTION_SEEK_TO != 0L,
        )
        onChange?.invoke(now)
    }

    fun playPause() = controller?.transportControls?.let { if (now.playing) it.pause() else it.play() }
        ?: key(KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE)
    fun next() = controller?.transportControls?.skipToNext() ?: key(KeyEvent.KEYCODE_MEDIA_NEXT)
    fun previous() = controller?.transportControls?.skipToPrevious() ?: key(KeyEvent.KEYCODE_MEDIA_PREVIOUS)
    fun seekTo(ms: Long) {
        controller?.transportControls?.seekTo(ms)
    }

    private fun key(code: Int) {
        val t = SystemClock.uptimeMillis()
        audio.dispatchMediaKeyEvent(KeyEvent(t, t, KeyEvent.ACTION_DOWN, code, 0))
        audio.dispatchMediaKeyEvent(KeyEvent(t, t, KeyEvent.ACTION_UP, code, 0))
    }
}
