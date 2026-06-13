# 歌词以行为单位平滑滚动 v2 — 实现计划

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 回退 v1 的 Column+verticalScroll+scrollTo，恢复 LazyColumn+animateScrollToItem，以行为单位触发 600ms 平滑滚动动画。

**Architecture:** 移除逐像素 snap 的 while(isActive) 循环，回到 LaunchedEffect(currentLineIndex, autoScrollTick) 行级触发。用 `animateScrollToItem(index, offset, tween(600ms, FastOutSlowInEasing))` 替代 `scrollState.scrollTo()`。高亮由 `currentLineIndex` 驱动立即响应，不受动画影响。

**Tech Stack:** Kotlin, Compose Multiplatform, LazyColumn

---

## 文件结构

```
shared/src/commonMain/kotlin/com/example/aimusicplayer/ui/player/
└── NowPlayingOverlay.kt    # [MODIFY] LyricsSheet 函数内滚动逻辑区
```

---

### Task 1: 回退滚动实现 + 调整动画参数

**Files:**
- Modify: `shared/src/commonMain/kotlin/com/example/aimusicplayer/ui/player/NowPlayingOverlay.kt` — LyricsSheet 函数

- [ ] **Step 1: 替换 import**

移除不再需要的 import，添加需要的：

```diff
- import androidx.compose.animation.core.FastOutSlowInEasing
+ import androidx.compose.animation.core.FastOutSlowInEasing

- import androidx.compose.foundation.rememberScrollState
- import androidx.compose.foundation.verticalScroll
- import androidx.compose.runtime.mutableIntStateOf
- import androidx.compose.ui.layout.onSizeChanged
+ import androidx.compose.foundation.lazy.rememberLazyListState
```

- [ ] **Step 2: 替换 LyricsSheet 滚动逻辑块（第675-754行）**

读取文件确认当前代码位置，然后将以下代码块整体替换：

**删除从 `val scrollState = rememberScrollState()` 到手动检测结束的所有代码块**，替换为：

```kotlin
    val listState = rememberLazyListState()

    // ── Float-index for line highlight (immediate) ──────────────────
    val floatIndex = remember(adjustedPosition, lines.size) {
        calculateFloatIndex(lines, adjustedPosition)
    }
    val currentLineIndex = floatIndex.toInt()

    // ── Manual scroll detection ─────────────────────────────────────
    var userScrolled by remember { mutableStateOf(false) }
    var autoScrollTick by remember { mutableStateOf(0) }

    // Auto-scroll: triggered ONLY when the integer line index changes
    LaunchedEffect(currentLineIndex, autoScrollTick) {
        if (currentLineIndex >= 0 && lines.isNotEmpty()) {
            val viewportHeight = listState.layoutInfo.viewportEndOffset -
                listState.layoutInfo.viewportStartOffset
            if (viewportHeight > 0) {
                // Fixed anchor: current line ~1/3 from top
                val offset = -(viewportHeight / 3) + 60
                listState.animateScrollToItem(
                    index = currentLineIndex,
                    scrollOffset = offset,
                    animationSpec = tween(
                        durationMillis = 600,
                        easing = FastOutSlowInEasing,
                    ),
                )
            }
            userScrolled = false
        }
    }

    // Detect manual scrolls: if user drags away, mark and start reset timer
    LaunchedEffect(listState.isScrollInProgress) {
        if (!listState.isScrollInProgress && userScrolled) {
            delay(3_000)
            userScrolled = false
            autoScrollTick++ // trigger re-scroll via LaunchedEffect restart
        }
    }
    if (currentLineIndex >= 0 && listState.isScrollInProgress &&
        listState.firstVisibleItemIndex != currentLineIndex) {
        userScrolled = true
    }
```

- [ ] **Step 3: 替换 Column+verticalScroll 歌词体为 LazyColumn（第924-982行）**

将 `if (lines.isNotEmpty())` 块内的 `Column`（含 `onSizeChanged` + `verticalScroll` + `forEachIndexed`）替换为 LazyColumn：

```kotlin
            if (lines.isNotEmpty()) {
                LazyColumn(
                    state = listState,
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth()
                        .padding(horizontal = 24.dp),
                    contentPadding = PaddingValues(vertical = 120.dp),
                ) {
                    itemsIndexed(lines) { index, line ->
                        val isCurrent = index == currentLineIndex
                        val isPast = index < currentLineIndex

                        if (isCurrent) {
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
            } else {
```

- [ ] **Step 4: 移除 viewportHeightPx、isResuming、rememberUpdatedState 相关代码**

删除这些不再使用的声明（约在 scrollState 之后）：
```kotlin
var viewportHeightPx by remember { mutableIntStateOf(0) }  // 删除
var isResuming by remember { mutableStateOf(false) }        // 删除
val currentAdjustedPosition by rememberUpdatedState(...)    // 删除
val currentLines by rememberUpdatedState(lines)              // 删除
```

- [ ] **Step 5: 编译验证**

```bash
./gradlew :shared:compileReleaseKotlinAndroid
```
Expected: BUILD SUCCESSFUL

- [ ] **Step 6: 清理未使用的 import**

编译通过后，检查并移除以下不再需要的 import：
- `import kotlinx.coroutines.isActive`（不再使用 while(isActive)）
- `import androidx.compose.runtime.mutableIntStateOf`
- `import androidx.compose.runtime.rememberUpdatedState`（如果文件其他地方不用）
- `import androidx.compose.foundation.rememberScrollState`
- `import androidx.compose.foundation.verticalScroll`
- `import androidx.compose.ui.layout.onSizeChanged`

---

## 执行顺序

仅一个 Task，无依赖。
