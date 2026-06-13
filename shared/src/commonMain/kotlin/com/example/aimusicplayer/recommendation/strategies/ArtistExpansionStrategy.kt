package com.example.aimusicplayer.recommendation.strategies

import com.example.aimusicplayer.behavior.ScoredCandidate
import com.example.aimusicplayer.behavior.SongIndexEntry
import com.example.aimusicplayer.behavior.UserBehavior
import com.example.aimusicplayer.recommendation.RecommendationStrategy
import com.example.aimusicplayer.recommendation.config.RecommendationConfig
import com.example.aimusicplayer.recommendation.scoring.TimeDecay
import kotlinx.datetime.Clock

/**
 * 歌手拓展推荐策略。
 *
 * 统计用户最喜欢的 top N 歌手 → 从候选池查找这些歌手的其他未听歌曲 →
 * 按热度 + 时间衰减排序。
 */
class ArtistExpansionStrategy(
    private val topArtistCount: Int = 5,
) : RecommendationStrategy {

    override val name: String = "artist"

    override suspend fun recommend(
        userHistory: List<UserBehavior>,
        songIndex: List<SongIndexEntry>,
        config: RecommendationConfig,
        n: Int,
    ): List<ScoredCandidate> {
        if (userHistory.isEmpty() || songIndex.isEmpty()) return emptyList()

        val nowMs = Clock.System.now().toEpochMilliseconds()

        // 找出用户最喜欢的 top N 歌手（加权：喜欢 > 完成 > 播放次数，时间衰减）
        val artistScores = mutableMapOf<String, Float>()
        for (behavior in userHistory) {
            if (behavior.artist.isBlank()) continue
            val decay = TimeDecay.decay(behavior.playedAt, nowMs, config.timeDecayHalfLife)
            var score = decay * 1.0f  // base: 每次播放 = 1 分
            if (behavior.completed) score += decay * 2.0f  // 听完额外 +2
            if (behavior.liked) score += decay * 5.0f      // 喜欢额外 +5
            artistScores[behavior.artist] = (artistScores[behavior.artist] ?: 0f) + score
        }

        val topArtists = artistScores.entries
            .sortedByDescending { it.value }
            .take(topArtistCount)
            .map { it.key }

        if (topArtists.isEmpty()) return emptyList()

        // 获取已播放歌曲 ID 集合
        val playedKeys = userHistory
            .map { "${it.songId}|${it.platform}" }
            .toSet()

        // 从 song_index 查找 topArtist 的未听歌曲
        val candidates = mutableListOf<ScoredCandidate>()
        for (artist in topArtists) {
            val artistSongs = songIndex.filter { it.artist == artist && "${it.songId}|${it.platform}" !in playedKeys }
            val artistWeight = artistScores[artist] ?: 1.0f
            for (song in artistSongs) {
                // 分数基于歌手权重 + 全局热度归一化
                val score = artistWeight * (1.0f + song.playCountGlobal.toFloat() / 1000f)
                candidates.add(
                    ScoredCandidate(
                        song = song,
                        score = score,
                        strategyName = name,
                        reasonTemplate = "因为你喜欢$artist，推荐TA的另一首歌《{songName}》",
                    )
                )
            }
        }

        return candidates
            .sortedByDescending { it.score }
            .take(config.maxCandidatesPerStrategy.coerceAtLeast(n))
    }
}
