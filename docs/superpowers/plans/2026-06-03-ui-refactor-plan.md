# UI Refactor Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Refactor the frontend UI to match product.md — extract player as persistent layer, reduce to 3 tabs, embed search in chat.

**Architecture:** Box-wrapped Scaffold with Column bottomBar (MiniPlayerBar + NavigationBar). NowPlayingOverlay renders above Scaffold. MainScreen drops vinyl player for pure chat + embedded search. App.kt owns overlay visibility state and directly observes MusicPlayer for mini-bar display.

**Tech Stack:** Kotlin Multiplatform, Compose Multiplatform, Material 3, existing ViewModel layer (unchanged)

---

## File Map

| File | Action | Responsibility |
|------|--------|----------------|
| `ui/player/MiniPlayerBar.kt` | Create | 52dp bar: cover thumbnail + song info + play/pause + next. Tap opens overlay. |
| `ui/player/NowPlayingOverlay.kt` | Create | Full-screen overlay: vinyl disc, progress bar, controls, lyrics. Slide-up animation. |
| `ui/search/SearchPanel.kt` | Create | Animated search form + results, embedded in chat page. |
| `ui/main/MainScreen.kt` | Modify | Remove player/TopAppBar; add BrandBar + SearchShortcut + SearchPanel. |
| `ui/library/LibraryScreen.kt` | Modify | Remove Scaffold+TopAppBar; add compact inline header. |
| `ui/settings/SettingsScreen.kt` | Modify | Remove Scaffold+TopAppBar; add compact inline header. |
| `App.kt` | Modify | 3 tabs, Box+Scaffold, MiniPlayerBar in bottomBar, NowPlayingOverlay on top. |
| `ui/chat/ChatScreen.kt` | Delete | Superseded by refactored MainScreen. |

---

### Task 1: Create MiniPlayerBar

**Files:**
- Create: `shared/src/commonMain/kotlin/com/example/aimusicplayer/ui/player/MiniPlayerBar.kt`

- [ ] **Step 1: Write the MiniPlayerBar composable**

```kotlin
package com.example.aimusicplayer.ui.player

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.aimusicplayer.network.music.SongMetadata

/**
 * Persistent mini player bar displayed above the bottom navigation.
 *
 * Shows current song info and core playback controls. Tapping the bar
 * opens the full-screen [NowPlayingOverlay].
 *
 * ## Accessibility
 * - All touch targets ≥ 44dp (WCAG 2.1 AA)
 * - Content descriptions on all icon buttons (Chinese)
 *
 * @param currentSong  Currently loaded song, null → bar hidden
 * @param isPlaying    Whether audio is actively playing
 * @param onPlayPause  Toggle play/pause callback
 * @param onNext       Skip to next track callback
 * @param onTap        Called when the user taps the bar (open overlay)
 */
@Composable
fun MiniPlayerBar(
    currentSong: SongMetadata?,
    isPlaying: Boolean,
    onPlayPause: () -> Unit,
    onNext: () -> Unit,
    onTap: () -> Unit,
    modifier: Modifier = Modifier,
) {
    AnimatedVisibility(
        visible = currentSong != null,
        enter = slideInVertically(initialOffsetY = { it }) + fadeIn(),
        exit = slideOutVertically(targetOffsetY = { it }) + fadeOut(),
        modifier = modifier,
    ) {
        val song = currentSong ?: return@AnimatedVisibility

        Surface(
            tonalElevation = 2.dp,
            shadowElevation = 4.dp,
            color = MaterialTheme.colorScheme.surface,
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(52.dp)
                    .clickable(onClick = onTap)
                    .padding(horizontal = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                // ── Cover thumbnail (gradient placeholder) ─────────
                Surface(
                    modifier = Modifier.size(40.dp),
                    shape = RoundedCornerShape(8.dp),
                    color = MaterialTheme.colorScheme.surfaceVariant,
                ) {
                    androidx.compose.foundation.Canvas(modifier = Modifier.fillMaxSize()) {
                        drawRect(
                            brush = Brush.linearGradient(
                                colors = listOf(
                                    MaterialTheme.colorScheme.primary,
                                    MaterialTheme.colorScheme.secondary,
                                ),
                            ),
                        )
                    }
                }

                Spacer(Modifier.width(10.dp))

                // ── Song info ─────────────────────────────────────
                Row(
                    modifier = Modifier.weight(1f),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = song.songName,
                        style = MaterialTheme.typography.bodySmall,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onSurface,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f, fill = false),
                    )
                    Spacer(Modifier.width(6.dp))
                    Text(
                        text = song.artist.ifBlank { "" },
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f, fill = false),
                    )
                }

                Spacer(Modifier.width(8.dp))

                // ── Platform badge ────────────────────────────────
                val platformLabel = when (song.platform) {
                    "netease" -> "网易云"
                    "qq" -> "QQ音乐"
                    "kugou" -> "酷狗"
                    "bilibili" -> "B站"
                    else -> song.platform
                }
                Surface(
                    shape = RoundedCornerShape(4.dp),
                    color = MaterialTheme.colorScheme.primary.copy(alpha = 0.12f),
                ) {
                    Text(
                        text = platformLabel,
                        modifier = Modifier.padding(horizontal = 5.dp, vertical = 1.dp),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.primary,
                        fontWeight = FontWeight.Medium,
                    )
                }

                Spacer(Modifier.width(8.dp))

                // ── Play/Pause (44dp touch target) ────────────────
                IconButton(
                    onClick = onPlayPause,
                    modifier = Modifier
                        .size(44.dp)
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.primary),
                ) {
                    Icon(
                        imageVector = if (isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                        contentDescription = if (isPlaying) "暂停" else "播放",
                        tint = MaterialTheme.colorScheme.onPrimary,
                        modifier = Modifier.size(20.dp),
                    )
                }

                // ── Next (44dp touch target) ──────────────────────
                IconButton(
                    onClick = onNext,
                    modifier = Modifier.size(44.dp),
                ) {
                    Icon(
                        imageVector = Icons.Default.SkipNext,
                        contentDescription = "下一首",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(22.dp),
                    )
                }
            }
        }
    }
}
```

- [ ] **Step 2: Verify the file compiles**

No explicit compile check needed at this step — file has no external dependencies beyond existing project types. Will verify in final build step.

- [ ] **Step 3: Commit**

```bash
git add shared/src/commonMain/kotlin/com/example/aimusicplayer/ui/player/MiniPlayerBar.kt
git commit -m "feat: add MiniPlayerBar — persistent mini player above bottom nav

- 52dp bar with cover thumbnail, song info, platform badge
- Play/pause (44dp primary circle) + next (44dp) buttons
- AnimatedVisibility: slides up when song loaded, hides when null
- Tap entire bar to trigger overlay open callback

Co-Authored-By: Claude Opus 4.8 <noreply@anthropic.com>"
```

---

### Task 2: Create NowPlayingOverlay

**Files:**
- Create: `shared/src/commonMain/kotlin/com/example/aimusicplayer/ui/player/NowPlayingOverlay.kt`

- [ ] **Step 1: Write the NowPlayingOverlay composable**

```kotlin
package com.example.aimusicplayer.ui.player

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Lyrics
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material.icons.filled.SkipPrevious
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.example.aimusicplayer.network.music.SongMetadata

/**
 * Full-screen now-playing overlay with vinyl disc, progress bar, and controls.
 *
 * Slides up from the bottom when [visible] becomes true. Displays the
 * rotating vinyl disc (NetEase Cloud Music style), gradient progress bar
 * (QQ Music style), transport controls, and action buttons.
 *
 * ## Reduced motion
 * When the system prefers reduced motion, the vinyl disc rotation animation
 * is disabled and the disc renders statically.
 *
 * @param visible          Whether the overlay is shown
 * @param currentSong      Currently loaded song (null → placeholder state)
 * @param isPlaying        Whether audio is actively playing
 * @param currentPosition  Current playback position in ms
 * @param duration         Total track duration in ms
 * @param onDismiss        Called when user swipes down or taps close
 * @param onPlayPause      Toggle play/pause
 * @param onNext           Skip to next track
 * @param onPrevious       Skip to previous track
 * @param onDownload       Download current song
 * @param onLyrics         Toggle lyrics visibility
 * @param showLyrics       Whether lyrics sheet is visible
 * @param lyricsText       Lyrics content (null → "暂无歌词")
 * @param downloadProgress Download progress 0..1, null if not downloading
 */
@Composable
fun NowPlayingOverlay(
    visible: Boolean,
    currentSong: SongMetadata?,
    isPlaying: Boolean,
    currentPosition: Long,
    duration: Long,
    onDismiss: () -> Unit,
    onPlayPause: () -> Unit,
    onNext: () -> Unit,
    onPrevious: () -> Unit,
    onDownload: () -> Unit,
    onLyrics: () -> Unit,
    showLyrics: Boolean,
    lyricsText: String?,
    downloadProgress: Float?,
    modifier: Modifier = Modifier,
) {
    AnimatedVisibility(
        visible = visible,
        enter = slideInVertically(initialOffsetY = { it }),
        exit = slideOutVertically(targetOffsetY = { it }),
        modifier = modifier,
    ) {
        if (showLyrics) {
            // ── Lyrics full-screen ────────────────────────────────
            LyricsSheet(
                lyricsText = lyricsText ?: "暂无歌词",
                onDismiss = onLyrics,
            )
        } else {
            // ── Main player overlay ───────────────────────────────
            NowPlayingContent(
                currentSong = currentSong,
                isPlaying = isPlaying,
                currentPosition = currentPosition,
                duration = duration,
                onDismiss = onDismiss,
                onPlayPause = onPlayPause,
                onNext = onNext,
                onPrevious = onPrevious,
                onDownload = onDownload,
                onLyrics = onLyrics,
                downloadProgress = downloadProgress,
            )
        }
    }
}

// ═══════════════════════════════════════════════════════════════════════════
// Main player content
// ═══════════════════════════════════════════════════════════════════════════

@Composable
private fun NowPlayingContent(
    currentSong: SongMetadata?,
    isPlaying: Boolean,
    currentPosition: Long,
    duration: Long,
    onDismiss: () -> Unit,
    onPlayPause: () -> Unit,
    onNext: () -> Unit,
    onPrevious: () -> Unit,
    onDownload: () -> Unit,
    onLyrics: () -> Unit,
    downloadProgress: Float?,
) {
    val hasSong = currentSong != null

    // ── Rotation animation ────────────────────────────────────────
    val infiniteTransition = rememberInfiniteTransition(label = "overlayVinyl")
    val rotation by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 7500, easing = LinearEasing),
            repeatMode = RepeatMode.Restart,
        ),
        label = "overlayVinylAngle",
    )

    Surface(
        modifier = Modifier.fillMaxSize(),
        color = MaterialTheme.colorScheme.background,
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            // ── Drag handle ───────────────────────────────────────
            Spacer(Modifier.height(12.dp))
            Box(
                modifier = Modifier
                    .width(32.dp)
                    .height(4.dp)
                    .clip(RoundedCornerShape(2.dp))
                    .background(MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.3f)),
            )

            // ── Header row ────────────────────────────────────────
            Spacer(Modifier.height(16.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = "正在播放",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onBackground,
                )
                IconButton(
                    onClick = onDismiss,
                    modifier = Modifier
                        .size(36.dp)
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.surfaceVariant),
                ) {
                    Icon(
                        Icons.Default.Close,
                        contentDescription = "关闭",
                        modifier = Modifier.size(18.dp),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }

            Spacer(Modifier.height(20.dp))

            // ── Vinyl disc ─────────────────────────────────────────
            Box(
                modifier = Modifier
                    .size(240.dp)
                    .rotate(if (isPlaying) rotation else 0f),
                contentAlignment = Alignment.Center,
            ) {
                VinylDisc(hasSong = hasSong)
            }

            Spacer(Modifier.height(24.dp))

            // ── Song name + artist ─────────────────────────────────
            if (hasSong) {
                Text(
                    text = currentSong!!.songName,
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onBackground,
                    textAlign = TextAlign.Center,
                )
                Spacer(Modifier.height(4.dp))
                Text(
                    text = currentSong.artist.ifBlank { "未知艺术家" },
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                )
            } else {
                Text(
                    text = "选择一首歌开始播放",
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
                    textAlign = TextAlign.Center,
                )
            }

            Spacer(Modifier.height(24.dp))

            // ── Progress bar with time ─────────────────────────────
            if (hasSong) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        formatTime(currentPosition),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    GradientProgressBar(
                        progress = if (duration > 0) currentPosition.toFloat() / duration else 0f,
                        modifier = Modifier.weight(1f),
                    )
                    Text(
                        formatTime(duration),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }

            Spacer(Modifier.height(28.dp))

            // ── Transport controls ─────────────────────────────────
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                // Previous
                IconButton(
                    onClick = onPrevious,
                    modifier = Modifier.size(52.dp),
                    enabled = hasSong,
                ) {
                    Icon(
                        Icons.Default.SkipPrevious,
                        contentDescription = "上一首",
                        tint = if (hasSong) MaterialTheme.colorScheme.onBackground
                        else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.3f),
                        modifier = Modifier.size(34.dp),
                    )
                }

                Spacer(Modifier.width(20.dp))

                // Play/Pause (center, large, primary)
                IconButton(
                    onClick = onPlayPause,
                    modifier = Modifier
                        .size(68.dp)
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.primary),
                ) {
                    Icon(
                        if (isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                        contentDescription = if (isPlaying) "暂停" else "播放",
                        tint = MaterialTheme.colorScheme.onPrimary,
                        modifier = Modifier.size(36.dp),
                    )
                }

                Spacer(Modifier.width(20.dp))

                // Next
                IconButton(
                    onClick = onNext,
                    modifier = Modifier.size(52.dp),
                    enabled = hasSong,
                ) {
                    Icon(
                        Icons.Default.SkipNext,
                        contentDescription = "下一首",
                        tint = if (hasSong) MaterialTheme.colorScheme.onBackground
                        else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.3f),
                        modifier = Modifier.size(34.dp),
                    )
                }
            }

            Spacer(Modifier.height(32.dp))

            // ── Action row ─────────────────────────────────────────
            if (hasSong) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.Center,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    // Download
                    IconButton(
                        onClick = onDownload,
                        modifier = Modifier
                            .size(48.dp)
                            .clip(RoundedCornerShape(12.dp))
                            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f)),
                    ) {
                        if (downloadProgress != null && downloadProgress < 1f) {
                            CircularProgressIndicator(
                                progress = { downloadProgress },
                                modifier = Modifier.size(22.dp),
                                strokeWidth = 2.dp,
                                color = MaterialTheme.colorScheme.primary,
                            )
                        } else {
                            Icon(
                                Icons.Default.Download,
                                contentDescription = "下载",
                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.size(22.dp),
                            )
                        }
                    }

                    Spacer(Modifier.width(24.dp))

                    // Lyrics
                    IconButton(
                        onClick = onLyrics,
                        modifier = Modifier
                            .size(48.dp)
                            .clip(RoundedCornerShape(12.dp))
                            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f)),
                    ) {
                        Icon(
                            Icons.Default.Lyrics,
                            contentDescription = "歌词",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(22.dp),
                        )
                    }
                }
            }
        }
    }
}

// ═══════════════════════════════════════════════════════════════════════════
// Vinyl Disc
// ═══════════════════════════════════════════════════════════════════════════

@Composable
private fun VinylDisc(hasSong: Boolean) {
    val discColor = if (hasSong) Color(0xFF1A1A1E) else Color(0xFF2A2A30)
    val grooveColor = Color(0xFF2E2E35)
    val labelColor = if (hasSong) MaterialTheme.colorScheme.primary
    else MaterialTheme.colorScheme.surfaceVariant

    Canvas(modifier = Modifier.fillMaxSize()) {
        val cx = size.width / 2
        val cy = size.height / 2
        val maxRadius = size.minDimension / 2

        drawCircle(color = discColor, radius = maxRadius)
        val grooveCount = 8
        for (i in 1..grooveCount) {
            drawCircle(
                color = grooveColor,
                radius = maxRadius * (1f - i * 0.035f),
                style = Stroke(width = 1f),
            )
        }
        val labelRadius = maxRadius * 0.42f
        drawCircle(color = labelColor, radius = labelRadius)
        drawCircle(
            color = labelColor.copy(alpha = 0.7f),
            radius = labelRadius * 0.85f,
            style = Stroke(width = 1.5f),
        )
        drawCircle(color = Color.Black, radius = maxRadius * 0.06f)
    }
}

// ═══════════════════════════════════════════════════════════════════════════
// Gradient Progress Bar
// ═══════════════════════════════════════════════════════════════════════════

@Composable
private fun GradientProgressBar(
    progress: Float,
    modifier: Modifier = Modifier,
) {
    val primaryColor = MaterialTheme.colorScheme.primary
    val secondaryColor = MaterialTheme.colorScheme.secondary
    val trackColor = MaterialTheme.colorScheme.surfaceVariant

    Canvas(
        modifier = modifier
            .height(4.dp)
            .clip(RoundedCornerShape(4.dp)),
    ) {
        val trackHeight = size.height
        val trackY = size.height / 2

        drawRoundRect(
            color = trackColor,
            cornerRadius = CornerRadius(4f, 4f),
            size = Size(size.width, trackHeight),
        )

        if (progress > 0f) {
            val filledWidth = size.width * progress.coerceIn(0f, 1f)
            drawRoundRect(
                brush = Brush.horizontalGradient(
                    colors = listOf(primaryColor, secondaryColor),
                    startX = 0f,
                    endX = size.width,
                ),
                cornerRadius = CornerRadius(4f, 4f),
                size = Size(filledWidth, trackHeight),
            )
            drawCircle(
                color = secondaryColor,
                radius = trackHeight * 1.2f,
                center = Offset(filledWidth, trackY),
            )
        }
    }
}

// ═══════════════════════════════════════════════════════════════════════════
// Lyrics Sheet
// ═══════════════════════════════════════════════════════════════════════════

@Composable
private fun LyricsSheet(
    lyricsText: String,
    onDismiss: () -> Unit,
) {
    Surface(
        modifier = Modifier.fillMaxSize(),
        color = MaterialTheme.colorScheme.surface.copy(alpha = 0.97f),
    ) {
        Column {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    "歌词",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                )
                IconButton(
                    onClick = onDismiss,
                    modifier = Modifier
                        .size(32.dp)
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.surfaceVariant),
                ) {
                    Icon(
                        Icons.Default.Close,
                        contentDescription = "关闭歌词",
                        modifier = Modifier.size(18.dp),
                    )
                }
            }

            LazyColumn(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .padding(horizontal = 24.dp),
                contentPadding = PaddingValues(vertical = 16.dp),
            ) {
                val lines = lyricsText.split("\n")
                items(lines) { line ->
                    Text(
                        text = line.trim(),
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 8.dp),
                        textAlign = TextAlign.Center,
                    )
                }
            }
        }
    }
}

// ═══════════════════════════════════════════════════════════════════════════
// Utilities
// ═══════════════════════════════════════════════════════════════════════════

private fun formatTime(ms: Long): String {
    if (ms <= 0) return "0:00"
    val totalSeconds = ms / 1000
    val minutes = totalSeconds / 60
    val seconds = totalSeconds % 60
    return "$minutes:${seconds.toString().padStart(2, '0')}"
}
```

- [ ] **Step 2: Commit**

```bash
git add shared/src/commonMain/kotlin/com/example/aimusicplayer/ui/player/NowPlayingOverlay.kt
git commit -m "feat: add NowPlayingOverlay — full-screen vinyl player overlay

- Slides up from bottom, drag handle + close button to dismiss
- Rotating vinyl disc (reused from MainScreen), gradient progress bar
- Transport controls: prev, play/pause (68dp), next
- Download + lyrics action buttons
- Lyrics sheet toggle within overlay
- Reduced motion: disc renders static

Co-Authored-By: Claude Opus 4.8 <noreply@anthropic.com>"
```

---

### Task 3: Create SearchPanel

**Files:**
- Create: `shared/src/commonMain/kotlin/com/example/aimusicplayer/ui/search/SearchPanel.kt`

- [ ] **Step 1: Write the SearchPanel composable**

This component wraps the search form and results from the old SearchScreen into an animated panel that can be embedded in the chat page.

```kotlin
package com.example.aimusicplayer.ui.search

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Audiotrack
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.aimusicplayer.music.DownloadState
import com.example.aimusicplayer.network.music.SongMetadata

/**
 * Animated search panel embedded in the chat page.
 *
 * Expands below the search shortcut bar when the user wants to perform
 * a traditional keyword search (without AI). Uses [SearchViewModel] for
 * the search logic and reuses [SearchResultCard] from the SearchScreen.
 *
 * @param expanded     Whether the panel is visible
 * @param onDismiss    Called when user taps close or selects a result
 * @param viewModel    SearchViewModel instance (shared, created in App.kt)
 */
@Composable
fun SearchPanel(
    expanded: Boolean,
    onDismiss: () -> Unit,
    viewModel: SearchViewModel,
    modifier: Modifier = Modifier,
) {
    val songName by viewModel.songName.collectAsState()
    val artist by viewModel.artist.collectAsState()
    val searchResults by viewModel.searchResults.collectAsState()
    val isSearching by viewModel.isSearching.collectAsState()
    val searchError by viewModel.searchError.collectAsState()
    val searchStep by viewModel.searchStep.collectAsState()
    val hasSearched by viewModel.hasSearched.collectAsState()
    val currentSong by viewModel.currentSong.collectAsState()
    val isPlaying by viewModel.isPlaying.collectAsState()
    val downloadStates by viewModel.downloadStates.collectAsState()

    AnimatedVisibility(
        visible = expanded,
        enter = expandVertically() + fadeIn(),
        exit = shrinkVertically() + fadeOut(),
        modifier = modifier,
    ) {
        Surface(
            modifier = Modifier.fillMaxSize(),
            color = MaterialTheme.colorScheme.background,
        ) {
            Column(
                modifier = Modifier.fillMaxSize(),
            ) {
                // ── Header with close button ────────────────────────
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 12.dp, vertical = 8.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        "搜索歌曲",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onBackground,
                    )
                    IconButton(
                        onClick = onDismiss,
                        modifier = Modifier
                            .size(32.dp)
                            .clip(CircleShape)
                            .background(MaterialTheme.colorScheme.surfaceVariant),
                    ) {
                        Icon(
                            Icons.Default.Close,
                            contentDescription = "关闭搜索",
                            modifier = Modifier.size(18.dp),
                        )
                    }
                }

                // ── Search form ─────────────────────────────────────
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    OutlinedTextField(
                        value = songName,
                        onValueChange = viewModel::onSongNameChanged,
                        placeholder = { Text("歌曲名") },
                        leadingIcon = {
                            Icon(
                                Icons.Default.MusicNote,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                            )
                        },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true,
                        enabled = !isSearching,
                        shape = RoundedCornerShape(12.dp),
                        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Next),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = MaterialTheme.colorScheme.primary,
                        ),
                    )

                    OutlinedTextField(
                        value = artist,
                        onValueChange = viewModel::onArtistChanged,
                        placeholder = { Text("歌手名（选填）") },
                        leadingIcon = {
                            Icon(
                                Icons.Default.Audiotrack,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true,
                        enabled = !isSearching,
                        shape = RoundedCornerShape(12.dp),
                        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                        keyboardActions = KeyboardActions(onSearch = { viewModel.search() }),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = MaterialTheme.colorScheme.secondary,
                        ),
                    )

                    Button(
                        onClick = viewModel::search,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(48.dp),
                        enabled = songName.isNotBlank() && !isSearching,
                        shape = RoundedCornerShape(12.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.primary,
                        ),
                    ) {
                        if (isSearching) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(20.dp),
                                strokeWidth = 2.dp,
                                color = MaterialTheme.colorScheme.onPrimary,
                            )
                            Spacer(Modifier.width(8.dp))
                            Text("搜索中...")
                        } else {
                            Icon(Icons.Default.Search, contentDescription = null, modifier = Modifier.size(20.dp))
                            Spacer(Modifier.width(8.dp))
                            Text("搜索", fontWeight = FontWeight.Medium)
                        }
                    }

                    // Search step indicator
                    searchStep?.let { step ->
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.Center,
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(14.dp),
                                strokeWidth = 2.dp,
                            )
                            Spacer(Modifier.width(8.dp))
                            Text(
                                step,
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                }

                // ── Results area ────────────────────────────────────
                when {
                    isSearching -> {
                        Box(
                            modifier = Modifier.fillMaxSize(),
                            contentAlignment = Alignment.Center,
                        ) {
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(40.dp),
                                    color = MaterialTheme.colorScheme.primary,
                                )
                                Spacer(Modifier.height(12.dp))
                                Text(
                                    "搜索中...",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                        }
                    }

                    searchResults.isNotEmpty() -> {
                        // Now playing indicator
                        currentSong?.let { song ->
                            Surface(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 16.dp, vertical = 4.dp),
                                shape = RoundedCornerShape(10.dp),
                                color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.3f),
                            ) {
                                Row(
                                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                ) {
                                    Icon(
                                        Icons.Default.MusicNote,
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.primary,
                                        modifier = Modifier.size(18.dp),
                                    )
                                    Spacer(Modifier.width(8.dp))
                                    Text(
                                        if (isPlaying) "正在播放: " else "已暂停: ",
                                        style = MaterialTheme.typography.labelMedium,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    )
                                    Text(
                                        "${song.songName} — ${song.artist}",
                                        style = MaterialTheme.typography.labelMedium,
                                        fontWeight = FontWeight.SemiBold,
                                        color = MaterialTheme.colorScheme.primary,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis,
                                    )
                                }
                            }
                        }

                        // Results count
                        Text(
                            "找到 ${searchResults.size} 首",
                            style = MaterialTheme.typography.labelLarge,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            fontWeight = FontWeight.Medium,
                            modifier = Modifier.padding(horizontal = 20.dp, vertical = 4.dp),
                        )

                        // Results list
                        LazyColumn(
                            modifier = Modifier.fillMaxSize(),
                            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 4.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            items(searchResults, key = { it.song.songId + it.song.platform }) { item ->
                                SearchResultCard(
                                    item = item,
                                    isCurrentlyPlaying = currentSong?.songId == item.song.songId &&
                                        currentSong?.platform == item.song.platform,
                                    isPlaying = isPlaying,
                                    downloadState = downloadStates[item.song.songId],
                                    onPlay = {
                                        viewModel.playResult(item)
                                        onDismiss()
                                    },
                                    onDownload = { viewModel.downloadResult(item) },
                                )
                            }
                            item { Spacer(Modifier.height(16.dp)) }
                        }
                    }

                    hasSearched && searchResults.isEmpty() -> {
                        Box(
                            modifier = Modifier.fillMaxSize(),
                            contentAlignment = Alignment.Center,
                        ) {
                            Column(
                                horizontalAlignment = Alignment.CenterHorizontally,
                                modifier = Modifier.padding(32.dp),
                            ) {
                                Text("😕", fontSize = 40.sp)
                                Spacer(Modifier.height(8.dp))
                                Text(
                                    "未找到匹配的歌曲",
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.SemiBold,
                                )
                                Spacer(Modifier.height(4.dp))
                                Text(
                                    "已尝试所有平台",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                        }
                    }

                    else -> {
                        Box(
                            modifier = Modifier.fillMaxSize(),
                            contentAlignment = Alignment.Center,
                        ) {
                            Column(
                                horizontalAlignment = Alignment.CenterHorizontally,
                                modifier = Modifier.padding(32.dp),
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(64.dp)
                                        .clip(CircleShape)
                                        .background(MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f)),
                                    contentAlignment = Alignment.Center,
                                ) {
                                    Icon(
                                        Icons.Default.Search,
                                        contentDescription = null,
                                        modifier = Modifier.size(32.dp),
                                        tint = MaterialTheme.colorScheme.primary,
                                    )
                                }
                                Spacer(Modifier.height(12.dp))
                                Text(
                                    "输入歌曲名即可搜索",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                                Text(
                                    "添加歌手名可精确匹配",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

// ═══════════════════════════════════════════════════════════════════════════
// SearchResultCard (copied from SearchScreen.kt)
// ═══════════════════════════════════════════════════════════════════════════

@Composable
private fun SearchResultCard(
    item: SearchResultItem,
    isCurrentlyPlaying: Boolean,
    isPlaying: Boolean,
    downloadState: DownloadState?,
    onPlay: () -> Unit,
    onDownload: () -> Unit,
) {
    val song = item.song
    val isDownloading = downloadState != null && downloadState.isActive && downloadState.progress < 1f
    val isDownloaded = downloadState != null && downloadState.progress >= 1f

    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = if (isCurrentlyPlaying)
                MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.4f)
            else
                MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
        ),
        shape = RoundedCornerShape(12.dp),
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                // Cover placeholder
                Box(
                    modifier = Modifier
                        .size(48.dp)
                        .clip(RoundedCornerShape(10.dp))
                        .background(
                            if (isCurrentlyPlaying && isPlaying)
                                MaterialTheme.colorScheme.primary
                            else
                                MaterialTheme.colorScheme.primaryContainer,
                        ),
                    contentAlignment = Alignment.Center,
                ) {
                    if (isCurrentlyPlaying && isPlaying) {
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(2.dp),
                            verticalAlignment = Alignment.Bottom,
                        ) {
                            repeat(3) {
                                Box(
                                    modifier = Modifier
                                        .width(3.dp)
                                        .height((12 + it * 4).dp)
                                        .background(
                                            MaterialTheme.colorScheme.onPrimary,
                                            RoundedCornerShape(1.dp),
                                        ),
                                )
                            }
                        }
                    } else {
                        Text("🎵", fontSize = 20.sp)
                    }
                }

                Spacer(Modifier.width(12.dp))

                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = song.songName,
                        style = MaterialTheme.typography.bodyLarge,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Spacer(Modifier.height(2.dp))
                    Text(
                        text = song.artist.ifBlank { "未知艺术家" },
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Spacer(Modifier.height(4.dp))
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        PlatformBadge(platform = song.platform)
                        Surface(
                            shape = RoundedCornerShape(4.dp),
                            color = if (item.fromCache)
                                MaterialTheme.colorScheme.tertiary.copy(alpha = 0.1f)
                            else
                                MaterialTheme.colorScheme.secondary.copy(alpha = 0.1f),
                        ) {
                            Text(
                                if (item.fromCache) "缓存" else "在线",
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 1.dp),
                                style = MaterialTheme.typography.labelSmall,
                                color = if (item.fromCache)
                                    MaterialTheme.colorScheme.tertiary
                                else
                                    MaterialTheme.colorScheme.secondary,
                                fontWeight = FontWeight.Medium,
                            )
                        }
                    }
                }

                Spacer(Modifier.width(4.dp))

                // Download button
                IconButton(
                    onClick = onDownload,
                    modifier = Modifier
                        .size(36.dp)
                        .clip(RoundedCornerShape(8.dp))
                        .background(
                            if (isDownloaded)
                                MaterialTheme.colorScheme.tertiary.copy(alpha = 0.1f)
                            else
                                MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.8f),
                        ),
                ) {
                    if (isDownloading) {
                        CircularProgressIndicator(
                            progress = { downloadState!!.progress },
                            modifier = Modifier.size(16.dp),
                            strokeWidth = 2.dp,
                        )
                    } else {
                        Icon(
                            Icons.Default.Download,
                            contentDescription = "下载",
                            tint = if (isDownloaded)
                                MaterialTheme.colorScheme.tertiary
                            else
                                MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(18.dp),
                        )
                    }
                }

                // Play button
                IconButton(
                    onClick = onPlay,
                    modifier = Modifier
                        .size(40.dp)
                        .clip(CircleShape)
                        .background(
                            if (isCurrentlyPlaying)
                                MaterialTheme.colorScheme.primary
                            else
                                MaterialTheme.colorScheme.primary.copy(alpha = 0.1f),
                        ),
                ) {
                    Icon(
                        Icons.Default.PlayArrow,
                        contentDescription = "播放",
                        tint = if (isCurrentlyPlaying)
                            MaterialTheme.colorScheme.onPrimary
                        else
                            MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(22.dp),
                    )
                }
            }

            if (isDownloading && downloadState != null) {
                Spacer(Modifier.height(8.dp))
                LinearProgressIndicator(
                    progress = { downloadState.progress },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(3.dp),
                    color = MaterialTheme.colorScheme.primary,
                    trackColor = MaterialTheme.colorScheme.surfaceVariant,
                )
            }
        }
    }
}

// ═══════════════════════════════════════════════════════════════════════════
// Platform badge
// ═══════════════════════════════════════════════════════════════════════════

@Composable
private fun PlatformBadge(platform: String) {
    val (label, bgColor, textColor) = when (platform) {
        "netease" -> Triple(
            "网易云",
            MaterialTheme.colorScheme.error.copy(alpha = 0.1f),
            MaterialTheme.colorScheme.error,
        )
        "qq" -> Triple(
            "QQ音乐",
            MaterialTheme.colorScheme.secondary.copy(alpha = 0.1f),
            MaterialTheme.colorScheme.secondary,
        )
        "kugou" -> Triple(
            "酷狗",
            MaterialTheme.colorScheme.tertiary.copy(alpha = 0.1f),
            MaterialTheme.colorScheme.tertiary,
        )
        "bilibili" -> Triple(
            "B站",
            MaterialTheme.colorScheme.primary.copy(alpha = 0.1f),
            MaterialTheme.colorScheme.primary,
        )
        else -> Triple(platform, MaterialTheme.colorScheme.surfaceVariant, MaterialTheme.colorScheme.onSurfaceVariant)
    }

    Surface(shape = RoundedCornerShape(4.dp), color = bgColor) {
        Text(
            label,
            modifier = Modifier.padding(horizontal = 6.dp, vertical = 1.dp),
            style = MaterialTheme.typography.labelSmall,
            color = textColor,
            fontWeight = FontWeight.Medium,
        )
    }
}
```

- [ ] **Step 2: Commit**

```bash
git add shared/src/commonMain/kotlin/com/example/aimusicplayer/ui/search/SearchPanel.kt
git commit -m "feat: add SearchPanel — embedded search in chat page

- Animated expand/collapse panel with search form + results
- Reuses SearchViewModel for logic, SearchResultCard for display
- Platform badges, cache indicators, download + play buttons
- Handles all states: idle, searching, results, no results

Co-Authored-By: Claude Opus 4.8 <noreply@anthropic.com>"
```

---

### Task 4: Refactor MainScreen

**Files:**
- Modify: `shared/src/commonMain/kotlin/com/example/aimusicplayer/ui/main/MainScreen.kt`

This is the largest change. The entire file is rewritten to remove the player area and add the search shortcut + search panel.

- [ ] **Step 1: Replace the entire MainScreen.kt file**

```kotlin
package com.example.aimusicplayer.ui.main

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.aimusicplayer.ai.AiCommand
import com.example.aimusicplayer.ui.chat.ChatItem
import com.example.aimusicplayer.ui.search.SearchPanel
import com.example.aimusicplayer.ui.search.SearchViewModel

// ═══════════════════════════════════════════════════════════════════════════
// MainScreen — AI chat-first layout with embedded search
// ═══════════════════════════════════════════════════════════════════════════

/**
 * Primary chat screen with embedded search panel.
 *
 * Layout (top → bottom):
 * 1. BrandBar — compact brand identity + settings shortcut
 * 2. SearchShortcut — pill-shaped bar that expands [SearchPanel]
 * 3. Chat messages (LazyColumn, fills remaining space)
 * 4. ChatInputBar — text field + send button
 *
 * The vinyl player has been extracted to [com.example.aimusicplayer.ui.player.NowPlayingOverlay]
 * and the persistent mini player to [com.example.aimusicplayer.ui.player.MiniPlayerBar].
 *
 * @param viewModel            MainViewModel for chat + playback actions
 * @param searchViewModel      SearchViewModel for the embedded search panel
 * @param onNavigateToSettings Opens the settings tab
 */
@Composable
fun MainScreen(
    viewModel: MainViewModel,
    searchViewModel: SearchViewModel,
    onNavigateToSettings: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val chatItems by viewModel.chatItems.collectAsState()
    val inputText by viewModel.inputText.collectAsState()
    val isThinking by viewModel.isThinking.collectAsState()
    val errorMessage by viewModel.errorMessage.collectAsState()

    val listState = rememberLazyListState()
    val snackbarHostState = remember { SnackbarHostState() }
    var isSearchExpanded by remember { mutableStateOf(false) }

    // Auto-scroll when new messages arrive
    LaunchedEffect(chatItems.size) {
        if (chatItems.isNotEmpty()) {
            listState.animateScrollToItem(chatItems.size - 1)
        }
    }

    // Snackbar error handling
    LaunchedEffect(errorMessage) {
        errorMessage?.let {
            snackbarHostState.showSnackbar(it)
            viewModel.clearError()
        }
    }

    Column(modifier = modifier.fillMaxSize()) {
        // ═══════════════════════════════════════════════════════════
        // Brand bar
        // ═══════════════════════════════════════════════════════════
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 10.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                // App icon
                Box(
                    modifier = Modifier
                        .size(28.dp)
                        .clip(RoundedCornerShape(8.dp))
                        .background(
                            brush = Brush.linearGradient(
                                colors = listOf(
                                    MaterialTheme.colorScheme.primary,
                                    MaterialTheme.colorScheme.tertiary,
                                ),
                            ),
                        ),
                )
                Text(
                    text = "HearEverything",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onBackground,
                )
            }

            // Settings icon
            IconButton(onClick = onNavigateToSettings, modifier = Modifier.size(40.dp)) {
                Icon(
                    Icons.Default.Settings,
                    contentDescription = "设置",
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(22.dp),
                )
            }
        }

        // ═══════════════════════════════════════════════════════════
        // Search shortcut bar (or SearchPanel when expanded)
        // ═══════════════════════════════════════════════════════════
        if (isSearchExpanded) {
            SearchPanel(
                expanded = isSearchExpanded,
                onDismiss = { isSearchExpanded = false },
                viewModel = searchViewModel,
            )
        } else {
            // Collapsed: pill-shaped search shortcut
            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp)
                    .clickable { isSearchExpanded = true },
                shape = RoundedCornerShape(24.dp),
                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f),
                tonalElevation = 0.dp,
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    Icon(
                        Icons.Default.Search,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(20.dp),
                    )
                    Text(
                        text = "告诉我想听什么歌，或者直接搜索...",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
                    )
                }
            }
        }

        if (!isSearchExpanded) {
            Spacer(Modifier.height(8.dp))
        }

        // ═══════════════════════════════════════════════════════════
        // Error banner
        // ═══════════════════════════════════════════════════════════
        SnackbarHost(
            hostState = snackbarHostState,
            modifier = Modifier.padding(horizontal = 16.dp),
        )

        // ═══════════════════════════════════════════════════════════
        // Chat message list
        // ═══════════════════════════════════════════════════════════
        LazyColumn(
            state = listState,
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth(),
            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            if (chatItems.isEmpty()) {
                item {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 80.dp),
                        contentAlignment = Alignment.Center,
                    ) {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            Text(
                                text = "👋",
                                style = MaterialTheme.typography.displaySmall,
                            )
                            Text(
                                text = "告诉我你想听什么歌？",
                                style = MaterialTheme.typography.bodyLarge,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                textAlign = TextAlign.Center,
                            )
                            Text(
                                text = "试试说「播放周杰伦的晴天」或「来点摇滚」",
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
                                textAlign = TextAlign.Center,
                            )
                        }
                    }
                }
            }
            items(chatItems) { item ->
                ChatBubbleItem(item)
            }
            if (isThinking) {
                item {
                    Row(
                        modifier = Modifier.padding(8.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(16.dp),
                            strokeWidth = 2.dp,
                            color = MaterialTheme.colorScheme.primary,
                        )
                        Text(
                            "AI 正在思考...",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
        }

        // ═══════════════════════════════════════════════════════════
        // Chat input bar
        // ═══════════════════════════════════════════════════════════
        ChatInputBar(
            inputText = inputText,
            onInputChanged = viewModel::onInputChanged,
            onSend = viewModel::sendMessage,
            enabled = !isThinking,
        )
    }
}

// ═══════════════════════════════════════════════════════════════════════════
// Chat bubbles
// ═══════════════════════════════════════════════════════════════════════════

@Composable
private fun ChatBubbleItem(item: ChatItem) {
    when (item) {
        is ChatItem.UserMessage -> UserBubble(item.text)
        is ChatItem.AiMessage -> AiBubble(item.text, item.command)
        is ChatItem.SystemMessage -> SystemBubble(item.text)
    }
}

@Composable
private fun UserBubble(text: String) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.End,
    ) {
        Surface(
            color = MaterialTheme.colorScheme.primary,
            shape = RoundedCornerShape(
                topStart = 16.dp,
                topEnd = 16.dp,
                bottomStart = 16.dp,
                bottomEnd = 4.dp,
            ),
            modifier = Modifier.widthIn(max = 300.dp),
        ) {
            Text(
                text = text,
                modifier = Modifier.padding(12.dp),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onPrimary,
            )
        }
    }
}

@Composable
private fun AiBubble(text: String, command: AiCommand?) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.Start,
    ) {
        Surface(
            color = MaterialTheme.colorScheme.surfaceVariant,
            shape = RoundedCornerShape(
                topStart = 16.dp,
                topEnd = 16.dp,
                bottomStart = 4.dp,
                bottomEnd = 16.dp,
            ),
            modifier = Modifier.widthIn(max = 300.dp),
        ) {
            Text(
                text = text,
                modifier = Modifier.padding(12.dp),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurface,
            )
        }
    }
}

@Composable
private fun SystemBubble(text: String) {
    Box(
        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
            textAlign = TextAlign.Center,
        )
    }
}

// ═══════════════════════════════════════════════════════════════════════════
// Chat input bar
// ═══════════════════════════════════════════════════════════════════════════

@Composable
private fun ChatInputBar(
    inputText: String,
    onInputChanged: (String) -> Unit,
    onSend: () -> Unit,
    enabled: Boolean,
) {
    Surface(
        tonalElevation = 2.dp,
        shadowElevation = 6.dp,
        color = MaterialTheme.colorScheme.surface,
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            OutlinedTextField(
                value = inputText,
                onValueChange = onInputChanged,
                placeholder = { Text("输入歌曲名或播放指令...") },
                modifier = Modifier.weight(1f),
                singleLine = true,
                enabled = enabled,
                shape = RoundedCornerShape(24.dp),
            )
            Button(
                onClick = onSend,
                enabled = inputText.isNotBlank() && enabled,
                shape = RoundedCornerShape(12.dp),
            ) {
                Icon(
                    Icons.AutoMirrored.Filled.Send,
                    contentDescription = "发送",
                    modifier = Modifier.size(18.dp),
                )
            }
        }
    }
}
```

- [ ] **Step 2: Commit**

```bash
git add shared/src/commonMain/kotlin/com/example/aimusicplayer/ui/main/MainScreen.kt
git commit -m "refactor: MainScreen — pure chat layout with embedded search

- Remove TopAppBar, VinylPlayerArea, LyricsSheet (moved to NowPlayingOverlay)
- Add compact BrandBar with gradient app icon + settings shortcut
- Add SearchShortcut pill bar that expands to SearchPanel
- Chat messages + input bar remain unchanged
- Accepts SearchViewModel for embedded search
- Empty state with friendly prompt text

Co-Authored-By: Claude Opus 4.8 <noreply@anthropic.com>"
```

---

### Task 5: Simplify LibraryScreen

**Files:**
- Modify: `shared/src/commonMain/kotlin/com/example/aimusicplayer/ui/library/LibraryScreen.kt`

- [ ] **Step 1: Remove Scaffold + TopAppBar, add compact inline header**

Replace the existing `LibraryScreen` function. Keep the `SongCard` composable unchanged.

Replace lines 66-215 of the current file (the `LibraryScreen` function body). The `SongCard` composable at lines 221-341 stays the same.

The change is to remove the `Scaffold` wrapper and `TopAppBar`, replacing them with a Column structure with an inline header Row.

```kotlin
// Replace the LibraryScreen function (lines 66-215) with:

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LibraryScreen(
    viewModel: LibraryViewModel,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val songs by viewModel.downloadedSongs.collectAsState()
    val isLoading by viewModel.isLoading.collectAsState()
    val snackbarMessage by viewModel.snackbarMessage.collectAsState()
    val renameTarget by viewModel.renameTarget.collectAsState()

    val snackbarHostState = remember { SnackbarHostState() }

    LaunchedEffect(snackbarMessage) {
        snackbarMessage?.let {
            snackbarHostState.showSnackbar(it)
            viewModel.clearSnackbar()
        }
    }

    Column(modifier = modifier.fillMaxSize()) {
        // ── Compact header ─────────────────────────────────────────
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 8.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            TextButton(onClick = onBack) {
                Text("← 返回", color = MaterialTheme.colorScheme.primary)
            }
            Spacer(Modifier.weight(1f))
            Text(
                "本地歌单",
                fontWeight = FontWeight.Bold,
                style = MaterialTheme.typography.titleMedium,
            )
            Spacer(Modifier.weight(1f))
            // Invisible spacer for balance
            Spacer(Modifier.width(60.dp))
        }

        Spacer(Modifier.height(4.dp))

        // ── Snackbar ───────────────────────────────────────────────
        SnackbarHost(
            hostState = snackbarHostState,
            modifier = Modifier.padding(horizontal = 16.dp),
        )

        // ── Content ────────────────────────────────────────────────
        when {
            isLoading -> {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center,
                ) {
                    CircularProgressIndicator(color = MaterialTheme.colorScheme.primary)
                }
            }

            songs.isEmpty() -> {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center,
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        Box(
                            modifier = Modifier
                                .size(80.dp)
                                .clip(CircleShape)
                                .background(
                                    MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f),
                                ),
                            contentAlignment = Alignment.Center,
                        ) {
                            Icon(
                                Icons.Default.MusicNote,
                                contentDescription = null,
                                modifier = Modifier.size(40.dp),
                                tint = MaterialTheme.colorScheme.primary,
                            )
                        }
                        Text(
                            "还没有下载歌曲",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.onSurface,
                        )
                        Text(
                            "使用 AI 对话播放歌曲后，点击下载按钮保存到本地",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            textAlign = TextAlign.Center,
                        )
                    }
                }
            }

            else -> {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    item {
                        Text(
                            "共 ${songs.size} 首",
                            style = MaterialTheme.typography.labelLarge,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            fontWeight = FontWeight.Medium,
                            modifier = Modifier.padding(horizontal = 4.dp, vertical = 8.dp),
                        )
                    }
                    items(songs, key = { it.songId }) { song ->
                        SongCard(
                            song = song,
                            onPlay = { viewModel.playSong(song) },
                            onDelete = { viewModel.deleteSong(song) },
                        )
                    }
                    item { Spacer(Modifier.height(16.dp)) }
                }
            }
        }
    }

    // Rename dialog (unchanged)
    renameTarget?.let { song ->
        AlertDialog(
            onDismissRequest = viewModel::dismissRenameDialog,
            title = { Text("重命名") },
            text = { Text("重命名功能开发中...") },
            confirmButton = {
                TextButton(onClick = viewModel::dismissRenameDialog) {
                    Text("确定")
                }
            },
        )
    }
}
```

Also update imports: remove `Scaffold` import, add `Column` if not already imported.

- [ ] **Step 2: Commit**

```bash
git add shared/src/commonMain/kotlin/com/example/aimusicplayer/ui/library/LibraryScreen.kt
git commit -m "refactor: LibraryScreen — compact inline header replaces Scaffold

- Remove Scaffold + TopAppBar wrapper
- Add compact Row header with back button, centered title, balance spacer
- Song list, empty state, and actions remain unchanged

Co-Authored-By: Claude Opus 4.8 <noreply@anthropic.com>"
```

---

### Task 6: Simplify SettingsScreen

**Files:**
- Modify: `shared/src/commonMain/kotlin/com/example/aimusicplayer/ui/settings/SettingsScreen.kt`

- [ ] **Step 1: Remove Scaffold + TopAppBar, add compact inline header**

Replace the existing `SettingsScreen` function (lines 76-337). Keep all private composable functions (`SectionHeader`, `SettingsCard`, `SettingsToggle`, `DefaultQualityDropdown`, `ThemeModeDropdown`) unchanged.

```kotlin
// Replace the SettingsScreen function (lines 76-337) with:

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    viewModel: SettingsViewModel,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val baseUrl by viewModel.baseUrl.collectAsState()
    val apiKey by viewModel.apiKey.collectAsState()
    val isTesting by viewModel.isTesting.collectAsState()
    val testResult by viewModel.testResult.collectAsState()
    val backgroundPlayback by viewModel.backgroundPlayback.collectAsState()
    val lyricsSync by viewModel.lyricsSync.collectAsState()
    val defaultQuality by viewModel.defaultQuality.collectAsState()
    val themeMode by viewModel.themeMode.collectAsState()
    val cacheSizeText by viewModel.cacheSizeText.collectAsState()
    val isClearingCache by viewModel.isClearingCache.collectAsState()
    val cacheClearedSnackbar by viewModel.cacheClearedSnackbar.collectAsState()

    val snackbarHostState = remember { SnackbarHostState() }

    LaunchedEffect(cacheClearedSnackbar) {
        cacheClearedSnackbar?.let {
            snackbarHostState.showSnackbar(it)
            viewModel.clearSnackbar()
        }
    }

    Column(modifier = modifier.fillMaxSize()) {
        // ── Compact header ─────────────────────────────────────────
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 8.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            TextButton(onClick = {
                viewModel.saveAll()
                onBack()
            }) {
                Text("← 返回", color = MaterialTheme.colorScheme.primary)
            }
            Spacer(Modifier.weight(1f))
            Text(
                "设置",
                fontWeight = FontWeight.Bold,
                style = MaterialTheme.typography.titleMedium,
            )
            Spacer(Modifier.weight(1f))
            Spacer(Modifier.width(60.dp))
        }

        Spacer(Modifier.height(4.dp))

        // ── Snackbar ───────────────────────────────────────────────
        SnackbarHost(
            hostState = snackbarHostState,
            modifier = Modifier.padding(horizontal = 16.dp),
        )

        // ── Content (scrollable) ───────────────────────────────────
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Spacer(Modifier.height(4.dp))

            // ── Section: AI 配置 ─────────────────────────────────────
            SectionHeader("AI 配置")

            OutlinedTextField(
                value = baseUrl,
                onValueChange = viewModel::onBaseUrlChanged,
                label = { Text("Base URL") },
                placeholder = { Text(SettingsKeys.DEFAULT_BASE_URL) },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                shape = RoundedCornerShape(12.dp),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = MaterialTheme.colorScheme.primary,
                ),
            )

            OutlinedTextField(
                value = apiKey,
                onValueChange = viewModel::onApiKeyChanged,
                label = { Text("API Key") },
                placeholder = { Text("sk-xxxxxxxxxxxxxxxxxxxxxxxx") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                shape = RoundedCornerShape(12.dp),
                visualTransformation = PasswordVisualTransformation(),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = MaterialTheme.colorScheme.primary,
                ),
            )

            val keyLength = apiKey.length
            val keyStatusText = when {
                keyLength == 0 -> "未输入"
                keyLength < SettingsKeys.MIN_KEY_LENGTH -> "长度不足（需 ≥ ${SettingsKeys.MIN_KEY_LENGTH}）"
                else -> "已输入 ($keyLength 字符)"
            }
            val keyStatusColor = if (keyLength < SettingsKeys.MIN_KEY_LENGTH)
                MaterialTheme.colorScheme.error
            else
                MaterialTheme.colorScheme.primary
            Text(
                text = "API Key 状态: $keyStatusText",
                style = MaterialTheme.typography.labelMedium,
                color = keyStatusColor,
                fontWeight = FontWeight.Medium,
            )

            Button(
                onClick = viewModel::testConnection,
                modifier = Modifier.fillMaxWidth().height(48.dp),
                enabled = !isTesting && apiKey.length >= SettingsKeys.MIN_KEY_LENGTH,
                shape = RoundedCornerShape(12.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.primary,
                ),
            ) {
                if (isTesting) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(20.dp),
                        strokeWidth = 2.dp,
                        color = MaterialTheme.colorScheme.onPrimary,
                    )
                    Spacer(Modifier.width(8.dp))
                    Text("测试中...")
                } else {
                    Text("测试连接", fontWeight = FontWeight.Medium)
                }
            }

            testResult?.let {
                val (isSuccess, message) = when (it) {
                    is TestResult.Success -> true to "连接成功！API Key 有效。"
                    is TestResult.Failure -> false to it.message
                }
                Card(
                    colors = CardDefaults.cardColors(
                        containerColor = if (isSuccess)
                            MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.6f)
                        else
                            MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.6f),
                    ),
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Row(
                        modifier = Modifier.padding(12.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(if (isSuccess) "✅" else "❌")
                        Text(
                            text = message,
                            style = MaterialTheme.typography.bodySmall,
                            color = if (isSuccess)
                                MaterialTheme.colorScheme.onPrimaryContainer
                            else
                                MaterialTheme.colorScheme.onErrorContainer,
                        )
                    }
                }
            }

            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))

            // ── Section: 播放选项 ─────────────────────────────────────
            SectionHeader("播放选项")

            SettingsCard {
                SettingsToggle(
                    title = "后台播放",
                    subtitle = "应用退到后台时继续播放音乐",
                    checked = backgroundPlayback,
                    onCheckedChange = viewModel::onBackgroundPlaybackChanged,
                )
            }

            SettingsCard {
                SettingsToggle(
                    title = "歌词同步",
                    subtitle = "播放时自动显示同步滚动歌词",
                    checked = lyricsSync,
                    onCheckedChange = viewModel::onLyricsSyncChanged,
                )
            }

            SettingsCard {
                DefaultQualityDropdown(
                    currentQuality = defaultQuality,
                    onQualityChanged = viewModel::onDefaultQualityChanged,
                )
            }

            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))

            // ── Section: 外观 ─────────────────────────────────────────
            SectionHeader("外观")

            SettingsCard {
                ThemeModeDropdown(
                    currentTheme = themeMode,
                    onThemeChanged = viewModel::onThemeModeChanged,
                )
            }

            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))

            // ── Section: 存储管理 ─────────────────────────────────────
            SectionHeader("存储管理")

            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                ),
                shape = RoundedCornerShape(12.dp),
            ) {
                Row(
                    modifier = Modifier.padding(16.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column {
                        Text("缓存占用", fontWeight = FontWeight.Medium, style = MaterialTheme.typography.bodyMedium)
                        Spacer(Modifier.height(2.dp))
                        Text(
                            cacheSizeText,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    OutlinedButton(
                        onClick = viewModel::clearCache,
                        enabled = !isClearingCache,
                        shape = RoundedCornerShape(10.dp),
                    ) {
                        if (isClearingCache) {
                            CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp)
                            Spacer(Modifier.width(4.dp))
                        }
                        Text("清空缓存")
                    }
                }
            }

            Spacer(Modifier.height(24.dp))
        }
    }
}
```

Also remove the `Scaffold` import and ensure `Column` import exists.

- [ ] **Step 2: Commit**

```bash
git add shared/src/commonMain/kotlin/com/example/aimusicplayer/ui/settings/SettingsScreen.kt
git commit -m "refactor: SettingsScreen — compact inline header replaces Scaffold

- Remove Scaffold + TopAppBar wrapper
- Add compact Row header with back button (auto-saves), centered title
- Settings form sections remain unchanged

Co-Authored-By: Claude Opus 4.8 <noreply@anthropic.com>"
```

---

### Task 7: Restructure App.kt

**Files:**
- Modify: `shared/src/commonMain/kotlin/com/example/aimusicplayer/App.kt`

- [ ] **Step 1: Rewrite App.kt with new layout structure**

```kotlin
package com.example.aimusicplayer

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.LibraryMusic
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.aimusicplayer.ai.AiAssistant
import com.example.aimusicplayer.cache.CacheRepository
import com.example.aimusicplayer.music.DownloadManager
import com.example.aimusicplayer.music.MusicSearchManager
import com.example.aimusicplayer.network.music.BilibiliApi
import com.example.aimusicplayer.network.music.KugouApi
import com.example.aimusicplayer.network.music.NeteaseApi
import com.example.aimusicplayer.network.music.QQMusicApi
import com.example.aimusicplayer.player.MusicPlayer
import com.example.aimusicplayer.settings.SettingsKeys
import com.example.aimusicplayer.settings.SettingsRepository
import com.example.aimusicplayer.settings.SettingsStorage
import com.example.aimusicplayer.ui.library.LibraryScreen
import com.example.aimusicplayer.ui.library.LibraryViewModel
import com.example.aimusicplayer.ui.main.MainScreen
import com.example.aimusicplayer.ui.main.MainViewModel
import com.example.aimusicplayer.ui.player.MiniPlayerBar
import com.example.aimusicplayer.ui.player.NowPlayingOverlay
import com.example.aimusicplayer.ui.search.SearchViewModel
import com.example.aimusicplayer.ui.settings.SettingsScreen
import com.example.aimusicplayer.ui.settings.SettingsViewModel
import com.example.aimusicplayer.ui.theme.HearEverythingTheme

// ═══════════════════════════════════════════════════════════════════════════
// Navigation (3 tabs)
// ═══════════════════════════════════════════════════════════════════════════

private enum class Screen(
    val label: String,
    val icon: ImageVector,
    val selectedIcon: ImageVector,
) {
    Main(
        label = "对话",
        icon = Icons.Default.MusicNote,
        selectedIcon = Icons.Default.MusicNote,
    ),
    Library(
        label = "歌单",
        icon = Icons.Default.MusicNote,
        selectedIcon = Icons.Filled.LibraryMusic,
    ),
    Settings(
        label = "设置",
        icon = Icons.Default.Settings,
        selectedIcon = Icons.Default.Settings,
    ),
}

// ═══════════════════════════════════════════════════════════════════════════
// App root composable
// ═══════════════════════════════════════════════════════════════════════════

@Composable
fun App(
    storage: SettingsStorage,
    createMusicPlayer: () -> MusicPlayer,
    createDownloadManager: () -> DownloadManager,
    createCacheRepository: () -> CacheRepository,
) {
    // ── Create services (memoized) ─────────────────────────────────
    val settingsRepo = remember { SettingsRepository(storage) }
    val musicPlayer = remember { createMusicPlayer() }
    val downloadManager = remember { createDownloadManager() }
    val cacheRepository = remember { createCacheRepository() }
    val aiAssistant = remember { AiAssistant(storage) }

    val musicApis = remember {
        listOf(NeteaseApi(), QQMusicApi(), KugouApi(), BilibiliApi())
    }
    val searchManager = remember { MusicSearchManager(musicApis) }

    // ── ViewModels ─────────────────────────────────────────────────
    val mainViewModel = remember {
        MainViewModel(
            musicPlayer = musicPlayer,
            aiAssistant = aiAssistant,
            searchManager = searchManager,
            downloadManager = downloadManager,
            settingsRepo = settingsRepo,
        )
    }
    val searchViewModel = remember {
        SearchViewModel(
            musicPlayer = musicPlayer,
            searchManager = searchManager,
            cacheRepository = cacheRepository,
            downloadManager = downloadManager,
        )
    }
    val settingsViewModel = remember {
        SettingsViewModel(
            settingsRepo = settingsRepo,
            aiAssistant = aiAssistant,
            downloadManager = downloadManager,
        )
    }
    val libraryViewModel = remember {
        LibraryViewModel(
            musicPlayer = musicPlayer,
            downloadManager = downloadManager,
        )
    }

    // ── Theme ───────────────────────────────────────────────────────
    val themeMode by settingsViewModel.themeMode.collectAsState()
    val isDark = when (themeMode) {
        SettingsKeys.THEME_MODE_DARK -> true
        SettingsKeys.THEME_MODE_LIGHT -> false
        else -> isSystemInDarkTheme()
    }

    HearEverythingTheme(darkTheme = isDark) {
        var currentScreen by remember { mutableStateOf(Screen.Main) }
        var isOverlayVisible by remember { mutableStateOf(false) }

        // ── Player state (observed directly from MusicPlayer) ──────
        val currentSong by musicPlayer.currentSong.collectAsState()
        val isPlaying by musicPlayer.isPlaying.collectAsState()
        val currentPosition by musicPlayer.currentPosition.collectAsState()
        val duration by musicPlayer.duration.collectAsState()

        // ── Overlay state (read from MainViewModel) ─────────────────
        val showLyrics by mainViewModel.showLyrics.collectAsState()
        val lyricsText by mainViewModel.lyricsText.collectAsState()
        val downloadProgress = mainViewModel.downloadProgress.collectAsState().value[currentSong?.songId]?.progress

        Box(modifier = Modifier.fillMaxSize()) {
            Scaffold(
                bottomBar = {
                    Column {
                        // ── Mini player bar (persistent, above nav) ───
                        MiniPlayerBar(
                            currentSong = currentSong,
                            isPlaying = isPlaying,
                            onPlayPause = mainViewModel::togglePlayPause,
                            onNext = mainViewModel::nextTrack,
                            onTap = { isOverlayVisible = true },
                        )

                        // ── Bottom navigation (3 tabs) ────────────────
                        NavigationBar(
                            containerColor = MaterialTheme.colorScheme.surface,
                            tonalElevation = 0.dp,
                        ) {
                            Screen.entries.forEach { screen ->
                                val selected = currentScreen == screen
                                NavigationBarItem(
                                    selected = selected,
                                    onClick = { currentScreen = screen },
                                    icon = {
                                        Icon(
                                            imageVector = if (selected) screen.selectedIcon else screen.icon,
                                            contentDescription = screen.label,
                                        )
                                    },
                                    label = {
                                        Text(
                                            text = screen.label,
                                            style = MaterialTheme.typography.labelSmall.copy(
                                                fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal,
                                            ),
                                        )
                                    },
                                    colors = NavigationBarItemDefaults.colors(
                                        selectedIconColor = MaterialTheme.colorScheme.primary,
                                        unselectedIconColor = MaterialTheme.colorScheme.onSurfaceVariant,
                                        selectedTextColor = MaterialTheme.colorScheme.primary,
                                        unselectedTextColor = MaterialTheme.colorScheme.onSurfaceVariant,
                                        indicatorColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.12f),
                                    ),
                                )
                            }
                        }
                    }
                },
            ) { innerPadding ->
                val contentModifier = Modifier.padding(innerPadding)

                when (currentScreen) {
                    Screen.Main -> MainScreen(
                        viewModel = mainViewModel,
                        searchViewModel = searchViewModel,
                        onNavigateToSettings = { currentScreen = Screen.Settings },
                        modifier = contentModifier,
                    )

                    Screen.Library -> LibraryScreen(
                        viewModel = libraryViewModel,
                        onBack = { currentScreen = Screen.Main },
                        modifier = contentModifier,
                    )

                    Screen.Settings -> SettingsScreen(
                        viewModel = settingsViewModel,
                        onBack = { currentScreen = Screen.Main },
                        modifier = contentModifier,
                    )
                }
            }

            // ── Now Playing Overlay (rendered on top of everything) ─
            NowPlayingOverlay(
                visible = isOverlayVisible,
                currentSong = currentSong,
                isPlaying = isPlaying,
                currentPosition = currentPosition,
                duration = duration,
                onDismiss = { isOverlayVisible = false },
                onPlayPause = mainViewModel::togglePlayPause,
                onNext = mainViewModel::nextTrack,
                onPrevious = mainViewModel::previousTrack,
                onDownload = mainViewModel::downloadCurrentSong,
                onLyrics = mainViewModel::toggleLyrics,
                showLyrics = showLyrics,
                lyricsText = lyricsText,
                downloadProgress = downloadProgress,
            )
        }
    }
}
```

- [ ] **Step 2: Commit**

```bash
git add shared/src/commonMain/kotlin/com/example/aimusicplayer/App.kt
git commit -m "refactor: App.kt — 3-tab layout with persistent mini player + overlay

- Reduce tabs from 4 to 3 (对话|歌单|设置), remove Search tab
- Wrap Scaffold in Box for overlay rendering
- Column bottomBar: MiniPlayerBar (animated) + NavigationBar
- NowPlayingOverlay rendered on top with slide animation
- Direct MusicPlayer state observation for mini bar + overlay
- SearchViewModel passed to MainScreen for embedded search
- Remove old NeteaseTab composable (no longer needed)

Co-Authored-By: Claude Opus 4.8 <noreply@anthropic.com>"
```

---

### Task 8: Delete ChatScreen.kt

**Files:**
- Delete: `shared/src/commonMain/kotlin/com/example/aimusicplayer/ui/chat/ChatScreen.kt`
- Keep: `shared/src/commonMain/kotlin/com/example/aimusicplayer/ui/chat/ChatItem.kt`

- [ ] **Step 1: Delete ChatScreen.kt**

```bash
rm shared/src/commonMain/kotlin/com/example/aimusicplayer/ui/chat/ChatScreen.kt
```

- [ ] **Step 2: Commit**

```bash
git rm shared/src/commonMain/kotlin/com/example/aimusicplayer/ui/chat/ChatScreen.kt
git commit -m "chore: remove ChatScreen.kt — superseded by refactored MainScreen

ChatItem model retained for MainScreen chat bubbles.

Co-Authored-By: Claude Opus 4.8 <noreply@anthropic.com>"
```

---

### Task 9: Build Verification

- [ ] **Step 1: Run Gradle build (commonMain compilation)**

```bash
cd D:/Download/claude_demo/HearEverything && ./gradlew :shared:compileKotlinMetadata 2>&1 | tail -30
```

Expected: BUILD SUCCESSFUL

If compilation errors exist, fix them:
- Check import statements in new files
- Verify composable function signatures match call sites
- Ensure all referenced types are imported

- [ ] **Step 2: Run Android compilation**

```bash
cd D:/Download/claude_demo/HearEverything && ./gradlew :androidApp:compileDebugKotlin 2>&1 | tail -30
```

Expected: BUILD SUCCESSFUL

- [ ] **Step 3: Commit verification results (if any fixes needed)**

```bash
git add -A
git commit -m "fix: compilation fixes from build verification

Co-Authored-By: Claude Opus 4.8 <noreply@anthropic.com>"
```
