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
    suspend fun exportToZip(songs: List<DownloadedSong>): Result<ExportResult>

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
