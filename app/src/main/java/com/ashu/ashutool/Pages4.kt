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

import android.content.Intent
import android.content.ClipData
import android.content.ClipboardManager
import android.net.Uri
import android.provider.Settings
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.clickable
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.*

// ------------------------------------------------------------------ Network

@Composable
fun NetworkPage() {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    val apps by rememberApps()
    var query by remember { mutableStateOf("") }
    var system by remember { mutableStateOf(false) }
    var net by remember { mutableStateOf(LockStore.netModes(ctx)) }
    var open by remember { mutableStateOf<String?>(null) }
    val shown = remember(apps, query, system) {
        (apps ?: emptyList()).filter {
            it.system == system && (query.isBlank() || it.name.contains(query, true) || it.pkg.contains(query, true))
        }
    }
    fun apply() { scope.launch(Dispatchers.IO) { Firewall.applyAll(ctx) } }
    fun label(m: Int) = when (m) { 1 -> "No mobile data"; 2 -> "No Wi-Fi"; 3 -> "Blocked"; else -> "Allowed" }

    Page("Network", "Block mobile data or Wi-Fi per app", GBlue, Icons.Outlined.NetworkCheck) {
        item {
            Glass {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    GradIcon(Icons.Outlined.NetworkCheck, GBlue, 48.dp)
                    Spacer(Modifier.width(14.dp))
                    Column(Modifier.weight(1f)) {
                        GradText("${net.size}", GBlue, 28.sp)
                        Text(if (net.size == 1) "app restricted" else "apps restricted", color = TextLo, fontSize = 12.sp)
                    }
                    if (net.isNotEmpty()) GradButton("Reset all", GBlue) {
                        net.keys.forEach { LockStore.setNetMode(ctx, it, 0) }
                        net = LockStore.netModes(ctx)
                        scope.launch(Dispatchers.IO) { Firewall.clearAll(); Firewall.applyAll(ctx) }
                        toast(ctx, "All network rules removed")
                    }
                }
                Text(
                    "Rules live in their own firewall chain and come back after a restart. System apps are never blocked.",
                    color = TextLo, fontSize = 12.sp, modifier = Modifier.padding(top = 10.dp)
                )
            }
        }
        item { SearchField(query) { query = it } }
        item {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Chip("User apps", !system, GBlue) { system = false }
                Chip("System apps", system, GBlue) { system = true }
            }
        }
        if (apps == null) item { Hint("Loading apps") }
        else if (shown.isEmpty()) item { Hint("No apps in this view.") }
        items(shown, key = { it.pkg }) { app ->
            val m = net[app.pkg] ?: 0
            Glass(Modifier.animateItem(), radius = 18.dp, pad = 0.dp) {
                AppHead(app) {
                    GlassButton(
                        if (m == 0) Icons.Outlined.Wifi else Icons.Outlined.WifiOff, label(m),
                        if (m == 0) Accent else Warn
                    ) { open = if (open == app.pkg) null else app.pkg }
                }
                if (open == app.pkg) {
                    FlowRow(
                        Modifier.padding(start = 12.dp, end = 12.dp, bottom = 12.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        listOf(0, 1, 2, 3).forEach { mode ->
                            Chip(label(mode), m == mode, GBlue) {
                                if (app.guarded) toast(ctx, "This app is protected")
                                else { LockStore.setNetMode(ctx, app.pkg, mode); net = LockStore.netModes(ctx); apply() }
                            }
                        }
                    }
                }
            }
        }
    }
}


// -------------------------------------------------------------------- Games

@Composable
private fun GameCard(app: AppItem, modifier: Modifier, onClick: () -> Unit) {
    Glass(modifier, radius = 20.dp, pad = 10.dp, onClick = onClick) {
        Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.fillMaxWidth()) {
            AppIcon(app.pkg, 56.dp)
            Spacer(Modifier.height(6.dp))
            Text(app.name, color = TextHi, fontSize = 11.sp, maxLines = 1, overflow = TextOverflow.Ellipsis, textAlign = TextAlign.Center)
        }
    }
}

@Composable
fun GamesPage() = SubHost(
    root = { open -> GamesMain(open) },
    sub = { k, back ->
        when (k) {
            "sidebar" -> SidebarSub(back)
            "style" -> StyleSub(back)
            "mode" -> ModeSub(back)
            else -> WindowsSub(back)
        }
    }
)

@Composable
private fun GamesMain(open: (String) -> Unit) {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    val apps by rememberApps()
    var games by remember { mutableStateOf(GameMode.cached) }
    var pick by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { games = withContext(Dispatchers.IO) { GameMode.games(ctx) } }
    val list = remember(apps, games) { (apps ?: emptyList()).filter { it.pkg in games } }

    var overlayOk by remember { mutableStateOf(Settings.canDrawOverlays(ctx)) }
    var usageOk by remember { mutableStateOf(Usage.hasAccess(ctx)) }
    var rootOk by remember { mutableStateOf(false) }
    var tick by remember { mutableIntStateOf(0) }
    val a11yOk = AshuAccessibility.connected
    LaunchedEffect(Unit) {
        rootOk = withContext(Dispatchers.IO) { Root.isGranted() }
        while (true) { delay(1000); tick++ }
    }

    Page("Game launcher", "Boost, sidebar and quick launch", GTeal, Icons.Outlined.SportsEsports) {
        item {
            val t = tick
            val svc = MonitorService.instance
            val last = Fg.pkg
            val shown = t >= 0 && svc?.barShown() == true
            val err = (svc?.barError() ?: "").ifEmpty { GameDiag.error }
            val recent = Fg.recent().filter {
                it != ctx.packageName && it !in games && it != "com.android.systemui" && it !in Safety.homePkgs(ctx) && Safety.validPkg(it)
            }.take(4)
            Glass {
                Text("Game bar check", color = TextHi, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
                Spacer(Modifier.height(4.dp))
                StatusRow("Ashutool service running", svc != null)
                StatusRow("Draw over other apps", overlayOk)
                StatusRow("Sees which app is open", a11yOk || usageOk || rootOk)
                StatusRow("Safe mode off", !Guard.safe)
                Spacer(Modifier.height(4.dp))
                Text(
                    "Last app seen: ${last ?: "none yet"}${if (last != null && last in games) "  (game)" else ""}",
                    color = TextLo, fontSize = 12.sp
                )
                Text(if (shown) "The bar is on screen right now" else "The bar is not on screen right now", color = TextLo, fontSize = 12.sp)
                if (err.isNotEmpty()) Text("Error: $err", color = Danger, fontSize = 12.sp)
                Spacer(Modifier.height(8.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    GradButton("Test bar", GTeal, Icons.Outlined.Widgets) {
                        val r = MonitorService.instance?.testBar() ?: "The service is not running. Tap Fix with root"
                        toast(ctx, if (r.isEmpty()) "Bar shown for 6 seconds" else r)
                    }
                    GradButton("Fix with root", GBlue, Icons.Outlined.Build) {
                        scope.launch {
                            if (Guard.safe) { Guard.safe = false; LockStore.setStr(ctx, "starts", "") }
                            withContext(Dispatchers.IO) { Root.bootstrap(ctx) }
                            androidx.core.content.ContextCompat.startForegroundService(ctx, Intent(ctx, MonitorService::class.java))
                            overlayOk = Settings.canDrawOverlays(ctx)
                            usageOk = Usage.hasAccess(ctx)
                            rootOk = withContext(Dispatchers.IO) { Root.isGranted() }
                            toast(ctx, "Checked again")
                        }
                    }
                }
                if (recent.isNotEmpty()) {
                    Spacer(Modifier.height(12.dp))
                    Text("Game not detected? Open it for a moment, come back here and mark it:", color = TextLo, fontSize = 12.sp)
                    recent.forEach { p ->
                        Row(Modifier.fillMaxWidth().padding(top = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                            AppIcon(p, 32.dp)
                            Spacer(Modifier.width(10.dp))
                            Text(
                                try { ctx.packageManager.getApplicationLabel(ctx.packageManager.getApplicationInfo(p, 0)).toString() } catch (e: Exception) { p },
                                color = TextHi, fontSize = 13.sp, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f)
                            )
                            GlassButton(Icons.Outlined.SportsEsports, "Mark as game") {
                                scope.launch {
                                    games = withContext(Dispatchers.IO) { GameMode.setGame(ctx, p, true); GameMode.cached }
                                    MonitorService.instance?.recheck()
                                    toast(ctx, "Added. The bar will show in it now")
                                }
                            }
                        }
                    }
                }
            }
        }
        item {
            Glass {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    GradIcon(Icons.Outlined.SportsEsports, GTeal, 48.dp)
                    Spacer(Modifier.width(14.dp))
                    Column(Modifier.weight(1f)) {
                        GradText("${list.size}", GTeal, 28.sp)
                        Text(if (GameMode.active != null) "Game mode is active" else "games found", color = TextLo, fontSize = 12.sp)
                    }
                    GradButton("Add or remove", GTeal) { pick = true }
                }
            }
        }
        if (list.isEmpty() && apps != null) item { Glass { Hint("No games detected yet. Tap Add or remove to choose them.") } }
        list.chunked(3).forEachIndexed { r, row ->
            item {
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    row.forEachIndexed { c, g ->
                        GameCard(g, Modifier.weight(1f).enter(r * 3 + c, 90f)) {
                            scope.launch {
                                withContext(Dispatchers.IO) { GameMode.start(ctx, g.pkg) }
                                val i = ctx.packageManager.getLaunchIntentForPackage(g.pkg)
                                if (i != null) ctx.startActivity(i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) else toast(ctx, "Cannot open ${g.name}")
                            }
                        }
                    }
                    repeat(3 - row.size) { Spacer(Modifier.weight(1f)) }
                }
            }
        }
        item {
            SwitchRow(Icons.Outlined.Videocam, GRed, "Screen recording", "Floating timer with a stop button, also in the notification", Recorder.running) { on ->
                scope.launch(Dispatchers.IO) { if (on) Recorder.start() else Recorder.stop() }
            }
        }
        item { NavRow(Icons.Outlined.Widgets, GTeal, "Sidebar", "Size, position, edge and opacity") { open("sidebar") } }
        item { NavRow(Icons.Outlined.AutoAwesome, GViolet, "Bar style", "Pick a design and colour for the bar") { open("style") } }
        item { NavRow(Icons.Outlined.Speed, GFire, "Game mode", "Performance, do not disturb, popups") { open("mode") } }
        item { NavRow(Icons.Outlined.Layers, GBlue, "Windows and apps", "Open apps in a window from the sidebar") { open("windows") } }
    }

    if (pick) AlertDialog(
        onDismissRequest = { pick = false },
        containerColor = DialogBg, titleContentColor = TextHi, textContentColor = TextLo,
        title = { Text("Choose games") },
        text = {
            LazyColumn(Modifier.heightIn(max = 380.dp)) {
                items((apps ?: emptyList()).filter { !it.system }, key = { it.pkg }) { a ->
                    Row(Modifier.fillMaxWidth().padding(vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                        AppIcon(a.pkg, 32.dp)
                        Spacer(Modifier.width(10.dp))
                        Text(a.name, color = TextHi, fontSize = 14.sp, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
                        Switch(
                            checked = a.pkg in games,
                            onCheckedChange = { on -> scope.launch(Dispatchers.IO) { GameMode.setGame(ctx, a.pkg, on); games = GameMode.cached } },
                            colors = glassSwitchColors()
                        )
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = { pick = false }) { Text("Done", color = Accent) } }
    )
}

@Composable
private fun PrefSlider(label: String, key: String, def: Int, lo: Int, hi: Int, unit: String) {
    val ctx = LocalContext.current
    var v by remember { mutableFloatStateOf(LockStore.int(ctx, key, def).toFloat()) }
    Glass {
        SectionTitle(label, "${v.toInt()}$unit")
        Slider(
            value = v, onValueChange = { v = it }, valueRange = lo.toFloat()..hi.toFloat(),
            onValueChangeFinished = { LockStore.setInt(ctx, key, v.toInt()) },
            colors = SliderDefaults.colors(thumbColor = Accent, activeTrackColor = Accent, inactiveTrackColor = Tint.copy(alpha = 0.15f))
        )
    }
}

@Composable
private fun SidebarSub(back: () -> Unit) {
    val ctx = LocalContext.current
    var edge by remember { mutableIntStateOf(LockStore.int(ctx, "g_edge", 1)) }
    Page("Sidebar", "Customize the bar", GTeal, Icons.Outlined.Widgets, onBack = back) {
        item { PrefSwitch("g_sidebar", true, Icons.Outlined.Widgets, GTeal, "Game sidebar", "Edge bar with stats and quick tools while you play") }
        item { PrefSwitch("rec_pill", true, Icons.Outlined.Videocam, GRed, "Recording timer on screen", "See-through timer with a stop button you can drag") }
        item { PrefSwitch("g_fps", true, Icons.Outlined.Speed, GGreen, "FPS meter", "A small see-through FPS number in a corner while you play") }
        item { PrefSwitch("g_hud_speed", true, Icons.Outlined.NetworkCheck, GCyan, "Speed in the same readout", "Download speed next to the FPS number") }
        item {
            var pos by remember { mutableIntStateOf(LockStore.int(ctx, "g_hud_pos", 0)) }
            Glass {
                SectionTitle("Where the readout sits")
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf("Top left", "Top right", "Bottom left", "Bottom right").forEachIndexed { i, n ->
                        Chip(n, pos == i, GGreen) { pos = i; LockStore.setInt(ctx, "g_hud_pos", i) }
                    }
                }
            }
        }
        item { PrefSlider("Bar length", "g_h", 88, 40, 240, "") }
        item { PrefSlider("Bar thickness", "g_w", 7, 3, 16, "") }
        item { PrefSlider("Opacity", "g_alpha", 90, 30, 100, "%") }
        item { PrefSlider("Position along the edge", "g_pos", 50, 8, 92, "%") }
        item {
            Glass {
                SectionTitle("Edge")
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf("Left", "Right", "Top", "Bottom").forEachIndexed { i, l -> Chip(l, edge == i) { edge = i; LockStore.setInt(ctx, "g_edge", i) } }
                }
                Text("You can also press and hold the bar in a game, then drag it to any side.", color = TextLo, fontSize = 12.sp, modifier = Modifier.padding(top = 10.dp))
            }
        }
    }
}

@Composable
private fun StyleSub(back: () -> Unit) {
    val ctx = LocalContext.current
    var style by remember { mutableIntStateOf(LockStore.int(ctx, "g_style", 0)) }
    var color by remember { mutableIntStateOf(LockStore.int(ctx, "g_color", 0)) }
    Page("Bar style", "Pick a design and colour", GViolet, Icons.Outlined.AutoAwesome, onBack = back) {
        item {
            Glass {
                SectionTitle("Colour", BarColorNames[color.coerceIn(0, BarColors.size - 1)])
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    BarColors.forEachIndexed { i, c ->
                        Box(
                            Modifier.bounceClick { color = i; LockStore.setInt(ctx, "g_color", i) }.size(36.dp).clip(CircleShape).background(c)
                                .border(if (color == i) 3.dp else 1.dp, if (color == i) Accent else Line, CircleShape)
                        )
                    }
                }
            }
        }
        BarStyles.chunked(2).forEachIndexed { r, row ->
            item {
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    row.forEachIndexed { c, name ->
                        val i = r * 2 + c
                        Glass(Modifier.weight(1f), radius = 20.dp, pad = 10.dp, onClick = { style = i; LockStore.setInt(ctx, "g_style", i) }) {
                            Box(Modifier.fillMaxWidth().height(86.dp).clip(RoundedCornerShape(14.dp)).background(Color(0xFF0B1120))) {
                                Canvas(Modifier.fillMaxSize()) {
                                    drawBar(i, BarColors[color.coerceIn(0, BarColors.size - 1)], 0.95f, 1, 8.dp.toPx(), 56.dp.toPx())
                                }
                            }
                            Spacer(Modifier.height(8.dp))
                            Text(name, color = if (style == i) Accent else TextHi, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                        }
                    }
                    if (row.size == 1) Spacer(Modifier.weight(1f))
                }
            }
        }
    }
}

@Composable
private fun ModeSub(back: () -> Unit) {
    val ctx = LocalContext.current
    var govs by remember { mutableStateOf(emptyList<String>()) }
    var gov by remember { mutableStateOf(LockStore.str(ctx, "g_gov", "performance")) }
    var prof by remember { mutableIntStateOf(LockStore.int(ctx, "g_profile_auto", 0)) }
    LaunchedEffect(Unit) { govs = withContext(Dispatchers.IO) { Sys.policies().firstOrNull()?.govs ?: emptyList() } }
    Page("Game mode", "Applied when a game opens", GFire, Icons.Outlined.Speed, onBack = back) {
        item { Glass { Hint("Everything here is off until you turn it on. You can always change the profile, do not disturb and more from the game panel.") } }
        item { PrefSwitch("g_auto", false, Icons.Outlined.PlayCircle, GBlue, "Auto game mode", "Apply these settings when a game opens") }
        item {
            Glass {
                SectionTitle("Profile when a game opens")
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Chip("Standard", prof == 0, GBlue) { prof = 0; LockStore.setInt(ctx, "g_profile_auto", 0) }
                    Chip("Performance", prof == 1, GFire) { prof = 1; LockStore.setInt(ctx, "g_profile_auto", 1) }
                    Chip("Battery saver", prof == 2, GCyan) { prof = 2; LockStore.setInt(ctx, "g_profile_auto", 2) }
                }
                Text("Standard leaves the CPU as it is. Restored after the game.", color = TextLo, fontSize = 12.sp, modifier = Modifier.padding(top = 8.dp))
            }
        }
        item { PrefSwitch("g_dnd", false, Icons.Outlined.NotificationsOff, GViolet, "Do not disturb", "Silence calls and messages during the game") }
        item { PrefSwitch("g_headsup", false, Icons.Outlined.NotificationsPaused, GPink, "Block heads-up popups", "No banners over the game") }
        item { PrefSwitch("g_kill", false, Icons.Outlined.CleaningServices, GCyan, "Stop background apps", "Frees RAM before the game starts") }
        if (govs.isNotEmpty()) item {
            Glass {
                SectionTitle("Governor while playing")
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    govs.forEach { g -> Chip(g, g == gov, GFire) { gov = g; LockStore.setStr(ctx, "g_gov", g) } }
                }
            }
        }
    }
}

@Composable
private fun WindowsSub(back: () -> Unit) {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    val apps by rememberApps()
    var shelf by remember { mutableStateOf(LockStore.set(ctx, "g_shelf")) }
    var side by remember { mutableIntStateOf(LockStore.int(ctx, "g_float_side", 0)) }
    Page("Windows and apps", "Open apps over your game", GBlue, Icons.Outlined.Layers, onBack = back) {
        item { PrefSwitch("g_float", true, Icons.Outlined.Layers, GBlue, "Floating window", "Open shelf apps in a window when your ROM supports it") }
        item {
            Glass {
                Text("Where the window opens", color = TextHi, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
                Text("This is where a window opens the first time. After that it opens where you left it. Move it with the bar under it and resize it with the corner grip.", color = TextLo, fontSize = 12.sp, modifier = Modifier.padding(vertical = 8.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf("Left", "Right", "Center").forEachIndexed { i, name ->
                        Chip(name, side == i, GBlue) { side = i; LockStore.setInt(ctx, "g_float_side", i) }
                    }
                }
            }
        }
        item {
            Glass {
                Text("Freeform windows", color = TextHi, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
                Text("Turns on the system setting that allows floating windows. Some ROMs need a reboot afterwards.", color = TextLo, fontSize = 12.sp, modifier = Modifier.padding(vertical = 8.dp))
                GradButton("Enable freeform windows", GBlue) {
                    scope.launch { val ok = withContext(Dispatchers.IO) { Windows.enableFreeform() }; toast(ctx, if (ok) "Enabled. Reboot if windows do not float" else "Could not enable") }
                }
            }
        }
        item { SectionTitle("Sidebar shelf", "${shelf.size} chosen") }
        item { Glass { Hint("Chosen apps show in the sidebar under Open in window. With none chosen, your games are shown.") } }
        items((apps ?: emptyList()).filter { !it.system && !it.guarded }, key = { it.pkg }) { a ->
            Glass(Modifier.animateItem(), radius = 18.dp, pad = 0.dp) {
                AppHead(a) {
                    Switch(
                        checked = a.pkg in shelf,
                        onCheckedChange = { on ->
                            val n = if (on) shelf + a.pkg else shelf - a.pkg
                            shelf = n
                            LockStore.setSet(ctx, "g_shelf", n)
                        },
                        colors = glassSwitchColors()
                    )
                }
            }
        }
    }
}

// ----------------------------------------------------------------- Settings

@Composable
fun SettingsPage() = SubHost(
    root = { open -> SettingsMain(open) },
    sub = { k, back ->
        when (k) {
            "look" -> LookSub(back)
            "perm" -> PermSub(back)
            "safe" -> SafeSub(back)
            "log" -> LogSub(back)
            else -> AboutSub(back)
        }
    }
)

@Composable
private fun SettingsMain(open: (String) -> Unit) {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    var spOn by remember { mutableStateOf(LockStore.bool(ctx, "sp_on", false)) }
    var spX by remember { mutableIntStateOf(LockStore.int(ctx, "sp_x", 36)) }
    Page("Settings", "Look, permissions and safety", GCyan, Icons.Outlined.Settings) {
        item { NavRow(Icons.Outlined.ColorLens, GViolet, "Appearance", "Themes, glass strength, animation") { open("look") } }
        item { NavRow(Icons.Outlined.AdminPanelSettings, GCyan, "Permissions", "Root, instant lock, usage, overlay, calls") { open("perm") } }
        item { NavRow(Icons.Outlined.Shield, GGreen, "Safety", "Safe mode, firewall, protections") { open("safe") } }
        item { NavRow(Icons.Outlined.Terminal, GBlue, "Action log", "Every change made as root") { open("log") } }
        item { NavRow(Icons.Outlined.Info, GPink, "About Ashutool", "Version, developer and licence") { open("about") } }
        item {
            SwitchRow(
                Icons.Outlined.NetworkCheck, GCyan, "Speed in the status bar",
                "Network speed in the status bar, not in the notification panel. Off by default", spOn
            ) { v ->
                spOn = v
                LockStore.setBool(ctx, "sp_on", v)
                MonitorService.instance?.wakeSpeed()
            }
        }
        if (spOn) item {
            Glass {
                SectionTitle("Where in the bar")
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf("Left" to 22, "Left of camera" to 36, "Center" to 50, "Right" to 66).forEach { (n, v) ->
                        Chip(n, spX == v, GCyan) { spX = v; LockStore.setInt(ctx, "sp_x", v); MonitorService.instance?.wakeSpeed() }
                    }
                }
            }
        }
        if (spOn) item { PrefSwitch("sp_both", false, Icons.Outlined.SwapVert, GBlue, "Show upload too", "Both speeds, a little longer") }
        item {
            Glass {
                Text("Backup and restore", color = TextHi, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
                Text(
                    "Saves your settings to ${Backup.PATH}. Passwords, PINs and the vault code are never included.",
                    color = TextLo, fontSize = 12.sp, modifier = Modifier.padding(vertical = 8.dp)
                )
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    GradButton("Export", GTeal, Icons.Outlined.Save) {
                        scope.launch {
                            val n = withContext(Dispatchers.IO) { Backup.export(ctx) }
                            toast(ctx, if (n >= 0) "Saved $n settings" else "Could not save. Is root granted?")
                        }
                    }
                    GradButton("Import", GBlue, Icons.Outlined.Restore) {
                        scope.launch {
                            val n = withContext(Dispatchers.IO) { Backup.import(ctx) }
                            if (n >= 0) {
                                Palette.load(ctx)
                                toast(ctx, "Restored $n settings. Restart the app to apply everything")
                            } else toast(ctx, "No backup file found")
                        }
                    }
                }
            }
        }
        item { CreditFooter() }
    }
}

@Composable
private fun ThemeCard(p: Pal, selected: Boolean, onClick: () -> Unit) {
    val shape = RoundedCornerShape(20.dp)
    val bg = if (p.flat) Brush.verticalGradient(listOf(p.bg1, p.bg1)) else Brush.verticalGradient(listOf(p.bg1, p.bg2, p.bg3))
    Column(Modifier.bounceClick(onClick = onClick).width(112.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        Box(
            Modifier.size(width = 112.dp, height = 176.dp).clip(shape).background(bg)
                .border(if (selected) 2.dp else 1.dp, if (selected) Brush.linearGradient(listOf(p.primary.a, p.primary.b)) else SolidColor(Line), shape)
        ) {
            if (!p.flat) Canvas(Modifier.fillMaxSize()) {
                val w = size.width
                val h = size.height
                fun orb(col: Color, cx: Float, cy: Float, r: Float) =
                    drawCircle(Brush.radialGradient(listOf(col.copy(alpha = p.orbA), Color.Transparent), center = Offset(cx, cy), radius = r), radius = r, center = Offset(cx, cy))
                orb(p.orb1, w * 0.2f, h * 0.15f, w * 0.9f)
                orb(p.orb2, w * 0.95f, h * 0.5f, w * 0.8f)
                orb(p.orb3, w * 0.2f, h * 0.95f, w * 0.9f)
            }
            Column(Modifier.fillMaxSize().padding(10.dp), verticalArrangement = Arrangement.spacedBy(7.dp)) {
                Box(Modifier.fillMaxWidth(0.55f).height(8.dp).clip(RoundedCornerShape(4.dp)).background(p.textHi.copy(alpha = 0.85f)))
                Box(Modifier.fillMaxWidth(0.35f).height(5.dp).clip(RoundedCornerShape(3.dp)).background(p.textLo.copy(alpha = 0.7f)))
                Box(
                    Modifier.fillMaxWidth().height(54.dp).clip(RoundedCornerShape(12.dp))
                        .background(p.tint.copy(alpha = p.glassTop + 0.05f)).border(1.dp, p.tint.copy(alpha = p.bA * 0.7f), RoundedCornerShape(12.dp))
                ) {
                    Canvas(Modifier.size(38.dp).align(Alignment.CenterStart).padding(7.dp)) {
                        drawArc(p.tint.copy(alpha = 0.15f), 135f, 270f, false, style = Stroke(5f, cap = StrokeCap.Round))
                        drawArc(p.primary.h(), 135f, 190f, false, style = Stroke(5f, cap = StrokeCap.Round))
                    }
                    Column(Modifier.align(Alignment.CenterEnd).padding(end = 10.dp), verticalArrangement = Arrangement.spacedBy(5.dp)) {
                        Box(Modifier.width(34.dp).height(5.dp).clip(RoundedCornerShape(3.dp)).background(p.primary.h()))
                        Box(Modifier.width(24.dp).height(5.dp).clip(RoundedCornerShape(3.dp)).background(p.tint.copy(alpha = 0.3f)))
                    }
                }
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    repeat(2) { Box(Modifier.weight(1f).height(26.dp).clip(RoundedCornerShape(9.dp)).background(p.tint.copy(alpha = p.glassTop + 0.04f))) }
                }
                Spacer(Modifier.weight(1f))
                Box(Modifier.fillMaxWidth().height(20.dp).clip(RoundedCornerShape(10.dp)).background(p.tint.copy(alpha = p.glassTop + 0.08f)), contentAlignment = Alignment.Center) {
                    Row(horizontalArrangement = Arrangement.spacedBy(9.dp)) {
                        repeat(4) { i -> Box(Modifier.size(6.dp).clip(CircleShape).background(if (i == 0) p.accent else p.textLo.copy(alpha = 0.6f))) }
                    }
                }
            }
            if (selected) Box(
                Modifier.align(Alignment.TopEnd).padding(8.dp).size(18.dp).clip(CircleShape).background(p.primary.h()),
                contentAlignment = Alignment.Center
            ) { Icon(Icons.Outlined.Check, null, tint = Ink, modifier = Modifier.size(12.dp)) }
        }
        Spacer(Modifier.height(6.dp))
        Text(p.name, color = if (selected) Accent else TextLo, fontSize = 12.sp, fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal)
    }
}

@Composable
private fun LookSub(back: () -> Unit) {
    val ctx = LocalContext.current
    var glass by remember { mutableFloatStateOf(LockStore.int(ctx, "glass_x", 100).toFloat()) }
    var anim by remember { mutableStateOf(Palette.animated) }
    fun pick(i: Int) { Palette.auto = false; Palette.mode = i; LockStore.setBool(ctx, "theme_auto", false); LockStore.setInt(ctx, "theme", i) }
    Page("Appearance", Palette.cur.name, GViolet, Icons.Outlined.ColorLens, onBack = back) {
        item {
            SwitchRow(Icons.Outlined.ColorLens, GViolet, "Follow system", "Pure black in dark mode, pure white in light mode", Palette.auto) {
                Palette.auto = it
                LockStore.setBool(ctx, "theme_auto", it)
            }
        }
        item { SectionTitle("Dark themes") }
        item {
            LazyRow(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                items(Palette.themes.withIndex().filter { it.value.dark }.toList()) { (i, p) -> ThemeCard(p, !Palette.auto && Palette.mode == i) { pick(i) } }
            }
        }
        item { SectionTitle("Light themes") }
        item {
            LazyRow(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                items(Palette.themes.withIndex().filter { !it.value.dark }.toList()) { (i, p) -> ThemeCard(p, !Palette.auto && Palette.mode == i) { pick(i) } }
            }
        }
        item {
            Glass {
                SectionTitle("Glass strength", "${glass.toInt()}%")
                Slider(
                    value = glass, onValueChange = { glass = it; Palette.glass = it / 100f }, valueRange = 60f..160f,
                    onValueChangeFinished = { LockStore.setInt(ctx, "glass_x", glass.toInt()) },
                    colors = SliderDefaults.colors(thumbColor = Accent, activeTrackColor = Accent, inactiveTrackColor = Tint.copy(alpha = 0.15f))
                )
            }
        }
        item {
            SwitchRow(Icons.Outlined.AutoAwesome, GPink, "Animated background", "Drifting glow on gradient themes. Uses more battery", anim) {
                anim = it
                Palette.animated = it
                LockStore.setBool(ctx, "anim_bg", it)
            }
        }
        item { PrefSwitch("boot", true, Icons.Outlined.PowerSettingsNew, GCyan, "Start on boot", "Keeps lock, vault and sidebar active after a restart") }
    }
}

@Composable
private fun StatusRow(label: String, ok: Boolean) {
    Row(Modifier.fillMaxWidth().padding(vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(label, color = TextHi, fontSize = 13.sp, modifier = Modifier.weight(1f))
        Text(if (ok) "Ready" else "Missing", color = if (ok) Accent else Danger, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
    }
}

@Composable
private fun PermSub(back: () -> Unit) {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    var root by remember { mutableStateOf(false) }
    var usage by remember { mutableStateOf(Usage.hasAccess(ctx)) }
    var overlay by remember { mutableStateOf(Settings.canDrawOverlays(ctx)) }
    var phone by remember { mutableStateOf(androidx.core.content.ContextCompat.checkSelfPermission(ctx, android.Manifest.permission.READ_PHONE_STATE) == android.content.pm.PackageManager.PERMISSION_GRANTED) }
    val a11y = AshuAccessibility.connected
    LaunchedEffect(Unit) { root = withContext(Dispatchers.IO) { Root.isGranted() } }
    Page("Permissions", "What Ashutool has", GCyan, Icons.Outlined.AdminPanelSettings, onBack = back) {
        item {
            Glass {
                StatusRow("Root access", root)
                StatusRow("Instant lock (accessibility)", a11y)
                StatusRow("Usage access", usage)
                StatusRow("Draw over other apps", overlay)
                StatusRow("Phone state and answer calls", phone)
                Spacer(Modifier.height(8.dp))
                GradButton("Fix with root", GCyan, Icons.Outlined.Build) {
                    scope.launch {
                        withContext(Dispatchers.IO) { Root.bootstrap(ctx) }
                        usage = Usage.hasAccess(ctx); overlay = Settings.canDrawOverlays(ctx)
                        phone = androidx.core.content.ContextCompat.checkSelfPermission(ctx, android.Manifest.permission.READ_PHONE_STATE) == android.content.pm.PackageManager.PERMISSION_GRANTED
                        toast(ctx, "Permissions refreshed")
                    }
                }
            }
        }
    }
}

@Composable
private fun SafeSub(back: () -> Unit) {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    Page("Safety", "Guards that keep the phone usable", GGreen, Icons.Outlined.Shield, onBack = back) {
        item {
            Glass {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    GradIcon(Icons.Outlined.Shield, if (Guard.safe) GRed else GGreen, 40.dp)
                    Spacer(Modifier.width(12.dp))
                    Column(Modifier.weight(1f)) {
                        Text(if (Guard.safe) "Safe mode is on" else "Safety guards are on", color = TextHi, fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
                        Text(
                            if (Guard.safe) "The service restarted several times. Charge limit, firewall and game mode are paused."
                            else "Launcher, keyboard, dialer, system apps and root managers cannot be hidden, removed, stopped or blocked.",
                            color = TextLo, fontSize = 12.sp
                        )
                    }
                }
                if (Guard.safe) {
                    Spacer(Modifier.height(10.dp))
                    GradButton("Resume auto features", GTeal) { Guard.safe = false; LockStore.setStr(ctx, "starts", "") }
                }
            }
        }
        item {
            ActionRow(Icons.Outlined.NetworkCheck, GBlue, "Remove all firewall rules", "Flushes the Ashutool network chain", {
                scope.launch {
                    val ok = withContext(Dispatchers.IO) { Firewall.clearAll() }
                    toast(ctx, if (ok) "Firewall rules removed" else "Nothing to remove")
                }
            })
        }
        item { Glass { Hint("Ashutool never remounts or writes to the system partition. If you forget the lock and the recovery code, clear Ashutool's app data with root.") } }
    }
}

@Composable
private fun LogSub(back: () -> Unit) {
    var log by remember { mutableStateOf(Root.recent()) }
    WhileVisible { while (true) { log = Root.recent(); delay(3000) } }
    Page("Action log", "Last ${log.size} changes", GBlue, Icons.Outlined.Terminal, onBack = back) {
        if (log.isEmpty()) item { Glass { Hint("Nothing has been changed yet.") } }
        else item {
            Glass {
                log.forEach {
                    Text(it, color = TextLo, fontSize = 10.sp, fontFamily = FontFamily.Monospace, maxLines = 2, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(vertical = 2.dp))
                }
            }
        }
        item { Hint("Every change Ashutool makes as root is listed here. Reads are not logged.") }
    }
}

@Composable
private fun LicenseRow(name: String, licence: String) {
    Row(Modifier.fillMaxWidth().padding(vertical = 4.dp), horizontalArrangement = Arrangement.SpaceBetween) {
        Text(name, color = TextHi, fontSize = 13.sp, modifier = Modifier.weight(1f))
        Text(licence, color = TextLo, fontSize = 12.sp)
    }
}

@Composable
private fun AboutSub(back: () -> Unit) {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    var taps by remember { mutableIntStateOf(0) }
    var crash by remember { mutableStateOf(CrashLog.last(ctx)) }
    val verdict = remember { Integrity.verdict(ctx) }
    fun copy(text: String, msg: String) {
        ctx.getSystemService(ClipboardManager::class.java).setPrimaryClip(ClipData.newPlainText(Credits.APP, text))
        toast(ctx, msg)
    }
    Page("About ${Credits.APP}", "Version ${BuildConfig.VERSION_NAME}", GCyan, Icons.Outlined.Info, onBack = back) {
        item {
            Glass {
                Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
                    Box(Modifier.bounceClick { taps++; if (taps == 7) toast(ctx, "Hidden button unlocked") }) { GradIcon(Icons.Outlined.Bolt, GTeal, 72.dp) }
                    Spacer(Modifier.height(10.dp))
                    GradText(Credits.APP, GTeal, 26.sp)
                    Text("Root toolkit for custom ROMs", color = TextLo, fontSize = 13.sp)
                    Spacer(Modifier.height(6.dp))
                    Text(
                        Integrity.label(ctx),
                        color = if (verdict == Integrity.Verdict.OFFICIAL) Accent else Warn,
                        fontSize = 12.sp, fontWeight = FontWeight.Medium
                    )
                }
            }
        }
        item {
            Glass {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    GradIcon(Icons.Outlined.Person, GViolet, 48.dp)
                    Spacer(Modifier.width(12.dp))
                    Column {
                        Text(Credits.AUTHOR, color = TextHi, fontSize = 18.sp, fontWeight = FontWeight.Bold)
                        Text("Developer  \u00B7  ${Credits.TELEGRAM} on Telegram and Instagram", color = TextLo, fontSize = 12.sp)
                    }
                }
                Spacer(Modifier.height(14.dp))
                GradButton("Message on Telegram", GTeal, Icons.Outlined.ChatBubbleOutline) { openLink(ctx, Credits.TELEGRAM_URL) }
                Spacer(Modifier.height(10.dp))
                GlassButton(Icons.Outlined.CameraAlt, "Instagram ${Credits.TELEGRAM}") { openLink(ctx, Credits.INSTAGRAM_URL) }
            }
        }
        item {
            Glass {
                Text("Licence", color = TextHi, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
                Text(
                    "${Credits.APP} is free and open source software, ${Credits.LICENSE}. Copyright ${Credits.YEAR} ${Credits.AUTHOR}. " +
                        "You may use, study, change and share it. Any copy or modified version must stay open source under the same licence " +
                        "and must keep this author credit and the Telegram id ${Credits.TELEGRAM}. Read the LICENSE and NOTICE files in the source.",
                    color = TextLo, fontSize = 12.sp, modifier = Modifier.padding(top = 8.dp)
                )
            }
        }
        item {
            Glass {
                Text("Open source libraries", color = TextHi, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
                Spacer(Modifier.height(6.dp))
                LicenseRow("Jetpack Compose and AndroidX", "Apache 2.0")
                LicenseRow("Material icons", "Apache 2.0")
                LicenseRow("Kotlin and kotlinx.coroutines", "Apache 2.0")
                LicenseRow("libsu by topjohnwu", "Apache 2.0")
            }
        }
        item {
            Glass {
                Text("Help and bug reports", color = TextHi, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
                Text(
                    "Copy this and send it with your report. It has no personal data.",
                    color = TextLo, fontSize = 12.sp, modifier = Modifier.padding(vertical = 8.dp)
                )
                GlassButton(Icons.Outlined.ContentCopy, "Copy debug info") {
                    scope.launch {
                        val root = withContext(Dispatchers.IO) { Root.isGranted() }
                        copy(CrashLog.debugInfo(ctx, root), "Debug info copied")
                    }
                }
                val c = crash
                if (c != null) {
                    Spacer(Modifier.height(10.dp))
                    GlassButton(Icons.Outlined.BugReport, "Copy last crash") { copy(c, "Crash log copied") }
                    Spacer(Modifier.height(10.dp))
                    GlassButton(Icons.Outlined.Delete, "Clear crash log") { CrashLog.clear(ctx); crash = null }
                }
            }
        }
        if (taps >= 7) item {
            Glass {
                Text("Hidden button", color = TextHi, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
                Text("You found it. Send feedback or ideas straight to the developer.", color = TextLo, fontSize = 12.sp, modifier = Modifier.padding(vertical = 8.dp))
                GradButton("Say hi to ${Credits.AUTHOR.substringBefore(' ')}", GPink) { openLink(ctx, Credits.TELEGRAM_URL) }
            }
        }
        item { Glass { Hint("${Credits.APP} covers CPU, memory, battery, charging, thermal, app control, lock, hide, network, games, calls and more, all with root.") } }
        item { CreditFooter() }
    }
}
