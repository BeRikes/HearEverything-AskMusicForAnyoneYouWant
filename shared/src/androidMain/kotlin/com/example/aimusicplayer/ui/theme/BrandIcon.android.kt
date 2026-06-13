package com.example.aimusicplayer.ui.theme

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import coil3.compose.AsyncImage

/**
 * Android: load brand icon from drawable resource via Coil.
 */
@Composable
actual fun BrandIcon(modifier: Modifier) {
    AsyncImage(
        model = com.example.aimusicplayer.shared.R.drawable.ic_brand_icon,
        contentDescription = "HearEverything",
        modifier = modifier,
        contentScale = ContentScale.Fit,
    )
}
