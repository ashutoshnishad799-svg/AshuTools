@file:OptIn(ExperimentalLayoutApi::class)

package com.ashu.ashutool

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
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

// ---------------------------------------------------------------------- CPU

@Composable
fun CpuPage(back: () -> Unit) {
    val s by Monitor.snap.collectAsState()
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    var pols by remember { mutableStateOf<List<Policy>?>(null) }
    LaunchedEffect(Unit) { pols = withContext(Dispatchers.IO) { Sys.policies() } }
    val change: (String, () -> Boolean) -> Unit = { label, f ->
        scope.launch {
            val ok = withContext(Dispatchers.IO) { f() }
            toast(ctx, if (ok) "$label applied" else "$label failed")
            pols = withContext(Dispatchers.IO) { Sys.policies() }
        }
    }

    Page("CPU", "Clusters, governor and frequency", GTeal, Icons.Rounded.Memory, back) {
        item {
            Glass {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
                    Gauge(s.cpu / 100f, "${s.cpu.toInt()}%", "Load", Icons.Rounded.Memory, GTeal, dia = 112.dp)
                    Gauge(s.cpuTemp / 90f, "${s.cpuTemp.toInt()}\u00B0", "Temperature", Icons.Rounded.Thermostat, tempGrad(s.cpuTemp), dia = 112.dp)
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
        else items(list, key = { it.id }) { PolicyCard(it, change) }
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
            GradIcon(Icons.Rounded.Tune, GViolet, 40.dp)
            Spacer(Modifier.width(12.dp))
            Column {
                Text("Cluster ${p.id}", color = TextHi, fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
                Text("CPU ${p.cpus}   now ${p.minF / 1000} to ${p.maxF / 1000} MHz", color = TextLo, fontSize = 12.sp)
            }
        }
        if (p.govs.isNotEmpty()) {
            SubLabel("Governor")
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                p.govs.forEach { g -> Chip(g, g == p.gov, GViolet) { change("Governor $g") { Sys.setGov(p.id, g) } } }
            }
        }
        if (p.freqs.isNotEmpty()) {
            SubLabel("Max frequency, MHz")
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                p.freqs.forEach { f -> Chip("${f / 1000}", f == p.maxF, GTeal) { change("Max ${f / 1000} MHz") { Sys.setMax(p.id, f) } } }
            }
            SubLabel("Min frequency, MHz")
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                p.freqs.forEach { f -> Chip("${f / 1000}", f == p.minF, GBlue) { change("Min ${f / 1000} MHz") { Sys.setMin(p.id, f) } } }
            }
        }
    }
}

// ------------------------------------------------------------------- Memory

@Composable
fun MemoryPage(back: () -> Unit) {
    val s by Monitor.snap.collectAsState()
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    var m by remember { mutableStateOf<Mem?>(null) }
    var sw by remember { mutableFloatStateOf(-1f) }
    LaunchedEffect(Unit) {
        while (true) {
            val r = withContext(Dispatchers.IO) { Sys.mem() }
            m = r
            if (sw < 0f) sw = r.swappiness.toFloat()
            delay(2000)
        }
    }

    Page("Memory", "RAM, swap and cache", GViolet, Icons.Rounded.DeveloperBoard, back) {
        item {
            Glass {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Gauge(s.ramPct / 100f, "${s.ramPct.toInt()}%", "RAM used", Icons.Rounded.DeveloperBoard, GViolet, dia = 120.dp)
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
                        Tile(Icons.Rounded.CheckCircle, "Available", fmtBytes(mm.avail * 1024), GGreen),
                        Tile(Icons.Rounded.Layers, "Cached", fmtBytes(mm.cached * 1024), GBlue),
                        Tile(Icons.Rounded.Dns, "Buffers", fmtBytes(mm.buffers * 1024), GCyan),
                        Tile(Icons.Rounded.Storage, "Swap total", fmtBytes(mm.swapTotal * 1024), GPink)
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
            Glass {
                SectionTitle("Swappiness", if (sw >= 0) "${sw.toInt()}" else "")
                Text("Lower keeps apps in RAM longer. Higher moves idle pages to swap sooner.", color = TextLo, fontSize = 12.sp)
                Slider(
                    value = sw.coerceAtLeast(0f), onValueChange = { sw = it }, valueRange = 0f..100f,
                    colors = SliderDefaults.colors(
                        thumbColor = Accent, activeTrackColor = Accent, inactiveTrackColor = Color.White.copy(alpha = 0.15f)
                    )
                )
                GradButton("Apply swappiness", GViolet, Icons.Rounded.Check) {
                    scope.launch {
                        val ok = withContext(Dispatchers.IO) { Root.ok("echo ${sw.toInt()} > /proc/sys/vm/swappiness") }
                        toast(ctx, if (ok) "Swappiness set to ${sw.toInt()}" else "Could not change swappiness")
                    }
                }
            }
        }
        item {
            ActionRow(Icons.Rounded.CleaningServices, GCyan, "Free RAM cache", "Drop page cache, dentries and inodes", {
                scope.launch {
                    val ok = withContext(Dispatchers.IO) { Root.ok("sync", "echo 3 > /proc/sys/vm/drop_caches") }
                    toast(ctx, if (ok) "RAM cache cleared" else "Could not clear cache")
                }
            })
        }
    }
}

// ------------------------------------------------------------------ Battery

@Composable
fun BatteryPage(back: () -> Unit) {
    val s by Monitor.snap.collectAsState()
    val b = s.batt
    val watts = b.mv / 1000f * b.ma / 1000f
    Page("Battery", "Health, current and charge", GGreen, Icons.Rounded.BatteryChargingFull, back) {
        item {
            Glass {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Gauge(
                        b.level / 100f, "${b.level}%", b.status,
                        if (b.charging) Icons.Rounded.BatteryChargingFull else Icons.Rounded.BatteryFull,
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
                    Tile(Icons.Rounded.Thermostat, "Temperature", "${"%.1f".format(b.tempC)}\u00B0C", tempGrad(b.tempC)),
                    Tile(Icons.Rounded.Bolt, "Voltage", "${"%.2f".format(b.mv / 1000f)} V", GBlue),
                    Tile(Icons.Rounded.HealthAndSafety, "Health", b.health, GGreen),
                    Tile(Icons.Rounded.Power, "Source", b.plugged, GCyan),
                    Tile(Icons.Rounded.BatteryStd, "Capacity health", if (b.capHealth >= 0) "${b.capHealth}%" else "Not exposed", GTeal),
                    Tile(Icons.Rounded.Autorenew, "Charge cycles", if (b.cycles >= 0) "${b.cycles}" else "Not exposed", GViolet),
                    Tile(Icons.Rounded.Science, "Technology", b.tech, GPink),
                    Tile(Icons.Rounded.Speed, "Power", "${"%.1f".format(watts)} W", GFire)
                )
            )
        }
        item { Glass { SectionTitle("Current draw", "mA"); LineChart(s.currHist, GBlue) } }
        item { Glass { SectionTitle("Battery temperature", "\u00B0C"); LineChart(s.tempHist, GFire) } }
    }
}

// ------------------------------------------------------------------ Thermal

@Composable
fun ThermalPage(back: () -> Unit) {
    val s by Monitor.snap.collectAsState()
    Page("Thermal", "Every sensor on the device", GFire, Icons.Rounded.Thermostat, back) {
        item {
            Glass {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
                    Gauge(s.cpuTemp / 90f, "${s.cpuTemp.toInt()}\u00B0", "CPU", Icons.Rounded.Memory, tempGrad(s.cpuTemp), dia = 112.dp)
                    Gauge(s.batt.tempC / 60f, "${s.batt.tempC.toInt()}\u00B0", "Battery", Icons.Rounded.BatteryStd, tempGrad(s.batt.tempC), dia = 112.dp)
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
fun UsagePage(back: () -> Unit) {
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

    Page("App usage", "Screen time per app", GBlue, Icons.Rounded.QueryStats, back) {
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
                    Glass(radius = 18.dp, pad = 12.dp) {
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
