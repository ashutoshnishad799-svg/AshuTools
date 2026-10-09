package com.ashu.ashutool

import android.content.Context
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
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color

class Grad(val a: Color, val b: Color) {
    fun h() = Brush.horizontalGradient(listOf(a, b))
    fun d() = Brush.linearGradient(listOf(a, b))
}

/** One full look: background, glow orbs, text, accent and glass strength. */
class Pal(
    val name: String, val dark: Boolean, val flat: Boolean,
    val textHi: Color, val textLo: Color, val dialog: Color, val accent: Color,
    val bg1: Color, val bg2: Color, val bg3: Color,
    val orb1: Color, val orb2: Color, val orb3: Color, val orbA: Float,
    val primary: Grad, val secondary: Grad,
    val glassTop: Float, val glassBottom: Float, val bA: Float, val bB: Float, val bC: Float
) {
    val tint: Color get() = if (dark) Color.White else Color.Black
}

private fun c(v: Long) = Color(v)

object Palette {
    /** Index into [themes]. Indexes 0 to 2 are kept stable across versions. */
    var mode by mutableIntStateOf(0)
    var animated by mutableStateOf(true)

    val themes: List<Pal> = listOf(
        Pal("Aurora", true, false, c(0xFFF2F6FF), c(0xFF9FADC7), c(0xFF141C33), c(0xFF2DE2C0),
            c(0xFF070B14), c(0xFF0C1232), c(0xFF081A26), c(0xFF7B4DFF), c(0xFF12D8C0), c(0xFF2F6BFF), 0.50f,
            Grad(c(0xFF2DE2C0), c(0xFF4D8DFF)), Grad(c(0xFFB58BFF), c(0xFF5B7DFF)), 0.15f, 0.05f, 0.40f, 0.05f, 0.22f),
        Pal("Pure black", true, true, c(0xFFFFFFFF), c(0xFF9AA3B2), c(0xFF0E0E12), c(0xFF2DE2C0),
            c(0xFF000000), c(0xFF000000), c(0xFF000000), c(0xFF000000), c(0xFF000000), c(0xFF000000), 0f,
            Grad(c(0xFF2DE2C0), c(0xFF4D8DFF)), Grad(c(0xFFB58BFF), c(0xFF5B7DFF)), 0.10f, 0.04f, 0.28f, 0.04f, 0.14f),
        Pal("Pure white", false, true, c(0xFF0B1220), c(0xFF566176), c(0xFFFFFFFF), c(0xFF0FA88E),
            c(0xFFFFFFFF), c(0xFFFFFFFF), c(0xFFFFFFFF), c(0xFFFFFFFF), c(0xFFFFFFFF), c(0xFFFFFFFF), 0f,
            Grad(c(0xFF10C9A8), c(0xFF3B82F6)), Grad(c(0xFF8B5CF6), c(0xFF3B82F6)), 0.07f, 0.03f, 0.22f, 0.05f, 0.12f),
        Pal("Ocean", true, false, c(0xFFEAF6FF), c(0xFF9DB6CC), c(0xFF0C2238), c(0xFF22D3EE),
            c(0xFF04111F), c(0xFF062541), c(0xFF04243A), c(0xFF1E90FF), c(0xFF00E5FF), c(0xFF2563EB), 0.48f,
            Grad(c(0xFF22D3EE), c(0xFF3B82F6)), Grad(c(0xFF60A5FA), c(0xFF818CF8)), 0.15f, 0.05f, 0.40f, 0.05f, 0.22f),
        Pal("Sunset", true, false, c(0xFFFFF4EE), c(0xFFC9A9B4), c(0xFF26111F), c(0xFFFF8A4C),
            c(0xFF12080F), c(0xFF2B0F2A), c(0xFF3A1A1F), c(0xFFFF6B35), c(0xFFFF2E93), c(0xFF8B5CF6), 0.48f,
            Grad(c(0xFFFFB84D), c(0xFFFF5C8A)), Grad(c(0xFFF472B6), c(0xFFA78BFA)), 0.15f, 0.05f, 0.40f, 0.05f, 0.22f),
        Pal("Forest", true, false, c(0xFFEEFBF3), c(0xFF9BB9A8), c(0xFF0E2218), c(0xFF34D399),
            c(0xFF03110B), c(0xFF06251A), c(0xFF0B2A12), c(0xFF10B981), c(0xFF84CC16), c(0xFF059669), 0.45f,
            Grad(c(0xFFA3E635), c(0xFF10B981)), Grad(c(0xFF5EEAD4), c(0xFF4ADE80)), 0.15f, 0.05f, 0.40f, 0.05f, 0.22f),
        Pal("Cyber", true, false, c(0xFFF5F0FF), c(0xFFB3A6D6), c(0xFF190F33), c(0xFF00F0FF),
            c(0xFF07030F), c(0xFF150A2B), c(0xFF0A0A1F), c(0xFFFF2BD6), c(0xFF00E5FF), c(0xFF7C3AED), 0.50f,
            Grad(c(0xFFFF2BD6), c(0xFF00E5FF)), Grad(c(0xFFA78BFA), c(0xFF22D3EE)), 0.15f, 0.05f, 0.42f, 0.05f, 0.24f),
        Pal("Rose", true, false, c(0xFFFFF1F5), c(0xFFC9A3B4), c(0xFF2A1020), c(0xFFFB7185),
            c(0xFF150810), c(0xFF2B0F1E), c(0xFF1E0B22), c(0xFFF43F5E), c(0xFFEC4899), c(0xFFA855F7), 0.46f,
            Grad(c(0xFFFB7185), c(0xFFC084FC)), Grad(c(0xFFF9A8D4), c(0xFFA78BFA)), 0.15f, 0.05f, 0.40f, 0.05f, 0.22f),
        Pal("Graphite", true, false, c(0xFFEEF1F6), c(0xFF98A2B3), c(0xFF1A1D24), c(0xFF7DD3FC),
            c(0xFF0E0F12), c(0xFF17191F), c(0xFF101216), c(0xFF475569), c(0xFF64748B), c(0xFF334155), 0.36f,
            Grad(c(0xFF7DD3FC), c(0xFFA5B4FC)), Grad(c(0xFF94A3B8), c(0xFF7DD3FC)), 0.13f, 0.05f, 0.34f, 0.05f, 0.18f),
        Pal("Mint", false, false, c(0xFF06231B), c(0xFF4B6B60), c(0xFFFFFFFF), c(0xFF0E9F8A),
            c(0xFFE6FBF4), c(0xFFD3F5EA), c(0xFFE4F7FF), c(0xFF34D399), c(0xFF22D3EE), c(0xFFA7F3D0), 0.45f,
            Grad(c(0xFF10B981), c(0xFF06B6D4)), Grad(c(0xFF14B8A6), c(0xFF6366F1)), 0.08f, 0.03f, 0.22f, 0.05f, 0.10f),
        Pal("Sand", false, false, c(0xFF2B1A0A), c(0xFF7A6350), c(0xFFFFFFFF), c(0xFFC2570C),
            c(0xFFFFF7EB), c(0xFFFDEBD3), c(0xFFFFF1E0), c(0xFFFDBA74), c(0xFFFCA5A5), c(0xFFFCD34D), 0.50f,
            Grad(c(0xFFF59E0B), c(0xFFEF4444)), Grad(c(0xFFFB923C), c(0xFFF43F5E)), 0.08f, 0.03f, 0.22f, 0.05f, 0.10f),
        Pal("Sky", false, false, c(0xFF0B1630), c(0xFF56627A), c(0xFFFFFFFF), c(0xFF2563EB),
            c(0xFFEAF4FF), c(0xFFD9E9FF), c(0xFFEEF0FF), c(0xFF60A5FA), c(0xFFA78BFA), c(0xFF38BDF8), 0.45f,
            Grad(c(0xFF3B82F6), c(0xFF8B5CF6)), Grad(c(0xFF6366F1), c(0xFF06B6D4)), 0.08f, 0.03f, 0.22f, 0.05f, 0.10f)
    )

    val cur: Pal get() = themes[mode.coerceIn(0, themes.size - 1)]

    fun load(ctx: Context) {
        mode = LockStore.int(ctx, "theme", 0).coerceIn(0, themes.size - 1)
        animated = LockStore.bool(ctx, "anim_bg", true)
    }
}

val TextHi: Color get() = Palette.cur.textHi
val TextLo: Color get() = Palette.cur.textLo
val Tint: Color get() = Palette.cur.tint
val Accent: Color get() = Palette.cur.accent
val DialogBg: Color get() = Palette.cur.dialog
val Line: Color get() = Tint.copy(alpha = if (Palette.cur.dark) 0.15f else 0.14f)
val Surf2: Color get() = Tint.copy(alpha = if (Palette.cur.dark) 0.10f else 0.07f)
val Ink = Color(0xFF05101B)
val Accent2 = Color(0xFF7AA2FF)
val Warn = Color(0xFFFFB84D)
val Danger = Color(0xFFFF6B8B)

/** Brand gradients follow the theme. Only status colours (green, amber, red) stay fixed. */
val GTeal: Grad get() = Palette.cur.primary
val GViolet: Grad get() = Palette.cur.secondary
val GGreen = Grad(Color(0xFF9BF27A), Color(0xFF1FD6B0))
val GFire = Grad(Color(0xFFFFC14D), Color(0xFFFF6A5C))
val GPink: Grad get() = Palette.cur.secondary
val GBlue: Grad get() = Palette.cur.secondary
val GCyan: Grad get() = Palette.cur.primary
val GRed = Grad(Color(0xFFFF8A5C), Color(0xFFFF4D6D))

/** Backdrop for every screen. Gradient themes get slow drifting glow orbs, flat themes stay still. */
@Composable
fun Aurora(content: @Composable BoxScope.() -> Unit) {
    val pal = Palette.cur
    val p: State<Float> = if (!pal.flat && Palette.animated) {
        rememberInfiniteTransition(label = "aurora").animateFloat(
            0f, 1f, infiniteRepeatable(tween(20000, easing = LinearEasing), RepeatMode.Reverse), label = "p"
        )
    } else remember { mutableFloatStateOf(0.5f) }
    val base = if (pal.flat) Modifier.background(pal.bg1)
    else Modifier.background(Brush.verticalGradient(listOf(pal.bg1, pal.bg2, pal.bg3)))
    Box(Modifier.fillMaxSize().then(base)) {
        if (!pal.flat) {
            Canvas(Modifier.fillMaxSize()) {
                val w = size.width
                val h = size.height
                val t = p.value
                fun orb(col: Color, cx: Float, cy: Float, r: Float) {
                    drawCircle(
                        Brush.radialGradient(listOf(col.copy(alpha = pal.orbA), Color.Transparent), center = Offset(cx, cy), radius = r),
                        radius = r, center = Offset(cx, cy)
                    )
                }
                orb(pal.orb1, w * (0.10f + 0.30f * t), h * 0.10f, w * 0.80f)
                orb(pal.orb2, w * (0.98f - 0.35f * t), h * 0.46f, w * 0.72f)
                orb(pal.orb3, w * (0.15f + 0.25f * t), h * (0.94f - 0.10f * t), w * 0.85f)
            }
        }
        content()
    }
}

@Composable
fun AshuTheme(content: @Composable () -> Unit) {
    val pal = Palette.cur
    val scheme = if (pal.dark) darkColorScheme(
        primary = Accent, onPrimary = Ink, secondary = Accent2, background = Color.Transparent, onBackground = TextHi,
        surface = DialogBg, onSurface = TextHi, surfaceVariant = Surf2, onSurfaceVariant = TextLo, error = Danger, outline = Line
    ) else lightColorScheme(
        primary = Accent, onPrimary = Color.White, secondary = Accent2, background = Color.Transparent, onBackground = TextHi,
        surface = DialogBg, onSurface = TextHi, surfaceVariant = Surf2, onSurfaceVariant = TextLo, error = Danger, outline = Line
    )
    MaterialTheme(colorScheme = scheme, content = content)
}
