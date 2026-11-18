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
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.os.SystemClock
import android.os.VibrationEffect
import android.os.Vibrator
import android.telephony.TelephonyManager
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.*
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.Color

// =====================================================================================================================
// Things the island shows that are not read from the system notifications:
//
//   IslandClock  a stopwatch and a countdown timer. They work on the clock of the phone, so nothing has to tick while
//                they run. The island only redraws the numbers while it is on screen.
//   IslandNet    the speed of the network and what it is connected to
// =====================================================================================================================

/** 01:05 or 1:02:03, with hundredths when [centis] is set. */
fun islandClockText(ms: Long, centis: Boolean = false): String {
    val v = ms.coerceAtLeast(0L)
    val h = v / 3_600_000L
    val m = (v / 60_000L) % 60L
    val s = (v / 1000L) % 60L
    val base = if (h > 0L) "%d:%02d:%02d".format(h, m, s) else "%02d:%02d".format(m, s)
    return if (centis) base + ".%02d".format((v % 1000L) / 10L) else base
}

object IslandClock {
    /** 0 nothing is running, 1 stopwatch, 2 countdown timer. */
    var mode by mutableIntStateOf(0)
        private set
    var running by mutableStateOf(false)
        private set
    /** Length of the countdown in ms. */
    var totalMs by mutableLongStateOf(0L)
        private set
    /** Lap times of the stopwatch in ms from the start, newest first. */
    var laps by mutableStateOf<List<Long>>(emptyList())
        private set

    private var startedAt = 0L
    private var accMs = 0L

    private fun now() = SystemClock.elapsedRealtime()

    /** Time since the start, not counting pauses. */
    fun elapsed(): Long = accMs + if (running) now() - startedAt else 0L

    fun remaining(): Long = (totalMs - elapsed()).coerceAtLeast(0L)

    /** What the island draws: counts up for a stopwatch, down for a timer. The timer rounds up, so it reads 00:01 until it is done. */
    fun shown(): Long = if (mode == 2) remaining() else elapsed()

    fun text(centis: Boolean = false): String {
        val v = shown()
        return if (mode == 2 && !centis) islandClockText(((v + 999L) / 1000L) * 1000L) else islandClockText(v, centis && mode == 1)
    }

    fun startStopwatch() = begin(1, 0L)

    fun startTimer(ms: Long) = begin(2, ms.coerceAtLeast(1000L))

    private fun begin(m: Int, total: Long) {
        mode = m
        totalMs = total
        accMs = 0L
        startedAt = now()
        laps = emptyList()
        running = true
        publish()
    }

    /** Pause or go on. */
    fun toggle() {
        if (mode == 0) return
        if (running) { accMs = elapsed(); running = false } else { startedAt = now(); running = true }
        publish()
    }

    /** Timer: one more minute. */
    fun addMinute() {
        if (mode != 2) return
        totalMs += 60_000L
        publish()
    }

    /** Stopwatch: note the time without stopping. */
    fun lap() {
        if (mode == 1 && running) laps = (listOf(elapsed()) + laps).take(20)
    }

    /** Ends it and takes it off the island. */
    fun stop() {
        mode = 0
        running = false
        accMs = 0L
        totalMs = 0L
        laps = emptyList()
        IslandHub.remove("clock")
    }

    /** Called a few times a second while a timer runs, and once when the screen comes back on. */
    fun poll(ctx: Context) {
        if (mode == 2 && running && remaining() <= 0L) finish(ctx)
    }

    private fun finish(ctx: Context) {
        stop()
        IslandHub.flash(ctx, "clock_done", "Time's up", "Your timer has finished", Icons.Outlined.Alarm, Color(0xFFFFB84D), "", 12)
        IslandHub.expandNow()
        buzz(ctx)
    }

    private fun publish() {
        IslandHub.upsert(
            IAct(
                "clock", IKind.TIMER, 58,
                if (mode == 1) "Stopwatch" else "Timer", "",
                if (mode == 1) Icons.Outlined.Timer else Icons.Outlined.HourglassBottom,
                "", "", Color(0xFFFFB84D)
            )
        )
    }

    private fun buzz(ctx: Context) {
        try {
            val v = ctx.getSystemService(Vibrator::class.java) ?: return
            v.vibrate(VibrationEffect.createWaveform(longArrayOf(0, 260, 140, 260, 140, 480), -1))
        } catch (_: Exception) {}
    }
}

object IslandNet {
    var down by mutableLongStateOf(0L)
        private set
    var up by mutableLongStateOf(0L)
        private set
    /** "Wi-Fi", "5G", "4G", "Mobile data", "Ethernet", "VPN" or "Offline". */
    var type by mutableStateOf("")
        private set
    var online by mutableStateOf(true)
        private set
    var wifi by mutableStateOf(false)
        private set
    /** The last 40 download speeds in bytes per second, newest last. Drawn as the small graph. */
    var history by mutableStateOf<List<Float>>(emptyList())
        private set

    fun push(d: Long, u: Long) {
        down = d.coerceAtLeast(0L)
        up = u.coerceAtLeast(0L)
        history = (history + down.toFloat()).takeLast(40)
    }

    /** "850 KB/s" or "2.4 MB/s". */
    fun rate(bps: Long): String {
        val v = bps.coerceAtLeast(0L)
        return if (v >= 1_000_000L) "%.1f MB/s".format(v / 1_000_000f) else "${v / 1000L} KB/s"
    }

    /** Finds out what the phone is connected to. Cheap, but not meant for every frame. */
    fun readType(ctx: Context) {
        try {
            val cm = ctx.getSystemService(ConnectivityManager::class.java) ?: return
            val nw = cm.activeNetwork
            val caps = if (nw != null) cm.getNetworkCapabilities(nw) else null
            when {
                caps == null -> { online = false; wifi = false; type = "Offline" }
                caps.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) -> { online = true; wifi = true; type = "Wi-Fi" }
                caps.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR) -> { online = true; wifi = false; type = mobileGen(ctx) }
                caps.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET) -> { online = true; wifi = false; type = "Ethernet" }
                caps.hasTransport(NetworkCapabilities.TRANSPORT_VPN) -> { online = true; wifi = false; type = "VPN" }
                else -> { online = true; wifi = false; type = "Connected" }
            }
        } catch (_: Exception) {}
    }

    private fun mobileGen(ctx: Context): String = try {
        val tm = ctx.getSystemService(TelephonyManager::class.java)
        when (tm.dataNetworkType) {
            20 -> "5G"
            13, 19 -> "4G"
            3, 5, 6, 8, 9, 10, 12, 14, 15, 17 -> "3G"
            1, 2, 4, 7, 11 -> "2G"
            else -> "Mobile data"
        }
    } catch (_: Exception) { "Mobile data" }
}
