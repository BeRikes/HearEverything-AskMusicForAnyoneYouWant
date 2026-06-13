package com.example.aimusicplayer.import

import com.example.aimusicplayer.network.music.PlaylistDetail
import com.example.aimusicplayer.network.music.SongMetadata
import kotlinx.serialization.Serializable

// ══════════════════════════════════════════════════════════════════
// Persisted imported playlist
// ══════════════════════════════════════════════════════════════════

@Serializable
data class ImportedPlaylist(
    val id: String,
    val title: String,
    val coverUrl: String?,
    val platform: String,
    val sourceUrl: String,
    val importedAt: Long,
    val songs: List<ImportedSong>,
)

@Serializable
data class ImportedSong(
    val originalName: String,
    val originalArtist: String,
    val matchedSongId: String?,
    val matchedName: String?,
    val matchedArtist: String?,
    val matchedPlatform: String?,
    val playUrl: String?,
    val coverUrl: String?,
    val quality: String?,
)

// ══════════════════════════════════════════════════════════════════
// Import flow state machine
// ══════════════════════════════════════════════════════════════════

enum class ImportPhase {
    IDLE,
    PARSING,
    FETCHING,
    SEARCHING,
    CONFIRMING,
    SAVING,
    COMPLETED,
    ERROR,
}

enum class TrackConfirmStatus {
    SEARCHING,
    AWAITING,
    CONFIRMED,
    SKIPPED,
    FAILED,
}

data class ImportSession(
    val playlistDetail: PlaylistDetail? = null,
    val tracks: List<TrackImportState> = emptyList(),
    val phase: ImportPhase = ImportPhase.IDLE,
    val errorMessage: String? = null,
)

data class TrackImportState(
    val index: Int,
    val originalName: String,
    val originalArtist: String,
    val searchResults: List<SongMetadata> = emptyList(),
    val confirmStatus: TrackConfirmStatus = TrackConfirmStatus.SEARCHING,
    val selectedResult: SongMetadata? = null,
    val resolvedPlayUrls: Map<String, String> = emptyMap(),  // songId → playUrl for all results
)
