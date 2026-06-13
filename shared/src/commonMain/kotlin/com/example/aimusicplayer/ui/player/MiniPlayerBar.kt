package com.example.aimusicplayer.ui.player

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Error
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.List
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil3.compose.AsyncImage
import com.example.aimusicplayer.network.music.SongMetadata
import kotlin.math.roundToInt

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
    onPrevious: () -> Unit,
    onNext: () -> Unit,
    onPlaylist: () -> Unit,
    onTap: () -> Unit,
    playbackError: String? = null,
    previousSongName: String? = null,
    nextSongName: String? = null,
    playlistIndex: Int = 0,
    playlistSize: Int = 0,
    modifier: Modifier = Modifier,
) {
    AnimatedVisibility(
        visible = currentSong != null,
        enter = slideInVertically(initialOffsetY = { it }) + fadeIn(),
        exit = slideOutVertically(targetOffsetY = { it }) + fadeOut(),
        modifier = modifier,
    ) {
        val song = currentSong ?: return@AnimatedVisibility

        // ── Swipe gesture state ──────────────────────
        var dragOffsetX by remember { mutableStateOf(0f) }
        val animatedOffset by animateFloatAsState(
            targetValue = dragOffsetX,
            animationSpec = spring(),
        )
        val density = LocalDensity.current
        val swipeThresholdPx = with(density) { 40.dp.toPx() }
        // Boundaries from playlist position (more reliable than string null checks)
        // Use rememberUpdatedState so pointerInput(Unit) always sees latest values
        val currentAtStart = rememberUpdatedState(playlistIndex <= 0)
        val currentAtEnd = rememberUpdatedState(playlistIndex >= playlistSize - 1 || playlistSize <= 1)
        val currentPreviousSongName = rememberUpdatedState(previousSongName)
        val currentNextSongName = rememberUpdatedState(nextSongName)

        Surface(
            tonalElevation = 2.dp,
            shadowElevation = 4.dp,
            color = MaterialTheme.colorScheme.surface,
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(52.dp)
                    .padding(horizontal = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                // ── Tappable info area (opens overlay) ────────────
                Row(
                    modifier = Modifier
                        .weight(1f)
                        .height(52.dp)
                        .clickable(onClick = onTap),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    // ── Cover thumbnail ────────────────────────────────
                    val cover = song.coverUrl
                    if (!cover.isNullOrBlank()) {
                        AsyncImage(
                            model = cover,
                            contentDescription = "专辑封面",
                            modifier = Modifier
                                .size(40.dp)
                                .clip(RoundedCornerShape(8.dp)),
                        )
                    }

                    Spacer(Modifier.width(10.dp))

                    // ── Song info (swipeable with adjacent names) ───
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .clip(RoundedCornerShape(4.dp))
                            .pointerInput(Unit) {
                                detectHorizontalDragGestures(
                                    onDragStart = { dragOffsetX = 0f },
                                    onDragEnd = {
                                        if (dragOffsetX > swipeThresholdPx && !currentAtStart.value) {
                                            onPrevious()
                                        } else if (dragOffsetX < -swipeThresholdPx && !currentAtEnd.value) {
                                            onNext()
                                        }
                                        dragOffsetX = 0f
                                    },
                                    onDragCancel = { dragOffsetX = 0f },
                                    onHorizontalDrag = { _, dragAmount ->
                                        val resistance = when {
                                            dragAmount > 0 && dragOffsetX >= 0 && currentAtStart.value -> 0.15f
                                            dragAmount < 0 && dragOffsetX <= 0 && currentAtEnd.value -> 0.15f
                                            else -> 1f
                                        }
                                        dragOffsetX += dragAmount * resistance
                                    },
                                )
                            },
                    ) {
                        // ── Previous song name (slides in from left on right-swipe) ─
                        val prevName = currentPreviousSongName.value
                        val nextName = currentNextSongName.value
                        if (dragOffsetX > 0 && prevName != null) {
                            val prevAlpha = (dragOffsetX / swipeThresholdPx).coerceIn(0f, 1f)
                            Column(
                                modifier = Modifier
                                    .align(Alignment.CenterStart)
                                    .padding(start = 6.dp),
                                verticalArrangement = Arrangement.Center,
                            ) {
                                Text(
                                    text = prevName,
                                    style = MaterialTheme.typography.bodySmall,
                                    fontWeight = FontWeight.Medium,
                                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = prevAlpha * 0.6f),
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                )
                            }
                        }

                        // ── Next song name (slides in from right on left-swipe) ─
                        if (dragOffsetX < 0 && nextName != null) {
                            val nextAlpha = (-dragOffsetX / swipeThresholdPx).coerceIn(0f, 1f)
                            Column(
                                modifier = Modifier
                                    .align(Alignment.CenterEnd)
                                    .padding(end = 6.dp),
                                verticalArrangement = Arrangement.Center,
                            ) {
                                Text(
                                    text = nextName,
                                    style = MaterialTheme.typography.bodySmall,
                                    fontWeight = FontWeight.Medium,
                                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = nextAlpha * 0.6f),
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                )
                            }
                        }

                        // ── Current song name (follows finger, fades out on large offset) ──
                        val currentAlpha = (1f - (kotlin.math.abs(dragOffsetX) / swipeThresholdPx)).coerceIn(0.3f, 1f)
                        Column(
                            modifier = Modifier
                                .align(Alignment.Center)
                                .offset { IntOffset(animatedOffset.roundToInt(), 0) },
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.Center,
                        ) {
                            MarqueeText(
                                text = song.songName,
                                modifier = Modifier.fillMaxWidth(),
                                style = MaterialTheme.typography.bodySmall,
                                fontWeight = FontWeight.SemiBold,
                                color = MaterialTheme.colorScheme.onSurface.copy(alpha = currentAlpha),
                                gapWidth = 40.sp,
                                durationMs = 18000,
                                isAnimating = isPlaying,
                            )
                            Text(
                                text = song.artist.ifBlank { "" },
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = currentAlpha),
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }
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
                }

                Spacer(Modifier.width(8.dp))

                // ── Error indicator ──────────────────────────────
                if (playbackError != null) {
                    Icon(
                        Icons.Default.Error,
                        contentDescription = "播放错误: $playbackError",
                        tint = Color(0xFFE53935),
                        modifier = Modifier.size(18.dp),
                    )
                    Spacer(Modifier.width(8.dp))
                }

                // ── Playlist ────────────────────────────────
                IconButton(
                    onClick = onPlaylist,
                    modifier = Modifier.size(44.dp),
                ) {
                    Icon(
                        imageVector = Icons.Default.List,
                        contentDescription = "播放列表",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(22.dp),
                    )
                }

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

            }
        }
    }
}
