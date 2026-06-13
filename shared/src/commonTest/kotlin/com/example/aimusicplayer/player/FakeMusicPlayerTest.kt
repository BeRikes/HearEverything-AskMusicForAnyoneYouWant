package com.example.aimusicplayer.player

import com.example.aimusicplayer.fixtures.FakeMusicPlayer
import com.example.aimusicplayer.network.music.SongMetadata
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class FakeMusicPlayerTest {

    @Test
    fun play_sets_song_and_playing_state() = runTest {
        val player = FakeMusicPlayer()
        val song = SongMetadata(
            songId = "s1", songName = "夜曲", artist = "周杰伦",
            platform = "netease", duration = 222,
        )
        player.play("https://example.com/audio.mp3", "夜曲", "周杰伦", song)
        assertEquals("夜曲", player.currentSong.first()?.songName)
        assertTrue(player.isPlaying.first())
    }

    @Test
    fun pause_stops_playing() = runTest {
        val player = FakeMusicPlayer()
        player.play("url", "title", "artist", null)
        player.pause()
        assertFalse(player.isPlaying.first())
    }

    @Test
    fun resume_restarts_playing() = runTest {
        val player = FakeMusicPlayer()
        player.play("url", "title", "artist", null)
        player.pause()
        player.resume()
        assertTrue(player.isPlaying.first())
    }

    @Test
    fun stop_clears_song() = runTest {
        val player = FakeMusicPlayer()
        player.play("url", "t", "a", SongMetadata(songId = "x", songName = "X", artist = "Y", platform = "qq"))
        player.stop()
        assertFalse(player.isPlaying.first())
        assertNull(player.currentSong.first())
    }

    @Test
    fun seek_to_sets_position() = runTest {
        val player = FakeMusicPlayer()
        player.seekTo(50000L)
        assertEquals(50000L, player.currentPosition.first())
    }

    @Test
    fun clear_error_works() = runTest {
        val player = FakeMusicPlayer()
        player.setError("test error")
        assertEquals("test error", player.playbackError.first())
        player.clearPlaybackError()
        assertNull(player.playbackError.first())
    }

    @Test
    fun release_clears_state() = runTest {
        val player = FakeMusicPlayer()
        player.play("u", "t", "a", SongMetadata(songId = "s", songName = "S", artist = "A", platform = "bilibili"))
        player.release()
        assertFalse(player.isPlaying.first())
        assertNull(player.currentSong.first())
    }

    @Test
    fun test_helpers_set_state() = runTest {
        val player = FakeMusicPlayer()
        player.setDuration(300000L)
        player.setPosition(120000L)
        assertEquals(300000L, player.duration.first())
        assertEquals(120000L, player.currentPosition.first())
    }

    @Test
    fun error_is_cleared_on_new_play() = runTest {
        val player = FakeMusicPlayer()
        player.setError("previous error")
        player.play("url", "title", "artist", null)
        assertNull(player.playbackError.first())
    }
}
