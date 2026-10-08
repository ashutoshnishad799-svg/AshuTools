package com.ashu.ashutool

import android.app.ActivityManager
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.BatteryManager
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

data class Core(val id: Int, val online: Boolean, val khz: Int, val maxKhz: Int)

data class Batt(
    val level: Int = 0,
    val status: String = "-",
    val charging: Boolean = false,
    val plugged: String = "-",
    val health: String = "-",
    val tempC: Float = 0f,
    val mv: Int = 0,
    val ma: Int = 0,
    val tech: String = "-",
    val capHealth: Int = -1,
    val cycles: Int = -1
)

data class Snap(
    val cpu: Float = 0f,
    val cores: List<Core> = emptyList(),
    val gov: String = "-",
    val cpuTemp: Float = 0f,
    val ramPct: Float = 0f,
    val ramUsedMb: Long = 0,
    val ramTotalMb: Long = 0,
    val batt: Batt = Batt(),
    val cpuHist: List<Float> = emptyList(),
    val tempHist: List<Float> = emptyList(),
    val currHist: List<Float> = emptyList(),
    val ramHist: List<Float> = emptyList(),
    val cpuTempHist: List<Float> = emptyList(),
    val zones: List<Pair<String, Float>> = emptyList()
)

/** Samples CPU, RAM, thermal and battery data every 2 seconds. */
object Monitor {
    private val _snap = MutableStateFlow(Snap())
    val snap: StateFlow<Snap> = _snap

    private var job: Job? = null
    private var prevTotal = 0L
    private var prevIdle = 0L
    private val cpuH = ArrayList<Float>()
    private val tempH = ArrayList<Float>()
    private val currH = ArrayList<Float>()
    private val ramH = ArrayList<Float>()
    private val cTempH = ArrayList<Float>()

    fun start(ctx: Context) {
        if (job?.isActive == true) return
        val app = ctx.applicationContext
        job = CoroutineScope(SupervisorJob() + Dispatchers.IO).launch {
            while (isActive) {
                try {
                    _snap.value = sample(app, _snap.value)
                } catch (_: Exception) {
                }
                delay(LockStore.int(app, "n_interval", 2000).toLong())
            }
        }
    }

    private fun push(list: ArrayList<Float>, v: Float): List<Float> {
        list.add(v)
        if (list.size > 60) list.removeAt(0)
        return list.toList()
    }

    private fun parseRange(s: String?): Set<Int> {
        if (s.isNullOrBlank()) return emptySet()
        val r = HashSet<Int>()
        for (p in s.split(",")) {
            val a = p.trim().split("-")
            val lo = a[0].toIntOrNull() ?: continue
            val hi = a.getOrNull(1)?.toIntOrNull() ?: lo
            for (i in lo..hi) r.add(i)
        }
        return r
    }

    private fun sample(ctx: Context, old: Snap): Snap {
        val lines = Root.run(
            "head -n 1 /proc/stat",
            "grep -H . /sys/devices/system/cpu/cpu[0-9]*/cpufreq/scaling_cur_freq /sys/devices/system/cpu/cpu[0-9]*/cpufreq/cpuinfo_max_freq",
            "grep -H . /sys/devices/system/cpu/online /sys/devices/system/cpu/present",
            "grep -H . /sys/devices/system/cpu/cpu0/cpufreq/scaling_governor",
            "grep -H . /sys/class/thermal/thermal_zone[0-9]*/type /sys/class/thermal/thermal_zone[0-9]*/temp",
            "grep -H . /sys/class/power_supply/battery/charge_full /sys/class/power_supply/battery/charge_full_design /sys/class/power_supply/battery/cycle_count"
        )
        var statLine = ""
        val kv = HashMap<String, String>()
        for (l in lines) {
            if (l.startsWith("cpu ")) statLine = l
            else {
                val i = l.indexOf(':')
                if (i > 0) kv[l.substring(0, i)] = l.substring(i + 1).trim()
            }
        }

        // CPU load from /proc/stat deltas
        var cpu = old.cpu
        val p = statLine.trim().split(Regex("\\s+")).drop(1).mapNotNull { it.toLongOrNull() }
        if (p.size >= 5) {
            val total = p.take(8).sum()
            val idle = p[3] + p[4]
            if (prevTotal != 0L && total > prevTotal) {
                cpu = ((total - prevTotal - (idle - prevIdle)).toFloat() / (total - prevTotal) * 100f).coerceIn(0f, 100f)
            }
            prevTotal = total
            prevIdle = idle
        }

        // Cores
        val base = "/sys/devices/system/cpu"
        val online = parseRange(kv["$base/online"])
        var present = parseRange(kv["$base/present"])
        if (present.isEmpty()) present = online
        val cores = present.sorted().map { id ->
            Core(
                id,
                id in online,
                kv["$base/cpu$id/cpufreq/scaling_cur_freq"]?.toIntOrNull() ?: 0,
                kv["$base/cpu$id/cpufreq/cpuinfo_max_freq"]?.toIntOrNull() ?: 0
            )
        }
        val gov = kv["$base/cpu0/cpufreq/scaling_governor"] ?: old.gov

        // Thermal: prefer a zone that looks like CPU / SoC
        val zones = kv.keys.filter { it.contains("thermal_zone") && it.endsWith("/type") }.mapNotNull { k ->
            val t = kv[k.removeSuffix("/type") + "/temp"]?.toLongOrNull() ?: return@mapNotNull null
            if (t > 0) Pair(kv[k] ?: "", t) else null
        }
        val pick = zones.firstOrNull { Regex("cpu|soc|tsens|cluster|big|little", RegexOption.IGNORE_CASE).containsMatchIn(it.first) }
            ?: zones.firstOrNull()
        val cpuTemp = pick?.second?.let { if (it > 1000) it / 1000f else it.toFloat() } ?: old.cpuTemp

        // RAM
        val am = ctx.getSystemService(ActivityManager::class.java)
        val mi = ActivityManager.MemoryInfo()
        am.getMemoryInfo(mi)
        val totalMb = mi.totalMem / 1048576
        val usedMb = totalMb - mi.availMem / 1048576

        // Battery
        val bi = ctx.registerReceiver(null, IntentFilter(Intent.ACTION_BATTERY_CHANGED))
        val bm = ctx.getSystemService(BatteryManager::class.java)
        val level = bi?.getIntExtra(BatteryManager.EXTRA_LEVEL, 0) ?: 0
        val scale = (bi?.getIntExtra(BatteryManager.EXTRA_SCALE, 100) ?: 100).coerceAtLeast(1)
        val status = bi?.getIntExtra(BatteryManager.EXTRA_STATUS, -1) ?: -1
        val charging = status == BatteryManager.BATTERY_STATUS_CHARGING || status == BatteryManager.BATTERY_STATUS_FULL
        val statusText = when (status) {
            BatteryManager.BATTERY_STATUS_CHARGING -> "Charging"
            BatteryManager.BATTERY_STATUS_DISCHARGING -> "Discharging"
            BatteryManager.BATTERY_STATUS_FULL -> "Full"
            BatteryManager.BATTERY_STATUS_NOT_CHARGING -> "Not charging"
            else -> "Unknown"
        }
        val plugged = when (bi?.getIntExtra(BatteryManager.EXTRA_PLUGGED, 0) ?: 0) {
            BatteryManager.BATTERY_PLUGGED_AC -> "AC"
            BatteryManager.BATTERY_PLUGGED_USB -> "USB"
            BatteryManager.BATTERY_PLUGGED_WIRELESS -> "Wireless"
            else -> "Battery"
        }
        val health = when (bi?.getIntExtra(BatteryManager.EXTRA_HEALTH, 0) ?: 0) {
            BatteryManager.BATTERY_HEALTH_GOOD -> "Good"
            BatteryManager.BATTERY_HEALTH_OVERHEAT -> "Overheat"
            BatteryManager.BATTERY_HEALTH_DEAD -> "Dead"
            BatteryManager.BATTERY_HEALTH_OVER_VOLTAGE -> "Over voltage"
            BatteryManager.BATTERY_HEALTH_COLD -> "Cold"
            else -> "Unknown"
        }
        val raw = bm.getIntProperty(BatteryManager.BATTERY_PROPERTY_CURRENT_NOW)
        val ma = Math.abs(if (Math.abs(raw) > 20000) raw / 1000 else raw)
        val full = kv["/sys/class/power_supply/battery/charge_full"]?.toLongOrNull() ?: 0L
        val design = kv["/sys/class/power_supply/battery/charge_full_design"]?.toLongOrNull() ?: 0L
        val cycles = kv["/sys/class/power_supply/battery/cycle_count"]?.toIntOrNull() ?: -1
        val batt = Batt(
            level = level * 100 / scale,
            status = statusText,
            charging = charging,
            plugged = plugged,
            health = health,
            tempC = (bi?.getIntExtra(BatteryManager.EXTRA_TEMPERATURE, 0) ?: 0) / 10f,
            mv = bi?.getIntExtra(BatteryManager.EXTRA_VOLTAGE, 0) ?: 0,
            ma = ma,
            tech = bi?.getStringExtra(BatteryManager.EXTRA_TECHNOLOGY) ?: "-",
            capHealth = if (full > 0 && design > 0) (full * 100 / design).toInt().coerceAtMost(100) else -1,
            cycles = if (cycles > 0) cycles else -1
        )

        val zoneList = zones.map { (n, t) -> n to (if (t > 1000) t / 1000f else t.toFloat()) }
            .sortedByDescending { it.second }
        val ramPct = if (totalMb > 0) usedMb * 100f / totalMb else 0f

        return Snap(
            cpu = cpu,
            cores = cores,
            gov = gov,
            cpuTemp = cpuTemp,
            ramPct = ramPct,
            ramUsedMb = usedMb,
            ramTotalMb = totalMb,
            batt = batt,
            cpuHist = push(cpuH, cpu),
            tempHist = push(tempH, batt.tempC),
            currHist = push(currH, ma.toFloat()),
            ramHist = push(ramH, ramPct),
            cpuTempHist = push(cTempH, cpuTemp),
            zones = zoneList
        )
    }
}
