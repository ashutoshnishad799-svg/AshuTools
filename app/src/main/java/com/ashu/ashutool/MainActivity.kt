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
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        Palette.load(this)
        applyBars(this)
        val alias = intent?.component?.className?.endsWith("GamesAlias") == true
        setContent { AshuTheme { Aurora { AshuApp(if (alias) P.GAMES else P.HOME) } } }
    }
}

private fun applyBars(a: ComponentActivity) {
    val dark = Palette.cur.dark
    val style = if (dark) SystemBarStyle.dark(AColor.TRANSPARENT) else SystemBarStyle.light(AColor.TRANSPARENT, AColor.TRANSPARENT)
    a.enableEdgeToEdge(statusBarStyle = style, navigationBarStyle = style)
}

/** One entry per tab, in the order they appear in the bottom bar. */
enum class P(val label: String, val icon: ImageVector) {
    HOME("Home", Icons.Outlined.Home),
    GAMES("Games", Icons.Outlined.SportsEsports),
    CPU("CPU", Icons.Outlined.Memory),
    MEMORY("Memory", Icons.Outlined.DeveloperBoard),
    BATTERY("Battery", Icons.Outlined.BatteryChargingFull),
    CHARGING("Charging", Icons.Outlined.Power),
    THERMAL("Thermal", Icons.Outlined.Thermostat),
    APPHEAT("App heat", Icons.Outlined.LocalFireDepartment),
    USAGE("Usage", Icons.Outlined.QueryStats),
    MANAGER("Apps", Icons.Outlined.Apps),
    LOCK("Lock", Icons.Outlined.Lock),
    HIDE("Hide", Icons.Outlined.VisibilityOff),
    NETWORK("Network", Icons.Outlined.NetworkCheck),
    PROCESSES("Tasks", Icons.Outlined.Terminal),
    NOTIFY("Alerts", Icons.Outlined.Notifications),
    DISPLAY("Display", Icons.Outlined.PhoneAndroid),
    STORAGE("Storage", Icons.Outlined.SdStorage),
    POWER("Power", Icons.Outlined.PowerSettingsNew),
    DEVICE("Device", Icons.Outlined.Info),
    SETTINGS("Settings", Icons.Outlined.Settings)
}

@Composable
fun AshuApp(start: P) {
    val ctx = LocalContext.current
    var root by remember { mutableStateOf<Boolean?>(null) }
    var page by remember { mutableStateOf(start) }

    LaunchedEffect(Unit) {
        val ok = withContext(Dispatchers.IO) { Root.isGranted().also { if (it) Root.bootstrap(ctx) } }
        root = ok
        Monitor.start(ctx)
        if (ok) ContextCompat.startForegroundService(ctx, Intent(ctx, MonitorService::class.java))
    }
    LaunchedEffect(Palette.mode) { (ctx as? ComponentActivity)?.let { applyBars(it) } }
    BackHandler(enabled = page != P.HOME) { page = P.HOME }

    Column(Modifier.fillMaxSize()) {
        Box(Modifier.weight(1f)) {
            AnimatedContent(
                targetState = page,
                modifier = Modifier.fillMaxSize(),
                transitionSpec = {
                    val dir = if (targetState.ordinal > initialState.ordinal) 1 else -1
                    (slideInHorizontally(tween(320, easing = FastOutSlowInEasing)) { it * dir / 3 } + fadeIn(tween(260))) togetherWith
                        (slideOutHorizontally(tween(260)) { -it * dir / 4 } + fadeOut(tween(180)))
                },
                label = "tabs"
            ) { p -> PageHost(p, root) }
        }
        GlassNavBar(page) { page = it }
    }
}

@Composable
private fun PageHost(p: P, root: Boolean?) {
    when (p) {
        P.HOME -> HomePage(root)
        P.GAMES -> GamesPage()
        P.CPU -> CpuPage()
        P.MEMORY -> MemoryPage()
        P.BATTERY -> BatteryPage()
        P.CHARGING -> ChargingPage()
        P.THERMAL -> ThermalPage()
        P.APPHEAT -> AppHeatPage()
        P.USAGE -> UsagePage()
        P.MANAGER -> ManagerPage()
        P.LOCK -> LockPage()
        P.HIDE -> HidePage()
        P.NETWORK -> NetworkPage()
        P.PROCESSES -> ProcessesPage()
        P.NOTIFY -> NotifyPage()
        P.DISPLAY -> DisplayPage()
        P.STORAGE -> StoragePage()
        P.POWER -> PowerPage()
        P.DEVICE -> DevicePage()
        P.SETTINGS -> SettingsPage()
    }
}

/** Frosted glass bar. Swipe it left and right to reach every tool, tap one to open it. */
@Composable
fun GlassNavBar(selected: P, onSelect: (P) -> Unit) {
    val state = rememberLazyListState()
    val pal = Palette.cur
    val shape = RoundedCornerShape(30.dp)
    LaunchedEffect(selected) { state.animateScrollToItem((selected.ordinal - 2).coerceAtLeast(0)) }
    Box(Modifier.fillMaxWidth().navigationBarsPadding().padding(horizontal = 12.dp, vertical = 8.dp)) {
        Box(
            Modifier.fillMaxWidth().clip(shape)
                .background(Brush.verticalGradient(listOf(pal.tint.copy(alpha = pal.glassTop + 0.07f), pal.tint.copy(alpha = pal.glassBottom + 0.04f))))
                .border(
                    1.dp,
                    Brush.linearGradient(listOf(pal.tint.copy(alpha = pal.bA), pal.tint.copy(alpha = pal.bB), pal.tint.copy(alpha = pal.bC))),
                    shape
                )
        ) {
            LazyRow(
                state = state,
                contentPadding = PaddingValues(horizontal = 10.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                itemsIndexed(P.entries) { _, p -> NavItem(p, p == selected) { onSelect(p) } }
            }
            Box(
                Modifier.align(Alignment.CenterStart).width(20.dp).height(62.dp)
                    .background(Brush.horizontalGradient(listOf(pal.bg2.copy(alpha = 0.95f), Color.Transparent)))
            )
            Box(
                Modifier.align(Alignment.CenterEnd).width(20.dp).height(62.dp)
                    .background(Brush.horizontalGradient(listOf(Color.Transparent, pal.bg2.copy(alpha = 0.95f))))
            )
        }
    }
}

@Composable
private fun NavItem(p: P, sel: Boolean, onClick: () -> Unit) {
    val a by animateFloatAsState(if (sel) 1f else 0f, tween(260), label = "nav")
    val col = lerp(TextLo, Accent, a)
    Column(
        Modifier.bounceClick(onClick = onClick).width(68.dp).clip(RoundedCornerShape(20.dp))
            .background(Accent.copy(alpha = 0.16f * a))
            .padding(vertical = 8.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Icon(p.icon, p.label, tint = col, modifier = Modifier.size(24.dp))
        Spacer(Modifier.height(3.dp))
        Text(p.label, color = col, fontSize = 10.sp, maxLines = 1, fontWeight = if (sel) FontWeight.SemiBold else FontWeight.Medium)
    }
}
