package com.example.aimusicplayer.cache

/**
 * Pure Kotlin MD5 hash implementation for KMP compatibility.
 * Generates the cache key from song metadata.
 */
object Md5Utils {

    fun md5(input: String): String {
        val data = input.encodeToByteArray()
        val padded = padMessage(data)
        val (a, b, c, d) = computeHash(padded)
        return toHexString(a, b, c, d)
    }

    /**
     * Generate a deterministic cache ID from song metadata.
     * Format: MD5("songName|artist|platform")
     */
    fun generateSongId(songName: String, artist: String?, platform: String): String {
        val raw = "$songName|${artist ?: ""}|$platform"
        return md5(raw)
    }

    // ── MD5 implementation ───────────────────────────────────────────

    private fun padMessage(data: ByteArray): ByteArray {
        val msgLen = data.size
        val padLen = if (msgLen % 64 < 56) 56 - msgLen % 64 else 120 - msgLen % 64
        val totalLen = msgLen + padLen + 8
        val padded = ByteArray(totalLen)
        data.copyInto(padded)
        padded[msgLen] = 0x80.toByte() // append '1' bit

        // Append original length in bits (little-endian, 64 bits)
        val bitLen = msgLen.toLong() * 8
        for (i in 0 until 8) {
            padded[totalLen - 8 + i] = ((bitLen ushr (i * 8)) and 0xFF).toByte()
        }
        return padded
    }

    private fun computeHash(padded: ByteArray): Quad {
        var a = 0x67452301
        var b = 0xefcdab89.toInt()
        var c = 0x98badcfe.toInt()
        var d = 0x10325476

        // Process each 512-bit (64-byte) block
        val m = IntArray(16)
        for (offset in padded.indices step 64) {
            // Convert block to 16 x 32-bit words (little-endian)
            for (i in 0 until 16) {
                val base = offset + i * 4
                m[i] = (padded[base].toInt() and 0xFF) or
                        ((padded[base + 1].toInt() and 0xFF) shl 8) or
                        ((padded[base + 2].toInt() and 0xFF) shl 16) or
                        ((padded[base + 3].toInt() and 0xFF) shl 24)
            }

            var aa = a; var bb = b; var cc = c; var dd = d

            // Round 1
            fun F(x: Int, y: Int, z: Int) = (x and y) or (x.inv() and z)
            fun G(x: Int, y: Int, z: Int) = (x and z) or (y and z.inv())
            fun H(x: Int, y: Int, z: Int) = x xor y xor z
            fun I(x: Int, y: Int, z: Int) = y xor (x or z.inv())

            // Per-round constants (sin-based, 64 values)
            val K = intArrayOf(
                0xd76aa478.toInt(), 0xe8c7b756.toInt(), 0x242070db, 0xc1bdceee.toInt(),
                0xf57c0faf.toInt(), 0x4787c62a, 0xa8304613.toInt(), 0xfd469501.toInt(),
                0x698098d8, 0x8b44f7af.toInt(), 0xffff5bb1.toInt(), 0x895cd7be.toInt(),
                0x6b901122, 0xfd987193.toInt(), 0xa679438e.toInt(), 0x49b40821,
                // Round 2 (16..31)
                0xf61e2562.toInt(), 0xc040b340.toInt(), 0x265e5a51, 0xe9b6c7aa.toInt(),
                0xd62f105d.toInt(), 0x02441453, 0xd8a1e681.toInt(), 0xe7d3fbc8.toInt(),
                0x21e1cde6, 0xc33707d6.toInt(), 0xf4d50d87.toInt(), 0x455a14ed,
                0xa9e3e905.toInt(), 0xfcefa3f8.toInt(), 0x676f02d9, 0x8d2a4c8a.toInt(),
                // Round 3 (32..47)
                0xfffa3942.toInt(), 0x8771f681.toInt(), 0x6d9d6122, 0xfde5380c.toInt(),
                0xa4beea44.toInt(), 0x4bdecfa9, 0xf6bb4b60.toInt(), 0xbebfbc70.toInt(),
                0x289b7ec6, 0xeaa127fa.toInt(), 0xd4ef3085.toInt(), 0x04881d05,
                0xd9d4d039.toInt(), 0xe6db99e5.toInt(), 0x1fa27cf8, 0xc4ac5665.toInt(),
                // Round 4 (48..63)
                0xf4292244.toInt(), 0x432aff97.toInt(), 0xab9423a7.toInt(), 0xfc93a039.toInt(),
                0x655b59c3, 0x8f0ccc92.toInt(), 0xffeff47d.toInt(), 0x85845dd1.toInt(),
                0x6fa87e4f, 0xfe2ce6e0.toInt(), 0xa3014314.toInt(), 0x4e0811a1,
                0xf7537e82.toInt(), 0xbd3af235.toInt(), 0x2ad7d2bb, 0xeb86d391.toInt(),
            )

            val S1 = intArrayOf(7, 12, 17, 22, 7, 12, 17, 22, 7, 12, 17, 22, 7, 12, 17, 22)
            val S2 = intArrayOf(5, 9, 14, 20, 5, 9, 14, 20, 5, 9, 14, 20, 5, 9, 14, 20)
            val S3 = intArrayOf(4, 11, 16, 23, 4, 11, 16, 23, 4, 11, 16, 23, 4, 11, 16, 23)
            val S4 = intArrayOf(6, 10, 15, 21, 6, 10, 15, 21, 6, 10, 15, 21, 6, 10, 15, 21)

            // Round 1: F
            for (i in 0 until 16) {
                val f = F(bb, cc, dd)
                val g = i
                val temp = dd; dd = cc; cc = bb
                bb = bb + leftRotate(aa + f + K[i] + m[g], S1[i])
                aa = temp
            }
            // Round 2: G (K[16..31])
            for (i in 0 until 16) {
                val f = G(bb, cc, dd)
                val g = (5 * i + 1) % 16
                val temp = dd; dd = cc; cc = bb
                bb = bb + leftRotate(aa + f + K[16 + i] + m[g], S2[i])
                aa = temp
            }
            // Round 3: H (K[32..47])
            for (i in 0 until 16) {
                val f = H(bb, cc, dd)
                val g = (3 * i + 5) % 16
                val temp = dd; dd = cc; cc = bb
                bb = bb + leftRotate(aa + f + K[32 + i] + m[g], S3[i])
                aa = temp
            }
            // Round 4: I (K[48..63])
            for (i in 0 until 16) {
                val f = I(bb, cc, dd)
                val g = (7 * i) % 16
                val temp = dd; dd = cc; cc = bb
                bb = bb + leftRotate(aa + f + K[48 + i] + m[g], S4[i])
                aa = temp
            }

            a += aa; b += bb; c += cc; d += dd
        }
        return Quad(a, b, c, d)
    }

    private fun leftRotate(value: Int, shift: Int): Int =
        (value shl shift) or (value ushr (32 - shift))

    private data class Quad(val a: Int, val b: Int, val c: Int, val d: Int)

    private fun toHexString(a: Int, b: Int, c: Int, d: Int): String {
        fun intToLEHex(x: Int): String {
            val bytes = byteArrayOf(
                (x and 0xFF).toByte(),
                ((x ushr 8) and 0xFF).toByte(),
                ((x ushr 16) and 0xFF).toByte(),
                ((x ushr 24) and 0xFF).toByte()
            )
            return bytes.joinToString("") { byte -> "%02x".format(byte) }
        }
        return intToLEHex(a) + intToLEHex(b) + intToLEHex(c) + intToLEHex(d)
    }
}
