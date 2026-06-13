package com.example.aimusicplayer.recommendation.scoring

import kotlin.math.exp
import kotlin.math.ln

/**
 * 时间衰减函数。
 *
 * 越近的行为权重越高，越远的行为权重越低。
 * 使用指数衰减：weight = exp(-λ * daysSinceEvent)
 * 其中 λ = ln(2) / halfLifeDays（半衰期）
 */
object TimeDecay {

    /**
     * 计算一条行为记录的衰减权重。
     *
     * @param eventTimeMs 事件发生时间戳（epoch millis）
     * @param nowMs 当前时间戳
     * @param halfLifeDays 半衰期（天），默认 30 天
     * @return 0~1 之间的衰减因子
     */
    fun decay(
        eventTimeMs: Long,
        nowMs: Long,
        halfLifeDays: Int = 30,
    ): Float {
        if (halfLifeDays <= 0) return 1.0f
        val daysSince = (nowMs - eventTimeMs).toDouble() / (24 * 3600 * 1000)
        if (daysSince <= 0) return 1.0f
        val lambda = ln(2.0) / halfLifeDays
        return exp(-lambda * daysSince).toFloat().coerceIn(0.0f, 1.0f)
    }

    /**
     * 计算歌曲的累积衰减播放次数（播放次数 × 每次播放的衰减权重之和）。
     */
    fun weightedPlayCount(
        playEvents: List<Long>,
        nowMs: Long,
        halfLifeDays: Int = 30,
    ): Float {
        return playEvents.sumOf { decay(it, nowMs, halfLifeDays).toDouble() }.toFloat()
    }
}
