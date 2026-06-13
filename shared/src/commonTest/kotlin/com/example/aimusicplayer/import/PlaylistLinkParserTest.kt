package com.example.aimusicplayer.import

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class PlaylistLinkParserTest {

    @Test
    fun `parse NetEase standard URL`() {
        val result = PlaylistLinkParser.parse("https://music.163.com/playlist?id=123456")
        assertNotNull(result)
        assertEquals("netease", result.platform)
        assertEquals("123456", result.playlistId)
    }

    @Test
    fun `parse NetEase hash URL format`() {
        val result = PlaylistLinkParser.parse("https://music.163.com/#/playlist?id=789012")
        assertNotNull(result)
        assertEquals("netease", result.platform)
        assertEquals("789012", result.playlistId)
    }

    @Test
    fun `parse NetEase mobile URL`() {
        val result = PlaylistLinkParser.parse("https://y.music.163.com/m/playlist?id=345678")
        assertNotNull(result)
        assertEquals("netease", result.platform)
        assertEquals("345678", result.playlistId)
    }

    @Test
    fun `parse NetEase URL with extra query params`() {
        val result = PlaylistLinkParser.parse("https://music.163.com/playlist?id=111&userid=222")
        assertNotNull(result)
        assertEquals("netease", result.platform)
        assertEquals("111", result.playlistId)
    }

    @Test
    fun `parse QQ Music URL`() {
        val result = PlaylistLinkParser.parse(
            "https://i2.y.qq.com/n3/other/pages/details/playlist.html?type=1&id=772368334&hosteuin=oK6kowEAoK4z7eFk"
        )
        assertNotNull(result)
        assertEquals("qq", result.platform)
        assertEquals("772368334", result.playlistId)
    }

    @Test
    fun `parse Kugou URL`() {
        val result = PlaylistLinkParser.parse("https://m.kugou.com/songlist/gcid_3zx4xam7z4z058/")
        assertNotNull(result)
        assertEquals("kugou", result.platform)
        assertEquals("gcid_3zx4xam7z4z058", result.playlistId)
    }

    @Test
    fun `parse Kugou alternative URL`() {
        val result = PlaylistLinkParser.parse("https://www.kugou.com/songlist/gcid_abc123def456/")
        assertNotNull(result)
        assertEquals("kugou", result.platform)
        assertEquals("gcid_abc123def456", result.playlistId)
    }

    @Test
    fun `parse returns null for non-music URL`() {
        val result = PlaylistLinkParser.parse("https://www.google.com/search?q=music")
        assertNull(result)
    }

    @Test
    fun `parse returns null for random text`() {
        val result = PlaylistLinkParser.parse("播放周杰伦的夜曲")
        assertNull(result)
    }

    @Test
    fun `parse returns null for empty string`() {
        val result = PlaylistLinkParser.parse("")
        assertNull(result)
    }

    @Test
    fun `parse URL with leading or trailing whitespace`() {
        val result = PlaylistLinkParser.parse("  https://music.163.com/playlist?id=123456  ")
        assertNotNull(result)
        assertEquals("netease", result.platform)
        assertEquals("123456", result.playlistId)
    }

    @Test
    fun `isMusicLink returns true for Netease URL`() {
        assertTrue(PlaylistLinkParser.isMusicLink("https://music.163.com/playlist?id=123"))
    }

    @Test
    fun `isMusicLink returns true for QQ URL`() {
        assertTrue(PlaylistLinkParser.isMusicLink(
            "https://i2.y.qq.com/n3/other/pages/details/playlist.html?id=123&type=1"
        ))
    }

    @Test
    fun `isMusicLink returns true for Kugou URL`() {
        assertTrue(PlaylistLinkParser.isMusicLink("https://m.kugou.com/songlist/gcid_abc/"))
    }

    @Test
    fun `isMusicLink returns false for random text`() {
        assertFalse(PlaylistLinkParser.isMusicLink("hello world"))
    }

    @Test
    fun `isMusicLink returns false for empty string`() {
        assertFalse(PlaylistLinkParser.isMusicLink(""))
    }

    @Test
    fun `parse URL in mixed text`() {
        // The URL should still be extractable even in longer text
        val text = "请导入这个歌单 https://music.163.com/playlist?id=999888 谢谢"
        assertTrue(PlaylistLinkParser.isMusicLink(text))
        val result = PlaylistLinkParser.parse(text)
        assertNotNull(result)
        assertEquals("netease", result.platform)
        assertEquals("999888", result.playlistId)
    }
}
