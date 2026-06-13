package com.example.aimusicplayer.network.music

import com.example.aimusicplayer.network.music.crypto.MusicSignUtils
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue

class MusicSignUtilsTest {

    @Test
    fun `qqSign produces deterministic result`() {
        val params = mapOf("a" to "1", "b" to "2")
        val sign1 = MusicSignUtils.qqSign(params)
        val sign2 = MusicSignUtils.qqSign(params)
        assertEquals(sign1, sign2)
    }

    @Test
    fun `qqSign sorts params alphabetically`() {
        // Same params in different order should produce same signature
        val sign1 = MusicSignUtils.qqSign(mapOf("b" to "2", "a" to "1"))
        val sign2 = MusicSignUtils.qqSign(mapOf("a" to "1", "b" to "2"))
        assertEquals(sign1, sign2)
    }

    @Test
    fun `qqSign produces uppercase hex`() {
        val params = mapOf("key" to "value")
        val sign = MusicSignUtils.qqSign(params)
        assertEquals(32, sign.length)
        assertTrue(sign.all { it in '0'..'9' || it in 'A'..'F' })
    }

    @Test
    fun `qqSign different params produce different results`() {
        val sign1 = MusicSignUtils.qqSign(mapOf("a" to "1"))
        val sign2 = MusicSignUtils.qqSign(mapOf("a" to "2"))
        assertNotEquals(sign1, sign2)
    }

    @Test
    fun `kugouSign produces deterministic result`() {
        val params = mapOf("a" to "1", "b" to "2")
        val sign1 = MusicSignUtils.kugouSign(params)
        val sign2 = MusicSignUtils.kugouSign(params)
        assertEquals(sign1, sign2)
    }

    @Test
    fun `kugouSign sorts params alphabetically`() {
        val sign1 = MusicSignUtils.kugouSign(mapOf("b" to "2", "a" to "1"))
        val sign2 = MusicSignUtils.kugouSign(mapOf("a" to "1", "b" to "2"))
        assertEquals(sign1, sign2)
    }

    @Test
    fun `kugouSign produces uppercase hex`() {
        val params = mapOf("key" to "value")
        val sign = MusicSignUtils.kugouSign(params)
        assertEquals(32, sign.length)
        assertTrue(sign.all { it in '0'..'9' || it in 'A'..'F' })
    }

    @Test
    fun `kugouSign different from qqSign for same params`() {
        val params = mapOf("a" to "1")
        val qqSign = MusicSignUtils.qqSign(params)
        val kugouSign = MusicSignUtils.kugouSign(params)
        // QQ adds CJBPACrRuNy7 before hashing, Kugou doesn't
        assertNotEquals(qqSign, kugouSign)
    }

    @Test
    fun `getKugouDfid returns 32-char hex string`() {
        val dfid = MusicSignUtils.getKugouDfid()
        assertEquals(32, dfid.length)
        assertTrue(dfid.all { it in '0'..'9' || it in 'a'..'f' })
    }

    @Test
    fun `getKugouDfid is idempotent`() {
        val dfid1 = MusicSignUtils.getKugouDfid()
        val dfid2 = MusicSignUtils.getKugouDfid()
        assertEquals(dfid1, dfid2)
    }

    @Test
    fun `qqSign with empty params`() {
        val sign = MusicSignUtils.qqSign(emptyMap())
        assertEquals(32, sign.length)
    }

    @Test
    fun `kugouSign with empty params`() {
        val sign = MusicSignUtils.kugouSign(emptyMap())
        assertEquals(32, sign.length)
    }

    @Test
    fun `qqSign with special characters in params`() {
        val params = mapOf("key" to "value=with&special")
        val sign = MusicSignUtils.qqSign(params)
        assertEquals(32, sign.length)
    }
}
