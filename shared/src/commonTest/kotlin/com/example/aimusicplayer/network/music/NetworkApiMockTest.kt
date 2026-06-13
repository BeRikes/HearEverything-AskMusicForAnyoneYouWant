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
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Network API tests using Ktor MockEngine.
 * Tests error handling paths and response parsing for all 4 music platforms.
 */
class NetworkApiMockTest {

    // ── NeteaseApi ──────────────────────────────────────────────────

    @Test
    fun netease_search_error_returns_empty() = runTest {
        val engine = MockEngine {
            respond(
                content = ByteReadChannel(""),
                status = HttpStatusCode.InternalServerError,
                headers = headersOf(HttpHeaders.ContentType, "application/json")
            )
        }
        val api = NeteaseApi(HttpClient(engine))
        assertTrue(api.search("test", "netease").isEmpty())
    }

    @Test
    fun netease_play_url_error_returns_null() = runTest {
        val engine = MockEngine {
            respond(
                content = ByteReadChannel(""),
                status = HttpStatusCode.InternalServerError,
                headers = headersOf(HttpHeaders.ContentType, "application/json")
            )
        }
        val api = NeteaseApi(HttpClient(engine))
        assertNull(api.getPlayUrl("123", "netease", "standard"))
    }

    @Test
    fun netease_lyrics_error_returns_null() = runTest {
        val engine = MockEngine {
            respond(
                content = ByteReadChannel(""),
                status = HttpStatusCode.InternalServerError,
                headers = headersOf(HttpHeaders.ContentType, "application/json")
            )
        }
        val api = NeteaseApi(HttpClient(engine))
        assertNull(api.getLyrics("123", "netease"))
    }

    @Test
    fun netease_playlist_error_returns_null() = runTest {
        val engine = MockEngine {
            respond(
                content = ByteReadChannel(""),
                status = HttpStatusCode.InternalServerError,
                headers = headersOf(HttpHeaders.ContentType, "application/json")
            )
        }
        val api = NeteaseApi(HttpClient(engine))
        assertNull(api.getPlaylistDetail("123"))
    }

    @Test
    fun netease_platform_name() {
        assertEquals("netease", NeteaseApi().platform)
    }

    // ── QQMusicApi ──────────────────────────────────────────────────

    @Test
    fun qqmusic_search_error_returns_empty() = runTest {
        val engine = MockEngine {
            respond(
                content = ByteReadChannel(""),
                status = HttpStatusCode.InternalServerError,
                headers = headersOf(HttpHeaders.ContentType, "application/json")
            )
        }
        val api = QQMusicApi(HttpClient(engine))
        assertTrue(api.search("test", "qq").isEmpty())
    }

    @Test
    fun qqmusic_play_url_error_returns_null() = runTest {
        val engine = MockEngine {
            respond(
                content = ByteReadChannel(""),
                status = HttpStatusCode.InternalServerError,
                headers = headersOf(HttpHeaders.ContentType, "application/json")
            )
        }
        val api = QQMusicApi(HttpClient(engine))
        assertNull(api.getPlayUrl("test", "qq", "standard"))
    }

    @Test
    fun qqmusic_lyrics_error_returns_null() = runTest {
        val engine = MockEngine {
            respond(
                content = ByteReadChannel(""),
                status = HttpStatusCode.InternalServerError,
                headers = headersOf(HttpHeaders.ContentType, "application/json")
            )
        }
        val api = QQMusicApi(HttpClient(engine))
        assertNull(api.getLyrics("test", "qq"))
    }

    @Test
    fun qqmusic_platform_name() {
        assertEquals("qq", QQMusicApi().platform)
    }

    @Test
    fun qqmusic_playlist_error_returns_null() = runTest {
        val engine = MockEngine {
            respond(
                content = ByteReadChannel(""),
                status = HttpStatusCode.InternalServerError,
                headers = headersOf(HttpHeaders.ContentType, "application/json")
            )
        }
        val api = QQMusicApi(HttpClient(engine))
        assertNull(api.getPlaylistDetail("123"))
    }

    @Test
    fun strip_jsonp_handles_all_formats() {
        assertEquals("{}", stripJsonp("callback({})"))
        assertEquals("[]", stripJsonp("jsonp123([])"))
        assertEquals("{}", stripJsonp("jsonp({})"))
        assertEquals("", stripJsonp(""))
        assertEquals("""{"a":1}""", stripJsonp("""{"a":1}"""))
    }

    // ── BilibiliApi ────────────────────────────────────────────────

    @Test
    fun bilibili_search_error_returns_empty() = runTest {
        val engine = MockEngine {
            respond(
                content = ByteReadChannel(""),
                status = HttpStatusCode.InternalServerError,
                headers = headersOf(HttpHeaders.ContentType, "application/json")
            )
        }
        val api = BilibiliApi(HttpClient(engine))
        assertTrue(api.search("test", "bilibili").isEmpty())
    }

    @Test
    fun bilibili_lyrics_always_returns_null() = runTest {
        val api = BilibiliApi()
        assertNull(api.getLyrics("any", "bilibili"))
    }

    @Test
    fun bilibili_platform_name() {
        assertEquals("bilibili", BilibiliApi().platform)
    }

    @Test
    fun bilibili_play_url_no_match_returns_null() = runTest {
        val engine = MockEngine {
            respond(
                content = ByteReadChannel(""),
                status = HttpStatusCode.NotFound,
                headers = headersOf(HttpHeaders.ContentType, "application/json")
            )
        }
        val api = BilibiliApi(HttpClient(engine))
        assertNull(api.getPlayUrl("BVnotfound", "bilibili", "standard"))
    }
}
