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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AdminPanelSettings
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.*
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.graphics.drawable.toBitmap
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

fun toast(ctx: Context, msg: String) = Toast.makeText(ctx, msg, Toast.LENGTH_SHORT).show()

fun fmtDuration(ms: Long): String {
    val m = ms / 60000
    return if (m >= 60) "${m / 60}h ${m % 60}m" else "${m}m"
}

fun tempColor(t: Float) = if (t < 45f) Accent else if (t < 60f) Warn else Danger

@Composable
fun Panel(modifier: Modifier = Modifier, content: @Composable ColumnScope.() -> Unit) {
    val shape = RoundedCornerShape(20.dp)
    Column(
        modifier.fillMaxWidth().clip(shape).background(Surf).border(1.dp, Line, shape).padding(16.dp),
        content = content
    )
}

@Composable
fun Header(title: String, sub: String, root: Boolean? = null) {
    Row(Modifier.fillMaxWidth().padding(top = 8.dp, bottom = 4.dp), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text(title, color = TextHi, fontSize = 28.sp, fontWeight = FontWeight.Bold)
            Text(sub, color = TextLo, fontSize = 13.sp)
        }
        if (root != null) {
            val c = if (root) Accent else Danger
            Row(
                Modifier.clip(RoundedCornerShape(50)).background(c.copy(alpha = 0.14f)).padding(horizontal = 12.dp, vertical = 7.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(Icons.Rounded.AdminPanelSettings, null, tint = c, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(6.dp))
                Text(if (root) "Root granted" else "No root", color = c, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
            }
        }
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
fun Gauge(
    frac: Float, value: String, label: String, icon: ImageVector, color: Color,
    modifier: Modifier = Modifier, dia: Dp = 92.dp
) {
    val anim by animateFloatAsState(frac.coerceIn(0f, 1f), tween(600), label = "gauge")
    Column(modifier, horizontalAlignment = Alignment.CenterHorizontally) {
        Box(Modifier.size(dia), contentAlignment = Alignment.Center) {
            Canvas(Modifier.fillMaxSize()) {
                val sw = this.size.minDimension * 0.11f
                val box = Size(this.size.width - sw, this.size.height - sw)
                val tl = Offset(sw / 2, sw / 2)
                drawArc(Line, 135f, 270f, false, tl, box, style = Stroke(sw, cap = StrokeCap.Round))
                if (anim > 0.005f) drawArc(color, 135f, 270f * anim, false, tl, box, style = Stroke(sw, cap = StrokeCap.Round))
            }
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Icon(icon, null, tint = color, modifier = Modifier.size(15.dp))
                Text(value, color = TextHi, fontSize = (dia.value * 0.2f).sp, fontWeight = FontWeight.Bold)
            }
        }
        Text(label, color = TextLo, fontSize = 12.sp)
    }
}

@Composable
fun LineChart(values: List<Float>, color: Color, modifier: Modifier = Modifier.fillMaxWidth().height(90.dp), maxV: Float? = null) {
    Canvas(modifier) {
        val w = size.width
        val h = size.height
        for (i in 0..2) {
            val y = h * i / 2f
            drawLine(Line, Offset(0f, y), Offset(w, y), strokeWidth = 1.dp.toPx())
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
        drawPath(fill, Brush.verticalGradient(listOf(color.copy(alpha = 0.28f), Color.Transparent)))
        drawPath(line, color, style = Stroke(2.5.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round))
    }
}

@Composable
fun Bar(frac: Float, color: Color, modifier: Modifier = Modifier) {
    Box(modifier.fillMaxWidth().height(6.dp).clip(RoundedCornerShape(3.dp)).background(Surf2)) {
        Box(Modifier.fillMaxHeight().fillMaxWidth(frac.coerceIn(0f, 1f)).clip(RoundedCornerShape(3.dp)).background(color))
    }
}

@Composable
fun IconBox(icon: ImageVector, tint: Color = Accent, size: Dp = 38.dp) {
    Box(
        Modifier.size(size).clip(RoundedCornerShape(12.dp)).background(tint.copy(alpha = 0.14f)),
        contentAlignment = Alignment.Center
    ) { Icon(icon, null, tint = tint, modifier = Modifier.size(size * 0.52f)) }
}

@Composable
fun StatTile(icon: ImageVector, label: String, value: String, modifier: Modifier = Modifier, tint: Color = Accent) {
    val shape = RoundedCornerShape(16.dp)
    Row(
        modifier.clip(shape).background(Surf).border(1.dp, Line, shape).padding(14.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        IconBox(icon, tint)
        Spacer(Modifier.width(12.dp))
        Column {
            Text(label, color = TextLo, fontSize = 12.sp)
            Text(value, color = TextHi, fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
        }
    }
}

@Composable
fun ActionRow(
    icon: ImageVector, title: String, sub: String, tint: Color = Accent,
    onClick: (() -> Unit)? = null, trailing: (@Composable () -> Unit)? = null
) {
    val shape = RoundedCornerShape(16.dp)
    Row(
        Modifier.fillMaxWidth().clip(shape).background(Surf).border(1.dp, Line, shape)
            .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier)
            .padding(14.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        IconBox(icon, tint)
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(title, color = TextHi, fontSize = 15.sp, fontWeight = FontWeight.Medium)
            Text(sub, color = TextLo, fontSize = 12.sp)
        }
        trailing?.invoke()
    }
}

@Composable
fun Chip(text: String, selected: Boolean, onClick: () -> Unit) {
    FilterChip(
        selected = selected,
        onClick = onClick,
        label = { Text(text) },
        colors = FilterChipDefaults.filterChipColors(
            containerColor = Surf,
            labelColor = TextLo,
            selectedContainerColor = Accent.copy(alpha = 0.18f),
            selectedLabelColor = Accent
        )
    )
}

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
