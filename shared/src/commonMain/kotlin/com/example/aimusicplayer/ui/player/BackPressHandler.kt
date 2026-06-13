package com.example.aimusicplayer.ui.player

import androidx.compose.runtime.Composable

/**
 * Platform-specific back-press handler.
 *
 * On Android, registers with the back-press dispatcher to intercept
 * system back gestures. On iOS, this is a no-op (iOS has no system
 * back button/gesture in the same sense).
 */
@Composable
expect fun BackPressHandler(onBack: () -> Unit)
