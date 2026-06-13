package com.example.aimusicplayer.import

/**
 * Parses music platform share links to extract platform type and playlist ID.
 *
 * Supported formats:
 *   NetEase:
 *     - https://music.163.com/playlist?id=123456
 *     - https://music.163.com/#/playlist?id=123456
 *     - https://y.music.163.com/m/playlist?id=123456
 *   QQ Music:
 *     - https://i2.y.qq.com/n3/other/pages/details/playlist.html?...&id=772368334&...
 *   Kugou:
 *     - https://m.kugou.com/songlist/gcid_3zx4xam7z4z058/...
 */
object PlaylistLinkParser {

    data class ParseResult(val platform: String, val playlistId: String)

    // Regex patterns for each platform
    // 网易云: various URL formats all containing playlist?id= or playlist/<id>
    private val neteasePatterns = listOf(
        Regex("""music\.163\.com.*playlist[/?&]id=(\d+)""", RegexOption.IGNORE_CASE),
        Regex("""music\.163\.com/playlist/(\d+)""", RegexOption.IGNORE_CASE),
    )

    // QQ音乐: share link with id= query param
    // e.g. https://i2.y.qq.com/n3/other/pages/details/playlist.html?...&id=772368334&...
    private val qqPattern = Regex("""y\.qq\.com.*playlist.*[?&]id=(\d+)""", RegexOption.IGNORE_CASE)

    // 酷狗: share link with gcid_ prefix in path
    // e.g. https://m.kugou.com/songlist/gcid_3zx4xam7z4z058/...
    private val kugouPattern = Regex("""kugou\.com/songlist/(gcid_[a-zA-Z0-9]+)""", RegexOption.IGNORE_CASE)

    // Combined list for isMusicLink quick check
    private val allPatterns = neteasePatterns + qqPattern + kugouPattern

    /**
     * Parse a share link and extract platform + playlist ID.
     *
     * @param url Raw URL pasted by the user
     * @return ParseResult if recognized, null otherwise
     */
    fun parse(url: String): ParseResult? {
        val trimmed = url.trim()

        // 网易云
        for (pattern in neteasePatterns) {
            pattern.find(trimmed)?.let { match ->
                val id = match.groupValues[1]
                if (id.isNotEmpty()) return ParseResult("netease", id)
            }
        }

        // QQ音乐
        qqPattern.find(trimmed)?.let { match ->
            val id = match.groupValues[1]
            if (id.isNotEmpty()) return ParseResult("qq", id)
        }

        // 酷狗
        kugouPattern.find(trimmed)?.let { match ->
            val id = match.groupValues[1]
            if (id.isNotEmpty()) return ParseResult("kugou", id)
        }

        return null
    }

    /** Quick check if a string looks like any music platform link. */
    fun isMusicLink(text: String): Boolean {
        val trimmed = text.trim()
        return allPatterns.any { it.containsMatchIn(trimmed) }
    }
}
