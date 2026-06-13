package com.example.aimusicplayer.export

actual class FilePicker : FilePickerProvider {
    override suspend fun pickZipFile(): Result<String> {
        return Result.failure(UnsupportedOperationException("iOS file picker not yet implemented"))
    }
}
