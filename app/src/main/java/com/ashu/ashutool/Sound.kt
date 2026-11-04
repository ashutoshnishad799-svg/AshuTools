/*
 * Ashutool - root toolkit for custom ROMs
 * Copyright (C) 2026 Ashutosh Nishad
 * Telegram: @ashutosh_07x (https://t.me/ashutosh_07x)
 *
 * Free software under the GNU GPL v3.0 or later, with the attribution terms in NOTICE.
 * Keep this header and the author credit in every copy and modified version.
 */


package com.ashu.ashutool

import android.content.Context
import android.graphics.PixelFormat
import android.media.AudioManager
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.telephony.TelephonyManager
import android.view.Gravity
import android.view.WindowManager
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay

/**
 * Ring, vibrate and silent in one switch. A tap goes to the next one: ring, vibrate, silent, and ring again.
 * It talks to the audio service directly, so it is instant and needs no root. Silent needs Do Not Disturb access on
 * newer Android. Ashutool gets it through root once, only if the system asks for it.
 */
object Ringer {
    const val SILENT = AudioManager.RINGER_MODE_SILENT
    const val VIBRATE = AudioManager.RINGER_MODE_VIBRATE
    const val NORMAL = AudioManager.RINGER_MODE_NORMAL

    fun mode(ctx: Context): Int =
        try { ctx.getSystemService(AudioManager::class.java).ringerMode } catch (e: Exception) { NORMAL }

    /** Ring, then vibrate, then silent, then ring. */
    fun next(cur: Int): Int = when (cur) {
        NORMAL -> VIBRATE
        VIBRATE -> SILENT
        else -> NORMAL
    }

    fun label(mode: Int): String = when (mode) { SILENT -> "Silent"; VIBRATE -> "Vibrate"; else -> "Ring" }

    /** Bell with a cross for silent, a vibrating phone for vibrate, a plain bell for ring. */
    fun icon(mode: Int): ImageVector = when (mode) {
        SILENT -> Icons.Outlined.NotificationsOff
        VIBRATE -> Icons.Outlined.Vibration
        else -> Icons.Outlined.Notifications
    }

    /** Instant, no root. False when the system refused. */
    fun set(ctx: Context, mode: Int): Boolean = try {
        val am = ctx.getSystemService(AudioManager::class.java)
        am.ringerMode = mode
        am.ringerMode == mode
    } catch (e: Exception) { false }

    /** Slower fallback for a system that asks for Do Not Disturb access. Run it off the main thread. */
    fun setWithRoot(ctx: Context, mode: Int): Boolean {
        Root.ok("cmd notification allow_dnd ${ctx.packageName}")
        return set(ctx, mode)
    }

    /** One tap. Returns the mode the screen should show now. */
    fun cycle(ctx: Context, onFixed: (Int) -> Unit = {}): Int {
        val to = next(mode(ctx))
        if (!set(ctx, to)) {
            val app = ctx.applicationContext
            Thread {
                setWithRoot(app, to)
                Handler(Looper.getMainLooper()).post { onFixed(mode(app)) }
            }.start()
        }
        return to
    }
}

private class VolCh(val stream: Int, val label: String, val icon: ImageVector)

/**
 * The Ashutool volume panel. It opens from the quick settings tile, the Home page and the game sidebar.
 * Small shows one slider, Medium shows media, ring and alarm, Big shows more and is larger.
 * It sits on the left or right edge, follows the volume keys while it is open and closes by itself.
 */
object VolumePanel {
    private val main = Handler(Looper.getMainLooper())
    private var overlay: Overlay? = null
    private var touch by mutableIntStateOf(0)
    private var closing by mutableStateOf(false)
    @Volatile private var open = false

    val isOpen: Boolean get() = open

    /** From a quick settings tile: the panel of tiles is closed first, or the volume panel would be behind it. */
    fun fromTile(ctx: Context) {
        val app = ctx.applicationContext
        Thread {
            Root.run("cmd statusbar collapse")
            try { Thread.sleep(170) } catch (_: InterruptedException) {}
            main.post { show(app) }
        }.start()
    }

    fun show(ctx: Context) {
        val app = ctx.applicationContext
        if (!Settings.canDrawOverlays(app)) { toast(app, "Allow draw over other apps first"); return }
        val ov = overlay ?: Overlay(app).also { overlay = it }
        touch++
        if (ov.isShown && !closing) return
        closing = false
        val d = app.resources.displayMetrics.density
        val mode = LockStore.int(app, "vp_size", 1).coerceIn(0, 2)
        val side = LockStore.int(app, "vp_side", 1).coerceIn(0, 1)
        val (_, sh) = screenSize(app)
        val inCall = Calls.state == TelephonyManager.CALL_STATE_OFFHOOK
        val count = channels(mode, inCall).size
        val sliderW = when (mode) { 0 -> 46; 1 -> 52; else -> 66 }
        val w = ((count * (sliderW + 14) + 26) * d).toInt()
        val sliderH = when (mode) { 0 -> 150; 1 -> 190; else -> 250 }
        val h = ((sliderH + 120) * d).toInt().coerceAtMost((sh * 0.92f).toInt())
        val lp = WindowManager.LayoutParams(
            w, h, WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL or
                WindowManager.LayoutParams.FLAG_WATCH_OUTSIDE_TOUCH or WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS or
                WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
            PixelFormat.TRANSLUCENT
        )
        lp.gravity = (if (side == 0) Gravity.START else Gravity.END) or Gravity.CENTER_VERTICAL
        lp.x = (4 * d).toInt()
        Glassy.blur(app, lp, 40)
        open = true
        ov.show(lp, onOutside = { hide() }) { Content(app, mode, side, sliderW, sliderH, inCall) }
        if (!ov.isShown) open = false
    }

    fun hide() {
        val ov = overlay ?: return
        if (!ov.isShown || closing) return
        closing = true
        main.postDelayed({
            if (closing) { ov.remove(); closing = false; open = false }
        }, 230)
    }

    private fun channels(mode: Int, inCall: Boolean): List<VolCh> {
        val media = VolCh(AudioManager.STREAM_MUSIC, "Media", Icons.Outlined.MusicNote)
        val ring = VolCh(AudioManager.STREAM_RING, "Ring", Icons.Outlined.Notifications)
        val alarm = VolCh(AudioManager.STREAM_ALARM, "Alarm", Icons.Outlined.Alarm)
        val call = VolCh(AudioManager.STREAM_VOICE_CALL, "Call", Icons.Outlined.Call)
        val alerts = VolCh(AudioManager.STREAM_NOTIFICATION, "Alerts", Icons.Outlined.NotificationsActive)
        return when (mode) {
            0 -> listOf(if (inCall) call else media)
            1 -> if (inCall) listOf(call, media, ring) else listOf(media, ring, alarm)
            else -> if (inCall) listOf(call, media, ring, alarm) else listOf(media, ring, alarm, alerts)
        }
    }

    @Composable
    private fun Content(ctx: Context, mode: Int, side: Int, sliderW: Int, sliderH: Int, inCall: Boolean) {
        val am = remember { ctx.getSystemService(AudioManager::class.java) }
        val chans = remember(mode, inCall) { channels(mode, inCall) }
        val vols = remember { mutableStateMapOf<Int, Float>() }
        val dragging = remember { mutableStateMapOf<Int, Boolean>() }
        var ringer by remember { mutableIntStateOf(Ringer.mode(ctx)) }
        val seconds = LockStore.int(ctx, "vp_secs", 4).coerceIn(2, 12)

        LaunchedEffect(Unit) {
            while (true) {
                for (c in chans) {
                    if (dragging[c.stream] != true) {
                        val mx = am.getStreamMaxVolume(c.stream).coerceAtLeast(1)
                        vols[c.stream] = am.getStreamVolume(c.stream).toFloat() / mx
                    }
                }
                ringer = Ringer.mode(ctx)
                delay(200)
            }
        }
        // Close by itself after a few quiet seconds. Any touch or volume change starts the count again.
        LaunchedEffect(touch) {
            delay(seconds * 1000L)
            hide()
        }
        val a by animateFloatAsState(if (closing) 0f else 1f, tween(220, easing = FastOutSlowInEasing), label = "vpa")
        val shape = RoundedCornerShape(if (mode == 2) 34.dp else 28.dp)
        val pal = Palette.cur
        Column(
            Modifier.fillMaxSize().padding(6.dp)
                .graphicsLayer {
                    alpha = a
                    val sc = 0.92f + 0.08f * a
                    scaleX = sc; scaleY = sc
                    translationX = (1f - a) * 40f * (if (side == 0) -1f else 1f)
                }
                .clip(shape)
                .then(Glassy.fill(pal, 0.26f))
                .border(1.dp, Glassy.stroke(pal), shape)
                .padding(horizontal = 10.dp, vertical = 12.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Box(
                Modifier.size(if (mode == 0) 38.dp else 44.dp).clip(CircleShape).background(Accent.copy(alpha = 0.22f))
                    .border(1.dp, Accent.copy(alpha = 0.5f), CircleShape)
                    .pointerInput(Unit) {
                        detectTapGestures {
                            touch++
                            ringer = Ringer.cycle(ctx) { ringer = it }
                        }
                    },
                contentAlignment = Alignment.Center
            ) { Icon(Ringer.icon(ringer), Ringer.label(ringer), tint = TextHi, modifier = Modifier.size(if (mode == 0) 20.dp else 24.dp)) }
            Spacer(Modifier.height(10.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                chans.forEach { c ->
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(
                            "${((vols[c.stream] ?: 0f) * 100).toInt()}",
                            color = TextHi, fontSize = if (mode == 2) 13.sp else 11.sp, fontWeight = FontWeight.SemiBold
                        )
                        Spacer(Modifier.height(6.dp))
                        VSlider(vols[c.stream] ?: 0f, sliderW.dp, sliderH.dp, Accent,
                            onChange = { f ->
                                dragging[c.stream] = true
                                vols[c.stream] = f
                                touch++
                                try {
                                    val mx = am.getStreamMaxVolume(c.stream).coerceAtLeast(1)
                                    am.setStreamVolume(c.stream, (f * mx + 0.5f).toInt().coerceIn(0, mx), 0)
                                } catch (_: Exception) {}
                            },
                            onDone = { dragging[c.stream] = false }
                        )
                        Spacer(Modifier.height(8.dp))
                        Icon(c.icon, c.label, tint = TextLo, modifier = Modifier.size(if (mode == 2) 22.dp else 18.dp))
                        if (mode != 0) Text(c.label, color = TextLo, fontSize = 9.sp)
                    }
                }
            }
        }
    }

    @Composable
    private fun VSlider(frac: Float, w: Dp, h: Dp, accent: Color, onChange: (Float) -> Unit, onDone: () -> Unit) {
        val change by rememberUpdatedState(onChange)
        val done by rememberUpdatedState(onDone)
        val shape = RoundedCornerShape(w / 2)
        val f by animateFloatAsState(frac.coerceIn(0f, 1f), tween(90), label = "vf")
        Box(
            Modifier.size(w, h).clip(shape)
                .background(Tint.copy(alpha = 0.16f))
                .pointerInput(Unit) {
                    detectVerticalDragGestures(
                        onDragStart = { o -> change(1f - (o.y / size.height).coerceIn(0f, 1f)) },
                        onDragEnd = { done() },
                        onDragCancel = { done() },
                        onVerticalDrag = { c, _ -> c.consume(); change(1f - (c.position.y / size.height).coerceIn(0f, 1f)) }
                    )
                }
                .pointerInput(Unit) {
                    detectTapGestures { o -> change(1f - (o.y / size.height).coerceIn(0f, 1f)); done() }
                }
        ) {
            Box(
                Modifier.align(Alignment.BottomCenter).fillMaxWidth().fillMaxHeight(f)
                    .background(Brush.verticalGradient(listOf(accent, accent.copy(alpha = 0.55f))))
            )
        }
    }
}
