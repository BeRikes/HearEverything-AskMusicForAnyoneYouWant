package com.example.aimusicplayer.recommendation.scoring

import com.example.aimusicplayer.behavior.ScoredCandidate
import com.example.aimusicplayer.recommendation.config.RecommendationConfig

/**
 * 多策略分数加权合并器。
 *
 * 将所有策略输出的候选按策略权重加权，合并去重后排序。
 */
object ScoreAggregator {

    /**
     * 合并多个策略的候选结果。
     *
     * @param strategyResults 各策略的候选列表
     * @param weights 各策略对应的权重（与 strategyResults 按索引对应）
     * @param config 推荐配置
     * @return 去重合并后的候选列表，按加权总分降序
     */
    fun aggregate(
        strategyResults: List<List<ScoredCandidate>>,
        weights: List<Float>,
        config: RecommendationConfig,
    ): List<ScoredCandidate> {
        // 归一化权重
        val sum = weights.sum()
        val normalizedWeights = if (sum > 0f) weights.map { it / sum } else weights.map { 1.0f / weights.size }

        // 按 (songId, platform) 去重，保留最高加权分
        val scoreMap = mutableMapOf<String, ScoredCandidate>()

        strategyResults.forEachIndexed { idx, candidates ->
            val weight = normalizedWeights.getOrElse(idx) { 1.0f / strategyResults.size }
            for (candidate in candidates) {
                val key = "${candidate.song.songId}|${candidate.song.platform}"
                val weightedScore = candidate.score * weight
                val existing = scoreMap[key]
                if (existing == null || weightedScore > existing.score) {
                    scoreMap[key] = candidate.copy(score = weightedScore)
                }
            }
        }

        return scoreMap.values
            .sortedByDescending { it.score }
            .take(config.topN)
    }
}
