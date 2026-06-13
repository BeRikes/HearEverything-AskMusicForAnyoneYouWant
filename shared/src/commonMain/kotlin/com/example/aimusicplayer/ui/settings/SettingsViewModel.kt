package com.example.aimusicplayer.ui.settings

import com.example.aimusicplayer.ai.AiAssistant
import com.example.aimusicplayer.music.DownloadManager
import com.example.aimusicplayer.settings.SettingsKeys
import com.example.aimusicplayer.settings.SettingsRepository
import com.example.aimusicplayer.viewmodel.ViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * ViewModel for the app settings screen.
 *
 * Manages AI provider configuration, playback preferences, theme mode,
 * cache inspection, and connection testing.
 */
class SettingsViewModel(
    private val settingsRepo: SettingsRepository,
    private val aiAssistant: AiAssistant,
    private val downloadManager: DownloadManager,
) : ViewModel() {

    // ── AI config state ─────────────────────────────────────────────

    private val _baseUrl = MutableStateFlow("")
    val baseUrl: StateFlow<String> = _baseUrl.asStateFlow()

    private val _apiKey = MutableStateFlow("")
    val apiKey: StateFlow<String> = _apiKey.asStateFlow()

    private val _isTesting = MutableStateFlow(false)
    val isTesting: StateFlow<Boolean> = _isTesting.asStateFlow()

    private val _testResult = MutableStateFlow<TestResult?>(null)
    val testResult: StateFlow<TestResult?> = _testResult.asStateFlow()

    // ── Theme ───────────────────────────────────────────────────────

    private val _themeMode = MutableStateFlow(SettingsKeys.THEME_MODE_SYSTEM)
    val themeMode: StateFlow<String> = _themeMode.asStateFlow()

    // ── Storage ─────────────────────────────────────────────────────

    private val _cacheSizeText = MutableStateFlow("计算中...")
    val cacheSizeText: StateFlow<String> = _cacheSizeText.asStateFlow()

    private val _isClearingCache = MutableStateFlow(false)
    val isClearingCache: StateFlow<Boolean> = _isClearingCache.asStateFlow()

    private val _cacheClearedSnackbar = MutableStateFlow<String?>(null)
    val cacheClearedSnackbar: StateFlow<String?> = _cacheClearedSnackbar.asStateFlow()

    // ── History ─────────────────────────────────────────────────────

    private val _historyCount = MutableStateFlow(SettingsKeys.DEFAULT_HISTORY_COUNT)
    val historyCount: StateFlow<Int> = _historyCount.asStateFlow()

    // ── Recommendation config ────────────────────────────────────────

    private val _recArtistWeight = MutableStateFlow(SettingsKeys.DEFAULT_REC_ARTIST_WEIGHT)
    val recArtistWeight: StateFlow<String> = _recArtistWeight.asStateFlow()

    private val _recGenreWeight = MutableStateFlow(SettingsKeys.DEFAULT_REC_GENRE_WEIGHT)
    val recGenreWeight: StateFlow<String> = _recGenreWeight.asStateFlow()

    private val _recHotWeight = MutableStateFlow(SettingsKeys.DEFAULT_REC_HOT_WEIGHT)
    val recHotWeight: StateFlow<String> = _recHotWeight.asStateFlow()

    private val _recRecentExcludeDays = MutableStateFlow(SettingsKeys.DEFAULT_REC_RECENT_EXCLUDE_DAYS)
    val recRecentExcludeDays: StateFlow<String> = _recRecentExcludeDays.asStateFlow()

    private val _recTopN = MutableStateFlow(SettingsKeys.DEFAULT_REC_TOP_N)
    val recTopN: StateFlow<String> = _recTopN.asStateFlow()

    private val _recColdStartThreshold = MutableStateFlow(SettingsKeys.DEFAULT_REC_COLD_START_THRESHOLD)
    val recColdStartThreshold: StateFlow<String> = _recColdStartThreshold.asStateFlow()

    private val _recColdStartHotWeight = MutableStateFlow(SettingsKeys.DEFAULT_REC_COLD_START_HOT_WEIGHT)
    val recColdStartHotWeight: StateFlow<String> = _recColdStartHotWeight.asStateFlow()

    private val _recSkipThreshold = MutableStateFlow(SettingsKeys.DEFAULT_REC_SKIP_THRESHOLD)
    val recSkipThreshold: StateFlow<String> = _recSkipThreshold.asStateFlow()

    private val _recSkipBlockCount = MutableStateFlow(SettingsKeys.DEFAULT_REC_SKIP_BLOCK_COUNT)
    val recSkipBlockCount: StateFlow<String> = _recSkipBlockCount.asStateFlow()

    private val _recTimeDecayHalfLife = MutableStateFlow(SettingsKeys.DEFAULT_REC_TIME_DECAY_HALF_LIFE)
    val recTimeDecayHalfLife: StateFlow<String> = _recTimeDecayHalfLife.asStateFlow()

    private val _recMaxCandidates = MutableStateFlow(SettingsKeys.DEFAULT_REC_MAX_CANDIDATES_PER_STRATEGY)
    val recMaxCandidates: StateFlow<String> = _recMaxCandidates.asStateFlow()

    private val _recMinSongIndexSize = MutableStateFlow(SettingsKeys.DEFAULT_REC_MIN_SONG_INDEX_SIZE)
    val recMinSongIndexSize: StateFlow<String> = _recMinSongIndexSize.asStateFlow()

    private val _recSongIndexTtlHours = MutableStateFlow(SettingsKeys.DEFAULT_REC_SONG_INDEX_TTL_HOURS)
    val recSongIndexTtlHours: StateFlow<String> = _recSongIndexTtlHours.asStateFlow()

    // ── Load ────────────────────────────────────────────────────────

    init {
        loadSettings()
        loadCacheSize()
    }

    /** Called after download completes, song deleted, or cache cleared. */
    fun refreshCacheSize() {
        scope.launch {
            try {
                val songs = downloadManager.getDownloadedSongs()
                val totalBytes = downloadManager.getTotalDownloadSize()
                val count = songs.size
                val sizeMB = totalBytes / (1024 * 1024)
                _cacheSizeText.value = if (count == 0) "缓存为空"
                    else "约 $sizeMB MB ($count 首下载歌曲)"
            } catch (_: Exception) {
                _cacheSizeText.value = "无法获取"
            }
        }
    }

    private fun loadSettings() {
        scope.launch {
            _baseUrl.value = settingsRepo.getBaseUrl()
            _apiKey.value = settingsRepo.getApiKey()
            _historyCount.value = settingsRepo.getHistoryCount()
            _themeMode.value = settingsRepo.getThemeMode()
            // 推荐配置
            _recArtistWeight.value = settingsRepo.getRaw(SettingsKeys.REC_ARTIST_WEIGHT) ?: SettingsKeys.DEFAULT_REC_ARTIST_WEIGHT
            _recGenreWeight.value = settingsRepo.getRaw(SettingsKeys.REC_GENRE_WEIGHT) ?: SettingsKeys.DEFAULT_REC_GENRE_WEIGHT
            _recHotWeight.value = settingsRepo.getRaw(SettingsKeys.REC_HOT_WEIGHT) ?: SettingsKeys.DEFAULT_REC_HOT_WEIGHT
            _recRecentExcludeDays.value = settingsRepo.getRaw(SettingsKeys.REC_RECENT_EXCLUDE_DAYS) ?: SettingsKeys.DEFAULT_REC_RECENT_EXCLUDE_DAYS
            _recTopN.value = settingsRepo.getRaw(SettingsKeys.REC_TOP_N) ?: SettingsKeys.DEFAULT_REC_TOP_N
            _recColdStartThreshold.value = settingsRepo.getRaw(SettingsKeys.REC_COLD_START_THRESHOLD) ?: SettingsKeys.DEFAULT_REC_COLD_START_THRESHOLD
            _recColdStartHotWeight.value = settingsRepo.getRaw(SettingsKeys.REC_COLD_START_HOT_WEIGHT) ?: SettingsKeys.DEFAULT_REC_COLD_START_HOT_WEIGHT
            _recSkipThreshold.value = settingsRepo.getRaw(SettingsKeys.REC_SKIP_THRESHOLD) ?: SettingsKeys.DEFAULT_REC_SKIP_THRESHOLD
            _recSkipBlockCount.value = settingsRepo.getRaw(SettingsKeys.REC_SKIP_BLOCK_COUNT) ?: SettingsKeys.DEFAULT_REC_SKIP_BLOCK_COUNT
            _recTimeDecayHalfLife.value = settingsRepo.getRaw(SettingsKeys.REC_TIME_DECAY_HALF_LIFE) ?: SettingsKeys.DEFAULT_REC_TIME_DECAY_HALF_LIFE
            _recMaxCandidates.value = settingsRepo.getRaw(SettingsKeys.REC_MAX_CANDIDATES_PER_STRATEGY) ?: SettingsKeys.DEFAULT_REC_MAX_CANDIDATES_PER_STRATEGY
            _recMinSongIndexSize.value = settingsRepo.getRaw(SettingsKeys.REC_MIN_SONG_INDEX_SIZE) ?: SettingsKeys.DEFAULT_REC_MIN_SONG_INDEX_SIZE
            _recSongIndexTtlHours.value = settingsRepo.getRaw(SettingsKeys.REC_SONG_INDEX_TTL_HOURS) ?: SettingsKeys.DEFAULT_REC_SONG_INDEX_TTL_HOURS
        }
    }

    private fun loadCacheSize() {
        refreshCacheSize()
    }

    // ── Actions ─────────────────────────────────────────────────────

    fun onBaseUrlChanged(url: String) { _baseUrl.value = url }
    fun onApiKeyChanged(key: String) { _apiKey.value = key }
    fun onHistoryCountChanged(count: Int) {
        _historyCount.value = count
        scope.launch { settingsRepo.setHistoryCount(count) }
    }
    fun onThemeModeChanged(mode: String) {
        _themeMode.value = mode
        scope.launch { settingsRepo.setThemeMode(mode) }
    }

    /** Save all AI settings and persist. */
    fun saveAiSettings() {
        scope.launch {
            settingsRepo.setBaseUrl(_baseUrl.value)
            settingsRepo.setApiKey(_apiKey.value)
        }
    }

    /** Test the API connection with current settings. */
    fun testConnection() {
        scope.launch {
            _isTesting.value = true
            _testResult.value = null

            // Save current values before testing
            settingsRepo.setBaseUrl(_baseUrl.value)
            settingsRepo.setApiKey(_apiKey.value)

            val result = aiAssistant.testConnection()
            _testResult.value = if (result == null) {
                TestResult.Success
            } else {
                TestResult.Failure(result)
            }
            _isTesting.value = false
        }
    }

    /** Clear download cache. */
    fun clearCache() {
        scope.launch {
            _isClearingCache.value = true
            try {
                val songs = downloadManager.getDownloadedSongs()
                val count = songs.size
                downloadManager.clearAllDownloads()
                loadCacheSize()
                _cacheClearedSnackbar.value = "缓存已清空 ($count 首歌曲)"
            } catch (e: Exception) {
                _cacheClearedSnackbar.value = "清空缓存失败: ${e.message}"
            }
            _isClearingCache.value = false
        }
    }

    fun clearSnackbar() { _cacheClearedSnackbar.value = null }
    fun clearTestResult() { _testResult.value = null }

    // ── Recommendation actions（变更立即持久化）─────────────────────

    fun onRecArtistWeightChanged(v: String) { _recArtistWeight.value = v; saveRecKey(SettingsKeys.REC_ARTIST_WEIGHT, v) }
    fun onRecGenreWeightChanged(v: String) { _recGenreWeight.value = v; saveRecKey(SettingsKeys.REC_GENRE_WEIGHT, v) }
    fun onRecHotWeightChanged(v: String) { _recHotWeight.value = v; saveRecKey(SettingsKeys.REC_HOT_WEIGHT, v) }
    fun onRecRecentExcludeDaysChanged(v: String) { _recRecentExcludeDays.value = v; saveRecKey(SettingsKeys.REC_RECENT_EXCLUDE_DAYS, v) }
    fun onRecTopNChanged(v: String) { _recTopN.value = v; saveRecKey(SettingsKeys.REC_TOP_N, v) }
    fun onRecColdStartThresholdChanged(v: String) { _recColdStartThreshold.value = v; saveRecKey(SettingsKeys.REC_COLD_START_THRESHOLD, v) }
    fun onRecColdStartHotWeightChanged(v: String) { _recColdStartHotWeight.value = v; saveRecKey(SettingsKeys.REC_COLD_START_HOT_WEIGHT, v) }
    fun onRecSkipThresholdChanged(v: String) { _recSkipThreshold.value = v; saveRecKey(SettingsKeys.REC_SKIP_THRESHOLD, v) }
    fun onRecSkipBlockCountChanged(v: String) { _recSkipBlockCount.value = v; saveRecKey(SettingsKeys.REC_SKIP_BLOCK_COUNT, v) }
    fun onRecTimeDecayHalfLifeChanged(v: String) { _recTimeDecayHalfLife.value = v; saveRecKey(SettingsKeys.REC_TIME_DECAY_HALF_LIFE, v) }
    fun onRecMaxCandidatesChanged(v: String) { _recMaxCandidates.value = v; saveRecKey(SettingsKeys.REC_MAX_CANDIDATES_PER_STRATEGY, v) }
    fun onRecMinSongIndexSizeChanged(v: String) { _recMinSongIndexSize.value = v; saveRecKey(SettingsKeys.REC_MIN_SONG_INDEX_SIZE, v) }
    fun onRecSongIndexTtlHoursChanged(v: String) { _recSongIndexTtlHours.value = v; saveRecKey(SettingsKeys.REC_SONG_INDEX_TTL_HOURS, v) }

    private fun saveRecKey(key: String, value: String) {
        scope.launch { settingsRepo.putRaw(key, value) }
    }

    /** Save all settings and persist. */
    fun saveAll() {
        scope.launch {
            settingsRepo.setBaseUrl(_baseUrl.value)
            settingsRepo.setApiKey(_apiKey.value)
            settingsRepo.setThemeMode(_themeMode.value)
            // 推荐配置
            settingsRepo.putRaw(SettingsKeys.REC_ARTIST_WEIGHT, _recArtistWeight.value)
            settingsRepo.putRaw(SettingsKeys.REC_GENRE_WEIGHT, _recGenreWeight.value)
            settingsRepo.putRaw(SettingsKeys.REC_HOT_WEIGHT, _recHotWeight.value)
            settingsRepo.putRaw(SettingsKeys.REC_RECENT_EXCLUDE_DAYS, _recRecentExcludeDays.value)
            settingsRepo.putRaw(SettingsKeys.REC_TOP_N, _recTopN.value)
            settingsRepo.putRaw(SettingsKeys.REC_COLD_START_THRESHOLD, _recColdStartThreshold.value)
            settingsRepo.putRaw(SettingsKeys.REC_COLD_START_HOT_WEIGHT, _recColdStartHotWeight.value)
            settingsRepo.putRaw(SettingsKeys.REC_SKIP_THRESHOLD, _recSkipThreshold.value)
            settingsRepo.putRaw(SettingsKeys.REC_SKIP_BLOCK_COUNT, _recSkipBlockCount.value)
            settingsRepo.putRaw(SettingsKeys.REC_TIME_DECAY_HALF_LIFE, _recTimeDecayHalfLife.value)
            settingsRepo.putRaw(SettingsKeys.REC_MAX_CANDIDATES_PER_STRATEGY, _recMaxCandidates.value)
            settingsRepo.putRaw(SettingsKeys.REC_MIN_SONG_INDEX_SIZE, _recMinSongIndexSize.value)
            settingsRepo.putRaw(SettingsKeys.REC_SONG_INDEX_TTL_HOURS, _recSongIndexTtlHours.value)
        }
    }

    companion object {
        val HISTORY_OPTIONS = listOf(10, 20, 30, 50, 100)
        val THEME_OPTIONS = listOf(
            SettingsKeys.THEME_MODE_SYSTEM to "跟随系统",
            SettingsKeys.THEME_MODE_LIGHT to "浅色",
            SettingsKeys.THEME_MODE_DARK to "深色",
        )
    }
}

/** Result of testing the AI API connection. */
sealed class TestResult {
    data object Success : TestResult()
    data class Failure(val message: String) : TestResult()
}
