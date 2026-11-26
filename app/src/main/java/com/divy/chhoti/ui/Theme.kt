package com.divy.chhoti.ui

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

val Bg = Color(0xFF05080D)
val Panel = Color(0xFF0C1520)
val PanelHi = Color(0xFF132233)
val Cyan = Color(0xFF3FD0FF)
val CyanDim = Color(0xFF1A6F8C)
val Ink = Color(0xFFE6F6FF)
val Muted = Color(0xFF7D93A6)
val Danger = Color(0xFFFF6B6B)

@Composable
fun ChhotiTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = darkColorScheme(
            primary = Cyan, onPrimary = Bg, secondary = CyanDim,
            background = Bg, onBackground = Ink, surface = Panel, onSurface = Ink,
            surfaceVariant = PanelHi, onSurfaceVariant = Muted, error = Danger,
        ),
        content = content,
    )
}
