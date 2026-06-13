package com.example.aimusicplayer.music

import com.example.aimusicplayer.network.music.MusicPlatformApi
import com.example.aimusicplayer.network.music.SongMetadata
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Orchestrates music search across multiple platforms via public APIs.
 *
 * Searches are performed in a fixed order (Netease → QQ → Kugou → Bilibili)
 * and the first non-empty result set is returned.
 *
 * Also exposes [searchState] so the UI can show a loading spinner and handle
 * "no results" / error states gracefully.
 */
class MusicSearchManager(
    private val adapters: List<MusicPlatformApi>,
) {
    /** Ordered platform keys for deterministic search fallback. */
    private val platformOrder = listOf("bilibili", "netease", "qq")

    // ── Reactive state ──────────────────────────────────────────────

    private val _searchState = MutableStateFlow<SearchState>(SearchState.Idle)
    val searchState: StateFlow<SearchState> = _searchState.asStateFlow()

    // ── Public API ──────────────────────────────────────────────────

    /**
     * Search across all platforms, returning results from the first
     * platform that returns at least one result.
     *
     * @param query User's raw search query (song name + optional artist)
     * @param quality Desired audio quality (passed to play URL resolution)
     * @return List of [SongMetadata], or empty list
     */
    suspend fun search(query: String, quality: String = "standard"): List<SongMetadata> {
        _searchState.value = SearchState.Searching(query)

        val allResults = mutableListOf<SongMetadata>()
        for (platform in platformOrder) {
            val adapter = adapters.firstOrNull { it.platform == platform }
            if (adapter != null) {
                try {
                    val platformResults = adapter.search(query, platform)
                    if (platformResults.isNotEmpty()) {
                        allResults.addAll(platformResults)
                    }
                } catch (_: Exception) {
                    // next platform
                }
            }
        }

        if (allResults.isNotEmpty()) {
            _searchState.value = SearchState.Results(allResults, "multi")
        } else {
            _searchState.value = SearchState.NoResults(query)
        }
        return allResults
    }

    /**
     * Search for [query] on a **single specific** [platform].
     *
     * Search for [query] on a **single specific** [platform].
     *
     * @param query    Search keywords
     * @param platform Target platform key ("netease", "qq", "kugou", "bilibili")
     * @param quality  Desired audio quality (passed through to URL resolution)
     * @return Search results from the specified platform, or empty list
     */
    suspend fun searchOnPlatform(
        query: String,
        platform: String,
        quality: String = "standard",
    ): List<SongMetadata> {
        val adapter = adapters.firstOrNull { it.platform == platform }
            ?: return emptyList()

        return try {
            adapter.search(query, platform)
        } catch (_: Exception) {
            emptyList()
        }
    }

    /** Expose the platform resolution order for use by resolvers. */
    fun getPlatformOrder(): List<String> = platformOrder

    /**
     * Resolve a playable audio URL for a given song.
     *
     * @return Audio URL, or null if resolution fails.
     */
    suspend fun getPlayUrl(
        songId: String,
        platform: String,
        quality: String = "standard",
    ): String? {
        val adapter = adapters.firstOrNull { it.platform == platform }
            ?: return null

        return try {
            val url = adapter.getPlayUrl(songId, platform, quality)
            if (!url.isNullOrBlank()) url else null
        } catch (_: Exception) {
            null
        }
    }

    /**
     * Fetch lyrics for a given song from its platform.
     *
     * @return Lyrics text, or null if the platform doesn't support it.
     */
    suspend fun getLyrics(songId: String, platform: String): String? {
        val adapter = adapters.firstOrNull { it.platform == platform }
            ?: return null

        return try {
            adapter.getLyrics(songId, platform)
        } catch (_: Exception) {
            null
        }
    }

    /** Reset search state to idle (e.g. when navigating away). */
    fun clearSearchState() {
        _searchState.value = SearchState.Idle
    }
}

// ── Search state ────────────────────────────────────────────────────

/**
 * Observable state of the current search operation.
 */
sealed class SearchState {
    /** No search in progress. */
    data object Idle : SearchState()

    /** A search is running for [query]. */
    data class Searching(val query: String) : SearchState()

    /** Search returned [songs] from [platform]. */
    data class Results(val songs: List<SongMetadata>, val platform: String) : SearchState()

    /** No matches found for [query]. */
    data class NoResults(val query: String) : SearchState()

    /** An error occurred (network, parse, etc.). */
    data class Error(val message: String) : SearchState()
}
