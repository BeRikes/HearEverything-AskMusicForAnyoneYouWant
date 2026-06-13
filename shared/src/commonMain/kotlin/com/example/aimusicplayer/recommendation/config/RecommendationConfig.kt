package com.example.aimusicplayer.recommendation.config

import com.example.aimusicplayer.settings.SettingsKeys
import com.example.aimusicplayer.settings.SettingsRepository

/**
 * 推荐系统可配置参数。
 *
 * 从 [SettingsRepository] 读取，未配置时使用默认值。
 */
data class RecommendationConfig(
    /** 歌手策略权重（0~1） */
    val artistWeight: Float = 0.40f,
    /** 流派/专辑策略权重（0~1） */
    val genreWeight: Float = 0.35f,
    /** 热门兜底策略权重（0~1） */
    val hotWeight: Float = 0.25f,
    /** 冷启动阈值：行为条数 < 此值启用冷启动模式 */
    val coldStartThreshold: Int = 10,
    /** 冷启动时热门策略权重（覆盖 hotWeight） */
    val coldStartHotWeight: Float = 0.60f,
    /** 最近 N 天内播放过的歌曲不推荐 */
    val recentExcludeDays: Int = 7,
    /** 播放时长占比低于此值算跳过（0~1） */
    val skipThreshold: Float = 0.15f,
    /** 跳过 ≥ N 次后不再推荐该歌曲 */
    val skipBlockCount: Int = 5,
    /** 默认推荐数量 */
    val topN: Int = 10,
    /** 时间衰减半衰期（天） */
    val timeDecayHalfLife: Int = 30,
    /** 每个策略最大候选输出数 */
    val maxCandidatesPerStrategy: Int = 20,
    /** 候选池最小阈值（低于此值触发自动补充） */
    val minSongIndexSize: Int = 50,
    /** 候选池过期时间（小时，超过此时间的歌曲自动清理，0=永不过期） */
    val songIndexTtlHours: Int = 24,
) {
    companion object {
        val DEFAULT = RecommendationConfig()

        /** 从 [SettingsRepository] 加载配置，未配置项使用默认值。 */
        suspend fun fromSettings(settings: SettingsRepository): RecommendationConfig {
            return RecommendationConfig(
                artistWeight = settings.getRaw(SettingsKeys.REC_ARTIST_WEIGHT)?.toFloatOrNull() ?: DEFAULT.artistWeight,
                genreWeight = settings.getRaw(SettingsKeys.REC_GENRE_WEIGHT)?.toFloatOrNull() ?: DEFAULT.genreWeight,
                hotWeight = settings.getRaw(SettingsKeys.REC_HOT_WEIGHT)?.toFloatOrNull() ?: DEFAULT.hotWeight,
                coldStartThreshold = settings.getRaw(SettingsKeys.REC_COLD_START_THRESHOLD)?.toIntOrNull() ?: DEFAULT.coldStartThreshold,
                coldStartHotWeight = settings.getRaw(SettingsKeys.REC_COLD_START_HOT_WEIGHT)?.toFloatOrNull() ?: DEFAULT.coldStartHotWeight,
                recentExcludeDays = settings.getRaw(SettingsKeys.REC_RECENT_EXCLUDE_DAYS)?.toIntOrNull() ?: DEFAULT.recentExcludeDays,
                skipThreshold = settings.getRaw(SettingsKeys.REC_SKIP_THRESHOLD)?.toFloatOrNull() ?: DEFAULT.skipThreshold,
                skipBlockCount = settings.getRaw(SettingsKeys.REC_SKIP_BLOCK_COUNT)?.toIntOrNull() ?: DEFAULT.skipBlockCount,
                topN = settings.getRaw(SettingsKeys.REC_TOP_N)?.toIntOrNull() ?: DEFAULT.topN,
                timeDecayHalfLife = settings.getRaw(SettingsKeys.REC_TIME_DECAY_HALF_LIFE)?.toIntOrNull() ?: DEFAULT.timeDecayHalfLife,
                maxCandidatesPerStrategy = settings.getRaw(SettingsKeys.REC_MAX_CANDIDATES_PER_STRATEGY)?.toIntOrNull() ?: DEFAULT.maxCandidatesPerStrategy,
                minSongIndexSize = settings.getRaw(SettingsKeys.REC_MIN_SONG_INDEX_SIZE)?.toIntOrNull() ?: DEFAULT.minSongIndexSize,
                songIndexTtlHours = settings.getRaw(SettingsKeys.REC_SONG_INDEX_TTL_HOURS)?.toIntOrNull() ?: DEFAULT.songIndexTtlHours,
            )
        }

        /** 判断是否冷启动 */
        fun isColdStart(config: RecommendationConfig, behaviorCount: Int): Boolean =
            behaviorCount < config.coldStartThreshold

        /** 冷启动时有效权重（热门策略提权，其他等比例缩小） */
        fun effectiveWeights(config: RecommendationConfig, isCold: Boolean): Triple<Float, Float, Float> {
            if (!isCold) return Triple(config.artistWeight, config.genreWeight, config.hotWeight)
            val remaining = 1.0f - config.coldStartHotWeight
            val scaleFactor = if (config.artistWeight + config.genreWeight > 0f) {
                remaining / (config.artistWeight + config.genreWeight)
            } else 0f
            return Triple(
                config.artistWeight * scaleFactor,
                config.genreWeight * scaleFactor,
                config.coldStartHotWeight,
            )
        }
    }
}
