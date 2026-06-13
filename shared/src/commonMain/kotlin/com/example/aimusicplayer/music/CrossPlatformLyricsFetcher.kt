package com.example.aimusicplayer.music

import co.touchlab.kermit.Logger
import com.example.aimusicplayer.network.RateLimiterRegistry
import com.example.aimusicplayer.network.music.SongMetadata

/**
 * Cross-platform lyrics fetcher for Bilibili audio sources.
 *
 * Bilibili videos have no native lyrics API. This class searches Netease Cloud Music
 * by song name + artist to find matching lyrics, with confidence scoring to avoid
 * returning lyrics for different songs or different versions (live vs studio, etc.).
 *
 * ## Confidence levels
 *
 * | Level  | Criteria                                                                 |
 * |--------|--------------------------------------------------------------------------|
 * | HIGH   | songName bidirectional match + artist bidirectional match + dur diff <20%|
 * | MEDIUM | songName matches + artist fuzzy (one contains the other)                 |
 * | LOW    | only songName partial match, or special version detected                 |
 *
 * @param searchManager Multi-platform search orchestrator (used for Netease access)
 */
class CrossPlatformLyricsFetcher(
    private val searchManager: MusicSearchManager,
) {

    private val log = Logger.withTag("CrossPlatformLyrics")

    /**
     * Attempt to fetch lyrics for a Bilibili audio track by searching Netease.
     *
     * @param songName    Cleaned song name (ideally from [BilibiliTitleParser])
     * @param artist      Real artist name (extracted from title or uploader), can be empty
     * @param durationS   Duration of the Bilibili audio in seconds, for cross-validation
     * @param isSpecialVersion Whether the Bilibili title indicates a special version
     * @return Matched lyrics result, or null if no acceptable match found
     */
    suspend fun fetch(
        songName: String,
        artist: String,
        durationS: Int = 0,
        isSpecialVersion: Boolean = false,
        excludeSongIds: Set<String> = emptySet(),
    ): CrossPlatformLyricsResult? {
        if (songName.isBlank()) return null

        // ── Step 1: Search Netease ────────────────────────────────────
        val query = if (artist.isNotBlank()) "$songName $artist" else songName
        log.d { "Cross-platform lyrics search: \"$query\" (excluding ${excludeSongIds.size})" }

        RateLimiterRegistry.acquire("netease")
        val results = try {
            searchManager.searchOnPlatform(query, "netease", "standard")
        } catch (e: Exception) {
            log.w { "Netease search failed for lyrics: ${e.message}" }
            emptyList()
        }

        if (results.isEmpty()) {
            log.d { "No Netease results for \"$query\"" }
            return null
        }

        // ── Step 2: Score and rank candidates (skip excluded) ──────────
        val scored = results
            .filter { it.songId !in excludeSongIds }
            .map { candidate ->
                val score = scoreMatch(songName, artist, durationS, isSpecialVersion, candidate)
                candidate to score
            }
            .filter { it.second.confidence != LyricsMatchConfidence.LOW }
            .sortedByDescending { it.second.score }

        val best = scored.firstOrNull() ?: return null
        val (match, score) = best

        log.i {
            "Best lyric match: ${match.songName} (${match.artist}) " +
            "confidence=${score.confidence} score=${score.score}"
        }

        // ── Step 3: Fetch actual LRC lyrics from Netease ──────────────
        val lyrics = try {
            searchManager.getLyrics(match.songId, "netease")
        } catch (e: Exception) {
            log.w { "Lyrics fetch failed: ${e.message}" }
            null
        }

        if (lyrics.isNullOrBlank()) {
            log.d { "Empty lyrics returned for ${match.songId}" }
            return null
        }

        return CrossPlatformLyricsResult(
            lyrics = lyrics,
            confidence = score.confidence,
            matchedSongName = match.songName,
            matchedArtist = match.artist,
            matchedSongId = match.songId,
        )
    }

    // ══════════════════════════════════════════════════════════════════
    // Scoring
    // ══════════════════════════════════════════════════════════════════

    private data class MatchScore(
        val confidence: LyricsMatchConfidence,
        val score: Int, // higher = better match
    )

    private fun scoreMatch(
        searchName: String,
        searchArtist: String,
        bilibiliDurationS: Int,
        isSpecialVersion: Boolean,
        candidate: SongMetadata,
    ): MatchScore {
        // If B站 audio is a special version (live/remix/cover), never go beyond MEDIUM
        if (isSpecialVersion) {
            // Only match if name AND artist both match exactly
            val nameMatch = fuzzyMatch(searchName, candidate.songName)
            val artistMatch = if (searchArtist.isNotBlank()) {
                fuzzyMatch(searchArtist, candidate.artist)
            } else true

            if (nameMatch && artistMatch) {
                return MatchScore(LyricsMatchConfidence.MEDIUM, 50)
            }
            return MatchScore(LyricsMatchConfidence.LOW, 0)
        }

        var score = 0
        var nameMatched = false
        var artistMatched = false

        // ── Song name matching (weight: 60 points) ────────────────────
        val nameContains = candidate.songName.contains(searchName, ignoreCase = true)
        val searchContains = searchName.contains(candidate.songName, ignoreCase = true)

        if (nameContains && searchContains) {
            // Bidirectional match: same name (e.g. "发如雪" ↔ "发如雪")
            score += 60
            nameMatched = true
        } else if (nameContains) {
            // Candidate contains search (e.g. "发如雪 (Live)" contains "发如雪")
            score += 45
            nameMatched = true
        } else if (searchContains) {
            // Search contains candidate (e.g. "【无损】发如雪" contains "发如雪")
            score += 35
            nameMatched = true
        } else {
            // Word-level matching
            val searchWords = searchName.split(Regex("""\s+""")).filter { it.length > 1 }
            val candidateWords = candidate.songName.split(Regex("""\s+""")).filter { it.length > 1 }
            val commonWords = searchWords.count { sw ->
                candidateWords.any { cw -> cw.contains(sw, ignoreCase = true) || sw.contains(cw, ignoreCase = true) }
            }
            if (searchWords.isNotEmpty() && commonWords.toFloat() / searchWords.size >= 0.6f) {
                score += 20
                nameMatched = true
            }
        }

        if (!nameMatched) return MatchScore(LyricsMatchConfidence.LOW, score)

        // ── Artist matching (weight: 40 points) ───────────────────────
        if (searchArtist.isNotBlank()) {
            val artistContains = candidate.artist.contains(searchArtist, ignoreCase = true)
            val searchContainsArtist = searchArtist.contains(candidate.artist, ignoreCase = true)

            if (artistContains && searchContainsArtist) {
                score += 40
                artistMatched = true
            } else if (artistContains) {
                score += 30
                artistMatched = true
            } else if (searchContainsArtist) {
                score += 20
                artistMatched = true
            } else {
                // Artist mismatch — heavily penalize
                score -= 30
            }
        } else {
            // No artist provided — can't verify, cap score
            score += 10
        }

        // ── Duration cross-validation (weight: ±15 points) ────────────
        if (bilibiliDurationS > 0 && candidate.duration > 0) {
            val ratio = bilibiliDurationS.toFloat() / candidate.duration.toFloat()
            when {
                ratio in 0.85f..1.15f -> score += 15   // Very close
                ratio in 0.70f..1.30f -> score += 5    // Close enough
                ratio < 0.50f || ratio > 1.50f -> score -= 20 // Likely different version
            }
        }

        // ── Confidence threshold ──────────────────────────────────────
        val confidence = when {
            score >= 85 && nameMatched && (artistMatched || searchArtist.isBlank()) ->
                LyricsMatchConfidence.HIGH
            score >= 45 && nameMatched ->
                LyricsMatchConfidence.MEDIUM
            else ->
                LyricsMatchConfidence.LOW
        }

        return MatchScore(confidence, score)
    }

    /** Case-insensitive bidirectional containment check. */
    private fun fuzzyMatch(a: String, b: String): Boolean {
        return a.contains(b, ignoreCase = true) || b.contains(a, ignoreCase = true)
    }
}

/**
 * Result of cross-platform lyrics lookup.
 *
 * @param lyrics           Raw LRC-format lyrics text
 * @param confidence       How reliable the match is
 * @param matchedSongName  The Netease song name that was matched
 * @param matchedArtist    The Netease artist that was matched
 */
data class CrossPlatformLyricsResult(
    val lyrics: String,
    val confidence: LyricsMatchConfidence,
    val matchedSongName: String,
    val matchedArtist: String,
    val matchedSongId: String,
)

enum class LyricsMatchConfidence {
    /** Song name + artist both match, durations align — use directly. */
    HIGH,
    /** Song name matches, artist is fuzzy — use with "仅供参考" disclaimer. */
    MEDIUM,
    /** Match too weak or special version — skip entirely. */
    LOW,
}
