package com.example.aimusicplayer.export

import platform.Foundation.NSURL
import platform.UIKit.UIActivityViewController
import platform.UIKit.UIApplication

actual class ShareHandler : ShareHandlerProvider {
    override suspend fun shareFile(filePath: String, mimeType: String) {
        val fileUrl = NSURL.fileURLWithPath(filePath)
        val activityVC = UIActivityViewController(
            activityItems = listOf(fileUrl),
            applicationActivities = null,
        )
        val rootVC = UIApplication.sharedApplication.keyWindow?.rootViewController
        rootVC?.presentViewController(activityVC, animated = true, completion = null)
    }
}
