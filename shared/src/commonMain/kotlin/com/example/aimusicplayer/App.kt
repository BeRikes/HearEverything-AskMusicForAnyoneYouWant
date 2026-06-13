package com.example.aimusicplayer

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Error
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.QueueMusic
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Scaffold
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay
import com.example.aimusicplayer.ai.AiAssistant
import com.example.aimusicplayer.cache.CacheRepository
import com.example.aimusicplayer.music.DownloadManager
import com.example.aimusicplayer.music.MusicSearchManager
import com.example.aimusicplayer.network.music.BilibiliApi
import com.example.aimusicplayer.network.music.NeteaseApi
import com.example.aimusicplayer.network.music.KugouApi
import com.example.aimusicplayer.network.music.QQMusicApi
import com.example.aimusicplayer.player.MusicPlayer
import com.example.aimusicplayer.player.SystemMediaController
import com.example.aimusicplayer.settings.SettingsKeys
import com.example.aimusicplayer.settings.SettingsRepository
import com.example.aimusicplayer.settings.SettingsStorage
import com.example.aimusicplayer.ui.library.LibraryScreen
import com.example.aimusicplayer.ui.library.LibraryViewModel
import com.example.aimusicplayer.ui.main.MainScreen
import com.example.aimusicplayer.ui.main.MainViewModel
import com.example.aimusicplayer.ui.player.MiniPlayerBar
import com.example.aimusicplayer.ui.player.NowPlayingOverlay
import com.example.aimusicplayer.ui.search.SearchViewModel
import com.example.aimusicplayer.ui.settings.SettingsScreen
import com.example.aimusicplayer.ui.settings.SettingsViewModel
import com.example.aimusicplayer.behavior.LocalSongIndex
import com.example.aimusicplayer.behavior.UserBehaviorTracker
import com.example.aimusicplayer.music.PlaylistManager
import com.example.aimusicplayer.ui.playlist.PlaylistScreen
import com.example.aimusicplayer.ui.playlist.PlaylistViewModel
import com.example.aimusicplayer.import.PlaylistImportManager
import com.example.aimusicplayer.ui.theme.HearEverythingTheme
import com.example.aimusicplayer.export.ExportImportHandler
import com.example.aimusicplayer.export.FilePicker
import com.example.aimusicplayer.export.ShareHandler

// ═══════════════════════════════════════════════════════════════════════════
// Navigation
// ═══════════════════════════════════════════════════════════════════════════

/**
 * Top-level tabs for the HearEverything music player.
 *
 * Order: 对话 (chat) | 歌单 (library) | 设置 (settings)
 */
private enum class Screen(
    val label: String,
    val icon: ImageVector,
    val selectedIcon: ImageVector,
) {
    Main(
        label = "对话",
        icon = Icons.Default.MusicNote,
        selectedIcon = Icons.Default.MusicNote,
    ),
    Playlist(
        label = "播放列表",
        icon = Icons.Default.QueueMusic,
        selectedIcon = Icons.Default.QueueMusic,
    ),
    Library(
        label = "下载",
        icon = Icons.Default.Download,
        selectedIcon = Icons.Default.Download,
    ),
    Settings(
        label = "设置",
        icon = Icons.Default.Settings,
        selectedIcon = Icons.Default.Settings,
    ),
}

// ═══════════════════════════════════════════════════════════════════════════
// App root composable
// ═══════════════════════════════════════════════════════════════════════════

/**
 * Root composable for the HearEverything music player.
 *
 * This is the entry point called from platform hosts (Android [MainActivity],
 * iOS [MainViewController]).
 *
 * ## Service wiring
 *
 * Platform-specific dependencies are injected via factory lambdas because
 * [MusicPlayer], [DownloadManager], and [CacheRepository] are backed by
 * `expect class` declarations with platform-specific constructors.
 *
 * @param storage                     Cross-platform settings storage (injected by platform host)
 * @param createMusicPlayer           Factory for platform [MusicPlayer] instance
 * @param createDownloadManager       Factory for platform [DownloadManager] instance
 * @param createCacheRepository       Factory for platform [CacheRepository] instance
 * @param createPlaylistManager       Factory for [PlaylistManager]
 * @param createSystemMediaController Factory for [SystemMediaController] (lock screen / notification)
 */
@Composable
fun App(
    storage: SettingsStorage,
    createMusicPlayer: () -> MusicPlayer,
    createDownloadManager: () -> DownloadManager,
    createCacheRepository: () -> CacheRepository,
    createPlaylistManager: () -> PlaylistManager,
    createSystemMediaController: () -> SystemMediaController,
    createBehaviorTracker: () -> UserBehaviorTracker,
    createSongIndex: () -> LocalSongIndex,
    createExportImportHandler: () -> ExportImportHandler,
    createShareHandler: () -> ShareHandler,
    createFilePicker: () -> FilePicker,
    onPickImportFile: ((String) -> Unit) -> Unit = {},
) {
    // ── Create services (memoized, survive recomposition) ───────────
    val settingsRepo = remember { SettingsRepository(storage) }
    val musicPlayer = remember { createMusicPlayer() }
    val systemMediaController = remember { createSystemMediaController() }
    val downloadManager = remember { createDownloadManager() }
    val cacheRepository = remember { createCacheRepository() }
    val aiAssistant = remember { AiAssistant(storage) }

    // Attach system media controller to player (lock screen, notification)
    LaunchedEffect(musicPlayer) {
        systemMediaController.attach(musicPlayer)
    }

    val musicApis = remember {
        listOf(BilibiliApi(), NeteaseApi(), QQMusicApi(), KugouApi())
    }
    val searchManager = remember { MusicSearchManager(musicApis) }

    // ── Navigation state (created early so callbacks can capture it) ──
    val currentScreenState = remember { mutableStateOf(Screen.Main) }

    // ── ViewModels (created once per process) ───────────────────────
    val behaviorTracker = remember { createBehaviorTracker() }
    val songIndex = remember { createSongIndex() }
    val playlistManager = remember { createPlaylistManager() }
    val importManager = remember {
        PlaylistImportManager(
            musicApis = musicApis,
            searchManager = searchManager,
            playlistManager = playlistManager,
        )
    }

    val playlistViewModel = remember {
        PlaylistViewModel(
            playlistManager = playlistManager,
            musicPlayer = musicPlayer,
            settingsRepo = settingsRepo,
            importManager = importManager,
            downloadManager = downloadManager,
            behaviorTracker = behaviorTracker,
        )
    }

    val mainViewModel = remember {
        MainViewModel(
            musicPlayer = musicPlayer,
            aiAssistant = aiAssistant,
            searchManager = searchManager,
            downloadManager = downloadManager,
            settingsRepo = settingsRepo,
            playlistManager = playlistManager,
            userBehaviorTracker = behaviorTracker,
            localSongIndex = songIndex,
            onImportLinkDetected = { url ->
                playlistViewModel.startImport(url)
                playlistViewModel.selectTab(3) // 切换到导入歌单子tab
                currentScreenState.value = Screen.Playlist
            },
            onAutoImportLinkDetected = { url ->
                playlistViewModel.startImport(url)
                playlistViewModel.selectTab(3) // 切换到导入歌单子tab
                currentScreenState.value = Screen.Playlist
                // Trigger auto-confirm after the import reaches CONFIRMING phase
                playlistViewModel.autoConfirmAll()
            },
        )
    }

    // 当 PlaylistViewModel 中喜欢状态变更时，同步刷新 MainViewModel
    LaunchedEffect(Unit) {
        playlistViewModel.onFavoriteChanged = { songId, platform ->
            if (songId.isNotEmpty()) {
                mainViewModel.refreshLikeState(songId, platform)
            } else {
                // 清空全部：重新加载喜欢列表
                mainViewModel.refreshAllLikes()
            }
        }
    }

    val searchViewModel = remember {
        SearchViewModel(
            musicPlayer = musicPlayer,
            searchManager = searchManager,
            cacheRepository = cacheRepository,
            downloadManager = downloadManager,
            playlistManager = playlistManager,
            behaviorTracker = behaviorTracker,
        )
    }

    val settingsViewModel = remember {
        SettingsViewModel(
            settingsRepo = settingsRepo,
            aiAssistant = aiAssistant,
            downloadManager = downloadManager,
        )
    }
    val exportImportHandler = remember { createExportImportHandler() }
    val shareHandler = remember { createShareHandler() }
    val filePicker = remember { createFilePicker() }

    val libraryViewModel = remember {
        LibraryViewModel(
            musicPlayer = musicPlayer,
            downloadManager = downloadManager,
            playlistManager = playlistManager,
            exportImportHandler = exportImportHandler,
            shareHandler = shareHandler,
            filePicker = filePicker,
        )
    }

    // ── Wire system media controller for lock screen / notification ──
    systemMediaController.onNext = { mainViewModel.nextTrack() }
    systemMediaController.onPrevious = { mainViewModel.previousTrack() }
    systemMediaController.onToggleRepeatMode = { playlistViewModel.togglePlayMode() }
    systemMediaController.onToggleFavorite = { playlistViewModel.toggleFavorite() }
    systemMediaController.onDownload = { mainViewModel.downloadCurrentSong() }

    // ── Wire cache-size refresh signals ───────────────────────────
    mainViewModel.onCacheChanged = { settingsViewModel.refreshCacheSize() }
    searchViewModel.onCacheChanged = { settingsViewModel.refreshCacheSize() }
    libraryViewModel.onCacheChanged = { settingsViewModel.refreshCacheSize() }

    // ── Theme ───────────────────────────────────────────────────────
    val themeMode by settingsViewModel.themeMode.collectAsState()
    val isDark = when (themeMode) {
        SettingsKeys.THEME_MODE_DARK -> true
        SettingsKeys.THEME_MODE_LIGHT -> false
        else -> isSystemInDarkTheme()
    }

    HearEverythingTheme(darkTheme = isDark) {
        var currentScreen by currentScreenState

        // ── Player state ───────────────────────────────────────────
        val currentSong by musicPlayer.currentSong.collectAsState()
        val isPlaying by musicPlayer.isPlaying.collectAsState()
        val currentPosition by musicPlayer.currentPosition.collectAsState()
        val duration by musicPlayer.duration.collectAsState()

        // ── Overlay state from MainViewModel ───────────────────────
        val showLyrics by mainViewModel.showLyrics.collectAsState()
        val lyricsText by mainViewModel.lyricsText.collectAsState()
        val downloadProgress = mainViewModel.downloadProgress.collectAsState().value[currentSong?.songId]?.progress
        val mainPreparing by mainViewModel.isPreparingDownload.collectAsState()
        val searchPreparing by searchViewModel.preparingDownloads.collectAsState()
        val isPreparingDownload = mainPreparing || searchPreparing.contains(currentSong?.songId)
        val isCurrentSongDownloaded by mainViewModel.isCurrentSongDownloaded.collectAsState()
        val playerError by mainViewModel.playerError.collectAsState()
        val sleepTimerState by mainViewModel.sleepTimer.collectAsState()
        val lyricsOffsetMs by mainViewModel.lyricsOffsetMs.collectAsState()
        val isReSearching by mainViewModel.isReSearchingLyrics.collectAsState()
        val isLiked by mainViewModel.isCurrentSongLiked.collectAsState()
        val likedSongIds by mainViewModel.likedSongIds.collectAsState()

        var isOverlayVisible by remember { mutableStateOf(false) }

        // ── Top error banner state ──────────────────────────────────
        // Track which error message was dismissed, so a new/different error
        // re-shows the banner. Auto-dismissed after 4 seconds.
        var dismissedError by remember { mutableStateOf<String?>(null) }
        val displayError = playerError?.takeIf { it != dismissedError }

        LaunchedEffect(playerError) {
            if (playerError != null) {
                dismissedError = null // show the banner for new error
                delay(4_000)          // auto-dismiss after 4 seconds
                dismissedError = playerError
            } else {
                dismissedError = null
            }
        }

        // Wire AI agent callbacks (after state declarations)
        mainViewModel.onSearchRequested = { songName, artist ->
            searchViewModel.expandSearch()
            searchViewModel.onSongNameChanged(songName)
            searchViewModel.onArtistChanged(artist)
            searchViewModel.search()
        }
        mainViewModel.onOpenNowPlaying = { isOverlayVisible = true }
        mainViewModel.onOpenLyrics = {
            isOverlayVisible = true
            mainViewModel.toggleLyrics()
        }

        Box(modifier = Modifier.fillMaxSize()) {
            Scaffold(
                bottomBar = {
                    Column {
                        // Adjacent song names + position derived reactively from playlist + current song
                        val playlist by playlistViewModel.playlist.collectAsState()
                        val currentId = currentSong?.songId
                        val idx = playlist.indexOfFirst { it.songId == currentId }
                        val prevName = if (idx > 0) playlist[idx - 1].songName else null
                        val nextName = if (idx >= 0 && idx < playlist.lastIndex) playlist[idx + 1].songName else null
                        // Pass position info so MiniPlayerBar can directly compute boundaries
                        val playlistIndex = idx.coerceAtLeast(0)

                        MiniPlayerBar(
                            currentSong = currentSong,
                            isPlaying = isPlaying,
                            onPlayPause = { mainViewModel.togglePlayPause() },
                            onPrevious = { mainViewModel.previousTrack() },
                            onNext = { mainViewModel.nextTrack() },
                            onTap = { isOverlayVisible = true },
                            onPlaylist = { currentScreen = Screen.Playlist },
                            playbackError = playerError,
                            previousSongName = prevName,
                            nextSongName = nextName,
                            playlistIndex = playlistIndex,
                            playlistSize = playlist.size,
                        )

                        NavigationBar {
                            Screen.entries.forEach { screen ->
                                val selected = currentScreen == screen

                                NavigationBarItem(
                                    selected = selected,
                                    onClick = { currentScreen = screen },
                                    icon = {
                                        Icon(
                                            imageVector = if (selected) screen.selectedIcon else screen.icon,
                                            contentDescription = screen.label,
                                        )
                                    },
                                    label = {
                                        Text(text = screen.label)
                                    },
                                    colors = NavigationBarItemDefaults.colors(),
                                )
                            }
                        }
                    }
                },
            ) { innerPadding ->
                val modifier = Modifier.padding(innerPadding)

                when (currentScreen) {
                    Screen.Main -> MainScreen(
                        viewModel = mainViewModel,
                        searchViewModel = searchViewModel,
                        onNavigateToSettings = { currentScreen = Screen.Settings },
                        likedSongIds = likedSongIds,
                        modifier = modifier,
                    )

                    Screen.Playlist -> PlaylistScreen(
                        viewModel = playlistViewModel,
                        onNavigateToSearch = {
                            currentScreen = Screen.Main
                            searchViewModel.expandSearch()
                        },
                        currentSongId = currentSong?.songId,
                        isPlaying = isPlaying,
                        sleepTimerState = sleepTimerState,
                        modifier = modifier,
                    )

                    Screen.Library -> LibraryScreen(
                        viewModel = libraryViewModel,
                        onBack = { currentScreen = Screen.Main },
                        modifier = modifier,
                        onPickImportFile = onPickImportFile,
                    )

                    Screen.Settings -> SettingsScreen(
                        viewModel = settingsViewModel,
                        onBack = { currentScreen = Screen.Main },
                        sleepTimerState = sleepTimerState,
                        onStartSleepTimer = { minutes, finishCurrentSong ->
                            mainViewModel.startSleepTimer(minutes, finishCurrentSong)
                        },
                        onCancelSleepTimer = { mainViewModel.cancelSleepTimer() },
                        onToggleFinishSong = { mainViewModel.toggleSleepTimerFinishSong() },
                        modifier = modifier,
                    )
                }
            }

            // ── Now-playing overlay (on top of everything) ──────────
            NowPlayingOverlay(
                visible = isOverlayVisible,
                currentSong = currentSong,
                isPlaying = isPlaying,
                currentPosition = currentPosition,
                duration = duration,
                onDismiss = { isOverlayVisible = false },
                onPlayPause = { mainViewModel.togglePlayPause() },
                onNext = { mainViewModel.nextTrack() },
                onPrevious = { mainViewModel.previousTrack() },
                onDownload = { mainViewModel.downloadCurrentSong() },
                onLyrics = { mainViewModel.toggleLyrics() },
                onPlaylist = {
                    isOverlayVisible = false
                    currentScreen = Screen.Playlist
                },
                onSeek = { mainViewModel.seekTo(it) },
                showLyrics = showLyrics,
                lyricsText = lyricsText,
                downloadProgress = downloadProgress,
                isPreparingDownload = isPreparingDownload,
                isDownloaded = isCurrentSongDownloaded,
                playbackError = playerError,
                onClearError = { mainViewModel.clearPlaybackError() },
                lyricsOffsetMs = lyricsOffsetMs,
                onLyricsOffsetChanged = { mainViewModel.setLyricsOffset(currentSong?.songId ?: "", it) },
                onLyricsOffsetReset = { mainViewModel.resetLyricsOffset(currentSong?.songId ?: "") },
                onLyricsReSearch = { mainViewModel.reSearchLyrics(currentSong?.songId ?: "") },
                isReSearching = isReSearching,
                onLyricsManualSearch = { songId, songName, artist ->
                    mainViewModel.reSearchLyricsWith(songId, songName, artist)
                },
                sleepTimerState = sleepTimerState,
                onToggleLike = {
                    mainViewModel.toggleLike()
                    playlistViewModel.toggleFavorite()
                },
                isLiked = isLiked,
            )

            // ── Top error banner (LAST child = always on top) ──────
            AnimatedVisibility(
                visible = displayError != null,
                enter = slideInVertically(initialOffsetY = { -it }) + fadeIn(),
                exit = slideOutVertically(targetOffsetY = { -it }) + fadeOut(),
                modifier = Modifier.align(Alignment.TopCenter),
            ) {
                displayError?.let { error ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .statusBarsPadding()
                            .background(Color(0xFFD32F2F))
                            .clickable {
                                dismissedError = error
                                mainViewModel.clearPlaybackError()
                            }
                            .padding(horizontal = 16.dp, vertical = 14.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Icon(
                            Icons.Default.Error,
                            contentDescription = null,
                            tint = Color.White,
                            modifier = Modifier.height(20.dp),
                        )
                        Spacer(Modifier.width(12.dp))
                        Text(
                            text = error,
                            color = Color.White,
                            style = androidx.compose.material3.MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.Medium,
                            modifier = Modifier.weight(1f),
                        )
                        IconButton(
                            onClick = {
                                dismissedError = error
                                mainViewModel.clearPlaybackError()
                            },
                            modifier = Modifier.height(28.dp),
                        ) {
                            Icon(
                                Icons.Default.Close,
                                contentDescription = "关闭",
                                tint = Color.White.copy(alpha = 0.8f),
                                modifier = Modifier.height(18.dp),
                            )
                        }
                    }
                }
            }
        }
    }
}
