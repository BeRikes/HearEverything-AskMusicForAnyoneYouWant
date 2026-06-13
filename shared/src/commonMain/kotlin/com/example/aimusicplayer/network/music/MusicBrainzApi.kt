package com.example.aimusicplayer.network.music

import com.example.aimusicplayer.network.HttpClientFactory
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.request.parameter
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * MusicBrainz API 客户端。
 *
 * MusicBrainz 是免费、开源的音乐元数据库，无需 API Key。
 * 用于构建推荐系统的本地歌曲候选池。
 *
 * 频率限制：每秒最多 1 次请求（遵守 MusicBrainz 使用规范）。
 * 用户代理：必须设置标识性 User-Agent（MusicBrainz 强制要求）。
 */
class MusicBrainzApi(
    private val client: HttpClient = HttpClientFactory.create("musicbrainz"),
) {
    companion object {
        private const val BASE = "https://musicbrainz.org/ws/2"
        private const val USER_AGENT = "HearEverything/1.0 (aimusicplayer@example.com)"
        const val PLATFORM_KEY = "musicbrainz"
    }

    // ── Search ──────────────────────────────────────────────────────────

    /**
     * 搜索歌手，返回 MBID（MusicBrainz ID）。
     */
    suspend fun searchArtist(name: String): List<MbArtist> {
        return try {
            val response: MbArtistSearchResponse = client.get("$BASE/artist") {
                parameter("query", "artist:$name")
                parameter("fmt", "json")
                parameter("limit", "5")
                header("User-Agent", USER_AGENT)
            }.body()
            response.artists ?: emptyList()
        } catch (e: Exception) {
            emptyList()
        }
    }

    /**
     * 获取歌手的全部录音（歌曲）。
     */
    suspend fun getArtistRecordings(mbid: String, limit: Int = 50, offset: Int = 0): MbRecordingList {
        return try {
            client.get("$BASE/recording") {
                parameter("artist", mbid)
                parameter("fmt", "json")
                parameter("limit", limit.toString())
                parameter("offset", offset.toString())
                header("User-Agent", USER_AGENT)
            }.body()
        } catch (e: Exception) {
            MbRecordingList(recordings = emptyList(), recordingCount = 0)
        }
    }

    /**
     * 获取歌手的全部专辑（Release）。
     */
    suspend fun browseReleasesByArtist(mbid: String, limit: Int = 25): MbReleaseList {
        return try {
            client.get("$BASE/release") {
                parameter("artist", mbid)
                parameter("fmt", "json")
                parameter("limit", limit.toString())
                header("User-Agent", USER_AGENT)
            }.body()
        } catch (e: Exception) {
            MbReleaseList(releases = emptyList(), releaseCount = 0)
        }
    }

    /**
     * 按标签（流派）搜索录音。
     */
    suspend fun searchByTag(tag: String, limit: Int = 30): MbRecordingList {
        return try {
            client.get("$BASE/recording") {
                parameter("query", "tag:$tag")
                parameter("fmt", "json")
                parameter("limit", limit.toString())
                header("User-Agent", USER_AGENT)
            }.body()
        } catch (e: Exception) {
            MbRecordingList(recordings = emptyList(), recordingCount = 0)
        }
    }
}

// ── Response models ────────────────────────────────────────────────────────

@Serializable
data class MbArtistSearchResponse(
    val artists: List<MbArtist>? = null,
)

@Serializable
data class MbArtist(
    val id: String,
    val name: String,
    val score: Int = 0,
    val tags: List<MbTag>? = null,
    val area: MbArea? = null,
    @SerialName("life-span") val lifeSpan: MbLifeSpan? = null,
)

@Serializable
data class MbTag(
    val name: String,
    val count: Int? = null,
)

@Serializable
data class MbArea(
    val name: String,
)

@Serializable
data class MbLifeSpan(
    val begin: String? = null,
    val ended: Boolean? = null,
)

@Serializable
data class MbRecordingList(
    val recordings: List<MbRecording>,
    @SerialName("recording-count") val recordingCount: Int,
)

@Serializable
data class MbRecording(
    val id: String,
    val title: String,
    val length: Long? = null,
    @SerialName("artist-credit") val artistCredit: List<MbArtistCredit>? = null,
    val tags: List<MbTag>? = null,
    val releases: List<MbReleaseRef>? = null,
    @SerialName("first-release-date") val firstReleaseDate: String? = null,
)

@Serializable
data class MbArtistCredit(
    val name: String,
    val artist: MbArtistRef,
)

@Serializable
data class MbArtistRef(
    val id: String,
    val name: String,
)

@Serializable
data class MbReleaseRef(
    val id: String,
    val title: String,
    val date: String? = null,
)

@Serializable
data class MbReleaseList(
    val releases: List<MbRelease>,
    @SerialName("release-count") val releaseCount: Int,
)

@Serializable
data class MbRelease(
    val id: String,
    val title: String,
    val date: String? = null,
    @SerialName("artist-credit") val artistCredit: List<MbArtistCredit>? = null,
    val tags: List<MbTag>? = null,
    @SerialName("track-count") val trackCount: Int? = null,
)
