package com.example.aimusicplayer.music

import android.content.Context
import app.cash.sqldelight.db.QueryResult
import app.cash.sqldelight.db.SqlDriver
import com.example.aimusicplayer.cache.Md5Utils
import com.example.aimusicplayer.cache.currentTimeSeconds
import com.example.aimusicplayer.network.music.SongMetadata
import io.ktor.client.HttpClient
import io.ktor.client.engine.okhttp.OkHttp
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.statement.bodyAsChannel
import io.ktor.http.HttpHeaders
import io.ktor.utils.io.jvm.javaio.toInputStream
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.io.File
import java.io.FileOutputStream

/**
 * Android implementation of [DownloadManager].
 *
 * ## Storage
 * Audio files are saved to `{context.filesDir}/downloads/` using
 * the naming scheme `songName_artist_hash8.mp3`.
 *
 * ## Persistence
 * Download metadata is stored in the SQLDelight `DownloadRecord` table
 * (schema v2) via [SqlDriver] raw queries.
 *
 * ## Download engine
 * Uses Ktor [HttpClient] (OkHttp engine, already a project dependency)
 * with streaming read + progress callback.
 *
 * @param context Android Context (application context)
 * @param driver  SQLDelight SQL driver (from DatabaseDriverFactory)
 */
actual class DownloadManager(
    private val context: Context,
    private val driver: SqlDriver,
) : DownloadManagerProvider {
    // ── Coroutine scope ────────────────────────────────────────────

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    // ── HttpClient (dedicated instance for downloads) ──────────────

    private val httpClient = HttpClient(OkHttp) {
        engine {
            config {
                connectTimeout(30, java.util.concurrent.TimeUnit.SECONDS)
                readTimeout(300, java.util.concurrent.TimeUnit.SECONDS)
            }
        }
    }

    // ── Download directory ─────────────────────────────────────────

    private val downloadDir: File
        get() = File(context.filesDir, "downloads").also {
            if (!it.exists()) it.mkdirs()
        }

    // ── Reactive state ─────────────────────────────────────────────

    private val _allDownloads = MutableStateFlow<Map<String, DownloadState>>(emptyMap())
    actual override val allDownloads: StateFlow<Map<String, DownloadState>> = _allDownloads.asStateFlow()

    // Per-song progress flows (kept alive while song is in the map)
    private val progressFlows = mutableMapOf<String, MutableStateFlow<Float>>()

    // ══════════════════════════════════════════════════════════════
    // Core operations
    // ══════════════════════════════════════════════════════════════

    actual override suspend fun download(
        audioUrl: String,
        song: SongMetadata,
        quality: String,
        lyrics: String?,
        coverUrl: String?,
        realName: String?,
        realArtist: String?,
    ): Result<String> {
        val key = downloadKey(song.songId, song.platform)
        val fileName = buildFileName(song.songName, song.artist, song.platform)
        val outputFile = File(downloadDir, fileName)

        // ── Start progress tracking ─────────────────────────────────
        val progressFlow = getOrCreateProgressFlow(key)
        progressFlow.value = 0f
        _allDownloads.value = _allDownloads.value + (key to DownloadState(
            downloadKey = key, songId = song.songId, progress = 0f, isActive = true,
        ))

        return try {
            // ── Stream download (with platform CDN headers) ─────────
            val response = httpClient.get(audioUrl) {
                platformHeaders(song.platform).forEach { (key, value) ->
                    header(key, value)
                }
            }
            val totalBytes = response.headers[HttpHeaders.ContentLength]
                ?.toLongOrNull() ?: -1L
            val channel = response.bodyAsChannel()
            val inputStream = channel.toInputStream()

            FileOutputStream(outputFile).use { output ->
                val buffer = ByteArray(8192)
                var bytesRead: Int
                var totalRead = 0L
                while (inputStream.read(buffer).also { bytesRead = it } != -1) {
                    output.write(buffer, 0, bytesRead)
                    totalRead += bytesRead
                    val progress = if (totalBytes > 0)
                        (totalRead.toFloat() / totalBytes).coerceIn(0f, 1f)
                    else
                        -1f // Content-Length unknown — UI shows indeterminate bar
                    progressFlow.value = progress
                    _allDownloads.value = _allDownloads.value + (key to DownloadState(
                        downloadKey = key, songId = song.songId, progress = progress, isActive = true,
                    ))
                }
            }

            val localPath = outputFile.absolutePath
            val fileSize = outputFile.length()

            // ── Validate: reject HTML error pages and tiny files ──────
            if (fileSize < 32 * 1024) { // < 32 KB is not real audio
                outputFile.delete()
                throw RuntimeException("下载文件过小 (${fileSize} bytes) — CDN 可能拒绝了请求")
            }
            // Check for HTML content (starts with "<" or "<!DOCTYPE")
            val head = outputFile.inputStream().use { it.readNBytes(256) }
            val headStr = String(head, 0, head.size.coerceAtMost(256), Charsets.UTF_8)
            if (headStr.trimStart().startsWith("<")) {
                outputFile.delete()
                throw RuntimeException("下载文件为 HTML 错误页，CDN 拒绝了请求 (headers: ${platformHeaders(song.platform)})")
            }

            // ── Download cover image (non-fatal if fails) ──────────────
            var coverLocalPath: String? = null
            if (!coverUrl.isNullOrBlank()) {
                try {
                    val ext = coverUrl.substringAfterLast('.', "jpg")
                        .substringBefore('?').take(8)
                    val coverFile = File(downloadDir,
                        "${fileName.removeSuffix(".mp3")}_cover.$ext")
                    val coverResp = httpClient.get(coverUrl) {
                        platformHeaders(song.platform).forEach { (key, value) ->
                            header(key, value)
                        }
                    }
                    coverFile.outputStream().use { out ->
                        coverResp.bodyAsChannel().toInputStream().copyTo(out)
                    }
                    coverLocalPath = coverFile.absolutePath
                } catch (_: Exception) {
                    // Cover download is non-fatal
                }
            }

            // ── Persist to SQLDelight ─────────────────────────────────
            val now = currentTimeSeconds()
            driver.execute(
                identifier = null,
                sql = """
                    INSERT OR REPLACE INTO DownloadRecord
                    (id, fileName, songName, artist, platform, audioUrl, localPath, fileSize, quality, coverUrl, lyrics, downloadTime, realName, realArtist)
                    VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                """.trimIndent(),
                parameters = 14,
            ) {
                bindString(0, key)
                bindString(1, fileName)
                bindString(2, song.songName)
                bindString(3, song.artist)
                bindString(4, song.platform)
                bindString(5, audioUrl)
                bindString(6, localPath)
                bindLong(7, fileSize)
                bindString(8, quality)
                bindString(9, coverLocalPath ?: song.coverUrl)
                bindString(10, lyrics)
                bindLong(11, now)
                bindString(12, realName ?: "")
                bindString(13, realArtist ?: "")
            }

            // ── Mark complete ─────────────────────────────────────────
            progressFlow.value = 1f
            _allDownloads.value = _allDownloads.value + (key to DownloadState(
                downloadKey = key, songId = song.songId,
                progress = 1f,
                isActive = false,
                localPath = localPath,
            ))

            Result.success(localPath)
        } catch (e: Exception) {
            // Clean up partial file
            outputFile.delete()
            progressFlow.value = 0f
            _allDownloads.value = _allDownloads.value + (key to DownloadState(
                downloadKey = key, songId = song.songId,
                progress = 0f,
                isActive = false,
                error = e.message ?: "下载失败",
            ))
            Result.failure(e)
        }
    }

    actual override suspend fun getDownloadedSongs(): List<DownloadedSong> {
        return driver.executeQuery(
            identifier = null,
            sql = "SELECT id, fileName, songName, artist, platform, audioUrl, localPath, fileSize, quality, coverUrl, lyrics, downloadTime, realName, realArtist FROM DownloadRecord ORDER BY downloadTime DESC",
            mapper = { cursor ->
                val items = mutableListOf<DownloadedSong>()
                while (cursor.next().unwrap()) {
                    items.add(
                        DownloadedSong(
                            songId = cursor.getString(0)!!,
                            fileName = cursor.getString(1)!!,
                            songName = cursor.getString(2)!!,
                            artist = cursor.getString(3) ?: "",
                            platform = cursor.getString(4)!!,
                            audioUrl = cursor.getString(5)!!,
                            localPath = cursor.getString(6)!!,
                            fileSize = cursor.getLong(7) ?: 0L,
                            quality = cursor.getString(8)!!,
                            coverUrl = cursor.getString(9),
                            lyrics = cursor.getString(10),
                            downloadTime = cursor.getLong(11) ?: 0L,
                            realName = cursor.getString(12)?.ifBlank { null },
                            realArtist = cursor.getString(13)?.ifBlank { null },
                        )
                    )
                }
                QueryResult.Value(items)
            },
            parameters = 0,
        ).unwrap()
    }

    actual override suspend fun deleteSong(songId: String) {
        // 1. Look up the song in the list to find its local path
        val songs = getDownloadedSongs()
        val song = songs.find { it.songId == songId }
        // 2. Delete the file from disk
        song?.let { File(it.localPath).delete() }

        // 3. Remove from DB
        driver.execute(
            identifier = null,
            sql = "DELETE FROM DownloadRecord WHERE id = ?",
            parameters = 1,
        ) { bindString(0, songId) }

        // 4. Clear in-memory state
        _allDownloads.value = _allDownloads.value - songId
        progressFlows.remove(songId)
    }

    actual override suspend fun renameSong(songId: String, realName: String, realArtist: String) {
        driver.execute(
            identifier = null,
            sql = "UPDATE DownloadRecord SET realName = ?, realArtist = ? WHERE id = ?",
            parameters = 3,
        ) {
            bindString(0, realName)
            bindString(1, realArtist)
            bindString(2, songId)
        }
    }

    actual override suspend fun getTotalDownloadSize(): Long {
        val total: Long = driver.executeQuery(
            identifier = null,
            sql = "SELECT COALESCE(SUM(fileSize), 0) FROM DownloadRecord",
            mapper = { cursor ->
                cursor.next()
                QueryResult.Value(cursor.getLong(0) ?: 0L)
            },
            parameters = 0,
        ).unwrap()
        return total
    }

    actual override suspend fun clearAllDownloads() {
        val songs = getDownloadedSongs()
        // Delete all files
        for (song in songs) {
            File(song.localPath).delete()
        }
        // Delete all DB records
        driver.execute(
            identifier = null,
            sql = "DELETE FROM DownloadRecord",
            parameters = 0,
        )
        // Clear state
        _allDownloads.value = emptyMap()
        progressFlows.clear()
    }

    // ══════════════════════════════════════════════════════════════
    // Reactive state
    // ══════════════════════════════════════════════════════════════

    actual override fun downloadProgress(songId: String): StateFlow<Float> =
        getOrCreateProgressFlow(songId).asStateFlow()

    // ══════════════════════════════════════════════════════════════
    // Internal helpers
    // ══════════════════════════════════════════════════════════════

    private fun getOrCreateProgressFlow(songId: String): MutableStateFlow<Float> {
        return progressFlows.getOrPut(songId) {
            MutableStateFlow(0f)
        }
    }

    /**
     * CDN-specific HTTP headers required by each platform's download server.
     *
     * Without these headers, some CDNs (notably Bilibili) return HTML error
     * pages instead of the actual audio stream, causing
     * [UnrecognizedInputFormatException] when ExoPlayer tries to play the
     * downloaded file.
     */
    private fun platformHeaders(platform: String): Map<String, String> {
        return when (platform) {
            "bilibili" -> mapOf(
                "Referer" to "https://www.bilibili.com/",
                "Origin" to "https://www.bilibili.com",
                "User-Agent" to "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/122.0.0.0 Safari/537.36",
            )
            "qq" -> mapOf(
                "Referer" to "https://y.qq.com/",
            )
            "netease" -> mapOf(
                "Referer" to "https://music.163.com/",
            )
            "kugou" -> mapOf(
                "Referer" to "https://www.kugou.com/",
            )
            else -> emptyMap()
        }
    }

    /** Clean up resources. Call when the manager is no longer needed. */
    fun dispose() {
        scope.cancel()
        httpClient.close()
        progressFlows.clear()
    }
}

// ── File naming ─────────────────────────────────────────────────────

/** Regex matching characters unsafe for cross-platform filenames. */
private val UNSAFE_CHARS = Regex("""[<>:"/\\|?*\x00-\x1f]""")

/**
 * Build a safe, unique filename: `songName_artist_hash8.mp3`
 *
 * - Replaces unsafe filesystem characters with underscores
 * - Appends first 8 chars of MD5(songName|artist|platform) for uniqueness
 * - Truncates name+artist to 80 chars to stay within filesystem limits
 */
internal fun buildFileName(songName: String, artist: String, platform: String): String {
    val safeName = songName.replace(UNSAFE_CHARS, "_").take(40).trimEnd('_', ' ')
    val safeArtist = artist.replace(UNSAFE_CHARS, "_").take(40).trimEnd('_', ' ')
    val hash = Md5Utils.md5("$songName|$artist|$platform").take(8)
    return "${safeName}_${safeArtist}_$hash.mp3"
}

// ── SqlDriver cursor helper ──────────────────────────────────────────

internal fun <T> QueryResult<T>.unwrap(): T = when (this) {
    is QueryResult.Value -> value
    is QueryResult.AsyncValue -> value
}
