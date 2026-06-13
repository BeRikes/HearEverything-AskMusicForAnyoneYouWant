package com.example.aimusicplayer.behavior

/**
 * 用户对一首歌的单次行为记录。
 *
 * 每一次播放事件（开始→结束/跳过）对应一条记录。
 * 用于推荐系统的用户画像构建和负反馈过滤。
 */
data class UserBehavior(
    val id: Long = 0,
    val songId: String,
    val songName: String,
    val artist: String,
    val platform: String,
    val album: String? = null,
    val genre: String? = null,
    val year: Int? = null,
    /** 歌曲总时长（毫秒） */
    val durationMs: Long = 0,
    /** 实际播放时长（毫秒） */
    val playDurationMs: Long = 0,
    /** 是否完整听完（播放 > 90%） */
    val completed: Boolean = false,
    /** 用户是否标记喜欢 */
    val liked: Boolean = false,
    /** 是否算跳过（播放 < skipThreshold * duration） */
    val skipped: Boolean = false,
    /** 是否下载过 */
    val downloaded: Boolean = false,
    /** 播放开始时间戳（epoch millis） */
    val playedAt: Long,
)

/**
 * 歌曲元数据索引条目 —— 推荐候选池中的一首歌。
 */
data class SongIndexEntry(
    val songId: String,
    val platform: String,          // "netease" / "qq" / "bilibili" / "musicbrainz"
    val songName: String,
    val artist: String,
    val album: String? = null,
    val genre: String? = null,
    val year: Int? = null,
    val durationMs: Long = 0,
    val coverUrl: String? = null,
    /** 全局播放次数（用于热门兜底） */
    val playCountGlobal: Long = 0,
    /** 数据来源：search / history / musicbrainz */
    val source: String = "unknown",
    /** 入库时间戳（epoch millis），用于自动过期 */
    val createdAt: Long = 0,
)

/**
 * 带分数的推荐候选项。
 *
 * 各推荐策略输出此类型，最终由 [ScoreAggregator] 合并排序。
 */
data class ScoredCandidate(
    val song: SongIndexEntry,
    val score: Float,
    val strategyName: String,      // "artist" / "genre" / "hot"
    val reasonTemplate: String,    // 推荐理由模板，如 "因为你喜欢{artist}，推荐《{songName}》"
)
