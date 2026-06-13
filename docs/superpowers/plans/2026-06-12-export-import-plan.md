# 下载页面导出/导入功能 — 实施计划

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 为 LibraryScreen 增加"导出"（ZIP 打包 + 系统分享）和"导入"（文件选择 + ZIP 解压还原）功能。

**Architecture:** 遵循项目 expect/actual + Provider 接口模式，新增 `export` 包。ExportImportHandler 负责 ZIP 打包/解压，ShareHandler 负责系统分享面板，FilePicker 负责文件选择器。通过 App() composable 工厂注入 LibraryViewModel。

**Tech Stack:** kotlinx.serialization (已有 v1.7.3)、java.util.zip (Android)、Foundation NSData compression (iOS)、Compose Material3 Icons

---

## 文件结构

### 新增文件 (11 个)

| 文件 | 职责 |
|------|------|
| `shared/src/commonMain/.../export/ExportManifest.kt` | `ExportManifest` + `ExportSongItem` + `ImportResult` 数据类 |
| `shared/src/commonMain/.../export/ExportImportHandler.kt` | `ExportImportHandlerProvider` 接口 + expect 类声明 |
| `shared/src/commonMain/.../export/ShareHandler.kt` | `ShareHandlerProvider` 接口 + expect 类声明 |
| `shared/src/commonMain/.../export/FilePicker.kt` | `FilePickerProvider` 接口 + expect 类声明 |
| `shared/src/androidMain/.../export/ExportImportHandler.android.kt` | Android ZIP 实现 (java.util.zip) |
| `shared/src/androidMain/.../export/ShareHandler.android.kt` | Android Intent.ACTION_SEND + FileProvider |
| `shared/src/androidMain/.../export/FilePicker.android.kt` | Android 文件选择器 |
| `shared/src/iosMain/.../export/ExportImportHandler.ios.kt` | iOS ZIP 实现 |
| `shared/src/iosMain/.../export/ShareHandler.ios.kt` | iOS UIActivityViewController |
| `shared/src/iosMain/.../export/FilePicker.ios.kt` | iOS UIDocumentPickerViewController |
| `shared/src/commonTest/.../fixtures/FakeExportComponents.kt` | FakeExportImportHandler + FakeShareHandler + FakeFilePicker |

### 修改文件 (6 个)

| 文件 | 改动概要 |
|------|---------|
| `shared/src/commonMain/.../ui/library/LibraryViewModel.kt` | 新增导出/导入方法，新增状态 Flow |
| `shared/src/commonMain/.../ui/library/LibraryScreen.kt` | 新增导出/导入按钮，加载/禁用状态 |
| `shared/src/commonMain/.../App.kt` | 新增 3 个工厂参数 + LibraryViewModel 构造传参 |
| `androidApp/src/androidMain/.../MainActivity.kt` | 传入 3 个新工厂 |
| `shared/src/iosMain/.../MainViewController.kt` | 传入 3 个新工厂 |
| `androidApp/src/androidMain/AndroidManifest.xml` | 注册 FileProvider |

---

### Task 1: 创建 ExportManifest 数据模型

**Files:**
- Create: `shared/src/commonMain/kotlin/com/example/aimusicplayer/export/ExportManifest.kt`
- Create: `shared/src/commonTest/kotlin/com/example/aimusicplayer/export/ExportManifestTest.kt`

- [ ] **Step 1: 编写序列化/反序列化测试**

```kotlin
// shared/src/commonTest/kotlin/com/example/aimusicplayer/export/ExportManifestTest.kt
package com.example.aimusicplayer.export

import kotlinx.serialization.json.Json
import kotlin.test.Test
import kotlin.test.assertEquals

class ExportManifestTest {

    private val json = Json { prettyPrint = false; ignoreUnknownKeys = true }

    @Test
    fun `serialize and deserialize manifest round-trip`() {
        val songs = listOf(
            ExportSongItem(
                songId = "123|netease",
                fileName = "song_artist_a1b2c3d4.mp3",
                songName = "测试歌曲",
                artist = "测试歌手",
                platform = "netease",
                audioUrl = "https://example.com/audio.mp3",
                fileSize = 1024000L,
                coverUrl = null,
                lyrics = "[00:01.00]测试歌词",
                downloadTime = 1718123456789L,
                realName = "真实歌名",
                realArtist = "真实歌手",
            )
        )
        val manifest = ExportManifest(
            version = 1,
            exportedAt = 1718123456789L,
            appVersion = "1.0.0",
            songs = songs,
        )

        val encoded = json.encodeToString(ExportManifest.serializer(), manifest)
        val decoded = json.decodeFromString(ExportManifest.serializer(), encoded)

        assertEquals(1, decoded.version)
        assertEquals(1, decoded.songs.size)
        assertEquals("测试歌曲", decoded.songs[0].songName)
        assertEquals("真实歌名", decoded.songs[0].realName)
        assertEquals(null, decoded.songs[0].coverUrl)
    }

    @Test
    fun `deserialize manifest v1 with unknown future fields should not fail`() {
        val jsonString = """
            {"version":1,"exportedAt":0,"songs":[],"futureField":"ignored"}
        """.trimIndent()
        val decoded = json.decodeFromString(ExportManifest.serializer(), jsonString)
        assertEquals(1, decoded.version)
        assertEquals(0, decoded.songs.size)
    }
}
```

- [ ] **Step 2: 运行测试验证失败（文件尚未创建）**

```bash
./gradlew :shared:jvmTest --tests "com.example.aimusicplayer.export.ExportManifestTest"
```

- [ ] **Step 3: 创建数据类文件**

```kotlin
// shared/src/commonMain/kotlin/com/example/aimusicplayer/export/ExportManifest.kt
package com.example.aimusicplayer.export

import kotlinx.serialization.Serializable

/**
 * ZIP 包中的元数据清单，记录所有导出歌曲信息。
 *
 * @param version 格式版本号，用于未来兼容
 * @param exportedAt 导出时间戳 (epoch millis)
 * @param appVersion 可选 App 版本标识
 * @param songs 导出歌曲列表
 */
@Serializable
data class ExportManifest(
    val version: Int = 1,
    val exportedAt: Long,
    val appVersion: String = "",
    val songs: List<ExportSongItem>,
)

/**
 * 单首导出歌曲的元数据，与 [com.example.aimusicplayer.music.DownloadedSong] 对应（移除 quality）。
 */
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

/**
 * 导入操作结果。
 */
data class ImportResult(
    val importedCount: Int,
    val skippedCount: Int,
    val errors: List<String> = emptyList(),
)
```

- [ ] **Step 4: 运行测试验证通过**

```bash
./gradlew :shared:jvmTest --tests "com.example.aimusicplayer.export.ExportManifestTest"
```
Expected: 2 tests PASS

- [ ] **Step 5: 提交**

```bash
git add shared/src/commonMain/kotlin/com/example/aimusicplayer/export/ExportManifest.kt shared/src/commonTest/kotlin/com/example/aimusicplayer/export/ExportManifestTest.kt
git commit -m "feat: add ExportManifest data model for export/import feature

Co-Authored-By: Claude Opus 4.8 <noreply@anthropic.com>"
```

---

### Task 2: 创建 ExportImportHandlerProvider 接口 + expect + Fake

**Files:**
- Create: `shared/src/commonMain/kotlin/com/example/aimusicplayer/export/ExportImportHandler.kt`
- Create: `shared/src/commonTest/kotlin/com/example/aimusicplayer/fixtures/FakeExportComponents.kt`

- [ ] **Step 1: 创建 commonMain Provider 接口 + expect 类**

遵循项目 `DownloadManagerProvider` / `DownloadManager` 模式：

```kotlin
// shared/src/commonMain/kotlin/com/example/aimusicplayer/export/ExportImportHandler.kt
package com.example.aimusicplayer.export

import com.example.aimusicplayer.music.DownloadedSong

/**
 * 跨平台 ZIP 导出/导入处理器接口（用于 ViewModel 注入和测试桩）。
 */
interface ExportImportHandlerProvider {
    /**
     * 将已下载歌曲打包为 ZIP 文件。
     * @param songs 已下载歌曲列表
     * @return ZIP 文件绝对路径，或失败原因
     */
    suspend fun exportToZip(songs: List<DownloadedSong>): Result<String>

    /**
     * 从 ZIP 文件导入歌曲。
     * @param zipPath ZIP 文件路径
     * @return 导入结果（导入数量、跳过数量、错误信息）
     */
    suspend fun importFromZip(zipPath: String): Result<ImportResult>
}

/**
 * expect 类声明，平台 actual 实现 ZIP 打包/解压。
 *
 * ## 平台实现
 * - **Android**: java.util.zip.ZipOutputStream / ZipInputStream
 * - **iOS**: Foundation NSData compression
 */
expect class ExportImportHandler : ExportImportHandlerProvider
```

- [ ] **Step 2: 创建 Fake 测试桩（与 FakeShareHandler、FakeFilePicker 一起）**

```kotlin
// shared/src/commonTest/kotlin/com/example/aimusicplayer/fixtures/FakeExportComponents.kt
package com.example.aimusicplayer.fixtures

import com.example.aimusicplayer.export.ExportImportHandlerProvider
import com.example.aimusicplayer.export.FilePickerProvider
import com.example.aimusicplayer.export.ImportResult
import com.example.aimusicplayer.export.ShareHandlerProvider
import com.example.aimusicplayer.music.DownloadedSong

/**
 * 测试用 ExportImportHandler 桩。不执行真实 ZIP 操作，直接返回预设结果。
 */
class FakeExportImportHandler : ExportImportHandlerProvider {
    var exportResult: Result<String> = Result.success("/fake/export.zip")
    var importResult: Result<ImportResult> = Result.success(ImportResult(0, 0))
    var lastExportedSongs: List<DownloadedSong>? = null
    var lastImportedPath: String? = null

    override suspend fun exportToZip(songs: List<DownloadedSong>): Result<String> {
        lastExportedSongs = songs
        return exportResult
    }

    override suspend fun importFromZip(zipPath: String): Result<ImportResult> {
        lastImportedPath = zipPath
        return importResult
    }
}

/**
 * 测试用 ShareHandler 桩。不调起真实分享面板。
 */
class FakeShareHandler : ShareHandlerProvider {
    var lastSharedPath: String? = null
    var lastSharedMime: String? = null
    var shareCount = 0

    override suspend fun shareFile(filePath: String, mimeType: String) {
        lastSharedPath = filePath
        lastSharedMime = mimeType
        shareCount++
    }
}

/**
 * 测试用 FilePicker 桩。返回预设路径，不打开真实文件选择器。
 */
class FakeFilePicker : FilePickerProvider {
    var pickResult: Result<String> = Result.success("/fake/picked.zip")
    var pickCallCount = 0

    override suspend fun pickZipFile(): Result<String> {
        pickCallCount++
        return pickResult
    }
}
```

- [ ] **Step 3: 验证编译**

```bash
./gradlew :shared:jvmTest
```

- [ ] **Step 4: 提交**

```bash
git add shared/src/commonMain/kotlin/com/example/aimusicplayer/export/ExportImportHandler.kt shared/src/commonTest/kotlin/com/example/aimusicplayer/fixtures/FakeExportComponents.kt
git commit -m "feat: add ExportImportHandlerProvider interface + expect + fakes

Co-Authored-By: Claude Opus 4.8 <noreply@anthropic.com>"
```

---

### Task 3: 创建 ShareHandlerProvider + FilePickerProvider + expect

**Files:**
- Create: `shared/src/commonMain/kotlin/com/example/aimusicplayer/export/ShareHandler.kt`
- Create: `shared/src/commonMain/kotlin/com/example/aimusicplayer/export/FilePicker.kt`

- [ ] **Step 1: 创建 ShareHandler Provider 接口 + expect**

```kotlin
// shared/src/commonMain/kotlin/com/example/aimusicplayer/export/ShareHandler.kt
package com.example.aimusicplayer.export

/**
 * 跨平台系统分享处理器接口。
 */
interface ShareHandlerProvider {
    /**
     * 调起系统分享面板分享文件。
     * @param filePath 要分享的文件绝对路径
     * @param mimeType 文件 MIME 类型（如 "application/zip"）
     */
    suspend fun shareFile(filePath: String, mimeType: String)
}

/**
 * expect 类声明。
 * - **Android**: Intent.ACTION_SEND + FileProvider
 * - **iOS**: UIActivityViewController
 */
expect class ShareHandler : ShareHandlerProvider
```

- [ ] **Step 2: 创建 FilePicker Provider 接口 + expect**

```kotlin
// shared/src/commonMain/kotlin/com/example/aimusicplayer/export/FilePicker.kt
package com.example.aimusicplayer.export

/**
 * 跨平台文件选择器接口。
 */
interface FilePickerProvider {
    /**
     * 打开系统文件选择器，选取 ZIP 文件。
     * @return 选中文件的路径，用户取消时返回 Result.failure(CancellationException)
     */
    suspend fun pickZipFile(): Result<String>
}

/**
 * expect 类声明。
 * - **Android**: ActivityResultContracts.OpenDocument
 * - **iOS**: UIDocumentPickerViewController
 */
expect class FilePicker : FilePickerProvider
```

- [ ] **Step 3: 验证编译**

```bash
./gradlew :shared:compileDebugKotlinAndroid
```

- [ ] **Step 4: 提交**

```bash
git add shared/src/commonMain/kotlin/com/example/aimusicplayer/export/ShareHandler.kt shared/src/commonMain/kotlin/com/example/aimusicplayer/export/FilePicker.kt
git commit -m "feat: add ShareHandler + FilePicker provider interfaces and expect declarations

Co-Authored-By: Claude Opus 4.8 <noreply@anthropic.com>"
```

---

### Task 4: 实现 ExportImportHandler Android actual

**Files:**
- Create: `shared/src/androidMain/kotlin/com/example/aimusicplayer/export/ExportImportHandler.android.kt`

- [ ] **Step 1: 创建 Android actual 实现**

```kotlin
// shared/src/androidMain/kotlin/com/example/aimusicplayer/export/ExportImportHandler.android.kt
package com.example.aimusicplayer.export

import android.content.Context
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
    private val json = Json { prettyPrint = false; ignoreUnknownKeys = true }
    private val downloadDir: File
        get() = File(context.filesDir, "downloads").also { if (!it.exists()) it.mkdirs() }

    actual override suspend fun exportToZip(songs: List<DownloadedSong>): Result<String> {
        return try {
            val timestamp = System.currentTimeMillis()
            val zipFile = File(context.cacheDir, "hear_everything_export_$timestamp.zip")

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

                    // 2. 逐个追加 MP3 文件
                    for (song in songs) {
                        val mp3File = File(song.localPath)
                        if (!mp3File.exists() || !mp3File.canRead()) continue
                        zos.putNextEntry(ZipEntry(song.fileName))
                        FileInputStream(mp3File).use { input ->
                            input.copyTo(zos, bufferSize = 8192)
                        }
                        zos.closeEntry()
                    }
                }
            }

            Result.success(zipFile.absolutePath)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    actual override suspend fun importFromZip(zipPath: String): Result<ImportResult> {
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
                val cursor = driver.executeQuery(
                    null,
                    "SELECT COUNT(*) FROM DownloadRecord WHERE id = ?",
                    { bindString(0, item.songId) },
                    1,
                )
                var count = 0L
                if (cursor.next()) { count = cursor.getLong(0) ?: 0L }

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

                // 写入数据库
                driver.execute(
                    null,
                    """
                        INSERT OR REPLACE INTO DownloadRecord 
                        (id, fileName, songName, artist, platform, audioUrl, localPath, 
                         fileSize, quality, coverUrl, lyrics, downloadTime, realName, realArtist)
                        VALUES (?, ?, ?, ?, ?, ?, ?, ?, 'standard', ?, ?, ?, ?, ?)
                    """.trimIndent(),
                    14,
                ) {
                    bindString(0, item.songId)
                    bindString(1, item.fileName)
                    bindString(2, item.songName)
                    bindString(3, item.artist)
                    bindString(4, item.platform)
                    bindString(5, item.audioUrl)
                    bindString(6, destFile.absolutePath)
                    bindLong(7, item.fileSize)
                    bindString(8, item.coverUrl)
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
```

- [ ] **Step 2: 验证 Android 编译**

```bash
./gradlew :shared:compileDebugKotlinAndroid
```

- [ ] **Step 3: 提交**

```bash
git add shared/src/androidMain/kotlin/com/example/aimusicplayer/export/ExportImportHandler.android.kt
git commit -m "feat: implement ExportImportHandler Android actual (java.util.zip)

Co-Authored-By: Claude Opus 4.8 <noreply@anthropic.com>"
```

---

### Task 5: 实现 ShareHandler Android actual

**Files:**
- Create: `shared/src/androidMain/kotlin/com/example/aimusicplayer/export/ShareHandler.android.kt`
- Create: `shared/src/androidMain/kotlin/com/example/aimusicplayer/export/ExportFactory.android.kt` (含三个工厂函数)

- [ ] **Step 1: 创建 Android actual 实现**

```kotlin
// shared/src/androidMain/kotlin/com/example/aimusicplayer/export/ShareHandler.android.kt
package com.example.aimusicplayer.export

import android.content.Context
import android.content.Intent
import androidx.core.content.FileProvider
import java.io.File

/**
 * Android 实现：通过 FileProvider + Intent.ACTION_SEND 调起分享面板。
 *
 * @param context Android Context（applicationContext）
 */
actual class ShareHandler(
    private val context: Context,
) : ShareHandlerProvider {
    actual override suspend fun shareFile(filePath: String, mimeType: String) {
        val file = File(filePath)
        if (!file.exists()) return

        val uri = FileProvider.getUriForFile(
            context,
            "${context.packageName}.fileprovider",
            file,
        )

        val shareIntent = Intent(Intent.ACTION_SEND).apply {
            type = mimeType
            putExtra(Intent.EXTRA_STREAM, uri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }

        context.startActivity(
            Intent.createChooser(shareIntent, "分享到")
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        )
    }
}
```

- [ ] **Step 2: 创建 FilePicker Android actual**

```kotlin
// shared/src/androidMain/kotlin/com/example/aimusicplayer/export/FilePicker.android.kt
package com.example.aimusicplayer.export

import android.content.Context

/**
 * Android 实现：文件选择器需要 Activity 生命周期交互。
 *
 * 推荐在 Compose UI 层使用 [androidx.activity.compose.rememberLauncherForActivityResult]，
 * 通过 ViewModel 回调传递选中路径。此类提供基础框架。
 *
 * @param context Android Context
 */
actual class FilePicker(
    private val context: Context,
) : FilePickerProvider {
    actual override suspend fun pickZipFile(): Result<String> {
        // Android 文件选择需通过 Activity Result API。
        // 在 LibraryScreen 中使用 rememberLauncherForActivityResult，
        // 选择文件后调用 viewModel.importFromPath(path)。
        return Result.failure(
            UnsupportedOperationException(
                "请通过 Compose UI 文件选择器选择文件"
            )
        )
    }
}
```

- [ ] **Step 3: 创建 Android 工厂函数**

```kotlin
// shared/src/androidMain/kotlin/com/example/aimusicplayer/export/ExportFactory.android.kt
package com.example.aimusicplayer.export

import android.content.Context
import com.example.aimusicplayer.cache.DatabaseDriverFactory

/** 创建 [ExportImportHandler] Android 实现。 */
fun createExportImportHandler(context: Context): ExportImportHandler {
    val driver = DatabaseDriverFactory(context).createDriver()
    return ExportImportHandler(context, driver)
}

/** 创建 [ShareHandler] Android 实现。 */
fun createShareHandler(context: Context): ShareHandler = ShareHandler(context)

/** 创建 [FilePicker] Android 实现。 */
fun createFilePicker(context: Context): FilePicker = FilePicker(context)
```

- [ ] **Step 4: 验证编译**

```bash
./gradlew :shared:compileDebugKotlinAndroid
```

- [ ] **Step 5: 提交**

```bash
git add shared/src/androidMain/kotlin/com/example/aimusicplayer/export/ShareHandler.android.kt shared/src/androidMain/kotlin/com/example/aimusicplayer/export/FilePicker.android.kt shared/src/androidMain/kotlin/com/example/aimusicplayer/export/ExportFactory.android.kt
git commit -m "feat: implement ShareHandler + FilePicker Android actuals + factory

Co-Authored-By: Claude Opus 4.8 <noreply@anthropic.com>"
```

---

### Task 6: 创建 iOS actual 骨架

**Files:**
- Create: `shared/src/iosMain/kotlin/com/example/aimusicplayer/export/ExportImportHandler.ios.kt`
- Create: `shared/src/iosMain/kotlin/com/example/aimusicplayer/export/ShareHandler.ios.kt`
- Create: `shared/src/iosMain/kotlin/com/example/aimusicplayer/export/FilePicker.ios.kt`
- Create: `shared/src/iosMain/kotlin/com/example/aimusicplayer/export/ExportFactory.ios.kt`

- [ ] **Step 1: 创建所有 iOS actual 文件**

```kotlin
// shared/src/iosMain/kotlin/com/example/aimusicplayer/export/ExportImportHandler.ios.kt
package com.example.aimusicplayer.export

import app.cash.sqldelight.db.SqlDriver
import com.example.aimusicplayer.music.DownloadedSong

actual class ExportImportHandler(
    private val driver: SqlDriver,
) : ExportImportHandlerProvider {
    actual override suspend fun exportToZip(songs: List<DownloadedSong>): Result<String> {
        return Result.failure(UnsupportedOperationException("iOS export not yet implemented"))
    }

    actual override suspend fun importFromZip(zipPath: String): Result<ImportResult> {
        return Result.failure(UnsupportedOperationException("iOS import not yet implemented"))
    }
}
```

```kotlin
// shared/src/iosMain/kotlin/com/example/aimusicplayer/export/ShareHandler.ios.kt
package com.example.aimusicplayer.export

import platform.Foundation.NSURL
import platform.UIKit.UIActivityViewController
import platform.UIKit.UIApplication

actual class ShareHandler : ShareHandlerProvider {
    actual override suspend fun shareFile(filePath: String, mimeType: String) {
        val fileUrl = NSURL.fileURLWithPath(filePath)
        val activityVC = UIActivityViewController(
            activityItems = listOf(fileUrl),
            applicationActivities = null,
        )
        val rootVC = UIApplication.sharedApplication.keyWindow?.rootViewController
        rootVC?.presentViewController(activityVC, animated = true, completion = null)
    }
}
```

```kotlin
// shared/src/iosMain/kotlin/com/example/aimusicplayer/export/FilePicker.ios.kt
package com.example.aimusicplayer.export

actual class FilePicker : FilePickerProvider {
    actual override suspend fun pickZipFile(): Result<String> {
        return Result.failure(UnsupportedOperationException("iOS file picker not yet implemented"))
    }
}
```

```kotlin
// shared/src/iosMain/kotlin/com/example/aimusicplayer/export/ExportFactory.ios.kt
package com.example.aimusicplayer.export

import com.example.aimusicplayer.cache.DatabaseDriverFactory

fun createExportImportHandler(): ExportImportHandler {
    val driver = DatabaseDriverFactory().createDriver()
    return ExportImportHandler(driver)
}

fun createShareHandler(): ShareHandler = ShareHandler()

fun createFilePicker(): FilePicker = FilePicker()
```

- [ ] **Step 2: 提交**

```bash
git add shared/src/iosMain/kotlin/com/example/aimusicplayer/export/
git commit -m "feat: add iOS export/import actual skeletons + factory

Co-Authored-By: Claude Opus 4.8 <noreply@anthropic.com>"
```

---

### Task 7: 更新 LibraryViewModel

**Files:**
- Modify: `shared/src/commonMain/kotlin/com/example/aimusicplayer/ui/library/LibraryViewModel.kt`

- [ ] **Step 1: 修改构造函数签名和新增状态/方法**

当前构造函数（`LibraryViewModel.kt` 第 23-27 行）：
```kotlin
class LibraryViewModel(
    private val musicPlayer: MusicPlayerProvider,
    private val downloadManager: DownloadManagerProvider,
    private val playlistManager: PlaylistManager,
) : ViewModel() {
```

修改为（使用 Provider 接口以支持测试桩）：
```kotlin
class LibraryViewModel(
    private val musicPlayer: MusicPlayerProvider,
    private val downloadManager: DownloadManagerProvider,
    private val playlistManager: PlaylistManager,
    private val exportImportHandler: ExportImportHandlerProvider,
    private val shareHandler: ShareHandlerProvider,
    private val filePicker: FilePickerProvider,
) : ViewModel() {
```

新增 import（在文件头部）：
```kotlin
import com.example.aimusicplayer.export.ExportImportHandlerProvider
import com.example.aimusicplayer.export.FilePickerProvider
import com.example.aimusicplayer.export.ImportResult
import com.example.aimusicplayer.export.ShareHandlerProvider
```

新增状态 Flow（放在 `_renameTarget` 之后，约第 55 行）：
```kotlin
// ── Export / Import state ─────────────────────────────────────────
private val _isExporting = MutableStateFlow(false)
val isExporting: StateFlow<Boolean> = _isExporting.asStateFlow()

private val _isImporting = MutableStateFlow(false)
val isImporting: StateFlow<Boolean> = _isImporting.asStateFlow()
```

新增方法（放在 `renameSong()` 之后、`clearSnackbar()` 之前，约第 170 行）：
```kotlin
/** 导出所有已下载歌曲为 ZIP 并调起分享。 */
fun exportAll() {
    scope.launch {
        _isExporting.value = true
        try {
            val songs = downloadManager.getDownloadedSongs()
            if (songs.isEmpty()) {
                _snackbarMessage.value = "没有可导出的歌曲"
                return@launch
            }
            val result = exportImportHandler.exportToZip(songs)
            result.onSuccess { zipPath ->
                shareHandler.shareFile(zipPath, "application/zip")
            }.onFailure { e ->
                _snackbarMessage.value = "导出失败：${e.message ?: "未知错误"}"
            }
        } catch (e: Exception) {
            _snackbarMessage.value = "导出失败：${e.message ?: "未知错误"}"
        } finally {
            _isExporting.value = false
        }
    }
}

/** 从 ZIP 文件路径导入歌曲（由 FilePicker 选择完成后调用）。 */
fun importFromPath(zipPath: String) {
    scope.launch {
        _isImporting.value = true
        try {
            val result = exportImportHandler.importFromZip(zipPath)
            result.onSuccess { importResult ->
                val msg = buildString {
                    append("成功导入 ${importResult.importedCount} 首歌曲")
                    if (importResult.skippedCount > 0) {
                        append("，已跳过 ${importResult.skippedCount} 首重复歌曲")
                    }
                }
                _snackbarMessage.value = msg
                loadSongs()
                onCacheChanged?.invoke()
            }.onFailure { e ->
                _snackbarMessage.value = "导入失败：${e.message ?: "未知错误"}"
            }
        } catch (e: Exception) {
            _snackbarMessage.value = "导入失败：${e.message ?: "未知错误"}"
        } finally {
            _isImporting.value = false
        }
    }
}

/** 打开文件选择器，用户选择后自动导入。 */
fun pickAndImport() {
    scope.launch {
        try {
            val result = filePicker.pickZipFile()
            result.onSuccess { zipPath ->
                importFromPath(zipPath)
            }.onFailure { e ->
                if (e !is kotlinx.coroutines.CancellationException) {
                    _snackbarMessage.value = "选择文件失败：${e.message}"
                }
            }
        } catch (e: Exception) {
            if (e !is kotlinx.coroutines.CancellationException) {
                _snackbarMessage.value = "选择文件失败：${e.message}"
            }
        }
    }
}
```

- [ ] **Step 2: 验证编译**

```bash
./gradlew :shared:compileDebugKotlinAndroid
```

- [ ] **Step 3: 提交**

```bash
git add shared/src/commonMain/kotlin/com/example/aimusicplayer/ui/library/LibraryViewModel.kt
git commit -m "feat: add export/import methods to LibraryViewModel

Co-Authored-By: Claude Opus 4.8 <noreply@anthropic.com>"
```

---

### Task 8: 更新 App.kt（依赖注入）

**Files:**
- Modify: `shared/src/commonMain/kotlin/com/example/aimusicplayer/App.kt`

- [ ] **Step 1: 修改 App() composable 签名**

在当前签名（约第 138-148 行）末尾新增 3 个工厂参数：
```kotlin
@Composable
fun App(
    storage: SettingsStorage,
    createMusicPlayer: () -> MusicPlayer,
    createDownloadManager: () -> DownloadManager,
    createCacheRepository: () -> CacheRepository,
    createPlaylistManager: () -> PlaylistManager,
    createSystemMediaController: () -> SystemMediaController,
    createBehaviorTracker: () -> UserBehaviorTracker,
    createSongIndex: () -> LocalSongIndex,
    createExportImportHandler: () -> ExportImportHandler,
    createShareHandler: () -> ShareHandler,
    createFilePicker: () -> FilePicker,
) {
```

新增 import：
```kotlin
import com.example.aimusicplayer.export.ExportImportHandler
import com.example.aimusicplayer.export.FilePicker
import com.example.aimusicplayer.export.ShareHandler
```

- [ ] **Step 2: 修改 LibraryViewModel 创建处**

在 LibraryViewModel 创建之前（约第 234 行），添加组件创建：
```kotlin
val exportImportHandler = remember { createExportImportHandler() }
val shareHandler = remember { createShareHandler() }
val filePicker = remember { createFilePicker() }
```

修改 LibraryViewModel 构造（当前第 235-241 行）：
```kotlin
val libraryViewModel = remember {
    LibraryViewModel(
        musicPlayer = musicPlayer,
        downloadManager = downloadManager,
        playlistManager = playlistManager,
        exportImportHandler = exportImportHandler,
        shareHandler = shareHandler,
        filePicker = filePicker,
    )
}
```

- [ ] **Step 3: 验证编译**

```bash
./gradlew :shared:compileDebugKotlinAndroid
```

- [ ] **Step 4: 提交**

```bash
git add shared/src/commonMain/kotlin/com/example/aimusicplayer/App.kt
git commit -m "feat: inject export/import dependencies into App composable

Co-Authored-By: Claude Opus 4.8 <noreply@anthropic.com>"
```

---

### Task 9: 更新 LibraryScreen UI

**Files:**
- Modify: `shared/src/commonMain/kotlin/com/example/aimusicplayer/ui/library/LibraryScreen.kt`

- [ ] **Step 1: 修改按钮区域**

替换当前按钮 Row（约第 204-224 行）。需要先在 LibraryScreen composable 中收集新状态：

在 `LibraryScreen` composable 函数体中（`val isSimpleMode` 之后约第 125 行）添加：
```kotlin
val isExporting by viewModel.isExporting.collectAsState()
val isImporting by viewModel.isImporting.collectAsState()
val isBusy = isExporting || isImporting
```

将当前按钮 Row：
```kotlin
Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
    TextButton(onClick = viewModel::toggleDisplayMode) { ... }
    if (songs.isNotEmpty()) {
        TextButton(onClick = { viewModel.playAll() }) { ... }
    }
}
```

替换为：
```kotlin
Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
    // 简洁/原始 切换
    TextButton(
        onClick = viewModel::toggleDisplayMode,
        enabled = !isBusy,
    ) {
        Text(
            if (isSimpleMode) "简洁" else "原始",
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
    // 全部播放
    if (songs.isNotEmpty()) {
        TextButton(
            onClick = { viewModel.playAll() },
            enabled = !isBusy,
        ) {
            Icon(
                Icons.Default.PlayArrow,
                contentDescription = null,
                modifier = Modifier.size(16.dp),
                tint = MaterialTheme.colorScheme.primary,
            )
            Spacer(Modifier.width(4.dp))
            Text("全部播放", color = MaterialTheme.colorScheme.primary)
        }
    }
    // 导出
    if (songs.isNotEmpty()) {
        TextButton(
            onClick = { viewModel.exportAll() },
            enabled = !isBusy,
        ) {
            if (isExporting) {
                CircularProgressIndicator(
                    modifier = Modifier.size(16.dp),
                    strokeWidth = 2.dp,
                    color = Color.Black,
                )
            } else {
                Icon(
                    Icons.Default.Share,
                    contentDescription = null,
                    modifier = Modifier.size(16.dp),
                    tint = Color.Black,
                )
            }
            Spacer(Modifier.width(4.dp))
            Text(
                if (isExporting) "导出中..." else "导出",
                color = Color.Black,
            )
        }
    }
    // 导入
    TextButton(
        onClick = { viewModel.pickAndImport() },
        enabled = !isBusy,
    ) {
        if (isImporting) {
            CircularProgressIndicator(
                modifier = Modifier.size(16.dp),
                strokeWidth = 2.dp,
                color = Color.Black,
            )
        } else {
            Icon(
                Icons.Default.Add,
                contentDescription = null,
                modifier = Modifier.size(16.dp),
                tint = Color.Black,
            )
        }
        Spacer(Modifier.width(4.dp))
        Text(
            if (isImporting) "导入中..." else "导入",
            color = Color.Black,
        )
    }
}
```

新增 import：
```kotlin
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.ui.graphics.Color
```

> **图标说明:** `Icons.Default.Share` 在 Material Icons Default (Filled) 套装中可用。导入按钮使用 `Icons.Default.Add`（在 Default 套装中），如后续需要更合适的图标，可引入 `material-icons-extended` 使用 `FileOpen`。

- [ ] **Step 2: 验证编译**

```bash
./gradlew :shared:compileDebugKotlinAndroid
```

- [ ] **Step 3: 提交**

```bash
git add shared/src/commonMain/kotlin/com/example/aimusicplayer/ui/library/LibraryScreen.kt
git commit -m "feat: add export/import buttons to LibraryScreen

Co-Authored-By: Claude Opus 4.8 <noreply@anthropic.com>"
```

---

### Task 10: 更新平台宿主 + FileProvider 配置

**Files:**
- Modify: `androidApp/src/androidMain/kotlin/com/example/aimusicplayer/MainActivity.kt`
- Modify: `androidApp/src/androidMain/AndroidManifest.xml`
- Create: `androidApp/src/androidMain/res/xml/file_paths.xml`
- Modify: `shared/src/iosMain/kotlin/com/example/aimusicplayer/MainViewController.kt`

- [ ] **Step 1: 更新 Android MainActivity**

在 `MainActivity.kt` 中新增 import：
```kotlin
import com.example.aimusicplayer.export.createExportImportHandler
import com.example.aimusicplayer.export.createFilePicker
import com.example.aimusicplayer.export.createShareHandler
```

在 `setContent { App(...) }` 调用中新增 3 个参数：
```kotlin
setContent {
    App(
        storage = storage,
        createMusicPlayer = { MusicPlayerHolder.musicPlayer },
        createDownloadManager = { createDownloadManager(applicationContext) },
        createCacheRepository = { createCacheRepository(applicationContext) },
        createPlaylistManager = { createPlaylistManager(applicationContext) },
        createSystemMediaController = { MusicPlayerHolder.systemMediaController },
        createBehaviorTracker = { createUserBehaviorTracker(applicationContext) },
        createSongIndex = { createLocalSongIndex(applicationContext) },
        createExportImportHandler = { createExportImportHandler(applicationContext) },
        createShareHandler = { createShareHandler(applicationContext) },
        createFilePicker = { createFilePicker(applicationContext) },
    )
}
```

- [ ] **Step 2: 注册 FileProvider**

在 `AndroidManifest.xml` 的 `<application>` 标签内添加：
```xml
<provider
    android:name="androidx.core.content.FileProvider"
    android:authorities="${applicationId}.fileprovider"
    android:exported="false"
    android:grantUriPermissions="true">
    <meta-data
        android:name="android.support.FILE_PROVIDER_PATHS"
        android:resource="@xml/file_paths" />
</provider>
```

创建 `androidApp/src/androidMain/res/xml/file_paths.xml`：
```xml
<?xml version="1.0" encoding="utf-8"?>
<paths>
    <cache-path name="shared_exports" path="/" />
</paths>
```

- [ ] **Step 3: 更新 iOS MainViewController**

在 `MainViewController.kt` 中新增 import：
```kotlin
import com.example.aimusicplayer.export.createExportImportHandler
import com.example.aimusicplayer.export.createFilePicker
import com.example.aimusicplayer.export.createShareHandler
```

在 `App()` 调用中新增 3 个参数：
```kotlin
App(
    storage = storage,
    createMusicPlayer = { MusicPlayer() },
    createDownloadManager = { createDownloadManager() },
    createCacheRepository = { createCacheRepository() },
    createPlaylistManager = { PlaylistManager() },
    createSystemMediaController = { SystemMediaController() },
    createBehaviorTracker = { UserBehaviorTracker() },
    createSongIndex = { LocalSongIndex() },
    createExportImportHandler = { createExportImportHandler() },
    createShareHandler = { createShareHandler() },
    createFilePicker = { createFilePicker() },
)
```

- [ ] **Step 4: 验证完整编译**

```bash
./gradlew :shared:compileDebugKotlinAndroid :androidApp:assembleDebug
```
Expected: BUILD SUCCESSFUL

- [ ] **Step 5: 提交**

```bash
git add androidApp/src/androidMain/kotlin/com/example/aimusicplayer/MainActivity.kt androidApp/src/androidMain/AndroidManifest.xml androidApp/src/androidMain/res/xml/file_paths.xml shared/src/iosMain/kotlin/com/example/aimusicplayer/MainViewController.kt
git commit -m "feat: wire export/import factories in platform hosts + FileProvider config

Co-Authored-By: Claude Opus 4.8 <noreply@anthropic.com>"
```

---

### Task 11: 更新 ViewModel 测试

**Files:**
- Modify: `shared/src/androidUnitTest/kotlin/com/example/aimusicplayer/ui/library/LibraryViewModelRobolectricTest.kt`

- [ ] **Step 1: 修改 setUp 以注入 Fake 组件**

由于 `LibraryViewModel` 构造函数新增了 3 个参数，需更新测试 setUp。

当前 setUp 中的 ViewModel 创建（第 42 行）：
```kotlin
viewModel = LibraryViewModel(musicPlayer, downloadManager, playlistManager)
```

修改为：
```kotlin
import com.example.aimusicplayer.fixtures.FakeExportImportHandler
import com.example.aimusicplayer.fixtures.FakeFilePicker
import com.example.aimusicplayer.fixtures.FakeShareHandler

// 在测试类中新增：
private lateinit var fakeExportImport: FakeExportImportHandler
private lateinit var fakeShareHandler: FakeShareHandler
private lateinit var fakeFilePicker: FakeFilePicker

// 在 setUp() 中构造：
fakeExportImport = FakeExportImportHandler()
fakeShareHandler = FakeShareHandler()
fakeFilePicker = FakeFilePicker()

viewModel = LibraryViewModel(
    musicPlayer, downloadManager, playlistManager,
    fakeExportImport, fakeShareHandler, fakeFilePicker,
)
```

- [ ] **Step 2: 新增导出/导入测试**

```kotlin
@Test
fun `exportAll calls exportToZip with downloaded songs`() {
    fakeExportImport.exportResult = Result.success("/fake/export.zip")
    viewModel.exportAll()
    testDispatcher.scheduler.advanceUntilIdle()
    assertNotNull(fakeExportImport.lastExportedSongs)
    assertEquals(1, fakeShareHandler.shareCount)
}

@Test
fun `importFromPath shows success snackbar with count`() {
    fakeExportImport.importResult = Result.success(ImportResult(5, 2))
    viewModel.importFromPath("/test.zip")
    testDispatcher.scheduler.advanceUntilIdle()
    val msg = viewModel.snackbarMessage.value
    assertNotNull(msg)
    assertTrue(msg!!.contains("5"))
    assertTrue(msg.contains("2"))
}

@Test
fun `importFromPath shows error snackbar on failure`() {
    fakeExportImport.importResult = Result.failure(RuntimeException("文件损坏"))
    viewModel.importFromPath("/bad.zip")
    testDispatcher.scheduler.advanceUntilIdle()
    val msg = viewModel.snackbarMessage.value
    assertNotNull(msg)
    assertTrue(msg!!.contains("导入失败"))
}
```

- [ ] **Step 3: 运行测试验证**

```bash
./gradlew :shared:testDebugUnitTest --tests "com.example.aimusicplayer.ui.library.LibraryViewModelRobolectricTest"
```
Expected: All tests PASS (existing + new)

- [ ] **Step 4: 提交**

```bash
git add shared/src/androidUnitTest/kotlin/com/example/aimusicplayer/ui/library/LibraryViewModelRobolectricTest.kt
git commit -m "test: add export/import tests for LibraryViewModel

Co-Authored-By: Claude Opus 4.8 <noreply@anthropic.com>"
```

---

### Task 12: 最终构建与测试验证

- [ ] **Step 1: 完整编译**

```bash
./gradlew :shared:compileDebugKotlinAndroid :androidApp:assembleDebug
```
Expected: BUILD SUCCESSFUL

- [ ] **Step 2: 运行全部测试**

```bash
./gradlew :shared:allTests
```
Expected: All tests PASS（新增测试 + 已有测试无回归）

- [ ] **Step 3: 最终提交（如有遗漏文件）**

```bash
git status
git add <any_remaining_files>
git commit -m "chore: finalize export/import feature

Co-Authored-By: Claude Opus 4.8 <noreply@anthropic.com>"
```

---

## 已知限制与后续优化

1. **iOS ZIP 实现:** 当前为骨架代码（抛出 UnsupportedOperationException）。后续需在 macOS 环境完善——推荐使用 `NSFileWrapper` 或引入 KMP ZIP 库。
2. **FilePicker Android:** 当前 `FilePicker.android.kt` 的 `pickZipFile()` 返回 UnsupportedOperationException。推荐后续改为 Compose `rememberLauncherForActivityResult` 方式集成，在 LibraryScreen 中处理并将路径传回 ViewModel.importFromPath()。
3. **图标:** 导入按钮使用 `Icons.Default.Add`。后续可引入 `material-icons-extended` 使用 `FileOpen`。
4. **文件大小检查:** 100MB 提醒未在 commonMain 中实现（无 `java.io.File`），可在 ExportImportHandler 返回结果中附带文件大小信息。
