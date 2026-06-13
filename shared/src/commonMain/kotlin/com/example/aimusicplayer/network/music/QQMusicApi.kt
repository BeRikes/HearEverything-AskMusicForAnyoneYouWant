package com.example.aimusicplayer.network.music

import com.example.aimusicplayer.network.HttpClientFactory
import com.example.aimusicplayer.network.music.crypto.MusicSignUtils
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.statement.bodyAsText

/**
 * QQ Music (QQ音乐) unofficial API adapter.
 *
 * ## Endpoints
 * - Search: `GET https://c.y.qq.com/soso/fcgi-bin/client_search_cp`
 *   Returns JSONP — wrapper is stripped before parsing.
 * - Play URL: `GET https://u.y.qq.com/cgi-bin/musicu.fcg?data={json}`
 *   Returns vkey + purl → assembled as stream URL.
 *
 * Both endpoints now include a `sign` parameter generated via [MusicSignUtils.qqSign].
 */
class QQMusicApi(
    private val client: HttpClient = HttpClientFactory.create(PLATFORM),
) : MusicPlatformApi {

    override val platform: String = PLATFORM

    // ── Search ──────────────────────────────────────────────────────────

    override suspend fun search(query: String, platform: String): List<SongMetadata> {
        return try {
            // Build params and compute sign
            val params = mapOf(
                "p" to "1",
                "n" to "10",
                "w" to query,
                "format" to "json",
            )
            val sign = MusicSignUtils.qqSign(params)

            val raw = client.get(
                "${SEARCH_BASE}/soso/fcgi-bin/client_search_cp"
            ) {
                url {
                    params.forEach { (k, v) -> parameters.append(k, v) }
                    parameters.append("sign", sign)
                }
                header("Referer", "https://y.qq.com/")
            }.bodyAsText()

            val json = MusicJson
            val parsed: QQSearchResponse = json.decodeFromString(stripJsonp(raw))
            parsed.data?.song?.list.orEmpty().map { it.toSongMetadata() }
        } catch (e: Exception) {
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
            // Request multiple filename formats — free tier typically gets M500/M800 MP3,
            // while C400 M4A works for some songs. The API returns midurlinfo entries
            // only for the formats that are actually available.
            val filenames = when (quality) {
                "lossless" -> listOf("F000$songId.flac", "M500$songId.mp3", "C400$songId.m4a")
                "high"     -> listOf("M500$songId.mp3", "C400$songId.m4a", "M800$songId.mp3")
                else       -> listOf("M500$songId.mp3", "M800$songId.mp3", "C400$songId.m4a")
            }

            val dataPayload = buildString {
                append("{")
                append("\"req\":{\"param\":{\"guid\":\"$guid\"}},")
                append("\"req_0\":{")
                append("\"module\":\"vkey.GetVkeyServer\",")
                append("\"method\":\"CgiGetVkey\",")
                append("\"param\":{")
                append("\"guid\":\"$guid\",")
                append("\"songmid\":[\"$songId\"],")
                append("\"filename\":[")
                filenames.forEachIndexed { i, f ->
                    if (i > 0) append(",")
                    append("\"$f\"")
                }
                append("]")
                append("}}}")
            }

            // Compute sign for the play-URL request
            val sign = MusicSignUtils.qqSign(mapOf("data" to dataPayload))

            // QQ Music returns text/plain content type even for JSON responses
            // — must read as text and decode manually rather than using .body()
            val rawText = client.get(
                "${PLAY_BASE}/cgi-bin/musicu.fcg"
            ) {
                url {
                    parameters.append("data", dataPayload)
                    parameters.append("sign", sign)
                }
                header("Referer", "https://y.qq.com/")
            }.bodyAsText()

            val response: QQMusicPlayUrlResponse = MusicJson.decodeFromString(rawText)

            val vkeyData = response.req_0?.data
            if (vkeyData == null) {
                println("[QQMusicApi] PlayURL: req_0.data is null, raw=${rawText.take(200)}")
                return null
            }
            val vkey = vkeyData.vkey
            val infos = vkeyData.midurlinfo.orEmpty()
            if (infos.isEmpty() || infos.all { it.purl.isNullOrBlank() }) {
                println("[QQMusicApi] PlayURL: no purl in ${infos.size} midurlinfo entries")
                return null
            }
            // Try each midurlinfo entry — first non-empty purl wins
            val uin = vkeyData.uin.takeIf { it.isNotBlank() } ?: "0"
            for (info in infos) {
                val purl = info.purl ?: continue
                val url = if (purl.contains("?")) {
                    // Fix empty uin= params — preserve the original separator (? or &)
                    // to avoid creating a second '?' in the URL (which causes 403).
                    var fixed = purl.replace(Regex("""([?&])uin=&"""), "$1uin=0&")
                        .replace(Regex("""([?&])uin=$"""), "$1uin=0")
                    // If purl has params but no uin= at all, append uin=0
                    if (!fixed.contains("uin=")) {
                        fixed += "&uin=0"
                    }
                    "https://dl.stream.qqmusic.qq.com/$fixed"
                } else {
                    val vkeyParam = if (vkey.isNotBlank()) "&vkey=$vkey" else ""
                    "https://dl.stream.qqmusic.qq.com/$purl?guid=$guid&uin=$uin&fromtag=66$vkeyParam"
                }
                println("[QQMusicApi] PlayURL: $url")
                return url
            }
            null
        } catch (e: Exception) {
            println("[QQMusicApi] PlayURL EXCEPTION: ${e.javaClass.simpleName}: ${e.message}")
            e.printStackTrace()
            null
        }
    }

    // ── Lyrics ──────────────────────────────────────────────────────────

    override suspend fun getLyrics(songId: String, platform: String): String? {
        return try {
            val raw = client.get(
                "${SEARCH_BASE}/lyric/fcgi-bin/fcg_query_lyric_new.fcg"
            ) {
                url {
                    parameters.append("songmid", songId)
                    parameters.append("format", "json")
                    parameters.append("nobase64", "1")
                }
                header("Referer", "https://y.qq.com/")
            }.bodyAsText()

            val json = stripJsonp(raw)
            val response: QQLyricResponse = MusicJson.decodeFromString(json)
            // Check retcode==0 to confirm success
            if (response.retcode != 0 && response.code != 0) return null
            val lrc = response.lyric
            if (!lrc.isNullOrBlank() && lrc != "\n") {
                lrc
            } else {
                response.trans?.takeIf { it.isNotBlank() && it != "\n" }
            }
        } catch (e: Exception) {
            println("[QQMusicApi] LYRICS EXCEPTION: ${e.javaClass.simpleName}: ${e.message}")
            null
        }
    }

    // ── Playlist Detail ─────────────────────────────────────────────────

    /**
     * Fetch playlist detail from QQ Music.
     *
     * Calls GET /qzone/fcg-bin/fcg_ucc_getcdinfo_byids_cp.fcg with
     * the playlist ID and a computed sign. Extracts playlist name, cover,
     * and all track names/artists.
     */
    override suspend fun getPlaylistDetail(playlistId: String): PlaylistDetail? {
        return try {
            val params = mapOf(
                "format" to "json",
                "disstid" to playlistId,
                "type" to "1",
                "utf8" to "1",
            )
            val sign = MusicSignUtils.qqSign(params)

            val rawText = client.get(
                "${SEARCH_BASE}/qzone/fcg-bin/fcg_ucc_getcdinfo_byids_cp.fcg"
            ) {
                url {
                    params.forEach { (k, v) -> parameters.append(k, v) }
                    parameters.append("sign", sign)
                }
                header("Referer", "https://y.qq.com/")
            }.bodyAsText()

            val json = stripJsonp(rawText)
            println("[QQMusicApi] Playlist detail raw (first 500): ${rawText.take(500)}")
            val response: QQPlaylistDetailResponse = MusicJson.decodeFromString(json)

            if (response.code != 0) {
                println("[QQMusicApi] Playlist detail failed code=${response.code}")
                return null
            }

            val cd = response.cdlist?.firstOrNull() ?: return null
            val tracks = cd.songlist.orEmpty().map { song ->
                val artist = song.singer?.joinToString(" / ") { it.name } ?: "未知歌手"
                PlaylistTrack(songName = song.songname, artist = artist)
            }

            PlaylistDetail(
                playlistId = playlistId,
                title = cd.dissname.ifBlank { "QQ音乐歌单" },
                coverUrl = cd.logo?.replace("http://", "https://"),
                trackCount = tracks.size,
                tracks = tracks,
            )
        } catch (e: Exception) {
            println("[QQMusicApi] PLAYLIST DETAIL EXCEPTION: ${e.javaClass.simpleName}: ${e.message}")
            e.printStackTrace()
            null
        }
    }

    // ── Helpers ─────────────────────────────────────────────────────────

    private fun QQSongItem.toSongMetadata(): SongMetadata = SongMetadata(
        songId = songmid,
        songName = songname,
        artist = singer?.joinToString(" / ") { it.name } ?: "未知歌手",
        album = albumname.takeIf { it.isNotBlank() },
        duration = interval,
        coverUrl = albummid.takeIf { it.isNotBlank() }?.let {
            "https://y.qq.com/music/photo_new/T002R300x300M000${it}.jpg"
        },
        platform = PLATFORM,
        fee = pay?.payplay ?: 0,
    )

    // Generate a realistic QQ Music device GUID (10-digit, stable per session)
    private val guid: String = (1000000000L + kotlin.random.Random.nextLong(8000000000L)).toString()

    companion object {
        private const val PLATFORM = "qq"
        private const val SEARCH_BASE = "https://c.y.qq.com"
        private const val PLAY_BASE = "https://u.y.qq.com"
    }
}

// ═══════════════════════════════════════════════════════════════════════════
// JSONP utility — shared across modules that need it
// ═══════════════════════════════════════════════════════════════════════════

/**
 * Strip a JSONP callback wrapper from [raw], returning only the inner JSON object.
 *
 * Handles patterns like:
 * - `callback({...})`
 * - `jsonp1({...})`
 * - `foo_bar({...})`
 *
 * If the response is already plain JSON (starts with `{` or `[`), returns it unchanged.
 * This is important because `format=json` on QQ Music may return unwrapped JSON.
 */
internal fun stripJsonp(raw: String): String {
    val trimmed = raw.trim()

    // Already plain JSON — return as-is (prevents false stripping when JSON data
    // contains parentheses like song names: "Ticking Away (流光似箭)")
    if (trimmed.startsWith("{") || trimmed.startsWith("[")) {
        return trimmed
    }

    // JSONP pattern: callbackName(JSON)
    val firstParen = trimmed.indexOf('(')
    if (firstParen == -1) return trimmed

    // Validate there's a callback name before the paren (alphanumeric, _, $, .)
    val callbackName = trimmed.substring(0, firstParen).trim()
    if (callbackName.isEmpty() || !callbackName.all { it.isLetterOrDigit() || it == '_' || it == '$' || it == '.' }) {
        return trimmed
    }

    // Extract JSON between first '(' and matching last ')'
    // Count parentheses to handle nested parens in JSON strings
    var depth = 0
    var jsonStart = firstParen + 1
    var jsonEnd = trimmed.length - 1
    for (i in firstParen until trimmed.length) {
        when (trimmed[i]) {
            '(' -> depth++
            ')' -> {
                depth--
                if (depth == 0) {
                    jsonEnd = i
                    break
                }
            }
        }
    }
    return trimmed.substring(jsonStart, jsonEnd).trim()
}
