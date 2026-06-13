package com.example.aimusicplayer.export

import app.cash.sqldelight.db.SqlDriver
import com.example.aimusicplayer.music.DownloadedSong

actual class ExportImportHandler(
    private val driver: SqlDriver,
) : ExportImportHandlerProvider {
    override suspend fun exportToZip(songs: List<DownloadedSong>): Result<ExportResult> {
        return Result.failure(UnsupportedOperationException("iOS export not yet implemented"))
    }

    override suspend fun importFromZip(zipPath: String): Result<ImportResult> {
        return Result.failure(UnsupportedOperationException("iOS import not yet implemented"))
    }
}
