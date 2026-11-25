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
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.horizontalScroll
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.AutoAwesome
import androidx.compose.material.icons.outlined.BatteryChargingFull
import androidx.compose.material.icons.outlined.BlurOn
import androidx.compose.material.icons.outlined.Bolt
import androidx.compose.material.icons.outlined.Call
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.Mic
import androidx.compose.material.icons.outlined.MusicNote
import androidx.compose.material.icons.outlined.Notifications
import androidx.compose.material.icons.outlined.PhoneAndroid
import androidx.compose.material.icons.outlined.PowerSettingsNew
import androidx.compose.material.icons.outlined.Science
import androidx.compose.material.icons.outlined.SportsEsports
import androidx.compose.material.icons.outlined.TouchApp
import androidx.compose.material.icons.outlined.Vibration
import androidx.compose.material.icons.outlined.Videocam
import androidx.compose.material.icons.outlined.Visibility
import androidx.compose.material.icons.outlined.Warning
import androidx.compose.material3.Icon
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlin.math.sin

// The Island tab: a live picture of the island, the switch, the light edge, the size and place, the motion, and what it shows.

/** The island switch on the Home page. */
@Composable
fun IslandHomeCard() {
    val ctx = LocalContext.current
    var on by remember { mutableStateOf(IslandCfg.on) }
    SwitchRow(
        Icons.Outlined.BlurOn, GViolet, "Dynamic Island",
        "Calls, music, charging, recording and messages at the top of the screen. Everything is in the Island tab.", on
    ) { v ->
        on = v
        IslandCfg.putBool(ctx, "isl_on", v)
        AshuAccessibility.instance?.islandRefresh()
        CallNames.set(ctx, CallNames.wanted(ctx))
    }
}

private val HeroNames = listOf("Idle", "Music", "On call", "Charging")

private fun edgeBrush(style: Int, accent: Color, power: Float): Brush {
    val a = (0.25f + 0.75f * power).coerceIn(0f, 1f)
    return when (style) {
        1 -> Brush.linearGradient(listOf(accent.copy(alpha = a), accent.copy(alpha = a)))
        3 -> Brush.sweepGradient(
            listOf(Color(0xFFFF6B8B), Color(0xFFFFB84D), Color(0xFFA3E635), Color(0xFF22D3EE), Color(0xFF818CF8), Color(0xFFF472B6), Color(0xFFFF6B8B)).map { it.copy(alpha = a) }
        )
        else -> Brush.sweepGradient(
            listOf(Color(0xFF2DE2C0), Color(0xFF60A5FA), Color(0xFFA78BFA), Color(0xFFF472B6), Color(0xFF2DE2C0)).map { it.copy(alpha = a) }
        )
    }
}

/** Five bars that rise and fall like a sound wave. */
@Composable
private fun HeroBars(color: Color, height: Dp) {
    val ph: State<Float> = rememberInfiniteTransition(label = "hero-wave").animateFloat(
        0f, 6.2831855f, infiniteRepeatable(tween(1300, easing = LinearEasing)), label = "hero-wave-phase"
    )
    Canvas(Modifier.size(height * 1.1f, height)) {
        val n = 5
        val bw = size.width / (n * 2f - 1f)
        for (i in 0 until n) {
            val k = 0.30f + 0.70f * ((sin(ph.value + i * 1.25f) + 1f) / 2f)
            val bh = size.height * k
            drawRoundRect(color, Offset(i * 2f * bw, (size.height - bh) / 2f), Size(bw, bh), CornerRadius(bw / 2f))
        }
    }
}

@Composable
private fun HeroPill(phase: Int) {
    val pal = Palette.cur
    Row(Modifier.fillMaxWidth().padding(horizontal = 11.dp), verticalAlignment = Alignment.CenterVertically) {
        when (phase) {
            0 -> {}
            1 -> {
                Box(Modifier.size(20.dp).clip(CircleShape).background(pal.primary.d()))
                Spacer(Modifier.weight(1f))
                HeroBars(pal.accent, 14.dp)
            }
            2 -> {
                Box(Modifier.size(20.dp).clip(CircleShape).background(Color(0xFF34D399)), contentAlignment = Alignment.Center) {
                    Icon(Icons.Outlined.Call, null, tint = Ink, modifier = Modifier.size(12.dp))
                }
                Spacer(Modifier.weight(1f))
                Text("00:12", color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
            }
            else -> {
                Box(Modifier.size(20.dp).clip(CircleShape).background(Color(0xFF34D399).copy(alpha = 0.25f)), contentAlignment = Alignment.Center) {
                    Icon(Icons.Outlined.Bolt, null, tint = Color(0xFF34D399), modifier = Modifier.size(13.dp))
                }
                Spacer(Modifier.weight(1f))
                Text("87%", color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
            }
        }
    }
}

/** A small live picture of the island at the top of a phone, with your size and your edge. */
@Composable
private fun IslandHero(on: Boolean, edge: Int, edgePower: Float) {
    val pal = Palette.cur
    var phase by remember { mutableIntStateOf(0) }
    LaunchedEffect(Unit) {
        while (true) {
            delay(2600L)
            phase = (phase + 1) % 4
        }
    }
    val extra = when (phase) { 0 -> 0f; 1 -> 58f; 2 -> 50f; else -> 32f }
    val w by animateDpAsState((IslandCfg.w + extra).coerceAtMost(220f).dp, spring(dampingRatio = 0.72f, stiffness = 320f), label = "hero-w")
    val shape = RoundedCornerShape(28.dp)
    Box(
        Modifier.fillMaxWidth().height(150.dp).clip(shape)
            .background(Brush.verticalGradient(listOf(pal.bg1, pal.bg2, pal.bg3)))
            .border(1.dp, Line, shape),
        contentAlignment = Alignment.TopCenter
    ) {
        Box(
            Modifier.padding(top = 18.dp).width(if (on) w else 90.dp).height(IslandCfg.h.dp).clip(CircleShape).background(Color.Black)
                .then(
                    if (!on || edge == 0 || edgePower <= 0f) Modifier
                    else Modifier.border(1.4.dp, edgeBrush(edge, pal.accent, edgePower / 100f), CircleShape)
                ),
            contentAlignment = Alignment.Center
        ) {
            if (on) {
                AnimatedContent(
                    targetState = phase,
                    transitionSpec = { fadeIn(tween(220)) togetherWith fadeOut(tween(120)) },
                    label = "hero-content"
                ) { ph -> HeroPill(ph) }
            } else {
                Text("Off", color = Color.White.copy(alpha = 0.5f), fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
            }
        }
        Column(Modifier.align(Alignment.BottomCenter).padding(bottom = 14.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Text(if (on) HeroNames[phase] else "The island is off", color = TextHi, fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
            Text(if (on) "Black glass with a light edge" else "Switch it on below", color = TextLo, fontSize = 12.sp)
        }
    }
}

@Composable
private fun ISlide(label: String, key: String, def: Int, lo: Int, hi: Int, unit: String, changed: (String) -> Unit) {
    val ctx = LocalContext.current
    var v by remember { mutableFloatStateOf(LockStore.int(ctx, key, def).toFloat()) }
    Glass {
        SectionTitle(label, "${v.toInt()}$unit")
        Slider(
            value = v, onValueChange = { v = it }, valueRange = lo.toFloat()..hi.toFloat(),
            onValueChangeFinished = { IslandCfg.putInt(ctx, key, v.toInt()); changed(key) },
            colors = SliderDefaults.colors(thumbColor = Accent, activeTrackColor = Accent, inactiveTrackColor = Tint.copy(alpha = 0.15f))
        )
    }
}

@Composable
private fun ISwitch(key: String, def: Boolean, icon: ImageVector, grad: Grad, title: String, sub: String, changed: (String) -> Unit) {
    val ctx = LocalContext.current
    var v by remember { mutableStateOf(LockStore.bool(ctx, key, def)) }
    SwitchRow(icon, grad, title, sub, v) { on -> v = on; IslandCfg.putBool(ctx, key, on); changed(key) }
}

/** Shows something on the island so you can see a change without waiting for a call. */
private fun demo(ctx: android.content.Context, which: Int) {
    val now = SystemClock.elapsedRealtime()
    val note = IAct("demo_note", IKind.NOTE, 55, "Ashutool", "This is how a message looks on the island", null, "", ctx.packageName, Color(0xFF60A5FA), now + 8000L)
    val charge = IAct("demo_charge", IKind.CHARGE, 30, "Charging", "", Icons.Outlined.BatteryChargingFull, "", "", Color(0xFF34D399), now + 8000L)
    when (which) {
        0 -> IslandHub.upsert(note)
        1 -> IslandHub.upsert(charge)
        2 -> IslandHub.flashNet(ctx, true)
        else -> { IslandHub.upsert(charge); IslandHub.upsert(note) }
    }
}

@Composable
fun IslandPage() {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    LaunchedEffect(Unit) { IslandCfg.load(ctx) }
    var on by remember { mutableStateOf(LockStore.bool(ctx, "isl_on", true)) }
    var edge by remember { mutableIntStateOf(LockStore.int(ctx, "isl_edge", 2).coerceIn(0, 4)) }
    var edgePower by remember { mutableFloatStateOf(LockStore.int(ctx, "isl_edge_pwr", 55).toFloat()) }
    var feel by remember { mutableIntStateOf(LockStore.int(ctx, "isl_feel", 0).coerceIn(0, 2)) }
    var service by remember { mutableStateOf(AshuAccessibility.connected) }
    LaunchedEffect(Unit) {
        while (true) {
            service = AshuAccessibility.connected
            delay(1500L)
        }
    }
    // Size, place and scale change the window itself, so the window is made again. Everything else is read live.
    val changed: (String) -> Unit = { key ->
        val svc = AshuAccessibility.instance
        if (key == "isl_x" || key == "isl_scale" || key == "isl_top") svc?.islandChanged() else svc?.islandRefresh()
        CallNames.set(ctx, CallNames.wanted(ctx))
    }
    Page("Island", "Calls, music and more at the top", GViolet, Icons.Outlined.BlurOn) {
        item { IslandHero(on, edge, edgePower) }
        if (!service) item {
            Glass {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Outlined.Warning, null, tint = Danger, modifier = Modifier.size(20.dp))
                    Spacer(Modifier.width(10.dp))
                    Text("Accessibility is off, so the island cannot be drawn.", color = TextHi, fontSize = 13.sp, modifier = Modifier.weight(1f))
                }
                Spacer(Modifier.height(10.dp))
                GlassButton(Icons.Outlined.AutoAwesome, "Turn it on with root") {
                    scope.launch {
                        val ok = withContext(Dispatchers.IO) { Root.setA11y(ctx, true) }
                        toast(ctx, if (ok) "Accessibility is on" else "Is root granted?")
                    }
                }
            }
        }
        item {
            SwitchRow(
                Icons.Outlined.PowerSettingsNew, GViolet, "Dynamic Island",
                if (on) "The island is on" else "Switch on to show the island", on
            ) { v ->
                on = v
                IslandCfg.putBool(ctx, "isl_on", v)
                changed("isl_on")
            }
        }
        item {
            Glass {
                SectionTitle("Edge", "A light colour on the rim")
                Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf("Off", "Accent", "Aurora", "Rainbow", "Flow").forEachIndexed { i, n ->
                        Chip(n, edge == i, GViolet) {
                            edge = i
                            IslandCfg.putInt(ctx, "isl_edge", i)
                        }
                    }
                }
                Text("Strength", color = TextLo, fontSize = 11.sp, modifier = Modifier.padding(top = 10.dp))
                Slider(
                    value = edgePower, onValueChange = { edgePower = it }, valueRange = 0f..100f,
                    onValueChangeFinished = { IslandCfg.putInt(ctx, "isl_edge_pwr", edgePower.toInt()) },
                    colors = SliderDefaults.colors(thumbColor = Accent, activeTrackColor = Accent, inactiveTrackColor = Tint.copy(alpha = 0.15f))
                )
            }
        }
        item {
            Glass {
                SectionTitle("Motion", "How the island moves")
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf("Bouncy", "Smooth", "Snappy").forEachIndexed { i, n ->
                        Chip(n, feel == i, GTeal) {
                            feel = i
                            IslandCfg.putInt(ctx, "isl_feel", i)
                        }
                    }
                }
            }
        }
        item { SectionTitle("Size and place") }
        item { ISlide("Width", "isl_w", 120, 80, 220, " dp", changed) }
        item { ISlide("Height", "isl_h", 34, 24, 52, " dp", changed) }
        item { ISlide("Down from the top", "isl_top", 10, 0, 120, " dp", changed) }
        item { ISlide("Left and right", "isl_x", 0, -160, 160, " dp", changed) }
        item { ISlide("Overall size", "isl_scale", 100, 70, 140, "%", changed) }
        item { SectionTitle("What it shows") }
        item { ISwitch("isl_call", true, Icons.Outlined.Call, GGreen, "Calls", "Caller name, answer, decline, end", changed) }
        item { ISwitch("isl_media", true, Icons.Outlined.MusicNote, GPink, "Music and video", "Cover, wave, seek bar and controls", changed) }
        item { ISwitch("isl_charge", true, Icons.Outlined.BatteryChargingFull, GGreen, "Charging and battery", "Plugged in, fully charged, low battery", changed) }
        item { ISwitch("isl_rec", true, Icons.Outlined.Videocam, GRed, "Recording", "A running screen recording, with Stop", changed) }
        item { ISwitch("isl_ringer", true, Icons.Outlined.Vibration, GFire, "Sound mode", "Ring, vibrate or silent", changed) }
        item { ISwitch("isl_notif", true, Icons.Outlined.Notifications, GBlue, "Messages", "A short card for each new notification", changed) }
        item { ISwitch("isl_notif_text", true, Icons.Outlined.Visibility, GViolet, "Show the message text", "Off shows only the name of the app", changed) }
        item { SectionTitle("Behaviour") }
        item { ISlide("Closes after", "isl_hold", 3, 0, 30, " s", changed) }
        item { ISwitch("isl_bubble", true, Icons.Outlined.BlurOn, GViolet, "Two things at once", "A second thing, like music during a call, shows as a small bubble. Tap it to bring it to the front.", changed) }
        item { ISwitch("isl_glass", false, Icons.Outlined.Visibility, GCyan, "Glass look", "A see-through dark glass with a light top, instead of plain black", changed) }
        item { ISwitch("pay_shield", true, Icons.Outlined.Warning, GRed, "Pause for payment apps", "Google Pay, PhonePe and banking apps refuse to work while an accessibility service is on. Ashutool turns its own off while one of them is in front and on again after.", changed) }
        item { ISwitch("isl_idle2", true, Icons.Outlined.PhoneAndroid, GTeal, "Always show the black shape", "Off shows the island only when something happens", changed) }
        item { ISwitch("isl_hapt", true, Icons.Outlined.TouchApp, GBlue, "Tick on touch", "A small vibration when you tap it", changed) }
        item { ISwitch("isl_games", false, Icons.Outlined.SportsEsports, GFire, "Show in games", "Off hides it while a game runs", changed) }
        item { ISwitch("isl_land", false, Icons.Outlined.PhoneAndroid, GTeal, "Show in landscape", "Off hides it when the phone is turned", changed) }
        item {
            Glass {
                SectionTitle("Try it")
                Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    GlassButton(Icons.Outlined.Notifications, "Message") { demo(ctx, 0) }
                    GlassButton(Icons.Outlined.BatteryChargingFull, "Charging") { demo(ctx, 1) }
                    GlassButton(Icons.Outlined.Science, "5G") { demo(ctx, 2) }
                    GlassButton(Icons.Outlined.BlurOn, "Two at once") { demo(ctx, 3) }
                }
                Hint("Tap the island to open it. Swipe up to close it. Swipe left or right to change the song or flip between two things. Press and hold to open the app.")
            }
        }
        item { CreditFooter() }
    }
}
