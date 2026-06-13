# 跨平台歌单导入功能设计文档

## Context

HearEverything 目前只能通过搜索逐首添加歌曲到歌单，用户无法导入自己在网易云音乐、QQ 音乐、酷狗音乐中收藏的歌单。本功能支持用户粘贴音乐平台的分享链接，自动解析歌单内容、并发搜索匹配可播放版本，并通过卡片接力确认界面让用户快速完成批量导入。一期先实现网易云音乐，后续扩展 QQ 音乐和酷狗。

## 功能范围

### 一期：网易云音乐歌单导入
- 支持解析网易云分享链接（`https://music.163.com/playlist?id=xxx`）
- 调用 `/weapi/v3/playlist/detail` 获取歌单歌曲列表（复用现有 `NeteaseEncryptor` 加密）
- 获取歌单后并发搜索所有歌曲的可播放版本（复用 `MusicSearchManager`）
- 卡片接力确认界面：最多 3 张卡片同时可见，单击确认、✕ 跳过
- 搜索结果实时流式展示在卡片内，搜索超时/无结果卡片自动关闭
- 确认完成后持久化到"导入歌单"Tab，格式与当前歌单一致
- 支持在 AI 对话中粘贴链接触发导入

### 二期（后续）：QQ 音乐
### 三期（后续）：酷狗音乐

### 入口
- 播放列表页面新增第三个 Tab "导入歌单"（位于"历史歌单"右侧）
- AI 对话中粘贴链接自动检测并触发导入流程

---

## 数据模型

```kotlin
// import/PlaylistImportState.kt

// 一张已导入的歌单
@Serializable
data class ImportedPlaylist(
    val id: String,              // 歌单唯一 ID
    val title: String,           // 歌单名称（来自平台）
    val coverUrl: String?,       // 封面图 URL
    val platform: String,        // 来源平台 "netease"
    val sourceUrl: String,       // 原始分享链接
    val importedAt: Long,        // 导入时间戳（毫秒）
    val songs: List<ImportedSong>
)

// 导入歌单中的一首歌
@Serializable
data class ImportedSong(
    val originalName: String,    // 歌单中的原始歌名
    val originalArtist: String,  // 原始艺人名
    val matchedSongId: String?,  // 匹配成功后的 songId，null 表示未匹配/已跳过
    val matchedName: String?,
    val matchedArtist: String?,
    val matchedPlatform: String?,
    val playUrl: String?,
    val coverUrl: String?,
    val quality: String?,
)

// 导入流程中的单曲确认状态
enum class TrackConfirmStatus {
    SEARCHING,       // 搜索中
    AWAITING,        // 搜索结果已就绪，等待用户操作
    CONFIRMED,       // 用户已选择确认
    SKIPPED,         // 用户跳过 / 搜索超时自动跳过
    FAILED,          // 搜索失败
}

// 导入流程整体状态
data class ImportSession(
    val playlistDetail: PlaylistDetail?,       // 歌单元数据
    val tracks: List<TrackImportState>,         // 每首歌的状态
    val phase: ImportPhase,
)

data class TrackImportState(
    val index: Int,
    val originalName: String,
    val originalArtist: String,
    val searchResults: List<SongMetadata>,      // 流式更新的搜索结果
    val confirmStatus: TrackConfirmStatus,
    val selectedResult: SongMetadata?,          // 用户选中的结果
)

enum class ImportPhase {
    IDLE,              // 初始状态
    PARSING,           // 解析链接
    FETCHING,          // 获取歌单元数据
    SEARCHING,         // 并发搜索匹配中
    CONFIRMING,        // 用户确认中（卡片接力）
    SAVING,            // 持久化保存
    COMPLETED,         // 完成
    ERROR,             // 错误
}
```

---

## 架构设计

### 新增模块

```
shared/src/commonMain/kotlin/com/example/aimusicplayer/
  import/
    PlaylistLinkParser.kt          # 链接解析
    PlaylistImportManager.kt       # 导入编排器
    PlaylistImportState.kt         # 状态 & 数据模型
```

### PlaylistLinkParser

解析三大平台的分享链接，提取平台类型和歌单 ID：

```kotlin
object PlaylistLinkParser {
    data class ParseResult(val platform: String, val playlistId: String)

    fun parse(url: String): ParseResult? {
        // 网易云: https://music.163.com/playlist?id=123456
        //         https://music.163.com/#/playlist?id=123456
        // QQ音乐:  https://y.qq.com/n/ryqq/playlist/123456
        // 酷狗:    https://www.kugou.com/songlist/xxx
    }
}
```

一期只实现网易云正则匹配，QQ 和酷狗预留接口返回 null。

### PlaylistImportManager

编排整个导入流程，对外暴露 `StateFlow<ImportSession>`：

```
核心方法:
- startImport(url: String)          启动导入：解析链接 → 获取歌单 → 批量搜索
- confirmTrack(index: Int, result: SongMetadata)  确认某首歌
- skipTrack(index: Int)             跳过某首歌
- retryTrack(index: Int)            重新搜索
- saveImport()                      持久化到 SQLDelight
- reset()                           重置状态
```

**并发搜索策略**：获取歌单后，对所有 `tracks` 并发调用 `MusicSearchManager.search(songName, artist)`，每首歌的搜索结果通过 `StateFlow` 逐步推送到 `TrackImportState.searchResults`，UI 实时响应更新。

**搜索超时处理**：单首歌搜索超时 15 秒 → 自动标记 `SKIPPED`，卡片自动关闭。

### API 扩展

**MusicPlatformApi 新增：**

```kotlin
data class PlaylistDetail(
    val playlistId: String,
    val title: String,
    val coverUrl: String?,
    val trackCount: Int,
    val tracks: List<PlaylistTrack>
)

data class PlaylistTrack(
    val songName: String,
    val artist: String
)

// 接口新增方法（默认实现返回 null，各平台可选覆写）
suspend fun getPlaylistDetail(playlistId: String): PlaylistDetail? = null
```

**NeteaseApi 实现 `getPlaylistDetail()`**：
- 端点：`POST /weapi/v3/playlist/detail`
- 请求体：`{ id: playlistId, n: 100000, s: 0 }`（s 为偏移量，n 为拉取数量）
- 加密：复用 `NeteaseEncryptor.encrypt()` 生成 `params` 和 `encSecKey`
- 解析响应 JSON → `PlaylistDetail`（仅提取 `name`、`coverImgUrl`、`trackIds[].name/ar[].name`）

### 持久化

SQLDelight 新增表（在 `AIMusicSchema.kt` 中定义，版本升至 v4）：

```sql
CREATE TABLE ImportedPlaylist (
    id TEXT PRIMARY KEY,
    title TEXT NOT NULL,
    cover_url TEXT,
    platform TEXT NOT NULL,
    source_url TEXT NOT NULL,
    imported_at INTEGER NOT NULL
);

CREATE TABLE ImportedSong (
    id TEXT PRIMARY KEY,               -- playlistId + "_" + songIndex
    playlist_id TEXT NOT NULL REFERENCES ImportedPlaylist(id),
    original_name TEXT NOT NULL,
    original_artist TEXT NOT NULL,
    matched_song_id TEXT,
    matched_name TEXT,
    matched_artist TEXT,
    matched_platform TEXT,
    play_url TEXT,
    cover_url TEXT,
    quality TEXT,
    sort_order INTEGER NOT NULL
);
```

`PlaylistManager` 新增方法（expect/actual 同步实现）：
- `addImportedPlaylist(playlist: ImportedPlaylist)`
- `getImportedPlaylists(): List<ImportedPlaylist>`
- `removeImportedPlaylist(id: String)`
- `removeImportedSong(playlistId: String, songId: String)`

---

## UI 设计

### 播放列表页面 Tab 结构变更

```
┌── 当前歌单 ──┬── 历史歌单 ──┬── 导入歌单 ──┐    ← 三个子 Tab
```

### 导入歌单 Tab 有三个子视图

**视图 1：导入入口（初始状态）**
```
┌─────────────────────────────────────────┐
│  粘贴音乐平台分享链接                      │
│  ┌────────────────────────────────────┐  │
│  │ https://music.163.com/playlist?... │  │  ← OutlinedTextField
│  └────────────────────────────────────┘  │
│  [开始导入]                              │  ← Button
│  ─────────────────────────────────────  │
│  已导入的歌单：                           │
│  ┌ 周杰伦精选 · 48首 · 3天前 ─────────┐  │
│  └ 轻音乐歌单 · 30首 · 5天前 ─────────┘  │
└─────────────────────────────────────────┘
```

**视图 2：卡片接力确认界面（导入中）**
```
┌─────────────────────────────────────────┐
│  进度栏: 🎵 周杰伦精选歌单 · 50首         │
│  ✅ 已确认 12  ❌ 已跳过 3  🔄 搜索中 5  ⏳ 待确认 30 │
├─────────────────────────────────────────┤
│  ┌ 卡片 #1: 《夜曲》— 周杰伦  [✕] ┐    │
│  │ 搜索结果（实时流式更新）:         │    │
│  │ ● 夜曲-周杰伦 [网易] 标准品质 ──┐│    │  ← 单击选中/确认
│  │   夜曲-周杰伦 [QQ]   高品质   ──┐│    │
│  │   夜曲-周杰伦 [B站]   ...    ──┐│    │
│  └────────────────────────────────┘    │
│  ┌ 卡片 #2: 《发如雪》— 周杰伦 [✕] ┐  │
│  │ 搜索结果:                        │    │
│  │   发如雪-周杰伦 [QQ]   无损 ────┐│    │
│  │ ● 发如雪-周杰伦 [网易] 标准  ──┐│    │  ← 已选中（高亮）
│  └────────────────────────────────┘    │
│  ┌ 卡片 #3: 《以父之名》— 周杰伦 [✕] ┐  │
│  │ 🔄 搜索中...                     │    │
│  └────────────────────────────────┘    │
└─────────────────────────────────────────┘
```

**交互规则：**
| 操作 | 行为 |
|------|------|
| 单击搜索结果行 | 选定该版本 + 确认 → 卡片立即关闭滑出，下一张待确认卡片从底部滑入 |
| 点击 [✕] | 跳过此歌 → 卡片关闭滑出，下一张滑入 |
| 搜索超时 / 无结果 | 卡片自动关闭（标记为跳过），下一张滑入 |
| 可视区域 | 始终最多 3 张卡片，确认后接力 |

**视图 3：导入完成统计 + 已导入歌单展示**
```
┌─────────────────────────────────────────┐
│  导入完成！                              │
│  ✅ 成功匹配 45 首  ❌ 跳过 5 首         │
│  [保存到导入歌单]                        │
│  ─────────────────────────────────────  │
│  封面  周杰伦精选歌单                     │
│        48 首 · 网易云 · 已导入            │
│  ┌ 夜曲 - 周杰伦 [网易] 标准 ──────────┐  │  ← 复用 PlaylistSongCard 样式
│  └ 发如雪 - 周杰伦 [QQ] 无损 ──────────┘  │
│  ┌ 以父之名 - 周杰伦 [网易] 高品质 ────┐  │
│  ...                                    │
└─────────────────────────────────────────┘
```

### AI 对话触发

在 `MainViewModel` 的消息发送处理中，检测消息文本是否匹配 `PlaylistLinkParser` 的链接格式。如果匹配，不发送给 AI，而是触发导入流程：设置 `currentScreen = Screen.Playlist` 并通知 `PlaylistViewModel` 切换到导入 Tab。

---

## 新增/修改文件清单

### 新增文件
| 文件路径 | 职责 |
|---------|------|
| `shared/.../import/PlaylistLinkParser.kt` | 链接解析，提取平台和歌单 ID |
| `shared/.../import/PlaylistImportManager.kt` | 导入编排器，管理状态机和并发搜索 |
| `shared/.../import/PlaylistImportState.kt` | 状态数据类、枚举定义 |
| `shared/.../ui/playlist/ImportTab.kt` | 导入 Tab UI（入口、卡片接力、已导入展示） |

### 修改文件
| 文件路径 | 改动内容 |
|---------|---------|
| `shared/.../network/music/MusicPlatformApi.kt` | 新增 `PlaylistDetail`、`PlaylistTrack` 数据类；接口新增 `getPlaylistDetail()` 方法（默认返回 null） |
| `shared/.../network/music/NeteaseApi.kt` | 实现 `getPlaylistDetail()`，调用 `/weapi/v3/playlist/detail` |
| `shared/.../music/PlaylistManager.kt` | expect class 新增 `addImportedPlaylist()`、`getImportedPlaylists()`、`removeImportedPlaylist()`、`removeImportedSong()` |
| `shared/.../music/PlaylistManager.android.kt` | actual 实现，新增导入歌单 CRUD |
| `shared/.../music/PlaylistManager.ios.kt` | actual 实现，新增导入歌单 CRUD |
| `shared/.../cache/AIMusicSchema.kt` | 升级到 v4，新增 `ImportedPlaylist` 和 `ImportedSong` 表 |
| `shared/.../ui/playlist/PlaylistScreen.kt` | Tab 从 2 个改为 3 个（当前歌单、历史歌单、导入歌单） |
| `shared/.../ui/playlist/PlaylistViewModel.kt` | 管理导入相关状态、调用 PlaylistImportManager |
| `shared/.../ui/main/MainViewModel.kt` | 检测链接消息，触发导入流程 |
| `shared/.../App.kt` | 注入 PlaylistImportManager，传递导入回调 |

---

## 边界情况处理

| 场景 | 处理方式 |
|------|---------|
| 用户在卡片确认中途切换到其他 Tab | 导入会话保持在 `PlaylistImportManager` 中，切回导入 Tab 时恢复确认界面 |
| 用户在确认过程中发起新的导入 | 弹出确认对话框"当前有未完成的导入，是否放弃？" |
| 歌单为空（0 首歌曲） | `FETCHING` 完成后直接显示"歌单为空"错误提示 |
| 所有歌曲搜索均失败/超时 | 确认界面正常走完（全部自动跳过），统计显示 0 首成功 |
| 用户在视图 3 未点击"保存"就离开 | 下次进入导入 Tab 时，如果存在未保存的完成会话，自动显示视图 3（统计 + 保存按钮） |
| 链接解析失败（非音乐平台链接） | 显示"无法识别的链接，请粘贴网易云音乐歌单分享链接" |
| 网络错误（歌单获取失败） | 显示具体错误信息 + [重试] 按钮 |
| 相同歌单重复导入 | 不做去重检查，允许重复导入（用户可能想导入更新后的歌单） |

---

## Verification

1. `./gradlew :androidApp:assembleDebug` 编译通过
2. 手动测试：
   - 在导入 Tab 中粘贴网易云分享链接 → 点击"开始导入" → 显示歌单名称和曲目数
   - 卡片接力界面出现，最多 3 张卡片，搜索结果实时流式展示
   - 单击某条搜索结果 → 该卡片消失，下一张滑入
   - 点击 [✕] → 卡片消失，该歌曲标记为跳过
   - 搜索超时/无结果 → 卡片自动关闭
   - 全部处理完毕 → 显示统计结果 → 保存到导入歌单
   - 导入的歌单出现在"导入歌单"Tab 列表中，可播放、可删除
   - 在 AI 对话中粘贴网易云链接 → 自动跳转到导入 Tab 并触发导入
   - 重启 App → 已导入的歌单数据保留
