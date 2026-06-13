package com.example.aimusicplayer.export

import android.content.Context
import app.cash.sqldelight.db.QueryResult
import app.cash.sqldelight.db.SqlDriver
import com.example.aimusicplayer.music.DownloadedSong
import kotlinx.serialization.json.Json
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream

/**
 * Android 实现：使用 java.util.zip 进行 ZIP 打包/解压。
 *
 * @param context Android Context（用于获取 filesDir/downloads/ 路径）
 * @param driver SQLDelight SQL driver（用于写入导入记录）
 */
actual class ExportImportHandler(
    private val context: Context,
    private val driver: SqlDriver,
) : ExportImportHandlerProvider {
    private val json = Json { ignoreUnknownKeys = true }
    private val downloadDir: File
        get() = File(context.filesDir, "downloads").also { if (!it.exists()) it.mkdirs() }

    override suspend fun exportToZip(songs: List<DownloadedSong>): Result<ExportResult> {
        return try {
            val timestamp = System.currentTimeMillis()
            val zipFile = File(context.cacheDir, "hear_everything_export_$timestamp.zip")

            var exportedCount = 0
            FileOutputStream(zipFile).use { fos ->
                ZipOutputStream(fos).use { zos ->
                    // 1. 写入 manifest.json
                    val manifest = ExportManifest(
                        version = 1,
                        exportedAt = timestamp,
                        songs = songs.map { it.toExportItem() },
                    )
                    zos.putNextEntry(ZipEntry("manifest.json"))
                    zos.write(json.encodeToString(ExportManifest.serializer(), manifest).toByteArray())
                    zos.closeEntry()

                    // 2. 逐个追加 MP3 文件（放在 ZIP 根目录），同时打包本地封面
                    for (song in songs) {
                        val mp3File = File(song.localPath)
                        if (!mp3File.exists() || !mp3File.canRead()) continue
                        exportedCount++
                        zos.putNextEntry(ZipEntry(song.fileName))
                        FileInputStream(mp3File).use { input ->
                            input.copyTo(zos, bufferSize = 8192)
                        }
                        zos.closeEntry()

                        // 3. 如果有本地封面文件，也加入 ZIP
                        if (!song.coverUrl.isNullOrBlank()) {
                            val coverFile = File(song.coverUrl)
                            if (coverFile.exists() && coverFile.canRead()) {
                                val ext = coverFile.extension.ifEmpty { "jpg" }
                                val coverEntry = "cover_${song.fileName.removeSuffix(".mp3")}.$ext"
                                zos.putNextEntry(ZipEntry(coverEntry))
                                FileInputStream(coverFile).use { input ->
                                    input.copyTo(zos, bufferSize = 8192)
                                }
                                zos.closeEntry()
                            }
                        }
                    }
                }
            }

            Result.success(ExportResult(zipFile.absolutePath, songs.size, exportedCount))
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    override suspend fun importFromZip(zipPath: String): Result<ImportResult> {
        return try {
            val zipFile = File(zipPath)
            if (!zipFile.exists() || !zipFile.canRead()) {
                return Result.failure(IllegalArgumentException("文件不存在或无法读取"))
            }

            var manifest: ExportManifest? = null
            val extractedFiles = mutableMapOf<String, File>() // fileName -> tempFile

            // 解压 ZIP
            ZipInputStream(FileInputStream(zipFile)).use { zis ->
                var entry = zis.nextEntry
                while (entry != null) {
                    if (!entry.isDirectory) {
                        val tempFile = File(context.cacheDir, entry.name)
                        FileOutputStream(tempFile).use { fos ->
                            zis.copyTo(fos, bufferSize = 8192)
                        }
                        if (entry.name == "manifest.json") {
                            manifest = json.decodeFromString(
                                ExportManifest.serializer(),
                                tempFile.readText()
                            )
                        } else {
                            extractedFiles[entry.name] = tempFile
                        }
                    }
                    zis.closeEntry()
                    entry = zis.nextEntry
                }
            }

            // 校验 manifest
            val m = manifest ?: return Result.failure(IllegalStateException("manifest.json 缺失"))
            if (m.version != 1) {
                return Result.failure(IllegalStateException("数据格式版本不兼容 (version=${m.version})"))
            }

            // 入库
            var imported = 0
            var skipped = 0
            val errors = mutableListOf<String>()

            for (item in m.songs) {
                val tempFile = extractedFiles[item.fileName]
                if (tempFile == null) {
                    skipped++
                    errors.add("缺失文件: ${item.fileName}")
                    continue
                }

                // 检查重复
                val count: Long = driver.executeQuery(
                    identifier = null,
                    sql = "SELECT COUNT(*) FROM DownloadRecord WHERE id = ?",
                    mapper = { cursor ->
                        cursor.next()
                        QueryResult.Value(cursor.getLong(0) ?: 0L)
                    },
                    parameters = 1,
                ) { bindString(0, item.songId) }.unwrap()

                if (count > 0) {
                    skipped++
                    tempFile.delete()
                    continue
                }

                // 拷贝到下载目录（处理文件名冲突）
                var destFile = File(downloadDir, item.fileName)
                var suffix = 1
                while (destFile.exists()) {
                    val dotIndex = item.fileName.lastIndexOf('.')
                    val base = if (dotIndex > 0) item.fileName.substring(0, dotIndex) else item.fileName
                    val ext = if (dotIndex > 0) item.fileName.substring(dotIndex) else ""
                    destFile = File(downloadDir, "${base}_$suffix$ext")
                    suffix++
                }

                tempFile.copyTo(destFile, overwrite = false)
                tempFile.delete()

                // 恢复封面文件
                val effectiveCoverUrl: String? = if (!item.coverUrl.isNullOrBlank()) {
                    // 如果是远程 URL，保持不变
                    if (item.coverUrl.startsWith("http://") || item.coverUrl.startsWith("https://")) {
                        item.coverUrl
                    } else {
                        // 本地封面：从 ZIP 中查找匹配的封面文件
                        val coverPrefix = "cover_${item.fileName.removeSuffix(".mp3")}."
                        val coverKey = extractedFiles.keys.firstOrNull { it.startsWith(coverPrefix) }
                        if (coverKey != null) {
                            val coverFile = extractedFiles[coverKey]!!
                            val coverExt = coverFile.extension.ifEmpty { "jpg" }
                            val coverDest = File(downloadDir, "${destFile.name.removeSuffix(".mp3")}_cover.$coverExt")
                            coverFile.copyTo(coverDest, overwrite = true)
                            coverFile.delete()
                            extractedFiles.remove(coverKey)
                            coverDest.absolutePath
                        } else {
                            // 没有封面文件，尝试保留远程 URL（可能 coverUrl 本身就是远程的）
                            null
                        }
                    }
                } else null

                // 写入数据库
                driver.execute(
                    identifier = null,
                    sql = """
                        INSERT OR REPLACE INTO DownloadRecord
                        (id, fileName, songName, artist, platform, audioUrl, localPath,
                         fileSize, quality, coverUrl, lyrics, downloadTime, realName, realArtist)
                        VALUES (?, ?, ?, ?, ?, ?, ?, ?, 'standard', ?, ?, ?, ?, ?)
                    """.trimIndent(),
                    parameters = 14,
                ) {
                    bindString(0, item.songId)
                    bindString(1, item.fileName)
                    bindString(2, item.songName)
                    bindString(3, item.artist)
                    bindString(4, item.platform)
                    bindString(5, item.audioUrl)
                    bindString(6, destFile.absolutePath)
                    bindLong(7, item.fileSize)
                    bindString(8, effectiveCoverUrl)
                    bindString(9, item.lyrics)
                    bindLong(10, item.downloadTime)
                    bindString(11, item.realName)
                    bindString(12, item.realArtist)
                }

                imported++
            }

            // 清理 ZIP 缓存文件
            zipFile.delete()
            Result.success(ImportResult(imported, skipped, errors))
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    private fun DownloadedSong.toExportItem() = ExportSongItem(
        songId = songId, fileName = fileName, songName = songName,
        artist = artist, platform = platform, audioUrl = audioUrl,
        fileSize = fileSize, coverUrl = coverUrl, lyrics = lyrics,
        downloadTime = downloadTime, realName = realName, realArtist = realArtist,
    )
}

/**
 * Helper to unwrap [QueryResult].
 */
private fun <T> QueryResult<T>.unwrap(): T = when (this) {
    is QueryResult.Value -> value
    is QueryResult.AsyncValue -> value
}
