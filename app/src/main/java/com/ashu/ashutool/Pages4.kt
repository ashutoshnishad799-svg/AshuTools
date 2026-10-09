@file:OptIn(ExperimentalLayoutApi::class)

package com.ashu.ashutool

import android.content.Intent
import android.provider.Settings
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
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

// ----------------------------------------------------------------- Charging

@Composable
fun ChargingPage() {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    val s by Monitor.snap.collectAsState()
    var caps by remember { mutableStateOf<Charge.Caps?>(null) }
    var enabled by remember { mutableStateOf<Boolean?>(null) }
    var cur by remember { mutableStateOf<Int?>(null) }
    var limOn by remember { mutableStateOf(LockStore.bool(ctx, "cl_on", false)) }
    var lim by remember { mutableFloatStateOf(LockStore.int(ctx, "cl_limit", 80).toFloat()) }
    var manual by remember { mutableStateOf(LockStore.bool(ctx, "cl_manual", false)) }
    var tempOn by remember { mutableStateOf(LockStore.bool(ctx, "cl_temp_on", false)) }
    var tmax by remember { mutableFloatStateOf(LockStore.int(ctx, "cl_temp", 42).toFloat()) }
    LaunchedEffect(Unit) {
        while (true) {
            caps = withContext(Dispatchers.IO) { Charge.caps ?: Charge.detect() }
            enabled = withContext(Dispatchers.IO) { Charge.isEnabled() }
            cur = withContext(Dispatchers.IO) { Charge.readCurrent() }
            delay(3000)
        }
    }
    fun enforceNow() { scope.launch(Dispatchers.IO) { Charge.enforce(ctx, Monitor.snap.value) } }
    val b = s.batt
    val c = caps
    val canToggle = c?.toggle != null
    val canLimit = canToggle || c?.limit != null

    Page("Charging", "Limit, pause and current control", GGreen, Icons.Outlined.Power) {
        item {
            Glass {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Gauge(
                        b.level / 100f, "${b.level}%", b.status,
                        if (b.charging) Icons.Outlined.BatteryChargingFull else Icons.Outlined.BatteryFull, GGreen, dia = 112.dp
                    )
                    Spacer(Modifier.width(18.dp))
                    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text(
                            when (enabled) { true -> "Charging allowed"; false -> "Charging paused"; null -> "Control not found" },
                            color = TextHi, fontSize = 17.sp, fontWeight = FontWeight.SemiBold
                        )
                        Text("${b.ma} mA   ${"%.1f".format(b.tempC)}\u00B0C", color = TextLo, fontSize = 13.sp)
                        Text("Source: ${b.plugged}", color = TextLo, fontSize = 12.sp)
                    }
                }
            }
        }
        if (c == null) item { Hint("Looking for charging controls") }
        else if (!canLimit && c.current == null) item {
            Glass {
                Text("No charging control found", color = TextHi, fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
                Spacer(Modifier.height(6.dp))
                Text(
                    "This kernel does not expose a known charging switch, so limit and pause stay off. " +
                        "Nothing was changed. Many Qualcomm, Pixel, Motorola, Samsung and Oplus kernels are supported.",
                    color = TextLo, fontSize = 12.sp
                )
            }
        }
        if (canLimit) {
            item {
                SwitchRow(
                    Icons.Outlined.BatteryStd, GGreen, "Charge limit",
                    if (c?.limit != null) "Hardware limit at ${lim.toInt()}%" else "Stops at ${lim.toInt()}%, resumes at ${lim.toInt() - 3}%", limOn
                ) {
                    limOn = it
                    LockStore.setBool(ctx, "cl_on", it)
                    enforceNow()
                }
            }
            item {
                Glass {
                    SectionTitle("Limit", "${lim.toInt()}%")
                    Slider(
                        value = lim, onValueChange = { lim = it }, valueRange = 50f..100f,
                        onValueChangeFinished = { LockStore.setInt(ctx, "cl_limit", lim.toInt()); enforceNow() },
                        colors = SliderDefaults.colors(thumbColor = Accent, activeTrackColor = Accent, inactiveTrackColor = Tint.copy(alpha = 0.15f))
                    )
                    Text("80% is a good everyday limit for battery life.", color = TextLo, fontSize = 12.sp)
                }
            }
        }
        if (canToggle) {
            item {
                SwitchRow(Icons.Outlined.Pause, GFire, "Pause charging now", "Run from the charger without charging the battery", manual) {
                    manual = it
                    LockStore.setBool(ctx, "cl_manual", it)
                    enforceNow()
                }
            }
            item {
                SwitchRow(Icons.Outlined.Thermostat, GRed, "Temperature guard", "Pause charging when the battery gets hot (${tmax.toInt()}\u00B0C)", tempOn) {
                    tempOn = it
                    LockStore.setBool(ctx, "cl_temp_on", it)
                    enforceNow()
                }
            }
            if (tempOn) item {
                Glass {
                    SectionTitle("Pause above", "${tmax.toInt()}\u00B0C")
                    Slider(
                        value = tmax, onValueChange = { tmax = it }, valueRange = 35f..50f,
                        onValueChangeFinished = { LockStore.setInt(ctx, "cl_temp", tmax.toInt()); enforceNow() },
                        colors = SliderDefaults.colors(thumbColor = Accent, activeTrackColor = Accent, inactiveTrackColor = Tint.copy(alpha = 0.15f))
                    )
                }
            }
        }
        if (c?.current != null) {
            item {
                Glass {
                    SectionTitle("Charging current", cur?.let { "${it / 1000} mA" } ?: "")
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        listOf(500, 1000, 1500, 2000, 3000).forEach { ma ->
                            Chip("$ma mA", cur != null && abs(cur!! / 1000 - ma) < 60, GGreen) {
                                scope.launch {
                                    val ok = withContext(Dispatchers.IO) { Charge.setCurrentUa(ctx, ma * 1000) }
                                    toast(ctx, if (ok) "Current limited to $ma mA" else "Could not change current")
                                }
                            }
                        }
                        Chip("Default", false, GGreen) {
                            scope.launch {
                                val ok = withContext(Dispatchers.IO) { Charge.restoreCurrent(ctx) }
                                toast(ctx, if (ok) "Default current restored" else "No saved default yet")
                            }
                        }
                    }
                    Text("Lower current charges slower and runs cooler. Some chargers ignore this.", color = TextLo, fontSize = 12.sp, modifier = Modifier.padding(top = 10.dp))
                }
            }
        }
        item {
            Glass {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Outlined.Shield, null, tint = Accent, modifier = Modifier.size(20.dp))
                    Spacer(Modifier.width(10.dp))
                    Text("Safety", color = TextHi, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
                }
                Text(
                    "Charging always switches back on when you unplug, when the service stops, and after a restart. " +
                        "Only fixed kernel paths are written, and current is kept between 300 and 6000 mA.",
                    color = TextLo, fontSize = 12.sp, modifier = Modifier.padding(top = 8.dp)
                )
                if (c != null) {
                    Text(
                        "Switch: ${c.toggle?.first?.substringAfterLast('/') ?: "none"}   Current: ${c.current?.substringAfterLast('/') ?: "none"}   Limit: ${c.limit?.substringAfterLast('/') ?: "none"}",
                        color = TextLo, fontSize = 11.sp, modifier = Modifier.padding(top = 8.dp)
                    )
                }
            }
        }
    }
}

private fun abs(v: Int) = if (v < 0) -v else v

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
fun GamesPage() {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    val apps by rememberApps()
    var games by remember { mutableStateOf(GameMode.cached) }
    var pick by remember { mutableStateOf(false) }
    var govs by remember { mutableStateOf(emptyList<String>()) }
    var gov by remember { mutableStateOf(LockStore.str(ctx, "g_gov", "performance")) }
    var side by remember { mutableIntStateOf(LockStore.int(ctx, "g_side", 1)) }
    LaunchedEffect(Unit) {
        games = withContext(Dispatchers.IO) { GameMode.games(ctx) }
        govs = withContext(Dispatchers.IO) { Sys.policies().firstOrNull()?.govs ?: emptyList() }
    }
    val list = remember(apps, games) { (apps ?: emptyList()).filter { it.pkg in games } }

    Page("Game launcher", "Boost, sidebar and quick launch", GTeal, Icons.Outlined.SportsEsports) {
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
        item { PrefSwitch("g_sidebar", true, Icons.Outlined.Widgets, GTeal, "Game sidebar", "Edge handle with stats and quick tools while you play") }
        item { PrefSwitch("g_auto", true, Icons.Outlined.PlayCircle, GBlue, "Auto game mode", "Apply the settings below when a game opens") }
        item { PrefSwitch("g_perf", true, Icons.Outlined.Speed, GFire, "Performance governor", "Switch CPU governor while playing, restored after") }
        item { PrefSwitch("g_dnd", true, Icons.Outlined.NotificationsOff, GViolet, "Do not disturb", "Silence calls and messages during the game") }
        item { PrefSwitch("g_headsup", true, Icons.Outlined.NotificationsPaused, GPink, "Block heads-up popups", "No banners over the game") }
        item { PrefSwitch("g_kill", false, Icons.Outlined.CleaningServices, GCyan, "Stop background apps", "Frees RAM before the game starts") }
        item {
            Glass {
                SectionTitle("Sidebar side")
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Chip("Left", side == 0) { side = 0; LockStore.setInt(ctx, "g_side", 0) }
                    Chip("Right", side == 1) { side = 1; LockStore.setInt(ctx, "g_side", 1) }
                }
                if (govs.isNotEmpty()) {
                    SubLabel("Governor while playing")
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        govs.forEach { g -> Chip(g, g == gov, GFire) { gov = g; LockStore.setStr(ctx, "g_gov", g) } }
                    }
                }
            }
        }
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

// ----------------------------------------------------------------- Settings

@Composable
private fun ThemeSwatch(p: Pal, selected: Boolean, onClick: () -> Unit) {
    val shape = RoundedCornerShape(18.dp)
    val bg = if (p.flat) Brush.verticalGradient(listOf(p.bg1, p.bg1)) else Brush.verticalGradient(listOf(p.bg1, p.bg2, p.bg3))
    Column(Modifier.bounceClick(onClick = onClick).width(92.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        Box(
            Modifier.size(width = 92.dp, height = 112.dp).clip(shape).background(bg)
                .border(
                    if (selected) 2.dp else 1.dp,
                    if (selected) Brush.linearGradient(listOf(p.primary.a, p.primary.b)) else SolidColor(Line),
                    shape
                )
        ) {
            if (!p.flat) Canvas(Modifier.fillMaxSize()) {
                val w = size.width
                val h = size.height
                fun orb(col: Color, cx: Float, cy: Float, r: Float) {
                    drawCircle(Brush.radialGradient(listOf(col.copy(alpha = p.orbA), Color.Transparent), center = Offset(cx, cy), radius = r), radius = r, center = Offset(cx, cy))
                }
                orb(p.orb1, w * 0.2f, h * 0.15f, w * 0.8f)
                orb(p.orb2, w * 0.95f, h * 0.5f, w * 0.7f)
                orb(p.orb3, w * 0.2f, h * 0.95f, w * 0.8f)
            }
            Box(Modifier.align(Alignment.TopEnd).padding(8.dp).size(14.dp).clip(CircleShape).background(p.primary.h()))
            Column(Modifier.align(Alignment.BottomStart).padding(8.dp), verticalArrangement = Arrangement.spacedBy(5.dp)) {
                Box(Modifier.fillMaxWidth().height(20.dp).clip(RoundedCornerShape(8.dp)).background(p.tint.copy(alpha = 0.16f)))
                Box(Modifier.fillMaxWidth(0.6f).height(8.dp).clip(RoundedCornerShape(4.dp)).background(p.tint.copy(alpha = 0.28f)))
            }
        }
        Spacer(Modifier.height(6.dp))
        Text(p.name, color = if (selected) Accent else TextLo, fontSize = 12.sp, fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal)
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
fun SettingsPage() {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    var anim by remember { mutableStateOf(Palette.animated) }
    var root by remember { mutableStateOf(false) }
    var usage by remember { mutableStateOf(Usage.hasAccess(ctx)) }
    var overlay by remember { mutableStateOf(Settings.canDrawOverlays(ctx)) }
    var log by remember { mutableStateOf(Root.recent()) }
    val a11y = AshuAccessibility.connected
    LaunchedEffect(Unit) {
        root = withContext(Dispatchers.IO) { Root.isGranted() }
        while (true) {
            log = Root.recent()
            usage = Usage.hasAccess(ctx)
            overlay = Settings.canDrawOverlays(ctx)
            delay(2000)
        }
    }

    Page("Settings", "Themes, permissions and safety", GCyan, Icons.Outlined.Settings) {
        item {
            Glass {
                SectionTitle("Theme", Palette.cur.name)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Palette.themes.forEachIndexed { i, p ->
                        ThemeSwatch(p, Palette.mode == i) {
                            Palette.mode = i
                            LockStore.setInt(ctx, "theme", i)
                        }
                    }
                }
            }
        }
        item {
            SwitchRow(Icons.Outlined.AutoAwesome, GViolet, "Animated background", "Slow drifting glow on gradient themes", anim) {
                anim = it
                Palette.animated = it
                LockStore.setBool(ctx, "anim_bg", it)
            }
        }
        item { PrefSwitch("boot", true, Icons.Outlined.PowerSettingsNew, GPink, "Start on boot", "Keeps lock, vault and sidebar active after a restart") }
        item {
            Glass {
                SectionTitle("Permissions")
                StatusRow("Root access", root)
                StatusRow("Instant app lock (accessibility)", a11y)
                StatusRow("Usage access", usage)
                StatusRow("Draw over other apps", overlay)
                Spacer(Modifier.height(8.dp))
                GradButton("Fix with root", GCyan, Icons.Outlined.Build) {
                    scope.launch {
                        withContext(Dispatchers.IO) { Root.bootstrap(ctx) }
                        toast(ctx, "Permissions refreshed")
                    }
                }
            }
        }
        item {
            Glass {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    GradIcon(Icons.Outlined.Shield, if (Guard.safe) GRed else GGreen, 40.dp)
                    Spacer(Modifier.width(12.dp))
                    Column(Modifier.weight(1f)) {
                        Text(if (Guard.safe) "Safe mode is on" else "Safety guards are on", color = TextHi, fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
                        Text(
                            if (Guard.safe) "The service restarted several times. Charge limit, firewall and game mode are paused."
                            else "Launcher, keyboard, dialer, system apps and root managers cannot be hidden, removed or blocked.",
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
        item {
            Glass {
                SectionTitle("Action log", "last ${log.size}")
                if (log.isEmpty()) Hint("Nothing has been changed yet.")
                log.take(14).forEach {
                    Text(it, color = TextLo, fontSize = 10.sp, fontFamily = FontFamily.Monospace, maxLines = 2, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(vertical = 2.dp))
                }
                Text("Every change Ashutool makes as root is listed here. Reads are not logged.", color = TextLo, fontSize = 11.sp, modifier = Modifier.padding(top = 8.dp))
            }
        }
        item {
            Glass {
                Text("Ashutool 1.0", color = TextHi, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
                Text(
                    "Never remounts or writes to the system partition. If you forget the lock PIN, clear the app data of Ashutool with root.",
                    color = TextLo, fontSize = 12.sp, modifier = Modifier.padding(top = 4.dp)
                )
            }
        }
    }
}
