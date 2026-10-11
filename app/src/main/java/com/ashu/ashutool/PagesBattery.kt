@file:OptIn(ExperimentalLayoutApi::class)

package com.ashu.ashutool

import androidx.compose.foundation.layout.*
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

@Composable
fun BatteryPage() = SubHost(
    root = { open -> BatteryMain(open) },
    sub = { k, back ->
        when (k) {
            "drain" -> DrainSub(back)
            "apps" -> AppsUseSub(back)
            "bg" -> BackgroundSub(back)
            "charge" -> ChargingSub(back)
            "saver" -> SaverSub(back)
            else -> AccuracySub(back)
        }
    }
)

@Composable
private fun BatteryMain(open: (String) -> Unit) {
    val s by Monitor.snap.collectAsState()
    val b = s.batt
    val watts = b.mv / 1000f * b.ma / 1000f
    Page("Battery", "Health, drain, background apps and charging", GGreen, Icons.Outlined.BatteryChargingFull) {
        item {
            Glass {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Gauge(
                        b.level / 100f, "${b.level}%", b.status,
                        if (b.charging) Icons.Outlined.BatteryChargingFull else Icons.Outlined.BatteryFull,
                        if (b.level <= 15 && !b.charging) GRed else GGreen, dia = 128.dp
                    )
                    Spacer(Modifier.width(20.dp))
                    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text("${b.ma} mA", color = TextHi, fontSize = 28.sp, fontWeight = FontWeight.Bold)
                        Text("${"%.1f".format(watts)} W", color = TextLo, fontSize = 14.sp)
                        Text(if (b.charging) "Charging current" else "Discharge current", color = TextLo, fontSize = 12.sp)
                    }
                }
            }
        }
        item {
            TileGrid(
                listOf(
                    Tile(Icons.Outlined.Thermostat, "Temperature", "${"%.1f".format(b.tempC)}\u00B0C", tempGrad(b.tempC)),
                    Tile(Icons.Outlined.HealthAndSafety, "Health", b.health, GGreen),
                    Tile(Icons.Outlined.Bolt, "Voltage", "${"%.2f".format(b.mv / 1000f)} V", GBlue),
                    Tile(Icons.Outlined.Power, "Source", b.plugged, GCyan)
                )
            )
        }
        item { NavRow(Icons.Outlined.BatteryAlert, GFire, "Drain and history", "Rate per hour, last 24 hours, charts") { open("drain") } }
        item { NavRow(Icons.Outlined.QueryStats, GBlue, "Use by app", "Which apps used the battery") { open("apps") } }
        item { NavRow(Icons.Outlined.Layers, GPink, "Background apps", "Stop or restrict apps, kill on screen off") { open("bg") } }
        item { NavRow(Icons.Outlined.Power, GGreen, "Charging control", "Limit, pause, current, temperature guard") { open("charge") } }
        item { NavRow(Icons.Outlined.Bedtime, GViolet, "Battery saver", "Doze, screen-off CPU limit, auto saver") { open("saver") } }
        item { NavRow(Icons.Outlined.Science, GCyan, "Accuracy", "Capacity estimate, reset stats, calibration") { open("accuracy") } }
    }
}

@Composable
private fun DrainSub(back: () -> Unit) {
    val s by Monitor.snap.collectAsState()
    val b = s.batt
    val series = remember(s) { BatteryLog.series() }
    val rate = s.ratePh
    Page("Drain and history", "Last 24 hours", GFire, Icons.Outlined.BatteryAlert, onBack = back) {
        item {
            TileGrid(
                listOf(
                    Tile(Icons.Outlined.BatteryAlert, if (b.charging) "Charge rate" else "Drain rate", rate?.let { "${if (it > 0) "+" else ""}${"%.1f".format(it)} %/h" } ?: "Measuring", if (b.charging) GGreen else GFire),
                    Tile(Icons.Outlined.Schedule, "Used in 24 h", "${"%.0f".format(s.drain24)}%", GBlue),
                    Tile(Icons.Outlined.Timelapse, "24 h average", "${"%.1f".format(s.avg24)} %/h", GViolet),
                    Tile(Icons.Outlined.Bedtime, "Deep sleep", "${s.deepSleep}%", GCyan)
                )
            )
        }
        item { Glass { SectionTitle("Battery level", "last 24 hours"); LineChart(series, GGreen, maxV = 100f) } }
        item { Glass { SectionTitle("Current draw", "mA"); LineChart(s.currHist, GBlue) } }
        item { Glass { SectionTitle("Battery temperature", "\u00B0C"); LineChart(s.tempHist, GFire) } }
    }
}

@Composable
private fun AppsUseSub(back: () -> Unit) {
    val ctx = LocalContext.current
    var power by remember { mutableStateOf<List<Triple<String, String, Float>>?>(null) }
    LaunchedEffect(Unit) { power = withContext(Dispatchers.IO) { Sys.appPower(ctx) } }
    Page("Use by app", "Since the last full charge", GBlue, Icons.Outlined.QueryStats, onBack = back) {
        val list = power
        if (list == null) item { Hint("Reading battery stats") }
        else if (list.isEmpty()) item { Glass { Hint("This ROM does not report per-app battery use.") } }
        else {
            val top = list.first().third.coerceAtLeast(0.1f)
            items(list, key = { it.first }) { (pkg, name, mah) ->
                Glass(Modifier.animateItem(), radius = 18.dp, pad = 12.dp) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        AppIcon(pkg, 38.dp)
                        Spacer(Modifier.width(12.dp))
                        Column(Modifier.weight(1f)) {
                            Row {
                                Text(name, color = TextHi, fontSize = 14.sp, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
                                Text("${"%.0f".format(mah)} mAh", color = TextLo, fontSize = 13.sp)
                            }
                            Spacer(Modifier.height(6.dp))
                            Bar(mah / top, GGreen)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun BackgroundSub(back: () -> Unit) {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    val apps by rememberApps()
    var running by remember { mutableStateOf<List<Triple<String, String, Int>>?>(null) }
    var flags by remember { mutableStateOf(BgControl.flags(ctx)) }
    var query by remember { mutableStateOf("") }
    WhileVisible {
        while (true) {
            running = withContext(Dispatchers.IO) { Sys.runningApps(ctx) }
            delay(5000)
        }
    }
    val shown = remember(apps, query) {
        (apps ?: emptyList()).filter { !it.system && !it.guarded && (query.isBlank() || it.name.contains(query, true)) }
    }
    fun toggle(pkg: String, bit: Int) {
        val cur = flags[pkg] ?: 0
        val next = if ((cur and bit) != 0) cur and bit.inv() else cur or bit
        scope.launch {
            withContext(Dispatchers.IO) { BgControl.setFlags(ctx, pkg, next) }
            flags = BgControl.flags(ctx)
        }
    }
    Page("Background apps", "Stop, restrict and auto-kill", GPink, Icons.Outlined.Layers, onBack = back) {
        item { PrefSwitch("bg_all_off", false, Icons.Outlined.ScreenLockPortrait, GPink, "Stop all background apps on screen off", "Skips apps marked Keep. Launcher, keyboard and dialer are never touched") }
        item {
            ActionRow(Icons.Outlined.CleaningServices, GCyan, "Stop all now", "Stops every downloaded app except the one in front", {
                scope.launch {
                    val n = withContext(Dispatchers.IO) { Sys.killBackground(ctx) }
                    toast(ctx, "Stopped $n apps")
                    running = withContext(Dispatchers.IO) { Sys.runningApps(ctx) }
                }
            })
        }
        item { SectionTitle("Running now", "${running?.size ?: 0} apps") }
        val r = running
        if (r == null) item { Hint("Reading running apps") }
        else if (r.isEmpty()) item { Hint("Nothing downloaded is running.") }
        else items(r.take(12), key = { "r_" + it.first }) { t ->
            Glass(Modifier.animateItem(), radius = 18.dp, pad = 12.dp) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    AppIcon(t.first, 36.dp)
                    Spacer(Modifier.width(12.dp))
                    Text(t.second, color = TextHi, fontSize = 14.sp, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
                    Text("${t.third} MB", color = TextLo, fontSize = 12.sp)
                    Spacer(Modifier.width(10.dp))
                    GlassButton(Icons.Outlined.Stop, "Stop", Warn) {
                        scope.launch {
                            withContext(Dispatchers.IO) { Apps.forceStop(ctx, t.first) }
                            running = withContext(Dispatchers.IO) { Sys.runningApps(ctx) }
                        }
                    }
                }
            }
        }
        item { SectionTitle("Rules per app") }
        item { SearchField(query) { query = it } }
        items(shown.take(60), key = { "a_" + it.pkg }) { app ->
            val f = flags[app.pkg] ?: 0
            Glass(Modifier.animateItem(), radius = 18.dp, pad = 0.dp) {
                AppHead(app)
                FlowRow(Modifier.padding(start = 12.dp, end = 12.dp, bottom = 12.dp), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Chip("Restrict", (f and 1) != 0, GPink) { toggle(app.pkg, 1) }
                    Chip("Stop when I leave", (f and 2) != 0, GPink) { toggle(app.pkg, 2) }
                    Chip("Stop on screen off", (f and 4) != 0, GPink) { toggle(app.pkg, 4) }
                    Chip("Keep", (f and 8) != 0, GGreen) { toggle(app.pkg, 8) }
                }
            }
        }
    }
}

@Composable
private fun SaverSub(back: () -> Unit) {
    val ctx = LocalContext.current
    var min by remember { mutableIntStateOf(LockStore.int(ctx, "bs_doze_min", 5)) }
    var th by remember { mutableFloatStateOf(LockStore.int(ctx, "bs_th", 20).toFloat()) }
    Page("Battery saver", "Optional, all reversible", GViolet, Icons.Outlined.Bedtime, onBack = back) {
        item { PrefSwitch("bs_doze", false, Icons.Outlined.Bedtime, GViolet, "Deep Doze after screen off", "Forces idle a few minutes after the screen turns off, while unplugged") }
        item {
            Glass {
                SectionTitle("Doze after", "$min min")
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf(2, 5, 10, 15).forEach { m -> Chip("$m min", min == m, GViolet) { min = m; LockStore.setInt(ctx, "bs_doze_min", m) } }
                }
            }
        }
        item { PrefSwitch("bs_cpu", false, Icons.Outlined.Memory, GBlue, "Limit CPU when screen is off", "Powersave governor and half speed, restored the moment the screen turns on") }
        item { PrefSwitch("bs_auto", false, Icons.Outlined.BatteryStd, GGreen, "Automatic battery saver", "Turns the system saver on at a chosen level and off after charging") }
        item {
            Glass {
                SectionTitle("Turn on at", "${th.toInt()}%")
                Slider(
                    value = th, onValueChange = { th = it }, valueRange = 5f..60f,
                    onValueChangeFinished = { LockStore.setInt(ctx, "bs_th", th.toInt()) },
                    colors = SliderDefaults.colors(thumbColor = Accent, activeTrackColor = Accent, inactiveTrackColor = Tint.copy(alpha = 0.15f))
                )
            }
        }
        item {
            Glass {
                Text("Ashutool saves battery too", color = TextHi, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
                Text(
                    "With the screen off it does not run any timer and reads the battery once a minute. " +
                        "With the app closed it samples every few seconds, and nothing polls in the background.",
                    color = TextLo, fontSize = 12.sp, modifier = Modifier.padding(top = 6.dp)
                )
            }
        }
    }
}

@Composable
private fun AccuracySub(back: () -> Unit) {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    val s by Monitor.snap.collectAsState()
    val b = s.batt
    Page("Accuracy", "Make the numbers trustworthy", GCyan, Icons.Outlined.Science, onBack = back) {
        item {
            TileGrid(
                listOf(
                    Tile(Icons.Outlined.BatteryStd, "Estimated capacity", if (b.estMah > 0) "${b.estMah} mAh" else "Measuring", GGreen),
                    Tile(Icons.Outlined.HealthAndSafety, "Design capacity", if (b.designMah > 0) "${b.designMah} mAh" else "Not exposed", GBlue),
                    Tile(Icons.Outlined.BatteryStd, "Capacity health", if (b.capHealth >= 0) "${b.capHealth}%" else "Not exposed", GTeal),
                    Tile(Icons.Outlined.Autorenew, "Charge cycles", if (b.cycles >= 0) "${b.cycles}" else "Not exposed", GViolet)
                )
            )
        }
        item {
            ActionRow(Icons.Outlined.Restore, GFire, "Reset battery stats", "Clears the system's per-app battery history, so new numbers start clean", {
                scope.launch {
                    val ok = withContext(Dispatchers.IO) { Root.ok("dumpsys batterystats --reset") }
                    toast(ctx, if (ok) "Battery stats reset" else "Could not reset")
                }
            })
        }
        item {
            ActionRow(Icons.Outlined.DeleteSweep, GPink, "Reset Ashutool drain history", "Starts the 24 hour chart and averages again", {
                BatteryLog.reset()
                toast(ctx, "History cleared")
            })
        }
        item {
            Glass {
                Text("For the best accuracy", color = TextHi, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
                Text(
                    "1. Reset battery stats, then charge to 100% and unplug.\n" +
                        "2. Use the phone normally until about 15%, then charge once without interruptions.\n" +
                        "3. The estimated capacity is most accurate when the level is between 20% and 95%.\n" +
                        "Current readings are averaged over a few samples so they do not jump around.",
                    color = TextLo, fontSize = 12.sp, modifier = Modifier.padding(top = 6.dp)
                )
            }
        }
    }
}

// ----------------------------------------------------------------- Charging

@Composable
fun ChargingSub(back: () -> Unit) {
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
    WhileVisible {
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

    Page("Charging", "Limit, pause and current control", GGreen, Icons.Outlined.Power, onBack = back) {
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

