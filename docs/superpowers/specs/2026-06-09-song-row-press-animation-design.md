# 歌曲行按压交互动画 — 设计文档

**日期**: 2026-06-09
**状态**: 待实施

---

## 问题

测试发现：所有歌曲行 UI 组件点击播放/长按查看详情时缺少交互反馈动画。用户按压时无任何视觉变化，体验不佳。

## 需求

- 点击歌曲行播放 → 按压交互动画
- 长按歌曲行显示详情 → 按压交互动画
- 动画覆盖整行，无留边
- 不影响现有的滚动、滑动切 Tab、拖拽排序等手势

## 设计决策

| 决策点 | 选项 |
|---|---|
| 动画风格 | 暗色半透明叠加闪烁（drawn overlay flash） |
| 点击/长按 | 相同动画 |
| 入场时长 | 75ms tween |
| 退场时长 | 150ms tween |
| 叠加颜色 | `Color.Black` |
| 最大不透明度 | 0.12 (12%) |

---

## 架构

### 新增文件

```
shared/src/commonMain/kotlin/com/example/aimusicplayer/ui/common/PressAnimation.kt
```

### 核心实现：`Modifier.pressFlash()`

一个 `@Composable` 的 Modifier 扩展函数，封装手势检测和按压动画：

```kotlin
@Composable
fun Modifier.pressFlash(
    onTap: () -> Unit,
    onLongPress: (() -> Unit)? = null,
    overlayColor: Color = Color.Black,
    maxAlpha: Float = 0.12f,
    pressDuration: Int = 75,
    releaseDuration: Int = 150,
): Modifier
```

#### 工作原理

1. **手势层** (`pointerInput`): 使用 `detectTapGestures` 的 `onPress` 回调，检测按压开始与释放/取消
2. **动画层** (`Animatable<Float>`): 入场 75ms，退场 150ms，不对称时长提供更自然的触觉反馈
3. **绘制层** (`drawWithContent`): 先 `drawContent()` 再 `drawRect()`，确保叠加层覆盖整行

```
Modifier.pressFlash(onTap, onLongPress?)
  ├── pointerInput → detectTapGestures
  │     ├── onTap → 触发 onTap()
  │     ├── onLongPress → 触发 onLongPress?()
  │     └── onPress → isPressed=true → tryAwaitRelease() → isPressed=false
  ├── drawWithContent
  │     ├── drawContent()
  │     └── if (alpha > 0) drawRect(Color.Black * maxAlpha * alpha)
  └── (其他 modifiers 无影响)
```

---

## 改动范围

### 需要修改的组件（共 5 个）

| 文件 | 组件 | 当前手势 | 改动 |
|---|---|---|---|
| `ui/library/LibraryScreen.kt` | `SongCard` | `pointerInput` + `detectTapGestures` | 替换为 `.pressFlash()` |
| `ui/search/SearchPanel.kt` | `SearchResultCard` | `pointerInput` + `detectTapGestures` | 替换为 `.pressFlash()` |
| `ui/search/SearchScreen.kt` | `SearchResultCard` | `.clickable { onPlay() }` | 替换为 `.pressFlash()` + 补齐长按功能 |
| `ui/playlist/PlaylistScreen.kt` | `PlaylistSongCard` | `pointerInput` + `detectTapGestures` | 替换为 `.pressFlash()` |
| `ui/playlist/PlaylistScreen.kt` | `HistorySongRow` | `pointerInput` + `detectTapGestures` | 替换为 `.pressFlash()` |

### 手势兼容性

所有组件当前已使用 `detectTapGestures.onPress { tryAwaitRelease() }` 模式。`pressFlash` 在此模式基础上增加绘制层，不改变事件消费逻辑：

- **滚动**: 滑动时系统取消 press → `tryAwaitRelease()` 返回 false → 暗色淡出 ✓
- **拖拽排序** (PlaylistSongCard): 手柄 `.detectReorderAfterLongPress` 优先级不变 ✓
- **左右滑动切 Tab** (PlaylistScreen): Column 级 `detectHorizontalDragGestures` 不受影响 ✓
- **已存在的拖拽缩放动画** (`graphicsLayer.scaleX/scaleY`): 与 `drawWithContent` 不冲突 ✓

---

## 状态说明

### 正常态
- 行显示默认背景色（surfaceVariant / primaryContainer 等现有逻辑不变）

### 按压态
- 整行叠加黑色半透明层 (12% opacity)
- 入场动画 75ms

### 释放/取消
- 叠加层在 150ms 内淡出
- 恢复默认背景

---

## 非功能需求

- **性能**: `drawWithContent` 和 `Animatable` 均为 Compose 标准 API，无额外内存开销
- **跨平台**: 仅使用 Compose Multiplatform common 模块 API，Android/iOS 一致行为
- **可测试性**: `pressFlash` 作为独立函数，可单独测试手势和动画逻辑

---

## 不包含

- 涟漪（Ripple）效果 — 已排除
- 缩放动画 — 已排除
- 点击音效 — 不在范围
- 其他非歌曲行组件 — 不在范围
