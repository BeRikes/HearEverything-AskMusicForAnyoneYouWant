package com.example.aimusicplayer.network

import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * Configuration parameters for a platform's rate limit.
 *
 * @property permitsPerSecond Requests allowed per second (fractional allowed, 0 = unlimited)
 * @property permitsPerMinute Requests allowed per minute (0 = unlimited)
 * @property randomDelayRange Random delay added after each request (seconds)
 */
data class RateLimitConfig(
    val permitsPerSecond: Double,
    val permitsPerMinute: Int,
    val randomDelayRange: ClosedFloatingPointRange<Double> = 0.5..3.0,
) {
    companion object {
        /** Disabled / unlimited rate limiting. */
        val UNLIMITED = RateLimitConfig(0.0, 0)
    }
}

/**
 * Global singleton registry managing per-platform [RateLimiter] instances.
 *
 * Pre-configured for common Chinese music platforms and a generic crawler mode.
 * Use [updateConfig] to adjust limits dynamically at runtime.
 *
 * ## Platform keys
 * - `netease`  — 网易云音乐 (NetEase Cloud Music)
 * - `qq`       — QQ音乐 (QQ Music)
 * - `kugou`    — 酷狗音乐 (Kugou Music)
 * - `bilibili` — B站 (Bilibili)
 * - `crawler`  — 通用爬虫模式 (Generic / unknown sites)
 */
object RateLimiterRegistry {

    private val mutex = Mutex()
    private val limiters = mutableMapOf<String, RateLimiter>()

    // ── Default platform configurations ─────────────────────────────────

    private val defaultConfigs = mapOf(
        // 网易云: moderate rate limit — ~2 req/s, ~60/min
        "netease" to RateLimitConfig(
            permitsPerSecond = 2.0,
            permitsPerMinute = 60,
            randomDelayRange = 1.0..3.0,
        ),
        // QQ音乐: slightly stricter — ~1.5 req/s, ~45/min
        "qq" to RateLimitConfig(
            permitsPerSecond = 1.5,
            permitsPerMinute = 45,
            randomDelayRange = 1.5..4.0,
        ),
        // 酷狗: moderate — ~2 req/s, ~50/min
        "kugou" to RateLimitConfig(
            permitsPerSecond = 2.0,
            permitsPerMinute = 50,
            randomDelayRange = 1.0..3.5,
        ),
        // B站: relatively lenient for public API — ~3 req/s, ~80/min
        "bilibili" to RateLimitConfig(
            permitsPerSecond = 3.0,
            permitsPerMinute = 80,
            randomDelayRange = 0.5..2.0,
        ),
        // 通用爬虫模式: moderate — ~1.0 req/s, ~30/min, short humanization delay
        "crawler" to RateLimitConfig(
            permitsPerSecond = 1.0,
            permitsPerMinute = 30,
            randomDelayRange = 0.5..2.0,
        ),
        // AI 大模型 API: relaxed — ~1 req/s, ~30/min (user-driven, low concurrency)
        "ai_service" to RateLimitConfig(
            permitsPerSecond = 1.0,
            permitsPerMinute = 30,
            randomDelayRange = 0.0..0.5,
        ),
    )

    init {
        // Initialize all default limiters
        for ((key, config) in defaultConfigs) {
            limiters[key] = RateLimiter(
                permitsPerSecond = config.permitsPerSecond,
                permitsPerMinute = config.permitsPerMinute,
                randomDelayRange = config.randomDelayRange,
            )
        }
    }

    // ── Public API ──────────────────────────────────────────────────────

    /**
     * Acquire a permit for the given [platform].
     * If the platform is not yet registered, falls back to the `crawler` config.
     *
     * @param platform Platform key (e.g., "netease", "qq", "kugou", "bilibili")
     */
    suspend fun acquire(platform: String) {
        val limiter = mutex.withLock {
            limiters.getOrPut(platform) {
                val fallback = defaultConfigs["crawler"]!!
                RateLimiter(
                    permitsPerSecond = fallback.permitsPerSecond,
                    permitsPerMinute = fallback.permitsPerMinute,
                    randomDelayRange = fallback.randomDelayRange,
                )
            }
        }
        limiter.acquire(platform)
    }

    /**
     * Update or register rate limit configuration for a platform at runtime.
     *
     * @param platform Platform key
     * @param config New rate limit parameters
     */
    suspend fun updateConfig(platform: String, config: RateLimitConfig) {
        mutex.withLock {
            limiters[platform] = RateLimiter(
                permitsPerSecond = config.permitsPerSecond,
                permitsPerMinute = config.permitsPerMinute,
                randomDelayRange = config.randomDelayRange,
            )
        }
    }

    /**
     * Get the current [RateLimitConfig] for a platform, or null if not registered.
     */
    fun getConfig(platform: String): RateLimitConfig? {
        return defaultConfigs[platform]
    }

    /**
     * Reset all platform limiters to full capacity.
     */
    suspend fun resetAll() {
        mutex.withLock {
            for (limiter in limiters.values) {
                limiter.reset()
            }
        }
    }

    /**
     * List all currently registered platform keys.
     */
    fun registeredPlatforms(): Set<String> = limiters.keys.toSet()
}
