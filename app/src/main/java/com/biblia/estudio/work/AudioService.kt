package com.biblia.estudio.work

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.net.Uri
import android.os.Build
import android.os.IBinder
import android.support.v4.media.MediaMetadataCompat
import android.support.v4.media.session.MediaSessionCompat
import android.support.v4.media.session.PlaybackStateCompat
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import com.biblia.estudio.MainActivity
import com.biblia.estudio.R
import com.biblia.estudio.app
import com.biblia.estudio.domain.AudioState
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch

/**
 * Mantiene la lectura en voz alta con la pantalla apagada y muestra los controles
 * (anterior, pausa/seguir, siguiente, detener) en la notificación y en la pantalla de bloqueo.
 */
class AudioService : Service() {
    companion object {
        private const val CHANNEL = "audio"
        private const val NOTIF = 5
        private const val ACTION_TOGGLE = "com.biblia.estudio.audio.TOGGLE"
        private const val ACTION_NEXT = "com.biblia.estudio.audio.NEXT"
        private const val ACTION_PREV = "com.biblia.estudio.audio.PREV"
        private const val ACTION_STOP = "com.biblia.estudio.audio.STOP"
        fun start(context: Context) = ContextCompat.startForegroundService(context, Intent(context, AudioService::class.java))
        fun stop(context: Context) { context.stopService(Intent(context, AudioService::class.java)) }
    }

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private lateinit var session: MediaSessionCompat
    private val player get() = applicationContext.app.audio

    override fun onCreate() {
        super.onCreate()
        getSystemService(NotificationManager::class.java).createNotificationChannel(
            NotificationChannel(CHANNEL, "Lectura en voz alta", NotificationManager.IMPORTANCE_LOW).apply { description = "Controles de la lectura en voz alta" })
        session = MediaSessionCompat(this, "codice-vivo").apply {
            setCallback(object : MediaSessionCompat.Callback() {
                override fun onPlay() = player.resume()
                override fun onPause() = player.pause()
                override fun onSkipToNext() = player.next()
                override fun onSkipToPrevious() = player.previous()
                override fun onStop() = player.stop()
            })
            isActive = true
        }
        scope.launch { player.state.collect { s -> if (s == null) { stopSelf() } else update(s) } }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        // Obligatorio: pasar a primer plano enseguida
        val s = player.state.value
        val n = build(s ?: AudioState(43, 1, "", false))
        if (Build.VERSION.SDK_INT >= 29) startForeground(NOTIF, n, ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PLAYBACK) else startForeground(NOTIF, n)
        when (intent?.action) {
            ACTION_TOGGLE -> player.toggle()
            ACTION_NEXT -> player.next()
            ACTION_PREV -> player.previous()
            ACTION_STOP -> player.stop()
        }
        if (s == null && intent?.action == null) stopSelf()
        return START_NOT_STICKY
    }

    private fun action(action: String, code: Int) = PendingIntent.getService(this, code, Intent(this, AudioService::class.java).setAction(action), PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)

    private fun build(s: AudioState): android.app.Notification {
        val title = runCatching { player.label(s) }.getOrDefault("Códice vivo")
        val open = PendingIntent.getActivity(this, 40, Intent(Intent.ACTION_VIEW, Uri.parse("codicevivo://reader"), this, MainActivity::class.java), PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
        return NotificationCompat.Builder(this, CHANNEL)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(title)
            .setContentText(if (s.playing) "Leyendo · ${s.version}" else "En pausa · ${s.version}")
            .setContentIntent(open)
            .setDeleteIntent(action(ACTION_STOP, 44))
            .setOngoing(s.playing)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .addAction(android.R.drawable.ic_media_previous, "Capítulo anterior", action(ACTION_PREV, 41))
            .addAction(if (s.playing) android.R.drawable.ic_media_pause else android.R.drawable.ic_media_play, if (s.playing) "Pausar" else "Seguir", action(ACTION_TOGGLE, 42))
            .addAction(android.R.drawable.ic_media_next, "Capítulo siguiente", action(ACTION_NEXT, 43))
            .addAction(android.R.drawable.ic_menu_close_clear_cancel, "Detener", action(ACTION_STOP, 44))
            .setStyle(androidx.media.app.NotificationCompat.MediaStyle().setMediaSession(session.sessionToken).setShowActionsInCompactView(0, 1, 2))
            .build()
    }

    private fun update(s: AudioState) {
        session.setMetadata(MediaMetadataCompat.Builder()
            .putString(MediaMetadataCompat.METADATA_KEY_TITLE, player.label(s))
            .putString(MediaMetadataCompat.METADATA_KEY_ARTIST, "Códice vivo · ${s.version}").build())
        session.setPlaybackState(PlaybackStateCompat.Builder()
            .setActions(PlaybackStateCompat.ACTION_PLAY or PlaybackStateCompat.ACTION_PAUSE or PlaybackStateCompat.ACTION_PLAY_PAUSE or
                PlaybackStateCompat.ACTION_SKIP_TO_NEXT or PlaybackStateCompat.ACTION_SKIP_TO_PREVIOUS or PlaybackStateCompat.ACTION_STOP)
            .setState(if (s.playing) PlaybackStateCompat.STATE_PLAYING else PlaybackStateCompat.STATE_PAUSED, s.index.toLong(), 1f).build())
        @Suppress("MissingPermission")
        androidx.core.app.NotificationManagerCompat.from(this).notify(NOTIF, build(s))
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() { scope.cancel(); session.release(); super.onDestroy() }
}
