package com.example.aimusicplayer.fixtures

import com.example.aimusicplayer.network.music.SongMetadata
import com.example.aimusicplayer.player.MusicPlayerProvider
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * In-memory fake implementation of [MusicPlayerProvider] for unit testing ViewModels.
 */
class FakeMusicPlayer : MusicPlayerProvider {

    private val _currentPosition = MutableStateFlow(0L)
    private val _duration = MutableStateFlow(0L)
    private val _isPlaying = MutableStateFlow(false)
    private val _currentSong = MutableStateFlow<SongMetadata?>(null)
    private val _playbackError = MutableStateFlow<String?>(null)

    override val currentPosition: StateFlow<Long> = _currentPosition.asStateFlow()
    override val duration: StateFlow<Long> = _duration.asStateFlow()
    override val isPlaying: StateFlow<Boolean> = _isPlaying.asStateFlow()
    override val currentSong: StateFlow<SongMetadata?> = _currentSong.asStateFlow()
    override val playbackError: StateFlow<String?> = _playbackError.asStateFlow()

    private var _isPaused = false
    private var _isStopped = false

    override suspend fun play(url: String, title: String, artist: String, song: SongMetadata?) {
        _currentSong.value = song
        _isPlaying.value = true
        _isPaused = false
        _isStopped = false
        _playbackError.value = null
    }

    override fun pause() {
        _isPaused = true
        _isPlaying.value = false
    }

    override fun resume() {
        if (_isPaused) {
            _isPaused = false
            _isPlaying.value = true
        }
    }

    override fun stop() {
        _isStopped = true
        _isPlaying.value = false
        _currentSong.value = null
    }

    override fun seekTo(positionMs: Long) {
        _currentPosition.value = positionMs
    }

    override fun setVolume(volume: Float) { /* no-op */ }

    override fun clearPlaybackError() {
        _playbackError.value = null
    }

    override fun release() {
        _isPlaying.value = false
        _currentSong.value = null
    }

    // Test helpers
    fun setDuration(ms: Long) { _duration.value = ms }
    fun setPosition(ms: Long) { _currentPosition.value = ms }
    fun setPlaying(playing: Boolean) { _isPlaying.value = playing }
    fun setError(error: String?) { _playbackError.value = error }
    fun setCurrentSong(song: SongMetadata?) { _currentSong.value = song }
}
