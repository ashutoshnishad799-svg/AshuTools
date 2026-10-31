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

import androidx.compose.foundation.layout.*
import androidx.compose.material3.Switch
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.*

private class HeatRow(val key: String, val label: String, val pct: Float, val isApp: Boolean)

/**
 * Per-app heat.
 * "Heating now" shows which apps use the most CPU this second, since CPU use is what warms the phone.
 * "History" shows the temperature measured while each app was in the foreground.
 */
@Composable
fun AppHeatPage(back: () -> Unit) {
    val ctx = LocalContext.current
    val s by Monitor.snap.collectAsState()
    var tab by remember { mutableIntStateOf(0) }
    var now by remember { mutableStateOf<List<HeatRow>?>(null) }
    var hist by remember { mutableStateOf(AppThermal.top()) }
    var confirmReset by remember { mutableStateOf(false) }

    WhileVisible {
        var prev: Ticks? = null
        val pm = ctx.packageManager
        while (true) {
            val cur = withContext(Dispatchers.IO) { Sys.procTicks() }
            val p = prev
            if (p != null && cur.total > p.total) {
                now = withContext(Dispatchers.Default) {
                    val dt = (cur.total - p.total).toFloat()
                    val agg = HashMap<String, Float>()
                    for ((pid, t) in cur.ticks) {
                        val before = p.ticks[pid] ?: continue
                        val d = t - before
                        val name = cur.names[pid] ?: continue
                        if (d <= 0) continue
                        val key = name.substringBefore(':')
                        agg[key] = (agg[key] ?: 0f) + d / dt * 100f
                    }
                    agg.entries.sortedByDescending { it.value }.take(25).map { (k, v) ->
                        val installed = try { pm.getApplicationInfo(k, 0); true } catch (e: Exception) { false }
                        val label = if (installed) {
                            try { pm.getApplicationLabel(pm.getApplicationInfo(k, 0)).toString() } catch (e: Exception) { k }
                        } else k.substringAfterLast('/')
                        HeatRow(k, label, v, installed)
                    }
                }
            }
            prev = cur
            hist = AppThermal.top()
            delay(2500)
        }
    }

    Page("Heat by app", "Which apps run hot", GFire, Icons.Outlined.LocalFireDepartment, onBack = back) {
        item {
            Glass {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
                    Gauge(s.cpuTemp / 90f, "${s.cpuTemp.toInt()}\u00B0", "CPU", Icons.Outlined.Memory, tempGrad(s.cpuTemp), dia = 104.dp)
                    Gauge(s.batt.tempC / 60f, "${s.batt.tempC.toInt()}\u00B0", "Battery", Icons.Outlined.BatteryStd, tempGrad(s.batt.tempC), dia = 104.dp)
                    Gauge(s.cpu / 100f, "${s.cpu.toInt()}%", "Load", Icons.Outlined.Speed, GTeal, dia = 104.dp)
                }
            }
        }
        item {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Chip("Heating now", tab == 0, GFire) { tab = 0 }
                Chip("History", tab == 1, GFire) { tab = 1 }
            }
        }
        if (tab == 0) {
            val list = now
            if (list == null) item { Hint("Measuring CPU use per app") }
            else if (list.isEmpty()) item { Glass { Hint("Nothing is using the CPU right now.") } }
            else {
                item { Hint("CPU share is the main source of heat. Higher means the app is warming the phone more.") }
                val top = list.first().pct.coerceAtLeast(0.1f)
                items(list, key = { it.key }) { r ->
                    Glass(Modifier.animateItem(), radius = 18.dp, pad = 12.dp) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            if (r.isApp) AppIcon(r.key, 36.dp) else GradIcon(Icons.Outlined.Terminal, GBlue, 36.dp)
                            Spacer(Modifier.width(12.dp))
                            Column(Modifier.weight(1f)) {
                                Row {
                                    Text(r.label, color = TextHi, fontSize = 14.sp, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
                                    Text("${"%.1f".format(r.pct)}%", color = TextLo, fontSize = 13.sp)
                                }
                                Spacer(Modifier.height(7.dp))
                                Bar(r.pct / top, if (r.pct > 15f) GRed else if (r.pct > 6f) GFire else GTeal)
                            }
                        }
                    }
                }
            }
        } else {
            if (hist.isEmpty()) item {
                Glass {
                    Hint(
                        "No history yet. Ashutool records the CPU temperature while each app is in front. " +
                            "Turn on instant lock or usage access, use your apps for a while, and results appear here."
                    )
                }
            } else {
                item {
                    Glass(onClick = { confirmReset = true }) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Outlined.DeleteSweep, null, tint = TextLo, modifier = Modifier.size(20.dp))
                            Spacer(Modifier.width(10.dp))
                            Text("Clear history", color = TextHi, fontSize = 14.sp)
                        }
                    }
                }
                items(hist, key = { it.pkg }) { r ->
                    Glass(Modifier.animateItem(), radius = 18.dp, pad = 12.dp) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            AppIcon(r.pkg, 40.dp)
                            Spacer(Modifier.width(12.dp))
                            Column(Modifier.weight(1f)) {
                                val label = remember(r.pkg) {
                                    try { ctx.packageManager.getApplicationLabel(ctx.packageManager.getApplicationInfo(r.pkg, 0)).toString() } catch (e: Exception) { r.pkg }
                                }
                                Row {
                                    Text(label, color = TextHi, fontSize = 14.sp, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
                                    Text("avg ${"%.0f".format(r.avgT)}\u00B0C", color = TextHi, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                                }
                                Spacer(Modifier.height(6.dp))
                                Bar(r.avgT / 70f, tempGrad(r.avgT))
                                Spacer(Modifier.height(5.dp))
                                Text(
                                    "peak ${"%.0f".format(r.maxT)}\u00B0C   battery peak ${"%.0f".format(r.maxB)}\u00B0C   CPU ${"%.0f".format(r.avgCpu)}%   ${r.minutes} min",
                                    color = TextLo, fontSize = 11.sp
                                )
                            }
                        }
                    }
                }
            }
        }
    }

    if (confirmReset) ConfirmDialog("Clear history", "Delete the saved temperature history for all apps?", "Clear", true,
        onConfirm = { confirmReset = false; AppThermal.reset(); hist = AppThermal.top() }, onDismiss = { confirmReset = false })
}

// ------------------------------------------------------------------- Limits

@Composable
fun LimitsPage(back: () -> Unit) {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    val apps by rememberApps()
    var access by remember { mutableStateOf(Usage.hasAccess(ctx)) }
    var used by remember { mutableStateOf<Map<String, Long>>(emptyMap()) }
    var limits by remember { mutableStateOf(Limits.all(ctx)) }
    var query by remember { mutableStateOf("") }
    var showSystem by remember { mutableStateOf(LockStore.bool(ctx, "limits_system", false)) }
    var edit by remember { mutableStateOf<AppItem?>(null) }
    WhileVisible {
        while (true) {
            if (access) used = withContext(Dispatchers.IO) { Usage.todayAll(ctx) }
            delay(30_000)
        }
    }
    val shown = remember(apps, query, limits, showSystem) {
        // Built in parts of the phone (themes, overlays, navigation bars) are hidden unless asked for.
        // An app that already has a limit always stays in the list so the limit can be removed.
        (apps ?: emptyList()).filter {
            !it.guarded && (showSystem || !it.plainSystem || limits.containsKey(it.pkg)) &&
                (query.isBlank() || it.name.contains(query, true))
        }
            .sortedWith(compareByDescending<AppItem> { limits.containsKey(it.pkg) }.thenBy { it.name.lowercase() })
    }
    Page("App limits", "Daily time limit per app", GBlue, Icons.Outlined.Timer, onBack = back) {
        if (!access) item {
            Glass {
                Text("Usage access is off", color = TextHi, fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
                Text("Limits need it to count your time. Ashutool can grant it through root.", color = TextLo, fontSize = 13.sp, modifier = Modifier.padding(vertical = 8.dp))
                GradButton("Grant usage access", GBlue) {
                    scope.launch {
                        withContext(Dispatchers.IO) { Root.ok("appops set ${ctx.packageName} GET_USAGE_STATS allow") }
                        access = Usage.hasAccess(ctx)
                    }
                }
            }
        } else {
            item { Glass { Hint("When the time is up the app is covered with a limit screen. Extra time needs your lock password if one is set.") } }
            item { SearchField(query) { query = it } }
            item {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Chip("Show system apps", showSystem, GBlue) {
                        showSystem = !showSystem
                        LockStore.setBool(ctx, "limits_system", showSystem)
                    }
                }
            }
            items(shown.take(80), key = { it.pkg }) { app ->
                val lim = limits[app.pkg]
                val u = ((used[app.pkg] ?: 0L) / 60000).toInt()
                Glass(Modifier.animateItem(), radius = 18.dp, pad = 0.dp) {
                    AppHead(app) { GlassButton(Icons.Outlined.Timer, if (lim != null) fmtMin(lim) else "Set", if (lim != null) Accent else TextLo) { edit = app } }
                    if (lim != null) {
                        Column(Modifier.padding(start = 12.dp, end = 12.dp, bottom = 12.dp)) {
                            Bar(u.toFloat() / (lim + Limits.bonus(ctx, app.pkg)), if (u >= lim) GRed else GBlue)
                            Text("${fmtMin(u)} used today", color = TextLo, fontSize = 11.sp, modifier = Modifier.padding(top = 5.dp))
                        }
                    }
                }
            }
        }
    }
    edit?.let { a ->
        LimitDialog(
            a.name, limits[a.pkg] ?: 0,
            onSave = { Limits.set(ctx, a.pkg, it); limits = Limits.all(ctx); edit = null },
            onRemove = { Limits.set(ctx, a.pkg, 0); limits = Limits.all(ctx); edit = null },
            onDismiss = { edit = null }
        )
    }
}

// -------------------------------------------------------------------- Reels

@Composable
fun ReelsPage(back: () -> Unit) {
    val ctx = LocalContext.current
    var mode by remember { mutableIntStateOf(LockStore.int(ctx, "reel_mode", 0)) }
    var limit by remember { mutableIntStateOf(LockStore.int(ctx, "reel_limit", 15)) }
    var usedSec by remember { mutableIntStateOf(ReelStats.used(ctx)) }
    var dialog by remember { mutableStateOf(false) }
    WhileVisible { while (true) { usedSec = ReelStats.used(ctx); delay(5000) } }
    Page("Reels blocker", "Instagram Reels and YouTube Shorts", GPink, Icons.Outlined.VideoLibrary, onBack = back) {
        item {
            Glass {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    GradIcon(Icons.Outlined.Block, if (AshuAccessibility.connected) GGreen else GFire, 40.dp)
                    Spacer(Modifier.width(12.dp))
                    Column {
                        Text(if (AshuAccessibility.connected) "Ready" else "Needs instant lock", color = TextHi, fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
                        Text("Works through the accessibility service. Turn it on from the Lock tab.", color = TextLo, fontSize = 12.sp)
                    }
                }
            }
        }
        item { PrefSwitch("reel_ig", false, Icons.Outlined.VideoLibrary, GPink, "Instagram Reels", "Sends you back to the feed when Reels opens") }
        item { PrefSwitch("reel_yt", false, Icons.Outlined.SmartDisplay, GRed, "YouTube Shorts", "Closes Shorts and returns you back") }
        item {
            Glass {
                SectionTitle("Mode")
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Chip("Block fully", mode == 0, GPink) { mode = 0; LockStore.setInt(ctx, "reel_mode", 0) }
                    Chip("Daily limit", mode == 1, GPink) { mode = 1; LockStore.setInt(ctx, "reel_mode", 1) }
                }
                if (mode == 1) {
                    Spacer(Modifier.height(12.dp))
                    GlassButton(Icons.Outlined.Timer, "Limit: ${fmtMin(limit)}") { dialog = true }
                    Spacer(Modifier.height(10.dp))
                    Bar(usedSec.toFloat() / (limit * 60), if (usedSec >= limit * 60) GRed else GPink)
                    Text("${fmtMin(usedSec / 60)} of reels watched today", color = TextLo, fontSize = 11.sp, modifier = Modifier.padding(top = 5.dp))
                }
            }
        }
        item { Glass { Hint("The apps change their layout now and then, so detection can break after an update. Tell me if it stops working.") } }
    }
    if (dialog) LimitDialog(
        "Reels", limit,
        onSave = { limit = it; LockStore.setInt(ctx, "reel_limit", it); dialog = false },
        onRemove = { dialog = false }, onDismiss = { dialog = false }
    )
}
