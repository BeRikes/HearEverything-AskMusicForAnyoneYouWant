# QQ音乐 & 酷狗音乐歌单导入 — 设计文档

**日期**: 2026-06-08
**状态**: 已确认

## 背景

HearEverything 现有歌单导入功能（Phase 1）仅支持网易云音乐分享链接。代码中已预留 QQ音乐（Phase 2）和酷狗音乐（Phase 3）的 URL 解析正则，但被注释掉；`QQMusicApi` 和 `KugouApi` 缺少 `getPlaylistDetail()` 实现；UI 文案硬编码了"网易云音乐"。

## 目标

支持用户粘贴 QQ音乐和酷狗音乐的分享链接，完成与网易云一致的歌单导入全流程（解析链接 → 获取歌单详情 → 跨平台搜索匹配 → 确认导入）。

## 设计原则

- **最小改动**：`PlaylistImportManager`、状态机、Card Relay UI 均为平台无关，不需要改动
- **遵循现有模式**：`QQMusicApi.getPlaylistDetail()` 和 `KugouApi.getPlaylistDetail()` 的结构参考 `NeteaseApi.getPlaylistDetail()` 的实现
- **复用现有签名**：QQ音乐复用 `MusicSignUtils.qqSign`，酷狗复用 `MusicSignUtils.kugouSign`

## 改动范围

### 1. `PlaylistLinkParser.kt` — 激活并修正 URL 解析

**QQ音乐真实分享格式**:
```
https://i2.y.qq.com/n3/other/pages/details/playlist.html?platform=11&appshare=android_qq&appversion=20050008&hosteuin=...&id=772368334&ADTAG=wxfshare
```
- 歌单 ID 在 `id=` 查询参数中，纯数字
- 修正正则为匹配 `y.qq.com/...playlist...?...id=(\d+)`

**酷狗音乐真实分享格式**:
```
https://m.kugou.com/songlist/gcid_3zx4xam7z4z058/?src_cid=3zx4xam7z4z058&uid=1740652277&chl=wechat&iszlist=1
```
- 歌单 ID 在路径中，格式 `gcid_<alphanumeric>`
- 修正正则为匹配 `kugou.com/songlist/(gcid_[a-zA-Z0-9]+)`

**`isMusicLink()`** 同步更新，包含所有三个平台的模式。

### 2. `QQMusicApi.kt` — 实现 `getPlaylistDetail()`

**API 选择**: 调用 QQ音乐内部 CGI `fcg_ucc_getcdinfo_byids_cp.fcg`，该接口通过歌单 ID 返回歌单元数据和歌曲列表。

**端点**: `GET https://c.y.qq.com/qzone/fcg-bin/fcg_ucc_getcdinfo_byids_cp.fcg`

**参数**:
- `format=json`
- `disstid=<playlistId>`
- `sign=<qqSign(params)>`

**响应解析**:
- `cdlist[0].dissname` → `PlaylistDetail.title`
- `cdlist[0].logo` → `PlaylistDetail.coverUrl`
- `cdlist[0].songlist[]` → 每项提取 `songname`、`singer[].name` → `PlaylistTrack(songName, artist)`

**错误处理**: 与 `NeteaseApi.getPlaylistDetail()` 一致——code 非 0 返回 null，网络异常返回 null。

### 3. `KugouApi.kt` — 实现 `getPlaylistDetail()`

**API 选择**: 调用酷狗移动端 API `mobilecdn.kugou.com/api/v3/songlist/info`。

**参数处理**: 从 URL 解析出的 ID 格式为 `gcid_<id>`（如 `gcid_3zx4xam7z4z058`），需要去掉 `gcid_` 前缀，裸 ID 传给 API 的 `special_tag` 或 `global_collection_id` 参数（待对接确认）。

**端点**: `GET https://mobilecdn.kugou.com/api/v3/songlist/info`

**参数**:
- `format=json`
- `global_collection_id=<id>` 或 `special_tag=<id>`
- `page=1` `pagesize=500`
- `signature=<kugouSign(params)>`

**响应解析**:
- `data.info.list[].songname` → `PlaylistTrack.songName`
- `data.info.list[].singername` → `PlaylistTrack.artist`
- `data.info.name` → `PlaylistDetail.title`
- `data.info.img` → `PlaylistDetail.coverUrl`

**SSL 注意**: 酷狗 `mobilecdn.kugou.com` 有 CDN 证书不匹配问题，需复用 `KugouApi` 已有的 `createKugouHttpClient()`（已配置 SSL bypass）。

### 4. UI 文案更新

| 文件 | 位置 | 旧文案 | 新文案 |
|------|------|--------|--------|
| `ImportTab.kt` | `OutlinedTextField.placeholder` | "粘贴网易云音乐歌单分享链接" | "粘贴音乐平台歌单分享链接" |
| `ImportTab.kt` | 空状态提示 | "粘贴网易云音乐分享链接开始导入" | "粘贴音乐平台分享链接开始导入" |
| `ImportTab.kt` | `ImportedPlaylistCard` | "网易云" (硬编码) | 显示 `playlist.platform` 对应的中文名 |
| `PlaylistImportManager.kt` | 错误提示 | "无法识别的链接，请粘贴网易云音乐歌单分享链接" | "无法识别的链接，请粘贴 QQ音乐/网易云/酷狗 歌单分享链接" |
| `PlaylistImportManager.kt` | 错误提示 | "暂不支持 $currentPlatform 平台" | 保留通用格式，已自动适用 |

**`ImportedPlaylistCard` 平台名映射**: 与 `PlatformBadgeLabel` 已有的映射一致：`netease` → "网易云"、`qq` → "QQ音乐"、`kugou` → "酷狗"。

## 不改动的部分

- `PlaylistImportManager.kt` — 导入流程完全平台无关，无需改动（除错误文案）
- `PlaylistImportState.kt` — 数据模型不变
- `ImportTab.kt` — Card Relay UI、歌单详情视图不变
- `MusicPlatformApi.kt` — 接口已定义 `getPlaylistDetail()`，无需改动
- `MusicSignUtils` — 签名算法已就绪，无需改动

## API 风险与回退

- QQ音乐 CGI 接口非公开 API，可能变更。如果 `fcg_ucc_getcdinfo_byids_cp.fcg` 不可用，回退方案为解析分享链接的 HTML 页面中内嵌的 JSON 数据。
- 酷狗 API 同样非公开，需在实际调用中验证参数名和响应结构，根据实际响应做小幅调整。
- 两个 API 均走已有 HttpClient + rate limiter，网络异常时返回 null，由 `PlaylistImportManager` 统一显示"获取歌单失败"错误。

## 测试要点

1. **URL 解析**: 粘贴真实 QQ音乐/酷狗分享链接，确认正确提取 ID
2. **歌单获取**: 确认能获取到歌单标题、封面、全部歌曲
3. **搜索匹配**: 导入流程中歌曲跨平台搜索正常（已有逻辑，确保不回归）
4. **UI 展示**: 平台标签正确显示、错误提示涵盖所有平台
5. **回归**: 网易云音乐导入不受影响
