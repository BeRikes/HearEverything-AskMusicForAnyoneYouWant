package com.example.aimusicplayer.player

import com.example.aimusicplayer.network.music.SongMetadata
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import platform.AVFoundation.AVAudioSession
import platform.AVFoundation.AVAudioSessionCategoryPlayback
import platform.AVFoundation.AVPlayer
import platform.AVFoundation.AVPlayerItem
import platform.AVFoundation.AVPlayerItemStatus
import platform.AVFoundation.AVPlayerTimeControlStatus
import platform.AVFoundation.AVPlayerWaitingToPlayAtSpecifiedRate
import platform.AVFoundation.AVPlayerWaitingWithNoItemToPlay
import platform.AVFoundation.AVPlayerStatusReadyToPlay
import platform.AVFoundation.AVPlayerStatusFailed
import platform.AVFoundation.AVPlayerStatusUnknown
import platform.Foundation.CMTimeGetSeconds
import platform.Foundation.CMTimeMake
import platform.Foundation.NSURL
import platform.darwin.dispatch_get_main_queue

/**
 * iOS implementation of [MusicPlayer] backed by AVPlayer.
 *
 * ## Background audio
 * On init, the [AVAudioSession] is configured with category
 * `AVAudioSessionCategoryPlayback` so audio continues when the app is
 * backgrounded. iOS also requires the `audio` UIBackgroundModes key
 * in Info.plist for this to work.
 *
 * ## Position tracking
 * An AVPlayer periodic time observer fires every 500 ms on the main
 * dispatch queue, pushing the current position into [currentPosition].
 *
 * ## Error handling
 * Errors are captured by checking [AVPlayerItem.status] after loading.
 * Network failures, 404s, and decode errors all surface through this path.
 */
actual class MusicPlayer : MusicPlayerProvider {

    // ══════════════════════════════════════════════════════════════
    // Audio session
    // ══════════════════════════════════════════════════════════════

    init {
        configureAudioSession()
    }

    private fun configureAudioSession() {
        val session = AVAudioSession.sharedInstance()
        session.setCategory(
            category = AVAudioSessionCategoryPlayback,
            error = null,
        )
        session.setActive(
            active = true,
            error = null,
        )
    }

    // ══════════════════════════════════════════════════════════════
    // Internal state
    // ══════════════════════════════════════════════════════════════

    private val scope = CoroutineScope(Dispatchers.Main + SupervisorJob())

    private var player: AVPlayer? = null
    private var currentItem: AVPlayerItem? = null
    private var timeObserverToken: Any? = null

    // ══════════════════════════════════════════════════════════════
    // Exposed StateFlow properties
    // ══════════════════════════════════════════════════════════════

    private val _isPlaying = MutableStateFlow(false)
    private val _currentPosition = MutableStateFlow(0L)
    private val _duration = MutableStateFlow(0L)
    private val _currentSong = MutableStateFlow<SongMetadata?>(null)
    private val _playbackError = MutableStateFlow<String?>(null)

    actual val isPlaying: StateFlow<Boolean> = _isPlaying.asStateFlow()
    actual val currentPosition: StateFlow<Long> = _currentPosition.asStateFlow()
    actual val duration: StateFlow<Long> = _duration.asStateFlow()
    actual val currentSong: StateFlow<SongMetadata?> = _currentSong.asStateFlow()
    actual val playbackError: StateFlow<String?> = _playbackError.asStateFlow()

    // ══════════════════════════════════════════════════════════════
    // Playback controls
    // ══════════════════════════════════════════════════════════════

    actual suspend fun play(url: String, title: String, artist: String, song: SongMetadata?) {
        // Stop any previous playback before starting new
        stopPositionObserver()
        _playbackError.value = null
        _currentPosition.value = 0L
        _duration.value = 0L

        // Validate URL
        val nsUrl = NSURL.URLWithString(url)
        if (nsUrl == null) {
            _playbackError.value = "无效的音频 URL: $url"
            return
        }

        // Create player item
        val playerItem = AVPlayerItem(URL = nsUrl)
        currentItem = playerItem

        // Create or replace player
        if (player == null) {
            player = AVPlayer(playerItem = playerItem)
        } else {
            player?.replaceCurrentItemWithPlayerItem(playerItem)
        }

        // Start time observer — fires every 500 ms on the main queue
        startPositionObserver()

        // Duration becomes available asynchronously; poll it alongside position
        // once playback starts

        // Begin playback
        player?.play()
        _isPlaying.value = true

        // Poll once for duration after a short delay to let AVPlayer load metadata
        updateDurationFromItem(playerItem)

        _currentSong.value = song ?: SongMetadata(
            songId = url.hashCode().toString(),
            songName = title,
            artist = artist,
            platform = "playback",
        )
    }

    actual fun pause() {
        player?.pause()
        _isPlaying.value = false
    }

    actual fun resume() {
        player?.play()
        _isPlaying.value = true
    }

    actual fun stop() {
        player?.pause()
        stopPositionObserver()
        player?.replaceCurrentItemWithPlayerItem(null)
        currentItem = null
        _isPlaying.value = false
        _currentPosition.value = 0L
        _duration.value = 0L
        _currentSong.value = null
        _playbackError.value = null
    }

    actual fun seekTo(positionMs: Long) {
        val seconds = positionMs / 1000.0
        val time = CMTimeMake(
            value = (seconds * 1000).toLong(), // CMTime uses a timescale
            timescale = 1000,                   // milliseconds timescale
        )
        player?.seekTo(time = time)
        _currentPosition.value = positionMs
    }

    actual fun setVolume(volume: Float) {
        player?.volume = volume.coerceIn(0f, 1f)
    }

    actual fun clearPlaybackError() {
        _playbackError.value = null
    }

    // ══════════════════════════════════════════════════════════════
    // Lifecycle
    // ══════════════════════════════════════════════════════════════

    actual fun release() {
        stopPositionObserver()
        player?.pause()
        player?.replaceCurrentItemWithPlayerItem(null)
        player = null
        currentItem = null
        scope.cancel()
    }

    // ══════════════════════════════════════════════════════════════
    // Position observer (500 ms interval)
    // ══════════════════════════════════════════════════════════════

    private fun startPositionObserver() {
        val p = player ?: return
        stopPositionObserver()

        // CMTime(1, 2) = 1/2 second = 500 ms
        val interval = CMTimeMake(value = 1, timescale = 2)
        val mainQueue = dispatch_get_main_queue()

        timeObserverToken = p.addPeriodicTimeObserverForInterval(
            interval = interval,
            queue = mainQueue,
            usingBlock = { time ->
                val positionMs = (CMTimeGetSeconds(time) * 1000).toLong()
                _currentPosition.value = positionMs

                // Update state from timeControlStatus each tick
                syncPlaybackState()

                // Update duration from current item (metadata may have loaded)
                val item = p.currentItem
                if (item != null) {
                    updateDurationFromItem(item)
                    checkItemError(item)
                }
            },
        )
    }

    private fun stopPositionObserver() {
        val token = timeObserverToken ?: return
        player?.removeTimeObserver(token)
        timeObserverToken = null
    }

    // ══════════════════════════════════════════════════════════════
    // State & error helpers
    // ══════════════════════════════════════════════════════════════

    /**
     * Sync [_isPlaying] from AVPlayer's [AVPlayer.timeControlStatus].
     *
     * AVPlayerTimeControlStatus values:
     * - `AVPlayerTimeControlStatusPlaying` (0) — actively playing
     * - `AVPlayerTimeControlStatusPaused` (1) — paused by user
     * - `AVPlayerTimeControlStatusWaitingToPlayAtSpecifiedRate` (2) — buffering
     */
    private fun syncPlaybackState() {
        val p = player ?: return
        val status = p.timeControlStatus
        val playing = status == AVPlayerTimeControlStatus.AVPlayerTimeControlStatusPlaying
                || status == AVPlayerWaitingToPlayAtSpecifiedRate
        if (_isPlaying.value != playing) {
            _isPlaying.value = playing
        }
    }

    /** Attempt to read duration from the current item's loaded metadata. */
    private fun updateDurationFromItem(item: AVPlayerItem) {
        // duration may be invalid (CMTime.indefinite) before metadata is loaded
        val seconds = CMTimeGetSeconds(item.duration)
        if (!seconds.isNaN() && seconds > 0.0) {
            _duration.value = (seconds * 1000).toLong()
        }
    }

    /**
     * Check [AVPlayerItem.status] for errors.
     *
     * - `AVPlayerItemStatusReadyToPlay` — item loaded successfully, clear error
     * - `AVPlayerItemStatusFailed` — error occurred, capture localized description
     * - `AVPlayerItemStatusUnknown` — still loading, no action
     */
    private fun checkItemError(item: AVPlayerItem) {
        when (item.status) {
            AVPlayerItemStatus.AVPlayerItemStatusReadyToPlay -> {
                // Clear any previous error
                if (_playbackError.value != null) {
                    _playbackError.value = null
                }
            }
            AVPlayerItemStatus.AVPlayerItemStatusFailed -> {
                val error = item.error
                val message = when {
                    error == null -> "未知播放错误"
                    error.localizedDescription.contains("network", ignoreCase = true)
                            || error.localizedDescription.contains("connection", ignoreCase = true) ->
                        "网络连接失败: ${error.localizedDescription}"
                    error.localizedDescription.contains("decode", ignoreCase = true)
                            || error.localizedDescription.contains("format", ignoreCase = true) ->
                        "音频解码失败: ${error.localizedDescription}"
                    error.localizedDescription.contains("not found", ignoreCase = true)
                            || error.localizedDescription.contains("404", ignoreCase = true) ->
                        "音频文件不存在 (404)"
                    else ->
                        "播放错误: ${error.localizedDescription}"
                }
                _playbackError.value = message
                _isPlaying.value = false
            }
            AVPlayerItemStatus.AVPlayerItemStatusUnknown -> {
                // Still loading — no action
            }
            else -> { /* unknown status value */ }
        }
    }
}
