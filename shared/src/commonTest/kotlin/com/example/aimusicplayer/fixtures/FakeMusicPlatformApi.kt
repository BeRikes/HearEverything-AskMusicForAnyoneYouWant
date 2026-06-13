package com.example.aimusicplayer.fixtures

import com.example.aimusicplayer.network.music.MusicPlatformApi
import com.example.aimusicplayer.network.music.PlaylistDetail
import com.example.aimusicplayer.network.music.SongMetadata

/**
 * Configurable fake implementation of [MusicPlatformApi] for unit testing.
 */
class FakeMusicPlatformApi(
    override val platform: String,
    private val searchResults: suspend (String) -> List<SongMetadata> = { emptyList() },
    private val playUrlResult: suspend (String, String) -> String? = { _, _ -> null },
    private val lyricsResult: suspend (String) -> String? = { null },
    private val playlistDetailResult: suspend (String) -> PlaylistDetail? = { null },
) : MusicPlatformApi {

    override suspend fun search(query: String, platform: String): List<SongMetadata> {
        return searchResults(query)
    }

    override suspend fun getPlayUrl(songId: String, platform: String, quality: String): String? {
        return playUrlResult(songId, quality)
    }

    override suspend fun getLyrics(songId: String, platform: String): String? {
        return lyricsResult(songId)
    }

    override suspend fun getPlaylistDetail(playlistId: String): PlaylistDetail? {
        return playlistDetailResult(playlistId)
    }

    companion object {
        fun withSearchResults(
            platform: String,
            results: List<SongMetadata>,
        ): FakeMusicPlatformApi = FakeMusicPlatformApi(
            platform = platform,
            searchResults = { _ -> results },
        )

        fun empty(platform: String): FakeMusicPlatformApi = FakeMusicPlatformApi(platform = platform)

        fun failing(platform: String, error: Throwable = RuntimeException("Simulated failure")): FakeMusicPlatformApi =
            FakeMusicPlatformApi(
                platform = platform,
                searchResults = { throw error },
            )

        fun testSong(
            id: String = "test-001",
            songName: String = "Test Song",
            artist: String = "Test Artist",
            platform: String = "test",
            duration: Int = 240,
            quality: String = "standard",
            fee: Int = 0,
        ): SongMetadata = SongMetadata(
            songId = id,
            songName = songName,
            artist = artist,
            album = "Test Album",
            duration = duration,
            coverUrl = "https://example.com/cover.jpg",
            platform = platform,
            quality = quality,
            fee = fee,
        )
    }
}
