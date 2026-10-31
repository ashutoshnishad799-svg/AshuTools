/*
 * Ashutool - root toolkit for custom ROMs
 * Copyright (C) 2026 Ashutosh Nishad
 * Telegram: @ashutosh_07x (https://t.me/ashutosh_07x)
 *
 * Free software under the GNU GPL v3.0 or later, with the attribution terms in NOTICE.
 * Keep this header and the author credit in every copy and modified version.
 */

package com.ashu.ashutool

import android.app.NotificationManager
import android.content.Context
import android.os.BatteryManager
import android.os.SystemClock
import androidx.core.app.NotificationCompat

/**
 * Battery current readings of the last six hours, for the Electric current page.
 * Kept in memory only. In the background a reading comes every twenty seconds or so, so it stays small.
 */
object CurrentLog {
    private class P(val t: Long, val ma: Int, val mv: Int, val chg: Boolean)

    private val pts = ArrayDeque<P>()
    private const val KEEP_MS = 6 * 3_600_000L + 60_000L
    private const val MAX = 6000
    private const val MIN_GAP = 2000L

    class Stat(val avg: Int, val min: Int, val max: Int, val n: Int)

    @Synchronized
    fun add(b: Batt) {
        if (b.ma <= 0) return
        val now = System.currentTimeMillis()
        val last = pts.lastOrNull()
        if (last != null && now - last.t < MIN_GAP) return
        pts.addLast(P(now, b.ma, b.mv, b.charging))
        val cut = now - KEEP_MS
        while (pts.isNotEmpty() && pts.first().t < cut) pts.removeFirst()
        while (pts.size > MAX) pts.removeFirst()
    }

    /** Average, lowest and highest current over the last [windowMs], while charging or while discharging. */
    @Synchronized
    fun stat(windowMs: Long, chg: Boolean): Stat {
        val cut = System.currentTimeMillis() - windowMs
        var n = 0
        var sum = 0L
        var mn = Int.MAX_VALUE
        var mx = 0
        for (p in pts) {
            if (p.t < cut || p.chg != chg) continue
            n++
            sum += p.ma
            if (p.ma < mn) mn = p.ma
            if (p.ma > mx) mx = p.ma
        }
        return if (n == 0) Stat(0, 0, 0, 0) else Stat((sum / n).toInt(), mn, mx, n)
    }

    /** The window cut into [buckets] equal parts, each the average of its readings. A chart starts at the first reading it has. */
    @Synchronized
    fun series(windowMs: Long, chg: Boolean, buckets: Int = 60): List<Float> {
        val from = System.currentTimeMillis() - windowMs
        val sum = FloatArray(buckets)
        val cnt = IntArray(buckets)
        for (p in pts) {
            if (p.t < from || p.chg != chg) continue
            val i = (((p.t - from).toDouble() / windowMs) * buckets).toInt().coerceIn(0, buckets - 1)
            sum[i] += p.ma.toFloat()
            cnt[i]++
        }
        val first = (0 until buckets).firstOrNull { cnt[it] > 0 } ?: return emptyList()
        val out = ArrayList<Float>(buckets - first)
        var last = 0f
        for (i in first until buckets) {
            if (cnt[i] > 0) last = sum[i] / cnt[i]
            out.add(last)
        }
        return out
    }
}

/**
 * Totals since the phone was last plugged in or unplugged, split into screen on, screen off, deep sleep and held awake.
 * Time comes from the system clocks. Milliamp hours come from the battery's own charge counter, when the phone has one.
 * Nothing runs for it: it only does a little arithmetic when the screen turns on or off, and with each battery reading
 * that is taken anyway.
 */
object Session {
    class View(
        val charging: Boolean,
        val totalMs: Long, val onMs: Long, val offMs: Long, val deepMs: Long, val awakeMs: Long,
        /** Milliamp hours moved while the screen was on and off. Negative when the phone has no charge counter. */
        val onMah: Float, val offMah: Float,
        val startLevel: Int, val level: Int
    ) {
        val hasMah: Boolean get() = onMah >= 0f && offMah >= 0f
        val mah: Float get() = if (hasMah) onMah + offMah else -1f
    }

    private var valid = false
    private var charging = false
    private var startLevel = 0
    private var lastLevel = 0
    private var startAt = 0L
    private var segAt = 0L
    private var segUp = 0L
    private var segUah = 0L
    private var segOn = true
    private var onMs = 0L
    private var offMs = 0L
    private var deepMs = 0L
    private var awakeMs = 0L
    private var onUah = 0L
    private var offUah = 0L
    private var hasCounter = false

    private fun counter(ctx: Context): Long = try {
        ctx.getSystemService(BatteryManager::class.java).getLongProperty(BatteryManager.BATTERY_PROPERTY_CHARGE_COUNTER)
    } catch (e: Exception) { 0L }

    private fun begin(ctx: Context, chg: Boolean, level: Int, screenOn: Boolean) {
        val c = counter(ctx)
        valid = true
        charging = chg
        startLevel = level
        lastLevel = level
        startAt = SystemClock.elapsedRealtime()
        segAt = startAt
        segUp = SystemClock.uptimeMillis()
        segUah = c
        segOn = screenOn
        onMs = 0L; offMs = 0L; deepMs = 0L; awakeMs = 0L; onUah = 0L; offUah = 0L
        hasCounter = c > 0L
    }

    /** Adds the time since the last change to the screen on or the screen off total. */
    private fun close(ctx: Context) {
        val now = SystemClock.elapsedRealtime()
        val up = SystemClock.uptimeMillis()
        val dt = (now - segAt).coerceAtLeast(0L)
        val dUp = (up - segUp).coerceIn(0L, dt)
        val c = counter(ctx)
        val dUah = if (hasCounter && c > 0L && segUah > 0L) Math.abs(c - segUah) else 0L
        if (segOn) {
            onMs += dt
            onUah += dUah
        } else {
            offMs += dt
            awakeMs += dUp
            deepMs += dt - dUp
            offUah += dUah
        }
        segAt = now
        segUp = up
        segUah = c
    }

    /** Called with every battery reading. A change between charging and discharging starts a new session. */
    @Synchronized
    fun tick(ctx: Context, chg: Boolean, level: Int, screenOn: Boolean) {
        if (!valid || chg != charging) { begin(ctx, chg, level, screenOn); return }
        lastLevel = level
        if (screenOn != segOn) { close(ctx); segOn = screenOn }
    }

    /** Called the moment the screen turns on or off, so the split is exact and not late by a reading. */
    @Synchronized
    fun screen(ctx: Context, on: Boolean) {
        if (!valid || on == segOn) return
        close(ctx)
        segOn = on
    }

    /** The totals so far, including the stretch that is still running. Null before the first battery reading. */
    @Synchronized
    fun view(ctx: Context): View? {
        if (!valid) return null
        val now = SystemClock.elapsedRealtime()
        val up = SystemClock.uptimeMillis()
        val dt = (now - segAt).coerceAtLeast(0L)
        val dUp = (up - segUp).coerceIn(0L, dt)
        val c = counter(ctx)
        val dUah = if (hasCounter && c > 0L && segUah > 0L) Math.abs(c - segUah) else 0L
        var on = onMs; var off = offMs; var deep = deepMs; var awake = awakeMs; var onU = onUah; var offU = offUah
        if (segOn) { on += dt; onU += dUah } else { off += dt; awake += dUp; deep += dt - dUp; offU += dUah }
        return View(
            charging, (now - startAt).coerceAtLeast(0L), on, off, deep, awake,
            if (hasCounter) onU / 1000f else -1f, if (hasCounter) offU / 1000f else -1f,
            startLevel, lastLevel
        )
    }
}

/** Notifies when the battery gets too hot or too cold, with limits you choose. Off by default. */
object TempAlarm {
    const val CHANNEL = "alerts"
    private const val ID_HOT = 2001
    private const val ID_COLD = 2002
    private const val GAP_MS = 10 * 60_000L
    private var lastHot = 0L
    private var lastCold = 0L

    fun check(ctx: Context, s: Snap) {
        if (!LockStore.bool(ctx, "ta_on", false)) return
        val t = s.batt.tempC
        // Zero means the sensor gave nothing
        if (t <= 0f) return
        val hi = LockStore.int(ctx, "ta_hi", 42)
        val lo = LockStore.int(ctx, "ta_lo", 5)
        val now = SystemClock.elapsedRealtime()
        if (t >= hi && (lastHot == 0L || now - lastHot > GAP_MS)) {
            lastHot = now
            post(ctx, ID_HOT, "Battery is hot", "${"%.1f".format(t)}\u00B0C, above your limit of $hi\u00B0C. Close heavy apps or unplug the charger to let it cool.")
        } else if (t <= lo && (lastCold == 0L || now - lastCold > GAP_MS)) {
            lastCold = now
            post(ctx, ID_COLD, "Battery is cold", "${"%.1f".format(t)}\u00B0C, below your limit of $lo\u00B0C. Cold batteries drain faster and charge slowly.")
        }
    }

    private fun post(ctx: Context, id: Int, title: String, body: String) {
        try {
            val n = NotificationCompat.Builder(ctx, CHANNEL)
                .setSmallIcon(R.drawable.ic_stat_bolt)
                .setContentTitle(title)
                .setContentText(body)
                .setStyle(NotificationCompat.BigTextStyle().bigText(body))
                .setAutoCancel(true)
                .setPriority(NotificationCompat.PRIORITY_HIGH)
                .build()
            ctx.getSystemService(NotificationManager::class.java).notify(id, n)
        } catch (_: Exception) {}
    }
}
