package com.example.aimusicplayer.network

import kotlinx.coroutines.delay
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.datetime.Clock
import kotlin.random.Random
import kotlin.time.Duration.Companion.seconds

/**
 * Token-bucket based rate limiter for controlling request frequency
 * to specific domains or platforms, preventing anti-scraping bans.
 *
 * Supports both per-second and per-minute limits simultaneously.
 * Each [acquire] call consumes one token from both buckets and
 * adds a random delay within [randomDelayRange].
 *
 * @param permitsPerSecond Maximum requests allowed per second (0 = unlimited)
 * @param permitsPerMinute Maximum requests allowed per minute (0 = unlimited)
 * @param randomDelayRange Random delay range in seconds added after each acquire
 */
class RateLimiter(
    private val permitsPerSecond: Double,
    private val permitsPerMinute: Int,
    private val randomDelayRange: ClosedFloatingPointRange<Double> = 0.5..3.0,
) {
    // ── Token bucket state ──────────────────────────────────────────────
    private val mutex = Mutex()

    // Per-second bucket: fractional tokens for smooth rate limiting
    private var secondTokens: Double = if (permitsPerSecond > 0) permitsPerSecond else Double.MAX_VALUE
    private var lastSecondRefill: Long = nowMillis()

    // Per-minute bucket: integer tokens
    private var minuteTokens: Int = if (permitsPerMinute > 0) permitsPerMinute else Int.MAX_VALUE
    private var lastMinuteRefill: Long = nowMillis()

    private val secondRefillIntervalMs: Long = if (permitsPerSecond > 0) {
        (1000.0 / permitsPerSecond).toLong().coerceAtLeast(1)
    } else {
        1
    }

    // ── Public API ──────────────────────────────────────────────────────

    /**
     * Acquire a single permit for [platform].
     *
     * Blocks (suspends) until a token is available from both the per-second
     * and per-minute buckets, then adds a random delay within [randomDelayRange]
     * to further humanize the request pattern.
     *
     * @param platform Identifier used for logging / future per-platform config
     */
    suspend fun acquire(platform: String) {
        mutex.withLock {
            val now = nowMillis()

            // ── Refill second bucket ────────────────────────────────────
            if (permitsPerSecond > 0) {
                val elapsedMs = now - lastSecondRefill
                // Refill using elapsed time — each secondRefillIntervalMs grants one new token
                val newTokens = elapsedMs.toDouble() / secondRefillIntervalMs.toDouble()
                secondTokens = (secondTokens + newTokens).coerceAtMost(permitsPerSecond)
                lastSecondRefill = now

                // Wait if no token available (compute needed wait)
                if (secondTokens < 1.0) {
                    val waitMs = ((1.0 - secondTokens) * secondRefillIntervalMs).toLong() + 1
                    // Release lock while waiting, then re-acquire
                    // We use a two-phase approach: back off and retry
                    // Actual wait is done via delay outside the mutex
                    return@withLock WaitDecision.Wait(waitMs, platform)
                }
                secondTokens -= 1.0
            }

            // ── Refill minute bucket ────────────────────────────────────
            if (permitsPerMinute > 0) {
                val elapsedMs = now - lastMinuteRefill
                if (elapsedMs >= 60_000) {
                    // Full minute passed — reset bucket
                    minuteTokens = permitsPerMinute
                    lastMinuteRefill = now
                }

                if (minuteTokens <= 0) {
                    val waitMs = 60_000 - (now - lastMinuteRefill) + 50
                    return@withLock WaitDecision.Wait(waitMs, platform)
                }
                minuteTokens -= 1
            }

            return@withLock WaitDecision.Proceed(platform)
        }.let { decision ->
            when (decision) {
                is WaitDecision.Proceed -> {
                    // Apply random humanization delay
                    val randomDelay = Random.nextDouble(randomDelayRange.start, randomDelayRange.endInclusive)
                    delay((randomDelay * 1000).toLong())
                }
                is WaitDecision.Wait -> {
                    delay(decision.durationMs)
                    // Recursive retry after waiting
                    acquire(decision.platform)
                }
            }
        }
    }

    /**
     * Reset all buckets to full capacity.
     * Useful after configuration changes or manual intervention.
     */
    suspend fun reset() {
        mutex.withLock {
            val now = nowMillis()
            secondTokens = if (permitsPerSecond > 0) permitsPerSecond else Double.MAX_VALUE
            minuteTokens = if (permitsPerMinute > 0) permitsPerMinute else Int.MAX_VALUE
            lastSecondRefill = now
            lastMinuteRefill = now
        }
    }

    // ── Helpers ─────────────────────────────────────────────────────────

    private fun nowMillis(): Long = Clock.System.now().toEpochMilliseconds()

    /** Internal decision type returned from the locked section */
    private sealed class WaitDecision {
        data class Proceed(val platform: String) : WaitDecision()
        data class Wait(val durationMs: Long, val platform: String) : WaitDecision()
    }
}
