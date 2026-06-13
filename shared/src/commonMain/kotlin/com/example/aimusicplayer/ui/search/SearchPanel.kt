package com.example.aimusicplayer.ui.search

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Error
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Search
import coil3.compose.AsyncImage
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInWindow
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntRect
import androidx.compose.ui.unit.DpOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Popup
import androidx.compose.ui.window.PopupProperties
import com.example.aimusicplayer.ui.common.pressFlash
import com.example.aimusicplayer.music.DownloadState
import com.example.aimusicplayer.network.music.SongMetadata
import kotlinx.coroutines.delay
import kotlin.math.roundToInt

// ═══════════════════════════════════════════════════════════════════════════
// SearchPanel — animated search panel embedded in the chat page
// ═══════════════════════════════════════════════════════════════════════════

/**
 * An animated search panel that wraps search form and results.
 *
 * Expands below the search shortcut bar when [expanded] is true.
 *
 * ## States
 * - **Initial**: Prompt to enter a song name
 * - **Searching**: Progress indicator with step description
 * - **Results**: Scrollable list of [SearchResultCard] items
 * - **No results**: Message with retry suggestion
 */
@Composable
fun SearchPanel(
    expanded: Boolean,
    onDismiss: () -> Unit,
    viewModel: SearchViewModel,
    modifier: Modifier = Modifier,
) {
    val songName by viewModel.songName.collectAsState()
    val artist by viewModel.artist.collectAsState()
    val searchResults by viewModel.searchResults.collectAsState()
    val isSearching by viewModel.isSearching.collectAsState()
    val searchError by viewModel.searchError.collectAsState()
    val searchStep by viewModel.searchStep.collectAsState()
    val hasSearched by viewModel.hasSearched.collectAsState()
    val currentSong by viewModel.currentSong.collectAsState()
    val isPlaying by viewModel.isPlaying.collectAsState()
    val downloadStates by viewModel.downloadStates.collectAsState()
    val preparingDownloads by viewModel.preparingDownloads.collectAsState()
    val downloadErrors by viewModel.downloadErrors.collectAsState()
    val playlistSongIds by viewModel.playlistSongIds.collectAsState()

    // Refresh playlist status on first composition
    LaunchedEffect(Unit) { viewModel.refreshPlaylistStatus() }

    // Clear errors automatically
    LaunchedEffect(searchError) {
        searchError?.let { viewModel.clearError() }
    }

    AnimatedVisibility(
        visible = expanded,
        enter = expandVertically() + fadeIn(),
        exit = shrinkVertically() + fadeOut(),
        modifier = modifier,
    ) {
        Surface(
            tonalElevation = 2.dp,
            shadowElevation = 4.dp,
            color = MaterialTheme.colorScheme.surface,
        ) {
            Column(
                modifier = Modifier.fillMaxWidth(),
            ) {
                // ── Header ─────────────────────────────────────────────
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(start = 16.dp, end = 4.dp, top = 8.dp, bottom = 4.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = "搜索歌曲",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                    IconButton(
                        onClick = onDismiss,
                        modifier = Modifier.size(32.dp),
                    ) {
                        Icon(
                            Icons.Default.Close,
                            contentDescription = "关闭",
                            modifier = Modifier.size(20.dp),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }

                // ── Search form ────────────────────────────────────────
                SearchForm(
                    songName = songName,
                    artist = artist,
                    onSongNameChanged = viewModel::onSongNameChanged,
                    onArtistChanged = viewModel::onArtistChanged,
                    onSearch = viewModel::search,
                    onStopSearch = viewModel::stopSearch,
                    isSearching = isSearching,
                    searchStep = searchStep,
                )

                // ── Content area ───────────────────────────────────────
                when {
                    // Has results — show list IMMEDIATELY, even while still searching more platforms
                    searchResults.isNotEmpty() -> {
                        Column(
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            // Now playing indicator
                            currentSong?.let { song ->
                                NowPlayingBar(
                                    song = song,
                                    isPlaying = isPlaying,
                                )
                            }

                            // Results count + ongoing search indicator
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 20.dp, vertical = 4.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Text(
                                    text = "找到 ${searchResults.size} 首",
                                    style = MaterialTheme.typography.labelLarge,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    fontWeight = FontWeight.Medium,
                                )
                                if (isSearching && searchStep != null) {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                                    ) {
                                        CircularProgressIndicator(
                                            modifier = Modifier.size(14.dp),
                                            strokeWidth = 2.dp,
                                            color = MaterialTheme.colorScheme.primary,
                                        )
                                        Text(
                                            text = searchStep!!,
                                            style = MaterialTheme.typography.labelSmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        )
                                    }
                                }
                            }

                            // Results list
                            LazyColumn(
                                modifier = Modifier.height(360.dp),
                                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 4.dp),
                                verticalArrangement = Arrangement.spacedBy(8.dp),
                            ) {
                                items(searchResults, key = { it.song.songId + it.song.platform }) { item ->
                                    SearchResultCard(
                                        item = item,
                                        isCurrentlyPlaying = currentSong?.songId == item.song.songId &&
                                            currentSong?.platform == item.song.platform,
                                        isPlaying = isPlaying,
                                        downloadState = downloadStates["${item.song.songId}|${item.song.platform}"],
                                        isPreparing = preparingDownloads.contains(item.song.songId),
                                        hasError = downloadErrors.contains(item.song.songId),
                                        onPlay = { viewModel.playResult(item) },
                                        onDownload = { viewModel.downloadResult(item) },
                                        onAddToPlaylist = { viewModel.addToPlaylist(item) },
                                        isInPlaylist = playlistSongIds.contains("${item.song.songId}|${item.song.platform}"),
                                    )
                                }
                                // Show a subtle loading footer while still searching
                                if (isSearching) {
                                    item {
                                        Box(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .padding(vertical = 12.dp),
                                            contentAlignment = Alignment.Center,
                                        ) {
                                            Row(
                                                verticalAlignment = Alignment.CenterVertically,
                                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                            ) {
                                                CircularProgressIndicator(
                                                    modifier = Modifier.size(16.dp),
                                                    strokeWidth = 2.dp,
                                                    color = MaterialTheme.colorScheme.primary.copy(alpha = 0.6f),
                                                )
                                                Text(
                                                    text = "继续搜索更多平台...",
                                                    style = MaterialTheme.typography.labelMedium,
                                                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                                                )
                                            }
                                        }
                                    }
                                }
                                item { Spacer(Modifier.height(8.dp)) }
                            }
                        }
                    }

                    // Searching but no results yet — show full progress
                    isSearching -> {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(200.dp),
                            contentAlignment = Alignment.Center,
                        ) {
                            Column(
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.spacedBy(12.dp),
                            ) {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(36.dp),
                                    color = MaterialTheme.colorScheme.primary,
                                    strokeWidth = 3.dp,
                                )
                                Text(
                                    text = if (searchStep != null) searchStep!! else "搜索中...",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                        }
                    }

                    // No results after search
                    hasSearched && searchResults.isEmpty() -> {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(200.dp),
                            contentAlignment = Alignment.Center,
                        ) {
                            Column(
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.spacedBy(12.dp),
                                modifier = Modifier.padding(32.dp),
                            ) {
                                Text(
                                    text = "未找到匹配的歌曲",
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.SemiBold,
                                    color = MaterialTheme.colorScheme.onSurface,
                                )

                                val queryText = if (artist.isNotBlank()) "\"$songName — $artist\"" else "\"$songName\""
                                Text(
                                    text = "搜索 $queryText\n已尝试所有平台，未找到可播放的音源",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    textAlign = TextAlign.Center,
                                )

                                Button(
                                    onClick = viewModel::search,
                                    shape = RoundedCornerShape(10.dp),
                                    colors = ButtonDefaults.buttonColors(
                                        containerColor = MaterialTheme.colorScheme.secondary,
                                    ),
                                ) {
                                    Text("重新搜索")
                                }

                                Text(
                                    text = "提示：尝试简化关键词或检查拼写",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
                                )
                            }
                        }
                    }

                    // Initial idle state
                    else -> {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(200.dp),
                            contentAlignment = Alignment.Center,
                        ) {
                            Column(
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.spacedBy(12.dp),
                                modifier = Modifier.padding(32.dp),
                            ) {
                                Icon(
                                    Icons.Default.Search,
                                    contentDescription = null,
                                    modifier = Modifier.size(48.dp),
                                    tint = MaterialTheme.colorScheme.primary.copy(alpha = 0.6f),
                                )
                                Text(
                                    text = "输入歌曲名即可搜索",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

// ═══════════════════════════════════════════════════════════════════════════
// Search form (inline for the panel)
// ═══════════════════════════════════════════════════════════════════════════

@Composable
private fun SearchForm(
    songName: String,
    artist: String,
    onSongNameChanged: (String) -> Unit,
    onArtistChanged: (String) -> Unit,
    onSearch: () -> Unit,
    onStopSearch: () -> Unit,
    isSearching: Boolean,
    searchStep: String?,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        // Song name input
        OutlinedTextField(
            value = songName,
            onValueChange = onSongNameChanged,
            placeholder = { Text("歌曲名") },
            leadingIcon = {
                Icon(
                    Icons.Default.MusicNote,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                )
            },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true,
            enabled = !isSearching,
            shape = RoundedCornerShape(12.dp),
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Next),
            colors = OutlinedTextFieldDefaults.colors(
                focusedBorderColor = MaterialTheme.colorScheme.primary,
                focusedLeadingIconColor = MaterialTheme.colorScheme.primary,
            ),
        )

        // Artist input
        OutlinedTextField(
            value = artist,
            onValueChange = onArtistChanged,
            placeholder = { Text("歌手名（选填，用于精确匹配）") },
            leadingIcon = {
                Icon(
                    Icons.Default.Person,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true,
            enabled = !isSearching,
            shape = RoundedCornerShape(12.dp),
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
            keyboardActions = KeyboardActions(onSearch = { onSearch() }),
            colors = OutlinedTextFieldDefaults.colors(
                focusedBorderColor = MaterialTheme.colorScheme.secondary,
            ),
        )

        // Search button area
        if (isSearching) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                // Searching indicator
                Button(
                    onClick = {},
                    enabled = false,
                    modifier = Modifier
                        .weight(1f)
                        .height(48.dp),
                    shape = RoundedCornerShape(12.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.primary,
                        disabledContainerColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.6f),
                    ),
                ) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(20.dp),
                        strokeWidth = 2.dp,
                        color = MaterialTheme.colorScheme.onPrimary,
                    )
                    Spacer(Modifier.width(8.dp))
                    Text("搜索中...")
                }
                // Stop button
                Button(
                    onClick = onStopSearch,
                    modifier = Modifier.height(48.dp),
                    shape = RoundedCornerShape(12.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.error,
                    ),
                ) {
                    Text("停止", fontWeight = FontWeight.Medium, color = MaterialTheme.colorScheme.onError)
                }
            }
        } else {
            Button(
                onClick = onSearch,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(48.dp),
                enabled = songName.isNotBlank() && !isSearching,
                shape = RoundedCornerShape(12.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.primary,
                ),
            ) {
                Icon(
                    Icons.Default.Search,
                    contentDescription = null,
                    modifier = Modifier.size(20.dp),
                )
                Spacer(Modifier.width(8.dp))
                Text("搜索", fontWeight = FontWeight.Medium)
            }
        }

        // Search step indicator
        AnimatedVisibility(visible = searchStep != null) {
            searchStep?.let { step ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 4.dp),
                    horizontalArrangement = Arrangement.Center,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(14.dp),
                        strokeWidth = 2.dp,
                        color = MaterialTheme.colorScheme.primary,
                    )
                    Spacer(Modifier.width(8.dp))
                    Text(
                        step,
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
    }
}

// ═══════════════════════════════════════════════════════════════════════════
// Search result card
// ═══════════════════════════════════════════════════════════════════════════

@Composable
private fun SearchResultCard(
    item: SearchResultItem,
    isCurrentlyPlaying: Boolean,
    isPlaying: Boolean,
    downloadState: DownloadState?,
    isPreparing: Boolean = false,
    hasError: Boolean = false,
    onPlay: () -> Unit,
    onDownload: () -> Unit,
    onAddToPlaylist: (() -> Unit)? = null,
    isInPlaylist: Boolean = false,
) {
    val song = item.song
    val isDownloading = isPreparing || (downloadState != null && downloadState.isActive && downloadState.progress < 1f)
    val isDownloaded = downloadState != null && downloadState.progress >= 1f

    // Keep progress bar visible for at least 1.5s after completion
    var showProgress by remember { mutableStateOf(false) }
    LaunchedEffect(downloadState?.progress, downloadState?.isActive) {
        if (downloadState != null && downloadState.progress < 1f && downloadState.isActive) {
            showProgress = true
        } else if (showProgress) {
            val completed = downloadState != null && downloadState.progress >= 1f
            if (completed) delay(1_500)
            showProgress = false
        }
    }

    var showTooltip by remember { mutableStateOf(false) }
    var parentSize by remember { mutableStateOf(IntSize.Zero) }
    var menuSize by remember { mutableStateOf(IntSize.Zero) }
    var parentPosX by remember { mutableStateOf(0f) }
    val density = LocalDensity.current
    val windowWidth = (parentSize.width + parentPosX * 2f).roundToInt().coerceAtLeast(parentSize.width)

    Box(modifier = Modifier.onGloballyPositioned { parentSize = it.size; parentPosX = it.positionInWindow().x }) {
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .pressFlash(
                    onTap = { onPlay() },
                    onLongPress = { showTooltip = true },
                    onRelease = { showTooltip = false },
                ),
            colors = CardDefaults.cardColors(
                containerColor = if (isCurrentlyPlaying)
                    MaterialTheme.colorScheme.primary.copy(alpha = 0.12f)
                else
                    MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
            ),
            shape = RoundedCornerShape(12.dp),
        ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                // Cover image
                val coverUrl = song.coverUrl
                if (!coverUrl.isNullOrBlank()) {
                    AsyncImage(
                        model = coverUrl,
                        contentDescription = "专辑封面",
                        modifier = Modifier
                            .size(48.dp)
                            .clip(RoundedCornerShape(10.dp)),
                    )
                }

                Spacer(Modifier.width(12.dp))

                // Song info
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = song.songName,
                        style = MaterialTheme.typography.bodyLarge,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Spacer(Modifier.height(2.dp))
                    Text(
                        text = song.artist.ifBlank { "未知艺术家" },
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Spacer(Modifier.height(4.dp))

                    // Badges row
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        // Platform badge (colored)
                        PlatformBadge(platform = song.platform)

                        // Cache indicator
                        Surface(
                            shape = RoundedCornerShape(4.dp),
                            color = if (item.fromCache)
                                MaterialTheme.colorScheme.tertiary.copy(alpha = 0.1f)
                            else
                                MaterialTheme.colorScheme.secondary.copy(alpha = 0.1f),
                        ) {
                            Text(
                                if (item.fromCache) "缓存" else "在线",
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 1.dp),
                                style = MaterialTheme.typography.labelSmall,
                                color = if (item.fromCache)
                                    MaterialTheme.colorScheme.tertiary
                                else
                                    MaterialTheme.colorScheme.secondary,
                                fontWeight = FontWeight.Medium,
                            )
                        }

                        // Quality badge
                        if (song.quality != "standard") {
                            Surface(
                                shape = RoundedCornerShape(4.dp),
                                color = MaterialTheme.colorScheme.primary.copy(alpha = 0.1f),
                            ) {
                                Text(
                                    song.quality,
                                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 1.dp),
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.primary,
                                    fontWeight = FontWeight.Medium,
                                )
                            }
                        }
                    }
                }

                Spacer(Modifier.width(6.dp))

                // Download button (hidden if already downloaded)
                if (!isDownloaded) {
                    Box(
                        modifier = Modifier
                            .size(32.dp)
                            .clickable(onClick = onDownload),
                        contentAlignment = Alignment.Center,
                    ) {
                        if (isDownloading) {
                            if (downloadState != null && downloadState.isActive) {
                                CircularProgressIndicator(
                                    progress = { downloadState.progress },
                                    modifier = Modifier.size(16.dp),
                                    strokeWidth = 2.dp,
                                )
                            } else {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(16.dp),
                                    strokeWidth = 2.dp,
                                )
                            }
                        } else {
                            Icon(
                                Icons.Default.Download,
                                contentDescription = "下载",
                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.size(18.dp),
                            )
                        }
                    }
                }

                // Error indicator
                if (hasError) {
                    Icon(
                        Icons.Default.Error,
                        contentDescription = "下载失败",
                        tint = MaterialTheme.colorScheme.error,
                        modifier = Modifier.size(20.dp),
                    )
                    Spacer(Modifier.width(6.dp))
                }

                // Add to playlist button
                if (onAddToPlaylist != null) {
                    Box(
                        modifier = Modifier
                            .size(32.dp)
                            .clickable(onClick = onAddToPlaylist),
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(
                            Icons.Default.Add,
                            contentDescription = if (isInPlaylist) "已添加" else "添加到歌单",
                            tint = if (isInPlaylist) MaterialTheme.colorScheme.primary
                                   else MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(18.dp),
                        )
                    }
                    Spacer(Modifier.width(6.dp))
                }

            }

            // Download progress bar + percentage (min 1.5s visible)
            if (showProgress && downloadState != null) {
                Spacer(Modifier.height(8.dp))
                val determinate = downloadState.progress >= 0f
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    if (determinate) {
                        LinearProgressIndicator(
                            progress = { downloadState.progress },
                            modifier = Modifier
                                .weight(1f)
                                .height(4.dp),
                            color = MaterialTheme.colorScheme.primary,
                            trackColor = MaterialTheme.colorScheme.surfaceVariant,
                        )
                        Spacer(Modifier.width(8.dp))
                        Text(
                            text = "${(downloadState.progress * 100).toInt()}%",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.primary,
                            fontWeight = FontWeight.Medium,
                        )
                    } else {
                        // Content-Length unknown — indeterminate animation
                        LinearProgressIndicator(
                            modifier = Modifier
                                .weight(1f)
                                .height(4.dp),
                            color = MaterialTheme.colorScheme.primary,
                            trackColor = MaterialTheme.colorScheme.surfaceVariant,
                        )
                    }
                }
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
                        Text(song.songName, style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.Bold, softWrap = true)
                        Spacer(Modifier.height(2.dp))
                        Text(song.artist, style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant, softWrap = true)
                    }
                }
            }
        }
    }
}

// ═══════════════════════════════════════════════════════════════════════════
// Platform badge
// ═══════════════════════════════════════════════════════════════════════════

@Composable
private fun PlatformBadge(platform: String) {
    val (label, bgColor, textColor) = when (platform) {
        "netease" -> Triple(
            "网易云",
            MaterialTheme.colorScheme.error.copy(alpha = 0.1f),
            MaterialTheme.colorScheme.error,
        )
        "qq" -> Triple(
            "QQ音乐",
            MaterialTheme.colorScheme.secondary.copy(alpha = 0.1f),
            MaterialTheme.colorScheme.secondary,
        )
        "kugou" -> Triple(
            "酷狗",
            MaterialTheme.colorScheme.tertiary.copy(alpha = 0.1f),
            MaterialTheme.colorScheme.tertiary,
        )
        "bilibili" -> Triple(
            "B站",
            MaterialTheme.colorScheme.primary.copy(alpha = 0.1f),
            MaterialTheme.colorScheme.primary,
        )
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

// ═══════════════════════════════════════════════════════════════════════════
// Now playing bar
// ═══════════════════════════════════════════════════════════════════════════

@Composable
private fun NowPlayingBar(
    song: SongMetadata,
    isPlaying: Boolean,
) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 4.dp),
        shape = RoundedCornerShape(10.dp),
        color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.3f),
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                Icons.Default.MusicNote,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(18.dp),
            )
            Spacer(Modifier.width(8.dp))
            Text(
                if (isPlaying) "正在播放: " else "已暂停: ",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(
                "${song.songName} — ${song.artist}",
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.primary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}
