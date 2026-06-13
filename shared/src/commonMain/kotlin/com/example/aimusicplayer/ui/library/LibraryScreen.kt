package com.example.aimusicplayer.ui.library

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInWindow
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntRect
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.DpOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Popup
import androidx.compose.ui.window.PopupProperties
import coil3.compose.AsyncImage
import kotlin.math.roundToInt
import com.example.aimusicplayer.music.DownloadedSong
import com.example.aimusicplayer.ui.common.pressFlash

// ═══════════════════════════════════════════════════════════════════════════
// LibraryScreen — local music library (NetEase-style)
// ═══════════════════════════════════════════════════════════════════════════

/**
 * Displays downloaded songs with play and delete actions.
 *
 * Design: NetEase Cloud Music local song list style — song cards with
 * cover placeholders, platform badges, and action icons.
 */
@Composable
fun LibraryScreen(
    viewModel: LibraryViewModel,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    onPickImportFile: ((String) -> Unit) -> Unit = {},
) {
    val songs by viewModel.downloadedSongs.collectAsState()
    val isLoading by viewModel.isLoading.collectAsState()
    val snackbarMessage by viewModel.snackbarMessage.collectAsState()
    val renameTarget by viewModel.renameTarget.collectAsState()
    val isSimpleMode by viewModel.isSimpleMode.collectAsState()
    val isExporting by viewModel.isExporting.collectAsState()
    val isImporting by viewModel.isImporting.collectAsState()
    val isBusy = isExporting || isImporting

    val snackbarHostState = remember { SnackbarHostState() }

    // Refresh every time the library tab is shown
    LaunchedEffect(Unit) {
        viewModel.refresh()
    }

    LaunchedEffect(snackbarMessage) {
        snackbarMessage?.let {
            snackbarHostState.showSnackbar(it)
            viewModel.clearSnackbar()
        }
    }

    Column(modifier = modifier.fillMaxSize()) {
        // Compact header
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            TextButton(onClick = onBack) {
                Text("← 返回")
            }
            Spacer(Modifier.weight(1f))
            Text(
                "本地歌单",
                fontWeight = FontWeight.Bold,
            )
            Spacer(Modifier.weight(1f))
            Spacer(Modifier.width(60.dp))
        }

        // Content area
        Box(modifier = Modifier.weight(1f).fillMaxWidth()) {
            when {
                // Loading
                isLoading -> {
                    Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center,
                    ) {
                        CircularProgressIndicator(color = MaterialTheme.colorScheme.primary)
                    }
                }

                // Empty state
                songs.isEmpty() -> {
                    Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center,
                    ) {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(12.dp),
                        ) {
                            // Styled icon with background
                            Box(
                                modifier = Modifier
                                    .size(80.dp)
                                    .clip(CircleShape)
                                    .background(
                                        MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f),
                                    ),
                                contentAlignment = Alignment.Center,
                            ) {
                                Icon(
                                    Icons.Default.MusicNote,
                                    contentDescription = null,
                                    modifier = Modifier.size(40.dp),
                                    tint = MaterialTheme.colorScheme.primary,
                                )
                            }
                            Text(
                                "还没有下载歌曲",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.SemiBold,
                                color = MaterialTheme.colorScheme.onSurface,
                            )
                            Text(
                                "使用 AI 对话播放歌曲后，点击下载按钮保存到本地",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                textAlign = TextAlign.Center,
                            )
                            Spacer(Modifier.height(8.dp))
                            TextButton(
                                onClick = {
                                    onPickImportFile { path ->
                                        viewModel.importFromPath(path)
                                    }
                                },
                                enabled = !isImporting,
                            ) {
                                if (isImporting) {
                                    CircularProgressIndicator(
                                        modifier = Modifier.size(16.dp),
                                        strokeWidth = 2.dp,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    )
                                } else {
                                    Icon(
                                        Icons.Default.Add,
                                        contentDescription = null,
                                        modifier = Modifier.size(16.dp),
                                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                    )
                                }
                                Spacer(Modifier.width(4.dp))
                                Text(
                                    if (isImporting) "导入中..." else "导入",
                                    style = MaterialTheme.typography.labelLarge,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                        }
                    }
                }

                // Song list
                else -> {
                    LazyColumn(
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        item {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 4.dp, vertical = 8.dp),
                                horizontalArrangement = Arrangement.Start,
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Text(
                                    "共 ${songs.size} 首",
                                    style = MaterialTheme.typography.labelLarge,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    fontWeight = FontWeight.Medium,
                                )
                                Spacer(Modifier.width(12.dp))
                                Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                                    TextButton(
                                        onClick = viewModel::toggleDisplayMode,
                                        enabled = !isBusy,
                                    ) {
                                        Text(
                                            if (isSimpleMode) "简洁" else "原始",
                                            style = MaterialTheme.typography.labelLarge,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        )
                                    }
                                    if (songs.isNotEmpty()) {
                                        TextButton(
                                            onClick = { viewModel.playAll() },
                                            enabled = !isBusy,
                                        ) {
                                            Icon(
                                                Icons.Default.PlayArrow,
                                                contentDescription = null,
                                                modifier = Modifier.size(16.dp),
                                                tint = MaterialTheme.colorScheme.primary,
                                            )
                                            Spacer(Modifier.width(4.dp))
                                            Text("播放", color = MaterialTheme.colorScheme.primary)
                                        }
                                    }
                                    Spacer(Modifier.weight(1f))
                                    if (songs.isNotEmpty()) {
                                        IconButton(
                                            onClick = { viewModel.exportAll() },
                                            enabled = !isBusy,
                                        ) {
                                            if (isExporting) {
                                                CircularProgressIndicator(
                                                    modifier = Modifier.size(16.dp),
                                                    strokeWidth = 2.dp,
                                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                                )
                                            } else {
                                                Icon(
                                                    Icons.Default.Share,
                                                    contentDescription = null,
                                                    modifier = Modifier.size(16.dp),
                                                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                                )
                                            }
                                        }
                                    }
                                    IconButton(
                                        onClick = {
                                            onPickImportFile { path ->
                                                viewModel.importFromPath(path)
                                            }
                                        },
                                        enabled = !isBusy,
                                    ) {
                                        if (isImporting) {
                                            CircularProgressIndicator(
                                                modifier = Modifier.size(16.dp),
                                                strokeWidth = 2.dp,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                            )
                                        } else {
                                            Icon(
                                                Icons.Default.Add,
                                                contentDescription = null,
                                                modifier = Modifier.size(16.dp),
                                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                            )
                                        }
                                    }
                                }
                            }
                        }
                        items(songs, key = { it.songId }) { song ->
                            SongCard(
                                song = song,
                                isSimpleMode = isSimpleMode,
                                onPlay = { viewModel.playSong(song) },
                                onDelete = { viewModel.deleteSong(song) },
                                onRename = { viewModel.showRenameDialog(song) },
                            )
                        }
                        item { Spacer(Modifier.height(16.dp)) }
                    }
                }
            }
        }
    }

    // Rename dialog (B站 songs only)
    renameTarget?.let { song ->
        var newTitle by remember(song.songId) { mutableStateOf(song.realName ?: song.songName) }
        var newArtist by remember(song.songId) { mutableStateOf(song.realArtist ?: song.artist) }
        AlertDialog(
            onDismissRequest = viewModel::dismissRenameDialog,
            title = { Text("编辑显示名称") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    OutlinedTextField(
                        value = newTitle,
                        onValueChange = { newTitle = it },
                        label = { Text("歌名") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                    )
                    OutlinedTextField(
                        value = newArtist,
                        onValueChange = { newArtist = it },
                        label = { Text("歌手") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    viewModel.renameSong(song, newTitle, newArtist)
                    viewModel.dismissRenameDialog()
                }) {
                    Text("确定")
                }
            },
            dismissButton = {
                TextButton(onClick = viewModel::dismissRenameDialog) {
                    Text("取消")
                }
            },
        )
    }
}

// ═══════════════════════════════════════════════════════════════════════════
// Song card — NetEase-style with left accent
// ═══════════════════════════════════════════════════════════════════════════

@Composable
private fun SongCard(
    song: DownloadedSong,
    isSimpleMode: Boolean = false,
    onPlay: () -> Unit,
    onDelete: () -> Unit,
    onRename: () -> Unit = {},
) {
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
                containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
            ),
            shape = RoundedCornerShape(12.dp),
        ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            // Cover art (local or remote)
            val cover = song.coverUrl
            if (!cover.isNullOrBlank()) {
                val model = if (cover.startsWith("/")) "file://$cover" else cover
                AsyncImage(
                    model = model,
                    contentDescription = "专辑封面",
                    modifier = Modifier
                        .size(36.dp)
                        .clip(RoundedCornerShape(8.dp)),
                )
            }

            Spacer(Modifier.width(12.dp))

            // Song info
            Column(modifier = Modifier.weight(1f)) {
                if (isSimpleMode) {
                    Text(
                        text = song.realName ?: song.songName,
                        style = MaterialTheme.typography.bodyLarge,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Spacer(Modifier.height(2.dp))
                    Text(
                        text = (song.realArtist ?: song.artist).ifBlank { "未知艺术家" },
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                } else {
                    Text(
                        text = song.songName,
                        style = MaterialTheme.typography.bodyLarge,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Spacer(Modifier.height(2.dp))
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                    ) {
                        Text(
                            text = song.artist.ifBlank { "未知艺术家" },
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                        // Quality badge
                        if (song.quality != "standard") {
                            Surface(
                                shape = RoundedCornerShape(4.dp),
                                color = MaterialTheme.colorScheme.secondary.copy(alpha = 0.1f),
                            ) {
                                Text(
                                    song.quality,
                                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 1.dp),
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.secondary,
                                    fontWeight = FontWeight.Medium,
                                )
                            }
                        }
                    }
                }
            }

            // Platform badge
            Surface(
                shape = RoundedCornerShape(4.dp),
                color = MaterialTheme.colorScheme.primary.copy(alpha = 0.1f),
                modifier = Modifier.padding(end = 8.dp),
            ) {
                Text(
                    song.platform,
                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 1.dp),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.primary,
                    fontWeight = FontWeight.Medium,
                )
            }

            // Edit button (B站 only)
            if (song.platform == "bilibili") {
                IconButton(
                    onClick = onRename,
                    modifier = Modifier.size(32.dp),
                ) {
                    Icon(
                        Icons.Default.Edit,
                        contentDescription = "编辑名称",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
                        modifier = Modifier.size(16.dp),
                    )
                }
            }

            // Delete button
            Box(
                modifier = Modifier
                    .size(32.dp)
                    .clickable(onClick = onDelete),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    Icons.Default.Delete,
                    contentDescription = "删除",
                    tint = MaterialTheme.colorScheme.error,
                    modifier = Modifier.size(16.dp),
                )
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
