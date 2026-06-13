package com.example.aimusicplayer.ui.player

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material.icons.filled.Lyrics
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.List
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.SkipPrevious
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil3.compose.AsyncImage
import com.example.aimusicplayer.music.BilibiliTitleParser
import com.example.aimusicplayer.network.music.SongMetadata
import com.example.aimusicplayer.ui.main.SleepTimerState
import com.example.aimusicplayer.ui.main.formatTimerRemaining
import kotlinx.coroutines.delay

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
    onPlaylist: () -> Unit,
    onSeek: (Long) -> Unit,
    showLyrics: Boolean,
    lyricsText: String?,
    downloadProgress: Float?,
    isPreparingDownload: Boolean = false,
    isDownloaded: Boolean = false,
    playbackError: String? = null,
    onClearError: () -> Unit = {},
    lyricsOffsetMs: Long = 0L,
    onLyricsOffsetChanged: (Long) -> Unit = {},
    onLyricsOffsetReset: () -> Unit = {},
    onLyricsReSearch: () -> Unit = {},
    isReSearching: Boolean = false,
    onLyricsManualSearch: (songId: String, songName: String, artist: String) -> Unit = { _, _, _ -> },
    sleepTimerState: SleepTimerState = SleepTimerState(),
    onToggleLike: () -> Unit = {},
    isLiked: Boolean = false,
    modifier: Modifier = Modifier,
) {
    // Back gesture: lyrics → now-playing → close overlay
    if (visible) {
        BackPressHandler {
            if (showLyrics) onLyrics() else onDismiss()
        }
    }

    AnimatedVisibility(
        visible = visible,
        enter = slideInVertically(initialOffsetY = { it }),
        exit = slideOutVertically(targetOffsetY = { it }),
        modifier = modifier,
    ) {
        if (showLyrics) {
            LyricsSheet(
                lyricsText = lyricsText ?: "加载中...",
                currentPosition = currentPosition,
                onDismiss = onLyrics,
                lyricsOffsetMs = lyricsOffsetMs,
                isBilibili = currentSong?.platform == "bilibili",
                songId = currentSong?.songId ?: "",
                currentSongName = currentSong?.songName ?: "",
                currentArtist = currentSong?.artist ?: "",
                onOffsetChanged = onLyricsOffsetChanged,
                onOffsetReset = onLyricsOffsetReset,
                onReSearch = onLyricsReSearch,
                isReSearching = isReSearching,
                onLyricsManualSearch = onLyricsManualSearch,
                onSeekToLine = { timestampMs -> onSeek(timestampMs) },
            )
        } else {
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
                onPlaylist = onPlaylist,
                onToggleLike = onToggleLike,
                isLiked = isLiked,
                onSeek = onSeek,
                downloadProgress = downloadProgress,
                isPreparingDownload = isPreparingDownload,
                isDownloaded = isDownloaded,
                playbackError = playbackError,
                onClearError = onClearError,
                sleepTimerState = sleepTimerState,
            )
        }
    }
}

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
    onPlaylist: () -> Unit,
    onToggleLike: () -> Unit = {},
    isLiked: Boolean = false,
    onSeek: (Long) -> Unit,
    downloadProgress: Float?,
    isPreparingDownload: Boolean = false,
    isDownloaded: Boolean = false,
    playbackError: String? = null,
    onClearError: () -> Unit = {},
    sleepTimerState: SleepTimerState = SleepTimerState(),
) {
    val hasSong = currentSong != null

    val infiniteTransition = rememberInfiniteTransition(label = "overlayVinyl")
    val rotation by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 15000, easing = LinearEasing),
            repeatMode = RepeatMode.Restart,
        ),
        label = "overlayVinylAngle",
    )

    Surface(
        modifier = Modifier
            .fillMaxSize()
            .pointerInput(Unit) {
                detectVerticalDragGestures { _, dragAmount ->
                    if (dragAmount > 40f) onDismiss()
                }
            },
        color = MaterialTheme.colorScheme.background,
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {

            // Header row
            Spacer(Modifier.height(8.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                val timerInfoText = when {
                    !sleepTimerState.isActive -> null
                    sleepTimerState.remainingMs <= 0L && sleepTimerState.finishCurrentSong -> "  ·  🎵 播完关闭"
                    else -> "  ·  ⏰ ${formatTimerRemaining(sleepTimerState.remainingMs)}"
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = "正在播放",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onBackground,
                    )
                    if (timerInfoText != null) {
                        Text(
                            text = timerInfoText,
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onBackground,
                        )
                    }
                }
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

            Spacer(Modifier.height(12.dp))

            // Playback error banner
            if (playbackError != null) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(12.dp))
                        .background(Color(0xFFE53935).copy(alpha = 0.15f))
                        .padding(horizontal = 16.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = "⚠️ $playbackError",
                        style = MaterialTheme.typography.bodySmall,
                        color = Color(0xFFE53935),
                        modifier = Modifier.weight(1f),
                    )
                    IconButton(
                        onClick = onClearError,
                        modifier = Modifier.size(28.dp),
                    ) {
                        Icon(
                            Icons.Default.Close,
                            contentDescription = "关闭错误提示",
                            tint = Color(0xFFE53935),
                            modifier = Modifier.size(16.dp),
                        )
                    }
                }
                Spacer(Modifier.height(8.dp))
            }

            // Cover art or vinyl disc
            val isBilibili = currentSong?.platform == "bilibili"
            if (isBilibili && hasSong && !currentSong?.coverUrl.isNullOrBlank()) {
                // Bilibili cover: native aspect ratio, rounded corners
                AsyncImage(
                    model = currentSong!!.coverUrl,
                    contentDescription = "专辑封面",
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(16.dp)),
                )
            } else {
                // Vinyl disc — fills available width, height matches width (circle)
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .aspectRatio(1f)
                        .rotate(if (isPlaying) rotation else 0f),
                    contentAlignment = Alignment.Center,
                ) {
                    VinylDisc(hasSong = hasSong, coverUrl = currentSong?.coverUrl)
                }
            }

            Spacer(Modifier.height(12.dp))

            // Song name + artist
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

            // Seekable progress bar with time
            if (hasSong && duration > 0) {
                val sliderProgress = (currentPosition.toFloat() / duration).coerceIn(0f, 1f)
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
                    Slider(
                        value = sliderProgress,
                        onValueChange = { fraction ->
                            onSeek((fraction * duration).toLong())
                        },
                        modifier = Modifier
                            .weight(1f)
                            .height(24.dp),
                        colors = SliderDefaults.colors(
                            thumbColor = MaterialTheme.colorScheme.primary,
                            activeTrackColor = MaterialTheme.colorScheme.primary,
                            inactiveTrackColor = MaterialTheme.colorScheme.surfaceVariant,
                        ),
                    )
                    Text(
                        formatTime(duration),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }

            Spacer(Modifier.height(28.dp))

            // Transport controls
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically,
            ) {
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

            // Action row
            if (hasSong) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.Center,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    // Download button — hidden when already downloaded
                    if (!isDownloaded) {
                        val isDownloading = isPreparingDownload || (downloadProgress != null && downloadProgress < 1f)
                        IconButton(
                            onClick = onDownload,
                            modifier = Modifier
                                .size(48.dp)
                                .clip(RoundedCornerShape(12.dp))
                                .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f)),
                        ) {
                            if (isDownloading) {
                                if (downloadProgress != null && downloadProgress < 1f) {
                                    CircularProgressIndicator(
                                        progress = { downloadProgress },
                                        modifier = Modifier.size(22.dp),
                                        strokeWidth = 2.dp,
                                        color = MaterialTheme.colorScheme.primary,
                                    )
                                } else {
                                    CircularProgressIndicator(
                                        modifier = Modifier.size(22.dp),
                                        strokeWidth = 2.dp,
                                        color = MaterialTheme.colorScheme.primary,
                                    )
                                }
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
                    }

                    IconButton(
                        onClick = onPlaylist,
                        modifier = Modifier
                            .size(48.dp)
                            .clip(RoundedCornerShape(12.dp))
                            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f)),
                    ) {
                        Icon(
                            Icons.Default.List,
                            contentDescription = "歌单",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(22.dp),
                        )
                    }

                    Spacer(Modifier.width(24.dp))

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

                    Spacer(Modifier.width(24.dp))

                    IconButton(
                        onClick = onToggleLike,
                        modifier = Modifier
                            .size(48.dp)
                            .clip(RoundedCornerShape(12.dp))
                            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f)),
                    ) {
                        Icon(
                            if (isLiked) Icons.Default.Favorite else Icons.Default.FavoriteBorder,
                            contentDescription = if (isLiked) "取消喜欢" else "喜欢",
                            tint = if (isLiked) Color(0xFFE53935) else MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(22.dp),
                        )
                    }
                }
            }

            // ── Download progress bar (min 1.5s visible) ─────────
            val isDownloading = !isDownloaded && (isPreparingDownload || (downloadProgress != null && downloadProgress < 1f))
            var showProgress by remember { mutableStateOf(false) }
            LaunchedEffect(downloadProgress, isDownloaded) {
                if (isDownloading) {
                    showProgress = true
                } else if (showProgress && !isDownloading) {
                    delay(1_500)
                    showProgress = false
                }
            }
            if (showProgress && downloadProgress != null) {
                Spacer(Modifier.height(16.dp))
                val determinate = downloadProgress >= 0f
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    if (determinate) {
                        LinearProgressIndicator(
                            progress = { downloadProgress },
                            modifier = Modifier
                                .weight(1f)
                                .height(4.dp),
                            color = MaterialTheme.colorScheme.primary,
                            trackColor = MaterialTheme.colorScheme.surfaceVariant,
                        )
                        Spacer(Modifier.width(10.dp))
                        Text(
                            text = "${(downloadProgress * 100).toInt()}%",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.primary,
                            fontWeight = FontWeight.Medium,
                        )
                    } else {
                        // Content-Length unknown — indeterminate animation
                        LinearProgressIndicator(
                            modifier = Modifier
                                .weight(1f)
                                .height(4.dp),
                            color = MaterialTheme.colorScheme.primary,
                            trackColor = MaterialTheme.colorScheme.surfaceVariant,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun VinylDisc(hasSong: Boolean, coverUrl: String? = null) {
    val discColor = Color(0xFF1A1A1E)
    val grooveColor = Color(0xFF2E2E35)

    Box(contentAlignment = Alignment.Center) {
        // Background disc with grooves in outer ring only
        Canvas(modifier = Modifier.fillMaxSize()) {
            val maxRadius = size.minDimension / 2

            drawCircle(color = discColor, radius = maxRadius)
            // Tight grooves in outer 15% ring
            val grooveCount = 4
            for (i in 1..grooveCount) {
                drawCircle(
                    color = grooveColor,
                    radius = maxRadius * (1f - i * 0.035f),
                    style = Stroke(width = 1f),
                )
            }
            // Black label fills 85% of disc radius — minimal "red" border
            val labelRadius = maxRadius * 0.85f
            drawCircle(color = Color.Black, radius = labelRadius)
            // Subtle ring on label edge
            drawCircle(
                color = Color(0xFF333333),
                radius = labelRadius * 0.95f,
                style = Stroke(width = 1f),
            )
            drawCircle(color = Color(0xFF111111), radius = maxRadius * 0.05f)
        }

        // Cover image fills nearly entire label
        if (hasSong && !coverUrl.isNullOrBlank()) {
            AsyncImage(
                model = coverUrl,
                contentDescription = "专辑封面",
                modifier = Modifier
                    .fillMaxSize(fraction = 0.85f * 0.93f)
                    .clip(CircleShape),
            )
        }
    }
}

/**
 * Animated circular-arrow re-search button drawn with Canvas.
 * Rotates continuously while [isSearching] is true.
 */
@Composable
private fun ReSearchButton(
    isSearching: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    iconScale: Float = 1f,    // 1.0 for re-search, ~0.67 for reset
    clockwise: Boolean = true, // true = re-search, false = reset
) {
    val infiniteTransition = rememberInfiniteTransition(label = "reSearch")
    val rotation by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(
            animation = tween(800, easing = LinearEasing),
            repeatMode = RepeatMode.Restart,
        ),
        label = "reSearchRotate",
    )

    val iconColor = MaterialTheme.colorScheme.onSurfaceVariant
    val iconSize = (14 * iconScale).dp
    val strokeWidth = (2 * iconScale).dp
    val arrowTipSize = (3 * iconScale).dp

    Box(
        modifier = modifier
            .size(28.dp)
            .clip(RoundedCornerShape(6.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f))
            .clickable { onClick() },
        contentAlignment = Alignment.Center,
    ) {
        Canvas(
            modifier = Modifier
                .size(iconSize)
                .rotate(if (isSearching) rotation else 0f),
        ) {
            val strokeW = strokeWidth.toPx()
            val r = (size.minDimension - strokeW) / 2
            val cx = size.width / 2
            val cy = size.height / 2
            // Clockwise or counter-clockwise arc
            val sweep = if (clockwise) 300f else -300f
            drawArc(
                color = iconColor,
                startAngle = if (clockwise) -90f else 90f,
                sweepAngle = sweep,
                useCenter = false,
                topLeft = Offset(cx - r, cy - r),
                size = Size(r * 2, r * 2),
                style = Stroke(width = strokeW, cap = StrokeCap.Round),
            )
            // Arrowhead position depends on direction
            val arrowDeg = if (clockwise) 120.0 else 60.0
            val arrowAngle = Math.toRadians(arrowDeg)
            val tipX = cx + r * kotlin.math.cos(arrowAngle).toFloat()
            val tipY = cy + r * kotlin.math.sin(arrowAngle).toFloat()
            val arrowSize = arrowTipSize.toPx()
            // Arrow wings point outward along the arc direction
            val wingOffset = if (clockwise) 140.0 else -140.0
            val a1 = Math.toRadians(arrowDeg + wingOffset)
            val a2 = Math.toRadians(arrowDeg - wingOffset)
            val path = Path().apply {
                moveTo(tipX, tipY)
                lineTo(
                    tipX + arrowSize * kotlin.math.cos(a1).toFloat(),
                    tipY + arrowSize * kotlin.math.sin(a1).toFloat(),
                )
                lineTo(
                    tipX + arrowSize * kotlin.math.cos(a2).toFloat(),
                    tipY + arrowSize * kotlin.math.sin(a2).toFloat(),
                )
                close()
            }
            drawPath(path, color = iconColor)
        }
    }
}

@Composable
private fun LyricsSheet(
    lyricsText: String,
    currentPosition: Long,
    onDismiss: () -> Unit,
    lyricsOffsetMs: Long = 0L,
    isBilibili: Boolean = false,
    songId: String = "",
    currentSongName: String = "",
    currentArtist: String = "",
    onOffsetChanged: (Long) -> Unit = {},
    onOffsetReset: () -> Unit = {},
    onReSearch: () -> Unit = {},
    isReSearching: Boolean = false,
    onLyricsManualSearch: (songId: String, songName: String, artist: String) -> Unit = { _, _, _ -> },
    onSeekToLine: (Long) -> Unit = {},
) {
    val lines = remember(lyricsText) { parseLrcToLines(lyricsText) }
    val listState = rememberLazyListState()

    // Apply manual sync offset
    val adjustedPosition = currentPosition + lyricsOffsetMs

    // ── Float-index for line highlight (immediate response) ─────────
    val floatIndex = remember(adjustedPosition, lines.size) {
        calculateFloatIndex(lines, adjustedPosition)
    }
    val currentLineIndex = floatIndex.toInt()

    // ── Manual scroll detection ─────────────────────────────────────
    var userScrolled by remember { mutableStateOf(false) }
    var autoScrollTick by remember { mutableStateOf(0) }
    var isProgrammaticScroll by remember { mutableStateOf(false) }

    // Auto-scroll: triggered ONLY when the integer line index changes
    LaunchedEffect(currentLineIndex, autoScrollTick) {
        if (currentLineIndex >= 0 && lines.isNotEmpty()) {
            val viewportHeight = listState.layoutInfo.viewportEndOffset -
                listState.layoutInfo.viewportStartOffset
            if (viewportHeight > 0) {
                // Fixed anchor: current line ~1/3 from top
                val offset = -(viewportHeight / 3) + 60
                isProgrammaticScroll = true
                listState.animateScrollToItem(
                    index = currentLineIndex,
                    scrollOffset = offset,
                )
                isProgrammaticScroll = false
            }
            userScrolled = false
        }
    }

    // Detect manual scrolls: if user drags away, mark and start reset timer
    LaunchedEffect(listState.isScrollInProgress) {
        if (!listState.isScrollInProgress && userScrolled) {
            delay(5_000)
            userScrolled = false
            autoScrollTick++ // trigger re-scroll
        }
    }
    if (currentLineIndex >= 0 && listState.isScrollInProgress &&
        listState.firstVisibleItemIndex != currentLineIndex &&
        !isProgrammaticScroll) {
        userScrolled = true
    }

    // ── Offset control expanded state ────────────────────────────
    var showOffsetSlider by remember { mutableStateOf(false) }
    var interactionTick by remember { mutableStateOf(0) }
    // Auto-collapse after 3s idle — resets on every interaction
    LaunchedEffect(showOffsetSlider, interactionTick) {
        if (showOffsetSlider) {
            delay(5_000)
            showOffsetSlider = false
        }
    }

    // Wrapper that bumps interaction tick to reset the timer
    val onOffsetInteract: (Long) -> Unit = { ms ->
        interactionTick++
        onOffsetChanged(ms)
    }

    Surface(
        modifier = Modifier.fillMaxSize(),
        color = MaterialTheme.colorScheme.surface,
    ) {
        Column(
            modifier = Modifier.statusBarsPadding(),
        ) {
            // Header — with optional offset control for B站
            if (showOffsetSlider && isBilibili) {
                // ── Expanded: Slider bar ──────────────────────
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 8.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    // Coarse backward
                    Text(
                        "◀◀",
                        modifier = Modifier
                            .clickable { onOffsetInteract(lyricsOffsetMs - 3000) }
                            .padding(4.dp),
                        fontSize = 14.sp,
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                    // Fine backward
                    Text(
                        "◀",
                        modifier = Modifier
                            .clickable { onOffsetInteract(lyricsOffsetMs - 500) }
                            .padding(4.dp),
                        fontSize = 14.sp,
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                    // Slider
                    val offsetSeconds = lyricsOffsetMs / 1000f
                    Slider(
                        value = offsetSeconds.coerceIn(-30f, 30f),
                        onValueChange = { onOffsetInteract((it * 1000).toLong()) },
                        onValueChangeFinished = { interactionTick++ },
                        valueRange = -30f..30f,
                        modifier = Modifier
                            .weight(1f)
                            .height(32.dp),
                        colors = SliderDefaults.colors(
                            thumbColor = MaterialTheme.colorScheme.primary,
                            activeTrackColor = MaterialTheme.colorScheme.primary,
                            inactiveTrackColor = MaterialTheme.colorScheme.surfaceVariant,
                        ),
                    )
                    // Fine forward
                    Text(
                        "▶",
                        modifier = Modifier
                            .clickable { onOffsetInteract(lyricsOffsetMs + 500) }
                            .padding(4.dp),
                        fontSize = 14.sp,
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                    // Coarse forward
                    Text(
                        "▶▶",
                        modifier = Modifier
                            .clickable { onOffsetInteract(lyricsOffsetMs + 3000) }
                            .padding(4.dp),
                        fontSize = 14.sp,
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                }
                // Offset value display — centered below slider
                val offsetLabel = if (lyricsOffsetMs == 0L) "+0.0s"
                    else "${if (lyricsOffsetMs > 0) "+" else ""}${"%.1f".format(lyricsOffsetMs / 1000f)}s"
                Text(
                    offsetLabel,
                    modifier = Modifier.fillMaxWidth().padding(bottom = 4.dp),
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Medium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                )
            } else {
                // ── Collapsed: title left, controls centered, close right ─
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 12.dp),
                ) {
                    // Title — left
                    Text(
                        "歌词",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.align(Alignment.CenterStart),
                    )

                    // Close button — right
                    IconButton(
                        onClick = onDismiss,
                        modifier = Modifier
                            .size(32.dp)
                            .clip(CircleShape)
                            .background(MaterialTheme.colorScheme.surfaceVariant)
                            .align(Alignment.CenterEnd),
                    ) {
                        Icon(
                            Icons.Default.Close,
                            contentDescription = "关闭歌词",
                            modifier = Modifier.size(18.dp),
                        )
                    }

                    // Control buttons — center (B站 only)
                    if (isBilibili) {
                        var showManualSearchDialog by remember { mutableStateOf(false) }
                        Row(
                            modifier = Modifier.align(Alignment.Center),
                            verticalAlignment = Alignment.Bottom,
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                        ) {
                            ReSearchButton(
                                isSearching = isReSearching,
                                onClick = onReSearch,
                            )
                            // Edit button — manual rename
                            Box(
                                modifier = Modifier
                                    .size(28.dp)
                                    .clip(RoundedCornerShape(6.dp))
                                    .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f))
                                    .clickable { showManualSearchDialog = true },
                                contentAlignment = Alignment.Center,
                            ) {
                                Icon(
                                    Icons.Default.Edit,
                                    contentDescription = "编辑搜索词",
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.size(14.dp),
                                )
                            }
                            // Offset button — icon only, expands slider on click
                            Box(
                                modifier = Modifier
                                    .size(28.dp)
                                    .clip(RoundedCornerShape(6.dp))
                                    .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f))
                                    .clickable { showOffsetSlider = !showOffsetSlider },
                                contentAlignment = Alignment.Center,
                            ) {
                                Text(
                                    "⚙",
                                    fontSize = 14.sp,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                            ReSearchButton(
                                isSearching = false,
                                onClick = onOffsetReset,
                                clockwise = false,
                            )
                        }

                        // Manual search dialog
                        if (showManualSearchDialog) {
                            val parsed = remember(currentSongName, currentArtist) {
                                BilibiliTitleParser.parse(currentSongName, currentArtist)
                            }
                            var editSongName by remember { mutableStateOf(parsed.cleanSongName) }
                            var editArtist by remember { mutableStateOf(parsed.extractedArtist ?: "") }
                            AlertDialog(
                                onDismissRequest = { showManualSearchDialog = false },
                                title = { Text("编辑歌词搜索词") },
                                text = {
                                    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                                        OutlinedTextField(
                                            value = editSongName,
                                            onValueChange = { editSongName = it },
                                            label = { Text("歌名") },
                                            singleLine = true,
                                            modifier = Modifier.fillMaxWidth(),
                                        )
                                        OutlinedTextField(
                                            value = editArtist,
                                            onValueChange = { editArtist = it },
                                            label = { Text("歌手（选填）") },
                                            singleLine = true,
                                            modifier = Modifier.fillMaxWidth(),
                                        )
                                    }
                                },
                                confirmButton = {
                                    TextButton(onClick = {
                                        onLyricsManualSearch(songId, editSongName, editArtist)
                                        showManualSearchDialog = false
                                    }) {
                                        Text("搜索")
                                    }
                                },
                                dismissButton = {
                                    TextButton(onClick = { showManualSearchDialog = false }) {
                                        Text("取消")
                                    }
                                },
                            )
                        }
                    }
                }
            }

            // Lyrics body — LRC with LazyColumn + pointer overlay
            if (lines.isNotEmpty()) {
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth(),
                ) {
                    LazyColumn(
                        state = listState,
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(horizontal = 24.dp),
                    ) {
                        // Top spacer — allows first line to reach viewport center
                        item { Spacer(Modifier.height(400.dp)) }

                        itemsIndexed(lines) { index, line ->
                            val isCurrent = index == currentLineIndex
                            val isPast = index < currentLineIndex

                            if (isCurrent) {
                                // Current line with light background for visibility
                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .background(
                                            MaterialTheme.colorScheme.primary.copy(alpha = 0.12f),
                                            RoundedCornerShape(10.dp),
                                        )
                                        .padding(vertical = 8.dp, horizontal = 4.dp),
                                    contentAlignment = Alignment.Center,
                                ) {
                                    Text(
                                        text = line.text,
                                        style = MaterialTheme.typography.bodyLarge,
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 18.sp,
                                        color = MaterialTheme.colorScheme.primary,
                                        textAlign = TextAlign.Center,
                                    )
                                }
                            } else {
                                Text(
                                    text = line.text,
                                    style = MaterialTheme.typography.bodyLarge,
                                    fontWeight = FontWeight.Normal,
                                    fontSize = 15.sp,
                                    color = if (isPast)
                                        MaterialTheme.colorScheme.onSurface.copy(alpha = 0.35f)
                                    else
                                        MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f),
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(vertical = 8.dp),
                                    textAlign = TextAlign.Center,
                                )
                            }
                        }

                        // Bottom spacer — allows last line to reach viewport center
                        item { Spacer(Modifier.height(400.dp)) }
                    }

                    // ── Pointer overlay (appears on manual scroll) ─────
                    Column(
                        modifier = Modifier.align(Alignment.Center),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center,
                    ) {
                        AnimatedVisibility(
                            visible = userScrolled && lines.isNotEmpty(),
                            enter = fadeIn(tween(200)),
                            exit = fadeOut(tween(200)),
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 24.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                // Left play button — points right (toward center)
                                Box(
                                    modifier = Modifier
                                        .size(24.dp)
                                        .clip(RoundedCornerShape(6.dp))
                                        .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f))
                                        .clickable {
                                            val centerIndex = getCenterLineIndex(listState, lines)
                                            if (centerIndex >= 0 && centerIndex < lines.size) {
                                                onSeekToLine(lines[centerIndex].timestampMs)
                                                userScrolled = false
                                            }
                                        },
                                    contentAlignment = Alignment.Center,
                                ) {
                                    Icon(
                                        Icons.Default.PlayArrow,
                                        contentDescription = "从此行播放",
                                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                        modifier = Modifier.size(14.dp),
                                    )
                                }

                                Spacer(Modifier.width(12.dp))

                                // Horizontal line
                                Box(
                                    modifier = Modifier
                                        .weight(1f)
                                        .height(1.5.dp)
                                        .background(MaterialTheme.colorScheme.primary),
                                )

                                Spacer(Modifier.width(12.dp))

                                // Right play button — points left (toward center)
                                Box(
                                    modifier = Modifier
                                        .size(24.dp)
                                        .clip(RoundedCornerShape(6.dp))
                                        .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f))
                                        .clickable {
                                            val centerIndex = getCenterLineIndex(listState, lines)
                                            if (centerIndex >= 0 && centerIndex < lines.size) {
                                                onSeekToLine(lines[centerIndex].timestampMs)
                                                userScrolled = false
                                            }
                                        },
                                    contentAlignment = Alignment.Center,
                                ) {
                                    Icon(
                                        Icons.Default.PlayArrow,
                                        contentDescription = "从此行播放",
                                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                        modifier = Modifier
                                            .size(14.dp)
                                            .rotate(180f),
                                    )
                                }
                        }
                        }
                    }
                }
            } else {
                // Plain text fallback (no LRC timestamps)
                val plainLines = lyricsText.split("\n")
                LazyColumn(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth()
                        .padding(horizontal = 24.dp),
                    contentPadding = PaddingValues(vertical = 16.dp),
                ) {
                    itemsIndexed(plainLines) { _, line ->
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
}

// ═══════════════════════════════════════════════════════════════════════════
// LRC parser
// ═══════════════════════════════════════════════════════════════════════════

/** A single parsed lyric line with its timestamp. */
internal data class LyricLine(
    val timestampMs: Long,
    val text: String,
)

/**
 * Parse LRC-format lyrics text into a sorted list of [LyricLine].
 *
 * Handles both `.00` (centiseconds) and `.000` (milliseconds) timestamp
 * formats. Returns an empty list if no timestamped lines are found
 * (e.g. plain text lyrics without LRC markup).
 */
internal fun parseLrcToLines(lrcText: String): List<LyricLine> {
    // Match [mm:ss.xx] or [mm:ss.xxx] followed by optional text
    val regex = Regex("""\[(\d{2}):(\d{2})[.:](\d{2,3})](.*)""")
    return lrcText.split("\n").mapNotNull { line ->
        regex.find(line.trim())?.let { match ->
            val min = match.groupValues[1].toLongOrNull() ?: return@let null
            val sec = match.groupValues[2].toLongOrNull() ?: return@let null
            val frac = match.groupValues[3].toLongOrNull() ?: return@let null
            // Normalize: .00 = centiseconds → multiply by 10; .000 = ms → as-is
            val ms = if (frac <= 99) frac * 10 else frac
            val text = match.groupValues[4].trim()
            if (text.isNotBlank()) {
                LyricLine(min * 60_000 + sec * 1000 + ms, text)
            } else null
        }
    }.sortedBy { it.timestampMs }
}

/**
 * Calculate a continuous "float index" from playback position for smooth scrolling.
 *
 * Returns a [Double] where the integer part is the current lyric line index and
 * the fractional part represents progress through that line (0.0–1.0).
 *
 * For example, 3.5 means the current line is index 3, and playback has reached
 * 50% of the time window between line 3 and line 4.
 *
 * @param lines  Sorted list of parsed lyric lines (must be non-empty)
 * @param positionMs  Adjusted playback position in milliseconds
 * @return Float index clamped to [0.0, lines.lastIndex]
 */
private fun calculateFloatIndex(
    lines: List<LyricLine>,
    positionMs: Long,
): Double {
    if (lines.isEmpty()) return 0.0
    if (lines.size == 1 || positionMs <= lines.first().timestampMs) return 0.0
    if (positionMs >= lines.last().timestampMs) return (lines.lastIndex).toDouble()

    for (i in 0 until lines.lastIndex) {
        val t1 = lines[i].timestampMs
        val t2 = lines[i + 1].timestampMs
        if (positionMs >= t1 && positionMs < t2) {
            val duration = t2 - t1
            val elapsed = positionMs - t1
            val progress = if (duration > 0) elapsed.toDouble() / duration.toDouble() else 0.0
            return i + progress.coerceIn(0.0, 1.0)
        }
    }
    return (lines.lastIndex).toDouble()
}

/**
 * Find the index of the lyric line closest to the vertical center of the viewport.
 *
 * Used when the user clicks the pointer play button — computes which line the
 * pointer is visually aligned with at that moment.
 *
 * @param listState  Current scroll state of the lyrics [LazyColumn]
 * @param lines      Parsed lyric lines with timestamps
 * @return Index into [lines], or -1 if no visible items
 */
private fun getCenterLineIndex(
    listState: LazyListState,
    lines: List<LyricLine>,
): Int {
    val layoutInfo = listState.layoutInfo
    val viewportCenter = (layoutInfo.viewportStartOffset + layoutInfo.viewportEndOffset) / 2

    return layoutInfo.visibleItemsInfo
        .minByOrNull { item ->
            val itemCenter = item.offset + item.size / 2
            kotlin.math.abs(itemCenter - viewportCenter)
        }
        ?.index ?: -1
}

// ── Time formatting ──────────────────────────────────────────────────────

private fun formatTime(ms: Long): String {
    if (ms <= 0) return "0:00"
    val totalSeconds = ms / 1000
    val minutes = totalSeconds / 60
    val seconds = totalSeconds % 60
    return "$minutes:${seconds.toString().padStart(2, '0')}"
}
