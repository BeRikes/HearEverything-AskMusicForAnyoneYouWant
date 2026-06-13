package com.example.aimusicplayer.cache

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class CacheRepositoryImplTest {

    @Test
    fun `createSongEntity sets correct fields`() {
        val entity = CacheRepositoryImpl.createSongEntity(
            id = "test-id-001",
            songName = "夜曲",
            artist = "周杰伦",
            platform = "netease",
            audioUrl = "https://example.com/audio.mp3",
            coverUrl = "https://example.com/cover.jpg",
            lyrics = "[00:00]test",
            quality = "lossless",
        )

        assertEquals("test-id-001", entity.id)
        assertEquals("夜曲", entity.songName)
        assertEquals("周杰伦", entity.artist)
        assertEquals("netease", entity.platform)
        assertEquals("https://example.com/audio.mp3", entity.audioUrl)
        assertEquals("https://example.com/cover.jpg", entity.coverUrl)
        assertEquals("[00:00]test", entity.lyrics)
        assertEquals("lossless", entity.quality)

        // Timestamps should be reasonable
        val now = kotlinx.datetime.Clock.System.now().epochSeconds
        assertTrue(entity.createTime in (now - 5)..(now + 5))
        assertTrue(entity.expireTime > entity.createTime)
    }

    @Test
    fun `createSongEntity with default values`() {
        val entity = CacheRepositoryImpl.createSongEntity(
            id = "test-default",
            songName = "Song",
            artist = null,
            platform = "qq",
            audioUrl = "url",
        )

        assertEquals("standard", entity.quality)
        assertEquals(null, entity.coverUrl)
        assertEquals(null, entity.lyrics)
        assertEquals(null, entity.artist)
    }

    @Test
    fun `createSongEntity with custom TTL`() {
        val entity = CacheRepositoryImpl.createSongEntity(
            id = "test-ttl",
            songName = "Song",
            artist = "Artist",
            platform = "kugou",
            audioUrl = "url",
            ttlSeconds = 3600, // 1 hour
        )

        val now = kotlinx.datetime.Clock.System.now().epochSeconds
        val expectedExpire = now + 3600
        assertTrue(entity.expireTime in (expectedExpire - 5)..(expectedExpire + 5))
    }

    @Test
    fun `unwrap QueryResult Value`() {
        val testValue = "test-result"
        val queryResult = app.cash.sqldelight.db.QueryResult.Value(testValue)
        val unwrapped = queryResult.unwrap()
        assertEquals(testValue, unwrapped)
    }

    @Test
    fun `currentTimeSeconds returns reasonable value`() {
        val now = currentTimeSeconds()
        val systemNow = kotlinx.datetime.Clock.System.now().epochSeconds
        assertTrue(now in (systemNow - 5)..(systemNow + 5))
    }

    @Test
    fun `createSongEntity expireTime always after createTime`() {
        val entity = CacheRepositoryImpl.createSongEntity(
            id = "timing-test",
            songName = "Test",
            artist = null,
            platform = "bilibili",
            audioUrl = "url",
        )
        assertTrue(entity.expireTime > entity.createTime)
    }
}
