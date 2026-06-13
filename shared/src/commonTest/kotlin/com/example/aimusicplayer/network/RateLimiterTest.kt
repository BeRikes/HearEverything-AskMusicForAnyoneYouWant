package com.example.aimusicplayer.network

import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import kotlin.test.Test
import kotlin.test.assertTrue

class RateLimiterTest {

    @Test
    fun `acquire with unlimited config does not block`(): Unit = kotlinx.coroutines.test.runTest {
        val limiter = RateLimiter(
            permitsPerSecond = 0.0,   // unlimited
            permitsPerMinute = 0,     // unlimited
            randomDelayRange = 0.0..0.001, // minimal delay for testing
        )
        val start = kotlinx.datetime.Clock.System.now().toEpochMilliseconds()
        limiter.acquire("test")
        val elapsed = kotlinx.datetime.Clock.System.now().toEpochMilliseconds() - start
        // With unlimited config and tiny delay, should complete quickly
        assertTrue(elapsed < 1000, "Should complete in under 1s, took ${elapsed}ms")
    }

    @Test
    fun `acquire with per-second limit allows requests`(): Unit = kotlinx.coroutines.test.runTest {
        val limiter = RateLimiter(
            permitsPerSecond = 100.0,  // very generous
            permitsPerMinute = 0,      // unlimited
            randomDelayRange = 0.0..0.001,
        )
        limiter.acquire("test")
        // Should not throw
    }

    @Test
    fun `acquire with per-minute limit only`(): Unit = kotlinx.coroutines.test.runTest {
        val limiter = RateLimiter(
            permitsPerSecond = 0.0,    // unlimited per second
            permitsPerMinute = 1000,   // generous per minute
            randomDelayRange = 0.0..0.001,
        )
        limiter.acquire("test")
        // Should not throw
    }

    @Test
    fun `multiple acquires rapidly consume tokens`(): Unit = kotlinx.coroutines.test.runTest {
        val limiter = RateLimiter(
            permitsPerSecond = 50.0,
            permitsPerMinute = 1000,
            randomDelayRange = 0.0..0.001,
        )
        // Acquire many tokens rapidly
        repeat(20) {
            limiter.acquire("test")
        }
        // Should complete without hanging
    }

    @Test
    fun `reset restores tokens`(): Unit = kotlinx.coroutines.test.runTest {
        val limiter = RateLimiter(
            permitsPerSecond = 10.0,
            permitsPerMinute = 500,
            randomDelayRange = 0.0..0.001,
        )
        repeat(5) { limiter.acquire("test") }
        limiter.reset()
        // After reset, should be able to acquire more immediately
        repeat(5) { limiter.acquire("test") }
    }

    @Test
    fun `concurrent acquires work`(): Unit = kotlinx.coroutines.test.runTest {
        val limiter = RateLimiter(
            permitsPerSecond = 100.0,
            permitsPerMinute = 1000,
            randomDelayRange = 0.0..0.001,
        )
        coroutineScope {
            repeat(10) {
                launch {
                    limiter.acquire("test-$it")
                }
            }
        }
    }
}
