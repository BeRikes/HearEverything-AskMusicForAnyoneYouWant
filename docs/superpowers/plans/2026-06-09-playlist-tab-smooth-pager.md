# Playlist Tab Smooth Pager Animation — Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Replace abrupt `when(selectedTab)` tab switching in PlaylistScreen with HorizontalPager-driven continuous sliding + animated tab indicator.

**Architecture:** HorizontalPager owns page state with `rememberPagerState(3)`. Bidirectional sync via LaunchedEffects: pager → ViewModel via `snapshotFlow`, ViewModel → pager via `animateScrollToPage`. A sliding indicator Bar composites below the tab chips, using `derivedStateOf` to interpolate its X offset from `pagerState.currentPageOffsetFraction`. Tab chip clicks update ViewModel immediately (instant highlight) and let the LaunchedEffect drive the pager animation. The old `detectHorizontalDragGestures` block is removed.

**Tech Stack:** Compose Multiplatform 1.7.3, Kotlin 2.1.20

---

## File Structure Map

```
shared/src/commonMain/kotlin/com/example/aimusicplayer/
├── ui/playlist/
│   ├── PlaylistScreen.kt      # [MODIFY] HorizontalPager + indicator + sync
│   └── PlaylistViewModel.kt   # [MODIFY] selectTab equality guard
```

---

### Task 1: Add equality guard to ViewModel.selectTab

**Files:**
- Modify: `shared/src/commonMain/kotlin/com/example/aimusicplayer/ui/playlist/PlaylistViewModel.kt:77`

Prevents redundant StateFlow emissions when the pager syncs the same page back.

- [ ] **Step 1: Add equality guard**

Replace line 77:
```kotlin
fun selectTab(index: Int) { _selectedTab.value = index }
```

With:
```kotlin
fun selectTab(index: Int) {
    if (_selectedTab.value != index) _selectedTab.value = index
}
```

- [ ] **Step 2: Commit**

```bash
git add shared/src/commonMain/kotlin/com/example/aimusicplayer/ui/playlist/PlaylistViewModel.kt
git commit -m "fix: add equality guard to PlaylistViewModel.selectTab

Prevents redundant StateFlow emissions when pager syncs the
same page back, avoiding unnecessary recompositions.

Co-Authored-By: Claude Opus 4.8 <noreply@anthropic.com>"
```

---

### Task 2: Refactor PlaylistScreen with HorizontalPager + sliding indicator

**Files:**
- Modify: `shared/src/commonMain/kotlin/com/example/aimusicplayer/ui/playlist/PlaylistScreen.kt`

This is the main change. We replace the `pointerInput` drag gesture + `when(selectedTab)` content switch with a `HorizontalPager` and add a synchronized sliding indicator.

- [ ] **Step 1: Replace imports (lines 1-58)**

Remove:
```kotlin
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
```

Add (keep all other imports, insert in alphabetical order):
```kotlin
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.ui.util.lerp
```

- [ ] **Step 2: Replace PlaylistScreen composable (lines 60-149)**

Replace the entire `PlaylistScreen` composable function (lines 60-149) with:

```kotlin
@Composable
fun PlaylistScreen(
    viewModel: PlaylistViewModel,
    onNavigateToSearch: () -> Unit,
    currentSongId: String? = null,
    isPlaying: Boolean = false,
    modifier: Modifier = Modifier,
) {
    val selectedTab by viewModel.selectedTab.collectAsState()
    val playlist by viewModel.playlist.collectAsState()
    val history by viewModel.history.collectAsState()
    val playMode by viewModel.playMode.collectAsState()

    val pagerState = rememberPagerState(pageCount = 3) { selectedTab }

    // Sync 1: Pager → ViewModel (swipe gestures)
    LaunchedEffect(pagerState) {
        snapshotFlow { pagerState.currentPage }
            .collect { page -> viewModel.selectTab(page) }
    }

    // Sync 2: ViewModel → Pager (tab chip clicks)
    LaunchedEffect(selectedTab) {
        if (selectedTab != pagerState.currentPage) {
            pagerState.animateScrollToPage(selectedTab)
        }
    }

    // Refresh data on page change
    LaunchedEffect(pagerState.currentPage) { viewModel.refresh() }

    // ── Tab indicator state ──
    val tabCenters = remember { mutableStateListOf(0f, 0f, 0f) }
    val density = LocalDensity.current
    val indicatorWidthDp = 40.dp
    val indicatorHalfWidthPx = with(density) { indicatorWidthDp.toPx() / 2f }
    val indicatorCenterX by remember {
        derivedStateOf {
            if (tabCenters.any { it == 0f }) return@derivedStateOf 0f
            val nextPage = (pagerState.currentPage + 1).coerceIn(0, 2)
            val fraction = pagerState.currentPageOffsetFraction
            lerp(tabCenters[pagerState.currentPage], tabCenters[nextPage], fraction)
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
                Spacer(Modifier.width(36.dp))
                TabChip(
                    label = "历史歌单",
                    selected = selectedTab == 1,
                    onClick = { viewModel.selectTab(1) },
                    modifier = Modifier.onGloballyPositioned { coords ->
                        tabCenters[1] = coords.positionInParent().x + coords.size.width / 2f
                    },
                )
                Spacer(Modifier.width(36.dp))
                TabChip(
                    label = "导入歌单",
                    selected = selectedTab == 2,
                    onClick = { viewModel.selectTab(2) },
                    modifier = Modifier.onGloballyPositioned { coords ->
                        tabCenters[2] = coords.positionInParent().x + coords.size.width / 2f
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
                2 -> ImportTab(
                    viewModel = viewModel,
                    modifier = Modifier.fillMaxSize(),
                )
            }
        }
    }
}
```

- [ ] **Step 2b: Update TabChip signature to accept modifier parameter (lines 152-172)**

Replace the `TabChip` composable:

```kotlin
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
            .padding(horizontal = 14.dp, vertical = 6.dp),
    )
}
```

- [ ] **Step 3: Commit**

```bash
git add shared/src/commonMain/kotlin/com/example/aimusicplayer/ui/playlist/PlaylistScreen.kt
git commit -m "feat: replace tab switching with HorizontalPager + sliding indicator

- Replace when(selectedTab) with HorizontalPager for 1:1 finger tracking
- Add bidirectional PagerState ↔ ViewModel sync via LaunchedEffects
- Add animated sliding indicator under tab chips driven by currentPageOffsetFraction
- Remove old detectHorizontalDragGestures block
- TabChip now accepts modifier parameter for onGloballyPositioned measurement

Co-Authored-By: Claude Opus 4.8 <noreply@anthropic.com>"
```

---

### Task 3: Build and verify

- [ ] **Step 1: Run Gradle compilation check**

```bash
cd D:/Download/claude_demo/HearEverything && ./gradlew :shared:compileKotlinAndroid 2>&1 | tail -20
```

Expected: BUILD SUCCESSFUL. If compilation errors, fix and re-run before proceeding.

- [ ] **Step 2: Review final file for correctness**

Open `shared/src/commonMain/kotlin/com/example/aimusicplayer/ui/playlist/PlaylistScreen.kt` and verify:
- No remaining references to `detectHorizontalDragGestures`
- `HorizontalPager` and `rememberPagerState` are imported
- `derivedStateOf` + `snapshotFlow` usage looks correct
- `TabChip` has `modifier` parameter

- [ ] **Step 3: Commit verification**

```bash
cd D:/Download/claude_demo/HearEverything && git log --oneline -3
```

Expected: Two commits visible (Task 1 equality guard, Task 2 pager refactor).

---

## Execution Order

Tasks MUST run in order: 1 → 2 → 3

Task 1 is a prerequisite guard. Task 2 is the main change. Task 3 verifies.
