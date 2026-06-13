package com.example.aimusicplayer.recommendation

import com.example.aimusicplayer.behavior.LocalSongIndex
import com.example.aimusicplayer.behavior.UserBehavior
import com.example.aimusicplayer.behavior.UserBehaviorRepository
import com.example.aimusicplayer.recommendation.config.RecommendationConfig
import com.example.aimusicplayer.recommendation.filters.NegativeFeedbackFilter
import com.example.aimusicplayer.recommendation.filters.RecentPlayFilter
import com.example.aimusicplayer.recommendation.reason.ReasonGenerator
import com.example.aimusicplayer.recommendation.reason.RecommendationResult
import com.example.aimusicplayer.recommendation.scoring.ScoreAggregator
import com.example.aimusicplayer.recommendation.strategies.ArtistExpansionStrategy
import com.example.aimusicplayer.recommendation.strategies.GenreAlbumStrategy
import com.example.aimusicplayer.recommendation.strategies.HotFallbackStrategy

/**
 * 推荐引擎 —— 编排各策略、合并、过滤、生成最终推荐。
 *
 * 用法：
 * ```kotlin
 * val engine = RecommendationEngine(behaviorRepo, songIndex)
 * val results = engine.recommend(n = 10)
 * ```
 */
class RecommendationEngine(
    private val behaviorRepo: UserBehaviorRepository,
    private val songIndex: LocalSongIndex,
    val config: RecommendationConfig = RecommendationConfig.DEFAULT,
) {
    private val strategies = listOf(
        ArtistExpansionStrategy(),
        GenreAlbumStrategy(),
        HotFallbackStrategy(),
    )

    /**
     * 生成推荐。
     *
     * @param n 期望返回的推荐数量（默认从配置读取）
     * @param excludeRecent 是否排除近期播放（默认 true）
     * @return 排序后的推荐结果列表
     */
    suspend fun recommend(
        n: Int = config.topN,
        excludeRecent: Boolean = true,
    ): List<RecommendationResult> {
        // 1. 读取用户行为和候选池（过期自动清理）
        val userHistory = behaviorRepo.getAllBehaviors()
        if (config.songIndexTtlHours > 0) {
            songIndex.removeExpired(config.songIndexTtlHours)
        }
        val allSongs = if (config.songIndexTtlHours > 0) {
            songIndex.getNonExpired(config.songIndexTtlHours)
        } else {
            songIndex.getAll()
        }

        // 2. 判断冷启动
        val isCold = RecommendationConfig.isColdStart(config, userHistory.size)
        val (artistW, genreW, hotW) = RecommendationConfig.effectiveWeights(config, isCold)

        // 3. 各策略并行生成候选
        val strategyResults = strategies.map { strategy ->
            strategy.recommend(userHistory, allSongs, config, n)
        }

        // 4. 加权合并 + 排序
        val weights = listOf(artistW, genreW, hotW)
        var aggregated = ScoreAggregator.aggregate(strategyResults, weights, config)

        // 5. 过滤
        aggregated = NegativeFeedbackFilter.filter(aggregated, userHistory, config)
        if (excludeRecent) {
            aggregated = RecentPlayFilter.filter(aggregated, userHistory, config)
        }

        // 6. 截取 Top N
        val topN = aggregated.take(n.coerceIn(1, 50))

        // 7. 生成推荐理由
        return ReasonGenerator.generateAll(topN)
    }

    /**
     * 获取候选池大小，用于判断是否需要 MusicBrainz 同步。
     */
    suspend fun getIndexSize(): Int = songIndex.count()

    /**
     * 判断是否需要 MusicBrainz 同步（候选池太小）。
     */
    suspend fun needsIndexSync(): Boolean = songIndex.count() < config.minSongIndexSize
}
