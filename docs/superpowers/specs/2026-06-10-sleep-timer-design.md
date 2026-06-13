# Sleep Timer Design

> 定时关闭（睡眠定时器）— 用户设置倒计时，计时结束自动暂停播放。

## Status
Draft — pending review

## Overview

Allow users to set a countdown timer. When the timer expires, the app pauses playback automatically, optionally waiting for the current song to finish first.

---

## Data Model

### SleepTimerState

```kotlin
data class SleepTimerState(
    val isActive: Boolean = false,
    val remainingMs: Long = 0L,
    val totalMs: Long = 0L,
    val finishCurrentSong: Boolean = false,
)
```

| Field | Description |
|---|---|
| `isActive` | Whether a countdown is currently running |
| `remainingMs` | Milliseconds remaining, updated every second |
| `totalMs` | Initial total duration (for progress display) |
| `finishCurrentSong` | If true, wait for current song to end after countdown reaches zero before pausing |

### Timer Presets

| Preset | `finishCurrentSong` |
|---|---|
| 关闭 (Off) | false |
| 10 分钟 | false |
| 15 分钟 | false |
| 30 分钟 | false |
| 60 分钟 | false |
| 自定义 (Custom input) | false |

A separate toggle switch controls `finishCurrentSong` independently of the time selection.

---

## Architecture

- **MainViewModel** hosts all timer logic and state. No new ViewModel class.
- `SleepTimerState` is exposed via `StateFlow<SleepTimerState>`, consumed by all UI layers.

### Key Methods (MainViewModel)

| Method | Description |
|---|---|
| `startSleepTimer(minutes: Int, finishCurrentSong: Boolean)` | Start/reset a countdown. Cancels any existing timer. |
| `cancelSleepTimer()` | Stop the timer and reset state. |
| `private sleepTimerLoop()` | Suspend function — `delay(1000)` loop, decrement `remainingMs`, emit updates. On reaching 0: pause playback (immediately or after song end depending on `finishCurrentSong`). |

### Countdown Behavior

```
startSleepTimer(15, finishCurrentSong = true)

loop:
  delay(1000)
  remainingMs -= 1000
  emit updated state

  if remainingMs <= 0:
    if finishCurrentSong:
      observe currentSong position/duration
      wait until playback ends naturally (position ≈ duration)
    musicPlayer.pause()
    emit SleepTimerState(isActive = false, ...)
```

- Timer counts regardless of play/pause state.
- Calling `startSleepTimer` again overwrites the running timer with new values.
- Monitoring song end reuses the same `isPlaying` + `currentPosition`/`duration` detection already used for auto-next-track in MainViewModel.

---

## UI Components

### 1. Settings Page — Timer Entry (SettingsScreen)

A list row "⏰ 定时关闭":
- Shows current status below the label (e.g., "已设置 · 15 分钟 · 播完当前歌曲" or "未设置" when inactive).
- When active, the row highlights with `primaryContainer` background.
- Tap opens the timer picker **Bottom Sheet**.

### Bottom Sheet

Vertical list of options:
```
○ 关闭定时器
● 15 分钟          ← selected preset
○ 10 分钟
○ 30 分钟
○ 60 分钟
○ 自定义: [___] 分钟
─────────────────────
播完整首歌后关闭  [toggle]
```

- Selecting a preset immediately starts/closes the timer and dismisses the sheet.
- "关闭定时器" calls `cancelSleepTimer()`.
- The "播完整首歌后关闭" toggle is separate from the time selection and can be toggled at any time.

### 2. NowPlayingOverlay — Info Display

In the header row, to the right of "正在播放" text:

- **Active timer**: `正在播放  ⏰ 28:30` — same typography (`titleMedium`, `FontWeight.Bold`, `onBackground`), color uses theme primary for the timer portion.
- **Song-end mode**: `正在播放  🎵 播完关闭` — same styling.
- **No timer**: Only "正在播放" is shown, no extra text.
- **Not clickable** — display only.

### 3. PlaylistScreen — Info Display

To the right of "共 N 首":
- Same typography as the count text (`labelLarge`, `FontWeight.Medium`, `onSurfaceVariant`).
- Format: `共 12 首  ·  ⏰ 28:30` or `共 12 首  ·  🎵 播完关闭`.
- Not shown when no timer is active.

### 4. MiniPlayerBar

No timer indicator.

---

## Files to Modify

| File | Change |
|---|---|
| `App.kt` | Pass new timer state/callbacks through composable parameters to NowPlayingOverlay and Settings |
| `ui/main/MainViewModel.kt` | Add `SleepTimerState`, StateFlow, start/cancel methods, countdown loop |
| `ui/player/NowPlayingOverlay.kt` | Add timer info text next to "正在播放" |
| `ui/playlist/PlaylistScreen.kt` | Add timer info next to "共 N 首" |
| `ui/settings/SettingsScreen.kt` | Add timer entry row + Bottom Sheet picker |

---

## Edge Cases

| Scenario | Behavior |
|---|---|
| Timer set while no song playing | Timer still counts down; when it expires, `pause()` is a no-op |
| User changes song during countdown | Timer unaffected, continues counting |
| User manually pauses during countdown | Timer unaffected, continues counting |
| "Finish current song" enabled, timer expires mid-song | Waits for current song to end, then pauses |
| "Finish current song" enabled, no song playing when timer expires | Timer completes immediately (nothing to wait for) |
| App restarted during active timer | Timer does not persist; it is session-only |
| Timer expires during "finish current song" wait, user manually skips to next song | Next song is allowed to play; pausing only happens when THAT song ends |

---

## Non-Goals

- No persistence across app restarts (session-only feature)
- No gradual volume fade-out
- No scheduled timer (start at a specific clock time)
