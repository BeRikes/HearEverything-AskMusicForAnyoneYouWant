# 歌词指针（Lyrics Pointer）— 实现计划

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 在 `LyricsSheet` 中增加指针功能：手动拖拽时屏幕中央出现红色指针（两条横线 + 播放按钮），点击按钮跳转到视口中央行播放。

**Architecture:** 在现有 `LyricsSheet` 的 `LazyColumn` 外包裹 `Box`，叠加一个 `AnimatedVisibility` 驱动的指针层。指针可见性由现有 `userScrolled` 状态控制。点击时实时计算视口中央行索引，回调 `onSeekToLine`。超时从 3 秒改为 5 秒。

**Tech Stack:** Kotlin, Compose Multiplatform, Material3

---

## 文件结构

```
shared/src/commonMain/kotlin/com/example/aimusicplayer/ui/player/
└── NowPlayingOverlay.kt    # [MODIFY] LyricsSheet + NowPlayingOverlay 调用处
```

---

### Task 1: 添加 `onSeekToLine` 参数并贯通调用链

**Files:**
- Modify: `shared/src/commonMain/kotlin/com/example/aimusicplayer/ui/player/NowPlayingOverlay.kt`

- [ ] **Step 1: 在 `LyricsSheet` 函数签名末尾新增参数**

找到第 658-669 行的 `LyricsSheet` 函数签名，在 `isReSearching` 参数后添加：

```kotlin
@Composable
private fun LyricsSheet(
    lyricsText: String,
    currentPosition: Long,
    onDismiss: () -> Unit,
    lyricsOffsetMs: Long = 0L,
    isBilibili: Boolean = false,
    onOffsetChanged: (Long) -> Unit = {},
    onOffsetReset: () -> Unit = {},
    onReSearch: () -> Unit = {},
    isReSearching: Boolean = false,
    onSeekToLine: (Long) -> Unit = {},  // 新增
) {
```

- [ ] **Step 2: 在 `NowPlayingOverlay` 的 `LyricsSheet` 调用处传递 `onSeekToLine`**

找到第 131-142 行调用处，末尾新增一行：

```kotlin
if (showLyrics) {
    LyricsSheet(
        lyricsText = lyricsText ?: "加载中...",
        currentPosition = currentPosition,
        onDismiss = onLyrics,
        lyricsOffsetMs = lyricsOffsetMs,
        isBilibili = currentSong?.platform == "bilibili",
        onOffsetChanged = onLyricsOffsetChanged,
        onOffsetReset = onLyricsOffsetReset,
        onReSearch = onLyricsReSearch,
        isReSearching = isReSearching,
        onSeekToLine = { timestampMs -> onSeek(timestampMs) },  // 新增
    )
}
```

- [ ] **Step 3: 编译验证参数贯通**

```bash
./gradlew :shared:compileReleaseKotlinAndroid
```
Expected: BUILD SUCCESSFUL

---

### Task 2: 超时 3 秒 → 5 秒

**Files:**
- Modify: `shared/src/commonMain/kotlin/com/example/aimusicplayer/ui/player/NowPlayingOverlay.kt`

- [ ] **Step 1: 修改 `delay` 调用**

找到第 706 行的 `delay(3_000)`，改为 `delay(5_000)`：

```kotlin
// 修改前（第 706 行）
delay(3_000)

// 修改后
delay(5_000)
```

- [ ] **Step 2: 编译验证**

```bash
./gradlew :shared:compileReleaseKotlinAndroid
```
Expected: BUILD SUCCESSFUL

---

### Task 3: 添加 `getCenterLineIndex` 工具函数

**Files:**
- Modify: `shared/src/commonMain/kotlin/com/example/aimusicplayer/ui/player/NowPlayingOverlay.kt`

- [ ] **Step 1: 在 `calculateFloatIndex` 函数后、`formatTime` 函数前添加新函数**

找到第 1031 行附近（`calculateFloatIndex` 结束的 `}` 之后，`formatTime` 之前），插入：

```kotlin
/**
 * Find the index of the lyric line closest to the vertical center of the viewport.
 *
 * Used when the user clicks the pointer play button — computes which line the
 * pointer is visually aligned with at that moment.
 *
 * @param listState  Current scroll state of the lyrics [LazyColumn]
 * @param lines      Parsed lyric lines with timestamps
 * @return Index into [lines], or -1 if no visible items
 */
private fun getCenterLineIndex(
    listState: LazyListState,
    lines: List<LyricLine>,
): Int {
    val layoutInfo = listState.layoutInfo
    val viewportCenter = (layoutInfo.viewportStartOffset + layoutInfo.viewportEndOffset) / 2

    return layoutInfo.visibleItemsInfo
        .minByOrNull { item ->
            val itemCenter = item.offset + item.size / 2
            kotlin.math.abs(itemCenter - viewportCenter)
        }
        ?.index ?: -1
}
```

注意：`LazyListState` 类型已在当前文件的 import 中通过 `rememberLazyListState` 间接可用，但函数签名中直接使用 `LazyListState` 需要确认 import。`LazyListState` 在 `androidx.compose.foundation.lazy` 包中。

检查第 31-33 行已有 import：
```kotlin
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
```

需要新增 import：
```kotlin
import androidx.compose.foundation.lazy.LazyListState
```

同时确保 `kotlin.math.abs` 可用（Kotlin 标准库，无需额外 import）。

- [ ] **Step 2: 新增 import**

在第 33 行后添加：

```kotlin
import androidx.compose.foundation.lazy.LazyListState
```

- [ ] **Step 3: 编译验证**

```bash
./gradlew :shared:compileReleaseKotlinAndroid
```
Expected: BUILD SUCCESSFUL

---

### Task 4: 添加指针 UI 层（Box 包裹 LazyColumn + AnimatedVisibility 指针）

**Files:**
- Modify: `shared/src/commonMain/kotlin/com/example/aimusicplayer/ui/player/NowPlayingOverlay.kt`

- [ ] **Step 1: 新增 import — `fadeIn` / `fadeOut`**

在第 3-12 行动画 import 区域，`AnimatedVisibility` 之后添加：

```kotlin
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
```

即：
```kotlin
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn   // 新增
import androidx.compose.animation.fadeOut  // 新增
```

- [ ] **Step 2: 用 `Box` 包裹 LRC 歌词体的 `LazyColumn`，叠加指针层**

找到第 884-936 行的 `if (lines.isNotEmpty())` 块。当前结构为：

```kotlin
            // Lyrics body — LRC with LazyColumn
            if (lines.isNotEmpty()) {
                LazyColumn(
                    state = listState,
                    ...
                ) { ... }
            } else {
```

替换为 `Box` 包裹结构，在 `LazyColumn` 上方叠加指针：

```kotlin
            // Lyrics body — LRC with LazyColumn + pointer overlay
            if (lines.isNotEmpty()) {
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth(),
                ) {
                    LazyColumn(
                        state = listState,
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(horizontal = 24.dp),
                        contentPadding = PaddingValues(vertical = 120.dp),
                    ) {
                        itemsIndexed(lines) { index, line ->
                            val isCurrent = index == currentLineIndex
                            val isPast = index < currentLineIndex

                            if (isCurrent) {
                                // Current line with light background for visibility
                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .background(
                                            MaterialTheme.colorScheme.primary.copy(alpha = 0.12f),
                                            RoundedCornerShape(10.dp),
                                        )
                                        .padding(vertical = 8.dp, horizontal = 4.dp),
                                    contentAlignment = Alignment.Center,
                                ) {
                                    Text(
                                        text = line.text,
                                        style = MaterialTheme.typography.bodyLarge,
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 18.sp,
                                        color = MaterialTheme.colorScheme.primary,
                                        textAlign = TextAlign.Center,
                                    )
                                }
                            } else {
                                Text(
                                    text = line.text,
                                    style = MaterialTheme.typography.bodyLarge,
                                    fontWeight = FontWeight.Normal,
                                    fontSize = 15.sp,
                                    color = if (isPast)
                                        MaterialTheme.colorScheme.onSurface.copy(alpha = 0.35f)
                                    else
                                        MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f),
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(vertical = 8.dp),
                                    textAlign = TextAlign.Center,
                                )
                            }
                        }
                    }

                    // ── Pointer overlay (appears on manual scroll) ─────
                    AnimatedVisibility(
                        visible = userScrolled && lines.isNotEmpty(),
                        enter = fadeIn(tween(200)),
                        exit = fadeOut(tween(200)),
                        modifier = Modifier.align(Alignment.Center),
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 24.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            // Left horizontal line
                            Box(
                                modifier = Modifier
                                    .weight(1f)
                                    .height(1.5.dp)
                                    .background(MaterialTheme.colorScheme.primary),
                            )

                            Spacer(Modifier.width(12.dp))

                            // Play button
                            IconButton(
                                onClick = {
                                    val centerIndex = getCenterLineIndex(listState, lines)
                                    if (centerIndex >= 0 && centerIndex < lines.size) {
                                        onSeekToLine(lines[centerIndex].timestampMs)
                                        userScrolled = false
                                    }
                                },
                                modifier = Modifier
                                    .size(40.dp)
                                    .clip(CircleShape)
                                    .background(MaterialTheme.colorScheme.primary),
                            ) {
                                Icon(
                                    Icons.Default.PlayArrow,
                                    contentDescription = "从此行播放",
                                    tint = MaterialTheme.colorScheme.onPrimary,
                                    modifier = Modifier.size(24.dp),
                                )
                            }

                            Spacer(Modifier.width(12.dp))

                            // Right horizontal line
                            Box(
                                modifier = Modifier
                                    .weight(1f)
                                    .height(1.5.dp)
                                    .background(MaterialTheme.colorScheme.primary),
                            )
                        }
                    }
                }
            } else {
```

- [ ] **Step 3: 编译验证**

```bash
./gradlew :shared:compileReleaseKotlinAndroid
```
Expected: BUILD SUCCESSFUL

---

### Task 5: 最终验证

- [ ] **Step 1: 完整编译**

```bash
./gradlew :shared:compileReleaseKotlinAndroid
```
Expected: BUILD SUCCESSFUL

- [ ] **Step 2: 检查未使用的 import**

编译通过后确认没有未使用的 import。预期新增的 import 全部被使用：
- `androidx.compose.animation.fadeIn`
- `androidx.compose.animation.fadeOut`
- `androidx.compose.foundation.lazy.LazyListState`

---

## 执行顺序

```
Task 1 (参数贯通) → Task 2 (超时) → Task 3 (工具函数) → Task 4 (指针 UI) → Task 5 (验证)
```

Task 1-3 无相互依赖，可并行执行。Task 4 依赖 Task 3（使用 `getCenterLineIndex`）。
