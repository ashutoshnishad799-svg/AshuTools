/*
 * Ashutool - root toolkit for custom ROMs
 * Copyright (C) 2026 Ashutosh Nishad
 * Telegram: @ashutosh_07x (https://t.me/ashutosh_07x)
 *
 * Free software under the GNU GPL v3.0 or later, with the attribution terms in NOTICE.
 * Keep this header and the author credit in every copy and modified version.
 */


package com.ashu.ashutool

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
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
import androidx.compose.foundation.rememberScrollState
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.AdminPanelSettings
import androidx.compose.material.icons.outlined.Apps
import androidx.compose.material.icons.outlined.AutoAwesome
import androidx.compose.material.icons.outlined.Block
import androidx.compose.material.icons.outlined.BlurOn
import androidx.compose.material.icons.outlined.Bolt
import androidx.compose.material.icons.outlined.Call
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.ColorLens
import androidx.compose.material.icons.outlined.Notifications
import androidx.compose.material.icons.outlined.PhoneAndroid
import androidx.compose.material.icons.outlined.PowerSettingsNew
import androidx.compose.material.icons.outlined.Schedule
import androidx.compose.material.icons.outlined.Science
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material.icons.outlined.SettingsBackupRestore
import androidx.compose.material.icons.outlined.SportsEsports
import androidx.compose.material.icons.outlined.Timer
import androidx.compose.material.icons.outlined.TouchApp
import androidx.compose.material.icons.outlined.Tune
import androidx.compose.material.icons.outlined.Widgets
import androidx.compose.material.icons.outlined.Visibility
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
import com.agupta07505.smartisland.AshutoolBridge
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.sin

// The Island tab. The island is Smart Island (module :smartisland) with all of its own screens, which wear the look of
// Ashutool, see IslandSkin. This page is the door to them: a live preview, the status, quick doors to the Smart Island
// pages, and the things Ashutool adds.

/** The island switch on the Home page. */
@Composable
fun IslandHomeCard() {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    SwitchRow(
        Icons.Outlined.BlurOn, GViolet, "Dynamic Island",
        "Calls, music, timers, notifications and much more at the top of the screen. Everything is in the Island tab.",
        IslandControl.isOn(ctx)
    ) { want -> scope.launch { withContext(Dispatchers.IO) { IslandControl.setEnabled(ctx, want) } } }
}

private val HeroNames = listOf("Idle", "Music", "On call", "Charging")

/** Five bars that rise and fall like a sound wave. */
@Composable
private fun HeroBars(color: Color, height: Dp) {
    val ph: State<Float> = rememberInfiniteTransition(label = "hero-wave").animateFloat(
        0f, 6.2831855f, infiniteRepeatable(tween(1300, easing = LinearEasing)), label = "hero-wp"
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
            0 -> {
                Icon(Icons.Outlined.Schedule, null, tint = pal.accent, modifier = Modifier.size(14.dp))
                Spacer(Modifier.width(6.dp))
                Text(SimpleDateFormat("h:mm", Locale.getDefault()).format(Date()), color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
            }
            1 -> {
                Box(Modifier.size(22.dp).clip(CircleShape).background(pal.primary.d()))
                Spacer(Modifier.weight(1f))
                HeroBars(pal.accent, 16.dp)
            }
            2 -> {
                Box(Modifier.size(22.dp).clip(CircleShape).background(Color(0xFF34D399)), contentAlignment = Alignment.Center) {
                    Icon(Icons.Outlined.Call, null, tint = Ink, modifier = Modifier.size(13.dp))
                }
                Spacer(Modifier.weight(1f))
                Text("00:12", color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
            }
            else -> {
                Box(Modifier.size(22.dp).clip(CircleShape).background(Color(0xFF34D399).copy(alpha = 0.25f)), contentAlignment = Alignment.Center) {
                    Icon(Icons.Outlined.Bolt, null, tint = Color(0xFF34D399), modifier = Modifier.size(14.dp))
                }
                Spacer(Modifier.weight(1f))
                Text("87%", color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
            }
        }
    }
}

/** The colours of the light edge, the same families the island draws. */
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

/** A small live picture of the island at the top of a phone, in the colours of the current Ashutool theme. */
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
    val target = when (phase) { 0 -> 120.dp; 1 -> 178.dp; 2 -> 170.dp; else -> 152.dp }
    val w by animateDpAsState(if (on) target else 96.dp, spring(dampingRatio = 0.58f, stiffness = 380f), label = "hero-w")
    val shape = RoundedCornerShape(28.dp)
    Box(
        Modifier.fillMaxWidth().height(150.dp).clip(shape)
            .background(Brush.verticalGradient(listOf(pal.bg1, pal.bg2, pal.bg3)))
            .border(1.dp, Line, shape),
        contentAlignment = Alignment.TopCenter
    ) {
        Canvas(Modifier.fillMaxSize()) {
            fun orb(c: Color, cx: Float, cy: Float, r: Float) {
                drawCircle(
                    Brush.radialGradient(listOf(c.copy(alpha = pal.orbA), Color.Transparent), center = Offset(cx, cy), radius = r),
                    radius = r, center = Offset(cx, cy)
                )
            }
            orb(pal.orb1, size.width * 0.15f, 0f, size.width * 0.6f)
            orb(pal.orb2, size.width * 0.90f, size.height * 0.5f, size.width * 0.5f)
            orb(pal.orb3, size.width * 0.30f, size.height, size.width * 0.5f)
        }
        Box(
            Modifier.padding(top = 18.dp).width(w).height(34.dp).clip(CircleShape).background(Color.Black)
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
                    label = "hero-c"
                ) { ph -> HeroPill(ph) }
            } else {
                Text("Off", color = Color.White.copy(alpha = 0.5f), fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
            }
        }
        Column(Modifier.align(Alignment.BottomCenter).padding(bottom = 14.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Text(if (on) HeroNames[phase] else "The island is off", color = TextHi, fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
            Text(if (on) "Light edge, your theme" else "Switch it on below", color = TextLo, fontSize = 12.sp)
        }
    }
}

@Composable
private fun StatusChip(ok: Boolean, label: String, modifier: Modifier, onClick: () -> Unit) {
    val shape = RoundedCornerShape(18.dp)
    Row(
        modifier.bounceClick(onClick = onClick).clip(shape).background(Surf2).border(1.dp, Line, shape).padding(horizontal = 8.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.Center
    ) {
        Icon(
            if (ok) Icons.Outlined.CheckCircle else Icons.Outlined.Block, null,
            tint = if (ok) Color(0xFF34D399) else Danger, modifier = Modifier.size(16.dp)
        )
        Spacer(Modifier.width(5.dp))
        Text(label, color = TextHi, fontSize = 12.sp, fontWeight = FontWeight.Medium, maxLines = 1)
    }
}

@Composable
private fun IslandTile(icon: ImageVector, grad: Grad, title: String, sub: String, modifier: Modifier, onClick: () -> Unit) {
    Glass(modifier, radius = 24.dp, pad = 14.dp, onClick = onClick) {
        GradIcon(icon, grad, 38.dp)
        Spacer(Modifier.height(10.dp))
        Text(title, color = TextHi, fontSize = 14.sp, fontWeight = FontWeight.SemiBold, maxLines = 1)
        Text(sub, color = TextLo, fontSize = 11.sp, maxLines = 2)
    }
}

private class Door(val icon: ImageVector, val grad: Grad, val title: String, val sub: String, val section: String)

@Composable
fun IslandPage() {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    var overlay by remember { mutableStateOf(AshutoolBridge.isOverlayEnabled(ctx)) }
    var listener by remember { mutableStateOf(AshutoolBridge.isListenerEnabled(ctx)) }
    var perm by remember { mutableStateOf(AshutoolBridge.hasOverlayPermission(ctx)) }
    var busy by remember { mutableStateOf(false) }
    var gameHide by remember { mutableStateOf(LockStore.bool(ctx, "isl_game_hide", true)) }
    var ashuLook by remember { mutableStateOf(LockStore.bool(ctx, "isl_host_look", true)) }
    var edge by remember { mutableIntStateOf(LockStore.int(ctx, "isl_edge", 2).coerceIn(0, 4)) }
    var edgePower by remember { mutableFloatStateOf(LockStore.int(ctx, "isl_edge_pwr", 55).toFloat()) }
    val on = AshutoolBridge.islandEnabled && overlay
    // These change while the page is open (the root setup, the Smart Island screens), so they are read again now and then
    LaunchedEffect(Unit) {
        while (true) {
            overlay = AshutoolBridge.isOverlayEnabled(ctx)
            listener = AshutoolBridge.isListenerEnabled(ctx)
            perm = AshutoolBridge.hasOverlayPermission(ctx)
            delay(1500L)
        }
    }
    fun runSetup() {
        if (busy) return
        busy = true
        scope.launch {
            val ok = withContext(Dispatchers.IO) { IslandControl.setup(ctx) }
            busy = false
            toast(ctx, if (ok) "Island set up" else "Some steps failed, is root granted?")
        }
    }
    val doors = listOf(
        Door(Icons.Outlined.Tune, GViolet, "Studio", "Look, size and what it shows", "Studio"),
        Door(Icons.Outlined.PhoneAndroid, GTeal, "Position", "Where the island sits", "Position"),
        Door(Icons.Outlined.Science, GFire, "Demo", "Try every event", "Demo"),
        Door(Icons.Outlined.ColorLens, GPink, "Colours", "Colours of the island", "ColorStudio"),
        Door(Icons.Outlined.Widgets, GBlue, "Idle widgets", "Clock, weather, speed", "IdleWidgets"),
        Door(Icons.Outlined.TouchApp, GGreen, "Gestures", "Swipes and taps", "GesturesGuide"),
        Door(Icons.Outlined.Notifications, GBlue, "Notifications", "Rules for each app", "NotificationRules"),
        Door(Icons.Outlined.AdminPanelSettings, GRed, "Permissions", "Everything it needs", "PermissionsCenter")
    )
    Page("Island", "Calls, music, timers and more at the top", GViolet, Icons.Outlined.BlurOn) {
        item { IslandHero(on, edge, edgePower) }
        item {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                StatusChip(overlay, "Service", Modifier.weight(1f)) { if (!overlay) runSetup() else toast(ctx, "The island service is on") }
                StatusChip(listener, "Notifications", Modifier.weight(1f)) { if (!listener) runSetup() else toast(ctx, "Notification access is on") }
                StatusChip(perm, "Overlay", Modifier.weight(1f)) { if (!perm) runSetup() else toast(ctx, "Overlay permission is on") }
            }
        }
        item {
            SwitchRow(
                Icons.Outlined.PowerSettingsNew, GViolet, "Island power",
                if (on) "The island is showing" else "Switch on to show the island", on
            ) { want -> scope.launch { withContext(Dispatchers.IO) { IslandControl.setEnabled(ctx, want) } } }
        }
        item {
            Glass {
                SectionTitle("Edge", "A light colour on the rim")
                Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf("Off", "Accent", "Aurora", "Rainbow", "Flow").forEachIndexed { i, n ->
                        Chip(n, edge == i, GViolet) {
                            edge = i
                            LockStore.setInt(ctx, "isl_edge", i)
                            IslandSkin.refresh(ctx)
                        }
                    }
                }
                Text("Strength", color = TextLo, fontSize = 11.sp, modifier = Modifier.padding(top = 10.dp))
                Slider(
                    value = edgePower, onValueChange = { edgePower = it }, valueRange = 0f..100f,
                    onValueChangeFinished = { LockStore.setInt(ctx, "isl_edge_pwr", edgePower.toInt()); IslandSkin.refresh(ctx) },
                    colors = SliderDefaults.colors(thumbColor = Accent, activeTrackColor = Accent, inactiveTrackColor = Tint.copy(alpha = 0.15f))
                )
            }
        }
        item { SectionTitle("Customise", "Opens that page") }
        doors.chunked(2).forEach { pair ->
            item {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    pair.forEach { d ->
                        IslandTile(d.icon, d.grad, d.title, d.sub, Modifier.weight(1f)) { AshutoolBridge.openSettings(ctx, "ashu:" + d.section) }
                    }
                    if (pair.size == 1) Spacer(Modifier.weight(1f))
                }
            }
        }
        item { GlassButton(Icons.Outlined.Settings, "All island settings") { AshutoolBridge.openSettings(ctx, "ashu:Settings") } }
        item { SectionTitle("Extras") }
        item {
            SwitchRow(
                Icons.Outlined.SportsEsports, GFire, "Hide the island in games",
                "Switched off while a game runs, back on when you leave it. An island you turned off yourself stays off.", gameHide
            ) { gameHide = it; LockStore.setBool(ctx, "isl_game_hide", it) }
        }
        item {
            SwitchRow(
                Icons.Outlined.Visibility, GTeal, "Ashutool look in the island screens",
                "The island screens use your Ashutool theme, glass and background. Off gives them their own look.", ashuLook
            ) { ashuLook = it; LockStore.setBool(ctx, "isl_host_look", it); IslandSkin.refresh(ctx) }
        }
        item {
            Glass {
                Text("Match the island to Ashutool", color = TextHi, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
                Text(
                    "Gives the island's music wave, notification dot and the tint of its open cards the accent colour of your Ashutool theme. You can change them later in Colours.",
                    color = TextLo, fontSize = 12.sp, modifier = Modifier.padding(top = 4.dp, bottom = 10.dp)
                )
                GlassButton(Icons.Outlined.AutoAwesome, "Match island colours") {
                    scope.launch {
                        withContext(Dispatchers.IO) { IslandTwists.matchAccent(ctx) }
                        toast(ctx, "Island colours matched")
                    }
                }
            }
        }
        item {
            Glass {
                Hint("Ashutool sets the island up once by itself with root. If a permission was taken away, or something does not show, run it again.")
                Spacer(Modifier.height(10.dp))
                GlassButton(Icons.Outlined.AutoAwesome, if (busy) "Working..." else "Set up everything with root") { runSetup() }
            }
        }
        item { CreditFooter() }
    }
}
