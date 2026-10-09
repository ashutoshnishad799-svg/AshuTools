@file:OptIn(ExperimentalLayoutApi::class)

package com.ashu.ashutool

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.*

// --------------------------------------------------------------------- Home

@Composable
fun HomePage(root: Boolean?) {
    val s by Monitor.snap.collectAsState()
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    var boosting by remember { mutableStateOf(false) }
    val doBoost: () -> Unit = {
        if (!boosting) scope.launch {
            boosting = true
            val r = withContext(Dispatchers.IO) {
                val before = Sys.availMb(ctx)
                val n = Sys.boost(ctx)
                delay(800)
                n to (Sys.availMb(ctx) - before)
            }
            boosting = false
            toast(ctx, if (r.second > 0) "Stopped ${r.first} apps, freed ${r.second} MB" else "Stopped ${r.first} apps")
        }
    }
    val b = s.batt

    LazyColumn(
        Modifier.fillMaxSize().statusBarsPadding(),
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 10.dp, bottom = 20.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        item {
            Row(Modifier.fillMaxWidth().padding(top = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    GradText("Ashutool", GTeal, 34.sp)
                    Text("Root toolkit for your ROM", color = TextLo, fontSize = 13.sp)
                }
                RootChip(root)
            }
        }
        if (Guard.safe) item {
            Glass(Modifier.enter(0)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    GradIcon(Icons.Outlined.Shield, GRed, 40.dp)
                    Spacer(Modifier.width(12.dp))
                    Column(Modifier.weight(1f)) {
                        Text("Safe mode", color = TextHi, fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
                        Text("The service restarted repeatedly, so auto features are paused.", color = TextLo, fontSize = 12.sp)
                    }
                    GradButton("Resume", GTeal) { Guard.safe = false; LockStore.setStr(ctx, "starts", "") }
                }
            }
        }
        item {
            Glass(Modifier.enter(1)) {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
                    Gauge(s.cpu / 100f, "${s.cpu.toInt()}%", "CPU", Icons.Outlined.Memory, GTeal)
                    Gauge(s.ramPct / 100f, "${s.ramPct.toInt()}%", "RAM", Icons.Outlined.DeveloperBoard, GViolet)
                    Gauge(
                        b.level / 100f, "${b.level}%", "Battery",
                        if (b.charging) Icons.Outlined.BatteryChargingFull else Icons.Outlined.BatteryFull,
                        if (b.level <= 15 && !b.charging) GRed else GGreen
                    )
                }
                Spacer(Modifier.height(14.dp))
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
                    MiniStat("CPU temp", "${s.cpuTemp.toInt()}\u00B0C")
                    MiniStat("Battery", "${"%.1f".format(b.tempC)}\u00B0C")
                    MiniStat("Draw", "${b.ma} mA")
                    MiniStat("Rate", s.ratePh?.let { "${if (it > 0) "+" else ""}${"%.1f".format(it)}%/h" } ?: "...")
                }
            }
        }
        item {
            Glass(Modifier.enter(2)) {
                SectionTitle("CPU load", "recent")
                LineChart(s.cpuHist, GTeal, maxV = 100f)
            }
        }
        item {
            Glass(Modifier.enter(3), onClick = doBoost) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    GradIcon(Icons.Outlined.Bolt, GFire, 48.dp)
                    Spacer(Modifier.width(14.dp))
                    Column(Modifier.weight(1f)) {
                        Text(if (boosting) "Boosting" else "One-tap boost", color = TextHi, fontSize = 16.sp, fontWeight = FontWeight.Bold)
                        Text("Stops third-party apps, trims caches, frees RAM. Launcher and keyboard are skipped.", color = TextLo, fontSize = 12.sp)
                    }
                }
            }
        }
        item {
            Text(
                "Swipe the bar below to open more tools.",
                color = TextLo, fontSize = 12.sp, textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth()
            )
        }
    }
}

@Composable
private fun MiniStat(label: String, value: String) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(value, color = TextHi, fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
        Text(label, color = TextLo, fontSize = 11.sp)
    }
}

// ---------------------------------------------------------------------- CPU

@Composable
fun CpuPage() {
    val s by Monitor.snap.collectAsState()
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    var pols by remember { mutableStateOf<List<Policy>?>(null) }
    LaunchedEffect(Unit) {
        pols = withContext(Dispatchers.IO) { Sys.policies().also { Sys.saveOriginal(ctx, it) } }
    }
    val change: (String, () -> Boolean) -> Unit = { label, f ->
        scope.launch {
            val ok = withContext(Dispatchers.IO) { f() }
            toast(ctx, if (ok) "$label applied" else "$label failed")
            pols = withContext(Dispatchers.IO) { Sys.policies() }
        }
    }

    Page("CPU", "Clusters, governor and frequency", GTeal, Icons.Outlined.Memory) {
        item {
            Glass {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
                    Gauge(s.cpu / 100f, "${s.cpu.toInt()}%", "Load", Icons.Outlined.Memory, GTeal, dia = 112.dp)
                    Gauge(s.cpuTemp / 90f, "${s.cpuTemp.toInt()}\u00B0", "Temperature", Icons.Outlined.Thermostat, tempGrad(s.cpuTemp), dia = 112.dp)
                }
            }
        }
        item { Glass { SectionTitle("CPU load", "recent"); LineChart(s.cpuHist, GTeal, maxV = 100f) } }
        item {
            Glass {
                SectionTitle("Cores", "Governor ${s.gov}")
                if (s.cores.isEmpty()) Hint("Waiting for root data")
                s.cores.forEach { CoreRow(it) }
            }
        }
        val list = pols
        if (list == null) item { Hint("Reading clusters") }
        else if (list.isEmpty()) item { Glass { Hint("This kernel does not expose cpufreq policies, so cluster controls are hidden.") } }
        else {
            items(list, key = { it.id }) { PolicyCard(it, change) }
            item {
                ActionRow(Icons.Outlined.Restore, GTeal, "Restore kernel defaults", "Put governor and limits back to the values from first launch", {
                    change("Defaults") { Sys.restoreOriginal(ctx) }
                })
            }
        }
    }
}

@Composable
private fun CoreRow(c: Core) {
    Row(Modifier.fillMaxWidth().padding(vertical = 5.dp), verticalAlignment = Alignment.CenterVertically) {
        Text("CPU${c.id}", color = TextLo, fontSize = 12.sp, modifier = Modifier.width(48.dp))
        Box(Modifier.weight(1f)) { Bar(if (c.maxKhz > 0) c.khz.toFloat() / c.maxKhz else 0f, if (c.online) GTeal else GBlue) }
        Text(
            if (c.online && c.khz > 0) "${c.khz / 1000} MHz" else "Offline",
            color = if (c.online) TextHi else TextLo, fontSize = 12.sp,
            textAlign = TextAlign.End, modifier = Modifier.width(78.dp)
        )
    }
}

@Composable
private fun PolicyCard(p: Policy, change: (String, () -> Boolean) -> Unit) {
    Glass {
        Row(verticalAlignment = Alignment.CenterVertically) {
            GradIcon(Icons.Outlined.Tune, GViolet, 40.dp)
            Spacer(Modifier.width(12.dp))
            Column {
                Text("Cluster ${p.id}", color = TextHi, fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
                Text("CPU ${p.cpus}   now ${p.minF / 1000} to ${p.maxF / 1000} MHz", color = TextLo, fontSize = 12.sp)
            }
        }
        if (p.govs.isNotEmpty()) {
            SubLabel("Governor")
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                p.govs.forEach { g -> Chip(g, g == p.gov, GViolet) { change("Governor $g") { Sys.setGov(p, g) } } }
            }
        }
        if (p.freqs.isNotEmpty()) {
            SubLabel("Max frequency, MHz")
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                p.freqs.forEach { f -> Chip("${f / 1000}", f == p.maxF, GTeal) { change("Max ${f / 1000} MHz") { Sys.setMax(p, f) } } }
            }
            SubLabel("Min frequency, MHz")
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                p.freqs.forEach { f -> Chip("${f / 1000}", f == p.minF, GBlue) { change("Min ${f / 1000} MHz") { Sys.setMin(p, f) } } }
            }
        }
    }
}

// ------------------------------------------------------------------- Memory

@Composable
fun MemoryPage() {
    val s by Monitor.snap.collectAsState()
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    var m by remember { mutableStateOf<Mem?>(null) }
    var sw by remember { mutableFloatStateOf(-1f) }
    var swaps by remember { mutableStateOf<List<String>>(emptyList()) }
    var confirmOff by remember { mutableStateOf(false) }
    var info by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) {
        while (true) {
            val r = withContext(Dispatchers.IO) { Sys.mem() }
            m = r
            if (sw < 0f) sw = r.swappiness.toFloat()
            val sp = withContext(Dispatchers.IO) { Sys.swaps() }
            swaps = sp
            if (sp.isNotEmpty()) LockStore.setStr(ctx, "swap_devs", sp.joinToString(","))
            delay(2000)
        }
    }
    fun applySwappiness(v: Int) {
        scope.launch {
            val ok = withContext(Dispatchers.IO) { Sys.setSwappiness(v) }
            toast(ctx, if (ok) "Swappiness set to $v" else "Could not change swappiness")
        }
    }

    Page("Memory", "RAM, swap and cache", GViolet, Icons.Outlined.DeveloperBoard) {
        item {
            Glass {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Gauge(s.ramPct / 100f, "${s.ramPct.toInt()}%", "RAM used", Icons.Outlined.DeveloperBoard, GViolet, dia = 120.dp)
                    Spacer(Modifier.width(20.dp))
                    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text("${s.ramUsedMb} MB", color = TextHi, fontSize = 26.sp, fontWeight = FontWeight.Bold)
                        Text("of ${s.ramTotalMb} MB", color = TextLo, fontSize = 13.sp)
                    }
                }
            }
        }
        item { Glass { SectionTitle("Memory use", "recent"); LineChart(s.ramHist, GViolet, maxV = 100f) } }
        m?.let { mm ->
            item {
                TileGrid(
                    listOf(
                        Tile(Icons.Outlined.CheckCircle, "Available", fmtBytes(mm.avail * 1024), GGreen),
                        Tile(Icons.Outlined.Layers, "Cached", fmtBytes(mm.cached * 1024), GBlue),
                        Tile(Icons.Outlined.Dns, "Buffers", fmtBytes(mm.buffers * 1024), GCyan),
                        Tile(Icons.Outlined.Storage, "Swap total", fmtBytes(mm.swapTotal * 1024), GPink)
                    )
                )
            }
            if (mm.swapTotal > 0) {
                item {
                    Glass {
                        SectionTitle("Swap in use", "${fmtBytes((mm.swapTotal - mm.swapFree) * 1024)} of ${fmtBytes(mm.swapTotal * 1024)}")
                        Bar((mm.swapTotal - mm.swapFree).toFloat() / mm.swapTotal, GPink)
                    }
                }
            }
        }
        item {
            SwitchRow(
                Icons.Outlined.Storage, GPink, "Swap (zRAM)",
                if (swaps.isEmpty()) "Off" else swaps.joinToString(), swaps.isNotEmpty()
            ) { on ->
                if (on) scope.launch {
                    val devs = LockStore.str(ctx, "swap_devs", "/dev/block/zram0").split(",").filter { it.isNotBlank() }
                    val ok = withContext(Dispatchers.IO) { Sys.swapOn(devs) }
                    toast(ctx, if (ok) "Swap is on" else "Could not turn swap on")
                } else confirmOff = true
            }
        }
        item {
            Glass {
                SectionTitle("Swappiness", if (sw >= 0) "${sw.toInt()}" else "")
                Text("Lower keeps apps in RAM longer. Higher moves idle pages to swap sooner. Android default is 60.", color = TextLo, fontSize = 12.sp)
                Slider(
                    value = sw.coerceAtLeast(0f), onValueChange = { sw = it }, valueRange = 0f..100f,
                    colors = SliderDefaults.colors(thumbColor = Accent, activeTrackColor = Accent, inactiveTrackColor = Tint.copy(alpha = 0.15f))
                )
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
                    GradButton("Apply", GViolet, Icons.Outlined.Check) { applySwappiness(sw.toInt()) }
                    GlassButton(Icons.Outlined.Refresh, "Reset to 60") { sw = 60f; applySwappiness(60) }
                }
            }
        }
        item {
            Glass(onClick = { info = !info }) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Outlined.Info, null, tint = Accent, modifier = Modifier.size(20.dp))
                    Spacer(Modifier.width(10.dp))
                    Text("What is swappiness?", color = TextHi, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
                }
                if (info) Text(
                    "When RAM fills up, Android compresses idle app memory into zRAM, a compressed swap area. " +
                        "Swappiness (0 to 100) tells the kernel how eager to do that. At 0 it avoids swap, at 100 it uses it aggressively. " +
                        "A value between 30 and 60 is normal. The setting is safe and goes back to default after a reboot.",
                    color = TextLo, fontSize = 12.sp, modifier = Modifier.padding(top = 10.dp)
                )
            }
        }
        item {
            ActionRow(Icons.Outlined.CleaningServices, GCyan, "Free RAM cache", "Drop page cache, dentries and inodes", {
                scope.launch {
                    val ok = withContext(Dispatchers.IO) { Sys.dropCaches() }
                    toast(ctx, if (ok) "RAM cache cleared" else "Could not clear cache")
                }
            })
        }
    }

    if (confirmOff) ConfirmDialog(
        "Turn swap off", "Apps may close or the device may freeze if memory runs out. Continue?", "Turn off", true,
        onConfirm = {
            confirmOff = false
            val devs = swaps
            scope.launch {
                val ok = withContext(Dispatchers.IO) { Sys.swapOff(devs) }
                toast(ctx, if (ok) "Swap is off" else "Could not turn swap off")
            }
        },
        onDismiss = { confirmOff = false }
    )
}

// ------------------------------------------------------------------ Battery

@Composable
fun BatteryPage() {
    val s by Monitor.snap.collectAsState()
    val ctx = LocalContext.current
    val b = s.batt
    val watts = b.mv / 1000f * b.ma / 1000f
    var power by remember { mutableStateOf<List<Triple<String, String, Float>>?>(null) }
    LaunchedEffect(Unit) { power = withContext(Dispatchers.IO) { Sys.appPower(ctx) } }
    val series = remember(s) { BatteryLog.series() }
    val rate = s.ratePh

    Page("Battery", "Health, drain and charge", GGreen, Icons.Outlined.BatteryChargingFull) {
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
                    Tile(Icons.Outlined.BatteryAlert, if (b.charging) "Charge rate" else "Drain rate",
                        rate?.let { "${if (it > 0) "+" else ""}${"%.1f".format(it)} %/h" } ?: "Measuring", if (b.charging) GGreen else GFire),
                    Tile(Icons.Outlined.Schedule, "Used in 24 h", "${"%.0f".format(s.drain24)}%", GBlue),
                    Tile(Icons.Outlined.Timelapse, "24 h average", "${"%.1f".format(s.avg24)} %/h", GViolet),
                    Tile(Icons.Outlined.Thermostat, "Temperature", "${"%.1f".format(b.tempC)}\u00B0C", tempGrad(b.tempC)),
                    Tile(Icons.Outlined.Bolt, "Voltage", "${"%.2f".format(b.mv / 1000f)} V", GBlue),
                    Tile(Icons.Outlined.HealthAndSafety, "Health", b.health, GGreen),
                    Tile(Icons.Outlined.Power, "Source", b.plugged, GCyan),
                    Tile(Icons.Outlined.BatteryStd, "Capacity health", if (b.capHealth >= 0) "${b.capHealth}%" else "Not exposed", GTeal),
                    Tile(Icons.Outlined.Autorenew, "Charge cycles", if (b.cycles >= 0) "${b.cycles}" else "Not exposed", GViolet),
                    Tile(Icons.Outlined.Science, "Technology", b.tech, GPink)
                )
            )
        }
        item { Glass { SectionTitle("Battery level", "last 24 hours"); LineChart(series, GGreen, maxV = 100f) } }
        item { Glass { SectionTitle("Current draw", "mA"); LineChart(s.currHist, GBlue) } }
        item { Glass { SectionTitle("Battery temperature", "\u00B0C"); LineChart(s.tempHist, GFire) } }
        item {
            Glass {
                SectionTitle("Use by app", "since last full charge")
                val list = power
                if (list == null) Hint("Reading battery stats")
                else if (list.isEmpty()) Hint("This ROM does not report per-app battery use.")
                else {
                    val top = list.first().third.coerceAtLeast(0.1f)
                    list.forEach { (pkg, name, mah) ->
                        Row(Modifier.fillMaxWidth().padding(vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                            AppIcon(pkg, 34.dp)
                            Spacer(Modifier.width(10.dp))
                            Column(Modifier.weight(1f)) {
                                Row {
                                    Text(name, color = TextHi, fontSize = 13.sp, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
                                    Text("${"%.0f".format(mah)} mAh", color = TextLo, fontSize = 12.sp)
                                }
                                Spacer(Modifier.height(5.dp))
                                Bar(mah / top, GGreen)
                            }
                        }
                    }
                }
            }
        }
    }
}

// ------------------------------------------------------------------ Thermal

@Composable
fun ThermalPage() {
    val s by Monitor.snap.collectAsState()
    Page("Thermal", "Every sensor on the device", GFire, Icons.Outlined.Thermostat) {
        item {
            Glass {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
                    Gauge(s.cpuTemp / 90f, "${s.cpuTemp.toInt()}\u00B0", "CPU", Icons.Outlined.Memory, tempGrad(s.cpuTemp), dia = 112.dp)
                    Gauge(s.batt.tempC / 60f, "${s.batt.tempC.toInt()}\u00B0", "Battery", Icons.Outlined.BatteryStd, tempGrad(s.batt.tempC), dia = 112.dp)
                }
            }
        }
        item { Glass { SectionTitle("CPU temperature", "\u00B0C"); LineChart(s.cpuTempHist, GFire) } }
        item {
            Glass {
                SectionTitle("Sensors", "${s.zones.size} zones")
                if (s.zones.isEmpty()) Hint("No thermal zones were readable.")
                s.zones.take(40).forEach { (name, t) ->
                    Column(Modifier.padding(vertical = 6.dp)) {
                        Row {
                            Text(name, color = TextHi, fontSize = 13.sp, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
                            Text("${"%.1f".format(t)}\u00B0C", color = TextLo, fontSize = 13.sp)
                        }
                        Spacer(Modifier.height(6.dp))
                        Bar(t / 100f, tempGrad(t))
                    }
                }
            }
        }
    }
}

// -------------------------------------------------------------------- Usage

@Composable
fun UsagePage() {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    var access by remember { mutableStateOf(Usage.hasAccess(ctx)) }
    var days by remember { mutableIntStateOf(1) }
    var rows by remember { mutableStateOf<List<UsageItem>?>(null) }
    LaunchedEffect(days, access) {
        if (access) {
            rows = null
            rows = withContext(Dispatchers.IO) { Usage.query(ctx, days) }
        }
    }

    Page("App usage", "Screen time per app", GBlue, Icons.Outlined.QueryStats) {
        if (!access) {
            item {
                Glass {
                    Text("Usage access is off", color = TextHi, fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
                    Spacer(Modifier.height(6.dp))
                    Text("Ashutool can grant this permission to itself through root.", color = TextLo, fontSize = 13.sp)
                    Spacer(Modifier.height(14.dp))
                    GradButton("Grant usage access", GBlue) {
                        scope.launch {
                            withContext(Dispatchers.IO) { Root.ok("appops set ${ctx.packageName} GET_USAGE_STATS allow") }
                            access = Usage.hasAccess(ctx)
                        }
                    }
                }
            }
        } else {
            item {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Chip("Today", days == 1, GBlue) { days = 1 }
                    Chip("7 days", days == 7, GBlue) { days = 7 }
                    Chip("30 days", days == 30, GBlue) { days = 30 }
                }
            }
            val list = rows
            if (list == null) {
                item { Hint("Reading usage stats") }
            } else if (list.isEmpty()) {
                item { Hint("No app was used for more than a minute in this range.") }
            } else {
                item {
                    Glass {
                        Text("Total screen time", color = TextLo, fontSize = 12.sp)
                        GradText(fmtDuration(list.sumOf { it.ms }), GBlue, 34.sp)
                        Text("${list.size} apps used", color = TextLo, fontSize = 12.sp)
                    }
                }
                val top = list.first().ms.coerceAtLeast(1)
                items(list.take(40), key = { it.pkg }) { u ->
                    Glass(Modifier.animateItem(), radius = 18.dp, pad = 12.dp) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            AppIcon(u.pkg, 42.dp)
                            Spacer(Modifier.width(12.dp))
                            Column(Modifier.weight(1f)) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Text(u.name, color = TextHi, fontSize = 14.sp, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
                                    Text(fmtDuration(u.ms), color = TextLo, fontSize = 13.sp)
                                }
                                Spacer(Modifier.height(8.dp))
                                Bar(u.ms.toFloat() / top, GBlue)
                            }
                        }
                    }
                }
            }
        }
    }
}
