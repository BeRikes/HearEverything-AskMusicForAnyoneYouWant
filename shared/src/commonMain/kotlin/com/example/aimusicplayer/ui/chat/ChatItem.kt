package com.example.aimusicplayer.ui.chat

import com.example.aimusicplayer.ai.AiCommand
import com.example.aimusicplayer.recommendation.reason.RecommendationResult
import kotlinx.datetime.Clock

/**
 * Unified chat message model for the chat-style main screen.
 *
 * Each type renders differently:
 * - [UserMessage]: right-aligned, primary container color
 * - [AiMessage]:   left-aligned, surface variant color
 * - [SystemMessage]: centered, muted grey text
 */
sealed class ChatItem {

    /** A message sent by the user. */
    data class UserMessage(
        val text: String,
        val timestamp: Long = Clock.System.now().toEpochMilliseconds(),
    ) : ChatItem()

    /**
     * A response from the AI assistant.
     *
     * @param text    Human-readable reply text displayed in the bubble.
     * @param command Parsed [AiCommand] — null if the AI response couldn't be parsed.
     */
    data class AiMessage(
        val text: String,
        val command: AiCommand? = null,
        val timestamp: Long = Clock.System.now().toEpochMilliseconds(),
    ) : ChatItem()

    /**
     * System-level notification (e.g. "API Key not configured",
     * "Network error", "Song not found").
     */
    data class SystemMessage(
        val text: String,
        val timestamp: Long = Clock.System.now().toEpochMilliseconds(),
    ) : ChatItem()

    /**
     * 推荐结果消息 —— 展示推荐歌曲列表卡片。
     */
    data class RecommendationMessage(
        val recommendations: List<RecommendationResult>,
        val command: AiCommand? = null,
        val timestamp: Long = Clock.System.now().toEpochMilliseconds(),
    ) : ChatItem()
}
