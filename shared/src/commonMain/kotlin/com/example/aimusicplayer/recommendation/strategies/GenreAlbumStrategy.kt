package com.example.aimusicplayer.recommendation.strategies

import com.example.aimusicplayer.behavior.ScoredCandidate
import com.example.aimusicplayer.behavior.SongIndexEntry
import com.example.aimusicplayer.behavior.UserBehavior
import com.example.aimusicplayer.recommendation.RecommendationStrategy
import com.example.aimusicplayer.recommendation.config.RecommendationConfig
import com.example.aimusicplayer.recommendation.scoring.TimeDecay
import kotlinx.datetime.Clock

/**
 * 流派/专辑相似推荐策略。
 *
 * 统计用户最常听的流派 top N → 从候选池查找同流派、同专辑、
 * 发行年份相近的未听歌曲。
 */
class GenreAlbumStrategy(
    private val topGenreCount: Int = 3,
) : RecommendationStrategy {

    override val name: String = "genre"

    override suspend fun recommend(
        userHistory: List<UserBehavior>,
        songIndex: List<SongIndexEntry>,
        config: RecommendationConfig,
        n: Int,
    ): List<ScoredCandidate> {
        if (userHistory.isEmpty() || songIndex.isEmpty()) return emptyList()

        val nowMs = Clock.System.now().toEpochMilliseconds()

        // 统计用户最常听流派
        val genreScores = mutableMapOf<String, Float>()
        for (behavior in userHistory) {
            val genre = behavior.genre ?: continue
            if (genre.isBlank()) continue
            val decay = TimeDecay.decay(behavior.playedAt, nowMs, config.timeDecayHalfLife)
            genreScores[genre] = (genreScores[genre] ?: 0f) + decay
        }

        val topGenres = genreScores.entries
            .sortedByDescending { it.value }
            .take(topGenreCount)
            .map { it.key }

        // 统计用户常听的专辑
        val albumScores = mutableMapOf<String, Float>()
        for (behavior in userHistory) {
            val album = behavior.album ?: continue
            if (album.isBlank()) continue
            val decay = TimeDecay.decay(behavior.playedAt, nowMs, config.timeDecayHalfLife)
            albumScores[album] = (albumScores[album] ?: 0f) + decay
        }

        // 统计最常听歌曲的年份范围
        val years = userHistory.mapNotNull { it.year }.filter { it > 1900 }
        val medianYear = if (years.isNotEmpty()) years.sorted().let { it[it.size / 2] } else null

        // 已播放 ID 集合
        val playedKeys = userHistory
            .map { "${it.songId}|${it.platform}" }
            .toSet()

        val candidates = mutableListOf<ScoredCandidate>()

        for (entry in songIndex) {
            val key = "${entry.songId}|${entry.platform}"
            if (key in playedKeys) continue

            var score = 0f

            // 流派匹配加分
            val entryGenre = entry.genre
            if (entryGenre != null && entryGenre in topGenres) {
                val genreScore = genreScores[entryGenre] ?: 1.0f
                score += genreScore * 3.0f
            }

            // 同专辑加分
            val entryAlbum = entry.album
            if (entryAlbum != null && entryAlbum in albumScores) {
                val albumScore = albumScores[entryAlbum] ?: 1.0f
                score += albumScore * 2.0f
            }

            // 年份相近加分（±3 年）
            val entryYear = entry.year
            if (entryYear != null && medianYear != null) {
                val yearDiff = kotlin.math.abs(entryYear - medianYear)
                if (yearDiff <= 3) {
                    score += (3.0f - yearDiff.toFloat()) * 0.5f
                }
            }

            // 全局热度轻微加分
            score += entry.playCountGlobal.toFloat() / 10000f

            if (score > 0f) {
                val reasonGenre = entryGenre ?: topGenres.firstOrNull() ?: "你的听歌习惯"
                candidates.add(
                    ScoredCandidate(
                        song = entry,
                        score = score,
                        strategyName = name,
                        reasonTemplate = "基于你常听的{genre}风格，推荐《{songName}》",
                    )
                )
            }
        }

        return candidates
            .sortedByDescending { it.score }
            .take(config.maxCandidatesPerStrategy.coerceAtLeast(n))
    }
}
