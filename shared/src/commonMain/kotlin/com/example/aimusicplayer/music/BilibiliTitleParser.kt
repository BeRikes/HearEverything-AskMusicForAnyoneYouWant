package com.example.aimusicplayer.music

/**
 * Parses raw Bilibili video titles to extract clean song metadata for
 * cross-platform lyrics matching.
 *
 * Bilibili titles contain uploader commentary, quality tags, and other noise
 * that prevents naive matching against Netease/QQ song databases. This parser
 * strips metadata noise and identifies the real artist and song name.
 *
 * ## Strategy
 * 1. Detect special version keywords (Live/Remix/Cover) across the whole title
 * 2. Strip ALL metadata brackets `【...】` (but check for version keywords first)
 * 3. Extract `《songName》` content — this is the most reliable signal
 * 4. Look for artist via: `《...》—Artist`, `Artist - Song`, etc.
 * 5. Strip common noise words: "MV", "主题曲", "试听" etc.
 * 6. Clean uploader name as artist fallback
 *
 * ## Examples
 * ```
 * "【4K修复】《发如雪》—周杰伦 无损音质"         → songName="发如雪", artist="周杰伦"
 * "【VALORANT赛事】首个官方赛事主题曲MV《Die For You》 Champions 2021"
 *                                               → songName="Die For You", artist=null
 * "【Hi-Res】周杰伦 - 晴天 MV"                   → songName="晴天", artist="周杰伦"
 * "发如雪 (女声版)"                              → songName="发如雪", isSpecialVersion=true
 * "用百万级豪华装备试听《Die For You》【Hi-Res】"   → songName="Die For You", artist=null
 * ```
 */
object BilibiliTitleParser {

    /**
     * Parse a raw Bilibili video title into structured form.
     *
     * @param rawTitle The raw title from Bilibili API (with `<em>` tags already stripped)
     * @param uploader The uploader username from Bilibili API (fallback when title has no artist)
     */
    fun parse(rawTitle: String, uploader: String = ""): ParsedBilibiliTitle {
        var text = rawTitle.trim()

        // ── Step 0: Decode HTML entities and strip HTML tags ───────────
        text = text.replace("<em class=\"keyword\">", "")
            .replace("</em>", "")
            .replace("&quot;", "\"")
            .replace("&#34;", "\"")
            .replace("&apos;", "'")
            .replace("&#39;", "'")
            .replace("&amp;", "&")
            .replace("&lt;", "<")
            .replace("&gt;", ">")
            .replace("&nbsp;", " ")

        // ── Step 1: Detect special version (BEFORE stripping brackets) ─
        val versionInfo = detectSpecialVersion(text)

        // Check 【Live】 【翻唱】 【Remix】 inside brackets before removing them
        val bracketVersionInfo = text.extractVersionFromBrackets()

        // ── Step 2: Strip ALL 【...】 brackets (but keep version info detected) ─
        text = text.replace(Regex("""【[^】]*】"""), " ")

        // ── Step 3: Strip [...] brackets (English-style, usually quality tags) ─
        text = text.replace(Regex("""\[[^\]]*(?:4K|1080|720|无损|Hi-Res|HQ|SQ|高清|超清|修复|画质|音质|MV)[^\]]*\]"""), " ")

        // ── Step 4: Extract 《songName》 — the most reliable signal ────
        var songName: String? = null
        var extractedArtist: String? = null
        var artistFromTitle = false

        val bookTitleRegex = Regex("""《(.+?)》""")
        val bookMatch = bookTitleRegex.find(text)

        if (bookMatch != null) {
            // Content inside 《...》 is our best guess for the song name
            songName = bookMatch.groupValues[1].trim()

            // Check for "—Artist" or "-Artist" after the book title
            val afterBook = text.substring(bookMatch.range.last + 1).trim()
            val artistAfterBook = afterBook.extractArtistAfterDash()
            if (artistAfterBook != null) {
                extractedArtist = artistAfterBook
                artistFromTitle = true
            }

            // Check for "Artist—" or "Artist-" before the book title
            if (extractedArtist == null) {
                val beforeBook = text.substring(0, bookMatch.range.first).trim()
                val artistBeforeBook = beforeBook.extractArtistBeforeDash()
                if (artistBeforeBook != null) {
                    extractedArtist = artistBeforeBook
                    artistFromTitle = true
                }
            }

            // Check for adjacent text before 《 (e.g. "试听The Weeknd《Die For You》")
            if (extractedArtist == null) {
                val beforeBook = text.substring(0, bookMatch.range.first)
                // Split by ｜ (B站 tag separator), take only the last segment
                val segments = beforeBook.split("｜")
                val lastSegment = segments.lastOrNull()?.trim() ?: beforeBook.trim()
                // Take the last ~25 chars before 《, strip leading noise
                val adjacent = lastSegment.takeLast(30).trim()
                    .replace(Regex("""^.*?(?:用|拿|以|试听|听|体验|测试|的)\s*"""), "")
                    .trim()
                // If what remains looks like an artist name (short, no brackets/quotes)
                if (adjacent.length in 2..25
                    && !adjacent.contains("《")
                    && !adjacent.contains("【")
                    && !adjacent.contains("｜")
                    && !adjacent.contains("\"")
                    && !adjacent.contains(""")
                    && !adjacent.contains(""")) {
                    extractedArtist = adjacent
                    artistFromTitle = true
                }
            }
        }

        // ── Step 5: If no 《...》 found, try other patterns ────────────
        if (songName == null) {
            val result = text.parseArtistSongPatterns()
            songName = result.first
            extractedArtist = result.second
            if (extractedArtist != null) artistFromTitle = true
        }

        // ── Step 6: Clean the song name ──────────────────────────────
        var cleanName = (songName ?: text).cleanNoise()
        if (cleanName.isBlank()) cleanName = text.cleanNoise()

        // ── Step 7: Remove trailing quality / descriptor words ────────
        cleanName = cleanName
            .replace(Regex("""\s*(?:无损音质|高音质|高品质|HQ|SQ|MV|官方版|原版|主题曲|OST)\s*$"""), "")
            .trim()

        // ── Step 8: Fallback artist from uploader ─────────────────────
        if (extractedArtist.isNullOrBlank() && uploader.isNotBlank()) {
            extractedArtist = uploader.cleanUploaderName()
        }

        // ── Merge version info from brackets and full text ────────────
        val finalIsSpecial = versionInfo.isSpecial || bracketVersionInfo.isSpecial
        val finalVersionType = versionInfo.type ?: bracketVersionInfo.type

        return ParsedBilibiliTitle(
            cleanSongName = cleanName.ifBlank { text.cleanNoise().ifBlank { text } },
            extractedArtist = extractedArtist?.takeIf { it.isNotBlank() },
            artistFromTitle = artistFromTitle,
            isSpecialVersion = finalIsSpecial,
            versionType = finalVersionType,
        )
    }

    // ══════════════════════════════════════════════════════════════════
    // Private extension helpers
    // ══════════════════════════════════════════════════════════════════

    /**
     * Look for version-indicating keywords inside 【...】 brackets.
     * These brackets will be removed in the next step, so check them first.
     */
    private fun String.extractVersionFromBrackets(): VersionInfo {
        val bracketContent = Regex("""【([^】]+)】""").find(this)?.groupValues?.get(1)?.lowercase() ?: return VersionInfo()
        return when {
            Regex("""live|演唱会|现场|concert""", RegexOption.IGNORE_CASE).containsMatchIn(bracketContent) ->
                VersionInfo(true, "live")
            Regex("""cover|翻唱""", RegexOption.IGNORE_CASE).containsMatchIn(bracketContent) ->
                VersionInfo(true, "cover")
            Regex("""remix|beat|电音|dj""", RegexOption.IGNORE_CASE).containsMatchIn(bracketContent) ->
                VersionInfo(true, "remix")
            Regex("""钢琴|伴奏|纯音|instrumental|piano""", RegexOption.IGNORE_CASE).containsMatchIn(bracketContent) ->
                VersionInfo(true, "instrumental")
            else -> VersionInfo()
        }
    }

    /** Try to extract "Artist" from text like "... —Artist" or "...-Artist". */
    private fun String.extractArtistAfterDash(): String? {
        // Look for " — Name" or " - Name" pattern at the start of remaining text
        val m = Regex("""^[—\-]\s*(.+?)(?:\s*[\[【(""'']|\s*$)""").find(this.trim()) ?: return null
        val candidate = m.groupValues[1].trim()
        return candidate.takeIf { it.length in 1..20 && !it.contains("《") && !it.contains("【") }
    }

    /** Try to extract "Artist" from text like "Artist —" or "Artist-" before book title. */
    private fun String.extractArtistBeforeDash(): String? {
        val m = Regex("""(.+?)\s*[—\-]\s*$""").find(this.trim()) ?: return null
        val candidate = m.groupValues[1].trim()
        return candidate.takeIf { it.length in 1..20 && !it.contains("《") && !it.contains("【") }
    }

    /**
     * Parse text for common artist-song patterns when no 《...》 is present.
     * Returns (songName, artist) pair.
     */
    private fun String.parseArtistSongPatterns(): Pair<String, String?> {
        val t = this.trim().removeLeadingNoise()
        var songName: String? = null
        var artist: String? = null

        // Pattern: "Artist - SongName" (spaces around dash, common on B站)
        // Terminate right side at brackets, parens, or quotes to avoid capturing noise
        val spacedDash = Regex("""^(.+?)\s+[—\-]\s+(.+?)(?:\s*[\[【(""'']|\s*$)""").find(t)
        if (spacedDash != null) {
            val left = spacedDash.groupValues[1].trim()
            val right = spacedDash.groupValues[2].trim()
            // Convention: "Artist - SongName" is the standard music format
            // Left side is artist, right side is song name
            artist = left.takeIf { it.length in 1..30 && !it.contains("《") && !it.contains("【") }
            songName = right
        }

        // Pattern: "SongName｜Artist"
        if (songName == null) {
            val bar = Regex("""^(.+?)[｜|]\s*(.+?)(?:\s*[\[【(""'']|\s*$)""").find(t)
            if (bar != null) {
                songName = bar.groupValues[1].trim()
                val cand = bar.groupValues[2].trim()
                artist = cand.takeIf { it.length <= 20 }
            }
        }

        // Pattern: "SongName-Artist" (no-space dash at end)
        if (songName == null) {
            val endDash = Regex("""^(.+)[—\-]([^—\-]{1,20})$""").find(t)
            if (endDash != null) {
                songName = endDash.groupValues[1].trim()
                artist = endDash.groupValues[2].trim().takeIf { !it.contains("《") && !it.contains("【") }
            }
        }

        return (songName ?: t) to artist
    }

    /**
     * Clean noise from a candidate song name:
     * - Remove remaining bracket-like text
     * - Strip common descriptor prefixes/suffixes
     * - Normalize whitespace
     */
    private fun String.cleanNoise(): String {
        var result = this
            // Remove square-bracket content [xxx] and 【xxx】 (always noise tags)
            .replace(Regex("""[\[【][^\]】]*[\]】]"""), " ")
            // Only remove parens if they contain noise keywords (preserve (Acoustic), (Live) etc.)
            .replace(Regex("""[(（][^)）]*(?:无损|高清|超清|Hi-Res|HQ|SQ|MV版|官方|原版|音质|画质|4K|1080P|修复|赛事|Champions|DJ版|伴奏|钢琴|纯音|instrumental|piano|热播)[^)）]*[)）]""", RegexOption.IGNORE_CASE), " ")
            // Remove 《》 marks
            .replace("《", "").replace("》", "")
            .replace("「", "").replace("」", "")
            // Remove various quote marks
            .replace("“", "").replace("”", "") // left/right double quotes
            .replace("‘", "").replace("’", "") // left/right single quotes
            .replace("“", "").replace("”", "")
            .replace("'", "").replace("'", "")
            // Remove leading noise: descriptors before the actual song name
            .removeLeadingNoise()
            // Normalize whitespace
            .replace(Regex("""\s+"""), " ")
            .trim()

        return result
    }

    /**
     * Strip leading noise phrases that aren't part of the song name.
     *
     * Common B站 noise patterns:
     * - "首个官方赛事主题曲MV" (descriptor before song)
     * - "用百万级豪华装备试听" (audiophile description)
     * - "【xxx】" remnants
     */
    private fun String.removeLeadingNoise(): String {
        // Strip leading noise words: these are descriptors, not song names
        val noisePrefixes = listOf(
            "首个", "官方", "赛事", "主题曲", "原声", "OST",
            "试听", "豪华装备", "百万级", "顶级", "HiFi",
        )

        // Try splitting on common boundaries and taking the better part
        // If the string contains "MV" not at the end, it might be a descriptor
        // Use heuristics: prefer shorter segments that look like song names

        var result = this
        // If there's a "MV" or "主题曲" followed by more content, strip the descriptor part
        result = result.replace(Regex("""^.*?(?:首个|官方)\s*(?:赛事|主题曲|MV|OST)\s*"""), "")
            .replace(Regex("""^.*?(?:用|拿|以).+?(?:试听|听|体验|测试)\s*"""), "")
            // Strip leading audiophile/platform noise: "百万级装备试听", "HiFi听", "循环歌单", etc.
            .replace(Regex("""^.*?(?:百万级|顶级|HiFi|Hi-Res|豪华)\S*?(?:试听|听|装备)\s*"""), "")
            .replace(Regex("""^.*?[｜|]\s*"""), "")

        return result.trim()
    }

    /** Clean B站 uploader username into a plausible artist name. */
    private fun String.cleanUploaderName(): String? {
        val t = this.trim()
        if (t.matches(Regex("""^\d+$"""))) return null
        if (t.length > 16) return null
        // Remove common suffixes
        return t.replace(Regex("""[-_\s]*(?:音乐|Music|Official|官方|频道|Channel|_-_)\s*$""", RegexOption.IGNORE_CASE), "")
            .trim()
            .takeIf { it.length in 2..12 }
    }

    // ══════════════════════════════════════════════════════════════════
    // Version detection
    // ══════════════════════════════════════════════════════════════════

    private data class VersionInfo(val isSpecial: Boolean = false, val type: String? = null)

    private fun detectSpecialVersion(text: String): VersionInfo {
        val lower = text.lowercase()

        if (Regex("""(?:live|演唱会|现场版|音乐现场|concert)""", RegexOption.IGNORE_CASE).containsMatchIn(lower))
            return VersionInfo(true, "live")
        if (Regex("""(?:cover|翻唱|翻[訳译]|女声版|男声版|女生版|男生版)""", RegexOption.IGNORE_CASE).containsMatchIn(lower))
            return VersionInfo(true, "cover")
        if (Regex("""(?:remix|r&b|r＆b|beat|type\s*beat|dj版|电音)""", RegexOption.IGNORE_CASE).containsMatchIn(lower))
            return VersionInfo(true, "remix")
        if (Regex("""(?:钢琴版|钢琴|伴奏|纯音乐|纯音|instrumental|piano)""").containsMatchIn(lower))
            return VersionInfo(true, "instrumental")

        return VersionInfo()
    }
}

/**
 * Result of parsing a Bilibili video title.
 *
 * @param cleanSongName    Song name with quality tags and brackets removed
 * @param extractedArtist  Real artist name extracted from title, or null
 * @param isSpecialVersion True if the title indicates a Live/Remix/Cover/etc.
 * @param versionType      Type of special version ("live", "cover", "remix", "instrumental"), or null
 */
data class ParsedBilibiliTitle(
    val cleanSongName: String,
    val extractedArtist: String?,
    /** True if the artist was parsed from the title (reliable), false if from uploader (unreliable). */
    val artistFromTitle: Boolean,
    val isSpecialVersion: Boolean,
    val versionType: String?,
)
