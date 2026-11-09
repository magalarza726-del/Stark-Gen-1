package com.maverick.airboard.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

val StarkBackground = Color(0xFF030A18)
val StarkSurface = Color(0xFF08172E)
val StarkSurfaceHigh = Color(0xFF0D2340)
val StarkCyan = Color(0xFF17D7FF)
val StarkBlue = Color(0xFF176BFF)
val StarkViolet = Color(0xFF8A4DFF)
val StarkText = Color(0xFFF5F8FF)
val StarkMuted = Color(0xFFAAB6CD)
val StarkSuccess = Color(0xFF31E6C8)

private val StarkColors = darkColorScheme(
    primary = StarkCyan,
    secondary = StarkViolet,
    background = StarkBackground,
    surface = StarkSurface,
    onPrimary = Color.White,
    onSecondary = Color.White,
    onBackground = StarkText,
    onSurface = StarkText,
)

@Composable
fun StarkTheme(content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = StarkColors, content = content)
}
