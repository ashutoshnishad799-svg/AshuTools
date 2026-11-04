/*
 * Ashutool - root toolkit for custom ROMs
 * Copyright (C) 2026 Ashutosh Nishad
 * Telegram: @ashutosh_07x (https://t.me/ashutosh_07x)
 *
 * Free software under the GNU GPL v3.0 or later, with the attribution terms in NOTICE.
 * Keep this header and the author credit in every copy and modified version.
 */

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
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
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
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        Palette.load(this)
        applyBars(this)
        val alias = intent?.component?.className?.endsWith("GamesAlias") == true
        val modified = Integrity.tampered(this)
        setContent { AshuTheme { Aurora { if (modified) TamperedScreen() else AshuApp(if (alias) P.GAMES else P.HOME) } } }
    }

    override fun onStart() {
        super.onStart()
        Monitor.uiVisible = true
        Monitor.kick(this)
    }

    override fun onStop() {
        Monitor.uiVisible = false
        super.onStop()
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
    CALLS("Calls", Icons.Outlined.Call),
    CPU("CPU", Icons.Outlined.Memory),
    MEMORY("Memory", Icons.Outlined.DeveloperBoard),
    BATTERY("Battery", Icons.Outlined.BatteryChargingFull),
    THERMAL("Thermal", Icons.Outlined.Thermostat),
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
    SETTINGS("Settings", Icons.Outlined.Settings),
    ISLAND("Island", Icons.Outlined.BlurOn),
    SOUND("Sound", Icons.Outlined.MusicNote)
}

/**
 * The bottom bar has six groups instead of nineteen tabs. Each group holds the tools that belong together,
 * and the pages of a group are chosen with the small tabs above the page.
 */
enum class G(val label: String, val icon: ImageVector, val pages: List<P>) {
    HOME("Home", Icons.Outlined.Home, listOf(P.HOME)),
    MONITOR("Monitor", Icons.Outlined.Memory, listOf(P.CPU, P.MEMORY, P.THERMAL, P.NETWORK, P.STORAGE, P.DEVICE)),
    ISLAND("Island", Icons.Outlined.BlurOn, listOf(P.ISLAND, P.SOUND)),
    GAMES("Games", Icons.Outlined.SportsEsports, listOf(P.GAMES, P.CALLS, P.USAGE)),
    POWER("Power", Icons.Outlined.BatteryChargingFull, listOf(P.BATTERY, P.POWER)),
    MORE("More", Icons.Outlined.Apps, listOf(P.MANAGER, P.PROCESSES, P.LOCK, P.HIDE, P.NOTIFY, P.DISPLAY, P.SETTINGS));

    companion object {
        fun of(p: P): G = entries.first { p in it.pages }
    }
}

@Composable
fun AshuApp(start: P) {
    val ctx = LocalContext.current
    var root by remember { mutableStateOf<Boolean?>(null) }
    var page by remember { mutableStateOf(start) }
    // The page each group was on last, so coming back to a group lands where you left it
    val lastOf = remember { mutableStateMapOf<G, P>() }
    LaunchedEffect(page) { lastOf[G.of(page)] = page }

    LaunchedEffect(Unit) {
        val ok = withContext(Dispatchers.IO) { Root.isGranted().also { if (it) Root.bootstrap(ctx) } }
        root = ok
        Monitor.start(ctx)
        if (ok) ContextCompat.startForegroundService(ctx, Intent(ctx, MonitorService::class.java))
    }
    LaunchedEffect(Palette.mode) { (ctx as? ComponentActivity)?.let { applyBars(it) } }
    BackHandler(enabled = page != P.HOME) { page = P.HOME }
    val sysDark = isSystemInDarkTheme()
    LaunchedEffect(sysDark) { Palette.systemDark = sysDark }
    val protect = LockStore.bool(ctx, "protect_self", true) && LockStore.hasCred(ctx)
    var authed by remember { mutableStateOf(false) }
    val act = ctx as? ComponentActivity
    DisposableEffect(act) {
        val obs = LifecycleEventObserver { _, ev -> if (ev == Lifecycle.Event.ON_STOP) authed = false }
        act?.lifecycle?.addObserver(obs)
        onDispose { act?.lifecycle?.removeObserver(obs) }
    }

    if (protect && !authed) {
        LockPanel(null, 0f, onOk = { authed = true }, onCancel = { act?.finish() }, title = "Ashutool")
    } else {
        val group = G.of(page)
        Column(Modifier.fillMaxSize()) {
            // A group with several pages shows its small tabs under the status bar. The page below then
            // does not add the status bar space a second time.
            if (group.pages.size > 1) {
                Box(Modifier.statusBarsPadding()) { GroupTabs(group, page) { page = it } }
            }
            Box(Modifier.weight(1f).then(if (group.pages.size > 1) Modifier.consumeWindowInsets(WindowInsets.statusBars) else Modifier)) {
                AnimatedContent(
                    targetState = page,
                    modifier = Modifier.fillMaxSize(),
                    transitionSpec = {
                        fun order(x: P) = G.of(x).ordinal * 100 + G.of(x).pages.indexOf(x)
                        val dir = if (order(targetState) > order(initialState)) 1 else -1
                        (slideInHorizontally(tween(320, easing = FastOutSlowInEasing)) { it * dir / 3 } + fadeIn(tween(260))) togetherWith
                            (slideOutHorizontally(tween(260)) { -it * dir / 4 } + fadeOut(tween(180)))
                    },
                    label = "tabs"
                ) { p -> PageHost(p, root) }
            }
            GlassNavBar(group) { g -> page = lastOf[g] ?: g.pages.first() }
        }
    }
}

@Composable
private fun PageHost(p: P, root: Boolean?) {
    when (p) {
        P.HOME -> HomePage(root)
        P.GAMES -> GamesPage()
        P.CALLS -> CallsPage()
        P.CPU -> CpuPage()
        P.MEMORY -> MemoryPage()
        P.BATTERY -> BatteryPage()
        P.THERMAL -> ThermalPage()
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
        P.ISLAND -> IslandPage()
        P.SOUND -> SoundPage()
    }
}

/** The small tabs of a group, for example CPU, Memory and Thermal under Monitor. */
@Composable
private fun GroupTabs(g: G, page: P, onSelect: (P) -> Unit) {
    val state = rememberLazyListState()
    LaunchedEffect(page) { state.animateScrollToItem(g.pages.indexOf(page).coerceAtLeast(0)) }
    LazyRow(
        state = state,
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        items(g.pages) { p -> Chip(p.label, p == page, GTeal) { onSelect(p) } }
    }
}

/**
 * Floating frosted glass bar with the six groups. The group you are on opens up into a lit pill with its name,
 * the others stay as icons. The pill grows and shrinks with a spring.
 */
@Composable
fun GlassNavBar(selected: G, onSelect: (G) -> Unit) {
    val pal = Palette.cur
    val shape = RoundedCornerShape(34.dp)
    Box(Modifier.fillMaxWidth().navigationBarsPadding().padding(horizontal = 14.dp, vertical = 10.dp)) {
        Row(
            Modifier.fillMaxWidth().clip(shape)
                .then(Glassy.fill(pal, 0.22f))
                .border(1.dp, Glassy.stroke(pal), shape)
                .padding(6.dp),
            horizontalArrangement = Arrangement.spacedBy(4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            G.entries.forEach { g ->
                key(g) {
                    val wt by animateFloatAsState(if (g == selected) 2.15f else 1f, spring(dampingRatio = 0.78f, stiffness = 420f), label = "navw")
                    NavItem(g, g == selected, Modifier.weight(wt)) { onSelect(g) }
                }
            }
        }
    }
}

@Composable
private fun NavItem(g: G, sel: Boolean, modifier: Modifier, onClick: () -> Unit) {
    val pal = Palette.cur
    val a by animateFloatAsState(if (sel) 1f else 0f, tween(260), label = "nav")
    val lit = if (pal.dark) Ink else Color.White
    val col = lerp(TextLo, lit, a)
    val shape = RoundedCornerShape(28.dp)
    Row(
        modifier.bounceClick(onClick = onClick).clip(shape)
            .background(Brush.horizontalGradient(listOf(pal.primary.a.copy(alpha = a), pal.primary.b.copy(alpha = a))))
            .padding(vertical = 11.dp, horizontal = 4.dp),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(g.icon, g.label, tint = col, modifier = Modifier.size(24.dp))
        if (a > 0.45f) {
            Spacer(Modifier.width(5.dp))
            Text(g.label, color = col.copy(alpha = ((a - 0.45f) / 0.55f).coerceIn(0f, 1f)), fontSize = 12.sp, maxLines = 1, fontWeight = FontWeight.Bold)
        }
    }
}
