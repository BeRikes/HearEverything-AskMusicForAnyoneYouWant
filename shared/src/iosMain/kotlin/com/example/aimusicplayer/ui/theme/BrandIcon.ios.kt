package com.example.aimusicplayer.ui.theme

import androidx.compose.foundation.Canvas
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke

/**
 * iOS: draw the M+♬ brand icon in Canvas. Falls back to vector design
 * until a PNG asset is added to the iOS bundle.
 */
@Composable
actual fun BrandIcon(modifier: Modifier) {
    Canvas(modifier = modifier) {
        val w = size.width; val h = size.height
        val sx = w / 24f; val sy = h / 24f
        val iconColor = Color.White

        drawOval(iconColor, Offset(5.5f * sx, 13f * sy), Size(7f * sx, 6f * sy))
        drawOval(iconColor, Offset(13.5f * sx, 12.5f * sy), Size(7f * sx, 6f * sy))

        val path = Path().apply {
            moveTo(9f * sx, 4f * sy); lineTo(9f * sx, 15f * sy)
            moveTo(17f * sx, 5f * sy); lineTo(17f * sx, 14.5f * sy)
            moveTo(9f * sx, 5f * sy); lineTo(15f * sx, 6.5f * sy); lineTo(17f * sx, 6f * sy)
            moveTo(9f * sx, 8f * sy); lineTo(11f * sx, 9f * sy); lineTo(17f * sx, 8.5f * sy)
        }
        drawPath(path, iconColor, style = Stroke(width = 1.5f * sx, cap = StrokeCap.Round, join = StrokeJoin.Round))
    }
}
