package com.example.aimusicplayer.recommendation.reason

import com.example.aimusicplayer.behavior.ScoredCandidate

/**
 * 推荐理由生成器。
 *
 * 将候选中的 `reasonTemplate` 填充为可读的推荐理由文本。
 */
object ReasonGenerator {

    /**
     * 为单个候选生成推荐理由。
     *
     * @param candidate 带理由模板的候选
     * @return 填充后的推荐理由，如 "因为你喜欢周杰伦，推荐TA的另一首歌《七里香》"
     */
    fun generate(candidate: ScoredCandidate): String {
        return candidate.reasonTemplate
            .replace("{artist}", candidate.song.artist)
            .replace("{songName}", candidate.song.songName)
            .replace("{album}", candidate.song.album ?: "")
            .replace("{genre}", candidate.song.genre ?: "你的听歌习惯")
    }

    /**
     * 批量生成推荐理由。
     */
    fun generateAll(candidates: List<ScoredCandidate>): List<RecommendationResult> {
        return candidates.map { candidate ->
            RecommendationResult(
                songId = candidate.song.songId,
                platform = candidate.song.platform,
                songName = candidate.song.songName,
                artist = candidate.song.artist,
                album = candidate.song.album,
                coverUrl = candidate.song.coverUrl,
                score = candidate.score,
                reason = generate(candidate),
                strategyName = candidate.strategyName,
            )
        }
    }
}

/**
 * 最终推荐结果（对外暴露的 UI 友好格式）。
 */
data class RecommendationResult(
    val songId: String,
    val platform: String,
    val songName: String,
    val artist: String,
    val album: String? = null,
    val coverUrl: String? = null,
    val score: Float,
    val reason: String,
    val strategyName: String,
)
