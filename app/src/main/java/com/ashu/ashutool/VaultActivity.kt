package com.ashu.ashutool

import android.content.Intent
import android.graphics.Color as AColor
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.OpenInBrowser
import androidx.compose.material.icons.outlined.Visibility
import androidx.compose.material.icons.outlined.VisibilityOff
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** Opened by the dialer secret code or the volume sequence. Lists hidden apps and lets the user open or unhide them. */
class VaultActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        Palette.load(this)
        val dark = Palette.cur.dark
        enableEdgeToEdge(
            statusBarStyle = if (dark) SystemBarStyle.dark(AColor.TRANSPARENT) else SystemBarStyle.light(AColor.TRANSPARENT, AColor.TRANSPARENT),
            navigationBarStyle = if (dark) SystemBarStyle.dark(AColor.TRANSPARENT) else SystemBarStyle.light(AColor.TRANSPARENT, AColor.TRANSPARENT)
        )
        setContent { AshuTheme { Aurora { VaultScreen { finish() } } } }
    }
}

@Composable
fun VaultScreen(close: () -> Unit) {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    val need = remember { LockStore.bool(ctx, "vault_auth", true) && LockStore.hasCred(ctx) }
    var authed by remember { mutableStateOf(!need) }
    var apps by remember { mutableStateOf<List<AppItem>?>(null) }
    LaunchedEffect(authed) {
        if (authed) apps = withContext(Dispatchers.IO) { Apps.load(ctx).filter { it.hidden } }
    }

    if (!authed) {
        LockPanel(null, 0f, onOk = { authed = true }, onCancel = close)
    } else {
        Page("Hidden apps", "Only you can see this", GViolet, Icons.Outlined.VisibilityOff, onBack = close, bottomInset = true) {
            val list = apps
            if (list == null) item { Hint("Loading") }
            else if (list.isEmpty()) item { Glass { Hint("No hidden apps yet. Hide apps from the Hide tab.") } }
            else items(list, key = { it.pkg }) { app ->
                Glass(Modifier.animateItem(), radius = 18.dp, pad = 0.dp) {
                    AppHead(app) {
                        GlassButton(Icons.Outlined.OpenInBrowser, "Open") {
                            scope.launch {
                                val ok = withContext(Dispatchers.IO) { Apps.tempOpen(ctx, app.pkg) }
                                delay(450)
                                val i = ctx.packageManager.getLaunchIntentForPackage(app.pkg)
                                if (ok && i != null) {
                                    ctx.startActivity(i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
                                    close()
                                } else toast(ctx, "Could not open this app")
                            }
                        }
                        Spacer(Modifier.width(6.dp))
                        GlassButton(Icons.Outlined.Visibility, "Unhide") {
                            scope.launch {
                                withContext(Dispatchers.IO) { Apps.unhide(ctx, app.pkg) }
                                apps = apps?.filter { it.pkg != app.pkg }
                                toast(ctx, "${app.name} is visible again")
                            }
                        }
                    }
                }
            }
        }
    }
}
