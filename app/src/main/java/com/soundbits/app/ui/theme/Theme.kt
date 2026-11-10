package com.soundbits.app.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val YtmDark = darkColorScheme(
    primary = Color.White,
    onPrimary = Color.Black,
    background = Color(0xFF030303),
    onBackground = Color.White,
    surface = Color(0xFF030303),
    onSurface = Color.White,
    surfaceVariant = Color(0xFF212121),
    onSurfaceVariant = Color(0xFFAAAAAA),
    surfaceContainer = Color(0xFF0F0F0F),
    surfaceContainerHigh = Color(0xFF1E1E1E),
    secondaryContainer = Color(0xFF2E2E2E),
    onSecondaryContainer = Color.White,
    error = Color(0xFFFF5252),
)

@Composable
fun SoundBitsTheme(content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = YtmDark, content = content)
}
