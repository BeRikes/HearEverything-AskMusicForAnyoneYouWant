# Playlist Tab Smooth Pager Animation — Design Spec

**Date:** 2026-06-09
**Status:** Approved

## Overview

Replace the current abrupt `when(selectedTab)` tab switching in `PlaylistScreen` with a `HorizontalPager`-based continuous sliding experience, matching the feel of a phone desktop home screen: 1:1 finger tracking, adjacent pages visible during drag, and physics-based snap on release. A sliding indicator under the tab chips animates in sync with the page offset.

## Architecture

```
PlaylistScreen
├── TabRow (top chip bar)
│   ├── TabChip("当前歌单")  ── click → pager.animateScrollToPage(0)
│   ├── TabChip("历史歌单")  ── click → pager.animateScrollToPage(1)
│   ├── TabChip("导入歌单")  ── click → pager.animateScrollToPage(2)
│   └── Box (sliding indicator) ── offset driven by pagerState.currentPageOffsetFraction
├── HorizontalDivider
└── HorizontalPager(state = pagerState, beyondViewportPageCount = 1)
    ├── Page 0: CurrentPlaylistTab(...)
    ├── Page 1: HistoryTab(...)
    └── Page 2: ImportTab(...)
```

## Key Components

### 1. HorizontalPager
- Uses `androidx.compose.foundation.pager.HorizontalPager` (available in Compose Multiplatform 1.7.3)
- `rememberPagerState(pageCount = 3)` creates the state
- `beyondViewportPageCount = 1` ensures adjacent pages are composed for smooth sliding
- Replaces the old `detectHorizontalDragGestures` + `when(selectedTab)` pattern

### 2. Bidirectional State Sync

```
User swipe gesture  → PagerState.currentPage changes
                   → LaunchedEffect(snapshotFlow) pushes to ViewModel.selectTab()

Click TabChip       → ViewModel.selectTab(n)
                   → LaunchedEffect detects mismatch with pagerState.currentPage
                   → pagerState.animateScrollToPage(n)
```

- A `snapshotFlow { pagerState.currentPage }` pushes page changes from pager to ViewModel
- A second `LaunchedEffect(selectedTab)` animates the pager when the tab chip is clicked (external change)
- Guard against loops: skip `animateScrollToPage` when `pagerState.currentPage == selectedTab`

### 3. Sliding Tab Indicator

- A small rounded rectangle (approx. 40×4dp) positioned below the tab chips, centered under the active chip
- Each `TabChip` reports its center X position **relative to the parent TabRow** via `onGloballyPositioned { it.positionInParent().x + it.size.width / 2 }` — stored in a 3-element `MutableStateList`
- The indicator is placed in a `Box` overlay on the TabRow, and its `offset(x)` is the interpolated center minus half the indicator width:

```
val nextPage = (currentPage + 1).coerceIn(0, pageCount - 1)
indicatorCenterX = lerp(
    tabCenters[currentPage],
    tabCenters[nextPage],
    currentPageOffsetFraction
)
indicatorOffsetX = indicatorCenterX - indicatorWidth / 2
```

- `currentPageOffsetFraction` ranges 0→1 as the user drags from page N toward page N+1
- At the last page (`currentPage == pageCount - 1`), `nextPage == currentPage`, so the indicator stays still
- This creates perfectly smooth indicator motion that follows the finger

### 4. Tab Chip Styling

- When a chip is the current page (`pagerState.currentPage == index`): bold + primary color + subtle background
- When not selected: normal weight + muted color + no background
- Transition hint: could animate font weight via `animateFloatAsState` on `currentPageOffsetFraction` proximity — **deferred as optional polish**

## Gesture Handling

- **Outer tab sliding takes priority.** The `HorizontalPager` handles horizontal drags to switch between the 3 main tabs (当前歌单 / 历史歌单 / 导入歌单)
- **ImportTab internal navigation** switches sub-views via button clicks only (no internal horizontal swiping), avoiding gesture conflicts with the outer pager
- **Vertical scrolling** inside each tab's `LazyColumn` is unaffected

## Files Modified

| File | Change |
|------|--------|
| `PlaylistScreen.kt` | Replace `when(selectedTab)` with `HorizontalPager`; add `PagerState`; add sliding indicator; remove old `detectHorizontalDragGestures` |
| `PlaylistViewModel.kt` | No changes needed — `selectedTab` state flow and `selectTab()` remain the same |

## Edge Cases

- **Rapid tab clicks**: `animateScrollToPage` is cancellable — a new click cancels the in-flight animation
- **Boundary**: Swiping left from tab 0 or right from tab 2 is naturally blocked by `HorizontalPager`
- **Empty tabs**: Pages with empty state UI (empty playlist, empty history) compose and slide normally
- **Content height differences**: Each page fills the available height independently; no cross-page height constraint

## Pager Physics

Use default `HorizontalPager` fling behavior which matches Android Launcher:
- `flingBehavior: PagerDefaults.flingBehavior(state)` — standard snap physics
- Page snap threshold: ~50% of page width (Compose default)
- Velocity-based fling: fast swipes skip the threshold check and snap immediately

No custom physics parameters needed — the defaults already match phone desktop feel.
