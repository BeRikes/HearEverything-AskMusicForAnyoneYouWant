package com.example.aimusicplayer.fixtures

import com.example.aimusicplayer.music.DownloadManagerProvider
import com.example.aimusicplayer.music.DownloadState
import com.example.aimusicplayer.music.DownloadedSong
import com.example.aimusicplayer.network.music.SongMetadata
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * In-memory fake implementation of [DownloadManagerProvider] for testing ViewModels.
 */
class FakeDownloadManager : DownloadManagerProvider {

    private val downloaded = mutableListOf<DownloadedSong>()
    private val _allDownloads = MutableStateFlow<Map<String, DownloadState>>(emptyMap())
    private val progressFlows = mutableMapOf<String, MutableStateFlow<Float>>()

    override val allDownloads: StateFlow<Map<String, DownloadState>> = _allDownloads.asStateFlow()

    override suspend fun download(audioUrl: String, song: SongMetadata, quality: String, lyrics: String?, coverUrl: String?, realName: String?, realArtist: String?): Result<String> {
        val file = DownloadedSong(song.songId, "test.mp3", song.songName, song.artist, song.platform, audioUrl, "/tmp/test.mp3")
        downloaded.add(file)
        return Result.success("/tmp/test.mp3")
    }

    override suspend fun getDownloadedSongs(): List<DownloadedSong> = downloaded.toList()

    override suspend fun deleteSong(songId: String) { downloaded.removeAll { it.songId == songId } }

    override suspend fun renameSong(songId: String, realName: String, realArtist: String) {
        val idx = downloaded.indexOfFirst { it.songId == songId }
        if (idx >= 0) downloaded[idx] = downloaded[idx].copy(realName = realName, realArtist = realArtist)
    }

    override suspend fun getTotalDownloadSize(): Long = downloaded.sumOf { it.fileSize }

    override suspend fun clearAllDownloads() { downloaded.clear() }

    override fun downloadProgress(songId: String): StateFlow<Float> {
        return progressFlows.getOrPut(songId) { MutableStateFlow(0f) }.asStateFlow()
    }

    fun addSong(song: DownloadedSong) { downloaded.add(song) }
    fun reset() { downloaded.clear(); progressFlows.clear() }
}
