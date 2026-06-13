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
