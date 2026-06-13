package com.example.aimusicplayer.network

import com.example.aimusicplayer.network.music.crypto.configureSslBypass
import io.ktor.client.HttpClient
import io.ktor.client.HttpClientConfig
import io.ktor.client.plugins.HttpTimeout
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.plugins.defaultRequest
import io.ktor.client.request.headers
import io.ktor.http.ContentType
import io.ktor.http.contentType
import io.ktor.serialization.kotlinx.json.json
import kotlinx.serialization.json.Json

/**
 * Factory for creating pre-configured [HttpClient] instances with
 * rate limiting, User-Agent rotation, and timeout baked in.
 *
 * All network requests **must** go through clients created by this factory
 * to ensure rate limits are enforced uniformly.
 */
object HttpClientFactory {

    private val defaultJson = Json {
        ignoreUnknownKeys = true
        isLenient = true
        prettyPrint = false
        encodeDefaults = true
    }

    /**
     * Create an [HttpClient] configured for a specific [platform].
     *
     * The returned client will:
     * - Rate-limit all requests according to the platform's config
     * - Rotate User-Agent headers on every request
     * - Enforce a **10-second timeout** on every request (connect + socket + request)
     * - Accept/send JSON by default
     *
     * @param platformKey Platform key ("netease", "qq", "kugou", "bilibili", "crawler")
     * @param additionalConfig Optional additional configuration block
     */
    fun create(
        platformKey: String = "crawler",
        additionalConfig: (HttpClientConfig<*>.() -> Unit)? = null,
    ): HttpClient = HttpClient {
        // ── Rate limiting + UA rotation (must be first) ─────────────
        install(RateLimitPlugin) {
            platform = platformKey
            rotateUserAgent = true
        }

        // ── Request timeout: 10 seconds ─────────────────────────────
        install(HttpTimeout) {
            requestTimeoutMillis = 10_000
            connectTimeoutMillis = 10_000
            socketTimeoutMillis = 10_000
        }

        // ── JSON content negotiation ────────────────────────────────
        install(ContentNegotiation) {
            json(defaultJson)
        }

        // ── Default request headers ─────────────────────────────────
        defaultRequest {
            contentType(ContentType.Application.Json)
            headers {
                append("Accept", "application/json, text/plain, */*")
                append("Accept-Language", "zh-CN,zh;q=0.9,en;q=0.8")
            }
        }

        // ── Apply caller's additional configuration ─────────────────
        additionalConfig?.invoke(this)

        // ── SSL bypass for CDN-backed platforms (Kugou) ─────────────
        if (platformKey == "kugou") {
            configureSslBypass()
        }
    }

    /**
     * Create an [HttpClient] for a specific platform (convenience overload).
     */
    fun createForPlatform(platformKey: String): HttpClient = create(platformKey)
}
