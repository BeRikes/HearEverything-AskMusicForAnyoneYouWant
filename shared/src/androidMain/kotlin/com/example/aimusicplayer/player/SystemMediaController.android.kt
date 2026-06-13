package com.example.aimusicplayer.player

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.media.session.MediaSession
import android.media.session.PlaybackState
import android.os.Build
import android.os.SystemClock
import android.util.Log
import android.view.KeyEvent
import com.example.aimusicplayer.network.music.SongMetadata
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.net.HttpURLConnection
import java.net.URL

actual class SystemMediaController(private val context: Context) : SystemMediaControllerProvider {

    private val scope = CoroutineScope(Dispatchers.Main + SupervisorJob())

    // Lazy: defer MediaSession creation until playback starts — an active or
    // even inactive session causes the system to query our process during
    // the media-output-picker panel animation, causing the animation stutter.
    private var session: MediaSession? = null

    private var callbacksSetup = false

    private fun requireSession(): MediaSession {
        val existing = session
        if (existing != null) return existing
        val s = MediaSession(context, TAG)
        s.setFlags(MediaSession.FLAG_HANDLES_TRANSPORT_CONTROLS)
        session = s
        if (!callbacksSetup) {
            s.setCallback(createMediaSessionCallback())
            callbacksSetup = true
        }
        return s
    }

    private var player: MusicPlayerProvider? = null
    private var coverBitmap: Bitmap? = null
    private var currentCoverUrl: String? = null
    private var coverJob: Job? = null
    private var isAttached = false
    private var lastMetaDur = 0L

    // Cached PendingIntents — never change, avoid Binder IPC on every notification update
    private var cachedOpenIntent: PendingIntent? = null
    private var cachedStopIntent: PendingIntent? = null
    private var cachedPrevPending: PendingIntent? = null
    private var cachedNextPending: PendingIntent? = null
    private var cachedPlayPending: PendingIntent? = null
    private var cachedPausePending: PendingIntent? = null

    actual override var onNext: (() -> Unit)? = null
    actual override var onPrevious: (() -> Unit)? = null
    actual override var onToggleRepeatMode: (() -> Unit)? = null
    actual override var onToggleFavorite: (() -> Unit)? = null
    actual override var onDownload: (() -> Unit)? = null

    actual override fun attach(player: MusicPlayerProvider) {
        if (isAttached) return
        this.player = player
        createNotificationChannel()
        observeState(player)
        isAttached = true
    }

    actual override fun detach() {
        session?.let {
            it.isActive = false
            it.release()
        }
        session = null
        scope.cancel()
        player = null
        coverBitmap = null
        isAttached = false
        val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        nm.cancel(NOTIFICATION_ID)
    }

    private fun observeState(player: MusicPlayerProvider) {
        scope.launch {
            player.currentSong.collect { song ->
                requireSession().isActive = song != null
                loadCoverImage(song)
                updateMetadata(song)
                updateNotification()
            }
        }
        scope.launch {
            player.isPlaying.collect { playing ->
                updatePlaybackState(playing, player.currentPosition.value, player.duration.value)
            }
        }
        scope.launch {
            player.currentPosition.collect { pos ->
                updatePlaybackState(player.isPlaying.value, pos, player.duration.value)
            }
        }
    }

    // ── Cover image loading ────────────────────────────────────────

    private fun loadCoverImage(song: SongMetadata?) {
        val url = song?.coverUrl
        coverJob?.cancel()

        if (url == null) {
            currentCoverUrl = null
            coverBitmap = null
            updateNotification()
            return
        }

        if (url == currentCoverUrl) return

        currentCoverUrl = url
        coverBitmap = null
        updateNotification()

        coverJob = scope.launch(Dispatchers.IO) {
            try {
                val bitmap: Bitmap? = if (url.startsWith("/")) {
                    BitmapFactory.decodeFile(url)
                } else {
                    val connection = URL(url).openConnection() as HttpURLConnection
                    connection.apply {
                        setRequestProperty("User-Agent", "Mozilla/5.0 (Linux; Android 14) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/122.0.0.0 Mobile Safari/537.36")
                        setRequestProperty("Accept", "image/*")
                        connectTimeout = 15_000
                        readTimeout = 15_000
                    }
                    val decoded = BitmapFactory.decodeStream(connection.inputStream)
                    connection.disconnect()
                    decoded
                }
                if (bitmap == null) return@launch
                withContext(Dispatchers.Main) {
                    if (url == currentCoverUrl) {
                        coverBitmap = bitmap
                        updateMetadata(player?.currentSong?.value)
                        updateNotification()
                    }
                }
            } catch (e: Exception) {
                Log.w(TAG, "loadCoverImage failed: ${song?.songName}", e)
            }
        }
    }

    // ── MediaSession callbacks ─────────────────────────────────────

    private fun createMediaSessionCallback(): MediaSession.Callback {
        return object : MediaSession.Callback() {
            override fun onPlay() { player?.resume() }
            override fun onPause() { player?.pause() }
            override fun onSkipToNext() { onNext?.invoke() }
            override fun onSkipToPrevious() { onPrevious?.invoke() }
            override fun onSeekTo(pos: Long) { player?.seekTo(pos) }
            override fun onStop() { player?.stop() }
            override fun onMediaButtonEvent(intent: Intent): Boolean {
                val keyEvent = intent.getParcelableExtra<KeyEvent>(Intent.EXTRA_KEY_EVENT) ?: return false
                if (keyEvent.action != KeyEvent.ACTION_DOWN) return false
                return when (keyEvent.keyCode) {
                    KeyEvent.KEYCODE_MEDIA_PLAY -> { player?.resume(); true }
                    KeyEvent.KEYCODE_MEDIA_PAUSE -> { player?.pause(); true }
                    KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE -> {
                        val p = player ?: return false
                        if (p.isPlaying.value) p.pause() else p.resume()
                        true
                    }
                    KeyEvent.KEYCODE_MEDIA_NEXT -> { onNext?.invoke(); true }
                    KeyEvent.KEYCODE_MEDIA_PREVIOUS -> { onPrevious?.invoke(); true }
                    KeyEvent.KEYCODE_MEDIA_STOP -> { player?.stop(); true }
                    else -> false
                }
            }
        }
    }

    // ── Metadata ───────────────────────────────────────────────────

    private fun updateMetadata(song: SongMetadata?) {
        val builder = android.media.MediaMetadata.Builder()
        if (song != null) {
            builder.putString(android.media.MediaMetadata.METADATA_KEY_TITLE, song.songName)
            builder.putString(android.media.MediaMetadata.METADATA_KEY_ARTIST, song.artist)
            song.album?.let {
                builder.putString(android.media.MediaMetadata.METADATA_KEY_ALBUM, it)
            }
            val metaDur = maxOf(
                (song.duration * 1000L),
                player?.duration?.value ?: 0L
            )
            builder.putLong(android.media.MediaMetadata.METADATA_KEY_DURATION, metaDur)
            coverBitmap?.let {
                builder.putBitmap(android.media.MediaMetadata.METADATA_KEY_ALBUM_ART, it)
            }
        }
        requireSession().setMetadata(builder.build())
    }

    // ── PlaybackState ──────────────────────────────────────────────

    private fun updatePlaybackState(playing: Boolean, position: Long, duration: Long) {
        requireSession().setPlaybackState(
            PlaybackState.Builder()
                .setActions(ALL_ACTIONS)
                .setState(
                    if (playing) PlaybackState.STATE_PLAYING else PlaybackState.STATE_PAUSED,
                    position, 1.0f, SystemClock.elapsedRealtime()
                )
                .build()
        )
        if (duration > 0 && duration != lastMetaDur) {
            lastMetaDur = duration
            updateMetadata(player?.currentSong?.value)
        }
        updateNotification()
    }

    // ── Notification ───────────────────────────────────────────────

    private fun updateNotification() {
        val p = player ?: return
        val song = p.currentSong.value
        val playing = p.isPlaying.value
        val pos = p.currentPosition.value
        val exoDur = p.duration.value
        val metaDur = (song?.duration ?: 0) * 1000L
        val dur = maxOf(exoDur, metaDur)

        ensurePendingIntents()

        val mediaStyle = Notification.MediaStyle()
            .setMediaSession(requireSession().sessionToken)
            .setShowActionsInCompactView(0, 1, 2)

        val builder = Notification.Builder(context, CHANNEL_ID)
            .setContentTitle(song?.songName ?: "HearEverything")
            .setContentText(song?.artist ?: "正在播放")
            .setSmallIcon(com.example.aimusicplayer.shared.R.drawable.ic_notification_cover)
            .setContentIntent(cachedOpenIntent)
            .setOnlyAlertOnce(true)
            .setOngoing(playing)
            .setVisibility(Notification.VISIBILITY_PUBLIC)

        if (dur > 0) {
            builder.setProgress(dur.toInt(), pos.coerceIn(0, dur).toInt(), false)
        } else if (song != null) {
            builder.setProgress(100, 0, true)
        }

        coverBitmap?.let { builder.setLargeIcon(it) }
        builder.setStyle(mediaStyle)
        builder.setDeleteIntent(cachedStopIntent)

        // null PendingIntent → system routes through MediaSession transport controls
        // (onPlay/onPause/onSkipToNext) — direct callback, no broadcast overhead
        builder.addAction(Notification.Action.Builder(
            android.R.drawable.ic_media_previous, "上一曲", null
        ).build())
        builder.addAction(if (playing)
            Notification.Action.Builder(android.R.drawable.ic_media_pause, "暂停", null).build()
        else
            Notification.Action.Builder(android.R.drawable.ic_media_play, "播放", null).build()
        )
        builder.addAction(Notification.Action.Builder(
            android.R.drawable.ic_media_next, "下一曲", null
        ).build())

        val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        nm.notify(NOTIFICATION_ID, builder.build())
    }

    private fun ensurePendingIntents() {
        if (cachedOpenIntent != null) return
        cachedOpenIntent = context.packageManager
            .getLaunchIntentForPackage(context.packageName)
            ?.let { PendingIntent.getActivity(context, 0, it, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE) }
        // Use direct PendingIntent.getService() — bypasses ACTION_MEDIA_BUTTON
        // broadcast + KeyEvent deserialization that blocks the UI thread.
        cachedStopIntent = PlaybackService.buildActionPending(context, PlaybackService.ACTION_STOP)
        cachedPrevPending = PlaybackService.buildActionPending(context, PlaybackService.ACTION_PREV)
        cachedNextPending = PlaybackService.buildActionPending(context, PlaybackService.ACTION_NEXT)
        cachedPlayPending = PlaybackService.buildActionPending(context, PlaybackService.ACTION_PLAY)
        cachedPausePending = PlaybackService.buildActionPending(context, PlaybackService.ACTION_PAUSE)
    }

    // ── Notification channel ───────────────────────────────────────

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        if (manager.getNotificationChannel(CHANNEL_ID) != null) return
        manager.createNotificationChannel(
            NotificationChannel(CHANNEL_ID, "音乐播放", NotificationManager.IMPORTANCE_LOW).apply {
                description = "音乐播放控制和歌曲信息"
                setSound(null, null)
                setShowBadge(false)
            }
        )
    }

    companion object {
        private const val TAG = "SystemMediaController"
        private const val CHANNEL_ID = "music_playback_channel"
        private const val NOTIFICATION_ID = 1001
        private val ALL_ACTIONS = PlaybackState.ACTION_PLAY_PAUSE or
                PlaybackState.ACTION_PLAY or
                PlaybackState.ACTION_PAUSE or
                PlaybackState.ACTION_SKIP_TO_NEXT or
                PlaybackState.ACTION_SKIP_TO_PREVIOUS or
                PlaybackState.ACTION_SEEK_TO or
                PlaybackState.ACTION_STOP
    }
}
