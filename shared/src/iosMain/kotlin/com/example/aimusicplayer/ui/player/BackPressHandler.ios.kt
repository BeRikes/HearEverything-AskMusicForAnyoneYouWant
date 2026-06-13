package com.example.aimusicplayer.ui.player

import androidx.compose.runtime.Composable

@Composable
actual fun BackPressHandler(onBack: () -> Unit) {
    // iOS has no system back button/gesture — the user dismisses
    // via the close button or swipe-down gesture.
}
