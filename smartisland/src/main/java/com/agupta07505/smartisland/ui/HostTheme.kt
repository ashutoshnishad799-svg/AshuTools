/*
 * Ashutool - root toolkit for custom ROMs
 * Copyright (C) 2026 Ashutosh Nishad
 * Telegram: @ashutosh_07x (https://t.me/ashutosh_07x)
 *
 * Free software under the GNU GPL v3.0 or later, with the attribution terms in NOTICE.
 *
 * This file is new, written for Ashutool. It lets the app that hosts Smart Island (Ashutool) hand over its own look:
 * colours, glass strength and the drifting glow behind the screens. When a look is set, SmartIslandTheme paints the
 * Smart Island settings screens with it. Nothing else of Smart Island (its island, its settings) is touched.
 */

package com.agupta07505.smartisland.ui

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat
import com.agupta07505.smartisland.ui.glass.GlassBackdropColors
import com.agupta07505.smartisland.ui.glass.LocalGlassBackdropColors
import com.agupta07505.smartisland.ui.glass.LocalGlassDark
import com.agupta07505.smartisland.ui.glass.LocalLiquidGlass

/** A look from the host app: text, accent, the three background colours, the three glow colours and the glass strength. */
data class HostLook(
    val dark: Boolean,
    /** A flat look has no glow and no movement. */
    val flat: Boolean,
    val textHi: Color,
    val textLo: Color,
    /** The nearly solid colour of dialogs, menus and sheets. */
    val dialog: Color,
    val accent: Color,
    val accent2: Color,
    val bg1: Color,
    val bg2: Color,
    val bg3: Color,
    val orb1: Color,
    val orb2: Color,
    val orb3: Color,
    val orbAlpha: Float,
    /** 0.6 to 1.6, how strong the glass panels are. */
    val glass: Float,
    val animated: Boolean
)

object HostTheme {
    /** Set by the host app. Null means Smart Island uses its own theme. Changing it repaints the open screens at once. */
    var look by mutableStateOf<HostLook?>(null)
}

/** The colour roles of Material 3 for a [HostLook]: glass panels in the host's tint, near solid dialogs, the host's accent. */
internal fun hostColorScheme(l: HostLook): ColorScheme {
    val base = accentColorScheme(l.accent, l.dark)
    val tint = if (l.dark) Color.White else Color.Black
    val g = l.glass.coerceIn(0.6f, 1.6f)
    fun glass(a: Float) = tint.copy(alpha = (a * g).coerceIn(0f, 0.5f))
    val onAccent = if (l.dark) Color(0xFF05101B) else Color.White
    return base.copy(
        primary = l.accent,
        onPrimary = onAccent,
        primaryContainer = lerp(l.dialog, l.accent, 0.30f),
        onPrimaryContainer = l.textHi,
        secondary = l.accent2,
        onSecondary = onAccent,
        secondaryContainer = lerp(l.dialog, l.accent2, 0.24f),
        onSecondaryContainer = l.textHi,
        tertiary = l.orb2,
        tertiaryContainer = lerp(l.dialog, l.orb2, 0.24f),
        onTertiaryContainer = l.textHi,
        background = Color.Transparent,
        onBackground = l.textHi,
        surface = glass(0.075f),
        onSurface = l.textHi,
        surfaceVariant = glass(0.11f),
        onSurfaceVariant = l.textLo,
        surfaceTint = l.accent,
        outline = tint.copy(alpha = 0.30f),
        outlineVariant = tint.copy(alpha = 0.14f),
        surfaceBright = glass(0.16f),
        surfaceDim = glass(0.04f),
        surfaceContainerLowest = glass(0.04f),
        surfaceContainerLow = glass(0.06f),
        surfaceContainer = glass(0.09f),
        surfaceContainerHigh = l.dialog.copy(alpha = 0.96f),
        surfaceContainerHighest = l.dialog.copy(alpha = 0.98f)
    )
}

/** The background of every screen: the three colours as a slow vertical blend, with three glow orbs that drift. */
@Composable
fun AshutoolBackdrop(l: HostLook, modifier: Modifier = Modifier) {
    val p: State<Float> = if (!l.flat && l.animated) {
        rememberInfiniteTransition(label = "host-bg").animateFloat(
            0f, 1f, infiniteRepeatable(tween(20000, easing = LinearEasing), RepeatMode.Reverse), label = "host-bg-p"
        )
    } else remember { mutableFloatStateOf(0.5f) }
    val fill = if (l.flat) Modifier.background(l.bg1) else Modifier.background(Brush.verticalGradient(listOf(l.bg1, l.bg2, l.bg3)))
    Box(modifier.fillMaxSize().then(fill)) {
        if (!l.flat) {
            Canvas(Modifier.fillMaxSize()) {
                val w = size.width
                val h = size.height
                val t = p.value
                fun orb(col: Color, cx: Float, cy: Float, r: Float) {
                    drawCircle(
                        Brush.radialGradient(listOf(col.copy(alpha = l.orbAlpha), Color.Transparent), center = Offset(cx, cy), radius = r),
                        radius = r, center = Offset(cx, cy)
                    )
                }
                orb(l.orb1, w * (0.10f + 0.30f * t), h * 0.10f, w * 0.80f)
                orb(l.orb2, w * (0.98f - 0.35f * t), h * 0.46f, w * 0.72f)
                orb(l.orb3, w * (0.15f + 0.25f * t), h * (0.94f - 0.10f * t), w * 0.85f)
            }
        }
    }
}

/** What SmartIslandTheme draws when the host gave a look. */
@Composable
internal fun HostThemed(l: HostLook, content: @Composable () -> Unit) {
    val context = LocalContext.current
    val scheme = remember(l) { hostColorScheme(l) }
    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            val window = (context as? android.app.Activity)?.window ?: return@SideEffect
            val controller = WindowCompat.getInsetsController(window, view)
            controller.isAppearanceLightStatusBars = !l.dark
            controller.isAppearanceLightNavigationBars = !l.dark
        }
    }
    MaterialTheme(colorScheme = scheme) {
        CompositionLocalProvider(
            LocalLiquidGlass provides true,
            LocalGlassDark provides l.dark,
            LocalGlassBackdropColors provides remember(l) { GlassBackdropColors(l.bg1, l.orb1, l.orb2, l.orb3) }
        ) {
            Box(Modifier.fillMaxSize()) {
                AshutoolBackdrop(l)
                content()
            }
        }
    }
}
