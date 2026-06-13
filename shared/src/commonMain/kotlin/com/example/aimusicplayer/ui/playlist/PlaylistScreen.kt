package com.example.aimusicplayer.ui.playlist

import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.layout.*

import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowForward
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.DragHandle
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInParent
import androidx.compose.ui.layout.positionInWindow
import androidx.compose.ui.platform.LocalDensity

import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset

import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.util.lerp
import androidx.compose.ui.window.Popup
import androidx.compose.ui.window.PopupProperties
import com.example.aimusicplayer.ui.common.pressFlash
import com.example.aimusicplayer.music.HistorySong
import com.example.aimusicplayer.music.PlayMode
import com.example.aimusicplayer.music.PlaylistSong
import com.example.aimusicplayer.network.music.SongMetadata
import com.example.aimusicplayer.ui.player.MarqueeText
import org.burnoutcrew.reorderable.ReorderableItem
import org.burnoutcrew.reorderable.ReorderableLazyListState
import org.burnoutcrew.reorderable.detectReorderAfterLongPress
import org.burnoutcrew.reorderable.rememberReorderableLazyListState
import org.burnoutcrew.reorderable.reorderable
import kotlinx.coroutines.delay
import kotlinx.datetime.Clock
import kotlin.math.abs
import kotlin.math.roundToInt
import com.example.aimusicplayer.ui.main.SleepTimerState
import com.example.aimusicplayer.ui.main.formatTimerRemaining

@Composable
fun PlaylistScreen(
    viewModel: PlaylistViewModel,
    onNavigateToSearch: () -> Unit,
    currentSongId: String? = null,
    isPlaying: Boolean = false,
    sleepTimerState: SleepTimerState = SleepTimerState(),
    modifier: Modifier = Modifier,
) {
    val selectedTab by viewModel.selectedTab.collectAsState()
    val playlist by viewModel.playlist.collectAsState()
    val history by viewModel.history.collectAsState()
    val playMode by viewModel.playMode.collectAsState()
    val likedSongs by viewModel.favorites.collectAsState()

    val pagerState = rememberPagerState(initialPage = selectedTab) { 4 }

    // Sync 1: Pager → ViewModel (only when settled, not mid-animation)
    LaunchedEffect(pagerState) {
        snapshotFlow { pagerState.currentPage to pagerState.isScrollInProgress }
            .collect { (page, scrolling) ->
                if (!scrolling) viewModel.selectTab(page)
            }
    }

    // Sync 2: ViewModel → Pager (tab chip clicks)
    LaunchedEffect(selectedTab) {
        if (selectedTab != pagerState.currentPage) {
            pagerState.animateScrollToPage(selectedTab)
        }
    }

    // Refresh data on page change
    LaunchedEffect(pagerState.currentPage) { viewModel.refresh() }

    // Refresh immediately when playback state changes (song starts/ends)
    LaunchedEffect(currentSongId, isPlaying) { viewModel.refresh() }


    // ── Tab indicator state ──
    val tabCenters = remember { mutableStateListOf(0f, 0f, 0f, 0f) }
    val density = LocalDensity.current
    val indicatorWidthDp = 40.dp
    val indicatorHalfWidthPx = with(density) { indicatorWidthDp.toPx() / 2f }
    val indicatorCenterX by remember {
        derivedStateOf {
            if (tabCenters.any { it == 0f }) return@derivedStateOf 0f
            val fraction = pagerState.currentPageOffsetFraction
            val nextPage = if (fraction >= 0f)
                (pagerState.currentPage + 1).coerceIn(0, 3)
            else
                (pagerState.currentPage - 1).coerceIn(0, 3)
            lerp(tabCenters[pagerState.currentPage], tabCenters[nextPage], abs(fraction))
        }
    }

    Column(modifier = modifier.fillMaxSize()) {
        // ── Centered symmetric subtabs with sliding indicator ──
        Box(modifier = Modifier.fillMaxWidth()) {
            Row(
                modifier = Modifier.fillMaxWidth().padding(vertical = 14.dp),
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                TabChip(
                    label = "当前歌单",
                    selected = selectedTab == 0,
                    onClick = { viewModel.selectTab(0) },
                    modifier = Modifier.onGloballyPositioned { coords ->
                        tabCenters[0] = coords.positionInParent().x + coords.size.width / 2f
                    },
                )
                Spacer(Modifier.width(16.dp))
                TabChip(
                    label = "历史歌单",
                    selected = selectedTab == 1,
                    onClick = { viewModel.selectTab(1) },
                    modifier = Modifier.onGloballyPositioned { coords ->
                        tabCenters[1] = coords.positionInParent().x + coords.size.width / 2f
                    },
                )
                Spacer(Modifier.width(16.dp))
                TabChip(
                    label = "我的喜欢",
                    selected = selectedTab == 2,
                    onClick = { viewModel.selectTab(2) },
                    modifier = Modifier.onGloballyPositioned { coords ->
                        tabCenters[2] = coords.positionInParent().x + coords.size.width / 2f
                    },
                )
                Spacer(Modifier.width(16.dp))
                TabChip(
                    label = "导入歌单",
                    selected = selectedTab == 3,
                    onClick = { viewModel.selectTab(3) },
                    modifier = Modifier.onGloballyPositioned { coords ->
                        tabCenters[3] = coords.positionInParent().x + coords.size.width / 2f
                    },
                )
            }

            // ── Sliding indicator ──
            if (tabCenters.all { it > 0f }) {
                Box(
                    modifier = Modifier
                        .align(Alignment.BottomStart)
                        .offset { IntOffset(x = (indicatorCenterX - indicatorHalfWidthPx).roundToInt(), y = 0) }
                        .width(indicatorWidthDp)
                        .height(4.dp)
                        .clip(RoundedCornerShape(2.dp))
                        .background(MaterialTheme.colorScheme.primary),
                )
            }
        }

        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f))

        // ── Swipeable page content ──
        Box(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth(),
        ) {
            HorizontalPager(
                state = pagerState,
                beyondViewportPageCount = 1,
                modifier = Modifier.fillMaxSize(),
            ) { page ->
                when (page) {
                    0 -> CurrentPlaylistTab(
                        playlist = playlist,
                        currentSongId = currentSongId,
                        isPlaying = isPlaying,
                        sleepTimerState = sleepTimerState,
                        playMode = playMode,
                        onToggleMode = viewModel::togglePlayMode,
                        onPlay = viewModel::playSongFromPlaylist,
                        onRemove = viewModel::removeFromPlaylist,
                        onMove = viewModel::moveSong,
                        onAddSongs = onNavigateToSearch,
                        onClear = viewModel::clearPlaylist,
                        onDownload = viewModel::downloadPlaylist,
                    )
                    1 -> HistoryTab(
                        history = history,
                        onPlay = viewModel::playSongFromHistory,
                        onAddToPlaylist = { song ->
                            viewModel.addToPlaylist(
                                SongMetadata(
                                    songId = song.songId,
                                    songName = song.songName,
                                    artist = song.artist,
                                    platform = song.platform,
                                    quality = song.quality,
                                    coverUrl = song.coverUrl,
                                ),
                                song.playUrl,
                            )
                        },
                        onClear = viewModel::clearHistory,
                    )
                    2 -> FavoriteSongsTab(
                        likedSongs = likedSongs,
                        currentSongId = currentSongId,
                        isPlaying = isPlaying,
                        onPlay = viewModel::playFavoriteSong,
                        onUnlike = viewModel::removeFavoriteSong,
                        onMove = viewModel::moveFavoriteSong,
                        onClear = viewModel::clearFavorites,
                    )
                    3 -> ImportTab(
                        viewModel = viewModel,
                        modifier = Modifier.fillMaxSize(),
                    )
                }
            }
        }
    }
}

// ── Tab chip ───────────────────────────────────────────────────────────

@Composable
private fun TabChip(
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Text(
        text = label,
        style = MaterialTheme.typography.titleSmall,
        fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal,
        color = if (selected) MaterialTheme.colorScheme.primary
                else MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = modifier
            .clip(RoundedCornerShape(6.dp))
            .clickable(onClick = onClick)
            .then(
                if (selected) Modifier.background(
                    MaterialTheme.colorScheme.primary.copy(alpha = 0.1f),
                    RoundedCornerShape(6.dp),
                ) else Modifier
            )
            .padding(horizontal = 10.dp, vertical = 6.dp),
    )
}

// ── Current Playlist Tab ─────────────────────────────────────────────

@Composable
private fun CurrentPlaylistTab(
    playlist: List<PlaylistSong>,
    currentSongId: String? = null,
    isPlaying: Boolean = false,
    playMode: PlayMode,
    onToggleMode: () -> Unit,
    onPlay: (PlaylistSong) -> Unit,
    onRemove: (String, String) -> Unit,
    onMove: (Int, Int) -> Unit,
    onAddSongs: () -> Unit,
    onClear: () -> Unit,
    onDownload: () -> Unit,
    sleepTimerState: SleepTimerState = SleepTimerState(),
) {
    if (playlist.isEmpty()) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text("📭", fontSize = 48.sp)
                Spacer(Modifier.height(12.dp))
                Text("当前歌单为空", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                Spacer(Modifier.height(4.dp))
                Text(
                    "搜索歌曲后点击 + 歌单，或直接播放歌曲自动加入",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    } else {
        // ── Local mutable display list — mutated during drag, synced from playlist ──
        val displayList = remember { mutableStateListOf<PlaylistSong>() }
        var isDragging by remember { mutableStateOf(false) }
        // Sync from playlist when NOT dragging (external changes like add/remove)
        LaunchedEffect(playlist) {
            if (!isDragging) {
                displayList.clear()
                displayList.addAll(playlist)
            }
        }
        // Initial population
        if (displayList.isEmpty()) { displayList.addAll(playlist) }

        // ── Reorderable state ──────────────────────────────────
        val reorderableState = rememberReorderableLazyListState(
            onMove = { from, to ->
                // Mutate local copy — library handles visual animation
                isDragging = true
                val fromIdx = from.index.coerceIn(0, displayList.lastIndex)
                val toIdx = to.index.coerceIn(0, displayList.size)
                val item = displayList.removeAt(fromIdx)
                displayList.add(toIdx, item)
            },
            onDragEnd = { startIndex, endIndex ->
                isDragging = false
                // Persist final order to DB
                if (startIndex != endIndex) {
                    onMove(startIndex, endIndex)
                }
            },
        )

        Column(modifier = Modifier.fillMaxSize()) {
            // Header with count + play mode
            Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                val timerInfoText = when {
                    !sleepTimerState.isActive -> null
                    sleepTimerState.remainingMs <= 0L && sleepTimerState.finishCurrentSong -> "  ·  🎵 播完关闭"
                    else -> "  ·  ⏰ ${formatTimerRemaining(sleepTimerState.remainingMs)}"
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        "共 ${playlist.size} 首",
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = FontWeight.Medium,
                    )
                    if (timerInfoText != null) {
                        Text(
                            text = timerInfoText,
                            style = MaterialTheme.typography.labelLarge,
                            fontWeight = FontWeight.Medium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    TextButton(onClick = onClear) {
                        Text(
                            "清空",
                            style = MaterialTheme.typography.labelLarge,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    TextButton(onClick = onDownload) {
                        Text(
                            "⬇下载",
                            style = MaterialTheme.typography.labelLarge,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    TextButton(onClick = onToggleMode) {
                        Icon(
                            imageVector = when (playMode) {
                                PlayMode.SEQUENTIAL -> Icons.Default.ArrowForward
                                PlayMode.LOOP -> Icons.Default.Refresh
                                PlayMode.REPEAT_ONE -> Icons.Default.Refresh
                            },
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(16.dp),
                        )
                        Spacer(Modifier.width(2.dp))
                        Text(
                            when (playMode) {
                                PlayMode.SEQUENTIAL -> "顺序"
                                PlayMode.LOOP -> "循环"
                                PlayMode.REPEAT_ONE -> "单曲"
                            },
                            style = MaterialTheme.typography.labelLarge,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }

            LazyColumn(
                state = reorderableState.listState,
                modifier = Modifier.weight(1f).fillMaxWidth().reorderable(reorderableState),
                contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 4.dp, bottom = 140.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                itemsIndexed(displayList, key = { _, s -> s.songId }) { index, song ->
                    ReorderableItem(reorderableState, key = song.songId) { dragging ->
                        PlaylistSongCard(
                            index = index,
                            song = song,
                            isCurrent = song.songId == currentSongId,
                            isPlaying = isPlaying,
                            isDragging = dragging,
                            reorderableState = reorderableState,
                            onPlay = { onPlay(song) },
                            onRemove = { onRemove(song.songId, song.platform) },
                        )
                    }
                }
            }

        }
    }
}

// ── Playlist song card ──────────────────────────────────────

@Composable
private fun PlaylistSongCard(
    index: Int,
    song: PlaylistSong,
    isCurrent: Boolean = false,
    isPlaying: Boolean = false,
    isDragging: Boolean = false,
    reorderableState: ReorderableLazyListState? = null,
    onPlay: () -> Unit,
    onRemove: () -> Unit,
) {
    val scale by animateFloatAsState(
        targetValue = if (isDragging) 1.03f else 1f,
        animationSpec = spring(stiffness = Spring.StiffnessLow),
    )
    val bgColor = when {
        isDragging -> MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.7f)
        isCurrent -> MaterialTheme.colorScheme.primary.copy(alpha = 0.12f)
        else -> MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f)
    }

    val pressAlpha = remember { Animatable(0f) }

    val currentIsDragging by rememberUpdatedState(isDragging)
    var showTooltip by remember { mutableStateOf(false) }
    var isPressingContent by remember { mutableStateOf(false) }
    var longPressTriggered by remember { mutableStateOf(false) }
    var parentSize by remember { mutableStateOf(IntSize.Zero) }
    var menuSize by remember { mutableStateOf(IntSize.Zero) }
    var parentPosX by remember { mutableStateOf(0f) }
    val density = LocalDensity.current
    val windowWidth = (parentSize.width + parentPosX * 2f).roundToInt().coerceAtLeast(parentSize.width)

    Box(modifier = Modifier.onGloballyPositioned { parentSize = it.size; parentPosX = it.positionInWindow().x }) {
        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .graphicsLayer {
                    scaleX = scale
                    scaleY = scale
                }
                .clickable(
                    onClick = {
                        if (!longPressTriggered) onPlay()
                        longPressTriggered = false
                        showTooltip = false
                    },
                )
                .drawWithContent {
                    drawContent()
                    if (pressAlpha.value > 0.001f) {
                        drawRect(Color.Black.copy(alpha = pressAlpha.value))
                    }
                },
            shape = RoundedCornerShape(10.dp),
            color = bgColor,
        ) {
            Row(
                modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                // Press animation — driven by content area press state
                LaunchedEffect(isPressingContent) {
                    if (isPressingContent) {
                        pressAlpha.snapTo(0.12f)
                    } else {
                        pressAlpha.animateTo(0f, tween(150))
                    }
                }

                // Long-press timer — fires after 400ms hold on content area
                LaunchedEffect(isPressingContent) {
                    if (isPressingContent) {
                        delay(400)
                        if (isPressingContent && !currentIsDragging) {
                            showTooltip = true
                            longPressTriggered = true
                        }
                    }
                }

                Box(
                    modifier = Modifier
                        .weight(1f)
                        .pointerInput(Unit) {
                            awaitPointerEventScope {
                                while (true) {
                                    val event = awaitPointerEvent(PointerEventPass.Initial)
                                    val pressed = event.changes.any { it.pressed }
                                    if (pressed) longPressTriggered = false
                                    isPressingContent = pressed
                                    if (!pressed) showTooltip = false
                                }
                            }
                        },
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            "${index + 1}",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.width(24.dp),
                        )
                        Column(Modifier.weight(1f)) {
                            if (isCurrent) {
                                MarqueeText(
                                    text = song.songName,
                                    style = MaterialTheme.typography.bodyMedium,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.primary,
                                    gapWidth = 56.sp,
                                    durationMs = 18000,
                                    isAnimating = isPlaying,
                                )
                            } else {
                                Text(song.songName, style = MaterialTheme.typography.bodyMedium,
                                    fontWeight = FontWeight.Medium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            }
                            Text(song.artist, style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        }
                    }
                }
                // Drag handle — long-press to reorder
                if (reorderableState != null) {
                    Icon(
                        Icons.Default.DragHandle,
                        contentDescription = "拖拽排序",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = if (isDragging) 0.7f else 0.4f),
                        modifier = Modifier
                            .size(24.dp)
                            .detectReorderAfterLongPress(reorderableState),
                    )
                }
                Spacer(Modifier.width(4.dp))
                IconButton(onClick = onRemove, modifier = Modifier.size(36.dp)) {
                    Icon(Icons.Default.Close, "移除",
                        tint = MaterialTheme.colorScheme.error.copy(alpha = 0.6f), modifier = Modifier.size(18.dp))
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

// ── History Tab ─────────────────────────────────────────────────────

@Composable
private fun HistoryTab(
    history: List<HistorySong>,
    onPlay: (HistorySong) -> Unit,
    onAddToPlaylist: (HistorySong) -> Unit,
    onClear: () -> Unit,
) {
    if (history.isEmpty()) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text("🕐", fontSize = 48.sp)
                Spacer(Modifier.height(12.dp))
                Text("暂无播放记录", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                Text("播放超过 30 秒的歌曲将自动记录",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    } else {
        Column(modifier = Modifier.fillMaxSize()) {
            Text(
                "最近 ${history.size} 首",
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.Medium,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
            )

            LazyColumn(
                modifier = Modifier.weight(1f).fillMaxWidth(),
                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 4.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                itemsIndexed(history, key = { _, s -> "${s.songId}_${s.playedAt}" }) { _, song ->
                    HistorySongRow(
                        song = song,
                        onPlay = { onPlay(song) },
                        onAddToPlaylist = { onAddToPlaylist(song) },
                    )
                }
                item { Spacer(Modifier.height(16.dp)) }
            }

        }
    }
}

// ── History song row ─────────────────────────────────────────────────

@Composable
private fun HistorySongRow(
    song: HistorySong,
    onPlay: () -> Unit,
    onAddToPlaylist: () -> Unit,
) {
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
                .pressFlash(
                    onTap = { onPlay() },
                    onLongPress = { showTooltip = true },
                    onRelease = { showTooltip = false },
                ),
            shape = RoundedCornerShape(10.dp),
            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f),
        ) {
            Row(
                modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(Modifier.weight(1f)) {
                    Text(song.songName, style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.Medium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text(song.artist, style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
                Text(formatRelativeTime(song.playedAt),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f))
                Spacer(Modifier.width(8.dp))
                IconButton(onClick = onAddToPlaylist, modifier = Modifier.size(34.dp)) {
                    Icon(Icons.Default.Add, "添加到歌单",
                        tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(18.dp))
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

// ── Favorite Songs Tab ─────────────────────────────────────────────

@Composable
private fun FavoriteSongsTab(
    likedSongs: List<PlaylistSong>,
    currentSongId: String? = null,
    isPlaying: Boolean = false,
    onPlay: (PlaylistSong) -> Unit,
    onUnlike: (String, String) -> Unit,
    onMove: (Int, Int) -> Unit,
    onClear: () -> Unit,
) {
    if (likedSongs.isEmpty()) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text("❤️", fontSize = 48.sp)
                Spacer(Modifier.height(12.dp))
                Text(
                    "暂无喜欢的歌曲",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                )
                Spacer(Modifier.height(4.dp))
                Text(
                    "在播放页面点击 ❤ 即可收藏",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    } else {
        // ── Local mutable display list for drag state ──
        val displayList = remember { mutableStateListOf<PlaylistSong>() }
        var isDragging by remember { mutableStateOf(false) }
        LaunchedEffect(likedSongs) {
            if (!isDragging) {
                displayList.clear()
                displayList.addAll(likedSongs)
            }
        }
        if (displayList.isEmpty()) { displayList.addAll(likedSongs) }

        // ── Reorderable state ──
        val reorderableState = rememberReorderableLazyListState(
            onMove = { from, to ->
                isDragging = true
                val fromIdx = from.index.coerceIn(0, displayList.lastIndex)
                val toIdx = to.index.coerceIn(0, displayList.size)
                val item = displayList.removeAt(fromIdx)
                displayList.add(toIdx, item)
            },
            onDragEnd = { startIndex, endIndex ->
                isDragging = false
                if (startIndex != endIndex) {
                    onMove(startIndex, endIndex)
                }
            },
        )

        Column(modifier = Modifier.fillMaxSize()) {
            // Header
            Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    "共 ${likedSongs.size} 首",
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.Medium,
                )
                TextButton(onClick = onClear) {
                    Text(
                        "清空",
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }

            // Liked songs list (reorderable)
            LazyColumn(
                state = reorderableState.listState,
                modifier = Modifier.weight(1f).fillMaxWidth().reorderable(reorderableState),
                contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 4.dp, bottom = 140.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                itemsIndexed(displayList, key = { _, s -> "${s.songId}|${s.platform}" }) { index, song ->
                    ReorderableItem(reorderableState, key = "${song.songId}|${song.platform}") { dragging ->
                        PlaylistSongCard(
                            index = index,
                            song = song,
                            isCurrent = song.songId == currentSongId,
                            isPlaying = isPlaying,
                            isDragging = dragging,
                            reorderableState = reorderableState,
                            onPlay = { onPlay(song) },
                            onRemove = { onUnlike(song.songId, song.platform) },
                        )
                    }
                }
            }
        }
    }
}

// ── Relative time formatting (KMP-safe) ────────────────────────────────

private fun formatRelativeTime(playedAtMs: Long): String {
    val now = Clock.System.now().toEpochMilliseconds()
    val diff = now - playedAtMs
    val minutes = diff / 60_000
    val hours = minutes / 60
    val days = hours / 24
    return when {
        minutes < 1 -> "刚刚"
        minutes < 60 -> "${minutes}分钟前"
        hours < 24 -> "${hours}小时前"
        days < 30 -> "${days}天前"
        else -> "${days / 30}个月前"
    }
}
