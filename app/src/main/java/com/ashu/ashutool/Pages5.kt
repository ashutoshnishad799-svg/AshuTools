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

private class HeatRow(val key: String, val label: String, val pct: Float, val isApp: Boolean)

/**
 * Per-app heat.
 * "Heating now" shows which apps use the most CPU this second, since CPU use is what warms the phone.
 * "History" shows the temperature measured while each app was in the foreground.
 */
@Composable
fun AppHeatPage() {
    val ctx = LocalContext.current
    val s by Monitor.snap.collectAsState()
    var tab by remember { mutableIntStateOf(0) }
    var now by remember { mutableStateOf<List<HeatRow>?>(null) }
    var hist by remember { mutableStateOf(AppThermal.top()) }
    var confirmReset by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
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

    Page("App heat", "Which apps run hot", GFire, Icons.Outlined.LocalFireDepartment) {
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
