@file:OptIn(ExperimentalLayoutApi::class)

package com.ashu.ashutool

import android.content.Intent
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

private val listPad = PaddingValues(16.dp)
private val gap = Arrangement.spacedBy(14.dp)

// ---------------------------------------------------------------- Dashboard

@Composable
fun Dashboard(root: Boolean?) {
    val s by Monitor.snap.collectAsState()
    val b = s.batt
    LazyColumn(Modifier.fillMaxSize(), contentPadding = listPad, verticalArrangement = gap) {
        item { Header("Ashutool", "Live system overview", root) }
        item {
            Panel {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
                    Gauge(s.cpu / 100f, "${s.cpu.toInt()}%", "CPU", Icons.Rounded.Memory, Accent)
                    Gauge(s.ramPct / 100f, "${s.ramPct.toInt()}%", "RAM", Icons.Rounded.Storage, Accent2)
                    Gauge(s.cpuTemp / 90f, "${s.cpuTemp.toInt()}\u00B0", "Temp", Icons.Rounded.Thermostat, tempColor(s.cpuTemp))
                }
                Spacer(Modifier.height(10.dp))
                Text(
                    "${s.ramUsedMb} MB of ${s.ramTotalMb} MB memory in use",
                    color = TextLo, fontSize = 12.sp, modifier = Modifier.fillMaxWidth(), textAlign = TextAlign.Center
                )
            }
        }
        item {
            Panel {
                SectionTitle("CPU load", "Last 2 minutes")
                LineChart(s.cpuHist, Accent, maxV = 100f)
            }
        }
        item {
            Panel {
                SectionTitle("Cores", "Governor: ${s.gov}")
                if (s.cores.isEmpty()) Text("Waiting for root data", color = TextLo, fontSize = 13.sp)
                s.cores.forEach { CoreRow(it) }
            }
        }
        item {
            Panel {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Gauge(
                        b.level / 100f, "${b.level}%", "Battery",
                        if (b.charging) Icons.Rounded.BatteryChargingFull else Icons.Rounded.BatteryFull,
                        if (b.level <= 15 && !b.charging) Danger else Accent
                    )
                    Spacer(Modifier.width(20.dp))
                    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text(b.status, color = TextHi, fontSize = 17.sp, fontWeight = FontWeight.SemiBold)
                        Text("${b.ma} mA draw", color = TextLo, fontSize = 13.sp)
                        Text("${"%.1f".format(b.tempC)}\u00B0C battery", color = TextLo, fontSize = 13.sp)
                    }
                }
            }
        }
    }
}

@Composable
private fun CoreRow(c: Core) {
    Row(Modifier.fillMaxWidth().padding(vertical = 5.dp), verticalAlignment = Alignment.CenterVertically) {
        Text("CPU${c.id}", color = TextLo, fontSize = 12.sp, modifier = Modifier.width(48.dp))
        Box(Modifier.weight(1f)) {
            Bar(if (c.maxKhz > 0) c.khz.toFloat() / c.maxKhz else 0f, if (c.online) Accent else Line)
        }
        Text(
            if (c.online && c.khz > 0) "${c.khz / 1000} MHz" else "Offline",
            color = if (c.online) TextHi else TextLo, fontSize = 12.sp,
            textAlign = TextAlign.End, modifier = Modifier.width(78.dp)
        )
    }
}

// ------------------------------------------------------------------ Battery

@Composable
fun BatteryScreen() {
    val s by Monitor.snap.collectAsState()
    val b = s.batt
    val tiles = listOf(
        Triple(Icons.Rounded.Thermostat, "Temperature", "${"%.1f".format(b.tempC)}\u00B0C"),
        Triple(Icons.Rounded.Bolt, "Voltage", "${"%.2f".format(b.mv / 1000f)} V"),
        Triple(Icons.Rounded.Speed, "Current", "${b.ma} mA"),
        Triple(Icons.Rounded.HealthAndSafety, "Health", b.health),
        Triple(Icons.Rounded.Power, "Source", b.plugged),
        Triple(Icons.Rounded.Science, "Technology", b.tech),
        Triple(Icons.Rounded.BatteryStd, "Capacity health", if (b.capHealth >= 0) "${b.capHealth}%" else "Not exposed"),
        Triple(Icons.Rounded.Autorenew, "Charge cycles", if (b.cycles >= 0) "${b.cycles}" else "Not exposed")
    )
    LazyColumn(Modifier.fillMaxSize(), contentPadding = listPad, verticalArrangement = gap) {
        item { Header("Battery", "Health and power draw") }
        item {
            Panel {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Gauge(
                        b.level / 100f, "${b.level}%", b.status,
                        if (b.charging) Icons.Rounded.BatteryChargingFull else Icons.Rounded.BatteryFull,
                        if (b.level <= 15 && !b.charging) Danger else Accent, dia = 120.dp
                    )
                    Spacer(Modifier.width(20.dp))
                    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text("${b.ma} mA", color = TextHi, fontSize = 26.sp, fontWeight = FontWeight.Bold)
                        Text(if (b.charging) "Charging current" else "Discharge current", color = TextLo, fontSize = 13.sp)
                        Text("Source: ${b.plugged}", color = TextLo, fontSize = 13.sp)
                    }
                }
            }
        }
        item {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                tiles.chunked(2).forEach { row ->
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        row.forEach { (ic, l, v) ->
                            StatTile(ic, l, v, Modifier.weight(1f), if (l == "Temperature") tempColor(b.tempC) else Accent)
                        }
                        if (row.size == 1) Spacer(Modifier.weight(1f))
                    }
                }
            }
        }
        item { Panel { SectionTitle("Current draw", "mA, last 2 minutes"); LineChart(s.currHist, Accent2) } }
        item { Panel { SectionTitle("Battery temperature", "\u00B0C, last 2 minutes"); LineChart(s.tempHist, Warn) } }
    }
}

// -------------------------------------------------------------------- Usage

@Composable
fun UsageScreen() {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    var access by remember { mutableStateOf(Usage.hasAccess(ctx)) }
    var days by remember { mutableIntStateOf(1) }
    var items by remember { mutableStateOf<List<UsageItem>?>(null) }

    LaunchedEffect(days, access) {
        if (access) {
            items = null
            items = withContext(Dispatchers.IO) { Usage.query(ctx, days) }
        }
    }

    LazyColumn(Modifier.fillMaxSize(), contentPadding = listPad, verticalArrangement = gap) {
        item { Header("App usage", "Screen time per app") }
        if (!access) {
            item {
                Panel {
                    Text("Usage access is off", color = TextHi, fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
                    Spacer(Modifier.height(6.dp))
                    Text("Ashutool can grant itself this permission through root.", color = TextLo, fontSize = 13.sp)
                    Spacer(Modifier.height(14.dp))
                    Button(
                        onClick = {
                            scope.launch {
                                withContext(Dispatchers.IO) { Root.ok("appops set ${ctx.packageName} GET_USAGE_STATS allow") }
                                access = Usage.hasAccess(ctx)
                            }
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = Accent, contentColor = Color(0xFF00201A))
                    ) { Text("Grant usage access") }
                }
            }
        } else {
            item {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Chip("Today", days == 1) { days = 1 }
                    Chip("7 days", days == 7) { days = 7 }
                    Chip("30 days", days == 30) { days = 30 }
                }
            }
            val list = items
            if (list == null) {
                item { Text("Reading usage stats", color = TextLo, fontSize = 13.sp) }
            } else if (list.isEmpty()) {
                item { Text("No app was used for more than a minute in this range.", color = TextLo, fontSize = 13.sp) }
            } else {
                item {
                    Panel {
                        Text("Total screen time", color = TextLo, fontSize = 12.sp)
                        Text(fmtDuration(list.sumOf { it.ms }), color = TextHi, fontSize = 30.sp, fontWeight = FontWeight.Bold)
                    }
                }
                val max = list.first().ms.coerceAtLeast(1)
                items(list.take(40), key = { it.pkg }) { UsageRow(it, max) }
            }
        }
    }
}

@Composable
private fun UsageRow(u: UsageItem, max: Long) {
    val shape = RoundedCornerShape(16.dp)
    Row(
        Modifier.fillMaxWidth().clip(shape).background(Surf).border(1.dp, Line, shape).padding(12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        AppIcon(u.pkg, 42.dp)
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(u.name, color = TextHi, fontSize = 14.sp, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
                Text(fmtDuration(u.ms), color = TextLo, fontSize = 13.sp)
            }
            Spacer(Modifier.height(8.dp))
            Bar(u.ms.toFloat() / max, Accent2)
        }
    }
}

// --------------------------------------------------------------------- Apps

@Composable
fun AppsScreen() {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    var apps by remember { mutableStateOf<List<AppItem>?>(null) }
    var locked by remember { mutableStateOf(LockStore.locked(ctx)) }
    var query by remember { mutableStateOf("") }
    var filter by remember { mutableIntStateOf(0) }
    var expanded by remember { mutableStateOf<String?>(null) }
    var pinFor by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(Unit) { apps = withContext(Dispatchers.IO) { Apps.load(ctx) } }

    val shown = remember(apps, query, filter, locked) {
        (apps ?: emptyList()).filter {
            val inFilter = when (filter) {
                0 -> !it.system
                1 -> it.system
                2 -> it.hidden
                else -> it.pkg in locked
            }
            inFilter && (query.isBlank() || it.name.contains(query, true) || it.pkg.contains(query, true))
        }
    }

    fun lock(pkg: String, on: Boolean) {
        LockStore.setLocked(ctx, pkg, on)
        locked = LockStore.locked(ctx)
    }

    LazyColumn(Modifier.fillMaxSize(), contentPadding = listPad, verticalArrangement = Arrangement.spacedBy(10.dp)) {
        item { Header("Apps", "Lock, hide, stop and clean") }
        item {
            OutlinedTextField(
                value = query, onValueChange = { query = it }, singleLine = true,
                placeholder = { Text("Search apps", color = TextLo) },
                leadingIcon = { Icon(Icons.Rounded.Search, null, tint = TextLo) },
                modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(16.dp),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = Accent, unfocusedBorderColor = Line,
                    focusedContainerColor = Surf, unfocusedContainerColor = Surf, cursorColor = Accent
                )
            )
        }
        item {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Chip("User", filter == 0) { filter = 0 }
                Chip("System", filter == 1) { filter = 1 }
                Chip("Hidden", filter == 2) { filter = 2 }
                Chip("Locked", filter == 3) { filter = 3 }
            }
        }
        if (apps == null) item { Text("Loading apps", color = TextLo, fontSize = 13.sp) }
        else if (shown.isEmpty()) item { Text("No apps in this view.", color = TextLo, fontSize = 13.sp) }
        items(shown, key = { it.pkg }) { app ->
            val isLocked = app.pkg in locked
            val open = expanded == app.pkg
            val shape = RoundedCornerShape(16.dp)
            Column(Modifier.fillMaxWidth().clip(shape).background(Surf).border(1.dp, Line, shape)) {
                Row(
                    Modifier.fillMaxWidth().clickable { expanded = if (open) null else app.pkg }.padding(12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    AppIcon(app.pkg, 42.dp)
                    Spacer(Modifier.width(12.dp))
                    Column(Modifier.weight(1f)) {
                        Text(app.name, color = TextHi, fontSize = 14.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        Text(app.pkg, color = TextLo, fontSize = 11.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                    if (isLocked) Icon(Icons.Rounded.Lock, null, tint = Accent, modifier = Modifier.size(18.dp))
                    if (app.hidden) {
                        Spacer(Modifier.width(8.dp))
                        Icon(Icons.Rounded.VisibilityOff, null, tint = Warn, modifier = Modifier.size(18.dp))
                    }
                }
                if (open) {
                    FlowRow(
                        Modifier.padding(start = 12.dp, end = 12.dp, bottom = 12.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        ActionBtn(if (isLocked) Icons.Rounded.LockOpen else Icons.Rounded.Lock, if (isLocked) "Unlock" else "Lock") {
                            if (isLocked) lock(app.pkg, false)
                            else if (!LockStore.hasPin(ctx)) pinFor = app.pkg
                            else lock(app.pkg, true)
                        }
                        ActionBtn(if (app.hidden) Icons.Rounded.Visibility else Icons.Rounded.VisibilityOff, if (app.hidden) "Unhide" else "Hide") {
                            if (app.pkg in Apps.protectedPkgs) toast(ctx, "This app is protected")
                            else scope.launch {
                                val r = withContext(Dispatchers.IO) { Apps.setHidden(app.pkg, !app.hidden) }
                                if (r) apps = apps?.map { if (it.pkg == app.pkg) it.copy(hidden = !it.hidden) else it }
                                else toast(ctx, "Could not change visibility")
                            }
                        }
                        ActionBtn(Icons.Rounded.Stop, "Force stop") {
                            if (app.pkg in Apps.protectedPkgs) toast(ctx, "This app is protected")
                            else scope.launch {
                                val r = withContext(Dispatchers.IO) { Apps.forceStop(app.pkg) }
                                toast(ctx, if (r) "${app.name} stopped" else "Could not stop app")
                            }
                        }
                        ActionBtn(Icons.Rounded.CleaningServices, "Clear cache") {
                            scope.launch {
                                val r = withContext(Dispatchers.IO) { Apps.clearCache(app.pkg) }
                                toast(ctx, if (r) "Cache cleared" else "Could not clear cache")
                            }
                        }
                    }
                }
            }
        }
    }

    pinFor?.let { pkg ->
        PinSetupDialog(
            onDismiss = { pinFor = null },
            onSave = { pin ->
                LockStore.setPin(ctx, pin)
                lock(pkg, true)
                pinFor = null
            }
        )
    }
}

@Composable
private fun ActionBtn(icon: ImageVector, text: String, onClick: () -> Unit) {
    FilledTonalButton(
        onClick = onClick,
        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
        colors = ButtonDefaults.filledTonalButtonColors(containerColor = Surf2, contentColor = TextHi)
    ) {
        Icon(icon, null, tint = Accent, modifier = Modifier.size(16.dp))
        Spacer(Modifier.width(6.dp))
        Text(text, fontSize = 12.sp)
    }
}

@Composable
private fun PinSetupDialog(onDismiss: () -> Unit, onSave: (String) -> Unit) {
    var pin by remember { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = Surf2,
        title = { Text("Set app lock PIN", color = TextHi) },
        text = {
            OutlinedTextField(
                value = pin,
                onValueChange = { if (it.length <= 6 && it.all(Char::isDigit)) pin = it },
                label = { Text("4 to 6 digits") },
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
                visualTransformation = PasswordVisualTransformation()
            )
        },
        confirmButton = { TextButton(enabled = pin.length >= 4, onClick = { onSave(pin) }) { Text("Save PIN") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } }
    )
}

// -------------------------------------------------------------------- Tools

@Composable
fun ToolsScreen() {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    val s by Monitor.snap.collectAsState()
    var notif by remember { mutableStateOf(LockStore.statsInNotif(ctx)) }
    var govs by remember { mutableStateOf(emptyList<String>()) }
    var confirm by remember { mutableStateOf<Pair<String, String>?>(null) }

    LaunchedEffect(Unit) {
        govs = withContext(Dispatchers.IO) {
            Root.run("cat /sys/devices/system/cpu/cpu0/cpufreq/scaling_available_governors")
                .joinToString(" ").trim().split(" ").filter { it.isNotBlank() }
        }
    }

    fun run(label: String, vararg cmd: String) {
        scope.launch {
            val r = withContext(Dispatchers.IO) { Root.ok(*cmd) }
            toast(ctx, if (r) "$label done" else "$label failed")
        }
    }

    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Header("Tools", "Root actions for your ROM")

        ActionRow(
            Icons.Rounded.Notifications, "Stats in notification",
            "Show CPU, RAM and battery in the status panel",
            trailing = {
                Switch(
                    checked = notif,
                    onCheckedChange = { notif = it; LockStore.setStatsInNotif(ctx, it) },
                    colors = SwitchDefaults.colors(checkedTrackColor = Accent, checkedThumbColor = Color(0xFF00201A))
                )
            }
        )

        SectionLabel("CPU governor")
        Panel {
            if (govs.isEmpty()) Text("No governor list found on this kernel.", color = TextLo, fontSize = 13.sp)
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                govs.forEach { g ->
                    Chip(g, s.gov == g) {
                        run(
                            "Governor $g",
                            "for c in /sys/devices/system/cpu/cpu[0-9]*; do echo $g > \$c/cpufreq/scaling_governor; done"
                        )
                    }
                }
            }
        }

        SectionLabel("Performance")
        ActionRow(Icons.Rounded.Stop, "Kill background apps", "Force stop every user app", Accent2, {
            run(
                "Kill background apps",
                "for p in \$(pm list packages -3 | cut -d: -f2); do [ \"\$p\" != \"${ctx.packageName}\" ] && am force-stop \$p; done; true"
            )
        })
        ActionRow(Icons.Rounded.DeleteSweep, "Clear all app caches", "Trim cache for every installed app", Accent2, {
            run("Clear caches", "pm trim-caches 999999999999")
        })
        ActionRow(Icons.Rounded.Memory, "Free RAM cache", "Drop page cache, dentries and inodes", Accent2, {
            run("Free RAM", "sync", "echo 3 > /proc/sys/vm/drop_caches")
        })
        ActionRow(Icons.Rounded.Bedtime, "Force Doze", "Put the device into deep idle now", Accent2, {
            run("Force Doze", "dumpsys deviceidle force-idle")
        })
        ActionRow(Icons.Rounded.WbSunny, "Exit Doze", "Return to normal power state", Accent2, {
            run("Exit Doze", "dumpsys deviceidle unforce")
        })

        SectionLabel("Power")
        ActionRow(Icons.Rounded.PhoneAndroid, "Restart System UI", "Reload status bar and launcher surface", Warn, {
            run("Restart System UI", "killall com.android.systemui")
        })
        ActionRow(Icons.Rounded.Refresh, "Soft reboot", "Restart the Android framework only", Warn, {
            confirm = "Soft reboot" to "setprop ctl.restart zygote"
        })
        ActionRow(Icons.Rounded.PowerSettingsNew, "Reboot", "Restart the device", Danger, {
            confirm = "Reboot" to "reboot"
        })
        ActionRow(Icons.Rounded.SettingsBackupRestore, "Reboot to recovery", "Restart into recovery mode", Danger, {
            confirm = "Reboot to recovery" to "reboot recovery"
        })
        ActionRow(Icons.Rounded.Terminal, "Reboot to bootloader", "Restart into fastboot", Danger, {
            confirm = "Reboot to bootloader" to "reboot bootloader"
        })
        Spacer(Modifier.height(8.dp))
    }

    confirm?.let { (label, cmd) ->
        AlertDialog(
            onDismissRequest = { confirm = null },
            containerColor = Surf2,
            title = { Text(label, color = TextHi) },
            text = { Text("Save your work first. The device will restart.", color = TextLo) },
            confirmButton = {
                TextButton(onClick = { confirm = null; run(label, cmd) }) { Text(label, color = Danger) }
            },
            dismissButton = { TextButton(onClick = { confirm = null }) { Text("Cancel") } }
        )
    }
}

@Composable
private fun SectionLabel(text: String) {
    Text(text, color = TextLo, fontSize = 13.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.padding(top = 10.dp, start = 4.dp))
}
