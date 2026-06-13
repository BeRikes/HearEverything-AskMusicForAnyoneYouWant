package com.example.aimusicplayer.export

import kotlinx.serialization.Serializable

@Serializable
data class ExportManifest(
    val version: Int = 1,
    val exportedAt: Long,
    val appVersion: String = "",
    val songs: List<ExportSongItem>,
)

@Serializable
data class ExportSongItem(
    val songId: String,
    val fileName: String,
    val songName: String,
    val artist: String,
    val platform: String,
    val audioUrl: String,
    val fileSize: Long,
    val coverUrl: String? = null,
    val lyrics: String? = null,
    val downloadTime: Long,
    val realName: String? = null,
    val realArtist: String? = null,
)

data class ImportResult(
    val importedCount: Int,
    val skippedCount: Int,
    val errors: List<String> = emptyList(),
)

/**
 * 导出操作结果。
 */
data class ExportResult(
    val zipPath: String,
    val totalSongs: Int,
    val exportedSongs: Int,
) {
    val skippedSongs: Int get() = totalSongs - exportedSongs
    val hasFailures: Boolean get() = skippedSongs > 0
}
