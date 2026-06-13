# 歌词平滑滚动 — 实现计划

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 将歌词滚动从"行索引变化时 animateScrollToItem 跳转"改为"播放位置驱动的小数行索引 scrollToItem 连续贴位"，消除行切换跳跃感。

**Architecture:** 仅在 `LyricsSheet` 函数内改动 — 新增 `calculateFloatIndex()` 辅助函数，将现有 `LaunchedEffect(currentLineIndex)` 离散触发改为 `LaunchedEffect(adjustedPosition)` 响应式连续驱动。滚动方法从 `animateScrollToItem` 换为 `scrollToItem`（snap），利用 ~250ms 的 position 更新频率本身实现视觉连续性。

**Tech Stack:** Kotlin, Compose Multiplatform, LazyColumn

---

## 文件结构

```
shared/src/commonMain/kotlin/com/example/aimusicplayer/ui/player/
└── NowPlayingOverlay.kt    # [MODIFY] LyricsSheet + 新增 calculateFloatIndex
```

---

### Task 1: 添加 calculateFloatIndex 函数

**Files:**
- Modify: `shared/src/commonMain/kotlin/com/example/aimusicplayer/ui/player/NowPlayingOverlay.kt` — 在 `parseLrcToLines` 之后新增函数

- [ ] **Step 1: 在 parseLrcToLines 函数之后、formatTime 函数之前插入 calculateFloatIndex**

定位到文件末尾 `parseLrcToLines` 函数的闭括号 `}`（约第997行）之后，`// ── Time formatting` 注释之前，插入以下代码：

```kotlin
/**
 * Calculate a continuous "float index" from playback position for smooth scrolling.
 *
 * Returns a [Double] where the integer part is the current lyric line index and
 * the fractional part represents progress through that line (0.0–1.0).
 *
 * For example, 3.5 means the current line is index 3, and playback has reached
 * 50% of the time window between line 3 and line 4.
 *
 * @param lines  Sorted list of parsed lyric lines (must be non-empty)
 * @param positionMs  Adjusted playback position in milliseconds
 * @return Float index clamped to [0.0, lines.lastIndex]
 */
private fun calculateFloatIndex(
    lines: List<LyricLine>,
    positionMs: Long,
): Double {
    if (lines.isEmpty()) return 0.0
    if (lines.size == 1 || positionMs <= lines.first().timestampMs) return 0.0
    if (positionMs >= lines.last().timestampMs) return (lines.lastIndex).toDouble()

    for (i in 0 until lines.lastIndex) {
        val t1 = lines[i].timestampMs
        val t2 = lines[i + 1].timestampMs
        if (positionMs >= t1 && positionMs < t2) {
            val duration = t2 - t1
            val elapsed = positionMs - t1
            val progress = if (duration > 0) elapsed.toDouble() / duration.toDouble() else 0.0
            return i + progress.coerceIn(0.0, 1.0)
        }
    }
    return (lines.lastIndex).toDouble()
}
```

---

### Task 2: 重构 LyricsSheet 滚动逻辑

**Files:**
- Modify: `shared/src/commonMain/kotlin/com/example/aimusicplayer/ui/player/NowPlayingOverlay.kt:657-715` — LyricsSheet 函数内部的滚动相关代码块

- [ ] **Step 1: 替换 currentLineIndex 计算和 LaunchedEffect 滚动逻辑**

删除当前第674-714行的以下代码块：
```kotlin
    // Find current line index from adjusted playback position
    val currentLineIndex = remember(adjustedPosition, lines.size) {
        if (lines.isEmpty()) -1
        else {
            var idx = -1
            for (i in lines.indices) {
                if (lines[i].timestampMs <= adjustedPosition) idx = i else break
            }
            idx
        }
    }

    // Auto-scroll to keep the current line in upper-middle area
    // Scroll to current line when it changes; user manual scroll resets after 3s
    var userScrolled by remember { mutableStateOf(false) }
    var autoScrollTick by remember { mutableStateOf(0) }

    // Auto-scroll trigger: currentLineIndex change OR 3s after user stops scrolling
    LaunchedEffect(currentLineIndex, autoScrollTick) {
        if (currentLineIndex >= 0 && lines.isNotEmpty()) {
            val viewportHeight = listState.layoutInfo.viewportEndOffset -
                listState.layoutInfo.viewportStartOffset
            // Position current line in upper-middle (~1/3 from top)
            val offset = -(viewportHeight / 3) + 60
            listState.animateScrollToItem(currentLineIndex, offset)
            userScrolled = false
        }
    }

    // Detect manual scrolls: if user drags away from current line, mark and start reset timer
    LaunchedEffect(listState.isScrollInProgress) {
        if (!listState.isScrollInProgress && userScrolled) {
            delay(3_000)
            autoScrollTick++ // trigger re-scroll
        }
    }
    // Watch for manual scroll (isScrollInProgress + not at current position)
    if (currentLineIndex >= 0 && listState.isScrollInProgress &&
        listState.firstVisibleItemIndex != currentLineIndex) {
        userScrolled = true
    }
```

替换为：
```kotlin
    // ── Float-index calculation for smooth continuous scrolling ─────
    val floatIndex = remember(adjustedPosition, lines.size) {
        calculateFloatIndex(lines, adjustedPosition)
    }
    val currentLineIndex = floatIndex.toInt()

    // Estimate line height for fractional scroll offset (dp → px)
    val estimatedLineHeightPx = with(LocalDensity.current) { 48.dp.toPx() }

    // ── Manual scroll detection ─────────────────────────────────────
    var userScrolled by remember { mutableStateOf(false) }
    var autoScrollTick by remember { mutableStateOf(0) }

    // Auto-scroll: continuous position-driven (runs on every position update)
    LaunchedEffect(adjustedPosition, autoScrollTick) {
        if (!userScrolled && floatIndex >= 0.0 && lines.isNotEmpty()) {
            val viewportHeight = listState.layoutInfo.viewportEndOffset -
                listState.layoutInfo.viewportStartOffset
            if (viewportHeight > 0) {
                val fractionalPart = floatIndex - floatIndex.toInt()
                // Fraction of current line already "scrolled through"
                val lineOffset = (fractionalPart * estimatedLineHeightPx).toInt()
                // Viewport offset: position the current line ~1/3 from the top
                val viewportOffset = -(viewportHeight / 3) + 60
                listState.scrollToItem(currentLineIndex, lineOffset + viewportOffset)
            }
        }
    }

    // Detect manual scrolls: if user drags away, mark and start reset timer
    LaunchedEffect(listState.isScrollInProgress) {
        if (!listState.isScrollInProgress && userScrolled) {
            delay(3_000)
            autoScrollTick++ // trigger re-scroll
        }
    }
    if (currentLineIndex >= 0 && listState.isScrollInProgress &&
        listState.firstVisibleItemIndex != currentLineIndex) {
        userScrolled = true
    }
```

- [ ] **Step 2: 确保 import 完整**

检查文件顶部 import 区域，确认 `LocalDensity` 已导入。如果没有，添加：
```kotlin
import androidx.compose.ui.platform.LocalDensity
```

`dp` 已经有 `import androidx.compose.ui.unit.dp`，`scrollToItem` 是 `LazyListState` 的方法无需额外导入。

---

### Task 3: 验证 Gradle 编译

**Files:** 无新建

- [ ] **Step 1: 运行 Gradle 编译检查**

```bash
./gradlew :shared:compileKotlinAndroid
```
Expected: BUILD SUCCESSFUL

- [ ] **Step 2: 修复编译错误（如有）**

常见问题检查清单：
- `LocalDensity` 是否已 import → 如缺少，添加 `import androidx.compose.ui.platform.LocalDensity`
- `scrollToItem` 需要 `LazyListState` 实例（已有 `listState`）
- `floatIndex` 的类型是 `Double`（`remember` 正确推断）
- `calculateFloatIndex` 函数是否放在 `private` 可见性且位于文件顶层

- [ ] **Step 3: 编译通过后提交**

```bash
git add shared/src/commonMain/kotlin/com/example/aimusicplayer/ui/player/NowPlayingOverlay.kt
git commit -m "feat: smooth continuous lyrics scrolling driven by playback position

Replace discrete animateScrollToItem (triggered by line index change) with
continuous scrollToItem (triggered by every position update). New
calculateFloatIndex() maps playback time to a fractional line index for
sub-line scroll precision.

Co-Authored-By: Claude Opus 4.8 <noreply@anthropic.com>"
```

---

## 执行顺序

Tasks MUST run in order: 1 → 2 → 3

Task 1 添加纯函数（无依赖），Task 2 使用该函数重构滚动逻辑，Task 3 验证。
