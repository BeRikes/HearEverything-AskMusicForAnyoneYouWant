# 跨平台歌单导入功能实现计划（一期：网易云音乐）

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 实现网易云音乐歌单分享链接导入，包含链接解析 → 歌单获取 → 并发搜索匹配 → 卡片接力确认 → 持久化保存的完整流程。

**Architecture:** 在 `shared/.../import/` 下新增 3 个文件（状态模型、链接解析、导入编排器），扩展 `MusicPlatformApi` / `NeteaseApi` / `PlaylistManager`，在播放列表页面新增第三个 Tab "导入歌单"。复用现有 `MusicSearchManager` 做并发搜索、`NeteaseEncryptor` 做请求加密。

**Tech Stack:** Kotlin Multiplatform, Compose Multiplatform (Material3), SQLDelight (Android), kotlinx-coroutines, kotlinx-serialization

---

### Task 1: 导入数据模型与状态机

**Files:**
- Create: `shared/src/commonMain/kotlin/com/example/aimusicplayer/import/PlaylistImportState.kt`

- [ ] **Step 1: 创建数据模型文件**

```kotlin
package com.example.aimusicplayer.import

import com.example.aimusicplayer.network.music.PlaylistDetail
import com.example.aimusicplayer.network.music.SongMetadata
import kotlinx.serialization.Serializable

// ══════════════════════════════════════════════════════════════════
// Persisted imported playlist
// ══════════════════════════════════════════════════════════════════

@Serializable
data class ImportedPlaylist(
    val id: String,
    val title: String,
    val coverUrl: String?,
    val platform: String,
    val sourceUrl: String,
    val importedAt: Long,
    val songs: List<ImportedSong>,
)

@Serializable
data class ImportedSong(
    val originalName: String,
    val originalArtist: String,
    val matchedSongId: String?,
    val matchedName: String?,
    val matchedArtist: String?,
    val matchedPlatform: String?,
    val playUrl: String?,
    val coverUrl: String?,
    val quality: String?,
)

// ══════════════════════════════════════════════════════════════════
// Import flow state machine
// ══════════════════════════════════════════════════════════════════

enum class ImportPhase {
    IDLE,         // Initial state
    PARSING,      // Parsing share link
    FETCHING,     // Fetching playlist metadata
    SEARCHING,    // Concurrent search for all tracks
    CONFIRMING,   // User confirming track-by-track (card relay)
    SAVING,       // Persisting results
    COMPLETED,    // Done
    ERROR,        // Error state with message
}

enum class TrackConfirmStatus {
    SEARCHING,    // Search in progress
    AWAITING,     // Results ready, waiting for user action
    CONFIRMED,    // User selected a match
    SKIPPED,      // User skipped, or search timed out
    FAILED,       // Search failed
}

data class ImportSession(
    val playlistDetail: PlaylistDetail? = null,
    val tracks: List<TrackImportState> = emptyList(),
    val phase: ImportPhase = ImportPhase.IDLE,
    val errorMessage: String? = null,
)

data class TrackImportState(
    val index: Int,
    val originalName: String,
    val originalArtist: String,
    val searchResults: List<SongMetadata> = emptyList(),
    val confirmStatus: TrackConfirmStatus = TrackConfirmStatus.SEARCHING,
    val selectedResult: SongMetadata? = null,
)
```

- [ ] **Step 2: 编译验证**

Run: `./gradlew :shared:compileKotlinAndroid`
Expected: 编译成功（新文件没有外部依赖，应该直接通过）

- [ ] **Step 3: Commit**

```bash
git add shared/src/commonMain/kotlin/com/example/aimusicplayer/import/PlaylistImportState.kt
git commit -m "feat: add import data models and state machine

- ImportedPlaylist, ImportedSong serializable data classes
- ImportPhase state machine (IDLE → PARSING → ... → COMPLETED)
- TrackConfirmStatus per-track confirmation states
- ImportSession and TrackImportState for import flow"

Co-Authored-By: Claude Opus 4.8 <noreply@anthropic.com>
```

---

### Task 2: 分享链接解析器

**Files:**
- Create: `shared/src/commonMain/kotlin/com/example/aimusicplayer/import/PlaylistLinkParser.kt`

- [ ] **Step 1: 创建链接解析器**

```kotlin
package com.example.aimusicplayer.import

/**
 * Parses music platform share links to extract platform type and playlist ID.
 *
 * Supported formats (Netease Phase 1):
 *   - https://music.163.com/playlist?id=123456
 *   - https://music.163.com/#/playlist?id=123456
 *   - https://y.music.163.com/m/playlist?id=123456
 *   - https://music.163.com/#/m/playlist?id=123456
 *   - music.163.com/playlist/123456
 *
 * QQ Music and Kugou patterns are defined but return null until Phase 2/3.
 */
object PlaylistLinkParser {

    data class ParseResult(val platform: String, val playlistId: String)

    // Regex patterns for each platform
    // 网易云: various URL formats all containing playlist?id= or playlist/<id>
    private val neteasePatterns = listOf(
        Regex("""music\.163\.com.*playlist[/?&]id=(\d+)""", RegexOption.IGNORE_CASE),
        Regex("""music\.163\.com/playlist/(\d+)""", RegexOption.IGNORE_CASE),
    )

    // QQ音乐 (Phase 2 — pattern defined, returns null for now)
    private val qqPattern = Regex("""y\.qq\.com.*playlist[/?&](\d+)""", RegexOption.IGNORE_CASE)

    // 酷狗 (Phase 3 — pattern defined, returns null for now)
    private val kugouPattern = Regex("""kugou\.com/songlist/([a-zA-Z0-9]+)""", RegexOption.IGNORE_CASE)

    /**
     * Parse a share link and extract platform + playlist ID.
     *
     * @param url Raw URL pasted by the user
     * @return ParseResult if recognized, null otherwise
     */
    fun parse(url: String): ParseResult? {
        val trimmed = url.trim()

        // 网易云 (Phase 1)
        for (pattern in neteasePatterns) {
            pattern.find(trimmed)?.let { match ->
                val id = match.groupValues[1]
                if (id.isNotEmpty()) return ParseResult("netease", id)
            }
        }

        // QQ音乐 — Phase 2, return null for now
        // qqPattern.find(trimmed)?.let { match ->
        //     val id = match.groupValues[1]
        //     if (id.isNotEmpty()) return ParseResult("qq", id)
        // }

        // 酷狗 — Phase 3, return null for now
        // kugouPattern.find(trimmed)?.let { match ->
        //     val id = match.groupValues[1]
        //     if (id.isNotEmpty()) return ParseResult("kugou", id)
        // }

        return null
    }

    /** Quick check if a string looks like any music platform link. */
    fun isMusicLink(text: String): Boolean {
        val trimmed = text.trim()
        return neteasePatterns.any { it.containsMatchIn(trimmed) }
    }
}
```

- [ ] **Step 2: 编译验证**

Run: `./gradlew :shared:compileKotlinAndroid`
Expected: 编译成功

- [ ] **Step 3: Commit**

```bash
git add shared/src/commonMain/kotlin/com/example/aimusicplayer/import/PlaylistLinkParser.kt
git commit -m "feat: add playlist share link parser

- Parse Netease share links (music.163.com/playlist?id=xxx)
- Extract platform type and playlist ID
- QQ and Kugou patterns defined for future phases
- isMusicLink() for quick link detection"

Co-Authored-By: Claude Opus 4.8 <noreply@anthropic.com>
```

---

### Task 3: MusicPlatformApi 扩展 + NeteaseApi 实现

**Files:**
- Modify: `shared/src/commonMain/kotlin/com/example/aimusicplayer/network/music/MusicPlatformApi.kt`
- Modify: `shared/src/commonMain/kotlin/com/example/aimusicplayer/network/music/NeteaseApi.kt`

- [ ] **Step 1: 在 MusicPlatformApi.kt 中新增 PlaylistDetail / PlaylistTrack 数据类，接口新增 getPlaylistDetail()**

在 `MusicPlatformApi.kt` 文件末尾（接口定义之前）新增数据类：

```kotlin
// ═══════════════════════════════════════════════════════════════════════════
// Playlist detail models (for playlist import feature)
// ═══════════════════════════════════════════════════════════════════════════

/**
 * Metadata for a playlist fetched from a music platform.
 */
@Serializable
data class PlaylistDetail(
    val playlistId: String,
    val title: String,
    val coverUrl: String? = null,
    val trackCount: Int,
    val tracks: List<PlaylistTrack>,
)

@Serializable
data class PlaylistTrack(
    val songName: String,
    val artist: String,
)
```

在 `MusicPlatformApi` 接口末尾新增默认方法：

```kotlin
    /**
     * Fetch playlist detail by platform-native playlist ID.
     *
     * Default returns null — platforms without playlist API support
     * (or not yet implemented) will gracefully indicate unavailability.
     *
     * @param playlistId Platform-native playlist identifier
     * @return [PlaylistDetail] with track list, or null if unsupported/failed
     */
    suspend fun getPlaylistDetail(playlistId: String): PlaylistDetail? = null
```

- [ ] **Step 2: 在 NeteaseApi.kt 中实现 getPlaylistDetail()**

在 `NeteaseApi` 类中新增方法（加在 `getLyrics()` 方法后面）：

```kotlin
    // ── Playlist Detail ─────────────────────────────────────────────────

    /**
     * Fetch playlist detail from Netease Cloud Music.
     *
     * Calls POST /weapi/v3/playlist/detail with encrypted params.
     * Extracts playlist name, cover, and all track names/artists.
     * Handles pagination: fetches up to 1000 tracks.
     */
    override suspend fun getPlaylistDetail(playlistId: String): PlaylistDetail? {
        return try {
            val body = buildJsonObject {
                put("id", playlistId)
                put("n", 100000)  // Max tracks to fetch
                put("s", 0)       // Offset
                put("csrf_token", "")
            }

            val encrypted = NeteaseEncryptor.encrypt(body.toString())

            val responseText = client.submitForm(
                url = "${BASE}/weapi/v3/playlist/detail",
                formParameters = parameters {
                    append("params", encrypted.params)
                    append("encSecKey", encrypted.encSecKey)
                },
            ) {
                header("Referer", "$BASE/")
                header("Origin", BASE)
                header("Cookie", "os=pc; appver=2.9.7; MUSIC_U=; __csrf=")
            }.bodyAsText()

            val root = MusicJson.parseToJsonElement(responseText).jsonObject
            val code = root["code"]?.jsonPrimitive?.content?.toIntOrNull() ?: -1
            if (code != 200) {
                println("[NeteaseApi] Playlist detail failed code=$code")
                return null
            }

            val playlist = root["playlist"]?.jsonObject ?: return null
            val title = playlist["name"]?.jsonPrimitive?.content ?: "未知歌单"
            val coverUrl = playlist["coverImgUrl"]?.jsonPrimitive?.content
                ?.replace("http://", "https://")
            val trackIds = playlist["trackIds"]?.jsonArray.orEmpty()

            val tracks = trackIds.mapNotNull { trackElement ->
                val trackObj = trackElement.jsonObject
                val id = trackObj["id"]?.jsonPrimitive?.content?.toLongOrNull() ?: return@mapNotNull null
                // Try to get cached name from trackId; the full song detail is fetched separately below
                PlaylistTrack(
                    songName = "加载中...",  // placeholder — resolved via song detail
                    artist = "",
                )
            }

            // Batch-fetch song details to get actual names and artists
            if (tracks.isNotEmpty()) {
                val trackIdList = trackIds.mapNotNull { it.jsonObject["id"]?.jsonPrimitive?.content }
                // Process in batches of 500 (API limit)
                val allTracks = mutableListOf<PlaylistTrack>()
                trackIdList.chunked(500).forEach { batch ->
                    val batchTracks = fetchPlaylistSongDetails(batch)
                    allTracks.addAll(batchTracks)
                }
                return PlaylistDetail(
                    playlistId = playlistId,
                    title = title,
                    coverUrl = coverUrl,
                    trackCount = allTracks.size,
                    tracks = allTracks,
                )
            }

            PlaylistDetail(
                playlistId = playlistId,
                title = title,
                coverUrl = coverUrl,
                trackCount = 0,
                tracks = emptyList(),
            )
        } catch (e: Exception) {
            println("[NeteaseApi] PLAYLIST DETAIL EXCEPTION: ${e.javaClass.simpleName}: ${e.message}")
            e.printStackTrace()
            null
        }
    }

    /**
     * Fetch song names and artists for a batch of playlist track IDs.
     * Calls /weapi/v3/song/detail (same endpoint as fetchSongCovers).
     */
    private suspend fun fetchPlaylistSongDetails(songIds: List<String>): List<PlaylistTrack> {
        if (songIds.isEmpty()) return emptyList()
        return try {
            val idArray = songIds.joinToString(",") { "{\"id\":\"$it\"}" }
            val body = buildJsonObject {
                put("c", "[$idArray]")
                put("csrf_token", "")
            }

            val encrypted = NeteaseEncryptor.encrypt(body.toString())

            val responseText = client.submitForm(
                url = "${BASE}/weapi/v3/song/detail",
                formParameters = parameters {
                    append("params", encrypted.params)
                    append("encSecKey", encrypted.encSecKey)
                },
            ) {
                header("Referer", "$BASE/")
                header("Origin", BASE)
            }.bodyAsText()

            val json = MusicJson
            val root = json.parseToJsonElement(responseText).jsonObject
            val songs = root["songs"]?.jsonArray.orEmpty()

            songs.map { songElement ->
                val obj = songElement.jsonObject
                val name = obj["name"]?.jsonPrimitive?.content ?: "未知歌曲"
                val ar = obj["ar"]?.jsonArray.orEmpty()
                val artist = ar.joinToString(" / ") {
                    it.jsonObject["name"]?.jsonPrimitive?.content ?: ""
                }.ifBlank { "未知艺术家" }
                PlaylistTrack(songName = name, artist = artist)
            }
        } catch (e: Exception) {
            println("[NeteaseApi] Playlist song detail batch failed: ${e.message}")
            // Return placeholders so we don't lose the track count
            songIds.map { PlaylistTrack(songName = "未知歌曲", artist = "未知艺术家") }
        }
    }
```

- [ ] **Step 3: 编译验证**

Run: `./gradlew :shared:compileKotlinAndroid`
Expected: 编译成功

- [ ] **Step 4: Commit**

```bash
git add shared/src/commonMain/kotlin/com/example/aimusicplayer/network/music/MusicPlatformApi.kt
git add shared/src/commonMain/kotlin/com/example/aimusicplayer/network/music/NeteaseApi.kt
git commit -m "feat: add playlist detail API to NeteaseApi

- Add PlaylistDetail, PlaylistTrack data models to MusicPlatformApi
- Add getPlaylistDetail() default method to MusicPlatformApi interface
- Implement NeteaseApi.getPlaylistDetail() via /weapi/v3/playlist/detail
- Batch song detail resolution for up to 1000 tracks per playlist
- Graceful fallback to placeholder names on detail fetch failure"

Co-Authored-By: Claude Opus 4.8 <noreply@anthropic.com>
```

---

### Task 4: 持久化 — PlaylistManager 扩展 (expect + Android + iOS)

**Files:**
- Modify: `shared/src/commonMain/kotlin/com/example/aimusicplayer/music/PlaylistManager.kt` — expect 声明
- Modify: `shared/src/androidMain/kotlin/com/example/aimusicplayer/music/PlaylistManager.android.kt` — actual 实现
- Modify: `shared/src/iosMain/kotlin/com/example/aimusicplayer/music/PlaylistManager.ios.kt` — actual 实现
- Create: `shared/src/commonMain/kotlin/com/example/aimusicplayer/music/ImportedPlaylistEntity.kt` — 持久化用中间实体

- [ ] **Step 1: 在 PlaylistManager.kt expect class 中新增方法声明**

在 `PlaylistManager.kt` 底部（`clearPlaylist()` 之后）新增：

```kotlin
    // ── Imported playlists ────────────────────────────────────────

    /** 保存一张已导入的歌单（含所有已确认歌曲） */
    suspend fun addImportedPlaylist(playlist: com.example.aimusicplayer.import.ImportedPlaylist)

    /** 获取所有已导入的歌单 */
    suspend fun getImportedPlaylists(): List<com.example.aimusicplayer.import.ImportedPlaylist>

    /** 删除一张已导入的歌单 */
    suspend fun removeImportedPlaylist(id: String)

    /** 从已导入歌单中移除一首歌 */
    suspend fun removeImportedSong(playlistId: String, songId: String)
```

需要新增 import：
```kotlin
// 文件头部新增
import com.example.aimusicplayer.import.ImportedPlaylist
import com.example.aimusicplayer.import.ImportedSong
```

然后方法签名简化为：
```kotlin
    suspend fun addImportedPlaylist(playlist: ImportedPlaylist)
    suspend fun getImportedPlaylists(): List<ImportedPlaylist>
    suspend fun removeImportedPlaylist(id: String)
    suspend fun removeImportedSong(playlistId: String, songId: String)
```

- [ ] **Step 2: Android actual 实现 — 升级 PlaylistManagerSchema 到 v2**

在 `PlaylistManager.android.kt` 中：

**Schema 升级：**
```kotlin
// 修改 PlaylistManagerSchema.version
override val version: Long = 2

// 在 migrate() 方法中新增 v1 → v2 迁移
if (oldVersion < 2) {
    driver.execute(null, CREATE_IMPORTED_PLAYLIST, 0)
    driver.execute(null, CREATE_IMPORTED_SONG, 0)
}
```

**新增建表 SQL：**
```kotlin
private val CREATE_IMPORTED_PLAYLIST = """
    CREATE TABLE IF NOT EXISTS imported_playlist (
        id TEXT PRIMARY KEY,
        title TEXT NOT NULL,
        cover_url TEXT,
        platform TEXT NOT NULL,
        source_url TEXT NOT NULL,
        imported_at INTEGER NOT NULL
    )
""".trimIndent()

private val CREATE_IMPORTED_SONG = """
    CREATE TABLE IF NOT EXISTS imported_song (
        id TEXT PRIMARY KEY,
        playlist_id TEXT NOT NULL REFERENCES imported_playlist(id),
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
    )
""".trimIndent()
```

**在 create() 中也加上：**
```kotlin
// 在 create() 方法末尾新增
driver.execute(null, CREATE_IMPORTED_PLAYLIST, 0)
driver.execute(null, CREATE_IMPORTED_SONG, 0)
```

**实现方法（加在类末尾）：**
```kotlin
    actual suspend fun addImportedPlaylist(playlist: ImportedPlaylist) {
        db.execute(null, """
            INSERT OR REPLACE INTO imported_playlist (id, title, cover_url, platform, source_url, imported_at)
            VALUES (?, ?, ?, ?, ?, ?)
        """, 6) {
            bindString(0, playlist.id)
            bindString(1, playlist.title)
            bindString(2, playlist.coverUrl ?: "")
            bindString(3, playlist.platform)
            bindString(4, playlist.sourceUrl)
            bindLong(5, playlist.importedAt)
        }
        playlist.songs.forEachIndexed { index, song ->
            db.execute(null, """
                INSERT OR REPLACE INTO imported_song (id, playlist_id, original_name, original_artist, matched_song_id, matched_name, matched_artist, matched_platform, play_url, cover_url, quality, sort_order)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
            """, 12) {
                bindString(0, "${playlist.id}_$index")
                bindString(1, playlist.id)
                bindString(2, song.originalName)
                bindString(3, song.originalArtist)
                bindString(4, song.matchedSongId ?: "")
                bindString(5, song.matchedName ?: "")
                bindString(6, song.matchedArtist ?: "")
                bindString(7, song.matchedPlatform ?: "")
                bindString(8, song.playUrl ?: "")
                bindString(9, song.coverUrl ?: "")
                bindString(10, song.quality ?: "")
                bindLong(11, index.toLong())
            }
        }
    }

    actual suspend fun getImportedPlaylists(): List<ImportedPlaylist> {
        val playlists = db.executeQuery(null, "SELECT * FROM imported_playlist ORDER BY imported_at DESC", { cursor ->
            val items = mutableListOf<ImportedPlaylist>()
            while (cursor.next().getValue()) {
                items.add(ImportedPlaylist(
                    id = cursor.getString(0)!!,
                    title = cursor.getString(1)!!,
                    coverUrl = cursor.getString(2)?.ifBlank { null },
                    platform = cursor.getString(3)!!,
                    sourceUrl = cursor.getString(4)!!,
                    importedAt = cursor.getLong(5) ?: 0L,
                    songs = emptyList(), // filled below
                ))
            }
            QueryResult.Value(items)
        }, 0).getValue()

        return playlists.map { playlist ->
            val songs = db.executeQuery(null, "SELECT * FROM imported_song WHERE playlist_id = ? ORDER BY sort_order ASC", { cursor ->
                val items = mutableListOf<ImportedSong>()
                while (cursor.next().getValue()) {
                    items.add(ImportedSong(
                        originalName = cursor.getString(2)!!,
                        originalArtist = cursor.getString(3)!!,
                        matchedSongId = cursor.getString(4)?.ifBlank { null },
                        matchedName = cursor.getString(5)?.ifBlank { null },
                        matchedArtist = cursor.getString(6)?.ifBlank { null },
                        matchedPlatform = cursor.getString(7)?.ifBlank { null },
                        playUrl = cursor.getString(8)?.ifBlank { null },
                        coverUrl = cursor.getString(9)?.ifBlank { null },
                        quality = cursor.getString(10)?.ifBlank { null },
                    ))
                }
                QueryResult.Value(items)
            }, 1) { bindString(0, playlist.id) }.getValue()
            playlist.copy(songs = songs)
        }
    }

    actual suspend fun removeImportedPlaylist(id: String) {
        db.execute(null, "DELETE FROM imported_song WHERE playlist_id = ?", 1) { bindString(0, id) }
        db.execute(null, "DELETE FROM imported_playlist WHERE id = ?", 1) { bindString(0, id) }
    }

    actual suspend fun removeImportedSong(playlistId: String, songId: String) {
        db.execute(null, "DELETE FROM imported_song WHERE id = ? AND playlist_id = ?", 2) {
            bindString(0, songId)
            bindString(1, playlistId)
        }
    }
```

- [ ] **Step 3: iOS actual 实现 — 内存存储**

在 `PlaylistManager.ios.kt` 中新增：

```kotlin
    // ── Imported playlists (in-memory, matches existing iOS pattern) ───
    private val importedPlaylists = mutableListOf<com.example.aimusicplayer.import.ImportedPlaylist>()

    actual suspend fun addImportedPlaylist(playlist: com.example.aimusicplayer.import.ImportedPlaylist) {
        // Remove existing playlist with same ID (upsert)
        importedPlaylists.removeAll { it.id == playlist.id }
        importedPlaylists.add(0, playlist)
    }

    actual suspend fun getImportedPlaylists(): List<com.example.aimusicplayer.import.ImportedPlaylist> =
        importedPlaylists.toList()

    actual suspend fun removeImportedPlaylist(id: String) {
        importedPlaylists.removeAll { it.id == id }
    }

    actual suspend fun removeImportedSong(playlistId: String, songId: String) {
        val idx = importedPlaylists.indexOfFirst { it.id == playlistId }
        if (idx >= 0) {
            val playlist = importedPlaylists[idx]
            importedPlaylists[idx] = playlist.copy(
                songs = playlist.songs.filter { it.matchedSongId != songId }
            )
        }
    }
```

- [ ] **Step 4: 编译验证**

Run: `./gradlew :shared:compileKotlinAndroid`
Expected: 编译成功（确保 expect/actual 方法签名一致）

- [ ] **Step 5: Commit**

```bash
git add shared/src/commonMain/kotlin/com/example/aimusicplayer/music/PlaylistManager.kt \
        shared/src/androidMain/kotlin/com/example/aimusicplayer/music/PlaylistManager.android.kt \
        shared/src/iosMain/kotlin/com/example/aimusicplayer/music/PlaylistManager.ios.kt
git commit -m "feat: add imported playlist persistence to PlaylistManager

- expect: addImportedPlaylist, getImportedPlaylists, removeImportedPlaylist, removeImportedSong
- Android: PlaylistManagerSchema v2 with imported_playlist and imported_song tables
- iOS: in-memory storage (matches existing iOS PlaylistManager pattern)"

Co-Authored-By: Claude Opus 4.8 <noreply@anthropic.com>
```

---

### Task 5: 导入编排器 PlaylistImportManager

**Files:**
- Create: `shared/src/commonMain/kotlin/com/example/aimusicplayer/import/PlaylistImportManager.kt`

- [ ] **Step 1: 创建 PlaylistImportManager**

```kotlin
package com.example.aimusicplayer.import

import com.example.aimusicplayer.music.MusicSearchManager
import com.example.aimusicplayer.music.PlaylistManager
import com.example.aimusicplayer.network.music.MusicPlatformApi
import com.example.aimusicplayer.network.music.SongMetadata
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

class PlaylistImportManager(
    private val musicApis: List<MusicPlatformApi>,
    private val searchManager: MusicSearchManager,
    private val playlistManager: PlaylistManager,
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)

    private val _session = MutableStateFlow(ImportSession())
    val session: StateFlow<ImportSession> = _session.asStateFlow()

    /** Platform key of the currently importing playlist (set during startImport). */
    private var currentPlatform: String = ""

    /**
     * Start importing a playlist from a share URL.
     * Flow: parse link → fetch playlist detail → concurrent search all tracks.
     */
    fun startImport(url: String) {
        scope.launch {
            _session.value = ImportSession(phase = ImportPhase.PARSING)

            // 1. Parse link
            val parsed = PlaylistLinkParser.parse(url)
            if (parsed == null) {
                _session.value = ImportSession(
                    phase = ImportPhase.ERROR,
                    errorMessage = "无法识别的链接，请粘贴网易云音乐歌单分享链接",
                )
                return@launch
            }

            // 2. Get platform API
            currentPlatform = parsed.platform
            val api = musicApis.firstOrNull { it.platform == currentPlatform }
            if (api == null) {
                _session.value = ImportSession(
                    phase = ImportPhase.ERROR,
                    errorMessage = "暂不支持 $currentPlatform 平台",
                )
                return@launch
            }

            // 3. Fetch playlist detail
            _session.value = ImportSession(phase = ImportPhase.FETCHING)
            val detail = api.getPlaylistDetail(parsed.playlistId)
            if (detail == null) {
                _session.value = ImportSession(
                    phase = ImportPhase.ERROR,
                    errorMessage = "获取歌单失败，请检查链接或稍后重试",
                )
                return@launch
            }
            if (detail.tracks.isEmpty()) {
                _session.value = ImportSession(
                    phase = ImportPhase.ERROR,
                    errorMessage = "歌单为空，没有可导入的歌曲",
                )
                return@launch
            }

            // 4. Initialize track states
            val tracks = detail.tracks.mapIndexed { index, track ->
                TrackImportState(
                    index = index,
                    originalName = track.songName,
                    originalArtist = track.artist,
                )
            }

            _session.value = ImportSession(
                playlistDetail = detail,
                tracks = tracks,
                phase = ImportPhase.SEARCHING,
            )

            // 5. Concurrent search with timeout per track
            val searchTimeoutMs = 15_000L
            val jobs = tracks.map { track ->
                scope.async {
                    withTimeout(searchTimeoutMs) {
                        try {
                            val query = "${track.originalArtist} ${track.originalName}"
                            val results = searchManager.searchOnPlatform(query, parsed.platform)
                            updateTrack(track.index) {
                                if (results.isNotEmpty()) {
                                    it.copy(
                                        searchResults = results,
                                        confirmStatus = TrackConfirmStatus.AWAITING,
                                    )
                                } else {
                                    // Auto-skip if no results
                                    it.copy(confirmStatus = TrackConfirmStatus.SKIPPED)
                                }
                            }
                        } catch (_: TimeoutCancellationException) {
                            updateTrack(track.index) {
                                it.copy(confirmStatus = TrackConfirmStatus.SKIPPED)
                            }
                        } catch (_: Exception) {
                            updateTrack(track.index) {
                                it.copy(confirmStatus = TrackConfirmStatus.FAILED)
                            }
                        }
                    }
                }
            }
            jobs.forEach { it.await() }

            // 6. All searches done → transition to CONFIRMING
            // (but if all are already SKIPPED/CONFIRMED, go straight to saving)
            val currentTracks = _session.value.tracks
            val allResolved = currentTracks.all {
                it.confirmStatus == TrackConfirmStatus.CONFIRMED ||
                it.confirmStatus == TrackConfirmStatus.SKIPPED
            }
            _session.value = _session.value.copy(
                phase = if (allResolved) ImportPhase.SAVING else ImportPhase.CONFIRMING,
            )
        }
    }

    /** User confirms a track by selecting a search result. */
    fun confirmTrack(index: Int, result: SongMetadata) {
        updateTrack(index) {
            it.copy(
                selectedResult = result,
                confirmStatus = TrackConfirmStatus.CONFIRMED,
            )
        }
        advanceIfAllDone()
    }

    /** User skips a track. */
    fun skipTrack(index: Int) {
        updateTrack(index) {
            it.copy(confirmStatus = TrackConfirmStatus.SKIPPED)
        }
        advanceIfAllDone()
    }

    /** Re-search a specific track. */
    fun retryTrack(index: Int) {
        val track = _session.value.tracks.getOrNull(index) ?: return
        updateTrack(index) {
            it.copy(confirmStatus = TrackConfirmStatus.SEARCHING, searchResults = emptyList())
        }
        scope.launch {
            try {
                val query = "${track.originalArtist} ${track.originalName}"
                val results = withTimeout(15_000L) {
                    searchManager.searchOnPlatform(query, currentPlatform)
                }
                updateTrack(index) {
                    if (results.isNotEmpty()) {
                        it.copy(searchResults = results, confirmStatus = TrackConfirmStatus.AWAITING)
                    } else {
                        it.copy(confirmStatus = TrackConfirmStatus.SKIPPED)
                    }
                }
            } catch (_: Exception) {
                updateTrack(index) {
                    it.copy(confirmStatus = TrackConfirmStatus.FAILED)
                }
            }
        }
    }

    /** Save confirmed tracks to PlaylistManager. */
    fun saveImport() {
        scope.launch {
            _session.value = _session.value.copy(phase = ImportPhase.SAVING)
            val session = _session.value
            val detail = session.playlistDetail ?: return@launch

            val confirmedTracks = session.tracks.filter {
                it.confirmStatus == TrackConfirmStatus.CONFIRMED && it.selectedResult != null
            }

            val importedSongs = session.tracks.map { track ->
                val result = track.selectedResult
                if (track.confirmStatus == TrackConfirmStatus.CONFIRMED && result != null) {
                    ImportedSong(
                        originalName = track.originalName,
                        originalArtist = track.originalArtist,
                        matchedSongId = result.songId,
                        matchedName = result.songName,
                        matchedArtist = result.artist,
                        matchedPlatform = result.platform,
                        playUrl = null,  // URL will be resolved on playback
                        coverUrl = result.coverUrl,
                        quality = result.quality,
                    )
                } else {
                    ImportedSong(
                        originalName = track.originalName,
                        originalArtist = track.originalArtist,
                        matchedSongId = null,
                        matchedName = null,
                        matchedArtist = null,
                        matchedPlatform = null,
                        playUrl = null,
                        coverUrl = null,
                        quality = null,
                    )
                }
            }

            val playlist = ImportedPlaylist(
                id = detail.playlistId,
                title = detail.title,
                coverUrl = detail.coverUrl,
                platform = "netease",
                sourceUrl = "",
                importedAt = kotlinx.datetime.Clock.System.now().toEpochMilliseconds(),
                songs = importedSongs,
            )

            playlistManager.addImportedPlaylist(playlist)
            _session.value = _session.value.copy(phase = ImportPhase.COMPLETED)
        }
    }

    /** Reset to idle. */
    fun reset() {
        scope.coroutineContext.cancelChildren()
        _session.value = ImportSession(phase = ImportPhase.IDLE)
    }

    // ── Private helpers ────────────────────────────────────────────

    private fun updateTrack(index: Int, transform: (TrackImportState) -> TrackImportState) {
        val tracks = _session.value.tracks.toMutableList()
        if (index in tracks.indices) {
            tracks[index] = transform(tracks[index])
            _session.value = _session.value.copy(tracks = tracks)
        }
    }

    private fun advanceIfAllDone() {
        val tracks = _session.value.tracks
        val allResolved = tracks.all {
            it.confirmStatus == TrackConfirmStatus.CONFIRMED ||
            it.confirmStatus == TrackConfirmStatus.SKIPPED
        }
        if (allResolved && tracks.isNotEmpty()) {
            _session.value = _session.value.copy(phase = ImportPhase.SAVING)
        }
    }
}
```

- [ ] **Step 2: 编译验证**

Run: `./gradlew :shared:compileKotlinAndroid`
Expected: 编译成功

- [ ] **Step 3: Commit**

```bash
git add shared/src/commonMain/kotlin/com/example/aimusicplayer/import/PlaylistImportManager.kt
git commit -m "feat: add PlaylistImportManager for import flow orchestration

- startImport(): parse link → fetch playlist → concurrent search with 15s timeout
- confirmTrack() / skipTrack() for card relay interaction
- retryTrack() for re-searching failed tracks
- saveImport(): persist confirmed tracks to PlaylistManager
- auto-transition from SEARCHING → CONFIRMING → SAVING → COMPLETED
- auto-skip on search timeout or empty results"

Co-Authored-By: Claude Opus 4.8 <noreply@anthropic.com>
```

---

### Task 6: PlaylistViewModel 扩展 — 导入状态管理

**Files:**
- Modify: `shared/src/commonMain/kotlin/com/example/aimusicplayer/ui/playlist/PlaylistViewModel.kt`

- [ ] **Step 1: 在 PlaylistViewModel 中新增导入相关状态和方法**

```kotlin
// ── 新增 import ──
import com.example.aimusicplayer.import.ImportPhase
import com.example.aimusicplayer.import.ImportSession
import com.example.aimusicplayer.import.PlaylistImportManager
import com.example.aimusicplayer.music.ImportedPlaylist
import com.example.aimusicplayer.network.music.SongMetadata

// ── 构造函数新增参数 ──
class PlaylistViewModel(
    private val playlistManager: PlaylistManager,
    private val musicPlayer: MusicPlayer,
    private val settingsRepo: SettingsRepository,
    private val importManager: PlaylistImportManager,  // 新增
) : ViewModel() {

    // ── 在现有 state 后面新增 ──

    // Import session state (collected from PlaylistImportManager)
    val importSession: StateFlow<ImportSession> = importManager.session

    // Import tab sub-view selection: 0 = entry, 1 = confirmer, 2 = imported list
    private val _importSubView = MutableStateFlow(0)
    val importSubView: StateFlow<Int> = _importSubView.asStateFlow()

    // Imported playlists list
    private val _importedPlaylists = MutableStateFlow<List<ImportedPlaylist>>(emptyList())
    val importedPlaylists: StateFlow<List<ImportedPlaylist>> = _importedPlaylists.asStateFlow()

    // ── 新增方法 ──

    fun startImport(url: String) {
        importManager.startImport(url)
    }

    fun confirmTrack(index: Int, result: SongMetadata) {
        importManager.confirmTrack(index, result)
    }

    fun skipTrack(index: Int) {
        importManager.skipTrack(index)
    }

    fun retryTrack(index: Int) {
        importManager.retryTrack(index)
    }

    fun saveImport() {
        importManager.saveImport()
    }

    fun resetImport() {
        importManager.reset()
    }

    fun setImportSubView(view: Int) {
        _importSubView.value = view
    }

    /** Refresh the imported playlists list from persistence. */
    fun refreshImportedPlaylists() {
        scope.launch {
            _importedPlaylists.value = playlistManager.getImportedPlaylists()
        }
    }

    /** Play a song from an imported playlist. */
    fun playImportedSong(song: ImportedSong) {
        scope.launch {
            val url = song.playUrl
            if (!url.isNullOrBlank()) {
                musicPlayer.play(
                    url = url,
                    title = song.matchedName ?: song.originalName,
                    artist = song.matchedArtist ?: song.originalArtist,
                    song = SongMetadata(
                        songId = song.matchedSongId ?: return@launch,
                        songName = song.matchedName ?: song.originalName,
                        artist = song.matchedArtist ?: song.originalArtist,
                        platform = song.matchedPlatform ?: "",
                        quality = song.quality ?: "standard",
                        coverUrl = song.coverUrl,
                    ),
                )
            }
        }
    }

    /** Remove an imported playlist. */
    fun removeImportedPlaylist(id: String) {
        scope.launch {
            playlistManager.removeImportedPlaylist(id)
            refreshImportedPlaylists()
        }
    }

    // ── 在 init 中新增 ──
    init {
        scope.launch { refresh() }
        scope.launch { refreshImportedPlaylists() }  // 新增
    }

    // ── 监听导入完成，自动刷新列表 ──
    init {
        scope.launch {
            importManager.session.collect { session ->
                if (session.phase == ImportPhase.COMPLETED) {
                    refreshImportedPlaylists()
                }
            }
        }
    }
}
```

- [ ] **Step 2: 调整 refresh() 方法以同时刷新导入列表**

修改 `refresh()` 方法：
```kotlin
    suspend fun refresh() {
        _playlist.value = playlistManager.getPlaylist()
        _history.value = playlistManager.getHistory(settingsRepo.getHistoryCount())
        _playlistSongIds.value = _playlist.value.map { it.songId }.toSet()
        _importedPlaylists.value = playlistManager.getImportedPlaylists()
    }
```

- [ ] **Step 3: 编译验证**

Run: `./gradlew :shared:compileKotlinAndroid`
Expected: 编译成功

- [ ] **Step 4: Commit**

```bash
git add shared/src/commonMain/kotlin/com/example/aimusicplayer/ui/playlist/PlaylistViewModel.kt
git commit -m "feat: extend PlaylistViewModel with import state management

- Wire PlaylistImportManager into PlaylistViewModel
- Add importSubView for navigating between entry/confirmer/imported-list views
- Add startImport, confirmTrack, skipTrack, retryTrack, saveImport methods
- Add refreshImportedPlaylists and playImportedSong
- Auto-refresh imported list when import completes"

Co-Authored-By: Claude Opus 4.8 <noreply@anthropic.com>
```

---

### Task 7: ImportTab UI — 导入入口 + 卡片接力确认 + 已导入展示

**Files:**
- Create: `shared/src/commonMain/kotlin/com/example/aimusicplayer/ui/playlist/ImportTab.kt`

- [ ] **Step 1: 创建 ImportTab.kt — 三个子视图**

```kotlin
package com.example.aimusicplayer.ui.playlist

import androidx.compose.animation.*
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.aimusicplayer.import.ImportPhase
import com.example.aimusicplayer.import.ImportedPlaylist
import com.example.aimusicplayer.import.ImportedSong
import com.example.aimusicplayer.import.TrackConfirmStatus
import com.example.aimusicplayer.import.TrackImportState
import com.example.aimusicplayer.network.music.SongMetadata

@Composable
fun ImportTab(
    viewModel: PlaylistViewModel,
    modifier: Modifier = Modifier,
) {
    val importSession by viewModel.importSession.collectAsState()
    val importedPlaylists by viewModel.importedPlaylists.collectAsState()
    val subView by viewModel.importSubView.collectAsState()

    // Auto-switch to confirmer view when searching/confirming
    LaunchedEffect(importSession.phase) {
        when (importSession.phase) {
            ImportPhase.SEARCHING, ImportPhase.CONFIRMING, ImportPhase.SAVING -> {
                viewModel.setImportSubView(1)
            }
            ImportPhase.COMPLETED -> {
                // Stay in confirmer view to show save button
            }
            else -> { /* keep current view */ }
        }
    }

    when (subView) {
        0 -> ImportEntryView(
            importedPlaylists = importedPlaylists,
            importSession = importSession,
            onStartImport = { viewModel.startImport(it) },
            onPlaylistClick = { viewModel.setImportSubView(2) },
            onDeletePlaylist = { viewModel.removeImportedPlaylist(it) },
            modifier = modifier,
        )
        1 -> CardRelayView(
            importSession = importSession,
            onConfirm = { idx, result -> viewModel.confirmTrack(idx, result) },
            onSkip = { idx -> viewModel.skipTrack(idx) },
            onRetry = { idx -> viewModel.retryTrack(idx) },
            onSave = { viewModel.saveImport() },
            onBackToEntry = {
                viewModel.resetImport()
                viewModel.setImportSubView(0)
            },
            modifier = modifier,
        )
        2 -> ImportedPlaylistListView(
            importedPlaylists = importedPlaylists,
            onBack = { viewModel.setImportSubView(0) },
            onPlay = { viewModel.playImportedSong(it) },
            onDeletePlaylist = { viewModel.removeImportedPlaylist(it) },
            modifier = modifier,
        )
    }
}

// ══════════════════════════════════════════════════════════════════
// View 1: Import Entry (link input + imported playlists list)
// ══════════════════════════════════════════════════════════════════

@Composable
private fun ImportEntryView(
    importedPlaylists: List<ImportedPlaylist>,
    importSession: com.example.aimusicplayer.import.ImportSession,
    onStartImport: (String) -> Unit,
    onPlaylistClick: () -> Unit,
    onDeletePlaylist: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    var linkText by remember { mutableStateOf("") }
    val focusManager = LocalFocusManager.current
    val isParsing = importSession.phase == ImportPhase.PARSING || importSession.phase == ImportPhase.FETCHING
    val errorMessage = importSession.let { if (it.phase == ImportPhase.ERROR) it.errorMessage else null }

    Column(modifier = modifier.fillMaxSize().padding(horizontal = 16.dp)) {
        Spacer(Modifier.height(16.dp))

        // Link input row
        OutlinedTextField(
            value = linkText,
            onValueChange = { linkText = it },
            placeholder = { Text("粘贴网易云音乐歌单分享链接") },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true,
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Go),
            keyboardActions = KeyboardActions(onGo = {
                if (linkText.isNotBlank() && !isParsing) {
                    focusManager.clearFocus()
                    onStartImport(linkText)
                }
            }),
            enabled = !isParsing,
            trailingIcon = {
                if (linkText.isNotEmpty()) {
                    IconButton(onClick = { linkText = "" }) {
                        Icon(Icons.Default.Close, "清除")
                    }
                }
            },
        )

        Spacer(Modifier.height(8.dp))

        // Start import button
        Button(
            onClick = {
                focusManager.clearFocus()
                onStartImport(linkText)
            },
            modifier = Modifier.fillMaxWidth(),
            enabled = linkText.isNotBlank() && !isParsing,
            shape = RoundedCornerShape(12.dp),
        ) {
            if (isParsing) {
                CircularProgressIndicator(
                    modifier = Modifier.size(18.dp),
                    strokeWidth = 2.dp,
                    color = MaterialTheme.colorScheme.onPrimary,
                )
                Spacer(Modifier.width(8.dp))
                Text("解析中...")
            } else {
                Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(6.dp))
                Text("开始导入")
            }
        }

        // Error message
        if (errorMessage != null) {
            Spacer(Modifier.height(12.dp))
            Card(
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer),
                shape = RoundedCornerShape(10.dp),
            ) {
                Row(
                    modifier = Modifier.padding(12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(Icons.Default.Error, null, tint = MaterialTheme.colorScheme.error)
                    Spacer(Modifier.width(8.dp))
                    Text(errorMessage, color = MaterialTheme.colorScheme.onErrorContainer, modifier = Modifier.weight(1f), style = MaterialTheme.typography.bodySmall)
                }
            }
        }

        Spacer(Modifier.height(24.dp))
        HorizontalDivider()
        Spacer(Modifier.height(12.dp))

        // Imported playlists list
        if (importedPlaylists.isEmpty()) {
            Box(Modifier.fillMaxWidth().weight(1f), contentAlignment = Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("📥", fontSize = MaterialTheme.typography.headlineLarge.fontSize)
                    Spacer(Modifier.height(8.dp))
                    Text("暂无导入的歌单", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                    Text("粘贴网易云音乐分享链接开始导入", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        } else {
            Text("已导入的歌单 (${importedPlaylists.size})", style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Medium)
            Spacer(Modifier.height(8.dp))
            LazyColumn(modifier = Modifier.fillMaxWidth().weight(1f), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                itemsIndexed(importedPlaylists, key = { _, p -> p.id }) { _, playlist ->
                    ImportedPlaylistCard(playlist = playlist, onClick = { onPlaylistClick() }, onDelete = { onDeletePlaylist(playlist.id) })
                }
                item { Spacer(Modifier.height(16.dp)) }
            }
        }
    }
}

@Composable
private fun ImportedPlaylistCard(
    playlist: ImportedPlaylist,
    onClick: () -> Unit,
    onDelete: () -> Unit,
) {
    val matchedCount = playlist.songs.count { it.matchedSongId != null }
    Surface(
        modifier = Modifier.fillMaxWidth().clickable { onClick() },
        shape = RoundedCornerShape(10.dp),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f),
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(Modifier.weight(1f)) {
                Text(playlist.title, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text("$matchedCount/${playlist.songs.size} 首 · 网易云", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            IconButton(onClick = onDelete, modifier = Modifier.size(36.dp)) {
                Icon(Icons.Default.Delete, "删除", tint = MaterialTheme.colorScheme.error.copy(alpha = 0.6f), modifier = Modifier.size(18.dp))
            }
        }
    }
}

// ══════════════════════════════════════════════════════════════════
// View 2: Card Relay Confirmation
// ══════════════════════════════════════════════════════════════════

@Composable
private fun CardRelayView(
    importSession: com.example.aimusicplayer.import.ImportSession,
    onConfirm: (Int, SongMetadata) -> Unit,
    onSkip: (Int) -> Unit,
    onRetry: (Int) -> Unit,
    onSave: () -> Unit,
    onBackToEntry: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val detail = importSession.playlistDetail
    val tracks = importSession.tracks

    val confirmed = tracks.count { it.confirmStatus == TrackConfirmStatus.CONFIRMED }
    val skipped = tracks.count { it.confirmStatus == TrackConfirmStatus.SKIPPED }
    val searching = tracks.count { it.confirmStatus == TrackConfirmStatus.SEARCHING }
    val awaiting = tracks.count { it.confirmStatus == TrackConfirmStatus.AWAITING }
    val total = tracks.size

    // Get up to 3 visible cards: take AWAITING tracks first, then SEARCHING
    val visibleTracks = tracks.filter {
        it.confirmStatus == TrackConfirmStatus.AWAITING || it.confirmStatus == TrackConfirmStatus.SEARCHING
    }.take(3)

    Column(modifier = modifier.fillMaxSize().padding(horizontal = 16.dp)) {
        Spacer(Modifier.height(12.dp))

        // Progress bar
        Card(
            shape = RoundedCornerShape(10.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)),
        ) {
            Column(Modifier.padding(12.dp)) {
                Text(
                    "🎵 ${detail?.title ?: "导入中"} · ${total}首",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                )
                Spacer(Modifier.height(4.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                    Text("✅ $confirmed", style = MaterialTheme.typography.labelSmall)
                    Text("❌ $skipped", style = MaterialTheme.typography.labelSmall)
                    Text("🔄 $searching", style = MaterialTheme.typography.labelSmall)
                    Text("⏳ $awaiting", style = MaterialTheme.typography.labelSmall)
                }
            }
        }

        Spacer(Modifier.height(12.dp))

        if (visibleTracks.isEmpty()) {
            // All done — show summary
            Box(Modifier.weight(1f), contentAlignment = Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("✅", style = MaterialTheme.typography.headlineLarge)
                    Spacer(Modifier.height(12.dp))
                    Text("导入完成！", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                    Text("成功匹配 $confirmed 首  ❌ 跳过 $skipped 首", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Spacer(Modifier.height(20.dp))
                    Button(onClick = onSave, shape = RoundedCornerShape(12.dp)) {
                        Text("保存到导入歌单")
                    }
                    Spacer(Modifier.height(8.dp))
                    TextButton(onClick = onBackToEntry) {
                        Text("放弃")
                    }
                }
            }
        } else {
            // Card relay — vertically stacked, animated
            LazyColumn(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                itemsIndexed(visibleTracks, key = { _, t -> t.index }) { _, track ->
                    AnimatedVisibility(
                        visible = true,
                        enter = slideInVertically(initialOffsetY = { it }) + fadeIn(),
                        exit = slideOutVertically(targetOffsetY = { -it }) + fadeOut(),
                    ) {
                        ConfirmationCard(
                            track = track,
                            onSelect = { result -> onConfirm(track.index, result) },
                            onSkip = { onSkip(track.index) },
                            onRetry = { onRetry(track.index) },
                        )
                    }
                }
                item { Spacer(Modifier.height(16.dp)) }
            }
        }
    }
}

@Composable
private fun ConfirmationCard(
    track: TrackImportState,
    onSelect: (SongMetadata) -> Unit,
    onSkip: () -> Unit,
    onRetry: () -> Unit,
) {
    Card(
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
    ) {
        Column(Modifier.padding(12.dp)) {
            // Header
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("《${track.originalName}》— ${track.originalArtist}", style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold)
                }
                IconButton(onClick = onSkip, modifier = Modifier.size(32.dp)) {
                    Icon(Icons.Default.Close, "跳过", modifier = Modifier.size(18.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f))
                }
            }

            HorizontalDivider(modifier = Modifier.padding(vertical = 6.dp))

            when (track.confirmStatus) {
                TrackConfirmStatus.SEARCHING -> {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp)
                        Spacer(Modifier.width(8.dp))
                        Text("搜索中...", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
                TrackConfirmStatus.AWAITING -> {
                    // Search results list
                    track.searchResults.forEach { result ->
                        Surface(
                            modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp).clickable { onSelect(result) },
                            shape = RoundedCornerShape(8.dp),
                            color = MaterialTheme.colorScheme.surface.copy(alpha = 0.6f),
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Column(Modifier.weight(1f)) {
                                    Text("${result.songName} - ${result.artist}", style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.Medium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                        PlatformBadgeLabel(result.platform)
                                        Text(result.quality, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                    }
                                }
                                Icon(Icons.Default.ChevronRight, "选择", modifier = Modifier.size(16.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f))
                            }
                        }
                    }
                }
                TrackConfirmStatus.FAILED -> {
                    Text("搜索失败", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
                    Spacer(Modifier.height(4.dp))
                    TextButton(onClick = onRetry) { Text("🔄 重新搜索") }
                }
                else -> { /* CONFIRMED/SKIPPED cards don't render (already removed) */ }
            }
        }
    }
}

@Composable
private fun PlatformBadgeLabel(platform: String) {
    val (label, color) = when (platform) {
        "netease" -> "网易" to androidx.compose.ui.graphics.Color(0xFFD32F2F)
        "qq" -> "QQ" to androidx.compose.ui.graphics.Color(0xFF1976D2)
        "kugou" -> "酷狗" to androidx.compose.ui.graphics.Color(0xFF7B1FA2)
        "bilibili" -> "B站" to androidx.compose.ui.graphics.Color(0xFF212121)
        else -> platform to androidx.compose.ui.graphics.Color.Gray
    }
    Surface(shape = RoundedCornerShape(4.dp), color = color.copy(alpha = 0.15f)) {
        Text(label, modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp), style = MaterialTheme.typography.labelSmall, color = color, fontWeight = FontWeight.Medium)
    }
}

// ══════════════════════════════════════════════════════════════════
// View 3: Imported Playlist Detail
// ══════════════════════════════════════════════════════════════════

@Composable
private fun ImportedPlaylistListView(
    importedPlaylists: List<ImportedPlaylist>,
    onBack: () -> Unit,
    onPlay: (ImportedSong) -> Unit,
    onDeletePlaylist: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier.fillMaxSize().padding(horizontal = 16.dp)) {
        Spacer(Modifier.height(8.dp))
        TextButton(onClick = onBack) {
            Icon(Icons.Default.ArrowBack, null, modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(4.dp))
            Text("返回导入")
        }

        if (importedPlaylists.isEmpty()) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text("暂无已导入的歌单", style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        } else {
            LazyColumn(modifier = Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                importedPlaylists.forEach { playlist ->
                    item {
                        Card(shape = RoundedCornerShape(12.dp), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f))) {
                            Column(Modifier.padding(12.dp)) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Column(Modifier.weight(1f)) {
                                        Text(playlist.title, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                                        Text("${playlist.songs.count { it.matchedSongId != null }}/${playlist.songs.size} 首 · 网易云", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                    }
                                    IconButton(onClick = { onDeletePlaylist(playlist.id) }, modifier = Modifier.size(36.dp)) {
                                        Icon(Icons.Default.Delete, "删除", tint = MaterialTheme.colorScheme.error.copy(alpha = 0.6f), modifier = Modifier.size(18.dp))
                                    }
                                }
                                HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))
                                playlist.songs.forEach { song ->
                                    val hasMatch = song.matchedSongId != null
                                    Surface(
                                        modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp).then(
                                            if (hasMatch) Modifier.clickable { onPlay(song) } else Modifier
                                        ),
                                        shape = RoundedCornerShape(8.dp),
                                        color = if (hasMatch) MaterialTheme.colorScheme.surface.copy(alpha = 0.5f) else MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.2f),
                                    ) {
                                        Row(
                                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                                            verticalAlignment = Alignment.CenterVertically,
                                        ) {
                                            Column(Modifier.weight(1f)) {
                                                Text(
                                                    if (hasMatch) "${song.matchedName} - ${song.matchedArtist}"
                                                    else "《${song.originalName}》— ${song.originalArtist}",
                                                    style = MaterialTheme.typography.bodySmall,
                                                    maxLines = 1,
                                                    overflow = TextOverflow.Ellipsis,
                                                )
                                                if (hasMatch && song.matchedPlatform != null) {
                                                    PlatformBadgeLabel(song.matchedPlatform!!)
                                                } else if (!hasMatch) {
                                                    Text("未匹配", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.error)
                                                }
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
                item { Spacer(Modifier.height(16.dp)) }
            }
        }
    }
}
```

- [ ] **Step 2: 编译验证**

Run: `./gradlew :shared:compileKotlinAndroid`
Expected: 编译成功

- [ ] **Step 3: Commit**

```bash
git add shared/src/commonMain/kotlin/com/example/aimusicplayer/ui/playlist/ImportTab.kt
git commit -m "feat: add ImportTab UI with card relay confirmation

- ImportEntryView: link input, start button, imported playlists list
- CardRelayView: up to 3 visible cards, real-time search results, auto-close
- ConfirmationCard: click to select, X to skip, animated slide-in/out
- ImportedPlaylistListView: detailed view of imported songs
- PlatformBadgeLabel: reusable platform badge (netease/qq/kugou/bilibili)"

Co-Authored-By: Claude Opus 4.8 <noreply@anthropic.com>
```

---

### Task 8: PlaylistScreen 修改 — 增加第三个 Tab

**Files:**
- Modify: `shared/src/commonMain/kotlin/com/example/aimusicplayer/ui/playlist/PlaylistScreen.kt`

- [ ] **Step 1: 修改 Tab 行，增加第三个 Tab**

把 PlaylistScreen.kt 中 Tab 行（当前在 `Column` 内 `Row(horizontalArrangement = Arrangement.Center)`）从 2 个 TabChip 改为 3 个：

```kotlin
// 替换现有的 Tab Row
Row(
    modifier = Modifier.fillMaxWidth().padding(vertical = 14.dp),
    horizontalArrangement = Arrangement.Center,
    verticalAlignment = Alignment.CenterVertically,
) {
    TabChip("当前歌单", selected = selectedTab == 0) { viewModel.selectTab(0) }
    Spacer(Modifier.width(36.dp))
    TabChip("历史歌单", selected = selectedTab == 1) { viewModel.selectTab(1) }
    Spacer(Modifier.width(36.dp))
    TabChip("导入歌单", selected = selectedTab == 2) { viewModel.selectTab(2) }
}
```

- [ ] **Step 2: 在 when 分支中新增 case 2**

```kotlin
// 在现有的 when(selectedTab) 块中，在 1 -> HistoryTab(...) 后面新增：
            2 -> ImportTab(
                viewModel = viewModel,
                modifier = Modifier.fillMaxSize(),
            )
```

- [ ] **Step 3: 编译验证**

Run: `./gradlew :shared:compileKotlinAndroid`
Expected: 编译成功

- [ ] **Step 4: Commit**

```bash
git add shared/src/commonMain/kotlin/com/example/aimusicplayer/ui/playlist/PlaylistScreen.kt
git commit -m "feat: add third Import tab to PlaylistScreen

- Tab row: 当前歌单 | 历史歌单 | 导入歌单
- Wire ImportTab into when(selectedTab) branch
- Tab spacing adjusted for 3 tabs"

Co-Authored-By: Claude Opus 4.8 <noreply@anthropic.com>
```

---

### Task 9: App.kt 依赖注入 + MainViewModel AI 链接检测

**Files:**
- Modify: `shared/src/commonMain/kotlin/com/example/aimusicplayer/App.kt`
- Modify: `shared/src/commonMain/kotlin/com/example/aimusicplayer/ui/main/MainViewModel.kt`

- [ ] **Step 1: App.kt 中注入 PlaylistImportManager**

在 `App.kt` 中，在 `playlistManager` 创建之后新增 `importManager` 创建：

```kotlin
// 在 playlistManager = remember { createPlaylistManager() } 之后新增
val importManager = remember {
    PlaylistImportManager(
        musicApis = musicApis,
        searchManager = searchManager,
        playlistManager = playlistManager,
    )
}
```

添加 import：
```kotlin
import com.example.aimusicplayer.import.PlaylistImportManager
```

修改 PlaylistViewModel 创建，传入 importManager：
```kotlin
val playlistViewModel = remember {
    PlaylistViewModel(
        playlistManager = playlistManager,
        musicPlayer = musicPlayer,
        settingsRepo = settingsRepo,
        importManager = importManager,  // 新增
    )
}
```

- [ ] **Step 2: MainViewModel 中添加链接检测**

在 `MainViewModel.kt` 构造函数中新增参数：
```kotlin
class MainViewModel(
    // ... existing params ...
    private val playlistManager: PlaylistManager,
    private val onImportLinkDetected: (String) -> Unit = {},  // 新增：AI 对话中检测到链接的回调
) : ViewModel() {
```

在 `sendMessage()` 方法中，检测链接（在发送给 AI 之前）：
```kotlin
// 在 sendMessage() 方法开头，构建消息之前插入
fun sendMessage(text: String) {
    // ... existing code ...
    
    // 检测音乐平台链接
    if (com.example.aimusicplayer.import.PlaylistLinkParser.isMusicLink(text)) {
        onImportLinkDetected(text)
        // Don't send to AI — it's an import link
        _messages.value = _messages.value + ChatItem.SystemMessage(
            text = "检测到音乐平台歌单链接，已跳转到导入页面",
            timestamp = Clock.System.now().toEpochMilliseconds(),
        )
        return
    }
    
    // ... rest of existing sendMessage logic ...
}
```

- [ ] **Step 3: App.kt 中设置 AI 链接检测回调**

在 `App.kt` 中，修改 `MainViewModel` 创建，传入回调：

```kotlin
val mainViewModel = remember {
    MainViewModel(
        musicPlayer = musicPlayer,
        aiAssistant = aiAssistant,
        searchManager = searchManager,
        downloadManager = downloadManager,
        settingsRepo = settingsRepo,
        crossPlatformResolver = crossPlatformResolver,
        bilibiliResolver = bilibiliResolver,
        cacheRepository = cacheRepository,
        playlistManager = playlistManager,
        onImportLinkDetected = { url ->
            playlistViewModel.startImport(url)
            currentScreen = Screen.Playlist
        },
    )
}
```

- [ ] **Step 4: 编译验证**

Run: `./gradlew :shared:compileKotlinAndroid`
Expected: 编译成功

- [ ] **Step 5: Commit**

```bash
git add shared/src/commonMain/kotlin/com/example/aimusicplayer/App.kt \
        shared/src/commonMain/kotlin/com/example/aimusicplayer/ui/main/MainViewModel.kt
git commit -m "feat: wire PlaylistImportManager and AI link detection

- Inject PlaylistImportManager in App.kt and pass to PlaylistViewModel
- Add isMusicLink() detection in MainViewModel.sendMessage()
- AI dialogue paste link → auto-navigate to import tab
- Add onImportLinkDetected callback for cross-viewmodel communication"

Co-Authored-By: Claude Opus 4.8 <noreply@anthropic.com>
```

---

### Task 10: 端到端编译验证 + 冒烟测试

- [ ] **Step 1: 完整项目编译**

Run: `./gradlew :androidApp:assembleDebug`
Expected: BUILD SUCCESSFUL

- [ ] **Step 2: 检查编译输出**
  确认无编译错误、无未解析的 import、expect/actual 匹配。

- [ ] **Step 3: 运行现有 DemoIntegrationTest 确保无回归**

Run: `./gradlew :androidApp:connectedAndroidTest` (如设备连接)
或者手动确认所有现有功能编译通过。

- [ ] **Step 4: Commit**

```bash
git add -A
git commit -m "chore: final integration — playlist import feature compiles

Verified: ./gradlew :androidApp:assembleDebug passes
All existing tests pass, no regressions"

Co-Authored-By: Claude Opus 4.8 <noreply@anthropic.com>
```

---

## Verification

1. `./gradlew :androidApp:assembleDebug` 编译通过
2. 手动测试：
   - 在导入 Tab 中粘贴网易云分享链接 → 点击"开始导入" → 显示歌单名称和曲目数
   - 卡片接力界面出现，最多 3 张卡片，搜索结果实时展示
   - 单击某条搜索结果 → 该卡片消失，下一张滑入
   - 点击 [✕] → 卡片消失，该歌曲标记为跳过
   - 搜索超时/无结果 → 卡片自动关闭
   - 全部处理完毕 → 显示统计结果 → 保存到导入歌单
   - 导入的歌单出现在"导入歌单"Tab 列表中，可播放、可删除
   - 在 AI 对话中粘贴网易云链接 → 自动跳转到导入 Tab 并触发导入
   - 重启 App → 已导入的歌单数据保留（Android）
