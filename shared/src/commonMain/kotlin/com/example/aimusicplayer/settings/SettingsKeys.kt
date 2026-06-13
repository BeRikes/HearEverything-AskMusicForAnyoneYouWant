package com.example.aimusicplayer.settings

/**
 * Key constants for persisted settings.
 * All storage is handled cross-platform via expect/actual [SettingsStorage].
 */
object SettingsKeys {
    // ── AI provider ─────────────────────────────────────────────────
    /** API provider base URL, e.g. "https://api.deepseek.com/v1" */
    const val API_BASE_URL = "api_base_url"
    /** User's API authentication key */
    const val API_KEY = "api_key"
    /** Default base URL when none is configured */
    const val DEFAULT_BASE_URL = "https://api.deepseek.com/v1"
    /** Minimum valid API key length */
    const val MIN_KEY_LENGTH = 10

    // ── Playback ────────────────────────────────────────────────────
    /** Whether background playback is enabled (Android foreground service / iOS AudioSession) */
    const val BACKGROUND_PLAYBACK = "background_playback"
    /** Whether to show synchronized lyrics when available */
    const val LYRICS_SYNC = "lyrics_sync"
    /** Default download quality: "low", "standard", "high" */
    const val DEFAULT_QUALITY = "default_quality"
    /** Default quality value */
    const val DEFAULT_QUALITY_VALUE = "standard"
    /** Maximum number of history records to keep. Default 20, options: 10/20/30/50/100. */
    const val HISTORY_COUNT = "history_count"
    const val DEFAULT_HISTORY_COUNT = 20

    // ── Lyrics ────────────────────────────────────────────────────────
    /** Per-song lyrics offset map as JSON: {"songId": offsetMs, ...}. B站 only. */
    const val LYRICS_OFFSETS = "lyrics_offsets"

    // ── Theme ───────────────────────────────────────────────────────
    /** Dark mode preference: "system", "light", "dark" */
    const val THEME_MODE = "theme_mode"
    const val THEME_MODE_SYSTEM = "system"
    const val THEME_MODE_LIGHT = "light"
    const val THEME_MODE_DARK = "dark"

    // ── Recommendation ─────────────────────────────────────────────
    /** 推荐策略权重：歌手拓展 (0.0 ~ 1.0) */
    const val REC_ARTIST_WEIGHT = "rec_artist_weight"
    const val DEFAULT_REC_ARTIST_WEIGHT = "0.40"
    /** 推荐策略权重：流派/专辑 (0.0 ~ 1.0) */
    const val REC_GENRE_WEIGHT = "rec_genre_weight"
    const val DEFAULT_REC_GENRE_WEIGHT = "0.35"
    /** 推荐策略权重：热门兜底 (0.0 ~ 1.0) */
    const val REC_HOT_WEIGHT = "rec_hot_weight"
    const val DEFAULT_REC_HOT_WEIGHT = "0.25"
    /** 最近 N 天内播放过的歌曲不推荐 */
    const val REC_RECENT_EXCLUDE_DAYS = "rec_recent_exclude_days"
    const val DEFAULT_REC_RECENT_EXCLUDE_DAYS = "7"
    /** 默认推荐数量 */
    const val REC_TOP_N = "rec_top_n"
    const val DEFAULT_REC_TOP_N = "10"
    /** 冷启动阈值：行为条数少于此值启用冷启动 */
    const val REC_COLD_START_THRESHOLD = "rec_cold_start_threshold"
    const val DEFAULT_REC_COLD_START_THRESHOLD = "10"
    /** 冷启动时热门策略权重 */
    const val REC_COLD_START_HOT_WEIGHT = "rec_cold_start_hot_weight"
    const val DEFAULT_REC_COLD_START_HOT_WEIGHT = "0.60"
    /** 跳过判定阈值：播放时长占比低于此值算跳过 */
    const val REC_SKIP_THRESHOLD = "rec_skip_threshold"
    const val DEFAULT_REC_SKIP_THRESHOLD = "0.15"
    /** 跳过 N 次后不再推荐 */
    const val REC_SKIP_BLOCK_COUNT = "rec_skip_block_count"
    const val DEFAULT_REC_SKIP_BLOCK_COUNT = "5"
    /** 时间衰减半衰期（天） */
    const val REC_TIME_DECAY_HALF_LIFE = "rec_time_decay_half_life"
    const val DEFAULT_REC_TIME_DECAY_HALF_LIFE = "30"
    /** 每策略最大候选输出数 */
    const val REC_MAX_CANDIDATES_PER_STRATEGY = "rec_max_candidates_per_strategy"
    const val DEFAULT_REC_MAX_CANDIDATES_PER_STRATEGY = "20"
    /** 候选池最小阈值（低于此值触发自动补充） */
    const val REC_MIN_SONG_INDEX_SIZE = "rec_min_song_index_size"
    const val DEFAULT_REC_MIN_SONG_INDEX_SIZE = "50"
    /** 候选池过期时间（小时，0=永不过期） */
    const val REC_SONG_INDEX_TTL_HOURS = "rec_song_index_ttl_hours"
    const val DEFAULT_REC_SONG_INDEX_TTL_HOURS = "24"
    /** MusicBrainz 上次同步时间戳 */
    const val REC_LAST_SYNC_TIME = "rec_last_sync_time"
}
