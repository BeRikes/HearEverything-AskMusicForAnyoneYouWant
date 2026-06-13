# HearEverything 应用图标设计

**日期：** 2026-06-11
**状态：** 已确认

## 设计概念

M + ♬（十六分音符）融合图标。两根竖线既是音符的符干也是字母 M 的双腿，两个浅 V 形既是 ♬ 的双横梁也是 M 的 V 形笔画。

## 配色

| 元素 | 颜色 | 色值 |
|------|------|------|
| 背景 | 暗色 | `#141418` |
| 前景（全体） | 网易云红 | `#E84B4B` |

纯色方案，无渐变。简洁有力，小尺寸清晰。

## 结构（108×108 画布，安全区 r=33 中心 54,54）

### 音符头
- 左：中心 (34, 72), rx=12, ry=9, 旋转 -20°, 实心椭圆
- 右：中心 (72, 70), rx=12, ry=9, 旋转 -20°, 实心椭圆
- 两音符头向左倾斜，视觉统一

### 符干（竖线）
- 左竖线：(40, 66) → (40, 22)，线宽 4px
- 右竖线：(78, 64) → (78, 24)，线宽 4px
- 竖线起点嵌入音符头右上角，无间隙

### 上 V（第一横梁）
- 左端点：(40, 26)，接左竖线
- 底端点：(70, 31)，偏右竖线，垂直跨度 5px
- 右端点：(78, 27)，接右竖线
- 线宽 4px

### 下 V（第二横梁）
- 左端点：(40, 36)，接左竖线
- 底端点：(49, 41)，偏左竖线，垂直跨度 5px
- 右端点：(78, 37)，接右竖线
- 线宽 4px

### 整体效果
两个浅 V 错位叠加——上 V 偏右下、下 V 偏左下——形成 ♬ 的平行双梁效果，同时上下两个 V 的底端连线构成字母 M 的菱形笔画。

## 实现方式

Android Adaptive Icon（API 26+）：

- `mipmap-anydpi-v26/ic_launcher.xml` — 引用两个 layer
- `drawable/ic_launcher_background.xml` — 暗色底 `#141418`
- `drawable/ic_launcher_foreground.xml` — 红色 M + ♬ 图形（108dp viewport）

同时更新通知图标 `shared/src/androidMain/res/drawable/ic_music_note.xml` 为同样图形。

## 文件清单

| 文件 | 操作 |
|------|------|
| `androidApp/src/main/res/mipmap-anydpi-v26/ic_launcher.xml` | 修改 |
| `androidApp/src/main/res/drawable/ic_launcher_background.xml` | 修改 |
| `androidApp/src/main/res/drawable/ic_launcher_foreground.xml` | 重写 |
| `shared/src/androidMain/res/drawable/ic_music_note.xml` | 重写（通知小图标同步更新） |
| `androidApp/src/main/AndroidManifest.xml` | 已有 `android:icon`，无需改动 |
