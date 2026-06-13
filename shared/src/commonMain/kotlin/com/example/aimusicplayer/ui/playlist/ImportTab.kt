package com.example.aimusicplayer.ui.playlist

import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.*
import androidx.compose.foundation.background
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import com.example.aimusicplayer.ui.common.pressFlash
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInWindow
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.DpOffset
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntRect
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Popup
import androidx.compose.ui.window.PopupProperties
import coil3.compose.AsyncImage
import com.example.aimusicplayer.import.ImportPhase
import com.example.aimusicplayer.import.ImportSession
import com.example.aimusicplayer.import.ImportedPlaylist
import com.example.aimusicplayer.import.ImportedSong
import com.example.aimusicplayer.import.TrackConfirmStatus
import com.example.aimusicplayer.import.TrackImportState
import com.example.aimusicplayer.network.music.SongMetadata
import kotlin.math.roundToInt

// ══════════════════════════════════════════════════════════════════
// Main ImportTab — switches between 3 sub-views
// ══════════════════════════════════════════════════════════════════

@Composable
fun ImportTab(
    viewModel: PlaylistViewModel,
    modifier: Modifier = Modifier,
) {
    val importSession by viewModel.importSession.collectAsState()
    val importedPlaylists by viewModel.importedPlaylists.collectAsState()
    val subView by viewModel.importSubView.collectAsState()
    val selectedPlaylistId by viewModel.selectedPlaylistId.collectAsState()

    // Auto-switch views based on import phase
    LaunchedEffect(importSession.phase) {
        when (importSession.phase) {
            ImportPhase.SEARCHING, ImportPhase.CONFIRMING, ImportPhase.SAVING -> {
                viewModel.setImportSubView(1)
            }
            ImportPhase.COMPLETED -> {
                // Save done — reset and return to entry view
                viewModel.resetImport()
                viewModel.refreshImportedPlaylists()
                viewModel.setImportSubView(0)
            }
            ImportPhase.IDLE -> { /* keep current view */ }
            ImportPhase.ERROR -> { /* keep current view to show error */ }
            else -> { /* keep current view */ }
        }
    }

    when (subView) {
        0 -> ImportEntryView(
            importedPlaylists = importedPlaylists,
            importSession = importSession,
            onStartImport = { viewModel.startImport(it) },
            onDeletePlaylist = { viewModel.removeImportedPlaylist(it) },
            onPlayPlaylist = { viewModel.replacePlaylistWithImported(it) },
            onDownloadPlaylist = { viewModel.downloadImportedPlaylist(it) },
            onPlaylistClick = { playlistId ->
                viewModel.selectImportedPlaylist(playlistId)
            },
            modifier = modifier,
        )
        1 -> CardRelayView(
            importSession = importSession,
            onConfirm = { idx, result -> viewModel.confirmTrack(idx, result) },
            onSkip = { idx -> viewModel.skipTrack(idx) },
            onRetry = { idx -> viewModel.retryTrack(idx) },
            onSave = { viewModel.saveImport() },
            onBackToEntry = {
                viewModel.resetImport()
                viewModel.setImportSubView(0)
            },
            onAutoConfirm = {
                viewModel.autoConfirmAll()
            },
            onAbort = {
                viewModel.resetImport()
                viewModel.setImportSubView(0)
            },
            modifier = modifier,
        )
        2 -> {
            val selected = importedPlaylists.firstOrNull { it.id == selectedPlaylistId }
            ImportedPlaylistDetailView(
                playlist = selected,
                onBack = { viewModel.clearSelectedPlaylist() },
                onPlay = { viewModel.playImportedSong(it) },
                onRemove = { index ->
                    viewModel.removeImportedSong(selectedPlaylistId ?: "", index)
                },
                onPlayAll = { viewModel.replacePlaylistWithImported(selectedPlaylistId ?: "") },
                onDeletePlaylist = {
                    viewModel.removeImportedPlaylist(selectedPlaylistId ?: "")
                    viewModel.clearSelectedPlaylist()
                },
                onAddToPlaylist = { song ->
                    val metadata = SongMetadata(
                        songId = song.matchedSongId ?: "",
                        songName = song.matchedName ?: song.originalName,
                        artist = song.matchedArtist ?: song.originalArtist,
                        platform = song.matchedPlatform ?: "",
                        coverUrl = song.coverUrl ?: "",
                        quality = song.quality ?: "standard",
                    )
                    viewModel.addToPlaylist(metadata, song.playUrl)
                },
                modifier = modifier,
            )
        }
    }
}

// ══════════════════════════════════════════════════════════════════
// View 1: Import Entry
// ══════════════════════════════════════════════════════════════════

@Composable
private fun ImportEntryView(
    importedPlaylists: List<ImportedPlaylist>,
    importSession: ImportSession,
    onStartImport: (String) -> Unit,
    onDeletePlaylist: (String) -> Unit,
    onPlayPlaylist: (String) -> Unit,
    onDownloadPlaylist: (String) -> Unit,
    onPlaylistClick: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    var linkText by remember { mutableStateOf("") }
    val focusManager = androidx.compose.ui.platform.LocalFocusManager.current
    val isParsing = importSession.phase == ImportPhase.PARSING || importSession.phase == ImportPhase.FETCHING
    val errorMessage = importSession.let { if (it.phase == ImportPhase.ERROR) it.errorMessage else null }

    Column(modifier = modifier.fillMaxSize().padding(horizontal = 16.dp)) {
        Spacer(Modifier.height(16.dp))

        // Link input
        OutlinedTextField(
            value = linkText,
            onValueChange = { linkText = it },
            placeholder = { Text("粘贴音乐平台歌单分享链接") },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true,
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Go),
            keyboardActions = KeyboardActions(onGo = {
                if (linkText.isNotBlank() && !isParsing) {
                    focusManager.clearFocus()
                    onStartImport(linkText)
                }
            }),
            enabled = !isParsing,
            trailingIcon = {
                if (linkText.isNotEmpty()) {
                    IconButton(onClick = { linkText = "" }) {
                        Icon(Icons.Default.Close, "清除")
                    }
                }
            },
        )

        Spacer(Modifier.height(8.dp))

        // Start button
        Button(
            onClick = {
                focusManager.clearFocus()
                onStartImport(linkText)
            },
            modifier = Modifier.fillMaxWidth(),
            enabled = linkText.isNotBlank() && !isParsing,
            shape = RoundedCornerShape(12.dp),
        ) {
            if (isParsing) {
                CircularProgressIndicator(
                    modifier = Modifier.size(18.dp),
                    strokeWidth = 2.dp,
                    color = MaterialTheme.colorScheme.onPrimary,
                )
                Spacer(Modifier.width(8.dp))
                Text("解析中...")
            } else {
                Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(6.dp))
                Text("开始导入")
            }
        }

        // Error message
        if (errorMessage != null) {
            Spacer(Modifier.height(12.dp))
            Card(
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer),
                shape = RoundedCornerShape(10.dp),
            ) {
                Row(modifier = Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.Error, null, tint = MaterialTheme.colorScheme.error)
                    Spacer(Modifier.width(8.dp))
                    Text(errorMessage, color = MaterialTheme.colorScheme.onErrorContainer,
                        modifier = Modifier.weight(1f), style = MaterialTheme.typography.bodySmall)
                }
            }
        }

        Spacer(Modifier.height(24.dp))
        HorizontalDivider()
        Spacer(Modifier.height(12.dp))

        // Imported playlists
        if (importedPlaylists.isEmpty()) {
            Box(Modifier.fillMaxWidth().weight(1f), contentAlignment = Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("📥", fontSize = MaterialTheme.typography.headlineLarge.fontSize)
                    Spacer(Modifier.height(8.dp))
                    Text("暂无导入的歌单", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                    Text("粘贴音乐平台分享链接开始导入", style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        } else {
            Text("已导入的歌单 (${importedPlaylists.size})", style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Medium)
            Spacer(Modifier.height(8.dp))
            LazyColumn(modifier = Modifier.fillMaxWidth().weight(1f), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                itemsIndexed(importedPlaylists, key = { _, p -> p.id }) { _, playlist ->
                    ImportedPlaylistCard(
                        playlist = playlist,
                        onClick = { onPlaylistClick(playlist.id) },
                        onPlay = { onPlayPlaylist(playlist.id) },
                        onDownload = { onDownloadPlaylist(playlist.id) },
                        onDelete = { onDeletePlaylist(playlist.id) },
                    )
                }
                item { Spacer(Modifier.height(16.dp)) }
            }
        }
    }
}

@Composable
private fun ImportedPlaylistCard(
    playlist: ImportedPlaylist,
    onClick: () -> Unit,
    onPlay: () -> Unit,
    onDownload: () -> Unit,
    onDelete: () -> Unit,
) {
    val matchedCount = playlist.songs.count { it.matchedSongId != null }
    Surface(
        modifier = Modifier.fillMaxWidth().clickable { onClick() },
        shape = RoundedCornerShape(10.dp),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f),
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(Modifier.weight(1f)) {
                Text(playlist.title, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium,
                    maxLines = 1, overflow = TextOverflow.Ellipsis)
                val platformLabel = when (playlist.platform) {
                    "netease" -> "网易云"
                    "qq" -> "QQ音乐"
                    "kugou" -> "酷狗"
                    "bilibili" -> "B站"
                    else -> playlist.platform
                }
                Text("$matchedCount/${playlist.songs.size} 首 · $platformLabel", style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            IconButton(onClick = onPlay, modifier = Modifier.size(36.dp)) {
                Icon(Icons.Default.PlayArrow, "播放", tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(18.dp))
            }
            IconButton(onClick = onDownload, modifier = Modifier.size(36.dp)) {
                Icon(Icons.Default.Download, "下载", tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
                    modifier = Modifier.size(18.dp))
            }
            IconButton(onClick = onDelete, modifier = Modifier.size(36.dp)) {
                Icon(Icons.Default.Delete, "删除", tint = MaterialTheme.colorScheme.error.copy(alpha = 0.6f),
                    modifier = Modifier.size(18.dp))
            }
        }
    }
}

// ══════════════════════════════════════════════════════════════════
// View 2: Card Relay Confirmation
// ══════════════════════════════════════════════════════════════════

@Composable
private fun CardRelayView(
    importSession: ImportSession,
    onConfirm: (Int, SongMetadata) -> Unit,
    onSkip: (Int) -> Unit,
    onRetry: (Int) -> Unit,
    onSave: () -> Unit,
    onBackToEntry: () -> Unit,
    onAutoConfirm: () -> Unit,
    onAbort: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val detail = importSession.playlistDetail
    val tracks = importSession.tracks

    val confirmed = tracks.count { it.confirmStatus == TrackConfirmStatus.CONFIRMED }
    val skipped = tracks.count { it.confirmStatus == TrackConfirmStatus.SKIPPED }
    val searching = tracks.count { it.confirmStatus == TrackConfirmStatus.SEARCHING }
    val awaiting = tracks.count { it.confirmStatus == TrackConfirmStatus.AWAITING }

    // Visible cards: AWAITING first, then SEARCHING, up to 3
    val visibleTracks = tracks.filter {
        it.confirmStatus == TrackConfirmStatus.AWAITING || it.confirmStatus == TrackConfirmStatus.SEARCHING
    }.take(3)

    Column(modifier = modifier.fillMaxSize().padding(horizontal = 16.dp)) {
        Spacer(Modifier.height(12.dp))

        // Progress bar
        Card(
            shape = RoundedCornerShape(10.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)),
        ) {
            Column(Modifier.padding(12.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text(
                            "🎵 ${detail?.title ?: "导入中"} · ${tracks.size}首",
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Bold,
                        )
                        Spacer(Modifier.height(4.dp))
                        Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                            Text("✅ $confirmed", style = MaterialTheme.typography.labelSmall)
                            Text("❌ $skipped", style = MaterialTheme.typography.labelSmall)
                            Text("🔄 $searching", style = MaterialTheme.typography.labelSmall)
                            Text("⏳ $awaiting", style = MaterialTheme.typography.labelSmall)
                        }
                    }
                    if (searching + awaiting > 0) {
                        TextButton(onClick = onAutoConfirm) {
                            Text("自动确认", style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Medium)
                        }
                        TextButton(onClick = onAbort) {
                            Text("放弃", style = MaterialTheme.typography.labelLarge,
                                color = MaterialTheme.colorScheme.error.copy(alpha = 0.7f))
                        }
                    }
                }
            }
        }

        Spacer(Modifier.height(12.dp))

        if (visibleTracks.isEmpty()) {
            // All done — show summary + failed songs detail
            val failedTracks = tracks.filter {
                it.confirmStatus == TrackConfirmStatus.SKIPPED || it.confirmStatus == TrackConfirmStatus.FAILED
            }

            Column(modifier = Modifier.weight(1f).fillMaxWidth()) {
                // Stats header — centered
                Column(
                    modifier = Modifier.weight(1f, fill = false).fillMaxWidth(),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center,
                ) {
                    Text("✅", fontSize = MaterialTheme.typography.headlineLarge.fontSize)
                    Spacer(Modifier.height(12.dp))
                    Text("导入完成！", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                    Text("成功匹配 $confirmed 首  ❌ 跳过 $skipped 首",
                        style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Spacer(Modifier.height(20.dp))
                    Button(onClick = onSave, shape = RoundedCornerShape(12.dp)) {
                        Text("保存到导入歌单")
                    }
                    Spacer(Modifier.height(8.dp))
                    TextButton(onClick = onBackToEntry) {
                        Text("放弃")
                    }
                }

                // Failed songs list
                if (failedTracks.isNotEmpty()) {
                    Spacer(Modifier.height(12.dp))
                    Card(
                        shape = RoundedCornerShape(10.dp),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.3f)),
                    ) {
                        Column(Modifier.padding(12.dp)) {
                            Text(
                                "未导入的歌曲 (${failedTracks.size})：",
                                style = MaterialTheme.typography.labelLarge,
                                fontWeight = FontWeight.Medium,
                                color = MaterialTheme.colorScheme.error,
                            )
                            Spacer(Modifier.height(6.dp))
                            failedTracks.forEach { track ->
                                val reason = when (track.confirmStatus) {
                                    TrackConfirmStatus.SKIPPED -> "搜索超时或无结果"
                                    TrackConfirmStatus.FAILED -> "搜索失败"
                                    else -> ""
                                }
                                Row(
                                    modifier = Modifier.padding(vertical = 2.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                ) {
                                    Text(
                                        "《${track.originalName}》— ${track.originalArtist}",
                                        style = MaterialTheme.typography.bodySmall,
                                        modifier = Modifier.weight(1f),
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis,
                                    )
                                    Spacer(Modifier.width(8.dp))
                                    Text(
                                        reason,
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    )
                                }
                            }
                        }
                    }
                }

                Spacer(Modifier.height(16.dp))
            }
        } else {
            // Card relay
            LazyColumn(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                itemsIndexed(visibleTracks, key = { _, t -> t.index }) { _, track ->
                    ConfirmationCard(
                        track = track,
                        onSelect = { result -> onConfirm(track.index, result) },
                        onSkip = { onSkip(track.index) },
                        onRetry = { onRetry(track.index) },
                    )
                }
                item { Spacer(Modifier.height(16.dp)) }
            }
        }
    }
}

@Composable
private fun ConfirmationCard(
    track: TrackImportState,
    onSelect: (SongMetadata) -> Unit,
    onSkip: () -> Unit,
    onRetry: () -> Unit,
) {
    Card(
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
    ) {
        Column(Modifier.padding(12.dp)) {
            // Header
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(
                        "《${track.originalName}》— ${track.originalArtist}",
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.SemiBold,
                    )
                }
                IconButton(onClick = onSkip, modifier = Modifier.size(32.dp)) {
                    Icon(Icons.Default.Close, "跳过", modifier = Modifier.size(18.dp),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f))
                }
            }

            HorizontalDivider(modifier = Modifier.padding(vertical = 6.dp))

            when (track.confirmStatus) {
                TrackConfirmStatus.SEARCHING -> {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp)
                        Spacer(Modifier.width(8.dp))
                        Text("搜索中...", style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
                TrackConfirmStatus.AWAITING -> {
                    track.searchResults.forEach { result ->
                        Surface(
                            modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp).clickable { onSelect(result) },
                            shape = RoundedCornerShape(8.dp),
                            color = MaterialTheme.colorScheme.surface.copy(alpha = 0.6f),
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                // Cover image
                                if (!result.coverUrl.isNullOrBlank()) {
                                    AsyncImage(
                                        model = result.coverUrl,
                                        contentDescription = "封面",
                                        modifier = Modifier.size(44.dp).clip(RoundedCornerShape(6.dp)),
                                        contentScale = ContentScale.Crop,
                                    )
                                }
                                Spacer(Modifier.width(10.dp))
                                Column(Modifier.weight(1f)) {
                                    Text("${result.songName} - ${result.artist}",
                                        style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.Medium,
                                        maxLines = 1, overflow = TextOverflow.Ellipsis)
                                    Spacer(Modifier.height(2.dp))
                                    PlatformBadgeLabel(result.platform)
                                }
                                Icon(Icons.Default.ChevronRight, "选择", modifier = Modifier.size(16.dp),
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f))
                            }
                        }
                    }
                }
                TrackConfirmStatus.FAILED -> {
                    Text("搜索失败", style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error)
                    Spacer(Modifier.height(4.dp))
                    TextButton(onClick = onRetry) { Text("🔄 重新搜索") }
                }
                else -> { /* CONFIRMED/SKIPPED not rendered in relay */ }
            }
        }
    }
}

// ══════════════════════════════════════════════════════════════════
// View 3: Single Imported Playlist Detail (matches CurrentPlaylistTab style)
// ══════════════════════════════════════════════════════════════════

@Composable
private fun ImportedPlaylistDetailView(
    playlist: ImportedPlaylist?,
    onBack: () -> Unit,
    onPlay: (ImportedSong) -> Unit,
    onRemove: (Int) -> Unit,
    onPlayAll: () -> Unit,
    onDeletePlaylist: () -> Unit,
    onAddToPlaylist: (ImportedSong) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier.fillMaxSize()) {
        // Back button
        TextButton(onClick = onBack, modifier = Modifier.padding(start = 4.dp)) {
            Icon(Icons.Default.ArrowBack, null, modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(4.dp))
            Text("返回")
        }

        if (playlist == null || playlist.songs.isEmpty()) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text("歌单内容为空", style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        } else {
            val songs = playlist.songs
            // Header — matches CurrentPlaylistTab style
            Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(playlist.title, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                Text(
                    "共 ${songs.size} 首",
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.Medium,
                )
                TextButton(onClick = onPlayAll) {
                    Text(
                        "全部播放",
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = FontWeight.Medium,
                    )
                }
                TextButton(onClick = onDeletePlaylist) {
                    Text(
                        "删除歌单",
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.error.copy(alpha = 0.7f),
                    )
                }
            }

            // Song list — matches PlaylistSongCard style exactly
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 4.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                itemsIndexed(songs, key = { _, s -> s.matchedSongId ?: s.originalName }) { index, song ->
                    val hasMatch = song.matchedSongId != null
                    val displayName = if (hasMatch) (song.matchedName ?: song.originalName) else song.originalName
                    val displayArtist = if (hasMatch) (song.matchedArtist ?: song.originalArtist) else song.originalArtist
                    var showTooltip by remember { mutableStateOf(false) }
                    var parentSize by remember { mutableStateOf(IntSize.Zero) }
                    var menuSize by remember { mutableStateOf(IntSize.Zero) }
                    var parentPosX by remember { mutableStateOf(0f) }
                    val density = LocalDensity.current
                    val windowWidth = (parentSize.width + parentPosX * 2f).roundToInt().coerceAtLeast(parentSize.width)

                    Box(modifier = Modifier.onGloballyPositioned { parentSize = it.size; parentPosX = it.positionInWindow().x }) {
                        Surface(
                            modifier = Modifier
                                .fillMaxWidth()
                                .then(if (hasMatch) Modifier.pressFlash(
                                    onTap = { onPlay(song) },
                                    onLongPress = { showTooltip = true },
                                    onRelease = { showTooltip = false },
                                ) else Modifier),
                            shape = RoundedCornerShape(10.dp),
                            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f),
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Text(
                                    "${index + 1}",
                                    style = MaterialTheme.typography.labelMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.width(24.dp),
                                )
                                Column(Modifier.weight(1f)) {
                                    Text(
                                        if (hasMatch) displayName else "《$displayName》",
                                        style = MaterialTheme.typography.bodyMedium,
                                        fontWeight = FontWeight.Medium,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis,
                                    )
                                    Text(
                                        if (hasMatch) displayArtist else "— $displayArtist",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = if (hasMatch) MaterialTheme.colorScheme.onSurfaceVariant
                                                else MaterialTheme.colorScheme.error.copy(alpha = 0.7f),
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis,
                                    )
                                }
                                if (hasMatch) {
                                    IconButton(onClick = { onAddToPlaylist(song) }, modifier = Modifier.size(36.dp)) {
                                        Icon(Icons.Default.Add, "加入歌单",
                                            tint = MaterialTheme.colorScheme.primary,
                                            modifier = Modifier.size(18.dp))
                                    }
                                }
                                IconButton(onClick = { onRemove(index) }, modifier = Modifier.size(36.dp)) {
                                Icon(Icons.Default.Close, "移除",
                                    tint = MaterialTheme.colorScheme.error.copy(alpha = 0.6f),
                                    modifier = Modifier.size(18.dp))
                            }
                        }
                    }

                    if (showTooltip) {
                        Popup(
                            alignment = Alignment.BottomCenter,
                            offset = IntOffset(0, -parentSize.height),
                            onDismissRequest = { showTooltip = false },
                            properties = PopupProperties(focusable = true)
                        ) {
                            Surface(
                                color = MaterialTheme.colorScheme.surface,
                                shape = RoundedCornerShape(8.dp),
                                shadowElevation = 6.dp,
                                modifier = Modifier.widthIn(max = 300.dp)
                            ) {
                                Column(modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp)) {
                                    Text(displayName, style = MaterialTheme.typography.bodyMedium,
                                        fontWeight = FontWeight.Bold, softWrap = true)
                                    Spacer(Modifier.height(2.dp))
                                    Text(displayArtist, style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant, softWrap = true)
                                }
                            }
                        }
                    }
                }
            }
                item { Spacer(Modifier.height(16.dp)) }
            }
        }
    }
}

// ══════════════════════════════════════════════════════════════════
// Shared helpers
// ══════════════════════════════════════════════════════════════════

@Composable
private fun PlatformBadgeLabel(platform: String) {
    val isDark = isSystemInDarkTheme()
    val (label, bgColor, textColor) = when (platform) {
        "netease" -> {
            val color = if (isDark) Color(0xFFEF5350) else Color(0xFFD32F2F)
            Triple("网易云", color.copy(alpha = 0.15f), color)
        }
        "qq" -> {
            val color = if (isDark) Color(0xFF64B5F6) else Color(0xFF1976D2)
            Triple("QQ音乐", color.copy(alpha = 0.15f), color)
        }
        "kugou" -> {
            val color = if (isDark) Color(0xFFBA68C8) else Color(0xFF7B1FA2)
            Triple("酷狗", color.copy(alpha = 0.15f), color)
        }
        "bilibili" -> {
            // B站 brand pink — slightly lighter in dark mode for legibility
            val color = if (isDark) Color(0xFFFF8AAD) else Color(0xFFFB7299)
            Triple("B站", color.copy(alpha = 0.15f), color)
        }
        else -> Triple(platform, MaterialTheme.colorScheme.surfaceVariant, MaterialTheme.colorScheme.onSurfaceVariant)
    }
    Surface(
        shape = RoundedCornerShape(4.dp),
        color = bgColor,
    ) {
        Text(
            label,
            modifier = Modifier.padding(horizontal = 6.dp, vertical = 1.dp),
            style = MaterialTheme.typography.labelSmall,
            color = textColor,
            fontWeight = FontWeight.Medium,
        )
    }
}
