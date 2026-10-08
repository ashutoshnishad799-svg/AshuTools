package com.ashu.ashutool

import android.content.Intent
import android.graphics.Color as AColor
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.dark(AColor.TRANSPARENT),
            navigationBarStyle = SystemBarStyle.dark(AColor.TRANSPARENT)
        )
        setContent { AshuTheme { Aurora { AshuApp() } } }
    }
}

/** Every feature page. Add an entry here and in PageHost to add a page. */
enum class P(val title: String, val icon: ImageVector, val grad: Grad) {
    CPU("CPU", Icons.Rounded.Memory, GTeal),
    MEMORY("Memory", Icons.Rounded.DeveloperBoard, GViolet),
    BATTERY("Battery", Icons.Rounded.BatteryChargingFull, GGreen),
    THERMAL("Thermal", Icons.Rounded.Thermostat, GFire),
    USAGE("App usage", Icons.Rounded.QueryStats, GBlue),
    MANAGER("App manager", Icons.Rounded.Apps, GPink),
    LOCK("App lock", Icons.Rounded.Lock, GTeal),
    HIDE("App hide", Icons.Rounded.VisibilityOff, GViolet),
    PROCESSES("Processes", Icons.Rounded.Terminal, GPink),
    NOTIFY("Notification", Icons.Rounded.Notifications, GCyan),
    DISPLAY("Display", Icons.Rounded.PhoneAndroid, GCyan),
    STORAGE("Storage", Icons.Rounded.SdStorage, GBlue),
    POWER("Power", Icons.Rounded.PowerSettingsNew, GRed),
    DEVICE("Device", Icons.Rounded.Info, GViolet)
}

@Composable
fun AshuApp() {
    val ctx = LocalContext.current
    var root by remember { mutableStateOf<Boolean?>(null) }
    var page by remember { mutableStateOf<P?>(null) }

    LaunchedEffect(Unit) {
        val ok = withContext(Dispatchers.IO) {
            Root.isGranted().also { if (it) Root.bootstrap(ctx.packageName) }
        }
        root = ok
        Monitor.start(ctx)
        if (ok) ContextCompat.startForegroundService(ctx, Intent(ctx, MonitorService::class.java))
    }
    BackHandler(enabled = page != null) { page = null }

    AnimatedContent(
        targetState = page,
        transitionSpec = {
            (fadeIn(tween(260)) + scaleIn(tween(260), initialScale = 0.95f)) togetherWith fadeOut(tween(160))
        },
        label = "nav"
    ) { p ->
        if (p == null) Home(root) { page = it } else PageHost(p) { page = null }
    }
}

@Composable
private fun PageHost(p: P, back: () -> Unit) {
    when (p) {
        P.CPU -> CpuPage(back)
        P.MEMORY -> MemoryPage(back)
        P.BATTERY -> BatteryPage(back)
        P.THERMAL -> ThermalPage(back)
        P.USAGE -> UsagePage(back)
        P.MANAGER -> ManagerPage(back)
        P.LOCK -> LockPage(back)
        P.HIDE -> HidePage(back)
        P.PROCESSES -> ProcessesPage(back)
        P.NOTIFY -> NotifyPage(back)
        P.DISPLAY -> DisplayPage(back)
        P.STORAGE -> StoragePage(back)
        P.POWER -> PowerPage(back)
        P.DEVICE -> DevicePage(back)
    }
}

// --------------------------------------------------------------------- Home

private fun subtitle(p: P, s: Snap, lockedCount: Int): String = when (p) {
    P.CPU -> "${s.cpu.toInt()}%   ${(s.cores.maxOfOrNull { it.khz } ?: 0) / 1000} MHz"
    P.MEMORY -> "${s.ramPct.toInt()}% in use"
    P.BATTERY -> "${s.batt.level}%   ${s.batt.status}"
    P.THERMAL -> "${s.cpuTemp.toInt()}\u00B0C CPU, ${s.batt.tempC.toInt()}\u00B0C battery"
    P.USAGE -> "Screen time per app"
    P.MANAGER -> "Stop, clean, remove"
    P.LOCK -> if (lockedCount == 0) "PIN for any app" else "$lockedCount locked"
    P.HIDE -> "Hide apps from the launcher"
    P.PROCESSES -> "Top memory users"
    P.NOTIFY -> "Stats in the status panel"
    P.DISPLAY -> "Density and animations"
    P.STORAGE -> "Space, TRIM, caches"
    P.POWER -> "Doze, reboot menu"
    P.DEVICE -> "ROM, kernel, SELinux"
}

@Composable
fun Home(root: Boolean?, open: (P) -> Unit) {
    val s by Monitor.snap.collectAsState()
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    var boosting by remember { mutableStateOf(false) }
    val lockedCount = LockStore.locked(ctx).size
    val doBoost: () -> Unit = {
        if (!boosting) scope.launch {
            boosting = true
            val freed = withContext(Dispatchers.IO) {
                val before = Sys.availMb(ctx)
                Sys.boost(ctx.packageName)
                delay(800)
                Sys.availMb(ctx) - before
            }
            boosting = false
            toast(ctx, if (freed > 0) "Boost done, freed $freed MB" else "Boost done")
        }
    }

    LazyColumn(
        Modifier.fillMaxSize().systemBarsPadding(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        item {
            Row(Modifier.fillMaxWidth().padding(top = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    GradText("Ashutool", GTeal, 34.sp)
                    Text("Root toolkit for your ROM", color = TextLo, fontSize = 13.sp)
                }
                RootChip(root)
            }
        }
        item {
            Glass {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
                    Gauge(s.cpu / 100f, "${s.cpu.toInt()}%", "CPU", Icons.Rounded.Memory, GTeal)
                    Gauge(s.ramPct / 100f, "${s.ramPct.toInt()}%", "RAM", Icons.Rounded.DeveloperBoard, GViolet)
                    Gauge(
                        s.batt.level / 100f, "${s.batt.level}%", "Battery",
                        if (s.batt.charging) Icons.Rounded.BatteryChargingFull else Icons.Rounded.BatteryFull,
                        if (s.batt.level <= 15 && !s.batt.charging) GRed else GGreen
                    )
                }
                Spacer(Modifier.height(14.dp))
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
                    MiniStat("CPU temp", "${s.cpuTemp.toInt()}\u00B0C")
                    MiniStat("Battery", "${"%.1f".format(s.batt.tempC)}\u00B0C")
                    MiniStat("Draw", "${s.batt.ma} mA")
                }
            }
        }
        item {
            Glass(onClick = doBoost) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    GradIcon(Icons.Rounded.Bolt, GFire, 48.dp)
                    Spacer(Modifier.width(14.dp))
                    Column(Modifier.weight(1f)) {
                        Text(if (boosting) "Boosting" else "One-tap boost", color = TextHi, fontSize = 16.sp, fontWeight = FontWeight.Bold)
                        Text("Stops background apps, trims caches, frees RAM", color = TextLo, fontSize = 12.sp)
                    }
                    GradButton(if (boosting) "Working" else "Boost", GFire, onClick = doBoost)
                }
            }
        }
        item { Text("Features", color = TextHi, fontSize = 18.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(top = 4.dp)) }
        items(P.entries.chunked(2)) { row ->
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                row.forEach { p -> FeatureTile(p, subtitle(p, s, lockedCount), Modifier.weight(1f)) { open(p) } }
                if (row.size == 1) Spacer(Modifier.weight(1f))
            }
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

@Composable
private fun FeatureTile(p: P, sub: String, modifier: Modifier, onClick: () -> Unit) {
    Glass(modifier.height(140.dp), onClick = onClick) {
        GradIcon(p.icon, p.grad, 46.dp)
        Spacer(Modifier.weight(1f))
        Text(p.title, color = TextHi, fontSize = 16.sp, fontWeight = FontWeight.Bold)
        Text(sub, color = TextLo, fontSize = 12.sp, maxLines = 2)
    }
}
