# 设置页面布局优化 — 实现计划

> **For agentic workers:** 使用 superpowers:subagent-driven-development 或 superpowers:executing-plans 按任务逐步实现。步骤使用 checkbox (`- [ ]`) 语法跟踪进度。

**目标:** 将设置页重构为 iOS Grouped 风格，移除无效设置和死代码，修复重复分隔线 bug。

**架构:** 局部精修，只涉及 UI 层（Compose）和数据层（Repository + ViewModel）的少量改动。同 section 多设置项合并到一张卡片，section 间用间距取代分隔线。

**技术栈:** Kotlin Multiplatform, Compose Multiplatform, Material3

---

### Task 1: 移除 SettingsRepository 中的 backgroundPlayback 方法

**文件:**
- 修改: `shared/src/commonMain/kotlin/com/example/aimusicplayer/settings/SettingsRepository.kt`

- [ ] **Step 1: 删除 getBackgroundPlayback 和 setBackgroundPlayback 方法**

找到并删除以下两段代码：

```kotlin
    suspend fun getBackgroundPlayback(): Boolean =
        storage.getString(SettingsKeys.BACKGROUND_PLAYBACK)?.toBooleanStrictOrNull() ?: true

    suspend fun setBackgroundPlayback(enabled: Boolean) =
        storage.putString(SettingsKeys.BACKGROUND_PLAYBACK, enabled.toString())
```

- [ ] **Step 2: 删除 clearAll 中的 BACKGROUND_PLAYBACK 相关行**

```kotlin
    // 删除这一行：
    storage.remove(SettingsKeys.BACKGROUND_PLAYBACK)
```

`clearAll()` 方法变为：

```kotlin
    suspend fun clearAll() {
        storage.remove(SettingsKeys.API_BASE_URL)
        storage.remove(SettingsKeys.API_KEY)
        storage.remove(SettingsKeys.LYRICS_SYNC)
        storage.remove(SettingsKeys.DEFAULT_QUALITY)
        storage.remove(SettingsKeys.HISTORY_COUNT)
        storage.remove(SettingsKeys.LYRICS_OFFSETS)
        storage.remove(SettingsKeys.THEME_MODE)
    }
```

- [ ] **Step 3: 运行 SettingsRepository 测试确认改动**

```bash
./gradlew :shared:jvmTest --tests "com.example.aimusicplayer.settings.SettingsRepositoryTest"
```

预期：与 background playback 相关的两个测试失败 —— 下一步会处理。

- [ ] **Step 4: 提交**

```bash
git add shared/src/commonMain/kotlin/com/example/aimusicplayer/settings/SettingsRepository.kt
git commit -m "refactor: remove unused backgroundPlayback from SettingsRepository

Co-Authored-By: Claude Opus 4.8 <noreply@anthropic.com>"
```

---

### Task 2: 更新 SettingsRepositoryTest 移除 background playback 测试

**文件:**
- 修改: `shared/src/commonTest/kotlin/com/example/aimusicplayer/settings/SettingsRepositoryTest.kt`

- [ ] **Step 1: 删除 background playback 相关测试方法**

删除以下两个测试函数：

```kotlin
    @Test
    fun `background playback defaults to true`() = runTest {
        assertTrue(repo.getBackgroundPlayback())
    }

    @Test
    fun `set background playback false`() = runTest {
        repo.setBackgroundPlayback(false)
        assertFalse(repo.getBackgroundPlayback())
    }
```

- [ ] **Step 2: 更新 clearAll 测试，移除 background playback 断言**

将 `clearAll removes all settings` 测试中的背景播放断言删除：

```kotlin
    @Test
    fun `clearAll removes all settings`() = runTest {
        repo.setApiKey("sk-xxx")
        repo.setBaseUrl("https://custom.url")
        repo.setThemeMode("dark")

        repo.clearAll()

        assertEquals("", repo.getApiKey())
        assertEquals(SettingsKeys.DEFAULT_BASE_URL, repo.getBaseUrl())
        assertEquals("system", repo.getThemeMode())
    }
```

删除了 `repo.setBackgroundPlayback(false)` 调用和 `assertTrue(repo.getBackgroundPlayback())` 断言。

- [ ] **Step 3: 运行测试确认全部通过**

```bash
./gradlew :shared:jvmTest --tests "com.example.aimusicplayer.settings.SettingsRepositoryTest"
```

预期：所有测试 PASS。

- [ ] **Step 4: 提交**

```bash
git add shared/src/commonTest/kotlin/com/example/aimusicplayer/settings/SettingsRepositoryTest.kt
git commit -m "test: remove background playback tests from SettingsRepositoryTest

Co-Authored-By: Claude Opus 4.8 <noreply@anthropic.com>"
```

---

### Task 3: 移除 SettingsViewModel 中的 backgroundPlayback

**文件:**
- 修改: `shared/src/commonMain/kotlin/com/example/aimusicplayer/ui/settings/SettingsViewModel.kt`

- [ ] **Step 1: 删除 backgroundPlayback StateFlow 声明（第 41-42 行）**

```kotlin
    // 删除以下两行：
    private val _backgroundPlayback = MutableStateFlow(true)
    val backgroundPlayback: StateFlow<Boolean> = _backgroundPlayback.asStateFlow()
```

- [ ] **Step 2: 删除 loadSettings 中的 backgroundPlayback 加载（第 92 行）**

删除这一行：
```kotlin
            _backgroundPlayback.value = settingsRepo.getBackgroundPlayback()
```

`loadSettings()` 变为：

```kotlin
    private fun loadSettings() {
        scope.launch {
            _baseUrl.value = settingsRepo.getBaseUrl()
            _apiKey.value = settingsRepo.getApiKey()
            _historyCount.value = settingsRepo.getHistoryCount()
            _themeMode.value = settingsRepo.getThemeMode()
        }
    }
```

- [ ] **Step 3: 删除 onBackgroundPlaybackChanged 方法（第 106-109 行）**

```kotlin
    // 删除以下方法：
    fun onBackgroundPlaybackChanged(enabled: Boolean) {
        _backgroundPlayback.value = enabled
        scope.launch { settingsRepo.setBackgroundPlayback(enabled) }
    }
```

- [ ] **Step 4: 删除 saveAll 中的 backgroundPlayback 持久化（第 172 行）**

删除这一行：
```kotlin
            settingsRepo.setBackgroundPlayback(_backgroundPlayback.value)
```

`saveAll()` 变为：

```kotlin
    fun saveAll() {
        scope.launch {
            settingsRepo.setBaseUrl(_baseUrl.value)
            settingsRepo.setApiKey(_apiKey.value)
            settingsRepo.setThemeMode(_themeMode.value)
        }
    }
```

- [ ] **Step 5: 提交**

```bash
git add shared/src/commonMain/kotlin/com/example/aimusicplayer/ui/settings/SettingsViewModel.kt
git commit -m "refactor: remove unused backgroundPlayback from SettingsViewModel

Co-Authored-By: Claude Opus 4.8 <noreply@anthropic.com>"
```

---

### Task 4: 更新 SettingsViewModelRobolectricTest 移除 background playback 测试

**文件:**
- 修改: `shared/src/androidUnitTest/kotlin/com/example/aimusicplayer/ui/settings/SettingsViewModelRobolectricTest.kt`

- [ ] **Step 1: 删除 background_playback_defaults_to_true 测试**

```kotlin
    // 删除以下测试方法：
    @Test
    fun background_playback_defaults_to_true() {
        assertTrue(viewModel.backgroundPlayback.value)
    }
```

- [ ] **Step 2: 运行测试确认通过**

```bash
./gradlew :shared:testDebugUnitTest --tests "com.example.aimusicplayer.ui.settings.SettingsViewModelRobolectricTest"
```

预期：所有测试 PASS。

- [ ] **Step 3: 提交**

```bash
git add shared/src/androidUnitTest/kotlin/com/example/aimusicplayer/ui/settings/SettingsViewModelRobolectricTest.kt
git commit -m "test: remove background playback test from SettingsViewModelRobolectricTest

Co-Authored-By: Claude Opus 4.8 <noreply@anthropic.com>"
```

---

### Task 5: 删除死代码 AiSettingsScreen.kt

**文件:**
- 删除: `shared/src/commonMain/kotlin/com/example/aimusicplayer/ui/settings/AiSettingsScreen.kt`

- [ ] **Step 1: 确认无代码引用该文件**

```bash
grep -r "AiSettingsScreen" shared/src/commonMain/ --include="*.kt"
```

预期：仅 `AiResult.kt`(注释) 和 `AiSettingsScreen.kt`(自身定义) 中有引用，无实际调用代码。

- [ ] **Step 2: 删除文件**

```bash
rm shared/src/commonMain/kotlin/com/example/aimusicplayer/ui/settings/AiSettingsScreen.kt
```

- [ ] **Step 3: 提交**

```bash
git add shared/src/commonMain/kotlin/com/example/aimusicplayer/ui/settings/AiSettingsScreen.kt
git commit -m "chore: remove unused AiSettingsScreen.kt

Co-Authored-By: Claude Opus 4.8 <noreply@anthropic.com>"
```

---

### Task 6: 重构 SettingsScreen 布局 — iOS Grouped 风格

**文件:**
- 修改: `shared/src/commonMain/kotlin/com/example/aimusicplayer/ui/settings/SettingsScreen.kt`

这是最核心的改动。需要同时：修复重复分隔线 bug、移除后台播放 UI、合并卡片、收紧间距。

- [ ] **Step 1: 删除 backgroundPlayback 状态订阅（第 91 行）**

```kotlin
    // 删除：
    val backgroundPlayback by viewModel.backgroundPlayback.collectAsState()
```

- [ ] **Step 2: 重构 AI 配置区域 —— 输入框合并到一张卡片**

将原有的两个独立 `OutlinedTextField` 替换为一张 `SettingsCard` 包裹的布局：

```kotlin
                // ── Section: AI 配置 ─────────────────────────────────────
                SectionHeader("AI 配置")

                SettingsCard {
                    Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp)) {
                        // Base URL — 无外边框风格
                        OutlinedTextField(
                            value = baseUrl,
                            onValueChange = viewModel::onBaseUrlChanged,
                            label = { Text("Base URL") },
                            placeholder = { Text(SettingsKeys.DEFAULT_BASE_URL) },
                            modifier = Modifier.fillMaxWidth(),
                            singleLine = true,
                            shape = RoundedCornerShape(8.dp),
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri),
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedBorderColor = Color.Transparent,
                                unfocusedBorderColor = Color.Transparent,
                                focusedContainerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f),
                                unfocusedContainerColor = Color.Transparent,
                            ),
                        )

                        HorizontalDivider(
                            thickness = 0.5.dp,
                            color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f),
                        )

                        // API Key
                        OutlinedTextField(
                            value = apiKey,
                            onValueChange = viewModel::onApiKeyChanged,
                            label = { Text("API Key") },
                            placeholder = { Text("sk-xxxxxxxxxxxxxxxxxxxxxxxx") },
                            modifier = Modifier.fillMaxWidth(),
                            singleLine = true,
                            shape = RoundedCornerShape(8.dp),
                            visualTransformation = PasswordVisualTransformation(),
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedBorderColor = Color.Transparent,
                                unfocusedBorderColor = Color.Transparent,
                                focusedContainerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f),
                                unfocusedContainerColor = Color.Transparent,
                            ),
                        )
                    }
                }
```

**注意**：需要在文件顶部新增 import：
```kotlin
import androidx.compose.ui.graphics.Color
```

- [ ] **Step 3: 重构播放选项区域 —— 移除后台播放，仅保留历史记录数量**

```kotlin
                Spacer(Modifier.height(16.dp))

                // ── Section: 播放选项 ─────────────────────────────────────
                SectionHeader("播放选项")

                SettingsCard {
                    HistoryCountDropdown(
                        currentCount = historyCount,
                        onCountChanged = viewModel::onHistoryCountChanged,
                    )
                }
```

同时删除 `HistoryCountDropdown` 内部的 `.padding(horizontal = 16.dp, vertical = 8.dp)`（因为它现在由外层 `SettingsCard` 提供容器边距），将其 `modifier` 简化为：

```kotlin
            modifier = Modifier
                .fillMaxWidth()
                .menuAnchor(MenuAnchorType.PrimaryNotEditable, enabled = true),
```

- [ ] **Step 4: 重构外观区域 —— 修复重复分隔线 bug**

```kotlin
                Spacer(Modifier.height(16.dp))

                // ── Section: 外观 ─────────────────────────────────────────
                SectionHeader("外观")

                SettingsCard {
                    ThemeModeDropdown(
                        currentTheme = themeMode,
                        onThemeChanged = viewModel::onThemeModeChanged,
                    )
                }
```

同样删除 `ThemeModeDropdown` 内部的 `.padding(horizontal = 16.dp, vertical = 8.dp)`，简化为：

```kotlin
            modifier = Modifier
                .fillMaxWidth()
                .menuAnchor(MenuAnchorType.PrimaryNotEditable, enabled = true),
```

- [ ] **Step 5: 重构定时关闭区域**

```kotlin
                Spacer(Modifier.height(16.dp))

                // ── Section: 定时关闭 ────────────────────────────────────────
                SectionHeader("定时关闭")

                SleepTimerSection(
                    sleepTimerState = sleepTimerState,
                    onStartSleepTimer = onStartSleepTimer,
                    onCancelSleepTimer = onCancelSleepTimer,
                    onToggleFinishSong = onToggleFinishSong,
                )
```

无需改动 `SleepTimerSection` 内部代码——它本身已经是一张独立卡片。

- [ ] **Step 6: 重构存储管理区域 —— 改用 SettingsCard**

将原来的 `Card(...)` 替换为 `SettingsCard`，保持内部 Row 结构不变：

```kotlin
                Spacer(Modifier.height(16.dp))

                // ── Section: 存储管理 ─────────────────────────────────────
                SectionHeader("存储管理")

                SettingsCard {
                    Row(
                        modifier = Modifier.padding(16.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Column {
                            Text(
                                "缓存占用",
                                fontWeight = FontWeight.Medium,
                                style = MaterialTheme.typography.bodyMedium,
                            )
                            Spacer(Modifier.height(2.dp))
                            Text(
                                cacheSizeText,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        OutlinedButton(
                            onClick = viewModel::clearCache,
                            enabled = !isClearingCache,
                            shape = RoundedCornerShape(10.dp),
                        ) {
                            if (isClearingCache) {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(16.dp),
                                    strokeWidth = 2.dp,
                                )
                                Spacer(Modifier.width(4.dp))
                            }
                            Text("清空缓存")
                        }
                    }
                }
```

- [ ] **Step 7: 调整外层 Column 间距 —— 移除 Arrangement.spacedBy**

将外层 `Column` 的 `verticalArrangement = Arrangement.spacedBy(16.dp)` 替换为 `verticalArrangement = Arrangement.Top`，section 之间手动用 `Spacer(Modifier.height(16.dp))` 控制。

顶部 `Spacer(Modifier.height(4.dp))` 保留。

- [ ] **Step 8: 移除不再使用的 import**

以下 import 不再需要（后台播放 toggle 已移除）：
```kotlin
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
```

`SettingsToggle` 函数已无调用者，保留 `SettingsToggle` 函数定义（但如果只有后台播放在用，它现在就成死代码了）。由于只有后台播放在用 `SettingsToggle`，可以一并删除该函数。

`SettingsCard` 函数保留（仍广泛使用）。

- [ ] **Step 9: 检查代码是否仍有未使用的 import**

运行 IDE 或手动检查：
- `Switch` 和 `SwitchDefaults` — 删除（`SleepTimerSection` 底部还有 Switch 使用，所以需要保留！）
- 重新审视：`SleepTimerSection` 的 bottom sheet 中使用了 `Switch`（第 651 行），所以 `Switch` 和 `SwitchDefaults` 仍然需要保留。

真正可以删除的只有 `SettingsToggle` 函数本身（第 697-733 行），因为它唯一的调用者（后台播放 toggle）已被移除。

- [ ] **Step 10: 删除 SettingsToggle 函数**

```kotlin
    // 删除整个 SettingsToggle 函数（第 697-733 行）：
    @Composable
    private fun SettingsToggle(
        title: String,
        subtitle: String,
        checked: Boolean,
        onCheckedChange: (Boolean) -> Unit,
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 12.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    title,
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Medium,
                )
                Spacer(Modifier.height(2.dp))
                Text(
                    subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Switch(
                checked = checked,
                onCheckedChange = onCheckedChange,
                colors = SwitchDefaults.colors(
                    checkedThumbColor = MaterialTheme.colorScheme.onPrimary,
                    checkedTrackColor = MaterialTheme.colorScheme.primary,
                ),
            )
        }
    }
```

- [ ] **Step 11: 提交**

```bash
git add shared/src/commonMain/kotlin/com/example/aimusicplayer/ui/settings/SettingsScreen.kt
git commit -m "feat: refactor settings layout to iOS grouped style

- Merge multi-item sections into single card with 0.5dp dividers
- Remove section HorizontalDividers, use Spacer for separation
- Remove background playback toggle (unused setting)
- Fix duplicate HorizontalDivider between 外观 and 定时关闭
- Use SettingsCard for storage management section
- Merge Base URL + API Key into single card with borderless fields
- Remove unused SettingsToggle composable
- Consolidate section spacing to uniform 16dp

Co-Authored-By: Claude Opus 4.8 <noreply@anthropic.com>"
```

---

### Task 7: 验证编译和测试

**文件:** 无新建或修改，验证步骤。

- [ ] **Step 1: 编译 shared 模块确认无编译错误**

```bash
./gradlew :shared:compileDebugKotlinAndroid
```

预期：BUILD SUCCESSFUL。

- [ ] **Step 2: 运行全量 commonTest**

```bash
./gradlew :shared:jvmTest
```

预期：所有测试 PASS。

- [ ] **Step 3: 运行 Android Unit Test**

```bash
./gradlew :shared:testDebugUnitTest
```

预期：所有测试 PASS。

- [ ] **Step 4: 运行全部测试做最终确认**

```bash
./gradlew :shared:allTests
```

预期：全部 PASS。

---

### 改动文件总览

| 任务 | 文件 | 操作 |
|------|------|------|
| Task 1 | `SettingsRepository.kt` | 删除 2 个方法 + 1 行 clearAll |
| Task 2 | `SettingsRepositoryTest.kt` | 删除 2 个测试 + 修改 1 个测试 |
| Task 3 | `SettingsViewModel.kt` | 删除 state + action + load/save 中的引用 |
| Task 4 | `SettingsViewModelRobolectricTest.kt` | 删除 1 个测试 |
| Task 5 | `AiSettingsScreen.kt` | **删除文件** |
| Task 6 | `SettingsScreen.kt` | 重构布局、修 bug、移除无效 UI |
| Task 7 | — | 验证（编译 + 全量测试） |
