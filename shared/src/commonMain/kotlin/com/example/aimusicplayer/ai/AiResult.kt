package com.example.aimusicplayer.ai

/**
 * Result of an AI assistant request.
 *
 * Callers MUST handle [ConfigMissing] by directing the user to the settings screen.
 * Callers SHOULD handle [AuthError] by clearing the stored API key.
 */
sealed class AiResult {

    /** The AI returned a valid, parsed command. */
    data class Success(val command: AiCommand) : AiResult()

    /**
     * The AI returned a plain text response (no JSON command).
     * Used for conversational replies like asking the user to provide a link.
     */
    data class TextOnly(val text: String) : AiResult()

    /**
     * No API key has been configured (missing or too short).
     * UI should display a banner / card linking to [AiSettingsScreen].
     */
    data object ConfigMissing : AiResult()

    /**
     * Provider returned 401 — the API key is invalid.
     * The stored key has been automatically cleared.
     * UI should redirect to settings.
     */
    data object AuthError : AiResult()

    /**
     * Generic error (network failure, timeout, JSON parse failure, etc.).
     */
    data class Error(val message: String, val cause: Throwable? = null) : AiResult()
}
