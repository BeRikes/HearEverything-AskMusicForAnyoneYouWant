package com.example.aimusicplayer.network.music

import com.example.aimusicplayer.network.HttpClientFactory
import com.example.aimusicplayer.network.music.crypto.NeteaseEncryptor
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.request.header
import io.ktor.client.request.forms.submitForm
import io.ktor.client.statement.bodyAsText
import io.ktor.http.parameters
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put

/**
 * NetEase Cloud Music (网易云音乐) unofficial API adapter.
 *
 * Uses AES-128-CBC (double encryption) + custom RSA to produce the `params`
 * and `encSecKey` fields required by the /weapi/ endpoints.
 *
 * ## Endpoints
 * - Search: `POST https://music.163.com/weapi/search/get` + song detail for covers
 * - Play URL: `POST https://music.163.com/weapi/song/enhance/player/url/v1`
 *
 * ## Notes
 * - Returns direct .mp3 URLs when available
 * - VIP/paid songs are filtered out (they return only 30s previews)
 * - All calls catch exceptions and return empty/null results on failure
 * - Rate-limited via the shared [RateLimitPlugin]
 */
class NeteaseApi(
    private val client: HttpClient = HttpClientFactory.create(PLATFORM),
) : MusicPlatformApi {

    override val platform: String = PLATFORM

    // ── Search ──────────────────────────────────────────────────────────

    override suspend fun search(query: String, platform: String): List<SongMetadata> {
        return try {
            val body = buildJsonObject {
                put("s", query)
                put("type", 1)
                put("offset", 0)
                put("limit", 30)
                put("csrf_token", "")
            }

            val encrypted = NeteaseEncryptor.encrypt(body.toString())

            val httpResponse = client.submitForm(
                url = "${BASE}/weapi/search/get",
                formParameters = parameters {
                    append("params", encrypted.params)
                    append("encSecKey", encrypted.encSecKey)
                },
            ) {
                header("Referer", "$BASE/")
                header("Origin", BASE)
                header("Cookie", "os=pc; appver=2.9.7; MUSIC_U=; __csrf=")
            }

            val responseText = httpResponse.bodyAsText()
            val response: NeteaseSearchResponse = MusicJson.decodeFromString(responseText)
            val rawSongs = response.result?.songs.orEmpty()
            val songs = rawSongs.map { it.toSongMetadata() }

            // Batch-fetch album covers via song detail API
            if (songs.isNotEmpty()) {
                val songIds = rawSongs.map { it.id.toString() }
                val covers = fetchSongCovers(songIds)
                if (covers.isNotEmpty()) {
                    return songs.map { s ->
                        covers[s.songId]?.let { s.copy(coverUrl = it) } ?: s
                    }
                }
            }
            songs
        } catch (e: Exception) {
            println("[NeteaseApi] SEARCH EXCEPTION: ${e.javaClass.simpleName}: ${e.message}")
            e.printStackTrace()
            emptyList()
        }
    }

    // ── Play URL ────────────────────────────────────────────────────────

    /** Sealed result for a single quality/encode fetch attempt. */
    private sealed class FetchResult {
        data class Success(val url: String) : FetchResult()
        /** Netease returned freeTrialInfo — definitive VIP, not just heuristic. */
        data object PreviewDetected : FetchResult()
        /** Other failure — network, empty result, size heuristic. Continue trying. */
        data object Failed : FetchResult()
    }

    override suspend fun getPlayUrl(
        songId: String,
        platform: String,
        quality: String,
    ): String? {
        return try {
            val ladder = qualityLadder(quality)
            var previewStreak = 0 // consecutive PreviewDetected counts
            for ((level, encodeType) in ladder) {
                when (val result = fetchPlayUrl(songId, level, encodeType)) {
                    is FetchResult.Success -> {
                        println("[NeteaseApi] Full audio: $songId level=$level encode=$encodeType")
                        return result.url
                    }
                    is FetchResult.PreviewDetected -> {
                        previewStreak++
                        if (previewStreak >= 1) {
                            println("[NeteaseApi] VIP confirmed for $songId (freeTrialInfo detected) — aborting")
                            return null
                        }
                    }
                    is FetchResult.Failed -> {
                        previewStreak = 0 // reset — not a definitive preview
                    }
                }
            }
            null
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }

    /** Try a single quality/encode combination. */
    private suspend fun fetchPlayUrl(
        songId: String,
        level: String,
        encodeType: String,
    ): FetchResult {
        val body = buildJsonObject {
            put("ids", "[$songId]")
            put("level", level)
            put("encodeType", encodeType)
            put("csrf_token", "")
        }

        val encrypted = NeteaseEncryptor.encrypt(body.toString())

        val response: NeteasePlayUrlResponse = client.submitForm(
            url = "${BASE}/weapi/song/enhance/player/url/v1",
            formParameters = parameters {
                append("params", encrypted.params)
                append("encSecKey", encrypted.encSecKey)
            },
        ) {
            header("Referer", "$BASE/")
            header("Origin", BASE)
        }.body()

        val matched = response.data
            ?.firstOrNull { item ->
                !item.url.isNullOrBlank() && item.size > 0 && item.br > 0
            } ?: return FetchResult.Failed

        // Definitive VIP: Netease explicitly marks it with freeTrialInfo
        if (matched.freeTrialInfo != null) {
            println("[NeteaseApi] Preview (VIP) for $songId at $level/$encodeType trial=${matched.freeTrialInfo}")
            return FetchResult.PreviewDetected
        }

        // Heuristic: small file + VIP fee — likely preview but not definitive
        if (matched.size < 500_000L && matched.fee > 0) {
            println("[NeteaseApi] Preview (size) for $songId at $level/$encodeType size=${matched.size} fee=${matched.fee}")
            return FetchResult.Failed
        }

        return FetchResult.Success(matched.url!!)
    }

    /**
     * Quality ladder — reduced to 3 most likely combinations.
     * Free songs typically succeed on entry #1; VIP is caught by [getPlayUrl]
     * after 3 consecutive [FetchResult.PreviewDetected] results.
     */
    private fun qualityLadder(requestedQuality: String): List<Pair<String, String>> {
        return when (requestedQuality) {
            "lossless" -> listOf(
                "lossless" to "flac",
                "exhigh" to "flac",
                "standard" to "mp3",
            )
            "high" -> listOf(
                "higher" to "mp3",
                "standard" to "flac",
                "standard" to "mp3",
            )
            else -> listOf(
                "standard" to "mp3",
                "standard" to "flac",
                "higher" to "mp3",
            )
        }
    }

    // ── Lyrics ───────────────────────────────────────────────────────────

    override suspend fun getLyrics(songId: String, platform: String): String? {
        return try {
            val body = buildJsonObject {
                put("id", songId)
                put("lv", -1)
                put("tv", -1)
                put("csrf_token", "")
            }

            val encrypted = NeteaseEncryptor.encrypt(body.toString())

            val response: NeteaseLyricResponse = client.submitForm(
                url = "${BASE}/weapi/song/lyric",
                formParameters = parameters {
                    append("params", encrypted.params)
                    append("encSecKey", encrypted.encSecKey)
                },
            ) {
                header("Referer", "$BASE/")
                header("Origin", BASE)
                header("Cookie", "os=pc; appver=2.9.7; MUSIC_U=; __csrf=")
            }.body()

            val lrcText = response.lrc?.lyric
            if (!lrcText.isNullOrBlank()) {
                lrcText
            } else {
                response.tlyric?.lyric
            }
        } catch (e: Exception) {
            println("[NeteaseApi] LYRICS EXCEPTION: ${e.javaClass.simpleName}: ${e.message}")
            null
        }
    }

    // ── Playlist Detail ─────────────────────────────────────────────────

    /**
     * Fetch playlist detail from Netease Cloud Music.
     *
     * Calls POST /weapi/v3/playlist/detail with encrypted params.
     * Extracts playlist name, cover, and all track names/artists.
     * Handles pagination: fetches up to 1000 tracks.
     */
    override suspend fun getPlaylistDetail(playlistId: String): PlaylistDetail? {
        return try {
            val body = buildJsonObject {
                put("id", playlistId)
                put("n", 100000)
                put("s", 0)
                put("csrf_token", "")
            }

            val encrypted = NeteaseEncryptor.encrypt(body.toString())

            val responseText = client.submitForm(
                url = "${BASE}/weapi/v3/playlist/detail",
                formParameters = parameters {
                    append("params", encrypted.params)
                    append("encSecKey", encrypted.encSecKey)
                },
            ) {
                header("Referer", "$BASE/")
                header("Origin", BASE)
                header("Cookie", "os=pc; appver=2.9.7; MUSIC_U=; __csrf=")
            }.bodyAsText()

            val root = MusicJson.parseToJsonElement(responseText).jsonObject
            val code = root["code"]?.jsonPrimitive?.content?.toIntOrNull() ?: -1
            if (code != 200) {
                println("[NeteaseApi] Playlist detail failed code=$code")
                return null
            }

            val playlist = root["playlist"]?.jsonObject ?: return null
            val title = playlist["name"]?.jsonPrimitive?.content ?: "未知歌单"
            val coverUrl = playlist["coverImgUrl"]?.jsonPrimitive?.content
                ?.replace("http://", "https://")
            val trackIds = playlist["trackIds"]?.jsonArray.orEmpty()

            if (trackIds.isEmpty()) {
                return PlaylistDetail(
                    playlistId = playlistId,
                    title = title,
                    coverUrl = coverUrl,
                    trackCount = 0,
                    tracks = emptyList(),
                )
            }

            // Batch-fetch song details to get actual names and artists
            val trackIdList = trackIds.mapNotNull {
                it.jsonObject["id"]?.jsonPrimitive?.content
            }
            val allTracks = mutableListOf<PlaylistTrack>()
            trackIdList.chunked(500).forEach { batch ->
                val batchTracks = fetchPlaylistSongDetails(batch)
                allTracks.addAll(batchTracks)
            }

            PlaylistDetail(
                playlistId = playlistId,
                title = title,
                coverUrl = coverUrl,
                trackCount = allTracks.size,
                tracks = allTracks,
            )
        } catch (e: Exception) {
            println("[NeteaseApi] PLAYLIST DETAIL EXCEPTION: ${e.javaClass.simpleName}: ${e.message}")
            e.printStackTrace()
            null
        }
    }

    /**
     * Fetch song names and artists for a batch of playlist track IDs.
     * Calls /weapi/v3/song/detail (same endpoint as fetchSongCovers).
     */
    private suspend fun fetchPlaylistSongDetails(songIds: List<String>): List<PlaylistTrack> {
        if (songIds.isEmpty()) return emptyList()
        return try {
            val idArray = songIds.joinToString(",") { "{\"id\":\"$it\"}" }
            val body = buildJsonObject {
                put("c", "[$idArray]")
                put("csrf_token", "")
            }

            val encrypted = NeteaseEncryptor.encrypt(body.toString())

            val responseText = client.submitForm(
                url = "${BASE}/weapi/v3/song/detail",
                formParameters = parameters {
                    append("params", encrypted.params)
                    append("encSecKey", encrypted.encSecKey)
                },
            ) {
                header("Referer", "$BASE/")
                header("Origin", BASE)
            }.bodyAsText()

            val json = MusicJson
            val root = json.parseToJsonElement(responseText).jsonObject
            val songs = root["songs"]?.jsonArray.orEmpty()

            songs.map { songElement ->
                val obj = songElement.jsonObject
                val name = obj["name"]?.jsonPrimitive?.content ?: "未知歌曲"
                val ar = obj["ar"]?.jsonArray.orEmpty()
                val artist = ar.joinToString(" / ") {
                    it.jsonObject["name"]?.jsonPrimitive?.content ?: ""
                }.ifBlank { "未知艺术家" }
                PlaylistTrack(songName = name, artist = artist)
            }
        } catch (e: Exception) {
            println("[NeteaseApi] Playlist song detail batch failed: ${e.message}")
            songIds.map { PlaylistTrack(songName = "未知歌曲", artist = "未知艺术家") }
        }
    }

    // ── Song detail (for covers) ────────────────────────────────────────

    /**
     * Fetch album covers for a batch of song IDs via the song detail API.
     * Returns map of songId → coverUrl.
     */
    suspend fun fetchSongCovers(songIds: List<String>): Map<String, String> {
        if (songIds.isEmpty()) return emptyMap()
        return try {
            val idArray = songIds.joinToString(",") { "{\"id\":\"$it\"}" }
            val body = buildJsonObject {
                put("c", "[$idArray]")
                put("csrf_token", "")
            }

            val encrypted = NeteaseEncryptor.encrypt(body.toString())

            val responseText = client.submitForm(
                url = "${BASE}/weapi/v3/song/detail",
                formParameters = parameters {
                    append("params", encrypted.params)
                    append("encSecKey", encrypted.encSecKey)
                },
            ) {
                header("Referer", "$BASE/")
                header("Origin", BASE)
            }.bodyAsText()

            val result = mutableMapOf<String, String>()
            val json = MusicJson
            val root = json.parseToJsonElement(responseText).jsonObject
            val songs = root["songs"]?.jsonArray.orEmpty()
            for (song in songs) {
                val obj = song.jsonObject
                val id = obj["id"]?.jsonPrimitive?.content ?: continue
                val al = obj["al"]?.jsonObject
                val pic = al?.get("picUrl")?.jsonPrimitive?.content
                    ?.replace("http://", "https://")
                if (pic != null) result[id] = pic
            }
            result
        } catch (e: Exception) {
            println("[NeteaseApi] SONG DETAIL EXCEPTION: ${e.javaClass.simpleName}: ${e.message}")
            emptyMap()
        }
    }

    // ── Helpers ─────────────────────────────────────────────────────────

    private fun NeteaseSong.toSongMetadata(): SongMetadata {
        val cover = album?.picUrl?.replace("http://", "https://")
        if (cover == null) {
            println("[NeteaseApi] No cover for songId=$id, name=$name, album=${album?.name}")
        }
        return SongMetadata(
            songId = id.toString(),
            songName = name,
            artist = artists?.joinToString(" / ") { it.name } ?: "未知艺术家",
            album = album?.name,
            duration = duration / 1000, // ms → seconds
            coverUrl = cover,
            platform = PLATFORM,
            fee = fee,
        )
    }

    companion object {
        private const val PLATFORM = "netease"
        private const val BASE = "https://music.163.com"
    }
}
