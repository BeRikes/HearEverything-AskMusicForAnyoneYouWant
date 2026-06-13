package com.example.aimusicplayer.cache

import kotlinx.serialization.json.Json
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull

class SongCacheEntityTest {

    private val json = Json { ignoreUnknownKeys = true }

    @Test
    fun `SongCacheEntity serialization roundtrip`() {
        val entity = SongCacheEntity(
            id = "abc123",
            songName = "夜曲",
            artist = "周杰伦",
            platform = "netease",
            audioUrl = "https://example.com/audio.mp3",
            coverUrl = "https://example.com/cover.jpg",
            lyrics = "[00:00]Test lyrics",
            quality = "lossless",
            expireTime = 1700000000L,
            createTime = 1699999900L,
        )
        val serialized = json.encodeToString(SongCacheEntity.serializer(), entity)
        val deserialized = json.decodeFromString(SongCacheEntity.serializer(), serialized)
        assertEquals(entity.id, deserialized.id)
        assertEquals(entity.songName, deserialized.songName)
        assertEquals(entity.artist, deserialized.artist)
        assertEquals(entity.platform, deserialized.platform)
        assertEquals(entity.audioUrl, deserialized.audioUrl)
        assertEquals(entity.coverUrl, deserialized.coverUrl)
        assertEquals(entity.lyrics, deserialized.lyrics)
        assertEquals(entity.quality, deserialized.quality)
        assertEquals(entity.expireTime, deserialized.expireTime)
        assertEquals(entity.createTime, deserialized.createTime)
    }

    @Test
    fun `SongCacheEntity with null artist`() {
        val entity = SongCacheEntity(
            id = "test",
            songName = "Instrumental",
            artist = null,
            platform = "bilibili",
            audioUrl = "https://example.com/audio.mp3",
            expireTime = 1700000000L,
            createTime = 1699999900L,
        )
        val serialized = json.encodeToString(SongCacheEntity.serializer(), entity)
        val deserialized = json.decodeFromString(SongCacheEntity.serializer(), serialized)
        assertEquals(null, deserialized.artist)
    }

    @Test
    fun `SongCacheEntity with null cover and lyrics`() {
        val entity = SongCacheEntity(
            id = "test",
            songName = "No Cover",
            artist = "Artist",
            platform = "qq",
            audioUrl = "https://example.com/audio.mp3",
            coverUrl = null,
            lyrics = null,
            expireTime = 1700000000L,
            createTime = 1699999900L,
        )
        val serialized = json.encodeToString(SongCacheEntity.serializer(), entity)
        val deserialized = json.decodeFromString(SongCacheEntity.serializer(), serialized)
        assertEquals(null, deserialized.coverUrl)
        assertEquals(null, deserialized.lyrics)
    }

    @Test
    fun `SongCacheEntity default quality`() {
        val jsonStr = """{"id":"test","songName":"Song","platform":"netease","audioUrl":"https://example.com/audio.mp3","expireTime":1700000000,"createTime":1699999900}"""
        val entity = json.decodeFromString(SongCacheEntity.serializer(), jsonStr)
        assertEquals("standard", entity.quality)
    }

    @Test
    fun `FailedLookup data class works`() {
        val failed = FailedLookup(id = "lookup-1", expireTime = 1700000000L)
        assertEquals("lookup-1", failed.id)
        assertEquals(1700000000L, failed.expireTime)
    }

    @Test
    fun `SongCacheEntity equality`() {
        val e1 = SongCacheEntity(
            id = "abc", songName = "Song", platform = "netease",
            audioUrl = "url", expireTime = 100L, createTime = 99L
        )
        val e2 = e1.copy()
        assertEquals(e1, e2)
    }

    @Test
    fun `SongCacheEntity copy produces different instance`() {
        val e1 = SongCacheEntity(
            id = "abc", songName = "Song", platform = "netease",
            audioUrl = "url", expireTime = 100L, createTime = 99L
        )
        val e2 = e1.copy(songName = "Different")
        assertEquals(e1.id, e2.id)
        assertEquals("Different", e2.songName)
    }
}
