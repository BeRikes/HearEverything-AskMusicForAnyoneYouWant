package com.example.aimusicplayer.behavior

import com.example.aimusicplayer.network.music.SongMetadata
import kotlinx.datetime.Clock

/**
 * iOS 端行为追踪器 —— 纯内存实现。
 *
 * 行为数据仅在当前 session 内有效（iOS 端目前主要面向开发和轻量使用）。
 * 未来可升级为 SQLite 持久化。
 */
actual class UserBehaviorTracker {
    private val behaviors = mutableListOf<UserBehavior>()
    private val songIndex = mutableListOf<SongIndexEntry>()

    private var currentSongId: String? = null
    private var currentPlatform: String? = null
    private var playStartMs: Long = 0L
    private var nextId: Long = 1L

    actual suspend fun recordPlayStart(song: SongMetadata, totalDurationMs: Long) {
        // 结束前一首
        currentSongId?.let { prevId ->
            if (prevId != song.songId || currentPlatform != song.platform) {
                val elapsed = Clock.System.now().toEpochMilliseconds() - playStartMs
                finishPlay(elapsed, skipThreshold = 0.3f)
            }
        }

        currentSongId = song.songId
        currentPlatform = song.platform
        playStartMs = Clock.System.now().toEpochMilliseconds()

        behaviors.add(UserBehavior(
            id = nextId++,
            songId = song.songId,
            songName = song.songName,
            artist = song.artist,
            platform = song.platform,
            album = song.album,
            durationMs = if (song.duration > 0) song.duration.toLong() else totalDurationMs,
            playedAt = playStartMs,
        ))

        // 累积到 song_index
        if (songIndex.none { it.songId == song.songId && it.platform == song.platform }) {
            songIndex.add(SongIndexEntry(
                songId = song.songId,
                platform = song.platform,
                songName = song.songName,
                artist = song.artist,
                album = song.album,
                source = "history",
            ))
        }
    }

    actual suspend fun recordPlayEnd(actualPlayMs: Long, skipThreshold: Float) {
        finishPlay(actualPlayMs, skipThreshold)
    }

    private fun finishPlay(actualPlayMs: Long, skipThreshold: Float) {
        val songId = currentSongId ?: return
        val platform = currentPlatform ?: return
        val idx = behaviors.indexOfLast { it.songId == songId && it.platform == platform }
        if (idx < 0) return

        val behavior = behaviors[idx]
        val completed = behavior.durationMs > 0 && actualPlayMs >= behavior.durationMs * 0.9
        val skipped = !completed && behavior.durationMs > 0 && actualPlayMs < behavior.durationMs * skipThreshold

        behaviors[idx] = behavior.copy(
            playDurationMs = actualPlayMs,
            completed = completed,
            skipped = skipped,
        )

        currentSongId = null
        currentPlatform = null
    }

    actual suspend fun toggleLike(songId: String, platform: String, songName: String, artist: String): Boolean {
        val idx = behaviors.indexOfLast { it.songId == songId && it.platform == platform }
        return if (idx >= 0) {
            val toggled = !behaviors[idx].liked
            behaviors[idx] = behaviors[idx].copy(liked = toggled)
            toggled
        } else {
            behaviors.add(UserBehavior(
                id = nextId++,
                songId = songId,
                songName = songName,
                artist = artist,
                platform = platform,
                liked = true,
                playedAt = Clock.System.now().toEpochMilliseconds(),
            ))
            true
        }
    }

    actual suspend fun isLiked(songId: String, platform: String): Boolean {
        return behaviors.any { it.songId == songId && it.platform == platform && it.liked }
    }

    actual suspend fun recordDownload(song: SongMetadata) {
        val idx = behaviors.indexOfLast { it.songId == song.songId && it.platform == song.platform }
        if (idx >= 0) {
            behaviors[idx] = behaviors[idx].copy(downloaded = true)
        } else {
            behaviors.add(UserBehavior(
                id = nextId++,
                songId = song.songId,
                songName = song.songName,
                artist = song.artist,
                platform = song.platform,
                downloaded = true,
                playedAt = Clock.System.now().toEpochMilliseconds(),
            ))
        }
    }

    actual suspend fun getSkipCounts(songIds: List<Pair<String, String>>): Map<String, Int> {
        val result = mutableMapOf<String, Int>()
        for ((songId, platform) in songIds) {
            val count = behaviors.count { it.songId == songId && it.platform == platform && it.skipped }
            if (count > 0) result["${songId}|${platform}"] = count
        }
        return result
    }

    actual suspend fun getAllBehaviors(): List<UserBehavior> {
        return behaviors.sortedByDescending { it.playedAt }
    }

    actual suspend fun getBehaviorCount(): Int = behaviors.size

    actual suspend fun clearAll() {
        behaviors.clear()
        songIndex.clear()
    }

    /** 获取 song_index（iOS 内存实现额外暴露） */
    fun getSongIndex(): List<SongIndexEntry> = songIndex.toList()
}
