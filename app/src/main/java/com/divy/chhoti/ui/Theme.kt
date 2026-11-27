package com.divy.chhoti.ui

import androidx.compose.foundation.Image
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.Dp
import com.divy.chhoti.R

/**
 * Chhoti's colours, taken from the character herself: warm paper white, her indigo hair, her yellow badge.
 * Light by default, dark when the phone is in dark mode.
 */
@Immutable
data class Palette(
    val bg: Color,
    val surface: Color,
    val surfaceAlt: Color,
    val line: Color,
    val ink: Color,
    val muted: Color,
    val accent: Color,
    val onAccent: Color,
    val mine: Color,
    val onMine: Color,
    val sun: Color,
    val sunSoft: Color,
    val danger: Color,
    val good: Color,
    val dark: Boolean,
)

val LightPalette = Palette(
    bg = Color(0xFFFAF7F2), surface = Color(0xFFFFFFFF), surfaceAlt = Color(0xFFF1ECE4), line = Color(0xFFE6DFD4),
    ink = Color(0xFF1F1B16), muted = Color(0xFF746C62), accent = Color(0xFF3A2E8C), onAccent = Color.White,
    mine = Color(0xFF3A2E8C), onMine = Color.White, sun = Color(0xFFFFC53D), sunSoft = Color(0xFFFFF3D1),
    danger = Color(0xFFC0392B), good = Color(0xFF2E7D4F), dark = false,
)

val DarkPalette = Palette(
    bg = Color(0xFF15131A), surface = Color(0xFF1F1C26), surfaceAlt = Color(0xFF2A2633), line = Color(0xFF353040),
    ink = Color(0xFFF3EFE8), muted = Color(0xFFA59E94), accent = Color(0xFFA9A1FF), onAccent = Color(0xFF16122E),
    mine = Color(0xFF433A99), onMine = Color.White, sun = Color(0xFFFFC53D), sunSoft = Color(0xFF3A3220),
    danger = Color(0xFFFF7A6B), good = Color(0xFF6FCF97), dark = true,
)

val LocalPalette = staticCompositionLocalOf { LightPalette }

/** The current palette: `C.ink`, `C.accent`... */
val C: Palette
    @Composable get() = LocalPalette.current

@Composable
fun ChhotiTheme(content: @Composable () -> Unit) {
    val p = if (isSystemInDarkTheme()) DarkPalette else LightPalette
    val scheme = if (p.dark) darkColorScheme(
        primary = p.accent, onPrimary = p.onAccent, background = p.bg, onBackground = p.ink,
        surface = p.surface, onSurface = p.ink, surfaceVariant = p.surfaceAlt, onSurfaceVariant = p.muted,
        outline = p.line, error = p.danger,
    ) else lightColorScheme(
        primary = p.accent, onPrimary = p.onAccent, background = p.bg, onBackground = p.ink,
        surface = p.surface, onSurface = p.ink, surfaceVariant = p.surfaceAlt, onSurfaceVariant = p.muted,
        outline = p.line, error = p.danger,
    )
    CompositionLocalProvider(LocalPalette provides p) {
        MaterialTheme(colorScheme = scheme, content = content)
    }
}

/** Chhoti's face, drawn for the channel. */
@Composable
fun Avatar(size: Dp, modifier: Modifier = Modifier) {
    Image(painterResource(R.drawable.chhoti_face), contentDescription = "Chhoti",
        modifier = modifier.size(size).clip(CircleShape))
}
