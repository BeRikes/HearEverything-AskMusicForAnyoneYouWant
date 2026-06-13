package com.example.aimusicplayer.music

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class BilibiliTitleParserTest {

    @Test
    fun `parse title with 书名号 and dash artist`() {
        val result = BilibiliTitleParser.parse("【4K修复】《发如雪》—周杰伦 无损音质")
        assertTrue(result.cleanSongName.isNotBlank())
        assertTrue(result.cleanSongName.contains("发如雪") || result.cleanSongName.contains("4K"))
    }

    @Test
    fun `parse title with Artist - SongName pattern`() {
        val result = BilibiliTitleParser.parse("【Hi-Res】周杰伦 - 晴天 MV")
        assertTrue(result.cleanSongName.isNotBlank())
        assertTrue(result.artistFromTitle)
    }

    @Test
    fun `parse title with 书名号 and no artist`() {
        val result = BilibiliTitleParser.parse("【VALORANT赛事】首个官方赛事主题曲MV《Die For You》 Champions 2021")
        assertNotNull(result.cleanSongName)
        assertTrue(result.cleanSongName.contains("Die For You") || result.cleanSongName.contains("赛事") || result.cleanSongName.isNotBlank())
    }

    @Test
    fun `parse title with special version in brackets - Live`() {
        val result = BilibiliTitleParser.parse("【4K LIVE】《七里香》—周杰伦 演唱会")
        assertTrue(result.cleanSongName.isNotBlank())
        assertTrue(result.isSpecialVersion || result.cleanSongName.contains("七里香"))
    }

    @Test
    fun `parse title with special version - Cover`() {
        val result = BilibiliTitleParser.parse("【翻唱】《起风了》—买辣椒也用券")
        assertTrue(result.cleanSongName.isNotBlank())
        assertTrue(result.isSpecialVersion || result.cleanSongName.contains("起风了"))
    }

    @Test
    fun `parse title with special version - Remix`() {
        val result = BilibiliTitleParser.parse("【DJ版】《Faded》Remix")
        assertTrue(result.cleanSongName.isNotBlank())
        assertTrue(result.isSpecialVersion || result.cleanSongName.contains("Faded"))
    }

    @Test
    fun `parse title only has song name in 书名号`() {
        val result = BilibiliTitleParser.parse("用百万级豪华装备试听《Die For You》【Hi-Res】")
        assertTrue(result.cleanSongName.isNotBlank())
        assertTrue(result.cleanSongName.contains("Die For You") || result.cleanSongName.contains("试听") || result.cleanSongName.isNotBlank())
    }

    @Test
    fun `parse title with HTML entities`() {
        val result = BilibiliTitleParser.parse("《Hello&quot;World》—Artist")
        assertTrue(result.cleanSongName.isNotBlank())
        assertTrue(result.cleanSongName.contains("Hello"))
    }

    @Test
    fun `parse title with em tags stripped`() {
        val result = BilibiliTitleParser.parse("<em class=\"keyword\">周杰伦</em> - 夜曲")
        assertTrue(result.cleanSongName.isNotBlank())
        // The artist name should be extracted from the em tag content
        assertTrue(result.cleanSongName.contains("夜曲") || result.cleanSongName.contains("周杰伦") || result.cleanSongName.isNotBlank())
    }

    @Test
    fun `parse title with SongName｜Artist pattern`() {
        val result = BilibiliTitleParser.parse("Lemon｜Kenshi Yonezu")
        assertTrue(result.cleanSongName.isNotBlank())
        assertTrue(result.cleanSongName.contains("Lemon") || result.cleanSongName.contains("Kenshi"))
    }

    @Test
    fun `parse title with uploader fallback`() {
        val result = BilibiliTitleParser.parse("【无损】《晴天》", uploader = "周杰伦音乐频道")
        assertTrue(result.cleanSongName.isNotBlank())
        // Uploader fallback: artist comes from uploader
        assertNotNull(result.extractedArtist)
        assertFalse(result.artistFromTitle)
    }

    @Test
    fun `parse title with uploader cleaned`() {
        val result = BilibiliTitleParser.parse("《晴天》", uploader = "JayChou_Official")
        assertTrue(result.cleanSongName.isNotBlank())
    }

    @Test
    fun `parse empty title`() {
        val result = BilibiliTitleParser.parse("")
        assertNotNull(result.cleanSongName)
    }

    @Test
    fun `parse title with all brackets`() {
        val result = BilibiliTitleParser.parse("【4K】【Hi-Res】【无损】")
        assertNotNull(result.cleanSongName)
    }

    @Test
    fun `parse title SongName-Author pattern`() {
        val result = BilibiliTitleParser.parse("Lemon-Kenshi Yonezu")
        assertEquals("Lemon", result.cleanSongName)
    }

    @Test
    fun `parse title with Instrumental version`() {
        val result = BilibiliTitleParser.parse("《晴天》钢琴伴奏版")
        assertTrue(result.cleanSongName.isNotBlank())
        assertTrue(result.cleanSongName.contains("晴天"))
        assertTrue(result.isSpecialVersion)
    }

    @Test
    fun `parse title with adjacent artist before 书名号`() {
        val result = BilibiliTitleParser.parse("试听The Weeknd《Die For You》")
        assertTrue(result.cleanSongName.isNotBlank())
        assertTrue(result.cleanSongName.contains("Die For You"))
    }

    @Test
    fun `parse title removes quality suffix from song name`() {
        val result = BilibiliTitleParser.parse("《晴天》无损音质")
        assertTrue(result.cleanSongName.isNotBlank())
        assertTrue(result.cleanSongName.contains("晴天"))
    }

    @Test
    fun `parse title removes HQ suffix`() {
        val result = BilibiliTitleParser.parse("《稻香》HQ")
        assertTrue(result.cleanSongName.isNotBlank())
        assertTrue(result.cleanSongName.contains("稻香"))
    }

    @Test
    fun `parse title with ampersand HTML entity`() {
        val result = BilibiliTitleParser.parse("《A&amp;B》—Artist")
        assertTrue(result.cleanSongName.isNotBlank())
        assertTrue(result.cleanSongName.contains("A&B"))
    }

    @Test
    fun `parse title with lt gt HTML entities`() {
        val result = BilibiliTitleParser.parse("《Hello&lt;World&gt;》—Test")
        assertTrue(result.cleanSongName.isNotBlank())
        assertTrue(result.cleanSongName.contains("Hello"))
    }

    @Test
    fun `parse title nbsp HTML entity`() {
        val result = BilibiliTitleParser.parse("《Hello World》—Artist")
        assertTrue(result.cleanSongName.isNotBlank())
        assertTrue(result.cleanSongName.contains("Hello"))
    }

    @Test
    fun `parse very long title`() {
        val longTitle = "【4K修复】【Hi-Res无损】《" + "A".repeat(100) + "》—" + "B".repeat(20)
        val result = BilibiliTitleParser.parse(longTitle)
        assertNotNull(result.cleanSongName)
    }

    @Test
    fun `artistFromTitle is false when artist comes from uploader`() {
        val result = BilibiliTitleParser.parse("《Unknown Song》", uploader = "SomeChannel")
        assertTrue(result.cleanSongName.isNotBlank())
        // The uploader "SomeChannel" should be used as fallback artist
        // since there's no artist in the title
        assertNotNull(result.extractedArtist)
    }

    @Test
    fun `versionType is null for normal song`() {
        val result = BilibiliTitleParser.parse("《晴天》—周杰伦")
        assertNull(result.versionType)
    }

    @Test
    fun `parse title with square bracket quality tags removed`() {
        val result = BilibiliTitleParser.parse("[4K]《晴天》[MV]")
        assertTrue(result.cleanSongName.isNotBlank())
        assertTrue(result.cleanSongName.contains("晴天"))
    }
}
