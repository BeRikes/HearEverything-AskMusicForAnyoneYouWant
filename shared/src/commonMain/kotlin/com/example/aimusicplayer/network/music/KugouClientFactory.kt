package com.example.aimusicplayer.network.music

import io.ktor.client.HttpClient

/**
 * Platform-specific [HttpClient] factory for Kugou Music.
 *
 * Kugou's CDN (mobilecdn.kugou.com) sits behind Tencent Cloud CDN
 * (cdn.myqcloud.com / tcdnvod.com), so the SSL certificate doesn't
 * match the hostname. Each platform must configure SSL bypass at
 * the engine level.
 */
expect fun createKugouHttpClient(): HttpClient
