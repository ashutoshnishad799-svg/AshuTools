/*
 * Ashutool - root toolkit for custom ROMs
 * Copyright (C) 2026 Ashutosh Nishad
 * Telegram: @ashutosh_07x (https://t.me/ashutosh_07x)
 *
 * Free software under the GNU GPL v3.0 or later, with the attribution terms in NOTICE.
 * Keep this header and the author credit in every copy and modified version.
 */

package com.ashu.ashutool

import android.os.SystemClock
import android.telephony.TelephonyManager
import android.view.HapticFeedbackConstants
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.SpringSpec
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.wrapContentSize
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Bolt
import androidx.compose.material.icons.outlined.Call
import androidx.compose.material.icons.outlined.CallEnd
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.Mic
import androidx.compose.material.icons.outlined.MicOff
import androidx.compose.material.icons.outlined.MusicNote
import androidx.compose.material.icons.outlined.OpenInBrowser
import androidx.compose.material.icons.outlined.Speaker
import androidx.compose.material.icons.outlined.Stop
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ShaderBrush
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.layout
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.boundsInWindow
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.util.lerp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.abs
import kotlin.math.roundToInt
import kotlin.math.sin

// =====================================================================================================================
// The look of the island. One shape that is always the same piece of black glass: it is small, it grows into a card that
// goes nearly edge to edge, it gives way to another card, it leaves. Everything moves on springs.
//
// How it stays smooth: the size of the shape is worked out in the LAYOUT step from one spring value, not in the
// composition, so while it grows nothing is composed again, only measured and drawn. The window is never resized, the
// card inside it has its full size from the first frame and is only uncovered by the shape growing, and the corner,
// the fade of the two layers and the light edge are all read in the draw step.
// =====================================================================================================================

private fun <T> feelSpring(feel: Int): SpringSpec<T> = when (feel) {
    1 -> spring(dampingRatio = 0.90f, stiffness = 260f)
    2 -> spring(dampingRatio = 0.80f, stiffness = 700f)
    else -> spring(dampingRatio = 0.72f, stiffness = 320f)
}

/** The width of the small shape for each kind of thing, in dp at 100%. */
private fun compactWidth(kind: IKind, d: Float): Float {
    val extra = when (kind) {
        IKind.CALL -> 70f
        IKind.MEDIA -> 56f
        IKind.CHARGE -> 50f
        IKind.RECORD -> 56f
        IKind.NOTE -> 90f
        IKind.FLASH -> 64f
    }
    return (IslandCfg.w + extra) * d
}

/** The height of the open card for each kind of thing, in dp at 100%. */
private fun cardHeight(kind: IKind): Float = when (kind) {
    IKind.CALL -> 176f
    IKind.MEDIA -> 204f
    IKind.CHARGE -> 150f
    IKind.RECORD -> 124f
    IKind.NOTE -> 156f
    IKind.FLASH -> 112f
}

private fun dismissable(a: IAct) = a.kind == IKind.FLASH || a.kind == IKind.NOTE || a.kind == IKind.CHARGE

private fun clock(sec: Long): String {
    val s = sec.coerceAtLeast(0L)
    return if (s >= 3600) "%d:%02d:%02d".format(s / 3600, (s % 3600) / 60, s % 60) else "%02d:%02d".format(s / 60, s % 60)
}

private val AuroraEdge = listOf(Color(0xFF2DE2C0), Color(0xFF60A5FA), Color(0xFFA78BFA), Color(0xFFF472B6), Color(0xFF2DE2C0))
private val RainbowEdge = listOf(Color(0xFFFF6B8B), Color(0xFFFFB84D), Color(0xFFA3E635), Color(0xFF22D3EE), Color(0xFF818CF8), Color(0xFFF472B6), Color(0xFFFF6B8B))

/** The brush of the light edge. The sweep turns with [phase], 0 to 1. */
private fun edgeBrush(style: Int, size: Size, phase: Float, accent: Color): Brush {
    if (style == 1) return SolidColor(accent)
    val colors = (if (style == 3) RainbowEdge else AuroraEdge).map { it.toArgb() }.toIntArray()
    val cx = size.width / 2f
    val cy = size.height / 2f
    val shader = android.graphics.SweepGradient(cx, cy, colors, null)
    val m = android.graphics.Matrix()
    m.postRotate(phase * 360f, cx, cy)
    shader.setLocalMatrix(m)
    return ShaderBrush(shader)
}

private fun openOwner(ctx: android.content.Context, a: IAct) {
    val o = a.onOpen
    when (a.kind) {
        IKind.NOTE -> if (o != null) o() else if (a.pkg.isNotEmpty()) openApp(ctx, a.pkg)
        IKind.MEDIA -> IslandMedia.open(ctx)
        IKind.CALL -> Safety.dialerPkgs(ctx).firstOrNull()?.let { openApp(ctx, it) }
        else -> {}
    }
}

/**
 * The whole island. [onBounds] is told where the shape is in the window, in px, at every frame, so the host can keep the
 * touchable area of the window as small as the shape.
 */
@Composable
fun IslandRoot(onBounds: (Int, Int, Int, Int) -> Unit) {
    val ctx = LocalContext.current
    val view = LocalView.current
    val density = LocalDensity.current
    val d = IslandCfg.scale / 100f
    val m = IslandHub.mainAct()
    val open = IslandHub.expanded && m != null
    val screenW = IslandHub.screenWdp.toFloat()

    val spring = feelSpring<Float>(IslandCfg.feel)
    val cw = animateFloatAsState(if (m == null) IslandCfg.w * d else compactWidth(m.kind, d), spring, label = "isl-cw")
    val ch = animateFloatAsState(IslandCfg.h * d, spring, label = "isl-ch")
    val ew = animateFloatAsState(minOf(screenW - 20f, 520f).coerceAtLeast(240f), spring, label = "isl-ew")
    val eh = animateFloatAsState(if (m != null) cardHeight(m.kind) * d else IslandCfg.h * d, spring, label = "isl-eh")
    // 0 is the small shape, 1 is the open card. It overshoots a little, which is the bounce.
    val p = animateFloatAsState(if (open) 1f else 0f, feelSpring<Float>(IslandCfg.feel), label = "isl-p")

    // A second thing at the same time is a small bubble beside the island. The island itself stays in the middle.
    val second = if (IslandCfg.bubble) IslandHub.sideAct() else null
    val slot = animateFloatAsState(if (second != null && !open) IslandCfg.h * d + 8f else 0f, spring, label = "isl-slot")
    val rects = remember { Array(2) { IntArray(4) } }

    val showCard by remember { derivedStateOf { p.value > 0.02f } }
    val showPill by remember { derivedStateOf { p.value < 0.98f } }

    // the entrance, and a small pop whenever something new takes the front
    val appear = remember { Animatable(0f) }
    LaunchedEffect(Unit) { appear.animateTo(1f, tween(320, easing = FastOutSlowInEasing)) }
    val pop = remember { Animatable(1f) }
    LaunchedEffect(m?.id) {
        if (m != null) {
            pop.snapTo(0.95f)
            pop.animateTo(1f, spring(dampingRatio = 0.5f, stiffness = 420f))
        }
    }

    // the light edge moves only in the flow style
    val phase = remember { mutableFloatStateOf(0f) }
    LaunchedEffect(IslandCfg.edge) {
        if (IslandCfg.edge == 4) {
            while (true) withFrameNanos { t -> phase.floatValue = (t / 1_000_000_000f * 0.12f) % 1f }
        }
    }

    // the card closes by itself a few seconds after the last touch, except while a call rings
    LaunchedEffect(open, IslandHub.touch, m?.id) {
        val ringing = m?.kind == IKind.CALL && Calls.state == TelephonyManager.CALL_STATE_RINGING
        if (open && IslandCfg.hold > 0 && !ringing) {
            delay(IslandCfg.hold * 1000L)
            IslandHub.collapse()
        }
    }

    val mNow by rememberUpdatedState(m)
    fun tick() { if (IslandCfg.hapt) view.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP) }

    /** A drag ended: up closes or dismisses, down opens, left and right change song or flip between things. */
    fun swiped(dx: Float, dy: Float, limit: Float) {
        val cur = mNow ?: return
        if (abs(dy) >= abs(dx)) {
            if (dy < -limit) {
                if (IslandHub.expanded) IslandHub.collapse() else if (dismissable(cur)) IslandHub.remove(cur.id)
            } else if (dy > limit && !IslandHub.expanded) IslandHub.toggle()
        } else if (abs(dx) > limit * 1.5f) {
            val dir = if (dx > 0f) 1 else -1
            if (cur.kind == IKind.MEDIA && !IslandHub.expanded) {
                if (dir > 0) IslandMedia.next() else IslandMedia.prev()
            } else IslandHub.cycle(dir)
            tick()
        }
    }

    /** The corner of the shape in dp: round when small, softer when open. */
    fun cornerDp(): Float {
        val pr = p.value
        val w = lerp(cw.value, ew.value, pr)
        val h = lerp(ch.value, eh.value, pr)
        val c = lerp(ch.value / 2f, 34f * d, pr.coerceIn(0f, 1f))
        return c.coerceAtMost(minOf(w, h) / 2f).coerceAtLeast(0f)
    }

    /** Tells the host where the touchable area is: the island and the bubble, nothing between or around. */
    fun report() {
        var l = Int.MAX_VALUE
        var t = Int.MAX_VALUE
        var r = Int.MIN_VALUE
        var b = Int.MIN_VALUE
        for (q in rects) {
            if (q[2] > q[0]) { l = minOf(l, q[0]); t = minOf(t, q[1]); r = maxOf(r, q[2]); b = maxOf(b, q[3]) }
        }
        if (r > l) onBounds(l - 10, t - 10, r + 10, b + 10)
    }

    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.TopCenter) {
      Row(Modifier.padding(top = IslandCfg.top.dp)) {
        Spacer(Modifier.width(slot.value.dp))
        Box(
            Modifier
                .layout { measurable, _ ->
                    val pr = p.value
                    val wPx = (lerp(cw.value, ew.value, pr) * density.density).roundToInt().coerceAtLeast(2)
                    val hPx = (lerp(ch.value, eh.value, pr) * density.density).roundToInt().coerceAtLeast(2)
                    val placeable = measurable.measure(Constraints.fixed(wPx, hPx))
                    layout(wPx, hPx) { placeable.place(0, 0) }
                }
                .onGloballyPositioned { c ->
                    val b = c.boundsInWindow()
                    rects[0][0] = b.left.toInt(); rects[0][1] = b.top.toInt(); rects[0][2] = b.right.toInt(); rects[0][3] = b.bottom.toInt()
                    report()
                }
                .graphicsLayer {
                    shape = RoundedCornerShape(cornerDp().dp)
                    clip = true
                    val a = appear.value
                    alpha = a.coerceIn(0f, 1f)
                    val sc = (0.7f + 0.3f * a) * pop.value
                    scaleX = sc
                    scaleY = sc
                    transformOrigin = TransformOrigin(0.5f, 0f)
                }
                .background(if (IslandCfg.glass) Color(0xBF0E1217) else Color.Black)
                .drawWithContent {
                    drawContent()
                    if (IslandCfg.glass) {
                        // the glass: a light top that fades out and a thin bright rim
                        drawRect(Brush.verticalGradient(listOf(Color.White.copy(alpha = 0.20f), Color.Transparent), endY = size.height * 0.6f))
                        drawRoundRect(Color.White.copy(alpha = 0.22f), Offset.Zero, size, CornerRadius(cornerDp() * density.density), style = Stroke(1.dp.toPx()))
                    }
                    val style = IslandCfg.edge
                    val power = IslandCfg.edgePwr
                    if (style != 0 && power > 0) {
                        val line = 2.2f * density.density
                        val corner = CornerRadius(cornerDp() * density.density)
                        val brush = edgeBrush(style, size, phase.floatValue, mNow?.accent ?: Color(0xFFA78BFA))
                        val a = power / 100f
                        drawRoundRect(brush, Offset.Zero, size, corner, alpha = a * 0.30f, style = Stroke(line * 2.6f))
                        drawRoundRect(brush, Offset.Zero, size, corner, alpha = a * 0.85f, style = Stroke(line))
                    }
                }
                .pointerInput(Unit) {
                    // any touch keeps an open card open. Nothing is consumed here.
                    awaitPointerEventScope {
                        while (true) {
                            val e = awaitPointerEvent(PointerEventPass.Initial)
                            if (e.type == PointerEventType.Press) IslandHub.touch++
                        }
                    }
                }
                .pointerInput(Unit) {
                    detectTapGestures(
                        onTap = { if (mNow != null) { tick(); IslandHub.toggle() } },
                        onLongPress = { mNow?.let { tick(); openOwner(ctx, it) } }
                    )
                }
                .pointerInput(Unit) {
                    val limit = 18.dp.toPx()
                    var dx = 0f
                    var dy = 0f
                    detectDragGestures(
                        onDragStart = { dx = 0f; dy = 0f },
                        onDragEnd = { swiped(dx, dy, limit) },
                        onDragCancel = { },
                        onDrag = { change, amount -> change.consume(); dx += amount.x; dy += amount.y }
                    )
                }
        ) {
            if (m != null && showPill) {
                Box(
                    Modifier
                        .graphicsLayer { alpha = (1f - p.value / 0.25f).coerceIn(0f, 1f) }
                        .wrapContentSize(Alignment.TopCenter, unbounded = true)
                        .size(cw.value.dp, ch.value.dp)
                ) { PillContent(m, d) }
            }
            if (m != null && showCard) {
                Box(
                    Modifier
                        .graphicsLayer { alpha = ((p.value - 0.12f) / 0.5f).coerceIn(0f, 1f) }
                        .wrapContentSize(Alignment.TopCenter, unbounded = true)
                        .size(ew.value.dp, (cardHeight(m.kind) * d).dp)
                ) { CardContent(m, d) }
            }
        }
        Box(Modifier.width(slot.value.dp), contentAlignment = Alignment.TopStart) {
            if (second != null && slot.value > 2f) {
                Box(
                    Modifier
                        .padding(start = 8.dp)
                        .size(ch.value.dp)
                        .onGloballyPositioned { c ->
                            val b = c.boundsInWindow()
                            rects[1][0] = b.left.toInt(); rects[1][1] = b.top.toInt(); rects[1][2] = b.right.toInt(); rects[1][3] = b.bottom.toInt()
                            report()
                        }
                        .clip(CircleShape)
                        .background(if (IslandCfg.glass) Color(0xBF0E1217) else Color.Black)
                        .border(1.dp, second.accent.copy(alpha = 0.55f), CircleShape)
                        .pointerInput(second.id) { detectTapGestures(onTap = { tick(); IslandHub.focus(second.id) }) },
                    contentAlignment = Alignment.Center
                ) {
                    Lead(second, ch.value.dp * 0.66f)
                    DisposableEffect(Unit) { onDispose { rects[1].fill(0); report() } }
                }
            }
        }
      }
    }
}

// ============================================================================================= small pieces

@Composable
private fun Lead(a: IAct, size: Dp) {
    val art = IslandMedia.art
    when {
        a.kind == IKind.MEDIA && art != null ->
            Image(art, null, Modifier.size(size).clip(RoundedCornerShape(size * 0.28f)), contentScale = ContentScale.Crop)
        a.pkg.isNotEmpty() && a.kind != IKind.MEDIA -> AppIcon(a.pkg, size)
        else -> Box(
            Modifier.size(size).clip(CircleShape).background(a.accent.copy(alpha = 0.24f)),
            contentAlignment = Alignment.Center
        ) {
            if (a.glyph.isNotEmpty()) Text(a.glyph, color = a.accent, fontSize = (size.value * 0.40f).sp, fontWeight = FontWeight.ExtraBold, maxLines = 1)
            else a.icon?.let { Icon(it, null, tint = a.accent, modifier = Modifier.size(size * 0.58f)) }
        }
    }
}

/** Five bars that rise and fall like a sound wave while something plays, and lie flat when it is paused. */
@Composable
private fun Wave(playing: Boolean, color: Color, height: Dp) {
    val t = if (playing) rememberInfiniteTransition(label = "wave") else null
    val ph: State<Float>? = t?.animateFloat(0f, 6.2831855f, infiniteRepeatable(tween(1300, easing = LinearEasing)), label = "wave-phase")
    Canvas(Modifier.size(height * 1.1f, height)) {
        val n = 5
        val bw = size.width / (n * 2f - 1f)
        val phase = ph?.value ?: 0f
        for (i in 0 until n) {
            val k = if (ph == null) 0.28f else 0.30f + 0.70f * ((sin(phase + i * 1.25f) + 1f) / 2f)
            val bh = size.height * k
            drawRoundRect(color, Offset(i * 2f * bw, (size.height - bh) / 2f), Size(bw, bh), CornerRadius(bw / 2f))
        }
    }
}

@Composable
private fun PulseDot(color: Color, size: Dp) {
    val t = rememberInfiniteTransition(label = "dot")
    val a by t.animateFloat(0.35f, 1f, infiniteRepeatable(tween(700, easing = FastOutSlowInEasing), RepeatMode.Reverse), label = "dot-alpha")
    Box(Modifier.size(size).graphicsLayer { alpha = a }.clip(CircleShape).background(color))
}

@Composable
private fun MiniBattery(level: Int, color: Color, w: Dp, h: Dp) {
    Canvas(Modifier.size(w, h)) {
        val sw = 1.4.dp.toPx()
        val bodyW = size.width - 2.5.dp.toPx()
        drawRoundRect(color.copy(alpha = 0.55f), Offset(sw / 2f, sw / 2f), Size(bodyW - sw, size.height - sw), CornerRadius(size.height * 0.3f), style = Stroke(sw))
        val fw = (bodyW - 2f * sw - 1f).coerceAtLeast(0f) * (level / 100f).coerceIn(0f, 1f)
        drawRoundRect(color, Offset(sw * 1.5f, sw * 1.5f), Size(fw, (size.height - sw * 3f).coerceAtLeast(1f)), CornerRadius(size.height * 0.2f))
        drawRoundRect(color.copy(alpha = 0.55f), Offset(bodyW, size.height * 0.3f), Size(2.dp.toPx(), size.height * 0.4f), CornerRadius(1f))
    }
}

@Composable
private fun ProgressRing(frac: Float, color: Color, dia: Dp, stroke: Dp) {
    Canvas(Modifier.size(dia)) {
        val sw = stroke.toPx()
        val inset = sw / 2f
        val sz = Size(this.size.width - sw, this.size.height - sw)
        drawArc(Color.White.copy(alpha = 0.16f), -90f, 360f, false, Offset(inset, inset), sz, style = Stroke(sw))
        drawArc(color, -90f, 360f * frac.coerceIn(0f, 1f), false, Offset(inset, inset), sz, style = Stroke(sw, cap = androidx.compose.ui.graphics.StrokeCap.Round))
    }
}

@Composable
private fun Elapsed(since: Long, color: Color, fs: Float) {
    var sec by remember { mutableLongStateOf(0L) }
    LaunchedEffect(since) {
        while (true) {
            sec = if (since > 0L) (SystemClock.elapsedRealtime() - since) / 1000L else 0L
            delay(1000L)
        }
    }
    Text(clock(sec), color = color, fontSize = fs.sp, fontWeight = FontWeight.Medium, maxLines = 1)
}

@Composable
private fun RoundBtn(bg: Color, size: Dp, onClick: () -> Unit, content: @Composable () -> Unit) {
    Box(
        Modifier.bounceClick(onClick = onClick).size(size).clip(CircleShape).background(bg),
        contentAlignment = Alignment.Center
    ) { content() }
}

/** A wide rounded button with a label, and an icon if you give one. */
@Composable
private fun Capsule(text: String, bg: Color, fg: Color, h: Dp, fs: Float, modifier: Modifier = Modifier, icon: ImageVector? = null, onClick: () -> Unit) {
    Row(
        modifier.bounceClick(onClick = onClick).height(h).clip(RoundedCornerShape(50)).background(bg).padding(horizontal = 14.dp),
        horizontalArrangement = Arrangement.Center, verticalAlignment = Alignment.CenterVertically
    ) {
        if (icon != null) {
            Icon(icon, null, tint = fg, modifier = Modifier.size((fs * 1.5f).dp))
            Spacer(Modifier.width(6.dp))
        }
        Text(text, color = fg, fontSize = fs.sp, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

/** A bar you can tap or drag to jump to a place in the song. */
@Composable
private fun SeekBar(frac: Float, accent: Color, enabled: Boolean, onSeek: (Float) -> Unit, modifier: Modifier = Modifier) {
    var drag by remember { mutableStateOf<Float?>(null) }
    val shown = (drag ?: frac).coerceIn(0f, 1f)
    val seekNow by rememberUpdatedState(onSeek)
    Canvas(
        modifier
            .height(22.dp)
            .pointerInput(enabled) {
                if (!enabled) return@pointerInput
                detectTapGestures { o -> seekNow((o.x / size.width).coerceIn(0f, 1f)) }
            }
            .pointerInput(enabled) {
                if (!enabled) return@pointerInput
                detectHorizontalDragGestures(
                    onDragStart = { o -> drag = (o.x / size.width).coerceIn(0f, 1f) },
                    onDragEnd = { drag?.let { seekNow(it) }; drag = null },
                    onDragCancel = { drag = null },
                    onHorizontalDrag = { ch, amount -> ch.consume(); drag = ((drag ?: 0f) + amount / size.width).coerceIn(0f, 1f) }
                )
            }
    ) {
        val cy = size.height / 2f
        val th = 5.dp.toPx()
        drawRoundRect(Color.White.copy(alpha = 0.18f), Offset(0f, cy - th / 2f), Size(size.width, th), CornerRadius(th / 2f))
        drawRoundRect(accent, Offset(0f, cy - th / 2f), Size(size.width * shown, th), CornerRadius(th / 2f))
        drawCircle(Color.White, 6.dp.toPx(), Offset(size.width * shown, cy))
    }
}

// ============================================================================================= the small shape

@Composable
private fun PillContent(a: IAct, d: Float) {
    val h = (IslandCfg.h * d).dp
    val pad = (h.value * 0.22f).dp
    val lead = h * 0.66f
    val s by Monitor.snap.collectAsState()
    val title = when (a.kind) {
        IKind.CALL -> Calls.caller.ifEmpty { if (Calls.state == TelephonyManager.CALL_STATE_RINGING) "Incoming call" else "On call" }
        IKind.MEDIA -> IslandMedia.title.ifEmpty { "Playing" }
        IKind.CHARGE -> "${s.batt.level}%"
        IKind.RECORD -> "REC"
        else -> a.title
    }
    Row(Modifier.fillMaxSize().padding(horizontal = pad), verticalAlignment = Alignment.CenterVertically) {
        Lead(a, lead)
        Spacer(Modifier.width(7.dp))
        Text(title, color = Color.White, fontSize = (12f * d).sp, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
        Spacer(Modifier.width(6.dp))
        when (a.kind) {
            IKind.CALL ->
                if (Calls.state == TelephonyManager.CALL_STATE_OFFHOOK) Elapsed(Calls.startedAt, Color(0xFFB4B4BE), 11f * d)
                else Text("Ringing", color = Color(0xFFB4B4BE), fontSize = (10f * d).sp, maxLines = 1)
            IKind.MEDIA -> Wave(IslandMedia.playing, a.accent, lead * 0.7f)
            IKind.CHARGE -> MiniBattery(s.batt.level, a.accent, (22f * d).dp, (11f * d).dp)
            IKind.RECORD -> Row(verticalAlignment = Alignment.CenterVertically) {
                PulseDot(a.accent, (7f * d).dp)
                Spacer(Modifier.width(5.dp))
                Elapsed(Recorder.startedAt, Color(0xFFB4B4BE), 11f * d)
            }
            else -> {}
        }
    }
}

// ============================================================================================= the open card

@Composable
private fun CardContent(a: IAct, d: Float) {
    Column(
        Modifier.fillMaxSize().padding(horizontal = (18f * d).dp, vertical = (16f * d).dp),
        verticalArrangement = Arrangement.Center
    ) {
        when (a.kind) {
            IKind.CALL -> CallCard(a, d)
            IKind.MEDIA -> MediaCard(a, d)
            IKind.CHARGE -> ChargeCard(a, d)
            IKind.RECORD -> RecordCard(a, d)
            IKind.NOTE -> NoteCard(a, d)
            IKind.FLASH -> FlashCard(a, d)
        }
    }
}

private val Sub = Color(0xFFB4B4BE)

@Composable
private fun CallCard(a: IAct, d: Float) {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    val ringing = Calls.state == TelephonyManager.CALL_STATE_RINGING
    val name = Calls.caller.ifEmpty { if (ringing) "Incoming call" else "On call" }
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(
            Modifier.size((50f * d).dp).clip(CircleShape).background(a.accent.copy(alpha = 0.22f)),
            contentAlignment = Alignment.Center
        ) { Icon(Icons.Outlined.Call, null, tint = a.accent, modifier = Modifier.size((24f * d).dp)) }
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(name, color = Color.White, fontSize = (17f * d).sp, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
            if (ringing) Text("Incoming call", color = Sub, fontSize = (12f * d).sp)
            else Elapsed(Calls.startedAt, Sub, 12f * d)
        }
    }
    Spacer(Modifier.height((14f * d).dp))
    if (ringing) {
        val bh = (46f * d).dp
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Capsule("Decline", Color(0xFFFF4D6D), Color.White, bh, 14f * d, Modifier.weight(1f), Icons.Outlined.CallEnd) { scope.launch(Dispatchers.IO) { Calls.end(ctx) } }
            Capsule("Answer", Color(0xFF34D399), Color(0xFF05101B), bh, 14f * d, Modifier.weight(1f), Icons.Outlined.Call) { scope.launch(Dispatchers.IO) { Calls.answer(ctx) } }
        }
    } else {
        val bs = (46f * d).dp
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly, verticalAlignment = Alignment.CenterVertically) {
            RoundBtn(if (Calls.speaker) Color.White else Color.White.copy(alpha = 0.16f), bs, { Calls.setSpeaker(ctx, !Calls.speaker) }) {
                Icon(Icons.Outlined.Speaker, "Speaker", tint = if (Calls.speaker) Color.Black else Color.White, modifier = Modifier.size(bs * 0.5f))
            }
            RoundBtn(if (Calls.muted) Color.White else Color.White.copy(alpha = 0.16f), bs, { Calls.setMute(ctx, !Calls.muted) }) {
                Icon(if (Calls.muted) Icons.Outlined.MicOff else Icons.Outlined.Mic, "Mute", tint = if (Calls.muted) Color.Black else Color.White, modifier = Modifier.size(bs * 0.5f))
            }
            RoundBtn(Color(0xFFFF4D6D), bs, { scope.launch(Dispatchers.IO) { Calls.end(ctx) } }) {
                Icon(Icons.Outlined.CallEnd, "End call", tint = Color.White, modifier = Modifier.size(bs * 0.5f))
            }
        }
    }
}

/** Play, pause, next and previous, drawn by hand so they match whatever size they are used at. */
@Composable
private fun MediaGlyph(kind: Int, color: Color, sz: Dp) {
    Canvas(Modifier.size(sz)) {
        val w = this.size.width
        val h = this.size.height
        when (kind) {
            1 -> drawPath(androidx.compose.ui.graphics.Path().apply { moveTo(w * 0.28f, h * 0.16f); lineTo(w * 0.28f, h * 0.84f); lineTo(w * 0.86f, h * 0.5f); close() }, color)
            2 -> {
                drawRoundRect(color, Offset(w * 0.22f, h * 0.16f), Size(w * 0.21f, h * 0.68f), CornerRadius(w * 0.05f))
                drawRoundRect(color, Offset(w * 0.57f, h * 0.16f), Size(w * 0.21f, h * 0.68f), CornerRadius(w * 0.05f))
            }
            3 -> {
                drawPath(androidx.compose.ui.graphics.Path().apply { moveTo(w * 0.16f, h * 0.2f); lineTo(w * 0.16f, h * 0.8f); lineTo(w * 0.66f, h * 0.5f); close() }, color)
                drawRoundRect(color, Offset(w * 0.72f, h * 0.2f), Size(w * 0.12f, h * 0.6f), CornerRadius(w * 0.03f))
            }
            else -> {
                drawPath(androidx.compose.ui.graphics.Path().apply { moveTo(w * 0.84f, h * 0.2f); lineTo(w * 0.84f, h * 0.8f); lineTo(w * 0.34f, h * 0.5f); close() }, color)
                drawRoundRect(color, Offset(w * 0.16f, h * 0.2f), Size(w * 0.12f, h * 0.6f), CornerRadius(w * 0.03f))
            }
        }
    }
}

@Composable
private fun MediaCard(a: IAct, d: Float) {
    var pos by remember { mutableLongStateOf(0L) }
    LaunchedEffect(IslandMedia.playing, IslandMedia.title) {
        while (true) {
            pos = IslandMedia.positionMs()
            delay(250L)
        }
    }
    val dur = IslandMedia.durationMs
    val frac = if (dur > 0L) (pos.toFloat() / dur).coerceIn(0f, 1f) else 0f
    Row(verticalAlignment = Alignment.CenterVertically) {
        val art = IslandMedia.art
        Box(Modifier.size((58f * d).dp).clip(RoundedCornerShape(15.dp)).background(a.accent.copy(alpha = 0.22f)), contentAlignment = Alignment.Center) {
            if (art != null) Image(art, null, Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
            else Icon(Icons.Outlined.MusicNote, null, tint = a.accent, modifier = Modifier.size((28f * d).dp))
        }
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(IslandMedia.title.ifEmpty { "Playing" }, color = Color.White, fontSize = (16f * d).sp, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(IslandMedia.artist, color = Sub, fontSize = (12.5f * d).sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        Wave(IslandMedia.playing, a.accent, (22f * d).dp)
    }
    Spacer(Modifier.height(6.dp))
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(clock(pos / 1000L), color = Sub, fontSize = (10f * d).sp, modifier = Modifier.width(40.dp))
        SeekBar(frac, a.accent, dur > 0L, { f -> IslandMedia.seekTo((f * dur).toLong()) }, Modifier.weight(1f))
        Text(if (dur > 0L) clock(dur / 1000L) else "--:--", color = Sub, fontSize = (10f * d).sp, modifier = Modifier.width(40.dp), textAlign = TextAlign.End)
    }
    Spacer(Modifier.height(4.dp))
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly, verticalAlignment = Alignment.CenterVertically) {
        RoundBtn(Color.Transparent, (44f * d).dp, { IslandMedia.prev() }) { MediaGlyph(0, Color.White, (24f * d).dp) }
        RoundBtn(a.accent.copy(alpha = 0.30f), (52f * d).dp, { IslandMedia.toggle() }) { MediaGlyph(if (IslandMedia.playing) 2 else 1, Color.White, (28f * d).dp) }
        RoundBtn(Color.Transparent, (44f * d).dp, { IslandMedia.next() }) { MediaGlyph(3, Color.White, (24f * d).dp) }
    }
}

@Composable
private fun StatBlock(value: String, label: String, d: Float) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(value, color = Color.White, fontSize = (13f * d).sp, fontWeight = FontWeight.Bold, maxLines = 1)
        Text(label, color = Sub, fontSize = (10f * d).sp, maxLines = 1)
    }
}

@Composable
private fun ChargeCard(a: IAct, d: Float) {
    val s by Monitor.snap.collectAsState()
    val b = s.batt
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size((62f * d).dp), contentAlignment = Alignment.Center) {
            ProgressRing((b.level / 100f).coerceIn(0f, 1f), a.accent, (62f * d).dp, 6.dp)
            Text("${b.level}", color = Color.White, fontSize = (18f * d).sp, fontWeight = FontWeight.Bold)
        }
        Spacer(Modifier.width(14.dp))
        Column(Modifier.weight(1f)) {
            Text(if (b.charging) "Charging" else "Battery", color = Color.White, fontSize = (17f * d).sp, fontWeight = FontWeight.Bold)
            Text(if (b.plugged != "-") b.plugged else b.status, color = Sub, fontSize = (12f * d).sp, maxLines = 1)
        }
        Icon(Icons.Outlined.Bolt, null, tint = a.accent, modifier = Modifier.size((26f * d).dp))
    }
    Spacer(Modifier.height(10.dp))
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
        StatBlock("${b.ma} mA", "Current", d)
        StatBlock("%.1f\u00B0C".format(b.tempC), "Temperature", d)
        StatBlock(if (b.mv > 0) "%.2f V".format(b.mv / 1000f) else "-", "Voltage", d)
    }
}

@Composable
private fun RecordCard(a: IAct, d: Float) {
    val scope = rememberCoroutineScope()
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size((44f * d).dp), contentAlignment = Alignment.Center) { PulseDot(a.accent, (16f * d).dp) }
        Spacer(Modifier.width(10.dp))
        Column(Modifier.weight(1f)) {
            Text("Recording", color = Color.White, fontSize = (17f * d).sp, fontWeight = FontWeight.Bold)
            Elapsed(Recorder.startedAt, Sub, 13f * d)
        }
        Capsule("Stop", Color(0xFFFF4D6D), Color.White, (40f * d).dp, 13f * d, Modifier, Icons.Outlined.Stop) { scope.launch(Dispatchers.IO) { Recorder.stop() } }
    }
}

@Composable
private fun NoteCard(a: IAct, d: Float) {
    val ctx = LocalContext.current
    val label = remember(a.pkg) { islandAppName(ctx, a.pkg) }
    val bs = (36f * d).dp
    Row(verticalAlignment = Alignment.CenterVertically) {
        AppIcon(a.pkg, (24f * d).dp)
        Spacer(Modifier.width(8.dp))
        Text(label, color = Sub, fontSize = (11.5f * d).sp, fontWeight = FontWeight.Medium, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
        Text("now", color = Sub, fontSize = (10.5f * d).sp)
    }
    Spacer(Modifier.height(6.dp))
    Text(a.title, color = Color.White, fontSize = (16f * d).sp, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
    Text(a.sub, color = Sub, fontSize = (13f * d).sp, maxLines = 2, overflow = TextOverflow.Ellipsis)
    Spacer(Modifier.height(8.dp))
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.End) {
        RoundBtn(Color.White.copy(alpha = 0.14f), bs, { IslandHub.remove(a.id) }) {
            Icon(Icons.Outlined.Close, "Dismiss", tint = Color.White, modifier = Modifier.size(bs * 0.5f))
        }
        Spacer(Modifier.width(10.dp))
        RoundBtn(a.accent, bs, {
            val o = a.onOpen
            if (o != null) o() else openApp(ctx, a.pkg)
            IslandHub.remove(a.id)
        }) { Icon(Icons.Outlined.OpenInBrowser, "Open", tint = Color(0xFF05101B), modifier = Modifier.size(bs * 0.5f)) }
    }
}

@Composable
private fun FlashCard(a: IAct, d: Float) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Lead(a, (48f * d).dp)
        Spacer(Modifier.width(14.dp))
        Column(Modifier.weight(1f)) {
            Text(a.title, color = Color.White, fontSize = (18f * d).sp, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
            if (a.sub.isNotEmpty()) Text(a.sub, color = Sub, fontSize = (12.5f * d).sp, maxLines = 2, overflow = TextOverflow.Ellipsis)
        }
    }
}
