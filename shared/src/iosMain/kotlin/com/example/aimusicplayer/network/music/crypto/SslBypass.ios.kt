package com.example.aimusicplayer.network.music.crypto

import io.ktor.client.HttpClientConfig

actual fun HttpClientConfig<*>.configureSslBypass() {
    // iOS SSL bypass: Kugou CDN (mobilecdn.kugou.com) uses Tencent Cloud CDN
    // with a valid commercial SSL certificate. In practice, iOS accepts it
    // without additional configuration.
    // If certificate issues arise, add NSAppTransportSecurity exception for
    // mobilecdn.kugou.com in Info.plist.
}
