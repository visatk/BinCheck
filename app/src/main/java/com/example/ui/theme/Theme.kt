package com.example.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val ConsoleDarkColorScheme = darkColorScheme(
    primary = PlayEmerald,
    onPrimary = Color(0xFF061510),
    secondary = GearRoyalBlue,
    onSecondary = Color.White,
    tertiary = UnknownAmber,
    background = ConsoleBackground,
    onBackground = TextPrimaryLight,
    surface = CardContainerSurface,
    onSurface = TextPrimaryLight,
    surfaceVariant = InputConsoleSurface,
    onSurfaceVariant = TextSecondaryMuted,
    error = DieCrimson
)

@Composable
fun MyApplicationTheme(
    content: @Composable () -> Unit
) {
    MaterialTheme(
        colorScheme = ConsoleDarkColorScheme,
        typography = Typography,
        content = content
    )
}
