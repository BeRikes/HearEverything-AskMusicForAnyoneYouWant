package com.example.aimusicplayer.recommendation

import com.example.aimusicplayer.behavior.ScoredCandidate
import com.example.aimusicplayer.behavior.SongIndexEntry
import com.example.aimusicplayer.behavior.UserBehavior
import com.example.aimusicplayer.recommendation.config.RecommendationConfig

/**
 * 推荐策略接口。
 *
 * 每个策略独立计算一组候选推荐，返回带分数的候选列表。
 * 最终由 [RecommendationEngine] 的 [ScoreAggregator] 合并排序。
 */
interface RecommendationStrategy {
    /** 策略唯一标识，用于调试和理由生成 */
    val name: String

    /**
     * 根据用户行为和歌曲索引生成推荐候选。
     *
     * @param userHistory 用户全部行为记录
     * @param songIndex 本地歌曲元数据索引（候选池）
     * @param config 推荐配置
     * @param n 期望输出的候选数量
     * @return 带分数的候选列表，按分数降序
     */
    suspend fun recommend(
        userHistory: List<UserBehavior>,
        songIndex: List<SongIndexEntry>,
        config: RecommendationConfig,
        n: Int,
    ): List<ScoredCandidate>
}
