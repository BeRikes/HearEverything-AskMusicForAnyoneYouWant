# Sleep Timer Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Add a sleep timer feature — users set a countdown, playback pauses automatically when the timer expires.

**Architecture:** Timer logic lives in MainViewModel with a `SleepTimerState` StateFlow. UI components in NowPlayingOverlay, PlaylistScreen, and SettingsScreen observe the state. The existing auto-next-track detection in MainViewModel init is extended to handle the "finish current song" mode.

**Tech Stack:** Kotlin Multiplatform, Compose Multiplatform, kotlinx.coroutines

---

### Task 1: Add SleepTimerState and core timer logic to MainViewModel

**Files:**
- Modify: `shared/src/commonMain/kotlin/com/example/aimusicplayer/ui/main/MainViewModel.kt`

- [ ] **Step 1: Add SleepTimerState data class at file level**

Add after the imports (before the MainViewModel class):

```kotlin
/**
 * State for the sleep timer feature.
 *
 * @property isActive         Whether a countdown is currently running.
 * @property remainingMs      Milliseconds remaining, updated every second.
 * @property totalMs          Initial total duration in milliseconds.
 * @property finishCurrentSong If true, wait for the current song to end
 *                             after countdown reaches zero before pausing.
 */
data class SleepTimerState(
    val isActive: Boolean = false,
    val remainingMs: Long = 0L,
    val totalMs: Long = 0L,
    val finishCurrentSong: Boolean = false,
)
```

- [ ] **Step 2: Add sleep timer state and job fields to MainViewModel**

After the lyrics state section (around line 167, before `init`), add:

```kotlin
// ══════════════════════════════════════════════════════════════
// Sleep timer state
// ══════════════════════════════════════════════════════════════

private val _sleepTimer = MutableStateFlow(SleepTimerState())
val sleepTimer: StateFlow<SleepTimerState> = _sleepTimer.asStateFlow()
private var timerJob: kotlinx.coroutines.Job? = null
```

The import `kotlinx.coroutines.Job` is already transitively available since `scope.launch` returns `Job` and is used in the class. No new import needed since `asStateFlow()` is already imported.

- [ ] **Step 3: Add startSleepTimer method**

After the `cancelSleepTimer` will be placed next to it. Add these methods after the player control actions section (around line 399, after `previousTrack`):

```kotlin
/**
 * Start or restart the sleep timer with the given duration and mode.
 *
 * Calling this while a timer is already active overwrites the previous timer.
 *
 * @param minutes           Countdown duration in minutes.
 * @param finishCurrentSong Whether to wait for the current song to finish
 *                          before pausing after countdown reaches zero.
 */
fun startSleepTimer(minutes: Int, finishCurrentSong: Boolean) {
    timerJob?.cancel()
    val totalMs = minutes * 60_000L
    _sleepTimer.value = SleepTimerState(
        isActive = true,
        remainingMs = totalMs,
        totalMs = totalMs,
        finishCurrentSong = finishCurrentSong,
    )
    timerJob = scope.launch { sleepTimerLoop() }
}

/**
 * Cancel the active sleep timer and reset state.
 * Safe to call when no timer is active (no-op).
 */
fun cancelSleepTimer() {
    timerJob?.cancel()
    timerJob = null
    _sleepTimer.value = SleepTimerState()
}

/**
 * Toggle the "finish current song" flag on the active timer.
 * Only meaningful when a countdown timer is already running.
 * If no timer is active, this is a no-op.
 */
fun toggleSleepTimerFinishSong() {
    val current = _sleepTimer.value
    if (current.isActive && current.totalMs > 0) {
        _sleepTimer.value = current.copy(finishCurrentSong = !current.finishCurrentSong)
    }
}

/**
 * Internal countdown loop. Decrements [remainingMs] by 1000 every second.
 * When the countdown reaches zero and [SleepTimerState.finishCurrentSong] is false,
 * pauses immediately. Otherwise, the existing auto-next-track detection in [init]
 * picks up the zeroed timer and pauses when the song naturally ends.
 */
private suspend fun sleepTimerLoop() {
    while (_sleepTimer.value.remainingMs > 0) {
        delay(1_000)
        val state = _sleepTimer.value
        val newRemaining = (state.remainingMs - 1_000).coerceAtLeast(0L)
        _sleepTimer.value = state.copy(remainingMs = newRemaining)
    }
    // Countdown finished
    val state = _sleepTimer.value
    if (!state.finishCurrentSong) {
        // Pause immediately
        musicPlayer.pause()
        _sleepTimer.value = SleepTimerState()
    }
    // If finishCurrentSong is true, do nothing — the auto-next-track
    // detection in init handles pausing when the song naturally ends.
}
```

Add the import for `Job` if needed — add after the existing `launch` import:
```kotlin
import kotlinx.coroutines.Job
```
(Actually, `Job` is already implicitly available via `kotlinx.coroutines.launch` return type. No import needed since we reference it as `kotlinx.coroutines.Job` or can use the already-imported types.)

Add at the top import section:
```kotlin
import kotlinx.coroutines.Job
```

- [ ] **Step 4: Modify the existing auto-next-track detection in init**

Replace the auto-next detection block (lines 218-245 in `init`) with version that checks sleep timer:

Find this code in `init {}`:
```kotlin
        // Auto-play next song when current song finishes
        scope.launch {
            var wasPlaying = false
            isPlaying.collect { playing ->
                if (wasPlaying && !playing) {
                    // Playback just stopped — check if it ended naturally
                    val pos = currentPosition.value
                    val dur = duration.value
                    if (dur > 0 && pos > 0 && pos >= dur - 3000) {
                        log.d { "Auto-next: song ended naturally (pos=$pos, dur=$dur)" }
                        delay(2_000)
                        if (playlistManager.getPlayMode() == PlayMode.REPEAT_ONE) {
                            // Replay current song
                            musicPlayer.seekTo(0)
                            musicPlayer.resume()
                        } else {
                            // Remove finished song from playlist before advancing
                            val finished = currentSong.value
                            if (finished != null) {
                                playlistManager.removeFromPlaylist(finished.songId, finished.platform)
                            }
                            nextTrack()
                        }
                    }
                }
                wasPlaying = playing
            }
        }
```

Replace with:
```kotlin
        // Auto-play next song when current song finishes
        scope.launch {
            var wasPlaying = false
            isPlaying.collect { playing ->
                if (wasPlaying && !playing) {
                    // Playback just stopped — check if it ended naturally
                    val pos = currentPosition.value
                    val dur = duration.value
                    if (dur > 0 && pos > 0 && pos >= dur - 3000) {
                        // Check sleep timer: if finish-current-song mode is active
                        // and countdown has already reached zero, pause instead of advancing
                        val timerState = _sleepTimer.value
                        if (timerState.isActive && timerState.remainingMs <= 0L && timerState.finishCurrentSong) {
                            log.d { "Sleep timer: finish-current-song — pausing after song end" }
                            _sleepTimer.value = SleepTimerState()
                            // Player already stopped naturally; no need to call pause()
                        } else {
                            // Normal auto-next behavior
                            log.d { "Auto-next: song ended naturally (pos=$pos, dur=$dur)" }
                            delay(2_000)
                            if (playlistManager.getPlayMode() == PlayMode.REPEAT_ONE) {
                                // Replay current song
                                musicPlayer.seekTo(0)
                                musicPlayer.resume()
                            } else {
                                // Remove finished song from playlist before advancing
                                val finished = currentSong.value
                                if (finished != null) {
                                    playlistManager.removeFromPlaylist(finished.songId, finished.platform)
                                }
                                nextTrack()
                            }
                        }
                    }
                }
                wasPlaying = playing
            }
        }
```

- [ ] **Step 5: Add format helper for timer display**

Add a companion object or top-level helper function to convert remainingMs to display text. Add this at file level after `SleepTimerState`:

```kotlin
/**
 * Format remaining milliseconds as a human-readable timer string.
 * - < 1 hour:  "MM:SS"
 * - ≥ 1 hour:  "HH:MM:SS"
 */
fun formatTimerRemaining(remainingMs: Long): String {
    val totalSeconds = remainingMs / 1000
    val hours = totalSeconds / 3600
    val minutes = (totalSeconds % 3600) / 60
    val seconds = totalSeconds % 60
    return if (hours > 0) {
        "${hours}:${minutes.toString().padStart(2, '0')}:${seconds.toString().padStart(2, '0')}"
    } else {
        "${minutes.toString().padStart(2, '0')}:${seconds.toString().padStart(2, '0')}"
    }
}
```

- [ ] **Step 6: Commit**

```bash
git add shared/src/commonMain/kotlin/com/example/aimusicplayer/ui/main/MainViewModel.kt
git commit -m "feat: add SleepTimerState and core timer logic to MainViewModel

Co-Authored-By: Claude Opus 4.8 <noreply@anthropic.com>"
```

---

### Task 2: Pass timer state through App.kt

**Files:**
- Modify: `shared/src/commonMain/kotlin/com/example/aimusicplayer/App.kt`

- [ ] **Step 1: Collect sleep timer state from MainViewModel**

After the existing state collection block (around line 268, after `playerError`), add:

```kotlin
val sleepTimerState by mainViewModel.sleepTimer.collectAsState()
```

- [ ] **Step 2: Pass timer state to NowPlayingOverlay**

In the `NowPlayingOverlay(...)` call (around line 390), add a new parameter `sleepTimerState = sleepTimerState` after `isReSearching` and before the closing `)`:

The call block currently ends with:
```kotlin
                onLyricsManualSearch = { songId, songName, artist ->
                    mainViewModel.reSearchLyricsWith(songId, songName, artist)
                },
            )
```

Change to:
```kotlin
                onLyricsManualSearch = { songId, songName, artist ->
                    mainViewModel.reSearchLyricsWith(songId, songName, artist)
                },
                sleepTimerState = sleepTimerState,
            )
```

- [ ] **Step 3: Pass timer state to PlaylistScreen**

In the `PlaylistScreen(...)` call (around line 364):

Current:
```kotlin
                    Screen.Playlist -> PlaylistScreen(
                        viewModel = playlistViewModel,
                        onNavigateToSearch = {
                            currentScreen = Screen.Main
                            searchViewModel.expandSearch()
                        },
                        currentSongId = currentSong?.songId,
                        isPlaying = isPlaying,
                        modifier = modifier,
                    )
```

Change to:
```kotlin
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
```

- [ ] **Step 4: Pass timer state and callbacks to SettingsScreen**

In the `SettingsScreen(...)` call (around line 381):

Current:
```kotlin
                    Screen.Settings -> SettingsScreen(
                        viewModel = settingsViewModel,
                        onBack = { currentScreen = Screen.Main },
                        modifier = modifier,
                    )
```

Change to:
```kotlin
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
```

- [ ] **Step 5: Commit**

```bash
git add shared/src/commonMain/kotlin/com/example/aimusicplayer/App.kt
git commit -m "feat: wire sleep timer state and callbacks through App.kt

Co-Authored-By: Claude Opus 4.8 <noreply@anthropic.com>"
```

---

### Task 3: Add timer info text to NowPlayingOverlay header

**Files:**
- Modify: `shared/src/commonMain/kotlin/com/example/aimusicplayer/ui/player/NowPlayingOverlay.kt`

- [ ] **Step 1: Add sleepTimerState parameter to NowPlayingOverlay**

In the `NowPlayingOverlay` function signature (after line 129 `onLyricsManualSearch`), add:

```kotlin
    sleepTimerState: SleepTimerState = SleepTimerState(),
```

Add the import for `SleepTimerState` (it's in `com.example.aimusicplayer.ui.main`):
```kotlin
import com.example.aimusicplayer.ui.main.SleepTimerState
import com.example.aimusicplayer.ui.main.formatTimerRemaining
```

- [ ] **Step 2: Pass sleepTimerState through NowPlayingContent**

Add the parameter to `NowPlayingContent` (around line 204, after `onClearError`):

```kotlin
    sleepTimerState: SleepTimerState = SleepTimerState(),
```

And in the call site from `NowPlayingOverlay` (around line 181), add it:

```kotlin
            NowPlayingContent(
                currentSong = currentSong,
                ...
                onClearError = onClearError,
                sleepTimerState = sleepTimerState,
            )
```

- [ ] **Step 3: Modify the "正在播放" Text in header to include timer info**

Find in `NowPlayingContent` (lines 244-249):

```kotlin
                Text(
                    text = "正在播放",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onBackground,
                )
```

Replace with:

```kotlin
                val timerInfoText = when {
                    !sleepTimerState.isActive -> null
                    sleepTimerState.remainingMs <= 0L && sleepTimerState.finishCurrentSong -> "  ·  🎵 播完关闭"
                    else -> "  ·  ⏰ ${formatTimerRemaining(sleepTimerState.remainingMs)}"
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = "正在播放",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onBackground,
                    )
                    if (timerInfoText != null) {
                        Text(
                            text = timerInfoText,
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary,
                        )
                    }
                }
```

- [ ] **Step 4: Commit**

```bash
git add shared/src/commonMain/kotlin/com/example/aimusicplayer/ui/player/NowPlayingOverlay.kt
git commit -m "feat: add sleep timer info display to NowPlayingOverlay header

Co-Authored-By: Claude Opus 4.8 <noreply@anthropic.com>"
```

---

### Task 4: Add timer info next to "共 N 首" in PlaylistScreen

**Files:**
- Modify: `shared/src/commonMain/kotlin/com/example/aimusicplayer/ui/playlist/PlaylistScreen.kt`

- [ ] **Step 1: Add sleepTimerState parameter to PlaylistScreen**

In the `PlaylistScreen` function signature (line 64), add the parameter after `isPlaying`:

```kotlin
@Composable
fun PlaylistScreen(
    viewModel: PlaylistViewModel,
    onNavigateToSearch: () -> Unit,
    currentSongId: String? = null,
    isPlaying: Boolean = false,
    sleepTimerState: SleepTimerState = SleepTimerState(),
    modifier: Modifier = Modifier,
)
```

Add imports:
```kotlin
import com.example.aimusicplayer.ui.main.SleepTimerState
import com.example.aimusicplayer.ui.main.formatTimerRemaining
```

- [ ] **Step 2: Pass sleepTimerState to CurrentPlaylistTab**

Find where `CurrentPlaylistTab` is called in `PlaylistScreen`. Locate the call and add `sleepTimerState = sleepTimerState` to its parameters.

Grep for the call site:
```
CurrentPlaylistTab(
    playlist = playlist,
    currentSongId = ...
```

Add `sleepTimerState = sleepTimerState,` to the parameter list.

- [ ] **Step 3: Add sleepTimerState parameter to CurrentPlaylistTab**

In the `CurrentPlaylistTab` function signature (line 253), add the parameter:

```kotlin
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
)
```

- [ ] **Step 4: Modify "共 N 首" Text to include timer info**

In `CurrentPlaylistTab`, find (line 321):

```kotlin
                Text("共 ${playlist.size} 首", style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Medium)
```

Replace with:

```kotlin
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
```

Note: The original line at 321 didn't set `color`, which means it uses the default text color (inherited `onSurfaceVariant` from the parent context or MaterialTheme default). The style `labelLarge` doesn't have a fixed color. The row containing "清空" and "⬇下载" uses `onSurfaceVariant` for their Text colors, so we match that here.

- [ ] **Step 5: Commit**

```bash
git add shared/src/commonMain/kotlin/com/example/aimusicplayer/ui/playlist/PlaylistScreen.kt
git commit -m "feat: add sleep timer info next to '共 N 首' in PlaylistScreen

Co-Authored-By: Claude Opus 4.8 <noreply@anthropic.com>"
```

---

### Task 5: Add timer entry row and Bottom Sheet picker to SettingsScreen

**Files:**
- Modify: `shared/src/commonMain/kotlin/com/example/aimusicplayer/ui/settings/SettingsScreen.kt`

- [ ] **Step 1: Add parameters to SettingsScreen**

In the function signature (line 73), add after `onBack`:

```kotlin
@Composable
fun SettingsScreen(
    viewModel: SettingsViewModel,
    onBack: () -> Unit,
    sleepTimerState: SleepTimerState = SleepTimerState(),
    onStartSleepTimer: (minutes: Int, finishCurrentSong: Boolean) -> Unit = { _, _ -> },
    onCancelSleepTimer: () -> Unit = {},
    onToggleFinishSong: () -> Unit = {},
    modifier: Modifier = Modifier,
)
```

Add imports:
```kotlin
import com.example.aimusicplayer.ui.main.SleepTimerState
import com.example.aimusicplayer.ui.main.formatTimerRemaining
```

- [ ] **Step 2: Add timer section before "存储管理" section**

After the "外观" section (after line 278 `HorizontalDivider(...)` before section "存储管理") and its ThemeModeDropdown block, add:

```kotlin
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))

                // ── Section: 定时关闭 ────────────────────────────────────────
                SectionHeader("定时关闭")

                SleepTimerSection(
                    sleepTimerState = sleepTimerState,
                    onStartSleepTimer = onStartSleepTimer,
                    onCancelSleepTimer = onCancelSleepTimer,
                    onToggleFinishSong = onToggleFinishSong,
                )

                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
```

- [ ] **Step 3: Add SleepTimerSection + BottomSheet composables**

Add at the end of the file (before the existing private helper composables `SectionHeader`, `SettingsCard`, `SettingsToggle` at line 386+), the sleep timer section composable:

```kotlin
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SleepTimerSection(
    sleepTimerState: SleepTimerState,
    onStartSleepTimer: (Int, Boolean) -> Unit,
    onCancelSleepTimer: () -> Unit,
    onToggleFinishSong: () -> Unit,
) {
    var showBottomSheet by remember { mutableStateOf(false) }
    var customMinutesText by remember { mutableStateOf("") }
    val finishCurrentSong = sleepTimerState.finishCurrentSong

    // Status text for the entry row
    val statusText = when {
        !sleepTimerState.isActive -> "未设置"
        sleepTimerState.remainingMs <= 0L && sleepTimerState.finishCurrentSong -> "等待曲末暂停"
        sleepTimerState.finishCurrentSong -> "剩余 ${formatTimerRemaining(sleepTimerState.remainingMs)} · 播完关闭"
        else -> "剩余 ${formatTimerRemaining(sleepTimerState.remainingMs)}"
    }

    // Entry row
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { showBottomSheet = true },
        colors = CardDefaults.cardColors(
            containerColor = if (sleepTimerState.isActive)
                MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f)
            else
                MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
        ),
        shape = RoundedCornerShape(12.dp),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 14.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column {
                Text(
                    "⏰ 定时关闭",
                    fontWeight = FontWeight.Medium,
                    style = MaterialTheme.typography.bodyMedium,
                )
                Spacer(Modifier.height(2.dp))
                Text(
                    statusText,
                    style = MaterialTheme.typography.bodySmall,
                    color = if (sleepTimerState.isActive)
                        MaterialTheme.colorScheme.primary
                    else
                        MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            if (sleepTimerState.isActive) {
                TextButton(onClick = onCancelSleepTimer) {
                    Text("关闭", color = MaterialTheme.colorScheme.error)
                }
            }
        }
    }

    // ── Bottom Sheet ─────────────────────────────────────────
    if (showBottomSheet) {
        ModalBottomSheet(
            onDismissRequest = { showBottomSheet = false },
            shape = RoundedCornerShape(topStart = 20.dp, topEnd = 20.dp),
            containerColor = MaterialTheme.colorScheme.surface,
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp)
                    .padding(bottom = 32.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                Text(
                    "定时关闭",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(bottom = 8.dp),
                )

                // Preset options
                val presets = listOf(
                    "关闭定时器" to 0,
                    "10 分钟" to 10,
                    "15 分钟" to 15,
                    "30 分钟" to 30,
                    "60 分钟" to 60,
                )

                val selectedMinutes = if (sleepTimerState.isActive && sleepTimerState.totalMs > 0) {
                    (sleepTimerState.totalMs / 60_000L).toInt()
                } else {
                    null
                }

                presets.forEach { (label, minutes) ->
                    val isSelected = if (minutes == 0) {
                        !sleepTimerState.isActive
                    } else {
                        selectedMinutes == minutes
                    }
                    val isFinishSongActive = sleepTimerState.isActive &&
                            sleepTimerState.remainingMs <= 0L &&
                            sleepTimerState.finishCurrentSong

                    Surface(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable {
                                if (minutes == 0) {
                                    onCancelSleepTimer()
                                } else {
                                    onStartSleepTimer(minutes, finishCurrentSong)
                                }
                                showBottomSheet = false
                            },
                        shape = RoundedCornerShape(12.dp),
                        color = when {
                            isSelected && minutes > 0 && !isFinishSongActive -> MaterialTheme.colorScheme.primary.copy(alpha = 0.15f)
                            else -> MaterialTheme.colorScheme.surface
                        },
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 16.dp, vertical = 14.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text(
                                label,
                                style = MaterialTheme.typography.bodyLarge,
                                fontWeight = if (isSelected && minutes > 0) FontWeight.SemiBold else FontWeight.Normal,
                                color = when {
                                    isSelected && minutes > 0 -> MaterialTheme.colorScheme.primary
                                    minutes == 0 && !sleepTimerState.isActive -> MaterialTheme.colorScheme.primary
                                    else -> MaterialTheme.colorScheme.onSurface
                                },
                            )
                            if (isSelected && minutes > 0 && !isFinishSongActive) {
                                Text(
                                    "已选择",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.primary,
                                )
                            }
                        }
                    }
                }

                // Custom minutes input
                var customExpanded by remember { mutableStateOf(false) }
                Surface(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { customExpanded = !customExpanded },
                    shape = RoundedCornerShape(12.dp),
                    color = MaterialTheme.colorScheme.surface,
                ) {
                    Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 14.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text(
                                "✏️ 自定义",
                                style = MaterialTheme.typography.bodyLarge,
                            )
                            Text(
                                if (customExpanded) "▲" else "▼",
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        if (customExpanded) {
                            Spacer(Modifier.height(12.dp))
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                            ) {
                                OutlinedTextField(
                                    value = customMinutesText,
                                    onValueChange = { newValue ->
                                        customMinutesText = newValue.filter { it.isDigit() }
                                    },
                                    label = { Text("分钟") },
                                    singleLine = true,
                                    modifier = Modifier.weight(1f),
                                    shape = RoundedCornerShape(8.dp),
                                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                                )
                                Button(
                                    onClick = {
                                        val mins = customMinutesText.toIntOrNull()
                                        if (mins != null && mins > 0) {
                                            onStartSleepTimer(mins, finishCurrentSong)
                                            showBottomSheet = false
                                        }
                                    },
                                    enabled = (customMinutesText.toIntOrNull() ?: 0) > 0,
                                    shape = RoundedCornerShape(8.dp),
                                ) {
                                    Text("确定")
                                }
                            }
                        }
                    }
                }

                HorizontalDivider(
                    modifier = Modifier.padding(vertical = 8.dp),
                    color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f),
                )

                // Toggle: finish current song
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 8.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            "播完整首歌后关闭",
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.Medium,
                        )
                        Spacer(Modifier.height(2.dp))
                        Text(
                            "倒计时结束后等待当前歌曲播完再暂停",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    Switch(
                        checked = finishCurrentSong,
                        onCheckedChange = { checked ->
                            val current = sleepTimerState
                            if (current.isActive) {
                                onToggleFinishSong()
                            }
                            // If no active timer, this toggle has no effect
                        },
                        enabled = sleepTimerState.isActive,
                        colors = SwitchDefaults.colors(
                            checkedThumbColor = MaterialTheme.colorScheme.onPrimary,
                            checkedTrackColor = MaterialTheme.colorScheme.primary,
                        ),
                    )
                }
            }
        }
    }
}
```

Required additional imports at the top of the file (add alongside existing imports):
```kotlin
import androidx.compose.foundation.clickable
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.ui.text.input.KeyboardOptions
```

Check which are already imported:
- `clickable` — already imported
- `ExperimentalMaterial3Api` — already imported
- `Switch` — already imported
- `SwitchDefaults` — already imported
- `KeyboardOptions` — already imported

Only need to add:
```kotlin
import androidx.compose.material3.ModalBottomSheet
```

- [ ] **Step 4: Commit**

```bash
git add shared/src/commonMain/kotlin/com/example/aimusicplayer/ui/settings/SettingsScreen.kt
git commit -m "feat: add sleep timer section and Bottom Sheet picker to SettingsScreen

Co-Authored-By: Claude Opus 4.8 <noreply@anthropic.com>"
```

---

### Task 6: Build verification

- [ ] **Step 1: Verify compilation**

Run the Kotlin compile check for the shared module:

```bash
./gradlew :shared:compileKotlinMetadata
```

Expected: BUILD SUCCESSFUL, no compilation errors.

- [ ] **Step 2: Commit (if any fixes needed)**

```bash
git add -A
git commit -m "fix: compilation fixes for sleep timer feature

Co-Authored-By: Claude Opus 4.8 <noreply@anthropic.com>"
```
