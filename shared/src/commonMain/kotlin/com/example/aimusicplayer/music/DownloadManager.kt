package com.example.aimusicplayer.music

import com.example.aimusicplayer.network.music.SongMetadata
import kotlinx.coroutines.flow.StateFlow

// ═══════════════════════════════════════════════════════════════════════════
// Download state model
// ═══════════════════════════════════════════════════════════════════════════

/**
 * Observable state for a single download.
 */
data class DownloadState(
    /** Composite song identifier ("songId|platform"). */
    val downloadKey: String,
    /** Original song ID (for backward compat). */
    val songId: String = downloadKey,
    /** Progress 0f..1f, or 1f when complete. */
    val progress: Float,
    /** Whether the download is still active. */
    val isActive: Boolean,
    /** Error message if the download failed, null otherwise. */
    val error: String? = null,
    /** Local file path once completed. */
    val localPath: String? = null,
)

/** Returns composite download key for (songId, platform) uniqueness. */
fun downloadKey(songId: String, platform: String) = "$songId|$platform"

// ═══════════════════════════════════════════════════════════════════════════
// Downloaded song model (persisted to SQLDelight)
// ═══════════════════════════════════════════════════════════════════════════

/**
 * A song that has been fully downloaded and is available offline.
 */
data class DownloadedSong(
    val songId: String,
    val fileName: String,
    val songName: String,
    val artist: String,
    val platform: String,
    val audioUrl: String,
    val localPath: String,
    val fileSize: Long = 0L,
    val quality: String = "standard",
    val coverUrl: String? = null,
    val lyrics: String? = null,
    val downloadTime: Long = 0L,
    /** 真实歌名（搜索输入框的值，用于修正B站标题解析错误） */
    val realName: String? = null,
    /** 真实歌手名（搜索输入框的值，用于修正B站UP主名冒充歌手的问题） */
    val realArtist: String? = null,
)

// ═══════════════════════════════════════════════════════════════════════════
// DownloadManager — expect / actual
// ═══════════════════════════════════════════════════════════════════════════

/**
 * Cross-platform download manager for audio files.
 *
 * ## Platform implementations
 * - **Android**: Ktor-based download → `Context.filesDir/downloads/`, SQLDelight tracking
 * - **iOS**: Ktor-based download → `NSFileManager/Documents/downloads/`, SQLDelight tracking
 *
 * ## File naming convention
 * Files are saved as `songName_artist_hash8.mp3` where `hash8` is the first
 * 8 characters of `MD5(songName|artist|platform)`. This prevents collisions
 * and produces human-readable filenames.
 *
 * ## Usage
 * ```kotlin
 * val dm: DownloadManager = ...
 * dm.download(audioUrl, songMetadata, "high")
 * // Collect progress in UI:
 * dm.downloadProgress(songId).collect { progress -> ... }
 * // List offline songs:
 * val songs = dm.getDownloadedSongs()
 * // Delete:
 * dm.deleteSong(songId)
 * ```
 */
/**
 * Testable interface for cross-platform download management.
 */
interface DownloadManagerProvider {
    suspend fun download(audioUrl: String, song: SongMetadata, quality: String = "standard", lyrics: String? = null, coverUrl: String? = null, realName: String? = null, realArtist: String? = null): Result<String>
    suspend fun getDownloadedSongs(): List<DownloadedSong>
    suspend fun deleteSong(songId: String)
    suspend fun renameSong(songId: String, realName: String, realArtist: String)
    suspend fun getTotalDownloadSize(): Long
    suspend fun clearAllDownloads()
    fun downloadProgress(songId: String): StateFlow<Float>
    val allDownloads: StateFlow<Map<String, DownloadState>>
}

expect class DownloadManager : DownloadManagerProvider {
    override suspend fun download(audioUrl: String, song: SongMetadata, quality: String, lyrics: String?, coverUrl: String?, realName: String?, realArtist: String?): Result<String>
    override suspend fun getDownloadedSongs(): List<DownloadedSong>
    override suspend fun deleteSong(songId: String)
    override suspend fun renameSong(songId: String, realName: String, realArtist: String)
    override suspend fun getTotalDownloadSize(): Long
    override suspend fun clearAllDownloads()
    override fun downloadProgress(songId: String): StateFlow<Float>
    override val allDownloads: StateFlow<Map<String, DownloadState>>
}
