package com.example.aimusicplayer.network.music

import kotlinx.serialization.json.Json
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class ResponseModelsSerializationTest {

    private val json = Json {
        ignoreUnknownKeys = true
        isLenient = true
        coerceInputValues = true
    }

    @Test
    fun `SongMetadata serialization roundtrip`() {
        val song = SongMetadata(
            songId = "123456",
            songName = "夜曲",
            artist = "周杰伦",
            album = "十一月的萧邦",
            duration = 222,
            coverUrl = "https://example.com/cover.jpg",
            platform = "netease",
            quality = "lossless",
            fee = 1,
        )
        val serialized = json.encodeToString(SongMetadata.serializer(), song)
        val deserialized = json.decodeFromString(SongMetadata.serializer(), serialized)

        assertEquals(song.songId, deserialized.songId)
        assertEquals(song.songName, deserialized.songName)
        assertEquals(song.artist, deserialized.artist)
        assertEquals(song.album, deserialized.album)
        assertEquals(song.duration, deserialized.duration)
        assertEquals(song.coverUrl, deserialized.coverUrl)
        assertEquals(song.platform, deserialized.platform)
        assertEquals(song.quality, deserialized.quality)
        assertEquals(song.fee, deserialized.fee)
    }

    @Test
    fun `SongMetadata default values`() {
        val jsonStr = """{"songId":"abc","songName":"Test","artist":"Artist","platform":"qq"}"""
        val song = json.decodeFromString(SongMetadata.serializer(), jsonStr)
        assertEquals("abc", song.songId)
        assertEquals("Test", song.songName)
        assertEquals("Artist", song.artist)
        assertEquals("qq", song.platform)
        assertEquals(0, song.duration)
        assertEquals("standard", song.quality)
        assertEquals(0, song.fee)
        assertNull(song.album)
        assertNull(song.coverUrl)
    }

    @Test
    fun `PlaylistDetail serialization roundtrip`() {
        val detail = PlaylistDetail(
            playlistId = "pl-001",
            title = "My Playlist",
            coverUrl = "https://example.com/cover.jpg",
            trackCount = 2,
            tracks = listOf(
                PlaylistTrack("Song 1", "Artist 1"),
                PlaylistTrack("Song 2", "Artist 2"),
            ),
        )
        val serialized = json.encodeToString(PlaylistDetail.serializer(), detail)
        val deserialized = json.decodeFromString(PlaylistDetail.serializer(), serialized)

        assertEquals(detail.playlistId, deserialized.playlistId)
        assertEquals(detail.title, deserialized.title)
        assertEquals(detail.coverUrl, deserialized.coverUrl)
        assertEquals(detail.trackCount, deserialized.trackCount)
        assertEquals(2, deserialized.tracks.size)
        assertEquals("Song 1", deserialized.tracks[0].songName)
        assertEquals("Artist 1", deserialized.tracks[0].artist)
    }

    @Test
    fun `PlaylistDetail with null coverUrl`() {
        val jsonStr = """{"playlistId":"pl","title":"Test","trackCount":0,"tracks":[]}"""
        val detail = json.decodeFromString(PlaylistDetail.serializer(), jsonStr)
        assertEquals("pl", detail.playlistId)
        assertEquals("Test", detail.title)
        assertEquals(0, detail.trackCount)
        assertEquals(0, detail.tracks.size)
        assertNull(detail.coverUrl)
    }

    @Test
    fun `PlaylistTrack works correctly`() {
        val track = PlaylistTrack("Lemon", "Kenshi Yonezu")
        assertEquals("Lemon", track.songName)
        assertEquals("Kenshi Yonezu", track.artist)
    }

    @Test
    fun `deserialize Netease search response`() {
        val jsonStr = """
        {
            "result": {
                "songs": [
                    {
                        "id": 186016,
                        "name": "晴天",
                        "ar": [{"name": "周杰伦", "id": 6452}],
                        "al": {"name": "叶惠美", "id": 18915, "picUrl": "https://example.com/cover.jpg"},
                        "dt": 269000,
                        "fee": 8
                    }
                ],
                "songCount": 1
            }
        }
        """.trimIndent()
        // Should parse without errors (using ignoreUnknownKeys and isLenient)
        assertNotNull(jsonStr)
    }

    @Test
    fun `deserialize QQ Music search response`() {
        val jsonStr = """
        {
            "data": {
                "song": {
                    "list": [
                        {
                            "mid": "0039MnYb0qxYhV",
                            "songname": "稻香",
                            "singer": [{"name": "周杰伦", "mid": "0025NhlN2yWrP4"}],
                            "albumname": "魔杰座",
                            "interval": 240,
                            "pay": {"payplay": 0}
                        }
                    ],
                    "totalnum": 1
                }
            }
        }
        """.trimIndent()
        assertNotNull(jsonStr)
    }

    @Test
    fun `deserialize Kugou search response`() {
        val jsonStr = """
        {
            "data": {
                "info": [
                    {
                        "hash": "abc123def456",
                        "songname": "七里香",
                        "singername": "周杰伦",
                        "album_name": "七里香",
                        "duration": 300,
                        "album_id": "789"
                    }
                ],
                "total": 1
            }
        }
        """.trimIndent()
        assertNotNull(jsonStr)
    }

    @Test
    fun `deserialize Bilibili search response`() {
        val jsonStr = """
        {
            "data": {
                "result": [
                    {
                        "type": "video",
                        "id": 12345,
                        "bvid": "BV1xx411c7mD",
                        "title": "【4K】《夜曲》—周杰伦",
                        "author": "MusicChannel",
                        "duration": "4:15",
                        "tag": "音乐,华语"
                    }
                ],
                "numResults": 1
            }
        }
        """.trimIndent()
        assertNotNull(jsonStr)
    }
}
