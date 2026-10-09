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
import kotlin.math.abs

// ---------------------------------------------------------------- Processes

@Composable
fun ProcessesPage() {
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

    Page("Processes", "Top memory users, live", GPink, Icons.Outlined.Terminal) {
        val l = list
        if (l == null) item { Hint("Reading processes") }
        else {
            item { Hint("Tap an app process to stop it. System and root processes are read-only.") }
            val top = (l.firstOrNull()?.rssMb ?: 1).coerceAtLeast(1)
            items(l, key = { it.pid }) { p ->
                Glass(Modifier.animateItem(), radius = 18.dp, pad = 12.dp, onClick = if (p.killable) ({ kill = p }) else null) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text(p.name, color = TextHi, fontSize = 14.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            Text("PID ${p.pid}   ${p.user}", color = TextLo, fontSize = 11.sp)
                        }
                        if (!p.killable) {
                            Icon(Icons.Outlined.Shield, "Protected", tint = TextLo, modifier = Modifier.size(14.dp))
                            Spacer(Modifier.width(8.dp))
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
        ConfirmDialog("Stop process", "Stop ${p.name} (PID ${p.pid})?", "Stop", true, onConfirm = {
            kill = null
            scope.launch {
                val r = withContext(Dispatchers.IO) { Sys.kill(p) }
                toast(ctx, if (r) "${p.name} stopped" else "Could not stop process")
            }
        }, onDismiss = { kill = null })
    }
}

// ------------------------------------------------------------- Notification

@Composable
fun NotifyPage() {
    val ctx = LocalContext.current
    val s by Monitor.snap.collectAsState()
    var rev by remember { mutableIntStateOf(0) }
    var interval by remember { mutableIntStateOf(LockStore.int(ctx, "n_interval", 2000)) }
    val (title, body) = remember(s, rev) { statsLines(ctx, s) }
    val bump: () -> Unit = { rev++ }

    Page("Notification", "Live stats in the status panel", GCyan, Icons.Outlined.Notifications) {
        item {
            Glass {
                SectionTitle("Preview")
                Row(verticalAlignment = Alignment.CenterVertically) {
                    GradIcon(Icons.Outlined.Bolt, GTeal, 26.dp)
                    Spacer(Modifier.width(8.dp))
                    Text("Ashutool", color = TextLo, fontSize = 12.sp)
                }
                Spacer(Modifier.height(8.dp))
                Text(title, color = TextHi, fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
                Text(body, color = TextLo, fontSize = 13.sp)
            }
        }
        item { PrefSwitch("n_on", true, Icons.Outlined.Notifications, GCyan, "Show stats", "Turn off for a quiet notification", bump) }
        item { PrefSwitch("n_cpu", true, Icons.Outlined.Memory, GTeal, "CPU load", "Percent of total CPU time", bump) }
        item { PrefSwitch("n_temp", true, Icons.Outlined.Thermostat, GFire, "CPU temperature", "Hottest CPU sensor", bump) }
        item { PrefSwitch("n_ram", true, Icons.Outlined.DeveloperBoard, GViolet, "RAM use", "Percent of memory in use", bump) }
        item { PrefSwitch("n_batt", true, Icons.Outlined.BatteryChargingFull, GGreen, "Battery", "Level, current and temperature", bump) }
        item { PrefSwitch("n_drain", true, Icons.Outlined.BatteryAlert, GFire, "Drain rate", "Percent per hour now and over the last 24 hours", bump) }
        item { PrefSwitch("n_net", true, Icons.Outlined.NetworkCheck, GBlue, "Network speed", "Download and upload", bump) }
        item { PrefSwitch("n_sleep", false, Icons.Outlined.Bedtime, GViolet, "Deep sleep and uptime", "How much of the time the phone slept", bump) }
        item { PrefSwitch("n_storage", false, Icons.Outlined.SdStorage, GCyan, "Free storage", "Space left on data", bump) }
        item { PrefSwitch("n_freq", true, Icons.Outlined.Speed, GBlue, "Frequency and governor", "Fastest core and active governor", bump) }
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
        item { PrefSwitch("boot", true, Icons.Outlined.PowerSettingsNew, GPink, "Start on boot", "Keeps app lock and the sidebar active after a restart") }
    }
}

// ------------------------------------------------------------------ Display

@Composable
private fun RevertDialog(onKeep: () -> Unit, onRevert: () -> Unit) {
    var left by remember { mutableIntStateOf(15) }
    LaunchedEffect(Unit) {
        while (left > 0) { delay(1000); left-- }
        onRevert()
    }
    AlertDialog(
        onDismissRequest = {},
        containerColor = DialogBg, titleContentColor = TextHi, textContentColor = TextLo,
        title = { Text("Keep this density?") },
        text = { Text("It goes back by itself in $left s if you do not confirm.") },
        confirmButton = { TextButton(onClick = onKeep) { Text("Keep", color = Accent) } },
        dismissButton = { TextButton(onClick = onRevert) { Text("Revert now", color = TextLo) } }
    )
}

@Composable
fun DisplayPage() {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    var info by remember { mutableStateOf<DisplayInfo?>(null) }
    var revertTo by remember { mutableStateOf<Int?>(null) }
    LaunchedEffect(Unit) { info = withContext(Dispatchers.IO) { Sys.display() } }
    fun run(label: String, f: () -> Boolean) {
        scope.launch {
            val ok = withContext(Dispatchers.IO) { f() }
            toast(ctx, if (ok) "$label applied" else "$label failed")
            info = withContext(Dispatchers.IO) { Sys.display() }
        }
    }

    Page("Display", "Density, animations and touch", GCyan, Icons.Outlined.PhoneAndroid) {
        val i = info
        if (i == null) item { Hint("Reading display settings") }
        else {
            item {
                Glass {
                    SectionTitle("Density", "Now ${i.cur} dpi")
                    Text("Screen size ${i.size}   physical ${i.phys} dpi", color = TextLo, fontSize = 12.sp)
                    Text("A change asks you to confirm and goes back by itself after 15 seconds.", color = TextLo, fontSize = 12.sp, modifier = Modifier.padding(top = 4.dp))
                    Spacer(Modifier.height(12.dp))
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        listOf(80, 90, 100, 110, 120).forEach { pct ->
                            val v = i.phys * pct / 100
                            Chip("$pct%  $v", i.cur == v, GCyan) {
                                if (i.phys > 0 && i.cur != v) {
                                    val prev = i.cur
                                    run("Density") { if (pct == 100) Sys.resetDensity() else Sys.setDensity(v) }
                                    revertTo = prev
                                }
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
                            Chip(l, abs(i.anim - v) < 0.01f, GBlue) { run("Animations") { Sys.setAnim(v) } }
                        }
                    }
                }
            }
            item {
                SwitchRow(Icons.Outlined.TouchApp, GPink, "Show touches", "Draw a dot where the screen is touched", i.touches) {
                    run("Show touches") { Root.ok("settings put system show_touches ${if (it) 1 else 0}") }
                }
            }
            item {
                SwitchRow(Icons.Outlined.WbSunny, GFire, "Stay awake while charging", "Keep the screen on when plugged in", i.awake) {
                    run("Stay awake") { Root.ok("settings put global stay_on_while_plugged_in ${if (it) 7 else 0}") }
                }
            }
        }
    }

    revertTo?.let { prev ->
        RevertDialog(
            onKeep = { revertTo = null },
            onRevert = {
                revertTo = null
                val phys = info?.phys ?: 0
                run("Density") { if (prev == phys || prev <= 0) Sys.resetDensity() else Sys.setDensity(prev) }
            }
        )
    }
}

// ------------------------------------------------------------------ Storage

@Composable
fun StoragePage() {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    var vols by remember { mutableStateOf<List<Vol>?>(null) }
    LaunchedEffect(Unit) { vols = withContext(Dispatchers.IO) { Sys.volumes() } }

    Page("Storage", "Space, TRIM and cache cleanup", GBlue, Icons.Outlined.SdStorage) {
        val v = vols
        if (v == null) item { Hint("Reading storage") }
        else {
            val data = v.firstOrNull { it.path == "/data" }
            if (data != null) {
                val used = data.total - data.free
                item {
                    Glass {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Gauge(used.toFloat() / data.total, "${used * 100 / data.total}%", "Data used", Icons.Outlined.SdStorage, GBlue, dia = 120.dp)
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
            ActionRow(Icons.Outlined.DeleteSweep, GPink, "Clear all app caches", "Trim cache for every installed app", {
                scope.launch {
                    val ok = withContext(Dispatchers.IO) { Root.ok("pm trim-caches 999999999999") }
                    toast(ctx, if (ok) "Caches cleared" else "Could not clear caches")
                    vols = withContext(Dispatchers.IO) { Sys.volumes() }
                }
            })
        }
        item {
            ActionRow(Icons.Outlined.CleaningServices, GCyan, "Run TRIM", "Start storage maintenance now", {
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
fun PowerPage() {
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

    Page("Power", "Doze, restart and reboot", GRed, Icons.Outlined.PowerSettingsNew) {
        item {
            SwitchRow(Icons.Outlined.BatteryStd, GGreen, "Battery saver", "Limit background work", saver) {
                saver = it
                run("Battery saver", "settings put global low_power ${if (it) 1 else 0}")
            }
        }
        item { ActionRow(Icons.Outlined.Bedtime, GBlue, "Force Doze", "Put the device into deep idle now", { run("Force Doze", "dumpsys deviceidle force-idle") }) }
        item { ActionRow(Icons.Outlined.WbSunny, GFire, "Exit Doze", "Return to the normal power state", { run("Exit Doze", "dumpsys deviceidle unforce") }) }
        item { ActionRow(Icons.Outlined.PhoneAndroid, GCyan, "Restart System UI", "Reload status bar and shade", { run("Restart System UI", "killall com.android.systemui") }) }
        item { ActionRow(Icons.Outlined.Refresh, GFire, "Soft reboot", "Restart the Android framework only", { confirm = "Soft reboot" to "setprop ctl.restart zygote" }) }
        item { ActionRow(Icons.Outlined.RestartAlt, GRed, "Reboot", "Restart the device", { confirm = "Reboot" to "reboot" }) }
        item { ActionRow(Icons.Outlined.SettingsBackupRestore, GRed, "Reboot to recovery", "Restart into recovery mode", { confirm = "Reboot to recovery" to "reboot recovery" }) }
        item { ActionRow(Icons.Outlined.Terminal, GRed, "Reboot to bootloader", "Restart into fastboot", { confirm = "Reboot to bootloader" to "reboot bootloader" }) }
        item { ActionRow(Icons.Outlined.PowerSettingsNew, GRed, "Power off", "Shut the device down", { confirm = "Power off" to "reboot -p" }) }
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
fun DevicePage() {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    var info by remember { mutableStateOf<List<Pair<String, String>>?>(null) }
    var seTarget by remember { mutableStateOf<Boolean?>(null) }
    LaunchedEffect(Unit) { info = withContext(Dispatchers.IO) { Sys.device() } }

    Page("Device", "ROM, kernel and security", GViolet, Icons.Outlined.Info) {
        val list = info
        if (list == null) item { Hint("Reading device info") }
        else {
            val enforcing = list.firstOrNull { it.first == "SELinux" }?.second.equals("Enforcing", true)
            item {
                SwitchRow(Icons.Outlined.Security, GTeal, "SELinux enforcing", "Permissive lowers security until the next reboot", enforcing) { seTarget = it }
            }
            item {
                Glass {
                    SectionTitle("System")
                    list.forEach { (k, v) ->
                        InfoRow(k, if (k == "Uptime") Sys.fmtUptime(v.toLongOrNull() ?: 0L) else v)
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
