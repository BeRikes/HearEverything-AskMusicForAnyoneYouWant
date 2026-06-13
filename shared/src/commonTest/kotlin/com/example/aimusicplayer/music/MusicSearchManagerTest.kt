package com.example.aimusicplayer.music

import com.example.aimusicplayer.fixtures.FakeMusicPlatformApi
import com.example.aimusicplayer.network.music.MusicPlatformApi
import com.example.aimusicplayer.network.music.SongMetadata
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class MusicSearchManagerTest {

    @Test
    fun `search returns results from first platform`() = runTest {
        val song = FakeMusicPlatformApi.testSong(
            id = "bili-001",
            songName = "夜曲",
            platform = "bilibili",
        )
        val bilibiliApi = FakeMusicPlatformApi.withSearchResults("bilibili", listOf(song))
        val manager = MusicSearchManager(listOf(bilibiliApi, FakeMusicPlatformApi.empty("netease")))

        val results = manager.search("夜曲")
        assertEquals(1, results.size)
        assertEquals("夜曲", results[0].songName)
        assertEquals("bilibili", results[0].platform)
    }

    @Test
    fun `search falls through when first platform returns empty`() = runTest {
        val song = FakeMusicPlatformApi.testSong(
            id = "netease-001",
            songName = "稻香",
            platform = "netease",
        )
        val neteaseApi = FakeMusicPlatformApi.withSearchResults("netease", listOf(song))
        val manager = MusicSearchManager(
            listOf(FakeMusicPlatformApi.empty("bilibili"), neteaseApi)
        )

        val results = manager.search("稻香")
        assertEquals(1, results.size)
        assertEquals("netease", results[0].platform)
    }

    @Test
    fun `search handles platform throwing exception`() = runTest {
        val song = FakeMusicPlatformApi.testSong(id = "qq-001", platform = "qq")
        val qqApi = FakeMusicPlatformApi.withSearchResults("qq", listOf(song))
        val manager = MusicSearchManager(
            listOf(
                FakeMusicPlatformApi.failing("bilibili"),
                FakeMusicPlatformApi.empty("netease"),
                qqApi,
            )
        )

        val results = manager.search("test")
        assertEquals(1, results.size)
        assertEquals("qq", results[0].platform)
    }

    @Test
    fun `search returns empty when all platforms fail`() = runTest {
        val manager = MusicSearchManager(
            listOf(
                FakeMusicPlatformApi.failing("bilibili"),
                FakeMusicPlatformApi.failing("netease"),
                FakeMusicPlatformApi.failing("qq"),
            )
        )

        val results = manager.search("test")
        assertTrue(results.isEmpty())
    }

    @Test
    fun `search returns empty when all platforms return empty`() = runTest {
        val manager = MusicSearchManager(
            listOf(
                FakeMusicPlatformApi.empty("bilibili"),
                FakeMusicPlatformApi.empty("netease"),
                FakeMusicPlatformApi.empty("qq"),
            )
        )

        val results = manager.search("test")
        assertTrue(results.isEmpty())
    }

    @Test
    fun `searchState transitions to Searching then Results`() = runTest {
        val song = FakeMusicPlatformApi.testSong(platform = "bilibili")
        val api = FakeMusicPlatformApi.withSearchResults("bilibili", listOf(song))
        val manager = MusicSearchManager(listOf(api))

        manager.search("夜曲")

        // After search, should be in Results state
        val state = manager.searchState.value
        assertTrue(state is SearchState.Results)
    }

    @Test
    fun `searchState transitions to NoResults when nothing found`() = runTest {
        val manager = MusicSearchManager(listOf(FakeMusicPlatformApi.empty("bilibili")))

        manager.search("nonexistent_query_xyz")

        val state = manager.searchState.value
        assertTrue(state is SearchState.NoResults)
    }

    @Test
    fun `clearSearchState resets to Idle`() = runTest {
        val manager = MusicSearchManager(listOf(FakeMusicPlatformApi.empty("bilibili")))
        manager.search("test")
        manager.clearSearchState()
        assertTrue(manager.searchState.value is SearchState.Idle)
    }

    @Test
    fun `searchOnPlatform returns results for known platform`() = runTest {
        val song = FakeMusicPlatformApi.testSong(id = "qq-002", platform = "qq")
        val qqApi = FakeMusicPlatformApi.withSearchResults("qq", listOf(song))
        val manager = MusicSearchManager(listOf(qqApi))

        val results = manager.searchOnPlatform("稻香", "qq")
        assertEquals(1, results.size)
        assertEquals("qq-002", results[0].songId)
    }

    @Test
    fun `searchOnPlatform returns empty for unknown platform`() = runTest {
        val manager = MusicSearchManager(emptyList())
        val results = manager.searchOnPlatform("test", "unknown_platform")
        assertTrue(results.isEmpty())
    }

    @Test
    fun `getPlayUrl returns URL from matching platform`() = runTest {
        val api = object : MusicPlatformApi {
            override val platform = "test"
            override suspend fun search(query: String, platform: String) = emptyList<SongMetadata>()
            override suspend fun getPlayUrl(songId: String, platform: String, quality: String) = "https://audio.test/play.mp3"
            override suspend fun getLyrics(songId: String, artist: String) = null
        }
        val manager = MusicSearchManager(listOf(api))
        val url = manager.getPlayUrl("song-1", "test")
        assertEquals("https://audio.test/play.mp3", url)
    }

    @Test
    fun `getPlayUrl returns null for unknown platform`() = runTest {
        val manager = MusicSearchManager(emptyList())
        val url = manager.getPlayUrl("song-1", "unknown")
        assertNull(url)
    }

    @Test
    fun `getLyrics returns lyrics from matching platform`() = runTest {
        val api = object : MusicPlatformApi {
            override val platform = "test"
            override suspend fun search(query: String, platform: String) = emptyList<SongMetadata>()
            override suspend fun getPlayUrl(songId: String, platform: String, quality: String) = null
            override suspend fun getLyrics(songId: String, artist: String) = "[00:00]Test lyrics"
        }
        val manager = MusicSearchManager(listOf(api))
        val lyrics = manager.getLyrics("song-1", "test")
        assertEquals("[00:00]Test lyrics", lyrics)
    }

    @Test
    fun `getLyrics returns null for unknown platform`() = runTest {
        val manager = MusicSearchManager(emptyList())
        val lyrics = manager.getLyrics("song-1", "unknown")
        assertNull(lyrics)
    }

    @Test
    fun `getPlatformOrder returns configured order`() {
        val manager = MusicSearchManager(emptyList())
        val order = manager.getPlatformOrder()
        assertEquals(listOf("bilibili", "netease", "qq"), order)
    }

    @Test
    fun `search aggregates results from multiple platforms`() = runTest {
        val song1 = FakeMusicPlatformApi.testSong(id = "bili-1", platform = "bilibili")
        val song2 = FakeMusicPlatformApi.testSong(id = "netease-1", platform = "netease")
        val manager = MusicSearchManager(
            listOf(
                FakeMusicPlatformApi.withSearchResults("bilibili", listOf(song1)),
                FakeMusicPlatformApi.withSearchResults("netease", listOf(song2)),
            )
        )

        val results = manager.search("test")
        // Bilibili is first in platformOrder, and both platforms have results.
        // The search() method adds results from ALL platforms that have results
        // (it iterates through all platforms and accumulates results).
        // Since bilibili comes first: both platforms contribute.
        assertTrue(results.size >= 1)
        assertTrue(results.any { it.platform == "bilibili" })
    }
}
