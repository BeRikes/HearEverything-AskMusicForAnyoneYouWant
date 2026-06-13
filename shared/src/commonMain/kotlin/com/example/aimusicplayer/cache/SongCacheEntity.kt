package com.example.aimusicplayer.cache

import kotlinx.serialization.Serializable

/**
 * Cached song metadata entity.
 * id = MD5("songName|artist|platform") for deterministic cache keys.
 */
@Serializable
data class SongCacheEntity(
    val id: String,
    val songName: String,
    val artist: String? = null,
    val platform: String,
    val audioUrl: String,
    val coverUrl: String? = null,
    val lyrics: String? = null,
    val quality: String = "standard",
    val expireTime: Long,   // Unix timestamp (seconds)
    val createTime: Long    // Unix timestamp (seconds)
)

/**
 * Records a failed song lookup to avoid repeated failed network requests.
 */
data class FailedLookup(
    val id: String,
    val expireTime: Long   // Unix timestamp (seconds)
)
