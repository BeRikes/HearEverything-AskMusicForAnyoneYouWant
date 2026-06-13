package com.example.aimusicplayer.ui.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MenuAnchorType
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.clickable
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.rememberModalBottomSheetState
import com.example.aimusicplayer.settings.SettingsKeys
import com.example.aimusicplayer.ui.main.formatTimerRemaining
import com.example.aimusicplayer.ui.main.SleepTimerState

// ═══════════════════════════════════════════════════════════════════════════
// SettingsScreen — AI config, playback, theme, storage
// ═══════════════════════════════════════════════════════════════════════════

/**
 * Full settings screen styled with the NetEase/QQ Music design language.
 *
 * Sections:
 * 1. **AI 配置** — Base URL, API Key, Test Connection
 * 2. **播放选项** — Background playback, lyrics sync, default quality
 * 3. **外观** — Theme mode
 * 4. **存储管理** — Cache size, clear cache
 */
@Composable
fun SettingsScreen(
    viewModel: SettingsViewModel,
    onBack: () -> Unit,
    sleepTimerState: SleepTimerState = SleepTimerState(),
    onStartSleepTimer: (minutes: Int, finishCurrentSong: Boolean) -> Unit = { _, _ -> },
    onCancelSleepTimer: () -> Unit = {},
    onToggleFinishSong: () -> Unit = {},
    modifier: Modifier = Modifier,
) {
    val baseUrl by viewModel.baseUrl.collectAsState()
    val apiKey by viewModel.apiKey.collectAsState()
    val isTesting by viewModel.isTesting.collectAsState()
    val testResult by viewModel.testResult.collectAsState()
    val themeMode by viewModel.themeMode.collectAsState()
    val cacheSizeText by viewModel.cacheSizeText.collectAsState()
    val isClearingCache by viewModel.isClearingCache.collectAsState()
    val cacheClearedSnackbar by viewModel.cacheClearedSnackbar.collectAsState()
    val historyCount by viewModel.historyCount.collectAsState()

    val snackbarHostState = remember { SnackbarHostState() }

    // Cache clear snackbar
    LaunchedEffect(cacheClearedSnackbar) {
        cacheClearedSnackbar?.let {
            snackbarHostState.showSnackbar(it)
            viewModel.clearSnackbar()
        }
    }

    Box(modifier = modifier.fillMaxSize()) {
        Column(modifier = Modifier.fillMaxSize()) {
            // Compact header
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 8.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                TextButton(onClick = {
                    viewModel.saveAll()
                    onBack()
                }) {
                    Text("← 返回", color = MaterialTheme.colorScheme.primary)
                }
                Spacer(Modifier.weight(1f))
                Text(
                    "设置",
                    fontWeight = FontWeight.Bold,
                    style = MaterialTheme.typography.titleMedium,
                )
                Spacer(Modifier.weight(1f))
                Spacer(Modifier.width(60.dp)) // balance
            }

            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 16.dp),
                verticalArrangement = Arrangement.Top,
            ) {
                Spacer(Modifier.height(4.dp))

                // ── Section: AI 配置 ─────────────────────────────────────
                SectionHeader("AI 配置")

                SettingsCard {
                    Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp)) {
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
                            ),
                        )
                        HorizontalDivider(
                            thickness = 0.5.dp,
                            color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f),
                        )
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
                            ),
                        )
                    }
                }

                // Key length indicator
                val keyLength = apiKey.length
                val keyStatusText = when {
                    keyLength == 0 -> "未输入"
                    keyLength < SettingsKeys.MIN_KEY_LENGTH -> "长度不足（需 ≥ ${SettingsKeys.MIN_KEY_LENGTH}）"
                    else -> "已输入 ($keyLength 字符)"
                }
                val keyStatusColor = if (keyLength < SettingsKeys.MIN_KEY_LENGTH)
                    MaterialTheme.colorScheme.error
                else
                    MaterialTheme.colorScheme.primary
                Text(
                    text = "API Key 状态: $keyStatusText",
                    style = MaterialTheme.typography.labelMedium,
                    color = keyStatusColor,
                    fontWeight = FontWeight.Medium,
                    modifier = Modifier.padding(vertical = 10.dp, horizontal = 4.dp),
                )

                // Test connection button
                Button(
                    onClick = viewModel::testConnection,
                    modifier = Modifier.fillMaxWidth().height(48.dp),
                    enabled = !isTesting && apiKey.length >= SettingsKeys.MIN_KEY_LENGTH,
                    shape = RoundedCornerShape(12.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.primary,
                    ),
                ) {
                    if (isTesting) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(20.dp),
                            strokeWidth = 2.dp,
                            color = MaterialTheme.colorScheme.onPrimary,
                        )
                        Spacer(Modifier.width(8.dp))
                        Text("测试中...")
                    } else {
                        Text("测试连接", fontWeight = FontWeight.Medium)
                    }
                }

                // Test result card
                testResult?.let {
                    val (isSuccess, message) = when (it) {
                        is TestResult.Success -> true to "连接成功！API Key 有效。"
                        is TestResult.Failure -> false to it.message
                    }
                    Card(
                        colors = CardDefaults.cardColors(
                            containerColor = if (isSuccess)
                                MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.6f)
                            else
                                MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.6f),
                        ),
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Row(
                            modifier = Modifier.padding(12.dp),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text(if (isSuccess) "✅" else "❌")
                            Text(
                                text = message,
                                style = MaterialTheme.typography.bodySmall,
                                color = if (isSuccess)
                                    MaterialTheme.colorScheme.onPrimaryContainer
                                else
                                    MaterialTheme.colorScheme.onErrorContainer,
                            )
                        }
                    }
                }

                Spacer(Modifier.height(16.dp))

                // ── Section: 播放选项 ─────────────────────────────────────
                SectionHeader("播放选项")

                // ── 播放选项入口卡片 ──────────────────────────────────────
                PlaybackSettingsEntry(
                    historyCount = historyCount,
                    onHistoryCountChanged = viewModel::onHistoryCountChanged,
                )

                Spacer(Modifier.height(16.dp))

                // ── 外观入口卡片 ───────────────────────────────────────────
                AppearanceSettingsEntry(
                    currentTheme = themeMode,
                    onThemeChanged = viewModel::onThemeModeChanged,
                )

                Spacer(Modifier.height(16.dp))

                // ── Section: 定时关闭 ────────────────────────────────────────
                SectionHeader("定时关闭")

                SleepTimerSection(
                    sleepTimerState = sleepTimerState,
                    onStartSleepTimer = onStartSleepTimer,
                    onCancelSleepTimer = onCancelSleepTimer,
                    onToggleFinishSong = onToggleFinishSong,
                )

                Spacer(Modifier.height(16.dp))

                // ── Section: 推荐设置 ─────────────────────────────────────
                SectionHeader("推荐设置")

                RecommendationSettingsSection(viewModel)

                Spacer(Modifier.height(16.dp))

                // ── Section: 存储管理 ─────────────────────────────────────
                SectionHeader("存储管理")

                SettingsCard {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
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

                Spacer(Modifier.height(24.dp))
            }
        }

        // Snackbar host
        SnackbarHost(
            hostState = snackbarHostState,
            modifier = Modifier.align(Alignment.BottomCenter),
        )
    }
}

// ═══════════════════════════════════════════════════════════════════════════
// Reusable components
// ═══════════════════════════════════════════════════════════════════════════

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun HistoryCountDropdown(
    currentCount: Int,
    onCountChanged: (Int) -> Unit,
) {
    var expanded by remember { mutableStateOf(false) }

    ExposedDropdownMenuBox(
        expanded = expanded,
        onExpandedChange = { expanded = !expanded },
    ) {
        OutlinedTextField(
            value = "$currentCount 首",
            onValueChange = {},
            readOnly = true,
            label = { Text("历史记录数量") },
            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 12.dp)
                .menuAnchor(MenuAnchorType.PrimaryNotEditable, enabled = true),
            shape = RoundedCornerShape(10.dp),
            colors = OutlinedTextFieldDefaults.colors(
                focusedBorderColor = MaterialTheme.colorScheme.primary,
            ),
        )
        ExposedDropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false },
        ) {
            SettingsViewModel.HISTORY_OPTIONS.forEach { count ->
                DropdownMenuItem(
                    text = { Text("$count 首") },
                    onClick = {
                        onCountChanged(count)
                        expanded = false
                    },
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SleepTimerSection(
    sleepTimerState: SleepTimerState,
    onStartSleepTimer: (Int, Boolean) -> Unit,
    onCancelSleepTimer: () -> Unit,
    onToggleFinishSong: () -> Unit,
) {
    var showBottomSheet by remember { mutableStateOf(false) }
    var customMinutesText by remember { mutableStateOf("") }
    val finishCurrentSong = sleepTimerState.finishCurrentSong

    // Status text for the entry row
    val statusText = when {
        !sleepTimerState.isActive -> "未设置"
        sleepTimerState.remainingMs <= 0L && sleepTimerState.finishCurrentSong -> "等待曲末暂停"
        sleepTimerState.finishCurrentSong -> "剩余 ${formatTimerRemaining(sleepTimerState.remainingMs)} · 播完关闭"
        else -> "剩余 ${formatTimerRemaining(sleepTimerState.remainingMs)}"
    }

    // Entry row
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { showBottomSheet = true },
        colors = CardDefaults.cardColors(
            containerColor = if (sleepTimerState.isActive)
                MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f)
            else
                MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
        ),
        shape = RoundedCornerShape(12.dp),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 14.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column {
                Text(
                    "⏰ 定时关闭",
                    fontWeight = FontWeight.Medium,
                    style = MaterialTheme.typography.bodyMedium,
                )
                Spacer(Modifier.height(2.dp))
                Text(
                    statusText,
                    style = MaterialTheme.typography.bodySmall,
                    color = if (sleepTimerState.isActive)
                        MaterialTheme.colorScheme.primary
                    else
                        MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            if (sleepTimerState.isActive) {
                TextButton(onClick = onCancelSleepTimer) {
                    Text("关闭", color = MaterialTheme.colorScheme.error)
                }
            }
        }
    }

    // ── Bottom Sheet ─────────────────────────────────────────
    if (showBottomSheet) {
        val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
        ModalBottomSheet(
            onDismissRequest = { showBottomSheet = false },
            sheetState = sheetState,
            shape = RoundedCornerShape(topStart = 20.dp, topEnd = 20.dp),
            containerColor = MaterialTheme.colorScheme.surface,
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp)
                    .padding(bottom = 32.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                Text(
                    "定时关闭",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(bottom = 8.dp),
                )

                // Preset options
                val presets = listOf(
                    "关闭定时器" to 0,
                    "10 分钟" to 10,
                    "15 分钟" to 15,
                    "30 分钟" to 30,
                    "60 分钟" to 60,
                )

                val selectedMinutes = if (sleepTimerState.isActive && sleepTimerState.totalMs > 0) {
                    (sleepTimerState.totalMs / 60_000L).toInt()
                } else {
                    null
                }

                presets.forEach { (label, minutes) ->
                    val isSelected = if (minutes == 0) {
                        !sleepTimerState.isActive
                    } else {
                        selectedMinutes == minutes
                    }
                    val isFinishSongActive = sleepTimerState.isActive &&
                            sleepTimerState.remainingMs <= 0L &&
                            sleepTimerState.finishCurrentSong

                    Surface(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable {
                                if (minutes == 0) {
                                    onCancelSleepTimer()
                                } else {
                                    onStartSleepTimer(minutes, finishCurrentSong)
                                }
                                showBottomSheet = false
                            },
                        shape = RoundedCornerShape(12.dp),
                        color = when {
                            isSelected && minutes > 0 && !isFinishSongActive -> MaterialTheme.colorScheme.primary.copy(alpha = 0.15f)
                            else -> MaterialTheme.colorScheme.surface
                        },
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 16.dp, vertical = 14.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text(
                                label,
                                style = MaterialTheme.typography.bodyLarge,
                                fontWeight = if (isSelected && minutes > 0) FontWeight.SemiBold else FontWeight.Normal,
                                color = when {
                                    isSelected && minutes > 0 -> MaterialTheme.colorScheme.primary
                                    minutes == 0 && !sleepTimerState.isActive -> MaterialTheme.colorScheme.primary
                                    else -> MaterialTheme.colorScheme.onSurface
                                },
                            )
                            if (isSelected && minutes > 0 && !isFinishSongActive) {
                                Text(
                                    "已选择",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.primary,
                                )
                            }
                        }
                    }
                }

                // Custom minutes input
                var customExpanded by remember { mutableStateOf(false) }
                Surface(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { customExpanded = !customExpanded },
                    shape = RoundedCornerShape(12.dp),
                    color = MaterialTheme.colorScheme.surface,
                ) {
                    Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 14.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text(
                                "✏️ 自定义",
                                style = MaterialTheme.typography.bodyLarge,
                            )
                            Text(
                                if (customExpanded) "▲" else "▼",
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        if (customExpanded) {
                            Spacer(Modifier.height(12.dp))
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                            ) {
                                OutlinedTextField(
                                    value = customMinutesText,
                                    onValueChange = { newValue ->
                                        customMinutesText = newValue.filter { it.isDigit() }
                                    },
                                    label = { Text("分钟") },
                                    singleLine = true,
                                    modifier = Modifier.weight(1f),
                                    shape = RoundedCornerShape(8.dp),
                                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                                )
                                Button(
                                    onClick = {
                                        val mins = customMinutesText.toIntOrNull()
                                        if (mins != null && mins > 0) {
                                            onStartSleepTimer(mins, finishCurrentSong)
                                            showBottomSheet = false
                                        }
                                    },
                                    enabled = (customMinutesText.toIntOrNull() ?: 0) > 0,
                                    shape = RoundedCornerShape(8.dp),
                                ) {
                                    Text("确定")
                                }
                            }
                        }
                    }
                }

                HorizontalDivider(
                    modifier = Modifier.padding(vertical = 8.dp),
                    color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f),
                )

                // Toggle: finish current song
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 12.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            "播完整首歌后关闭",
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.Medium,
                        )
                        Spacer(Modifier.height(2.dp))
                        Text(
                            "倒计时结束后等待当前歌曲播完再暂停",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    Switch(
                        checked = finishCurrentSong,
                        onCheckedChange = { checked ->
                            if (sleepTimerState.isActive) {
                                onToggleFinishSong()
                            }
                        },
                        enabled = sleepTimerState.isActive,
                        colors = SwitchDefaults.colors(
                            checkedThumbColor = MaterialTheme.colorScheme.onPrimary,
                            checkedTrackColor = MaterialTheme.colorScheme.primary,
                        ),
                    )
                }
            }
        }
    }
}

@Composable
private fun SectionHeader(title: String) {
    Text(
        text = title,
        style = MaterialTheme.typography.titleSmall,
        fontWeight = FontWeight.Bold,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(top = 4.dp),
    )
}

/**
 * A card wrapper for settings items (toggles, dropdowns).
 */
@Composable
private fun SettingsCard(content: @Composable () -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
        ),
        shape = RoundedCornerShape(12.dp),
    ) {
        content()
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ThemeModeDropdown(
    currentTheme: String,
    onThemeChanged: (String) -> Unit,
) {
    var expanded by remember { mutableStateOf(false) }
    val options = SettingsViewModel.THEME_OPTIONS
    val currentLabel = options.find { it.first == currentTheme }?.second ?: "跟随系统"

    ExposedDropdownMenuBox(
        expanded = expanded,
        onExpandedChange = { expanded = !expanded },
    ) {
        OutlinedTextField(
            value = currentLabel,
            onValueChange = {},
            readOnly = true,
            label = { Text("主题模式") },
            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 12.dp)
                .menuAnchor(MenuAnchorType.PrimaryNotEditable, enabled = true),
            shape = RoundedCornerShape(10.dp),
        )
        ExposedDropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false },
        ) {
            options.forEach { (value, label) ->
                DropdownMenuItem(
                    text = { Text(label) },
                    onClick = {
                        onThemeChanged(value)
                        expanded = false
                    },
                )
            }
        }
    }
}

// ═══════════════════════════════════════════════════════════════════════════
// Recommendation settings
// ═══════════════════════════════════════════════════════════════════════════

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun RecommendationSettingsSection(viewModel: SettingsViewModel) {
    val artistWeight by viewModel.recArtistWeight.collectAsState()
    val genreWeight by viewModel.recGenreWeight.collectAsState()
    val hotWeight by viewModel.recHotWeight.collectAsState()
    val recentExcludeDays by viewModel.recRecentExcludeDays.collectAsState()
    val topN by viewModel.recTopN.collectAsState()
    val coldStartThreshold by viewModel.recColdStartThreshold.collectAsState()
    val coldStartHotWeight by viewModel.recColdStartHotWeight.collectAsState()
    val skipThreshold by viewModel.recSkipThreshold.collectAsState()
    val skipBlockCount by viewModel.recSkipBlockCount.collectAsState()
    val timeDecayHalfLife by viewModel.recTimeDecayHalfLife.collectAsState()
    val maxCandidates by viewModel.recMaxCandidates.collectAsState()
    val minSongIndexSize by viewModel.recMinSongIndexSize.collectAsState()

    var showBottomSheet by remember { mutableStateOf(false) }

    // 状态摘要文本
    val songIndexTtlHours by viewModel.recSongIndexTtlHours.collectAsState()
    val statusText = "歌手${artistWeight} / 流派${genreWeight} / 热门${hotWeight} · 排除${recentExcludeDays}天 · Top${topN}"

    // ── 入口卡片 ────────────────────────────────────────────────
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { showBottomSheet = true },
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
        ),
        shape = RoundedCornerShape(12.dp),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 14.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column {
                Text(
                    "🎵 推荐设置",
                    fontWeight = FontWeight.Medium,
                    style = MaterialTheme.typography.bodyMedium,
                )
                Spacer(Modifier.height(2.dp))
                Text(
                    statusText,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }

    // ── 底部弹出配置面板 ─────────────────────────────────────────
    if (showBottomSheet) {
        val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
        ModalBottomSheet(
            onDismissRequest = { showBottomSheet = false },
            sheetState = sheetState,
            shape = RoundedCornerShape(topStart = 20.dp, topEnd = 20.dp),
            containerColor = MaterialTheme.colorScheme.surface,
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 16.dp)
                    .padding(bottom = 32.dp),
                verticalArrangement = Arrangement.spacedBy(0.dp),
            ) {
                Text(
                    "推荐设置",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(bottom = 12.dp),
                )

                SettingsSubHeader("策略权重（总和自动归一化）")

                RecSettingRow("歌手拓展权重", artistWeight, viewModel::onRecArtistWeightChanged,
                    KeyboardType.Decimal, "根据你喜爱和常听的歌手，推荐TA的其他未听歌曲。权重越高，歌手维度的推荐占比越大。",
                    isValid = { v -> v.toFloatOrNull()?.let { it in 0.0f..1.0f } == true })
                RecSettingRow("流派/专辑权重", genreWeight, viewModel::onRecGenreWeightChanged,
                    KeyboardType.Decimal, "根据你常听的音乐风格和专辑，推荐同流派、同专辑的未听歌曲。依赖歌曲元数据中的流派信息。",
                    isValid = { v -> v.toFloatOrNull()?.let { it in 0.0f..1.0f } == true })
                RecSettingRow("热门兜底权重", hotWeight, viewModel::onRecHotWeightChanged,
                    KeyboardType.Decimal, "推荐全平台热门歌曲。当用户是新用户或行为数据不足时，此权重会自动提高以确保有推荐结果。",
                    isValid = { v -> v.toFloatOrNull()?.let { it in 0.0f..1.0f } == true })

                Spacer(Modifier.height(8.dp))
                SettingsSubHeader("推荐基础参数")

                RecSettingRow("排除最近天数", recentExcludeDays, viewModel::onRecRecentExcludeDaysChanged,
                    KeyboardType.Number, "最近N天内播放过的歌曲不会出现在推荐中，避免重复推荐刚听过的歌。")
                RecSettingRow("推荐数量", topN, viewModel::onRecTopNChanged,
                    KeyboardType.Number, "每次推荐返回的歌曲数量上限，同时也影响Top N评分的N值。")

                Spacer(Modifier.height(8.dp))
                SettingsSubHeader("高级参数")

                RecSettingRow("跳过判定阈值", skipThreshold, viewModel::onRecSkipThresholdChanged,
                    KeyboardType.Decimal, "播放时长低于此比例（如0.15=15%）视为跳过。跳过的歌曲会被记录，超过屏蔽次数后不再推荐。",
                    isValid = { v -> v.toFloatOrNull()?.let { it in 0.0f..1.0f } == true })
                RecSettingRow("跳过屏蔽次数", skipBlockCount, viewModel::onRecSkipBlockCountChanged,
                    KeyboardType.Number, "同一首歌被跳过超过此次数后，永久从推荐中排除。设为较大值则可容忍更多跳过。")
                RecSettingRow("时间衰减半衰期(天)", timeDecayHalfLife, viewModel::onRecTimeDecayHalfLifeChanged,
                    KeyboardType.Number, "控制行为数据的时间衰减速度。30天表示30天前的播放行为权重衰减为一半，越近的行为影响越大。")
                RecSettingRow("每策略最大候选数", maxCandidates, viewModel::onRecMaxCandidatesChanged,
                    KeyboardType.Number, "每个推荐策略最多产出多少候选歌曲。防止单一策略垄断推荐结果，保证推荐多样性。")
                RecSettingRow("候选池最小阈值", minSongIndexSize, viewModel::onRecMinSongIndexSizeChanged,
                    KeyboardType.Number, "本地歌曲候选池低于此数量时，自动从平台搜索热门歌曲补充。增大此值可提高推荐质量但增加网络请求。")
                RecSettingRow("候选池过期时间（小时）", songIndexTtlHours, viewModel::onRecSongIndexTtlHoursChanged,
                    KeyboardType.Number, "候选池歌曲超过此小时数自动清理。设为0则每次推荐都清空重建（始终最新）。24=1天，72=3天。")
                RecSettingRow("冷启动阈值", coldStartThreshold, viewModel::onRecColdStartThresholdChanged,
                    KeyboardType.Number, "用户行为记录少于该数量时，自动切换为冷启动模式，提高热门兜底的权重。")
                RecSettingRow("冷启动热门权重", coldStartHotWeight, viewModel::onRecColdStartHotWeightChanged,
                    KeyboardType.Decimal, "冷启动模式下热门兜底的实际权重值，覆盖正常的热门权重。新用户主要靠此参数获得推荐。",
                    isValid = { v -> v.toFloatOrNull()?.let { it in 0.0f..1.0f } == true })
            }
        }
    }
}

@Composable
private fun RecSettingRow(
    label: String,
    value: String,
    onChanged: (String) -> Unit,
    keyboardType: KeyboardType = KeyboardType.Number,
    description: String? = null,
    isValid: (String) -> Boolean = { v -> v.toIntOrNull() != null },
) {
    var isEditing by remember { mutableStateOf(false) }
    var editText by remember(value) { mutableStateOf(value) }

    Column(modifier = Modifier
        .fillMaxWidth()
        .clickable { isEditing = !isEditing }
        .padding(vertical = 10.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                label,
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.weight(1f),
            )
            if (isEditing) {
                BasicTextField(
                    value = editText,
                    onValueChange = { editText = it },
                    singleLine = true,
                    modifier = Modifier.width(72.dp),
                    keyboardOptions = KeyboardOptions(keyboardType = keyboardType),
                    textStyle = MaterialTheme.typography.bodyMedium.copy(
                        fontWeight = FontWeight.Medium,
                        color = MaterialTheme.colorScheme.primary,
                        textAlign = TextAlign.Center,
                    ),
                    cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
                    decorationBox = { innerTextField ->
                        Box {
                            innerTextField()
                            HorizontalDivider(
                                modifier = Modifier.align(Alignment.BottomCenter).padding(top = 4.dp),
                                thickness = 1.dp,
                                color = MaterialTheme.colorScheme.primary,
                            )
                        }
                    },
                )
                Spacer(Modifier.width(6.dp))
                TextButton(onClick = {
                    if (editText.isNotBlank() && isValid(editText)) {
                        onChanged(editText)
                        isEditing = false
                    }
                }) {
                    Text("确定", style = MaterialTheme.typography.labelSmall)
                }
            } else {
                Text(
                    value,
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Medium,
                    color = MaterialTheme.colorScheme.primary,
                )
            }
        }
        if (description != null) {
            Text(
                text = description,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
                modifier = Modifier.padding(top = 4.dp),
            )
        }
    }
    HorizontalDivider(
        thickness = 0.5.dp,
        color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f),
    )
}

@Composable
private fun SettingsSubHeader(title: String) {
    Text(
        text = title,
        style = MaterialTheme.typography.labelMedium,
        fontWeight = FontWeight.SemiBold,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(top = 8.dp, bottom = 2.dp),
    )
}

// ═══════════════════════════════════════════════════════════════════════════
// Playback settings — bottom sheet entry
// ═══════════════════════════════════════════════════════════════════════════

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun PlaybackSettingsEntry(
    historyCount: Int,
    onHistoryCountChanged: (Int) -> Unit,
) {
    var showBottomSheet by remember { mutableStateOf(false) }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { showBottomSheet = true },
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
        ),
        shape = RoundedCornerShape(12.dp),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 14.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column {
                Text(
                    "播放选项",
                    fontWeight = FontWeight.Medium,
                    style = MaterialTheme.typography.bodyMedium,
                )
                Spacer(Modifier.height(2.dp))
                Text(
                    "历史记录保留 $historyCount 首",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }

    if (showBottomSheet) {
        val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
        ModalBottomSheet(
            onDismissRequest = { showBottomSheet = false },
            sheetState = sheetState,
            shape = RoundedCornerShape(topStart = 20.dp, topEnd = 20.dp),
            containerColor = MaterialTheme.colorScheme.surface,
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp)
                    .padding(bottom = 32.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                Text(
                    "播放选项",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(bottom = 8.dp),
                )

                SettingsSubHeader("历史记录数量")
                Text(
                    "控制播放历史最多保留多少条记录",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
                    modifier = Modifier.padding(start = 4.dp, top = 2.dp, bottom = 8.dp),
                )

                SettingsViewModel.HISTORY_OPTIONS.forEach { count ->
                    val isSelected = count == historyCount
                    Surface(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable {
                                onHistoryCountChanged(count)
                                showBottomSheet = false
                            },
                        shape = RoundedCornerShape(12.dp),
                        color = if (isSelected) MaterialTheme.colorScheme.primary.copy(alpha = 0.15f)
                                else MaterialTheme.colorScheme.surface,
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 16.dp, vertical = 14.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text(
                                "$count 首",
                                style = MaterialTheme.typography.bodyLarge,
                                fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Normal,
                                color = if (isSelected) MaterialTheme.colorScheme.primary
                                        else MaterialTheme.colorScheme.onSurface,
                            )
                            if (isSelected) {
                                Text(
                                    "已选择",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.primary,
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

// ═══════════════════════════════════════════════════════════════════════════
// Appearance settings — bottom sheet entry
// ═══════════════════════════════════════════════════════════════════════════

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AppearanceSettingsEntry(
    currentTheme: String,
    onThemeChanged: (String) -> Unit,
) {
    var showBottomSheet by remember { mutableStateOf(false) }
    val currentLabel = SettingsViewModel.THEME_OPTIONS.find { it.first == currentTheme }?.second ?: "跟随系统"

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { showBottomSheet = true },
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
        ),
        shape = RoundedCornerShape(12.dp),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 14.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column {
                Text(
                    "外观",
                    fontWeight = FontWeight.Medium,
                    style = MaterialTheme.typography.bodyMedium,
                )
                Spacer(Modifier.height(2.dp))
                Text(
                    currentLabel,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }

    if (showBottomSheet) {
        val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
        ModalBottomSheet(
            onDismissRequest = { showBottomSheet = false },
            sheetState = sheetState,
            shape = RoundedCornerShape(topStart = 20.dp, topEnd = 20.dp),
            containerColor = MaterialTheme.colorScheme.surface,
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp)
                    .padding(bottom = 32.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                Text(
                    "外观",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(bottom = 8.dp),
                )

                SettingsViewModel.THEME_OPTIONS.forEach { (value, label) ->
                    val isSelected = value == currentTheme
                    Surface(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable {
                                onThemeChanged(value)
                                showBottomSheet = false
                            },
                        shape = RoundedCornerShape(12.dp),
                        color = if (isSelected) MaterialTheme.colorScheme.primary.copy(alpha = 0.15f)
                                else MaterialTheme.colorScheme.surface,
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 16.dp, vertical = 14.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text(
                                label,
                                style = MaterialTheme.typography.bodyLarge,
                                fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Normal,
                                color = if (isSelected) MaterialTheme.colorScheme.primary
                                        else MaterialTheme.colorScheme.onSurface,
                            )
                            if (isSelected) {
                                Text(
                                    "已选择",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.primary,
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}
