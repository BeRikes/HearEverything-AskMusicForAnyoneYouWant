package com.example.aimusicplayer.network.music.crypto

import java.math.BigInteger
import java.security.SecureRandom
import java.util.Base64
import javax.crypto.Cipher
import javax.crypto.spec.IvParameterSpec
import javax.crypto.spec.SecretKeySpec

actual object CryptoProvider {

    actual fun aesEncryptCbc(plaintext: ByteArray, key: ByteArray, iv: ByteArray): ByteArray {
        val cipher = Cipher.getInstance("AES/CBC/PKCS5Padding")
        val keySpec = SecretKeySpec(key, "AES")
        val ivSpec = IvParameterSpec(iv)
        cipher.init(Cipher.ENCRYPT_MODE, keySpec, ivSpec)
        return cipher.doFinal(plaintext)
    }

    actual fun modPowHex(baseHex: String, exponentHex: String, modulusHex: String): String {
        val base = BigInteger(baseHex, 16)
        val exponent = BigInteger(exponentHex, 16)
        val modulus = BigInteger(modulusHex, 16)
        val result = base.modPow(exponent, modulus)
        return result.toString(16).padStart(256, '0')
    }

    actual fun randomAlphaNumeric(length: Int): String {
        val chars = "abcdefghijklmnopqrstuvwxyzABCDEFGHIJKLMNOPQRSTUVWXYZ0123456789"
        val random = SecureRandom()
        return buildString(length) {
            repeat(length) { append(chars[random.nextInt(chars.length)]) }
        }
    }

    actual fun base64Encode(data: ByteArray): String =
        Base64.getEncoder().encodeToString(data)

    actual fun hexEncode(data: ByteArray): String =
        data.joinToString("") { "%02x".format(it) }

    actual fun hexDecode(hex: String): ByteArray {
        require(hex.length % 2 == 0) { "Hex string must have even length" }
        return ByteArray(hex.length / 2) { i ->
            hex.substring(i * 2, i * 2 + 2).toInt(16).toByte()
        }
    }
}
