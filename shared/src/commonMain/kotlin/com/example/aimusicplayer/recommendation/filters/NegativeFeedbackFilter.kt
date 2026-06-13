package com.example.aimusicplayer.recommendation.filters

import com.example.aimusicplayer.behavior.ScoredCandidate
import com.example.aimusicplayer.behavior.UserBehavior
import com.example.aimusicplayer.recommendation.config.RecommendationConfig

/**
 * 负反馈过滤器。
 *
 * 排除以下歌曲：
 * 1. 用户播放过但未完成且未标记喜欢的歌曲（不喜欢/无感）
 * 2. 跳过次数 ≥ skipBlockCount 的歌曲
 */
object NegativeFeedbackFilter {

    /**
     * 过滤候选列表，移除负反馈命中的项。
     */
    fun filter(
        candidates: List<ScoredCandidate>,
        userHistory: List<UserBehavior>,
        config: RecommendationConfig,
    ): List<ScoredCandidate> {
        // 构建负反馈黑名单
        val blockedKeys = buildBlocklist(userHistory, config)
        return candidates.filter { candidate ->
            val key = "${candidate.song.songId}|${candidate.song.platform}"
            key !in blockedKeys
        }
    }

    private fun buildBlocklist(
        history: List<UserBehavior>,
        config: RecommendationConfig,
    ): Set<String> {
        val blocked = mutableSetOf<String>()

        // 按歌曲分组
        val bySong = history.groupBy { "${it.songId}|${it.platform}" }

        for ((key, behaviors) in bySong) {
            // 条件1：存在 liked=0 且未完成的记录
            val hasDislike = behaviors.any { !it.liked && !it.completed }

            // 条件2：跳过次数超过阈值
            val skipCount = behaviors.count { it.skipped }
            val tooManySkips = skipCount >= config.skipBlockCount

            if (hasDislike || tooManySkips) {
                blocked.add(key)
            }
        }

        return blocked
    }
}
