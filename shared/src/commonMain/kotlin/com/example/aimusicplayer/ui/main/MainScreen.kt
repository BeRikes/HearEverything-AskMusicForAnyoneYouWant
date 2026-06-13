package com.example.aimusicplayer.ui.main

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
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.rememberCoroutineScope
import kotlinx.coroutines.launch
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.aimusicplayer.ai.AiCommand
import com.example.aimusicplayer.ui.chat.ChatItem
import com.example.aimusicplayer.ui.search.SearchPanel
import com.example.aimusicplayer.ui.search.SearchViewModel
import com.example.aimusicplayer.ui.theme.BrandIcon

// ═══════════════════════════════════════════════════════════════════════════
// MainScreen — chat-first music player with integrated search
// ═══════════════════════════════════════════════════════════════════════════

/**
 * Main screen combining chat-based music discovery with an integrated
 * search panel and compact brand header.
 *
 * Layout (top → bottom):
 * 1. BrandBar — gradient icon + "HearEverything" title + settings button
 * 2. Search area — pill shortcut (collapsed) | SearchPanel (expanded)
 * 3. Chat messages (LazyColumn, fills remaining space)
 * 4. ChatInputBar — text field + send button
 * 5. Snackbar host for global errors (overlays at the bottom)
 *
 * @param viewModel         Main ViewModel for chat and playback state
 * @param searchViewModel   Search ViewModel for the search panel
 * @param onNavigateToSettings Navigation callback to settings screen
 * @param modifier          Optional modifier for the root layout
 */
@Composable
fun MainScreen(
    viewModel: MainViewModel,
    searchViewModel: SearchViewModel,
    onNavigateToSettings: () -> Unit,
    likedSongIds: Set<String> = emptySet(),
    modifier: Modifier = Modifier,
) {
    val chatItems by viewModel.chatItems.collectAsState()
    val inputText by viewModel.inputText.collectAsState()
    val isThinking by viewModel.isThinking.collectAsState()
    val errorMessage by viewModel.errorMessage.collectAsState()

    val listState = rememberLazyListState()
    val snackbarHostState = remember { SnackbarHostState() }
    val isSearchExpanded by searchViewModel.isSearchExpanded.collectAsState()
    val coroutineScope = rememberCoroutineScope()

    // Auto-scroll when new messages arrive
    LaunchedEffect(chatItems.size) {
        if (chatItems.isNotEmpty()) {
            listState.animateScrollToItem(chatItems.size - 1)
        }
    }

    // Snackbar error handling
    LaunchedEffect(errorMessage) {
        errorMessage?.let {
            snackbarHostState.showSnackbar(it)
            viewModel.clearError()
        }
    }

    Box(modifier = modifier.fillMaxSize()) {
        Column(modifier = Modifier.fillMaxSize()) {

            // ════════════════════════════════════════════════════════════
            // 1. BrandBar — compact header
            // ════════════════════════════════════════════════════════════
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                // Gradient app icon
                Box(
                    modifier = Modifier
                        .size(28.dp)
                        .clip(RoundedCornerShape(8.dp))
                        .background(Color.White),
                    contentAlignment = Alignment.Center,
                ) {
                    BrandIcon(modifier = Modifier.size(22.dp))
                }

                Spacer(Modifier.width(8.dp))

                Text(
                    "HearEverything",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                )

                Spacer(Modifier.weight(1f))

                // Settings button
                IconButton(
                    onClick = onNavigateToSettings,
                    modifier = Modifier.size(40.dp),
                ) {
                    Icon(
                        Icons.Default.Settings,
                        contentDescription = "设置",
                        tint = MaterialTheme.colorScheme.onSurface,
                    )
                }
            }

            // ════════════════════════════════════════════════════════════
            // 2. Search area — shortcut pill or expanded panel
            // ════════════════════════════════════════════════════════════
            if (isSearchExpanded) {
                SearchPanel(
                    expanded = true,
                    onDismiss = { searchViewModel.collapseSearch() },
                    viewModel = searchViewModel,
                )
            } else {
                Surface(
                    shape = RoundedCornerShape(24.dp),
                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 4.dp)
                        .clickable { searchViewModel.expandSearch() },
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        Icon(
                            Icons.Default.Search,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(20.dp),
                        )
                        Text(
                            "搜索歌曲",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }

            // ════════════════════════════════════════════════════════════
            // 3. Chat messages
            // ════════════════════════════════════════════════════════════
            LazyColumn(
                state = listState,
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth(),
                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                // Empty state
                if (chatItems.isEmpty()) {
                    item {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(32.dp),
                            contentAlignment = Alignment.Center,
                        ) {
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Text(
                                    "👋",
                                    fontSize = 32.sp,
                                )
                                Spacer(Modifier.height(8.dp))
                                Text(
                                    text = "告诉我你想听什么歌？",
                                    style = MaterialTheme.typography.bodyLarge,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    textAlign = TextAlign.Center,
                                )
                                Spacer(Modifier.height(4.dp))
                                Text(
                                    text = "例如：播放周杰伦的晴天",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
                                    textAlign = TextAlign.Center,
                                )
                            }
                        }
                    }
                }

                // Chat items
                items(chatItems) { item ->
                    ChatBubbleItem(
                        item,
                        onSearchSong = { songName, artist, songId, platform ->
                            searchViewModel.expandSearch()
                            searchViewModel.onSongNameChanged(songName)
                            searchViewModel.onArtistChanged(artist)
                            searchViewModel.search()
                            viewModel.removeFromSongIndex(songId, platform)
                        },
                        onLikeSong = { songId, platform, songName, artist ->
                            viewModel.toggleLikeSong(songId, platform, songName, artist)
                        },
                    )
                }

                // Thinking indicator
                if (isThinking) {
                    item {
                        Row(
                            modifier = Modifier.padding(8.dp),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(16.dp),
                                strokeWidth = 2.dp,
                                color = MaterialTheme.colorScheme.primary,
                            )
                            Text(
                                "AI 正在思考...",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                }
            }

            // ════════════════════════════════════════════════════════════
            // 4. Quick action chips
            // ════════════════════════════════════════════════════════════
            QuickActionChips(
                onRecommend = {
                    viewModel.onInputChanged("推荐")
                    viewModel.sendMessage()
                },
                onPlayRecommend = {
                    viewModel.onInputChanged("播放推荐歌曲")
                    viewModel.sendMessage()
                },
                onMyPreferences = {
                    viewModel.onInputChanged("我喜欢什么歌")
                    viewModel.sendMessage()
                },
                onImport = { viewModel.onInputChanged("导入歌单 ") },
                onLyrics = {
                    if (viewModel.currentSong.value != null) viewModel.toggleLyrics()
                    else {
                        coroutineScope.launch { snackbarHostState.showSnackbar("请先播放一首歌曲") }
                    }
                },
            )

            // ════════════════════════════════════════════════════════════
            // 5. Chat input bar
            // ════════════════════════════════════════════════════════════
            ChatInputBar(
                inputText = inputText,
                onInputChanged = viewModel::onInputChanged,
                onSend = viewModel::sendMessage,
                enabled = !isThinking,
            )
        }

        // Snackbar host (overlays at the bottom of the screen)
        SnackbarHost(
            hostState = snackbarHostState,
            modifier = Modifier.align(Alignment.BottomCenter),
        )
    }
}

// ═══════════════════════════════════════════════════════════════════════════
// Chat bubbles
// ═══════════════════════════════════════════════════════════════════════════

@Composable
private fun ChatBubbleItem(
    item: ChatItem,
    onSearchSong: (songName: String, artist: String, songId: String, platform: String) -> Unit = { _, _, _, _ -> },
    onLikeSong: (songId: String, platform: String, songName: String, artist: String) -> Unit = { _, _, _, _ -> },
) {
    when (item) {
        is ChatItem.UserMessage -> UserBubble(item.text)
        is ChatItem.AiMessage -> AiBubble(item.text, item.command)
        is ChatItem.SystemMessage -> SystemBubble(item.text)
        is ChatItem.RecommendationMessage -> RecommendationCard(item.recommendations, onSearchSong, onLikeSong)
    }
}

@Composable
private fun UserBubble(text: String) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.End,
    ) {
        Surface(
            color = MaterialTheme.colorScheme.primary,
            shape = RoundedCornerShape(
                topStart = 16.dp,
                topEnd = 16.dp,
                bottomStart = 16.dp,
                bottomEnd = 4.dp,
            ),
            modifier = Modifier.widthIn(max = 300.dp),
        ) {
            Text(
                text = text,
                modifier = Modifier.padding(12.dp),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onPrimary,
            )
        }
    }
}

@Composable
private fun AiBubble(text: String, command: AiCommand?) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.Start,
    ) {
        Surface(
            color = MaterialTheme.colorScheme.surfaceVariant,
            shape = RoundedCornerShape(
                topStart = 16.dp,
                topEnd = 16.dp,
                bottomStart = 4.dp,
                bottomEnd = 16.dp,
            ),
            modifier = Modifier.widthIn(max = 300.dp),
        ) {
            Text(
                text = text,
                modifier = Modifier.padding(12.dp),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurface,
            )
        }
    }
}

@Composable
private fun SystemBubble(text: String) {
    Box(
        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
            textAlign = TextAlign.Center,
        )
    }
}

// ═══════════════════════════════════════════════════════════════════════════
// Recommendation card
// ═══════════════════════════════════════════════════════════════════════════

@Composable
private fun RecommendationCard(
    recommendations: List<com.example.aimusicplayer.recommendation.reason.RecommendationResult>,
    onSearchSong: (songName: String, artist: String, songId: String, platform: String) -> Unit = { _, _, _, _ -> },
    onLikeSong: (songId: String, platform: String, songName: String, artist: String) -> Unit = { _, _, _, _ -> },
) {
    Surface(
        color = MaterialTheme.colorScheme.surfaceVariant,
        shape = RoundedCornerShape(16.dp),
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp),
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Text(
                text = "为你推荐 ${recommendations.size} 首歌曲：",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface,
            )
            Spacer(Modifier.height(8.dp))
            recommendations.forEach { rec ->
                RecommendationItem(rec, onSearchSong = onSearchSong, onLikeSong = onLikeSong)
                Spacer(Modifier.height(6.dp))
            }
        }
    }
}

@Composable
private fun RecommendationItem(
    rec: com.example.aimusicplayer.recommendation.reason.RecommendationResult,
    onSearchSong: (songName: String, artist: String, songId: String, platform: String) -> Unit = { _, _, _, _ -> },
    onLikeSong: (songId: String, platform: String, songName: String, artist: String) -> Unit = { _, _, _, _ -> },
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(MaterialTheme.colorScheme.surface)
            .padding(horizontal = 14.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = rec.songName,
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = rec.artist,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = rec.reason,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.primary.copy(alpha = 0.7f),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        // 喜欢按钮
        IconButton(onClick = { onLikeSong(rec.songId, rec.platform, rec.songName, rec.artist) }) {
            Icon(
                Icons.Default.FavoriteBorder,
                contentDescription = "喜欢 ${rec.songName}",
                tint = MaterialTheme.colorScheme.error,
            )
        }
        // 搜索按钮
        IconButton(onClick = { onSearchSong(rec.songName, rec.artist, rec.songId, rec.platform) }) {
            Icon(
                Icons.Default.Search,
                contentDescription = "搜索 ${rec.songName}",
                tint = MaterialTheme.colorScheme.primary,
            )
        }
    }
}

// ═══════════════════════════════════════════════════════════════════════════
// Chat input bar
// ═══════════════════════════════════════════════════════════════════════════

@Composable
private fun ChatInputBar(
    inputText: String,
    onInputChanged: (String) -> Unit,
    onSend: () -> Unit,
    enabled: Boolean,
) {
    Surface(
        tonalElevation = 4.dp,
        shadowElevation = 8.dp,
        color = MaterialTheme.colorScheme.surface,
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            OutlinedTextField(
                value = inputText,
                onValueChange = onInputChanged,
                placeholder = { Text("输入歌曲名或播放指令...") },
                modifier = Modifier.weight(1f),
                singleLine = true,
                enabled = enabled,
                shape = RoundedCornerShape(24.dp),
            )
            Button(
                onClick = onSend,
                enabled = inputText.isNotBlank() && enabled,
                shape = RoundedCornerShape(12.dp),
            ) {
                Icon(
                    Icons.AutoMirrored.Filled.Send,
                    contentDescription = "发送",
                    modifier = Modifier.size(18.dp),
                )
            }
        }
    }
}


// =====================================================================
// Quick action chips
// =====================================================================

@Composable
private fun QuickActionChips(
    onRecommend: () -> Unit,
    onPlayRecommend: () -> Unit,
    onMyPreferences: () -> Unit,
    onImport: () -> Unit,
    onLyrics: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 2.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        QuickChip("推荐", onClick = onRecommend)
        QuickChip("播放推荐", onClick = onPlayRecommend)
        QuickChip("我的喜好", onClick = onMyPreferences)
        QuickChip("导入", onClick = onImport)
        QuickChip("歌词", onClick = onLyrics)
    }
}

@Composable
private fun QuickChip(label: String, onClick: () -> Unit) {
    Surface(
        modifier = Modifier.clickable { onClick() },
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.7f),
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
        )
    }
}
