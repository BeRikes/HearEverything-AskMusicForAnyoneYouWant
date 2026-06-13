package com.example.aimusicplayer.export

import kotlinx.serialization.json.Json
import kotlin.test.Test
import kotlin.test.assertEquals

class ExportManifestTest {

    private val json = Json { ignoreUnknownKeys = true }

    @Test
    fun `serialize and deserialize manifest round-trip`() {
        val songs = listOf(
            ExportSongItem(
                songId = "123|netease",
                fileName = "song_artist_a1b2c3d4.mp3",
                songName = "测试歌曲",
                artist = "测试歌手",
                platform = "netease",
                audioUrl = "https://example.com/audio.mp3",
                fileSize = 1024000L,
                coverUrl = null,
                lyrics = "[00:01.00]测试歌词",
                downloadTime = 1718123456789L,
                realName = "真实歌名",
                realArtist = "真实歌手",
            )
        )
        val manifest = ExportManifest(
            version = 1,
            exportedAt = 1718123456789L,
            appVersion = "1.0.0",
            songs = songs,
        )

        val encoded = json.encodeToString(ExportManifest.serializer(), manifest)
        val decoded = json.decodeFromString(ExportManifest.serializer(), encoded)

        assertEquals(manifest, decoded)
    }

    @Test
    fun `deserialize manifest v1 with unknown future fields should not fail`() {
        val jsonString = """
            {"version":1,"exportedAt":0,"songs":[],"futureField":"ignored"}
        """.trimIndent()
        val decoded = json.decodeFromString(ExportManifest.serializer(), jsonString)
        assertEquals(1, decoded.version)
        assertEquals(0, decoded.songs.size)
    }

    @Test
    fun `serialize and deserialize manifest with empty song list`() {
        val manifest = ExportManifest(
            version = 1,
            exportedAt = 0L,
            songs = emptyList(),
        )
        val encoded = json.encodeToString(ExportManifest.serializer(), manifest)
        val decoded = json.decodeFromString(ExportManifest.serializer(), encoded)
        assertEquals(manifest, decoded)
    }
}
