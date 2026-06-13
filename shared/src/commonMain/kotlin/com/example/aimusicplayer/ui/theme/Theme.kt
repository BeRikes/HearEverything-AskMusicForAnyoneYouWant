package com.example.aimusicplayer.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

// ═══════════════════════════════════════════════════════════════════════════
// Brand colors — NetEase Red + QQ Blue-Purple
// ═══════════════════════════════════════════════════════════════════════════

// NetEase Cloud Music red family
internal val NeteaseRed = Color(0xFFC62F2F)
internal val NeteaseRedLight = Color(0xFFE84444)
internal val NeteaseRedDark = Color(0xFFA82828)
internal val NeteaseRedContainer = Color(0xFFFFF0F0)

// QQ Music blue-purple gradient tones
internal val QQBlue = Color(0xFF3B6FF5)
internal val QQPurple = Color(0xFF7B4FE0)
internal val QQBlueLight = Color(0xFF6B9AFF)
internal val QQPurpleLight = Color(0xFF9470F0)
internal val QQBlueContainer = Color(0xFFE8F0FF)
internal val QQPurpleContainer = Color(0xFFF0E8FF)

// Neutral
internal val DarkBg = Color(0xFF0A0A0C)
internal val DarkSurface = Color(0xFF141418)
internal val DarkSurfaceVariant = Color(0xFF1E1E24)
internal val DarkCard = Color(0xFF1A1A20)

internal val LightBg = Color(0xFFF3F3F5)
internal val LightSurface = Color(0xFFFFFFFF)
internal val LightSurfaceVariant = Color(0xFFEBEBEF)
internal val LightCard = Color(0xFFFFFFFF)

// ═══════════════════════════════════════════════════════════════════════════
// Light scheme — NetEase red as primary
// ═══════════════════════════════════════════════════════════════════════════

private val LightColorScheme = lightColorScheme(
    // ── Primary: NetEase Red ──
    primary = NeteaseRed,
    onPrimary = Color.White,
    primaryContainer = NeteaseRedContainer,
    onPrimaryContainer = Color(0xFF3E0000),

    // ── Secondary: QQ Blue-Purple ──
    secondary = QQBlue,
    onSecondary = Color.White,
    secondaryContainer = QQBlueContainer,
    onSecondaryContainer = Color(0xFF001B5E),

    // ── Tertiary: QQ Purple ──
    tertiary = QQPurple,
    onTertiary = Color.White,
    tertiaryContainer = QQPurpleContainer,
    onTertiaryContainer = Color(0xFF24005A),

    // ── Error ──
    error = Color(0xFFBA1A1A),
    onError = Color.White,
    errorContainer = Color(0xFFFFDAD6),
    onErrorContainer = Color(0xFF410002),

    // ── Background / Surface ──
    background = LightBg,
    onBackground = Color(0xFF1C1B1F),
    surface = LightSurface,
    onSurface = Color(0xFF1C1B1F),
    surfaceVariant = LightSurfaceVariant,
    onSurfaceVariant = Color(0xFF49454F),
    outline = Color(0xFF79747E),
    outlineVariant = Color(0xFFCAC4D0),
    inverseSurface = Color(0xFF313033),
    inverseOnSurface = Color(0xFFF4EFF4),
    inversePrimary = NeteaseRedLight,
    surfaceTint = NeteaseRed,
    scrim = Color.Black,
)

// ═══════════════════════════════════════════════════════════════════════════
// Dark scheme — near-black backgrounds, vibrant accents
// ═══════════════════════════════════════════════════════════════════════════

private val DarkColorScheme = darkColorScheme(
    // ── Primary: Netease Red (brighter for dark BG) ──
    primary = Color(0xFFE84B4B),
    onPrimary = Color(0xFF3E0000),
    primaryContainer = Color(0xFF7A1A1A),
    onPrimaryContainer = Color(0xFFFFDAD6),

    // ── Secondary: QQ Blue (lighter for dark BG) ──
    secondary = Color(0xFF8AB4F8),
    onSecondary = Color(0xFF001B5E),
    secondaryContainer = Color(0xFF1A3A7A),
    onSecondaryContainer = Color(0xFFD6E4FF),

    // ── Tertiary: QQ Purple (lighter for dark BG) ──
    tertiary = Color(0xFFB794F6),
    onTertiary = Color(0xFF24005A),
    tertiaryContainer = Color(0xFF4A2A8A),
    onTertiaryContainer = Color(0xFFEBE0FF),

    // ── Error ──
    error = Color(0xFFFFB4AB),
    onError = Color(0xFF690005),
    errorContainer = Color(0xFF93000A),
    onErrorContainer = Color(0xFFFFDAD6),

    // ── Background / Surface ──
    background = DarkBg,
    onBackground = Color(0xFFE6E1E5),
    surface = DarkSurface,
    onSurface = Color(0xFFE6E1E5),
    surfaceVariant = DarkSurfaceVariant,
    onSurfaceVariant = Color(0xFFCAC4D0),
    outline = Color(0xFF938F99),
    outlineVariant = Color(0xFF49454F),
    inverseSurface = Color(0xFFE6E1E5),
    inverseOnSurface = Color(0xFF313033),
    inversePrimary = NeteaseRed,
    surfaceTint = Color(0xFFE84B4B),
    scrim = Color.Black,
)

// ═══════════════════════════════════════════════════════════════════════════
// Theme wrapper
// ═══════════════════════════════════════════════════════════════════════════

/**
 * NetEase + QQ Music inspired theme wrapping Material3.
 *
 * - Primary: NetEase Cloud Music red (#C62F2F light / #E84B4B dark)
 * - Secondary: QQ Music blue (#3B6FF5 light / #8AB4F8 dark)
 * - Tertiary: QQ Music purple (#7B4FE0 light / #B794F6 dark)
 *
 * @param darkTheme Force light/dark mode (defaults to system setting).
 */
@Composable
fun HearEverythingTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit,
) {
    val colorScheme = if (darkTheme) DarkColorScheme else LightColorScheme

    MaterialTheme(
        colorScheme = colorScheme,
        content = content,
    )
}
