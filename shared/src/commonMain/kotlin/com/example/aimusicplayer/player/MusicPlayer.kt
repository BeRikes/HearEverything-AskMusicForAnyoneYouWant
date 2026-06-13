package com.example.aimusicplayer.player

import com.example.aimusicplayer.network.music.SongMetadata
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

/**
 * Testable interface for cross-platform music playback.
 * Implemented by [FakeMusicPlayer] in tests and by [MusicPlayer] actual class in production.
 */
interface MusicPlayerProvider {
    suspend fun play(url: String, title: String, artist: String = "", song: SongMetadata? = null)
    fun pause()
    fun resume()
    fun stop()
    fun seekTo(positionMs: Long)
    fun setVolume(volume: Float)
    val currentPosition: StateFlow<Long>
    val duration: StateFlow<Long>
    val isPlaying: StateFlow<Boolean>
    val currentSong: StateFlow<SongMetadata?>
    val playbackError: StateFlow<String?>
    fun clearPlaybackError()
    fun release()
}

/**
 * Cross-platform music player abstraction.
 * Android: Media3 ExoPlayer. iOS: AVPlayer.
 */
expect class MusicPlayer : MusicPlayerProvider {
    override suspend fun play(url: String, title: String, artist: String, song: SongMetadata?)
    override fun pause()
    override fun resume()
    override fun stop()
    override fun seekTo(positionMs: Long)
    override fun setVolume(volume: Float)
    override val currentPosition: StateFlow<Long>
    override val duration: StateFlow<Long>
    override val isPlaying: StateFlow<Boolean>
    override val currentSong: StateFlow<SongMetadata?>
    override val playbackError: StateFlow<String?>
    override fun clearPlaybackError()
    override fun release()
}
