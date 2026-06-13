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
