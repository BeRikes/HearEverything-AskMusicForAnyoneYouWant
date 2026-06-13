# Music API Anti-Crawl — Design Spec

**Date:** 2026-06-04
**Status:** Approved
**Scope:** NetEase Cloud Music, QQ Music, Kugou Music API reverse-engineering

---

## 1. Problem Statement

All three music platform APIs (NetEase, QQ, Kugou) are blocked by anti-crawling mechanisms.
The `HearEverything` app's search and playback features are non-functional.

Root causes per platform:

| Platform | Root Cause |
|----------|-----------|
| **NetEase** | Missing `params` (double AES-CBC) and `encSecKey` (custom RSA) encrypted parameters; using old `/api/*` endpoints instead of `/weapi/*` |
| **QQ Music** | Missing `sign` parameter generation for play-URL endpoint; otherwise mostly functional |
| **Kugou** | Missing `signature` parameter and `dfid` cookie generation |

---

## 2. Solution: Full JS Reverse + expect/actual Crypto Bridge

Implement encryption/signing in Kotlin common code using `expect/actual` for
platform-specific crypto primitives. Zero external dependencies.

### 2.1 File Structure

```
shared/src/
├── commonMain/kotlin/.../network/music/
│   ├── crypto/
│   │   ├── CryptoProvider.kt          ← expect: AES-CBC, BigInt modPow, Base64, Hex, MD5
│   │   ├── NeteaseEncryptor.kt        ← NetEase params + encSecKey generation
│   │   └── MusicSignUtils.kt          ← MD5, QQ sign, Kugou signature
│   ├── NeteaseApi.kt                  ← REWRITE: /weapi/* endpoints + encryption
│   ├── QQMusicApi.kt                  ← FIX: add sign parameter
│   ├── KugouApi.kt                    ← FIX: add signature + dfid
│   ├── MusicPlatformApi.kt            ← UNCHANGED (interfaces + DTOs)
│   └── (BilibiliApi.kt unchanged)
│
├── androidMain/kotlin/.../network/music/crypto/
│   └── CryptoProvider.android.kt      ← javax.crypto + java.math.BigInteger + java.security.MessageDigest
│
└── iosMain/kotlin/.../network/music/crypto/
    └── CryptoProvider.ios.kt          ← CryptoKit + custom BigInt for modPow
```

---

## 3. NetEase Cloud Music — Encryption

### 3.1 Algorithm

```
Input: JSON string (request body)

1. Generate 16-char random string (a-zA-Z0-9) → secondKey
2. params = AES-128-CBC(JSON,       key=FIRST_KEY, iv=IV)  → Base64
3. params = AES-128-CBC(params_str, key=secondKey, iv=IV)  → Base64
4. reversed = secondKey reversed
5. hex_val = bytes-to-hex(reversed)
6. encSecKey = hex_val^RSA_EXPONENT mod RSA_MODULUS        → 256-char hex string
7. Return { params, encSecKey }
```

### 3.2 Fixed Constants

| Name | Value |
|------|-------|
| `FIRST_KEY` | `0CoJUm6Qyw8W8jud` |
| `IV` | `0102030405060708` (16 bytes, ASCII) |
| `RSA_EXPONENT` | `010001` (65537 decimal) |
| `RSA_MODULUS` | `00e0b509f6259df8642dbc35662901477df22677ec152b5ff68ace615bb7b725152b3ab17a876aea8a5aa76d2e417629ec4ee341f56135fccf695280104e0312ecbda92557c93870114af6c9d05c4f7f0c3685b7a46bee255932575cce10b424d813cfe4875d3e82047b97ddef52741d546b8e289dc6935b3ece0462db0a22b8e7` |

All confirmed via JS reverse from `window.asrsea` in `music.163.com` core JS files.

### 3.3 Endpoint Changes

| Function | Old | New |
|----------|-----|-----|
| Search | `POST /api/search/get/` (form, no encrypt) | `POST /weapi/cloudsearch/get/web` (JSON body, encrypted) |
| Play URL | `POST /api/song/enhance/player/url` (form, no encrypt) | `POST /weapi/song/enhance/player/url/v1` (JSON body, encrypted) |

### 3.4 Request Body Schema

**Search:**
```json
{
  "hlpretag": "<span class=\"s-fc7\">",
  "hlposttag": "</span>",
  "s": "<query>",
  "type": "1",
  "offset": "0",
  "total": "true",
  "limit": "30",
  "csrf_token": ""
}
```

**Play URL:**
```json
{
  "ids": "[<songId>]",
  "level": "standard",
  "encodeType": "aac",
  "csrf_token": ""
}
```

---

## 4. QQ Music — Sign Parameter

### 4.1 Algorithm

```
sign = MD5( sorted_params_as_query_string + SIGN_KEY ).uppercase()
```

Where sorted params are alphabetically ordered key=value pairs joined by `&`.

### 4.2 Fixed Constant

`SIGN_KEY` = `CJBPACrRuNy7` — confirmed from `y.qq.com` JS reverse. To be verified against live endpoint; if the JS bundle has been rotated, extract by searching for `sign` variable assignment in `y.qq.com` sources.

### 4.3 Changes

- Search: Keep existing `c.y.qq.com` JSONP endpoint (still works), add `sign` param
- Play URL: Keep `u.y.qq.com` endpoint, add `sign` param to `data` payload

---

## 5. Kugou Music — Signature + dfid

### 5.1 Signature Algorithm

```
// Sort query params (excluding signature itself) alphabetically by key,
// concatenate values as key1=value1&key2=value2..., then MD5:
signature = MD5( sorted_params_kv_string ).uppercase()
```

Example: for params `{r: "play/getdata", hash: "abc", album_id: "123"}`:
- Sort by key: `album_id=123&hash=abc&r=play/getdata`
- signature = `MD5("album_id=123&hash=abc&r=play/getdata").uppercase()`

### 5.2 dfid Cookie

Generate a random device fingerprint string on first use, persist across requests.
Format: 32-char hex string (simulates Kugou app's device ID).

### 5.3 Changes

- Search: Add `signature` param + `dfid` cookie header
- Play URL: Add `signature` param + `dfid` cookie header

---

## 6. CryptoProvider — expect/actual Contract

### 6.1 common Expect Declaration

```kotlin
expect object CryptoProvider {
    /** AES-128-CBC encryption with PKCS7 padding. */
    fun aesEncryptCbc(plaintext: ByteArray, key: ByteArray, iv: ByteArray): ByteArray

    /** Modular exponentiation: base^exponent mod modulus (big-endian unsigned). */
    fun modPowBigInt(base: ByteArray, exponent: ByteArray, modulus: ByteArray): ByteArray

    /** Cryptographically random string of given length (a-zA-Z0-9). */
    fun randomAlphaNumeric(length: Int): String

    /** Base64 standard encoding (no line breaks). */
    fun base64Encode(data: ByteArray): String

    /** Hex encoding (lowercase). */
    fun hexEncode(data: ByteArray): String

    /** Hex decoding. */
    fun hexDecode(hex: String): ByteArray

    /** MD5 hash, returns lowercase hex string. */
    fun md5(data: ByteArray): String
}
```

### 6.2 Android Actual

- `aesEncryptCbc` → `javax.crypto.Cipher` with `AES/CBC/PKCS5Padding`
- `modPowBigInt` → `java.math.BigInteger.modPow()`
- `randomAlphaNumeric` → `java.security.SecureRandom`
- `base64Encode` → `java.util.Base64`
- `hexEncode` / `hexDecode` → manual byte-to-char conversion
- `md5` → `java.security.MessageDigest`

### 6.3 iOS Actual

- `aesEncryptCbc` → `CryptoKit.AES` with `.cbc` mode + manual PKCS7 padding
- `modPowBigInt` → Custom BigInt modular exponentiation (square-and-multiply)
- `randomAlphaNumeric` → `SecRandomCopyBytes`
- `base64Encode` → `Foundation.NSData.base64EncodedString`
- `hexEncode` / `hexDecode` → manual byte-to-char conversion
- `md5` → `CryptoKit.Insecure.MD5`

---

## 7. Error Handling / Resilience

All API classes maintain existing contract:
- Never throw — catch all exceptions, return empty lists / null
- Log errors via `println` (existing pattern)
- Rate limiting still enforced by `RateLimitPlugin`

---

## 8. Testing Strategy

1. **Unit tests for NeteaseEncryptor**: Verify `params` and `encSecKey` generation with known inputs
2. **Unit tests for MusicSignUtils**: Verify MD5, QQ sign, Kugou signature with known inputs
3. **Integration tests**: Search for known song → expect non-empty results from each platform
4. **Playback test**: Resolve play URL → verify URL is accessible

---

## 9. Implementation Order

1. `CryptoProvider` expect + android actual + ios actual
2. `NeteaseEncryptor` + rewrite `NeteaseApi`
3. `MusicSignUtils` + fix `QQMusicApi`
4. `MusicSignUtils` + fix `KugouApi`
5. Integration testing
