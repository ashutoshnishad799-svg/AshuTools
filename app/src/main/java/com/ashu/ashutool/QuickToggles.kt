/*
 * Ashutool - root toolkit for custom ROMs
 * Copyright (C) 2026 Ashutosh Nishad
 * Telegram: @ashutosh_07x (https://t.me/ashutosh_07x)
 *
 * Free software under the GNU GPL v3.0 or later, with the attribution terms in NOTICE.
 * Keep this header and the author credit in every copy and modified version.
 */

package com.ashu.ashutool

import android.content.Context
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** One switch: how to read its state and how to change it. Everything goes through root. */
private class Tg(
    val id: String,
    val label: String,
    val icon: ImageVector,
    val read: (Context) -> Boolean?,
    val write: (Context, Boolean) -> Boolean
)

private fun setting(ns: String, key: String): String? =
    Root.run("settings get $ns $key").firstOrNull()?.trim()?.takeIf { it.isNotEmpty() && it != "null" }

private fun flag(v: Boolean) = if (v) 1 else 0

private val TOGGLES = listOf(
    Tg("net5g", "5G", Icons.Outlined.SignalCellularAlt, { NetMode.query(it) }, { c, v -> NetMode.set(c, v).isEmpty() }),
    Tg("data", "Mobile data", Icons.Outlined.DataUsage, { setting("global", "mobile_data") == "1" },
        { _, v -> Root.ok("svc data ${if (v) "enable" else "disable"}") }),
    Tg("wifi", "Wi-Fi", Icons.Outlined.Wifi, { setting("global", "wifi_on")?.let { it != "0" } },
        { _, v -> Root.ok("svc wifi ${if (v) "enable" else "disable"}") }),
    Tg("air", "Airplane", Icons.Outlined.AirplanemodeActive, { setting("global", "airplane_mode_on") == "1" },
        { _, v ->
            Root.ok("cmd connectivity airplane-mode ${if (v) "enable" else "disable"}") ||
                Root.ok("settings put global airplane_mode_on ${flag(v)}", "am broadcast -a android.intent.action.AIRPLANE_MODE --ez state $v")
        }),
    Tg("touch", "Touches", Icons.Outlined.TouchApp, { setting("system", "show_touches") == "1" },
        { _, v -> Root.ok("settings put system show_touches ${flag(v)}") }),
    Tg("anim", "No animations", Icons.Outlined.Animation, { setting("global", "animator_duration_scale")?.toFloatOrNull()?.let { it == 0f } },
        { _, v ->
            val x = if (v) "0" else "1"
            Root.ok("settings put global window_animation_scale $x", "settings put global transition_animation_scale $x", "settings put global animator_duration_scale $x")
        }),
    Tg("awake", "Stay awake", Icons.Outlined.Visibility, { setting("global", "stay_on_while_plugged_in")?.let { it != "0" } },
        { _, v -> Root.ok("settings put global stay_on_while_plugged_in ${if (v) 3 else 0}") }),
    Tg("saver", "Battery saver", Icons.Outlined.BatterySaver, { setting("global", "low_power") == "1" },
        { _, v -> Root.ok("settings put global low_power ${flag(v)}") }),
    Tg("rotate", "Auto rotate", Icons.Outlined.ScreenRotation, { setting("system", "accelerometer_rotation") == "1" },
        { _, v -> Root.ok("settings put system accelerometer_rotation ${flag(v)}") })
)

@Composable
private fun QTile(label: String, icon: ImageVector, on: Boolean?, modifier: Modifier, onClick: () -> Unit) {
    val shape = RoundedCornerShape(16.dp)
    val active = on == true
    Column(
        modifier.bounceClick(onClick = onClick).clip(shape)
            .background(
                if (active) Brush.linearGradient(listOf(Accent, Accent.copy(alpha = 0.72f)))
                else Brush.linearGradient(listOf(TextHi.copy(alpha = 0.08f), TextHi.copy(alpha = 0.08f)))
            )
            .padding(vertical = 10.dp, horizontal = 2.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Icon(icon, label, tint = if (active) Ink else TextHi, modifier = Modifier.size(22.dp))
        Spacer(Modifier.height(4.dp))
        Text(label, color = if (active) Ink else TextLo, fontSize = 10.sp, fontWeight = FontWeight.Medium, maxLines = 1)
    }
}

/** Small switches for the things people turn on and off all day. Needs root. */
@Composable
fun QuickTogglesCard() {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    val state = remember { mutableStateMapOf<String, Boolean?>() }
    LaunchedEffect(Unit) {
        withContext(Dispatchers.IO) { for (t in TOGGLES) state[t.id] = t.read(ctx) }
    }
    Glass {
        Text("Quick toggles", color = TextHi, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
        Text("5G is also a tile for the quick settings panel. Edit the panel and add 5G / 4G.", color = TextLo, fontSize = 11.sp, modifier = Modifier.padding(top = 2.dp))
        TOGGLES.chunked(3).forEach { row ->
            Row(Modifier.fillMaxWidth().padding(top = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                row.forEach { t ->
                    QTile(t.label, t.icon, state[t.id], Modifier.weight(1f)) {
                        scope.launch {
                            val want = state[t.id] != true
                            state[t.id] = want
                            val ok = withContext(Dispatchers.IO) { t.write(ctx, want) }
                            val real = withContext(Dispatchers.IO) { t.read(ctx) }
                            state[t.id] = real ?: (if (ok) want else !want)
                            if (!ok) toast(ctx, "${t.label} did not change")
                        }
                    }
                }
                repeat(3 - row.size) { Spacer(Modifier.weight(1f)) }
            }
        }
    }
}
