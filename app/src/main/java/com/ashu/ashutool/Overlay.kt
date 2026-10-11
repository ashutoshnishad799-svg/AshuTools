package com.ashu.ashutool

import android.content.Context
import android.graphics.PixelFormat
import android.media.AudioManager
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.provider.Settings
import android.util.DisplayMetrics
import android.view.Gravity
import android.view.MotionEvent
import android.view.WindowManager
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
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
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
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
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
import kotlin.math.abs

/** Full screen size in pixels, including the area under system bars. */
@Suppress("DEPRECATION")
fun screenSize(ctx: Context): Pair<Int, Int> {
    val wm = ctx.getSystemService(WindowManager::class.java)
    if (Build.VERSION.SDK_INT >= 30) {
        val b = wm.currentWindowMetrics.bounds
        return b.width() to b.height()
    }
    val dm = DisplayMetrics()
    wm.defaultDisplay.getRealMetrics(dm)
    return dm.widthPixels to dm.heightPixels
}

/** Hosts Compose in a window that is not an Activity. Used by every overlay in the app. */
class Overlay(private val ctx: Context) : LifecycleOwner, SavedStateRegistryOwner, ViewModelStoreOwner {
    private val registry = LifecycleRegistry(this)
    private val saved = SavedStateRegistryController.create(this)
    private val store = ViewModelStore()
    private val wm = ctx.getSystemService(WindowManager::class.java)
    private var view: ComposeView? = null

    override val lifecycle: Lifecycle get() = registry
    override val savedStateRegistry: SavedStateRegistry get() = saved.savedStateRegistry
    override val viewModelStore: ViewModelStore get() = store

    init {
        saved.performAttach()
        saved.performRestore(null)
        registry.currentState = Lifecycle.State.CREATED
    }

    val isShown: Boolean get() = view != null

    /** onOutside is called when the user touches anywhere outside the window. The window needs FLAG_WATCH_OUTSIDE_TOUCH. */
    fun show(params: WindowManager.LayoutParams, onOutside: (() -> Unit)? = null, content: @Composable () -> Unit) {
        remove()
        Palette.load(ctx)
        params.flags = params.flags or WindowManager.LayoutParams.FLAG_HARDWARE_ACCELERATED
        val v = ComposeView(ctx)
        v.setViewTreeLifecycleOwner(this)
        v.setViewTreeSavedStateRegistryOwner(this)
        v.setViewTreeViewModelStoreOwner(this)
        if (onOutside != null) {
            v.setOnTouchListener { _, ev ->
                if (ev.action == MotionEvent.ACTION_OUTSIDE) { onOutside(); true } else false
            }
        }
        v.setContent { AshuTheme { content() } }
        registry.currentState = Lifecycle.State.RESUMED
        try {
            wm.addView(v, params)
            view = v
        } catch (e: Exception) {
            registry.currentState = Lifecycle.State.CREATED
        }
    }

    fun update(params: WindowManager.LayoutParams) {
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
val BarStyles = listOf("Pill", "Dots", "Glass capsule", "Neon line", "Orb", "Edge tab")

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
 * Tap to open. Long press and drag to move it to any edge. Swipe it towards the edge to hide it.
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
    private var dismissedFor: String? = null
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
        val pw = minOf((324 * d).toInt(), sw - (16 * d).toInt())
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
        return lp
    }

    /** Shows the bar for a game. Safe to call every tick. Retries if the window could not be added. */
    fun showFor(p: String) {
        pkg = p
        if (dismissedFor == p) return
        if (overlay.isShown) { refresh(); return }
        if (SystemClock.elapsedRealtime() - lastFail < 3000) return
        alive = true
        reload()
        expanded = false
        open = false
        val lp = handleParams()
        handleLp = lp
        overlay.show(lp) { Content() }
        if (!overlay.isShown) lastFail = SystemClock.elapsedRealtime()
    }

    /** Applies size, colour and position changes made in the Games page while the bar is visible. */
    private fun refresh() {
        if (dragging || expanded) return
        reload()
        val lp = handleParams()
        handleLp = lp
        overlay.update(lp)
    }

    fun hide() {
        alive = false
        open = false
        expanded = false
        closing = false
        overlay.remove()
    }

    fun resetDismiss() { dismissedFor = null }

    private fun dismiss() {
        dismissedFor = pkg
        hide()
        handler.post { toast(ctx, "Sidebar hidden for this game") }
    }

    private fun openPanel() {
        if (!alive || expanded) return
        expanded = true
        open = false
        overlay.show(panelParams(), { closePanel() }) { Content() }
        handler.post { open = true }
    }

    private fun closePanel() {
        if (!expanded || closing) return
        closing = true
        open = false
        handler.postDelayed({
            closing = false
            if (!alive) return@postDelayed
            expanded = false
            val lp = handleParams()
            handleLp = lp
            overlay.show(lp) { Content() }
        }, 320)
    }

    private fun moveBy(dx: Float, dy: Float) {
        val lp = handleLp ?: return
        lp.x += dx.toInt()
        lp.y += dy.toInt()
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
        var grab by remember { mutableStateOf(Offset.Zero) }
        val haptic = LocalHapticFeedback.current
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
                            if (across > along) {
                                if (inward > 36f) openPanel() else if (inward < -48f) dismiss()
                            }
                        }
                    )
                }
                .pointerInput(edge) {
                    detectDragGesturesAfterLongPress(
                        onDragStart = {
                            grab = it
                            dragging = true
                            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                        },
                        onDrag = { c, _ -> c.consume(); moveBy(c.position.x - grab.x, c.position.y - grab.y) },
                        onDragEnd = { dock() },
                        onDragCancel = { dock() }
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
        val shape = RoundedCornerShape(26.dp)
        val am = remember { ctx.getSystemService(AudioManager::class.java) }
        var vol by remember { mutableFloatStateOf(am.getStreamVolume(AudioManager.STREAM_MUSIC).toFloat()) }
        var bright by remember {
            mutableFloatStateOf(
                try { Settings.System.getInt(ctx.contentResolver, Settings.System.SCREEN_BRIGHTNESS).toFloat() } catch (e: Exception) { 128f }
            )
        }
        val label = remember(pkg) { appLabel(pkg) }
        val shelf = remember(pkg) {
            val set = LockStore.set(ctx, "g_shelf")
            (if (set.isNotEmpty()) set.toList() else GameMode.cached.toList()).filter { it != pkg && Safety.validPkg(it) }.take(14)
        }
        Column(
            Modifier.fillMaxSize().padding(8.dp).clip(shape)
                .background(Brush.verticalGradient(listOf(Color(0xF2111B33), Color(0xF2081724))))
                .border(1.dp, Color.White.copy(alpha = 0.28f), shape)
                .verticalScroll(rememberScrollState())
                .padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                AppIcon(pkg, 36.dp)
                Spacer(Modifier.width(10.dp))
                Column(Modifier.weight(1f)) {
                    Text(label, color = SbText, fontSize = 15.sp, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text("Game tools", color = SbLow, fontSize = 11.sp)
                }
                Icon(Icons.Outlined.Close, "Close", tint = SbText, modifier = Modifier.size(26.dp).clickable { closePanel() })
            }
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Stat("CPU", "${s.cpu.toInt()}%"); Stat("Temp", "${s.cpuTemp.toInt()}\u00B0"); Stat("RAM", "${s.ramPct.toInt()}%")
            }
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Stat("Battery", "${s.batt.level}%"); Stat("Down", fmtSpeed(s.netDown)); Stat("Up", fmtSpeed(s.netUp))
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Tool(Icons.Outlined.Speed, "Performance", GameUi.perf, Modifier.weight(1f)) { io.launch { GameMode.setPerf(ctx, !GameUi.perf) } }
                Tool(Icons.Outlined.NotificationsOff, "Do not disturb", GameUi.dnd, Modifier.weight(1f)) { io.launch { GameMode.setDnd(ctx, !GameUi.dnd) } }
                Tool(Icons.Outlined.CleaningServices, "Clear RAM", false, Modifier.weight(1f)) {
                    io.launch { say(if (Root.ok("am kill-all") && Sys.dropCaches()) "RAM cleared" else "Not available") }
                }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Tool(Icons.Outlined.PhotoCamera, "Screenshot", false, Modifier.weight(1f)) {
                    closePanel()
                    io.launch {
                        delay(700)
                        val f = "/sdcard/Pictures/Ashutool/ashu_${System.currentTimeMillis()}.png"
                        say(if (Root.ok("mkdir -p /sdcard/Pictures/Ashutool", "screencap -p $f")) "Screenshot saved" else "Could not save")
                    }
                }
                Tool(Icons.Outlined.Videocam, if (Recorder.running) "Stop record" else "Record", Recorder.running, Modifier.weight(1f)) {
                    if (Recorder.running) io.launch { Recorder.stop(); say("Recording saved") }
                    else {
                        closePanel()
                        io.launch { delay(700); if (!Recorder.start()) say("Could not start recording") }
                    }
                }
                Tool(Icons.Outlined.VisibilityOff, "Hide bar", false, Modifier.weight(1f)) { dismiss() }
            }
            if (shelf.isNotEmpty()) {
                Text("Open in window", color = SbLow, fontSize = 11.sp)
                LazyRow(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    items(shelf, key = { it }) { p ->
                        Column(
                            Modifier.clickable {
                                closePanel()
                                io.launch { say(if (Windows.open(ctx, p, LockStore.bool(ctx, "g_float", true))) "Opening" else "Could not open") }
                            },
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            AppIcon(p, 42.dp)
                            Text(appLabel(p), color = SbLow, fontSize = 10.sp, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.width(52.dp))
                        }
                    }
                }
            }
            SliderRow(Icons.Outlined.LightMode, "Brightness", bright, 5f, 255f) {
                bright = it
                io.launch { Root.ok("settings put system screen_brightness_mode 0", "settings put system screen_brightness ${it.toInt()}") }
            }
            SliderRow(Icons.Outlined.MusicNote, "Volume", vol, 0f, am.getStreamMaxVolume(AudioManager.STREAM_MUSIC).toFloat()) {
                vol = it
                am.setStreamVolume(AudioManager.STREAM_MUSIC, it.toInt(), 0)
            }
        }
    }

    private fun appLabel(p: String): String =
        try { ctx.packageManager.getApplicationLabel(ctx.packageManager.getApplicationInfo(p, 0)).toString() } catch (e: Exception) { p }

    @Composable
    private fun Stat(label: String, value: String) {
        Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.width(84.dp)) {
            Text(value, color = SbText, fontSize = 14.sp, fontWeight = FontWeight.SemiBold, maxLines = 1)
            Text(label, color = SbLow, fontSize = 10.sp)
        }
    }

    @Composable
    private fun Tool(icon: ImageVector, label: String, on: Boolean, modifier: Modifier, onClick: () -> Unit) {
        val shape = RoundedCornerShape(16.dp)
        Column(
            modifier.bounceClick(onClick = onClick).clip(shape)
                .background(
                    if (on) Brush.linearGradient(listOf(Color.White, Color(0xFFCFE3FF)))
                    else Brush.linearGradient(listOf(Color.White.copy(alpha = 0.10f), Color.White.copy(alpha = 0.10f)))
                )
                .padding(vertical = 12.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Icon(icon, label, tint = if (on) Ink else SbText, modifier = Modifier.size(22.dp))
            Spacer(Modifier.height(4.dp))
            Text(label, color = if (on) Ink else SbText, fontSize = 10.sp, fontWeight = FontWeight.Medium, maxLines = 1)
        }
    }

    @Composable
    private fun SliderRow(icon: ImageVector, label: String, value: Float, lo: Float, hi: Float, onChange: (Float) -> Unit) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(icon, label, tint = SbLow, modifier = Modifier.size(20.dp))
            Slider(
                value = value.coerceIn(lo, hi), onValueChange = onChange, valueRange = lo..hi,
                modifier = Modifier.weight(1f).padding(start = 8.dp),
                colors = SliderDefaults.colors(thumbColor = Color.White, activeTrackColor = Color.White, inactiveTrackColor = Color.White.copy(alpha = 0.22f))
            )
        }
    }
}
