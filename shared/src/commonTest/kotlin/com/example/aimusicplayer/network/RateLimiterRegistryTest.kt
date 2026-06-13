package com.example.aimusicplayer.network

import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class RateLimiterRegistryTest {

    @Test
    fun `all default platforms are registered`() {
        val platforms = RateLimiterRegistry.registeredPlatforms()
        assertTrue(platforms.contains("netease"))
        assertTrue(platforms.contains("qq"))
        assertTrue(platforms.contains("kugou"))
        assertTrue(platforms.contains("bilibili"))
        assertTrue(platforms.contains("crawler"))
        assertTrue(platforms.contains("ai_service"))
    }

    @Test
    fun `acquire for known platform does not throw`() = runTest {
        RateLimiterRegistry.acquire("netease")
    }

    @Test
    fun `acquire for unknown platform falls back to crawler`() = runTest {
        // Should not throw - falls back to crawler config
        RateLimiterRegistry.acquire("unknown_platform_xyz")
    }

    @Test
    fun `getConfig returns config for known platform`() {
        val config = RateLimiterRegistry.getConfig("netease")
        assertNotNull(config)
        assertEquals(2.0, config.permitsPerSecond)
        assertEquals(60, config.permitsPerMinute)
    }

    @Test
    fun `getConfig returns config for QQ`() {
        val config = RateLimiterRegistry.getConfig("qq")
        assertNotNull(config)
        assertEquals(1.5, config.permitsPerSecond)
        assertEquals(45, config.permitsPerMinute)
    }

    @Test
    fun `getConfig returns config for Kugou`() {
        val config = RateLimiterRegistry.getConfig("kugou")
        assertNotNull(config)
        assertEquals(2.0, config.permitsPerSecond)
        assertEquals(50, config.permitsPerMinute)
    }

    @Test
    fun `getConfig returns config for Bilibili`() {
        val config = RateLimiterRegistry.getConfig("bilibili")
        assertNotNull(config)
        assertEquals(3.0, config.permitsPerSecond)
        assertEquals(80, config.permitsPerMinute)
    }

    @Test
    fun `getConfig returns null for unknown platform`() {
        val config = RateLimiterRegistry.getConfig("nonexistent")
        assertEquals(null, config)
    }

    @Test
    fun `updateConfig replaces existing limiter`() = runTest {
        val newConfig = RateLimitConfig(
            permitsPerSecond = 5.0,
            permitsPerMinute = 100,
            randomDelayRange = 0.1..1.0,
        )
        RateLimiterRegistry.updateConfig("test-platform", newConfig)
        // Should be able to acquire from the updated config
        RateLimiterRegistry.acquire("test-platform")
    }

    @Test
    fun `resetAll does not throw`() = runTest {
        // Acquire some tokens first
        repeat(3) { RateLimiterRegistry.acquire("bilibili") }
        RateLimiterRegistry.resetAll()
        // Should not throw
    }

    @Test
    fun `UNLIMITED config has zero limits`() {
        val config = RateLimitConfig.UNLIMITED
        assertEquals(0.0, config.permitsPerSecond)
        assertEquals(0, config.permitsPerMinute)
    }
}
