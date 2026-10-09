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

// --------------------------------------------------------------------- Lock

@Composable
fun LockPage() {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    val apps by rememberApps()
    var locked by remember { mutableStateOf(LockStore.locked(ctx)) }
    var query by remember { mutableStateOf("") }
    var filter by remember { mutableIntStateOf(0) }
    var pinFor by remember { mutableStateOf<String?>(null) }
    var open by remember { mutableStateOf<String?>(null) }
    var net by remember { mutableStateOf(LockStore.netModes(ctx)) }
    var relock by remember { mutableIntStateOf(LockStore.int(ctx, "relock_ms", 0)) }
    var defNet by remember { mutableIntStateOf(LockStore.int(ctx, "lock_def_net", 0)) }
    val a11y = AshuAccessibility.connected

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
    fun applyNet() { scope.launch(Dispatchers.IO) { Firewall.applyAll(ctx) } }
    fun setLock(pkg: String, on: Boolean) {
        LockStore.setLocked(ctx, pkg, on)
        locked = LockStore.locked(ctx)
        if (on && defNet != 0) {
            LockStore.setNetMode(ctx, pkg, defNet)
            net = LockStore.netModes(ctx)
            applyNet()
        }
    }

    Page("App lock", "PIN or pattern before apps open", GTeal, Icons.Outlined.Lock) {
        item {
            Glass {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    GradIcon(Icons.Outlined.Lock, GTeal, 48.dp)
                    Spacer(Modifier.width(14.dp))
                    Column(Modifier.weight(1f)) {
                        GradText("${locked.size}", GTeal, 28.sp)
                        Text(if (locked.size == 1) "app locked" else "apps locked", color = TextLo, fontSize = 12.sp)
                    }
                    GradButton(if (LockStore.hasCred(ctx)) "Change lock" else "Set lock", GTeal) { pinFor = "*" }
                }
            }
        }
        item {
            Glass {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    GradIcon(Icons.Outlined.Bolt, if (a11y) GGreen else GFire, 40.dp)
                    Spacer(Modifier.width(12.dp))
                    Column(Modifier.weight(1f)) {
                        Text(if (a11y) "Instant lock is on" else "Instant lock is off", color = TextHi, fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
                        Text(
                            if (a11y) "The lock covers an app the moment it opens." else "Without it the lock appears a moment late.",
                            color = TextLo, fontSize = 12.sp
                        )
                    }
                    if (!a11y) GradButton("Turn on", GFire) {
                        scope.launch {
                            val ok = withContext(Dispatchers.IO) { LockStore.setBool(ctx, "use_a11y", true); Root.setA11y(ctx, true) }
                            toast(ctx, if (ok) "Enabling instant lock" else "Could not enable")
                        }
                    }
                }
            }
        }
        item { PrefSwitch("lock_blur", true, Icons.Outlined.BlurOn, GViolet, "Blur behind lock screen", "Frosted glass over the locked app (Android 12 and up)") }
        item { PrefSwitch("relock_off", true, Icons.Outlined.ScreenLockPortrait, GBlue, "Lock again when screen turns off", "Ask for the password after every screen off") }
        item {
            Glass {
                SectionTitle("Lock again after leaving an app")
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf(0 to "Immediately", 30_000 to "30 s", 60_000 to "1 min", 300_000 to "5 min").forEach { (ms, l) ->
                        Chip(l, relock == ms) { relock = ms; LockStore.setInt(ctx, "relock_ms", ms) }
                    }
                }
                SubLabel("Network for newly locked apps")
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf(0 to "Allow", 1 to "No mobile data", 3 to "Block all").forEach { (m, l) ->
                        Chip(l, defNet == m, GBlue) { defNet = m; LockStore.setInt(ctx, "lock_def_net", m) }
                    }
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
            val forbidden = Safety.lockForbidden(ctx, app.pkg)
            Glass(Modifier.animateItem(), radius = 18.dp, pad = 0.dp) {
                AppHead(app, Modifier.clickable(enabled = on) { open = if (open == app.pkg) null else app.pkg }) {
                    if (forbidden) Text("Cannot lock", color = TextLo, fontSize = 11.sp)
                    else Switch(
                        checked = on,
                        onCheckedChange = {
                            if (!it) setLock(app.pkg, false)
                            else if (!LockStore.hasCred(ctx)) pinFor = app.pkg
                            else setLock(app.pkg, true)
                        },
                        colors = glassSwitchColors()
                    )
                }
                if (on && open == app.pkg) {
                    Column(Modifier.padding(start = 12.dp, end = 12.dp, bottom = 12.dp)) {
                        Text("Network for this app", color = TextLo, fontSize = 12.sp)
                        Spacer(Modifier.height(8.dp))
                        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            listOf(0 to "Allow", 1 to "No mobile data", 2 to "No Wi-Fi", 3 to "Block all").forEach { (m, l) ->
                                Chip(l, (net[app.pkg] ?: 0) == m, GBlue) {
                                    if (app.guarded) toast(ctx, "This app is protected")
                                    else { LockStore.setNetMode(ctx, app.pkg, m); net = LockStore.netModes(ctx); applyNet() }
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    pinFor?.let { target ->
        CredentialDialog(
            onDismiss = { pinFor = null },
            onSaved = {
                if (target != "*") setLock(target, true)
                pinFor = null
                toast(ctx, "Lock saved")
            }
        )
    }
}

// --------------------------------------------------------------------- Hide

@Composable
fun HidePage() {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    var apps by rememberApps()
    var query by remember { mutableStateOf("") }
    var filter by remember { mutableIntStateOf(0) }
    var mode by remember { mutableIntStateOf(LockStore.int(ctx, "hide_mode", 0)) }
    var code by remember { mutableStateOf(LockStore.str(ctx, "vault_code", "")) }

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

    Page("App hide", "Hide apps and open them from a vault", GViolet, Icons.Outlined.VisibilityOff) {
        item {
            Glass {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    GradIcon(Icons.Outlined.VisibilityOff, GViolet, 48.dp)
                    Spacer(Modifier.width(14.dp))
                    Column(Modifier.weight(1f)) {
                        GradText("$hiddenCount", GViolet, 28.sp)
                        Text("apps hidden", color = TextLo, fontSize = 12.sp)
                    }
                    GradButton("Open vault", GViolet) {
                        ctx.startActivity(Intent(ctx, VaultActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
                    }
                }
            }
        }
        item {
            Glass {
                SectionTitle("How to hide")
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Chip("Freeze", mode == 0, GViolet) { mode = 0; LockStore.setInt(ctx, "hide_mode", 0) }
                    Chip("Hide completely", mode == 1, GViolet) { mode = 1; LockStore.setInt(ctx, "hide_mode", 1) }
                }
                Text(
                    if (mode == 0) "Freeze turns the app off and removes its icon. It still shows in system settings."
                    else "Hide completely makes the app invisible to the system, as if it was not installed.",
                    color = TextLo, fontSize = 12.sp, modifier = Modifier.padding(top = 10.dp)
                )
            }
        }
        item { PrefSwitch("hide_stop", true, Icons.Outlined.Stop, GPink, "Stop the app when hidden", "Closes it in the background so nothing keeps running") }
        item { PrefSwitch("hide_rehide", true, Icons.Outlined.Replay, GBlue, "Hide again when I leave", "Apps opened from the vault go back into hiding") }
        item {
            Glass {
                SectionTitle("Secret access")
                Text(
                    if (code.length in 4..6) "Dial *#*#$code#*#* in the phone app to open the vault."
                    else "Choose 4 to 6 digits, then dial *#*#code#*#* in the phone app.",
                    color = TextLo, fontSize = 12.sp
                )
                Spacer(Modifier.height(10.dp))
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    PinField(code, "Secret code") { code = it }
                    GradButton("Save code", GViolet) {
                        if (code.length in 4..6) { LockStore.setStr(ctx, "vault_code", code); toast(ctx, "Code saved") }
                        else toast(ctx, "Use 4 to 6 digits")
                    }
                }
            }
        }
        item { PrefSwitch("vault_dial", true, Icons.Outlined.Dialpad, GViolet, "Open with dialer code", "Works while the Ashutool service is running") }
        item { PrefSwitch("vault_vol", false, Icons.Outlined.Keyboard, GBlue, "Open with volume keys", "Volume up, up, down. Needs instant lock turned on") }
        item { PrefSwitch("vault_auth", true, Icons.Outlined.Password, GTeal, "Ask lock password in vault", "Uses the same PIN or pattern as app lock") }
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
            Glass(Modifier.animateItem(), radius = 18.dp, pad = 0.dp) {
                AppHead(app) {
                    Switch(
                        checked = app.hidden,
                        onCheckedChange = { hide ->
                            if (app.guarded) toast(ctx, "This app is protected")
                            else scope.launch {
                                val r = withContext(Dispatchers.IO) { if (hide) Apps.hide(ctx, app.pkg, mode) else Apps.unhide(ctx, app.pkg) }
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
