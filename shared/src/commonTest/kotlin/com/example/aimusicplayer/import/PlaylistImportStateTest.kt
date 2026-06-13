package com.example.aimusicplayer.import

import kotlinx.serialization.json.Json
import kotlin.test.Test
import kotlin.test.assertEquals

class PlaylistImportStateTest {

    private val json = Json { ignoreUnknownKeys = true }

    @Test
    fun `ImportedPlaylist serialization roundtrip`() {
        val playlist = ImportedPlaylist(
            id = "pl-001",
            title = "My Imported Playlist",
            coverUrl = "https://example.com/cover.jpg",
            platform = "netease",
            sourceUrl = "https://music.163.com/playlist?id=123",
            importedAt = 1700000000L,
            songs = listOf(
                ImportedSong(
                    originalName = "Song 1",
                    originalArtist = "Artist 1",
                    matchedSongId = "song-1",
                    matchedName = "Song 1 (Matched)",
                    matchedArtist = "Artist 1",
                    matchedPlatform = "qq",
                    playUrl = "https://example.com/play.mp3",
                    coverUrl = "https://example.com/cover1.jpg",
                    quality = "high",
                )
            ),
        )
        val serialized = json.encodeToString(ImportedPlaylist.serializer(), playlist)
        val deserialized = json.decodeFromString(ImportedPlaylist.serializer(), serialized)

        assertEquals(playlist.id, deserialized.id)
        assertEquals(playlist.title, deserialized.title)
        assertEquals(playlist.coverUrl, deserialized.coverUrl)
        assertEquals(playlist.platform, deserialized.platform)
        assertEquals(playlist.sourceUrl, deserialized.sourceUrl)
        assertEquals(playlist.importedAt, deserialized.importedAt)
        assertEquals(1, deserialized.songs.size)
        assertEquals("Song 1", deserialized.songs[0].originalName)
    }

    @Test
    fun `ImportedSong with null matched fields`() {
        val song = ImportedSong(
            originalName = "Unmatched",
            originalArtist = "Unknown",
            matchedSongId = null,
            matchedName = null,
            matchedArtist = null,
            matchedPlatform = null,
            playUrl = null,
            coverUrl = null,
            quality = null,
        )
        val serialized = json.encodeToString(ImportedSong.serializer(), song)
        val deserialized = json.decodeFromString(ImportedSong.serializer(), serialized)
        assertEquals(null, deserialized.matchedSongId)
        assertEquals(null, deserialized.playUrl)
    }

    @Test
    fun `ImportPhase enum values`() {
        assertEquals("IDLE", ImportPhase.IDLE.name)
        assertEquals("PARSING", ImportPhase.PARSING.name)
        assertEquals("FETCHING", ImportPhase.FETCHING.name)
        assertEquals("SEARCHING", ImportPhase.SEARCHING.name)
        assertEquals("CONFIRMING", ImportPhase.CONFIRMING.name)
        assertEquals("SAVING", ImportPhase.SAVING.name)
        assertEquals("COMPLETED", ImportPhase.COMPLETED.name)
        assertEquals("ERROR", ImportPhase.ERROR.name)
    }

    @Test
    fun `TrackConfirmStatus enum values`() {
        assertEquals("SEARCHING", TrackConfirmStatus.SEARCHING.name)
        assertEquals("AWAITING", TrackConfirmStatus.AWAITING.name)
        assertEquals("CONFIRMED", TrackConfirmStatus.CONFIRMED.name)
        assertEquals("SKIPPED", TrackConfirmStatus.SKIPPED.name)
        assertEquals("FAILED", TrackConfirmStatus.FAILED.name)
    }

    @Test
    fun `ImportSession default values`() {
        val session = ImportSession()
        assertEquals(ImportPhase.IDLE, session.phase)
        assertEquals(null, session.playlistDetail)
        assertEquals(0, session.tracks.size)
        assertEquals(null, session.errorMessage)
    }

    @Test
    fun `TrackImportState default values`() {
        val state = TrackImportState(
            index = 0,
            originalName = "Test",
            originalArtist = "Test Artist",
        )
        assertEquals(0, state.index)
        assertEquals("Test", state.originalName)
        assertEquals("Test Artist", state.originalArtist)
        assertEquals(0, state.searchResults.size)
        assertEquals(TrackConfirmStatus.SEARCHING, state.confirmStatus)
        assertEquals(null, state.selectedResult)
        assertEquals(0, state.resolvedPlayUrls.size)
    }

    @Test
    fun `ImportedPlaylist with empty songs list`() {
        val playlist = ImportedPlaylist(
            id = "empty",
            title = "Empty Playlist",
            coverUrl = null,
            platform = "kugou",
            sourceUrl = "https://kugou.com/songlist/gcid_abc/",
            importedAt = 1700000000L,
            songs = emptyList(),
        )
        assertEquals(0, playlist.songs.size)
    }
}
