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
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
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
import androidx.compose.runtime.mutableLongStateOf
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

/** One look for the island: the inside, the edge and the text colours. */
class ISkin(
    val name: String,
    val fill: List<Color>,
    val border: List<Color>,
    val borderW: Float,
    val text: Color,
    val sub: Color
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
    ISkin("Light", listOf(c(0xF2F7F8FB), c(0xF2E4E9F2)), listOf(c(0x66FFFFFF), c(0x33000000)), 1.0f, c(0xFF0F172A), c(0xFF475569))
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
        m == null -> IslandCfg.w * d
        expanded -> IslandLayout.expandedW(IslandHub.screenWdp - bubble, d)
        else -> IslandLayout.compactW(m.kind, d)
    }
    val th = if (m != null && expanded) IslandLayout.expandedH(m.kind, d) else baseH
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

    Box(Modifier.fillMaxSize().padding(top = IslandLayout.PAD.dp), contentAlignment = Alignment.TopCenter) {
        Row(
            Modifier.graphicsLayer {
                val a = appear.value
                alpha = (a * IslandCfg.alpha / 100f).coerceIn(0f, 1f)
                val sc = 0.55f + 0.45f * a
                scaleX = sc; scaleY = sc
                transformOrigin = TransformOrigin(0.5f, 0f)
            },
            horizontalArrangement = Arrangement.spacedBy(IslandLayout.GAP.dp),
            verticalAlignment = Alignment.Top
        ) {
            IslandSurface(
                Modifier
                    .pointerInput(Unit) {
                        detectTapGestures(
                            onTap = { if (mNow != null) { tick(); IslandHub.toggleExpanded() } },
                            onLongPress = { mNow?.let { tick(); openOwner(ctx, it) } }
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
                                    else if (cur != null && (cur.kind == IKind.FLASH || cur.kind == IKind.NOTE || cur.kind == IKind.CHARGE)) IslandHub.remove(cur.id)
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
                    } else Box(Modifier.fillMaxSize())
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
                    bw, bw, CircleShape, bw / 2, skin, if (IslandCfg.fx >= 3) 1 else IslandCfg.fx, side.accent
                ) {
                    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { Lead(side, bw * 0.66f, st) }
                }
            }
        }
    }
}

private fun openOwner(ctx: android.content.Context, a: IAct) {
    when (a.kind) {
        IKind.NOTE -> if (a.pkg.isNotEmpty()) openApp(ctx, a.pkg)
        IKind.MEDIA -> IslandMedia.open(ctx)
        IKind.CALL -> Safety.dialerPkgs(ctx).firstOrNull()?.let { openApp(ctx, it) }
        else -> {}
    }
}

/**
 * The shape itself: a glow behind it, an edge, and the inside. The edge is a still gradient, or a light that runs
 * around it (Sweep, Rainbow). Everything that moves is drawn in the draw step, so the rest does not redraw each frame.
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
        Box(Modifier.fillMaxSize().padding(bw).clip(shape).background(inner), content = content)
    }
}

// ============================================================================================= small pieces

@Composable
private fun Lead(a: IAct, size: Dp, st: IStyle) {
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

@Composable
private fun RoundBtn(bg: Color, size: Dp, onClick: () -> Unit, content: @Composable () -> Unit) {
    Box(
        Modifier.bounceClick(onClick = onClick).size(size).clip(CircleShape).background(bg),
        contentAlignment = Alignment.Center
    ) { content() }
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

// ============================================================================================= compact

@Composable
private fun CompactContent(a: IAct, st: IStyle, h: Dp) {
    val d = st.d
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
    Row(Modifier.fillMaxWidth().height(h).padding(horizontal = pad), verticalAlignment = Alignment.CenterVertically) {
        Lead(a, lead, st)
        Spacer(Modifier.width(7.dp))
        Text(title, color = st.skin.text, fontSize = (12f * d).sp, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
        Spacer(Modifier.width(6.dp))
        when (a.kind) {
            IKind.CALL ->
                if (Calls.state == TelephonyManager.CALL_STATE_OFFHOOK) Elapsed(Calls.startedAt, st.skin.sub, 11f * d)
                else Text("Ringing", color = st.skin.sub, fontSize = (10f * d).sp, maxLines = 1)
            IKind.MEDIA -> Equalizer(IslandMedia.playing, a.accent, lead * 0.7f)
            IKind.RECORD -> Elapsed(Recorder.startedAt, st.skin.sub, 11f * d)
            IKind.CHARGE -> Icon(Icons.Outlined.Bolt, null, tint = a.accent, modifier = Modifier.size(lead * 0.8f))
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
        Box(Modifier.size((46f * d).dp).clip(CircleShape).background(a.accent.copy(alpha = 0.25f)), contentAlignment = Alignment.Center) {
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
    val bs = (44f * d).dp
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly, verticalAlignment = Alignment.CenterVertically) {
        if (ringing) {
            RoundBtn(Color(0xFFFF4D6D), bs, { scope.launch(Dispatchers.IO) { Calls.end(ctx) } }) {
                Icon(Icons.Outlined.CallEnd, "Decline", tint = Color.White, modifier = Modifier.size(bs * 0.5f))
            }
            RoundBtn(Color(0xFF34D399), bs, { scope.launch(Dispatchers.IO) { Calls.answer(ctx) } }) {
                Icon(Icons.Outlined.Call, "Answer", tint = Color.White, modifier = Modifier.size(bs * 0.5f))
            }
        } else {
            RoundBtn(if (Calls.speaker) Color.White else Color.White.copy(alpha = 0.16f), bs, { Calls.setSpeaker(ctx, !Calls.speaker) }) {
                Icon(Icons.Outlined.Speaker, "Speaker", tint = if (Calls.speaker) Ink else Color.White, modifier = Modifier.size(bs * 0.5f))
            }
            RoundBtn(if (Calls.muted) Color.White else Color.White.copy(alpha = 0.16f), bs, { Calls.setMute(ctx, !Calls.muted) }) {
                Icon(if (Calls.muted) Icons.Outlined.MicOff else Icons.Outlined.Mic, "Mute", tint = if (Calls.muted) Ink else Color.White, modifier = Modifier.size(bs * 0.5f))
            }
            RoundBtn(Color(0xFFFF4D6D), bs, { scope.launch(Dispatchers.IO) { Calls.end(ctx) } }) {
                Icon(Icons.Outlined.CallEnd, "End call", tint = Color.White, modifier = Modifier.size(bs * 0.5f))
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
            delay(500L)
        }
    }
    val dur = IslandMedia.durationMs
    val frac = if (dur > 0L) (pos.toFloat() / dur).coerceIn(0f, 1f) else 0f
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
    Spacer(Modifier.height(10.dp))
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(clock(pos / 1000L), color = sub, fontSize = (10f * d).sp, modifier = Modifier.width(38.dp))
        Box(
            Modifier.weight(1f).height(14.dp)
                .pointerInput(dur) { detectTapGestures { o -> if (dur > 0L) IslandMedia.seekTo((o.x / size.width * dur).toLong()) } },
            contentAlignment = Alignment.CenterStart
        ) {
            Box(Modifier.fillMaxWidth().height(4.dp).clip(CircleShape).background(Color.White.copy(alpha = 0.18f)))
            Box(Modifier.fillMaxWidth(frac).height(4.dp).clip(CircleShape).background(a.accent))
        }
        Text(if (dur > 0L) clock(dur / 1000L) else "--:--", color = sub, fontSize = (10f * d).sp, modifier = Modifier.width(38.dp), textAlign = TextAlign.End)
    }
    Spacer(Modifier.height(6.dp))
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly, verticalAlignment = Alignment.CenterVertically) {
        val ic = st.skin.text
        RoundBtn(Color.Transparent, (40f * d).dp, { IslandMedia.prev() }) { MediaGlyph(0, ic, (24f * d).dp) }
        RoundBtn(Color.White.copy(alpha = 0.18f), (46f * d).dp, { IslandMedia.toggle() }) { MediaGlyph(if (IslandMedia.playing) 2 else 1, ic, (26f * d).dp) }
        RoundBtn(Color.Transparent, (40f * d).dp, { IslandMedia.next() }) { MediaGlyph(3, ic, (24f * d).dp) }
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
            Canvas(Modifier.fillMaxSize()) {
                val sw = 6.dp.toPx()
                val inset = sw / 2f
                val sz = Size(size.width - sw, size.height - sw)
                drawArc(Color.White.copy(alpha = 0.16f), -90f, 360f, false, Offset(inset, inset), sz, style = Stroke(sw))
                drawArc(a.accent, -90f, 360f * frac, false, Offset(inset, inset), sz, style = Stroke(sw, cap = StrokeCap.Round))
            }
            Text("${b.level}", color = st.skin.text, fontSize = (18f * d).sp, fontWeight = FontWeight.Bold)
        }
        Spacer(Modifier.width(14.dp))
        Column(Modifier.weight(1f)) {
            Text(if (b.charging) "Charging" else "Battery", color = st.skin.text, fontSize = (16f * d).sp, fontWeight = FontWeight.Bold)
            Text("${b.ma} mA   ${"%.1f".format(b.tempC)}\u00B0C", color = st.skin.sub, fontSize = (12f * d).sp)
            if (b.plugged != "-") Text(b.plugged, color = st.skin.sub, fontSize = (11f * d).sp)
        }
        Icon(Icons.Outlined.Bolt, null, tint = a.accent, modifier = Modifier.size((26f * d).dp))
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
        RoundBtn(Color(0xFFFF4D6D), (44f * d).dp, { scope.launch(Dispatchers.IO) { Recorder.stop() } }) {
            Icon(Icons.Outlined.Stop, "Stop", tint = Color.White, modifier = Modifier.size((22f * d).dp))
        }
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
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End, verticalAlignment = Alignment.CenterVertically) {
        RoundBtn(Color.White.copy(alpha = 0.16f), (36f * d).dp, { IslandHub.remove(a.id) }) {
            Icon(Icons.Outlined.Close, "Dismiss", tint = st.skin.text, modifier = Modifier.size((18f * d).dp))
        }
        Spacer(Modifier.width(10.dp))
        RoundBtn(a.accent, (36f * d).dp, { openApp(ctx, a.pkg); IslandHub.remove(a.id) }) {
            Icon(Icons.Outlined.OpenInBrowser, "Open", tint = Ink, modifier = Modifier.size((18f * d).dp))
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
}
