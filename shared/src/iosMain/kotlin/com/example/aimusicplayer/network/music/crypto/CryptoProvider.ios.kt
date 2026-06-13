package com.example.aimusicplayer.network.music.crypto

import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.addressOf
import kotlinx.cinterop.usePinned
import platform.CommonCrypto.CCCrypt
import platform.CommonCrypto.kCCAlgorithmAES
import platform.CommonCrypto.kCCEncrypt
import platform.CommonCrypto.kCCOptionPKCS7Padding
import platform.CommonCrypto.kCCSuccess
import platform.Foundation.NSData
import platform.Foundation.NSDataBase64EncodingEndLineWithCarriageReturn
import platform.Foundation.NSDataBase64EncodingEndLineWithLineFeed
import platform.Foundation.create
import platform.Security.SecRandomCopyBytes
import platform.darwin.kSecRandomDefault
import platform.posix.uint32_tVar

actual object CryptoProvider {

    actual fun aesEncryptCbc(plaintext: ByteArray, key: ByteArray, iv: ByteArray): ByteArray {
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
        return nsData.base64EncodedStringWithOptions(0u)
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

// ── CommonCrypto AES-CBC ──────────────────────────────────────────────

@OptIn(ExperimentalForeignApi::class)
private fun aesCbcEncryptCommonCrypto(
    plaintext: ByteArray,
    key: ByteArray,
    iv: ByteArray
): ByteArray {
    val bufferSize = plaintext.size + 16 // room for PKCS7 padding
    val buffer = ByteArray(bufferSize)
    var bytesEncrypted = 0u

    plaintext.usePinned { plainPinned ->
        key.usePinned { keyPinned ->
            iv.usePinned { ivPinned ->
                buffer.usePinned { bufPinned ->
                    val status = CCCrypt(
                        kCCEncrypt,
                        kCCAlgorithmAES,
                        kCCOptionPKCS7Padding,
                        keyPinned.addressOf(0),
                        16u, // AES-128 key length
                        ivPinned.addressOf(0),
                        plainPinned.addressOf(0),
                        plaintext.size.toULong(),
                        bufPinned.addressOf(0),
                        bufferSize.toULong(),
                        uint32_tVar(bytesEncrypted).ptr
                    )
                    if (status != kCCSuccess) {
                        throw RuntimeException("CCCrypt failed with status $status")
                    }
                }
            }
        }
    }
    return buffer.copyOf(bytesEncrypted.toInt())
}

// ── Pure Kotlin BigInt (little-endian byte array, unsigned) ─────────

private class BigInt private constructor(
    private val digits: ByteArray
) {
    val isZero: Boolean get() = digits.size == 1 && digits[0] == 0.toByte()

    companion object {
        fun fromHex(hex: String): BigInt {
            val str = hex.trimStart('0').ifEmpty { "0" }
            // Parse into big-endian bytes first, then reverse to little-endian
            val beLen = (str.length + 1) / 2
            val beBytes = ByteArray(beLen) { i ->
                val start = str.length - (i + 1) * 2
                val end = str.length - i * 2
                val chunk = str.substring(maxOf(0, start), end)
                chunk.toInt(16).toByte()
            }
            val le = beBytes.reversedArray()
            val lastNonZero = le.indexOfLast { it != 0.toByte() }
            return if (lastNonZero < 0) BigInt(byteArrayOf(0))
                   else BigInt(le.copyOf(lastNonZero + 1))
        }
    }

    fun toHex(): String {
        val be = digits.reversedArray()
        return be.joinToString("") { "%02x".format(it) }.trimStart('0').ifEmpty { "0" }
    }

    /** (this * other) % modulus — combined to keep numbers bounded. */
    fun multiplyMod(other: BigInt, modulus: BigInt): BigInt {
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
        val rawBytes = ByteArray(result.size) { i -> result[i].toByte() }
        return BigInt(rawBytes).mod(modulus)
    }

    /** this % modulus via binary long division. */
    fun mod(modulus: BigInt): BigInt {
        if (modulus.isZero) throw ArithmeticException("Division by zero")
        var remainder = BigInt(byteArrayOf(0))
        val beDigits = digits.reversedArray()
        for (byte in beDigits) {
            for (bit in 7 downTo 0) {
                remainder = remainder.shiftLeftOne()
                if (((byte.toInt() ushr bit) and 1) == 1) {
                    remainder = remainder.addOne()
                }
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

// ── NSData conversion ─────────────────────────────────────────────────

@OptIn(ExperimentalForeignApi::class)
private fun ByteArray.toNSData(): NSData {
    return this.usePinned { pinned ->
        NSData.create(bytes = pinned.addressOf(0), length = this.size.toULong())
    }
}
