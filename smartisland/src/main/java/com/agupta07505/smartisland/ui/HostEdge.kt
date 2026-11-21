/*
 * Ashutool - root toolkit for custom ROMs
 * Copyright (C) 2026 Ashutosh Nishad
 * Telegram: @ashutosh_07x (https://t.me/ashutosh_07x)
 *
 * Free software under the GNU GPL v3.0 or later, with the attribution terms in NOTICE.
 *
 * This file is new, written for Ashutool. It draws a light coloured edge around the island: a thin line and a soft
 * inner glow in the shape of the island. The host app chooses the style and the strength.
 */

package com.agupta07505.smartisland.ui

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Outline
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.ShaderBrush
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

object HostEdge {
    /** 0 off, 1 one colour (the accent of the host), 2 aurora, 3 rainbow, 4 flow (aurora that moves slowly). */
    var style by mutableIntStateOf(2)
        private set

    /** 0 to 100. */
    var strength by mutableIntStateOf(55)
        private set

    /** 0 to 1, only moves in the flow style. */
    var phase by mutableFloatStateOf(0f)
        private set

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private var job: Job? = null

    fun configure(style: Int, strength: Int) {
        this.style = style.coerceIn(0, 4)
        this.strength = strength.coerceIn(0, 100)
        job?.cancel()
        job = null
        if (this.style == 4) {
            job = scope.launch {
                while (true) {
                    delay(40L)
                    phase = (phase + 0.010f) % 1f
                }
            }
        }
    }
}

private val AuroraColors = listOf(Color(0xFF2DE2C0), Color(0xFF60A5FA), Color(0xFFA78BFA), Color(0xFFF472B6), Color(0xFF2DE2C0))
private val RainbowColors = listOf(
    Color(0xFFFF6B8B), Color(0xFFFFB84D), Color(0xFFA3E635), Color(0xFF22D3EE), Color(0xFF818CF8), Color(0xFFF472B6), Color(0xFFFF6B8B)
)

/** The light edge of the island, drawn over its content in the shape [shape]. Everything is read at draw time. */
internal fun Modifier.hostEdge(shape: Density.() -> Shape): Modifier = drawWithContent {
    drawContent()
    val st = HostEdge.style
    val power = HostEdge.strength
    if (st == 0 || power <= 0) return@drawWithContent
    val outline = shape().createOutline(size, layoutDirection, this)
    val path = Path()
    when (outline) {
        is Outline.Rectangle -> path.addRect(outline.rect)
        is Outline.Rounded -> path.addRoundRect(outline.roundRect)
        is Outline.Generic -> path.addPath(outline.path)
    }
    val alpha = (power / 100f).coerceIn(0f, 1f)
    val line = 2.2.dp.toPx()
    val brush = if (st == 1) {
        SolidColor(HostTheme.look?.accent ?: Color(0xFFA78BFA))
    } else {
        val colors = (if (st == 3) RainbowColors else AuroraColors).map { it.toArgb() }.toIntArray()
        val cx = size.width / 2f
        val cy = size.height / 2f
        val shader = android.graphics.SweepGradient(cx, cy, colors, null)
        val m = android.graphics.Matrix()
        m.postRotate(HostEdge.phase * 360f, cx, cy)
        shader.setLocalMatrix(m)
        ShaderBrush(shader)
    }
    // A soft wider line under the thin one gives the edge a little light
    drawPath(path, brush, alpha = alpha * 0.30f, style = Stroke(width = line * 2.6f))
    drawPath(path, brush, alpha = alpha * 0.85f, style = Stroke(width = line))
}
