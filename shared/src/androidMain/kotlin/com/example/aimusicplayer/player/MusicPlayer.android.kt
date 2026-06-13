package com.example.aimusicplayer.player

import android.content.Context
import android.util.Log
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.datasource.DefaultHttpDataSource
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import androidx.media3.exoplayer.source.ProgressiveMediaSource
import com.example.aimusicplayer.network.music.SongMetadata
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

/**
 * Android implementation of [MusicPlayer] backed by Media3 ExoPlayer.
 *
 * ## Architecture
 * - **ExoPlayer** for audio decoding and network streaming
 * - **MediaSession** (via ExoPlayer) for system-level media control integration
 *   (lock screen controls, Bluetooth, Android Auto)
 * - **Coroutine-based position polling** every 500 ms
 * For background playback, use [PlaybackService] and [SystemMediaController]
 * to manage the foreground notification and MediaSession.
 *
 * @param context Android Context (typically Application context for singleton usage)
 */
actual class MusicPlayer(private val context: Context) : MusicPlayerProvider {

    // ══════════════════════════════════════════════════════════════
    // Internal dependencies
    // ══════════════════════════════════════════════════════════════

    private val scope = CoroutineScope(Dispatchers.Main + SupervisorJob())

    // Lazy: defer ExoPlayer + AudioManager registration until first play().
    // Eager construction causes the media-output-picker panel animation to
    // stutter (system queries all registered audio players during the animation).
    private var exoPlayer: ExoPlayer? = null

    private fun requireExoPlayer(): ExoPlayer {
        val existing = exoPlayer
        if (existing != null) return existing
        val player = ExoPlayer.Builder(context)
            .setMediaSourceFactory(DefaultMediaSourceFactory(context))
            .setHandleAudioBecomingNoisy(true)
            .build()
        player.addListener(exoPlayerListener)
        exoPlayer = player
        return player
    }

    // ══════════════════════════════════════════════════════════════
    // State
    // ══════════════════════════════════════════════════════════════

    private val _isPlaying = MutableStateFlow(false)
    private val _currentPosition = MutableStateFlow(0L)
    private val _duration = MutableStateFlow(0L)
    private val _currentSong = MutableStateFlow<SongMetadata?>(null)
    private val _playbackError = MutableStateFlow<String?>(null)

    actual override val isPlaying: StateFlow<Boolean> = _isPlaying.asStateFlow()
    actual override val currentPosition: StateFlow<Long> = _currentPosition.asStateFlow()
    actual override val duration: StateFlow<Long> = _duration.asStateFlow()
    actual override val currentSong: StateFlow<SongMetadata?> = _currentSong.asStateFlow()
    actual override val playbackError: StateFlow<String?> = _playbackError.asStateFlow()

    // ══════════════════════════════════════════════════════════════
    // Position polling (every 500 ms)
    // ══════════════════════════════════════════════════════════════

    private var positionJob: Job? = null

    private fun startPositionPolling() {
        if (positionJob?.isActive == true) return
        positionJob = scope.launch {
            while (isActive) {
                _currentPosition.value = requireExoPlayer().currentPosition
                delay(500)
            }
        }
    }

    private fun stopPositionPolling() {
        positionJob?.cancel()
        positionJob = null
    }

    // ══════════════════════════════════════════════════════════════
    // Error classification
    // ══════════════════════════════════════════════════════════════

    private fun classifyPlaybackError(error: PlaybackException): String {
        return when {
            error.errorCode == PlaybackException.ERROR_CODE_IO_NETWORK_CONNECTION_FAILED ->
                "网络连接失败，请检查网络设置"
            error.errorCode == PlaybackException.ERROR_CODE_IO_NETWORK_CONNECTION_TIMEOUT ->
                "网络连接超时，服务器无响应"
            error.errorCode == PlaybackException.ERROR_CODE_IO_FILE_NOT_FOUND ->
                "音频文件不存在 (404)"
            error.errorCode == PlaybackException.ERROR_CODE_IO_BAD_HTTP_STATUS ->
                "服务器返回错误状态码"
            error.errorCode == PlaybackException.ERROR_CODE_DECODING_FAILED ->
                "音频解码失败，格式可能不受支持"
            error.errorCode == PlaybackException.ERROR_CODE_DECODER_INIT_FAILED ->
                "解码器初始化失败"
            error.errorCode == PlaybackException.ERROR_CODE_PARSING_CONTAINER_MALFORMED ->
                "音频容器格式损坏"
            error.errorCode == PlaybackException.ERROR_CODE_PARSING_CONTAINER_UNSUPPORTED ->
                "不支持的音频容器格式"
            error.errorCode == PlaybackException.ERROR_CODE_AUDIO_TRACK_INIT_FAILED ->
                "音频输出初始化失败"
            else ->
                "播放错误: ${error.localizedMessage ?: "未知错误"}"
        }
    }

    // ══════════════════════════════════════════════════════════════
    // ExoPlayer listener (attached lazily in requireExoPlayer)
    // ══════════════════════════════════════════════════════════════

    private val exoPlayerListener = object : Player.Listener {

        override fun onPlaybackStateChanged(state: Int) {
            when (state) {
                Player.STATE_READY -> {
                    _playbackError.value = null
                    _duration.value = requireExoPlayer().duration.coerceAtLeast(0L)
                    Log.d("MusicPlayer", "STATE_READY: dur=${_duration.value}ms, song=${_currentSong.value?.songName}, willSetPlaying=${requireExoPlayer().playWhenReady}")
                    _isPlaying.value = requireExoPlayer().playWhenReady
                    if (requireExoPlayer().playWhenReady) {
                        startPositionPolling()
                    }
                }
                Player.STATE_ENDED -> {
                    _isPlaying.value = false
                    stopPositionPolling()
                }
                Player.STATE_IDLE -> {
                    _isPlaying.value = false
                    stopPositionPolling()
                }
                Player.STATE_BUFFERING -> {
                    // Keep current isPlaying value — don't flip to false
                    // just because we're buffering
                }
            }
        }

        override fun onIsPlayingChanged(isPlaying: Boolean) {
            _isPlaying.value = isPlaying
            if (isPlaying) {
                startPositionPolling()
            } else {
                stopPositionPolling()
            }
        }

        override fun onPlayerError(error: PlaybackException) {
            _playbackError.value = classifyPlaybackError(error)
            _isPlaying.value = false
            stopPositionPolling()
        }

        override fun onMediaMetadataChanged(mediaMetadata: MediaMetadata) {
            val song = _currentSong.value ?: return
            val updatedTitle = mediaMetadata.title?.toString() ?: song.songName
            val updatedArtist = mediaMetadata.artist?.toString() ?: song.artist
            if (updatedTitle != song.songName || updatedArtist != song.artist) {
                _currentSong.value = song.copy(songName = updatedTitle, artist = updatedArtist)
            }
        }
    }

    // ══════════════════════════════════════════════════════════════
    // Playback controls
    // ══════════════════════════════════════════════════════════════

    actual override suspend fun play(url: String, title: String, artist: String, song: SongMetadata?) {
        // Clear previous error and reset position for the new track
        _playbackError.value = null
        _currentPosition.value = 0L
        _duration.value = 0L

        // Convert local file path to file:// URI for ExoPlayer
        val isLocalFile = url.startsWith("/")
        val uri = if (isLocalFile) "file://$url" else url

        val platform = song?.platform ?: ""

        // Local file: use simple MediaItem (HTTP headers don't apply)
        if (isLocalFile) {
            requireExoPlayer().setMediaItem(
                MediaItem.Builder()
                    .setUri(uri)
                    .setMediaMetadata(
                        MediaMetadata.Builder()
                            .setTitle(title)
                            .setArtist(artist)
                            .build()
                    )
                    .build()
            )
        } else {
            // Build platform-specific HTTP headers for CDN access
            val httpHeaders = mutableMapOf<String, String>()
            when (platform) {
                "bilibili" -> {
                    httpHeaders["Referer"] = "https://www.bilibili.com/"
                    httpHeaders["Origin"] = "https://www.bilibili.com"
                    httpHeaders["User-Agent"] = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/122.0.0.0 Safari/537.36"
                }
                "qq" -> {
                    httpHeaders["Referer"] = "https://y.qq.com/"
                    httpHeaders["User-Agent"] = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36"
                }
                "kugou" -> {
                    httpHeaders["Referer"] = "https://www.kugou.com/"
                }
                "netease" -> {
                    httpHeaders["Referer"] = "https://music.163.com/"
                }
            }

            if (httpHeaders.isNotEmpty()) {
                val dataSourceFactory = DefaultHttpDataSource.Factory()
                    .setDefaultRequestProperties(httpHeaders)
                val mediaSource = ProgressiveMediaSource.Factory(dataSourceFactory)
                    .createMediaSource(MediaItem.fromUri(uri))
                requireExoPlayer().setMediaSource(mediaSource)
            } else {
                requireExoPlayer().setMediaItem(
                    MediaItem.Builder()
                        .setUri(uri)
                        .setMediaMetadata(
                            MediaMetadata.Builder()
                                .setTitle(title)
                                .setArtist(artist)
                                .build()
                        )
                        .build()
                )
            }
        }
        requireExoPlayer().prepare()
        requireExoPlayer().playWhenReady = true

        _currentSong.value = song ?: SongMetadata(
            songId = url.hashCode().toString(),
            songName = title,
            artist = artist,
            platform = "playback",
        )
        Log.d("MusicPlayer", "play() set currentSong: name=${_currentSong.value?.songName}, metaDur=${_currentSong.value?.duration}s, pos=${_currentPosition.value}, exoDur=${_duration.value}")
    }

    actual override fun pause() {
        requireExoPlayer().playWhenReady = false
        requireExoPlayer().pause()
    }

    actual override fun resume() {
        requireExoPlayer().playWhenReady = true
        requireExoPlayer().play()
    }

    actual override fun stop() {
        requireExoPlayer().stop()
        requireExoPlayer().clearMediaItems()
        _isPlaying.value = false
        _currentPosition.value = 0L
        _duration.value = 0L
        _currentSong.value = null
        stopPositionPolling()
    }

    actual override fun seekTo(positionMs: Long) {
        requireExoPlayer().seekTo(positionMs)
    }

    actual override fun setVolume(volume: Float) {
        requireExoPlayer().volume = volume.coerceIn(0f, 1f)
    }

    actual override fun clearPlaybackError() {
        _playbackError.value = null
    }

    // ══════════════════════════════════════════════════════════════
    // Lifecycle
    // ══════════════════════════════════════════════════════════════

    actual override fun release() {
        stopPositionPolling()
        requireExoPlayer().release()
        scope.cancel()
    }

}
