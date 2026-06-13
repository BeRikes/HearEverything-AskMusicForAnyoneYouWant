package com.example.aimusicplayer.network.music.crypto

import io.ktor.client.HttpClientConfig
import io.ktor.client.engine.okhttp.OkHttpConfig
import java.security.SecureRandom
import java.security.cert.X509Certificate
import javax.net.ssl.SSLContext
import javax.net.ssl.X509TrustManager

actual fun HttpClientConfig<*>.configureSslBypass() {
    val trustAll = arrayOf<X509TrustManager>(object : X509TrustManager {
        override fun checkClientTrusted(c: Array<X509Certificate>?, a: String?) {}
        override fun checkServerTrusted(c: Array<X509Certificate>?, a: String?) {}
        override fun getAcceptedIssuers(): Array<X509Certificate> = arrayOf()
    })

    try {
        val sslContext = SSLContext.getInstance("TLSv1.2")
        sslContext.init(null, trustAll, SecureRandom())

        @Suppress("UNCHECKED_CAST")
        val okHttpConfig = this as OkHttpConfig
        okHttpConfig.config {
            hostnameVerifier { _, _ -> true }
            sslSocketFactory(sslContext.socketFactory, trustAll[0] as X509TrustManager)
        }
    } catch (e: ClassCastException) {
        // Engine is not OkHttp — configureSslBypass is a no-op
    }
}
