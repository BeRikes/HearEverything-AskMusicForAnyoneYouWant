package com.example.aimusicplayer.cache

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue

class Md5UtilsTest {

    @Test
    fun `md5 of empty string`() {
        val hash = Md5Utils.md5("")
        assertEquals("d41d8cd98f00b204e9800998ecf8427e", hash)
    }

    @Test
    fun `md5 of known string`() {
        // "abc" MD5 is well-known: 900150983cd24fb0d6963f7d28e17f72
        val hash = Md5Utils.md5("abc")
        assertEquals("900150983cd24fb0d6963f7d28e17f72", hash)
    }

    @Test
    fun `md5 of Chinese characters`() {
        // "夜曲" (Nocturne — Jay Chou song)
        val hash = Md5Utils.md5("夜曲")
        assertEquals(32, hash.length)
        assertTrue(hash.all { it in '0'..'9' || it in 'a'..'f' })
    }

    @Test
    fun `md5 output is always 32 lowercase hex chars`() {
        val inputs = listOf("", "a", "hello world", "夜曲", "周杰伦", "1234567890")
        for (input in inputs) {
            val hash = Md5Utils.md5(input)
            assertEquals(32, hash.length, "Hash length for '$input' should be 32")
            assertTrue(
                hash.all { it in '0'..'9' || it in 'a'..'f' },
                "Hash for '$input' should be lowercase hex: $hash"
            )
        }
    }

    @Test
    fun `md5 is deterministic`() {
        val input = "test-deterministic-123"
        val hash1 = Md5Utils.md5(input)
        val hash2 = Md5Utils.md5(input)
        assertEquals(hash1, hash2)
    }

    @Test
    fun `md5 different inputs produce different hashes`() {
        val hash1 = Md5Utils.md5("hello")
        val hash2 = Md5Utils.md5("Hello")
        assertNotEquals(hash1, hash2)
    }

    @Test
    fun `generateSongId produces deterministic result`() {
        val id1 = Md5Utils.generateSongId("夜曲", "周杰伦", "netease")
        val id2 = Md5Utils.generateSongId("夜曲", "周杰伦", "netease")
        assertEquals(id1, id2)
    }

    @Test
    fun `generateSongId with null artist`() {
        val id = Md5Utils.generateSongId("test song", null, "netease")
        // Should not crash and should produce a valid hash
        assertEquals(32, id.length)
    }

    @Test
    fun `generateSongId produces different IDs for different platforms`() {
        val id1 = Md5Utils.generateSongId("夜曲", "周杰伦", "netease")
        val id2 = Md5Utils.generateSongId("夜曲", "周杰伦", "qq")
        val id3 = Md5Utils.generateSongId("夜曲", "周杰伦", "kugou")
        // All three should be different
        assertNotEquals(id1, id2)
        assertNotEquals(id2, id3)
        assertNotEquals(id1, id3)
    }

    @Test
    fun `generateSongId different songs produce different IDs`() {
        val id1 = Md5Utils.generateSongId("夜曲", "周杰伦", "netease")
        val id2 = Md5Utils.generateSongId("稻香", "周杰伦", "netease")
        assertNotEquals(id1, id2)
    }

    @Test
    fun `md5 longer strings`() {
        val longString = "a".repeat(1000)
        val hash = Md5Utils.md5(longString)
        assertEquals(32, hash.length)
        assertTrue(hash.all { it in '0'..'9' || it in 'a'..'f' })
    }

    @Test
    fun `md5 unicode special chars`() {
        val hash = Md5Utils.md5("🎵🎶 music notes")
        assertEquals(32, hash.length)
    }
}
