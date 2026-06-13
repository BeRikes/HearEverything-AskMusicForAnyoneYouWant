package com.example.aimusicplayer.network

import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlin.random.Random

/**
 * Thread-safe pool of User-Agent strings with round-robin rotation.
 *
 * Provides a set of common mobile User-Agent strings to mimic
 * real device traffic and avoid bot detection.
 */
object UserAgentPool {

    // ── Mobile User-Agent pool (5+ common UAs) ─────────────────────────

    private val agents: List<String> = listOf(
        // iPhone 15 Pro / iOS 17 — Safari
        "Mozilla/5.0 (iPhone; CPU iPhone OS 17_4 like Mac OS X) AppleWebKit/605.1.15 " +
            "(KHTML, like Gecko) Version/17.4 Mobile/15E148 Safari/604.1",

        // Android 14 / Chrome 122 — Samsung Galaxy S24
        "Mozilla/5.0 (Linux; Android 14; SM-S9210) AppleWebKit/537.36 " +
            "(KHTML, like Gecko) Chrome/122.0.6261.119 Mobile Safari/537.36",

        // Android 13 / Chrome 120 — Xiaomi 13
        "Mozilla/5.0 (Linux; Android 13; 2211133C) AppleWebKit/537.36 " +
            "(KHTML, like Gecko) Chrome/120.0.6099.210 Mobile Safari/537.36",

        // iPhone 14 / iOS 16 — Safari
        "Mozilla/5.0 (iPhone; CPU iPhone OS 16_7 like Mac OS X) AppleWebKit/605.1.15 " +
            "(KHTML, like Gecko) Version/16.6 Mobile/15E148 Safari/604.1",

        // Android 12 / Chrome 119 — Huawei P50
        "Mozilla/5.0 (Linux; Android 12; ABR-AL00) AppleWebKit/537.36 " +
            "(KHTML, like Gecko) Chrome/119.0.6045.163 Mobile Safari/537.36",

        // Android 14 / Chrome 123 — OnePlus 12
        "Mozilla/5.0 (Linux; Android 14; CPH2581) AppleWebKit/537.36 " +
            "(KHTML, like Gecko) Chrome/123.0.6312.40 Mobile Safari/537.36",

        // iPhone 13 / iOS 15 — Safari (slightly older for diversity)
        "Mozilla/5.0 (iPhone; CPU iPhone OS 15_8 like Mac OS X) AppleWebKit/605.1.15 " +
            "(KHTML, like Gecko) Version/15.6.4 Mobile/15E148 Safari/604.1",

        // Android 14 / Chrome Canary 124 — Pixel 8
        "Mozilla/5.0 (Linux; Android 14; Pixel 8) AppleWebKit/537.36 " +
            "(KHTML, like Gecko) Chrome/124.0.6367.42 Mobile Safari/537.36",
    )

    private val mutex = Mutex()
    private var index: Int = 0

    // ── Public API ──────────────────────────────────────────────────────

    /**
     * Returns the next User-Agent string using round-robin rotation.
     * Thread-safe across coroutines.
     */
    suspend fun next(): String = mutex.withLock {
        val agent = agents[index]
        index = (index + 1) % agents.size
        agent
    }

    /**
     * Returns a random User-Agent from the pool.
     * Useful for initial request or when randomization is preferred.
     */
    suspend fun random(): String = mutex.withLock {
        agents[Random.nextInt(agents.size)]
    }

    /**
     * The total number of User-Agent strings in the pool.
     */
    val size: Int get() = agents.size
}
