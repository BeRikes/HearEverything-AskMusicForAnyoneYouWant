package com.example.aimusicplayer.network.music.crypto

/**
 * Platform-specific cryptographic primitives for music API encryption.
 *
 * Implemented via expect/actual:
 * - Android: javax.crypto + java.math.BigInteger + java.util.Base64
 * - iOS:     CryptoKit + custom BigInt + Foundation NSData
 */
expect object CryptoProvider {

    /**
     * AES-128-CBC encryption with PKCS7 padding.
     *
     * @param plaintext Raw bytes to encrypt
     * @param key 16-byte AES key (ASCII-encoded, e.g. "0CoJUm6Qyw8W8jud")
     * @param iv  16-byte initialization vector (ASCII-encoded, e.g. "0102030405060708")
     * @return Encrypted ciphertext bytes
     */
    fun aesEncryptCbc(plaintext: ByteArray, key: ByteArray, iv: ByteArray): ByteArray

    /**
     * Modular exponentiation for RSA: baseHex^exponentHex mod modulusHex.
     *
     * All parameters are unsigned big-endian hex strings. Returns a hex string
     * zero-padded to max(256, result length).
     */
    fun modPowHex(baseHex: String, exponentHex: String, modulusHex: String): String

    /**
     * Cryptographically secure random alphanumeric string [a-zA-Z0-9].
     */
    fun randomAlphaNumeric(length: Int): String

    /** Standard Base64 encode (no line breaks, no padding omitted). */
    fun base64Encode(data: ByteArray): String

    /** Hex encode bytes → lowercase hex string. */
    fun hexEncode(data: ByteArray): String

    /** Hex decode string → bytes. */
    fun hexDecode(hex: String): ByteArray
}
