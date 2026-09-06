package com.neonorbit.app.ui

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

val SpaceBlack = Color(0xFF05070E)
val PanelBlack = Color(0xE8121622)
val NeonCyan = Color(0xFF54F6FF)
val NeonViolet = Color(0xFFA960FF)
val NeonMagenta = Color(0xFFFF4FD8)
val DangerRed = Color(0xFFFF496E)
val SoftWhite = Color(0xFFF5F7FF)
val Muted = Color(0xFF8D96AF)

private val Colors = darkColorScheme(
    primary = NeonCyan,
    secondary = NeonViolet,
    tertiary = NeonMagenta,
    background = SpaceBlack,
    surface = PanelBlack,
    onPrimary = SpaceBlack,
    onBackground = SoftWhite,
    onSurface = SoftWhite,
)

@Composable
fun NeonOrbitTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = Colors,
        typography = MaterialTheme.typography,
        content = content,
    )
}
