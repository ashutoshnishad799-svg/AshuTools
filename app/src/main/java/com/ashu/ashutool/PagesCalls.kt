package com.ashu.ashutool

import android.Manifest
import android.content.pm.PackageManager
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@Composable
fun CallsPage() = SubHost(
    root = { open -> CallsMain(open) },
    sub = { k, back -> if (k == "incoming") IncomingSub(back) else DuringSub(back) }
)

@Composable
private fun CallsMain(open: (String) -> Unit) {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    var ok by remember { mutableStateOf(has(ctx)) }
    Page("Calls", "Incoming banner, auto speaker, call controls", GGreen, Icons.Outlined.Call) {
        item {
            Glass {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    GradIcon(Icons.Outlined.Call, if (ok) GGreen else GFire, 44.dp)
                    Spacer(Modifier.width(12.dp))
                    Column(Modifier.weight(1f)) {
                        Text(if (ok) "Call access is ready" else "Call access is missing", color = TextHi, fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
                        Text("Ashutool grants phone state and answer permissions to itself through root.", color = TextLo, fontSize = 12.sp)
                    }
                    if (!ok) GradButton("Fix", GFire) {
                        scope.launch { withContext(Dispatchers.IO) { Root.bootstrap(ctx) }; ok = has(ctx); toast(ctx, if (ok) "Ready" else "Restart the app") }
                    }
                }
            }
        }
        item { NavRow(Icons.Outlined.NotificationsActive, GGreen, "Incoming calls", "Banner instead of full screen, auto answer") { open("incoming") } }
        item { NavRow(Icons.Outlined.Speaker, GBlue, "During a call", "Auto speaker, floating call controls") { open("during") } }
    }
}

private fun has(ctx: android.content.Context) =
    ContextCompat.checkSelfPermission(ctx, Manifest.permission.READ_PHONE_STATE) == PackageManager.PERMISSION_GRANTED

@Composable
private fun IncomingSub(back: () -> Unit) {
    val ctx = LocalContext.current
    var sec by remember { mutableIntStateOf(LockStore.int(ctx, "calls_auto_sec", 3)) }
    Page("Incoming calls", "Keep your app in front", GGreen, Icons.Outlined.NotificationsActive, onBack = back) {
        item { PrefSwitch("calls_banner", true, Icons.Outlined.Call, GGreen, "Compact call banner", "A small answer and reject banner at the top") }
        item { PrefSwitch("calls_nofull", false, Icons.Outlined.PhoneAndroid, GBlue, "Do not take over the screen", "Sends you back to the app you were using when a call rings. Needs instant lock") }
        item { PrefSwitch("calls_auto", false, Icons.Outlined.PlayCircle, GFire, "Auto answer", "Answers by itself after a short wait") }
        item {
            Glass {
                SectionTitle("Auto answer after", "$sec s")
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf(2, 3, 5, 8).forEach { s -> Chip("$s s", sec == s, GFire) { sec = s; LockStore.setInt(ctx, "calls_auto_sec", s) } }
                }
            }
        }
    }
}

@Composable
private fun DuringSub(back: () -> Unit) {
    Page("During a call", "Controls while you talk", GBlue, Icons.Outlined.Speaker, onBack = back) {
        item { PrefSwitch("calls_speaker", false, Icons.Outlined.Speaker, GBlue, "Auto speaker", "Turns the speaker on as soon as the call is answered") }
        item { PrefSwitch("calls_mini", true, Icons.Outlined.CallEnd, GRed, "Floating call controls", "See-through end, speaker and mute buttons. Drag them anywhere or hide them") }
        item {
            Glass { Text("The speaker switch depends on your dialer and Android version. If it does not stick, use the floating speaker button.", color = TextLo, fontSize = 12.sp) }
        }
    }
}
