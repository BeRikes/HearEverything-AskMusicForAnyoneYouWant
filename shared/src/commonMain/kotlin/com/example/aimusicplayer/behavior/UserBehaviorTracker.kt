package com.example.aimusicplayer.behavior

import com.example.aimusicplayer.network.music.SongMetadata

/**
 * 用户行为追踪器。
 *
 * 监听播放器状态变化，记录用户的播放/完成/跳过/喜欢/下载行为。
 * 平台层（Android/iOS）提供具体的持久化实现。
 */
expect class UserBehaviorTracker {
    /**
     * 记录一首歌开始播放。
     * 调用时机：播放器 `isPlaying` 变为 true。
     */
    suspend fun recordPlayStart(song: SongMetadata, totalDurationMs: Long)

    /**
     * 记录一首歌播放结束。
     * 调用时机：播放器切歌或停止。
     *
     * @param actualPlayMs 实际播放时长（毫秒）
     * @param skipThreshold 跳过判定阈值（0.0 ~ 1.0，如 0.3 表示播放 < 30% 算跳过）
     */
    suspend fun recordPlayEnd(actualPlayMs: Long, skipThreshold: Float)

    /**
     * 切换歌曲的喜欢状态。
     * @param songName 歌曲名（无播放记录时用于填充行为记录，避免空值）
     * @param artist   歌手名（无播放记录时用于填充行为记录，避免空值）
     * @return 切换后的喜欢状态
     */
    suspend fun toggleLike(songId: String, platform: String, songName: String = "", artist: String = ""): Boolean

    /**
     * 查询某首歌是否被标记为喜欢。
     */
    suspend fun isLiked(songId: String, platform: String): Boolean

    /**
     * 记录下载完成。
     */
    suspend fun recordDownload(song: SongMetadata)

    /**
     * 获取指定歌手的跳过次数（用于负反馈过滤）。
     * @return 歌曲ID → 跳过次数的映射
     */
    suspend fun getSkipCounts(songIds: List<Pair<String, String>>): Map<String, Int>

    /**
     * 获取用户全部行为记录（按 playedAt 倒序）。
     */
    suspend fun getAllBehaviors(): List<UserBehavior>

    /**
     * 获取行为记录总数。
     */
    suspend fun getBehaviorCount(): Int

    /**
     * 清除所有行为数据（用于重置）。
     */
    suspend fun clearAll()
}
