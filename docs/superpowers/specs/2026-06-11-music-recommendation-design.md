# 音乐推荐功能设计规范

**日期**: 2026-06-11  
**版本**: v1.0  
**范围**: HearEverything 单用户本地音乐推荐系统

---

## 1. 目标与约束

### 目标
为 HearEverything 单用户音乐播放器实现高质量、可解释的本地音乐推荐。根据用户的听歌行为（播放次数、喜欢标记、跳过、下载等），从候选曲库中推荐用户未听过或很少听的新歌曲。

### 约束
- **纯本地运行**：推荐算法在本地计算，不依赖任何远程 AI 服务
- **可解释**：每条推荐结果附带推荐理由
- **高质量**：多策略组合评分，负反馈抑制
- **不触发反爬**：候选池使用 MusicBrainz（免费开放 API）+ 本地累积

---

## 2. 架构设计

### 2.1 新增包结构

所有新代码位于 `shared/src/commonMain/kotlin/com/example/aimusicplayer/` 下：

```
recommendation/
├── RecommendationEngine.kt        # 推荐引擎核心
├── RecommendationStrategy.kt      # 策略接口 + 数据类
├── strategies/
│   ├── ArtistExpansionStrategy.kt # 策略1: 歌手拓展推荐
│   ├── GenreAlbumStrategy.kt      # 策略2: 流派/专辑相似推荐
│   └── HotFallbackStrategy.kt     # 策略3: 热门兜底 + 冷启动
├── filters/
│   ├── NegativeFeedbackFilter.kt  # 负反馈过滤
│   └── RecentPlayFilter.kt        # 近期播放去重
├── scoring/
│   ├── ScoreAggregator.kt         # 多策略分数加权合并
│   └── TimeDecay.kt               # 时间衰减函数
├── reason/
│   └── ReasonGenerator.kt         # 推荐理由生成
├── config/
│   └── RecommendationConfig.kt    # 配置参数
└── data/
    ├── UserBehaviorRepository.kt  # 行为数据读取
    └── LocalSongIndex.kt          # 本地歌曲索引（候选池）

behavior/
├── UserBehaviorTracker.kt         # 行为收集器
└── UserBehaviorEntity.kt          # 行为数据类

network/music/
└── MusicBrainzApi.kt              # MusicBrainz API 客户端
```

### 2.2 数据流

```
1. 用户播放歌曲 → UserBehaviorTracker 记录行为 → behavior.db
2. 用户在对话 Tab 触发推荐 → AI 解析意图 → 推荐指令
3. RecommendationEngine.recommend(n):
   a. LocalSongIndex 获取候选池（不足则 MusicBrainz 增量同步）
   b. UserBehaviorRepository 读取用户行为
   c. 各策略并行计算候选 + 分数
   d. ScoreAggregator 加权合并
   e. RecentPlayFilter + NegativeFeedbackFilter 过滤
   f. ReasonGenerator 生成理由
   g. 返回 Top N 推荐列表
4. 推荐结果以 AI 消息卡片形式展示在对话 Tab
```

### 2.3 与现有代码的关系

- **不修改** `MusicSearchManager`、`MusicPlatformApi`、缓存系统
- **新增** `behavior` 和 `recommendation` 包，与现有代码解耦
- **扩展** `SettingsKeys` 添加推荐配置键
- **修改** `AiAssistant` / `MainViewModel` 支持推荐意图解析
- **修改** 播放器相关逻辑，添加行为追踪钩子

---

## 3. 数据库设计

### 3.1 behavior.db — 用户行为数据库

```sql
CREATE TABLE user_behavior (
    id INTEGER PRIMARY KEY AUTOINCREMENT,
    song_id TEXT NOT NULL,
    song_name TEXT NOT NULL,
    artist TEXT NOT NULL,
    platform TEXT NOT NULL,
    album TEXT,
    genre TEXT,
    year INTEGER,
    duration_ms INTEGER DEFAULT 0,
    play_duration_ms INTEGER DEFAULT 0,
    completed INTEGER DEFAULT 0,       -- 是否听完 (>90%)
    liked INTEGER DEFAULT 0,           -- 0=无, 1=喜欢
    skipped INTEGER DEFAULT 0,         -- 是否算跳过 (<30%)
    downloaded INTEGER DEFAULT 0,
    played_at INTEGER NOT NULL,
    created_at INTEGER NOT NULL
);
CREATE INDEX idx_behavior_song ON user_behavior(song_id, platform);
CREATE INDEX idx_behavior_played_at ON user_behavior(played_at DESC);
CREATE INDEX idx_behavior_artist ON user_behavior(artist);
```

### 3.2 song_index.db — 本地歌曲元数据索引（候选池）

```sql
CREATE TABLE song_index (
    song_id TEXT NOT NULL,
    platform TEXT NOT NULL,            -- "netease"/"qq"/"bilibili"/"musicbrainz"
    song_name TEXT NOT NULL,
    artist TEXT NOT NULL,
    album TEXT,
    genre TEXT,
    year INTEGER,
    duration_ms INTEGER DEFAULT 0,
    cover_url TEXT,
    play_count_global INTEGER DEFAULT 0,
    source TEXT NOT NULL,              -- "search"/"history"/"musicbrainz"
    created_at INTEGER NOT NULL,
    PRIMARY KEY (song_id, platform)
);
CREATE INDEX idx_song_artist ON song_index(artist);
CREATE INDEX idx_song_genre ON song_index(genre);
CREATE INDEX idx_song_play_count ON song_index(play_count_global DESC);
```

### 3.3 数据库隔离原则

- `behavior.db` 和 `song_index.db` 独立，不合并到现有 `cache.db` 或 `playlist.db`
- 理由：职责分离、生命周期不同、离线评估脚本可独立读取

---

## 4. 推荐算法

### 4.1 策略接口

```kotlin
interface RecommendationStrategy {
    suspend fun recommend(
        userHistory: List<UserBehavior>,
        songIndex: List<SongIndexEntry>,
        config: RecommendationConfig,
        n: Int
    ): List<ScoredRecommendation>
}
```

### 4.2 策略1：歌手拓展推荐（默认权重 40%）

- 统计用户行为中播放次数最多 + 喜欢标记最多的 top 5 歌手
- 从 song_index 查找这些歌手的全部歌曲
- 排除已播放过的歌曲
- 推荐理由：`"因为你喜欢{歌手}，推荐TA的另一首歌《{歌名}》"`

### 4.3 策略2：流派/专辑推荐（默认权重 35%）

- 统计用户最常听的流派 top 3
- 查找同流派、同专辑、发行年份 ±3 年内的歌曲
- 同专辑歌曲额外加分
- 推荐理由：`"基于你常听的{流派}风格，推荐《{歌名}》"`

### 4.4 策略3：热门兜底 + 冷启动（默认权重 25%）

- 按 play_count_global 降序选候选
- 排除已听过歌曲
- 当用户行为 < 10 条时，此策略权重自动提升至 60%
- 推荐理由：`"热门歌曲推荐：《{歌名}》"`

### 4.5 过滤器（统一后处理）

1. **负反馈过滤**：排除 `liked = 0` 且未完成的歌曲；排除跳过 ≥ 3 次的歌曲
2. **近期播放去重**：排除最近 7 天内播放过的歌曲（天数可配置）
3. **去重**：同一歌曲只保留分数最高的条目

---

## 5. 候选池策略

### 5.1 数据来源

**MusicBrainz（主）+ 本地累积（辅）**：

- MusicBrainz：免费、无需 API Key、开源、元数据丰富（流派、年份、关系图谱）
- 本地累积：日常使用中搜索/播放过的歌曲自动入库
- 混合模式：MusicBrainz 初始填充 + 本地持续增长

### 5.2 MusicBrainz API

在 `network/music/MusicBrainzApi.kt` 中实现：

- `searchArtist(name)` → 获取 MBID
- `getArtistRecordings(mbid, limit)` → 该歌手全部歌曲
- `browseReleasesByArtist(mbid)` → 专辑/年份信息
- `searchByGenre(genre, limit)` → 热门流派歌曲

请求频率：每秒 1 次（遵守 MusicBrainz 使用规范）

### 5.3 索引构建触发

- 首次使用推荐功能时，后台构建（显示进度）
- 设置中提供"刷新推荐候选池"按钮，手动增量更新
- 增量策略：只更新新喜欢/新常听的歌手

---

## 6. 用户行为追踪

### 6.1 行为记录时机

| 行为 | 触发条件 | 关键字段 |
|------|---------|---------|
| 播放 | `isPlaying` 变为 true | song_id, played_at, duration_ms |
| 完成 | `currentPosition >= duration * 0.9` | completed=1, play_duration_ms |
| 跳过 | 播放 < 30% 时长且切歌 | skipped=1, play_duration_ms |
| 喜欢 | 用户点击红心 | liked=1 |
| 下载 | 下载完成 | downloaded=1 |

### 6.2 实现方式

`UserBehaviorTracker` 在 `MainViewModel.init` 中启动协程，监听 `MusicPlayer` 的 StateFlow（`isPlaying`、`currentSong`、`currentPosition`、`duration`），计算播放行为类型并写入 `behavior.db`。

### 6.3 喜欢/不喜欢交互

- 播放器界面新增红心按钮（❤/🤍 切换）
- 搜索结果列表每首歌也有红心按钮
- 推荐卡片中也有红心按钮
- 不喜欢通过行为推断（多次跳过即不喜欢），无独立不喜欢按钮

---

## 7. 配置系统

### 7.1 配置键

扩展 `SettingsKeys.kt`：

| 配置键 | 类型 | 默认值 | 说明 |
|--------|------|--------|------|
| `REC_ARTIST_WEIGHT` | Float | 0.40 | 歌手策略权重 |
| `REC_GENRE_WEIGHT` | Float | 0.35 | 流派/专辑策略权重 |
| `REC_HOT_WEIGHT` | Float | 0.25 | 热门兜底策略权重 |
| `REC_RECENT_EXCLUDE_DAYS` | Int | 7 | 最近 N 天内播过的歌不推荐 |
| `REC_TOP_N` | Int | 10 | 默认推荐数量 |
| `REC_COLD_START_THRESHOLD` | Int | 10 | 冷启动阈值 |
| `REC_COLD_START_HOT_WEIGHT` | Float | 0.60 | 冷启动时热门权重 |
| `REC_SKIP_THRESHOLD` | Float | 0.30 | 播放占比低于此值算跳过 |
| `REC_SKIP_BLOCK_COUNT` | Int | 3 | 跳过 ≥ N 次后不再推荐 |
| `REC_TIME_DECAY_HALF_LIFE` | Int | 30 | 时间衰减半衰期（天） |
| `REC_MAX_CANDIDATES_PER_STRATEGY` | Int | 20 | 每策略最大候选数 |
| `REC_LAST_SYNC_TIME` | Long | 0 | MusicBrainz 上次同步时间戳 |

### 7.2 设置 UI

在 `SettingsScreen` 新增"推荐设置"区域：
- 核心参数放在前面：策略权重、排除天数、推荐数量 — 用输入框（非滑动条）
- 高级参数放在后面：冷启动配置、跳过阈值、衰减参数
- 三权重可不归一（引擎内部归一化）

---

## 8. 离线评估脚本

### 8.1 脚本位置

`scripts/evaluate_recommendation.py`

### 8.2 评估流程

1. 读取 `behavior.db`，按 `played_at` 排序
2. 80/20 时间切分：前 80% 训练集，后 20% 测试集
3. 用训练集构建用户画像（常听歌手、流派、播放次数）
4. 用等价 Python 实现运行推荐逻辑，生成 Top 10
5. 计算指标：
   - **HitRate@10**：Top 10 命中测试集歌曲的比例
   - **Precision@10**：Top 10 中用户喜欢的比例
   - **Coverage**：推荐覆盖了多少不同歌手
6. 支持参数网格搜索，找最优权重组合

### 8.3 技术选型

使用 Python（pandas + numpy），项目已有 Python 脚本先例（`scripts/verify_nc_encrypt.py`）。

---

## 9. UI 集成

### 9.1 触发方式

在 `AiAssistant` 中新增推荐意图解析。用户输入"推荐一些歌"、"有什么好听的"等 → AI 解析为推荐指令 → `AiCommand(action = "recommend")`。

### 9.2 交互流程

```
用户: "给我推荐几首歌"
→ AiAssistant 解析为 AiCommand(action="recommend", count=10)
→ MainViewModel.handleRecommend():
    1. 检查 song_index.db 候选池是否充足（> 50 首）
    2. 不足 → 触发 MusicBrainz 增量同步（提示"正在构建候选池..."）
    3. 调用 RecommendationEngine.recommend(n=10)
    4. 将推荐结果转为 AI 消息（contentType = "recommendation"）
    5. 展示在对话气泡中
```

### 9.3 推荐卡片格式

每首推荐歌曲：歌名 + 歌手 + 推荐理由 + **搜索按钮** + 喜欢按钮

- **搜索按钮**：推荐歌曲来自 MusicBrainz，无播放 URL。点击搜索按钮 → 在三大平台搜索该歌曲 → 找到可播放的 URL → 开始播放
- **喜欢按钮**：标记喜欢，反馈给推荐系统

---

## 10. 验证计划

### 10.1 单元测试（commonTest）

- 各策略独立测试（用 Fake 数据）
- 过滤器测试（负反馈、去重）
- ScoreAggregator 加权合并逻辑
- TimeDecay 衰减曲线验证
- ReasonGenerator 理由模板
- UserBehaviorTracker 行为判定逻辑

### 10.2 集成测试（androidInstrumentedTest）

- 完整推荐流程（从行为读取到推荐输出）
- MusicBrainz API 集成测试
- 缓存与行为数据库读写

### 10.3 离线评估

- `scripts/evaluate_recommendation.py` 验证算法有效性
- HitRate@10 目标 > 0.15（冷启动阶段可接受较低值，随数据增长提升）

### 10.4 手动验证

- 模拟用户播放不同歌手的歌曲 → 验证推荐是否偏向这些歌手
- 标记喜欢/跳过 → 验证负反馈过滤是否生效
- 冷启动场景（清空数据库）→ 验证热门兜底是否正常

---

## 11. 实施阶段建议

| 阶段 | 范围 | 产出 |
|------|------|------|
| Phase 1 | 数据库 + 行为追踪 | behavior.db, song_index.db, UserBehaviorTracker |
| Phase 2 | 推荐引擎核心 | RecommendationEngine + 3个策略 + 过滤器 + 评分 |
| Phase 3 | MusicBrainz 集成 | MusicBrainzApi + SongIndexBuilder |
| Phase 4 | UI 集成 | AI 意图解析 + 推荐卡片 + 红心按钮 |
| Phase 5 | 配置 + 设置页面 | 推荐设置 UI + 配置读写 |
| Phase 6 | 评估脚本 | evaluate_recommendation.py |
