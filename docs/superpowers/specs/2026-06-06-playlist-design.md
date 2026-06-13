# 歌单管理功能设计文档

## Context

用户需要歌单管理功能，包含两张歌单：当前歌单（待播放队列）和历史歌单（播放记录）。当前从搜索结果或 AI 助手播放歌曲后无法形成播放列表，也无法查看之前听过什么歌。本功能解决这些问题。

## 功能范围

### 当前歌单
- 用户从搜索结果手动添加歌曲，点击播放也自动加入
- 支持拖拽排序和移除单首歌曲
- 支持顺序播放和列表循环两种模式
- 持久化存储，App 重启后保留

### 历史歌单
- 播放超过 30 秒的歌曲自动记录
- 按播放时间倒序排列
- 记录数量可在设置中配置（10/20/30/50/100，默认 20）
- 持久化存储，App 重启后保留
- 支持清空全部历史

### 入口
- 底部导航栏新增"播放列表"标签页（位于对话和歌单之间）
- MiniPlayerBar 右侧新增歌单图标
- NowPlayingOverlay 操作栏新增"歌单"按钮

### 播放模式
- **SEQUENTIAL**（顺序）：播完列表最后一首停止
- **LOOP**（循环）：播完最后一首回到第一首继续

---

## 数据模型

```kotlin
// music/PlaylistManager.kt

data class PlaylistSong(
    val songId: String,
    val songName: String,
    val artist: String,
    val platform: String,
    val coverUrl: String?,
    val playUrl: String?,     // 缓存播放 URL（可能过期）
    val quality: String,
    val addedAt: Long,        // 加入时间戳（毫秒）
    val order: Int,           // 排序序号
)

data class HistorySong(
    val songId: String,
    val songName: String,
    val artist: String,
    val platform: String,
    val coverUrl: String?,
    val playUrl: String?,     // 缓存播放 URL（可能过期）
    val quality: String,
    val playedAt: Long,       // 播放时间戳（毫秒）
)

enum class PlayMode { SEQUENTIAL, LOOP }
```

---

## 架构设计

采用独立 PlaylistManager 服务（参考 DownloadManager 的 expect/actual 模式）：

### PlaylistManager（expect class）

- `addToPlaylist(song: SongMetadata, playUrl: String?)` — 添加歌曲到当前歌单
- `removeFromPlaylist(songId: String)` — 移除歌曲
- `reorderPlaylist(songId: String, newOrder: Int)` — 拖拽排序
- `getPlaylist(): List<PlaylistSong>` — 获取当前歌单
- `addToHistory(song: SongMetadata, playUrl: String?)` — 记录播放历史
- `getHistory(): List<HistorySong>` — 获取历史歌单
- `clearHistory()` — 清空历史
- `setPlayMode(mode: PlayMode)` / `getPlayMode(): PlayMode` — 播放模式
- 历史数量上限由 `SettingsRepository` 管理，PlaylistManager 负责裁剪

### 播放 URL 策略
1. 优先使用 `playUrl` 字段中缓存的 URL
2. 如果播放失败（player playbackError），调用 `searchManager.getPlayUrl()` 重新解析
3. 解析成功后更新缓存

### 历史记录触发
- `MainViewModel` 中播放开始后启动协程 `delay(30_000)`，30 秒后若仍在播放则写入历史

---

## UI 设计

### 导航栏布局
底部导航栏新增"播放列表"标签：
```
对话 | 播放列表 | 歌单 | 设置
```

### 播放列表页面（PlaylistScreen）
- 顶部子标签居中对称排列：`当前歌单` | `历史歌单`
- **当前歌单 tab**：
  - 顶部显示歌曲数量 + 播放模式切换（顺序 / 循环）
  - 歌曲列表：序号 | 封面 | 歌名-歌手 | 拖拽手柄(⋮⋮) | 移除(✕)
  - 当前播放歌曲高亮显示
  - 底部"+ 添加歌曲"按钮 → 跳转到搜索界面
  - 空状态：提示搜索歌曲后添加
- **历史歌单 tab**：
  - 顶部显示记录数量上限 + 设置提示
  - 歌曲列表：封面 | 歌名-歌手 | 相对时间 | 播放(▶) | 添加到歌单(+)
  - 底部"清空历史"按钮

### 搜索结果 — 新增"+ 歌单"按钮
- 每张搜索结果卡片新增"+ 歌单"按钮
- 已添加的显示"已添加 ✓"
- 点击后 toast 提示"已添加到当前歌单"

### MiniPlayerBar — 新增入口
- 右侧（播放/下一首旁）新增歌单图标按钮
- 点击后通过回调打开播放列表页面或 NowPlayingOverlay

### NowPlayingOverlay — 新增入口
- 操作按钮行（下载、歌词旁）新增"歌单"按钮
- 点击后：若有回调则打开播放列表；否则可考虑 toast 提示

---

## 需要新增/修改的文件

### 新增文件
| 文件路径 | 职责 |
|---------|------|
| `shared/.../music/PlaylistManager.kt` | expect class，歌单接口 + 数据模型 + PlayMode 枚举 |
| `shared/.../music/PlaylistManager.android.kt` | actual 实现，SQLite 持久化（三张表：playlist, history, play_mode） |
| `shared/.../ui/playlist/PlaylistScreen.kt` | Compose UI — 播放列表 Tab 页面 |
| `shared/.../ui/playlist/PlaylistViewModel.kt` | 歌单状态管理、播放模式切换、URL 失效重试 |

### 修改文件
| 文件路径 | 改动内容 |
|---------|---------|
| `shared/.../App.kt` | 新增 Screen.Playlist 枚举值；创建 PlaylistManager 实例注入 ViewModel；底部导航栏新增 Tab |
| `shared/.../ui/search/SearchPanel.kt` | SearchResultCard 新增"+ 歌单"按钮（需回调或 ViewModel 注入） |
| `shared/.../ui/search/SearchScreen.kt` | 同上 |
| `shared/.../ui/player/NowPlayingOverlay.kt` | 操作按钮行新增"歌单"按钮 |
| `shared/.../ui/player/MiniPlayerBar.kt` | 右侧新增歌单图标 |
| `shared/.../ui/main/MainViewModel.kt` | 播放开始后启动 30s 计时器写入历史；当前歌单自动添加逻辑 |
| `shared/.../ui/settings/SettingsScreen.kt` | 播放选项区域新增"历史记录数量"下拉选择 |
| `shared/.../ui/settings/SettingsViewModel.kt` | 加载/保存历史数量设置 |
| `shared/.../settings/SettingsKeys.kt` | 新增 HISTORY_COUNT 常量和默认值 |

---

## Verification

1. `./gradlew :androidApp:assembleDebug` 编译通过
2. 手动测试：
   - 搜索歌曲 → 点击"+ 歌单" → 打开播放列表 → 歌曲出现在当前歌单中
   - 直接播放歌曲 → 自动加入当前歌单
   - 拖动排序 → 顺序正确更新
   - 切换循环模式 → 播放完自动循环
   - 播放超过 30s 的歌曲 → 出现在历史歌单中
   - 修改设置中历史数量 → 历史歌单上限更新
   - 重启 App → 歌单数据保留
