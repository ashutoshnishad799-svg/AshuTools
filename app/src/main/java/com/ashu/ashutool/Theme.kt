package com.ashu.ashutool

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color

val Ink = Color(0xFF05101B)
val TextHi = Color(0xFFF2F6FF)
val TextLo = Color(0xFF9FADC7)
val Line = Color(0x26FFFFFF)
val Surf2 = Color(0x1AFFFFFF)
val DialogBg = Color(0xFF141C33)
val Accent = Color(0xFF2DE2C0)
val Accent2 = Color(0xFF7AA2FF)
val Warn = Color(0xFFFFB84D)
val Danger = Color(0xFFFF6B8B)

class Grad(val a: Color, val b: Color) {
    fun h() = Brush.horizontalGradient(listOf(a, b))
    fun d() = Brush.linearGradient(listOf(a, b))
}

val GTeal = Grad(Color(0xFF2DE2C0), Color(0xFF4D8DFF))
val GViolet = Grad(Color(0xFFB58BFF), Color(0xFF5B7DFF))
val GGreen = Grad(Color(0xFF9BF27A), Color(0xFF1FD6B0))
val GFire = Grad(Color(0xFFFFC14D), Color(0xFFFF6A5C))
val GPink = Grad(Color(0xFFFF7AC0), Color(0xFFA06BFF))
val GBlue = Grad(Color(0xFF4DB8FF), Color(0xFF8A6BFF))
val GCyan = Grad(Color(0xFF3DE0FF), Color(0xFF2DE2C0))
val GRed = Grad(Color(0xFFFF8A5C), Color(0xFFFF4D6D))

/** Animated aurora backdrop. The glass cards sit on top of it. */
@Composable
fun Aurora(content: @Composable BoxScope.() -> Unit) {
    val t = rememberInfiniteTransition(label = "aurora")
    val p by t.animateFloat(
        0f, 1f, infiniteRepeatable(tween(20000, easing = LinearEasing), RepeatMode.Reverse), label = "p"
    )
    Box(
        Modifier.fillMaxSize().background(
            Brush.verticalGradient(listOf(Color(0xFF070B14), Color(0xFF0C1232), Color(0xFF081A26)))
        )
    ) {
        Canvas(Modifier.fillMaxSize()) {
            val w = size.width
            val h = size.height
            fun orb(c: Color, cx: Float, cy: Float, r: Float) {
                drawCircle(
                    Brush.radialGradient(listOf(c.copy(alpha = 0.50f), Color.Transparent), center = Offset(cx, cy), radius = r),
                    radius = r, center = Offset(cx, cy)
                )
            }
            orb(Color(0xFF7B4DFF), w * (0.10f + 0.30f * p), h * 0.10f, w * 0.80f)
            orb(Color(0xFF12D8C0), w * (0.98f - 0.35f * p), h * 0.46f, w * 0.72f)
            orb(Color(0xFF2F6BFF), w * (0.15f + 0.25f * p), h * (0.94f - 0.10f * p), w * 0.85f)
        }
        content()
    }
}

@Composable
fun AshuTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = darkColorScheme(
            primary = Accent,
            onPrimary = Ink,
            secondary = Accent2,
            background = Color.Transparent,
            onBackground = TextHi,
            surface = DialogBg,
            onSurface = TextHi,
            surfaceVariant = Surf2,
            onSurfaceVariant = TextLo,
            error = Danger,
            outline = Line
        ),
        content = content
    )
}
