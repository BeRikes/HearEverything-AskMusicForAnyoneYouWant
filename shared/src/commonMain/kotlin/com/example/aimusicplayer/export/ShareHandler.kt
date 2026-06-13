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
