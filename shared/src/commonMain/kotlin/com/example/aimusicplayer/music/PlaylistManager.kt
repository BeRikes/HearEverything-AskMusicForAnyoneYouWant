package com.example.aimusicplayer.music

import com.example.aimusicplayer.network.music.SongMetadata
import com.example.aimusicplayer.import.ImportedPlaylist

data class PlaylistSong(
    val songId: String,
    val songName: String,
    val artist: String,
    val platform: String,
    val coverUrl: String?,
    val playUrl: String?,
    val quality: String,
    val duration: Int = 0,
    val addedAt: Long,
    val order: Int,
    val realName: String? = null,
    val realArtist: String? = null,
)

data class HistorySong(
    val songId: String,
    val songName: String,
    val artist: String,
    val platform: String,
    val coverUrl: String?,
    val playUrl: String?,
    val quality: String,
    val duration: Int = 0,
    val playedAt: Long,
    val realName: String? = null,
    val realArtist: String? = null,
)

enum class PlayMode { SEQUENTIAL, LOOP, REPEAT_ONE }

expect class PlaylistManager {
    /** 添加歌曲到当前歌单末尾 */
    suspend fun addToPlaylist(song: SongMetadata, playUrl: String?)

    /** 添加歌曲到当前歌单最前面（头部） */
    suspend fun addToPlaylistFirst(song: SongMetadata, playUrl: String?)

    /** 从当前歌单移除 */
    suspend fun removeFromPlaylist(songId: String, platform: String)

    /** 拖拽排序：将 fromIndex 的歌曲移动到 toIndex */
    suspend fun moveSong(fromIndex: Int, toIndex: Int)

    /** 获取当前歌单（按 order 排序） */
    suspend fun getPlaylist(): List<PlaylistSong>

    /** 检查歌曲是否已在当前歌单中 */
    suspend fun isInPlaylist(songId: String, platform: String): Boolean

    /** 记录播放历史（播放超过 30s 调用） */
    suspend fun addToHistory(song: SongMetadata, playUrl: String?)

    /** 获取历史歌单（按 playedAt 倒序，最多 maxCount 条） */
    suspend fun getHistory(maxCount: Int): List<HistorySong>

    /** 清空全部历史 */
    suspend fun clearHistory()

    /** 清空当前歌单 */
    suspend fun clearPlaylist()

    /** 获取播放模式 */
    fun getPlayMode(): PlayMode

    /** 设置播放模式 */
    fun setPlayMode(mode: PlayMode)

    // ── Imported playlists ────────────────────────────────────────

    /** 保存一张已导入的歌单（含所有已确认歌曲） */
    suspend fun addImportedPlaylist(playlist: ImportedPlaylist)

    /** 获取所有已导入的歌单 */
    suspend fun getImportedPlaylists(): List<ImportedPlaylist>

    /** 删除一张已导入的歌单 */
    suspend fun removeImportedPlaylist(id: String)

    /** 从已导入歌单中移除一首歌（按索引） */
    suspend fun removeImportedSong(playlistId: String, songIndex: Int)

    // ── Favorites（我的喜欢）───────────────────────────────────

    /** 添加歌曲到收藏夹 */
    suspend fun addToFavorites(song: SongMetadata)

    /** 从收藏夹移除 */
    suspend fun removeFromFavorites(songId: String, platform: String)

    /** 获取收藏夹全部歌曲（按 likedAt 倒序） */
    suspend fun getFavorites(): List<PlaylistSong>

    /** 清空收藏夹 */
    suspend fun clearFavorites()

    /** 拖拽排序收藏夹 */
    suspend fun moveFavoriteSong(fromIndex: Int, toIndex: Int)

    /** 检查歌曲是否已收藏 */
    suspend fun isFavorite(songId: String, platform: String): Boolean
}
