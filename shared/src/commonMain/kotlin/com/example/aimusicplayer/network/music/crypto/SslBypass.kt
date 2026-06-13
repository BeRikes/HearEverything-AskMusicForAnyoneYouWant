package com.example.aimusicplayer.network.music.crypto

import io.ktor.client.HttpClientConfig

/**
 * Platform-specific SSL bypass for CDN endpoints whose certificates
 * don't match their hostnames (e.g., Kugou's mobilecdn behind Tencent Cloud CDN).
 *
 * - Android actual: installs an OkHttp hostname verifier that accepts all hosts
 * - iOS actual: TODO — use ATS exception or NSAppTransportSecurity config
 */
expect fun HttpClientConfig<*>.configureSslBypass()
