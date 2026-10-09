package com.ashu.ashutool

import android.content.Context
import android.widget.Toast
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.outlined.AdminPanelSettings
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.*
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.graphics.drawable.toBitmap
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import kotlin.math.abs

// ------------------------------------------------------------- helpers

fun toast(ctx: Context, msg: String) = Toast.makeText(ctx, msg, Toast.LENGTH_SHORT).show()

fun fmtDuration(ms: Long): String {
    val m = ms / 60000
    return if (m >= 60) "${m / 60}h ${m % 60}m" else "${m}m"
}

fun fmtBytes(b: Long): String {
    val g = b / 1073741824.0
    return if (g >= 1) "%.1f GB".format(g) else "${b / 1048576} MB"
}

fun fmtSpeed(bps: Long): String =
    if (bps >= 1048576) "%.1f MB/s".format(bps / 1048576.0) else "${bps / 1024} KB/s"

fun tempGrad(t: Float) = if (t < 45f) GTeal else if (t < 60f) GFire else GRed

// ----------------------------------------------------------- animation

/** Springy press feedback. Put it first in a modifier chain so the whole element scales. */
@Composable
fun Modifier.bounceClick(enabled: Boolean = true, onClick: () -> Unit): Modifier {
    val src = remember { MutableInteractionSource() }
    val pressed by src.collectIsPressedAsState()
    val sc by animateFloatAsState(if (pressed) 0.96f else 1f, spring(0.55f, 600f), label = "press")
    return this.graphicsLayer { scaleX = sc; scaleY = sc }
        .clickable(interactionSource = src, indication = null, enabled = enabled, onClick = onClick)
}

/** Staggered slide and fade when an element first appears. */
@Composable
fun Modifier.enter(index: Int, fromX: Float = 0f): Modifier {
    var on by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { delay(index * 45L); on = true }
    val a by animateFloatAsState(if (on) 1f else 0f, tween(420), label = "ea")
    val y by animateFloatAsState(if (on) 0f else 36f, tween(520, easing = FastOutSlowInEasing), label = "ey")
    val x by animateFloatAsState(if (on) 0f else fromX, tween(520, easing = FastOutSlowInEasing), label = "ex")
    return this.graphicsLayer { alpha = a; translationY = y; translationX = x }
}

// --------------------------------------------------------------- glass

@Composable
fun Glass(
    modifier: Modifier = Modifier,
    radius: Dp = 24.dp,
    pad: Dp = 16.dp,
    onClick: (() -> Unit)? = null,
    content: @Composable ColumnScope.() -> Unit
) {
    val shape = RoundedCornerShape(radius)
    val pal = Palette.cur
    val base = if (onClick != null) modifier.bounceClick(onClick = onClick) else modifier
    Column(
        base.fillMaxWidth().clip(shape)
            .background(Brush.verticalGradient(listOf(pal.tint.copy(alpha = pal.glassTop), pal.tint.copy(alpha = pal.glassBottom))))
            .border(
                1.dp,
                Brush.linearGradient(listOf(pal.tint.copy(alpha = pal.bA), pal.tint.copy(alpha = pal.bB), pal.tint.copy(alpha = pal.bC))),
                shape
            )
            .padding(pad),
        content = content
    )
}

@Composable
fun GradIcon(icon: ImageVector, grad: Grad, size: Dp = 40.dp) {
    val shape = RoundedCornerShape(size * 0.30f)
    Box(
        Modifier.size(size).clip(shape)
            .background(Brush.linearGradient(listOf(grad.a.copy(alpha = 0.20f), grad.b.copy(alpha = 0.08f))))
            .border(1.dp, Brush.linearGradient(listOf(grad.a.copy(alpha = 0.55f), grad.b.copy(alpha = 0.15f))), shape),
        contentAlignment = Alignment.Center
    ) {
        Icon(
            icon, null, tint = Color.White,
            modifier = Modifier.size(size * 0.5f)
                .graphicsLayer { compositingStrategy = CompositingStrategy.Offscreen }
                .drawWithCache {
                    val br = Brush.linearGradient(listOf(grad.a, grad.b), start = Offset.Zero, end = Offset(this.size.width, this.size.height))
                    onDrawWithContent {
                        drawContent()
                        drawRect(br, blendMode = BlendMode.SrcIn)
                    }
                }
        )
    }
}

@Composable
fun GradText(text: String, grad: Grad, size: TextUnit, weight: FontWeight = FontWeight.Bold) {
    Text(text, style = TextStyle(brush = grad.h(), fontSize = size, fontWeight = weight))
}

@Composable
fun Page(
    title: String, sub: String, grad: Grad, icon: ImageVector,
    onBack: (() -> Unit)? = null, bottomInset: Boolean = false,
    content: LazyListScope.() -> Unit
) {
    Column(Modifier.fillMaxSize().statusBarsPadding().then(if (bottomInset) Modifier.navigationBarsPadding() else Modifier)) {
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            if (onBack != null) {
                Box(
                    Modifier.bounceClick(onClick = onBack).size(44.dp).clip(CircleShape).background(Surf2).border(1.dp, Line, CircleShape),
                    contentAlignment = Alignment.Center
                ) { Icon(Icons.AutoMirrored.Outlined.ArrowBack, "Back", tint = TextHi) }
                Spacer(Modifier.width(14.dp))
            }
            Column(Modifier.weight(1f)) {
                Text(title, color = TextHi, fontSize = 24.sp, fontWeight = FontWeight.Bold)
                Text(sub, color = TextLo, fontSize = 12.sp)
            }
            GradIcon(icon, grad, 44.dp)
        }
        LazyColumn(
            Modifier.fillMaxSize(),
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 6.dp, bottom = 20.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
            content = content
        )
    }
}

@Composable
fun RootChip(root: Boolean?) {
    val ok = root == true
    val c = if (root == null) TextLo else if (ok) Accent else Danger
    Row(
        Modifier.clip(RoundedCornerShape(50)).background(c.copy(alpha = 0.16f))
            .border(1.dp, c.copy(alpha = 0.4f), RoundedCornerShape(50))
            .padding(horizontal = 12.dp, vertical = 7.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(Icons.Outlined.AdminPanelSettings, null, tint = c, modifier = Modifier.size(18.dp))
        Spacer(Modifier.width(6.dp))
        Text(if (root == null) "Checking" else if (ok) "Root granted" else "No root", color = c, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
    }
}

@Composable
fun SectionTitle(title: String, sub: String = "") {
    Row(Modifier.fillMaxWidth().padding(bottom = 10.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(title, color = TextHi, fontSize = 15.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f))
        if (sub.isNotEmpty()) Text(sub, color = TextLo, fontSize = 12.sp)
    }
}

@Composable
fun SubLabel(text: String) {
    Text(text, color = TextLo, fontSize = 12.sp, fontWeight = FontWeight.Medium, modifier = Modifier.padding(top = 14.dp, bottom = 8.dp))
}

@Composable
fun Hint(text: String) = Text(text, color = TextLo, fontSize = 13.sp, modifier = Modifier.padding(4.dp))

// --------------------------------------------------------------- charts

@Composable
fun Gauge(
    frac: Float, value: String, label: String, icon: ImageVector, grad: Grad,
    modifier: Modifier = Modifier, dia: Dp = 92.dp
) {
    val anim by animateFloatAsState(frac.coerceIn(0f, 1f), tween(700), label = "gauge")
    val track = Tint.copy(alpha = 0.10f)
    Column(modifier, horizontalAlignment = Alignment.CenterHorizontally) {
        Box(Modifier.size(dia), contentAlignment = Alignment.Center) {
            Canvas(Modifier.fillMaxSize()) {
                val sw = this.size.minDimension * 0.12f
                val box = Size(this.size.width - sw, this.size.height - sw)
                val tl = Offset(sw / 2, sw / 2)
                drawArc(track, 135f, 270f, false, tl, box, style = Stroke(sw, cap = StrokeCap.Round))
                if (anim > 0.005f) {
                    rotate(135f) {
                        drawArc(
                            Brush.sweepGradient(0f to grad.a, 0.75f to grad.b, 1f to grad.b, center = center),
                            0f, 270f * anim, false, tl, box, style = Stroke(sw, cap = StrokeCap.Round)
                        )
                    }
                }
            }
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Icon(icon, null, tint = grad.a, modifier = Modifier.size(15.dp))
                Text(value, color = TextHi, fontSize = (dia.value * 0.2f).sp, fontWeight = FontWeight.Bold)
            }
        }
        Text(label, color = TextLo, fontSize = 12.sp)
    }
}

@Composable
fun LineChart(
    values: List<Float>, grad: Grad,
    modifier: Modifier = Modifier.fillMaxWidth().height(96.dp), maxV: Float? = null
) {
    val gridC = Tint.copy(alpha = 0.08f)
    Canvas(modifier) {
        val w = size.width
        val h = size.height
        for (i in 0..2) {
            val y = h * i / 2f
            drawLine(gridC, Offset(0f, y), Offset(w, y), strokeWidth = 1.dp.toPx())
        }
        if (values.size < 2) return@Canvas
        val lo = if (maxV != null) 0f else values.min() - 1f
        val hi = maxV ?: (values.max() + 1f)
        val range = (hi - lo).coerceAtLeast(1f)
        val line = Path()
        values.forEachIndexed { i, v ->
            val x = w * i / (values.size - 1)
            val y = h - (v - lo) / range * h
            if (i == 0) line.moveTo(x, y) else line.lineTo(x, y)
        }
        val fill = Path().apply {
            addPath(line)
            lineTo(w, h)
            lineTo(0f, h)
            close()
        }
        drawPath(fill, Brush.verticalGradient(listOf(grad.a.copy(alpha = 0.32f), Color.Transparent)))
        drawPath(line, grad.h(), style = Stroke(2.6.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round))
    }
}

@Composable
fun Bar(frac: Float, grad: Grad, modifier: Modifier = Modifier) {
    val f by animateFloatAsState(frac.coerceIn(0f, 1f), tween(500), label = "bar")
    Box(modifier.fillMaxWidth().height(7.dp).clip(RoundedCornerShape(4.dp)).background(Tint.copy(alpha = 0.10f))) {
        Box(Modifier.fillMaxHeight().fillMaxWidth(f).clip(RoundedCornerShape(4.dp)).background(grad.h()))
    }
}

// ---------------------------------------------------------------- tiles

data class Tile(val icon: ImageVector, val label: String, val value: String, val grad: Grad = GTeal)

@Composable
fun StatTile(t: Tile, modifier: Modifier = Modifier) {
    Glass(modifier, radius = 18.dp, pad = 12.dp) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            GradIcon(t.icon, t.grad, 36.dp)
            Spacer(Modifier.width(10.dp))
            Column {
                Text(t.label, color = TextLo, fontSize = 11.sp)
                Text(t.value, color = TextHi, fontSize = 15.sp, fontWeight = FontWeight.SemiBold, maxLines = 1)
            }
        }
    }
}

@Composable
fun TileGrid(tiles: List<Tile>) {
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        tiles.chunked(2).forEach { row ->
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                row.forEach { StatTile(it, Modifier.weight(1f)) }
                if (row.size == 1) Spacer(Modifier.weight(1f))
            }
        }
    }
}

@Composable
fun ActionRow(
    icon: ImageVector, grad: Grad, title: String, sub: String,
    onClick: (() -> Unit)? = null, trailing: (@Composable () -> Unit)? = null
) {
    Glass(radius = 20.dp, pad = 14.dp, onClick = onClick) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            GradIcon(icon, grad, 40.dp)
            Spacer(Modifier.width(14.dp))
            Column(Modifier.weight(1f)) {
                Text(title, color = TextHi, fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
                Text(sub, color = TextLo, fontSize = 12.sp)
            }
            trailing?.invoke()
        }
    }
}

@Composable
fun glassSwitchColors() = SwitchDefaults.colors(
    checkedTrackColor = Accent, checkedThumbColor = Ink, checkedBorderColor = Color.Transparent,
    uncheckedTrackColor = Surf2, uncheckedThumbColor = TextLo, uncheckedBorderColor = Line
)

@Composable
fun SwitchRow(icon: ImageVector, grad: Grad, title: String, sub: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    ActionRow(icon, grad, title, sub, onClick = { onChange(!checked) }) {
        Switch(checked = checked, onCheckedChange = onChange, colors = glassSwitchColors())
    }
}

@Composable
fun InfoRow(label: String, value: String) {
    Row(Modifier.fillMaxWidth().padding(vertical = 7.dp), verticalAlignment = Alignment.Top) {
        Text(label, color = TextLo, fontSize = 13.sp, modifier = Modifier.width(110.dp))
        Text(value.ifBlank { "-" }, color = TextHi, fontSize = 13.sp, modifier = Modifier.weight(1f))
    }
}

// --------------------------------------------------------------- buttons

@Composable
fun Chip(text: String, selected: Boolean, grad: Grad = GTeal, onClick: () -> Unit) {
    val shape = RoundedCornerShape(50)
    Box(
        Modifier.bounceClick(onClick = onClick).clip(shape)
            .then(if (selected) Modifier.background(grad.h()) else Modifier.background(Surf2).border(1.dp, Line, shape))
            .padding(horizontal = 14.dp, vertical = 8.dp)
    ) {
        Text(text, color = if (selected) Ink else TextHi, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
    }
}

@Composable
fun GradButton(text: String, grad: Grad = GTeal, icon: ImageVector? = null, modifier: Modifier = Modifier, onClick: () -> Unit) {
    Row(
        modifier.bounceClick(onClick = onClick).clip(RoundedCornerShape(16.dp)).background(grad.h())
            .padding(horizontal = 18.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Center
    ) {
        if (icon != null) {
            Icon(icon, null, tint = Ink, modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(8.dp))
        }
        Text(text, color = Ink, fontSize = 14.sp, fontWeight = FontWeight.Bold)
    }
}

@Composable
fun GlassButton(icon: ImageVector, text: String, tint: Color = Accent, onClick: () -> Unit) {
    val shape = RoundedCornerShape(50)
    Row(
        Modifier.bounceClick(onClick = onClick).clip(shape).background(Surf2).border(1.dp, Line, shape)
            .padding(horizontal = 14.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(icon, null, tint = tint, modifier = Modifier.size(16.dp))
        Spacer(Modifier.width(6.dp))
        Text(text, color = TextHi, fontSize = 12.sp, fontWeight = FontWeight.Medium)
    }
}

// --------------------------------------------------------------- fields

@Composable
fun SearchField(query: String, onChange: (String) -> Unit) {
    OutlinedTextField(
        value = query, onValueChange = onChange, singleLine = true,
        placeholder = { Text("Search apps", color = TextLo) },
        leadingIcon = { Icon(Icons.Outlined.Search, null, tint = TextLo) },
        modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(18.dp),
        colors = OutlinedTextFieldDefaults.colors(
            focusedBorderColor = Accent, unfocusedBorderColor = Line,
            focusedContainerColor = Surf2, unfocusedContainerColor = Surf2,
            focusedTextColor = TextHi, unfocusedTextColor = TextHi, cursorColor = Accent
        )
    )
}

@Composable
fun PinField(value: String, label: String, onChange: (String) -> Unit) {
    OutlinedTextField(
        value = value,
        onValueChange = { if (it.length <= 6 && it.all(Char::isDigit)) onChange(it) },
        label = { Text(label) },
        singleLine = true,
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
        visualTransformation = PasswordVisualTransformation(),
        colors = OutlinedTextFieldDefaults.colors(
            focusedBorderColor = Accent, unfocusedBorderColor = Line,
            focusedTextColor = TextHi, unfocusedTextColor = TextHi,
            focusedLabelColor = Accent, unfocusedLabelColor = TextLo, cursorColor = Accent
        )
    )
}

@Composable
fun ConfirmDialog(
    title: String, text: String, confirm: String, danger: Boolean = false,
    onConfirm: () -> Unit, onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = DialogBg, titleContentColor = TextHi, textContentColor = TextLo,
        title = { Text(title) },
        text = { Text(text) },
        confirmButton = { TextButton(onClick = onConfirm) { Text(confirm, color = if (danger) Danger else Accent) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel", color = TextLo) } }
    )
}

// ------------------------------------------------------ pattern and lock

private fun middleDot(a: Int, b: Int): Int? {
    val ax = a % 3; val ay = a / 3; val bx = b % 3; val by = b / 3
    return if ((ax + bx) % 2 == 0 && (ay + by) % 2 == 0 && (abs(ax - bx) == 2 || abs(ay - by) == 2))
        ((ay + by) / 2) * 3 + (ax + bx) / 2 else null
}

/** 3 by 3 pattern pad. Calls onDone with the dot indexes (0 to 8) when the finger lifts. */
@Composable
fun PatternPad(modifier: Modifier = Modifier, error: Boolean = false, onDone: (List<Int>) -> Unit) {
    var path by remember { mutableStateOf(listOf<Int>()) }
    var cur by remember { mutableStateOf<Offset?>(null) }
    val col = if (error) Danger else Accent
    val dim = Tint.copy(alpha = 0.35f)
    Canvas(
        modifier.aspectRatio(1f).pointerInput(Unit) {
            fun hit(o: Offset): Int? {
                val cell = size.width / 3f
                for (i in 0..8) {
                    val c = Offset((i % 3 + 0.5f) * cell, (i / 3 + 0.5f) * cell)
                    if ((o - c).getDistance() < cell * 0.3f) return i
                }
                return null
            }
            detectDragGestures(
                onDragStart = { o -> path = listOfNotNull(hit(o)); cur = o },
                onDrag = { change, _ ->
                    change.consume()
                    cur = change.position
                    val h = hit(change.position)
                    if (h != null && h !in path) {
                        val last = path.lastOrNull()
                        val mid = if (last != null) middleDot(last, h) else null
                        path = path + listOfNotNull(mid?.takeIf { it !in path }, h)
                    }
                },
                onDragEnd = {
                    val p = path
                    path = emptyList()
                    cur = null
                    if (p.isNotEmpty()) onDone(p)
                },
                onDragCancel = { path = emptyList(); cur = null }
            )
        }
    ) {
        val cell = size.width / 3f
        fun pt(i: Int) = Offset((i % 3 + 0.5f) * cell, (i / 3 + 0.5f) * cell)
        for (k in 1 until path.size) {
            drawLine(col, pt(path[k - 1]), pt(path[k]), strokeWidth = 6.dp.toPx(), cap = StrokeCap.Round)
        }
        val c = cur
        val last = path.lastOrNull()
        if (c != null && last != null) {
            drawLine(col.copy(alpha = 0.6f), pt(last), c, strokeWidth = 6.dp.toPx(), cap = StrokeCap.Round)
        }
        for (i in 0..8) {
            val on = i in path
            if (on) drawCircle(col.copy(alpha = 0.25f), 24.dp.toPx(), pt(i))
            drawCircle(if (on) col else dim, if (on) 12.dp.toPx() else 7.dp.toPx(), pt(i))
        }
    }
}

@Composable
private fun VerifyInline(onOk: () -> Unit) {
    val ctx = LocalContext.current
    var pin by remember { mutableStateOf("") }
    var err by remember { mutableStateOf(false) }
    if (LockStore.type(ctx) == 0) {
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            PinField(pin, "Current PIN") { pin = it; err = false }
            if (err) Text("Wrong PIN", color = Danger, fontSize = 12.sp)
            TextButton(enabled = pin.length >= 4, onClick = { if (LockStore.checkPin(ctx, pin)) onOk() else err = true }) {
                Text("Verify", color = Accent)
            }
        }
    } else {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text("Draw your current pattern", color = TextLo, fontSize = 13.sp)
            Spacer(Modifier.height(10.dp))
            PatternPad(Modifier.size(230.dp), err) { p -> if (LockStore.checkPattern(ctx, p)) onOk() else err = true }
        }
    }
}

/** Set or change the app lock credential. Supports PIN and pattern. */
@Composable
fun CredentialDialog(onDismiss: () -> Unit, onSaved: () -> Unit) {
    val ctx = LocalContext.current
    var verified by remember { mutableStateOf(!LockStore.hasCred(ctx)) }
    var type by remember { mutableIntStateOf(LockStore.type(ctx)) }
    var pin by remember { mutableStateOf("") }
    var first by remember { mutableStateOf<List<Int>?>(null) }
    var err by remember { mutableStateOf<String?>(null) }
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = DialogBg, titleContentColor = TextHi, textContentColor = TextLo,
        title = { Text(if (!verified) "Enter current lock" else "Set app lock") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                if (!verified) VerifyInline { verified = true }
                else {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Chip("PIN", type == 0) { type = 0; first = null; err = null }
                        Chip("Pattern", type == 1) { type = 1; first = null; err = null }
                    }
                    if (type == 0) PinField(pin, "New PIN, 4 to 6 digits") { pin = it }
                    else Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.fillMaxWidth()) {
                        Text(
                            if (first == null) "Draw a pattern, at least 4 dots" else "Draw it again to confirm",
                            color = TextLo, fontSize = 13.sp
                        )
                        Spacer(Modifier.height(10.dp))
                        PatternPad(Modifier.size(230.dp), err != null) { p ->
                            when {
                                p.size < 4 -> err = "Connect at least 4 dots"
                                first == null -> { first = p; err = null }
                                first == p -> { LockStore.setPattern(ctx, p); onSaved() }
                                else -> { err = "Patterns do not match"; first = null }
                            }
                        }
                    }
                    err?.let { Text(it, color = Danger, fontSize = 12.sp) }
                }
            }
        },
        confirmButton = {
            if (verified && type == 0) TextButton(
                enabled = pin.length >= 4,
                onClick = { LockStore.setPin(ctx, pin); onSaved() }
            ) { Text("Save PIN", color = Accent) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel", color = TextLo) } }
    )
}

// ---------------------------------------------------------------- icons

object IconCache {
    private val map = HashMap<String, ImageBitmap>()

    @Synchronized fun peek(pkg: String): ImageBitmap? = map[pkg]
    @Synchronized private fun put(pkg: String, b: ImageBitmap) { map[pkg] = b }

    fun get(ctx: Context, pkg: String): ImageBitmap? {
        peek(pkg)?.let { return it }
        return try {
            val b = ctx.packageManager.getApplicationIcon(pkg).toBitmap(96, 96).asImageBitmap()
            put(pkg, b)
            b
        } catch (e: Exception) {
            null
        }
    }
}

@Composable
fun AppIcon(pkg: String, size: Dp = 40.dp) {
    val ctx = LocalContext.current
    val bmp by produceState<ImageBitmap?>(IconCache.peek(pkg), pkg) {
        value = withContext(Dispatchers.IO) { IconCache.get(ctx, pkg) }
    }
    val b = bmp
    val shape = RoundedCornerShape(size * 0.25f)
    if (b != null) Image(b, null, Modifier.size(size).clip(shape))
    else Box(Modifier.size(size).clip(shape).background(Surf2))
}
