# UI Polish — 2026-06-08

## 1. 导入歌单详情页 "加入歌单" 按钮

**文件**：`shared/.../ui/playlist/ImportTab.kt` → `ImportedPlaylistDetailView`

每首已匹配歌曲行右侧，关闭按钮左侧，增加 `+` 按钮：

```
[序号] [歌名 / 歌手]   [+ 加入歌单] [✕ 移除]
```

要点：
- 图标：`Icons.Default.Add`，tint = `MaterialTheme.colorScheme.primary`
- 尺寸：`IconButton(Modifier.size(36.dp))`，图标 18dp，与关闭按钮一致
- 仅 `hasMatch == true` 时显示
- 点击时构造 `SongMetadata`，调用 `viewModel.addToPlaylist(metadata, playUrl)`
  - 需要 `ImportedPlaylistDetailView` 新增 `onAddToPlaylist: (ImportedSong) -> Unit` 回调
  - `ImportTab` 中实现该回调：
    - 从 `ImportedSong` 的 `matchedSongId`/`matchedName`/`matchedArtist`/`matchedPlatform`/`matchedCoverUrl` 构建 `SongMetadata`
    - `playUrl` 从 `resolvedPlayUrls` 或 `matchedPlayUrl` 取（优先已解析的 URL）

---

## 2. 长按悬浮信息框（全局）

**适用范围**：所有页面歌曲行，**排除** MiniPlayerBar：
- `PlaylistSongCard`（PlaylistScreen.kt）
- `HistorySongRow`（PlaylistScreen.kt）
- `ImportedPlaylistDetailView` 歌曲行（ImportTab.kt）
- `SongCard`（LibraryScreen.kt）
- `SearchPanel` 搜索结果行（SearchPanel.kt）

**交互**：
- 长按歌曲行 > 500ms → `Popup` 在行上方居中弹出
- 松手 → Popup 消失
- 实现方式：每处给歌曲行 `Modifier` 追加 `pointerInput` + `detectTapGestures`

```kotlin
var showTooltip by remember { mutableStateOf(false) }

Modifier.pointerInput(Unit) {
    detectTapGestures(
        onLongPress = { showTooltip = true },
        onPress = {
            try {
                tryAwaitRelease()
            } finally {
                showTooltip = false  // hide on release or cancellation
            }
        }
    )
}

if (showTooltip) {
    Popup(alignment = Alignment.TopCenter, onDismissRequest = { showTooltip = false }) {
        // card content
    }
}
```

**拉起时机**：
- `onLongPress`（~500ms 持续按压后）→ 设置 `showTooltip = true`，Popup 出现
- `onPress` 中的 `tryAwaitRelease()` 阻塞等待手指离开，`finally` 确保无论正常松手还是手势取消都会设置 `showTooltip = false`
- Popup 的 `onDismissRequest` 作为后备关闭路径

**Popup 样式**：
- `Card`，圆角 8dp，`containerColor = MaterialTheme.colorScheme.surface`，`tonalElevation = 6.dp`
- 内容 Column：**歌名**（`bodyMedium`，`FontWeight.Bold`）+ **歌手**（`bodySmall`，`onSurfaceVariant`）
- **不截断**：`softWrap = true`，不设 `maxLines`，完整展示
- 内边距：水平 12dp，垂直 8dp
- 最大宽度：屏幕宽度的 80%（通过 `Modifier.widthIn(max = ...)` 或 `fillMaxWidth(0.8f)`）

**复用**：抽一个 `LongPressTooltip` composable 或直接在 `detectTapGestures` 处内联 Popup。

---

## 3. 播放模式按钮去背景框

**文件**：`shared/.../ui/playlist/PlaylistScreen.kt` → `CurrentPlaylistTab`（~行 243-273）

**当前代码**：
```kotlin
Row(
    modifier = Modifier
        .clip(RoundedCornerShape(6.dp))
        .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f))
        .clickable { onToggleMode() }
        .padding(horizontal = 10.dp, vertical = 5.dp),
    ...
)
```

**改为** `TextButton`，与旁边的 "⬇下载" 保持一致：
```kotlin
TextButton(onClick = onToggleMode) {
    Icon(playModeIcon, null, Modifier.size(16.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
    Spacer(Modifier.width(4.dp))
    Text(playModeLabel, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
}
```

---

## 4. 缩小下载页歌曲行高

**文件**：`shared/.../ui/library/LibraryScreen.kt` → `SongCard`

三处改动：

| 元素 | 当前值 | 新值 |
|------|--------|------|
| 封面 `Modifier.size(...)` | 52.dp | 36.dp |
| 封面 `shape` corner | 10.dp | 8.dp |
| 删除按钮 `Modifier.size(...)` | 40.dp | 32.dp |
| 删除按钮图标 size | 20.dp | 16.dp |
| Row `Modifier.padding(...)` | 12.dp | `horizontal = 12.dp, vertical = 6.dp` |

总行高：76dp → ~48dp（减少 37%）
