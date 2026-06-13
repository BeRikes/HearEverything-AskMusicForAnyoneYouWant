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
    override suspend fun shareFile(filePath: String, mimeType: String) {
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
