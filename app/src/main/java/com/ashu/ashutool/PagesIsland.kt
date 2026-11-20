/*
 * Ashutool - root toolkit for custom ROMs
 * Copyright (C) 2026 Ashutosh Nishad
 * Telegram: @ashutosh_07x (https://t.me/ashutosh_07x)
 *
 * Free software under the GNU GPL v3.0 or later, with the attribution terms in NOTICE.
 * Keep this header and the author credit in every copy and modified version.
 */


package com.ashu.ashutool

import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.AutoAwesome
import androidx.compose.material.icons.outlined.Block
import androidx.compose.material.icons.outlined.BlurOn
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.Tune
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.agupta07505.smartisland.AshutoolBridge
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

// The Island tab. The island itself is Smart Island, copied into the module :smartisland, with all of its own settings screen.
// This page switches it on and off, sets it up through root and opens that screen.

/** The island switch on the Home page. */
@Composable
fun IslandHomeCard() {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    var on by remember { mutableStateOf(IslandControl.isOn(ctx)) }
    SwitchRow(
        Icons.Outlined.BlurOn, GViolet, "Dynamic Island",
        "Smart Island: calls, music, timers, notifications and much more at the top of the screen. Its settings are in the Island tab.", on
    ) { want ->
        on = want
        scope.launch { withContext(Dispatchers.IO) { IslandControl.setEnabled(ctx, want) } }
    }
}

@Composable
private fun StatusLine(ok: Boolean, good: String, bad: String) {
    Row(Modifier.padding(vertical = 3.dp), verticalAlignment = Alignment.CenterVertically) {
        Icon(
            if (ok) Icons.Outlined.CheckCircle else Icons.Outlined.Block, null,
            tint = if (ok) Color(0xFF34D399) else Color(0xFFFF6B8B), modifier = Modifier.size(18.dp)
        )
        Spacer(Modifier.width(8.dp))
        Text(if (ok) good else bad, color = TextHi, fontSize = 13.sp)
    }
}

@Composable
fun IslandPage() {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    var overlay by remember { mutableStateOf(AshutoolBridge.isOverlayEnabled(ctx)) }
    var listener by remember { mutableStateOf(AshutoolBridge.isListenerEnabled(ctx)) }
    var perm by remember { mutableStateOf(AshutoolBridge.hasOverlayPermission(ctx)) }
    var busy by remember { mutableStateOf(false) }
    // These change while the page is open (the root setup, the Smart Island screen), so they are read again now and then
    LaunchedEffect(Unit) {
        while (true) {
            overlay = AshutoolBridge.isOverlayEnabled(ctx)
            listener = AshutoolBridge.isListenerEnabled(ctx)
            perm = AshutoolBridge.hasOverlayPermission(ctx)
            delay(1500L)
        }
    }
    Page("Island", "Smart Island, built into Ashutool", GViolet, Icons.Outlined.BlurOn) {
        item {
            Glass {
                SectionTitle("Status")
                StatusLine(overlay, "The island is on", "The island is off")
                StatusLine(listener, "Notification access is on", "Notification access is off")
                StatusLine(perm, "Overlay permission is on", "Overlay permission is off")
            }
        }
        item {
            SwitchRow(Icons.Outlined.BlurOn, GViolet, "Dynamic Island", "Show the island over every app", overlay) { want ->
                overlay = want
                scope.launch {
                    withContext(Dispatchers.IO) { IslandControl.setEnabled(ctx, want) }
                    overlay = AshutoolBridge.isOverlayEnabled(ctx)
                }
            }
        }
        item {
            Glass {
                Text(
                    "Everything about the island is set in the Smart Island screen: themes, shapes, sizes, what it shows (calls, music, timers, " +
                        "downloads, charging, Bluetooth, notifications, widgets and more), per-app rules and its utilities.",
                    color = TextLo, fontSize = 12.sp
                )
                Spacer(Modifier.height(10.dp))
                GlassButton(Icons.Outlined.Tune, "Open island settings") { AshutoolBridge.openSettings(ctx) }
            }
        }
        item {
            Glass {
                Hint("Ashutool sets the island up once by itself with root. If a permission was taken away, or something does not show, run it again.")
                Spacer(Modifier.height(10.dp))
                GlassButton(Icons.Outlined.AutoAwesome, if (busy) "Working..." else "Set up everything with root") {
                    if (!busy) {
                        busy = true
                        scope.launch {
                            val ok = withContext(Dispatchers.IO) { IslandControl.setup(ctx) }
                            busy = false
                            toast(ctx, if (ok) "Island set up" else "Some steps failed, is root granted?")
                        }
                    }
                }
            }
        }
        item { IslandCreditCard() }
        item { CreditFooter() }
    }
}
