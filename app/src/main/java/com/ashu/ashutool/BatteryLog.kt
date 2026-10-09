package com.ashu.ashutool

import android.content.Context
import java.io.File

/** Battery level history, kept on disk for 48 hours. Feeds the drain rate and the 24 hour stats. */
object BatteryLog {
    private class Pt(val t: Long, val level: Int, val chg: Boolean)

    private val pts = ArrayList<Pt>()
    private var file: File? = null
    private const val H = 3_600_000L

    @Synchronized
    fun init(ctx: Context) {
        if (file != null) return
        val f = File(ctx.filesDir, "battery.log")
        file = f
        val cut = System.currentTimeMillis() - 48 * H
        try {
            if (f.exists()) f.readLines().forEach { l ->
                val p = l.split(",")
                val t = p.getOrNull(0)?.toLongOrNull()
                val lv = p.getOrNull(1)?.toIntOrNull()
                if (p.size == 3 && t != null && lv != null && t >= cut) pts.add(Pt(t, lv, p[2] == "1"))
            }
            f.writeText(pts.joinToString("") { "${it.t},${it.level},${if (it.chg) 1 else 0}\n" })
        } catch (_: Exception) {
        }
    }

    @Synchronized
    fun add(level: Int, chg: Boolean) {
        val now = System.currentTimeMillis()
        val last = pts.lastOrNull()
        if (last != null && now - last.t < 120_000 && last.level == level && last.chg == chg) return
        pts.add(Pt(now, level, chg))
        try { file?.appendText("$now,$level,${if (chg) 1 else 0}\n") } catch (_: Exception) {}
        val cut = now - 48 * H
        while (pts.isNotEmpty() && pts[0].t < cut) pts.removeAt(0)
    }

    /** Percent per hour over the current charging or discharging stretch. Negative means draining. */
    @Synchronized
    fun rate(): Float? {
        val last = pts.lastOrNull() ?: return null
        var i = pts.size - 1
        while (i > 0) {
            val p = pts[i - 1]
            if (p.chg != last.chg || pts[i].t - p.t > 30 * 60_000L || last.t - p.t > H) break
            i--
        }
        val hrs = (last.t - pts[i].t) / H.toFloat()
        if (hrs < 0.12f) return null
        return (last.level - pts[i].level) / hrs
    }

    @Synchronized
    fun series(hours: Int = 24): List<Float> {
        val cut = System.currentTimeMillis() - hours * H
        return pts.filter { it.t >= cut }.map { it.level.toFloat() }
    }

    /** Total percent drained while unplugged in the last 24 hours. */
    @Synchronized
    fun drain24(): Float {
        val cut = System.currentTimeMillis() - 24 * H
        var d = 0f
        for (i in 1 until pts.size) {
            val a = pts[i - 1]
            val b = pts[i]
            if (b.t >= cut && !a.chg && !b.chg && b.t - a.t <= 30 * 60_000L && a.level > b.level) d += a.level - b.level
        }
        return d
    }

    /** Average percent per hour while unplugged in the last 24 hours. */
    @Synchronized
    fun avg24(): Float {
        val cut = System.currentTimeMillis() - 24 * H
        var ms = 0L
        var d = 0f
        for (i in 1 until pts.size) {
            val a = pts[i - 1]
            val b = pts[i]
            if (b.t >= cut && !a.chg && !b.chg && b.t - a.t <= 30 * 60_000L) {
                ms += b.t - a.t
                if (a.level > b.level) d += a.level - b.level
            }
        }
        val hrs = ms / H.toFloat()
        return if (hrs > 0.2f) d / hrs else 0f
    }
}
