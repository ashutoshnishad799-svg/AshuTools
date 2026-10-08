package com.ashu.ashutool

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

val Bg = Color(0xFF0B1018)
val Surf = Color(0xFF121A26)
val Surf2 = Color(0xFF1B2636)
val Line = Color(0xFF263449)
val Accent = Color(0xFF3DDBC0)
val Accent2 = Color(0xFF8AA4FF)
val Warn = Color(0xFFF2B552)
val Danger = Color(0xFFFF6F7D)
val TextHi = Color(0xFFE9EFF7)
val TextLo = Color(0xFF8C9AB0)

@Composable
fun AshuTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = darkColorScheme(
            primary = Accent,
            onPrimary = Color(0xFF00201A),
            secondary = Accent2,
            background = Bg,
            onBackground = TextHi,
            surface = Surf,
            onSurface = TextHi,
            surfaceVariant = Surf2,
            onSurfaceVariant = TextLo,
            error = Danger,
            outline = Line
        ),
        content = content
    )
}
