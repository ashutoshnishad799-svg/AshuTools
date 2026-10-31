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
import android.service.quicksettings.Tile
import android.service.quicksettings.TileService
import android.view.Gravity
import android.view.WindowManager
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.CenterFocusStrong
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.CropSquare
import androidx.compose.material.icons.outlined.East
import androidx.compose.material.icons.outlined.Fullscreen
import androidx.compose.material.icons.outlined.FullscreenExit
import androidx.compose.material.icons.outlined.Layers
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material.icons.outlined.LockOpen
import androidx.compose.material.icons.outlined.MoreHoriz
import androidx.compose.material.icons.outlined.North
import androidx.compose.material.icons.outlined.OpenInFull
import androidx.compose.material.icons.outlined.PhotoCamera
import androidx.compose.material.icons.outlined.PhotoSizeSelectSmall
import androidx.compose.material.icons.outlined.Remove
import androidx.compose.material.icons.outlined.South
import androidx.compose.material.icons.outlined.VolumeOff
import androidx.compose.material.icons.outlined.VolumeUp
import androidx.compose.material.icons.outlined.West
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.CoroutineExceptionHandler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Floating app windows: open an app in a window, move it, resize it, make it full screen, shrink it to a bubble,
 * close it. The system draws the window. Ashutool adds the controls around it and asks the system to move it.
 *
 * Why it stays steady:
 *  - Every command to the system goes through one gate, one at a time, so opening, moving and closing never overlap.
 *  - Only a window the system made floating is ever moved, and home and recents are never touched.
 *  - Each window has a state (open, bubble, closing) and every action checks it first.
 *  - Resizing shows an outline and sends one command when you let go, because every size change makes apps rebuild.
 *  - An error inside this code is caught and written down, it never closes the app.
 *  - The screen is locked while a window is open and your settings are put back afterwards, see RotationGuard.
 */
object FloatWindows {
    private val io = CoroutineScope(
        SupervisorJob() + Dispatchers.IO + CoroutineExceptionHandler { _, e ->
            GameDiag.error = "Windows: ${e.javaClass.simpleName}: ${e.message ?: ""}".take(160)
        }
    )
    private val gate = Mutex()
    private val chromes = ConcurrentHashMap<String, WindowChrome>()
    private val watching = AtomicBoolean(false)

    /** Apps being opened right now. They count as having a window from the first moment, before the bar exists. */
    private val pending: MutableSet<String> = java.util.concurrent.ConcurrentHashMap.newKeySet<String>()

    /** A short note for the user about the last open, empty when everything worked. */
    @Volatile var note: String = ""

    /** Runs a system command. Only one runs at a time, in the order they were asked. */
    private suspend fun <T> sys(block: () -> T): T = gate.withLock { withContext(Dispatchers.IO) { block() } }

    private val POPULAR = listOf(
        "com.whatsapp", "org.telegram.messenger", "com.instagram.android", "com.google.android.youtube", "com.android.chrome",
        "com.google.android.apps.messaging", "com.google.android.apps.photos", "com.google.android.calculator",
        "com.android.calculator2", "com.google.android.dialer", "com.google.android.apps.maps", "com.android.vending"
    )

    /** Apps offered for floating windows: the ones you chose, or well known apps that are installed. */
    fun apps(ctx: Context): List<String> {
        val pm = ctx.packageManager
        fun ok(p: String) = Safety.validPkg(p) && pm.getLaunchIntentForPackage(p) != null
        val chosen = LockStore.set(ctx, "g_shelf").filter { ok(it) }
        return (if (chosen.isNotEmpty()) chosen else POPULAR.filter { ok(it) }).take(12)
    }

    /** Size and place of a new window, from the side you chose. Room is kept above it for the buttons. */
    private fun defaultBounds(ctx: Context): Rect {
        val (sw, sh) = screenSize(ctx)
        val d = ctx.resources.displayMetrics.density
        val margin = (10 * d).toInt()
        val land = sw > sh
        val side = LockStore.int(ctx, "g_float_side", 1).coerceIn(0, 2)
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

    internal fun closed(pkg: String) { chromes.remove(pkg) }

    /** True while this app has one of our floating windows, or is being opened. Game space does not end because of it. */
    fun hasWindow(pkg: String): Boolean = pending.contains(pkg) || chromes.containsKey(pkg)

    /** How many windows are open. Call on the main thread. */
    fun count(): Int = chromes.size

    /** Removes all bars, gives the screen rotation back and puts the home screen in full screen. */
    suspend fun reset(ctx: Context) {
        withContext(Dispatchers.Main) { for (c in chromes.values.toList()) c.dismiss() }
        sys { RotationGuard.restore(ctx.applicationContext); Windows.resetHome() }
    }

    /** Closes every window and stops the apps in them. */
    suspend fun closeAll(ctx: Context) {
        withContext(Dispatchers.Main) { for (c in chromes.values.toList()) c.closeNow() }
    }

    /** Opens [pkg] floating. Returns false only if the app could not be started at all. */
    suspend fun open(ctx: Context, pkg: String): Boolean {
        if (!Safety.validPkg(pkg)) return false
        // A second tap while it is still opening does nothing
        if (!pending.add(pkg)) return true
        val app = ctx.applicationContext
        // True while this call holds the screen rotation lock and no window bar has taken it over yet
        var held = false
        try {
            // A window that is still there comes to the front. A bar whose app was closed from recents is cleared
            // and the window is made again, otherwise the app would never open in a window a second time.
            val old = withContext(Dispatchers.Main) { chromes[pkg] }
            if (old != null) {
                val alive = sys { Windows.task(pkg) } != null
                if (alive) { withContext(Dispatchers.Main) { old.bringBack() }; return true }
                withContext(Dispatchers.Main) { old.dismiss() }
            }
            // Never open a window of the launcher or of Ashutool itself
            if (pkg in Safety.homePkgs(app) || pkg == app.packageName) { note = "This app cannot open in a window"; return false }
            // The screen is only locked inside a game. A portrait app over a landscape game is what made it turn back
            // and forth. Anywhere else the rotation is left alone, because every change of it restarts the system UI layout.
            val lockRot = Fg.game != null || LockStore.bool(app, "fw_lock_rot", false)
            if (lockRot) { sys { RotationGuard.hold(app) }; held = true }
            val uiBefore = sys { Windows.systemUiPid() }
            val result = try {
                openNow(app, pkg, lockRot)
            } catch (e: kotlinx.coroutines.CancellationException) {
                throw e
            } catch (e: Exception) {
                note = "Window controls failed: ${e.message}"
                1
            }
            // 2 means a bar was made and it will release the lock later
            if (result == 2) held = false
            // A few seconds later, check that the system UI is still the same process. If it crashed, keep the log.
            io.launch { delay(3000); sys { Windows.watchSystemUi(app, uiBefore, "opening $pkg") } }
            return result != 0
        } finally {
            if (held) withContext(NonCancellable) { sys { RotationGuard.release(app) } }
            // The extra time covers the system's own window events after the start. It is not waited for here.
            io.launch { delay(1500); pending.remove(pkg) }
        }
    }

    /** Waits for the window of [pkg] to exist. Stops early once it is floating, or once a not floating one has stayed the same. */
    private suspend fun waitTask(pkg: String, max: Int): Windows.TaskRef? {
        var found: Windows.TaskRef? = null
        var tries = 0
        while (tries < max) {
            delay(250)
            found = sys { Windows.task(pkg) }
            val f = found
            if (f != null && (f.floating || tries >= 3)) break
            tries++
        }
        return found
    }

    /** 0 the app did not start, 1 it started without controls, 2 it started and the controls are up. */
    private suspend fun openNow(app: Context, pkg: String, rot: Boolean): Int {
        note = ""
        // With the system switches off the app would open full screen and ignore the window. Turn them on first.
        if (sys { Windows.ensureFreeform() }) delay(600)
        if (!sys { Windows.open(app, pkg, true) }) return 0
        // Wait for the window to exist, and only ever move it if the system made it a floating window
        var found = waitTask(pkg, 10)
        if (found != null && !found.floating) {
            // Some ROMs only honour the windowing mode on the second try, once the switches have settled
            sys { Windows.open(app, pkg, true) }
            found = waitTask(pkg, 8)
        }
        val ref = found
        val id = ref?.id
        val bounds = savedBounds(app, pkg) ?: defaultBounds(app)
        var movable = false
        if (ref != null && ref.floating) {
            movable = sys { Windows.resize(ref.id, bounds) }
            if (movable) {
                // Some apps put their window back where it was a moment after starting, so place it once more
                delay(900)
                sys { Windows.resize(ref.id, bounds) }
            } else note = "Opened. This ROM does not allow moving windows (${Windows.lastDiag})"
        } else if (ref != null) {
            val feature = sys { Windows.featureText() }
            note = "Opened full screen. This ROM did not make a floating window (feature: ${feature.ifEmpty { "unknown" }}). " +
                "Restart the phone once if freeform was just turned on."
        } else {
            note = "Opened. Could not find the window to move (${Windows.lastDiag})"
        }
        withContext(Dispatchers.Main) {
            val c = WindowChrome(app, pkg, id, movable, bounds, rot)
            chromes[pkg]?.dismiss()
            chromes[pkg] = c
            c.show()
        }
        watch()
        return 2
    }

    /** Removes bars whose window is gone, and keeps the window number up to date. Only runs while a bar exists. */
    private fun watch() {
        if (!watching.compareAndSet(false, true)) return
        io.launch {
            try {
                var misses = HashMap<String, Int>()
                while (true) {
                    delay(6000)
                    val snapshot = withContext(Dispatchers.Main) { chromes.values.toList() }
                    if (snapshot.isEmpty()) break
                    if (!Monitor.screenOn) continue
                    val next = HashMap<String, Int>()
                    for (c in snapshot) {
                        val ref = if (c.hasTask) sys { Windows.task(c.pkg) } else null
                        val alive = if (c.hasTask) ref != null else sys { Windows.running(c.pkg) }
                        if (alive) {
                            if (ref != null) withContext(Dispatchers.Main) { c.retarget(ref) }
                        } else {
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
     * The controls of one floating window, small pieces around it:
     *  top    tiny red buttons: close, bubble, full screen, more
     *  bottom a drag bar, hold and move it to move the window
     *  corner a grip, drag it to change the size. An outline shows the new size and the window changes when you let go
     *  bubble when the window is minimised, a small round button on the screen edge
     * Everything fades and scales in and out. Where the window ends up is remembered for next time.
     */
    private class WindowChrome(
        val ctx: Context, val pkg: String, id: Int?, canMove: Boolean, val bounds: Rect,
        /** True when this window made the screen rotation lock, so it must give it back. */
        val rot: Boolean
    ) {
        enum class St { ACTIVE, BUBBLE, CLOSING }

        private val d = ctx.resources.displayMetrics.density
        private val topO = Overlay(ctx)
        private val botO = Overlay(ctx)
        private val gripO = Overlay(ctx)
        private val ghostO = Overlay(ctx)
        private val bubbleO = Overlay(ctx)
        private val zoneO = Overlay(ctx)
        private var topLp: WindowManager.LayoutParams? = null
        private var botLp: WindowManager.LayoutParams? = null
        private var gripLp: WindowManager.LayoutParams? = null
        private var ghostLp: WindowManager.LayoutParams? = null
        private var bubbleLp: WindowManager.LayoutParams? = null
        private val main = Handler(Looper.getMainLooper())
        private var cleanup: Runnable? = null
        private val dotH = (26 * d).toInt()
        private val stripH = (22 * d).toInt()
        private val gripW = (36 * d).toInt()
        private val gap = (3 * d).toInt()
        private val topW = (104 * d).toInt()
        private val bubbleSize = (50 * d).toInt()
        private var freeX = 0
        private var freeY = 0
        private var before: Rect? = null

        @Volatile var taskId: Int? = id
        val hasTask: Boolean get() = taskId != null
        @Volatile private var state = St.ACTIVE

        private var movable by mutableStateOf(canMove && id != null)
        private var full by mutableStateOf(false)
        private var more by mutableStateOf(false)
        private var locked by mutableStateOf(false)
        private var muted by mutableStateOf(false)
        private var visible by mutableStateOf(false)
        private var zoneHot by mutableStateOf(false)

        @Volatile private var target = Rect(bounds)
        private val busy = AtomicBoolean(false)
        private var released = false

        private fun params(w: Int, h: Int, touchable: Boolean = true): WindowManager.LayoutParams {
            var flags = WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL or
                WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS or WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN
            if (!touchable) flags = flags or WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE
            val p = WindowManager.LayoutParams(w, h, WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY, flags, PixelFormat.TRANSLUCENT)
            p.gravity = Gravity.TOP or Gravity.START
            return p
        }

        /** Highest place the window may have. Below the status bar in portrait, with room for the buttons above it. */
        private fun topMin(): Int {
            val (w, h) = screenSize(ctx)
            val bar = if (w > h) 0 else statusBarHeight(ctx)
            return bar + dotH + gap
        }

        /** Room taken by the navigation buttons at the bottom. Zero with gesture navigation. */
        private fun navInset(): Int {
            val (w, h) = screenSize(ctx)
            val mode = try { android.provider.Settings.Secure.getInt(ctx.contentResolver, "navigation_mode", 2) } catch (e: Exception) { 2 }
            if (mode == 2) return 0
            val id = ctx.resources.getIdentifier(if (w > h) "navigation_bar_height_landscape" else "navigation_bar_height", "dimen", "android")
            return if (id > 0) ctx.resources.getDimensionPixelSize(id) else 0
        }

        /** Dragging by the top buttons or the bottom bar moves the window and shows the drop to close target. */
        private fun moveSpec() = DragSpec(
            longPress = false,
            onStart = { focus(); zoneShow() },
            onMove = { dx, dy -> drag(dx, dy) },
            onEnd = { dragEnd() }
        )

        // ---------------------------------------------------------------- life of the window

        fun show() {
            val (sw, _) = screenSize(ctx)
            freeX = (sw - topW) / 2
            freeY = (56 * d).toInt()
            showPieces()
            place()
            main.post { visible = true }
        }

        /** The top buttons, and the bottom bar and grip when the window can be moved. */
        private fun showPieces() {
            val t = params(WindowManager.LayoutParams.WRAP_CONTENT, WindowManager.LayoutParams.WRAP_CONTENT)
            topLp = t
            topO.show(t, drag = moveSpec()) { Top() }
            if (movable && !full) showBottom()
        }

        private fun showBottom() {
            val b = params((bounds.width() - gripW).coerceAtLeast((60 * d).toInt()), stripH)
            botLp = b
            botO.show(b, drag = moveSpec()) { Bottom() }
            val g = params(gripW, stripH)
            gripLp = g
            gripO.show(
                g,
                drag = DragSpec(longPress = false, onStart = { focus() }, onMove = { dx, dy -> resizeDrag(dx, dy) }, onEnd = { resizeEnd() })
            ) { Grip() }
        }

        private fun removePieces() {
            topO.remove(); botO.remove(); gripO.remove(); ghostO.remove(); zoneO.remove()
        }

        /** Removes every piece, with a short fade. Safe to call more than once. */
        fun dismiss() {
            if (state == St.CLOSING) return
            state = St.CLOSING
            if (muted) { muted = false; io.launch { sys { Windows.mute(pkg, false) } } }
            visible = false
            closed(pkg)
            cleanup?.let { main.removeCallbacks(it) }
            main.postDelayed({ removePieces(); bubbleO.remove() }, 220)
            zoneHot = false
            if (!released) { released = true; if (rot) io.launch { sys { RotationGuard.release(ctx) } } }
        }

        private fun close() {
            if (state == St.CLOSING) return
            dismiss()
            io.launch { sys { Windows.forceStop(pkg) } }
        }

        /** Same as the close button, for Close all. */
        fun closeNow() = close()

        /** Called when the app is opened again: a bubble comes back, a window comes to the front. */
        fun bringBack() {
            if (state == St.BUBBLE) restore() else focus()
        }

        /** The window number can change when an app restarts. The latest one is kept. */
        fun retarget(ref: Windows.TaskRef) {
            taskId = ref.id
            if (!ref.floating && movable) movable = false
        }

        private fun focus() {
            val id = taskId ?: return
            if (state != St.ACTIVE) return
            io.launch { sys { Windows.focus(id) } }
        }

        // ---------------------------------------------------------------- places

        private fun save() { if (movable && !full && state == St.ACTIVE) saveBounds(ctx, pkg, bounds) }

        /** Puts the pieces around the window, or in free space if the window cannot be moved. */
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
            if (locked || state != St.ACTIVE) return
            val (sw, sh) = screenSize(ctx)
            if (movable && !full) {
                val w = bounds.width()
                val h = bounds.height()
                val left = (bounds.left + dx).coerceIn(0, (sw - w).coerceAtLeast(0))
                val top = (bounds.top + dy).coerceIn(topMin(), (sh - (60 * d).toInt()).coerceAtLeast(topMin()))
                bounds.set(left, top, left + w, top + h)
                zoneHot = zoneOn() && inZone()
                sync()
            } else if (!movable) {
                freeX += dx
                freeY += dy
                place()
            }
        }

        /** Changing the size: only an outline follows the finger. The window changes once, when you let go. */
        private fun resizeDrag(dx: Int, dy: Int) {
            if (!movable || full || locked || state != St.ACTIVE) return
            val (sw, sh) = screenSize(ctx)
            val r = (bounds.right + dx).coerceIn(bounds.left + (200 * d).toInt(), sw)
            val b = (bounds.bottom + dy).coerceIn(bounds.top + (240 * d).toInt(), sh)
            bounds.set(bounds.left, bounds.top, r, b)
            showGhost()
            place()
        }

        private fun resizeEnd() {
            ghostO.remove()
            if (state != St.ACTIVE) return
            sync()
            save()
        }

        private fun showGhost() {
            if (!ghostO.isShown) {
                val g = params(bounds.width(), bounds.height(), touchable = false)
                ghostLp = g
                ghostO.show(g) { Ghost() }
            }
            ghostLp?.let {
                it.x = bounds.left
                it.y = bounds.top
                it.width = bounds.width()
                it.height = bounds.height()
                ghostO.update(it)
            }
        }

        /** Moves the pieces right away and asks the system to move the window. Only the latest place is sent. */
        private fun sync() {
            place()
            if (taskId == null || !movable || state != St.ACTIVE) return
            target = Rect(bounds)
            if (!busy.compareAndSet(false, true)) return
            io.launch {
                var last: Rect? = null
                var failed = false
                try {
                    while (state == St.ACTIVE) {
                        val t = target
                        if (last != null && last == t) break
                        val tid = taskId ?: break
                        if (!sys { Windows.resize(tid, t) }) { failed = true; break }
                        // A change of size makes many apps rebuild their screen, so those are sent less often than moves
                        val resized = last == null || last.width() != t.width() || last.height() != t.height()
                        last = t
                        delay(if (resized) 260L else 60L)
                    }
                } finally {
                    busy.set(false)
                }
                if (failed) withContext(Dispatchers.Main) { movable = false; toast(ctx, "This ROM stopped moving the window") }
                // A newer place arrived while the last command was running
                else if (last != target && state == St.ACTIVE) main.post { sync() }
            }
        }

        // ---------------------------------------------------------------- drop to close

        private fun zoneOn(): Boolean = movable && !full && !locked && state == St.ACTIVE

        /** The window is pulled down over the round close target at the bottom middle of the screen. */
        private fun inZone(): Boolean {
            val (sw, sh) = screenSize(ctx)
            return Math.abs(bounds.centerX() - sw / 2) < (140 * d).toInt() && bounds.top >= sh - (210 * d).toInt()
        }

        /** Shows the round close target while a window is being moved. */
        private fun zoneShow() {
            if (!zoneOn() || zoneO.isShown) return
            val z = WindowManager.LayoutParams(
                (220 * d).toInt(), (130 * d).toInt(), WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE or
                    WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS or WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
                PixelFormat.TRANSLUCENT
            )
            z.gravity = Gravity.BOTTOM or Gravity.CENTER_HORIZONTAL
            z.y = (28 * d).toInt()
            zoneHot = false
            zoneO.show(z) { Zone() }
        }

        /** The finger was lifted. Over the target the window closes, anywhere else its place is kept. */
        private fun dragEnd() {
            val hot = zoneHot && zoneOn()
            zoneHot = false
            zoneO.remove()
            if (hot) { close(); return }
            save()
        }

        // ---------------------------------------------------------------- actions

        private fun toggleFull() {
            if (state != St.ACTIVE) return
            if (!movable) {
                io.launch { sys { Windows.open(ctx, pkg, false) } }
                dismiss()
                return
            }
            val (sw, sh) = screenSize(ctx)
            if (!full) {
                before = Rect(bounds)
                // Below the status bar, so the buttons never end up inside it, and above the navigation buttons
                bounds.set(0, topMin(), sw, (sh - navInset()).coerceAtLeast(topMin() + (240 * d).toInt()))
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

        /** Shrinks the window to a round button on the edge of the screen. Tap it to bring the window back. */
        private fun minimize() {
            if (!movable || state != St.ACTIVE) return
            val id = taskId ?: return
            val (sw, sh) = screenSize(ctx)
            if (full) { full = false; bounds.set(before ?: defaultBounds(ctx)) }
            state = St.BUBBLE
            more = false
            visible = false
            val keep = Rect(bounds)
            val left = sw + (24 * d).toInt()
            val off = Rect(left, keep.top, left + keep.width(), keep.bottom)
            val run = Runnable { removePieces() }
            cleanup = run
            main.postDelayed(run, 200)
            showBubble(keep, sw, sh)
            io.launch { sys { Windows.resize(id, off) } }
        }

        private fun showBubble(keep: Rect, sw: Int, sh: Int) {
            val b = params(bubbleSize, bubbleSize)
            val right = keep.centerX() > sw / 2
            b.x = if (right) sw - bubbleSize - gap else gap
            b.y = (keep.centerY() - bubbleSize / 2).coerceIn(statusBarHeight(ctx) + gap, (sh - bubbleSize - gap).coerceAtLeast(0))
            bubbleLp = b
            bubbleO.show(
                b,
                drag = DragSpec(longPress = false, onMove = { dx, dy -> bubbleMove(dx, dy) }, onEnd = { bubbleSnap() })
            ) { Bubble() }
        }

        private fun bubbleMove(dx: Int, dy: Int) {
            val b = bubbleLp ?: return
            val (sw, sh) = screenSize(ctx)
            b.x = (b.x + dx).coerceIn(0, (sw - bubbleSize).coerceAtLeast(0))
            b.y = (b.y + dy).coerceIn(0, (sh - bubbleSize).coerceAtLeast(0))
            bubbleO.update(b)
        }

        /** After moving the bubble it sticks to the nearest side edge. */
        private fun bubbleSnap() {
            val b = bubbleLp ?: return
            val (sw, _) = screenSize(ctx)
            b.x = if (b.x + bubbleSize / 2 > sw / 2) sw - bubbleSize - gap else gap
            bubbleO.update(b)
        }

        fun restore() {
            if (state != St.BUBBLE) return
            state = St.ACTIVE
            cleanup?.let { main.removeCallbacks(it) }
            cleanup = null
            bubbleO.remove()
            visible = false
            showPieces()
            place()
            main.post { visible = true }
            val id = taskId
            val r = Rect(bounds)
            if (id != null) io.launch { sys { Windows.resize(id, r) }; sys { Windows.focus(id) } }
        }

        /** Size as a share of the screen, kept around the same middle. */
        private fun preset(wFrac: Float, hFrac: Float) {
            if (!movable || state != St.ACTIVE) return
            val (sw, sh) = screenSize(ctx)
            val w = (sw * wFrac).toInt().coerceIn((200 * d).toInt(), sw)
            val h = (sh * hFrac).toInt().coerceIn((240 * d).toInt(), (sh - topMin()).coerceAtLeast((240 * d).toInt()))
            val left = (bounds.centerX() - w / 2).coerceIn(0, (sw - w).coerceAtLeast(0))
            val top = bounds.top.coerceIn(topMin(), (sh - h).coerceAtLeast(topMin()))
            if (full) { full = false; showBottom() }
            bounds.set(left, top, left + w, minOf(top + h, sh))
            sync()
            save()
        }

        private fun small() {
            val (sw, sh) = screenSize(ctx)
            if (sw > sh) preset(0.22f, 0.50f) else preset(0.55f, 0.28f)
        }

        private fun snap(first: Boolean) {
            if (!movable || state != St.ACTIVE) return
            val (sw, sh) = screenSize(ctx)
            val top = topMin() + (22 * d).toInt()
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
            if (!movable || state != St.ACTIVE) return
            if (full) { full = false; showBottom() }
            bounds.set(defaultBounds(ctx))
            sync()
            save()
        }

        private fun toggleMute() {
            val want = !muted
            io.launch {
                val ok = sys { Windows.mute(pkg, want) }
                withContext(Dispatchers.Main) { if (ok) muted = want else toast(ctx, "Could not change the sound") }
            }
        }

        private fun shot() {
            more = false
            io.launch {
                delay(500)
                val f = "/sdcard/Pictures/Ashutool/ashu_${System.currentTimeMillis()}.png"
                val ok = sys { Root.ok("mkdir -p /sdcard/Pictures/Ashutool", "screencap -p $f") }
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
                        if (movable) Dot(Color(0xFFE0526A), Icons.Outlined.Remove) { minimize() }
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
                        Chip3("Small", Icons.Outlined.PhotoSizeSelectSmall, false) { small() }
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

        /** The round close target. It grows and turns red when the window is over it. */
        @Composable
        private fun Zone() {
            val hot = zoneHot
            val scale by animateFloatAsState(if (hot) 1.28f else 1f, tween(150), label = "zone")
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Box(
                        Modifier.size(62.dp).graphicsLayer { scaleX = scale; scaleY = scale }.clip(CircleShape)
                            .background(if (hot) Color(0xFFFF4D4D) else Color(0xE61C1114)).border(2.dp, Color(0xFFFF6B6B), CircleShape),
                        contentAlignment = Alignment.Center
                    ) { Icon(Icons.Outlined.Close, null, tint = Color.White, modifier = Modifier.size(28.dp)) }
                    Spacer(Modifier.height(6.dp))
                    Text(if (hot) "Let go to close" else "Drag here to close", color = Color.White, fontSize = 11.sp, fontWeight = FontWeight.Medium)
                }
            }
        }

        /** The outline shown while the size is being changed. */
        @Composable
        private fun Ghost() {
            var shown by remember { mutableStateOf(false) }
            LaunchedEffect(Unit) { shown = true }
            AnimatedVisibility(visible = shown, modifier = Modifier.fillMaxSize(), enter = fadeIn(tween(120)), exit = fadeOut(tween(100))) {
                val shape = RoundedCornerShape(10.dp)
                Box(Modifier.fillMaxSize().clip(shape).background(Color(0x22FF6B6B)).border(2.dp, Color(0xFFFF6B6B), shape))
            }
        }

        /** The round button a minimised window turns into. */
        @Composable
        private fun Bubble() {
            var shown by remember { mutableStateOf(false) }
            LaunchedEffect(Unit) { shown = true }
            AnimatedVisibility(
                visible = shown,
                modifier = Modifier.fillMaxSize(),
                enter = fadeIn(tween(160)) + scaleIn(tween(240), initialScale = 0.3f),
                exit = fadeOut(tween(120)) + scaleOut(tween(160), targetScale = 0.3f)
            ) {
                Box(
                    Modifier.fillMaxSize().clip(CircleShape).background(Color(0xE61C1114)).border(2.dp, Color(0xFFFF6B6B), CircleShape)
                        .clickable { restore() },
                    contentAlignment = Alignment.Center
                ) {
                    AppIcon(pkg, 34.dp)
                }
            }
        }
    }
}

/**
 * A small panel with your floating apps, to open one in a window at any time, in a game or not.
 * It is opened from the quick settings tile Floating apps, or from the Home page.
 */
class FloatPicker(private val ctx: Context) {
    private val overlay = Overlay(ctx)
    private val main = Handler(Looper.getMainLooper())
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO + CoroutineExceptionHandler { _, _ -> })
    private var shown by mutableStateOf(false)
    private var removal: Runnable? = null

    val isShown: Boolean get() = overlay.isShown

    fun show() {
        removal?.let { main.removeCallbacks(it) }
        if (overlay.isShown) { hide(); return }
        val d = ctx.resources.displayMetrics.density
        val (sw, _) = screenSize(ctx)
        val lp = WindowManager.LayoutParams(
            minOf((330 * d).toInt(), sw - (16 * d).toInt()), WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL or
                WindowManager.LayoutParams.FLAG_WATCH_OUTSIDE_TOUCH or WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS or
                WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
            PixelFormat.TRANSLUCENT
        )
        lp.gravity = Gravity.BOTTOM or Gravity.CENTER_HORIZONTAL
        lp.y = (40 * d).toInt()
        shown = false
        overlay.show(lp, onOutside = { hide() }) { Panel() }
        main.post { shown = true }
    }

    fun hide() {
        shown = false
        val r = Runnable { overlay.remove() }
        removal = r
        main.postDelayed(r, 220)
    }

    private fun label(p: String): String =
        try { ctx.packageManager.getApplicationLabel(ctx.packageManager.getApplicationInfo(p, 0)).toString() } catch (e: Exception) { p }

    @Composable
    private fun Panel() {
        val apps = remember { FloatWindows.apps(ctx) }
        val open = FloatWindows.count()
        AnimatedVisibility(
            visible = shown,
            enter = fadeIn(tween(160)) + slideInVertically(tween(240)) { it / 2 },
            exit = fadeOut(tween(140)) + slideOutVertically(tween(180)) { it / 2 }
        ) {
            val shape = RoundedCornerShape(24.dp)
            Column(
                Modifier.fillMaxWidth().padding(6.dp).clip(shape).background(Color(0xF0151A2C))
                    .border(1.dp, Color.White.copy(alpha = 0.22f), shape).padding(12.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Outlined.Layers, null, tint = Color(0xFFFF8FA3), modifier = Modifier.size(20.dp))
                    Spacer(Modifier.width(8.dp))
                    Text("Floating apps", color = Color.White, fontSize = 14.sp, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
                    Icon(Icons.Outlined.Close, "Close", tint = Color.White, modifier = Modifier.size(22.dp).clickable { hide() })
                }
                if (apps.isEmpty()) {
                    Text("No apps yet. Choose them in Games, Windows and apps.", color = Color(0xFFB4C0D8), fontSize = 12.sp)
                }
                apps.chunked(4).forEach { row ->
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        row.forEach { p -> Cell(p) }
                        repeat(4 - row.size) { Spacer(Modifier.weight(1f)) }
                    }
                }
                if (open > 0) {
                    Text(
                        "$open open. Close all",
                        color = Color(0xFFFF8FA3), fontSize = 12.sp, fontWeight = FontWeight.SemiBold,
                        modifier = Modifier.clickable { hide(); scope.launch { FloatWindows.closeAll(ctx) } }.padding(vertical = 2.dp)
                    )
                }
                Text(
                    "${Credits.APP}  \u00B7  ${Credits.AUTHOR}  \u00B7  ${Credits.TELEGRAM}",
                    color = Color(0xFF8A94B0), fontSize = 9.sp, modifier = Modifier.fillMaxWidth(), textAlign = androidx.compose.ui.text.style.TextAlign.Center
                )
            }
        }
    }

    @Composable
    private fun RowScope.Cell(p: String) {
        Column(
            Modifier.weight(1f).clip(RoundedCornerShape(14.dp)).clickable {
                hide()
                scope.launch {
                    val ok = FloatWindows.open(ctx, p)
                    val n = FloatWindows.note
                    main.post { toast(ctx, if (!ok) "Could not open" else n.ifEmpty { "Opening" }) }
                }
            }.padding(vertical = 6.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            AppIcon(p, 40.dp)
            Text(label(p), color = Color(0xFFD3DAEC), fontSize = 10.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
    }
}

/** The Floating apps tile in the quick settings panel. */
class FloatTileService : TileService() {
    override fun onStartListening() {
        qsTile?.let {
            it.label = "Floating apps"
            it.state = Tile.STATE_INACTIVE
            it.updateTile()
        }
    }

    override fun onClick() {
        val app = applicationContext
        Thread {
            Root.run("cmd statusbar collapse")
            Handler(Looper.getMainLooper()).postDelayed({
                val s = MonitorService.instance
                if (s != null) s.showPicker() else toast(app, "Open Ashutool once so it can start")
            }, 350)
        }.start()
    }
}
