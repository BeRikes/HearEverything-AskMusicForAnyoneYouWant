package com.example.aimusicplayer.ui.library

import com.example.aimusicplayer.music.DownloadManagerProvider
import com.example.aimusicplayer.music.DownloadedSong
import com.example.aimusicplayer.music.PlaylistManager
import com.example.aimusicplayer.network.music.SongMetadata
import com.example.aimusicplayer.player.MusicPlayerProvider
import com.example.aimusicplayer.viewmodel.ViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import com.example.aimusicplayer.export.ExportImportHandlerProvider
import com.example.aimusicplayer.export.FilePickerProvider
import com.example.aimusicplayer.export.ShareHandlerProvider
import kotlinx.coroutines.launch

/**
 * ViewModel for the local music library (downloaded songs).
 *
 * Displays a list of downloaded songs with play and delete actions.
 * Taps "play" to start local playback, "delete" to remove from storage.
 *
 * @param musicPlayer    Cross-platform audio player
 * @param downloadManager File download manager for listing/deleting songs
 */
class LibraryViewModel(
    private val musicPlayer: MusicPlayerProvider,
    private val downloadManager: DownloadManagerProvider,
    private val playlistManager: PlaylistManager,
    private val exportImportHandler: ExportImportHandlerProvider,
    private val shareHandler: ShareHandlerProvider,
    private val filePicker: FilePickerProvider,
) : ViewModel() {

    /** Callback invoked after a song is deleted (for cache size update). */
    var onCacheChanged: (() -> Unit)? = null

    // ── Song list ───────────────────────────────────────────────────

    private val _downloadedSongs = MutableStateFlow<List<DownloadedSong>>(emptyList())
    val downloadedSongs: StateFlow<List<DownloadedSong>> = _downloadedSongs.asStateFlow()

    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()

    // ── Snackbar ────────────────────────────────────────────────────

    private val _snackbarMessage = MutableStateFlow<String?>(null)
    val snackbarMessage: StateFlow<String?> = _snackbarMessage.asStateFlow()

    // ── Display mode ──────────────────────────────────────────────

    private val _isSimpleMode = MutableStateFlow(false)
    val isSimpleMode: StateFlow<Boolean> = _isSimpleMode.asStateFlow()

    fun toggleDisplayMode() { _isSimpleMode.value = !_isSimpleMode.value }

    // ── Rename dialog ───────────────────────────────────────────────

    private val _renameTarget = MutableStateFlow<DownloadedSong?>(null)
    val renameTarget: StateFlow<DownloadedSong?> = _renameTarget.asStateFlow()

    // ── Export / Import state ─────────────────────────────────────────

    private val _isExporting = MutableStateFlow(false)
    val isExporting: StateFlow<Boolean> = _isExporting.asStateFlow()

    private val _isImporting = MutableStateFlow(false)
    val isImporting: StateFlow<Boolean> = _isImporting.asStateFlow()

    // ── Actions ─────────────────────────────────────────────────────

    /** Load the list of downloaded songs from storage. */
    fun loadSongs() {
        scope.launch {
            _isLoading.value = true
            _downloadedSongs.value = downloadManager.getDownloadedSongs()
            _isLoading.value = false
        }
    }

    /** Play a downloaded song from local storage. */
    fun playSong(song: DownloadedSong) {
        scope.launch {
            val songMeta = SongMetadata(
                songId = song.songId,
                songName = song.songName,
                artist = song.artist,
                platform = song.platform,
                quality = song.quality,
                coverUrl = song.coverUrl,
                realName = song.realName,
                realArtist = song.realArtist,
            )
            musicPlayer.play(
                url = song.localPath,
                title = song.realName ?: song.songName,
                artist = song.realArtist ?: song.artist,
                song = songMeta,
            )
            playlistManager.addToPlaylistFirst(songMeta, song.localPath)
            _snackbarMessage.value = "正在播放: ${song.realName ?: song.songName}"
        }
    }

    /** Replace current playlist with all downloaded songs and play the first. */
    fun playAll() {
        scope.launch {
            val songs = downloadManager.getDownloadedSongs()
            if (songs.isEmpty()) return@launch
            playlistManager.clearPlaylist()
            for (song in songs) {
                val songMeta = SongMetadata(
                    songId = song.songId,
                    songName = song.songName,
                    artist = song.artist,
                    platform = song.platform,
                    quality = song.quality,
                    coverUrl = song.coverUrl,
                    realName = song.realName,
                    realArtist = song.realArtist,
                )
                playlistManager.addToPlaylist(songMeta, song.localPath)
            }
            // Play the first song
            val first = songs.first()
            musicPlayer.play(
                url = first.localPath,
                title = first.realName ?: first.songName,
                artist = first.realArtist ?: first.artist,
                song = SongMetadata(
                    songId = first.songId,
                    songName = first.songName,
                    artist = first.artist,
                    platform = first.platform,
                    quality = first.quality,
                    coverUrl = first.coverUrl,
                    realName = first.realName,
                    realArtist = first.realArtist,
                ),
            )
            _snackbarMessage.value = "已添加 ${songs.size} 首到当前歌单"
        }
    }

    /** Delete a downloaded song. */
    fun deleteSong(song: DownloadedSong) {
        scope.launch {
            try {
                downloadManager.deleteSong(song.songId)
                _downloadedSongs.value = downloadManager.getDownloadedSongs()
                _snackbarMessage.value = "已删除: ${song.songName}"
                onCacheChanged?.invoke()
            } catch (e: Exception) {
                _snackbarMessage.value = "删除失败: ${e.message}"
            }
        }
    }

    /** Show rename dialog for [song]. */
    fun showRenameDialog(song: DownloadedSong) {
        _renameTarget.value = song
    }

    /** Dismiss rename dialog. */
    fun dismissRenameDialog() {
        _renameTarget.value = null
    }

    /** Rename a downloaded song's real name and artist. */
    fun renameSong(song: DownloadedSong, realName: String, realArtist: String) {
        scope.launch {
            try {
                downloadManager.renameSong(song.songId, realName, realArtist)
                // Update local state
                _downloadedSongs.value = _downloadedSongs.value.map {
                    if (it.songId == song.songId) it.copy(realName = realName, realArtist = realArtist) else it
                }
                _snackbarMessage.value = "已更新显示名称"
            } catch (e: Exception) {
                _snackbarMessage.value = "重命名失败: ${e.message}"
            }
        }
    }

    /** 导出所有已下载歌曲为 ZIP 并调起分享。 */
    fun exportAll() {
        scope.launch {
            _isExporting.value = true
            try {
                val songs = downloadManager.getDownloadedSongs()
                if (songs.isEmpty()) {
                    _snackbarMessage.value = "没有可导出的歌曲"
                    return@launch
                }
                val result = exportImportHandler.exportToZip(songs)
                result.onSuccess { exportResult ->
                    // 提示跳过的文件
                    if (exportResult.hasFailures) {
                        _snackbarMessage.value = "部分歌曲导出失败 (${exportResult.exportedSongs}/${exportResult.totalSongs})"
                    }
                    shareHandler.shareFile(exportResult.zipPath, "application/zip")
                }.onFailure { e ->
                    _snackbarMessage.value = "导出失败：${e.message ?: "未知错误"}"
                }
            } catch (e: Exception) {
                _snackbarMessage.value = "导出失败：${e.message ?: "未知错误"}"
            } finally {
                _isExporting.value = false
            }
        }
    }

    /** 从 ZIP 文件路径导入歌曲（由 FilePicker 选择完成后调用）。 */
    fun importFromPath(zipPath: String) {
        scope.launch {
            _isImporting.value = true
            try {
                val result = exportImportHandler.importFromZip(zipPath)
                result.onSuccess { importResult ->
                    val msg = buildString {
                        append("成功导入 ${importResult.importedCount} 首歌曲")
                        if (importResult.skippedCount > 0) {
                            append("，已跳过 ${importResult.skippedCount} 首重复歌曲")
                        }
                    }
                    _snackbarMessage.value = msg
                    loadSongs()
                    onCacheChanged?.invoke()
                }.onFailure { e ->
                    _snackbarMessage.value = when {
                        e is IllegalArgumentException -> "文件格式不支持，请选择 .zip 文件"
                        e is IllegalStateException && e.message?.contains("manifest") == true -> "数据文件损坏，无法导入"
                        e is IllegalStateException && e.message?.contains("版本") == true -> "数据格式版本不兼容，请更新 App"
                        else -> "导入失败：${e.message ?: "未知错误"}"
                    }
                }
            } catch (e: Exception) {
                _snackbarMessage.value = "导入失败：${e.message ?: "未知错误"}"
            } finally {
                _isImporting.value = false
            }
        }
    }

    /** 打开文件选择器，用户选择后自动导入。 */
    fun pickAndImport() {
        scope.launch {
            try {
                val result = filePicker.pickZipFile()
                result.onSuccess { zipPath ->
                    importFromPath(zipPath)
                }.onFailure { e ->
                    if (e !is kotlinx.coroutines.CancellationException) {
                        _snackbarMessage.value = "选择文件失败：${e.message}"
                    }
                }
            } catch (e: Exception) {
                if (e !is kotlinx.coroutines.CancellationException) {
                    _snackbarMessage.value = "选择文件失败：${e.message}"
                }
            }
        }
    }

    fun clearSnackbar() {
        _snackbarMessage.value = null
    }

    /** Refresh the downloaded song list from storage. Call on tab switch. */
    fun refresh() {
        loadSongs()
    }

    init {
        loadSongs()
    }
}
