# 歌曲行按压交互动画 — 实现计划

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 为全部 5 个歌曲行 UI 组件添加暗色半透明按压闪烁动画

**Architecture:** 新建一个 `Modifier.pressFlash()` 扩展函数（`ui/common/PressAnimation.kt`），封装 `detectTapGestures` + `Animatable` + `drawWithContent`。5 个现有组件只需将 `pointerInput`/`clickable` 替换为 `.pressFlash()` 即可。

**Tech Stack:** Kotlin, Compose Multiplatform (commonMain), Material3

---

## 文件结构

| 操作 | 文件 | 职责 |
|---|---|---|
| **Create** | `ui/common/PressAnimation.kt` | `pressFlash` Modifier 扩展函数 |
| **Modify** | `ui/library/LibraryScreen.kt` | `SongCard` — 替换手势 Modifier |
| **Modify** | `ui/search/SearchPanel.kt` | `SearchResultCard` — 替换手势 Modifier |
| **Modify** | `ui/search/SearchScreen.kt` | `SearchResultCard` — 替换 `.clickable` + 补齐长按/弹窗 |
| **Modify** | `ui/playlist/PlaylistScreen.kt` | `PlaylistSongCard` — 替换手势 Modifier |
| **Modify** | `ui/playlist/PlaylistScreen.kt` | `HistorySongRow` — 替换手势 Modifier |

---

### Task 1: 创建 `PressAnimation.kt`

**Files:**
- Create: `shared/src/commonMain/kotlin/com/example/aimusicplayer/ui/common/PressAnimation.kt`

- [ ] **Step 1: 新建 PressAnimation.kt**

```kotlin
package com.example.aimusicplayer.ui.common

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import kotlinx.coroutines.launch

/**
 * 按压暗色闪烁 Modifier 扩展。
 *
 * 点击和长按时在整行上方叠加暗色半透明层，入场 75ms / 退场 150ms。
 *
 * @param onTap        点击回调
 * @param onLongPress  长按回调，可选
 * @param onRelease    按压释放回调（动画启动后调用），可选，用于清理长按弹窗等状态
 * @param overlayColor 叠加层颜色，默认黑色
 * @param maxAlpha     叠加层最大不透明度，默认 0.12f
 * @param pressDuration 入场动画时长 ms，默认 75
 * @param releaseDuration 退场动画时长 ms，默认 150
 */
@Composable
fun Modifier.pressFlash(
    onTap: () -> Unit,
    onLongPress: (() -> Unit)? = null,
    onRelease: (() -> Unit)? = null,
    overlayColor: Color = Color.Black,
    maxAlpha: Float = 0.12f,
    pressDuration: Int = 75,
    releaseDuration: Int = 150,
): Modifier = composed {
    val currentOnTap by rememberUpdatedState(onTap)
    val currentOnLongPress by rememberUpdatedState(onLongPress)
    val currentOnRelease by rememberUpdatedState(onRelease)
    val scope = rememberCoroutineScope()
    val overlayAlpha = remember { Animatable(0f) }

    this
        .pointerInput(Unit) {
            detectTapGestures(
                onTap = { currentOnTap() },
                onLongPress = currentOnLongPress?.let { cb -> { cb() } },
                onPress = {
                    // 入场动画
                    scope.launch { overlayAlpha.animateTo(maxAlpha, tween(pressDuration)) }
                    tryAwaitRelease()
                    // 退场动画
                    scope.launch { overlayAlpha.animateTo(0f, tween(releaseDuration)) }
                    currentOnRelease?.invoke()
                },
            )
        }
        .drawWithContent {
            drawContent()
            if (overlayAlpha.value > 0.001f) {
                drawRect(
                    color = overlayColor.copy(alpha = overlayAlpha.value),
                    topLeft = Offset.Zero,
                    size = size,
                )
            }
        }
}
```

---

### Task 2: 修改 `LibraryScreen.kt` — SongCard

**Files:**
- Modify: `shared/src/commonMain/kotlin/com/example/aimusicplayer/ui/library/LibraryScreen.kt:308-317`

- [ ] **Step 1: 添加 import**

在文件顶部的 import 区域添加：

```kotlin
import com.example.aimusicplayer.ui.common.pressFlash
```

- [ ] **Step 2: 替换 SongCard 的 pointerInput 为 pressFlash**

将第 308-317 行：

```kotlin
                .pointerInput(Unit) {
                    detectTapGestures(
                        onTap = { onPlay() },
                        onLongPress = { showTooltip = true },
                        onPress = {
                            tryAwaitRelease()
                            showTooltip = false
                        }
                    )
                },
```

替换为：

```kotlin
                .pressFlash(
                    onTap = { onPlay() },
                    onLongPress = { showTooltip = true },
                    onRelease = { showTooltip = false },
                ),
```

- [ ] **Step 3: 清理不再使用的 import**

删除以下不再需要的 import（如果不被文件中其他地方使用）：
- `import androidx.compose.foundation.gestures.detectTapGestures` — 保留，可能其他组件使用（检查文件：仅 SongCard 使用，可移除）
- `import androidx.compose.ui.input.pointer.pointerInput` — 仅 SongCard 使用，移除

> 注意：如果文件中其他组件也用到了这些 import，则保留。

---

### Task 3: 修改 `SearchPanel.kt` — SearchResultCard

**Files:**
- Modify: `shared/src/commonMain/kotlin/com/example/aimusicplayer/ui/search/SearchPanel.kt:589-600`

- [ ] **Step 1: 添加 import**

```kotlin
import com.example.aimusicplayer.ui.common.pressFlash
```

- [ ] **Step 2: 替换 SearchResultCard 的 pointerInput 为 pressFlash**

将第 589-600 行：

```kotlin
                .pointerInput(Unit) {
                    detectTapGestures(
                        onTap = { onPlay() },
                        onLongPress = { showTooltip = true },
                        onPress = {
                            tryAwaitRelease()
                            showTooltip = false
                        }
                    )
                },
```

替换为：

```kotlin
                .pressFlash(
                    onTap = { onPlay() },
                    onLongPress = { showTooltip = true },
                    onRelease = { showTooltip = false },
                ),
```

- [ ] **Step 3: 清理不再使用的 import**

如果 `pointerInput` 和 `detectTapGestures` 不被文件中其他组件使用（SearchPanel 中仅 SearchResultCard 使用），移除它们。

---

### Task 4: 修改 `SearchScreen.kt` — SearchResultCard（补齐长按 + 弹窗）

**Files:**
- Modify: `shared/src/commonMain/kotlin/com/example/aimusicplayer/ui/search/SearchScreen.kt:356-578`

这是改动最大的组件。当前使用 `.clickable { onPlay() }`，缺少长按和弹窗。需要：
1. 替换手势为 `pressFlash`
2. 添加 Tooltip/Popup 逻辑
3. 添加必要的状态变量和 import

- [ ] **Step 1: 添加 import**

```kotlin
import androidx.compose.foundation.gestures.detectTapGestures  // 不再直接引用，由 pressFlash 内部使用
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInWindow
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntRect
import androidx.compose.ui.unit.DpOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp  // 已有
import androidx.compose.ui.window.Popup
import androidx.compose.ui.window.PopupProperties
import com.example.aimusicplayer.ui.common.pressFlash
import kotlin.math.roundToInt
```

- [ ] **Step 2: 修改 SearchResultCard — 添加状态变量和 wrapper Box**

将当前的 SearchResultCard（第 357 行起）替换为完整版本：

```kotlin
@Composable
private fun SearchResultCard(
    item: SearchResultItem,
    isCurrentlyPlaying: Boolean,
    isPlaying: Boolean,
    downloadState: com.example.aimusicplayer.music.DownloadState?,
    isPreparing: Boolean = false,
    hasError: Boolean = false,
    onPlay: () -> Unit,
    onDownload: () -> Unit,
    onAddToPlaylist: (() -> Unit)? = null,
    isInPlaylist: Boolean = false,
) {
    val song = item.song
    val isDownloading = isPreparing || (downloadState != null && downloadState.isActive && downloadState.progress < 1f)
    val isDownloaded = downloadState != null && downloadState.progress >= 1f

    var showTooltip by remember { mutableStateOf(false) }
    var parentSize by remember { mutableStateOf(IntSize.Zero) }
    var menuSize by remember { mutableStateOf(IntSize.Zero) }
    var parentPosX by remember { mutableStateOf(0f) }
    val density = LocalDensity.current
    val windowWidth = (parentSize.width + parentPosX * 2f).roundToInt().coerceAtLeast(parentSize.width)

    Box(modifier = Modifier.onGloballyPositioned { parentSize = it.size; parentPosX = it.positionInWindow().x }) {
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .pressFlash(
                    onTap = { onPlay() },
                    onLongPress = { showTooltip = true },
                    onRelease = { showTooltip = false },
                ),
            colors = CardDefaults.cardColors(
                containerColor = if (isCurrentlyPlaying)
                    MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.4f)
                else
                    MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
            ),
            shape = RoundedCornerShape(12.dp),
        ) {
            Column(modifier = Modifier.padding(12.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    // Cover placeholder (with play state indicator)
                    Box(
                        modifier = Modifier
                            .size(48.dp)
                            .clip(RoundedCornerShape(10.dp))
                            .background(
                                if (isCurrentlyPlaying && isPlaying)
                                    MaterialTheme.colorScheme.primary
                                else
                                    MaterialTheme.colorScheme.primaryContainer,
                            ),
                        contentAlignment = Alignment.Center,
                    ) {
                        if (isCurrentlyPlaying && isPlaying) {
                            Row(
                                horizontalArrangement = Arrangement.spacedBy(2.dp),
                                verticalAlignment = Alignment.Bottom,
                            ) {
                                repeat(3) {
                                    Box(
                                        modifier = Modifier
                                            .width(3.dp)
                                            .height((12 + it * 4).dp)
                                            .background(
                                                MaterialTheme.colorScheme.onPrimary,
                                                RoundedCornerShape(1.dp),
                                            ),
                                    )
                                }
                            }
                        } else {
                            Text("🎵", fontSize = 20.sp)
                        }
                    }

                    Spacer(Modifier.width(12.dp))

                    // Song info
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = song.songName,
                            style = MaterialTheme.typography.bodyLarge,
                            fontWeight = FontWeight.SemiBold,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                        Spacer(Modifier.height(2.dp))
                        Text(
                            text = song.artist.ifBlank { "未知艺术家" },
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                        Spacer(Modifier.height(4.dp))

                        // Badges row
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            PlatformBadge(platform = song.platform)

                            Surface(
                                shape = RoundedCornerShape(4.dp),
                                color = if (item.fromCache)
                                    MaterialTheme.colorScheme.tertiary.copy(alpha = 0.1f)
                                else
                                    MaterialTheme.colorScheme.secondary.copy(alpha = 0.1f),
                            ) {
                                Text(
                                    if (item.fromCache) "缓存" else "在线",
                                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 1.dp),
                                    style = MaterialTheme.typography.labelSmall,
                                    color = if (item.fromCache)
                                        MaterialTheme.colorScheme.tertiary
                                    else
                                        MaterialTheme.colorScheme.secondary,
                                    fontWeight = FontWeight.Medium,
                                )
                            }

                            if (song.quality != "standard") {
                                Surface(
                                    shape = RoundedCornerShape(4.dp),
                                    color = MaterialTheme.colorScheme.primary.copy(alpha = 0.1f),
                                ) {
                                    Text(
                                        song.quality,
                                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 1.dp),
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.primary,
                                        fontWeight = FontWeight.Medium,
                                    )
                                }
                            }
                        }
                    }

                    Spacer(Modifier.width(4.dp))

                    // Download button (hidden if already downloaded)
                    if (!isDownloaded) {
                        IconButton(
                            onClick = onDownload,
                            modifier = Modifier
                                .size(36.dp)
                                .clip(RoundedCornerShape(8.dp))
                                .background(
                                    MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.8f),
                                ),
                        ) {
                            if (isDownloading) {
                                if (downloadState != null && downloadState.isActive) {
                                    CircularProgressIndicator(
                                        progress = { downloadState.progress },
                                        modifier = Modifier.size(16.dp),
                                        strokeWidth = 2.dp,
                                    )
                                } else {
                                    CircularProgressIndicator(
                                        modifier = Modifier.size(16.dp),
                                        strokeWidth = 2.dp,
                                    )
                                }
                            } else {
                                Icon(
                                    Icons.Default.Download,
                                    contentDescription = "下载",
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.size(18.dp),
                                )
                            }
                        }
                    }

                    // Error indicator
                    if (hasError) {
                        Icon(
                            Icons.Default.Error,
                            contentDescription = "下载失败",
                            tint = MaterialTheme.colorScheme.error,
                            modifier = Modifier.size(18.dp),
                        )
                        Spacer(Modifier.width(4.dp))
                    }

                    // Add to playlist button
                    if (onAddToPlaylist != null) {
                        Spacer(Modifier.width(4.dp))
                        IconButton(
                            onClick = onAddToPlaylist,
                            modifier = Modifier
                                .size(36.dp)
                                .clip(RoundedCornerShape(8.dp))
                                .background(
                                    if (isInPlaylist) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.3f)
                                    else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.8f),
                                ),
                        ) {
                            Icon(
                                Icons.Default.Add,
                                contentDescription = if (isInPlaylist) "已添加" else "添加到歌单",
                                tint = if (isInPlaylist) MaterialTheme.colorScheme.primary
                                       else MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.size(18.dp),
                            )
                        }
                    }
                }

                // Download progress bar
                if (isDownloading && downloadState != null) {
                    Spacer(Modifier.height(8.dp))
                    LinearProgressIndicator(
                        progress = { downloadState.progress },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(3.dp),
                        color = MaterialTheme.colorScheme.primary,
                        trackColor = MaterialTheme.colorScheme.surfaceVariant,
                    )
                }
            }
        }

        // Long-press tooltip Popup
        if (showTooltip) {
            Popup(
                alignment = Alignment.BottomCenter,
                offset = IntOffset(0, -parentSize.height),
                onDismissRequest = { showTooltip = false },
                properties = PopupProperties(focusable = true),
            ) {
                Surface(
                    color = Color.White,
                    shape = RoundedCornerShape(8.dp),
                    shadowElevation = 6.dp,
                    modifier = Modifier.widthIn(max = 300.dp),
                ) {
                    Column(modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp)) {
                        Text(
                            song.songName,
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.Bold,
                            softWrap = true,
                        )
                        Spacer(Modifier.height(2.dp))
                        Text(
                            song.artist,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            softWrap = true,
                        )
                    }
                }
            }
        }
    }
}
```

- [ ] **Step 3: 移除不再使用的 import**

移除 `import androidx.compose.foundation.clickable`（如果仅 SearchResultCard 使用）。

---

### Task 5: 修改 `PlaylistScreen.kt` — PlaylistSongCard

**Files:**
- Modify: `shared/src/commonMain/kotlin/com/example/aimusicplayer/ui/playlist/PlaylistScreen.kt:346-360`

- [ ] **Step 1: 添加 import**

```kotlin
import com.example.aimusicplayer.ui.common.pressFlash
```

- [ ] **Step 2: 替换 PlaylistSongCard 的 pointerInput 为 pressFlash**

将第 346-360 行：

```kotlin
                .graphicsLayer {
                    scaleX = scale
                    scaleY = scale
                }
                .pointerInput(Unit) {
                    detectTapGestures(
                        onTap = { onPlay() },
                        onLongPress = { showTooltip = true },
                        onPress = {
                            tryAwaitRelease()
                            showTooltip = false
                        }
                    )
                },
```

替换为：

```kotlin
                .graphicsLayer {
                    scaleX = scale
                    scaleY = scale
                }
                .pressFlash(
                    onTap = { onPlay() },
                    onLongPress = { showTooltip = true },
                    onRelease = { showTooltip = false },
                ),
```

注意：`.graphicsLayer` 必须保留并放在 `.pressFlash` 之前，因为这是拖拽排序的缩放动画。

- [ ] **Step 3: 清理不再使用的 import**

如果 `pointerInput` 和 `detectTapGestures` 不被文件中其他组件使用，移除它们。注意 `ImportTab.kt` 和 PlaylistScreen 的 Column 级手势也用了 `pointerInput`，所以需保留 `pointerInput` 的 import。

---

### Task 6: 修改 `PlaylistScreen.kt` — HistorySongRow

**Files:**
- Modify: `shared/src/commonMain/kotlin/com/example/aimusicplayer/ui/playlist/PlaylistScreen.kt:507-519`

- [ ] **Step 1: 替换 HistorySongRow 的 pointerInput 为 pressFlash**

（import 已在 Task 5 添加，无需重复）

将第 507-519 行：

```kotlin
                .pointerInput(Unit) {
                    detectTapGestures(
                        onTap = { onPlay() },
                        onLongPress = { showTooltip = true },
                        onPress = {
                            tryAwaitRelease()
                            showTooltip = false
                        }
                    )
                },
```

替换为：

```kotlin
                .pressFlash(
                    onTap = { onPlay() },
                    onLongPress = { showTooltip = true },
                    onRelease = { showTooltip = false },
                ),
```

---

### Task 7: 构建验证

**Files:** 无新文件

- [ ] **Step 1: 编译项目**

```bash
cd D:/Download/claude_demo/HearEverything
./gradlew :shared:compileKotlinAndroid
```

预期：BUILD SUCCESSFUL，无编译错误。

- [ ] **Step 2: 验证 import 清理**

确认不再需要以下 import 的文件已移除它们（仅在该文件中只有 song card 组件使用时）：
- `LibraryScreen.kt`: 移除 `detectTapGestures` 和 `pointerInput`
- `SearchPanel.kt`: 移除 `detectTapGestures` 和 `pointerInput`
- `SearchScreen.kt`: 移除 `clickable`
- `PlaylistScreen.kt`: 移除 `detectTapGestures`（如果 `pointerInput` 还被其他地方使用，需保留 `pointerInput`）
