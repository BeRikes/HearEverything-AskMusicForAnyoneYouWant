package com.example.aimusicplayer.music

import com.example.aimusicplayer.fixtures.FakeMusicPlatformApi
import com.example.aimusicplayer.network.music.SongMetadata
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull

class CrossPlatformLyricsFetcherTest {

    @Test
    fun `fetch returns null for blank song name`() = runTest {
        val searchManager = MusicSearchManager(emptyList())
        val fetcher = CrossPlatformLyricsFetcher(searchManager)

        val result = fetcher.fetch("", "artist")
        assertNull(result)
    }

    @Test
    fun `fetch returns null when no results found`() = runTest {
        val neteaseApi = FakeMusicPlatformApi.empty("netease")
        val searchManager = MusicSearchManager(listOf(neteaseApi))
        val fetcher = CrossPlatformLyricsFetcher(searchManager)

        val result = fetcher.fetch("Unknown Song", "Unknown Artist")
        assertNull(result)
    }

    @Test
    fun `fetch with high confidence match returns lyrics`() = runTest {
        val song = SongMetadata(
            songId = "netease-001",
            songName = "Lemon",
            artist = "Kenshi Yonezu",
            duration = 256,
            platform = "netease",
        )
        val api = object : com.example.aimusicplayer.network.music.MusicPlatformApi {
            override val platform = "netease"
            override suspend fun search(query: String, platform: String) = listOf(song)
            override suspend fun getPlayUrl(songId: String, platform: String, quality: String) = null
            override suspend fun getLyrics(songId: String, artist: String) = "[00:00.00]夢ならばどれほどよかったでしょう"
        }
        val searchManager = MusicSearchManager(listOf(api))
        val fetcher = CrossPlatformLyricsFetcher(searchManager)

        val result = fetcher.fetch(
            songName = "Lemon",
            artist = "Kenshi Yonezu",
            durationS = 256,
        )
        assertNotNull(result)
        assertNotNull(result.lyrics)
    }

    @Test
    fun `fetch returns null when search exceptions occur`() = runTest {
        val api = FakeMusicPlatformApi.failing("netease", RuntimeException("Network error"))
        val searchManager = MusicSearchManager(listOf(api))
        val fetcher = CrossPlatformLyricsFetcher(searchManager)

        val result = fetcher.fetch("Lemon", "Kenshi Yonezu")
        assertNull(result)
    }
}
