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

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
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
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

// The Sound tab: volume panel, quick settings tiles and glass blur. It used to share a file with the old island page.

/** The Sound tab. */
@Composable
fun SoundPage() { SoundMain() }

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
