@file:OptIn(ExperimentalLayoutApi::class)

package com.ashu.ashutool

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.KeyboardArrowRight
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.repeatOnLifecycle
import kotlinx.coroutines.CoroutineScope

/**
 * A tab with sub pages. The root page lists entries, each opens a page of its own with a back button.
 * The system back gesture closes the sub page first.
 */
@Composable
fun SubHost(
    root: @Composable (open: (String) -> Unit) -> Unit,
    sub: @Composable (key: String, back: () -> Unit) -> Unit
) {
    var cur by remember { mutableStateOf<String?>(null) }
    BackHandler(enabled = cur != null) { cur = null }
    AnimatedContent(
        targetState = cur,
        modifier = Modifier.fillMaxSize(),
        transitionSpec = {
            if (targetState != null)
                (slideInHorizontally(tween(300, easing = FastOutSlowInEasing)) { it / 2 } + fadeIn(tween(240))) togetherWith
                    (slideOutHorizontally(tween(260)) { -it / 5 } + fadeOut(tween(160)))
            else
                (slideInHorizontally(tween(300, easing = FastOutSlowInEasing)) { -it / 5 } + fadeIn(tween(240))) togetherWith
                    (slideOutHorizontally(tween(260)) { it / 2 } + fadeOut(tween(160)))
        },
        label = "sub"
    ) { k -> if (k == null) root { cur = it } else sub(k) { cur = null } }
}

/** A row that opens a sub page. */
@Composable
fun NavRow(icon: ImageVector, grad: Grad, title: String, sub: String, onClick: () -> Unit) {
    ActionRow(icon, grad, title, sub, onClick = onClick) {
        Icon(Icons.AutoMirrored.Outlined.KeyboardArrowRight, null, tint = TextLo, modifier = Modifier.size(22.dp))
    }
}

/** Round glass icon button, used for the settings gear in page headers. */
@Composable
fun IconBtn(icon: ImageVector, desc: String, onClick: () -> Unit) {
    Box(
        Modifier.bounceClick(onClick = onClick).size(44.dp).clip(CircleShape).background(Surf2).border(1.dp, Line, CircleShape),
        contentAlignment = Alignment.Center
    ) { Icon(icon, desc, tint = TextHi, modifier = Modifier.size(22.dp)) }
}

fun fmtMin(t: Int): String = when {
    t >= 60 && t % 60 == 0 -> "${t / 60} h"
    t >= 60 -> "${t / 60} h ${t % 60} min"
    else -> "$t min"
}

/** Pick a daily time limit with presets or custom hours and minutes. */
@Composable
fun LimitDialog(label: String, current: Int, onSave: (Int) -> Unit, onRemove: () -> Unit, onDismiss: () -> Unit) {
    var h by remember { mutableIntStateOf(if (current > 0) current / 60 else 1) }
    var m by remember { mutableIntStateOf(if (current > 0) (current % 60) / 5 * 5 else 0) }
    val total = h * 60 + m
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = DialogBg, titleContentColor = TextHi, textContentColor = TextLo,
        title = { Text("Daily limit: $label", maxLines = 1) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(if (total == 0) "Pick a time" else fmtMin(total), color = Accent, fontSize = 26.sp, fontWeight = FontWeight.Bold)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf(15, 30, 45, 60, 90, 120, 180).forEach { v ->
                        Chip(fmtMin(v), total == v) { h = v / 60; m = v % 60 }
                    }
                }
                SubLabel("Hours")
                Slider(
                    value = h.toFloat(), onValueChange = { h = it.toInt() }, valueRange = 0f..12f, steps = 11,
                    colors = SliderDefaults.colors(thumbColor = Accent, activeTrackColor = Accent, inactiveTrackColor = Tint.copy(alpha = 0.15f))
                )
                SubLabel("Minutes")
                Slider(
                    value = m.toFloat(), onValueChange = { m = (it.toInt() / 5) * 5 }, valueRange = 0f..55f, steps = 10,
                    colors = SliderDefaults.colors(thumbColor = Accent, activeTrackColor = Accent, inactiveTrackColor = Tint.copy(alpha = 0.15f))
                )
            }
        },
        confirmButton = { TextButton(enabled = total > 0, onClick = { onSave(total) }) { Text("Set limit", color = Accent) } },
        dismissButton = {
            Row {
                if (current > 0) TextButton(onClick = onRemove) { Text("Remove", color = Danger) }
                TextButton(onClick = onDismiss) { Text("Cancel", color = TextLo) }
            }
        }
    )
}

/** Runs a loop only while the app is on screen, so background polling never drains the battery. */
@Composable
fun WhileVisible(block: suspend CoroutineScope.() -> Unit) {
    val owner = LocalContext.current as? LifecycleOwner
    LaunchedEffect(owner) {
        if (owner == null) block() else owner.lifecycle.repeatOnLifecycle(Lifecycle.State.STARTED) { block() }
    }
}
