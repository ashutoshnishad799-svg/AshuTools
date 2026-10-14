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
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.CropSquare
import androidx.compose.material.icons.outlined.CenterFocusStrong
import androidx.compose.material.icons.outlined.East
import androidx.compose.material.icons.outlined.Fullscreen
import androidx.compose.material.icons.outlined.FullscreenExit
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material.icons.outlined.LockOpen
import androidx.compose.material.icons.outlined.MoreHoriz
import androidx.compose.material.icons.outlined.North
import androidx.compose.material.icons.outlined.OpenInFull
import androidx.compose.material.icons.outlined.PhotoCamera
import androidx.compose.material.icons.outlined.PhotoSizeSelectSmall
import androidx.compose.material.icons.outlined.South
import androidx.compose.material.icons.outlined.VolumeOff
import androidx.compose.material.icons.outlined.VolumeUp
import androidx.compose.material.icons.outlined.West
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
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
 * Opens an app in a floating window with a small control pill.
 * The system draws the window. Ashutool adds a compact pill above it. Drag the pill to move the window.
 * It has full screen, more and close. More opens sizes, snap to a side, sound off, lock position and screenshot.
 * The window opens on the right side of the screen by default, which can be changed under Windows and apps.
 * If the ROM does not allow moving windows the pill still appears with the buttons that do not need it.
 */
object FloatWindows {
    private val io = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val chromes = HashMap<String, WindowChrome>()
    private val watching = AtomicBoolean(false)

    /** A short note for the user about the last open, empty when everything worked. */
    @Volatile var note: String = ""

    /**
     * Where a new window goes. Landscape: a tall panel on the chosen side, so a game keeps the other side free.
     * Portrait: a wide card, lower on the screen. Room is left above the window for the pill.
     */
    private fun defaultBounds(ctx: Context): Rect {
        val (sw, sh) = screenSize(ctx)
        val d = ctx.resources.displayMetrics.density
        val margin = (10 * d).toInt()
        val land = sw > sh
        val side = LockStore.int(ctx, "g_float_side", 1).coerceIn(0, 2)
        val w = if (land) (sw * 0.40f).toInt() else (sw * 0.92f).toInt()
        val h = if (land) (sh * 0.80f).toInt() else (sh * 0.52f).toInt()
        val left = when {
            !land -> (sw - w) / 2
            side == 0 -> margin
            side == 1 -> sw - w - margin
            else -> (sw - w) / 2
        }
        val minTop = (44 * d).toInt() + (30 * d).toInt()
        val top = if (land) (sh * 0.14f).toInt().coerceAtLeast(minTop) else (sh - h - (24 * d).toInt()).coerceAtLeast(minTop)
        return Rect(left, top, left + w, minOf(top + h, sh - margin))
    }

    /** Opens [pkg] floating. Returns false only if the app could not be started at all. */
    suspend fun open(ctx: Context, pkg: String): Boolean {
        note = ""
        val app = ctx.applicationContext
        if (!Windows.open(app, pkg, true)) return false
        // An app that already has a pill just comes to the front
        if (withContext(Dispatchers.Main) { chromes.containsKey(pkg) }) return true
        var id: Int? = null
        var tries = 0
        while (id == null && tries < 10) {
            delay(250)
            id = Windows.taskId(pkg)
            tries++
        }
        val taskId = id
        val bounds = defaultBounds(app)
        var movable = false
        if (taskId != null) {
            movable = Windows.resize(taskId, bounds)
            if (movable) {
                // Some apps put their window back where it was a moment after starting, so place it once more
                delay(900)
                Windows.resize(taskId, bounds)
            } else note = "Opened. This ROM does not allow moving windows (${Windows.lastDiag})"
        } else {
            note = "Opened. Could not find the window to move (${Windows.lastDiag})"
        }
        // Last resort: ask for the place when starting the app. Android's own launch bounds option needs no shell command.
        if (!movable) launchWithBounds(app, pkg, bounds)
        withContext(Dispatchers.Main) {
            val c = WindowChrome(app, pkg, taskId, movable, bounds)
            chromes[pkg]?.dismiss()
            chromes[pkg] = c
            c.show()
        }
        watch()
        return true
    }

    private fun launchWithBounds(ctx: Context, pkg: String, bounds: Rect): Boolean = try {
        val i = ctx.packageManager.getLaunchIntentForPackage(pkg)
        if (i == null) false
        else {
            i.addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK)
            ctx.startActivity(i, android.app.ActivityOptions.makeBasic().setLaunchBounds(bounds).toBundle())
            true
        }
    } catch (e: Exception) { false }

    internal fun closed(pkg: String) { chromes.remove(pkg) }

    /** Removes pills whose window is gone, for example when the app was closed from recents. Only runs while a pill exists. */
    private fun watch() {
        if (!watching.compareAndSet(false, true)) return
        io.launch {
            try {
                var misses = HashMap<String, Int>()
                while (true) {
                    delay(4000)
                    val snapshot = withContext(Dispatchers.Main) { chromes.values.toList() }
                    if (snapshot.isEmpty()) break
                    if (!Monitor.screenOn) continue
                    val next = HashMap<String, Int>()
                    for (c in snapshot) {
                        val alive = if (c.taskId != null) Windows.taskId(c.pkg) != null else Windows.running(c.pkg)
                        if (!alive) {
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

    /** The control pill of one floating window. */
    private class WindowChrome(
        val ctx: Context, val pkg: String, val taskId: Int?, canMove: Boolean, val bounds: Rect
    ) {
        private val overlay = Overlay(ctx)
        private val d = ctx.resources.displayMetrics.density
        private val pillW = (206 * d).toInt()
        private val rowH = (40 * d).toInt()
        private val gap = (4 * d).toInt()
        private var lp: WindowManager.LayoutParams? = null
        private var before: Rect? = null
        private var px = 0
        private var py = 0

        private var movable by mutableStateOf(canMove && taskId != null)
        private var full by mutableStateOf(false)
        private var more by mutableStateOf(false)
        private var locked by mutableStateOf(false)
        private var muted by mutableStateOf(false)
        private var visible by mutableStateOf(false)

        @Volatile private var target = Rect(bounds)
        private val busy = AtomicBoolean(false)

        fun show() {
            val (sw, _) = screenSize(ctx)
            px = (sw - pillW) / 2
            py = (56 * d).toInt()
            val p = WindowManager.LayoutParams(
                pillW, WindowManager.LayoutParams.WRAP_CONTENT, WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL or
                    WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS or WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
                PixelFormat.TRANSLUCENT
            )
            p.gravity = Gravity.TOP or Gravity.START
            lp = p
            place()
            overlay.show(p, drag = DragSpec(longPress = false, onMove = { dx, dy -> drag(dx, dy) })) { Pill() }
            Handler(Looper.getMainLooper()).post { visible = true }
        }

        fun dismiss() {
            if (muted) io.launch { Windows.mute(pkg, false) }
            overlay.remove()
            closed(pkg)
        }

        /** Puts the pill centred above the window, or inside its top edge when there is no room above. */
        private fun place() {
            val p = lp ?: return
            val (sw, sh) = screenSize(ctx)
            val h = overlay.size().second.takeIf { it > 0 } ?: rowH
            if (movable) {
                px = bounds.centerX() - pillW / 2
                py = bounds.top - rowH - gap
                if (py < 0) py = bounds.top + gap
            }
            p.x = px.coerceIn(0, (sw - pillW).coerceAtLeast(0))
            p.y = py.coerceIn(0, (sh - h).coerceAtLeast(0))
            overlay.update(p)
        }

        private fun drag(dx: Int, dy: Int) {
            if (locked || full) return
            val (sw, sh) = screenSize(ctx)
            if (movable) {
                val w = bounds.width()
                val h = bounds.height()
                val left = (bounds.left + dx).coerceIn(0, (sw - w).coerceAtLeast(0))
                val top = (bounds.top + dy).coerceIn(rowH + gap, (sh - (60 * d).toInt()).coerceAtLeast(rowH + gap))
                bounds.set(left, top, left + w, top + h)
                sync()
            } else {
                px += dx
                py += dy
                place()
            }
        }

        private fun preset(wFrac: Float, hFrac: Float) {
            if (!movable) return
            val (sw, sh) = screenSize(ctx)
            val w = (sw * wFrac).toInt().coerceIn((200 * d).toInt(), sw)
            val h = (sh * hFrac).toInt().coerceIn((240 * d).toInt(), sh - rowH - gap)
            val left = (bounds.centerX() - w / 2).coerceIn(0, (sw - w).coerceAtLeast(0))
            val top = bounds.top.coerceIn(rowH + gap, (sh - h).coerceAtLeast(rowH + gap))
            full = false
            bounds.set(left, top, left + w, minOf(top + h, sh))
            sync()
        }

        private fun medium() {
            if (!movable) return
            full = false
            bounds.set(defaultBounds(ctx))
            sync()
        }

        /** Left or right half in landscape, top or bottom half in portrait. */
        private fun snap(first: Boolean) {
            if (!movable) return
            val (sw, sh) = screenSize(ctx)
            val top = rowH + gap + (24 * d).toInt()
            full = false
            if (sw > sh) {
                if (first) bounds.set(0, top, sw / 2, sh) else bounds.set(sw / 2, top, sw, sh)
            } else {
                val mid = (sh + top) / 2
                if (first) bounds.set(0, top, sw, mid) else bounds.set(0, mid, sw, sh)
            }
            sync()
        }

        private fun toggleFull() {
            if (!movable) {
                // The ROM does not let windows be resized, so open the app normally, which is full screen
                io.launch { Windows.open(ctx, pkg, false) }
                dismiss()
                return
            }
            val (sw, sh) = screenSize(ctx)
            if (!full) {
                before = Rect(bounds)
                bounds.set(0, rowH + gap, sw, sh)
                full = true
            } else {
                bounds.set(before ?: defaultBounds(ctx))
                full = false
            }
            more = false
            sync()
        }

        private fun toggleMute() {
            val want = !muted
            io.launch {
                val ok = Windows.mute(pkg, want)
                withContext(Dispatchers.Main) { if (ok) muted = want else toast(ctx, "Could not change the sound") }
            }
        }

        private fun shot() {
            more = false
            io.launch {
                delay(500)
                val f = "/sdcard/Pictures/Ashutool/ashu_${System.currentTimeMillis()}.png"
                val ok = Root.ok("mkdir -p /sdcard/Pictures/Ashutool", "screencap -p $f")
                withContext(Dispatchers.Main) { toast(ctx, if (ok) "Screenshot saved" else "Could not save") }
            }
        }

        private fun close() {
            dismiss()
            io.launch { Windows.forceStop(pkg) }
        }

        /** Moves the pill with the window and asks the system to move the window. */
        private fun sync() {
            place()
            target = Rect(bounds)
            val id = taskId ?: return
            if (!busy.compareAndSet(false, true)) return
            io.launch {
                var last: Rect? = null
                var failed = false
                try {
                    while (true) {
                        val t = target
                        if (last != null && last == t) break
                        if (!Windows.resize(id, t)) { failed = true; break }
                        last = t
                    }
                } finally {
                    busy.set(false)
                }
                if (failed) withContext(Dispatchers.Main) { movable = false; toast(ctx, "This ROM does not allow moving windows") }
                else if (last != target) sync()
            }
        }

        @Composable
        private fun Pill() {
            val land = remember { val (w, h) = screenSize(ctx); w > h }
            AnimatedVisibility(visible = visible, enter = fadeIn() + scaleIn(initialScale = 0.88f), exit = fadeOut()) {
                val shape = RoundedCornerShape(22.dp)
                Column(
                    Modifier.width(206.dp).clip(shape).background(Color(0xF2171230))
                        .border(1.dp, Color.White.copy(alpha = 0.22f), shape).padding(horizontal = 6.dp, vertical = 4.dp)
                ) {
                    Row(
                        Modifier.fillMaxWidth().height(32.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(start = 4.dp)) {
                            AppIcon(pkg, 24.dp)
                            if (locked) {
                                Spacer(Modifier.width(6.dp))
                                Icon(Icons.Outlined.Lock, null, tint = Color.White.copy(alpha = 0.8f), modifier = Modifier.size(14.dp))
                            }
                        }
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            PillButton(if (full) Icons.Outlined.FullscreenExit else Icons.Outlined.Fullscreen, false, Color.White) { toggleFull() }
                            PillButton(Icons.Outlined.MoreHoriz, more, Color.White) { more = !more; place() }
                            PillButton(Icons.Outlined.Close, false, Color(0xFFFF7A90)) { close() }
                        }
                    }
                    AnimatedVisibility(visible = more, enter = fadeIn() + expandVertically(), exit = fadeOut() + shrinkVertically()) {
                        Column(Modifier.padding(top = 4.dp, bottom = 2.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            if (movable) {
                                Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                                    PillChip("Mini", Icons.Outlined.PhotoSizeSelectSmall, false) {
                                        if (land) preset(0.30f, 0.50f) else preset(0.55f, 0.28f)
                                    }
                                    PillChip("Medium", Icons.Outlined.CropSquare, false) { medium() }
                                    PillChip("Large", Icons.Outlined.OpenInFull, false) {
                                        if (land) preset(0.62f, 0.86f) else preset(0.96f, 0.66f)
                                    }
                                }
                                Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                                    PillChip(if (land) "Left" else "Top", if (land) Icons.Outlined.West else Icons.Outlined.North, false) { snap(true) }
                                    PillChip(if (land) "Right" else "Bottom", if (land) Icons.Outlined.East else Icons.Outlined.South, false) { snap(false) }
                                    PillChip("Reset", Icons.Outlined.CenterFocusStrong, false) { medium() }
                                }
                            }
                            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                                PillChip("Sound", if (muted) Icons.Outlined.VolumeOff else Icons.Outlined.VolumeUp, muted) { toggleMute() }
                                PillChip("Lock", if (locked) Icons.Outlined.Lock else Icons.Outlined.LockOpen, locked) { locked = !locked }
                                PillChip("Shot", Icons.Outlined.PhotoCamera, false) { shot() }
                            }
                        }
                    }
                }
            }
        }

        @Composable
        private fun PillButton(icon: ImageVector, active: Boolean, tint: Color, onClick: () -> Unit) {
            Box(
                Modifier.size(32.dp).clip(CircleShape)
                    .background(if (active) Color.White.copy(alpha = 0.18f) else Color.Transparent)
                    .clickable { onClick() },
                contentAlignment = Alignment.Center
            ) {
                Icon(icon, null, tint = tint, modifier = Modifier.size(19.dp))
            }
        }

        @Composable
        private fun RowScope.PillChip(label: String, icon: ImageVector, on: Boolean, onClick: () -> Unit) {
            val shape = RoundedCornerShape(12.dp)
            Column(
                Modifier.weight(1f).clip(shape)
                    .background(if (on) Color.White.copy(alpha = 0.92f) else Color.White.copy(alpha = 0.10f))
                    .clickable { onClick() }.padding(vertical = 6.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Icon(icon, null, tint = if (on) Color(0xFF171230) else Color.White, modifier = Modifier.size(18.dp))
                Text(label, color = if (on) Color(0xFF171230) else Color.White, fontSize = 9.sp, fontWeight = FontWeight.Medium, maxLines = 1)
            }
        }
    }
}
