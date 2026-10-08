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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.*
import kotlin.math.abs

// ---------------------------------------------------------------- Processes

@Composable
fun ProcessesPage(back: () -> Unit) {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    var list by remember { mutableStateOf<List<Proc>?>(null) }
    var kill by remember { mutableStateOf<Proc?>(null) }
    LaunchedEffect(Unit) {
        while (true) {
            list = withContext(Dispatchers.IO) { Sys.procs() }
            delay(3000)
        }
    }

    Page("Processes", "Top memory users, live", GPink, Icons.Rounded.Terminal, back) {
        val l = list
        if (l == null) item { Hint("Reading processes") }
        else {
            val top = (l.firstOrNull()?.rssMb ?: 1).coerceAtLeast(1)
            items(l, key = { it.pid }) { p ->
                Glass(radius = 18.dp, pad = 12.dp, onClick = { kill = p }) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text(p.name, color = TextHi, fontSize = 14.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            Text("PID ${p.pid}", color = TextLo, fontSize = 11.sp)
                        }
                        Text("${p.rssMb} MB", color = TextLo, fontSize = 13.sp)
                    }
                    Spacer(Modifier.height(8.dp))
                    Bar(p.rssMb.toFloat() / top, GPink)
                }
            }
        }
    }

    kill?.let { p ->
        ConfirmDialog("Kill process", "Send SIGKILL to ${p.name} (PID ${p.pid})?", "Kill", true, onConfirm = {
            kill = null
            scope.launch {
                val r = withContext(Dispatchers.IO) { Root.ok("kill -9 ${p.pid}") }
                toast(ctx, if (r) "${p.name} killed" else "Could not kill process")
            }
        }, onDismiss = { kill = null })
    }
}

// ------------------------------------------------------------- Notification

@Composable
private fun PrefSwitch(
    key: String, def: Boolean, icon: androidx.compose.ui.graphics.vector.ImageVector, grad: Grad,
    title: String, sub: String, onChange: () -> Unit
) {
    val ctx = LocalContext.current
    var on by remember { mutableStateOf(LockStore.bool(ctx, key, def)) }
    SwitchRow(icon, grad, title, sub, on) {
        on = it
        LockStore.setBool(ctx, key, it)
        onChange()
    }
}

@Composable
fun NotifyPage(back: () -> Unit) {
    val ctx = LocalContext.current
    val s by Monitor.snap.collectAsState()
    var rev by remember { mutableIntStateOf(0) }
    var interval by remember { mutableIntStateOf(LockStore.int(ctx, "n_interval", 2000)) }
    val (title, body) = remember(s, rev) { statsLines(ctx, s) }
    val bump: () -> Unit = { rev++ }

    Page("Notification", "Live stats in the status panel", GCyan, Icons.Rounded.Notifications, back) {
        item {
            Glass {
                SectionTitle("Preview")
                Row(verticalAlignment = Alignment.CenterVertically) {
                    GradIcon(Icons.Rounded.Bolt, GTeal, 26.dp)
                    Spacer(Modifier.width(8.dp))
                    Text("Ashutool", color = TextLo, fontSize = 12.sp)
                }
                Spacer(Modifier.height(8.dp))
                Text(title, color = TextHi, fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
                Text(body, color = TextLo, fontSize = 13.sp)
            }
        }
        item { PrefSwitch("n_on", true, Icons.Rounded.Notifications, GCyan, "Show stats", "Turn off for a quiet notification", bump) }
        item { PrefSwitch("n_cpu", true, Icons.Rounded.Memory, GTeal, "CPU load", "Percent of total CPU time", bump) }
        item { PrefSwitch("n_temp", true, Icons.Rounded.Thermostat, GFire, "CPU temperature", "Hottest CPU sensor", bump) }
        item { PrefSwitch("n_ram", true, Icons.Rounded.DeveloperBoard, GViolet, "RAM use", "Percent of memory in use", bump) }
        item { PrefSwitch("n_batt", true, Icons.Rounded.BatteryChargingFull, GGreen, "Battery", "Level, current and temperature", bump) }
        item { PrefSwitch("n_freq", true, Icons.Rounded.Speed, GBlue, "Frequency and governor", "Fastest core and active governor", bump) }
        item {
            Glass {
                SectionTitle("Update interval", "${interval / 1000} s")
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf(1000, 2000, 3000, 5000).forEach {
                        Chip("${it / 1000} s", interval == it, GCyan) {
                            interval = it
                            LockStore.setInt(ctx, "n_interval", it)
                        }
                    }
                }
                Text("Shorter intervals use more battery.", color = TextLo, fontSize = 12.sp, modifier = Modifier.padding(top = 10.dp))
            }
        }
        item { PrefSwitch("boot", true, Icons.Rounded.PowerSettingsNew, GPink, "Start on boot", "Keeps app lock active after a restart", bump) }
    }
}

// ------------------------------------------------------------------ Display

@Composable
fun DisplayPage(back: () -> Unit) {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    var info by remember { mutableStateOf<DisplayInfo?>(null) }
    LaunchedEffect(Unit) { info = withContext(Dispatchers.IO) { Sys.display() } }
    fun run(label: String, vararg cmd: String) {
        scope.launch {
            val ok = withContext(Dispatchers.IO) { Root.ok(*cmd) }
            toast(ctx, if (ok) "$label applied" else "$label failed")
            info = withContext(Dispatchers.IO) { Sys.display() }
        }
    }

    Page("Display", "Density, animations and touch", GCyan, Icons.Rounded.PhoneAndroid, back) {
        val i = info
        if (i == null) item { Hint("Reading display settings") }
        else {
            item {
                Glass {
                    SectionTitle("Density", "Now ${i.cur} dpi")
                    Text("Screen size ${i.size}   physical ${i.phys} dpi", color = TextLo, fontSize = 12.sp)
                    Spacer(Modifier.height(12.dp))
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        listOf(80, 90, 100, 110, 120).forEach { pct ->
                            val v = i.phys * pct / 100
                            Chip("$pct%  $v", i.cur == v, GCyan) {
                                if (pct == 100) run("Density", "wm density reset") else run("Density", "wm density $v")
                            }
                        }
                    }
                }
            }
            item {
                Glass {
                    SectionTitle("Animation speed", "Window, transition, animator")
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        listOf(0f to "Off", 0.5f to "0.5x", 1f to "1x", 1.5f to "1.5x", 2f to "2x").forEach { (v, l) ->
                            Chip(l, abs(i.anim - v) < 0.01f, GBlue) { scope.launch {
                                val ok = withContext(Dispatchers.IO) { Sys.setAnim(v) }
                                toast(ctx, if (ok) "Animations set to $l" else "Could not change animations")
                                info = withContext(Dispatchers.IO) { Sys.display() }
                            } }
                        }
                    }
                }
            }
            item {
                SwitchRow(Icons.Rounded.TouchApp, GPink, "Show touches", "Draw a dot where the screen is touched", i.touches) {
                    run("Show touches", "settings put system show_touches ${if (it) 1 else 0}")
                }
            }
            item {
                SwitchRow(Icons.Rounded.WbSunny, GFire, "Stay awake while charging", "Keep the screen on when plugged in", i.awake) {
                    run("Stay awake", "settings put global stay_on_while_plugged_in ${if (it) 7 else 0}")
                }
            }
        }
    }
}

// ------------------------------------------------------------------ Storage

@Composable
fun StoragePage(back: () -> Unit) {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    var vols by remember { mutableStateOf<List<Vol>?>(null) }
    LaunchedEffect(Unit) { vols = withContext(Dispatchers.IO) { Sys.volumes() } }

    Page("Storage", "Space, TRIM and cache cleanup", GBlue, Icons.Rounded.SdStorage, back) {
        val v = vols
        if (v == null) item { Hint("Reading storage") }
        else {
            val data = v.firstOrNull { it.path == "/data" }
            if (data != null) {
                val used = data.total - data.free
                item {
                    Glass {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Gauge(used.toFloat() / data.total, "${used * 100 / data.total}%", "Data used", Icons.Rounded.SdStorage, GBlue, dia = 120.dp)
                            Spacer(Modifier.width(20.dp))
                            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                Text(fmtBytes(data.free), color = TextHi, fontSize = 26.sp, fontWeight = FontWeight.Bold)
                                Text("free of ${fmtBytes(data.total)}", color = TextLo, fontSize = 13.sp)
                            }
                        }
                    }
                }
            }
            item {
                Glass {
                    SectionTitle("Partitions")
                    v.forEach {
                        Column(Modifier.padding(vertical = 6.dp)) {
                            Row {
                                Text(it.label, color = TextHi, fontSize = 13.sp, modifier = Modifier.weight(1f))
                                Text("${fmtBytes(it.total - it.free)} of ${fmtBytes(it.total)}", color = TextLo, fontSize = 12.sp)
                            }
                            Spacer(Modifier.height(6.dp))
                            Bar((it.total - it.free).toFloat() / it.total, GBlue)
                        }
                    }
                }
            }
        }
        item {
            ActionRow(Icons.Rounded.DeleteSweep, GPink, "Clear all app caches", "Trim cache for every installed app", {
                scope.launch {
                    val ok = withContext(Dispatchers.IO) { Root.ok("pm trim-caches 999999999999") }
                    toast(ctx, if (ok) "Caches cleared" else "Could not clear caches")
                    vols = withContext(Dispatchers.IO) { Sys.volumes() }
                }
            })
        }
        item {
            ActionRow(Icons.Rounded.CleaningServices, GCyan, "Run TRIM", "Start storage maintenance now", {
                scope.launch {
                    val ok = withContext(Dispatchers.IO) { Root.ok("sm idle-maint run") }
                    toast(ctx, if (ok) "Maintenance started" else "Could not start maintenance")
                }
            })
        }
    }
}

// -------------------------------------------------------------------- Power

@Composable
fun PowerPage(back: () -> Unit) {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    var saver by remember { mutableStateOf(false) }
    var confirm by remember { mutableStateOf<Pair<String, String>?>(null) }
    LaunchedEffect(Unit) {
        saver = withContext(Dispatchers.IO) { Root.run("settings get global low_power").firstOrNull()?.trim() == "1" }
    }
    fun run(label: String, vararg cmd: String) {
        scope.launch {
            val ok = withContext(Dispatchers.IO) { Root.ok(*cmd) }
            toast(ctx, if (ok) "$label done" else "$label failed")
        }
    }

    Page("Power", "Doze, restart and reboot", GRed, Icons.Rounded.PowerSettingsNew, back) {
        item {
            SwitchRow(Icons.Rounded.BatteryStd, GGreen, "Battery saver", "Limit background work", saver) {
                saver = it
                run("Battery saver", "settings put global low_power ${if (it) 1 else 0}")
            }
        }
        item { ActionRow(Icons.Rounded.Bedtime, GBlue, "Force Doze", "Put the device into deep idle now", { run("Force Doze", "dumpsys deviceidle force-idle") }) }
        item { ActionRow(Icons.Rounded.WbSunny, GFire, "Exit Doze", "Return to the normal power state", { run("Exit Doze", "dumpsys deviceidle unforce") }) }
        item { ActionRow(Icons.Rounded.PhoneAndroid, GCyan, "Restart System UI", "Reload status bar and shade", { run("Restart System UI", "killall com.android.systemui") }) }
        item { ActionRow(Icons.Rounded.Refresh, GFire, "Soft reboot", "Restart the Android framework only", { confirm = "Soft reboot" to "setprop ctl.restart zygote" }) }
        item { ActionRow(Icons.Rounded.RestartAlt, GRed, "Reboot", "Restart the device", { confirm = "Reboot" to "reboot" }) }
        item { ActionRow(Icons.Rounded.SettingsBackupRestore, GRed, "Reboot to recovery", "Restart into recovery mode", { confirm = "Reboot to recovery" to "reboot recovery" }) }
        item { ActionRow(Icons.Rounded.Terminal, GRed, "Reboot to bootloader", "Restart into fastboot", { confirm = "Reboot to bootloader" to "reboot bootloader" }) }
        item { ActionRow(Icons.Rounded.PowerSettingsNew, GRed, "Power off", "Shut the device down", { confirm = "Power off" to "reboot -p" }) }
    }

    confirm?.let { (label, cmd) ->
        ConfirmDialog(label, "Save your work first. This takes effect immediately.", label, true, onConfirm = {
            confirm = null
            run(label, cmd)
        }, onDismiss = { confirm = null })
    }
}

// ------------------------------------------------------------------- Device

@Composable
fun DevicePage(back: () -> Unit) {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    var info by remember { mutableStateOf<List<Pair<String, String>>?>(null) }
    var seTarget by remember { mutableStateOf<Boolean?>(null) }
    LaunchedEffect(Unit) { info = withContext(Dispatchers.IO) { Sys.device() } }

    Page("Device", "ROM, kernel and security", GViolet, Icons.Rounded.Info, back) {
        val list = info
        if (list == null) item { Hint("Reading device info") }
        else {
            val enforcing = list.firstOrNull { it.first == "SELinux" }?.second.equals("Enforcing", true)
            item {
                SwitchRow(Icons.Rounded.Security, GTeal, "SELinux enforcing", "Turn off only for testing", enforcing) { seTarget = it }
            }
            item {
                Glass {
                    SectionTitle("System")
                    list.forEach { (k, v) ->
                        InfoRow(k, if (k == "Uptime") Sys.fmtUptime(v.toLongOrNull() ?: 0) else v)
                    }
                }
            }
        }
    }

    seTarget?.let { on ->
        ConfirmDialog(
            if (on) "Enable SELinux" else "Disable SELinux",
            if (on) "Switch SELinux to enforcing mode?" else "Permissive mode lowers system security until the next reboot.",
            if (on) "Enable" else "Disable", !on,
            onConfirm = {
                seTarget = null
                scope.launch {
                    val ok = withContext(Dispatchers.IO) { Root.ok("setenforce ${if (on) 1 else 0}") }
                    toast(ctx, if (ok) "SELinux updated" else "Could not change SELinux")
                    info = withContext(Dispatchers.IO) { Sys.device() }
                }
            },
            onDismiss = { seTarget = null }
        )
    }
}
