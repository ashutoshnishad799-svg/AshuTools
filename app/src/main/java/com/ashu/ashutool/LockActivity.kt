package com.ashu.ashutool

import android.content.Intent
import android.graphics.Color as AColor
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.OnBackPressedCallback
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.Backspace
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
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

class LockActivity : ComponentActivity() {
    private var pkg by mutableStateOf("")

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.dark(AColor.TRANSPARENT),
            navigationBarStyle = SystemBarStyle.dark(AColor.TRANSPARENT)
        )
        pkg = intent.getStringExtra("pkg") ?: run { finish(); return }
        setContent {
            AshuTheme {
                Aurora { PinScreen(pkg, onOk = { LockStore.unlocked = pkg; finish() }) }
            }
        }
        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() = goHome()
        })
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        intent.getStringExtra("pkg")?.let { pkg = it }
    }

    private fun goHome() {
        startActivity(
            Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        )
        finish()
    }
}

@Composable
fun PinScreen(pkg: String, onOk: () -> Unit) {
    val ctx = LocalContext.current
    val len = remember { LockStore.pinLen(ctx) }
    val name = remember(pkg) {
        runCatching {
            ctx.packageManager.getApplicationLabel(ctx.packageManager.getApplicationInfo(pkg, 0)).toString()
        }.getOrDefault(pkg)
    }
    var pin by remember(pkg) { mutableStateOf("") }
    var err by remember(pkg) { mutableStateOf(false) }

    fun press(d: String) {
        if (pin.length >= len) return
        pin += d
        err = false
        if (pin.length == len) {
            if (LockStore.check(ctx, pin)) onOk() else { err = true; pin = "" }
        }
    }

    Column(
        Modifier.fillMaxSize().systemBarsPadding().padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        AppIcon(pkg, 72.dp)
        Spacer(Modifier.height(16.dp))
        Text(name, color = TextHi, fontSize = 22.sp, fontWeight = FontWeight.Bold)
        Text("Enter your PIN to continue", color = TextLo, fontSize = 14.sp)
        Spacer(Modifier.height(28.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(14.dp)) {
            repeat(len) { i ->
                Box(
                    Modifier.size(14.dp).clip(CircleShape)
                        .then(if (i < pin.length) Modifier.background(GTeal.d()) else Modifier.background(Color.White.copy(alpha = 0.15f)))
                )
            }
        }
        Spacer(Modifier.height(14.dp))
        Text(if (err) "Wrong PIN. Try again." else " ", color = Danger, fontSize = 13.sp)
        Spacer(Modifier.height(18.dp))
        listOf(listOf("1", "2", "3"), listOf("4", "5", "6"), listOf("7", "8", "9"), listOf("", "0", "<")).forEach { row ->
            Row(horizontalArrangement = Arrangement.spacedBy(20.dp), modifier = Modifier.padding(vertical = 8.dp)) {
                row.forEach { k ->
                    when (k) {
                        "" -> Spacer(Modifier.size(76.dp))
                        "<" -> KeyBox(onClick = { if (pin.isNotEmpty()) pin = pin.dropLast(1) }) {
                            Icon(Icons.AutoMirrored.Rounded.Backspace, "Delete", tint = TextHi)
                        }
                        else -> KeyBox(onClick = { press(k) }) {
                            Text(k, color = TextHi, fontSize = 26.sp, fontWeight = FontWeight.Medium)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun KeyBox(onClick: () -> Unit, content: @Composable () -> Unit) {
    Box(
        Modifier.size(76.dp).clip(CircleShape)
            .background(Brush.verticalGradient(listOf(Color.White.copy(alpha = 0.18f), Color.White.copy(alpha = 0.06f))))
            .border(
                1.dp,
                Brush.linearGradient(listOf(Color.White.copy(alpha = 0.40f), Color.White.copy(alpha = 0.05f))),
                CircleShape
            )
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center
    ) { content() }
}
