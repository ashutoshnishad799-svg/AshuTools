package com.ashu.ashutool

import android.content.Context
import java.io.File

/**
 * Temperature seen while each app was in the foreground.
 * Android has no per-app sensor, so this is the closest honest answer: which apps run hot.
 */
object AppThermal {
    class Stat(var secs: Float = 0f, var sumT: Float = 0f, var maxT: Float = 0f, var maxB: Float = 0f, var sumCpu: Float = 0f, var last: Long = 0L)
    class Row(val pkg: String, val avgT: Float, val maxT: Float, val maxB: Float, val avgCpu: Float, val minutes: Int)

    private val map = HashMap<String, Stat>()
    private var file: File? = null
    private var lastSave = 0L

    @Synchronized
    fun init(ctx: Context) {
        if (file != null) return
        val f = File(ctx.filesDir, "appheat.txt")
        file = f
        try {
            if (f.exists()) f.readLines().forEach { l ->
                val p = l.split(",")
                if (p.size == 7) map[p[0]] = Stat(p[1].toFloat(), p[2].toFloat(), p[3].toFloat(), p[4].toFloat(), p[5].toFloat(), p[6].toLong())
            }
        } catch (_: Exception) {
        }
    }

    @Synchronized
    fun record(ctx: Context, cpuT: Float, battT: Float, cpu: Float, dt: Float) {
        init(ctx)
        val pkg = Fg.pkg ?: return
        if (pkg == ctx.packageName || cpuT <= 0f) return
        val now = System.currentTimeMillis()
        val s = map.getOrPut(pkg) { Stat() }
        s.secs += dt
        s.sumT += cpuT * dt
        s.sumCpu += cpu * dt
        if (cpuT > s.maxT) s.maxT = cpuT
        if (battT > s.maxB) s.maxB = battT
        s.last = now
        if (now - lastSave > 60_000) save(now)
    }

    private fun save(now: Long) {
        lastSave = now
        val cut = now - 45L * 86_400_000L
        map.entries.removeAll { it.value.last < cut }
        while (map.size > 150) {
            val oldest = map.entries.minByOrNull { it.value.last } ?: break
            map.remove(oldest.key)
        }
        try {
            file?.writeText(map.entries.joinToString("") { (k, v) -> "$k,${v.secs},${v.sumT},${v.maxT},${v.maxB},${v.sumCpu},${v.last}\n" })
        } catch (_: Exception) {
        }
    }

    /** Apps with at least 30 seconds of data, hottest average first. */
    @Synchronized
    fun top(): List<Row> = map.entries.filter { it.value.secs >= 30f }.map { (k, v) ->
        Row(k, v.sumT / v.secs, v.maxT, v.maxB, v.sumCpu / v.secs, (v.secs / 60f).toInt())
    }.sortedByDescending { it.avgT }

    @Synchronized
    fun reset() {
        map.clear()
        try { file?.writeText("") } catch (_: Exception) {}
    }
}
