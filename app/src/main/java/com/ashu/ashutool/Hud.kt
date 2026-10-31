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
import android.graphics.PixelFormat
import android.view.Gravity
import android.view.WindowManager
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlin.math.roundToInt

/** Height of the status bar in pixels. */
fun statusBarHeight(ctx: Context): Int {
    val id = ctx.resources.getIdentifier("status_bar_height", "dimen", "android")
    val h = if (id > 0) ctx.resources.getDimensionPixelSize(id) else 0
    return if (h > 0) h else (24 * ctx.resources.displayMetrics.density).toInt()
}

/** Formats a speed in bytes per second, for example 850 KB/s or 2.4 MB/s. */
fun speedText(down: Long, up: Long, both: Boolean): String {
    fun one(b: Long): String {
        val v = b.coerceAtLeast(0L)
        return if (v >= 1_000_000L) "%.1f MB/s".format(v / 1_000_000f) else "${v / 1000L} KB/s"
    }
    return if (both) "\u2193 ${one(down)}  \u2191 ${one(up)}" else one(down)
}

/** Numbers shown by the overlays, written by the service once a second. */
object SpeedState {
    var text by mutableStateOf("")
}

object HudState {
    /** Frames per second of the game in front, -1 when it cannot be read. */
    var fps by mutableIntStateOf(-1)
    var showFps by mutableStateOf(true)
    var showSpeed by mutableStateOf(true)
}

/**
 * Frames per second of the game in front. SurfaceFlinger keeps the times of the last frames of every layer.
 * The layer that belongs to the game is found once, then its frame times are read once a second.
 */
object FpsMeter {
    @Volatile private var layerPkg = ""
    @Volatile private var layer: String? = null
    private var misses = 0

    private fun findLayer(pkg: String): String? {
        val mine = Root.run("dumpsys SurfaceFlinger --list").map { it.trim() }.filter {
            it.contains(pkg) && !it.startsWith("Background for") && !it.startsWith("Bounds for") && !it.contains("Splash")
        }
        return mine.lastOrNull { it.contains("SurfaceView") } ?: mine.lastOrNull { it.contains("#") } ?: mine.lastOrNull()
    }

    private val SYSFS = listOf(
        "/sys/class/drm/sde-crtc-0/measured_fps", "/sys/class/graphics/fb0/measured_fps", "/sys/class/drm/card0/sde-crtc-0/measured_fps"
    )
    @Volatile private var sysfs: String? = null
    private var sysfsChecked = false

    /** Frames per second the display driver reports. Qualcomm phones have it. Null when this phone does not. */
    private fun fromDisplay(): Int? {
        if (!sysfsChecked) {
            sysfsChecked = true
            sysfs = SYSFS.firstOrNull { p -> Root.run("cat $p 2>/dev/null").any { it.contains("fps", ignoreCase = true) } }
        }
        val p = sysfs ?: return null
        val text = Root.run("cat $p 2>/dev/null").joinToString(" ")
        return Regex("fps:\\s*([0-9.]+)", RegexOption.IGNORE_CASE).find(text)?.groupValues?.get(1)?.toFloatOrNull()?.roundToInt()
    }

    /** Frames shown during the last second, or null when this phone does not give the numbers. */
    fun sample(pkg: String): Int? {
        fromDisplay()?.let { return it }
        if (layerPkg != pkg || layer == null) {
            layer = findLayer(pkg)
            layerPkg = pkg
            misses = 0
        }
        val l = layer ?: return null
        val out = Root.run("dumpsys SurfaceFlinger --latency ${Root.q(l)}")
        val times = ArrayList<Long>()
        for (i in 1 until out.size) {
            val parts = out[i].trim().split(Regex("\\s+"))
            if (parts.size < 3) continue
            val t = parts[1].toLongOrNull() ?: continue
            if (t > 0L && t != Long.MAX_VALUE) times.add(t)
        }
        val last = times.maxOrNull()
        if (last == null) {
            if (++misses >= 3) layer = null
            return null
        }
        misses = 0
        if (System.nanoTime() - last > 1_500_000_000L) return 0
        return times.count { it > last - 1_000_000_000L }
    }

    fun reset() { layer = null; layerPkg = "" }
}

private fun fpsColor(fps: Int): Color = when {
    fps < 0 -> Color(0xFFB4C0D8)
    fps >= 50 -> Color(0xFF4ADE80)
    fps >= 30 -> Color(0xFFFACC15)
    else -> Color(0xFFF87171)
}

private val HudShadow = TextStyle(shadow = Shadow(Color.Black, Offset(0f, 1f), 4f))

/** Small see-through readout in a corner of the screen while you play. It never takes touches. */
class GameHud(private val ctx: Context) {
    private val overlay = Overlay(ctx)

    private fun params(): WindowManager.LayoutParams {
        val d = ctx.resources.displayMetrics.density
        val lp = WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT, WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE or
                WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
            PixelFormat.TRANSLUCENT
        )
        val pos = LockStore.int(ctx, "g_hud_pos", 0)
        lp.gravity = when (pos) {
            1 -> Gravity.TOP or Gravity.END
            2 -> Gravity.BOTTOM or Gravity.START
            3 -> Gravity.BOTTOM or Gravity.END
            else -> Gravity.TOP or Gravity.START
        }
        lp.x = (10 * d).toInt()
        // Below the status bar when it is on top, so it never covers the clock or the icons
        lp.y = if (pos < 2) statusBarHeight(ctx) + (4 * d).toInt() else (6 * d).toInt()
        return lp
    }

    fun show() {
        if (overlay.isShown) { overlay.update(params()); return }
        overlay.show(params()) { Hud() }
    }

    fun hide() = overlay.remove()

    @Composable
    private fun Hud() {
        val fps = HudState.fps
        val speed = SpeedState.text
        if (!HudState.showFps && !(HudState.showSpeed && speed.isNotEmpty())) return
        Row(
            Modifier.clip(RoundedCornerShape(9.dp)).background(Color.Black.copy(alpha = 0.26f)).padding(horizontal = 7.dp, vertical = 3.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            if (HudState.showFps) {
                Text(if (fps >= 0) "$fps FPS" else "-- FPS", color = fpsColor(fps), fontSize = 12.sp, fontWeight = FontWeight.Bold, style = HudShadow)
            }
            if (HudState.showSpeed && speed.isNotEmpty()) {
                Text(speed, color = Color(0xE6FFFFFF), fontSize = 10.sp, fontWeight = FontWeight.Medium, style = HudShadow)
            }
        }
    }
}

/** Network speed in the status bar. A thin see-through window over the bar, never takes touches. */
class StatusSpeed(private val ctx: Context) {
    private val overlay = Overlay(ctx)

    private fun barHeight(): Int = statusBarHeight(ctx)

    private fun params(): WindowManager.LayoutParams {
        val (sw, _) = screenSize(ctx)
        val lp = WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT, barHeight(),
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE or
                WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
            PixelFormat.TRANSLUCENT
        )
        // 50 is the middle of the bar. Smaller moves the readout to the left, larger to the right.
        val pct = LockStore.int(ctx, "sp_x", 36)
        lp.gravity = Gravity.TOP or Gravity.CENTER_HORIZONTAL
        lp.x = ((pct - 50) / 100f * sw).toInt()
        lp.y = 0
        return lp
    }

    fun show() {
        if (overlay.isShown) { overlay.update(params()); return }
        overlay.show(params()) { Readout() }
    }

    fun hide() = overlay.remove()

    @Composable
    private fun Readout() {
        val t = SpeedState.text
        Box(Modifier.fillMaxHeight().padding(horizontal = 4.dp), contentAlignment = Alignment.Center) {
            Text(t, color = Color.White, fontSize = 11.sp, fontWeight = FontWeight.SemiBold, style = HudShadow, maxLines = 1)
        }
    }
}
