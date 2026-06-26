package com.payassistai.app.ui.theme

import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

// Light color scheme
val LightColorScheme = lightColorScheme(
    primary = Color(0xFF6C63FF),
    onPrimary = Color.White,
    primaryContainer = Color(0xFFD0BCFF),
    onPrimaryContainer = Color(0xFF2D1B69),
    secondary = Color(0xFF625B71),
    onSecondary = Color.White,
    secondaryContainer = Color(0xFFE8DEF8),
    onSecondaryContainer = Color(0xFF1E192B),
    tertiary = Color(0xFF7D5260),
    onTertiary = Color.White,
    tertiaryContainer = Color(0xFFFFD8E4),
    onTertiaryContainer = Color(0xFF31111D),
    background = Color(0xFFF5F5F5),      // ← original light background
    onBackground = Color(0xFF1C1B1F),
    surface = Color(0xFFF1EDF7),         // ← new light purple for bars
    onSurface = Color(0xFF1C1B1F),
    surfaceVariant = Color(0xFFE7E0EC),
    onSurfaceVariant = Color(0xFF49454F),
    error = Color(0xFFBA1A1A),
    onError = Color.White,
    errorContainer = Color(0xFFFFDAD6),
    onErrorContainer = Color(0xFF410002),
    outline = Color(0xFF79747E),
    outlineVariant = Color(0xFFCAC4D0),
    scrim = Color.Black,
    inverseSurface = Color(0xFF313033),
    inverseOnSurface = Color(0xFFF4EFF4),
    inversePrimary = Color(0xFFD0BCFF),
    surfaceTint = Color(0xFF6C63FF),
)

// Dark color scheme
val DarkColorScheme = darkColorScheme(
    primary = Color(0xFF6C63FF),
    onPrimary = Color.White,
    primaryContainer = Color(0xFF443A9E),
    onPrimaryContainer = Color(0xFFD0BCFF),
    secondary = Color(0xFF2A2D3A),
    onSecondary = Color.White,
    secondaryContainer = Color(0xFF44475A),
    onSecondaryContainer = Color(0xFFCAC4D0),
    tertiary = Color(0xFF7D5260),
    onTertiary = Color.White,
    tertiaryContainer = Color(0xFF9E6F7D),
    onTertiaryContainer = Color(0xFFFFD8E4),
    background = Color(0xFF0F111A),      // ← original dark background
    onBackground = Color.White,
    surface = Color(0xFF201E26),         // ← new dark grey for bars
    onSurface = Color.White,
    surfaceVariant = Color(0xFF2A2D3A),
    onSurfaceVariant = Color(0xFFCAC4D0),
    error = Color(0xFFCF6679),
    onError = Color.Black,
    errorContainer = Color(0xFF8C1D1D),
    onErrorContainer = Color(0xFFFFDAD6),
    outline = Color(0xFF79747E),
    outlineVariant = Color(0xFF49454F),
    scrim = Color.Black,
    inverseSurface = Color(0xFFE7E0EC),
    inverseOnSurface = Color(0xFF1C1B1F),
    inversePrimary = Color(0xFF6C63FF),
    surfaceTint = Color(0xFF6C63FF),
)

@Composable
fun PayAssistAITheme(
    darkTheme: Boolean,
    content: @Composable () -> Unit
) {
    val colorScheme = if (darkTheme) DarkColorScheme else LightColorScheme
    MaterialTheme(
        colorScheme = colorScheme,
        typography = Typography,
        content = content
    )
}