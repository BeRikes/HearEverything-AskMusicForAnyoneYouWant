# 歌词指针（Lyrics Pointer）— 设计文档

**日期:** 2026-06-06
**状态:** 已批准
**范围:** `LyricsSheet` 组件（`NowPlayingOverlay.kt`）

## 背景

当前歌词界面支持手动拖拽浏览歌词，3 秒无操作后自动恢复跟随播放位置。但用户手动浏览时无法直接"从这一行开始播放"——必须回到进度条手动拖拽，体验割裂。

本功能在手动浏览时提供一个屏幕中央的视觉指针 + 播放按钮，用户对准目标行后点击即可跳转播放。

## 目标

1. 用户手动拖拽歌词时，屏幕中央出现指针（红色横线 + 播放按钮）
2. 点击播放按钮 → 实时计算视口中央对应的歌词行 → seek 到该行时间戳并播放
3. 指针在停止拖拽 5 秒后自动消失，或点击播放后立即消失
4. 不影响现有的自动滚动、行高亮、LRC 解析等逻辑

## 设计

### 核心数据流

```
用户拖拽 LazyColumn
  → listState.isScrollInProgress = true
  → firstVisibleItemIndex != currentLineIndex
  → userScrolled = true （现有逻辑）
  → 指针渲染为可见

用户停止拖拽
  → 5 秒计时开始（从现有 3 秒改为 5 秒）
  → 超时后 userScrolled = false
  → 指针消失，自动滚动恢复

用户点击播放按钮
  → 读取 listState.layoutInfo，计算视口中心点
  → 找到中心点对应的歌词行索引
  → 调用 onSeekToLine(line.timestampMs)
  → userScrolled = false，指针消失
  → 自动滚动恢复
```

### 视口中央行计算（点击时实时计算）

```kotlin
fun getCenterLineIndex(listState: LazyListState, lines: List<LyricLine>): Int {
    val layoutInfo = listState.layoutInfo
    val viewportCenter = (layoutInfo.viewportStartOffset + layoutInfo.viewportEndOffset) / 2
    
    return layoutInfo.visibleItemsInfo
        .minByOrNull { item ->
            val itemCenter = item.offset + item.size / 2
            abs(itemCenter - viewportCenter)
        }
        ?.index ?: -1
}
```

### 指针 UI 布局

指针是覆盖在 `LazyColumn` 之上的固定层，始终在视口垂直中央：

```
┌──────────────────────────────────┐
│          (歌词内容区域)           │
│                                  │
│   已唱过的行 (透明度 0.35)        │
│   已唱过的行                      │
│   ────────────────  ▶  ───────── │  ← 指针层（固定中央）
│   待唱行 (透明度 0.70)            │
│   待唱行                          │
│                                  │
└──────────────────────────────────┘
```

- 两条水平线（`1.5dp` 粗，`primary` 色）分别在上方和下方
- 中间偏右一个圆形播放按钮（`primary` 色背景，白色 ▶ 图标）
- 整体半透明，不遮挡歌词文字

用 `Box` 叠加：`LazyColumn` + 条件渲染的指针 `Row`。

### 参数变更

**`LyricsSheet` 签名新增：**

```kotlin
@Composable
private fun LyricsSheet(
    // ... 现有参数保持不变 ...
    onSeekToLine: (Long) -> Unit = {},  // 新增：点击指针播放按钮时回调
)
```

**`NowPlayingOverlay` 调用处：**
```kotlin
LyricsSheet(
    // ... 现有参数 ...
    onSeekToLine = { timestampMs -> onSeek(timestampMs) },
)
```

### 超时调整

现有一处修改：
- `LyricsSheet` 中 `LaunchedEffect(listState.isScrollInProgress)` 内的 `delay(3_000)` → `delay(5_000)`

### 动画

指针出现/消失使用 `AnimatedVisibility` + `fadeIn/fadeOut`（`tween(200ms)`），避免突兀闪现。

### 边界情况

| 场景 | 处理 |
|------|------|
| `lines` 为空 | 指针不渲染（userScrolled 在这些场景下不会为 true，但加防御判断） |
| 点击播放时视口无可见行 | 忽略点击，指针不消失 |
| 指针可见时用户继续拖拽 | 5 秒计时重置（现有 LaunchedEffect 已处理） |
| 指针可见时播放到新行 | 不冲突 — 高亮仍跟踪 currentLineIndex（可能被滚出视口），指针独立显示 |
| 纯文本歌词（无 LRC 时间戳） | 指针仍出现，但 `lines` 列表为空（无时间戳行可解析）。点击播放按钮时 `getCenterLineIndex` 返回 -1，忽略点击 |
| 快速连续点击播放按钮 | 取最后一次点击的计算结果 |

### 改动范围

- **文件:** `shared/src/commonMain/kotlin/com/example/aimusicplayer/ui/player/NowPlayingOverlay.kt`
  - `LyricsSheet` 函数签名：新增 `onSeekToLine` 参数
  - `LyricsSheet` 函数体：
    - LazyColumn 外包一层 `Box`，叠加指针层
    - 新增指针 UI（`AnimatedVisibility` + 横线 + 播放按钮）
    - 新增 `getCenterLineIndex()` 工具函数
    - 点击播放按钮的处理逻辑
    - 超时 `delay(3_000)` → `delay(5_000)`
  - `NowPlayingOverlay` 中 `LyricsSheet` 调用处：传递 `onSeekToLine`

### 保持不变

- LRC 解析器（`parseLrcToLines`）
- `calculateFloatIndex` 函数
- 自动滚动逻辑（`LaunchedEffect(currentLineIndex, autoScrollTick)`）
- 当前行视觉高亮
- 手动滚动检测逻辑（仅超时值改变）
- B站偏移控制 UI
- 纯文本回退 LazyColumn
