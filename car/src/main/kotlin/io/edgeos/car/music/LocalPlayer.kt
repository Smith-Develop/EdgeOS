package io.edgeos.car.music

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.media.AudioAttributes
import android.media.AudioFocusRequest
import android.media.AudioManager
import android.media.MediaMetadata
import android.media.MediaMetadataRetriever
import android.media.MediaPlayer
import android.media.session.MediaController
import android.media.session.MediaSession
import android.media.session.PlaybackState
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.os.SystemClock

/**
 * El reproductor propio de EdgeOS Car para la música de la radio y de los USB. Publica una sesión multimedia como
 * cualquier app: así lo controlan los botones del volante, la tarjeta de Inicio y el Bluetooth del coche.
 * Shortcut: vive en el proceso del launcher sin servicio en primer plano; al ser la pantalla de inicio Android no
 * lo cierra, pero si algún día suena cortado en segundo plano, pasarlo a un servicio.
 */
class LocalPlayer(private val context: Context) {
    private val main = Handler(Looper.getMainLooper())
    private val audio = context.getSystemService(AudioManager::class.java)
    // Se crea al usarla por primera vez.
    private val session by lazy {
        MediaSession(context, "EdgeOS Car").apply {
            @Suppress("DEPRECATION")
            setFlags(MediaSession.FLAG_HANDLES_MEDIA_BUTTONS or MediaSession.FLAG_HANDLES_TRANSPORT_CONTROLS)
            setCallback(object : MediaSession.Callback() {
                override fun onPlay() = play()
                override fun onPause() = pause()
                override fun onSkipToNext() = next()
                override fun onSkipToPrevious() = previous()
                override fun onSeekTo(pos: Long) = seekTo(pos)
                override fun onStop() = pause()
            }, main)
        }
    }
    private var player: MediaPlayer? = null
    private var queue: List<Track> = emptyList()
    private var index = -1
    private var prepared = false
    private var resumeOnFocus = false

    val controller: MediaController get() = session.controller
    val current: Track? get() = queue.getOrNull(index)

    private val attributes = AudioAttributes.Builder()
        .setUsage(AudioAttributes.USAGE_MEDIA).setContentType(AudioAttributes.CONTENT_TYPE_MUSIC).build()

    // Llamadas y avisos del navegador: pausar o bajar el volumen, y volver después.
    private val focusListener = AudioManager.OnAudioFocusChangeListener { change ->
        main.post {
            when (change) {
                AudioManager.AUDIOFOCUS_LOSS -> { resumeOnFocus = false; pause() }
                AudioManager.AUDIOFOCUS_LOSS_TRANSIENT -> { if (player?.isPlaying == true) { resumeOnFocus = true; pause() } }
                AudioManager.AUDIOFOCUS_LOSS_TRANSIENT_CAN_DUCK -> player?.setVolume(0.3f, 0.3f)
                AudioManager.AUDIOFOCUS_GAIN -> {
                    player?.setVolume(1f, 1f)
                    if (resumeOnFocus) { resumeOnFocus = false; play() }
                }
            }
        }
    }

    // AudioFocusRequest es de Android 8; antes, la forma antigua con el flujo de música.
    private val focus: Any? by lazy {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O)
            AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN).setAudioAttributes(attributes)
                .setOnAudioFocusChangeListener(focusListener).build()
        else null
    }

    @Suppress("DEPRECATION")
    private fun requestFocus(): Boolean = AudioManager.AUDIOFOCUS_REQUEST_GRANTED ==
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) audio.requestAudioFocus(focus as AudioFocusRequest)
        else audio.requestAudioFocus(focusListener, AudioManager.STREAM_MUSIC, AudioManager.AUDIOFOCUS_GAIN)

    @Suppress("DEPRECATION")
    private fun abandonFocus() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) audio.abandonAudioFocusRequest(focus as AudioFocusRequest)
        else audio.abandonAudioFocus(focusListener)
    }

    /** Toca la lista desde la canción [start]. */
    fun play(tracks: List<Track>, start: Int) {
        queue = tracks
        load(start, autoplay = true)
    }

    fun play() {
        val p = player ?: return if (queue.isNotEmpty()) load(index.coerceAtLeast(0), true) else Unit
        if (!prepared) return
        if (!requestFocus()) return
        session.isActive = true
        p.start()
        publishState()
    }

    fun pause() {
        player?.takeIf { prepared && it.isPlaying }?.pause()
        publishState()
    }

    fun next() = if (queue.isNotEmpty()) load((index + 1) % queue.size, true) else Unit

    /** Como en todos los reproductores: si ya van más de 3 s, vuelve al principio de la canción. */
    fun previous() {
        val p = player
        if (p != null && prepared && p.currentPosition > 3000) return seekTo(0)
        if (queue.isNotEmpty()) load((index - 1 + queue.size) % queue.size, true)
    }

    fun seekTo(ms: Long) {
        player?.takeIf { prepared }?.seekTo(ms.toInt())
        publishState()
    }

    fun release() {
        abandonFocus()
        player?.release()
        player = null
        session.release()
    }

    private fun load(i: Int, autoplay: Boolean) {
        index = i
        val track = queue.getOrNull(i) ?: return
        player?.release()
        prepared = false
        val p = MediaPlayer()
        player = p
        p.setAudioAttributes(attributes)
        p.setOnPreparedListener {
            prepared = true
            publishMetadata(track, null)
            if (autoplay) play() else publishState()
        }
        p.setOnCompletionListener { next() }
        // Un archivo dañado no para la música: se salta al siguiente.
        p.setOnErrorListener { _, _, _ -> main.post { if (queue.size > 1) next() }; true }
        runCatching {
            p.setDataSource(track.path)
            p.prepareAsync()
        }.onFailure { if (queue.size > 1) main.post { next() } }
        publishMetadata(track, null)
        // La carátula, del propio archivo y fuera del hilo principal.
        Thread {
            val art = runCatching {
                MediaMetadataRetriever().run {
                    setDataSource(track.path)
                    val bytes = embeddedPicture
                    release()
                    bytes?.let { decodeSmall(it) }
                }
            }.getOrNull()
            main.post { if (current === track) publishMetadata(track, art) }
        }.start()
    }

    private fun decodeSmall(bytes: ByteArray): Bitmap? {
        val o = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeByteArray(bytes, 0, bytes.size, o)
        var s = 1
        while (maxOf(o.outWidth, o.outHeight) / (s * 2) >= 512) s *= 2
        return BitmapFactory.decodeByteArray(bytes, 0, bytes.size, BitmapFactory.Options().apply { inSampleSize = s })
    }

    private fun publishMetadata(t: Track, art: Bitmap?) {
        val duration = player?.takeIf { prepared }?.duration?.toLong() ?: t.duration
        session.setMetadata(MediaMetadata.Builder()
            .putString(MediaMetadata.METADATA_KEY_TITLE, t.title)
            .putString(MediaMetadata.METADATA_KEY_ARTIST, t.artist.ifEmpty { t.folder })
            .putString(MediaMetadata.METADATA_KEY_ALBUM, t.album)
            .putLong(MediaMetadata.METADATA_KEY_DURATION, duration)
            .apply { if (art != null) putBitmap(MediaMetadata.METADATA_KEY_ALBUM_ART, art) }
            .build())
    }

    private fun publishState() {
        val p = player
        val playing = p != null && prepared && p.isPlaying
        session.setPlaybackState(PlaybackState.Builder()
            .setState(if (playing) PlaybackState.STATE_PLAYING else PlaybackState.STATE_PAUSED,
                if (p != null && prepared) p.currentPosition.toLong() else 0, if (playing) 1f else 0f, SystemClock.elapsedRealtime())
            .setActions(PlaybackState.ACTION_PLAY or PlaybackState.ACTION_PAUSE or PlaybackState.ACTION_PLAY_PAUSE or
                PlaybackState.ACTION_SKIP_TO_NEXT or PlaybackState.ACTION_SKIP_TO_PREVIOUS or PlaybackState.ACTION_SEEK_TO)
            .build())
    }
}
