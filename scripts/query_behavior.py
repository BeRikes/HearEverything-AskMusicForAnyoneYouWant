#!/usr/bin/env python3
"""查询用户行为记录数据库（behavior.db）的全部信息。

用法:
    python scripts/query_behavior.py <behavior.db路径>              # 打印全部记录
    python scripts/query_behavior.py <behavior.db路径> --summary    # 仅摘要统计
    python scripts/query_behavior.py <behavior.db路径> --liked      # 仅喜欢记录
    python scripts/query_behavior.py <behavior.db路径> --platform bilibili  # 按平台筛选

依赖: Python 3.6+ (仅需标准库 sqlite3)
"""

import argparse
import sqlite3
import sys
from datetime import datetime, timezone, timedelta

# Windows 终端 UTF-8 支持
if sys.platform == "win32":
    try:
        sys.stdout.reconfigure(encoding="utf-8", errors="replace")
    except AttributeError:
        pass

# 北京时间
CST = timezone(timedelta(hours=8))


def ts_to_str(ms: int | None) -> str:
    if not ms:
        return "N/A"
    try:
        return datetime.fromtimestamp(ms / 1000, tz=CST).strftime("%Y-%m-%d %H:%M:%S")
    except (OSError, ValueError):
        return "N/A"


def ms_to_duration(ms: int) -> str:
    if ms <= 0:
        return "—"
    s = ms // 1000
    m, s = divmod(s, 60)
    return f"{m}:{s:02d}"


def fmt_bool(v: int) -> str:
    return "Y" if v else " "


# ── Queries ──────────────────────────────────────────────────────────────

def table_exists(cur: sqlite3.Cursor, table: str) -> bool:
    cur.execute(
        "SELECT name FROM sqlite_master WHERE type='table' AND name=?",
        (table,),
    )
    return cur.fetchone() is not None


def query_table(db_path: str, table: str, platform: str | None = None):
    """通用查询，自动处理表不存在的情况"""
    conn = sqlite3.connect(db_path)
    conn.row_factory = sqlite3.Row
    cur = conn.cursor()

    if not table_exists(cur, table):
        conn.close()
        return None  # 表不存在

    if platform:
        cur.execute(
            f"SELECT * FROM {table} WHERE platform = ? ORDER BY played_at DESC",
            (platform,),
        )
    else:
        order_col = "played_at" if table == "user_behavior" else "created_at"
        cur.execute(f"SELECT * FROM {table} ORDER BY {order_col} DESC")
    rows = cur.fetchall()
    conn.close()
    return rows


def query_user_behavior(db_path: str, platform: str | None = None):
    return query_table(db_path, "user_behavior", platform)


def query_song_index(db_path: str, platform: str | None = None):
    return query_table(db_path, "song_index", platform)


# ── Display ──────────────────────────────────────────────────────────────

def print_divider():
    print("─" * 140)


def print_user_behavior(rows, title="[用户行为记录] user_behavior"):
    if rows is None:
        print(f"\n{title}\n  (表不存在 - 尚未产生任何行为记录)")
        return
    if not rows:
        print(f"\n{title}\n  (空)")
        return

    print(f"\n{title}  - 共 {len(rows)} 条\n")
    # 表头
    header = (
        f"{'ID':>4}  {'歌名':<50} {'歌手':<28} {'平台':<10} "
        f"{'时长':>6} {'播放':>6} {'完':>2} {'喜':>2} {'跳':>2} {'下':>2} "
        f"{'播放时间':<20} {'歌曲ID'}"
    )
    print(header)
    print_divider()
    for r in rows:
        line = (
            f"{r['id']:>4}  "
            f"{r['song_name']:<50} "
            f"{r['artist']:<28} "
            f"{r['platform']:<10} "
            f"{ms_to_duration(r['duration_ms']):>6} "
            f"{ms_to_duration(r['play_duration_ms']):>6} "
            f"{fmt_bool(r['completed']):>2} "
            f"{fmt_bool(r['liked']):>2} "
            f"{fmt_bool(r['skipped']):>2} "
            f"{fmt_bool(r['downloaded']):>2} "
            f"{ts_to_str(r['played_at']):<20} "
            f"{r['song_id']}"
        )
        print(line)
    print_divider()


def print_song_index(rows, title="[候选池] song_index"):
    if rows is None:
        print(f"\n{title}\n  (表不存在 — 尚未构建候选池)")
        return
    if not rows:
        print(f"\n{title}\n  (空)")
        return

    print(f"\n{title}  — 共 {len(rows)} 条\n")
    header = (
        f"{'歌名':<24} {'歌手':<18} {'平台':<10} {'时长':>6} "
        f"{'来源':<14} {'全局播放':>8} {'入库时间':<20}"
    )
    print(header)
    print_divider()
    for r in rows:
        line = (
            f"{r['song_name'][:24]:<24} "
            f"{r['artist'][:18]:<18} "
            f"{r['platform']:<10} "
            f"{ms_to_duration(r['duration_ms']):>6} "
            f"{r['source']:<14} "
            f"{r['play_count_global']:>8} "
            f"{ts_to_str(r['created_at']):<20}"
        )
        print(line)
    print_divider()


def print_summary(behaviors, index_rows):
    """打印汇总统计"""
    behaviors = behaviors or []
    index_rows = index_rows or []
    total = len(behaviors)
    liked = sum(1 for r in behaviors if r["liked"])
    downloaded = sum(1 for r in behaviors if r["downloaded"])
    completed = sum(1 for r in behaviors if r["completed"])
    skipped = sum(1 for r in behaviors if r["skipped"])

    # 按平台统计
    by_platform = {}
    for r in behaviors:
        p = r["platform"]
        by_platform[p] = by_platform.get(p, 0) + 1

    # 按歌手统计 (top 10)
    by_artist = {}
    for r in behaviors:
        a = r["artist"] if r["artist"] else "(空)"
        by_artist[a] = by_artist.get(a, 0) + 1
    top_artists = sorted(by_artist.items(), key=lambda x: -x[1])[:10]

    print("\n[汇总统计]\n")
    print(f"  行为记录总数: {total}")
    print(f"  喜欢 (liked):         {liked:>4}")
    print(f"  下载 (downloaded):    {downloaded:>4}")
    print(f"  完整播放 (completed):  {completed:>4}")
    print(f"  跳过 (skipped):       {skipped:>4}")
    print(f"  候选池歌曲数:          {len(index_rows)}")

    if by_platform:
        print("\n  按平台分布:")
        for p, c in sorted(by_platform.items(), key=lambda x: -x[1]):
            print(f"    {p:<12} {c:>4}")

    if top_artists:
        print("\n  Top 10 歌手:")
        for artist, count in top_artists:
            print(f"    {artist[:20]:<22} {count:>4}")


# ── Main ─────────────────────────────────────────────────────────────────

def main():
    parser = argparse.ArgumentParser(description="查询用户行为记录数据库")
    parser.add_argument("db_path", nargs="?", default="behavior.db", help="behavior.db 文件路径")
    parser.add_argument("--summary", "-s", action="store_true", help="仅显示摘要统计")
    parser.add_argument("--liked", "-l", action="store_true", help="仅显示喜欢记录")
    parser.add_argument("--downloaded", "-d", action="store_true", help="仅显示下载记录")
    parser.add_argument("--platform", "-p", type=str, help="按平台筛选 (netease/qq/kugou/bilibili)")
    parser.add_argument("--index", "-i", action="store_true", help="显示候选池 (song_index)")
    args = parser.parse_args()

    try:
        behaviors = query_user_behavior(args.db_path, args.platform)
    except sqlite3.OperationalError as e:
        print(f"[ERROR] 无法打开数据库: {e}", file=sys.stderr)
        sys.exit(1)

    index_rows = query_song_index(args.db_path, args.platform)

    # 筛选
    if behaviors and args.liked:
        behaviors = [r for r in behaviors if r["liked"]]
    if behaviors and args.downloaded:
        behaviors = [r for r in behaviors if r["downloaded"]]

    if args.summary:
        print_summary(behaviors, index_rows)
    else:
        print_user_behavior(behaviors)
        if args.index:
            print_song_index(index_rows)
        print_summary(behaviors, index_rows)


if __name__ == "__main__":
    main()
