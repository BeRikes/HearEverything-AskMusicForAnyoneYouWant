package com.example.aimusicplayer.network.music

import com.example.aimusicplayer.network.HttpClientFactory
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.request.get
import io.ktor.client.request.header
import kotlin.random.Random

/**
 * Bilibili (B站) unofficial API adapter for audio extraction.
 *
 * Uses anonymous session cookies (buvid3) to appear as a "known device"
 * and unlock higher audio quality tiers without requiring login.
 */
class BilibiliApi(
    private val client: HttpClient = HttpClientFactory.create(PLATFORM),
) : MusicPlatformApi {

    override val platform: String = PLATFORM

    /** Standard Bilibili headers: Referer + Origin + UA + Cookie. */
    private val standardHeaders: suspend (io.ktor.client.request.HttpRequestBuilder).() -> Unit = {
        header("Referer", "https://www.bilibili.com/")
        header("Origin", "https://www.bilibili.com")
        header("Cookie", "buvid3=$BUVID3")
        header("User-Agent",
            "Mozilla/5.0 (Linux; Android 14) AppleWebKit/537.36 " +
            "(KHTML, like Gecko) Chrome/122.0.6261.119 Mobile Safari/537.36")
    }

    // ── Search ──────────────────────────────────────────────────────────

    override suspend fun search(query: String, platform: String): List<SongMetadata> {
        return try {
            val response: BiliSearchResponse = client.get(
                "${BASE}/x/web-interface/search/type"
            ) {
                url {
                    parameters.append("search_type", "video")
                    parameters.append("keyword", query)
                    parameters.append("page", "1")
                }
                standardHeaders(this)
            }.body()

            val results = response.data?.result.orEmpty()
            results.take(5).map { it.toSongMetadata() }
        } catch (e: Exception) {
            e.printStackTrace()
            emptyList()
        }
    }

    // ── Play URL ────────────────────────────────────────────────────────

    override suspend fun getPlayUrl(
        songId: String,   // bvid (e.g., "BV1xx411c7mD")
        platform: String,
        quality: String,
    ): String? {
        return try {
            val cid = resolveCid(songId) ?: return null
            val qn = 127 // Always request max quality (8K tier → best audio)

            // ═══ Strategy 1: DASH audio-only (transcoded, reliable) ═══
            val dash = fetchDash(songId, cid, qn)
            if (dash != null) {
                // Prefer dedicated audio stream with AAC codec
                val audioUrl = selectBestAudio(dash.audio.orEmpty())
                if (!audioUrl.isNullOrBlank()) return audioUrl

                // Fallback: DASH video stream (lowest bandwidth)
                val videoUrl = dash.video.orEmpty()
                    .minByOrNull { it.bandwidth }
                    ?.let { it.baseUrl ?: it.base_url }
                if (!videoUrl.isNullOrBlank()) return videoUrl
            }

            // ═══ Strategy 2: durl (legacy FLV, last resort) ═══
            // For videos without DASH support (very old uploads)
            return fetchDurl(songId, cid, qn)
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }

    // ── Strategy 1: DASH with progressive fnval fallback ──────────────

    private suspend fun fetchDash(bvid: String, cid: Long, qn: Int): BiliDash? {
        val fnvals = listOf(4048, 2080, 80, 16)

        for (fnval in fnvals) {
            val response: BiliPlayUrlResponse = client.get(
                "${BASE}/x/player/playurl"
            ) {
                url {
                    parameters.append("bvid", bvid)
                    parameters.append("cid", cid.toString())
                    parameters.append("qn", qn.toString())
                    parameters.append("fnval", fnval.toString())
                    parameters.append("fnver", "0")
                    parameters.append("fourk", "1")
                    parameters.append("platform", "android")
                }
                standardHeaders(this)
            }.body()

            response.data?.dash?.let { return it }
        }
        return null
    }

    // ── Strategy 2: durl (legacy FLV, last resort) ────────────────────

    private suspend fun fetchDurl(bvid: String, cid: Long, qn: Int): String? {
        val response: BiliPlayUrlResponse = client.get(
            "${BASE}/x/player/playurl"
        ) {
            url {
                parameters.append("bvid", bvid)
                parameters.append("cid", cid.toString())
                parameters.append("qn", qn.toString())
                parameters.append("fnval", "0")    // disable DASH → get legacy durl
                parameters.append("fnver", "0")
                parameters.append("fourk", "1")
                parameters.append("platform", "android")
            }
            standardHeaders(this)
        }.body()

        val durl = response.data?.durl.orEmpty()
        val best = durl.maxByOrNull { it.size }
        return best?.url
    }

    // ── Codec-aware audio selection ─────────────────────────────────────

    /**
     * Select the best audio stream preferring AAC (mp4a) over Dolby (ec-3).
     *
     * Dolby E-AC-3 can cause decoding artifacts/noise on mobile players.
     * AAC at 192 kbps sounds cleaner than E-AC-3 at 320 kbps on Android.
     *
     * Priority: AAC (mp4a) > FLAC > mp4 container > other
     * Within same priority tier, prefer higher bandwidth.
     */
    private fun selectBestAudio(streams: List<BiliDashStream>): String? {
        if (streams.isEmpty()) return null

        val valid = streams.filter {
            !it.baseUrl.isNullOrBlank() || !it.base_url.isNullOrBlank()
        }
        if (valid.isEmpty()) return null

        val best = valid.maxWithOrNull(
            compareBy<BiliDashStream> {
                val codec = (it.codecs ?: "").lowercase()
                val mime = (it.mimeType ?: "").lowercase()
                when {
                    "mp4a" in codec -> 3     // AAC — cleanest for mobile
                    "flac" in codec -> 2      // FLAC — lossless, good
                    "mp4" in mime -> 1        // mp4 container (likely AAC)
                    else -> 0                  // Dolby ec-3 / webm / unknown
                }
            }.thenBy { it.bandwidth }
        )

        return best?.baseUrl ?: best?.base_url
    }

    // ── Lyrics (not supported) ──────────────────────────────────────────

    override suspend fun getLyrics(songId: String, platform: String): String? = null

    // ── Helpers ─────────────────────────────────────────────────────────

    private suspend fun resolveCid(bvid: String): Long? {
        return try {
            val response: BiliVideoInfoResponse = client.get(
                "${BASE}/x/web-interface/view"
            ) {
                url { parameters.append("bvid", bvid) }
                standardHeaders(this)
            }.body()

            val cid = response.data?.cid ?: return null
            if (cid == 0L) null else cid
        } catch (e: Exception) {
            null
        }
    }

    private fun BiliSearchResultItem.toSongMetadata(): SongMetadata = SongMetadata(
        songId = bvid,
        songName = title.replace("<em class=\"keyword\">", "").replace("</em>", ""),
        artist = author,
        duration = parseDuration(duration),
        coverUrl = pic?.let { "https:$it" },
        platform = PLATFORM,
    )

    /** Parse "mm:ss" or "hh:mm:ss" duration string to seconds. */
    private fun parseDuration(dur: String): Int {
        val parts = dur.trim().split(":")
        return when (parts.size) {
            3 -> (parts[0].toIntOrNull() ?: 0) * 3600 +
                 (parts[1].toIntOrNull() ?: 0) * 60 +
                 (parts[2].toIntOrNull() ?: 0)
            2 -> (parts[0].toIntOrNull() ?: 0) * 60 +
                 (parts[1].toIntOrNull() ?: 0)
            else -> 0
        }
    }

    /** Map quality label to Bilibili audio qn (quality number). */
    private fun qualityToQn(quality: String): Int = when (quality) {
        "lossless" -> 30280  // 320 kbps AAC / Hi-Res
        "high"     -> 30216  // 128 kbps AAC
        else       -> 30232  // 192 kbps AAC
    }

    companion object {
        private const val PLATFORM = "bilibili"
        private const val BASE = "https://api.bilibili.com"

        /** Persistent anonymous device ID — makes the server treat us as a "known" device. */
        private val BUVID3 = buildBuvid3()

        private fun buildBuvid3(): String {
            val hex = (1..32).map { "0123456789ABCDEF"[Random.nextInt(16)] }.joinToString("")
            return "XY$hex"
        }
    }
}
