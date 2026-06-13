package com.example.aimusicplayer.network.music.crypto

/**
 * NetEase Cloud Music API parameter encryption.
 *
 * Produces the `params` and `encSecKey` fields required by all /weapi/ endpoints.
 *
 * ## Algorithm
 * 1. Generate a random 16-char alphanumeric string → secondKey
 * 2. params = AES_CBC(json_text, FIRST_KEY, IV) → Base64
 * 3. params = AES_CBC(step2, secondKey, IV) → Base64
 * 4. encSecKey = RSA(secondKey.reversed().toHex()) with fixed public key
 *
 * ## Fixed Keys (extracted from music.163.com JS via reverse engineering)
 * - FIRST_KEY: "0CoJUm6Qyw8W8jud"
 * - IV:        "0102030405060708"
 * - RSA exponent: 65537 (0x010001)
 * - RSA modulus: 2048-bit value hardcoded below
 */
object NeteaseEncryptor {

    private const val FIRST_KEY = "0CoJUm6Qyw8W8jud"
    private const val IV = "0102030405060708"
    private const val RSA_EXPONENT = "010001"
    private const val RSA_MODULUS =
        "00e0b509f6259df8642dbc35662901477df22677ec152b5ff68ace615bb7b7" +
        "25152b3ab17a876aea8a5aa76d2e417629ec4ee341f56135fccf695280104e" +
        "0312ecbda92557c93870114af6c9d05c4f7f0c3685b7a46bee255932575cc" +
        "e10b424d813cfe4875d3e82047b97ddef52741d546b8e289dc6935b3ece04" +
        "62db0a22b8e7"

    /**
     * Result of encryption for a single request.
     *
     * @property params Double-AES-encrypted JSON body (Base64)
     * @property encSecKey RSA-encrypted random key (256-char hex)
     */
    data class EncryptedParams(
        val params: String,
        val encSecKey: String,
    )

    /**
     * Encrypt a plain JSON string for a /weapi/ POST request.
     *
     * @param jsonText The JSON request body (e.g. `{"s":"晴天","type":"1","limit":"5",...}`)
     * @return [EncryptedParams] containing the `params` and `encSecKey` values
     */
    fun encrypt(jsonText: String): EncryptedParams {
        val secondKey = CryptoProvider.randomAlphaNumeric(16)
        return encryptWithKey(jsonText, secondKey)
    }

    /**
     * Encrypt with a specific [secondKey] for debugging.
     * Allows comparing output with reference implementations using the same key.
     */
    internal fun encryptWithKey(jsonText: String, secondKey: String): EncryptedParams {
        val firstKeyBytes = FIRST_KEY.encodeToByteArray()
        val ivBytes = IV.encodeToByteArray()
        val jsonBytes = jsonText.encodeToByteArray()
        val secondKeyBytes = secondKey.encodeToByteArray()

        // Step 1: First AES (fixed key)
        val firstPass = CryptoProvider.aesEncryptCbc(jsonBytes, firstKeyBytes, ivBytes)
        val firstBase64 = CryptoProvider.base64Encode(firstPass)

        // Step 2: Second AES (random key) — encrypt first Base64 string's UTF-8 bytes
        val secondPass = CryptoProvider.aesEncryptCbc(
            firstBase64.encodeToByteArray(),
            secondKeyBytes,
            ivBytes,
        )
        val params = CryptoProvider.base64Encode(secondPass)

        // Step 3: RSA encrypt reversed key
        val reversedKey = secondKey.reversed()
        val reversedHex = CryptoProvider.hexEncode(reversedKey.encodeToByteArray())
        val encSecKey = CryptoProvider.modPowHex(reversedHex, RSA_EXPONENT, RSA_MODULUS)

        return EncryptedParams(params = params, encSecKey = encSecKey)
    }
}
