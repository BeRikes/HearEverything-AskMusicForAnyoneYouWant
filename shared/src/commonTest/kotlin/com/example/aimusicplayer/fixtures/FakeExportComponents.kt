package com.example.aimusicplayer.fixtures

import com.example.aimusicplayer.export.ExportImportHandlerProvider
import com.example.aimusicplayer.export.ExportResult
import com.example.aimusicplayer.export.FilePickerProvider
import com.example.aimusicplayer.export.ImportResult
import com.example.aimusicplayer.export.ShareHandlerProvider
import com.example.aimusicplayer.music.DownloadedSong

/**
 * 测试用 ExportImportHandler 桩。不执行真实 ZIP 操作，直接返回预设结果。
 */
class FakeExportImportHandler : ExportImportHandlerProvider {
    var exportResult: Result<ExportResult> = Result.success(ExportResult("/fake/export.zip", 0, 0))
    var importResult: Result<ImportResult> = Result.success(ImportResult(0, 0))
    var lastExportedSongs: List<DownloadedSong>? = null
    var lastImportedPath: String? = null

    override suspend fun exportToZip(songs: List<DownloadedSong>): Result<ExportResult> {
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
