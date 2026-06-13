# UI Refactor Design — HearEverything

**Date:** 2026-06-03
**Status:** Approved
**Source:** product.md

## Overview

重构 HearEverything 前端 UI，使实现符合 product.md 定义的设计原则。核心变更：将播放器从对话页内提取为常驻组件，简化导航结构，强化 AI 对话为主交互。

## Design Principles (from product.md)

1. **音乐先行** — 播放控制始终可见可触达，播放器是 App 的常驻层
2. **对话即交互** — AI 对话是主要输入方式，传统搜索框是补充
3. **平台透明** — AI 自动跨平台搜索，结果标注来源
4. **单手可达** — 核心操作在底部拇指热区，底部导航+悬浮播放器双层结构
5. **有个性但不吵闹** — 暗色模式默认，网易云红品牌色，撞色体系

## Architecture Change

### Before (Current)
```
Scaffold per screen with own TopAppBar
  ├─ MainScreen: TopAppBar + VinylPlayer + Chat + InputBar
  ├─ SearchScreen: TopAppBar + SearchForm + Results
  ├─ LibraryScreen: TopAppBar + SongList
  └─ SettingsScreen: TopAppBar + SettingsForm
BottomNav (4 tabs: 对话|搜索|歌单|设置)
```
**Problem:** Player only visible on MainScreen tab. Switching tabs loses playback visibility.

### After (Refactored)
```
App.kt (root)
  ├─ Box (fills entire screen)
  │   ├─ Scaffold
  │   │   ├─ content: PageContent (3 tabs)
  │   │   │   ├─ MainScreen: BrandBar + SearchShortcut + Chat + InputBar
  │   │   │   ├─ LibraryScreen: compact header + SongList
  │   │   │   └─ SettingsScreen: compact header + SettingsForm
  │   │   └─ bottomBar: Column
  │   │       ├─ MiniPlayerBar (52dp, visible when currentSong != null)
  │   │       └─ NavigationBar (3 tabs: 对话|歌单|设置)
  │   └─ NowPlayingOverlay (AnimatedVisibility, slide from bottom, rendered ON TOP of Scaffold)
  │
  │   State owned by App.kt:
  │     - isOverlayVisible: MutableState<Boolean> — toggled by MiniPlayerBar tap / overlay dismiss
  │     - currentScreen: Screen enum (3 values)
```

## File Changes

### New Files

| File | Purpose |
|------|---------|
| `ui/player/MiniPlayerBar.kt` | Persistent mini player bar (48-52dp), shows cover thumbnail, song/artist, platform badge, play/pause, skip next. Tap to open NowPlayingOverlay. |
| `ui/player/NowPlayingOverlay.kt` | Full-screen overlay: rotating vinyl disc (reuse VinylDisc), gradient progress bar, transport controls, download + lyrics buttons, swipe-down to dismiss. |
| `ui/search/SearchPanel.kt` | Search form embedded in MainScreen chat page — expands when user taps the search shortcut bar. Includes song name + artist inputs, platform filter, results list. |

### Modified Files

| File | Changes |
|------|---------|
| `App.kt` | Restructure root layout: 3 tabs instead of 4, Column bottomBar (MiniPlayerBar + NavigationBar), NowPlayingOverlay. Remove Search tab enum entry. |
| `ui/main/MainScreen.kt` | Remove TopAppBar, VinylPlayerArea, LyricsSheet (moved to overlay). Add compact BrandBar, SearchShortcut bar, embed SearchPanel. Chat + InputBar remain. |
| `ui/library/LibraryScreen.kt` | Simplify header (remove standalone TopAppBar), keep song list and actions. |
| `ui/settings/SettingsScreen.kt` | Simplify header (remove standalone TopAppBar), keep settings form. |

### Deleted Files

| File | Reason |
|------|--------|
| `ui/chat/ChatScreen.kt` | Functionality superseded by refactored MainScreen. ChatItem model retained. |

### Unchanged Files

| File | Reason |
|------|--------|
| `ui/theme/Theme.kt` | Already implements NetEase Red + QQ Blue-Purple clash palette correctly. Dark mode default. |
| `ui/chat/ChatItem.kt` | Message model still used by MainScreen. |
| `ui/main/MainViewModel.kt` | Core logic unchanged; player state delegation remains. May add search panel state. |
| `ui/search/SearchViewModel.kt` | Retained for SearchPanel; no longer a standalone tab. |
| `ui/library/LibraryViewModel.kt` | Unchanged. |
| `ui/settings/SettingsViewModel.kt` | Unchanged. |

## Component Specifications

### MiniPlayerBar

- **Height:** 52dp (≥44dp touch targets for a11y)
- **Layout:** Row: [cover 40dp] [song info (weight 1f)] [play/pause 36dp circle] [next 36dp]
- **Cover:** 40×40dp, rounded 8dp, gradient placeholder (primary→secondary)
- **Song info:** songName (13sp, bold) + artist · platform (10sp, muted)
- **Play/Pause:** 36dp circle, primary color background, onPrimary icon (18dp)
- **Next:** 36dp, icon only, muted color
- **Visibility:** AnimatedVisibility, shown when currentSong != null
- **Interaction:** Clickable row → opens NowPlayingOverlay with slide-up animation

### NowPlayingOverlay

- **Entry animation:** Slide up from bottom (AnimatedVisibility + slideInVertically)
- **Exit:** Slide down or swipe gesture
- **Background:** Dark surface with subtle gradient
- **Content (top→bottom):**
  1. Drag handle (16×4dp pill)
  2. Vinyl disc (200dp, centered, rotation animation when playing)
  3. Song name (18sp bold) + artist (13sp muted)
  4. Gradient progress bar (NetEase red → QQ blue-purple) with time labels
  5. Transport controls: prev | play/pause (60dp, primary) | next
  6. Action row: download | lyrics | share placeholder
- **Reduced motion:** Vinyl disc static (no rotation animation)

### MainScreen (Chat Page)

- **Brand bar:** Row: [app icon 28dp gradient] [app name 15sp bold] [Spacer] [settings icon]
- **Search shortcut:** Pill-shaped bar (height 44dp), rounded 24dp, subtle border. Text: "告诉我想听什么歌，或者直接搜索..." with search icon. Expands SearchPanel on tap.
- **Chat area:** Existing LazyColumn with ChatBubbleItem, empty state prompt
- **Input bar:** Existing rounded text field + send button (unchanged)

### SearchPanel (embedded in chat page)

- **Expansion:** AnimatedVisibility, slides down from search shortcut
- **Content:** Song name input (primary) + artist input (secondary, optional) + search button + results list
- **Results:** Reuse SearchResultCard from current SearchScreen
- **Collapse:** Tap outside or on a result to play

### LibraryScreen (simplified header)

- Remove standalone `TopAppBar` with `navigationIcon`
- Use a compact inline header: `Row { "← 返回" TextButton | "本地歌单" title | Spacer }` at top of content
- Song list, play/delete actions remain unchanged

### SettingsScreen (simplified header)

- Remove standalone `TopAppBar` with `navigationIcon`
- Use a compact inline header: `Row { "← 返回" TextButton | "设置" title | Spacer }` at top of content
- Settings form sections remain unchanged

## Implementation Notes

- **Overlay host:** `App.kt` wraps `Scaffold` in a `Box`. `NowPlayingOverlay` is rendered as the last child of the Box (on top). Visibility controlled by `isOverlayVisible: MutableState<Boolean>` in App.kt's composable scope.
- **MiniPlayerBar → Overlay connection:** `App.kt` passes `{ isOverlayVisible = true }` callback to MiniPlayerBar via parameter. Overlay passes `{ isOverlayVisible = false }` callback for dismiss.
- **Search integration:** `MainScreen` manages its own `isSearchExpanded` state. Search panel slides between the search shortcut bar and the chat list. When expanded, it replaces the chat area (not overlays it).
- **Platform badges:** Reuse existing `PlatformBadge` from SearchScreen.kt in both SearchPanel and MiniPlayerBar.
- **Old SearchScreen.kt:** Kept on disk but no longer referenced from App.kt navigation. The `SearchResultItem`, `SearchResultCard`, `PlatformBadge` composables within it are reused by SearchPanel.

## Data Flow

```
MusicPlayer (single shared instance, created in App.kt)
  │
  ├─→ MiniPlayerBar: currentSong, isPlaying (display only)
  ├─→ NowPlayingOverlay: currentSong, isPlaying, currentPosition, duration (display + control)
  ├─→ MainViewModel: currentSong, isPlaying + play/pause/next/prev actions
  ├─→ SearchViewModel: currentSong, isPlaying (for now-playing indicator)
  └─→ LibraryViewModel: currentSong, isPlaying + play action
```

All ViewModels receive the same `MusicPlayer` instance. State observation via `StateFlow` ensures consistent playback state across all components.

## Design Tokens

- **Primary (NetEase Red):** `#E84B4B` dark / `#C62F2F` light
- **Secondary (QQ Blue):** `#8AB4F8` dark / `#3B6FF5` light
- **Tertiary (QQ Purple):** `#B794F6` dark / `#7B4FE0` light
- **Background:** `#0A0A0C` dark / `#F3F3F5` light
- **Surface:** `#141418` dark / `#FFFFFF` light
- **Surface Variant:** `#1E1E24` dark / `#EBEBEF` light
- **Mini player height:** 52dp
- **Bottom nav height:** standard (64dp with system insets)
- **Touch targets:** ≥44dp per WCAG 2.1 AA
- **Corner radius:** 12dp (cards), 24dp (inputs), 8dp (thumbnails)

## Accessibility

- All player controls have ≥44dp touch targets
- Content descriptions on all icons (Chinese labels)
- Reduced motion: vinyl rotation disabled when system prefers reduced motion
- Font scaling: supports system font size up to 200%
- WCAG 2.1 AA contrast on all text/background pairs

## Scope

This refactor is scoped to **UI layer only** (Compose components). No changes to:
- Network layer (MusicPlatformApi, crawlers)
- Cache layer (CacheRepository)
- Player engine (MusicPlayer)
- AI assistant (AiAssistant)
- Settings persistence (SettingsRepository, SettingsStorage)
- Platform-specific code (androidMain, iosMain)
