package com.example.aimusicplayer.player

import android.app.Notification
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.Binder
import android.os.IBinder

/**
 * Android foreground [Service] that keeps audio playback alive when the app
 * is backgrounded.
 *
 * The service creates the [MusicPlayer] instance and a [SystemMediaController]
 * that manages the MediaSession + media notification. The notification is
 * posted by [SystemMediaController] via [NotificationManager] and promoted
 * to foreground with the same ID so the OS knows this is an active media session.
 *
 * **Manifest registration (already in AndroidManifest.xml):**
 * ```xml
 * <service
 *     android:name="com.example.aimusicplayer.player.PlaybackService"
 *     android:foregroundServiceType="mediaPlayback"
 *     android:exported="false" />
 * ```
 */
class PlaybackService : Service() {

    private val binder = LocalBinder()

    var musicPlayer: MusicPlayer? = null
        private set

    var mediaController: SystemMediaController? = null
        private set

    private var isForeground = false

    override fun onCreate() {
        super.onCreate()
        promoteToForeground(buildPlaceholderNotification())
        isForeground = true

        if (!MusicPlayerHolder.isInitialized()) {
            MusicPlayerHolder.musicPlayer = MusicPlayer(this)
            MusicPlayerHolder.systemMediaController = SystemMediaController(this)
        }
        musicPlayer = MusicPlayerHolder.musicPlayer
        mediaController = MusicPlayerHolder.systemMediaController
        mediaController!!.attach(musicPlayer!!)
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_PLAY -> musicPlayer?.resume()
            ACTION_PAUSE -> musicPlayer?.pause()
            ACTION_PREV -> mediaController?.onPrevious?.invoke()
            ACTION_NEXT -> mediaController?.onNext?.invoke()
            ACTION_STOP -> musicPlayer?.stop()
        }
        return START_STICKY
    }

    override fun onBind(intent: Intent): IBinder = binder

    override fun onDestroy() {
        mediaController?.detach()
        musicPlayer?.release()
        if (isForeground) {
            stopForeground(STOP_FOREGROUND_REMOVE)
            isForeground = false
        }
        super.onDestroy()
    }

    private fun promoteToForeground(notification: Notification) {
        startForeground(FOREGROUND_NOTIFICATION_ID, notification)
    }

    private fun buildPlaceholderNotification(): Notification {
        val openIntent = packageManager
            .getLaunchIntentForPackage(packageName)
            ?.let { intent ->
                PendingIntent.getActivity(
                    this, 0, intent,
                    PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
                )
            }
        return Notification.Builder(this, CHANNEL_ID)
            .setContentTitle("HearEverything")
            .setContentText("音乐播放中")
            .setSmallIcon(com.example.aimusicplayer.shared.R.drawable.ic_notification_cover)
            .setContentIntent(openIntent)
            .setOngoing(true)
            .build()
    }

    /** Binder that exposes the shared [MusicPlayer] to bound clients (UI layer). */
    inner class LocalBinder : Binder() {
        fun getMusicPlayer(): MusicPlayer = musicPlayer!!
        fun getService(): PlaybackService = this@PlaybackService
    }

    companion object {
        private const val CHANNEL_ID = "music_playback_channel"
        private const val FOREGROUND_NOTIFICATION_ID = 1001

        // Direct service actions — bypass MediaSession/KeyEvent/Broadcast overhead
        const val ACTION_PLAY = "com.example.aimusicplayer.PLAY"
        const val ACTION_PAUSE = "com.example.aimusicplayer.PAUSE"
        const val ACTION_PREV = "com.example.aimusicplayer.PREV"
        const val ACTION_NEXT = "com.example.aimusicplayer.NEXT"
        const val ACTION_STOP = "com.example.aimusicplayer.STOP"

        /** Start the service from an Activity or other context. */
        fun start(context: Context) {
            val intent = Intent(context, PlaybackService::class.java)
            context.startForegroundService(intent)
        }

        /** Build a [PendingIntent] that calls [PlaybackService.onStartCommand] with [action]. */
        fun buildActionPending(context: Context, action: String): PendingIntent {
            val intent = Intent(context, PlaybackService::class.java).apply {
                this.action = action
            }
            return PendingIntent.getService(context, action.hashCode(), intent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        }
    }
}
