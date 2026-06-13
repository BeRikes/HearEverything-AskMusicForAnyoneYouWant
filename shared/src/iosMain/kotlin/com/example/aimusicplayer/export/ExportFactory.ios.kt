package com.example.aimusicplayer.export

import com.example.aimusicplayer.cache.DatabaseDriverFactory

fun createExportImportHandler(): ExportImportHandler {
    val driver = DatabaseDriverFactory().createDriver()
    return ExportImportHandler(driver)
}

fun createShareHandler(): ShareHandler = ShareHandler()

fun createFilePicker(): FilePicker = FilePicker()
