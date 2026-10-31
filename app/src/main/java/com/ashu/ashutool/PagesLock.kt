/*
 * Ashutool - root toolkit for custom ROMs
 * Copyright (C) 2026 Ashutosh Nishad
 * Telegram: @ashutosh_07x (https://t.me/ashutosh_07x)
 *
 * Free software under the GNU GPL v3.0 or later, with the attribution terms in NOTICE.
 * Keep this header and the author credit in every copy and modified version.
 */

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
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.*

@Composable
fun LockPage() = SubHost(root = { open -> LockMain(open) }, sub = { _, back -> LockSettings(back) })

@Composable
private fun LockMain(open: (String) -> Unit) {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    val apps by rememberApps()
    var locked by remember { mutableStateOf(LockStore.locked(ctx)) }
    var query by remember { mutableStateOf("") }
    var filter by remember { mutableIntStateOf(0) }
    var pinFor by remember { mutableStateOf<String?>(null) }
    var openRow by remember { mutableStateOf<String?>(null) }
    var net by remember { mutableStateOf(LockStore.netModes(ctx)) }
    val a11y = AshuAccessibility.connected
    val shown = remember(apps, query, filter, locked) {
        (apps ?: emptyList()).filter {
            (when (filter) { 0 -> it.pkg in locked; 1 -> !it.system; else -> it.system }) &&
                (query.isBlank() || it.name.contains(query, true) || it.pkg.contains(query, true))
        }
    }
    fun applyNet() { scope.launch(Dispatchers.IO) { Firewall.applyAll(ctx) } }
    fun setLock(pkg: String, on: Boolean) {
        LockStore.setLocked(ctx, pkg, on)
        locked = LockStore.locked(ctx)
        val d = LockStore.int(ctx, "lock_def_net", 0)
        if (on && d != 0) { LockStore.setNetMode(ctx, pkg, d); net = LockStore.netModes(ctx); applyNet() }
    }

    Page("App lock", "PIN or pattern before apps open", GTeal, Icons.Outlined.Lock, action = { IconBtn(Icons.Outlined.Settings, "Lock settings") { open("settings") } }) {
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
                if (locked.isNotEmpty() && !LockStore.hasCred(ctx)) {
                    Text("No lock is set, so these apps are open. Set a new PIN or pattern.", color = Warn, fontSize = 12.sp, modifier = Modifier.padding(top = 10.dp))
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
                        Text(if (a11y) "Locks cover an app the moment it opens, and blur it in recents." else "Without it the lock appears a moment late.", color = TextLo, fontSize = 12.sp)
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
                AppHead(app, Modifier.clickable(enabled = on) { openRow = if (openRow == app.pkg) null else app.pkg }) {
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
                if (on && openRow == app.pkg) {
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
        CredentialDialog(onDismiss = { pinFor = null }, onSaved = {
            if (target != "*") setLock(target, true)
            pinFor = null
            toast(ctx, "Lock saved")
        })
    }
}

@Composable
private fun LockSettings(back: () -> Unit) {
    val ctx = LocalContext.current
    var cred by remember { mutableStateOf(false) }
    var relock by remember { mutableIntStateOf(LockStore.int(ctx, "relock_ms", 0)) }
    var defNet by remember { mutableIntStateOf(LockStore.int(ctx, "lock_def_net", 0)) }
    Page("Lock settings", "Method, recovery and behaviour", GTeal, Icons.Outlined.Settings, onBack = back) {
        item {
            ActionRow(Icons.Outlined.Password, GTeal, "Lock method", if (!LockStore.hasCred(ctx)) "Not set" else if (LockStore.type(ctx) == 0) "PIN" else "Pattern", { cred = true })
        }
        item {
            Glass {
                Text("Recovery code", color = TextHi, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
                Text(
                    if (LockStore.hasRecovery(ctx)) "A recovery code is set. If you forget the lock, tap Forgot on the lock screen. Changing your lock gives you a new code."
                    else "Set a lock to get a one-time recovery code.",
                    color = TextLo, fontSize = 12.sp, modifier = Modifier.padding(top = 6.dp)
                )
            }
        }
        item { PrefSwitch("protect_self", true, Icons.Outlined.Shield, GGreen, "Lock Ashutool itself", "Ask for the lock when this app opens") }
        item { PrefSwitch("lock_blur", true, Icons.Outlined.BlurOn, GViolet, "Blur behind lock screen", "Frosted glass over the locked app (Android 12 and up)") }
        item { PrefSwitch("recents_guard", true, Icons.Outlined.Layers, GBlue, "Blur locked apps in recents", "Hides the preview of locked apps in the app switcher") }
        item { PrefSwitch("relock_off", true, Icons.Outlined.ScreenLockPortrait, GCyan, "Lock again when screen turns off", "Ask for the lock after every screen off") }
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
    }
    if (cred) CredentialDialog(onDismiss = { cred = false }, onSaved = { cred = false; toast(ctx, "Lock saved") })
}

// --------------------------------------------------------------------- Hide

@Composable
fun HidePage() = SubHost(root = { open -> HideMain(open) }, sub = { _, back -> HideSettings(back) })

@Composable
private fun HideMain(open: (String) -> Unit) {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    var apps by rememberApps()
    var query by remember { mutableStateOf("") }
    var filter by remember { mutableIntStateOf(0) }
    val hiddenCount = (apps ?: emptyList()).count { it.hidden }
    val shown = remember(apps, query, filter) {
        (apps ?: emptyList()).filter {
            (when (filter) { 0 -> it.hidden; 1 -> !it.system; else -> it.system }) &&
                (query.isBlank() || it.name.contains(query, true) || it.pkg.contains(query, true))
        }
    }
    Page("App hide", "Hide apps and open them from a vault", GViolet, Icons.Outlined.VisibilityOff, action = { IconBtn(Icons.Outlined.Settings, "Hide settings") { open("settings") } }) {
        item {
            Glass {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    GradIcon(Icons.Outlined.VisibilityOff, GViolet, 48.dp)
                    Spacer(Modifier.width(14.dp))
                    Column(Modifier.weight(1f)) {
                        GradText("$hiddenCount", GViolet, 28.sp)
                        Text("apps hidden", color = TextLo, fontSize = 12.sp)
                    }
                    GradButton("Open vault", GViolet) { ctx.startActivity(Intent(ctx, VaultActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }
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
            Glass(Modifier.animateItem(), radius = 18.dp, pad = 0.dp) {
                AppHead(app) {
                    Switch(
                        checked = app.hidden,
                        onCheckedChange = { hide ->
                            if (app.guarded) toast(ctx, "This app is protected")
                            else scope.launch {
                                val mode = LockStore.int(ctx, "hide_mode", 0)
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

@Composable
private fun HideSettings(back: () -> Unit) {
    val ctx = LocalContext.current
    var mode by remember { mutableIntStateOf(LockStore.int(ctx, "hide_mode", 0)) }
    var code by remember { mutableStateOf(LockStore.str(ctx, "vault_code", "")) }
    val saved = LockStore.str(ctx, "vault_code", "")
    Page("Hide settings", "Secret codes and vault", GViolet, Icons.Outlined.Settings, onBack = back) {
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
        item { PrefSwitch("hide_stop", true, Icons.Outlined.Stop, GPink, "Stop the app when hidden", "Closes it so nothing keeps running in the background") }
        item { PrefSwitch("hide_rehide", true, Icons.Outlined.Replay, GBlue, "Hide again when I leave", "Apps opened from the vault go back into hiding") }
        item {
            Glass {
                SectionTitle("Secret code", if (saved.length in 4..6) "Saved" else "Not set")
                Text(
                    if (saved.length in 4..6) "Dial *#*#$saved#*#* in the phone app. It opens the vault the moment the last key is typed."
                    else "Choose 4 to 6 digits, then dial *#*#code#*#* in the phone app.",
                    color = TextLo, fontSize = 12.sp
                )
                Spacer(Modifier.height(10.dp))
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    PinField(code, "Secret code") { code = it }
                    GradButton("Save code", GViolet) {
                        if (code.length in 4..6) { LockStore.setStr(ctx, "vault_code", code); toast(ctx, "Code saved") } else toast(ctx, "Use 4 to 6 digits")
                    }
                }
            }
        }
        item { PrefSwitch("vault_dial", true, Icons.Outlined.Dialpad, GViolet, "Open with dialer code", "Works from any dialer, no call button needed") }
        item { PrefSwitch("vault_vol", false, Icons.Outlined.Keyboard, GBlue, "Open with volume keys", "Volume up, up, down. Needs instant lock turned on") }
        item { PrefSwitch("vault_auth", true, Icons.Outlined.Password, GTeal, "Ask lock password in vault", "Uses the same PIN or pattern as app lock") }
        item {
            ActionRow(Icons.Outlined.OpenInBrowser, GViolet, "Open vault now", "Test it without dialing", {
                ctx.startActivity(Intent(ctx, VaultActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
            })
        }
    }
}
