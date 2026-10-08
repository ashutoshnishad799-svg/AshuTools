package com.ashu.ashutool

import android.app.ActivityManager
import android.content.Context
import android.os.StatFs

data class Policy(
    val id: Int, val cpus: String, val freqs: List<Int>,
    val maxF: Int, val minF: Int, val gov: String, val govs: List<String>
)

data class Mem(
    val total: Long, val avail: Long, val cached: Long, val buffers: Long,
    val swapTotal: Long, val swapFree: Long, val swappiness: Int
)

data class Proc(val pid: Int, val rssMb: Int, val name: String)
data class Vol(val label: String, val path: String, val total: Long, val free: Long)
data class DisplayInfo(
    val phys: Int, val cur: Int, val size: String,
    val anim: Float, val touches: Boolean, val awake: Boolean
)

/** Everything that talks to the kernel or the shell, kept out of the UI code. */
object Sys {
    private const val POL = "/sys/devices/system/cpu/cpufreq"

    // ---- CPU clusters
    fun policies(): List<Policy> {
        val lines = Root.run(
            "grep -H . $POL/policy[0-9]*/related_cpus $POL/policy[0-9]*/scaling_available_frequencies " +
                "$POL/policy[0-9]*/scaling_available_governors $POL/policy[0-9]*/scaling_max_freq " +
                "$POL/policy[0-9]*/scaling_min_freq $POL/policy[0-9]*/scaling_governor"
        )
        val kv = HashMap<String, String>()
        for (l in lines) {
            val i = l.indexOf(':')
            if (i > 0) kv[l.substring(0, i).removePrefix("$POL/")] = l.substring(i + 1).trim()
        }
        val ids = kv.keys.mapNotNull { Regex("policy(\\d+)/").find(it)?.groupValues?.get(1)?.toIntOrNull() }
            .distinct().sorted()
        return ids.map { id ->
            fun g(f: String) = kv["policy$id/$f"] ?: ""
            Policy(
                id,
                g("related_cpus").trim().replace(" ", ","),
                g("scaling_available_frequencies").split(" ").mapNotNull { it.toIntOrNull() }.sorted(),
                g("scaling_max_freq").toIntOrNull() ?: 0,
                g("scaling_min_freq").toIntOrNull() ?: 0,
                g("scaling_governor"),
                g("scaling_available_governors").split(" ").filter { it.isNotBlank() }
            )
        }
    }

    fun setGov(id: Int, g: String) = Root.ok("echo $g > $POL/policy$id/scaling_governor")
    fun setMax(id: Int, f: Int) = Root.ok("echo $f > $POL/policy$id/scaling_max_freq")
    fun setMin(id: Int, f: Int) = Root.ok("echo $f > $POL/policy$id/scaling_min_freq")

    // ---- Memory
    fun mem(): Mem {
        val kv = HashMap<String, Long>()
        var swappiness = 60
        for (l in Root.run("cat /proc/meminfo", "cat /proc/sys/vm/swappiness")) {
            val i = l.indexOf(':')
            if (i > 0) kv[l.substring(0, i)] = l.substring(i + 1).trim().split(" ")[0].toLongOrNull() ?: 0L
            else l.trim().toIntOrNull()?.let { swappiness = it }
        }
        return Mem(
            kv["MemTotal"] ?: 0, kv["MemAvailable"] ?: 0, kv["Cached"] ?: 0, kv["Buffers"] ?: 0,
            kv["SwapTotal"] ?: 0, kv["SwapFree"] ?: 0, swappiness
        )
    }

    fun availMb(ctx: Context): Long {
        val mi = ActivityManager.MemoryInfo()
        ctx.getSystemService(ActivityManager::class.java).getMemoryInfo(mi)
        return mi.availMem / 1048576
    }

    fun boost(pkg: String) = Root.ok(
        "for p in \$(pm list packages -3 | cut -d: -f2); do [ \"\$p\" != \"$pkg\" ] && am force-stop \$p; done; true",
        "pm trim-caches 999999999999",
        "sync",
        "echo 3 > /proc/sys/vm/drop_caches"
    )

    // ---- Processes
    fun procs(): List<Proc> = Root.run("ps -A -o PID,RSS,NAME").drop(1).mapNotNull { l ->
        val p = l.trim().split(Regex("\\s+"), 3)
        if (p.size < 3) return@mapNotNull null
        val pid = p[0].toIntOrNull() ?: return@mapNotNull null
        val mb = ((p[1].toLongOrNull() ?: 0L) / 1024).toInt()
        if (mb <= 0) null else Proc(pid, mb, p[2].substringAfterLast('/'))
    }.sortedByDescending { it.rssMb }.take(40)

    // ---- Storage
    fun volumes(): List<Vol> = listOf(
        "Data" to "/data", "System" to "/system", "Vendor" to "/vendor",
        "Product" to "/product", "Cache" to "/cache"
    ).mapNotNull { (label, path) ->
        try {
            val s = StatFs(path)
            if (s.totalBytes > 0) Vol(label, path, s.totalBytes, s.availableBytes) else null
        } catch (e: Exception) {
            null
        }
    }

    // ---- Display
    fun display(): DisplayInfo {
        val lines = Root.run(
            "wm density", "wm size",
            "settings get global window_animation_scale",
            "settings get system show_touches",
            "settings get global stay_on_while_plugged_in"
        )
        fun num(prefix: String) = lines.firstOrNull { it.startsWith(prefix) }
            ?.substringAfter(':')?.trim()?.toIntOrNull()
        val phys = num("Physical density") ?: 0
        val cur = num("Override density") ?: phys
        val size = (lines.firstOrNull { it.startsWith("Override size") } ?: lines.firstOrNull { it.startsWith("Physical size") })
            ?.substringAfter(':')?.trim() ?: "-"
        val plain = lines.filter { !it.contains(':') }.map { it.trim() }
        return DisplayInfo(
            phys, cur, size,
            plain.getOrNull(0)?.toFloatOrNull() ?: 1f,
            plain.getOrNull(1) == "1",
            (plain.getOrNull(2)?.toIntOrNull() ?: 0) != 0
        )
    }

    fun setAnim(v: Float) = Root.ok(
        "settings put global window_animation_scale $v",
        "settings put global transition_animation_scale $v",
        "settings put global animator_duration_scale $v"
    )

    // ---- Device
    fun device(): List<Pair<String, String>> {
        val props = listOf(
            "Model" to "ro.product.model", "Brand" to "ro.product.brand", "Device" to "ro.product.device",
            "Android" to "ro.build.version.release", "SDK level" to "ro.build.version.sdk",
            "ROM build" to "ro.build.display.id", "Security patch" to "ro.build.version.security_patch",
            "Platform" to "ro.board.platform", "ABI" to "ro.product.cpu.abi", "Fingerprint" to "ro.build.fingerprint"
        )
        val cmds = props.map { (l, p) -> "echo \"$l|\$(getprop $p)\"" } + listOf(
            "echo \"Kernel|\$(uname -r)\"",
            "echo \"SELinux|\$(getenforce)\"",
            "echo \"Uptime|\$(cut -d. -f1 /proc/uptime)\""
        )
        return Root.run(*cmds.toTypedArray()).mapNotNull { l ->
            val i = l.indexOf('|')
            if (i > 0) l.substring(0, i) to l.substring(i + 1).trim() else null
        }
    }

    fun fmtUptime(sec: Long): String {
        val d = sec / 86400
        val h = sec % 86400 / 3600
        val m = sec % 3600 / 60
        return if (d > 0) "${d}d ${h}h ${m}m" else "${h}h ${m}m"
    }
}

/** Title and body text for the live notification. Used by the service and by the preview card. */
fun statsLines(ctx: Context, s: Snap): Pair<String, String> {
    val top = mutableListOf<String>()
    if (LockStore.bool(ctx, "n_cpu", true)) top += "CPU ${s.cpu.toInt()}%"
    if (LockStore.bool(ctx, "n_temp", true)) top += "${s.cpuTemp.toInt()}\u00B0C"
    if (LockStore.bool(ctx, "n_ram", true)) top += "RAM ${s.ramPct.toInt()}%"
    val rows = mutableListOf<String>()
    if (LockStore.bool(ctx, "n_batt", true)) {
        val b = s.batt
        rows += "Battery ${b.level}%   ${b.ma} mA   ${"%.1f".format(b.tempC)}\u00B0C"
    }
    if (LockStore.bool(ctx, "n_freq", true)) {
        val mhz = (s.cores.maxOfOrNull { it.khz } ?: 0) / 1000
        rows += "Core max $mhz MHz   ${s.gov}"
    }
    val on = LockStore.bool(ctx, "n_on", true)
    val title = if (on && top.isNotEmpty()) top.joinToString("   ") else "Ashutool is running"
    val body = if (on && rows.isNotEmpty()) rows.joinToString("\n") else "Tap to open"
    return title to body
}
