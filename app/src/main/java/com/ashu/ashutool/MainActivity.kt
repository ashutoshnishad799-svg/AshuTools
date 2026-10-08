package com.ashu.ashutool

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent { AshuTheme { AshuApp() } }
    }
}

private data class NavTab(val title: String, val icon: ImageVector)

@Composable
fun AshuApp() {
    val ctx = LocalContext.current
    var root by remember { mutableStateOf<Boolean?>(null) }
    var tab by remember { mutableIntStateOf(0) }
    val tabs = listOf(
        NavTab("Dashboard", Icons.Rounded.Dashboard),
        NavTab("Battery", Icons.Rounded.BatteryChargingFull),
        NavTab("Usage", Icons.Rounded.QueryStats),
        NavTab("Apps", Icons.Rounded.Apps),
        NavTab("Tools", Icons.Rounded.Build)
    )

    LaunchedEffect(Unit) {
        val ok = withContext(Dispatchers.IO) {
            Root.isGranted().also { if (it) Root.bootstrap(ctx.packageName) }
        }
        root = ok
        Monitor.start(ctx)
        if (ok) ContextCompat.startForegroundService(ctx, Intent(ctx, MonitorService::class.java))
    }

    Scaffold(
        containerColor = Bg,
        bottomBar = {
            NavigationBar(containerColor = Surf) {
                tabs.forEachIndexed { i, t ->
                    NavigationBarItem(
                        selected = tab == i,
                        onClick = { tab = i },
                        icon = { Icon(t.icon, t.title) },
                        label = { Text(t.title, fontSize = 11.sp) },
                        colors = NavigationBarItemDefaults.colors(
                            selectedIconColor = Accent, selectedTextColor = Accent,
                            indicatorColor = Accent.copy(alpha = 0.15f),
                            unselectedIconColor = TextLo, unselectedTextColor = TextLo
                        )
                    )
                }
            }
        }
    ) { pad ->
        Box(Modifier.padding(pad)) {
            when (tab) {
                0 -> Dashboard(root)
                1 -> BatteryScreen()
                2 -> UsageScreen()
                3 -> AppsScreen()
                else -> ToolsScreen()
            }
        }
    }
}
