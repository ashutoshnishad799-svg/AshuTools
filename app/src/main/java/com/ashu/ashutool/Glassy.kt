/*
 * Ashutool - root toolkit for custom ROMs
 * Copyright (C) 2026 Ashutosh Nishad
 * Telegram: @ashutosh_07x (https://t.me/ashutosh_07x)
 *
 * Free software under the GNU GPL v3.0 or later, with the attribution terms in NOTICE.
 * Keep this header and the author credit in every copy and modified version.
 */


package com.ashu.ashutool

import android.content.Context
import android.os.Build
import android.view.WindowManager
import androidx.compose.foundation.background
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color

/**
 * The glass look for windows that float over other apps (the game panel, the volume panel, the Dynamic Island).
 * An overlay cannot see through to a blurred copy of the screen by itself, so it is built from a tinted base, a
 * light tint on top, a soft shine and a bright edge. On Android 12 and newer, with Glass blur switched on in Settings,
 * the system also blurs the real content behind the window.
 */
object Glassy {
    /** Real blur behind a window. Only does anything on Android 12+, when the ROM allows it and the user chose it. */
    @Suppress("DEPRECATION")
    fun blur(ctx: Context, lp: WindowManager.LayoutParams, radiusDp: Int) {
        if (Build.VERSION.SDK_INT < 31 || !LockStore.bool(ctx, "glass_blur", false)) return
        lp.flags = lp.flags or WindowManager.LayoutParams.FLAG_BLUR_BEHIND
        lp.blurBehindRadius = (radiusDp * ctx.resources.displayMetrics.density).toInt()
    }

    /** Whether real blur is switched on, so the fill can be a little more see-through. */
    private fun blurOn(): Boolean = Build.VERSION.SDK_INT >= 31 && Palette.realBlur

    /** Frosted background for a rounded panel. [tint] is the strength of the light layer on top. */
    fun fill(pal: Pal, tint: Float = 0.20f): Modifier {
        val base = (0.80f - 0.16f * (Palette.glass - 1f) - (if (blurOn()) 0.22f else 0f)).coerceIn(0.40f, 0.94f)
        val t = pal.tint
        return Modifier
            .background(pal.bg2.copy(alpha = base))
            .background(Brush.verticalGradient(listOf(t.copy(alpha = tint * 0.55f), t.copy(alpha = tint * 0.12f))))
            .drawBehind {
                drawRect(
                    Brush.linearGradient(
                        listOf(Color.White.copy(alpha = if (pal.dark) 0.14f else 0.34f), Color.Transparent),
                        start = Offset.Zero, end = Offset(size.width * 0.8f, size.height * 0.8f)
                    )
                )
            }
    }

    /** The bright, thin edge of the glass. */
    fun stroke(pal: Pal): Brush = Brush.linearGradient(
        listOf(pal.tint.copy(alpha = pal.bA), pal.tint.copy(alpha = pal.bB), pal.tint.copy(alpha = pal.bC))
    )
}
