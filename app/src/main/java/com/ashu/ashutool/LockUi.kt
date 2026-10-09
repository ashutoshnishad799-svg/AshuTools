package com.ashu.ashutool

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.Backspace
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay

/**
 * The lock screen. Used inside the accessibility overlay (instant), the fallback LockActivity and the vault.
 * pkg == null means the vault is asking for the password.
 * scrim is how strongly the background is dimmed. A blurred window behind needs a low value.
 */
@Composable
fun LockPanel(pkg: String?, scrim: Float, onOk: () -> Unit, onCancel: () -> Unit) {
    val ctx = LocalContext.current
    val type = remember { LockStore.type(ctx) }
    val len = remember { LockStore.pinLen(ctx) }
    val name = remember(pkg) {
        if (pkg == null) "Hidden apps" else try {
            ctx.packageManager.getApplicationLabel(ctx.packageManager.getApplicationInfo(pkg, 0)).toString()
        } catch (e: Exception) { pkg }
    }
    var pin by remember(pkg) { mutableStateOf("") }
    var err by remember(pkg) { mutableStateOf(false) }
    LaunchedEffect(err) { if (err) { delay(900); err = false } }

    fun press(d: String) {
        if (pin.length >= len || err) return
        pin += d
        if (pin.length == len) {
            if (LockStore.checkPin(ctx, pin)) onOk() else { err = true; pin = "" }
        }
    }

    val base = Palette.cur.bg1
    Box(Modifier.fillMaxSize().background(base.copy(alpha = scrim))) {
        Column(
            Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding().padding(horizontal = 24.dp, vertical = 16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            if (pkg != null) AppIcon(pkg, 68.dp) else GradIcon(Icons.Outlined.Lock, GTeal, 68.dp)
            Spacer(Modifier.height(14.dp))
            Text(name, color = TextHi, fontSize = 22.sp, fontWeight = FontWeight.Bold)
            Text(if (type == 0) "Enter your PIN" else "Draw your pattern", color = TextLo, fontSize = 14.sp)
            Spacer(Modifier.height(22.dp))
            if (type == 0) {
                Row(horizontalArrangement = Arrangement.spacedBy(14.dp)) {
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
                Text(if (err) "Wrong PIN. Try again." else " ", color = Danger, fontSize = 13.sp)
                Spacer(Modifier.height(10.dp))
                listOf(listOf("1", "2", "3"), listOf("4", "5", "6"), listOf("7", "8", "9"), listOf("", "0", "<")).forEach { row ->
                    Row(horizontalArrangement = Arrangement.spacedBy(18.dp), modifier = Modifier.padding(vertical = 6.dp)) {
                        row.forEach { k ->
                            when (k) {
                                "" -> Spacer(Modifier.size(68.dp))
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
            Spacer(Modifier.height(8.dp))
            TextButton(onClick = onCancel) { Text("Cancel", color = TextLo) }
        }
    }
}

@Composable
private fun KeyBox(onClick: () -> Unit, content: @Composable () -> Unit) {
    val pal = Palette.cur
    Box(
        Modifier.bounceClick(onClick = onClick).size(68.dp).clip(CircleShape)
            .background(Brush.verticalGradient(listOf(pal.tint.copy(alpha = pal.glassTop + 0.04f), pal.tint.copy(alpha = pal.glassBottom))))
            .border(1.dp, Brush.linearGradient(listOf(pal.tint.copy(alpha = pal.bA), pal.tint.copy(alpha = pal.bB))), CircleShape),
        contentAlignment = Alignment.Center
    ) { content() }
}
