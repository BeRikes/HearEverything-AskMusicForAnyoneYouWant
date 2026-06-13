package com.example.aimusicplayer.ai

import kotlinx.serialization.Serializable

/**
 * A single step in a multi-action AI workflow.
 *
 * Each [AiStep] represents one atomic operation the AI agent can perform.
 * Multiple steps are chained to fulfill complex user requests like
 * "search for X, play it, and open lyrics".
 */
@Serializable
data class AiStep(
    val action: String,
    val query: String? = null,        // legacy combined query
    val songName: String? = null,     // song name (for search_play)
    val artist: String? = null,       // artist name (for search_play)
    val controlAction: String? = null,
    val quality: String? = null,
    val url: String? = null,          // playlist share URL (for import_playlist / auto_import_playlist)
    val count: Int? = null,           // number of recommendations (for recommend)
    val minutes: Int? = null,         // sleep timer minutes (for sleep_timer)
    val finishCurrentSong: Boolean? = null, // sleep timer: wait for current song to end
    val mode: String? = null,         // play mode (for set_play_mode: "sequential"|"loop"|"repeat_one")
) {
    companion object {
        const val ACTION_SEARCH_PLAY = "search_play"
        const val ACTION_DOWNLOAD_CURRENT = "download_current"
        const val ACTION_CONTROL = "control"
        const val ACTION_SET_QUALITY = "set_quality"
        const val ACTION_OPEN_LYRICS = "open_lyrics"
        const val ACTION_OPEN_NOW_PLAYING = "open_now_playing"
        const val ACTION_IMPORT_PLAYLIST = "import_playlist"
        const val ACTION_AUTO_IMPORT_PLAYLIST = "auto_import_playlist"
        const val ACTION_RECOMMEND = "recommend"
        const val ACTION_TOGGLE_LIKE = "toggle_like"
        const val ACTION_SLEEP_TIMER = "sleep_timer"
        const val ACTION_SET_PLAY_MODE = "set_play_mode"
        const val ACTION_CLEAR_PLAYLIST = "clear_playlist"
        const val ACTION_PLAY_RECOMMEND = "play_recommend"
        const val ACTION_PLAY_N_RECOMMEND = "play_n_recommend"
        const val ACTION_UNKNOWN = "unknown"
    }
}

/**
 * Parsed AI instruction — either a single action or a sequence of steps.
 *
 * ## Format (new):
 * ```json
 * {"actions":[{"action":"search_play","query":"Lemon"},{"action":"open_lyrics"}]}
 * ```
 *
 * ## Format (legacy, still supported):
 * ```json
 * {"action":"search_play","query":"Lemon"}
 * ```
 */
@Serializable
data class AiCommand(
    // Legacy single-action fields (backward compatible)
    val action: String? = null,
    val query: String? = null,
    val controlAction: String? = null,
    val quality: String? = null,
    val url: String? = null,          // playlist share URL (for import_playlist / auto_import_playlist legacy format)
    val count: Int? = null,           // recommendation count / play_n_recommend count
    val minutes: Int? = null,         // sleep timer minutes
    val finishCurrentSong: Boolean? = null, // sleep timer: finish current song
    val mode: String? = null,         // play mode
    // New multi-step field
    val actions: List<AiStep>? = null,
) {
    companion object {
        const val ACTION_SEARCH_PLAY = AiStep.ACTION_SEARCH_PLAY
        const val ACTION_DOWNLOAD_CURRENT = AiStep.ACTION_DOWNLOAD_CURRENT
        const val ACTION_CONTROL = AiStep.ACTION_CONTROL
        const val ACTION_SET_QUALITY = AiStep.ACTION_SET_QUALITY
        const val ACTION_OPEN_LYRICS = AiStep.ACTION_OPEN_LYRICS
        const val ACTION_OPEN_NOW_PLAYING = AiStep.ACTION_OPEN_NOW_PLAYING
        const val ACTION_IMPORT_PLAYLIST = AiStep.ACTION_IMPORT_PLAYLIST
        const val ACTION_AUTO_IMPORT_PLAYLIST = AiStep.ACTION_AUTO_IMPORT_PLAYLIST
        const val ACTION_RECOMMEND = AiStep.ACTION_RECOMMEND
        const val ACTION_TOGGLE_LIKE = AiStep.ACTION_TOGGLE_LIKE
        const val ACTION_SLEEP_TIMER = AiStep.ACTION_SLEEP_TIMER
        const val ACTION_SET_PLAY_MODE = AiStep.ACTION_SET_PLAY_MODE
        const val ACTION_CLEAR_PLAYLIST = AiStep.ACTION_CLEAR_PLAYLIST
        const val ACTION_PLAY_RECOMMEND = AiStep.ACTION_PLAY_RECOMMEND
        const val ACTION_PLAY_N_RECOMMEND = AiStep.ACTION_PLAY_N_RECOMMEND
        const val ACTION_UNKNOWN = AiStep.ACTION_UNKNOWN
    }

    /** Normalize to a list of steps — handles both legacy and multi-action formats. */
    fun toSteps(): List<AiStep> {
        if (!actions.isNullOrEmpty()) return actions
        if (!action.isNullOrBlank()) {
            return listOf(AiStep(action = action, query = query, controlAction = controlAction, quality = quality, url = url,
                count = count, minutes = minutes, finishCurrentSong = finishCurrentSong, mode = mode))
        }
        return listOf(AiStep(action = ACTION_UNKNOWN))
    }
}
