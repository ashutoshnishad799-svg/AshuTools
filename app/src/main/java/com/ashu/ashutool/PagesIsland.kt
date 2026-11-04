/*
 * Ashutool - root toolkit for custom ROMs
 * Copyright (C) 2026 Ashutosh Nishad
 * Telegram: @ashutosh_07x (https://t.me/ashutosh_07x)
 *
 * Free software under the GNU GPL v3.0 or later, with the attribution terms in NOTICE.
 * Keep this header and the author credit in every copy and modified version.
 */

@file:OptIn(ExperimentalLayoutApi::class)

package com.ashu.ashutool

import android.os.SystemClock
import android.provider.Settings
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.Column
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

// =====================================================================================================================
// Island tab: the Dynamic Island and the sound controls. Every change is applied the moment you make it, because the
// island and the settings live in the same process. The island on screen is the live preview.
// =====================================================================================================================

@Composable
fun IslandPage() = SubHost(
    root = { open -> IslandMain(open) },
    sub = { k, back ->
        when (k) {
            "look" -> IslandLookSub(back)
            "size" -> IslandSizeSub(back)
            else -> IslandSourcesSub(back)
        }
    }
)

@Composable
fun SoundPage() { SoundMain() }

private fun applied(ctx: android.content.Context) { MonitorService.instance?.islandChanged() }

@Composable
private fun ISlide(label: String, key: String, def: Int, lo: Int, hi: Int, unit: String) {
    val ctx = LocalContext.current
    var v by remember { mutableFloatStateOf(LockStore.int(ctx, key, def).toFloat()) }
    Glass {
        SectionTitle(label, "${v.toInt()}$unit")
        Slider(
            value = v,
            onValueChange = { v = it; IslandCfg.putInt(ctx, key, it.toInt()) },
            valueRange = lo.toFloat()..hi.toFloat(),
            onValueChangeFinished = { applied(ctx) },
            colors = SliderDefaults.colors(thumbColor = Accent, activeTrackColor = Accent, inactiveTrackColor = Tint.copy(alpha = 0.15f))
        )
    }
}

@Composable
private fun IChoice(title: String, names: List<String>, key: String, def: Int, grad: Grad = GTeal) {
    val ctx = LocalContext.current
    var sel by remember { mutableIntStateOf(LockStore.int(ctx, key, def)) }
    Glass {
        SectionTitle(title)
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            names.forEachIndexed { i, n -> Chip(n, sel == i, grad) { sel = i; IslandCfg.putInt(ctx, key, i); applied(ctx) } }
        }
    }
}

@Composable
private fun ISwitch(key: String, def: Boolean, icon: androidx.compose.ui.graphics.vector.ImageVector, grad: Grad, title: String, sub: String) {
    val ctx = LocalContext.current
    var on by remember { mutableStateOf(LockStore.bool(ctx, key, def)) }
    SwitchRow(icon, grad, title, sub, on) { on = it; IslandCfg.putBool(ctx, key, it); applied(ctx) }
}

/** Shows demo things on the island, so you can see a change without waiting for a call. */
private fun demo(ctx: android.content.Context, which: Int) {
    val now = SystemClock.elapsedRealtime()
    when (which) {
        0 -> IslandHub.upsert(IAct("demo_note", IKind.NOTE, 55, "Ashutool", "This is how a notification looks on the island", null, "", ctx.packageName, Color(0xFF60A5FA), now + 7000L))
        1 -> IslandHub.upsert(IAct("demo_charge", IKind.CHARGE, 30, "Charging", "", Icons.Outlined.BatteryChargingFull, "", "", Color(0xFF34D399), now + 7000L))
        2 -> IslandHub.flashNet(ctx, true)
        else -> {
            // Two at once: tap the small bubble and the two change places
            IslandHub.upsert(IAct("demo_charge", IKind.CHARGE, 30, "Charging", "", Icons.Outlined.BatteryChargingFull, "", "", Color(0xFF34D399), now + 12000L))
            IslandHub.upsert(IAct("demo_note", IKind.NOTE, 55, "Ashutool", "Tap the small bubble to open it", null, "", ctx.packageName, Color(0xFF60A5FA), now + 12000L))
        }
    }
}

@Composable
private fun IslandStatusCard() {
    val ctx = LocalContext.current
    val running = MonitorService.instance != null
    val overlayOk = Settings.canDrawOverlays(ctx)
    Glass {
        Row(verticalAlignment = Alignment.CenterVertically) {
            GradIcon(Icons.Outlined.BlurOn, GViolet, 44.dp)
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text("Dynamic Island", color = TextHi, fontSize = 16.sp, fontWeight = FontWeight.Bold)
                Text(
                    when {
                        !running -> "Needs the Ashutool service. Open Home once and allow root."
                        !overlayOk -> "Needs permission to draw over other apps."
                        else -> "Shows calls, music, charging, recording and notifications over every app."
                    },
                    color = TextLo, fontSize = 12.sp
                )
            }
        }
        Spacer(Modifier.height(12.dp))
        Text("Try it", color = TextLo, fontSize = 12.sp, fontWeight = FontWeight.Medium)
        Spacer(Modifier.height(8.dp))
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            GlassButton(Icons.Outlined.Notifications, "Message") { demo(ctx, 0) }
            GlassButton(Icons.Outlined.BatteryChargingFull, "Charging") { demo(ctx, 1) }
            GlassButton(Icons.Outlined.SignalCellularAlt, "5G") { demo(ctx, 2) }
            GlassButton(Icons.Outlined.BlurOn, "Two at once") { demo(ctx, 3) }
        }
    }
}

/** The one-line switch for the island on the Home page. */
@Composable
fun IslandHomeCard() {
    val ctx = LocalContext.current
    var on by remember { mutableStateOf(LockStore.bool(ctx, "isl_on", true)) }
    SwitchRow(
        Icons.Outlined.BlurOn, GViolet, "Dynamic Island",
        "Calls, music, charging and notifications at the top of the screen. Style it in the Island tab.", on
    ) {
        on = it
        IslandCfg.putBool(ctx, "isl_on", it)
        MonitorService.instance?.islandChanged()
    }
}

@Composable
private fun IslandMain(open: (String) -> Unit) {
    val ctx = LocalContext.current
    var on by remember { mutableStateOf(LockStore.bool(ctx, "isl_on", true)) }
    Page("Island", "Dynamic Island and live activities", GViolet, Icons.Outlined.BlurOn) {
        item { IslandStatusCard() }
        item {
            SwitchRow(Icons.Outlined.BlurOn, GViolet, "Dynamic Island", "Turn the island on or off", on) {
                on = it
                IslandCfg.putBool(ctx, "isl_on", it)
                MonitorService.instance?.islandChanged()
            }
        }
        item { NavRow(Icons.Outlined.ColorLens, GPink, "Look", "Theme, shape, edge effect and animation") { open("look") } }
        item { NavRow(Icons.Outlined.Tune, GBlue, "Size and place", "Width, height, position, opacity and time on screen") { open("size") } }
        item { NavRow(Icons.Outlined.Widgets, GTeal, "What it shows", "Calls, music, charging, recording, notifications") { open("sources") } }
        item {
            Glass {
                Hint("With two things at once, for example a call and music, the more important one is the big shape and the other is a small bubble beside it. Tap the bubble and it opens while the other becomes the bubble. Tap the big shape to open it, swipe it up to close. Press and hold to open the app.")
            }
        }
        item { CreditFooter() }
    }
}

@Composable
private fun IslandLookSub(back: () -> Unit) {
    Page("Look", "Theme, shape and effects", GPink, Icons.Outlined.ColorLens, onBack = back) {
        item { IChoice("Theme", ISkinNames, "isl_skin", 0, GPink) }
        item { IChoice("Edge effect", IFxNames, "isl_fx", 1, GViolet) }
        item { IChoice("Shape", IShapeNames, "isl_shape", 0, GBlue) }
        item { ISlide("Roundness", "isl_round", 100, 0, 100, "%") }
        item { IChoice("Animation", IAnimNames, "isl_anim", 0, GTeal) }
        item { ISwitch("isl_tint", true, Icons.Outlined.AutoAwesome, GViolet, "Colour from what is playing", "The glow takes the colour of the cover or the app") }
        item { ISwitch("isl_hapt", true, Icons.Outlined.TouchApp, GTeal, "Tick on touch", "A small vibration when you tap the island") }
        item { ISwitch("isl_idle", false, Icons.Outlined.PhoneAndroid, GBlue, "Always show the shape", "Keep a small empty island on screen when nothing is going on") }
    }
}

@Composable
private fun IslandSizeSub(back: () -> Unit) {
    Page("Size and place", "Make it fit your screen", GBlue, Icons.Outlined.Tune, onBack = back) {
        item { ISlide("Overall size", "isl_scale", 100, 70, 150, "%") }
        item { ISlide("Width", "isl_w", 118, 70, 220, " dp") }
        item { ISlide("Height", "isl_h", 34, 22, 52, " dp") }
        item { ISlide("Distance from the top", "isl_top", 8, 0, 120, " dp") }
        item { ISlide("Move left or right", "isl_x", 0, -160, 160, " dp") }
        item { ISlide("Opacity", "isl_alpha", 100, 40, 100, "%") }
        item { ISlide("Stays open for", "isl_hold", 7, 0, 30, " s") }
        item { Glass { Hint("Stays open for 0 means it only closes when you swipe it up or tap it.") } }
        item { ISlide("Short messages stay for", "isl_flash", 3, 1, 10, " s") }
        item { ISwitch("isl_games", true, Icons.Outlined.SportsEsports, GTeal, "Show in games", "Keep the island on screen while you play") }
        item { ISwitch("isl_land", true, Icons.Outlined.ScreenLockPortrait, GBlue, "Show when the screen is sideways", "Turn off to hide it in landscape") }
    }
}

@Composable
private fun IslandSourcesSub(back: () -> Unit) {
    Page("What it shows", "Choose what appears", GTeal, Icons.Outlined.Widgets, onBack = back) {
        item { ISwitch("isl_call", true, Icons.Outlined.Call, GGreen, "Calls", "Caller name, timer and call buttons") }
        item { ISwitch("isl_call_open", true, Icons.Outlined.Call, GGreen, "Open for an incoming call", "Shows Answer and Decline at once") }
        item { ISwitch("isl_media", true, Icons.Outlined.MusicNote, GPink, "Music and video", "YouTube, Spotify, browsers and any player, with controls") }
        item { ISlide("Keep a paused player for", "isl_media_hold", 20, 0, 120, " s") }
        item { ISwitch("isl_charge", true, Icons.Outlined.BatteryChargingFull, GGreen, "Charging and battery", "Plug in, fully charged and low battery") }
        item { ISlide("Charging stays for", "isl_charge_sec", 5, 2, 20, " s") }
        item { ISwitch("isl_rec", true, Icons.Outlined.Videocam, GRed, "Screen recording", "Timer and a stop button") }
        item { ISwitch("isl_ringer", true, Icons.Outlined.NotificationsOff, GFire, "Sound mode", "Ring, vibrate and silent when you change it") }
        item { ISwitch("isl_notif", true, Icons.Outlined.Notifications, GBlue, "Notifications", "A short banner for new messages. Needs notification access, which root gives.") }
        item { ISwitch("isl_notif_text", true, Icons.Outlined.Visibility, GViolet, "Show the message text", "Turn off to show only the app name") }
    }
}

// ----------------------------------------------------------------------------------------------------- sound

@Composable
private fun SoundMain(back: (() -> Unit)? = null) {
    val ctx = LocalContext.current
    var size by remember { mutableIntStateOf(LockStore.int(ctx, "vp_size", 1).coerceIn(0, 2)) }
    var side by remember { mutableIntStateOf(LockStore.int(ctx, "vp_side", 1).coerceIn(0, 1)) }
    var panel by remember { mutableIntStateOf(LockStore.int(ctx, "g_panel", 1).coerceIn(0, 2)) }
    var secs by remember { mutableFloatStateOf(LockStore.int(ctx, "vp_secs", 4).toFloat()) }
    Page("Sound and glass", "Volume panel, tiles and blur", GCyan, Icons.Outlined.MusicNote, onBack = back) {
        item {
            Glass {
                Text("Quick settings tiles", color = TextHi, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
                Text(
                    "Pull down the quick settings, tap the pencil and add 5G / 4G, Sound and Volume. " +
                        "5G / 4G shows the network on the tile and switches at once. " +
                        "Sound goes ring, then vibrate, then silent, then ring again, one tap each.",
                    color = TextLo, fontSize = 12.sp, modifier = Modifier.padding(top = 4.dp)
                )
            }
        }
        item { QuickTogglesCard() }
        item {
            Glass {
                SectionTitle("Volume panel size")
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf("Small", "Medium", "Big").forEachIndexed { i, n -> Chip(n, size == i, GCyan) { size = i; LockStore.setInt(ctx, "vp_size", i) } }
                }
                Text("Small is one slider. Medium has media, ring and alarm. Big is larger and has more.", color = TextLo, fontSize = 11.sp, modifier = Modifier.padding(top = 8.dp))
            }
        }
        item {
            Glass {
                SectionTitle("Volume panel side")
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf("Left", "Right").forEachIndexed { i, n -> Chip(n, side == i, GCyan) { side = i; LockStore.setInt(ctx, "vp_side", i) } }
                }
            }
        }
        item {
            Glass {
                SectionTitle("Closes after", "${secs.toInt()} s")
                Slider(
                    value = secs, onValueChange = { secs = it }, valueRange = 2f..12f,
                    onValueChangeFinished = { LockStore.setInt(ctx, "vp_secs", secs.toInt()) },
                    colors = SliderDefaults.colors(thumbColor = Accent, activeTrackColor = Accent, inactiveTrackColor = Tint.copy(alpha = 0.15f))
                )
                Spacer(Modifier.height(6.dp))
                GlassButton(Icons.Outlined.MusicNote, "Show the panel now") { VolumePanel.show(ctx) }
            }
        }
        item {
            Glass {
                SectionTitle("Game panel size")
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf("Small", "Normal", "Large").forEachIndexed { i, n -> Chip(n, panel == i, GTeal) { panel = i; LockStore.setInt(ctx, "g_panel", i) } }
                }
            }
        }
        item {
            var blur by remember { mutableStateOf(LockStore.bool(ctx, "glass_blur", false)) }
            SwitchRow(
                Icons.Outlined.BlurOn, GViolet, "Glass blur (Android 12 and newer)",
                "Blurs the real screen behind the game panel and the volume panel. Needs a ROM that allows window blur. Off by default.",
                blur
            ) { blur = it; LockStore.setBool(ctx, "glass_blur", it); Palette.load(ctx) }
        }
    }
}
