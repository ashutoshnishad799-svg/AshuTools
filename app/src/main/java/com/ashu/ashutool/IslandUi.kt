/*
 * Ashutool - root toolkit for custom ROMs
 * Copyright (C) 2026 Ashutosh Nishad
 * Telegram: @ashutosh_07x (https://t.me/ashutosh_07x)
 *
 * Free software under the GNU GPL v3.0 or later, with the attribution terms in NOTICE.
 * Keep this header and the author credit in every copy and modified version.
 */


package com.ashu.ashutool

import android.app.AlarmManager
import android.content.Context
import android.os.SystemClock
import android.telephony.TelephonyManager
import android.text.format.DateFormat
import android.view.HapticFeedbackConstants
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.ContentTransform
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.SpringSpec
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.abs
import kotlin.math.sin

// =====================================================================================================================
// The look of the Dynamic Island. This is Ashutool's own design:
//   - a shape that morphs with a spring between the small, the idle and the expanded state
//   - twelve skins, a soft shine on top and a tint of the app colour that fills the expanded card
//   - a small shape that can be split in two halves around the camera hole (Camera gap)
//   - live parts drawn by hand: a wave for music, a ring for downloads and timers, a battery, a pulsing dot
//   - idle widgets that roll over each other (clock, date, battery, speed, next alarm)
//   - swipes on the small shape: next and previous song, dismiss, cycle through things
// Inspired by Smart Island by agupta07505, see IslandCredits.kt.
// =====================================================================================================================

/** One look for the island: the inside, the edge and the text colours. */
class ISkin(
    val name: String,
    val fill: List<Color>,
    val border: List<Color>,
    val borderW: Float,
    val text: Color,
    val sub: Color,
    /** How strong the soft shine on the top of the shape is. */
    val gloss: Float = 0.10f
)

private fun c(v: Long) = Color(v)

val ISkins: List<ISkin> = listOf(
    ISkin("Classic", listOf(c(0xFF000000), c(0xFF000000)), listOf(c(0x44FFFFFF), c(0x14FFFFFF)), 0.8f, Color.White, c(0xFFB4B4BE), 0.07f),
    ISkin("Glass", listOf(c(0xCC1E2440), c(0xB20C1226)), listOf(c(0xAAFFFFFF), c(0x22FFFFFF), c(0x77FFFFFF)), 1.2f, Color.White, c(0xFFC4CCE0), 0.18f),
    ISkin("Aurora", listOf(c(0xFF0B0620), c(0xFF171045)), listOf(c(0xFFA78BFA), c(0xFF38BDF8)), 1.4f, Color.White, c(0xFFC9C2F5)),
    ISkin("Neon", listOf(c(0xFF04050A), c(0xFF080A14)), listOf(c(0xFF22D3EE), c(0xFFFF2BD6)), 1.6f, Color.White, c(0xFFA5F3FC), 0.06f),
    ISkin("Sunset", listOf(c(0xFF2B0F2A), c(0xFF3F1A1C)), listOf(c(0xFFFFB84D), c(0xFFFF5C8A)), 1.4f, Color.White, c(0xFFFFD3C2)),
    ISkin("Ocean", listOf(c(0xFF04243A), c(0xFF06304E)), listOf(c(0xFF22D3EE), c(0xFF3B82F6)), 1.4f, Color.White, c(0xFFBAE6FD)),
    ISkin("Mint", listOf(c(0xFF06251A), c(0xFF0C3320)), listOf(c(0xFF34D399), c(0xFFA3E635)), 1.4f, Color.White, c(0xFFBBF7D0)),
    ISkin("Light", listOf(c(0xF2F7F8FB), c(0xF2E4E9F2)), listOf(c(0x66FFFFFF), c(0x33000000)), 1.0f, c(0xFF0F172A), c(0xFF475569), 0.25f),
    ISkin("Midnight", listOf(c(0xFF050816), c(0xFF0B1230)), listOf(c(0xFF6C8CFF), c(0xFF1E2A5A)), 1.2f, Color.White, c(0xFFB9C3F5)),
    ISkin("Rose", listOf(c(0xFF2A0B1C), c(0xFF3D1029)), listOf(c(0xFFFF7AB6), c(0xFFFFC2D9)), 1.3f, Color.White, c(0xFFFFC7DC)),
    ISkin("Carbon", listOf(c(0xFF121212), c(0xFF1E1E22)), listOf(c(0x66FFFFFF), c(0x1AFFFFFF)), 1.0f, Color.White, c(0xFFA1A1AA), 0.14f),
    ISkin("Gold", listOf(c(0xFF1A1206), c(0xFF2B1D08)), listOf(c(0xFFFFD166), c(0xFFB8860B)), 1.3f, Color.White, c(0xFFEBD9A8))
)

val ISkinNames: List<String> get() = ISkins.map { it.name }
val IFxNames = listOf("None", "Glow", "Pulse", "Sweep", "Rainbow")
val IShapeNames = listOf("Pill", "Notch", "Squircle", "Tab")
val IAnimNames = listOf("Bouncy", "Smooth", "Snappy", "Elastic")

private class IStyle(val d: Float, val skin: ISkin, val accent: Color)

private fun <T> springFor(i: Int): SpringSpec<T> = when (i) {
    1 -> spring(dampingRatio = 0.92f, stiffness = 260f)
    2 -> spring(dampingRatio = 0.80f, stiffness = 760f)
    3 -> spring(dampingRatio = 0.34f, stiffness = 300f)
    else -> spring(dampingRatio = 0.55f, stiffness = 380f)
}

private fun islandShape(style: Int, r: Dp): Shape = when (style) {
    1 -> RoundedCornerShape(topStart = r.coerceAtMost(5.dp), topEnd = r.coerceAtMost(5.dp), bottomEnd = r, bottomStart = r)
    2 -> RoundedCornerShape(r * 0.62f)
    3 -> RoundedCornerShape(topStart = r, topEnd = r, bottomEnd = r * 0.35f, bottomStart = r * 0.35f)
    else -> RoundedCornerShape(r)
}

private val RainbowColors = listOf(c(0xFFFF4D6D), c(0xFFFFB84D), c(0xFFA3E635), c(0xFF22D3EE), c(0xFF6366F1), c(0xFFFF2BD6), c(0xFFFF4D6D))

private fun clock(sec: Long): String {
    val s = sec.coerceAtLeast(0L)
    return if (s >= 3600) "%d:%02d:%02d".format(s / 3600, (s % 3600) / 60, s % 60) else "%02d:%02d".format(s / 60, s % 60)
}

private fun hm(ctx: Context, ms: Long): String =
    SimpleDateFormat(if (DateFormat.is24HourFormat(ctx)) "HH:mm" else "h:mm", Locale.getDefault()).format(Date(ms))

private fun nextAlarm(ctx: Context): Long = try {
    ctx.getSystemService(AlarmManager::class.java)?.nextAlarmClock?.triggerTime ?: 0L
} catch (_: Exception) { 0L }

private fun dismissable(a: IAct) = a.kind == IKind.FLASH || a.kind == IKind.NOTE || a.kind == IKind.CHARGE ||
    a.kind == IKind.PROGRESS || a.kind == IKind.TIMER

/** The whole island. It lives in its own overlay window, see [IslandHost]. */
@Composable
fun IslandRoot() {
    val ctx = LocalContext.current
    val view = LocalView.current
    val d = IslandCfg.scale / 100f
    val skin = ISkins[IslandCfg.skin.coerceIn(0, ISkins.size - 1)]
    val m = IslandHub.mainAct()
    val side = IslandHub.sideAct()
    val expanded = IslandHub.expanded && m != null
    val baseH = IslandCfg.h * d
    val bubble = if (side != null) baseH + IslandLayout.GAP else 0f
    val tw = when {
        m == null -> IslandLayout.idleW(d)
        expanded -> IslandLayout.expandedW(IslandHub.screenWdp - bubble, d)
        else -> IslandLayout.compactW(m.kind, d)
    }
    val th = if (m != null && expanded) IslandLayout.expandedH(m, d) else baseH
    val w by animateDpAsState(tw.dp, springFor<Dp>(IslandCfg.anim), label = "isl-w")
    val h by animateDpAsState(th.dp, springFor<Dp>(IslandCfg.anim), label = "isl-h")
    val expT by animateFloatAsState(if (expanded) 1f else 0f, tween(320, easing = FastOutSlowInEasing), label = "isl-e")
    val minSide = minOf(w.value, h.value)
    val radius = ((minSide / 2f) + ((38f * d) - (minSide / 2f)).coerceAtLeast(0f) * expT) * IslandCfg.round / 100f
    val shape = islandShape(IslandCfg.shape, radius.dp)
    val accent = m?.accent ?: Color(0xFF8B5CF6)
    val st = IStyle(d, skin, accent)

    val appear = remember { Animatable(0f) }
    LaunchedEffect(IslandHub.shown) { appear.animateTo(if (IslandHub.shown) 1f else 0f, tween(300, easing = FastOutSlowInEasing)) }

    // A small pop every time something new takes the main place
    val pop = remember { Animatable(1f) }
    LaunchedEffect(m?.id) {
        if (m != null) {
            pop.snapTo(0.93f)
            pop.animateTo(1f, spring(dampingRatio = 0.45f, stiffness = 420f))
        }
    }

    // Closes by itself a few seconds after the last touch, except while a call is ringing
    LaunchedEffect(expanded, IslandHub.touch, m?.id) {
        val ringing = m?.kind == IKind.CALL && Calls.state == TelephonyManager.CALL_STATE_RINGING
        if (expanded && IslandCfg.hold > 0 && !ringing) {
            delay(IslandCfg.hold * 1000L)
            IslandHub.collapse()
        }
    }

    val mNow by rememberUpdatedState(m)
    fun tick() { if (IslandCfg.hapt) view.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP) }

    /** A drag ended. Up and down open and close, left and right change song or dismiss. */
    fun swiped(dx: Float, dy: Float, th0: Float) {
        val cur = mNow ?: return
        if (abs(dy) >= abs(dx)) {
            if (dy < -th0) {
                if (IslandHub.expanded) IslandHub.collapse() else if (dismissable(cur)) IslandHub.remove(cur.id)
            } else if (dy > th0 && !IslandHub.expanded) IslandHub.toggleExpanded()
        } else if (IslandCfg.swipe && !IslandHub.expanded && abs(dx) > th0 * 1.5f) {
            val dir = if (dx > 0f) 1 else -1
            when (cur.kind) {
                IKind.MEDIA -> if (dir > 0) IslandMedia.next() else IslandMedia.prev()
                IKind.NOTE, IKind.FLASH, IKind.PROGRESS -> IslandHub.remove(cur.id)
                else -> IslandHub.cycle(dir)
            }
            tick()
        }
    }

    Box(Modifier.fillMaxSize().padding(top = IslandLayout.PAD.dp), contentAlignment = Alignment.TopCenter) {
        Row(
            Modifier.graphicsLayer {
                val a = appear.value
                alpha = (a * IslandCfg.alpha / 100f).coerceIn(0f, 1f)
                val sc = (0.55f + 0.45f * a) * pop.value
                scaleX = sc; scaleY = sc
                transformOrigin = TransformOrigin(0.5f, 0f)
            },
            horizontalArrangement = Arrangement.spacedBy(IslandLayout.GAP.dp),
            verticalAlignment = Alignment.Top
        ) {
            IslandSurface(
                Modifier
                    .pointerInput(Unit) {
                        // Any touch keeps the card open. Nothing is consumed here.
                        awaitPointerEventScope {
                            while (true) {
                                val e = awaitPointerEvent(PointerEventPass.Initial)
                                if (e.type == PointerEventType.Press) IslandHub.touch++
                            }
                        }
                    }
                    .pointerInput(Unit) {
                        detectTapGestures(
                            onTap = { if (mNow != null) { tick(); IslandHub.toggleExpanded() } },
                            onLongPress = { mNow?.let { tick(); openOwner(ctx, it) } }
                        )
                    }
                    .pointerInput(Unit) {
                        val th0 = 18.dp.toPx()
                        var dx = 0f
                        var dy = 0f
                        detectDragGestures(
                            onDragStart = { dx = 0f; dy = 0f },
                            onDragEnd = { swiped(dx, dy, th0) },
                            onDragCancel = { },
                            onDrag = { ch, amount -> ch.consume(); dx += amount.x; dy += amount.y }
                        )
                    },
                w, h, shape, radius.dp, skin, IslandCfg.fx, accent, expT
            ) {
                AnimatedContent(
                    targetState = m,
                    contentKey = { it?.id },
                    transitionSpec = { ContentTransform(fadeIn(tween(240, delayMillis = 60)) + scaleIn(tween(240), 0.92f), fadeOut(tween(140)) + scaleOut(tween(140), 0.96f), 0f, null) },
                    label = "isl-main"
                ) { act ->
                    if (act != null) {
                        AnimatedContent(
                            targetState = expanded,
                            transitionSpec = { ContentTransform(fadeIn(tween(220, delayMillis = 90)), fadeOut(tween(110)), 0f, null) },
                            label = "isl-size"
                        ) { big ->
                            if (big) ExpandedContent(act, st) else CompactContent(act, st, baseH.dp)
                        }
                    } else IdlePill(st)
                }
            }
            val showSide = side != null
            val bw by animateDpAsState(if (showSide) baseH.dp else 0.dp, springFor<Dp>(IslandCfg.anim), label = "isl-bw")
            if (side != null && bw > 2.dp) {
                val sideNow by rememberUpdatedState(side)
                IslandSurface(
                    Modifier.pointerInput(Unit) {
                        detectTapGestures(onTap = { tick(); IslandHub.focusAndOpen(sideNow.id) })
                    },
                    bw, bw, CircleShape, bw / 2, skin, if (IslandCfg.fx >= 3) 1 else IslandCfg.fx, side.accent, 0f
                ) {
                    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { Lead(side, bw * 0.66f, st) }
                }
            }
        }
    }
}

private fun openOwner(ctx: Context, a: IAct) {
    val o = a.onOpen
    when (a.kind) {
        IKind.NOTE, IKind.PROGRESS, IKind.TIMER -> if (o != null) o() else if (a.pkg.isNotEmpty()) openApp(ctx, a.pkg)
        IKind.MEDIA -> IslandMedia.open(ctx)
        IKind.CALL -> Safety.dialerPkgs(ctx).firstOrNull()?.let { openApp(ctx, it) }
        else -> {}
    }
}

/**
 * The shape itself: a glow behind it, an edge, the inside, a soft shine on top and, when expanded, a tint of the
 * accent colour in the corner. The edge is a still gradient, or a light that runs around it (Sweep, Rainbow).
 * Everything that moves is drawn in the draw step, so the rest does not redraw each frame.
 */
@Composable
private fun IslandSurface(
    modifier: Modifier, w: Dp, h: Dp, shape: Shape, radius: Dp, skin: ISkin, fx: Int, accent: Color, ambient: Float,
    content: @Composable BoxScope.() -> Unit
) {
    val sweep: State<Float>? =
        if (fx >= 3) rememberInfiniteTransition(label = "isl-sw").animateFloat(0f, 360f, infiniteRepeatable(tween(3400, easing = LinearEasing)), label = "isl-sp") else null
    val pulse: State<Float>? =
        if (fx == 2) rememberInfiniteTransition(label = "isl-pu").animateFloat(0.25f, 1f, infiniteRepeatable(tween(1300, easing = FastOutSlowInEasing), RepeatMode.Reverse), label = "isl-pp") else null
    val tinted = IslandCfg.tint
    val edge = remember(skin, accent, tinted) {
        if (tinted) listOf(accent, accent.copy(alpha = 0.30f), accent.copy(alpha = 0.85f)) else skin.border
    }
    val sweepBrush = remember(fx, accent, skin, tinted) {
        if (fx == 4) Brush.sweepGradient(RainbowColors)
        else {
            val a = if (tinted) accent else skin.border.first()
            Brush.sweepGradient(listOf(a.copy(alpha = 0.05f), a, a.copy(alpha = 0.05f), a.copy(alpha = 0.05f)))
        }
    }
    val baseBrush = remember(edge) { Brush.linearGradient(edge) }
    val inner = remember(skin) { Brush.linearGradient(skin.fill) }
    val bw = skin.borderW.dp
    Box(
        modifier
            .size(w, h)
            .drawBehind {
                // A soft halo made of a few wide, faint layers
                val strength = when (fx) { 1 -> 0.9f; 2 -> pulse?.value ?: 1f; 3, 4 -> 0.7f; else -> 0f }
                if (strength > 0f) {
                    val r = radius.toPx()
                    for (i in 1..6) {
                        val g = i * 2.3.dp.toPx()
                        drawRoundRect(
                            color = accent.copy(alpha = (0.20f * strength * (1f - i / 7f)).coerceIn(0f, 1f)),
                            topLeft = Offset(-g, -g),
                            size = Size(size.width + 2 * g, size.height + 2 * g),
                            cornerRadius = CornerRadius(r + g)
                        )
                    }
                }
            }
            .clip(shape)
            .drawBehind {
                drawRect(baseBrush)
                val s = sweep
                if (s != null) rotate(degrees = s.value) { drawCircle(sweepBrush, radius = maxOf(size.width, size.height)) }
            }
    ) {
        Box(
            Modifier.fillMaxSize().padding(bw).clip(shape).background(inner)
                .drawBehind {
                    if (IslandCfg.gloss && skin.gloss > 0f) {
                        drawRect(Brush.verticalGradient(listOf(Color.White.copy(alpha = skin.gloss), Color.Transparent), startY = 0f, endY = size.height * 0.6f))
                    }
                    if (ambient > 0.01f) {
                        drawRect(
                            Brush.radialGradient(
                                listOf(accent.copy(alpha = 0.30f * ambient), Color.Transparent),
                                center = Offset(size.width * 0.18f, 0f), radius = size.width * 0.85f
                            )
                        )
                    }
                },
            content = content
        )
    }
}

// ============================================================================================= small pieces

@Composable
private fun Lead(a: IAct, size: Dp, st: IStyle) {
    val art = IslandMedia.art
    when {
        a.kind == IKind.MEDIA && art != null ->
            Image(art, null, Modifier.size(size).clip(RoundedCornerShape(size * 0.28f)), contentScale = ContentScale.Crop)
        a.pkg.isNotEmpty() && a.kind != IKind.MEDIA && a.kind != IKind.TIMER -> AppIcon(a.pkg, size)
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
    val ph: State<Float>? = t?.animateFloat(0f, 6.2831855f, infiniteRepeatable(tween(1300, easing = LinearEasing)), label = "wph")
    Canvas(Modifier.size(height * 1.1f, height)) {
        val n = 5
        val bw = size.width / (n * 2f - 1f)
        val p = ph?.value ?: 0f
        for (i in 0 until n) {
            val k = if (ph == null) 0.28f else 0.30f + 0.70f * ((sin(p + i * 1.25f) + 1f) / 2f)
            val bh = size.height * k
            drawRoundRect(color, Offset(i * 2f * bw, (size.height - bh) / 2f), Size(bw, bh), CornerRadius(bw / 2f))
        }
    }
}

@Composable
private fun PulseDot(color: Color, size: Dp) {
    val t = rememberInfiniteTransition(label = "dot")
    val a by t.animateFloat(0.35f, 1f, infiniteRepeatable(tween(700, easing = FastOutSlowInEasing), RepeatMode.Reverse), label = "dota")
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
        drawArc(color, -90f, 360f * frac.coerceIn(0f, 1f), false, Offset(inset, inset), sz, style = Stroke(sw, cap = StrokeCap.Round))
    }
}

@Composable
private fun CountBadge(n: Int, color: Color, d: Float) {
    Box(Modifier.size((17f * d).dp).clip(CircleShape).background(color), contentAlignment = Alignment.Center) {
        Text(if (n > 9) "9+" else "$n", color = Ink, fontSize = (10f * d).sp, fontWeight = FontWeight.Bold, maxLines = 1)
    }
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

/** A round button with a small label under it. */
@Composable
private fun LabeledBtn(label: String, bg: Color, size: Dp, st: IStyle, onClick: () -> Unit, content: @Composable () -> Unit) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        RoundBtn(bg, size, onClick, content)
        Spacer(Modifier.height(3.dp))
        Text(label, color = st.skin.sub, fontSize = (10.5f * st.d).sp, maxLines = 1)
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

/** What a timer shows: the time left for a countdown, the time gone for a stopwatch. */
@Composable
private fun TimerText(a: IAct, color: Color, fs: Float) {
    var sec by remember(a.id, a.clockAt) { mutableLongStateOf(0L) }
    LaunchedEffect(a.id, a.clockAt, a.countdown) {
        while (true) {
            val e = SystemClock.elapsedRealtime()
            sec = if (a.countdown) (a.clockAt - e + 999L) / 1000L else (e - a.clockAt) / 1000L
            delay(250L)
        }
    }
    Text(clock(sec), color = color, fontSize = fs.sp, fontWeight = FontWeight.Bold, maxLines = 1)
}

@Composable
private fun Avatar(name: String, hasName: Boolean, accent: Color, dia: Dp, pulse: Boolean) {
    val t = if (pulse) rememberInfiniteTransition(label = "av") else null
    val k: State<Float>? = t?.animateFloat(0f, 1f, infiniteRepeatable(tween(1100, easing = LinearEasing)), label = "avk")
    Box(Modifier.size(dia), contentAlignment = Alignment.Center) {
        Canvas(Modifier.fillMaxSize()) {
            val p = k?.value
            if (p != null) drawCircle(accent.copy(alpha = 0.40f * (1f - p)), radius = this.size.minDimension * (0.40f + 0.10f * p))
        }
        Box(Modifier.size(dia * 0.8f).clip(CircleShape).background(accent.copy(alpha = 0.25f)), contentAlignment = Alignment.Center) {
            val ch = name.firstOrNull { it.isLetterOrDigit() }?.uppercaseChar()?.toString()
            if (ch != null && hasName) Text(ch, color = accent, fontSize = (dia.value * 0.38f).sp, fontWeight = FontWeight.Bold)
            else Icon(Icons.Outlined.Call, null, tint = accent, modifier = Modifier.size(dia * 0.44f))
        }
    }
}

/** A bar you can tap or drag to jump to a place in the song. */
@Composable
private fun SeekBar(frac: Float, accent: Color, enabled: Boolean, onSeek: (Float) -> Unit, modifier: Modifier = Modifier) {
    var drag by remember { mutableStateOf<Float?>(null) }
    val shown = (drag ?: frac).coerceIn(0f, 1f)
    val seekNow by rememberUpdatedState(onSeek)
    Box(
        modifier.height(22.dp)
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
            },
        contentAlignment = Alignment.CenterStart
    ) {
        Box(Modifier.fillMaxWidth().height(5.dp).clip(CircleShape).background(Color.White.copy(alpha = 0.18f)))
        Box(Modifier.fillMaxWidth(shown).height(5.dp).clip(CircleShape).background(accent))
        Box(Modifier.fillMaxWidth(shown).height(22.dp)) {
            Box(Modifier.align(Alignment.CenterEnd).size(11.dp).clip(CircleShape).background(Color.White))
        }
    }
}

// ============================================================================================= compact

@Composable
private fun CompactContent(a: IAct, st: IStyle, h: Dp) {
    val d = st.d
    val pad = (h.value * 0.22f).dp
    val lead = h * 0.66f
    val split = IslandCfg.gap > 0
    val s by Monitor.snap.collectAsState()
    val level = s.batt.level
    val title = when (a.kind) {
        IKind.CALL -> Calls.caller.ifEmpty { if (Calls.state == TelephonyManager.CALL_STATE_RINGING) "Incoming call" else "On call" }
        IKind.MEDIA -> IslandMedia.title.ifEmpty { "Playing" }
        IKind.CHARGE -> "$level%"
        IKind.RECORD -> "REC"
        else -> a.title
    }
    if (split) {
        // Two halves with the camera in the middle: the picture on the left, the live part on the right
        val sideDp = (IslandLayout.side(a.kind) * d).dp
        Row(Modifier.fillMaxSize(), verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.width(sideDp).padding(start = pad), contentAlignment = Alignment.CenterStart) { Lead(a, lead, st) }
            Spacer(Modifier.weight(1f))
            Box(Modifier.width(sideDp).padding(end = pad), contentAlignment = Alignment.CenterEnd) { Trailing(a, st, lead, true, level) }
        }
    } else {
        Row(Modifier.fillMaxSize().padding(horizontal = pad), verticalAlignment = Alignment.CenterVertically) {
            Lead(a, lead, st)
            Spacer(Modifier.width(7.dp))
            Text(title, color = st.skin.text, fontSize = (12f * d).sp, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
            Spacer(Modifier.width(6.dp))
            Trailing(a, st, lead, false, level)
        }
    }
}

@Composable
private fun Trailing(a: IAct, st: IStyle, lead: Dp, split: Boolean, level: Int) {
    val d = st.d
    when (a.kind) {
        IKind.CALL ->
            if (Calls.state == TelephonyManager.CALL_STATE_OFFHOOK) Elapsed(Calls.startedAt, st.skin.sub, 11f * d)
            else Text("Ringing", color = st.skin.sub, fontSize = (10f * d).sp, maxLines = 1)
        IKind.MEDIA -> Wave(IslandMedia.playing, a.accent, lead * 0.7f)
        IKind.CHARGE -> Row(verticalAlignment = Alignment.CenterVertically) {
            if (split) {
                Text("$level%", color = st.skin.text, fontSize = (11f * d).sp, fontWeight = FontWeight.SemiBold, maxLines = 1)
                Spacer(Modifier.width(4.dp))
            }
            MiniBattery(level, a.accent, (22f * d).dp, (11f * d).dp)
        }
        IKind.RECORD -> Row(verticalAlignment = Alignment.CenterVertically) {
            PulseDot(a.accent, (7f * d).dp)
            Spacer(Modifier.width(5.dp))
            Elapsed(Recorder.startedAt, st.skin.sub, 11f * d)
        }
        IKind.NOTE -> if (a.count > 1) CountBadge(a.count, a.accent, d)
        IKind.FLASH -> if (split) Text(a.title, color = st.skin.text, fontSize = (11f * d).sp, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.widthIn(max = (58f * d).dp))
        IKind.PROGRESS -> Row(verticalAlignment = Alignment.CenterVertically) {
            Text("${(a.progress * 100f).toInt()}%", color = st.skin.text, fontSize = (11f * d).sp, fontWeight = FontWeight.SemiBold, maxLines = 1)
            Spacer(Modifier.width(4.dp))
            ProgressRing(a.progress, a.accent, (16f * d).dp, 2.dp)
        }
        IKind.TIMER -> TimerText(a, st.skin.text, 12f * d)
    }
}

// ============================================================================================= idle widgets

private class IdleItem(val icon: ImageVector, val text: String, val color: Color)

private fun idleItem(ctx: Context, kind: Int, s: Snap, alarmAt: Long): IdleItem = when (kind) {
    0 -> IdleItem(Icons.Outlined.Schedule, hm(ctx, System.currentTimeMillis()), Color(0xFF8B5CF6))
    1 -> IdleItem(Icons.Outlined.CalendarToday, SimpleDateFormat("EEE d MMM", Locale.getDefault()).format(Date()), Color(0xFF38BDF8))
    2 -> IdleItem(if (s.batt.charging) Icons.Outlined.Bolt else Icons.Outlined.BatteryStd, "${s.batt.level}%", Color(0xFF34D399))
    3 -> IdleItem(Icons.Outlined.Speed, "\u2193 ${fmtSpeed(s.netDown)}", Color(0xFF22D3EE))
    else -> IdleItem(Icons.Outlined.Alarm, hm(ctx, alarmAt), Color(0xFFFFB84D))
}

/** Nothing is going on: the empty shape shows the clock, the date, the battery, the speed and the next alarm in turn. */
@Composable
private fun IdlePill(st: IStyle) {
    val ctx = LocalContext.current
    var alarmAt by remember { mutableLongStateOf(0L) }
    LaunchedEffect(Unit) {
        while (true) {
            alarmAt = nextAlarm(ctx)
            delay(15_000L)
        }
    }
    val items = ArrayList<Int>()
    if (IslandCfg.idleClock) items.add(0)
    if (IslandCfg.idleDate) items.add(1)
    if (IslandCfg.idleBatt) items.add(2)
    if (IslandCfg.idleNet) items.add(3)
    if (IslandCfg.idleAlarm && alarmAt > 0L) items.add(4)
    if (items.isEmpty()) Box(Modifier.fillMaxSize()) else IdleRoller(items.toList(), alarmAt, st)
}

@Composable
private fun IdleRoller(items: List<Int>, alarmAt: Long, st: IStyle) {
    val ctx = LocalContext.current
    val d = st.d
    val s by Monitor.snap.collectAsState()
    var idx by remember { mutableIntStateOf(0) }
    // The clock text is made again every time this recomposes. This tick makes sure that happens once a minute.
    var minute by remember { mutableLongStateOf(0L) }
    LaunchedEffect(Unit) {
        while (true) {
            minute = System.currentTimeMillis() / 60000L
            delay(10_000L)
        }
    }
    val n = items.size
    LaunchedEffect(n, IslandCfg.idleSec) {
        while (true) {
            delay(IslandCfg.idleSec * 1000L)
            if (n > 1) idx = (idx + 1) % n
        }
    }
    val cur = items[idx.coerceIn(0, n - 1)]
    val split = IslandCfg.gap > 0
    val pad = (IslandCfg.h * d * 0.22f).dp
    val sideDp = (64f * d).dp
    AnimatedContent(
        targetState = cur,
        transitionSpec = {
            (slideInVertically(tween(260)) { it } + fadeIn(tween(220))) togetherWith
                (slideOutVertically(tween(220)) { -it } + fadeOut(tween(160)))
        },
        label = "isl-idle"
    ) { kind ->
        val item = remember(kind, minute, s.batt.level, s.batt.charging, s.netDown, alarmAt) { idleItem(ctx, kind, s, alarmAt) }
        if (!split) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(item.icon, null, tint = item.color, modifier = Modifier.size((14f * d).dp))
                    Spacer(Modifier.width(5.dp))
                    Text(item.text, color = st.skin.text, fontSize = (12f * d).sp, fontWeight = FontWeight.SemiBold, maxLines = 1)
                }
            }
        } else {
            Row(Modifier.fillMaxSize(), verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.width(sideDp).padding(start = pad), contentAlignment = Alignment.CenterStart) {
                    Icon(item.icon, null, tint = item.color, modifier = Modifier.size((14f * d).dp))
                }
                Spacer(Modifier.weight(1f))
                Box(Modifier.width(sideDp).padding(end = pad), contentAlignment = Alignment.CenterEnd) {
                    Text(item.text, color = st.skin.text, fontSize = (12f * d).sp, fontWeight = FontWeight.SemiBold, maxLines = 1)
                }
            }
        }
    }
}

// ============================================================================================= expanded

@Composable
private fun ExpandedContent(a: IAct, st: IStyle) {
    val d = st.d
    val top = ((IslandLayout.topRoom() + 12f) * d).dp
    Column(
        Modifier.fillMaxSize().padding(start = (14f * d).dp, end = (14f * d).dp, top = top, bottom = (12f * d).dp),
        verticalArrangement = Arrangement.Center
    ) {
        when (a.kind) {
            IKind.CALL -> CallExpanded(a, st)
            IKind.MEDIA -> MediaExpanded(a, st)
            IKind.CHARGE -> ChargeExpanded(a, st)
            IKind.RECORD -> RecordExpanded(a, st)
            IKind.NOTE -> NoteExpanded(a, st)
            IKind.FLASH -> FlashExpanded(a, st)
            IKind.PROGRESS -> ProgressExpanded(a, st)
            IKind.TIMER -> TimerExpanded(a, st)
        }
    }
}

@Composable
private fun CallExpanded(a: IAct, st: IStyle) {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    val d = st.d
    val ringing = Calls.state == TelephonyManager.CALL_STATE_RINGING
    val named = Calls.caller.isNotEmpty()
    val name = Calls.caller.ifEmpty { if (ringing) "Incoming call" else "On call" }
    Row(verticalAlignment = Alignment.CenterVertically) {
        Avatar(name, named, a.accent, (50f * d).dp, ringing)
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(name, color = st.skin.text, fontSize = (16f * d).sp, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
            if (ringing) Text("Incoming call", color = st.skin.sub, fontSize = (12f * d).sp)
            else Elapsed(Calls.startedAt, st.skin.sub, 12f * d)
        }
    }
    Spacer(Modifier.height((12f * d).dp))
    if (ringing) {
        val bh = (44f * d).dp
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Capsule("Decline", Color(0xFFFF4D6D), Color.White, bh, 14f * d, Modifier.weight(1f), Icons.Outlined.CallEnd) { scope.launch(Dispatchers.IO) { Calls.end(ctx) } }
            Capsule("Answer", Color(0xFF34D399), Ink, bh, 14f * d, Modifier.weight(1f), Icons.Outlined.Call) { scope.launch(Dispatchers.IO) { Calls.answer(ctx) } }
        }
    } else {
        val bs = (42f * d).dp
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly, verticalAlignment = Alignment.Top) {
            LabeledBtn("Speaker", if (Calls.speaker) Color.White else Color.White.copy(alpha = 0.16f), bs, st, { Calls.setSpeaker(ctx, !Calls.speaker) }) {
                Icon(Icons.Outlined.Speaker, "Speaker", tint = if (Calls.speaker) Ink else Color.White, modifier = Modifier.size(bs * 0.5f))
            }
            LabeledBtn(if (Calls.muted) "Unmute" else "Mute", if (Calls.muted) Color.White else Color.White.copy(alpha = 0.16f), bs, st, { Calls.setMute(ctx, !Calls.muted) }) {
                Icon(if (Calls.muted) Icons.Outlined.MicOff else Icons.Outlined.Mic, "Mute", tint = if (Calls.muted) Ink else Color.White, modifier = Modifier.size(bs * 0.5f))
            }
            LabeledBtn("End", Color(0xFFFF4D6D), bs, st, { scope.launch(Dispatchers.IO) { Calls.end(ctx) } }) {
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
private fun MediaExpanded(a: IAct, st: IStyle) {
    val d = st.d
    val sub = st.skin.sub
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
            Text(IslandMedia.title.ifEmpty { "Playing" }, color = st.skin.text, fontSize = (15f * d).sp, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(IslandMedia.artist, color = sub, fontSize = (12f * d).sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        Wave(IslandMedia.playing, a.accent, (22f * d).dp)
    }
    Spacer(Modifier.height(6.dp))
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(clock(pos / 1000L), color = sub, fontSize = (10f * d).sp, modifier = Modifier.width(40.dp))
        SeekBar(frac, a.accent, dur > 0L, { f -> IslandMedia.seekTo((f * dur).toLong()) }, Modifier.weight(1f))
        Text(if (dur > 0L) clock(dur / 1000L) else "--:--", color = sub, fontSize = (10f * d).sp, modifier = Modifier.width(40.dp), textAlign = TextAlign.End)
    }
    Spacer(Modifier.height(4.dp))
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly, verticalAlignment = Alignment.CenterVertically) {
        val ic = st.skin.text
        RoundBtn(Color.Transparent, (42f * d).dp, { IslandMedia.prev() }) { MediaGlyph(0, ic, (24f * d).dp) }
        RoundBtn(a.accent.copy(alpha = 0.30f), (50f * d).dp, { IslandMedia.toggle() }) { MediaGlyph(if (IslandMedia.playing) 2 else 1, ic, (28f * d).dp) }
        RoundBtn(Color.Transparent, (42f * d).dp, { IslandMedia.next() }) { MediaGlyph(3, ic, (24f * d).dp) }
    }
}

@Composable
private fun Stat(value: String, label: String, st: IStyle) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(value, color = st.skin.text, fontSize = (13f * st.d).sp, fontWeight = FontWeight.Bold, maxLines = 1)
        Text(label, color = st.skin.sub, fontSize = (10f * st.d).sp, maxLines = 1)
    }
}

@Composable
private fun ChargeExpanded(a: IAct, st: IStyle) {
    val d = st.d
    val s by Monitor.snap.collectAsState()
    val b = s.batt
    val frac = (b.level / 100f).coerceIn(0f, 1f)
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size((62f * d).dp), contentAlignment = Alignment.Center) {
            ProgressRing(frac, a.accent, (62f * d).dp, 6.dp)
            Text("${b.level}", color = st.skin.text, fontSize = (18f * d).sp, fontWeight = FontWeight.Bold)
        }
        Spacer(Modifier.width(14.dp))
        Column(Modifier.weight(1f)) {
            Text(if (b.charging) "Charging" else "Battery", color = st.skin.text, fontSize = (16f * d).sp, fontWeight = FontWeight.Bold)
            Text(if (b.plugged != "-") b.plugged else b.status, color = st.skin.sub, fontSize = (12f * d).sp, maxLines = 1)
        }
        Icon(Icons.Outlined.Bolt, null, tint = a.accent, modifier = Modifier.size((26f * d).dp))
    }
    Spacer(Modifier.height(10.dp))
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
        Stat("${b.ma} mA", "Current", st)
        Stat("%.1f\u00B0C".format(b.tempC), "Temperature", st)
        Stat(if (b.mv > 0) "%.2f V".format(b.mv / 1000f) else "-", "Voltage", st)
    }
}

@Composable
private fun RecordExpanded(a: IAct, st: IStyle) {
    val scope = rememberCoroutineScope()
    val d = st.d
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size((44f * d).dp), contentAlignment = Alignment.Center) { PulseDot(a.accent, (16f * d).dp) }
        Spacer(Modifier.width(10.dp))
        Column(Modifier.weight(1f)) {
            Text("Recording", color = st.skin.text, fontSize = (16f * d).sp, fontWeight = FontWeight.Bold)
            Elapsed(Recorder.startedAt, st.skin.sub, 13f * d)
        }
        Capsule("Stop", Color(0xFFFF4D6D), Color.White, (40f * d).dp, 13f * d, Modifier, Icons.Outlined.Stop) { scope.launch(Dispatchers.IO) { Recorder.stop() } }
    }
}

@Composable
private fun NoteExpanded(a: IAct, st: IStyle) {
    val ctx = LocalContext.current
    val d = st.d
    val label = remember(a.pkg) { islandAppName(ctx, a.pkg) }
    val bs = (34f * d).dp
    Row(verticalAlignment = Alignment.CenterVertically) {
        AppIcon(a.pkg, (24f * d).dp)
        Spacer(Modifier.width(8.dp))
        Text(label, color = st.skin.sub, fontSize = (11.5f * d).sp, fontWeight = FontWeight.Medium, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
        if (a.count > 1) {
            CountBadge(a.count, a.accent, d)
            Spacer(Modifier.width(6.dp))
        }
        Text("now", color = st.skin.sub, fontSize = (10.5f * d).sp)
    }
    Spacer(Modifier.height(6.dp))
    Text(a.title, color = st.skin.text, fontSize = (15f * d).sp, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
    Text(a.sub, color = st.skin.sub, fontSize = (12.5f * d).sp, maxLines = 3, overflow = TextOverflow.Ellipsis)
    Spacer(Modifier.height(8.dp))
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        if (IslandCfg.buttons) a.actions.take(2).forEach { act ->
            Capsule(act.label, Color.White.copy(alpha = 0.16f), st.skin.text, bs, 12f * d, Modifier.weight(1f, fill = false)) { act.run(); IslandHub.remove(a.id) }
        }
        Spacer(Modifier.weight(1f))
        RoundBtn(Color.White.copy(alpha = 0.14f), bs, {
            IslandCfg.mute(ctx, a.pkg, true)
            IslandHub.remove(a.id)
            toast(ctx, "$label muted. Undo it in Island, Notifications.")
        }) { Icon(Icons.Outlined.NotificationsOff, "Mute this app", tint = st.skin.text, modifier = Modifier.size(bs * 0.5f)) }
        RoundBtn(Color.White.copy(alpha = 0.14f), bs, { IslandHub.remove(a.id) }) {
            Icon(Icons.Outlined.Close, "Dismiss", tint = st.skin.text, modifier = Modifier.size(bs * 0.5f))
        }
        RoundBtn(a.accent, bs, {
            val o = a.onOpen
            if (o != null) o() else openApp(ctx, a.pkg)
            IslandHub.remove(a.id)
        }) { Icon(Icons.Outlined.OpenInBrowser, "Open", tint = Ink, modifier = Modifier.size(bs * 0.5f)) }
    }
}

@Composable
private fun ProgressExpanded(a: IAct, st: IStyle) {
    val ctx = LocalContext.current
    val d = st.d
    val bh = (36f * d).dp
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size((60f * d).dp), contentAlignment = Alignment.Center) {
            ProgressRing(a.progress, a.accent, (60f * d).dp, 5.dp)
            Text("${(a.progress * 100f).toInt()}%", color = st.skin.text, fontSize = (14f * d).sp, fontWeight = FontWeight.Bold)
        }
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(a.title, color = st.skin.text, fontSize = (15f * d).sp, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
            if (a.sub.isNotEmpty()) Text(a.sub, color = st.skin.sub, fontSize = (12f * d).sp, maxLines = 2, overflow = TextOverflow.Ellipsis)
        }
        if (a.pkg.isNotEmpty()) AppIcon(a.pkg, (28f * d).dp)
    }
    Spacer(Modifier.height(10.dp))
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        Capsule("Hide", Color.White.copy(alpha = 0.16f), st.skin.text, bh, 12.5f * d, Modifier.weight(1f)) { IslandHub.remove(a.id) }
        Capsule("Open", a.accent, Ink, bh, 12.5f * d, Modifier.weight(1f), Icons.Outlined.OpenInBrowser) {
            val o = a.onOpen
            if (o != null) o() else openApp(ctx, a.pkg)
            IslandHub.remove(a.id)
        }
    }
}

@Composable
private fun TimerExpanded(a: IAct, st: IStyle) {
    val ctx = LocalContext.current
    val d = st.d
    val bh = (36f * d).dp
    var sec by remember(a.id, a.clockAt) { mutableLongStateOf(0L) }
    var frac by remember(a.id, a.clockAt) { mutableStateOf(0f) }
    LaunchedEffect(a.id, a.clockAt, a.countdown, a.span) {
        while (true) {
            val e = SystemClock.elapsedRealtime()
            if (a.countdown) {
                val left = (a.clockAt - e).coerceAtLeast(0L)
                sec = (left + 999L) / 1000L
                frac = if (a.span > 0L) (left.toFloat() / a.span).coerceIn(0f, 1f) else 0f
            } else {
                sec = ((e - a.clockAt) / 1000L).coerceAtLeast(0L)
                frac = ((sec % 60L) / 60f)
            }
            delay(250L)
        }
    }
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size((56f * d).dp), contentAlignment = Alignment.Center) {
            ProgressRing(frac, a.accent, (56f * d).dp, 5.dp)
            Icon(Icons.Outlined.Timer, null, tint = a.accent, modifier = Modifier.size((22f * d).dp))
        }
        Spacer(Modifier.width(14.dp))
        Column(Modifier.weight(1f)) {
            Text(a.title, color = st.skin.sub, fontSize = (12f * d).sp)
            Text(clock(sec), color = st.skin.text, fontSize = (28f * d).sp, fontWeight = FontWeight.Bold, maxLines = 1)
        }
    }
    Spacer(Modifier.height(10.dp))
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        Capsule("Hide", Color.White.copy(alpha = 0.16f), st.skin.text, bh, 12.5f * d, Modifier.weight(1f)) { IslandHub.remove(a.id) }
        Capsule("Open clock", a.accent, Ink, bh, 12.5f * d, Modifier.weight(1f), Icons.Outlined.OpenInBrowser) {
            val o = a.onOpen
            if (o != null) o() else openApp(ctx, a.pkg)
            IslandHub.remove(a.id)
        }
    }
}

@Composable
private fun FlashExpanded(a: IAct, st: IStyle) {
    val d = st.d
    Row(verticalAlignment = Alignment.CenterVertically) {
        Lead(a, (48f * d).dp, st)
        Spacer(Modifier.width(14.dp))
        Column(Modifier.weight(1f)) {
            Text(a.title, color = st.skin.text, fontSize = (17f * d).sp, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
            if (a.sub.isNotEmpty()) Text(a.sub, color = st.skin.sub, fontSize = (12f * d).sp, maxLines = 2, overflow = TextOverflow.Ellipsis)
        }
    }
    if (a.actions.isNotEmpty()) {
        Spacer(Modifier.height(10.dp))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            a.actions.take(2).forEach { act ->
                Capsule(act.label, a.accent, Ink, (36f * d).dp, 12.5f * d, Modifier.weight(1f)) { act.run(); IslandHub.remove(a.id) }
            }
        }
    }
}
