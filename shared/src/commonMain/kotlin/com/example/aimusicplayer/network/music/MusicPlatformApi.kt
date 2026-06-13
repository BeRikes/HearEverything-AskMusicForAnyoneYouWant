package com.example.aimusicplayer.network.music

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.decodeFromJsonElement

// ═══════════════════════════════════════════════════════════════════════════
// Shared JSON instance — lenient parsing for unofficial APIs
// ═══════════════════════════════════════════════════════════════════════════

internal val MusicJson = Json {
    ignoreUnknownKeys = true
    isLenient = true
    coerceInputValues = true
}

// ═══════════════════════════════════════════════════════════════════════════
// Unified song metadata (returned by all platforms)
// ═══════════════════════════════════════════════════════════════════════════

/**
 * Normalized song metadata returned by search across all platforms.
 * This is the single source of truth passed downstream to caching and playback.
 */
@Serializable
data class SongMetadata(
    val songId: String,              // Platform-native song ID (e.g., Netease "123456", QQ songmid)
    val songName: String,
    val artist: String,
    val album: String? = null,
    val duration: Int = 0,           // seconds
    val coverUrl: String? = null,
    val platform: String,            // "netease", "qq", "kugou", "bilibili"
    val quality: String = "standard", // "standard", "high", "lossless"
    val fee: Int = 0,                // 0=free, >0=VIP/paid (Netease)
    /** 真实歌名（搜索输入框的值，用于修正B站标题解析错误） */
    val realName: String? = null,
    /** 真实歌手名（搜索输入框的值，用于修正B站UP主名冒充歌手的问题） */
    val realArtist: String? = null,
)

// ═══════════════════════════════════════════════════════════════════════════
// Playlist detail models (for playlist import feature)
// ═══════════════════════════════════════════════════════════════════════════

/**
 * Metadata for a playlist fetched from a music platform.
 */
@Serializable
data class PlaylistDetail(
    val playlistId: String,
    val title: String,
    val coverUrl: String? = null,
    val trackCount: Int,
    val tracks: List<PlaylistTrack>,
)

@Serializable
data class PlaylistTrack(
    val songName: String,
    val artist: String,
)

// ═══════════════════════════════════════════════════════════════════════════
// Unified interface — all platform adapters implement this
// ═══════════════════════════════════════════════════════════════════════════

/**
 * Abstraction over per-platform search and playback resolution.
 *
 * Each implementation MUST:
 * - Use the rate-limited HttpClient (via [HttpClientFactory])
 * - Enforce a 10-second timeout per request
 * - Return empty results (never throw) on non-fatal failures so the
 *   search orchestrator can fall through to the next platform
 */
interface MusicPlatformApi {

    /** Platform key ("netease", "qq", "kugou", "bilibili"). */
    val platform: String

    /**
     * Search for songs matching [query] on [platform].
     *
     * @param query  Search keywords (song name + optional artist)
     * @param platform Platform key matching [RateLimiterRegistry] entries
     * @return Ordered list of best-matching songs; empty list on failure
     */
    suspend fun search(query: String, platform: String): List<SongMetadata>

    /**
     * Resolve a playable audio URL for the given [songId].
     *
     * @param songId   Platform-native song identifier
     * @param platform Platform key
     * @param quality  Desired quality level ("standard", "high", "lossless")
     * @return Absolute audio URL, or null if resolution fails
     */
    suspend fun getPlayUrl(songId: String, platform: String, quality: String): String?

    /**
     * Fetch lyrics for the given [songId] on this platform.
     *
     * @param songId   Platform-native song identifier
     * @param platform Platform key
     * @return Lyrics text, or null if unavailable
     */
    suspend fun getLyrics(songId: String, platform: String): String?

    /**
     * Fetch playlist detail by platform-native playlist ID.
     *
     * Default returns null — platforms without playlist API support
     * (or not yet implemented) will gracefully indicate unavailability.
     *
     * @param playlistId Platform-native playlist identifier
     * @return [PlaylistDetail] with track list, or null if unsupported/failed
     */
    suspend fun getPlaylistDetail(playlistId: String): PlaylistDetail? = null
}

// ═══════════════════════════════════════════════════════════════════════════
// NetEase Cloud Music response models
// ═══════════════════════════════════════════════════════════════════════════

@Serializable
internal data class NeteaseSearchResponse(
    val code: Int = -1,
    val result: NeteaseSearchResult? = null,
    val message: String? = null,
)

@Serializable
internal data class NeteaseSearchResult(
    val songs: List<NeteaseSong>? = null,
    val songCount: Int = 0,
)

@Serializable
internal data class NeteaseSong(
    val id: Long = 0,
    val name: String = "",
    val artists: List<NeteaseArtist>? = null,
    val album: NeteaseAlbum? = null,
    val duration: Int = 0, // ms
    val fee: Int = 0, // 0=free, 1=VIP, 4=album, 8=paid
)

@Serializable
internal data class NeteaseArtist(
    val name: String = "",
)

@Serializable
internal data class NeteaseAlbum(
    val name: String? = null,
    val picUrl: String? = null,
)

@Serializable
internal data class NeteaseSongDetailResponse(
    val songs: List<NeteaseSongDetail>? = null,
)

@Serializable
internal data class NeteaseSongDetail(
    val id: Long = 0,
    val name: String = "",
    val artists: List<NeteaseArtist>? = null,
    val album: NeteaseAlbum? = null,
    val duration: Int = 0,
)

@Serializable
internal data class NeteasePlayUrlResponse(
    val data: List<NeteasePlayUrlItem>? = null,
)

@Serializable
internal data class NeteasePlayUrlItem(
    val url: String? = null,
    val br: Int = 0, // bitrate
    val size: Long = 0,
    val type: String? = null,
    val fee: Int = 0, // 0=free, 1=VIP, 4=album, 8=paid
    val freeTrialInfo: FreeTrialInfo? = null, // non-null means preview/truncated
)

@Serializable
internal data class FreeTrialInfo(
    val start: Int = 0,
    val end: Int = 0,
)

// Netease lyrics
@Serializable
internal data class NeteaseLyricResponse(
    val code: Int = -1,
    val lrc: NeteaseLrc? = null,
    val tlyric: NeteaseLrc? = null, // translated lyrics
)

@Serializable
internal data class NeteaseLrc(
    val version: Int = 0,
    val lyric: String? = null,
)

// ═══════════════════════════════════════════════════════════════════════════
// QQ Music response models
// ═══════════════════════════════════════════════════════════════════════════

@Serializable
internal data class QQSearchResponse(
    val data: QQSearchData? = null,
)

@Serializable
internal data class QQSearchData(
    val song: QQSearchSongWrapper? = null,
)

@Serializable
internal data class QQSearchSongWrapper(
    val list: List<QQSongItem>? = null,
)

@Serializable
internal data class QQPayInfo(
    val payplay: Int = 0, // 0=free, 1=VIP
)

@Serializable
internal data class QQSongItem(
    val songmid: String = "",
    val songname: String = "",
    val singer: List<QQSingerItem>? = null,
    val albumname: String = "",
    val albummid: String = "",
    val interval: Int = 0, // seconds
    val pay: QQPayInfo? = null,
)

@Serializable
internal data class QQSingerItem(
    val name: String = "",
)

// QQ Music VKey response — the "req_0" field
@Serializable
internal data class QQMusicPlayUrlResponse(
    val req_0: QQMusicReq0? = null,
)

@Serializable
internal data class QQMusicReq0(
    val data: QQMusicVKeyData? = null,
)

@Serializable
internal data class QQMusicVKeyData(
    val midurlinfo: List<QQMusicUrlInfo>? = null,
    val vkey: String = "",
    val sip: List<String>? = null,
    val uin: String = "",
)

@Serializable
internal data class QQMusicUrlInfo(
    val purl: String? = null,
    val filename: String? = null,
)

// QQ lyrics response — lyrics at top level, not nested in data
@Serializable
internal data class QQLyricResponse(
    val retcode: Int = -1,
    val code: Int = -1,
    val lyric: String? = null,
    val trans: String? = null,
)

// ═══════════════════════════════════════════════════════════════════════════
// QQ Music playlist detail response models
// ═══════════════════════════════════════════════════════════════════════════

@Serializable
internal data class QQPlaylistDetailResponse(
    val code: Int = -1,
    val cdlist: List<QQPlaylistItem>? = null,
)

@Serializable
internal data class QQPlaylistItem(
    val dissname: String = "",
    val logo: String? = null,
    val songlist: List<QQPlaylistSong>? = null,
)

@Serializable
internal data class QQPlaylistSong(
    val songname: String = "",
    val singer: List<QQSingerItem>? = null,
)

// ═══════════════════════════════════════════════════════════════════════════
// Kugou response models
// ═══════════════════════════════════════════════════════════════════════════

@Serializable
internal data class KugouSearchResponse(
    val data: KugouSearchData? = null,
    val status: Int = 0,
)

@Serializable
internal data class KugouSearchData(
    val info: List<KugouSongInfo>? = null,
)

@Serializable
internal data class KugouSongInfo(
    val hash: String = "",
    val songname: String = "",
    val singername: String = "",
    val album_name: String = "",
    val album_id: String = "",
    val duration: Int = 0, // seconds
)

@Serializable
internal data class KugouPlayUrlResponse(
    val data: KugouPlayUrlData? = null,
    val status: Int = 0,
)

@Serializable
internal data class KugouPlayUrlData(
    val play_url: String? = null,
    val img: String? = null,
)

// ═══════════════════════════════════════════════════════════════════════════
// Kugou playlist detail response models (gateway API)
// ═══════════════════════════════════════════════════════════════════════════

@Serializable
internal data class KugouPlaylistGatewayResponse(
    val status: Int = 0,
    val data: KugouPlaylistGatewayData? = null,
)

@Serializable
internal data class KugouPlaylistGatewayData(
    val info: List<KugouPlaylistGatewayInfo>? = null,
    val lists: List<KugouPlaylistSongItem>? = null,
)

@Serializable
internal data class KugouPlaylistGatewayInfo(
    val specialid: Int = 0,
    val specialname: String = "",
    val name: String = "",
    val img: String? = null,
    val imgurl: String? = null,
)

@Serializable
internal data class KugouPlaylistSongItem(
    val hash: String = "",
    val songname: String = "",
    val singername: String = "",
    val album_name: String = "",
    val album_id: String = "",
    val duration: Int = 0,
)

// ═══════════════════════════════════════════════════════════════════════════
// Bilibili response models
// ═══════════════════════════════════════════════════════════════════════════

@Serializable
internal data class BiliSearchResponse(
    val data: BiliSearchData? = null,
)

@Serializable
internal data class BiliSearchData(
    val result: List<BiliSearchResultItem>? = null,
)

@Serializable
internal data class BiliSearchResultItem(
    val bvid: String = "",
    val title: String = "",
    val author: String = "",
    val duration: String = "", // mm:ss format
    val pic: String? = null,
)

@Serializable
internal data class BiliVideoInfoResponse(
    val data: BiliVideoInfoData? = null,
)

@Serializable
internal data class BiliVideoInfoData(
    val cid: Long = 0,
    val title: String = "",
    val pic: String? = null,
)

@Serializable
internal data class BiliPlayUrlResponse(
    val data: BiliPlayUrlData? = null,
)

@Serializable
internal data class BiliPlayUrlData(
    val dash: BiliDash? = null,
    val durl: List<BiliDurlItem>? = null, // legacy format — original FLV quality
)

@Serializable
internal data class BiliDurlItem(
    val url: String? = null,
    val size: Long = 0,
    val length: Long = 0,     // ms
    val backup_url: List<String>? = null,
)

@Serializable
internal data class BiliDash(
    val audio: List<BiliDashStream>? = null,
    val video: List<BiliDashStream>? = null,
)

@Serializable
internal data class BiliDashStream(
    val baseUrl: String? = null,
    val base_url: String? = null, // alternative key
    val id: Int = 0,
    val codecs: String? = null,
    val bandwidth: Int = 0,
    val mimeType: String? = null, // "audio/mp4", "audio/webm"
)
