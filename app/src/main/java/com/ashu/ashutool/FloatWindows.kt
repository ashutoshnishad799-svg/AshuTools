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
import android.graphics.Rect
import android.os.Handler
import android.os.Looper
import android.view.Gravity
import android.view.WindowManager
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.Fullscreen
import androidx.compose.material.icons.outlined.FullscreenExit
import androidx.compose.material.icons.outlined.Remove
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Opens an app in a floating window with its own title bar.
 * The system draws the window. Ashutool adds a bar above it that you can drag to move the window, with buttons
 * to make it smaller or bigger, to go full screen and back, and to close it. Needs a ROM with freeform windows.
 * If the ROM does not allow it, the app still opens and only the bar is skipped.
 */
object FloatWindows {
    private val io = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val chromes = HashMap<String, WindowChrome>()
    private val watching = AtomicBoolean(false)

    /** A short note for the user about the last open, empty when everything worked. */
    @Volatile var note: String = ""

    /** Size and place of a new window: a large card in the middle, with room above for the title bar. */
    private fun defaultBounds(ctx: Context): Rect {
        val (sw, sh) = screenSize(ctx)
        val d = ctx.resources.displayMetrics.density
        val land = sw > sh
        val w = if (land) (sw * 0.46f).toInt() else (sw * 0.90f).toInt()
        val h = if (land) (sh * 0.78f).toInt() else (sh * 0.52f).toInt()
        val left = (sw - w) / 2
        val top = (sh * (if (land) 0.14f else 0.16f)).toInt().coerceAtLeast((40 * d).toInt() + (28 * d).toInt())
        return Rect(left, top, left + w, minOf(top + h, sh - (16 * d).toInt()))
    }

    /** Opens [pkg] floating. Returns false only if the app could not be started at all. */
    suspend fun open(ctx: Context, pkg: String): Boolean {
        note = ""
        val app = ctx.applicationContext
        if (!Windows.open(app, pkg, true)) return false
        // An app that already has a bar just comes to the front
        if (withContext(Dispatchers.Main) { chromes.containsKey(pkg) }) return true
        var id: Int? = null
        var tries = 0
        while (id == null && tries < 10) {
            delay(250)
            id = Windows.taskId(pkg)
            tries++
        }
        val taskId = id
        if (taskId == null) { note = "Opened. Window controls are not available on this ROM"; return true }
        val bounds = defaultBounds(app)
        if (!Windows.resize(taskId, bounds)) { note = "Opened. This ROM does not allow moving windows"; return true }
        withContext(Dispatchers.Main) {
            val c = WindowChrome(app, pkg, taskId, bounds)
            chromes[pkg]?.dismiss()
            chromes[pkg] = c
            c.show()
        }
        watch()
        return true
    }

    internal fun closed(pkg: String) { chromes.remove(pkg) }

    /** Removes bars whose window is gone, for example when the app was closed from recents. Only runs while a bar exists. */
    private fun watch() {
        if (!watching.compareAndSet(false, true)) return
        io.launch {
            try {
                var misses = HashMap<String, Int>()
                while (true) {
                    delay(3000)
                    val snapshot = withContext(Dispatchers.Main) { chromes.values.toList() }
                    if (snapshot.isEmpty()) break
                    if (!Monitor.screenOn) continue
                    val next = HashMap<String, Int>()
                    for (c in snapshot) {
                        if (Windows.taskId(c.pkg) == null) {
                            val n = (misses[c.pkg] ?: 0) + 1
                            if (n >= 2) withContext(Dispatchers.Main) { c.dismiss() } else next[c.pkg] = n
                        }
                    }
                    misses = next
                }
            } finally {
                watching.set(false)
            }
        }
    }

    /** The title bar of one floating window. */
    private class WindowChrome(val ctx: Context, val pkg: String, val taskId: Int, val bounds: Rect) {
        private val overlay = Overlay(ctx)
        private val d = ctx.resources.displayMetrics.density
        private val barH = (40 * d).toInt()
        private var lp: WindowManager.LayoutParams? = null
        private var before: Rect? = null
        private var full by mutableStateOf(false)
        private var visible by mutableStateOf(false)
        private val label = try {
            ctx.packageManager.getApplicationLabel(ctx.packageManager.getApplicationInfo(pkg, 0)).toString()
        } catch (e: Exception) { pkg }

        @Volatile private var target = Rect(bounds)
        private val busy = AtomicBoolean(false)

        fun show() {
            val p = WindowManager.LayoutParams(
                bounds.width(), barH, WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL or
                    WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS or WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
                PixelFormat.TRANSLUCENT
            )
            p.gravity = Gravity.TOP or Gravity.START
            p.x = bounds.left
            p.y = bounds.top - barH
            lp = p
            val drag = DragSpec(longPress = false, onMove = { dx, dy -> move(dx, dy) })
            overlay.show(p, drag = drag) { Bar() }
            Handler(Looper.getMainLooper()).post { visible = true }
        }

        fun dismiss() {
            overlay.remove()
            closed(pkg)
        }

        /** Moves the bar and the window together. */
        private fun move(dx: Int, dy: Int) {
            if (full) return
            val (sw, sh) = screenSize(ctx)
            val w = bounds.width()
            val h = bounds.height()
            val left = (bounds.left + dx).coerceIn(0, (sw - w).coerceAtLeast(0))
            val top = (bounds.top + dy).coerceIn(barH, (sh - (60 * d).toInt()).coerceAtLeast(barH))
            bounds.set(left, top, left + w, top + h)
            sync()
        }

        private fun resizeBy(f: Float) {
            if (full) return
            val (sw, sh) = screenSize(ctx)
            val w = (bounds.width() * f).toInt().coerceIn((220 * d).toInt(), sw)
            val h = (bounds.height() * f).toInt().coerceIn((260 * d).toInt(), sh - barH)
            val left = bounds.left.coerceIn(0, (sw - w).coerceAtLeast(0))
            val top = bounds.top.coerceIn(barH, (sh - h).coerceAtLeast(barH))
            bounds.set(left, top, left + w, minOf(top + h, sh))
            sync()
        }

        private fun toggleFull() {
            val (sw, sh) = screenSize(ctx)
            if (!full) {
                before = Rect(bounds)
                bounds.set(0, barH, sw, sh)
                full = true
            } else {
                bounds.set(before ?: defaultBounds(ctx))
                full = false
            }
            sync()
        }

        private fun close() {
            dismiss()
            io.launch { Windows.forceStop(pkg) }
        }

        /** Puts the bar over the window's new place and asks the system to move the window there. */
        private fun sync() {
            lp?.let {
                it.x = bounds.left
                it.y = bounds.top - barH
                it.width = bounds.width()
                overlay.update(it)
            }
            target = Rect(bounds)
            if (!busy.compareAndSet(false, true)) return
            io.launch {
                var last: Rect? = null
                try {
                    while (true) {
                        val t = target
                        if (last != null && last == t) break
                        Windows.resize(taskId, t)
                        last = t
                    }
                } finally {
                    busy.set(false)
                }
                // A move that arrived after the last check but before busy was cleared
                if (last != target) sync()
            }
        }

        @Composable
        private fun Bar() {
            AnimatedVisibility(
                visible = visible,
                modifier = Modifier.fillMaxSize(),
                enter = fadeIn() + slideInVertically { it / 2 },
                exit = fadeOut() + slideOutVertically { it / 2 }
            ) {
                val shape = RoundedCornerShape(topStart = 18.dp, topEnd = 18.dp)
                Row(
                    Modifier.fillMaxSize().clip(shape).background(Color(0xEE141A2E)).border(1.dp, Color.White.copy(alpha = 0.22f), shape)
                        .padding(start = 12.dp, end = 6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(2.dp)
                ) {
                    AppIcon(pkg, 22.dp)
                    Spacer(Modifier.width(8.dp))
                    Text(
                        label, color = Color.White, fontSize = 13.sp, fontWeight = FontWeight.SemiBold,
                        maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f)
                    )
                    if (!full) {
                        BarButton(Icons.Outlined.Remove) { resizeBy(0.86f) }
                        BarButton(Icons.Outlined.Add) { resizeBy(1.16f) }
                    }
                    BarButton(if (full) Icons.Outlined.FullscreenExit else Icons.Outlined.Fullscreen) { toggleFull() }
                    BarButton(Icons.Outlined.Close) { close() }
                }
            }
        }

        @Composable
        private fun BarButton(icon: ImageVector, onClick: () -> Unit) {
            Box(Modifier.size(34.dp).clip(CircleShape).clickable { onClick() }, contentAlignment = Alignment.Center) {
                Icon(icon, null, tint = Color.White, modifier = Modifier.size(19.dp))
            }
        }
    }
}
