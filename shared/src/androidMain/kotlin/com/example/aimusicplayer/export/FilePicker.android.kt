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
    override suspend fun pickZipFile(): Result<String> {
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
