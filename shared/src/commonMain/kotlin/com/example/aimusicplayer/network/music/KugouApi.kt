package com.example.aimusicplayer.network.music

import com.example.aimusicplayer.cache.Md5Utils
import com.example.aimusicplayer.network.music.crypto.MusicSignUtils
import io.ktor.client.HttpClient
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.statement.bodyAsText
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

/**
 * Kugou Music (酷狗音乐) unofficial API adapter.
 *
 * ## Endpoints
 * - Search: `GET https://mobilecdn.kugou.com/api/v3/search/song` (SSL bypass required)
 * - Play URL: `GET https://wwwapi.kugou.com/yy/index.php`
 * - Playlist: fetches mobile share page HTML, extracts SSR `window.$output` JSON
 */
class KugouApi : MusicPlatformApi {

    override val platform: String = PLATFORM
    private val client: HttpClient = createKugouHttpClient()
    private val dfid: String = MusicSignUtils.getKugouDfid()

    /** kg_mid cookie required by Kugou — MD5 of 4-char random string.
     *  Mobile browsers carry this automatically; without it the server
     *  may reject requests. */
    private val kgMid: String by lazy {
        val chars = "abcdefghijklmnopqrstuvwxyzABCDEFGHIJKLMNOPQRSTUVWXYZ0123456789"
        val random4 = (1..4).map { chars[kotlin.random.Random.nextInt(chars.length)] }.joinToString("")
        Md5Utils.md5(random4)
    }

    // ── Search ──────────────────────────────────────────────────────────

    override suspend fun search(query: String, platform: String): List<SongMetadata> {
        return try {
            val deviceMid = (1000000000L + kotlin.random.Random.nextLong(8000000000L)).toString()
            val params = mapOf(
                "format" to "json",
                "keyword" to query,
                "page" to "1",
                "pagesize" to "10",
                "plat" to "android",
                "clientver" to "12000",
                "mid" to deviceMid,
            )
            val signature = MusicSignUtils.kugouSign(params)

            val rawText = client.get(
                "${SEARCH_BASE}/api/v3/search/song"
            ) {
                url {
                    params.forEach { (k, v) -> parameters.append(k, v) }
                    parameters.append("signature", signature)
                }
                header("Referer", "https://m.kugou.com/")
                header("Cookie", "dfid=$dfid")
                header("User-Agent", "Android")
            }.bodyAsText()

            val response: KugouSearchResponse = MusicJson.decodeFromString(rawText)
            val infos = response.data?.info.orEmpty()
            infos.map { it.toSongMetadata() }
        } catch (e: Exception) {
            println("[KugouApi] SEARCH EXCEPTION: ${e::class.simpleName}: ${e.message}")
            e.printStackTrace()
            emptyList()
        }
    }

    // ── Play URL ────────────────────────────────────────────────────────

    override suspend fun getPlayUrl(
        songId: String,
        platform: String,
        quality: String,
    ): String? {
        return try {
            val (hash, albumId) = parseSongId(songId) ?: return null

            val deviceMid = (1000000000L + kotlin.random.Random.nextLong(8000000000L)).toString()
            val params = mapOf(
                "r" to "play/getdata",
                "hash" to hash,
                "album_id" to albumId,
                "mid" to deviceMid,
                "plat" to "android",
                "dfid" to dfid,
                "clientver" to "12000",
                "srcappid" to "2919",
            )
            val signature = MusicSignUtils.kugouSign(params)

            val rawText = client.get(
                "${PLAY_BASE}/yy/index.php"
            ) {
                url {
                    params.forEach { (k, v) -> parameters.append(k, v) }
                    parameters.append("signature", signature)
                }
                header("Referer", "https://m.kugou.com/")
                header("Cookie", "dfid=$dfid")
            }.bodyAsText()

            val response: KugouPlayUrlResponse = MusicJson.decodeFromString(rawText)
            response.data?.play_url?.takeIf { it.isNotBlank() }
        } catch (e: Exception) {
            println("[KugouApi] PLAY_URL EXCEPTION: ${e::class.simpleName}: ${e.message}")
            e.printStackTrace()
            null
        }
    }

    // ── Lyrics (not supported) ──────────────────────────────────────────

    override suspend fun getLyrics(songId: String, platform: String): String? = null

    // ── Playlist Detail ─────────────────────────────────────────────────

    /**
     * Fetch playlist detail from Kugou Music.
     *
     * Fetches the mobile share page with a realistic mobile User-Agent
     * and extracts the SSR `window.$output` JSON. Mobile browsers get
     * the playlist data without login — desktop browsers are redirected.
     */
    override suspend fun getPlaylistDetail(playlistId: String): PlaylistDetail? {
        return try {
            val cleanId = playlistId.removePrefix("gcid_")

            val rawHtml = client.get(
                "https://m.kugou.com/songlist/gcid_$cleanId/"
            ) {
                header("Referer", "https://m.kugou.com/")
                header("Cookie", "kg_mid=$kgMid; dfid=$dfid; kg_dfid=$dfid; kg_dfid_collect=$dfid")
                header("User-Agent", MOBILE_UA)
            }.bodyAsText()

            // Extract the JSON from window.$output = {...}
            val outputPattern = Regex("""window\.\${'$'}output\s*=\s*(\{[\s\S]*?\});""")
            val outputMatch = outputPattern.find(rawHtml) ?: run {
                println("[KugouApi] window.\$output not found in HTML")
                return null
            }

            val jsonText = outputMatch.groupValues[1]
            val root = MusicJson.parseToJsonElement(jsonText).jsonObject
            val info = root["info"]?.jsonObject ?: run {
                println("[KugouApi] info object not found in \$output")
                return null
            }

            // Playlist metadata
            val listinfo = info["listinfo"]?.jsonObject
            val title = listinfo?.get("name")?.jsonPrimitive?.content ?: "酷狗歌单"
            val coverUrl = listinfo?.get("pic")?.jsonPrimitive?.content
                ?.replace("{size}", "400")
                ?.replace("http://", "https://")

            // Song list
            val songsArray = info["songs"]?.jsonArray
            val songs = songsArray?.mapNotNull { it.jsonObject } ?: emptyList()

            val tracks = songs.mapNotNull { song ->
                val rawName = song["name"]?.jsonPrimitive?.content
                    ?: return@mapNotNull null
                // Kugou SSR name field is formatted as "Artist、Artist - SongName"
                // Strip the artist prefix to get the clean song name
                val songName = rawName.substringAfter(" - ").ifBlank { rawName }
                val artist = song["singerinfo"]?.jsonArray
                    ?.mapNotNull { it.jsonObject["name"]?.jsonPrimitive?.content }
                    ?.joinToString(" / ")
                    ?.ifBlank { null }
                    ?: "未知歌手"
                PlaylistTrack(songName = songName, artist = artist)
            }

            if (tracks.isEmpty()) {
                println("[KugouApi] No tracks parsed from \$output")
                return null
            }

            PlaylistDetail(
                playlistId = playlistId,
                title = title,
                coverUrl = coverUrl,
                trackCount = tracks.size,
                tracks = tracks,
            )
        } catch (e: Exception) {
            println("[KugouApi] PLAYLIST DETAIL EXCEPTION: ${e::class.simpleName}: ${e.message}")
            e.printStackTrace()
            null
        }
    }

    // ── Helpers ─────────────────────────────────────────────────────────

    private fun KugouSongInfo.toSongMetadata(): SongMetadata = SongMetadata(
        songId = "$hash|$album_id",
        songName = songname,
        artist = singername,
        album = album_name.takeIf { it.isNotBlank() },
        duration = duration,
        coverUrl = album_id.takeIf { it.isNotBlank() && it != "0" }?.let {
            "https://imge.kugou.com/stdmusic/300/$it.png"
        },
        platform = PLATFORM,
    )

    private fun parseSongId(songId: String): Pair<String, String>? {
        val parts = songId.split("|")
        return if (parts.size == 2) Pair(parts[0], parts[1]) else null
    }

    companion object {
        private const val PLATFORM = "kugou"
        private const val SEARCH_BASE = "https://mobilecdn.kugou.com"
        private const val PLAY_BASE = "https://wwwapi.kugou.com"
        /** Realistic mobile Chrome UA — Kugou serves SSR data to mobile
         *  browsers but redirects desktop to login. */
        private const val MOBILE_UA = "Mozilla/5.0 (Linux; Android 13; Pixel 7) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Mobile Safari/537.36"
    }
}
