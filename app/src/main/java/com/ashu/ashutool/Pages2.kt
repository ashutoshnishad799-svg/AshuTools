@file:OptIn(ExperimentalLayoutApi::class)

package com.ashu.ashutool

import android.content.Intent
import androidx.compose.foundation.clickable
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

@Composable
internal fun rememberApps(): MutableState<List<AppItem>?> {
    val ctx = LocalContext.current
    val st = remember { mutableStateOf<List<AppItem>?>(null) }
    LaunchedEffect(Unit) { st.value = withContext(Dispatchers.IO) { Apps.load(ctx) } }
    return st
}

@Composable
internal fun AppHead(app: AppItem, modifier: Modifier = Modifier, trailing: @Composable RowScope.() -> Unit = {}) {
    Row(modifier.fillMaxWidth().padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
        AppIcon(app.pkg, 44.dp)
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(app.name, color = TextHi, fontSize = 14.sp, fontWeight = FontWeight.Medium, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f, fill = false))
                if (app.guarded) {
                    Spacer(Modifier.width(6.dp))
                    Icon(Icons.Outlined.Shield, "Protected", tint = TextLo, modifier = Modifier.size(14.dp))
                }
            }
            Text(app.pkg, color = TextLo, fontSize = 11.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        trailing()
    }
}

/** A switch bound to a preference. Shared by several tabs. */
@Composable
internal fun PrefSwitch(
    key: String, def: Boolean, icon: androidx.compose.ui.graphics.vector.ImageVector, grad: Grad,
    title: String, sub: String, onChange: () -> Unit = {}
) {
    val ctx = LocalContext.current
    var on by remember { mutableStateOf(LockStore.bool(ctx, key, def)) }
    SwitchRow(icon, grad, title, sub, on) {
        on = it
        LockStore.setBool(ctx, key, it)
        onChange()
    }
}

// ------------------------------------------------------------------ Manager

@Composable
fun ManagerPage() {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    var apps by rememberApps()
    var query by remember { mutableStateOf("") }
    var filter by remember { mutableIntStateOf(0) }
    var open by remember { mutableStateOf<String?>(null) }
    var removed by remember { mutableStateOf(Apps.removedApps(ctx)) }
    var confirm by remember { mutableStateOf<Triple<String, String, () -> Boolean>?>(null) }

    val shown = remember(apps, query, filter) {
        (apps ?: emptyList()).filter {
            (if (filter == 0) !it.system else it.system) &&
                (query.isBlank() || it.name.contains(query, true) || it.pkg.contains(query, true))
        }
    }
    fun run(okMsg: String, failMsg: String, f: () -> Boolean) {
        scope.launch {
            val r = withContext(Dispatchers.IO) { f() }
            toast(ctx, if (r) okMsg else failMsg)
        }
    }

    Page("App manager", "Stop, clean, remove and restore", GPink, Icons.Outlined.Apps) {
        if (filter != 2) item { SearchField(query) { query = it } }
        item {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Chip("User apps", filter == 0, GPink) { filter = 0 }
                Chip("System apps", filter == 1, GPink) { filter = 1 }
                Chip("Removed", filter == 2, GPink) { filter = 2 }
            }
        }
        if (filter == 2) {
            if (removed.isEmpty()) item { Glass { Hint("Nothing removed. Apps you remove here can be restored from this list.") } }
            items(removed, key = { it.first }) { (pkg, label) ->
                Glass(Modifier.animateItem(), radius = 18.dp, pad = 12.dp) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text(label, color = TextHi, fontSize = 14.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            Text(pkg, color = TextLo, fontSize = 11.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        }
                        GlassButton(Icons.Outlined.Restore, "Restore") {
                            scope.launch {
                                val r = withContext(Dispatchers.IO) { Apps.restore(ctx, pkg) }
                                toast(ctx, if (r) "$label restored" else "Could not restore")
                                removed = Apps.removedApps(ctx)
                                apps = withContext(Dispatchers.IO) { Apps.load(ctx) }
                            }
                        }
                    }
                }
            }
        } else {
            if (apps == null) item { Hint("Loading apps") }
            else if (shown.isEmpty()) item { Hint("No apps in this view.") }
            items(shown, key = { it.pkg }) { app ->
                val isOpen = open == app.pkg
                Glass(Modifier.animateItem(), radius = 18.dp, pad = 0.dp) {
                    AppHead(app, Modifier.clickable { open = if (isOpen) null else app.pkg })
                    if (isOpen) {
                        FlowRow(
                            Modifier.padding(start = 12.dp, end = 12.dp, bottom = 12.dp),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            GlassButton(Icons.Outlined.OpenInBrowser, "Open") {
                                val i = ctx.packageManager.getLaunchIntentForPackage(app.pkg)
                                if (i != null) ctx.startActivity(i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) else toast(ctx, "No launcher screen for this app")
                            }
                            GlassButton(Icons.Outlined.Stop, "Force stop", Warn) {
                                if (app.guarded) toast(ctx, "This app is protected")
                                else run("${app.name} stopped", "Could not stop app") { Apps.forceStop(ctx, app.pkg) }
                            }
                            GlassButton(Icons.Outlined.CleaningServices, "Clear cache") {
                                run("Cache cleared", "Could not clear cache") { Apps.clearCache(ctx, app.pkg) }
                            }
                            GlassButton(Icons.Outlined.DeleteSweep, "Clear data", Danger) {
                                if (app.guarded) toast(ctx, "This app is protected")
                                else confirm = Triple("Clear data", "Erase all data of ${app.name}? This cannot be undone.", { Apps.clearData(ctx, app.pkg) })
                            }
                            GlassButton(Icons.Outlined.Delete, "Remove", Danger) {
                                if (app.guarded) toast(ctx, "This app is protected")
                                else confirm = Triple(
                                    "Remove app",
                                    "Remove ${app.name} for the current user? The APK stays on the system, so you can restore it from the Removed tab.",
                                    { Apps.uninstall(ctx, app.pkg) }
                                )
                            }
                        }
                    }
                }
            }
        }
    }

    confirm?.let { (title, text, f) ->
        ConfirmDialog(title, text, title, true, onConfirm = {
            confirm = null
            scope.launch {
                val r = withContext(Dispatchers.IO) { f() }
                toast(ctx, if (r) "$title done" else "$title failed")
                removed = Apps.removedApps(ctx)
                apps = withContext(Dispatchers.IO) { Apps.load(ctx) }
            }
        }, onDismiss = { confirm = null })
    }
}

