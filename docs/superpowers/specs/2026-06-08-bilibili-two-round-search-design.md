# B站歌单导入搜索优化 — 设计文档

**日期：** 2026-06-08
**状态：** 已确认

---

## 背景

Bilibili（B站）是一个用户上传视频平台。视频标题中可能**不包含歌手名**，仅注明歌名。当前搜索逻辑对所有平台统一使用 `"歌手名 歌名"` 作为搜索关键词，导致 B 站搜索在有歌手名时匹配率降低——标题不含歌手的歌曲会被过滤掉。

## 需求

当歌手名已指定时，B 站搜索改为两轮：

1. **第1轮：** `"歌名 歌手名"` 严格匹配，搜最多 3 首
2. **第2轮：** `"歌名"` 忽略歌手，搜最多 3 首

歌手名为空或"未知歌手"时：仅用 `"歌名"` 搜最多 3 首。

同样逻辑应用到两个场景：
- **歌单导入搜索**（`PlaylistImportManager`）
- **系统歌曲搜索**（`SearchViewModel`）

## 设计

### 改动范围

| 文件 | 方法 | 场景 |
|------|------|------|
| `PlaylistImportManager.kt` | `searchTrackStreaming()` | 歌单导入时的逐曲搜索 |
| `SearchViewModel.kt` | `searchAcrossPlatforms()` | 系统搜索面板 |

### 核心逻辑

```kotlin
// 伪代码
if (platform == "bilibili") {
    val hasArtist = artistName.isNotBlank() && artistName != "未知歌手"

    // Round 1: 歌名 + 歌手名严格匹配
    if (hasArtist) {
        val round1Query = "$songName $artistName"
        // search → filter → resolve URLs → up to 3 results
    }

    // Round 2: 仅歌名（加上去重，避免与第1轮重复）
    if (allResults.size < maxPerPlatform * 2) {
        val round2Query = songName
        // search → filter → dedup by songId → resolve URLs → up to 3 results
    }
} else {
    // 其他平台逻辑不变
}
```

### 歌手名有效性判断

歌手名**有效**的条件：`artistName.isNotBlank() && artistName != "未知歌手"`

这与 `BilibiliGuaranteedResolver.buildStrategies()` 中的判断逻辑一致。

### 结果去重

第2轮搜索的结果按 `songId` 去重，避免与第1轮结果重复。

### 与现有 `BilibiliGuaranteedResolver` 的关系

`BilibiliGuaranteedResolver` 已在 Phase 2 截断回退场景中实现了多策略 B 站搜索（5种query组合）。本次改动让**正常搜索流程**中的 B 站也采用两轮策略。两者互补，互不冲突：

- `BilibiliGuaranteedResolver`：VIP截断回退，5种策略级联
- 本次改动：正常搜索流程，2轮搜索

### 不影响

- 其他平台（netease、qq、kugou）搜索逻辑完全不变
- 结果过滤逻辑（VIP过滤、歌名匹配）不变
- UI 层无改动
