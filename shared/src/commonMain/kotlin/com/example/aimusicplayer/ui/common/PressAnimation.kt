package com.example.aimusicplayer.ui.common

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.indication
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.PressInteraction
import androidx.compose.material3.ripple
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import kotlinx.coroutines.launch

/**
 * 按压暗色闪烁 + 涟漪 Modifier 扩展。
 *
 * 点击时从触摸点泛起 Material 涟漪，同时整行叠加暗色半透明层（入场瞬切 / 退场 150ms 淡出）。
 *
 * @param onTap        点击回调
 * @param onLongPress  长按回调，可选
 * @param onRelease    按压释放回调（动画启动后调用），可选，用于清理长按弹窗等状态
 * @param overlayColor 叠加层颜色，默认黑色
 * @param maxAlpha     叠加层最大不透明度，默认 0.12f
 * @param releaseDuration 退场动画时长 ms，默认 150
 */
@Composable
fun Modifier.pressFlash(
    onTap: () -> Unit,
    onLongPress: (() -> Unit)? = null,
    onRelease: (() -> Unit)? = null,
    overlayColor: Color = Color.Black,
    maxAlpha: Float = 0.12f,
    releaseDuration: Int = 150,
): Modifier = composed {
    val currentOnTap by rememberUpdatedState(onTap)
    val currentOnLongPress by rememberUpdatedState(onLongPress)
    val currentOnRelease by rememberUpdatedState(onRelease)
    val scope = rememberCoroutineScope()
    val interactionSource = remember { MutableInteractionSource() }
    val overlayAlpha = remember { Animatable(0f) }

    this
        .indication(interactionSource, ripple())
        .pointerInput(Unit) {
            detectTapGestures(
                onTap = { currentOnTap() },
                onLongPress = { currentOnLongPress?.invoke() },
                onPress = { offset ->
                    val press = PressInteraction.Press(offset)
                    scope.launch { interactionSource.emit(press) }
                    // 入场 — 瞬切（与当前歌单按压动画一致）
                    scope.launch { overlayAlpha.snapTo(maxAlpha) }
                    tryAwaitRelease()
                    // 退场 — 淡出
                    scope.launch { overlayAlpha.animateTo(0f, tween(releaseDuration)) }
                    scope.launch { interactionSource.emit(PressInteraction.Release(press)) }
                    currentOnRelease?.invoke()
                },
            )
        }
        .drawWithContent {
            drawContent()
            if (overlayAlpha.value > 0.001f) {
                drawRect(
                    color = overlayColor.copy(alpha = overlayAlpha.value),
                    topLeft = Offset.Zero,
                    size = size,
                )
            }
        }
}
