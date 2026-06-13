package com.example.aimusicplayer.ui.player

import androidx.activity.compose.BackHandler
import androidx.compose.runtime.Composable

@Composable
actual fun BackPressHandler(onBack: () -> Unit) {
    BackHandler(enabled = true, onBack = onBack)
}
