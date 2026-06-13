package com.example.aimusicplayer.recommendation.strategies

import com.example.aimusicplayer.behavior.ScoredCandidate
import com.example.aimusicplayer.behavior.SongIndexEntry
import com.example.aimusicplayer.behavior.UserBehavior
import com.example.aimusicplayer.recommendation.RecommendationStrategy
import com.example.aimusicplayer.recommendation.config.RecommendationConfig

/**
 * 热门兜底 + 冷启动策略。
 *
 * 按全局热度（play_count_global）降序推荐未听过的歌曲。
 * 冷启动时此策略权重自动提升。
 */
class HotFallbackStrategy : RecommendationStrategy {

    override val name: String = "hot"

    override suspend fun recommend(
        userHistory: List<UserBehavior>,
        songIndex: List<SongIndexEntry>,
        config: RecommendationConfig,
        n: Int,
    ): List<ScoredCandidate> {
        if (songIndex.isEmpty()) return emptyList()

        // 已播放 ID 集合
        val playedKeys = userHistory
            .map { "${it.songId}|${it.platform}" }
            .toSet()

        // 按全局热度降序，排除已听过
        val sorted = songIndex
            .filter { "${it.songId}|${it.platform}" !in playedKeys }
            .sortedByDescending { it.playCountGlobal }

        val maxCount = config.maxCandidatesPerStrategy.coerceAtLeast(n * 2) // 冷启动时需要更多候选

        return sorted.take(maxCount).map { entry ->
            // 归一化分数：playCountGlobal / 基准值
            val score = (entry.playCountGlobal.toFloat() / 1000f).coerceIn(0.1f, 10.0f)
            ScoredCandidate(
                song = entry,
                score = score,
                strategyName = name,
                reasonTemplate = "热门歌曲推荐：《{songName}》",
            )
        }
    }
}
