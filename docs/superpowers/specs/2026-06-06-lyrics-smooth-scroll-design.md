# 歌词平滑滚动动画 — 设计文档

**日期:** 2026-06-06
**状态:** 已批准
**范围:** `LyricsSheet` 组件（`NowPlayingOverlay.kt` 第657-962行）

## 目标

模仿网易云音乐歌词界面的平滑滚动行为，消除当前实现中的"行切换跳跃感"。

## 当前问题

当前实现基于离散的行索引变化触发滚动：

```kotlin
val currentLineIndex = findIndex(adjustedPosition)  // 整数，仅在跨行时变化
LaunchedEffect(currentLineIndex) {
    listState.animateScrollToItem(currentLineIndex, offset)
}
```

当播放位置在同一行内（如从 line[3] 的 30% 走到 80%）时，`currentLineIndex` 不变 → 不触发任何滚动 → 歌词视觉上完全静止，直到进入下一行才"跳"一下。

## 新设计：播放时间驱动的连续滚动

### 核心思路

用播放时间的**连续变化**替代行索引的**离散变化**来驱动滚动位置。

### 数据流

```
currentPosition (每 ~250ms 更新)
    │
    ▼
calculateFloatIndex(lines, adjustedPosition)
    │  → 返回 Double: 整数部分=行号, 小数部分=行内进度
    ▼
floatIndex → scrollToItem(idx, pixelOffset)
    │  → snap 到位（不带动画，高频调用本身就是平滑的）
    ▼
LazyColumn 显示位置随播放时间连续移动
```

### 关键计算：小数行索引

```kotlin
fun calculateFloatIndex(lines: List<LyricLine>, positionMs: Long): Double {
    if (lines.isEmpty()) return 0.0
    if (lines.size == 1 || positionMs <= lines.first().timestampMs) return 0.0
    if (positionMs >= lines.last().timestampMs) return (lines.lastIndex).toDouble()

    for (i in 0 until lines.lastIndex) {
        if (positionMs >= lines[i].timestampMs && positionMs < lines[i + 1].timestampMs) {
            val progress = (positionMs - lines[i].timestampMs).toDouble() /
                           (lines[i + 1].timestampMs - lines[i].timestampMs).toDouble()
            return i + progress.coerceIn(0.0, 1.0)
        }
    }
    return (lines.lastIndex).toDouble()
}
```

### 滚动定位

```kotlin
val floatIndex = calculateFloatIndex(lines, adjustedPosition)
val itemIndex = floatIndex.toInt()
val itemFraction = floatIndex - itemIndex
// lineHeight: 每行实际像素高度，通过 layoutInfo 或固定值估算
val scrollOffset = (itemFraction * lineHeight).toInt() - viewportOffset
listState.scrollToItem(itemIndex, scrollOffset)
```

### 手动滚动检测（保留现有逻辑）

- 检测 `listState.isScrollInProgress` 且 `firstVisibleItemIndex != currentLineIndex`
- 用户手动滚动后暂停自动定位，3秒无操作后恢复
- 恢复时直接跳回当前播放位置对应的 scrollToItem

### 边界情况

| 场景 | 处理 |
|------|------|
| `lines` 为空 | 不执行任何滚动逻辑 |
| `lines.size == 1` | floatIndex = 0.0，固定顶部 |
| position 在第一行之前 | floatIndex = 0.0 |
| position 在最后一行之后 | floatIndex = lines.lastIndex |
| 无LRC时间戳（纯文本） | 保持现有回退逻辑 |
| `lineHeight` 未知 | 首次布局使用估算值 (48dp)，layoutInfo 就绪后使用实际值 |

### 滚动更新频率

使用 `LaunchedEffect(currentPosition)` 响应式驱动，而非独立帧循环：
- `currentPosition` 本身每 ~250ms 更新一次，更新频率足够
- 每次更新直接 `scrollToItem`（snap，不带动画），高频 snap = 视觉效果连续
- 无需额外定时器，避免资源浪费

### 改动范围

- **文件:** `shared/src/commonMain/kotlin/com/example/aimusicplayer/ui/player/NowPlayingOverlay.kt`
- **函数:** `LyricsSheet` (第657-962行)
- **新增函数:** `calculateFloatIndex()` (私有，~20行)
- **不改动:** LRC解析器、当前行高亮样式、手动滚动检测逻辑、B站偏移控制UI、纯文本回退、LazyColumn结构

### 保持不变

- LazyColumn + itemsIndexed 结构
- 当前行视觉高亮（背景 + 粗体 + 主色文字）
- 已播放行/未播放行透明度区分
- 手动滚动检测和3秒自动恢复
- 偏移量滑块控制（B站功能）
- parseLrcToLines 解析器
- 1/3视口偏移量
