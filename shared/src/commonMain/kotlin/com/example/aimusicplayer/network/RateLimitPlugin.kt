package com.example.aimusicplayer.network

import io.ktor.client.HttpClient
import io.ktor.client.plugins.api.ClientPlugin
import io.ktor.client.plugins.api.createClientPlugin
import io.ktor.client.request.header

/**
 * Configuration for [RateLimitPlugin].
 *
 * @property platform Platform key used for rate limiting (default: "crawler").
 *                    Must match a key registered in [RateLimiterRegistry].
 * @property rotateUserAgent Whether to rotate User-Agent headers on each request.
 *                           Default: true.
 */
class RateLimitPluginConfig {
    /** Platform key for rate limiting. Default: "crawler" */
    var platform: String = "crawler"

    /** Whether to rotate User-Agent on each request. Default: true */
    var rotateUserAgent: Boolean = true
}

/**
 * Ktor HttpClient plugin that integrates rate limiting and User-Agent rotation
 * into every outgoing HTTP request.
 *
 * ## Usage
 * ```kotlin
 * // Uses "crawler" defaults:
 * val client = HttpClient {
 *     install(RateLimitPlugin)
 * }
 *
 * // With explicit platform binding:
 * val neteaseClient = HttpClient {
 *     install(RateLimitPlugin) {
 *         platform = "netease"
 *     }
 * }
 *
 * // Disable UA rotation for one client:
 * val customClient = HttpClient {
 *     install(RateLimitPlugin) {
 *         platform = "qq"
 *         rotateUserAgent = false
 *     }
 * }
 * ```
 *
 * ## Behavior
 * 1. **Before each request**: calls [RateLimiterRegistry.acquire] for the configured
 *    platform, suspending until a token is available.
 * 2. **Header injection**: if [RateLimitPluginConfig.rotateUserAgent] is true,
 *    sets a rotating `User-Agent` header from [UserAgentPool].
 */
val RateLimitPlugin: ClientPlugin<RateLimitPluginConfig> = createClientPlugin(
    name = "RateLimitPlugin",
    createConfiguration = ::RateLimitPluginConfig,
) {
    // Capture the plugin config at install time (capture as val since fields are mutable)
    val config = pluginConfig

    onRequest { request, _ ->
        // 1. Enforce rate limiting — suspends until a token is available
        RateLimiterRegistry.acquire(config.platform)

        // 2. Rotate User-Agent header
        if (config.rotateUserAgent) {
            request.header("User-Agent", UserAgentPool.next())
        }
    }
}
