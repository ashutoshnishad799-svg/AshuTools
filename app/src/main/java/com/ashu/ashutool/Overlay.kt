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
import android.hardware.display.DisplayManager
import android.media.AudioManager
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.provider.Settings
import android.telephony.TelephonyManager
import android.util.DisplayMetrics
import android.view.Display
import android.view.Gravity
import android.view.HapticFeedbackConstants
import android.view.MotionEvent
import android.view.View
import android.view.ViewConfiguration
import android.view.WindowManager
import android.widget.FrameLayout
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.*
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.LifecycleRegistry
import androidx.lifecycle.ViewModelStore
import androidx.lifecycle.ViewModelStoreOwner
import androidx.lifecycle.setViewTreeLifecycleOwner
import androidx.lifecycle.setViewTreeViewModelStoreOwner
import androidx.savedstate.SavedStateRegistry
import androidx.savedstate.SavedStateRegistryController
import androidx.savedstate.SavedStateRegistryOwner
import androidx.savedstate.setViewTreeSavedStateRegistryOwner
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlin.math.abs
import kotlin.math.hypot

/** Full screen size in pixels, including the area under system bars. Follows rotation. */
@Suppress("DEPRECATION")
fun screenSize(ctx: Context): Pair<Int, Int> {
    try {
        val d = ctx.getSystemService(DisplayManager::class.java).getDisplay(Display.DEFAULT_DISPLAY)
        if (d != null) {
            val dm = DisplayMetrics()
            d.getRealMetrics(dm)
            if (dm.widthPixels > 0 && dm.heightPixels > 0) return dm.widthPixels to dm.heightPixels
        }
    } catch (_: Exception) {}
    val wm = ctx.getSystemService(WindowManager::class.java)
    if (Build.VERSION.SDK_INT >= 30) {
        val b = wm.currentWindowMetrics.bounds
        return b.width() to b.height()
    }
    val dm = DisplayMetrics()
    wm.defaultDisplay.getRealMetrics(dm)
    return dm.widthPixels to dm.heightPixels
}

/**
 * Lets an overlay window move itself. The movement is measured in raw screen pixels, never in window
 * coordinates. When the window moves under the finger, window coordinates change with it, and that feedback
 * is what made the old floating pill shake and fly off screen.
 * longPress = false: drag as soon as the finger moves. longPress = true: drag only after a press and hold,
 * so taps and swipes on the content still work.
 */
class DragSpec(
    val longPress: Boolean,
    val onStart: () -> Unit = {},
    val onMove: (Int, Int) -> Unit,
    val onEnd: () -> Unit = {}
)

/** Root view of every overlay window. It watches touches before the content does and takes over when a drag begins. */
private class DragFrame(ctx: Context) : FrameLayout(ctx) {
    var spec: DragSpec? = null
    private val slop = ViewConfiguration.get(ctx).scaledTouchSlop
    private val pressTime = ViewConfiguration.getLongPressTimeout().toLong()
    private var downX = 0f
    private var downY = 0f
    private var curX = 0f
    private var curY = 0f
    private var lastX = 0f
    private var lastY = 0f
    private var dragging = false
    private var tracking = false

    private val arm = Runnable {
        val s = spec
        if (s != null && tracking && !dragging) {
            dragging = true
            lastX = curX
            lastY = curY
            performHapticFeedback(HapticFeedbackConstants.LONG_PRESS)
            s.onStart()
        }
    }

    private fun begin(ev: MotionEvent) {
        removeCallbacks(arm)
        downX = ev.rawX
        downY = ev.rawY
        curX = downX
        curY = downY
        lastX = downX
        lastY = downY
        dragging = false
        tracking = true
        if (spec?.longPress == true) postDelayed(arm, pressTime)
    }

    private fun move(ev: MotionEvent) {
        val s = spec ?: return
        curX = ev.rawX
        curY = ev.rawY
        if (dragging || !tracking) return
        if (hypot(curX - downX, curY - downY) > slop) {
            if (s.longPress) {
                removeCallbacks(arm)
                tracking = false
            } else {
                dragging = true
                lastX = curX
                lastY = curY
                s.onStart()
            }
        }
    }

    private fun drag(ev: MotionEvent) {
        val s = spec ?: return
        val dx = (ev.rawX - lastX).toInt()
        val dy = (ev.rawY - lastY).toInt()
        if (dx != 0 || dy != 0) {
            lastX += dx
            lastY += dy
            s.onMove(dx, dy)
        }
    }

    private fun finish(): Boolean {
        removeCallbacks(arm)
        val was = dragging
        dragging = false
        tracking = false
        if (was) spec?.onEnd?.invoke()
        return was
    }

    override fun onInterceptTouchEvent(ev: MotionEvent): Boolean {
        if (spec == null) return false
        when (ev.actionMasked) {
            MotionEvent.ACTION_DOWN -> begin(ev)
            MotionEvent.ACTION_MOVE -> move(ev)
            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> return finish()
        }
        return dragging
    }

    override fun onTouchEvent(ev: MotionEvent): Boolean {
        if (spec == null) return super.onTouchEvent(ev)
        when (ev.actionMasked) {
            MotionEvent.ACTION_DOWN -> begin(ev)
            MotionEvent.ACTION_MOVE -> { move(ev); if (dragging) drag(ev) }
            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> finish()
        }
        return true
    }

    /** A child must not be able to switch our interception off, or the drag would never start. */
    override fun requestDisallowInterceptTouchEvent(disallowIntercept: Boolean) {
        if (spec == null) super.requestDisallowInterceptTouchEvent(disallowIntercept)
    }

    override fun onDetachedFromWindow() {
        removeCallbacks(arm)
        super.onDetachedFromWindow()
    }
}

/** Hosts Compose in a window that is not an Activity. Used by every overlay in the app. */
class Overlay(private val ctx: Context) : LifecycleOwner, SavedStateRegistryOwner, ViewModelStoreOwner {
    private val registry = LifecycleRegistry(this)
    private val saved = SavedStateRegistryController.create(this)
    private val store = ViewModelStore()
    private val wm = ctx.getSystemService(WindowManager::class.java)
    private var view: DragFrame? = null

    override val lifecycle: Lifecycle get() = registry
    override val savedStateRegistry: SavedStateRegistry get() = saved.savedStateRegistry
    override val viewModelStore: ViewModelStore get() = store

    init {
        saved.performAttach()
        saved.performRestore(null)
        registry.currentState = Lifecycle.State.CREATED
    }

    val isShown: Boolean get() = view != null

    /** Why the last attempt to add the window failed, empty if it worked. */
    @Volatile var lastError: String = ""

    /** Measured size of the window in pixels. Zero until the first layout has happened. */
    fun size(): Pair<Int, Int> = (view?.width ?: 0) to (view?.height ?: 0)

    /**
     * onOutside is called when the user touches anywhere outside the window. The window needs FLAG_WATCH_OUTSIDE_TOUCH.
     * drag makes the whole window movable, see DragSpec.
     */
    fun show(
        params: WindowManager.LayoutParams,
        onOutside: (() -> Unit)? = null,
        drag: DragSpec? = null,
        content: @Composable () -> Unit
    ) {
        remove()
        Palette.load(ctx)
        params.flags = params.flags or WindowManager.LayoutParams.FLAG_HARDWARE_ACCELERATED
        val frame = DragFrame(ctx)
        frame.spec = drag
        val v = ComposeView(ctx)
        for (h in listOf<View>(frame, v)) {
            h.setViewTreeLifecycleOwner(this)
            h.setViewTreeSavedStateRegistryOwner(this)
            h.setViewTreeViewModelStoreOwner(this)
        }
        frame.addView(v, FrameLayout.LayoutParams(FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT))
        if (onOutside != null) {
            frame.setOnTouchListener { _, ev ->
                if (ev.action == MotionEvent.ACTION_OUTSIDE) { onOutside(); true } else false
            }
        }
        v.setContent { AshuTheme { content() } }
        registry.currentState = Lifecycle.State.RESUMED
        try {
            wm.addView(frame, params)
            view = frame
            lastError = ""
        } catch (e: Exception) {
            lastError = "${e.javaClass.simpleName}: ${e.message ?: ""}".take(160)
            registry.currentState = Lifecycle.State.CREATED
        }
    }

    fun update(params: WindowManager.LayoutParams) {
        params.flags = params.flags or WindowManager.LayoutParams.FLAG_HARDWARE_ACCELERATED
        view?.let { try { wm.updateViewLayout(it, params) } catch (_: Exception) {} }
    }

    fun remove() {
        view?.let { try { wm.removeView(it) } catch (_: Exception) {} }
        view = null
        registry.currentState = Lifecycle.State.CREATED
    }
}

// ================================================================ Bar designs

val BarColors = listOf(Color.White, Color(0xFF2DE2C0), Color(0xFF4D8DFF), Color(0xFFFF7AC0), Color(0xFFFFC14D))
val BarColorNames = listOf("White", "Teal", "Blue", "Pink", "Amber")
val BarStyles = listOf("Pill", "Dots", "Glass capsule", "Neon line", "Orb", "Edge tab", "Gradient")

private fun DrawScope.chevron(cx: Float, cy: Float, s: Float, edge: Int, col: Color) {
    val p = Path()
    when (edge) {
        0 -> { p.moveTo(cx - s / 2, cy - s); p.lineTo(cx + s / 2, cy); p.lineTo(cx - s / 2, cy + s) }
        1 -> { p.moveTo(cx + s / 2, cy - s); p.lineTo(cx - s / 2, cy); p.lineTo(cx + s / 2, cy + s) }
        2 -> { p.moveTo(cx - s, cy - s / 2); p.lineTo(cx, cy + s / 2); p.lineTo(cx + s, cy - s / 2) }
        else -> { p.moveTo(cx - s, cy + s / 2); p.lineTo(cx, cy - s / 2); p.lineTo(cx + s, cy + s / 2) }
    }
    drawPath(p, col, style = Stroke(2.6f, cap = StrokeCap.Round, join = StrokeJoin.Round))
}

/**
 * Draws one bar design docked to the given edge (0 left, 1 right, 2 top, 3 bottom).
 * vw is the bar thickness and vl its length, both in pixels.
 */
fun DrawScope.drawBar(style: Int, col: Color, a: Float, edge: Int, vw: Float, vl: Float) {
    val vertical = edge < 2
    val bw = if (vertical) vw else vl
    val bh = if (vertical) vl else vw
    val left = when (edge) { 0 -> 0f; 1 -> size.width - bw; else -> (size.width - bw) / 2f }
    val top = when (edge) { 2 -> 0f; 3 -> size.height - bh; else -> (size.height - bh) / 2f }
    val tl = Offset(left, top)
    val rs = Size(bw, bh)
    val c = col.copy(alpha = a)
    val rad = CornerRadius(minOf(bw, bh) / 2f)
    val mid = Offset(left + bw / 2f, top + bh / 2f)
    when (style) {
        0 -> drawRoundRect(c, tl, rs, rad)
        1 -> {
            val r = vw * 0.42f
            val gap = vl / 4f
            for (i in 1..3) {
                val p = if (vertical) Offset(mid.x, top + gap * i) else Offset(left + gap * i, mid.y)
                drawCircle(c, r, p)
            }
        }
        2 -> {
            drawRoundRect(Color.White.copy(alpha = 0.16f * a), tl, rs, rad)
            drawRoundRect(c, tl, rs, rad, style = Stroke(1.6f))
            chevron(mid.x, mid.y, minOf(bw, bh) * 0.28f, edge, c)
        }
        3 -> {
            val t = vw * 0.30f
            val gl = if (vertical) Size(t * 3f, bh) else Size(bw, t * 3f)
            val gtl = Offset(mid.x - gl.width / 2f, mid.y - gl.height / 2f)
            drawRoundRect(c.copy(alpha = a * 0.22f), gtl, gl, CornerRadius(t * 1.5f))
            val ln = if (vertical) Size(t, bh) else Size(bw, t)
            drawRoundRect(c, Offset(mid.x - ln.width / 2f, mid.y - ln.height / 2f), ln, CornerRadius(t / 2f))
        }
        4 -> {
            val d = minOf(size.width, size.height) * 0.78f
            val ctr = Offset(size.width / 2f, size.height / 2f)
            drawCircle(Brush.linearGradient(listOf(c.copy(alpha = 0.55f * a), c.copy(alpha = 0.18f * a))), d / 2f, ctr)
            drawCircle(c, d / 2f, ctr, style = Stroke(2.2f))
            drawCircle(c, d * 0.11f, ctr)
        }
        6 -> {
            val g = Palette.cur.primary
            drawRoundRect(
                Brush.linearGradient(listOf(g.a.copy(alpha = a), g.b.copy(alpha = a)), start = tl, end = Offset(tl.x + bw, tl.y + bh)),
                tl, rs, rad
            )
        }
        else -> {
            val r = minOf(bw, bh) / 2f
            val ext = when (edge) {
                0 -> Size(bw + r, bh)
                1 -> Size(bw + r, bh)
                2 -> Size(bw, bh + r)
                else -> Size(bw, bh + r)
            }
            val etl = when (edge) { 0 -> Offset(left - r, top); 1 -> tl; 2 -> Offset(left, top - r); else -> tl }
            drawRoundRect(Color.White.copy(alpha = 0.20f * a), etl, ext, CornerRadius(r))
            drawRoundRect(c, etl, ext, CornerRadius(r), style = Stroke(1.6f))
            chevron(mid.x, mid.y, minOf(bw, bh) * 0.24f, edge, c)
        }
    }
}

// ============================================================ Game sidebar

private val SbText = Color.White
private val SbLow = Color(0xFFB4C0D8)

/**
 * Edge bar that opens a tool panel while a game is in front.
 * Tap or swipe it inward to open. Press and hold, then drag, to move it to any edge.
 * The bar stays on screen for the whole game. It is never swiped away, only moved.
 * The panel is a normal sized window, not full screen, and closes when you touch outside it.
 */
class GameSidebar(private val ctx: Context) {
    private val overlay = Overlay(ctx)
    private val handler = Handler(Looper.getMainLooper())
    private val io = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var alive = false
    private var closing = false
    private var dragging = false
    private var lastFail = 0L
    private var handleLp: WindowManager.LayoutParams? = null

    private var pkg by mutableStateOf("")
    private var expanded by mutableStateOf(false)
    private var open by mutableStateOf(false)
    private var edge by mutableIntStateOf(1)
    private var style by mutableIntStateOf(0)
    private var colorIdx by mutableIntStateOf(0)
    private var alphaPct by mutableIntStateOf(90)
    private var barW by mutableIntStateOf(7)
    private var barH by mutableIntStateOf(88)
    private var pos by mutableIntStateOf(50)

    val isShown: Boolean get() = overlay.isShown

    /** Why the bar could not be added, empty if it is fine. */
    val error: String get() = overlay.lastError

    private val dragSpec = DragSpec(
        longPress = true,
        onStart = { dragging = true },
        onMove = { dx, dy -> moveBy(dx, dy) },
        onEnd = { dock() }
    )

    private fun reload() {
        edge = LockStore.int(ctx, "g_edge", 1).coerceIn(0, 3)
        style = LockStore.int(ctx, "g_style", 0).coerceIn(0, BarStyles.size - 1)
        colorIdx = LockStore.int(ctx, "g_color", 0).coerceIn(0, BarColors.size - 1)
        alphaPct = LockStore.int(ctx, "g_alpha", 90).coerceIn(30, 100)
        barW = LockStore.int(ctx, "g_w", 7).coerceIn(3, 20)
        barH = LockStore.int(ctx, "g_h", 88).coerceIn(30, 260)
        pos = LockStore.int(ctx, "g_pos", 50).coerceIn(5, 95)
    }

    private fun handleParams(): WindowManager.LayoutParams {
        val d = ctx.resources.displayMetrics.density
        val (sw, sh) = screenSize(ctx)
        val vertical = edge < 2
        val thick = ((if (style == 4) 56 else barW + 26) * d).toInt()
        val len = ((if (style == 4) 56 else barH + 56) * d).toInt()
        val w = if (vertical) thick else len
        val h = if (vertical) len else thick
        val lp = WindowManager.LayoutParams(
            w, h, WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL or
                WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS or WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
            PixelFormat.TRANSLUCENT
        )
        lp.gravity = Gravity.TOP or Gravity.START
        when (edge) {
            0 -> { lp.x = 0; lp.y = (pos / 100f * sh - h / 2f).toInt() }
            1 -> { lp.x = sw - w; lp.y = (pos / 100f * sh - h / 2f).toInt() }
            2 -> { lp.y = 0; lp.x = (pos / 100f * sw - w / 2f).toInt() }
            else -> { lp.y = sh - h; lp.x = (pos / 100f * sw - w / 2f).toInt() }
        }
        lp.x = lp.x.coerceIn(0, (sw - w).coerceAtLeast(0))
        lp.y = lp.y.coerceIn(0, (sh - h).coerceAtLeast(0))
        return lp
    }

    private fun panelParams(): WindowManager.LayoutParams {
        val d = ctx.resources.displayMetrics.density
        val (sw, sh) = screenSize(ctx)
        val scaleDp = when (LockStore.int(ctx, "g_panel", 1)) { 0 -> 292; 2 -> 360; else -> 324 }
        val pw = minOf((scaleDp * d).toInt(), sw - (16 * d).toInt())
        val ph = if (edge < 2) (sh * 0.92f).toInt() else (sh * 0.66f).toInt()
        val lp = WindowManager.LayoutParams(
            pw, ph, WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL or
                WindowManager.LayoutParams.FLAG_WATCH_OUTSIDE_TOUCH or WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS or
                WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
            PixelFormat.TRANSLUCENT
        )
        lp.gravity = when (edge) {
            0 -> Gravity.START or Gravity.CENTER_VERTICAL
            1 -> Gravity.END or Gravity.CENTER_VERTICAL
            2 -> Gravity.TOP or Gravity.CENTER_HORIZONTAL
            else -> Gravity.BOTTOM or Gravity.CENTER_HORIZONTAL
        }
        Glassy.blur(ctx, lp, 36)
        return lp
    }

    /** Adds the bar window. */
    private fun showHandle() {
        reload()
        val lp = handleParams()
        handleLp = lp
        overlay.show(lp, drag = dragSpec) { Content() }
        if (!overlay.isShown) lastFail = SystemClock.elapsedRealtime()
    }

    /**
     * Shows the bar for a game. Safe to call as often as you like: it adds the window if it is missing,
     * and fixes its size and place if the screen turned or a setting changed.
     */
    fun showFor(p: String) {
        pkg = p
        if (overlay.isShown) { refresh(); return }
        if (SystemClock.elapsedRealtime() - lastFail < 2000) return
        alive = true
        expanded = false
        open = false
        closing = false
        showHandle()
    }

    /** Applies size, colour, position and rotation changes while the bar is visible. */
    private fun refresh() {
        if (dragging || expanded) return
        reload()
        val lp = handleParams()
        val cur = handleLp
        if (cur != null && cur.x == lp.x && cur.y == lp.y && cur.width == lp.width && cur.height == lp.height) return
        handleLp = lp
        overlay.update(lp)
    }

    /** Called after the screen turns. A panel that is open is closed so it can never end up off screen. */
    fun relayout() {
        if (!alive || !overlay.isShown) return
        if (expanded) {
            expanded = false
            open = false
            closing = false
            Monitor.boost = false
            showHandle()
        } else refresh()
    }

    fun hide() {
        alive = false
        open = false
        expanded = false
        Monitor.boost = false
        closing = false
        dragging = false
        overlay.remove()
    }

    private fun openPanel() {
        if (!alive || expanded) return
        expanded = true
        open = false
        Monitor.boost = true
        Monitor.kick(ctx)
        overlay.show(panelParams(), { closePanel() }) { Content() }
        handler.post { open = true }
    }

    private fun closePanel() {
        if (!expanded || closing) return
        closing = true
        open = false
        handler.postDelayed({
            closing = false
            Monitor.boost = false
            if (!alive) return@postDelayed
            expanded = false
            showHandle()
        }, 320)
    }

    private fun moveBy(dx: Int, dy: Int) {
        val lp = handleLp ?: return
        val (sw, sh) = screenSize(ctx)
        lp.x = (lp.x + dx).coerceIn(0, (sw - lp.width).coerceAtLeast(0))
        lp.y = (lp.y + dy).coerceIn(0, (sh - lp.height).coerceAtLeast(0))
        overlay.update(lp)
    }

    /** After a long press drag, snap to the nearest edge and remember the spot. */
    private fun dock() {
        dragging = false
        val lp = handleLp ?: return
        val (sw, sh) = screenSize(ctx)
        val cx = lp.x + lp.width / 2f
        val cy = lp.y + lp.height / 2f
        val dl = cx
        val dr = sw - cx
        val dt = cy
        val db = sh - cy
        val m = minOf(minOf(dl, dr), minOf(dt, db))
        val e = if (m == dl) 0 else if (m == dr) 1 else if (m == dt) 2 else 3
        val p = if (e < 2) cy / sh * 100f else cx / sw * 100f
        LockStore.setInt(ctx, "g_edge", e)
        LockStore.setInt(ctx, "g_pos", p.toInt().coerceIn(6, 94))
        reload()
        val np = handleParams()
        handleLp = np
        overlay.update(np)
    }

    private fun say(msg: String) { handler.post { toast(ctx, msg) } }

    @Composable
    private fun Content() {
        if (!expanded) HandleView() else PanelView()
    }

    @Composable
    private fun HandleView() {
        val d = LocalDensity.current.density
        Canvas(
            Modifier.fillMaxSize()
                .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) { openPanel() }
                .pointerInput(edge) {
                    var dx = 0f
                    var dy = 0f
                    detectDragGestures(
                        onDragStart = { dx = 0f; dy = 0f },
                        onDrag = { c, dd -> c.consume(); dx += dd.x; dy += dd.y },
                        onDragEnd = {
                            val inward = when (edge) { 0 -> dx; 1 -> -dx; 2 -> dy; else -> -dy }
                            val along = if (edge < 2) abs(dy) else abs(dx)
                            val across = if (edge < 2) abs(dx) else abs(dy)
                            if (across > along && inward > 36f) openPanel()
                        }
                    )
                }
        ) {
            drawBar(style, BarColors[colorIdx], alphaPct / 100f, edge, barW * d, barH * d)
        }
    }

    @Composable
    private fun PanelView() {
        val e = edge
        val spec = tween<androidx.compose.ui.unit.IntOffset>(300, easing = FastOutSlowInEasing)
        AnimatedVisibility(
            visible = open,
            modifier = Modifier.fillMaxSize(),
            enter = when (e) {
                0 -> slideInHorizontally(spec) { -it }
                1 -> slideInHorizontally(spec) { it }
                2 -> slideInVertically(spec) { -it }
                else -> slideInVertically(spec) { it }
            } + fadeIn(tween(220)),
            exit = when (e) {
                0 -> slideOutHorizontally(tween(260)) { -it }
                1 -> slideOutHorizontally(tween(260)) { it }
                2 -> slideOutVertically(tween(260)) { -it }
                else -> slideOutVertically(tween(260)) { it }
            } + fadeOut(tween(200))
        ) { PanelCard() }
    }

    @Composable
    private fun PanelCard() {
        val s by Monitor.snap.collectAsState()
        val pal = Palette.cur
        val shape = RoundedCornerShape(28.dp)
        val am = remember { ctx.getSystemService(AudioManager::class.java) }
        var vol by remember { mutableFloatStateOf(am.getStreamVolume(AudioManager.STREAM_MUSIC).toFloat()) }
        var bright by remember {
            mutableFloatStateOf(
                try { Settings.System.getInt(ctx.contentResolver, Settings.System.SCREEN_BRIGHTNESS).toFloat() } catch (e: Exception) { 128f }
            )
        }
        var fpsOn by remember { mutableStateOf(LockStore.bool(ctx, "g_fps", true)) }
        var touches by remember { mutableStateOf(false) }
        var net by remember { mutableStateOf(NetMode.known(ctx)) }
        var ringer by remember { mutableIntStateOf(Ringer.mode(ctx)) }
        var island by remember { mutableStateOf(LockStore.bool(ctx, "isl_on", true)) }
        LaunchedEffect(Unit) {
            withContext(Dispatchers.IO) {
                touches = Root.run("settings get system show_touches").firstOrNull()?.trim() == "1"
            }
            NetMode.refresh(ctx) { v -> handler.post { if (v != null) net = v } }
        }
        val label = remember(pkg) { appLabel(pkg) }
        val accent = pal.accent
        Column(
            Modifier.fillMaxSize().padding(8.dp).clip(shape)
                .then(Glassy.fill(pal, 0.24f))
                .border(1.dp, Glassy.stroke(pal), shape)
                .verticalScroll(rememberScrollState())
                .padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(11.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                AppIcon(pkg, 30.dp)
                Spacer(Modifier.width(10.dp))
                Column(Modifier.weight(1f)) {
                    Text(label, color = TextHi, fontSize = 15.sp, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text("Game tools", color = TextLo, fontSize = 10.sp)
                }
                Box(
                    Modifier.bounceClick { closePanel() }.size(32.dp).clip(RoundedCornerShape(50)).background(TextHi.copy(alpha = 0.10f)),
                    contentAlignment = Alignment.Center
                ) { Icon(Icons.Outlined.Close, "Close", tint = TextHi, modifier = Modifier.size(18.dp)) }
            }
            // Live numbers in small glass chips
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                Stat("CPU", "${s.cpu.toInt()}%", Modifier.weight(1f))
                Stat("Temp", "${s.cpuTemp.toInt()}\u00B0", Modifier.weight(1f))
                Stat("RAM", "${s.ramPct.toInt()}%", Modifier.weight(1f))
                Stat("Batt", "${s.batt.level}%", Modifier.weight(1f))
                Stat("Down", fmtSpeed(s.netDown), Modifier.weight(1.3f))
            }
            // Performance profile
            Row(
                Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(TextHi.copy(alpha = 0.08f)).padding(3.dp),
                horizontalArrangement = Arrangement.spacedBy(3.dp)
            ) {
                Seg("Standard", GameUi.profile == 0, Modifier.weight(1f)) { io.launch { GameMode.setProfile(ctx, 0) } }
                Seg("Performance", GameUi.profile == 1, Modifier.weight(1f)) { io.launch { GameMode.setProfile(ctx, 1) } }
                Seg("Saver", GameUi.profile == 2, Modifier.weight(1f)) { io.launch { GameMode.setProfile(ctx, 2) } }
            }
            if (Calls.state == TelephonyManager.CALL_STATE_OFFHOOK) {
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    MiniTool(Icons.Outlined.Speaker, null, "Speaker", Calls.speaker, Modifier.weight(1f)) { Calls.setSpeaker(ctx, !Calls.speaker) }
                    MiniTool(if (Calls.muted) Icons.Outlined.MicOff else Icons.Outlined.Mic, null, "Mute", Calls.muted, Modifier.weight(1f)) { Calls.setMute(ctx, !Calls.muted) }
                    MiniTool(Icons.Outlined.CallEnd, null, "End call", false, Modifier.weight(1f)) { io.launch { Calls.end(ctx) } }
                    Spacer(Modifier.weight(1f))
                }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                MiniTool(Icons.Outlined.NotificationsOff, null, "DND", GameUi.dnd, Modifier.weight(1f)) { io.launch { GameMode.setDnd(ctx, !GameUi.dnd) } }
                MiniTool(Icons.Outlined.NotificationsPaused, null, "Popups", GameUi.heads, Modifier.weight(1f)) { io.launch { GameMode.setHeadsUp(ctx, !GameUi.heads) } }
                MiniTool(Icons.Outlined.Speed, null, "FPS", fpsOn, Modifier.weight(1f)) {
                    fpsOn = !fpsOn
                    LockStore.setBool(ctx, "g_fps", fpsOn)
                    MonitorService.instance?.wakeSpeed()
                }
                MiniTool(Icons.Outlined.CleaningServices, null, "Clear RAM", false, Modifier.weight(1f)) {
                    io.launch { say(if (Root.ok("am kill-all") && Sys.dropCaches()) "RAM cleared" else "Not available") }
                }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                MiniTool(Icons.Outlined.PhotoCamera, null, "Shot", false, Modifier.weight(1f)) {
                    closePanel()
                    io.launch {
                        delay(700)
                        val f = "/sdcard/Pictures/Ashutool/ashu_${System.currentTimeMillis()}.png"
                        say(if (Root.ok("mkdir -p /sdcard/Pictures/Ashutool", "screencap -p $f")) "Screenshot saved" else "Could not save")
                    }
                }
                MiniTool(Icons.Outlined.Videocam, null, if (Recorder.running) "Stop" else "Record", Recorder.running, Modifier.weight(1f)) {
                    if (Recorder.running) io.launch { Recorder.stop(); say("Recording saved") }
                    else {
                        closePanel()
                        io.launch { delay(700); if (!Recorder.start()) say("Could not start recording") }
                    }
                }
                MiniTool(Icons.Outlined.TouchApp, null, "Touches", touches, Modifier.weight(1f)) {
                    touches = !touches
                    io.launch { Root.ok("settings put system show_touches ${if (touches) 1 else 0}") }
                }
                // 5G / 4G right here: the picture shows which one you are on
                MiniTool(null, if (net == false) "4G" else "5G", "5G / 4G", net == true, Modifier.weight(1f)) {
                    val want = net != true
                    net = want
                    IslandHub.flashNet(ctx, want)
                    NetMode.apply(ctx, want) { err ->
                        handler.post { if (err.isNotEmpty()) { net = !want; toast(ctx, "5G / 4G did not change") } }
                    }
                }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                MiniTool(Ringer.icon(ringer), null, Ringer.label(ringer), ringer != Ringer.SILENT, Modifier.weight(1f)) {
                    ringer = Ringer.cycle(ctx) { ringer = it }
                }
                MiniTool(Icons.Outlined.MusicNote, null, "Volume", false, Modifier.weight(1f)) {
                    closePanel()
                    handler.postDelayed({ VolumePanel.show(ctx) }, 360)
                }
                MiniTool(Icons.Outlined.BlurOn, null, "Island", island, Modifier.weight(1f)) {
                    island = !island
                    IslandCfg.putBool(ctx, "isl_on", island)
                    MonitorService.instance?.islandChanged()
                }
                Spacer(Modifier.weight(1f))
            }
            SliderRow(Icons.Outlined.LightMode, "Brightness", bright, 5f, 255f, accent) {
                bright = it
                io.launch { Root.ok("settings put system screen_brightness_mode 0", "settings put system screen_brightness ${it.toInt()}") }
            }
            SliderRow(Icons.Outlined.MusicNote, "Volume", vol, 0f, am.getStreamMaxVolume(AudioManager.STREAM_MUSIC).toFloat(), accent) {
                vol = it
                am.setStreamVolume(AudioManager.STREAM_MUSIC, it.toInt(), 0)
            }
            Text(
                "${Credits.APP}  \u00B7  ${Credits.AUTHOR}  \u00B7  ${Credits.TELEGRAM}",
                color = TextLo, fontSize = 9.sp, textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                modifier = Modifier.fillMaxWidth()
            )
        }
    }

    @Composable
    private fun Seg(label: String, selected: Boolean, modifier: Modifier, onClick: () -> Unit) {
        val shape = RoundedCornerShape(13.dp)
        Box(
            modifier.bounceClick(onClick = onClick).clip(shape)
                .then(if (selected) Modifier.background(Palette.cur.primary.h()) else Modifier),
            contentAlignment = Alignment.Center
        ) {
            Text(
                label, color = if (selected) Ink else TextHi, fontSize = 11.sp, fontWeight = FontWeight.SemiBold, maxLines = 1,
                modifier = Modifier.padding(vertical = 8.dp)
            )
        }
    }

    /** Square button: an icon (or a short text like 5G) with a name under it. Lit when it is on. */
    @Composable
    private fun MiniTool(icon: ImageVector?, glyph: String?, label: String, on: Boolean, modifier: Modifier, onClick: () -> Unit) {
        val shape = RoundedCornerShape(16.dp)
        val ink = if (on) Ink else TextHi
        Column(
            modifier.bounceClick(onClick = onClick).clip(shape)
                .then(
                    if (on) Modifier.background(Palette.cur.primary.d())
                    else Modifier.background(TextHi.copy(alpha = 0.09f)).border(1.dp, TextHi.copy(alpha = 0.08f), shape)
                )
                .padding(vertical = 9.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Box(Modifier.height(22.dp), contentAlignment = Alignment.Center) {
                if (glyph != null) Text(glyph, color = ink, fontSize = 15.sp, fontWeight = FontWeight.ExtraBold)
                else if (icon != null) Icon(icon, label, tint = ink, modifier = Modifier.size(20.dp))
            }
            Spacer(Modifier.height(3.dp))
            Text(label, color = ink, fontSize = 9.sp, fontWeight = FontWeight.Medium, maxLines = 1)
        }
    }

    private fun appLabel(p: String): String =
        try { ctx.packageManager.getApplicationLabel(ctx.packageManager.getApplicationInfo(p, 0)).toString() } catch (e: Exception) { p }

    @Composable
    private fun Stat(label: String, value: String, modifier: Modifier) {
        Column(
            modifier.clip(RoundedCornerShape(12.dp)).background(TextHi.copy(alpha = 0.08f)).padding(vertical = 6.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(value, color = TextHi, fontSize = 12.sp, fontWeight = FontWeight.SemiBold, maxLines = 1)
            Text(label, color = TextLo, fontSize = 9.sp)
        }
    }

    @Composable
    private fun SliderRow(icon: ImageVector, label: String, value: Float, lo: Float, hi: Float, accent: Color, onChange: (Float) -> Unit) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(icon, label, tint = TextLo, modifier = Modifier.size(20.dp))
            Slider(
                value = value.coerceIn(lo, hi), onValueChange = onChange, valueRange = lo..hi,
                modifier = Modifier.weight(1f).padding(start = 8.dp),
                colors = SliderDefaults.colors(thumbColor = accent, activeTrackColor = accent, inactiveTrackColor = TextHi.copy(alpha = 0.18f))
            )
        }
    }
}
