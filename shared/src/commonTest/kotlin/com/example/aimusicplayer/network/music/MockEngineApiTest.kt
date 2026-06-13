package com.example.aimusicplayer.network.music

import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import io.ktor.utils.io.ByteReadChannel
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class MockEngineApiTest {

    // ── QQMusicApi ──────────────────────────────────────────────────

    @Test
    fun qqmusic_search_jsonp_wrapped() = runTest {
        val body = "callback({\"code\":0,\"data\":{\"song\":{\"list\":[{\"mid\":\"j1\",\"songname\":\"J\",\"singer\":[{\"name\":\"K\"}],\"albumname\":\"AL\",\"interval\":150,\"pay\":{\"payplay\":0}}],\"totalnum\":1}}})"
        val api = QQMusicApi(HttpClient(MockEngine { respond(ByteReadChannel(body.toByteArray()), HttpStatusCode.OK, headersOf(HttpHeaders.ContentType, "application/json")) }))
        val r = api.search("test", "qq")
        assertEquals(1, r.size)
    }

    @Test
    fun qqmusic_play_url_with_purl() = runTest {
        val body = """{"code":0,"req_0":{"data":{"midurlinfo":[{"purl":"M800abc.mp3","filename":"M800abc.mp3"}],"testfile2g":"x"}}}"""
        val api = QQMusicApi(HttpClient(MockEngine { respond(ByteReadChannel(body.toByteArray()), HttpStatusCode.OK, headersOf(HttpHeaders.ContentType, "application/json")) }))
        val url = api.getPlayUrl("abc", "qq", "high")
        assertTrue(url != null && url.contains("qq.com"))
    }

    @Test
    fun qqmusic_lyrics_parsing() = runTest {
        val body = """{"code":0,"lyric":"[00:00.00]Test\n[00:01.00]Line2","trans":""}"""
        val api = QQMusicApi(HttpClient(MockEngine { respond(ByteReadChannel(body.toByteArray()), HttpStatusCode.OK, headersOf(HttpHeaders.ContentType, "application/json")) }))
        val l = api.getLyrics("x", "qq")
        assertEquals("[00:00.00]Test\n[00:01.00]Line2", l)
    }

    // ── BilibiliApi ────────────────────────────────────────────────

    @Test
    fun bilibili_empty_results() = runTest {
        val body = """{"code":0,"data":{"result":[],"numResults":0}}"""
        val api = BilibiliApi(HttpClient(MockEngine { respond(ByteReadChannel(body.toByteArray()), HttpStatusCode.OK, headersOf(HttpHeaders.ContentType, "application/json")) }))
        assertTrue(api.search("xxx", "bilibili").isEmpty())
    }

    @Test
    fun bilibili_null_data() = runTest {
        val body = """{"code":-412,"message":"error","data":null}"""
        val api = BilibiliApi(HttpClient(MockEngine { respond(ByteReadChannel(body.toByteArray()), HttpStatusCode.OK, headersOf(HttpHeaders.ContentType, "application/json")) }))
        assertTrue(api.search("x", "bilibili").isEmpty())
    }
}
