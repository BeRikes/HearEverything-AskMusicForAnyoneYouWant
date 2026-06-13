# Music API Anti-Crawl Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Implement AES+RSA encryption for NetEase, MD5 signing for QQ Music and Kugou Music APIs, using Kotlin Multiplatform expect/actual for platform-specific crypto primitives.

**Architecture:** A `CryptoProvider` expect/actual object provides AES-CBC, BigInt modPow, Base64, Hex, and random generation. `NeteaseEncryptor` uses it for the double-AES + RSA encryption flow. `MusicSignUtils` wraps the existing `Md5Utils` for QQ/Kugou parameter signing. New files go under `network/music/crypto/`; existing `NeteaseApi`, `QQMusicApi`, `KugouApi` are modified in-place.

**Tech Stack:** Kotlin Multiplatform, Ktor HTTP client, javax.crypto (Android), CryptoKit (iOS), pure Kotlin BigInt for iOS

---

## File Map

| File | Action | Responsibility |
|------|--------|---------------|
| `network/music/crypto/CryptoProvider.kt` | CREATE | expect declarations for AES, BigInt modPow, Base64, Hex, random |
| `network/music/crypto/CryptoProvider.android.kt` | CREATE | Android actual: javax.crypto, java.math.BigInteger, java.util.Base64 |
| `network/music/crypto/CryptoProvider.ios.kt` | CREATE | iOS actual: CryptoKit AES, custom BigInt modPow, Foundation Base64 |
| `network/music/crypto/NeteaseEncryptor.kt` | CREATE | Double AES + RSA encryption, produces `params` + `encSecKey` |
| `network/music/crypto/MusicSignUtils.kt` | CREATE | QQ Music `sign` param, Kugou `signature` param generation |
| `network/music/NeteaseApi.kt` | MODIFY | Rewrite: /weapi/* endpoints, encrypted POST body |
| `network/music/QQMusicApi.kt` | MODIFY | Add `sign` parameter to search and play-URL requests |
| `network/music/KugouApi.kt` | MODIFY | Add `signature` + `dfid` cookie to search and play-URL requests |
| `network/music/MusicPlatformApi.kt` | UNCHANGED | Data classes and interface remain the same |

---

### Task 1: Create CryptoProvider expect declaration (common)

**Files:**
- Create: `shared/src/commonMain/kotlin/com/example/aimusicplayer/network/music/crypto/CryptoProvider.kt`

- [ ] **Step 1: Write the expect object**

```kotlin
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
```

- [ ] **Step 2: Commit**

```bash
git add shared/src/commonMain/kotlin/com/example/aimusicplayer/network/music/crypto/CryptoProvider.kt
git commit -m "feat: add CryptoProvider expect declaration for music API crypto"
```

---

### Task 2: Create CryptoProvider actual (Android)

**Files:**
- Create: `shared/src/androidMain/kotlin/com/example/aimusicplayer/network/music/crypto/CryptoProvider.android.kt`

- [ ] **Step 1: Write the Android actual implementation**

```kotlin
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
```

- [ ] **Step 2: Commit**

```bash
git add shared/src/androidMain/kotlin/com/example/aimusicplayer/network/music/crypto/CryptoProvider.android.kt
git commit -m "feat: add Android CryptoProvider actual using javax.crypto and BigInteger"
```

---

### Task 3: Create CryptoProvider actual (iOS)

**Files:**
- Create: `shared/src/iosMain/kotlin/com/example/aimusicplayer/network/music/crypto/CryptoProvider.ios.kt`

- [ ] **Step 1: Write the iOS actual — first the imports and BigInt helper**

The iOS actual needs a pure-Kotlin BigInt implementation because iOS doesn't have `java.math.BigInteger`. The exponent for NetEase RSA is always 65537 (0x10001), which requires ~17 modular multiplications via square-and-multiply.

```kotlin
package com.example.aimusicplayer.network.music.crypto

import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.addressOf
import kotlinx.cinterop.usePinned
import platform.CommonCrypto.CC_SHA256
import platform.CoreCrypto.CCKeyDerivationPBKDF
import platform.Foundation.NSData
import platform.Foundation.NSDataBase64EncodingEndLineWithCarriageReturn
import platform.Foundation.NSDataBase64EncodingEndLineWithLineFeed
import platform.Foundation.create
import platform.Security.SecRandomCopyBytes
import platform.darwin.kSecRandomDefault
import platform.posix.uint32_tVar
import kotlin.experimental.and

actual object CryptoProvider {

    actual fun aesEncryptCbc(plaintext: ByteArray, key: ByteArray, iv: ByteArray): ByteArray {
        // AES-128-CBC with PKCS7 padding — implemented via CommonCrypto
        return aesCbcEncryptCommonCrypto(plaintext, key, iv)
    }

    actual fun modPowHex(baseHex: String, exponentHex: String, modulusHex: String): String {
        val base = BigInt.fromHex(baseHex)
        val exponent = BigInt.fromHex(exponentHex)
        val modulus = BigInt.fromHex(modulusHex)
        val result = base.modPow(exponent, modulus)
        return result.toHex().padStart(256, '0')
    }

    actual fun randomAlphaNumeric(length: Int): String {
        val chars = "abcdefghijklmnopqrstuvwxyzABCDEFGHIJKLMNOPQRSTUVWXYZ0123456789"
        val bytes = ByteArray(length)
        SecRandomCopyBytes(kSecRandomDefault, bytes.size.toULong(), bytes.refTo(0))
        return buildString(length) {
            repeat(length) {
                val idx = (bytes[it].toInt() and 0xFF) % chars.length
                append(chars[idx])
            }
        }
    }

    actual fun base64Encode(data: ByteArray): String {
        val nsData = data.toNSData()
        return nsData.base64EncodedStringWithOptions(
            NSDataBase64EncodingEndLineWithCarriageReturn.toULong() or
            NSDataBase64EncodingEndLineWithLineFeed.toULong()
        )
    }

    actual fun hexEncode(data: ByteArray): String =
        data.joinToString("") { "%02x".format(it) }

    actual fun hexDecode(hex: String): ByteArray {
        require(hex.length % 2 == 0) { "Hex string must have even length" }
        return ByteArray(hex.length / 2) { i ->
            hex.substring(i * 2, i * 2 + 2).toInt(16).toByte()
        }
    }
}
```

- [ ] **Step 2: Append the CommonCrypto AES helper**

```kotlin
// ── CommonCrypto AES-CBC ──────────────────────────────────────────────

@OptIn(ExperimentalForeignApi::class)
private fun aesCbcEncryptCommonCrypto(
    plaintext: ByteArray,
    key: ByteArray,
    iv: ByteArray
): ByteArray {
    // Use CCCrypt from CommonCrypto via cinterop
    val bufferSize = plaintext.size + 16 // room for padding
    val buffer = ByteArray(bufferSize)
    var bytesEncrypted = 0

    plaintext.usePinned { plainPinned ->
        key.usePinned { keyPinned ->
            iv.usePinned { ivPinned ->
                buffer.usePinned { bufPinned ->
                    val status = platform.CommonCrypto.CCCrypt(
                        platform.CommonCrypto.kCCEncrypt,      // op
                        platform.CommonCrypto.kCCAlgorithmAES,  // alg
                        platform.CommonCrypto.kCCOptionPKCS7Padding, // options
                        keyPinned.addressOf(0),                 // key
                        16u,                                    // keyLength (AES-128)
                        ivPinned.addressOf(0),                  // iv
                        plainPinned.addressOf(0),               // dataIn
                        plaintext.size.toULong(),               // dataInLength
                        bufPinned.addressOf(0),                 // dataOut
                        bufferSize.toULong(),                   // dataOutAvailable
                        uint32_tVar(bytesEncrypted.toUInt()).ptr // dataOutMoved
                    )
                    if (status != platform.CommonCrypto.kCCSuccess) {
                        throw RuntimeException("CCCrypt failed with status $status")
                    }
                }
            }
        }
    }
    return buffer.copyOf(bytesEncrypted)
}
```

- [ ] **Step 3: Append the pure-Kotlin BigInt implementation**

Since `modPowHex` uses a tiny exponent (65537), square-and-multiply needs ~17 iterations. We implement BigInt on byte arrays with grade-school multiplication and binary long division.

```kotlin
// ── Pure Kotlin BigInt (little-endian byte array, unsigned) ─────────

private class BigInt private constructor(
    /** Little-endian digits, each 0..255, no trailing zeros (except for zero). */
    private val digits: ByteArray
) {
    val isZero: Boolean get() = digits.size == 1 && digits[0] == 0.toByte()

    companion object {
        /** Parse a big-endian hex string (no 0x prefix). */
        fun fromHex(hex: String): BigInt {
            val str = hex.trimStart('0').ifEmpty { "0" }
            // Parse into big-endian bytes first
            val beBytes = ByteArray((str.length + 1) / 2) { i ->
                val start = str.length - (i + 1) * 2
                val end = str.length - i * 2
                val chunk = str.substring(maxOf(0, start), end)
                chunk.toInt(16).toByte()
            }
            // Convert to little-endian and strip leading zeros
            val le = beBytes.reversedArray()
            val firstNonZero = le.indexOfLast { it != 0.toByte() }
            return if (firstNonZero < 0) BigInt(byteArrayOf(0))
                   else BigInt(le.copyOf(firstNonZero + 1))
        }
    }

    fun toHex(): String {
        val be = digits.reversedArray()
        return be.joinToString("") { "%02x".format(it) }.trimStart('0').ifEmpty { "0" }
    }

    /** (this * other) % modulus — combined multiply + mod to keep numbers bounded. */
    fun multiplyMod(other: BigInt, modulus: BigInt): BigInt {
        // Grade-school multiplication into result array
        val resultLen = digits.size + other.digits.size
        val result = IntArray(resultLen)
        for (i in digits.indices) {
            var carry = 0
            for (j in other.digits.indices) {
                val prod = (digits[i].toInt() and 0xFF) *
                           (other.digits[j].toInt() and 0xFF) +
                           result[i + j] + carry
                result[i + j] = prod and 0xFF
                carry = prod ushr 8
            }
            result[i + other.digits.size] = carry
        }
        // Convert to byte array
        val rawBytes = result.map { it.toByte() }.toByteArray()
        return BigInt(rawBytes).mod(modulus)
    }

    /** this % modulus via long division (binary). */
    fun mod(modulus: BigInt): BigInt {
        if (modulus.isZero) throw ArithmeticException("Division by zero")
        // Simple subtractive approach for now (exponent is small so total ops bounded)
        var remainder = BigInt(byteArrayOf(0))
        val beDigits = digits.reversedArray()
        for (byte in beDigits) {
            for (bit in 7 downTo 0) {
                // Shift remainder left by 1
                remainder = remainder.shiftLeftOne()
                // Set LSB if current bit is 1
                if (((byte.toInt() ushr bit) and 1) == 1) {
                    remainder = remainder.addOne()
                }
                // Subtract modulus if >=
                if (remainder.compareTo(modulus) >= 0) {
                    remainder = remainder.subtract(modulus)
                }
            }
        }
        return remainder
    }

    /** this^exponent mod modulus — square-and-multiply. */
    fun modPow(exponent: BigInt, modulus: BigInt): BigInt {
        var result = BigInt.fromHex("1")
        var base = this.mod(modulus)
        val expBe = exponent.digits.reversedArray()

        for (byte in expBe) {
            for (bit in 7 downTo 0) {
                result = result.multiplyMod(result, modulus)  // square
                if (((byte.toInt() ushr bit) and 1) == 1) {
                    result = result.multiplyMod(base, modulus) // multiply
                }
            }
        }
        return result
    }

    private fun shiftLeftOne(): BigInt {
        var carry = 0
        val newDigits = ByteArray(digits.size) { i ->
            val shifted = ((digits[i].toInt() and 0xFF) shl 1) or carry
            carry = (shifted ushr 8) and 0xFF
            shifted.toByte()
        }
        return if (carry != 0) BigInt(newDigits + carry.toByte())
               else BigInt(newDigits).stripLeadingZeros()
    }

    private fun addOne(): BigInt {
        var carry = 1
        val newDigits = ByteArray(digits.size) { i ->
            val sum = (digits[i].toInt() and 0xFF) + carry
            carry = sum ushr 8
            sum.toByte()
        }
        return if (carry != 0) BigInt(newDigits + carry.toByte())
               else BigInt(newDigits)
    }

    private fun subtract(other: BigInt): BigInt {
        var borrow = 0
        val newDigits = ByteArray(digits.size) { i ->
            val sub = (other.digits.getOrElse(i) { 0 }.toInt() and 0xFF)
            val a = (digits[i].toInt() and 0xFF) - borrow - sub
            borrow = if (a < 0) 1 else 0
            (a and 0xFF).toByte()
        }
        return BigInt(newDigits).stripLeadingZeros()
    }

    private fun compareTo(other: BigInt): Int {
        // Compare by length first, then lexicographically from most significant
        val thisLen = effectiveLength()
        val otherLen = other.effectiveLength()
        if (thisLen != otherLen) return thisLen - otherLen
        for (i in thisLen - 1 downTo 0) {
            val a = (digits[i].toInt() and 0xFF)
            val b = (other.digits[i].toInt() and 0xFF)
            if (a != b) return a - b
        }
        return 0
    }

    private fun effectiveLength(): Int {
        var len = digits.size
        while (len > 1 && digits[len - 1] == 0.toByte()) len--
        return len
    }

    private fun stripLeadingZeros(): BigInt {
        val len = effectiveLength()
        return BigInt(digits.copyOf(len))
    }
}
```

- [ ] **Step 4: Append the NSData conversion helper**

```kotlin
// ── NSData conversion ─────────────────────────────────────────────────

private fun ByteArray.toNSData(): NSData {
    @OptIn(ExperimentalForeignApi::class)
    return this.usePinned { pinned ->
        NSData.create(bytes = pinned.addressOf(0), length = this.size.toULong())
    }
}
```

- [ ] **Step 5: Commit**

```bash
git add shared/src/iosMain/kotlin/com/example/aimusicplayer/network/music/crypto/CryptoProvider.ios.kt
git commit -m "feat: add iOS CryptoProvider actual using CommonCrypto and custom BigInt"
```

---

### Task 4: Create NeteaseEncryptor (common)

**Files:**
- Create: `shared/src/commonMain/kotlin/com/example/aimusicplayer/network/music/crypto/NeteaseEncryptor.kt`

- [ ] **Step 1: Write NeteaseEncryptor**

```kotlin
package com.example.aimusicplayer.network.music.crypto

/**
 * NetEase Cloud Music API parameter encryption.
 *
 * Produces the `params` and `encSecKey` fields required by all /weapi/* endpoints.
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
     * Encrypt a plain JSON string for a /weapi/* POST request.
     *
     * @param jsonText The JSON request body (e.g. `{"s":"晴天","type":"1","limit":"5",...}`)
     * @return [EncryptedParams] containing the `params` and `encSecKey` values
     */
    fun encrypt(jsonText: String): EncryptedParams {
        val firstKeyBytes = FIRST_KEY.encodeToByteArray()
        val ivBytes = IV.encodeToByteArray()
        val jsonBytes = jsonText.encodeToByteArray()

        // Step 1: Generate random 16-char alphanumeric second key
        val secondKey = CryptoProvider.randomAlphaNumeric(16)
        val secondKeyBytes = secondKey.encodeToByteArray()

        // Step 2: First AES (fixed key) — encrypt plain JSON
        val firstPass = CryptoProvider.aesEncryptCbc(jsonBytes, firstKeyBytes, ivBytes)
        val firstBase64 = CryptoProvider.base64Encode(firstPass)

        // Step 3: Second AES (random key) — encrypt first-pass Base64 string
        val secondPass = CryptoProvider.aesEncryptCbc(
            firstBase64.encodeToByteArray(),
            secondKeyBytes,
            ivBytes,
        )
        val params = CryptoProvider.base64Encode(secondPass)

        // Step 4: RSA encrypt the reversed second key
        val reversedKey = secondKey.reversed()
        val reversedHex = CryptoProvider.hexEncode(reversedKey.encodeToByteArray())
        val encSecKey = CryptoProvider.modPowHex(reversedHex, RSA_EXPONENT, RSA_MODULUS)

        return EncryptedParams(params = params, encSecKey = encSecKey)
    }
}
```

- [ ] **Step 2: Commit**

```bash
git add shared/src/commonMain/kotlin/com/example/aimusicplayer/network/music/crypto/NeteaseEncryptor.kt
git commit -m "feat: add NeteaseEncryptor for double AES + RSA param encryption"
```

---

### Task 5: Rewrite NeteaseApi with encryption

**Files:**
- Modify: `shared/src/commonMain/kotlin/com/example/aimusicplayer/network/music/NeteaseApi.kt`

- [ ] **Step 1: Replace NeteaseApi.kt with encrypted /weapi/* implementation**

```kotlin
package com.example.aimusicplayer.network.music

import com.example.aimusicplayer.network.HttpClientFactory
import com.example.aimusicplayer.network.music.crypto.NeteaseEncryptor
import io.ktor.client.call.body
import io.ktor.client.request.header
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.http.ContentType
import io.ktor.http.contentType
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

/**
 * NetEase Cloud Music (网易云音乐) unofficial API adapter.
 *
 * Uses AES-128-CBC (double encryption) + custom RSA to produce the `params`
 * and `encSecKey` fields required by the /weapi/* endpoints.
 *
 * ## Endpoints
 * - Search: `POST https://music.163.com/weapi/cloudsearch/get/web`
 * - Play URL: `POST https://music.163.com/weapi/song/enhance/player/url/v1`
 *
 * ## Notes
 * - Returns direct .mp3 URLs when available
 * - VIP/paid songs are filtered out (they return only 30s previews)
 * - All calls catch exceptions and return empty/null results on failure
 * - Rate-limited via the shared [RateLimitPlugin]
 */
class NeteaseApi : MusicPlatformApi {

    override val platform: String = PLATFORM
    private val client = HttpClientFactory.create(PLATFORM)

    // ── Search ──────────────────────────────────────────────────────────

    override suspend fun search(query: String, platform: String): List<SongMetadata> {
        return try {
            val body = buildJsonObject {
                put("hlpretag", "<span class=\"s-fc7\">")
                put("hlposttag", "</span>")
                put("s", query)
                put("type", "1")
                put("offset", "0")
                put("total", "true")
                put("limit", "30")
                put("csrf_token", "")
            }

            val encrypted = NeteaseEncryptor.encrypt(body.toString())

            val response: NeteaseSearchResponse = client.post(
                "${BASE}/weapi/cloudsearch/get/web"
            ) {
                contentType(ContentType.Application.FormUrlEncoded)
                header("Referer", "$BASE/")
                header("Origin", BASE)
                setBody("params=${encrypted.params}&encSecKey=${encrypted.encSecKey}")
            }.body()

            val songs = response.result?.songs.orEmpty()
            songs.map { it.toSongMetadata() }
        } catch (e: Exception) {
            e.printStackTrace()
            emptyList()
        }
    }

    // ── Play URL ────────────────────────────────────────────────────────

    override suspend fun getPlayUrl(
        songId: String,
        platform: String,
        quality: String,
    ): String? {
        return try {
            val level = qualityToLevel(quality)
            val body = buildJsonObject {
                put("ids", "[$songId]")
                put("level", level)
                put("encodeType", "aac")
                put("csrf_token", "")
            }

            val encrypted = NeteaseEncryptor.encrypt(body.toString())

            val response: NeteasePlayUrlResponse = client.post(
                "${BASE}/weapi/song/enhance/player/url/v1"
            ) {
                contentType(ContentType.Application.FormUrlEncoded)
                header("Referer", "$BASE/")
                header("Origin", BASE)
                setBody("params=${encrypted.params}&encSecKey=${encrypted.encSecKey}")
            }.body()

            val matched = response.data
                ?.firstOrNull { item ->
                    !item.url.isNullOrBlank() && item.size > 0 && item.br > 0
                }

            // VIP / paid songs only return a 30s preview URL → reject
            if (matched != null && matched.fee > 0) {
                println("[NeteaseApi] Skipping VIP song (fee=${matched.fee}) — URL would be truncated")
                return null
            }

            matched?.url
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }

    // ── Helpers ─────────────────────────────────────────────────────────

    private fun NeteaseSong.toSongMetadata(): SongMetadata = SongMetadata(
        songId = id.toString(),
        songName = name,
        artist = artists?.joinToString(" / ") { it.name } ?: "未知艺术家",
        album = album?.name,
        duration = duration / 1000, // ms → seconds
        coverUrl = album?.picUrl?.replace("http://", "https://"),
        platform = PLATFORM,
    )

    private fun qualityToLevel(quality: String): String = when (quality) {
        "lossless" -> "lossless"
        "high"     -> "higher"
        else       -> "standard"
    }

    companion object {
        private const val PLATFORM = "netease"
        private const val BASE = "https://music.163.com"
    }
}
```

- [ ] **Step 2: Commit**

```bash
git add shared/src/commonMain/kotlin/com/example/aimusicplayer/network/music/NeteaseApi.kt
git commit -m "feat: rewrite NeteaseApi with encrypted /weapi/* endpoints"
```

---

### Task 6: Create MusicSignUtils (common)

**Files:**
- Create: `shared/src/commonMain/kotlin/com/example/aimusicplayer/network/music/crypto/MusicSignUtils.kt`

- [ ] **Step 1: Write MusicSignUtils**

Reuses the existing `Md5Utils` from the cache package for MD5 hashing.

```kotlin
package com.example.aimusicplayer.network.music.crypto

import com.example.aimusicplayer.cache.Md5Utils

/**
 * Signing utilities for QQ Music and Kugou Music API requests.
 *
 * ## QQ Music
 * - `sign` = MD5(sorted_params_kv_string + SIGN_KEY).uppercase()
 * - SIGN_KEY extracted from y.qq.com JS: "CJBPACrRuNy7"
 *
 * ## Kugou Music
 * - `signature` = MD5(sorted_params_kv_string).uppercase()
 * - Requires `dfid` cookie: a random device fingerprint (32-char hex)
 */
object MusicSignUtils {

    /** Fixed signing key for QQ Music API (from y.qq.com JS reverse). */
    private const val QQ_SIGN_KEY = "CJBPACrRuNy7"

    /** Lazily initialized device fingerprint ID for Kugou. */
    private var kugouDfid: String? = null

    // ── QQ Music ───────────────────────────────────────────────────────

    /**
     * Compute the `sign` parameter for QQ Music API requests.
     *
     * Algorithm:
     * 1. Sort query params alphabetically by key
     * 2. Concatenate as "key1=value1&key2=value2..."
     * 3. Append [QQ_SIGN_KEY]
     * 4. MD5 → uppercase hex
     *
     * @param params Map of query parameter key→value pairs (excluding `sign` itself)
     * @return 32-char uppercase hex MD5 string
     */
    fun qqSign(params: Map<String, String>): String {
        val sorted = params.entries
            .sortedBy { it.key }
            .joinToString("&") { "${it.key}=${it.value}" }
        return Md5Utils.md5(sorted + QQ_SIGN_KEY).uppercase()
    }

    // ── Kugou ──────────────────────────────────────────────────────────

    /**
     * Compute the `signature` parameter for Kugou Music API requests.
     *
     * Algorithm:
     * 1. Sort query params alphabetically by key
     * 2. Concatenate as "key1=value1&key2=value2..."
     * 3. MD5 → uppercase hex
     *
     * @param params Map of query parameter key→value pairs (excluding `signature` itself)
     * @return 32-char uppercase hex MD5 string
     */
    fun kugouSign(params: Map<String, String>): String {
        val sorted = params.entries
            .sortedBy { it.key }
            .joinToString("&") { "${it.key}=${it.value}" }
        return Md5Utils.md5(sorted).uppercase()
    }

    /**
     * Get or create the Kugou device fingerprint (dfid) cookie value.
     *
     * Generated once per app session: a random 32-char hex string
     * simulating the Kugou Android app's device ID.
     */
    fun getKugouDfid(): String {
        if (kugouDfid == null) {
            val bytes = (1..16).map {
                kotlin.random.Random.nextInt(256).toByte()
            }.toByteArray()
            kugouDfid = bytes.joinToString("") { "%02x".format(it) }
        }
        return kugouDfid!!
    }
}
```

- [ ] **Step 2: Commit**

```bash
git add shared/src/commonMain/kotlin/com/example/aimusicplayer/network/music/crypto/MusicSignUtils.kt
git commit -m "feat: add MusicSignUtils for QQ and Kugou API signing"
```

---

### Task 7: Fix QQMusicApi — add sign parameter

**Files:**
- Modify: `shared/src/commonMain/kotlin/com/example/aimusicplayer/network/music/QQMusicApi.kt`

- [ ] **Step 1: Replace QQMusicApi.kt with sign-enabled version**

```kotlin
package com.example.aimusicplayer.network.music

import com.example.aimusicplayer.network.HttpClientFactory
import com.example.aimusicplayer.network.music.crypto.MusicSignUtils
import io.ktor.client.call.body
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.statement.bodyAsText

/**
 * QQ Music (QQ音乐) unofficial API adapter.
 *
 * ## Endpoints
 * - Search: `GET https://c.y.qq.com/soso/fcgi-bin/client_search_cp`
 *   Returns JSONP — wrapper is stripped before parsing.
 * - Play URL: `GET https://u.y.qq.com/cgi-bin/musicu.fcg?data={json}`
 *   Returns vkey + purl → assembled as stream URL.
 *
 * Both endpoints now include a `sign` parameter generated via [MusicSignUtils.qqSign].
 */
class QQMusicApi : MusicPlatformApi {

    override val platform: String = PLATFORM
    private val client = HttpClientFactory.create(PLATFORM)

    // ── Search ──────────────────────────────────────────────────────────

    override suspend fun search(query: String, platform: String): List<SongMetadata> {
        return try {
            // Build params and compute sign
            val params = mapOf(
                "p" to "1",
                "n" to "10",
                "w" to query,
                "format" to "json",
            )
            val sign = MusicSignUtils.qqSign(params)

            val raw = client.get(
                "${SEARCH_BASE}/soso/fcgi-bin/client_search_cp"
            ) {
                url {
                    params.forEach { (k, v) -> parameters.append(k, v) }
                    parameters.append("sign", sign)
                }
                header("Referer", "https://y.qq.com/")
            }.bodyAsText()

            val json = MusicJson
            val parsed: QQSearchResponse = json.decodeFromString(stripJsonp(raw))
            parsed.data?.song?.list.orEmpty().map { it.toSongMetadata() }
        } catch (e: Exception) {
            e.printStackTrace()
            emptyList()
        }
    }

    // ── Play URL ────────────────────────────────────────────────────────

    override suspend fun getPlayUrl(
        songId: String,
        platform: String,
        quality: String,
    ): String? {
        return try {
            val filename = selectFilename(songId, quality)

            val dataPayload = buildString {
                append("{")
                append("\"req\":{\"param\":{\"guid\":\"$GUID\"}},")
                append("\"req_0\":{")
                append("\"module\":\"vkey.GetVkeyServer\",")
                append("\"method\":\"CgiGetVkey\",")
                append("\"param\":{")
                append("\"guid\":\"$GUID\",")
                append("\"songmid\":[\"$songId\"],")
                append("\"filename\":[\"$filename\"]")
                append("}}}")
            }

            // Compute sign for the play-URL request
            val sign = MusicSignUtils.qqSign(mapOf("data" to dataPayload))

            val response: QQMusicPlayUrlResponse = client.get(
                "${PLAY_BASE}/cgi-bin/musicu.fcg"
            ) {
                url {
                    parameters.append("data", dataPayload)
                    parameters.append("sign", sign)
                }
                header("Referer", "https://y.qq.com/")
            }.body()

            val vkeyData = response.req_0?.data ?: return null
            val vkey = vkeyData.vkey
            val purl = vkeyData.midurlinfo?.firstOrNull()?.purl

            if (vkey.isBlank() || purl.isNullOrBlank()) return null

            "http://dl.stream.qqmusic.qq.com/$purl?vkey=$vkey&guid=$GUID&fromtag=1"
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }

    // ── Helpers ─────────────────────────────────────────────────────────

    private fun QQSongItem.toSongMetadata(): SongMetadata = SongMetadata(
        songId = songmid,
        songName = songname,
        artist = singer?.joinToString(" / ") { it.name } ?: "未知歌手",
        album = albumname.takeIf { it.isNotBlank() },
        duration = interval,
        coverUrl = albummid.takeIf { it.isNotBlank() }?.let {
            "https://y.qq.com/music/photo_new/T002R300x300M000${it}.jpg"
        },
        platform = PLATFORM,
    )

    private fun selectFilename(songmid: String, quality: String): String = when (quality) {
        "lossless" -> "F000$songmid.flac"
        "high"     -> "M500$songmid.mp3"
        else       -> "M800$songmid.mp4"
    }

    companion object {
        private const val PLATFORM = "qq"
        private const val SEARCH_BASE = "https://c.y.qq.com"
        private const val PLAY_BASE = "https://u.y.qq.com"
        private const val GUID = "123456"
    }
}
```

- [ ] **Step 2: Commit**

```bash
git add shared/src/commonMain/kotlin/com/example/aimusicplayer/network/music/QQMusicApi.kt
git commit -m "feat: add sign parameter to QQMusicApi search and play-URL requests"
```

---

### Task 8: Fix KugouApi — add signature + dfid

**Files:**
- Modify: `shared/src/commonMain/kotlin/com/example/aimusicplayer/network/music/KugouApi.kt`

- [ ] **Step 1: Replace KugouApi.kt with signature + dfid version**

```kotlin
package com.example.aimusicplayer.network.music

import com.example.aimusicplayer.network.HttpClientFactory
import com.example.aimusicplayer.network.music.crypto.MusicSignUtils
import io.ktor.client.call.body
import io.ktor.client.request.get
import io.ktor.client.request.header

/**
 * Kugou Music (酷狗音乐) unofficial API adapter.
 *
 * ## Endpoints
 * - Search: `GET https://mobilecdn.kugou.com/api/v3/search/song`
 * - Play URL: `GET https://wwwapi.kugou.com/yy/index.php`
 *
 * Both endpoints now include `signature` parameter and `dfid` cookie.
 */
class KugouApi : MusicPlatformApi {

    override val platform: String = PLATFORM
    private val client = HttpClientFactory.create(PLATFORM)
    private val dfid: String = MusicSignUtils.getKugouDfid()

    // ── Search ──────────────────────────────────────────────────────────

    override suspend fun search(query: String, platform: String): List<SongMetadata> {
        return try {
            val params = mapOf(
                "format" to "json",
                "keyword" to query,
                "page" to "1",
                "pagesize" to "5",
            )
            val signature = MusicSignUtils.kugouSign(params)

            val response: KugouSearchResponse = client.get(
                "${SEARCH_BASE}/api/v3/search/song"
            ) {
                url {
                    params.forEach { (k, v) -> parameters.append(k, v) }
                    parameters.append("signature", signature)
                }
                header("Referer", "https://m.kugou.com/")
                header("Cookie", "dfid=$dfid")
            }.body()

            val infos = response.data?.info.orEmpty()
            infos.map { it.toSongMetadata() }
        } catch (e: Exception) {
            e.printStackTrace()
            emptyList()
        }
    }

    // ── Play URL ────────────────────────────────────────────────────────

    override suspend fun getPlayUrl(
        songId: String,
        platform: String,
        quality: String,
    ): String? {
        return try {
            val (hash, albumId) = parseSongId(songId) ?: return null

            val params = mapOf(
                "r" to "play/getdata",
                "hash" to hash,
                "album_id" to albumId,
                "mid" to "123456",
                "plat" to "android",
            )
            val signature = MusicSignUtils.kugouSign(params)

            val response: KugouPlayUrlResponse = client.get(
                "${PLAY_BASE}/yy/index.php"
            ) {
                url {
                    params.forEach { (k, v) -> parameters.append(k, v) }
                    parameters.append("signature", signature)
                }
                header("Referer", "https://m.kugou.com/")
                header("Cookie", "dfid=$dfid")
            }.body()

            response.data?.play_url?.takeIf { it.isNotBlank() }
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }

    // ── Helpers ─────────────────────────────────────────────────────────

    private fun KugouSongInfo.toSongMetadata(): SongMetadata = SongMetadata(
        songId = "$hash|$album_id",
        songName = songname,
        artist = singername,
        album = album_name.takeIf { it.isNotBlank() },
        duration = duration,
        platform = PLATFORM,
    )

    private fun parseSongId(songId: String): Pair<String, String>? {
        val parts = songId.split("|")
        return if (parts.size == 2) Pair(parts[0], parts[1]) else null
    }

    companion object {
        private const val PLATFORM = "kugou"
        private const val SEARCH_BASE = "https://mobilecdn.kugou.com"
        private const val PLAY_BASE = "https://wwwapi.kugou.com"
    }
}
```

- [ ] **Step 2: Commit**

```bash
git add shared/src/commonMain/kotlin/com/example/aimusicplayer/network/music/KugouApi.kt
git commit -m "feat: add signature and dfid to KugouApi requests"
```

---

## Verification

After all tasks complete:

1. **Build check (Android)**: `./gradlew :shared:compileKotlinAndroid` — must compile without errors
2. **Build check (iOS)**: `./gradlew :shared:compileKotlinIosSimulatorArm64` — must compile without errors
3. **Manual integration test**: Run the Android app, search for "晴天" — should return results from all three platforms
