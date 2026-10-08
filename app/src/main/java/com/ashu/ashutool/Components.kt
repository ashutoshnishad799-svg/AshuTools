package com.ashu.ashutool

import android.content.Context
import android.widget.Toast
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.AdminPanelSettings
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.*
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.vector.ImageVector
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
import kotlinx.coroutines.withContext

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

fun tempGrad(t: Float) = if (t < 45f) GTeal else if (t < 60f) GFire else GRed

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
    Column(
        modifier.fillMaxWidth().clip(shape)
            .background(Brush.verticalGradient(listOf(Color.White.copy(alpha = 0.15f), Color.White.copy(alpha = 0.05f))))
            .border(
                1.dp,
                Brush.linearGradient(listOf(Color.White.copy(alpha = 0.40f), Color.White.copy(alpha = 0.05f), Color.White.copy(alpha = 0.22f))),
                shape
            )
            .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier)
            .padding(pad),
        content = content
    )
}

@Composable
fun GradIcon(icon: ImageVector, grad: Grad, size: Dp = 40.dp) {
    Box(
        Modifier.size(size).clip(RoundedCornerShape(size * 0.32f)).background(grad.d()),
        contentAlignment = Alignment.Center
    ) { Icon(icon, null, tint = Ink, modifier = Modifier.size(size * 0.52f)) }
}

@Composable
fun GradText(text: String, grad: Grad, size: TextUnit, weight: FontWeight = FontWeight.Bold) {
    Text(text, style = TextStyle(brush = grad.h(), fontSize = size, fontWeight = weight))
}

@Composable
fun Page(
    title: String, sub: String, grad: Grad, icon: ImageVector, onBack: () -> Unit,
    content: LazyListScope.() -> Unit
) {
    Column(Modifier.fillMaxSize().systemBarsPadding()) {
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                Modifier.size(44.dp).clip(CircleShape).background(Surf2).border(1.dp, Line, CircleShape).clickable(onClick = onBack),
                contentAlignment = Alignment.Center
            ) { Icon(Icons.AutoMirrored.Rounded.ArrowBack, "Back", tint = TextHi) }
            Spacer(Modifier.width(14.dp))
            Column(Modifier.weight(1f)) {
                Text(title, color = TextHi, fontSize = 22.sp, fontWeight = FontWeight.Bold)
                Text(sub, color = TextLo, fontSize = 12.sp)
            }
            GradIcon(icon, grad, 44.dp)
        }
        LazyColumn(
            Modifier.fillMaxSize(),
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 6.dp, bottom = 24.dp),
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
        Icon(Icons.Rounded.AdminPanelSettings, null, tint = c, modifier = Modifier.size(18.dp))
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
    Column(modifier, horizontalAlignment = Alignment.CenterHorizontally) {
        Box(Modifier.size(dia), contentAlignment = Alignment.Center) {
            Canvas(Modifier.fillMaxSize()) {
                val sw = this.size.minDimension * 0.12f
                val box = Size(this.size.width - sw, this.size.height - sw)
                val tl = Offset(sw / 2, sw / 2)
                drawArc(Color.White.copy(alpha = 0.10f), 135f, 270f, false, tl, box, style = Stroke(sw, cap = StrokeCap.Round))
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
    Canvas(modifier) {
        val w = size.width
        val h = size.height
        for (i in 0..2) {
            val y = h * i / 2f
            drawLine(Color.White.copy(alpha = 0.08f), Offset(0f, y), Offset(w, y), strokeWidth = 1.dp.toPx())
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
    Box(modifier.fillMaxWidth().height(7.dp).clip(RoundedCornerShape(4.dp)).background(Color.White.copy(alpha = 0.10f))) {
        Box(Modifier.fillMaxHeight().fillMaxWidth(frac.coerceIn(0f, 1f)).clip(RoundedCornerShape(4.dp)).background(grad.h()))
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
        Modifier.clip(shape)
            .then(if (selected) Modifier.background(grad.h()) else Modifier.background(Surf2).border(1.dp, Line, shape))
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 8.dp)
    ) {
        Text(text, color = if (selected) Ink else TextHi, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
    }
}

@Composable
fun GradButton(text: String, grad: Grad = GTeal, icon: ImageVector? = null, modifier: Modifier = Modifier, onClick: () -> Unit) {
    Row(
        modifier.clip(RoundedCornerShape(16.dp)).background(grad.h()).clickable(onClick = onClick)
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
        Modifier.clip(shape).background(Surf2).border(1.dp, Line, shape).clickable(onClick = onClick)
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
        leadingIcon = { Icon(Icons.Rounded.Search, null, tint = TextLo) },
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
fun PinDialog(needOld: Boolean, onDismiss: () -> Unit, onSave: (String) -> Unit) {
    val ctx = LocalContext.current
    var old by remember { mutableStateOf("") }
    var pin by remember { mutableStateOf("") }
    var err by remember { mutableStateOf<String?>(null) }
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = DialogBg, titleContentColor = TextHi, textContentColor = TextLo,
        title = { Text(if (needOld) "Change PIN" else "Set app lock PIN") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                if (needOld) PinField(old, "Current PIN") { old = it }
                PinField(pin, "New PIN, 4 to 6 digits") { pin = it }
                err?.let { Text(it, color = Danger, fontSize = 12.sp) }
            }
        },
        confirmButton = {
            TextButton(
                enabled = pin.length >= 4 && (!needOld || old.length >= 4),
                onClick = { if (needOld && !LockStore.check(ctx, old)) err = "Current PIN is wrong" else onSave(pin) }
            ) { Text("Save PIN", color = Accent) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel", color = TextLo) } }
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
