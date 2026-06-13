# B站两轮搜索优化 — 实现计划

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** B站搜索改为两轮：先按"歌名+歌手名"严格匹配搜3首，再仅按"歌名"搜3首

**Architecture:** 在现有的 `for (platform in platformOrder)` 循环中为 `bilibili` 平台展开两轮搜索，每轮使用不同的 query。歌手名有效性判断复用 `BilibiliGuaranteedResolver` 中的已有模式。

**Tech Stack:** Kotlin Multiplatform, kotlinx.coroutines

---

### Task 1: PlaylistImportManager — B站两轮搜索

**Files:**
- Modify: `shared/src/commonMain/kotlin/com/example/aimusicplayer/import/PlaylistImportManager.kt:225-300`

将 `searchTrackStreaming` 方法中 Bilibili 的单轮搜索改为两轮。

- [ ] **Step 1: 替换 B站搜索逻辑**

找到 `searchTrackStreaming` 方法（约225行），将整个 `for (platform in platformOrder) { ... }` 循环体替换为以下代码，核心改动是 Bilibili 分支使用两轮 query 搜索：

```kotlin
        try {
            for (platform in platformOrder) {
                if (allResults.size >= 8) break // max total results

                // ── Bilibili: two-round search ──────────────────────
                if (platform == "bilibili") {
                    val hasArtist = artistName.isNotBlank() && artistName != "未知歌手"

                    // Round 1: song name + artist name (strict match)
                    if (hasArtist) {
                        val round1Query = "$songName $artistName"
                        allResults += searchAndResolveForImport(
                            platform, round1Query, songName, allResults, allUrls, trackIndex,
                            maxPerPlatform = 3, maxTotal = 8,
                        )
                    }

                    // Round 2: song name only (ignore artist, dedup vs round 1)
                    if (allResults.size < 8) {
                        val round2Query = songName
                        allResults += searchAndResolveForImport(
                            platform, round2Query, songName, allResults, allUrls, trackIndex,
                            maxPerPlatform = 3, maxTotal = 8,
                        )
                    }
                } else {
                    // ── Other platforms: single-round search ─────────
                    val query = "$artistName $songName"
                    allResults += searchAndResolveForImport(
                        platform, query, songName, allResults, allUrls, trackIndex,
                        maxPerPlatform = maxPerPlatform, maxTotal = 8,
                    )
                }
            }
        } catch (_: Exception) { /* track-level failure — partial results still shown */ }
```

- [ ] **Step 2: 添加 `searchAndResolveForImport` 辅助方法**

在 `PlaylistImportManager` 类中（`advanceIfAllDone` 方法之前，约312行），添加以下私有方法，封装单轮搜索→过滤→解析URL→推送结果的逻辑：

```kotlin
    /**
     * Execute a single search round for playlist import: search, filter, resolve
     * play URLs, and stream results to the track card.
     *
     * @return List of newly resolved [SongMetadata] from this round.
     */
    private suspend fun searchAndResolveForImport(
        platform: String,
        query: String,
        songName: String,
        existingResults: List<SongMetadata>,
        existingUrls: MutableMap<String, String>,
        trackIndex: Int,
        maxPerPlatform: Int,
        maxTotal: Int,
    ): List<SongMetadata> {
        val newResults = mutableListOf<SongMetadata>()

        // Rate limit before each platform search
        try { RateLimiterRegistry.acquire(platform) } catch (_: Exception) {}
        delay(Random.nextLong(100, 800))

        val songs = try {
            searchManager.searchOnPlatform(query, platform)
        } catch (_: Exception) { emptyList() }

        // Filter results: exact match on song name, dedup vs existing
        val existingIds = existingResults.map { it.songId }.toSet()
        val matched = songs.filter { s ->
            val sName = s.songName.trim()
            (sName.contains(songName, ignoreCase = true) || songName.contains(sName, ignoreCase = true)) &&
            s.songId !in existingIds
        }

        var platformCount = 0
        for (song in matched) {
            if (platformCount >= maxPerPlatform) break
            if (existingResults.size + newResults.size >= maxTotal) break

            // Skip VIP/paid songs (matches SearchViewModel behavior)
            if ((platform == "netease" || platform == "qq") && song.fee > 0) continue

            try { RateLimiterRegistry.acquire(platform) } catch (_: Exception) {}
            delay(Random.nextLong(50, 400))

            val url = searchManager.getPlayUrl(song.songId, platform, song.quality)
            if (!url.isNullOrBlank()) {
                existingUrls[song.songId] = url
                newResults.add(song)
                platformCount++

                // STREAM: push result to card immediately
                val combined = existingResults + newResults
                scope.launch {
                    updateTrack(trackIndex) { current ->
                        if (current.confirmStatus == TrackConfirmStatus.CONFIRMED ||
                            current.confirmStatus == TrackConfirmStatus.SKIPPED) {
                            current
                        } else {
                            current.copy(
                                searchResults = combined.toList(),
                                resolvedPlayUrls = existingUrls.toMap(),
                                confirmStatus = TrackConfirmStatus.AWAITING,
                            )
                        }
                    }
                }
            }
        }

        return newResults
    }
```

- [ ] **Step 3: 清理 `searchTrackStreaming` 中不再需要的局部变量**

`searchTrackStreaming` 方法中原有的 `val query = "$artistName $songName"`（第226行）不再需要，删除。`maxPerPlatform` 变量仍需要（非B站平台使用），保留。

- [ ] **Step 4: 验证构建**

```bash
cd D:\Download\claude_demo\HearEverything && ./gradlew :shared:compileKotlinMetadata 2>&1 | tail -20
```

预期：BUILD SUCCESSFUL

- [ ] **Step 5: 提交**

```bash
git add shared/src/commonMain/kotlin/com/example/aimusicplayer/import/PlaylistImportManager.kt
git commit -m "feat: B站歌单导入搜索改为两轮（歌手+歌名 → 仅歌名）"
```

---

### Task 2: SearchViewModel — B站两轮搜索

**Files:**
- Modify: `shared/src/commonMain/kotlin/com/example/aimusicplayer/ui/search/SearchViewModel.kt:680-757`

将 `searchAcrossPlatforms` 方法中 Bilibili 的单轮搜索改为两轮。

- [ ] **Step 1: 替换 B站搜索逻辑**

找到 `searchAcrossPlatforms` 方法（约680行），将 `for (platform in platformOrder) { ... }` 循环体替换为以下代码：

```kotlin
        for (platform in platformOrder) {
            // ── Bilibili: two-round search ──────────────────────────
            if (platform == "bilibili") {
                val hasArtist = artistName.isNotBlank() && artistName != "未知歌手"

                // Round 1: song name + artist name (strict match)
                if (hasArtist) {
                    searchAndResolveForSearchPanel(
                        platform, "$songName $artistName", songName, artistName,
                        maxPerPlatform = 3, maxTotal = maxTotal,
                    )
                }

                // Round 2: song name only (ignore artist, dedup vs round 1)
                if (_searchResults.value.size < maxTotal) {
                    searchAndResolveForSearchPanel(
                        platform, songName, songName, artistName,
                        maxPerPlatform = 3, maxTotal = maxTotal,
                    )
                }
            } else {
                // ── Other platforms: single-round search ─────────────
                searchAndResolveForSearchPanel(
                    platform, query, songName, artistName,
                    maxPerPlatform = maxPerPlatform, maxTotal = maxTotal,
                )
            }

            // Stop early if we have enough total results
            if (_searchResults.value.size >= maxTotal) {
                log.i { "Collected ${_searchResults.value.size} results across platforms — stopping search" }
                break
            }
        }
```

- [ ] **Step 2: 添加 `searchAndResolveForSearchPanel` 辅助方法**

在 `SearchViewModel` 类中（`searchAcrossPlatforms` 方法之后，约757行），添加以下私有方法：

```kotlin
    /**
     * Execute a single search round for the search panel: search on [platform]
     * with [query], filter, resolve play URLs, and stream to [_searchResults].
     *
     * Results already in [_searchResults] are skipped (dedup by songId).
     */
    private suspend fun searchAndResolveForSearchPanel(
        platform: String,
        query: String,
        songName: String,
        artistName: String,
        maxPerPlatform: Int,
        maxTotal: Int,
    ) {
        val label = platformLabel(platform)
        val existingIds = _searchResults.value.map { it.song.songId }.toSet()

        _searchStep.value = "正在搜索 $label ..."
        RateLimiterRegistry.acquire(platform)
        delay(Random.nextLong(200, 1500))

        val songs = try {
            searchManager.searchOnPlatform(query, platform, quality = "standard")
        } catch (e: Exception) {
            log.w { "Platform $platform search exception: ${e.message}" }
            emptyList()
        }

        // Filter: dedup + match
        val matchedSongs = songs.filter {
            matchesQuery(it, songName, artistName) && it.songId !in existingIds
        }
        log.i { "Platform $platform query=\"$query\": ${songs.size} raw → ${matchedSongs.size} matched" }

        if (matchedSongs.isNotEmpty()) {
            _searchStep.value = "正在解析 $label 播放地址..."
            var platformResults = 0

            for (song in matchedSongs) {
                if (platformResults >= maxPerPlatform) break
                if (_searchResults.value.size >= maxTotal) break

                // Skip Netease/QQ VIP/paid songs
                if ((song.platform == "netease" || song.platform == "qq") && song.fee > 0) {
                    log.d { "Skipping VIP: ${song.songName} (${song.platform} fee=${song.fee})" }
                    continue
                }

                RateLimiterRegistry.acquire(platform)
                delay(Random.nextLong(100, 800))

                val url = searchManager.getPlayUrl(song.songId, platform, song.quality)
                if (!url.isNullOrBlank()) {
                    cacheSong(song, url)
                    val item = SearchResultItem(song = song, cachedUrl = url, fromCache = false)

                    // STREAMING: push result to UI immediately
                    _searchResults.value = _searchResults.value + item
                    platformResults++

                    log.i { "Streamed: ${song.songName} — ${song.artist} (${song.platform})" }
                }
            }

            if (platformResults > 0) {
                log.i { "Platform $platform: added $platformResults playable results (total=${_searchResults.value.size})" }
                _searchStep.value = "$label 找到 $platformResults 首 ✓"
            } else {
                _searchStep.value = "$label 找到匹配歌曲但无法获取播放地址，尝试下一平台..."
            }
        } else {
            _searchStep.value = "$label 无匹配结果，尝试下一平台..."
        }
    }
```

- [ ] **Step 3: 验证构建**

```bash
cd D:\Download\claude_demo\HearEverything && ./gradlew :shared:compileKotlinMetadata 2>&1 | tail -20
```

预期：BUILD SUCCESSFUL

- [ ] **Step 4: 提交**

```bash
git add shared/src/commonMain/kotlin/com/example/aimusicplayer/ui/search/SearchViewModel.kt
git commit -m "feat: 系统搜索B站改为两轮（歌手+歌名 → 仅歌名）"
```

---

### 验证步骤（手动功能测试）

两个 Task 完成后，进行以下验证：

1. **歌单导入测试：** 粘贴一个包含歌手名的歌单链接 → 确认 B 站搜索结果有两轮来源（带歌手 + 纯歌名）
2. **系统搜索测试：** 在搜索面板输入歌名+歌手名 → 点击搜索 → 确认 B 站结果包含两轮搜索的歌曲
3. **无歌手测试：** 歌单中歌曲歌手为"未知歌手" → 确认 B 站仅用歌名搜索一轮
4. **去重测试：** 同一首歌在两轮中都被搜到 → 确认结果中只出现一次
