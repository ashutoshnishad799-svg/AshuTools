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
import androidx.compose.foundation.clickable
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.layout.width
import androidx.compose.ui.text.style.TextOverflow
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
        withContext(Dispatchers.IO) { for (t in Toggles.all) state[t.id] = try { t.read(ctx) } catch (e: Exception) { null } }
    }
    Glass {
        Text("Quick toggles", color = TextHi, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
        Text("Every switch here is also a tile for the quick settings panel. Edit the panel and add the ones you want.", color = TextLo, fontSize = 11.sp, modifier = Modifier.padding(top = 2.dp))
        Toggles.all.chunked(3).forEach { row ->
            Row(Modifier.fillMaxWidth().padding(top = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                row.forEach { t ->
                    QTile(t.label, t.icon, state[t.id], Modifier.weight(1f)) {
                        scope.launch {
                            val want = state[t.id] != true
                            state[t.id] = want
                            val ok = withContext(Dispatchers.IO) { try { t.write(ctx, want) } catch (e: Exception) { false } }
                            val real = withContext(Dispatchers.IO) { try { t.read(ctx) } catch (e: Exception) { null } }
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

/** Your floating apps on the Home page. Tap one to open it in a window, at any time. */
@Composable
fun FloatAppsCard() {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    val apps = remember { FloatWindows.apps(ctx) }
    fun label(p: String): String =
        try { ctx.packageManager.getApplicationLabel(ctx.packageManager.getApplicationInfo(p, 0)).toString() } catch (e: Exception) { p }
    Glass {
        Text("Floating apps", color = TextHi, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
        Text(
            "Open an app in a small window any time. Move it, resize it, shrink it to a bubble or close it. Also in the quick settings panel as Floating apps.",
            color = TextLo, fontSize = 11.sp, modifier = Modifier.padding(top = 2.dp, bottom = 8.dp)
        )
        if (apps.isEmpty()) {
            Text("No apps yet. Choose them in Games, Windows and apps.", color = TextLo, fontSize = 12.sp)
        } else {
            LazyRow(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                items(apps, key = { it }) { p ->
                    Column(
                        Modifier.clickable {
                            scope.launch {
                                val ok = FloatWindows.open(ctx, p)
                                toast(ctx, if (!ok) "Could not open" else FloatWindows.note.ifEmpty { "Opening" })
                            }
                        },
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        AppIcon(p, 44.dp)
                        Text(label(p), color = TextLo, fontSize = 10.sp, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.width(54.dp))
                    }
                }
            }
        }
    }
}
