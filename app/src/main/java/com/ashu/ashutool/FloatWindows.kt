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
import androidx.compose.material.icons.outlined.Remove
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.core.tween
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
import androidx.compose.ui.graphics.TransformOrigin
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
        val side = LockStore.int(ctx, "g_float_side", 0).coerceIn(0, 2)
        val w = if (land) (sw * 0.30f).toInt() else (sw * 0.92f).toInt()
        val h = if (land) (sh * 0.72f).toInt() else (sh * 0.50f).toInt()
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
        val bounds = savedBounds(app, pkg) ?: defaultBounds(app)
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

    private fun boundsKey(ctx: Context, pkg: String): String {
        val (w, h) = screenSize(ctx)
        return "fw_${pkg}_${if (w > h) "L" else "P"}"
    }

    /** Remembers where this app's window was left, separately for portrait and landscape. */
    internal fun saveBounds(ctx: Context, pkg: String, r: Rect) {
        LockStore.setStr(ctx, boundsKey(ctx, pkg), "${r.left},${r.top},${r.right},${r.bottom}")
    }

    private fun savedBounds(ctx: Context, pkg: String): Rect? {
        val p = LockStore.str(ctx, boundsKey(ctx, pkg), "").split(",").mapNotNull { it.toIntOrNull() }
        if (p.size != 4) return null
        val (sw, sh) = screenSize(ctx)
        val r = Rect(p[0], p[1], p[2], p[3])
        val d = ctx.resources.displayMetrics.density
        val ok = r.width() >= (160 * d).toInt() && r.height() >= (200 * d).toInt() && r.left >= 0 && r.top >= (28 * d).toInt() &&
            r.right <= sw && r.bottom <= sh
        return if (ok) r else null
    }

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

    /**
     * The controls of one floating window, three small pieces around it:
     *  top    tiny red buttons: close, full screen, mini, more
     *  bottom a drag bar. Hold and move it to move the window
     *  corner a grip. Drag it to change the size
     * Everything fades in and out. Where the window ends up is remembered for next time.
     */
    private class WindowChrome(
        val ctx: Context, val pkg: String, val taskId: Int?, canMove: Boolean, val bounds: Rect
    ) {
        private val d = ctx.resources.displayMetrics.density
        private val topO = Overlay(ctx)
        private val botO = Overlay(ctx)
        private val gripO = Overlay(ctx)
        private var topLp: WindowManager.LayoutParams? = null
        private var botLp: WindowManager.LayoutParams? = null
        private var gripLp: WindowManager.LayoutParams? = null
        private val main = Handler(Looper.getMainLooper())
        private val dotH = (26 * d).toInt()
        private val stripH = (22 * d).toInt()
        private val gripW = (36 * d).toInt()
        private val gap = (3 * d).toInt()
        private val topW = (104 * d).toInt()
        private var freeX = 0
        private var freeY = 0
        private var before: Rect? = null

        private var movable by mutableStateOf(canMove && taskId != null)
        private var full by mutableStateOf(false)
        private var more by mutableStateOf(false)
        private var locked by mutableStateOf(false)
        private var muted by mutableStateOf(false)
        private var visible by mutableStateOf(false)

        @Volatile private var target = Rect(bounds)
        private val busy = AtomicBoolean(false)

        private fun params(w: Int, h: Int): WindowManager.LayoutParams {
            val p = WindowManager.LayoutParams(
                w, h, WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL or
                    WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS or WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
                PixelFormat.TRANSLUCENT
            )
            p.gravity = Gravity.TOP or Gravity.START
            return p
        }

        fun show() {
            val (sw, _) = screenSize(ctx)
            freeX = (sw - topW) / 2
            freeY = (56 * d).toInt()
            val t = params(WindowManager.LayoutParams.WRAP_CONTENT, WindowManager.LayoutParams.WRAP_CONTENT)
            topLp = t
            topO.show(t, drag = DragSpec(longPress = false, onMove = { dx, dy -> drag(dx, dy) }, onEnd = { save() })) { Top() }
            if (movable) showBottom()
            place()
            main.post { visible = true }
        }

        private fun showBottom() {
            val b = params((bounds.width() - gripW).coerceAtLeast((60 * d).toInt()), stripH)
            botLp = b
            botO.show(b, drag = DragSpec(longPress = false, onMove = { dx, dy -> drag(dx, dy) }, onEnd = { save() })) { Bottom() }
            val g = params(gripW, stripH)
            gripLp = g
            gripO.show(g, drag = DragSpec(longPress = false, onMove = { dx, dy -> resizeDrag(dx, dy) }, onEnd = { save() })) { Grip() }
        }

        fun dismiss() {
            if (muted) { muted = false; io.launch { Windows.mute(pkg, false) } }
            visible = false
            closed(pkg)
            main.postDelayed({ topO.remove(); botO.remove(); gripO.remove() }, 220)
        }

        private fun save() { if (movable && !full) saveBounds(ctx, pkg, bounds) }

        /** Puts the three pieces around the window, or in free space if the window cannot be moved. */
        private fun place() {
            val (sw, sh) = screenSize(ctx)
            val t = topLp ?: return
            val maxX = (sw - topW).coerceAtLeast(0)
            if (movable) {
                var y = bounds.top - dotH - gap
                if (y < 0) y = bounds.top + gap
                t.x = (bounds.left + gap * 2).coerceIn(0, maxX)
                t.y = y.coerceIn(0, (sh - dotH).coerceAtLeast(0))
            } else {
                t.x = freeX.coerceIn(0, maxX)
                t.y = freeY.coerceIn(0, (sh - dotH).coerceAtLeast(0))
            }
            topO.update(t)
            val b = botLp
            val g = gripLp
            if (movable && !full && b != null && g != null) {
                val y = if (bounds.bottom + gap + stripH <= sh) bounds.bottom + gap else sh - stripH - gap
                b.x = bounds.left.coerceAtLeast(0)
                b.y = y
                b.width = (bounds.width() - gripW).coerceAtLeast((60 * d).toInt())
                g.x = (bounds.right - gripW).coerceIn(0, (sw - gripW).coerceAtLeast(0))
                g.y = y
                botO.update(b)
                gripO.update(g)
            }
        }

        /** Moves the window with the finger, or only the buttons if the window cannot be moved. */
        private fun drag(dx: Int, dy: Int) {
            if (locked) return
            val (sw, sh) = screenSize(ctx)
            if (movable && !full) {
                val w = bounds.width()
                val h = bounds.height()
                val left = (bounds.left + dx).coerceIn(0, (sw - w).coerceAtLeast(0))
                val top = (bounds.top + dy).coerceIn(dotH + gap, (sh - (60 * d).toInt()).coerceAtLeast(dotH + gap))
                bounds.set(left, top, left + w, top + h)
                sync()
            } else if (!movable) {
                freeX += dx
                freeY += dy
                place()
            }
        }

        private fun resizeDrag(dx: Int, dy: Int) {
            if (!movable || full || locked) return
            val (sw, sh) = screenSize(ctx)
            val r = (bounds.right + dx).coerceIn(bounds.left + (200 * d).toInt(), sw)
            val b = (bounds.bottom + dy).coerceIn(bounds.top + (240 * d).toInt(), sh)
            bounds.set(bounds.left, bounds.top, r, b)
            sync()
        }

        /** Moves the pieces right away and asks the system to move the window. Only the latest place is sent. */
        private fun sync() {
            place()
            val id = taskId ?: return
            target = Rect(bounds)
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
                if (failed) withContext(Dispatchers.Main) { movable = false; toast(ctx, "This ROM stopped moving the window") }
                else if (last != target) sync()
            }
        }

        private fun toggleFull() {
            if (!movable) {
                io.launch { Windows.open(ctx, pkg, false) }
                dismiss()
                return
            }
            val (sw, sh) = screenSize(ctx)
            if (!full) {
                before = Rect(bounds)
                bounds.set(0, dotH + gap, sw, sh)
                full = true
                botO.remove()
                gripO.remove()
            } else {
                bounds.set(before ?: defaultBounds(ctx))
                full = false
                showBottom()
            }
            sync()
        }

        private fun close() {
            dismiss()
            io.launch { Windows.forceStop(pkg) }
        }

        /** Size as a share of the screen, kept around the same middle. */
        private fun preset(wFrac: Float, hFrac: Float) {
            if (!movable) return
            val (sw, sh) = screenSize(ctx)
            val w = (sw * wFrac).toInt().coerceIn((200 * d).toInt(), sw)
            val h = (sh * hFrac).toInt().coerceIn((240 * d).toInt(), sh - dotH - gap)
            val left = (bounds.centerX() - w / 2).coerceIn(0, (sw - w).coerceAtLeast(0))
            val top = bounds.top.coerceIn(dotH + gap, (sh - h).coerceAtLeast(dotH + gap))
            if (full) { full = false; showBottom() }
            bounds.set(left, top, left + w, minOf(top + h, sh))
            sync()
            save()
        }

        private fun mini() {
            val (sw, sh) = screenSize(ctx)
            if (sw > sh) preset(0.22f, 0.50f) else preset(0.55f, 0.28f)
        }

        private fun snap(first: Boolean) {
            if (!movable) return
            val (sw, sh) = screenSize(ctx)
            val top = dotH + gap + (22 * d).toInt()
            if (full) { full = false; showBottom() }
            if (sw > sh) { if (first) bounds.set(0, top, sw / 2, sh - stripH) else bounds.set(sw / 2, top, sw, sh - stripH) }
            else {
                val mid = (sh + top) / 2
                if (first) bounds.set(0, top, sw, mid - stripH) else bounds.set(0, mid + dotH, sw, sh - stripH)
            }
            sync()
            save()
        }

        private fun reset() {
            if (!movable) return
            if (full) { full = false; showBottom() }
            bounds.set(defaultBounds(ctx))
            sync()
            save()
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

        // ---------------------------------------------------------------- pieces

        @Composable
        private fun Top() {
            AnimatedVisibility(
                visible = visible,
                enter = fadeIn(tween(180)) + scaleIn(tween(200), initialScale = 0.7f, transformOrigin = TransformOrigin(0f, 1f)),
                exit = fadeOut(tween(140))
            ) {
                val shape = RoundedCornerShape(15.dp)
                Column(
                    Modifier.clip(shape).background(Color(0xE61C1114)).border(1.dp, Color(0x66FF6B6B), shape)
                        .padding(horizontal = 5.dp, vertical = 2.dp)
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(2.dp)) {
                        Dot(Color(0xFFFF4D4D), Icons.Outlined.Close) { close() }
                        Dot(Color(0xFFFF6B6B), if (full) Icons.Outlined.FullscreenExit else Icons.Outlined.Fullscreen) { toggleFull() }
                        if (movable) Dot(Color(0xFFE0526A), Icons.Outlined.Remove) { mini() }
                        Dot(Color(0xFFFF8FA3), Icons.Outlined.MoreHoriz) { more = !more }
                        if (locked) Icon(Icons.Outlined.Lock, null, tint = Color(0xFFFFB4B4), modifier = Modifier.padding(start = 4.dp).size(11.dp))
                    }
                    AnimatedVisibility(visible = more, enter = fadeIn() + expandVertically(), exit = fadeOut() + shrinkVertically()) {
                        MoreGrid()
                    }
                }
            }
        }

        /** A small round button. The touch area is a little bigger than what you see. */
        @Composable
        private fun Dot(color: Color, icon: ImageVector, onClick: () -> Unit) {
            Box(Modifier.size(25.dp).clip(CircleShape).clickable { onClick() }, contentAlignment = Alignment.Center) {
                Box(Modifier.size(15.dp).clip(CircleShape).background(color), contentAlignment = Alignment.Center) {
                    Icon(icon, null, tint = Color.White, modifier = Modifier.size(10.dp))
                }
            }
        }

        @Composable
        private fun MoreGrid() {
            val land = remember(more) { val (w, h) = screenSize(ctx); w > h }
            Column(Modifier.width(186.dp).padding(top = 4.dp, bottom = 3.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                if (movable) {
                    Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                        Chip3("Mini", Icons.Outlined.PhotoSizeSelectSmall, false) { mini() }
                        Chip3("Medium", Icons.Outlined.CropSquare, false) { reset() }
                        Chip3("Large", Icons.Outlined.OpenInFull, false) { if (land) preset(0.62f, 0.86f) else preset(0.96f, 0.66f) }
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                        Chip3(if (land) "Left" else "Top", if (land) Icons.Outlined.West else Icons.Outlined.North, false) { snap(true) }
                        Chip3(if (land) "Right" else "Bottom", if (land) Icons.Outlined.East else Icons.Outlined.South, false) { snap(false) }
                        Chip3("Center", Icons.Outlined.CenterFocusStrong, false) { reset() }
                    }
                }
                Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    Chip3("Sound", if (muted) Icons.Outlined.VolumeOff else Icons.Outlined.VolumeUp, muted) { toggleMute() }
                    Chip3("Lock", if (locked) Icons.Outlined.Lock else Icons.Outlined.LockOpen, locked) { locked = !locked }
                    Chip3("Shot", Icons.Outlined.PhotoCamera, false) { shot() }
                }
            }
        }

        @Composable
        private fun RowScope.Chip3(label: String, icon: ImageVector, on: Boolean, onClick: () -> Unit) {
            val shape = RoundedCornerShape(10.dp)
            Column(
                Modifier.weight(1f).clip(shape).background(if (on) Color(0xFFFF8FA3) else Color.White.copy(alpha = 0.10f))
                    .clickable { onClick() }.padding(vertical = 5.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Icon(icon, null, tint = Color.White, modifier = Modifier.size(15.dp))
                Text(label, color = Color.White, fontSize = 8.sp, fontWeight = FontWeight.Medium, maxLines = 1)
            }
        }

        @Composable
        private fun Bottom() {
            AnimatedVisibility(
                visible = visible,
                modifier = Modifier.fillMaxSize(),
                enter = fadeIn(tween(200)) + slideInVertically(tween(220)) { -it },
                exit = fadeOut(tween(140))
            ) {
                Box(
                    Modifier.fillMaxSize().clip(RoundedCornerShape(bottomStart = 12.dp, bottomEnd = 3.dp)).background(Color(0xCC1C1114)),
                    contentAlignment = Alignment.Center
                ) {
                    Box(Modifier.width(44.dp).height(4.dp).clip(CircleShape).background(Color.White.copy(alpha = if (locked) 0.25f else 0.75f)))
                }
            }
        }

        @Composable
        private fun Grip() {
            AnimatedVisibility(
                visible = visible,
                modifier = Modifier.fillMaxSize(),
                enter = fadeIn(tween(200)) + scaleIn(tween(220), initialScale = 0.6f),
                exit = fadeOut(tween(140))
            ) {
                Box(
                    Modifier.fillMaxSize().clip(RoundedCornerShape(bottomEnd = 12.dp, bottomStart = 3.dp)).background(Color(0xCCE0526A)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(Icons.Outlined.OpenInFull, null, tint = Color.White, modifier = Modifier.size(13.dp))
                }
            }
        }
    }
}
