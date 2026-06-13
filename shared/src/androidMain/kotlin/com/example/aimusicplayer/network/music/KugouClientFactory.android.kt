package com.example.aimusicplayer.network.music

import com.example.aimusicplayer.network.RateLimitPlugin
import io.ktor.client.HttpClient
import io.ktor.client.engine.okhttp.OkHttp
import io.ktor.client.plugins.HttpTimeout
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.plugins.defaultRequest
import io.ktor.client.request.headers
import io.ktor.http.ContentType
import io.ktor.http.contentType
import io.ktor.serialization.kotlinx.json.json
import java.security.SecureRandom
import java.security.cert.X509Certificate
import javax.net.ssl.SSLContext
import javax.net.ssl.X509TrustManager
import kotlinx.serialization.json.Json

actual fun createKugouHttpClient(): HttpClient {
    val trustAll = arrayOf<X509TrustManager>(object : X509TrustManager {
        override fun checkClientTrusted(c: Array<X509Certificate>?, a: String?) {}
        override fun checkServerTrusted(c: Array<X509Certificate>?, a: String?) {}
        override fun getAcceptedIssuers(): Array<X509Certificate> = arrayOf()
    })
    val sslContext = SSLContext.getInstance("TLSv1.2").apply {
        init(null, trustAll, SecureRandom())
    }

    return HttpClient(OkHttp) {
        engine {
            config {
                hostnameVerifier { _, _ -> true }
                sslSocketFactory(sslContext.socketFactory, trustAll[0] as X509TrustManager)
            }
        }

        install(RateLimitPlugin) {
            platform = "kugou"
            rotateUserAgent = true
        }
        install(HttpTimeout) {
            requestTimeoutMillis = 10_000
            connectTimeoutMillis = 10_000
            socketTimeoutMillis = 10_000
        }
        install(ContentNegotiation) {
            json(Json {
                ignoreUnknownKeys = true
                isLenient = true
                prettyPrint = false
                encodeDefaults = true
            })
        }
        defaultRequest {
            contentType(ContentType.Application.Json)
            headers {
                append("Accept", "application/json, text/plain, */*")
                append("Accept-Language", "zh-CN,zh;q=0.9,en;q=0.8")
            }
        }
    }
}
