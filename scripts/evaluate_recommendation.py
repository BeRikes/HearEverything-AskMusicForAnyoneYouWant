#!/usr/bin/env python3
"""
离线评估脚本 —— 验证推荐算法有效性。

用法:
    python scripts/evaluate_recommendation.py [behavior.db路径] [song_index.db路径]

默认路径:
    - behavior.db: 查找项目根目录下的 shared/build/ 或指定路径
    - song_index.db: 同上

指标:
    - HitRate@10: 推荐 Top10 中命中用户实际听过歌曲的比例
    - Precision@10: Top10 中用户喜欢的歌曲占比
    - Coverage: 推荐覆盖的不同歌手数量

支持 --grid-search 模式自动遍历权重组合找最优配置。
"""

import sqlite3
import math
import sys
import os
from datetime import datetime, timedelta
from typing import Optional
from dataclasses import dataclass, field
from collections import Counter
from itertools import product


# ═══════════════════════════════════════════════════════════════════════════
# Data models
# ═══════════════════════════════════════════════════════════════════════════

@dataclass
class UserBehavior:
    id: int
    song_id: str
    song_name: str
    artist: str
    platform: str
    album: Optional[str]
    genre: Optional[str]
    year: Optional[int]
    duration_ms: int
    play_duration_ms: int
    completed: bool
    liked: bool
    skipped: bool
    downloaded: bool
    played_at: int

@dataclass
class SongIndexEntry:
    song_id: str
    platform: str
    song_name: str
    artist: str
    album: Optional[str]
    genre: Optional[str]
    year: Optional[int]
    play_count_global: int

@dataclass
class ScoredCandidate:
    song: SongIndexEntry
    score: float
    strategy_name: str
    reason_template: str

@dataclass
class RecommendationResult:
    song_id: str
    platform: str
    song_name: str
    artist: str
    score: float
    reason: str
    strategy_name: str

@dataclass
class RecommendationConfig:
    artist_weight: float = 0.40
    genre_weight: float = 0.35
    hot_weight: float = 0.25
    cold_start_threshold: int = 10
    cold_start_hot_weight: float = 0.60
    recent_exclude_days: int = 7
    skip_threshold: float = 0.30
    skip_block_count: int = 3
    top_n: int = 10
    time_decay_half_life: int = 30
    max_candidates_per_strategy: int = 20

    @property
    def is_cold_start(self) -> bool:
        return False  # set externally

    def effective_weights(self) -> tuple[float, float, float]:
        """冷启动时调整权重。"""
        if not self.is_cold_start:
            return self.artist_weight, self.genre_weight, self.hot_weight
        remaining = 1.0 - self.cold_start_hot_weight
        total = self.artist_weight + self.genre_weight
        scale = remaining / total if total > 0 else 0.0
        return (self.artist_weight * scale,
                self.genre_weight * scale,
                self.cold_start_hot_weight)


# ═══════════════════════════════════════════════════════════════════════════
# Time decay
# ═══════════════════════════════════════════════════════════════════════════

def time_decay(event_time_ms: int, now_ms: int, half_life_days: int = 30) -> float:
    days_since = (now_ms - event_time_ms) / (24 * 3600 * 1000.0)
    if days_since <= 0:
        return 1.0
    lam = math.log(2) / half_life_days
    return max(0.0, math.exp(-lam * days_since))


# ═══════════════════════════════════════════════════════════════════════════
# Strategies
# ═══════════════════════════════════════════════════════════════════════════

def strategy_artist_expansion(
    history: list[UserBehavior],
    song_index: list[SongIndexEntry],
    config: RecommendationConfig,
    now_ms: int,
) -> list[ScoredCandidate]:
    """歌手拓展推荐。"""
    artist_scores: dict[str, float] = {}
    for bh in history:
        if not bh.artist:
            continue
        decay = time_decay(bh.played_at, now_ms, config.time_decay_half_life)
        score = decay
        if bh.completed:
            score += decay * 2.0
        if bh.liked:
            score += decay * 5.0
        artist_scores[bh.artist] = artist_scores.get(bh.artist, 0.0) + score

    top_artists = sorted(artist_scores.items(), key=lambda x: -x[1])[:5]
    top_artists = [a for a, _ in top_artists]

    played_keys = {f"{bh.song_id}|{bh.platform}" for bh in history}
    candidates = []
    for artist in top_artists:
        artist_weight = artist_scores.get(artist, 1.0)
        for entry in song_index:
            if entry.artist != artist:
                continue
            if f"{entry.song_id}|{entry.platform}" in played_keys:
                continue
            score = artist_weight * (1.0 + entry.play_count_global / 1000.0)
            candidates.append(ScoredCandidate(
                song=entry, score=score, strategy_name="artist",
                reason_template=f"因为你喜欢{artist}，推荐TA的另一首歌《{entry.song_name}》"
            ))
    candidates.sort(key=lambda c: -c.score)
    return candidates[:config.max_candidates_per_strategy]


def strategy_genre_album(
    history: list[UserBehavior],
    song_index: list[SongIndexEntry],
    config: RecommendationConfig,
    now_ms: int,
) -> list[ScoredCandidate]:
    """流派/专辑相似推荐。"""
    genre_scores: dict[str, float] = {}
    album_scores: dict[str, float] = {}
    years = []
    for bh in history:
        decay = time_decay(bh.played_at, now_ms, config.time_decay_half_life)
        if bh.genre:
            genre_scores[bh.genre] = genre_scores.get(bh.genre, 0.0) + decay
        if bh.album:
            album_scores[bh.album] = album_scores.get(bh.album, 0.0) + decay
        if bh.year and bh.year > 1900:
            years.append(bh.year)

    top_genres = sorted(genre_scores.items(), key=lambda x: -x[1])[:3]
    top_genres = [g for g, _ in top_genres]
    median_year = sorted(years)[len(years) // 2] if years else None

    played_keys = {f"{bh.song_id}|{bh.platform}" for bh in history}
    candidates = []
    for entry in song_index:
        key = f"{entry.song_id}|{entry.platform}"
        if key in played_keys:
            continue
        score = 0.0
        if entry.genre and entry.genre in top_genres:
            score += genre_scores.get(entry.genre, 1.0) * 3.0
        if entry.album and entry.album in album_scores:
            score += album_scores.get(entry.album, 1.0) * 2.0
        if entry.year is not None and median_year is not None:
            diff = abs(entry.year - median_year)
            if diff <= 3:
                score += (3.0 - diff) * 0.5
        score += entry.play_count_global / 10000.0
        if score > 0:
            genre_str = entry.genre or (top_genres[0] if top_genres else "你的听歌习惯")
            candidates.append(ScoredCandidate(
                song=entry, score=score, strategy_name="genre",
                reason_template=f"基于你常听的{genre_str}风格，推荐《{entry.song_name}》"
            ))
    candidates.sort(key=lambda c: -c.score)
    return candidates[:config.max_candidates_per_strategy]


def strategy_hot_fallback(
    history: list[UserBehavior],
    song_index: list[SongIndexEntry],
    config: RecommendationConfig,
) -> list[ScoredCandidate]:
    """热门兜底推荐。"""
    played_keys = {f"{bh.song_id}|{bh.platform}" for bh in history}
    sorted_index = sorted(song_index, key=lambda e: -e.play_count_global)
    max_count = max(config.max_candidates_per_strategy, config.top_n * 2)
    candidates = []
    for entry in sorted_index:
        if f"{entry.song_id}|{entry.platform}" in played_keys:
            continue
        score = min(max(entry.play_count_global / 1000.0, 0.1), 10.0)
        candidates.append(ScoredCandidate(
            song=entry, score=score, strategy_name="hot",
            reason_template=f"热门歌曲推荐：《{entry.song_name}》"
        ))
        if len(candidates) >= max_count:
            break
    return candidates


# ═══════════════════════════════════════════════════════════════════════════
# Filters
# ═══════════════════════════════════════════════════════════════════════════

def negative_feedback_filter(
    candidates: list[ScoredCandidate],
    history: list[UserBehavior],
    config: RecommendationConfig,
) -> list[ScoredCandidate]:
    """负反馈过滤。"""
    by_song: dict[str, list[UserBehavior]] = {}
    for bh in history:
        key = f"{bh.song_id}|{bh.platform}"
        by_song.setdefault(key, []).append(bh)

    blocked = set()
    for key, behaviors in by_song.items():
        has_dislike = any(not b.liked and not b.completed for b in behaviors)
        skip_count = sum(1 for b in behaviors if b.skipped)
        too_many_skips = skip_count >= config.skip_block_count
        if has_dislike or too_many_skips:
            blocked.add(key)

    return [c for c in candidates
            if f"{c.song.song_id}|{c.song.platform}" not in blocked]


def recent_play_filter(
    candidates: list[ScoredCandidate],
    history: list[UserBehavior],
    config: RecommendationConfig,
    now_ms: int,
) -> list[ScoredCandidate]:
    """近期播放去重。"""
    cutoff_ms = now_ms - config.recent_exclude_days * 24 * 3600 * 1000
    recent_keys = {f"{bh.song_id}|{bh.platform}"
                   for bh in history if bh.played_at >= cutoff_ms}
    return [c for c in candidates
            if f"{c.song.song_id}|{c.song.platform}" not in recent_keys]


# ═══════════════════════════════════════════════════════════════════════════
# Core engine
# ═══════════════════════════════════════════════════════════════════════════

def recommend(
    history: list[UserBehavior],
    song_index: list[SongIndexEntry],
    config: RecommendationConfig,
    now_ms: Optional[int] = None,
    exclude_recent: bool = True,
) -> list[RecommendationResult]:
    """执行推荐（Python 版等价实现）。"""
    if now_ms is None:
        now_ms = history[-1].played_at if history else 0

    is_cold = len(history) < config.cold_start_threshold
    config.is_cold_start = is_cold
    artist_w, genre_w, hot_w = config.effective_weights()

    # 各策略生成候选
    strategy_results = [
        strategy_artist_expansion(history, song_index, config, now_ms),
        strategy_genre_album(history, song_index, config, now_ms),
        strategy_hot_fallback(history, song_index, config),
    ]
    weights = [artist_w, genre_w, hot_w]

    # 加权合并
    weight_sum = sum(weights)
    norm_weights = [w / weight_sum if weight_sum > 0 else 1.0 / len(weights) for w in weights]

    score_map: dict[str, ScoredCandidate] = {}
    for idx, candidates in enumerate(strategy_results):
        w = norm_weights[idx]
        for c in candidates:
            key = f"{c.song.song_id}|{c.song.platform}"
            weighted = c.score * w
            if key not in score_map or weighted > score_map[key].score:
                score_map[key] = ScoredCandidate(
                    song=c.song, score=weighted,
                    strategy_name=c.strategy_name,
                    reason_template=c.reason_template,
                )

    aggregated = sorted(score_map.values(), key=lambda c: -c.score)

    # 过滤
    aggregated = negative_feedback_filter(aggregated, history, config)
    if exclude_recent:
        aggregated = recent_play_filter(aggregated, history, config, now_ms)

    # 输出 Top N
    top_n = aggregated[:config.top_n]
    return [
        RecommendationResult(
            song_id=c.song.song_id, platform=c.song.platform,
            song_name=c.song.song_name, artist=c.song.artist,
            score=c.score, reason=c.reason_template,
            strategy_name=c.strategy_name,
        )
        for c in top_n
    ]


# ═══════════════════════════════════════════════════════════════════════════
# Database I/O
# ═══════════════════════════════════════════════════════════════════════════

def load_behavior_db(path: str) -> list[UserBehavior]:
    """读取 behavior.db 中的 user_behavior 表。"""
    conn = sqlite3.connect(path)
    conn.row_factory = sqlite3.Row
    rows = conn.execute("SELECT * FROM user_behavior ORDER BY played_at ASC").fetchall()
    conn.close()
    return [
        UserBehavior(
            id=r["id"], song_id=r["song_id"], song_name=r["song_name"],
            artist=r["artist"], platform=r["platform"],
            album=r["album"] if r["album"] else None,
            genre=r["genre"] if r["genre"] else None,
            year=r["year"] if r["year"] else None,
            duration_ms=r["duration_ms"] or 0,
            play_duration_ms=r["play_duration_ms"] or 0,
            completed=bool(r["completed"]), liked=bool(r["liked"]),
            skipped=bool(r["skipped"]), downloaded=bool(r["downloaded"]),
            played_at=r["played_at"],
        )
        for r in rows
    ]


def load_song_index(path: str) -> list[SongIndexEntry]:
    """读取 song_index 表。"""
    conn = sqlite3.connect(path)
    conn.row_factory = sqlite3.Row
    rows = conn.execute("SELECT * FROM song_index").fetchall()
    conn.close()
    return [
        SongIndexEntry(
            song_id=r["song_id"], platform=r["platform"],
            song_name=r["song_name"], artist=r["artist"],
            album=r["album"] if r["album"] else None,
            genre=r["genre"] if r["genre"] else None,
            year=r["year"] if r["year"] else None,
            play_count_global=r["play_count_global"] or 0,
        )
        for r in rows
    ]


# ═══════════════════════════════════════════════════════════════════════════
# Evaluation metrics
# ═══════════════════════════════════════════════════════════════════════════

def evaluate(
    train_history: list[UserBehavior],
    test_history: list[UserBehavior],
    song_index: list[SongIndexEntry],
    config: RecommendationConfig,
    n: int = 10,
) -> dict:
    """评估推荐算法在测试集上的表现。"""
    if not test_history or not song_index:
        return {"hit_rate": 0.0, "precision": 0.0, "coverage": 0}

    results = recommend(train_history, song_index, config, exclude_recent=True)
    top_n = results[:n]

    # 测试集中实际听过的歌曲
    test_keys = {f"{bh.song_id}|{bh.platform}" for bh in test_history}
    # 测试集中喜欢的歌曲
    test_liked = {f"{bh.song_id}|{bh.platform}" for bh in test_history if bh.liked}

    # HitRate@N：推荐列表中命中测试集歌曲的比例
    hits = sum(1 for r in top_n if f"{r.song_id}|{r.platform}" in test_keys)
    hit_rate = hits / len(test_keys) if test_keys else 0.0

    # Precision@N：Top N 中用户喜欢的比例
    liked_hits = sum(1 for r in top_n if f"{r.song_id}|{r.platform}" in test_liked)
    precision = liked_hits / n if n > 0 else 0.0

    # Coverage：不同歌手数量
    unique_artists = len(set(r.artist for r in top_n))

    return {"hit_rate": round(hit_rate, 4), "precision": round(precision, 4), "coverage": unique_artists}


# ═══════════════════════════════════════════════════════════════════════════
# Grid search
# ═══════════════════════════════════════════════════════════════════════════

def grid_search(
    train_history: list[UserBehavior],
    test_history: list[UserBehavior],
    song_index: list[SongIndexEntry],
) -> list[tuple[RecommendationConfig, dict]]:
    """遍历权重组合找最优配置。"""
    artist_weights = [0.3, 0.4, 0.5]
    genre_weights = [0.25, 0.35, 0.45]
    hot_weights = [0.15, 0.25, 0.35]
    exclude_days = [3, 7, 14]
    skip_block_counts = [2, 3, 5]

    best = []
    total = len(artist_weights) * len(genre_weights) * len(hot_weights) * len(exclude_days) * len(skip_block_counts)
    count = 0

    for aw, gw, hw, ed, sbc in product(artist_weights, genre_weights, hot_weights, exclude_days, skip_block_counts):
        # 归一化权重
        s = aw + gw + hw
        config = RecommendationConfig(
            artist_weight=aw / s, genre_weight=gw / s, hot_weight=hw / s,
            recent_exclude_days=ed, skip_block_count=sbc,
        )
        metrics = evaluate(train_history, test_history, song_index, config)
        best.append((config, metrics))
        count += 1
        if count % 20 == 0:
            print(f"  grid search progress: {count}/{total}")

    best.sort(key=lambda x: -x[1]["hit_rate"])
    return best[:10]


# ═══════════════════════════════════════════════════════════════════════════
# Main
# ═══════════════════════════════════════════════════════════════════════════

def main():
    behavior_path = sys.argv[1] if len(sys.argv) > 1 else None
    index_path = sys.argv[2] if len(sys.argv) > 2 else None
    grid_mode = "--grid-search" in sys.argv

    # 默认路径
    if not behavior_path:
        candidates = [
            "shared/build/behavior.db",
            "behavior.db",
        ]
        for p in candidates:
            if os.path.exists(p):
                behavior_path = p
                break
        if not behavior_path:
            print("❌ 未找到 behavior.db，请指定路径")
            print("用法: python scripts/evaluate_recommendation.py [behavior.db路径] [song_index.db路径]")
            sys.exit(1)

    if not index_path:
        candidates = [
            "shared/build/song_index.db",
            "song_index.db",
            behavior_path,  # song_index 可能在同一文件中
        ]
        for p in candidates:
            if os.path.exists(p):
                index_path = p
                break
        if not index_path:
            print("❌ 未找到 song_index.db")
            sys.exit(1)

    print(f"📂 behavior.db: {behavior_path}")
    print(f"📂 song_index.db: {index_path}")

    # 加载数据
    all_behaviors = load_behavior_db(behavior_path)
    song_index = load_song_index(index_path)

    if len(all_behaviors) < 20:
        print(f"⚠️ 行为数据仅 {len(all_behaviors)} 条，评估结果仅供参考（建议 > 100 条）")
    print(f"📊 行为记录: {len(all_behaviors)} 条")
    print(f"📊 候选池: {len(song_index)} 首")

    # 80/20 时间切分
    split_idx = int(len(all_behaviors) * 0.8)
    train = all_behaviors[:split_idx]
    test = all_behaviors[split_idx:]

    train_start = datetime.fromtimestamp(train[0].played_at / 1000) if train else None
    train_end = datetime.fromtimestamp(train[-1].played_at / 1000) if train else None
    test_end = datetime.fromtimestamp(test[-1].played_at / 1000) if test else None
    print(f"📅 训练集: {len(train)} 条 ({train_start} ~ {train_end})")
    print(f"📅 测试集: {len(test)} 条 ({train_end} ~ {test_end})")

    # 评估
    print("\n" + "=" * 60)
    print("评估结果（默认配置）")
    print("=" * 60)

    default_config = RecommendationConfig()
    metrics = evaluate(train, test, song_index, default_config)
    print(f"  HitRate@10:  {metrics['hit_rate']:.4f}  (越高越好)")
    print(f"  Precision@10: {metrics['precision']:.4f}  (越高越好)")
    print(f"  Coverage:     {metrics['coverage']} 歌手  (越高越好)")

    # 示例推荐输出
    print("\n" + "=" * 60)
    print("示例推荐结果（Top 5）")
    print("=" * 60)
    results = recommend(train, song_index, default_config, exclude_recent=False)
    for i, r in enumerate(results[:5], 1):
        print(f"  {i}. {r.song_name} — {r.artist}")
        print(f"     {r.reason}  [分数: {r.score:.2f}, 策略: {r.strategy_name}]")

    # 网格搜索
    if grid_mode:
        print("\n" + "=" * 60)
        print("参数网格搜索（可能需要几分钟）")
        print("=" * 60)
        best_configs = grid_search(train, test, song_index)
        print("\n最优配置 Top 5:")
        for i, (cfg, m) in enumerate(best_configs[:5], 1):
            print(f"  {i}. hit_rate={m['hit_rate']:.4f} prec={m['precision']:.4f} "
                  f"artist={cfg.artist_weight:.2f} genre={cfg.genre_weight:.2f} "
                  f"hot={cfg.hot_weight:.2f} excl_days={cfg.recent_exclude_days} "
                  f"skip_block={cfg.skip_block_count}")


if __name__ == "__main__":
    main()
