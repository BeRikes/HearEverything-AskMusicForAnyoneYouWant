package com.example.aimusicplayer.music

import app.cash.sqldelight.db.QueryResult
import app.cash.sqldelight.db.SqlDriver
import com.example.aimusicplayer.cache.Md5Utils
import com.example.aimusicplayer.cache.currentTimeSeconds
import com.example.aimusicplayer.network.music.SongMetadata
import io.ktor.client.HttpClient
import io.ktor.client.engine.darwin.Darwin
import io.ktor.client.request.get
import io.ktor.client.statement.bodyAsChannel
import io.ktor.http.HttpHeaders
import io.ktor.utils.io.core.readBytes
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import platform.Foundation.NSData
import platform.Foundation.NSFileManager
import platform.Foundation.NSSearchPathForDirectoriesInDomains
import platform.Foundation.NSDocumentDirectory
import platform.Foundation.NSUserDomainMask
import platform.Foundation.create
import platform.Foundation.writeToFile

/**
 * iOS implementation of [DownloadManager].
 *
 * ## Storage
 * Audio files are saved to `Documents/downloads/` using
 * the naming scheme `songName_artist_hash8.mp3`.
 *
 * ## Persistence
 * Download metadata is stored in the SQLDelight `DownloadRecord` table
 * (schema v2) via [SqlDriver] raw queries.
 *
 * ## Download engine
 * Uses Ktor [HttpClient] (Darwin engine, already a project dependency)
 * with streaming read + progress callback.
 *
 * @param driver SQLDelight SQL driver (from DatabaseDriverFactory)
 */
actual class DownloadManager(
    private val driver: SqlDriver,
) {
    // ── Coroutine scope ────────────────────────────────────────────

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    // ── HttpClient (dedicated instance) ─────────────────────────────

    private val httpClient = HttpClient(Darwin) {
        engine {
            configureRequest {
                setTimeoutInterval(30.0) // connect timeout
            }
        }
    }

    // ── Download directory ─────────────────────────────────────────

    private val documentsDir: String by lazy {
        NSSearchPathForDirectoriesInDomains(
            NSDocumentDirectory, NSUserDomainMask, true
        ).first() as String
    }

    private val downloadDir: String
        get() {
            val dir = "$documentsDir/downloads"
            NSFileManager.defaultManager.createDirectoryAtPath(
                dir, true, null, null
            )
            return dir
        }

    // ── Reactive state ─────────────────────────────────────────────

    private val _allDownloads = MutableStateFlow<Map<String, DownloadState>>(emptyMap())
    actual val allDownloads: StateFlow<Map<String, DownloadState>> = _allDownloads.asStateFlow()

    private val progressFlows = mutableMapOf<String, MutableStateFlow<Float>>()

    // ══════════════════════════════════════════════════════════════
    // Core operations
    // ══════════════════════════════════════════════════════════════

    actual suspend fun download(
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
        val filePath = "$downloadDir/$fileName"

        // ── Start progress tracking ─────────────────────────────────
        val progressFlow = getOrCreateProgressFlow(key)
        progressFlow.value = 0f
        _allDownloads.value = _allDownloads.value + (key to DownloadState(
            downloadKey = key, songId = song.songId, progress = 0f, isActive = true,
        ))

        return try {
            // ── Stream download ──────────────────────────────────────
            val response = httpClient.get(audioUrl)
            val totalBytes = response.headers[HttpHeaders.ContentLength]
                ?.toLongOrNull() ?: -1L
            val channel = response.bodyAsChannel()

            val data = mutableListOf<Byte>()
            var totalRead = 0L
            while (!channel.isClosedForRead) {
                val chunk = channel.readRemaining()
                val bytes = chunk.readBytes()
                data.addAll(bytes.toList())
                totalRead += bytes.size
                val progress = if (totalBytes > 0)
                    (totalRead.toFloat() / totalBytes).coerceIn(0f, 1f)
                else
                    -1f
                progressFlow.value = progress
                _allDownloads.value = _allDownloads.value + (key to DownloadState(
                    downloadKey = key, songId = song.songId, progress = progress, isActive = true,
                ))
            }

            // ── Write to disk ────────────────────────────────────────
            val nsData = data.toByteArray().let {
                NSData.create(
                    bytes = it.toList().map { b -> b.toUByte() },
                    length = it.size.toULong(),
                )
            }
            val writeSuccess = nsData?.writeToFile(filePath, atomically = true) ?: false
            if (!writeSuccess) {
                throw Exception("Failed to write file to disk: $filePath")
            }

            val fileSize = totalRead

            // ── Download cover image (non-fatal if fails) ──────────────
            var coverLocalPath: String? = null
            if (!coverUrl.isNullOrBlank()) {
                try {
                    val ext = coverUrl.substringAfterLast('.', "jpg")
                        .substringBefore('?').take(8)
                    val coverFileName = "${fileName.removeSuffix(".mp3")}_cover.$ext"
                    val coverFilePath = "$downloadDir/$coverFileName"
                    val coverResp = httpClient.get(coverUrl)
                    val coverData = mutableListOf<Byte>()
                    val coverChannel = coverResp.bodyAsChannel()
                    while (!coverChannel.isClosedForRead) {
                        val chunk = coverChannel.readRemaining()
                        coverData.addAll(chunk.readBytes().toList())
                    }
                    val nsCoverData = coverData.toByteArray().let {
                        NSData.create(
                            bytes = it.toList().map { b -> b.toUByte() },
                            length = it.size.toULong(),
                        )
                    }
                    if (nsCoverData?.writeToFile(coverFilePath, atomically = true) == true) {
                        coverLocalPath = coverFilePath
                    }
                } catch (_: Exception) { /* non-fatal */ }
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
                bindString(6, filePath)
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
                localPath = filePath,
            ))

            Result.success(filePath)
        } catch (e: Exception) {
            // Clean up partial file
            NSFileManager.defaultManager.removeItemAtPath(filePath, null)
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

    actual suspend fun getDownloadedSongs(): List<DownloadedSong> {
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

    actual suspend fun deleteSong(songId: String) {
        // 1. Look up the song in the list to find its local path
        val songs = getDownloadedSongs()
        val song = songs.find { it.songId == songId }
        // 2. Delete the file from disk
        song?.let { NSFileManager.defaultManager.removeItemAtPath(it.localPath, null) }

        // 3. Remove from DB
        driver.execute(
            identifier = null,
            sql = "DELETE FROM DownloadRecord WHERE id = ?",
            parameters = 1,
        ) { bindString(0, songId) }

        // 4. Clear state
        _allDownloads.value = _allDownloads.value - songId
        progressFlows.remove(songId)
    }

    actual suspend fun renameSong(songId: String, realName: String, realArtist: String) {
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

    actual suspend fun getTotalDownloadSize(): Long {
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

    actual suspend fun clearAllDownloads() {
        val songs = getDownloadedSongs()
        for (song in songs) {
            NSFileManager.defaultManager.removeItemAtPath(song.localPath, null)
        }
        driver.execute(
            identifier = null,
            sql = "DELETE FROM DownloadRecord",
            parameters = 0,
        )
        _allDownloads.value = emptyMap()
        progressFlows.clear()
    }

    // ══════════════════════════════════════════════════════════════
    // Reactive state
    // ══════════════════════════════════════════════════════════════

    actual fun downloadProgress(songId: String): StateFlow<Float> =
        getOrCreateProgressFlow(songId).asStateFlow()

    // ══════════════════════════════════════════════════════════════
    // Internal helpers
    // ══════════════════════════════════════════════════════════════

    private fun getOrCreateProgressFlow(songId: String): MutableStateFlow<Float> {
        return progressFlows.getOrPut(songId) {
            MutableStateFlow(0f)
        }
    }

    fun dispose() {
        scope.cancel()
        httpClient.close()
        progressFlows.clear()
    }
}

// ── File naming ─────────────────────────────────────────────────────

private val UNSAFE_CHARS = Regex("""[<>:"/\\|?*\x00-\x1f]""")

internal fun buildFileName(songName: String, artist: String, platform: String): String {
    val safeName = songName.replace(UNSAFE_CHARS, "_").take(40).trimEnd('_', ' ')
    val safeArtist = artist.replace(UNSAFE_CHARS, "_").take(40).trimEnd('_', ' ')
    val hash = Md5Utils.md5("$songName|$artist|$platform").take(8)
    return "${safeName}_${safeArtist}_$hash.mp3"
}

internal fun <T> QueryResult<T>.unwrap(): T = when (this) {
    is QueryResult.Value -> value
    is QueryResult.AsyncValue -> value
}
