# 下载页面导出/导入功能 — 设计方案

> 日期: 2026-06-12 | 状态: 待实施

## 概述

为下载页面（LibraryScreen）增加"导出"和"导入"功能，支持用户将本地已下载歌曲（元数据 + 音频文件）打包为 ZIP，通过系统分享面板发送到微信/QQ；接收方可通过"导入"按钮选择 ZIP 包并恢复歌曲。

---

## 数据格式

### ZIP 包结构

```
hear_everything_export_<timestamp>.zip
├── manifest.json          ← 元数据清单
├── song1_artist1_hash.mp3
├── song2_artist2_hash.mp3
└── ...
```

### manifest.json

```kotlin
@Serializable
data class ExportManifest(
    val version: Int = 1,
    val exportedAt: Long,
    val appVersion: String = "",
    val songs: List<ExportSongItem>
)

@Serializable
data class ExportSongItem(
    val songId: String,
    val fileName: String,
    val songName: String,
    val artist: String,
    val platform: String,
    val audioUrl: String,
    val fileSize: Long,
    val coverUrl: String? = null,
    val lyrics: String? = null,
    val downloadTime: Long,
    val realName: String? = null,
    val realArtist: String? = null
)
```

字段与 `DownloadedSong` 一一对应（移除 `quality`，该字段不做迁移）。`version` 保证未来格式升级时向后兼容。

---

## 新增组件

遵循项目 expect/actual 模式，新增 3 个 expect 类：

| 组件 | commonMain | Android actual | iOS actual |
|------|-----------|---------------|-----------|
| **ExportImportHandler** | expect 类 | `java.util.zip.ZipOutputStream` / `ZipInputStream` | Foundation compression (`NSData`/`NSFileWrapper`) |
| **ShareHandler** | expect 类 | `Intent.ACTION_SEND` + `FileProvider` | `UIActivityViewController` |
| **FilePicker** | expect 类 | `ActivityResultContracts.OpenDocument` | `UIDocumentPickerViewController` |

- 工厂注入：在 `App()` composable 中通过 `remember {}` 创建，传入 `LibraryViewModel`
- JSON 序列化：使用项目已有的 `kotlinx.serialization` (v1.7.3)

---

## UI 布局

### 按钮排列

下载页面 Section Header 区域，四个按钮一字排开：

```
[简洁/原始]  [▶ 全部播放]  [📤 导出]  [📥 导入]
  (灰色)       (红色)          (黑色)     (黑色)
```

| 按钮 | 图标 | 文字颜色 | 显示条件 |
|------|------|---------|---------|
| 简洁/原始 | 无 | `onSurfaceVariant` | 始终 |
| 全部播放 | `PlayArrow` (16dp) | `primary` | `songs.isNotEmpty()` |
| 导出 | `Share` (16dp) | `Color.Black` | `songs.isNotEmpty()` |
| 导入 | `FileOpen` (16dp) | `Color.Black` | 始终 |

- 全部放在现有 `Row(horizontalArrangement = Arrangement.spacedBy(4.dp))` 中
- 每个 `TextButton` 图标 `Modifier.size(16.dp)`，图标与文字间距 4.dp

### 加载状态

- 导出中/导入中：对应按钮显示小号 `CircularProgressIndicator` (16dp)，文字变为"导出中..." / "导入中..."，Row 内所有按钮禁用

---

## 核心流程

### 导出流程

```
1. 用户点击"导出"
2. LibraryViewModel.exportAll()
3. ExportImportHandler.exportAll(downloadedSongs):
   a. 收集所有 DownloadedSong
   b. 构造 ExportManifest，kotlinx.serialization 序列化为 JSON
   c. 创建临时 ZIP：先写 manifest.json，逐个追加 MP3
   d. 返回 ZIP 文件路径
4. ShareHandler.shareFile(zipPath, "application/zip")
5. 系统分享面板弹出 → 用户选择微信/QQ
```

### 导入流程

```
1. 用户点击"导入"
2. LibraryViewModel.importFile()
3. FilePicker.pickZipFile() → 系统文件选择器
4. ExportImportHandler.importFromZip(zipPath):
   a. 解压 ZIP 到临时目录
   b. 读取并校验 manifest.json
   c. 检查 version 兼容性
   d. 逐个校验 MP3 文件完整性
   e. 拷贝 MP3 到下载目录，插入 SQLite DownloadRecord
5. LibraryViewModel.loadSongs() 刷新列表
6. Snackbar 提示导入数量
```

---

## 错误处理

### 导出

| 场景 | 处理 |
|------|------|
| 无已下载歌曲 | 导出按钮隐藏 |
| ZIP 打包失败（磁盘满等） | Snackbar "导出失败：磁盘空间不足" |
| 单个文件读取失败 | 跳过该文件，继续打包，Snackbar "部分歌曲导出失败" |
| 文件超过 100MB | AlertDialog 提示"文件较大，微信可能无法发送" |

### 分享

| 场景 | 处理 |
|------|------|
| 用户取消分享面板 | 静默返回 |
| 目标 App 未安装 | 系统分享面板自动过滤 |

### 导入

| 场景 | 处理 |
|------|------|
| 非 ZIP 文件 | Snackbar "文件格式不支持，请选择 .zip 文件" |
| manifest.json 缺失或格式错误 | Snackbar "数据文件损坏，无法导入" |
| MP3 与 manifest 不匹配 | 跳过不匹配条目，仅导入匹配的 |
| 歌曲 ID 已存在（重复导入） | 跳过重复，Snackbar "已跳过 N 首重复歌曲" |
| 用户取消文件选择器 | 静默返回 |
| manifest version 不兼容 | Snackbar "数据格式版本不兼容，请更新 App" |
| 文件名冲突 | 自动追加序号 `_1`, `_2` |

---

## 涉及文件清单

### 新增文件

| 文件 | 说明 |
|------|------|
| `shared/src/commonMain/.../export/ExportManifest.kt` | 数据类 + @Serializable |
| `shared/src/commonMain/.../export/ExportImportHandler.kt` | expect 类定义 |
| `shared/src/androidMain/.../export/ExportImportHandler.android.kt` | Android ZIP 实现 |
| `shared/src/iosMain/.../export/ExportImportHandler.ios.kt` | iOS 压缩实现 |
| `shared/src/commonMain/.../export/ShareHandler.kt` | expect 类定义 |
| `shared/src/androidMain/.../export/ShareHandler.android.kt` | Android Intent 分享 |
| `shared/src/iosMain/.../export/ShareHandler.ios.kt` | iOS Share Sheet |
| `shared/src/commonMain/.../export/FilePicker.kt` | expect 类定义 |
| `shared/src/androidMain/.../export/FilePicker.android.kt` | Android 文件选择器 |
| `shared/src/iosMain/.../export/FilePicker.ios.kt` | iOS Document Picker |

### 修改文件

| 文件 | 改动 |
|------|------|
| `shared/src/commonMain/.../ui/library/LibraryScreen.kt` | 新增导出/导入按钮，加载状态 |
| `shared/src/commonMain/.../ui/library/LibraryViewModel.kt` | 新增 exportAll()、importFile() 方法 |
| `shared/src/commonMain/.../App.kt` | 注入 ExportImportHandler / ShareHandler / FilePicker 工厂 |

### 测试文件

| 文件 | 说明 |
|------|------|
| `shared/src/commonTest/.../export/ExportManifestTest.kt` | JSON 序列化/反序列化验证 |
| `shared/src/commonTest/.../export/ExportImportHandlerTest.kt` | ZIP 打包/解压逻辑 |
| `shared/src/commonTest/.../fixtures/FakeExportImportHandler.kt` | 测试桩 |
| `shared/src/androidUnitTest/.../LibraryViewModelExportTest.kt` | ViewModel 导出/导入流程 |
