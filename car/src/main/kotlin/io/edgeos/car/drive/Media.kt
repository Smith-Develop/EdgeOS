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
)

/**
 * Lo que suena y sus controles. Con acceso a notificaciones lee título, artista y carátula de cualquier app
 * (también la radio FM de muchas radios, que publica una sesión multimedia). Sin él, los botones siguen
 * funcionando como las teclas multimedia del volante.
 */
class Media(private val context: Context) {
    private val msm = context.getSystemService(MediaSessionManager::class.java)
    private val audio = context.getSystemService(AudioManager::class.java)
    private val component = ComponentName(context, MediaListener::class.java)
    private val main = Handler(Looper.getMainLooper())
    private var controller: MediaController? = null
    var onChange: ((NowPlaying) -> Unit)? = null
    var now = NowPlaying()
        private set

    private val callback = object : MediaController.Callback() {
        override fun onMetadataChanged(metadata: MediaMetadata?) = publish()
        override fun onPlaybackStateChanged(state: PlaybackState?) = publish()
        override fun onSessionDestroyed() = pick(null)
    }

    private val sessions = MediaSessionManager.OnActiveSessionsChangedListener { pick(it) }

    fun hasAccess(): Boolean {
        val enabled = Settings.Secure.getString(context.contentResolver, "enabled_notification_listeners") ?: return false
        return enabled.split(':').any { ComponentName.unflattenFromString(it) == component }
    }

    fun start() {
        if (!hasAccess()) return publish()
        try {
            msm.addOnActiveSessionsChangedListener(sessions, component, main)
            pick(msm.getActiveSessions(component))
        } catch (_: SecurityException) {
            publish()
        }
    }

    fun stop() {
        runCatching { msm.removeOnActiveSessionsChangedListener(sessions) }
        controller?.unregisterCallback(callback)
        controller = null
    }

    /** La sesión que está sonando; si ninguna suena, la primera (la última usada). */
    private fun pick(list: List<MediaController>?) {
        val next = list?.firstOrNull { it.playbackState?.state == PlaybackState.STATE_PLAYING } ?: list?.firstOrNull()
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
        now = NowPlaying(
            title = m?.getString(MediaMetadata.METADATA_KEY_TITLE) ?: m?.getString(MediaMetadata.METADATA_KEY_DISPLAY_TITLE),
            artist = m?.getString(MediaMetadata.METADATA_KEY_ARTIST) ?: m?.getString(MediaMetadata.METADATA_KEY_ALBUM),
            art = m?.getBitmap(MediaMetadata.METADATA_KEY_ALBUM_ART) ?: m?.getBitmap(MediaMetadata.METADATA_KEY_ART),
            playing = c?.playbackState?.state == PlaybackState.STATE_PLAYING,
            packageName = c?.packageName,
        )
        onChange?.invoke(now)
    }

    fun playPause() = controller?.transportControls?.let { if (now.playing) it.pause() else it.play() }
        ?: key(KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE)
    fun next() = controller?.transportControls?.skipToNext() ?: key(KeyEvent.KEYCODE_MEDIA_NEXT)
    fun previous() = controller?.transportControls?.skipToPrevious() ?: key(KeyEvent.KEYCODE_MEDIA_PREVIOUS)

    private fun key(code: Int) {
        val t = SystemClock.uptimeMillis()
        audio.dispatchMediaKeyEvent(KeyEvent(t, t, KeyEvent.ACTION_DOWN, code, 0))
        audio.dispatchMediaKeyEvent(KeyEvent(t, t, KeyEvent.ACTION_UP, code, 0))
    }
}
