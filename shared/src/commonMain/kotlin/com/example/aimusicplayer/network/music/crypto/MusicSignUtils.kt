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
