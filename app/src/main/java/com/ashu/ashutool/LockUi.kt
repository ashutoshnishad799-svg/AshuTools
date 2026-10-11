package com.ashu.ashutool

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.Backspace
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material.icons.outlined.Timer
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay

/**
 * The lock screen. Used inside the accessibility overlay (instant), the fallback LockActivity, the vault,
 * and to protect Ashutool itself. pkg == null means a screen that is not an app, shown with [title].
 * scrim is how strongly the background is dimmed. A blurred window behind needs a low value.
 * It never opens the system keyboard, so it works inside an overlay: PIN and recovery use the on-screen pad.
 */
@Composable
fun LockPanel(pkg: String?, scrim: Float, onOk: () -> Unit, onCancel: () -> Unit, title: String = "Hidden apps") {
    val ctx = LocalContext.current
    val type = remember { LockStore.type(ctx) }
    val pinLen = remember { LockStore.pinLen(ctx) }
    val name = remember(pkg, title) {
        if (pkg == null) title else try {
            ctx.packageManager.getApplicationLabel(ctx.packageManager.getApplicationInfo(pkg, 0)).toString()
        } catch (e: Exception) { pkg }
    }
    var recovery by remember { mutableStateOf(false) }
    var pin by remember(pkg, recovery) { mutableStateOf("") }
    var err by remember(pkg, recovery) { mutableStateOf(false) }
    LaunchedEffect(err) { if (err) { delay(900); err = false } }
    val len = if (recovery) 8 else pinLen

    fun press(d: String) {
        if (pin.length >= len || err) return
        pin += d
        if (pin.length == len) {
            val ok = if (recovery) LockStore.checkRecovery(ctx, pin) else LockStore.checkPin(ctx, pin)
            if (ok) {
                if (recovery) LockStore.clearCred(ctx)
                onOk()
            } else { err = true; pin = "" }
        }
    }

    val keypad = recovery || type == 0
    Box(Modifier.fillMaxSize().background(Palette.cur.bg1.copy(alpha = scrim))) {
        Column(
            Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding().padding(horizontal = 24.dp, vertical = 16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            if (pkg != null) AppIcon(pkg, 68.dp) else GradIcon(Icons.Outlined.Lock, GTeal, 68.dp)
            Spacer(Modifier.height(14.dp))
            Text(name, color = TextHi, fontSize = 22.sp, fontWeight = FontWeight.Bold)
            Text(
                if (recovery) "Enter your 8 digit recovery code" else if (type == 0) "Enter your PIN" else "Draw your pattern",
                color = TextLo, fontSize = 14.sp
            )
            Spacer(Modifier.height(20.dp))
            if (keypad) {
                Row(horizontalArrangement = Arrangement.spacedBy(if (len > 6) 10.dp else 14.dp)) {
                    repeat(len) { i ->
                        Box(
                            Modifier.size(14.dp).clip(CircleShape).background(
                                if (i < pin.length) Brush.linearGradient(listOf(GTeal.a, GTeal.b))
                                else Brush.linearGradient(listOf(Tint.copy(alpha = 0.18f), Tint.copy(alpha = 0.18f)))
                            )
                        )
                    }
                }
                Spacer(Modifier.height(10.dp))
                Text(if (err) (if (recovery) "Wrong code. Try again." else "Wrong PIN. Try again.") else " ", color = Danger, fontSize = 13.sp)
                Spacer(Modifier.height(8.dp))
                listOf(listOf("1", "2", "3"), listOf("4", "5", "6"), listOf("7", "8", "9"), listOf("", "0", "<")).forEach { row ->
                    Row(horizontalArrangement = Arrangement.spacedBy(18.dp), modifier = Modifier.padding(vertical = 6.dp)) {
                        row.forEach { k ->
                            when (k) {
                                "" -> Spacer(Modifier.size(66.dp))
                                "<" -> KeyBox(onClick = { if (pin.isNotEmpty()) pin = pin.dropLast(1) }) {
                                    Icon(Icons.AutoMirrored.Outlined.Backspace, "Delete", tint = TextHi)
                                }
                                else -> KeyBox(onClick = { press(k) }) {
                                    Text(k, color = TextHi, fontSize = 24.sp, fontWeight = FontWeight.Medium)
                                }
                            }
                        }
                    }
                }
            } else {
                PatternPad(Modifier.size(290.dp), err) { p ->
                    if (LockStore.checkPattern(ctx, p)) onOk() else err = true
                }
                Spacer(Modifier.height(8.dp))
                Text(if (err) "Wrong pattern. Try again." else " ", color = Danger, fontSize = 13.sp)
            }
            Spacer(Modifier.height(6.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                TextButton(onClick = onCancel) { Text("Cancel", color = TextLo) }
                if (recovery) TextButton(onClick = { recovery = false }) { Text("Back", color = Accent) }
                else if (LockStore.hasRecovery(ctx)) TextButton(onClick = { recovery = true }) { Text("Forgot?", color = Accent) }
            }
        }
    }
}

/** Shown over an app whose daily time is used up. Extra time needs the lock password when one is set. */
@Composable
fun LimitPanel(pkg: String, usedMin: Int, limitMin: Int, scrim: Float, onHome: () -> Unit, onBonus: () -> Unit) {
    val ctx = LocalContext.current
    var ask by remember { mutableStateOf(false) }
    val name = remember(pkg) {
        try { ctx.packageManager.getApplicationLabel(ctx.packageManager.getApplicationInfo(pkg, 0)).toString() } catch (e: Exception) { pkg }
    }
    if (ask) {
        LockPanel(pkg, scrim, onOk = { onBonus() }, onCancel = { ask = false })
    } else {
        Box(Modifier.fillMaxSize().background(Palette.cur.bg1.copy(alpha = scrim))) {
            Column(
                Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding().padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                AppIcon(pkg, 72.dp)
                Spacer(Modifier.height(16.dp))
                Text("Time limit reached", color = TextHi, fontSize = 22.sp, fontWeight = FontWeight.Bold)
                Spacer(Modifier.height(6.dp))
                Text("$name: ${fmtMin(usedMin)} used of ${fmtMin(limitMin)} today", color = TextLo, fontSize = 14.sp)
                Spacer(Modifier.height(26.dp))
                GradButton("Go home", GTeal, modifier = Modifier.fillMaxWidth(0.7f)) { onHome() }
                Spacer(Modifier.height(12.dp))
                GlassButton(Icons.Outlined.Timer, "5 more minutes") { if (LockStore.hasCred(ctx)) ask = true else onBonus() }
            }
        }
    }
}

@Composable
private fun KeyBox(onClick: () -> Unit, content: @Composable () -> Unit) {
    val pal = Palette.cur
    Box(
        Modifier.bounceClick(onClick = onClick).size(66.dp).clip(CircleShape)
            .background(Brush.verticalGradient(listOf(pal.tint.copy(alpha = pal.glassTop + 0.04f), pal.tint.copy(alpha = pal.glassBottom))))
            .border(1.dp, Brush.linearGradient(listOf(pal.tint.copy(alpha = pal.bA), pal.tint.copy(alpha = pal.bB))), CircleShape),
        contentAlignment = Alignment.Center
    ) { content() }
}
