package com.ashu.ashutool

import android.content.Context
import android.graphics.PixelFormat
import android.graphics.Rect
import android.os.Build
import android.os.SystemClock
import android.view.Gravity
import android.view.WindowManager
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay

fun mmss(sec: Long): String = "%02d:%02d".format(sec / 60, sec % 60)

private val Glass45 = Color.Black.copy(alpha = 0.45f)
private val Edge25 = Color.White.copy(alpha = 0.25f)

@Composable
private fun RoundBtn(icon: ImageVector, desc: String, bg: Color, size: Int = 34, onClick: () -> Unit) {
    Box(
        Modifier.size(size.dp).clip(CircleShape).background(bg).clickable(onClick = onClick),
        contentAlignment = Alignment.Center
    ) { Icon(icon, desc, tint = Color.White, modifier = Modifier.size((size * 0.55f).dp)) }
}

/** A small window you can drag anywhere. Its position is remembered between uses. */
abstract class FloatingWindow(protected val ctx: Context, private val key: String) {
    protected val overlay = Overlay(ctx)
    protected var lp: WindowManager.LayoutParams? = null

    val isShown: Boolean get() = overlay.isShown

    fun hide() = overlay.remove()

    protected fun newParams(defX: Int, defY: Int): WindowManager.LayoutParams {
        val p = WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT, WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL or
                WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS or WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
            PixelFormat.TRANSLUCENT
        )
        val (sw, sh) = screenSize(ctx)
        p.gravity = Gravity.TOP or Gravity.START
        p.x = LockStore.int(ctx, "${key}_x", defX).coerceIn(0, (sw - 60).coerceAtLeast(0))
        p.y = LockStore.int(ctx, "${key}_y", defY).coerceIn(0, (sh - 60).coerceAtLeast(0))
        lp = p
        return p
    }

    /** Drag handler. It moves the window by how far the finger moved from where it grabbed. */
    protected fun dragModifier(): Modifier = Modifier.pointerInput(Unit) {
        var grab = Offset.Zero
        detectDragGestures(
            onDragStart = { grab = it },
            onDrag = { change, _ ->
                change.consume()
                lp?.let {
                    it.x += (change.position.x - grab.x).toInt()
                    it.y += (change.position.y - grab.y).toInt()
                    overlay.update(it)
                }
            },
            onDragEnd = { lp?.let { LockStore.setInt(ctx, "${key}_x", it.x); LockStore.setInt(ctx, "${key}_y", it.y) } }
        )
    }
}

// ============================================================ Screen recording

/** See-through recording indicator with a running timer and a stop button. Drag it anywhere. */
class RecordPill(ctx: Context) : FloatingWindow(ctx, "rec") {
    fun show() {
        if (isShown) return
        val d = ctx.resources.displayMetrics.density
        val (sw, _) = screenSize(ctx)
        overlay.show(newParams((sw / 2 - 70 * d).toInt(), (48 * d).toInt())) { Pill() }
    }

    @Composable
    private fun Pill() {
        var sec by remember { mutableLongStateOf(0L) }
        LaunchedEffect(Unit) {
            while (true) {
                sec = (SystemClock.elapsedRealtime() - Recorder.startedAt) / 1000
                delay(1000)
            }
        }
        val blink by rememberInfiniteTransition(label = "rec").animateFloat(
            1f, 0.25f, infiniteRepeatable(tween(700), RepeatMode.Reverse), label = "dot"
        )
        val shape = RoundedCornerShape(50)
        Row(
            dragModifier().clip(shape).background(Glass45).border(1.dp, Edge25, shape)
                .padding(start = 12.dp, end = 4.dp, top = 4.dp, bottom = 4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(Modifier.size(9.dp).clip(CircleShape).background(Color(0xFFFF4D4D).copy(alpha = blink)))
            Spacer(Modifier.width(8.dp))
            Text(mmss(sec), color = Color.White, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
            Spacer(Modifier.width(8.dp))
            RoundBtn(Icons.Outlined.Stop, "Stop", Color(0xFFFF4D4D)) { Thread { Recorder.stop() }.start() }
        }
    }
}

// ===================================================================== Calls

/** Small see-through call controls: speaker, mute, end. Drag it anywhere or hide it down to a dot. */
class CallPill(ctx: Context) : FloatingWindow(ctx, "callpill") {
    private var collapsed by mutableStateOf(false)

    fun show() {
        if (isShown) return
        collapsed = false
        val d = ctx.resources.displayMetrics.density
        val (sw, _) = screenSize(ctx)
        overlay.show(newParams((sw / 2 - 100 * d).toInt(), (110 * d).toInt())) { Content() }
    }

    @Composable
    private fun Content() {
        var sec by remember { mutableLongStateOf(0L) }
        LaunchedEffect(Unit) {
            while (true) {
                sec = (SystemClock.elapsedRealtime() - Calls.startedAt) / 1000
                delay(1000)
            }
        }
        val shape = RoundedCornerShape(50)
        if (collapsed) {
            Box(
                dragModifier().size(32.dp).clip(CircleShape).background(Glass45).border(1.dp, Edge25, CircleShape)
                    .clickable { collapsed = false },
                contentAlignment = Alignment.Center
            ) { Icon(Icons.Outlined.Call, "Show call controls", tint = Color(0xFF34D399), modifier = Modifier.size(17.dp)) }
        } else {
            val off = Color.White.copy(alpha = 0.16f)
            Row(
                dragModifier().clip(shape).background(Glass45).border(1.dp, Edge25, shape).padding(4.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Text(mmss(sec), color = Color.White, fontSize = 13.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.padding(start = 8.dp))
                RoundBtn(Icons.Outlined.Speaker, "Speaker", if (Calls.speaker) Color(0xFF2563EB) else off) { Calls.setSpeaker(ctx, !Calls.speaker) }
                RoundBtn(if (Calls.muted) Icons.Outlined.MicOff else Icons.Outlined.Mic, "Mute", if (Calls.muted) Color(0xFF2563EB) else off) { Calls.setMute(ctx, !Calls.muted) }
                RoundBtn(Icons.Outlined.CallEnd, "End call", Color(0xFFFF4D4D)) { Thread { Calls.end(ctx) }.start() }
                RoundBtn(Icons.Outlined.VisibilityOff, "Hide", off) { collapsed = true }
            }
        }
    }
}

/** Compact incoming call banner at the top, so an incoming call does not take over the screen. */
class IncomingBanner(private val ctx: Context) {
    private val overlay = Overlay(ctx)

    fun show() {
        if (overlay.isShown) return
        val d = ctx.resources.displayMetrics.density
        val (sw, _) = screenSize(ctx)
        val lp = WindowManager.LayoutParams(
            (sw - 24 * d).toInt(), WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL or
                WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
            PixelFormat.TRANSLUCENT
        )
        lp.gravity = Gravity.TOP or Gravity.CENTER_HORIZONTAL
        lp.y = (36 * d).toInt()
        overlay.show(lp) { Banner() }
    }

    fun hide() = overlay.remove()

    @Composable
    private fun Banner() {
        val shape = RoundedCornerShape(28.dp)
        Row(
            Modifier.fillMaxWidth().clip(shape)
                .background(Brush.verticalGradient(listOf(Color(0xE60E1424), Color(0xE6081B27))))
                .border(1.dp, Color.White.copy(alpha = 0.28f), shape).padding(14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(Modifier.size(40.dp).clip(CircleShape).background(Color.White.copy(alpha = 0.12f)), contentAlignment = Alignment.Center) {
                Icon(Icons.Outlined.Call, null, tint = Color.White, modifier = Modifier.size(22.dp))
            }
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text("Incoming call", color = Color.White, fontSize = 16.sp, fontWeight = FontWeight.Bold)
                Text("Answer or reject from here", color = Color(0xFFB4C0D8), fontSize = 12.sp)
            }
            RoundBtn(Icons.Outlined.CallEnd, "Reject", Color(0xFFFF4D4D), 44) { Thread { Calls.end(ctx) }.start() }
            Spacer(Modifier.width(12.dp))
            RoundBtn(Icons.Outlined.Call, "Answer", Color(0xFF22C55E), 44) { Thread { Calls.answer(ctx) }.start() }
        }
    }
}

// ============================================================= Recents guard

/**
 * Covers locked apps on the recents screen with a frosted card.
 * The accessibility service finds where each locked app card is and passes the rectangles here.
 * The covers do not take touches, so swiping a card away still works.
 */
class RecentsGuard(private val ctx: Context) {
    private val pool = ArrayList<Overlay>()
    private val wm = ctx.getSystemService(WindowManager::class.java)

    private fun blurOk(): Boolean = Build.VERSION.SDK_INT >= 31 && wm.isCrossWindowBlurEnabled

    private fun params(r: Rect, blur: Boolean): WindowManager.LayoutParams {
        val pad = (6 * ctx.resources.displayMetrics.density).toInt()
        val lp = WindowManager.LayoutParams(
            (r.width() - 2 * pad).coerceAtLeast(40), (r.height() - 2 * pad).coerceAtLeast(40),
            WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE or
                WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
            PixelFormat.TRANSLUCENT
        )
        lp.gravity = Gravity.TOP or Gravity.START
        lp.x = r.left + pad
        lp.y = r.top + pad
        if (blur && Build.VERSION.SDK_INT >= 31) {
            lp.flags = lp.flags or WindowManager.LayoutParams.FLAG_BLUR_BEHIND
            lp.blurBehindRadius = 60
        }
        if (Build.VERSION.SDK_INT >= 28) lp.layoutInDisplayCutoutMode = WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES
        return lp
    }

    fun update(rects: List<Rect>) {
        val blur = LockStore.bool(ctx, "lock_blur", true) && blurOk()
        val n = rects.size.coerceAtMost(6)
        for (i in 0 until n) {
            val lp = params(rects[i], blur)
            if (i < pool.size && pool[i].isShown) pool[i].update(lp)
            else {
                val o = if (i < pool.size) pool[i] else Overlay(ctx).also { pool.add(it) }
                o.show(lp) { Cover(blur) }
            }
        }
        for (i in n until pool.size) pool[i].remove()
    }

    fun clear() { pool.forEach { it.remove() } }

    @Composable
    private fun Cover(blur: Boolean) {
        val base = Palette.cur.bg1
        Box(
            Modifier.fillMaxSize().clip(RoundedCornerShape(22.dp)).background(base.copy(alpha = if (blur) 0.35f else 0.97f)),
            contentAlignment = Alignment.Center
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Icon(Icons.Outlined.Lock, null, tint = TextHi, modifier = Modifier.size(34.dp))
                Spacer(Modifier.height(6.dp))
                Text("Locked", color = TextHi, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
            }
        }
    }
}
