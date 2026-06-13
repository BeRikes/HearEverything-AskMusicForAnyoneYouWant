package com.example.aimusicplayer.recommendation.filters

import com.example.aimusicplayer.behavior.ScoredCandidate
import com.example.aimusicplayer.behavior.UserBehavior
import com.example.aimusicplayer.recommendation.config.RecommendationConfig
import kotlinx.datetime.Clock

/**
 * 近期播放去重过滤器。
 *
 * 排除最近 N 天内播放过的歌曲（N 由 [RecommendationConfig.recentExcludeDays] 配置）。
 */
object RecentPlayFilter {

    /**
     * 过滤候选列表，移除最近 N 天内播放过的项。
     */
    fun filter(
        candidates: List<ScoredCandidate>,
        userHistory: List<UserBehavior>,
        config: RecommendationConfig,
    ): List<ScoredCandidate> {
        val nowMs = Clock.System.now().toEpochMilliseconds()
        val cutoffMs = nowMs - config.recentExcludeDays * 24L * 3600L * 1000L

        val recentKeys = userHistory
            .filter { it.playedAt >= cutoffMs }
            .map { "${it.songId}|${it.platform}" }
            .toSet()

        return candidates.filter { candidate ->
            val key = "${candidate.song.songId}|${candidate.song.platform}"
            key !in recentKeys
        }
    }
}
