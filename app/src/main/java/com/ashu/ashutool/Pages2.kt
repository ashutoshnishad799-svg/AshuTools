@file:OptIn(ExperimentalLayoutApi::class)

package com.ashu.ashutool

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.OpenInNew
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

@Composable
private fun rememberApps(): MutableState<List<AppItem>?> {
    val ctx = LocalContext.current
    val st = remember { mutableStateOf<List<AppItem>?>(null) }
    LaunchedEffect(Unit) { st.value = withContext(Dispatchers.IO) { Apps.load(ctx) } }
    return st
}

@Composable
private fun AppHead(app: AppItem, modifier: Modifier = Modifier, trailing: @Composable RowScope.() -> Unit = {}) {
    Row(modifier.fillMaxWidth().padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
        AppIcon(app.pkg, 44.dp)
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(app.name, color = TextHi, fontSize = 14.sp, fontWeight = FontWeight.Medium, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(app.pkg, color = TextLo, fontSize = 11.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        trailing()
    }
}

// ------------------------------------------------------------------ Manager

@Composable
fun ManagerPage(back: () -> Unit) {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    var apps by rememberApps()
    var query by remember { mutableStateOf("") }
    var system by remember { mutableStateOf(false) }
    var open by remember { mutableStateOf<String?>(null) }
    var confirm by remember { mutableStateOf<Triple<String, String, () -> Boolean>?>(null) }

    val shown = remember(apps, query, system) {
        (apps ?: emptyList()).filter {
            it.system == system && (query.isBlank() || it.name.contains(query, true) || it.pkg.contains(query, true))
        }
    }
    fun run(okMsg: String, failMsg: String, f: () -> Boolean) {
        scope.launch {
            val r = withContext(Dispatchers.IO) { f() }
            toast(ctx, if (r) okMsg else failMsg)
        }
    }

    Page("App manager", "Stop, clean and remove apps", GPink, Icons.Rounded.Apps, back) {
        item { SearchField(query) { query = it } }
        item {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Chip("User apps", !system, GPink) { system = false }
                Chip("System apps", system, GPink) { system = true }
            }
        }
        if (apps == null) item { Hint("Loading apps") }
        else if (shown.isEmpty()) item { Hint("No apps in this view.") }
        items(shown, key = { it.pkg }) { app ->
            val isOpen = open == app.pkg
            val guarded = app.pkg in Apps.protectedPkgs
            Glass(radius = 18.dp, pad = 0.dp) {
                AppHead(app, Modifier.clickable { open = if (isOpen) null else app.pkg })
                if (isOpen) {
                    FlowRow(
                        Modifier.padding(start = 12.dp, end = 12.dp, bottom = 12.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        GlassButton(Icons.AutoMirrored.Rounded.OpenInNew, "Open") {
                            val i = ctx.packageManager.getLaunchIntentForPackage(app.pkg)
                            if (i != null) ctx.startActivity(i) else toast(ctx, "No launcher screen for this app")
                        }
                        GlassButton(Icons.Rounded.Stop, "Force stop", Warn) {
                            if (guarded) toast(ctx, "This app is protected")
                            else run("${app.name} stopped", "Could not stop app") { Apps.forceStop(app.pkg) }
                        }
                        GlassButton(Icons.Rounded.CleaningServices, "Clear cache") {
                            run("Cache cleared", "Could not clear cache") { Apps.clearCache(app.pkg) }
                        }
                        GlassButton(Icons.Rounded.DeleteSweep, "Clear data", Danger) {
                            if (guarded) toast(ctx, "This app is protected")
                            else confirm = Triple("Clear data", "Erase all data of ${app.name}?", { Root.ok("pm clear ${app.pkg}") })
                        }
                        GlassButton(Icons.Rounded.Delete, "Uninstall", Danger) {
                            if (guarded) toast(ctx, "This app is protected")
                            else confirm = Triple("Uninstall", "Remove ${app.name} for the current user?", { Root.ok("pm uninstall --user 0 ${app.pkg}") })
                        }
                    }
                }
            }
        }
    }

    confirm?.let { (title, text, f) ->
        ConfirmDialog(title, text, title, true, onConfirm = {
            confirm = null
            run("$title done", "$title failed", f)
            apps = null
            scope.launch { apps = withContext(Dispatchers.IO) { delay(600); Apps.load(ctx) } }
        }, onDismiss = { confirm = null })
    }
}

// --------------------------------------------------------------------- Lock

@Composable
fun LockPage(back: () -> Unit) {
    val ctx = LocalContext.current
    val apps by rememberApps()
    var locked by remember { mutableStateOf(LockStore.locked(ctx)) }
    var query by remember { mutableStateOf("") }
    var filter by remember { mutableIntStateOf(0) }
    var pinFor by remember { mutableStateOf<String?>(null) }

    val shown = remember(apps, query, filter, locked) {
        (apps ?: emptyList()).filter {
            val inFilter = when (filter) {
                0 -> it.pkg in locked
                1 -> !it.system
                else -> it.system
            }
            inFilter && (query.isBlank() || it.name.contains(query, true) || it.pkg.contains(query, true))
        }
    }
    fun setLock(pkg: String, on: Boolean) {
        LockStore.setLocked(ctx, pkg, on)
        locked = LockStore.locked(ctx)
    }

    Page("App lock", "Ask for a PIN before apps open", GTeal, Icons.Rounded.Lock, back) {
        item {
            Glass {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    GradIcon(Icons.Rounded.Lock, GTeal, 48.dp)
                    Spacer(Modifier.width(14.dp))
                    Column(Modifier.weight(1f)) {
                        GradText("${locked.size}", GTeal, 28.sp)
                        Text(if (locked.size == 1) "app locked" else "apps locked", color = TextLo, fontSize = 12.sp)
                    }
                    GradButton(if (LockStore.hasPin(ctx)) "Change PIN" else "Set PIN", GTeal) { pinFor = "*" }
                }
            }
        }
        item { SearchField(query) { query = it } }
        item {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Chip("Locked", filter == 0) { filter = 0 }
                Chip("User apps", filter == 1) { filter = 1 }
                Chip("System apps", filter == 2) { filter = 2 }
            }
        }
        if (apps == null) item { Hint("Loading apps") }
        else if (shown.isEmpty()) item { Hint(if (filter == 0) "No locked apps yet. Open User apps and switch one on." else "No apps in this view.") }
        items(shown, key = { it.pkg }) { app ->
            val on = app.pkg in locked
            Glass(radius = 18.dp, pad = 0.dp) {
                AppHead(app) {
                    Switch(
                        checked = on,
                        onCheckedChange = {
                            if (!it) setLock(app.pkg, false)
                            else if (!LockStore.hasPin(ctx)) pinFor = app.pkg
                            else setLock(app.pkg, true)
                        },
                        colors = glassSwitchColors()
                    )
                }
            }
        }
    }

    pinFor?.let { target ->
        PinDialog(
            needOld = LockStore.hasPin(ctx),
            onDismiss = { pinFor = null },
            onSave = { pin ->
                LockStore.setPin(ctx, pin)
                if (target != "*") setLock(target, true)
                pinFor = null
                toast(ctx, "PIN saved")
            }
        )
    }
}

// --------------------------------------------------------------------- Hide

@Composable
fun HidePage(back: () -> Unit) {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    var apps by rememberApps()
    var query by remember { mutableStateOf("") }
    var filter by remember { mutableIntStateOf(0) }

    val hiddenCount = (apps ?: emptyList()).count { it.hidden }
    val shown = remember(apps, query, filter) {
        (apps ?: emptyList()).filter {
            val inFilter = when (filter) {
                0 -> it.hidden
                1 -> !it.system
                else -> it.system
            }
            inFilter && (query.isBlank() || it.name.contains(query, true) || it.pkg.contains(query, true))
        }
    }

    Page("App hide", "Remove apps from launcher and recents", GViolet, Icons.Rounded.VisibilityOff, back) {
        item {
            Glass {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    GradIcon(Icons.Rounded.VisibilityOff, GViolet, 48.dp)
                    Spacer(Modifier.width(14.dp))
                    Column(Modifier.weight(1f)) {
                        GradText("$hiddenCount", GViolet, 28.sp)
                        Text("apps hidden", color = TextLo, fontSize = 12.sp)
                    }
                    if (hiddenCount > 0) GradButton("Unhide all", GViolet) {
                        val cmds = (apps ?: emptyList()).filter { it.hidden }.map { "pm enable ${it.pkg}" }.toTypedArray()
                        scope.launch {
                            withContext(Dispatchers.IO) { Root.ok(*cmds) }
                            apps = apps?.map { it.copy(hidden = false) }
                            toast(ctx, "All apps are visible again")
                        }
                    }
                }
            }
        }
        item { SearchField(query) { query = it } }
        item {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Chip("Hidden", filter == 0, GViolet) { filter = 0 }
                Chip("User apps", filter == 1, GViolet) { filter = 1 }
                Chip("System apps", filter == 2, GViolet) { filter = 2 }
            }
        }
        if (apps == null) item { Hint("Loading apps") }
        else if (shown.isEmpty()) item { Hint(if (filter == 0) "Nothing is hidden. Open User apps and switch one on." else "No apps in this view.") }
        items(shown, key = { it.pkg }) { app ->
            Glass(radius = 18.dp, pad = 0.dp) {
                AppHead(app) {
                    Switch(
                        checked = app.hidden,
                        onCheckedChange = { hide ->
                            if (app.pkg in Apps.protectedPkgs) toast(ctx, "This app is protected")
                            else scope.launch {
                                val r = withContext(Dispatchers.IO) { Apps.setHidden(app.pkg, hide) }
                                if (r) apps = apps?.map { if (it.pkg == app.pkg) it.copy(hidden = hide) else it }
                                else toast(ctx, "Could not change visibility")
                            }
                        },
                        colors = glassSwitchColors()
                    )
                }
            }
        }
    }
}
