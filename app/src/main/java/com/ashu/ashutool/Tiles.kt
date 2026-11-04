/*
 * Ashutool - root toolkit for custom ROMs
 * Copyright (C) 2026 Ashutosh Nishad
 * Telegram: @ashutosh_07x (https://t.me/ashutosh_07x)
 *
 * Free software under the GNU GPL v3.0 or later, with the attribution terms in NOTICE.
 * Keep this header and the author credit in every copy and modified version.
 */


package com.ashu.ashutool

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color as AColor
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Typeface
import android.graphics.drawable.Icon
import android.media.AudioManager
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.service.quicksettings.Tile
import android.service.quicksettings.TileService
import androidx.core.content.ContextCompat

/**
 * The quick settings tiles of Ashutool. There are three, on purpose:
 *  - 5G / 4G: shows "5G" or "4G" as its picture and switches at once. A tap is drawn first, then sent in the order
 *    it was tapped. It talks to a root process that stays running, so nothing has to start for a tap.
 *  - Sound: ring, vibrate and silent in one tile. One tap goes to the next one. No root, no waiting.
 *  - Volume: opens the Ashutool volume panel.
 * Every other switch lives in the game panel or in the app. Android already has its own tiles for Wi-Fi, Bluetooth and so on.
 */
object TileIcons {
    private val cache = HashMap<String, Icon>()

    /** A white "5G" or "4G" badge. The system tints it to match the tile. */
    @Synchronized
    fun badge(text: String): Icon = cache.getOrPut(text) {
        val s = 96
        val bmp = Bitmap.createBitmap(s, s, Bitmap.Config.ARGB_8888)
        val c = Canvas(bmp)
        val line = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE; strokeWidth = 6f; color = AColor.WHITE }
        c.drawRoundRect(RectF(8f, 24f, 88f, 72f), 16f, 16f, line)
        val tp = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = AColor.WHITE
            textSize = 36f
            typeface = Typeface.DEFAULT_BOLD
            textAlign = Paint.Align.CENTER
        }
        c.drawText(text, 48f, 48f - (tp.descent() + tp.ascent()) / 2f, tp)
        Icon.createWithBitmap(bmp)
    }
}

/** The 5G / 4G tile. Same class name as before, so a tile the user already added keeps working. */
class NetTileService : TileService() {
    private val main = Handler(Looper.getMainLooper())
    /** Counts taps, so an answer that was asked for before a tap never paints over what the tap just drew. */
    private var taps = 0

    override fun onStartListening() {
        paint(NetMode.known(this))
        val mark = taps
        NetMode.refresh(this) { v -> main.post { if (v != null && taps == mark) paint(v) } }
    }

    override fun onTileAdded() { onStartListening() }

    override fun onClick() {
        val was = qsTile?.state == Tile.STATE_ACTIVE
        val want = !was
        taps++
        val seq = taps
        paint(want)
        IslandHub.flashNet(applicationContext, want)
        NetMode.apply(this, want) { err ->
            main.post {
                if (err.isNotEmpty()) {
                    if (taps == seq) paint(was)
                    toast(applicationContext, "5G / 4G did not change: ${err.take(70)}")
                }
            }
        }
    }

    private fun paint(on: Boolean?) {
        val t = qsTile ?: return
        t.label = "5G / 4G"
        t.state = if (on == true) Tile.STATE_ACTIVE else Tile.STATE_INACTIVE
        t.icon = TileIcons.badge(if (on == false) "4G" else "5G")
        if (Build.VERSION.SDK_INT >= 29) t.subtitle = when (on) { true -> "5G on"; false -> "4G only"; null -> "Tap to switch" }
        t.updateTile()
    }
}

/** Ring, vibrate and silent in one tile. */
class RingerTile : TileService() {
    private val main = Handler(Looper.getMainLooper())
    private var rx: BroadcastReceiver? = null

    override fun onStartListening() {
        paint(Ringer.mode(this))
        // The volume keys and the system panel change the mode too, so the tile follows
        val r = object : BroadcastReceiver() {
            override fun onReceive(c: Context, i: Intent) { paint(Ringer.mode(this@RingerTile)) }
        }
        try {
            ContextCompat.registerReceiver(this, r, IntentFilter(AudioManager.RINGER_MODE_CHANGED_ACTION), ContextCompat.RECEIVER_NOT_EXPORTED)
            rx = r
        } catch (_: Exception) {}
    }

    override fun onStopListening() {
        rx?.let { try { unregisterReceiver(it) } catch (_: Exception) {} }
        rx = null
    }

    override fun onTileAdded() { paint(Ringer.mode(this)) }

    override fun onClick() {
        val to = Ringer.cycle(this) { paint(it) }
        paint(to)
    }

    private fun paint(mode: Int) {
        val t = qsTile ?: return
        t.label = "Sound"
        t.state = if (mode == Ringer.SILENT) Tile.STATE_INACTIVE else Tile.STATE_ACTIVE
        t.icon = Icon.createWithResource(this, when (mode) {
            Ringer.SILENT -> R.drawable.ic_tile_mute
            Ringer.VIBRATE -> R.drawable.ic_tile_vibrate
            else -> R.drawable.ic_tile_ring
        })
        if (Build.VERSION.SDK_INT >= 29) t.subtitle = Ringer.label(mode)
        t.updateTile()
    }
}

/** Opens the Ashutool volume panel. */
class VolumeTile : TileService() {
    override fun onStartListening() { paint() }
    override fun onTileAdded() { paint() }

    override fun onClick() { VolumePanel.fromTile(applicationContext) }

    private fun paint() {
        val t = qsTile ?: return
        t.label = "Volume"
        t.state = Tile.STATE_INACTIVE
        t.icon = Icon.createWithResource(this, R.drawable.ic_tile_volume)
        if (Build.VERSION.SDK_INT >= 29) t.subtitle = "Open panel"
        t.updateTile()
    }
}
