package com.example.aimusicplayer.recommendation.data

import co.touchlab.kermit.Logger
import com.example.aimusicplayer.behavior.LocalSongIndex
import com.example.aimusicplayer.behavior.SongIndexEntry
import com.example.aimusicplayer.network.music.MusicBrainzApi
import kotlinx.coroutines.delay

/**
 * MusicBrainz 歌曲索引构建器。
 *
 * 根据用户喜欢的歌手和常听流派，从 MusicBrainz 拉取歌曲元数据
 * 并写入本地 [LocalSongIndex]。
 */
class SongIndexBuilder(
    private val musicBrainz: MusicBrainzApi,
    private val songIndex: LocalSongIndex,
) {
    private val log = Logger.withTag("SongIndexBuilder")

    /**
     * 为指定歌手列表构建索引。
     *
     * @param artists 歌手名称列表
     * @param maxSongsPerArtist 每位歌手最多拉取的歌曲数
     * @return 成功入库的歌曲数量
     */
    suspend fun buildForArtists(
        artists: List<String>,
        maxSongsPerArtist: Int = 30,
    ): Int {
        var totalAdded = 0

        for (artist in artists) {
            try {
                val recordings = fetchArtistSongs(artist, maxSongsPerArtist)
                val entries = recordings.map { recording ->
                    val genre = recording.tags?.firstOrNull()?.name
                    val year = recording.firstReleaseDate?.substringBefore("-")?.toIntOrNull()
                    SongIndexEntry(
                        songId = recording.id,
                        platform = MusicBrainzApi.PLATFORM_KEY,
                        songName = recording.title,
                        artist = artist,
                        genre = genre,
                        year = year,
                        durationMs = recording.length ?: 0L,
                        playCountGlobal = 0L,
                        source = "musicbrainz",
                    )
                }
                songIndex.insertOrIgnoreAll(entries)
                totalAdded += entries.size
                log.d { "buildForArtists: $artist → ${entries.size} songs" }

                // 频率限制：每秒 1 次请求
                delay(1100)
            } catch (e: Exception) {
                log.w { "buildForArtists: $artist failed: ${e.message}" }
            }
        }

        log.i { "buildForArtists: total added $totalAdded songs for ${artists.size} artists" }
        return totalAdded
    }

    /**
     * 为指定流派列表构建索引。
     *
     * @param genres 流派名称列表
     * @param maxPerGenre 每种流派最多拉取数
     * @return 成功入库的歌曲数量
     */
    suspend fun buildForGenres(
        genres: List<String>,
        maxPerGenre: Int = 20,
    ): Int {
        var totalAdded = 0

        for (genre in genres) {
            try {
                val recordings = musicBrainz.searchByTag(genre, maxPerGenre)
                val entries = recordings.recordings.map { recording ->
                    val credits = recording.artistCredit
                    val artist = credits?.firstOrNull()?.name ?: "未知歌手"
                    val year = recording.firstReleaseDate?.substringBefore("-")?.toIntOrNull()
                    SongIndexEntry(
                        songId = recording.id,
                        platform = MusicBrainzApi.PLATFORM_KEY,
                        songName = recording.title,
                        artist = artist,
                        genre = genre,
                        year = year,
                        durationMs = recording.length ?: 0L,
                        playCountGlobal = 0L,
                        source = "musicbrainz",
                    )
                }
                songIndex.insertOrIgnoreAll(entries)
                totalAdded += entries.size
                log.d { "buildForGenres: $genre → ${entries.size} songs" }

                delay(1100)
            } catch (e: Exception) {
                log.w { "buildForGenres: $genre failed: ${e.message}" }
            }
        }

        log.i { "buildForGenres: total added $totalAdded songs for ${genres.size} genres" }
        return totalAdded
    }

    private suspend fun fetchArtistSongs(artist: String, maxSongs: Int): List<com.example.aimusicplayer.network.music.MbRecording> {
        // Step 1: 搜索歌手 MBID
        val artists = musicBrainz.searchArtist(artist)
        val bestMatch = artists.firstOrNull() ?: return emptyList()
        log.d { "fetchArtistSongs: $artist → MBID=${bestMatch.id}" }

        // Step 2: 获取该歌手的录音
        val allRecordings = mutableListOf<com.example.aimusicplayer.network.music.MbRecording>()
        var offset = 0
        while (allRecordings.size < maxSongs) {
            val result = musicBrainz.getArtistRecordings(bestMatch.id, limit = 50, offset = offset)
            if (result.recordings.isEmpty()) break
            allRecordings.addAll(result.recordings)
            if (result.recordings.size < 50) break // 最后一页
            offset += 50
        }

        return allRecordings.take(maxSongs)
    }
}
