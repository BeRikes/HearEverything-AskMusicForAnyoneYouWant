# 歌词以行为单位平滑滚动 — 设计文档 v2

**日期:** 2026-06-06
**状态:** 已批准
**范围:** `LyricsSheet` 组件（`NowPlayingOverlay.kt` 第657行起）

## 背景

v1 方案尝试用逐像素连续滚动模仿网易云效果，但方向错误：
- `scrollTo` 每 250ms snap → 阻滞感
- 同一行内持续微动 → 视觉不安定
- `Column + verticalScroll` 替换 `LazyColumn` → 过度重构

v2 回归正确基础：**以行为单位触发滚动，动画本身平滑流畅**。

## 目标

1. 当前行变化时，**600ms 平滑动画**滚动到固定锚点位置
2. 同一行内**完全静止**，不随播放位置微动
3. 行高亮（字体 + 背景框）**立即响应**行号变化（不等动画完成）
4. 当前行始终停在屏幕**中偏上位置**（视口 1/3 处）
5. 用户手动滚动后 3s 无操作则**平滑恢复**

## 设计

### 核心数据流

```
currentPosition 更新
    │
    ▼
calculateFloatIndex() → 小数行号（如 3.5）
    │
    ▼
currentLineIndex = floatIndex.toInt()（整数部分）
    │
    ├── 立即 → 高亮 index == currentLineIndex 的行
    │
    └── currentLineIndex 变化时 → LaunchedEffect 触发
            │
            ▼
        animateScrollToItem(currentLineIndex, fixedOffset,
            tween(600ms, FastOutSlowInEasing))
            │
            ▼
        600ms 平滑动画 → 当前行停在视口 1/3 处
```

### 关键参数

| 参数 | 值 | 说明 |
|------|-----|------|
| 锚点偏移 | `-(viewportHeight / 3) + 60` | 当前行在视口上 1/3 处 |
| 动画时长 | 600ms | 人眼感知流畅但不拖沓 |
| 缓动函数 | `FastOutSlowInEasing` | 开始快结束慢，自然减速 |
| 高亮响应 | **立即**（不等待动画） | `currentLineIndex` 更新即触发 recompose |

### 技术方案：回退 + 修正

从 v1 的 `Column + verticalScroll + scrollTo` 回到 `LazyColumn + animateScrollToItem`：

- **移除 `rememberScrollState`、`verticalScroll`、`onSizeChanged`、`mutableIntStateOf`**
- **移除 `while(isActive)` 循环、`scrollTo`、`isResuming` 标志**
- **恢复 `rememberLazyListState`**
- **恢复 `LaunchedEffect(currentLineIndex, autoScrollTick)`** 作为滚动触发器
- **将 `animateScrollToItem` 的动画参数改为 `tween(600ms, FastOutSlowInEasing)`**
- **保留 `calculateFloatIndex`**（仅用于计算 `currentLineIndex = floatIndex.toInt()`）
- **保留手动滚动检测**（基于 `firstVisibleItemIndex != currentLineIndex`）
- **保留 3s 自动恢复**（`autoScrollTick` 触发重新滚动）

### 手动滚动检测（恢复原始逻辑）

```kotlin
// 手动拖拽检测
if (currentLineIndex >= 0 && listState.isScrollInProgress &&
    listState.firstVisibleItemIndex != currentLineIndex) {
    userScrolled = true
}

// 3s 后恢复
LaunchedEffect(listState.isScrollInProgress) {
    if (!listState.isScrollInProgress && userScrolled) {
        delay(3_000)
        userScrolled = false
        autoScrollTick++
    }
}
```

### 高亮立即响应

`currentLineIndex = floatIndex.toInt()` 是 Compose state，行号变化时立即触发 recompose。
`itemsIndexed` 中 `index == currentLineIndex` 条件即时生效，不等滚动动画完成。

### 边界情况

| 场景 | 处理 |
|------|------|
| `lines` 为空 | 不触发滚动 |
| `lines.size == 1` | currentLineIndex = 0，无滚动 |
| position 在第一行之前 | currentLineIndex = 0 |
| position 在最后一行之后 | currentLineIndex = lastIndex |
| 同一行内播放 | **完全不滚**，保持静止 |
| 连续快速跨行（拖进度条） | 最后一次触发动画到最新位置 |
| 无 LRC 时间戳 | 保持现有纯文本回退，不动 |

### 改动范围

- **文件:** `shared/src/commonMain/kotlin/com/example/aimusicplayer/ui/player/NowPlayingOverlay.kt`
- **函数:** `LyricsSheet` — 滚动逻辑区（约50行）
- **import 调整:** 移除 `verticalScroll`/`rememberScrollState`/`onSizeChanged`/`mutableIntStateOf`/`FastOutSlowInEasing`；恢复 `rememberLazyListState`

### 保持不变

- LRC 解析器（`parseLrcToLines`）
- `calculateFloatIndex` 函数
- 当前行视觉高亮（背景 + 粗体 + 主色）
- 已播放/未播放行透明度
- B站偏移控制 UI
- 纯文本回退 LazyColumn
- `contentPadding` 上下 120dp
