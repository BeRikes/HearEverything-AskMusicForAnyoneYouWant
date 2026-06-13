# QQ音乐 & 酷狗音乐歌单导入 实现计划

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 支持用户粘贴 QQ音乐和酷狗音乐歌单分享链接，完成与网易云一致的全流程歌单导入。

**Architecture:** 遵循现有 `NeteaseApi.getPlaylistDetail()` 模式，在 `QQMusicApi` 和 `KugouApi` 中实现同名方法；修正 `PlaylistLinkParser` 的正则以匹配真实分享链接格式；更新 UI 文案从"网易云"改为通用多平台表述。核心导入流程 `PlaylistImportManager` 无需改动——它已是平台无关的。

**Tech Stack:** Kotlin Multiplatform, Ktor HttpClient, kotlinx.serialization, Compose Multiplatform UI

---

### Task 1: 修正 PlaylistLinkParser URL 解析正则

**Files:**
- Modify: `shared/src/commonMain/kotlin/com/example/aimusicplayer/import/PlaylistLinkParser.kt`

- [ ] **Step 1: 替换 QQ音乐和酷狗的正则，并激活解析逻辑**

将现有的两条注释掉的正则替换为匹配真实分享链接的版本，并取消解析逻辑的注释。同时更新 `isMusicLink()` 包含所有三个平台。

```kotlin
object PlaylistLinkParser {

    data class ParseResult(val platform: String, val playlistId: String)

    // 网易云: various URL formats all containing playlist?id= or playlist/<id>
    private val neteasePatterns = listOf(
        Regex("""music\.163\.com.*playlist[/?&]id=(\d+)""", RegexOption.IGNORE_CASE),
        Regex("""music\.163\.com/playlist/(\d+)""", RegexOption.IGNORE_CASE),
    )

    // QQ音乐: share link with id= query param, e.g.
    // https://i2.y.qq.com/n3/other/pages/details/playlist.html?...&id=772368334&...
    private val qqPattern = Regex("""y\.qq\.com.*playlist.*[?&]id=(\d+)""", RegexOption.IGNORE_CASE)

    // 酷狗: share link with gcid_ prefix in path, e.g.
    // https://m.kugou.com/songlist/gcid_3zx4xam7z4z058/...
    private val kugouPattern = Regex("""kugou\.com/songlist/(gcid_[a-zA-Z0-9]+)""", RegexOption.IGNORE_CASE)

    // 合并所有平台的正则，用于 isMusicLink 快速检测
    private val allPatterns = neteasePatterns + qqPattern + kugouPattern

    /**
     * Parse a share link and extract platform + playlist ID.
     *
     * @param url Raw URL pasted by the user
     * @return ParseResult if recognized, null otherwise
     */
    fun parse(url: String): ParseResult? {
        val trimmed = url.trim()

        // 网易云
        for (pattern in neteasePatterns) {
            pattern.find(trimmed)?.let { match ->
                val id = match.groupValues[1]
                if (id.isNotEmpty()) return ParseResult("netease", id)
            }
        }

        // QQ音乐
        qqPattern.find(trimmed)?.let { match ->
            val id = match.groupValues[1]
            if (id.isNotEmpty()) return ParseResult("qq", id)
        }

        // 酷狗
        kugouPattern.find(trimmed)?.let { match ->
            val id = match.groupValues[1]
            if (id.isNotEmpty()) return ParseResult("kugou", id)
        }

        return null
    }

    /** Quick check if a string looks like any music platform link. */
    fun isMusicLink(text: String): Boolean {
        val trimmed = text.trim()
        return allPatterns.any { it.containsMatchIn(trimmed) }
    }
}
```

- [ ] **Step 2: 验证网易云回归**

确认网易云 URL 解析没有受到影响，用思维验证三个格式：
- `https://music.163.com/playlist?id=123456` → ParseResult("netease", "123456") ✓
- `https://music.163.com/#/playlist?id=123456` → 第一个 neteasePattern 匹配 ✓
- `https://y.music.163.com/m/playlist?id=123456` → 第一个 neteasePattern 匹配 ✓

- [ ] **Step 3: Commit**

```bash
git add shared/src/commonMain/kotlin/com/example/aimusicplayer/import/PlaylistLinkParser.kt
git commit -m "feat: activate QQ Music and Kugou URL parsing in PlaylistLinkParser"
```

---

### Task 2: 实现 QQMusicApi.getPlaylistDetail()

**Files:**
- Modify: `shared/src/commonMain/kotlin/com/example/aimusicplayer/network/music/QQMusicApi.kt`
- Modify: `shared/src/commonMain/kotlin/com/example/aimusicplayer/network/music/MusicPlatformApi.kt` (添加响应模型)

- [ ] **Step 1: 在 MusicPlatformApi.kt 中添加 QQ音乐歌单响应模型**

在 `KugouPlayUrlData` 之后（约第 315 行），添加以下序列化类：

```kotlin
// ═══════════════════════════════════════════════════════════════════════════
// QQ Music playlist detail response models
// ═══════════════════════════════════════════════════════════════════════════

@Serializable
internal data class QQPlaylistDetailResponse(
    val code: Int = -1,
    val cdlist: List<QQPlaylistItem>? = null,
)

@Serializable
internal data class QQPlaylistItem(
    val dissname: String = "",
    val logo: String? = null,
    val songlist: List<QQPlaylistSong>? = null,
)

@Serializable
internal data class QQPlaylistSong(
    val songname: String = "",
    val singer: List<QQSingerItem>? = null,
)
```

> 注意：`QQSingerItem` 已在第 242 行定义（用于搜索响应），无需重复定义。

- [ ] **Step 2: 在 QQMusicApi.kt 中实现 getPlaylistDetail()**

在 `getLyrics()` 方法之后、`// ── Helpers ──` 之前插入：

```kotlin
    // ── Playlist Detail ─────────────────────────────────────────────────

    /**
     * Fetch playlist detail from QQ Music.
     *
     * Calls GET /qzone/fcg-bin/fcg_ucc_getcdinfo_byids_cp.fcg with
     * the playlist ID and a computed sign. Extracts playlist name, cover,
     * and all track names/artists.
     */
    override suspend fun getPlaylistDetail(playlistId: String): PlaylistDetail? {
        return try {
            val params = mapOf(
                "format" to "json",
                "disstid" to playlistId,
                "type" to "1",
                "utf8" to "1",
            )
            val sign = MusicSignUtils.qqSign(params)

            val rawText = client.get(
                "${SEARCH_BASE}/qzone/fcg-bin/fcg_ucc_getcdinfo_byids_cp.fcg"
            ) {
                url {
                    params.forEach { (k, v) -> parameters.append(k, v) }
                    parameters.append("sign", sign)
                }
                header("Referer", "https://y.qq.com/")
            }.bodyAsText()

            val json = stripJsonp(rawText)
            println("[QQMusicApi] Playlist detail raw (first 500): ${rawText.take(500)}")
            val response: QQPlaylistDetailResponse = MusicJson.decodeFromString(json)

            if (response.code != 0) {
                println("[QQMusicApi] Playlist detail failed code=${response.code}")
                return null
            }

            val cd = response.cdlist?.firstOrNull() ?: return null
            val tracks = cd.songlist.orEmpty().map { song ->
                val artist = song.singer?.joinToString(" / ") { it.name } ?: "未知歌手"
                PlaylistTrack(songName = song.songname, artist = artist)
            }

            PlaylistDetail(
                playlistId = playlistId,
                title = cd.dissname.ifBlank { "QQ音乐歌单" },
                coverUrl = cd.logo?.replace("http://", "https://"),
                trackCount = tracks.size,
                tracks = tracks,
            )
        } catch (e: Exception) {
            println("[QQMusicApi] PLAYLIST DETAIL EXCEPTION: ${e.javaClass.simpleName}: ${e.message}")
            e.printStackTrace()
            null
        }
    }
```

- [ ] **Step 3: Commit**

```bash
git add shared/src/commonMain/kotlin/com/example/aimusicplayer/network/music/QQMusicApi.kt
git add shared/src/commonMain/kotlin/com/example/aimusicplayer/network/music/MusicPlatformApi.kt
git commit -m "feat: implement QQMusicApi.getPlaylistDetail"
```

---

### Task 3: 实现 KugouApi.getPlaylistDetail()

**Files:**
- Modify: `shared/src/commonMain/kotlin/com/example/aimusicplayer/network/music/KugouApi.kt`
- Modify: `shared/src/commonMain/kotlin/com/example/aimusicplayer/network/music/MusicPlatformApi.kt` (添加响应模型)

- [ ] **Step 1: 在 MusicPlatformApi.kt 中添加酷狗歌单响应模型**

在 `QQPlaylistSong` 之后（Task 2 新加的代码之后），添加：

```kotlin
// ═══════════════════════════════════════════════════════════════════════════
// Kugou playlist detail response models
// ═══════════════════════════════════════════════════════════════════════════

@Serializable
internal data class KugouPlaylistDetailResponse(
    val data: KugouPlaylistDetailData? = null,
    val status: Int = 0,
)

@Serializable
internal data class KugouPlaylistDetailData(
    val info: KugouPlaylistInfo? = null,
)

@Serializable
internal data class KugouPlaylistInfo(
    val name: String = "",
    val img: String? = null,
    val list: List<KugouPlaylistSongInfo>? = null,
)

@Serializable
internal data class KugouPlaylistSongInfo(
    val hash: String = "",
    val songname: String = "",
    val singername: String = "",
    val album_name: String = "",
    val album_id: String = "",
    val duration: Int = 0,
)
```

> 注意：此模型结构与 `KugouSearchData.info` 中的 `KugouSongInfo` 字段重合，但因序列化目标不同（歌单列表 vs 搜索结果），保留独立类型以避免耦合。

- [ ] **Step 2: 在 KugouApi.kt 中实现 getPlaylistDetail()**

在 `getLyrics()` 之后、`// ── Helpers ──` 之前插入：

```kotlin
    // ── Playlist Detail ─────────────────────────────────────────────────

    /**
     * Fetch playlist detail from Kugou Music.
     *
     * Calls GET /api/v3/songlist/info with the global_collection_id
     * (strip "gcid_" prefix from the parsed ID). Uses existing SSL-bypass
     * client and kugouSign signature.
     */
    override suspend fun getPlaylistDetail(playlistId: String): PlaylistDetail? {
        return try {
            // Strip "gcid_" prefix if present (from URL parsing)
            val cleanId = playlistId.removePrefix("gcid_")

            val deviceMid = (1000000000L + kotlin.random.Random.nextLong(8000000000L)).toString()
            val params = mapOf(
                "format" to "json",
                "global_collection_id" to cleanId,
                "page" to "1",
                "pagesize" to "500",
                "plat" to "android",
                "clientver" to "12000",
                "mid" to deviceMid,
            )
            val signature = MusicSignUtils.kugouSign(params)

            val rawText = client.get(
                "${SEARCH_BASE}/api/v3/songlist/info"
            ) {
                url {
                    params.forEach { (k, v) -> parameters.append(k, v) }
                    parameters.append("signature", signature)
                }
                header("Referer", "https://m.kugou.com/")
                header("Cookie", "dfid=$dfid")
                header("User-Agent", "Android")
            }.bodyAsText()

            println("[KugouApi] Playlist detail raw (first 500): ${rawText.take(500)}")
            val response: KugouPlaylistDetailResponse = MusicJson.decodeFromString(rawText)

            if (response.status != 1) {
                println("[KugouApi] Playlist detail failed status=${response.status}")
                return null
            }

            val info = response.data?.info ?: return null
            val tracks = info.list.orEmpty().map { song ->
                PlaylistTrack(songName = song.songname, artist = song.singername)
            }

            PlaylistDetail(
                playlistId = playlistId,
                title = info.name.ifBlank { "酷狗歌单" },
                coverUrl = info.img?.replace("http://", "https://"),
                trackCount = tracks.size,
                tracks = tracks,
            )
        } catch (e: Exception) {
            println("[KugouApi] PLAYLIST DETAIL EXCEPTION: ${e.javaClass.simpleName}: ${e.message}")
            e.printStackTrace()
            null
        }
    }
```

- [ ] **Step 3: Commit**

```bash
git add shared/src/commonMain/kotlin/com/example/aimusicplayer/network/music/KugouApi.kt
git add shared/src/commonMain/kotlin/com/example/aimusicplayer/network/music/MusicPlatformApi.kt
git commit -m "feat: implement KugouApi.getPlaylistDetail"
```

---

### Task 4: UI 文案更新 — 支持多平台

**Files:**
- Modify: `shared/src/commonMain/kotlin/com/example/aimusicplayer/ui/playlist/ImportTab.kt`
- Modify: `shared/src/commonMain/kotlin/com/example/aimusicplayer/import/PlaylistImportManager.kt`

- [ ] **Step 1: 更新 ImportTab.kt — 输入框 placeholder**

第 173 行，将：
```kotlin
placeholder = { Text("粘贴网易云音乐歌单分享链接") },
```
改为：
```kotlin
placeholder = { Text("粘贴音乐平台歌单分享链接") },
```

- [ ] **Step 2: 更新 ImportTab.kt — 空状态提示文案**

第 248 行，将：
```kotlin
Text("粘贴网易云音乐分享链接开始导入", style = MaterialTheme.typography.bodySmall, ...)
```
改为：
```kotlin
Text("粘贴音乐平台分享链接开始导入", style = MaterialTheme.typography.bodySmall, ...)
```

- [ ] **Step 3: 更新 ImportTab.kt — ImportedPlaylistCard 平台名显示**

第 291-292 行，`ImportedPlaylistCard` 中：
```kotlin
Text("$matchedCount/${playlist.songs.size} 首 · 网易云", ...)
```
改为使用平台名映射：
```kotlin
val platformLabel = when (playlist.platform) {
    "netease" -> "网易云"
    "qq" -> "QQ音乐"
    "kugou" -> "酷狗"
    "bilibili" -> "B站"
    else -> playlist.platform
}
Text("$matchedCount/${playlist.songs.size} 首 · $platformLabel", ...)
```

- [ ] **Step 4: 更新 PlaylistImportManager.kt — 链接无法识别的错误提示**

第 45 行，将：
```kotlin
errorMessage = "无法识别的链接，请粘贴网易云音乐歌单分享链接",
```
改为：
```kotlin
errorMessage = "无法识别的链接，请粘贴 QQ音乐/网易云/酷狗 歌单分享链接",
```

- [ ] **Step 5: 验证 UI 改动**

确认以下场景的文案正确：
- 输入框 placeholder: "粘贴音乐平台歌单分享链接"
- 空歌单列表提示: "粘贴音乐平台分享链接开始导入"
- 已导入歌单卡片: 根据 `platform` 字段显示正确平台名（"网易云"/"QQ音乐"/"酷狗"）
- 解析失败错误: "无法识别的链接，请粘贴 QQ音乐/网易云/酷狗 歌单分享链接"

- [ ] **Step 6: Commit**

```bash
git add shared/src/commonMain/kotlin/com/example/aimusicplayer/ui/playlist/ImportTab.kt
git add shared/src/commonMain/kotlin/com/example/aimusicplayer/import/PlaylistImportManager.kt
git commit -m "fix: update import UI copy to support multiple music platforms"
```

---

### Task 5: 集成验证

**Files:**
- 无需修改代码，验证端到端流程

- [ ] **Step 1: 验证 QQ音乐链接导入流程**

用以下链接确认全流程：
```
https://i2.y.qq.com/n3/other/pages/details/playlist.html?platform=11&appshare=android_qq&appversion=20050008&hosteuin=oKCsNenPNK6ion**&id=772368334&ADTAG=wxfshare
```
预期：解析得到 `ParseResult("qq", "772368334")` → 调用 `QQMusicApi.getPlaylistDetail("772368334")` → 获取歌单 → 逐首搜索 → 确认页展示。

- [ ] **Step 2: 验证酷狗链接导入流程**

用以下链接确认全流程：
```
https://m.kugou.com/songlist/gcid_3zx4xam7z4z058/?src_cid=3zx4xam7z4z058&uid=1740652277&chl=wechat&iszlist=1
```
预期：解析得到 `ParseResult("kugou", "gcid_3zx4xam7z4z058")` → 调用 `KugouApi.getPlaylistDetail("gcid_3zx4xam7z4z058")`（内部 strip 前缀） → 获取歌单 → 逐首搜索 → 确认页展示。

- [ ] **Step 3: 验证网易云回归**

用网易云链接确认无回归：
```
https://music.163.com/playlist?id=123456
```
预期：解析得到 `ParseResult("netease", "123456")` → 调用 `NeteaseApi.getPlaylistDetail("123456")` → 全流程正常。

- [ ] **Step 4: 验证非链接输入**

输入 `"周杰伦"` → 预期 `PlaylistLinkParser.parse()` 返回 null → 显示"无法识别的链接"错误。

- [ ] **Step 5: Commit (如有必要)**

如果验证过程中有修正，提交修正。
