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
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectVerticalDragGestures
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
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
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
import androidx.compose.runtime.mutableFloatStateOf
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
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlin.math.abs
import kotlin.math.exp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/** One look for the island: the inside, the edge and the text colours. */
class ISkin(
    val name: String,
    val fill: List<Color>,
    val border: List<Color>,
    val borderW: Float,
    val text: Color,
    val sub: Color,
    /** A light surface: buttons and bars inside it are drawn dark instead of white. */
    val light: Boolean = false
)

private fun c(v: Long) = Color(v)

val ISkins: List<ISkin> = listOf(
    ISkin("Classic", listOf(c(0xFF000000), c(0xFF000000)), listOf(c(0x44FFFFFF), c(0x14FFFFFF)), 0.8f, Color.White, c(0xFFB4B4BE)),
    ISkin("Glass", listOf(c(0xCC1E2440), c(0xB20C1226)), listOf(c(0xAAFFFFFF), c(0x22FFFFFF), c(0x77FFFFFF)), 1.2f, Color.White, c(0xFFC4CCE0)),
    ISkin("Aurora", listOf(c(0xFF0B0620), c(0xFF171045)), listOf(c(0xFFA78BFA), c(0xFF38BDF8)), 1.4f, Color.White, c(0xFFC9C2F5)),
    ISkin("Neon", listOf(c(0xFF04050A), c(0xFF080A14)), listOf(c(0xFF22D3EE), c(0xFFFF2BD6)), 1.6f, Color.White, c(0xFFA5F3FC)),
    ISkin("Sunset", listOf(c(0xFF2B0F2A), c(0xFF3F1A1C)), listOf(c(0xFFFFB84D), c(0xFFFF5C8A)), 1.4f, Color.White, c(0xFFFFD3C2)),
    ISkin("Ocean", listOf(c(0xFF04243A), c(0xFF06304E)), listOf(c(0xFF22D3EE), c(0xFF3B82F6)), 1.4f, Color.White, c(0xFFBAE6FD)),
    ISkin("Mint", listOf(c(0xFF06251A), c(0xFF0C3320)), listOf(c(0xFF34D399), c(0xFFA3E635)), 1.4f, Color.White, c(0xFFBBF7D0)),
    ISkin("Light", listOf(c(0xF2F7F8FB), c(0xF2E4E9F2)), listOf(c(0x66FFFFFF), c(0x33000000)), 1.0f, c(0xFF0F172A), c(0xFF475569), true),
    // Follows the phone: black in dark mode, light in light mode. The values here are not used, see islandSkin().
    ISkin("Auto", listOf(c(0xFF000000), c(0xFF000000)), listOf(c(0x44FFFFFF), c(0x14FFFFFF)), 0.8f, Color.White, c(0xFFB4B4BE))
)

/** The look in use. Auto picks the black one in dark mode and the light one in light mode. */
fun islandSkin(): ISkin {
    val i = IslandCfg.skin.coerceIn(0, ISkins.size - 1)
    return if (i == ISkins.size - 1) (if (IslandCfg.dark) ISkins[0] else ISkins[ISkins.size - 2]) else ISkins[i]
}

val ISkinNames: List<String> get() = ISkins.map { it.name }
val IFxNames = listOf("None", "Glow", "Pulse", "Sweep", "Rainbow")
val IShapeNames = listOf("Rectangle", "Notch", "Squircle", "Tab")
val IAnimNames = listOf("Bouncy", "Smooth", "Snappy", "Elastic", "Fluid")

private class IStyle(val d: Float, val skin: ISkin, val accent: Color) {
    /** Background of a quiet button. */
    val chip: Color get() = if (skin.light) Color(0x14000000) else Color(0x2AFFFFFF)
    /** The empty part of a bar. */
    val track: Color get() = if (skin.light) Color(0x1F000000) else Color(0x2EFFFFFF)
    /** Background of a button that is switched on. */
    val on: Color get() = skin.text
    /** Picture on a button that is switched on. */
    val onInk: Color get() = if (skin.light) Color.White else Ink
}

private fun <T> springFor(i: Int): SpringSpec<T> = when (i) {
    1 -> spring(dampingRatio = 0.92f, stiffness = 260f)
    2 -> spring(dampingRatio = 0.80f, stiffness = 760f)
    3 -> spring(dampingRatio = 0.34f, stiffness = 300f)
    // Fluid: a hint of overshoot and no wobble, the feel of the iPhone island
    4 -> spring(dampingRatio = 0.82f, stiffness = 360f)
    else -> spring(dampingRatio = 0.55f, stiffness = 380f)
}

private fun islandShape(style: Int, r: Dp): Shape = when (style) {
    1 -> RoundedCornerShape(topStart = r.coerceAtMost(5.dp), topEnd = r.coerceAtMost(5.dp), bottomEnd = r, bottomStart = r)
    2 -> RoundedCornerShape(r * 0.62f)
    3 -> RoundedCornerShape(topStart = r, topEnd = r, bottomEnd = r * 0.35f, bottomStart = r * 0.35f)
    else -> RoundedCornerShape(r)
}

/**
 * Corner radius in dp. The rectangle keeps its corners fairly square when it is closed and rounds them a little
 * more as it opens, like a card. The other shapes keep the older, rounder rule.
 */
private fun cornerR(style: Int, w: Float, h: Float, baseH: Float, d: Float, e: Float, round: Int): Float {
    val r = if (style == 0) {
        val closed = baseH * 0.40f
        val open = 28f * d
        closed + (open - closed) * e
    } else {
        val minSide = minOf(w, h)
        (minSide / 2f) + ((38f * d) - (minSide / 2f)).coerceAtLeast(0f) * e
    }
    return r * round / 100f
}

private val RainbowColors = listOf(c(0xFFFF4D6D), c(0xFFFFB84D), c(0xFFA3E635), c(0xFF22D3EE), c(0xFF6366F1), c(0xFFFF2BD6), c(0xFFFF4D6D))

private fun clock(sec: Long): String {
    val s = sec.coerceAtLeast(0L)
    return if (s >= 3600) "%d:%02d:%02d".format(s / 3600, (s % 3600) / 60, s % 60) else "%02d:%02d".format(s / 60, s % 60)
}

/** A short message, a notification or the charging card has nothing more to show once it was opened and closed again. */
private fun goesAfterClose(kind: IKind) = kind == IKind.FLASH || kind == IKind.NOTE || kind == IKind.CHARGE

/** The whole island. It lives in its own overlay window, see [IslandHost]. */
@Composable
fun IslandRoot() {
    val ctx = LocalContext.current
    val view = LocalView.current
    val scope = rememberCoroutineScope()
    val d = IslandCfg.scale / 100f
    val skin = islandSkin()
    val m = IslandHub.mainAct()
    val side = IslandHub.sideAct()
    val expanded = IslandHub.expanded && m != null
    val baseH = IslandCfg.h * d
    val tw = when {
        m == null -> IslandCfg.w * d
        expanded -> IslandLayout.expandedW(IslandHub.screenWdp.toFloat(), d)
        else -> IslandLayout.compactW(m.kind, d)
    }
    val th = if (m != null && expanded) IslandLayout.expandedH(m.kind, d) else baseH
    val w by animateDpAsState(tw.dp, springFor<Dp>(IslandCfg.anim), label = "isl-w")
    val h by animateDpAsState(th.dp, springFor<Dp>(IslandCfg.anim), label = "isl-h")
    val expT by animateFloatAsState(if (expanded) 1f else 0f, tween(320, easing = FastOutSlowInEasing), label = "isl-e")
    val radius = cornerR(IslandCfg.shape, w.value, h.value, baseH, d, expT, IslandCfg.round)
    val shape = islandShape(IslandCfg.shape, radius.dp)
    val accent = m?.accent ?: Color(0xFF8B5CF6)
    val st = IStyle(d, skin, accent)

    val appear = remember { Animatable(0f) }
    LaunchedEffect(IslandHub.shown) { appear.animateTo(if (IslandHub.shown) 1f else 0f, tween(300, easing = FastOutSlowInEasing)) }

    // Closes by itself a few seconds after the last touch, except while a call is ringing
    LaunchedEffect(expanded, IslandHub.touch, m?.id) {
        val ringing = m?.kind == IKind.CALL && Calls.state == TelephonyManager.CALL_STATE_RINGING
        if (expanded && IslandCfg.hold > 0 && !ringing) {
            delay(IslandCfg.hold * 1000L)
            IslandHub.collapse()
        }
    }

    // Which way the last swipe went. It is cleared a moment later, so a new call or message still fades in as usual.
    val slide = IslandHub.slide
    LaunchedEffect(slide) {
        if (slide != 0) {
            delay(450L)
            IslandHub.slide = 0
        }
    }

    val mNow by rememberUpdatedState(m)
    // How far the finger has pulled the island sideways, in px. It springs back to 0 when you let go.
    val dragX = remember { Animatable(0f) }
    val pressed = remember { mutableStateOf(false) }
    val press by animateFloatAsState(if (pressed.value) 0.96f else 1f, spring(dampingRatio = 0.6f, stiffness = 520f), label = "isl-press")
    fun tick() { if (IslandCfg.hapt) view.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP) }

    // The main shape is always in the middle. The window is as wide on the left as on the right, see IslandHub.window().
    Box(
        Modifier.fillMaxSize().padding(top = IslandLayout.PAD.dp).graphicsLayer {
            val a = appear.value
            alpha = (a * IslandCfg.alpha / 100f).coerceIn(0f, 1f)
            val sc = 0.55f + 0.45f * a
            scaleX = sc; scaleY = sc
            // The whole island leans a little with the finger. The window has room for this on both sides.
            translationX = dragX.value * 0.10f
            transformOrigin = TransformOrigin(0.5f, 0f)
        },
        contentAlignment = Alignment.TopCenter
    ) {
        IslandSurface(
            Modifier
                // Pressing makes it shrink a little under the finger, letting go makes it spring back
                .graphicsLayer { scaleX = press; scaleY = press }
                .pointerInput(Unit) {
                    detectTapGestures(
                        onPress = { pressed.value = true; tryAwaitRelease(); pressed.value = false },
                        // One tap opens it, the next tap closes it
                        onTap = {
                            val cur = mNow
                            if (cur != null) {
                                tick()
                                if (IslandHub.expanded) {
                                    IslandHub.collapse()
                                    if (goesAfterClose(cur.kind)) scope.launch { delay(260L); IslandHub.dismiss(cur.id) }
                                } else IslandHub.toggleExpanded()
                            }
                        },
                        onLongPress = { mNow?.let { tick(); openOwner(ctx, it) } }
                    )
                }
                // Swipe left or right: the next or the previous thing slides in
                .pointerInput(Unit) {
                    val limit = 96.dp.toPx()
                    val commit = 52.dp.toPx()
                    val flick = 18.dp.toPx()
                    var acc = 0f
                    var vel = 0f
                    var lastT = 0L
                    // The island follows the finger and gets heavier the further it goes. With nothing else to show it resists more.
                    fun rubber(x: Float): Float {
                        val e = if (IslandHub.acts.size > 1) x else x * 0.35f
                        val sg = if (e < 0f) -1f else 1f
                        return sg * limit * (1f - exp(-abs(e) / limit))
                    }
                    fun home() { scope.launch { dragX.animateTo(0f, spring(dampingRatio = 0.72f, stiffness = 380f)) } }
                    detectHorizontalDragGestures(
                        onDragStart = { acc = 0f; vel = 0f; lastT = 0L },
                        onDragEnd = {
                            val far = abs(acc) > commit || (abs(vel) > 0.8f && abs(acc) > flick)
                            if (far && IslandHub.cycle(if (acc < 0f) 1 else -1)) tick()
                            home()
                        },
                        onDragCancel = { home() },
                        onHorizontalDrag = { ch, amount ->
                            ch.consume()
                            acc += amount
                            val t = ch.uptimeMillis
                            if (lastT != 0L && t > lastT) vel = 0.6f * vel + 0.4f * (amount / (t - lastT).toFloat())
                            lastT = t
                            val x = rubber(acc)
                            scope.launch { dragX.snapTo(x) }
                        }
                    )
                }
                .pointerInput(Unit) {
                    var dy = 0f
                    detectVerticalDragGestures(
                        onDragStart = { dy = 0f },
                        onDragEnd = {
                            val cur = mNow
                            if (dy < -36f) {
                                if (IslandHub.expanded) IslandHub.collapse()
                                else if (cur != null && (goesAfterClose(cur.kind) || cur.kind == IKind.LIVE)) IslandHub.dismiss(cur.id)
                            } else if (dy > 36f && cur != null && !IslandHub.expanded) IslandHub.toggleExpanded()
                        },
                        onDragCancel = { },
                        onVerticalDrag = { ch, amount -> ch.consume(); dy += amount }
                    )
                },
            w, h, shape, radius.dp, skin, IslandCfg.fx, accent
        ) {
            AnimatedContent(
                targetState = m,
                contentKey = { it?.id },
                // The content moves with the finger and fades a little. The shape itself stays.
                modifier = Modifier.graphicsLayer {
                    translationX = dragX.value * 0.65f
                    alpha = 1f - 0.45f * (abs(dragX.value) / 96.dp.toPx()).coerceIn(0f, 1f)
                },
                transitionSpec = {
                    val sw = IslandHub.slide
                    if (sw != 0) {
                        // A swipe: the old content leaves on the side you swiped to, the new one comes in from the other side
                        ContentTransform(
                            slideInHorizontally(tween(360, easing = FastOutSlowInEasing)) { full -> (full * 0.6f * sw).toInt() } +
                                fadeIn(tween(240, delayMillis = 50)),
                            slideOutHorizontally(tween(260, easing = FastOutSlowInEasing)) { full -> (-full * 0.6f * sw).toInt() } +
                                fadeOut(tween(150)),
                            0f, null
                        )
                    } else {
                        ContentTransform(fadeIn(tween(240, delayMillis = 60)) + scaleIn(tween(240), 0.92f), fadeOut(tween(140)) + scaleOut(tween(140), 0.96f), 0f, null)
                    }
                },
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
                } else Box(Modifier.fillMaxSize())
            }
        }
        // The small bubble sits to the right of the main shape, and only while the island is closed
        val showSide = side != null && !expanded
        val bw by animateDpAsState(if (showSide) baseH.dp else 0.dp, springFor<Dp>(IslandCfg.anim), label = "isl-bw")
        if (side != null && bw > 2.dp) {
            val sideNow by rememberUpdatedState(side)
            val bubbleR = (bw.value * 0.38f * IslandCfg.round / 100f).dp
            IslandSurface(
                Modifier
                    .offset { IntOffset((w / 2 + IslandLayout.GAP.dp + bw / 2).roundToPx(), 0) }
                    .pointerInput(Unit) {
                        detectTapGestures(onTap = { tick(); IslandHub.focusAndOpen(sideNow.id) })
                    },
                bw, bw, islandShape(IslandCfg.shape, bubbleR), bubbleR, skin, if (IslandCfg.fx >= 3) 1 else IslandCfg.fx, side.accent
            ) {
                // When swiping changes the bubble, the old picture fades out and the new one grows in
                AnimatedContent(
                    targetState = side,
                    contentKey = { it.id },
                    transitionSpec = { ContentTransform(fadeIn(tween(220, delayMillis = 40)) + scaleIn(tween(220), 0.6f), fadeOut(tween(140)) + scaleOut(tween(140), 0.6f), 0f, null) },
                    label = "isl-bub"
                ) { sd ->
                    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { Lead(sd, bw * 0.66f, st) }
                }
            }
        }
    }
}

private fun openOwner(ctx: android.content.Context, a: IAct) {
    when (a.kind) {
        IKind.NOTE, IKind.LIVE -> if (a.pkg.isNotEmpty()) openApp(ctx, a.pkg)
        IKind.MEDIA -> IslandMedia.open(ctx)
        IKind.CALL -> Safety.dialerPkgs(ctx).firstOrNull()?.let { openApp(ctx, it) }
        else -> {}
    }
}

/**
 * The shape itself: a soft shadow, a glow behind it, an edge, and the inside with a faint light along the top.
 * The edge is a still gradient, or a light that runs around it (Sweep, Rainbow). Everything that moves is drawn in
 * the draw step, so the rest does not redraw each frame.
 */
@Composable
private fun IslandSurface(
    modifier: Modifier, w: Dp, h: Dp, shape: Shape, radius: Dp, skin: ISkin, fx: Int, accent: Color,
    content: @Composable BoxScope.() -> Unit
) {
    val sweep: State<Float>? =
        if (fx >= 3) rememberInfiniteTransition(label = "isl-sw").animateFloat(0f, 360f, infiniteRepeatable(tween(3400, easing = LinearEasing)), label = "isl-sp") else null
    val pulse: State<Float>? =
        if (fx == 2) rememberInfiniteTransition(label = "isl-pu").animateFloat(0.25f, 1f, infiniteRepeatable(tween(1300, easing = FastOutSlowInEasing), RepeatMode.Reverse), label = "isl-pp") else null
    val tinted = IslandCfg.tint
    val edge = remember(skin, accent, tinted) {
        if (tinted) listOf(accent.copy(alpha = 0.60f), accent.copy(alpha = 0.16f), accent.copy(alpha = 0.42f)) else skin.border
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
                val r = radius.toPx()
                // A soft shadow under the shape, a few faint layers that grow and sink a little
                for (i in 1..4) {
                    val g = i * 1.7.dp.toPx()
                    drawRoundRect(
                        color = Color.Black.copy(alpha = (if (skin.light) 0.10f else 0.16f) * (1f - i / 5f)),
                        topLeft = Offset(-g, -g * 0.25f),
                        size = Size(size.width + 2 * g, size.height + 2.5f * g),
                        cornerRadius = CornerRadius(r + g)
                    )
                }
                // A soft halo made of a few wide, faint layers
                val strength = when (fx) { 1 -> 0.9f; 2 -> pulse?.value ?: 1f; 3, 4 -> 0.7f; else -> 0f }
                if (strength > 0f) {
                    for (i in 1..6) {
                        val g = i * 2.3.dp.toPx()
                        drawRoundRect(
                            color = accent.copy(alpha = (0.15f * strength * (1f - i / 7f)).coerceIn(0f, 1f)),
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
                // A faint light along the top edge, the glass look
                .drawBehind {
                    drawRect(
                        Brush.verticalGradient(
                            listOf(Color.White.copy(alpha = if (skin.light) 0.55f else 0.075f), Color.Transparent),
                            startY = 0f, endY = size.height * 0.55f
                        )
                    )
                },
            content = content
        )
    }
}

// ============================================================================================= small pieces

private fun netIcon(): ImageVector = when {
    !IslandNet.online -> Icons.Outlined.WifiOff
    IslandNet.wifi -> Icons.Outlined.Wifi
    else -> Icons.Outlined.SignalCellularAlt
}

@Composable
private fun Lead(a: IAct, size: Dp, st: IStyle) {
    val art = IslandMedia.art
    when {
        a.kind == IKind.MEDIA && art != null ->
            Image(art, null, Modifier.size(size).clip(RoundedCornerShape(size * 0.28f)), contentScale = ContentScale.Crop)
        a.pkg.isNotEmpty() && a.kind != IKind.MEDIA -> AppIcon(a.pkg, size)
        else -> Box(
            Modifier.size(size).clip(RoundedCornerShape(size * 0.30f)).background(a.accent.copy(alpha = 0.24f)),
            contentAlignment = Alignment.Center
        ) {
            val ic = if (a.kind == IKind.NET) netIcon() else a.icon
            if (a.glyph.isNotEmpty()) Text(a.glyph, color = a.accent, fontSize = (size.value * 0.40f).sp, fontWeight = FontWeight.ExtraBold, maxLines = 1)
            else ic?.let { Icon(it, null, tint = a.accent, modifier = Modifier.size(size * 0.58f)) }
        }
    }
}

@Composable
private fun Equalizer(playing: Boolean, color: Color, height: Dp) {
    val t = if (playing) rememberInfiniteTransition(label = "eq") else null
    val a: State<Float>? = t?.animateFloat(0.25f, 1f, infiniteRepeatable(tween(380, easing = LinearEasing), RepeatMode.Reverse), label = "e1")
    val b: State<Float>? = t?.animateFloat(0.30f, 1f, infiniteRepeatable(tween(520, easing = LinearEasing), RepeatMode.Reverse), label = "e2")
    val cc: State<Float>? = t?.animateFloat(0.20f, 1f, infiniteRepeatable(tween(440, easing = LinearEasing), RepeatMode.Reverse), label = "e3")
    val e: State<Float>? = t?.animateFloat(0.35f, 1f, infiniteRepeatable(tween(610, easing = LinearEasing), RepeatMode.Reverse), label = "e4")
    Row(Modifier.height(height), horizontalArrangement = Arrangement.spacedBy(2.dp), verticalAlignment = Alignment.Bottom) {
        listOf(a, b, cc, e).forEach { s ->
            Box(
                Modifier.width(3.dp).fillMaxHeight()
                    .graphicsLayer { scaleY = s?.value ?: 0.3f; transformOrigin = TransformOrigin(0.5f, 1f) }
                    .clip(RoundedCornerShape(2.dp)).background(color)
            )
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
            1 -> drawPath(Path().apply { moveTo(w * 0.28f, h * 0.16f); lineTo(w * 0.28f, h * 0.84f); lineTo(w * 0.86f, h * 0.5f); close() }, color)
            2 -> {
                drawRoundRect(color, Offset(w * 0.22f, h * 0.16f), Size(w * 0.21f, h * 0.68f), CornerRadius(w * 0.05f))
                drawRoundRect(color, Offset(w * 0.57f, h * 0.16f), Size(w * 0.21f, h * 0.68f), CornerRadius(w * 0.05f))
            }
            3 -> {
                drawPath(Path().apply { moveTo(w * 0.16f, h * 0.2f); lineTo(w * 0.16f, h * 0.8f); lineTo(w * 0.66f, h * 0.5f); close() }, color)
                drawRoundRect(color, Offset(w * 0.72f, h * 0.2f), Size(w * 0.12f, h * 0.6f), CornerRadius(w * 0.03f))
            }
            else -> {
                drawPath(Path().apply { moveTo(w * 0.84f, h * 0.2f); lineTo(w * 0.84f, h * 0.8f); lineTo(w * 0.34f, h * 0.5f); close() }, color)
                drawRoundRect(color, Offset(w * 0.16f, h * 0.2f), Size(w * 0.12f, h * 0.6f), CornerRadius(w * 0.03f))
            }
        }
    }
}

/** A square button with rounded corners. The whole island is rectangular, so the buttons are too. */
@Composable
private fun RoundBtn(bg: Color, size: Dp, onClick: () -> Unit, content: @Composable () -> Unit) {
    Box(
        Modifier.bounceClick(onClick = onClick).size(size).clip(RoundedCornerShape(size * 0.34f)).background(bg),
        contentAlignment = Alignment.Center
    ) { content() }
}

/** A wide button with a picture and a word, for the things that matter most: Answer, Decline, Stop. */
@Composable
private fun ActionBtn(label: String, icon: ImageVector, bg: Color, fg: Color, height: Dp, fs: Float, modifier: Modifier, onClick: () -> Unit) {
    Row(
        modifier.bounceClick(onClick = onClick).height(height).clip(RoundedCornerShape(height * 0.34f)).background(bg),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(icon, label, tint = fg, modifier = Modifier.size(height * 0.46f))
        Spacer(Modifier.width(6.dp))
        Text(label, color = fg, fontSize = fs.sp, fontWeight = FontWeight.SemiBold, maxLines = 1)
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

/** The number to draw for the timer or the stopwatch. It only ticks while the clock runs and the card is on screen. */
@Composable
private fun clockTick(fast: Boolean): Long {
    var t by remember { mutableLongStateOf(IslandClock.shown()) }
    LaunchedEffect(IslandClock.running, IslandClock.mode, fast) {
        while (true) {
            t = IslandClock.shown()
            if (!IslandClock.running) break
            delay(if (fast) 50L else 200L)
        }
    }
    return t
}

@Composable
private fun ClockText(color: Color, fs: Float, centis: Boolean, weight: FontWeight) {
    val t = clockTick(centis)
    val txt = if (IslandClock.mode == 2 && !centis) islandClockText(((t + 999L) / 1000L) * 1000L) else islandClockText(t, centis && IslandClock.mode == 1)
    // Digits of the same width, so the numbers do not jiggle
    Text(txt, color = color, fontSize = fs.sp, fontWeight = weight, maxLines = 1, style = TextStyle(fontFeatureSettings = "tnum"))
}

/** A thin bar. A negative [frac] means "no numbers": a light runs back and forth instead. */
@Composable
private fun Bar(frac: Float, color: Color, track: Color, height: Dp) {
    val runX: State<Float>? =
        if (frac < 0f) rememberInfiniteTransition(label = "bar").animateFloat(0f, 1f, infiniteRepeatable(tween(1200, easing = FastOutSlowInEasing), RepeatMode.Reverse), label = "bar-x") else null
    Box(Modifier.fillMaxWidth().height(height).clip(RoundedCornerShape(height / 2)).background(track)) {
        if (runX == null) {
            val f by animateFloatAsState(frac.coerceIn(0f, 1f), tween(400, easing = FastOutSlowInEasing), label = "bar-f")
            Box(Modifier.fillMaxWidth(f).fillMaxHeight().clip(RoundedCornerShape(height / 2)).background(color))
        } else {
            Box(
                Modifier.fillMaxWidth(0.3f).fillMaxHeight()
                    .graphicsLayer { translationX = runX.value * size.width / 0.3f * 0.7f }
                    .clip(RoundedCornerShape(height / 2)).background(color)
            )
        }
    }
}

// ============================================================================================= compact

@Composable
private fun CompactContent(a: IAct, st: IStyle, h: Dp) {
    val d = st.d
    val pad = (h.value * 0.20f).dp
    val lead = h * 0.64f
    val s by Monitor.snap.collectAsState()
    val title = when (a.kind) {
        IKind.CALL -> Calls.caller.ifEmpty { if (Calls.state == TelephonyManager.CALL_STATE_RINGING) "Incoming call" else "On call" }
        IKind.MEDIA -> IslandMedia.title.ifEmpty { "Playing" }
        IKind.CHARGE -> "${s.batt.level}%"
        IKind.RECORD -> "REC"
        IKind.NET -> "\u2193 ${IslandNet.rate(IslandNet.down)}"
        else -> a.title
    }
    Row(Modifier.fillMaxWidth().height(h).padding(horizontal = pad), verticalAlignment = Alignment.CenterVertically) {
        Lead(a, lead, st)
        Spacer(Modifier.width(7.dp))
        if (a.kind == IKind.TIMER) {
            Box(Modifier.weight(1f)) { ClockText(st.skin.text, 13f * d, false, FontWeight.SemiBold) }
        } else {
            Text(title, color = st.skin.text, fontSize = (12f * d).sp, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
        }
        Spacer(Modifier.width(6.dp))
        when (a.kind) {
            IKind.CALL ->
                if (Calls.state == TelephonyManager.CALL_STATE_OFFHOOK) Elapsed(Calls.startedAt, st.skin.sub, 11f * d)
                else Text("Ringing", color = st.skin.sub, fontSize = (10f * d).sp, maxLines = 1)
            IKind.MEDIA -> Equalizer(IslandMedia.playing, a.accent, lead * 0.7f)
            IKind.RECORD -> Elapsed(Recorder.startedAt, st.skin.sub, 11f * d)
            IKind.CHARGE -> Icon(Icons.Outlined.Bolt, null, tint = a.accent, modifier = Modifier.size(lead * 0.8f))
            IKind.TIMER ->
                if (IslandClock.running) Box(Modifier.size(7.dp).clip(CircleShape).background(a.accent))
                else Icon(Icons.Outlined.Pause, null, tint = st.skin.sub, modifier = Modifier.size(lead * 0.7f))
            IKind.NET -> Text(IslandNet.type, color = st.skin.sub, fontSize = (10f * d).sp, maxLines = 1)
            IKind.LIVE -> {
                if (a.progress >= 0f) Text("${(a.progress * 100f).toInt()}%", color = st.skin.sub, fontSize = (10.5f * d).sp, fontWeight = FontWeight.Medium, maxLines = 1)
            }
            else -> {}
        }
    }
}

// ============================================================================================= expanded

@Composable
private fun ExpandedContent(a: IAct, st: IStyle) {
    val d = st.d
    Column(Modifier.fillMaxSize().padding((14f * d).dp), verticalArrangement = Arrangement.Center) {
        when (a.kind) {
            IKind.CALL -> CallExpanded(a, st)
            IKind.MEDIA -> MediaExpanded(a, st)
            IKind.CHARGE -> ChargeExpanded(a, st)
            IKind.RECORD -> RecordExpanded(a, st)
            IKind.NOTE -> NoteExpanded(a, st)
            IKind.FLASH -> FlashExpanded(a, st)
            IKind.TIMER -> TimerExpanded(a, st)
            IKind.NET -> NetExpanded(a, st)
            IKind.LIVE -> LiveExpanded(a, st)
        }
    }
}

@Composable
private fun CallExpanded(a: IAct, st: IStyle) {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    val d = st.d
    val ringing = Calls.state == TelephonyManager.CALL_STATE_RINGING
    val name = Calls.caller.ifEmpty { if (ringing) "Incoming call" else "On call" }
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size((46f * d).dp).clip(RoundedCornerShape((14f * d).dp)).background(a.accent.copy(alpha = 0.25f)), contentAlignment = Alignment.Center) {
            val ch = name.firstOrNull { it.isLetterOrDigit() }?.uppercaseChar()?.toString()
            if (ch != null && Calls.caller.isNotEmpty()) Text(ch, color = a.accent, fontSize = (20f * d).sp, fontWeight = FontWeight.Bold)
            else Icon(Icons.Outlined.Call, null, tint = a.accent, modifier = Modifier.size((24f * d).dp))
        }
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(name, color = st.skin.text, fontSize = (16f * d).sp, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
            if (ringing) Text("Incoming call", color = st.skin.sub, fontSize = (12f * d).sp)
            else Elapsed(Calls.startedAt, st.skin.sub, 12f * d)
        }
    }
    Spacer(Modifier.height(12.dp))
    val bh = (44f * d).dp
    val fs = 13f * d
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
        if (ringing) {
            ActionBtn("Decline", Icons.Outlined.CallEnd, Color(0xFFFF4D6D), Color.White, bh, fs, Modifier.weight(1f)) { scope.launch(Dispatchers.IO) { Calls.end(ctx) } }
            ActionBtn("Answer", Icons.Outlined.Call, Color(0xFF34D399), Color.White, bh, fs, Modifier.weight(1f)) { scope.launch(Dispatchers.IO) { Calls.answer(ctx) } }
        } else {
            ActionBtn(
                "Speaker", Icons.Outlined.Speaker,
                if (Calls.speaker) st.on else st.chip, if (Calls.speaker) st.onInk else st.skin.text, bh, fs, Modifier.weight(1f)
            ) { Calls.setSpeaker(ctx, !Calls.speaker) }
            ActionBtn(
                "Mute", if (Calls.muted) Icons.Outlined.MicOff else Icons.Outlined.Mic,
                if (Calls.muted) st.on else st.chip, if (Calls.muted) st.onInk else st.skin.text, bh, fs, Modifier.weight(1f)
            ) { Calls.setMute(ctx, !Calls.muted) }
            ActionBtn("End", Icons.Outlined.CallEnd, Color(0xFFFF4D6D), Color.White, bh, fs, Modifier.weight(1f)) { scope.launch(Dispatchers.IO) { Calls.end(ctx) } }
        }
    }
}

@Composable
private fun MediaExpanded(a: IAct, st: IStyle) {
    val ctx = LocalContext.current
    val d = st.d
    val sub = st.skin.sub
    var pos by remember { mutableLongStateOf(0L) }
    // While a finger drags the bar this is the place it points to (0 to 1), otherwise -1
    var scrub by remember { mutableFloatStateOf(-1f) }
    LaunchedEffect(IslandMedia.playing, IslandMedia.title) {
        while (true) {
            pos = IslandMedia.positionMs()
            delay(500L)
        }
    }
    val dur = IslandMedia.durationMs
    val frac = if (scrub >= 0f) scrub else if (dur > 0L) (pos.toFloat() / dur).coerceIn(0f, 1f) else 0f
    Row(verticalAlignment = Alignment.CenterVertically) {
        val art = IslandMedia.art
        Box(Modifier.size((56f * d).dp).clip(RoundedCornerShape(14.dp)).background(a.accent.copy(alpha = 0.22f)), contentAlignment = Alignment.Center) {
            if (art != null) Image(art, null, Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
            else Icon(Icons.Outlined.MusicNote, null, tint = a.accent, modifier = Modifier.size((28f * d).dp))
        }
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(IslandMedia.title.ifEmpty { "Playing" }, color = st.skin.text, fontSize = (15f * d).sp, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(IslandMedia.artist, color = sub, fontSize = (12f * d).sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        Equalizer(IslandMedia.playing, a.accent, (22f * d).dp)
    }
    Spacer(Modifier.height(8.dp))
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(clock((if (scrub >= 0f) (scrub * dur).toLong() else pos) / 1000L), color = sub, fontSize = (10f * d).sp, modifier = Modifier.width(38.dp))
        Box(
            Modifier.weight(1f).height(20.dp)
                .pointerInput(dur) { detectTapGestures { o -> if (dur > 0L) IslandMedia.seekTo((o.x / size.width * dur).toLong()) } }
                // Drag along the bar to scrub. This also keeps a swipe that starts on the bar from changing the island.
                .pointerInput(dur) {
                    detectHorizontalDragGestures(
                        onDragStart = { o -> if (dur > 0L) scrub = (o.x / size.width).coerceIn(0f, 1f) },
                        onDragEnd = { if (dur > 0L && scrub >= 0f) IslandMedia.seekTo((scrub * dur).toLong()); scrub = -1f },
                        onDragCancel = { scrub = -1f },
                        onHorizontalDrag = { ch, _ -> ch.consume(); if (dur > 0L) scrub = (ch.position.x / size.width).coerceIn(0f, 1f) }
                    )
                },
            contentAlignment = Alignment.CenterStart
        ) {
            Box(Modifier.fillMaxWidth().height(4.dp).clip(CircleShape).background(st.track))
            Box(Modifier.fillMaxWidth(frac).height(if (scrub >= 0f) 6.dp else 4.dp).clip(CircleShape).background(a.accent))
        }
        Text(if (dur > 0L) clock(dur / 1000L) else "--:--", color = sub, fontSize = (10f * d).sp, modifier = Modifier.width(38.dp), textAlign = TextAlign.End)
    }
    Spacer(Modifier.height(4.dp))
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly, verticalAlignment = Alignment.CenterVertically) {
        val ic = st.skin.text
        RoundBtn(Color.Transparent, (40f * d).dp, { IslandMedia.prev() }) { MediaGlyph(0, ic, (24f * d).dp) }
        RoundBtn(st.chip, (46f * d).dp, { IslandMedia.toggle() }) { MediaGlyph(if (IslandMedia.playing) 2 else 1, ic, (26f * d).dp) }
        RoundBtn(Color.Transparent, (40f * d).dp, { IslandMedia.next() }) { MediaGlyph(3, ic, (24f * d).dp) }
        RoundBtn(Color.Transparent, (40f * d).dp, { IslandMedia.open(ctx); IslandHub.collapse() }) {
            Icon(Icons.Outlined.OpenInNew, "Open player", tint = sub, modifier = Modifier.size((20f * d).dp))
        }
    }
}

@Composable
private fun ChargeExpanded(a: IAct, st: IStyle) {
    val d = st.d
    val s by Monitor.snap.collectAsState()
    val b = s.batt
    val frac = (b.level / 100f).coerceIn(0f, 1f)
    // The ring fills up when the card opens, and the bolt breathes while the phone charges
    val shown by animateFloatAsState(frac, tween(900, easing = FastOutSlowInEasing), label = "chg")
    val breathe: State<Float>? =
        if (b.charging) rememberInfiniteTransition(label = "bolt").animateFloat(0.45f, 1f, infiniteRepeatable(tween(900, easing = FastOutSlowInEasing), RepeatMode.Reverse), label = "bolt-a") else null
    val col = if (!b.charging && b.level <= 15) Color(0xFFFF6B8B) else a.accent
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size((62f * d).dp), contentAlignment = Alignment.Center) {
            Canvas(Modifier.fillMaxSize()) {
                val sw = 6.dp.toPx()
                val inset = sw / 2f
                val sz = Size(size.width - sw, size.height - sw)
                drawArc(st.track, -90f, 360f, false, Offset(inset, inset), sz, style = Stroke(sw))
                drawArc(col, -90f, 360f * shown, false, Offset(inset, inset), sz, style = Stroke(sw, cap = StrokeCap.Round))
            }
            Text("${b.level}", color = st.skin.text, fontSize = (18f * d).sp, fontWeight = FontWeight.Bold)
        }
        Spacer(Modifier.width(14.dp))
        Column(Modifier.weight(1f)) {
            Text(if (b.charging) "Charging" else if (b.level <= 15) "Battery low" else "Battery", color = st.skin.text, fontSize = (16f * d).sp, fontWeight = FontWeight.Bold)
            Text("${b.ma} mA   ${"%.1f".format(b.tempC)}\u00B0C", color = st.skin.sub, fontSize = (12f * d).sp)
            if (b.plugged != "-") Text(b.plugged, color = st.skin.sub, fontSize = (11f * d).sp)
        }
        Icon(Icons.Outlined.Bolt, null, tint = col.copy(alpha = breathe?.value ?: 1f), modifier = Modifier.size((26f * d).dp))
    }
}

@Composable
private fun RecordExpanded(a: IAct, st: IStyle) {
    val scope = rememberCoroutineScope()
    val d = st.d
    Row(verticalAlignment = Alignment.CenterVertically) {
        Lead(a, (44f * d).dp, st)
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text("Recording", color = st.skin.text, fontSize = (16f * d).sp, fontWeight = FontWeight.Bold)
            Elapsed(Recorder.startedAt, st.skin.sub, 13f * d)
        }
        ActionBtn("Stop", Icons.Outlined.Stop, Color(0xFFFF4D6D), Color.White, (40f * d).dp, 13f * d, Modifier.width((92f * d).dp)) { scope.launch(Dispatchers.IO) { Recorder.stop() } }
    }
}

@Composable
private fun NoteExpanded(a: IAct, st: IStyle) {
    val ctx = LocalContext.current
    val d = st.d
    Row(verticalAlignment = Alignment.Top) {
        Lead(a, (40f * d).dp, st)
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(a.title, color = st.skin.text, fontSize = (14f * d).sp, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(a.sub, color = st.skin.sub, fontSize = (12f * d).sp, maxLines = 3, overflow = TextOverflow.Ellipsis)
        }
    }
    Spacer(Modifier.height(8.dp))
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
        ActionBtn("Dismiss", Icons.Outlined.Close, st.chip, st.skin.text, (36f * d).dp, 12f * d, Modifier.weight(1f)) { IslandHub.dismiss(a.id) }
        ActionBtn("Open", Icons.Outlined.OpenInBrowser, a.accent, Ink, (36f * d).dp, 12f * d, Modifier.weight(1f)) { openApp(ctx, a.pkg); IslandHub.dismiss(a.id) }
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
}

@Composable
private fun TimerExpanded(a: IAct, st: IStyle) {
    val d = st.d
    val timer = IslandClock.mode == 2
    val t = clockTick(false)
    Row(verticalAlignment = Alignment.CenterVertically) {
        Lead(a, (30f * d).dp, st)
        Spacer(Modifier.width(10.dp))
        Column(Modifier.weight(1f)) {
            Text(if (timer) "Timer" else "Stopwatch", color = st.skin.text, fontSize = (14f * d).sp, fontWeight = FontWeight.Bold)
            Text(if (IslandClock.running) "Running" else "Paused", color = st.skin.sub, fontSize = (11f * d).sp)
        }
        if (!timer && IslandClock.laps.isNotEmpty()) {
            Text("Lap ${IslandClock.laps.size}  ${islandClockText(IslandClock.laps.first(), true)}", color = st.skin.sub, fontSize = (11f * d).sp, maxLines = 1)
        }
    }
    Box(Modifier.fillMaxWidth().padding(vertical = 2.dp), contentAlignment = Alignment.Center) {
        ClockText(st.skin.text, 38f * d, !timer, FontWeight.Light)
    }
    if (timer) {
        val frac = if (IslandClock.totalMs > 0L) (IslandClock.remaining().toFloat() / IslandClock.totalMs).coerceIn(0f, 1f) else 0f
        // Reading t keeps the bar moving while the clock runs
        Bar(if (t >= 0L) frac else 0f, a.accent, st.track, 4.dp)
    }
    Spacer(Modifier.height(8.dp))
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly, verticalAlignment = Alignment.CenterVertically) {
        RoundBtn(st.chip, (42f * d).dp, { IslandClock.stop() }) { Icon(Icons.Outlined.Stop, "Reset", tint = st.skin.text, modifier = Modifier.size((22f * d).dp)) }
        RoundBtn(a.accent, (48f * d).dp, { IslandClock.toggle() }) {
            Icon(if (IslandClock.running) Icons.Outlined.Pause else Icons.Outlined.PlayArrow, if (IslandClock.running) "Pause" else "Start", tint = Ink, modifier = Modifier.size((26f * d).dp))
        }
        if (timer) RoundBtn(st.chip, (42f * d).dp, { IslandClock.addMinute() }) {
            Text("+1", color = st.skin.text, fontSize = (14f * d).sp, fontWeight = FontWeight.Bold)
        } else RoundBtn(st.chip, (42f * d).dp, { IslandClock.lap() }) {
            Icon(Icons.Outlined.Flag, "Lap", tint = st.skin.text, modifier = Modifier.size((22f * d).dp))
        }
    }
}

@Composable
private fun NetStat(label: String, arrow: String, value: String, modifier: Modifier, st: IStyle, color: Color) {
    val d = st.d
    Column(modifier) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(arrow, color = color, fontSize = (12f * d).sp, fontWeight = FontWeight.Bold)
            Spacer(Modifier.width(4.dp))
            Text(label, color = st.skin.sub, fontSize = (11f * d).sp)
        }
        Text(value, color = st.skin.text, fontSize = (17f * d).sp, fontWeight = FontWeight.Bold, maxLines = 1, style = TextStyle(fontFeatureSettings = "tnum"))
    }
}

/** The last speeds as a small filled line. */
@Composable
private fun SpeedGraph(data: List<Float>, color: Color, modifier: Modifier) {
    Canvas(modifier) {
        if (data.size < 2) return@Canvas
        val mx = (data.maxOrNull() ?: 1f).coerceAtLeast(50_000f)
        val stepX = size.width / (data.size - 1)
        val line = Path()
        val fill = Path()
        data.forEachIndexed { i, v ->
            val x = i * stepX
            val y = size.height - (v / mx) * size.height * 0.92f - 1f
            if (i == 0) { line.moveTo(x, y); fill.moveTo(x, size.height); fill.lineTo(x, y) } else { line.lineTo(x, y); fill.lineTo(x, y) }
        }
        fill.lineTo(size.width, size.height)
        fill.close()
        drawPath(fill, Brush.verticalGradient(listOf(color.copy(alpha = 0.35f), Color.Transparent)))
        drawPath(line, color, style = Stroke(2.dp.toPx(), cap = StrokeCap.Round))
    }
}

@Composable
private fun NetExpanded(a: IAct, st: IStyle) {
    val d = st.d
    Row(verticalAlignment = Alignment.CenterVertically) {
        Lead(a, (30f * d).dp, st)
        Spacer(Modifier.width(10.dp))
        Column(Modifier.weight(1f)) {
            Text(IslandNet.type.ifEmpty { "Network" }, color = st.skin.text, fontSize = (15f * d).sp, fontWeight = FontWeight.Bold)
            Text(if (IslandNet.online) "Connected" else "No connection", color = st.skin.sub, fontSize = (11f * d).sp)
        }
    }
    Spacer(Modifier.height(8.dp))
    Row(Modifier.fillMaxWidth()) {
        NetStat("Download", "\u2193", IslandNet.rate(IslandNet.down), Modifier.weight(1f), st, a.accent)
        NetStat("Upload", "\u2191", IslandNet.rate(IslandNet.up), Modifier.weight(1f), st, Color(0xFFA78BFA))
    }
    Spacer(Modifier.height(8.dp))
    SpeedGraph(IslandNet.history, a.accent, Modifier.fillMaxWidth().height((32f * d).dp))
}

@Composable
private fun LiveExpanded(a: IAct, st: IStyle) {
    val ctx = LocalContext.current
    val d = st.d
    Row(verticalAlignment = Alignment.Top) {
        Lead(a, (40f * d).dp, st)
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(a.title, color = st.skin.text, fontSize = (14f * d).sp, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
            if (a.sub.isNotEmpty()) Text(a.sub, color = st.skin.sub, fontSize = (12f * d).sp, maxLines = 2, overflow = TextOverflow.Ellipsis)
        }
        if (a.progress >= 0f) Text("${(a.progress * 100f).toInt()}%", color = st.skin.text, fontSize = (13f * d).sp, fontWeight = FontWeight.Bold)
    }
    Spacer(Modifier.height(8.dp))
    Bar(a.progress, a.accent, st.track, 4.dp)
    Spacer(Modifier.height(10.dp))
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
        ActionBtn("Hide", Icons.Outlined.Close, st.chip, st.skin.text, (36f * d).dp, 12f * d, Modifier.weight(1f)) { IslandHub.dismiss(a.id) }
        ActionBtn("Open", Icons.Outlined.OpenInBrowser, a.accent, Ink, (36f * d).dp, 12f * d, Modifier.weight(1f)) { openApp(ctx, a.pkg); IslandHub.collapse() }
    }
}
