package com.example.aimusicplayer.ui.player

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animate
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.sp
import kotlin.math.roundToInt
import kotlinx.coroutines.delay

/**
 * Text that scrolls horizontally (marquee) when content exceeds available width.
 * Conveyor-belt leftward scroll with a gap before repeating.
 * Starts immediately; freezes in place when paused.
 */
@Composable
fun MarqueeText(
    text: String,
    modifier: Modifier = Modifier,
    style: TextStyle = MaterialTheme.typography.bodyMedium,
    color: Color = MaterialTheme.colorScheme.onSurface,
    fontWeight: FontWeight? = null,
    gapWidth: TextUnit = 48.sp,
    durationMs: Int = 6000,
    isAnimating: Boolean = true,
) {
    val styled = style.let { if (fontWeight != null) it.copy(fontWeight = fontWeight) else it }
    val textMeasurer = rememberTextMeasurer()
    val textSize = remember(text, styled) {
        textMeasurer.measure(text = text, style = styled, maxLines = 1, softWrap = false).size
    }
    val textWidthPx = textSize.width.toFloat()

    BoxWithConstraints(modifier = modifier.clipToBounds()) {
        val density = LocalDensity.current
        val containerWidthPx = with(density) { maxWidth.toPx() }

        if (textWidthPx > 0f && textWidthPx > containerWidthPx) {
            val gapPx = with(density) { gapWidth.toPx() }
            val scrollDistance = textWidthPx + gapPx
            var scrollX by remember(text) { mutableStateOf(0f) }
            val offsetX = scrollX.roundToInt()  // read during composition → drives recomposition
            // Track whether initial 2s delay has elapsed for current text.
            // Keyed by text so it resets on new song, but persists across pause/resume.
            var initialDelayDone by remember(text) { mutableStateOf(false) }

            LaunchedEffect(text, isAnimating) {
                if (!isAnimating) return@LaunchedEffect
                if (!initialDelayDone) {
                    delay(2_000)
                    initialDelayDone = true
                }
                while (true) {
                    val remaining = scrollDistance + scrollX
                    val duration = (remaining / scrollDistance * durationMs).toInt().coerceAtLeast(500)
                    animate(
                        initialValue = scrollX,
                        targetValue = -scrollDistance,
                        animationSpec = tween(durationMillis = duration, easing = LinearEasing),
                    ) { value, _ -> scrollX = value }
                    scrollX = 0f
                }
            }

            // Place each text copy at precise x-offsets.
            // Layout reports ONLY container width → parent never sees an oversized child.
            Layout(
                content = {
                    Text(text = text, style = styled, color = color, maxLines = 1, softWrap = false)
                    Text(text = text, style = styled, color = color, maxLines = 1, softWrap = false)
                    Text(text = text, style = styled, color = color, maxLines = 1, softWrap = false)
                }
            ) { measurables, constraints ->
                val placeables = measurables.map { it.measure(Constraints()) }
                val textW = placeables[0].width
                val textH = placeables.maxOf { it.height }
                val gapPxInt = gapPx.roundToInt()
                // Report container width, NOT full row width
                layout(constraints.maxWidth, textH) {
                    placeables[0].place(offsetX, 0)
                    placeables[1].place(offsetX + textW + gapPxInt, 0)
                    placeables[2].place(offsetX + 2 * (textW + gapPxInt), 0)
                }
            }
        } else {
            Text(
                text = text, style = styled, color = color,
                maxLines = 1, softWrap = false, overflow = TextOverflow.Ellipsis,
            )
        }
    }
}
