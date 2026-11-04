/*
 * Ashutool - root toolkit for custom ROMs
 * Copyright (C) 2026 Ashutosh Nishad
 * Telegram: @ashutosh_07x (https://t.me/ashutosh_07x)
 *
 * Free software under the GNU GPL v3.0 or later, with the attribution terms in NOTICE.
 * Keep this header and the author credit in every copy and modified version.
 */


package com.ashu.ashutool

import android.os.Handler
import android.os.Looper
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay

@Composable
private fun QTile(
    label: String, sub: String, active: Boolean, modifier: Modifier,
    onClick: () -> Unit, icon: @Composable (androidx.compose.ui.graphics.Color) -> Unit
) {
    val shape = RoundedCornerShape(22.dp)
    val ink = if (active) Ink else TextHi
    Column(
        modifier.bounceClick(onClick = onClick).clip(shape)
            .then(
                if (active) Modifier.background(Brush.linearGradient(listOf(Accent, Accent.copy(alpha = 0.70f))))
                else Modifier.background(Surf2).border(1.dp, Line, shape)
            )
            .padding(vertical = 14.dp, horizontal = 6.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Box(Modifier.height(28.dp), contentAlignment = Alignment.Center) { icon(ink) }
        Spacer(Modifier.height(6.dp))
        Text(label, color = ink, fontSize = 12.sp, fontWeight = FontWeight.SemiBold, maxLines = 1)
        Text(sub, color = if (active) Ink.copy(alpha = 0.75f) else TextLo, fontSize = 10.sp, maxLines = 1, textAlign = TextAlign.Center)
    }
}

/**
 * The three controls that are also quick settings tiles: 5G / 4G, Sound (ring, vibrate, silent) and Volume.
 * They use the same code as the tiles, so they are just as fast.
 */
@Composable
fun QuickTogglesCard() {
    val ctx = LocalContext.current
    val main = remember { Handler(Looper.getMainLooper()) }
    var net by remember { mutableStateOf(NetMode.known(ctx)) }
    var ringer by remember { mutableIntStateOf(Ringer.mode(ctx)) }
    var size by remember { mutableIntStateOf(LockStore.int(ctx, "vp_size", 1).coerceIn(0, 2)) }

    LaunchedEffect(Unit) { NetMode.refresh(ctx) { v -> main.post { if (v != null) net = v } } }
    WhileVisible { while (true) { ringer = Ringer.mode(ctx); delay(600) } }

    Glass {
        Text("Quick controls", color = TextHi, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
        Text(
            "The same three are tiles for the quick settings panel. Edit the panel and add 5G / 4G, Sound and Volume.",
            color = TextLo, fontSize = 11.sp, modifier = Modifier.padding(top = 2.dp)
        )
        Row(Modifier.fillMaxWidth().padding(top = 10.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            QTile(
                "5G / 4G", when (net) { true -> "5G on"; false -> "4G only"; null -> "Tap to switch" }, net == true, Modifier.weight(1f),
                onClick = {
                    val want = net != true
                    net = want
                    IslandHub.flashNet(ctx, want)
                    NetMode.apply(ctx, want) { err ->
                        main.post {
                            if (err.isNotEmpty()) { net = !want; toast(ctx, "5G / 4G did not change: ${err.take(70)}") }
                        }
                    }
                }
            ) { c -> Text(if (net == false) "4G" else "5G", color = c, fontSize = 20.sp, fontWeight = FontWeight.ExtraBold) }
            QTile(
                "Sound", Ringer.label(ringer), ringer != Ringer.SILENT, Modifier.weight(1f),
                onClick = { ringer = Ringer.cycle(ctx) { ringer = it } }
            ) { c -> Icon(Ringer.icon(ringer), Ringer.label(ringer), tint = c, modifier = Modifier.size(26.dp)) }
            QTile(
                "Volume", "Open panel", false, Modifier.weight(1f),
                onClick = { VolumePanel.show(ctx) }
            ) { c -> Icon(Icons.Outlined.MusicNote, "Volume", tint = c, modifier = Modifier.size(26.dp)) }
        }
        Text("Volume panel size", color = TextLo, fontSize = 11.sp, modifier = Modifier.padding(top = 12.dp, bottom = 6.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            listOf("Small", "Medium", "Big").forEachIndexed { i, n ->
                Chip(n, size == i, GTeal) { size = i; LockStore.setInt(ctx, "vp_size", i) }
            }
        }
    }
}
