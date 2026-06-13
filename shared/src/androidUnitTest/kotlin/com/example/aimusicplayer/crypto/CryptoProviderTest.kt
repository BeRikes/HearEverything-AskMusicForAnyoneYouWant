package com.example.aimusicplayer.crypto

import com.example.aimusicplayer.network.music.crypto.CryptoProvider
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNotEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class CryptoProviderTest {

    @Test
    fun `aesEncryptCbc produces output`() {
        val plaintext = "Hello, World!".encodeToByteArray()
        val key = ByteArray(16) { it.toByte() }
        val iv = ByteArray(16) { (it * 2).toByte() }

        val encrypted = CryptoProvider.aesEncryptCbc(plaintext, key, iv)
        assertNotNull(encrypted)
        assertTrue(encrypted.isNotEmpty())
        // AES CBC PKCS5Padding output should be at least as long as plaintext
        assertTrue(encrypted.size >= plaintext.size)
    }

    @Test
    fun `aesEncryptCbc produces different output for different keys`() {
        val plaintext = "Test message".encodeToByteArray()
        val key1 = ByteArray(16) { 1.toByte() }
        val key2 = ByteArray(16) { 2.toByte() }
        val iv = ByteArray(16) { 0x42.toByte() }

        val enc1 = CryptoProvider.aesEncryptCbc(plaintext, key1, iv)
        val enc2 = CryptoProvider.aesEncryptCbc(plaintext, key2, iv)
        assertNotEquals(enc1.joinToString(), enc2.joinToString())
    }

    @Test
    fun `aesEncryptCbc produces different output for different IV`() {
        val plaintext = "Test message".encodeToByteArray()
        val key = ByteArray(16) { 0x33.toByte() }
        val iv1 = ByteArray(16) { 0x11.toByte() }
        val iv2 = ByteArray(16) { 0x22.toByte() }

        val enc1 = CryptoProvider.aesEncryptCbc(plaintext, key, iv1)
        val enc2 = CryptoProvider.aesEncryptCbc(plaintext, key, iv2)
        assertNotEquals(enc1.joinToString(), enc2.joinToString())
    }

    @Test
    fun `modPowHex with known values`() {
        // Test: 2^3 mod 5 = 8 mod 5 = 3
        val result = CryptoProvider.modPowHex("2", "3", "5")
        assertEquals("3", result.trimStart('0'))
    }

    @Test
    fun `modPowHex result string length`() {
        // Large modulus should produce 256-char padded hex
        val modulus256 = "f" + "0".repeat(255)
        val result = CryptoProvider.modPowHex("a", "b", modulus256)
        // padStart(256, '0') means result should be exactly 256 chars
        assertEquals(256, result.length)
    }

    @Test
    fun `randomAlphaNumeric produces correct length`() {
        val result = CryptoProvider.randomAlphaNumeric(16)
        assertEquals(16, result.length)
        assertTrue(result.all { it in "abcdefghijklmnopqrstuvwxyzABCDEFGHIJKLMNOPQRSTUVWXYZ0123456789" })
    }

    @Test
    fun `randomAlphaNumeric of zero length`() {
        val result = CryptoProvider.randomAlphaNumeric(0)
        assertEquals("", result)
    }

    @Test
    fun `randomAlphaNumeric different calls produce different results`() {
        val r1 = CryptoProvider.randomAlphaNumeric(32)
        val r2 = CryptoProvider.randomAlphaNumeric(32)
        assertNotEquals(r1, r2)
    }

    @Test
    fun `base64Encode known value`() {
        val data = "Hello".encodeToByteArray()
        val encoded = CryptoProvider.base64Encode(data)
        assertEquals("SGVsbG8=", encoded)
    }

    @Test
    fun `base64Encode empty`() {
        val data = ByteArray(0)
        val encoded = CryptoProvider.base64Encode(data)
        assertEquals("", encoded)
    }

    @Test
    fun `hexEncode known value`() {
        val data = byteArrayOf(0x1a, 0x2b, 0xff.toByte())
        val hex = CryptoProvider.hexEncode(data)
        assertEquals("1a2bff", hex)
    }

    @Test
    fun `hexEncode empty`() {
        val data = ByteArray(0)
        val hex = CryptoProvider.hexEncode(data)
        assertEquals("", hex)
    }

    @Test
    fun `hexDecode known value`() {
        val data = CryptoProvider.hexDecode("1a2bff")
        assertEquals(3, data.size)
        assertEquals(0x1a.toByte(), data[0])
        assertEquals(0x2b.toByte(), data[1])
        assertEquals(0xff.toByte(), data[2])
    }

    @Test
    fun `hexDecode roundtrip`() {
        val original = byteArrayOf(0x00, 0x7f, 0x80.toByte(), 0xff.toByte())
        val hex = CryptoProvider.hexEncode(original)
        val decoded = CryptoProvider.hexDecode(hex)
        assertEquals(original.joinToString(), decoded.joinToString())
    }

    @Test
    fun `hexDecode odd length throws`() {
        assertFailsWith<IllegalArgumentException> {
            CryptoProvider.hexDecode("abc")
        }
    }

    @Test
    fun `hexDecode empty`() {
        val data = CryptoProvider.hexDecode("")
        assertEquals(0, data.size)
    }
}
