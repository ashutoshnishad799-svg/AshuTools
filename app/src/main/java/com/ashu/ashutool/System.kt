package com.ashu.ashutool

import android.app.ActivityManager
import android.content.Context
import android.content.pm.ApplicationInfo
import android.os.StatFs
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue

data class Policy(
    val id: Int, val cpus: String, val freqs: List<Int>,
    val maxF: Int, val minF: Int, val gov: String, val govs: List<String>
)

data class Mem(
    val total: Long, val avail: Long, val cached: Long, val buffers: Long,
    val swapTotal: Long, val swapFree: Long, val swappiness: Int
)

private val appUser = Regex("^u\\d+_a\\d+$")

data class Proc(val pid: Int, val user: String, val rssMb: Int, val name: String) {
    /** Only normal app processes can be killed from the UI. System and root processes are read-only. */
    val killable: Boolean get() = appUser.matches(user)
}

class Ticks(val total: Long, val ticks: Map<Int, Long>, val names: Map<Int, String>)
data class Vol(val label: String, val path: String, val total: Long, val free: Long)
data class DisplayInfo(
    val phys: Int, val cur: Int, val size: String,
    val anim: Float, val touches: Boolean, val awake: Boolean
)

/** Kernel and shell access, kept out of the UI code. Values written here are validated first. */
object Sys {
    private const val POL = "/sys/devices/system/cpu/cpufreq"
    private val govRe = Regex("^[A-Za-z0-9_\\-]+$")

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

    fun setGov(p: Policy, g: String): Boolean =
        g in p.govs && govRe.matches(g) && Root.ok("echo $g > $POL/policy${p.id}/scaling_governor")

    /** Widens the range first so the kernel never sees min above max. */
    fun setRange(p: Policy, mn: Int, mx: Int): Boolean {
        if (mn <= 0 || mx <= 0 || mn > mx || p.freqs.isEmpty()) return false
        val top = p.freqs.last()
        return Root.ok(
            "echo $top > $POL/policy${p.id}/scaling_max_freq",
            "echo $mn > $POL/policy${p.id}/scaling_min_freq",
            "echo $mx > $POL/policy${p.id}/scaling_max_freq"
        )
    }

    fun setMax(p: Policy, f: Int): Boolean = f in p.freqs && setRange(p, minOf(p.minF.coerceAtLeast(1), f), f)
    fun setMin(p: Policy, f: Int): Boolean = f in p.freqs && setRange(p, f, maxOf(p.maxF, f))

    fun saveOriginal(ctx: Context, pols: List<Policy>) {
        if (pols.isEmpty() || LockStore.str(ctx, "cpu_orig", "").isNotEmpty()) return
        LockStore.setStr(ctx, "cpu_orig", pols.joinToString(";") { "${it.id}:${it.gov}:${it.minF}:${it.maxF}" })
    }

    fun restoreOriginal(ctx: Context): Boolean {
        val saved = LockStore.str(ctx, "cpu_orig", "")
        if (saved.isEmpty()) return false
        val pols = policies().associateBy { it.id }
        var ok = true
        for (e in saved.split(";")) {
            val p = e.split(":")
            if (p.size != 4) continue
            val pol = pols[p[0].toIntOrNull() ?: continue] ?: continue
            val mn = p[2].toIntOrNull() ?: continue
            val mx = p[3].toIntOrNull() ?: continue
            ok = setGov(pol, p[1]) && setRange(pol, mn, mx) && ok
        }
        return ok
    }

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

    fun setSwappiness(v: Int): Boolean = Root.ok("echo ${v.coerceIn(0, 100)} > /proc/sys/vm/swappiness")

    fun swaps(): List<String> = Root.run("cat /proc/swaps").drop(1).mapNotNull {
        it.trim().split(Regex("\\s+")).firstOrNull()?.takeIf { d -> d.startsWith("/dev/") && !d.contains(' ') }
    }

    fun swapOff(devs: List<String>): Boolean =
        devs.isNotEmpty() && Root.ok(*devs.filter { it.startsWith("/dev/") }.map { "swapoff ${Root.q(it)}" }.toTypedArray())

    fun swapOn(devs: List<String>): Boolean =
        devs.isNotEmpty() && Root.ok(*devs.filter { it.startsWith("/dev/") }.map { "swapon ${Root.q(it)}" }.toTypedArray())

    fun availMb(ctx: Context): Long {
        val mi = ActivityManager.MemoryInfo()
        ctx.getSystemService(ActivityManager::class.java).getMemoryInfo(mi)
        return mi.availMem / 1048576
    }

    /** Force-stops third-party apps. Launchers, keyboards, dialer and root managers are never touched. */
    fun killBackground(ctx: Context, except: String? = null): Int {
        val keep = setOfNotNull(except, Fg.pkg, ctx.packageName)
        val pkgs = Root.run("pm list packages -3")
            .map { it.trim().removePrefix("package:") }
            .filter { Safety.validPkg(it) && it !in keep && !Safety.isProtected(ctx, it) }
        if (pkgs.isNotEmpty()) Root.ok(*pkgs.map { "am force-stop $it" }.toTypedArray())
        return pkgs.size
    }

    fun dropCaches(): Boolean = Root.ok("sync", "echo 3 > /proc/sys/vm/drop_caches")

    fun boost(ctx: Context, except: String? = null): Int {
        val n = killBackground(ctx, except)
        Root.ok("pm trim-caches 999999999999")
        dropCaches()
        return n
    }

    // ---- Processes
    fun procs(): List<Proc> = Root.run("ps -A -o PID,USER,RSS,NAME").drop(1).mapNotNull { l ->
        val p = l.trim().split(Regex("\\s+"), 4)
        if (p.size < 4) return@mapNotNull null
        val pid = p[0].toIntOrNull() ?: return@mapNotNull null
        val mb = ((p[2].toLongOrNull() ?: 0L) / 1024).toInt()
        if (mb <= 0) null else Proc(pid, p[1], mb, p[3].substringAfterLast('/'))
    }.sortedByDescending { it.rssMb }.take(40)

    fun kill(p: Proc): Boolean = p.killable && p.pid > 1000 && Root.ok("kill -9 ${p.pid}")

    /** CPU ticks per process, from /proc. Two samples give a per-process CPU share. */
    fun procTicks(): Ticks {
        val lines = Root.run("head -n 1 /proc/stat", "cat /proc/[0-9]*/stat", "ps -A -o PID,NAME")
        var total = 0L
        val ticks = HashMap<Int, Long>()
        val names = HashMap<Int, String>()
        val statRe = Regex("^(\\d+) \\(")
        for (l in lines) {
            if (l.startsWith("cpu ")) {
                total = l.trim().split(Regex("\\s+")).drop(1).mapNotNull { it.toLongOrNull() }.take(8).sum()
                continue
            }
            val m = statRe.find(l)
            if (m != null) {
                val pid = m.groupValues[1].toIntOrNull() ?: continue
                val close = l.lastIndexOf(')')
                if (close < 0 || close + 2 > l.length) continue
                val rest = l.substring(close + 2).split(' ')
                val u = rest.getOrNull(11)?.toLongOrNull() ?: continue
                val s = rest.getOrNull(12)?.toLongOrNull() ?: continue
                ticks[pid] = u + s
                continue
            }
            val t = l.trim()
            val sp = t.indexOf(' ')
            if (sp > 0) t.substring(0, sp).toIntOrNull()?.let { names[it] = t.substring(sp + 1).trim() }
        }
        return Ticks(total, ticks, names)
    }

    /** Estimated battery use per app since the last full charge, from the system's own stats. */
    fun appPower(ctx: Context): List<Triple<String, String, Float>> {
        val pm = ctx.packageManager
        val re = Regex("^\\s*Uid (\\S+): ([0-9.]+)")
        val ure = Regex("^u(\\d+)a(\\d+)$")
        val byPkg = LinkedHashMap<String, Float>()
        var inSection = false
        for (l in Root.run("dumpsys batterystats --charged")) {
            if (l.contains("Estimated power use (mAh)")) { inSection = true; continue }
            if (!inSection) continue
            if (l.isBlank()) { if (byPkg.isNotEmpty()) break else continue }
            val m = re.find(l) ?: continue
            val name = m.groupValues[1]
            val mah = m.groupValues[2].toFloatOrNull() ?: continue
            val uid = ure.find(name)?.let { it.groupValues[1].toInt() * 100000 + 10000 + it.groupValues[2].toInt() }
                ?: name.toIntOrNull() ?: continue
            val pkg = pm.getPackagesForUid(uid)?.firstOrNull() ?: continue
            byPkg[pkg] = (byPkg[pkg] ?: 0f) + mah
        }
        return byPkg.entries.sortedByDescending { it.value }.take(20).map { (pkg, mah) ->
            val label = try { pm.getApplicationLabel(pm.getApplicationInfo(pkg, 0)).toString() } catch (e: Exception) { pkg }
            Triple(pkg, label, mah)
        }
    }

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

    fun setDensity(dpi: Int): Boolean = dpi in 120..800 && Root.ok("wm density $dpi")
    fun resetDensity(): Boolean = Root.ok("wm density reset")

    fun setAnim(v: Float): Boolean {
        val x = v.coerceIn(0f, 5f)
        return Root.ok(
            "settings put global window_animation_scale $x",
            "settings put global transition_animation_scale $x",
            "settings put global animator_duration_scale $x"
        )
    }

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

// ===================================================================== Charging

/**
 * Charging control through well-known sysfs nodes. Only the fixed paths below are ever written,
 * and charging is always switched back on when unplugged, on service stop and after a crash.
 */
object Charge {
    class Caps(val toggle: Triple<String, String, String>?, val current: String?, val limit: String?)

    private const val B = "/sys/class/power_supply/battery"
    private val toggles = listOf(
        Triple("$B/charging_enabled", "1", "0"),
        Triple("$B/battery_charging_enabled", "1", "0"),
        Triple("$B/mmi_charging_enable", "1", "0"),
        Triple("$B/input_suspend", "0", "1"),
        Triple("/sys/class/qcom-battery/input_suspend", "0", "1"),
        Triple("$B/batt_slate_mode", "0", "1"),
        Triple("/sys/devices/platform/google,charger/charge_disable", "0", "1"),
        Triple("/sys/devices/platform/soc/soc:oplus_chg_core/oplus_chg/battery/mmi_charging_enable", "1", "0")
    )
    private val currents = listOf("$B/constant_charge_current_max", "$B/constant_charge_current")
    private val limits = listOf("$B/charge_control_end_threshold", "/sys/devices/platform/google,charger/charge_stop_level")

    @Volatile var caps: Caps? = null
    @Volatile var off = false
    @Volatile private var lastHw = -1

    fun detect(): Caps {
        val all = (toggles.map { it.first } + currents + limits).distinct()
        val found = Root.run("for f in ${all.joinToString(" ")}; do [ -e \"\$f\" ] && echo \"\$f\"; done")
            .map { it.trim() }.toSet()
        val c = Caps(toggles.firstOrNull { it.first in found }, currents.firstOrNull { it in found }, limits.firstOrNull { it in found })
        caps = c
        return c
    }

    fun setCharging(on: Boolean): Boolean {
        val t = (caps ?: detect()).toggle ?: return false
        return Root.ok("echo ${if (on) t.second else t.third} > ${t.first}")
    }

    fun isEnabled(): Boolean? {
        val t = (caps ?: detect()).toggle ?: return null
        val v = Root.run("cat ${t.first}").firstOrNull()?.trim() ?: return null
        return v == t.second
    }

    fun readCurrent(): Int? {
        val n = (caps ?: detect()).current ?: return null
        return Root.run("cat $n").firstOrNull()?.trim()?.toIntOrNull()
    }

    fun setCurrentUa(ctx: Context, ua: Int): Boolean {
        val n = (caps ?: detect()).current ?: return false
        if (LockStore.int(ctx, "cur_orig", -1) < 0) readCurrent()?.let { LockStore.setInt(ctx, "cur_orig", it) }
        return Root.ok("echo ${ua.coerceIn(300_000, 6_000_000)} > $n")
    }

    fun restoreCurrent(ctx: Context): Boolean {
        val n = (caps ?: detect()).current ?: return false
        val o = LockStore.int(ctx, "cur_orig", -1)
        return o > 0 && Root.ok("echo $o > $n")
    }

    /** Called when the service starts or stops. Never leave the phone unable to charge. */
    fun recover(ctx: Context) {
        if (LockStore.bool(ctx, "cl_off_state", false)) {
            if (setCharging(true)) LockStore.setBool(ctx, "cl_off_state", false)
            off = false
        }
    }

    fun enforce(ctx: Context, s: Snap) {
        val c = caps ?: detect()
        if (c.toggle == null && c.limit == null) return
        val plugged = s.batt.plugged != "Battery"
        val limitOn = LockStore.bool(ctx, "cl_on", false)
        val lim = LockStore.int(ctx, "cl_limit", 80).coerceIn(30, 100)
        val manual = LockStore.bool(ctx, "cl_manual", false)
        val tempOn = LockStore.bool(ctx, "cl_temp_on", false)
        val tmax = LockStore.int(ctx, "cl_temp", 42).coerceIn(30, 55)
        if (c.limit != null) {
            val want = if (limitOn) lim else 100
            if (lastHw != want && Root.ok("echo $want > ${c.limit}")) lastHw = want
        }
        if (c.toggle == null) return
        val soft = limitOn && c.limit == null
        val hit = soft && s.batt.level >= lim
        val resumeOk = !soft || s.batt.level <= lim - 3
        val hot = tempOn && s.batt.tempC >= tmax
        val coolOk = !tempOn || s.batt.tempC <= tmax - 3
        val shouldOff = plugged && (manual || (if (off) !(resumeOk && coolOk) else (hit || hot)))
        if (shouldOff && !off) {
            if (setCharging(false)) { off = true; LockStore.setBool(ctx, "cl_off_state", true) }
        } else if (!shouldOff && off) {
            if (setCharging(true)) { off = false; LockStore.setBool(ctx, "cl_off_state", false) }
        }
    }
}

// ===================================================================== Firewall

/** Per-app network blocking in its own iptables chain. Flushing that chain removes every rule. */
object Firewall {
    private const val CHAIN = "ashu_fw"

    private fun uidOf(ctx: Context, pkg: String): Int? =
        try { ctx.packageManager.getApplicationInfo(pkg, 0).uid } catch (e: Exception) { null }

    fun applyAll(ctx: Context): Boolean {
        val modes = LockStore.netModes(ctx)
        val cmds = ArrayList<String>()
        for (ipt in listOf("iptables", "ip6tables")) {
            cmds += "$ipt -N $CHAIN 2>/dev/null"
            cmds += "$ipt -F $CHAIN"
            cmds += "$ipt -C OUTPUT -j $CHAIN 2>/dev/null || $ipt -I OUTPUT 1 -j $CHAIN"
            for ((pkg, mode) in modes) {
                if (!Safety.validPkg(pkg) || Safety.isProtected(ctx, pkg)) continue
                val uid = uidOf(ctx, pkg) ?: continue
                if (uid < 10000) continue
                val own = "-m owner --uid-owner $uid"
                when (mode) {
                    1 -> cmds += "$ipt -A $CHAIN $own ! -o wlan+ ! -o lo -j REJECT"
                    2 -> cmds += "$ipt -A $CHAIN $own -o wlan+ -j REJECT"
                    3 -> cmds += "$ipt -A $CHAIN $own ! -o lo -j REJECT"
                }
            }
        }
        return Root.ok(*cmds.toTypedArray())
    }

    fun clearAll(): Boolean = Root.ok("iptables -F $CHAIN", "ip6tables -F $CHAIN")
}

// ======================================================================== Games

object GameUi {
    var perf by mutableStateOf(false)
    var dnd by mutableStateOf(false)
    var rec by mutableStateOf(false)
}

/**
 * Game mode. Everything it changes is saved first and restored when the game closes,
 * and again on the next service start if the app was killed in between.
 */
object GameMode {
    @Volatile var active: String? = null
    @Volatile var cached: Set<String> = emptySet()

    @Suppress("DEPRECATION")
    fun games(ctx: Context): Set<String> {
        val pm = ctx.packageManager
        val auto = try {
            pm.getInstalledApplications(0)
                .filter { it.category == ApplicationInfo.CATEGORY_GAME && (it.flags and ApplicationInfo.FLAG_SYSTEM) == 0 }
                .map { it.packageName }.toSet()
        } catch (e: Exception) { emptySet() }
        val set = (auto + LockStore.set(ctx, "g_added") - LockStore.set(ctx, "g_removed"))
            .filter { it != ctx.packageName && Safety.validPkg(it) }.toSet()
        cached = set
        return set
    }

    fun setGame(ctx: Context, pkg: String, on: Boolean) {
        val added = LockStore.set(ctx, "g_added").toMutableSet()
        val removed = LockStore.set(ctx, "g_removed").toMutableSet()
        if (on) { added += pkg; removed -= pkg } else { removed += pkg; added -= pkg }
        LockStore.setSet(ctx, "g_added", added)
        LockStore.setSet(ctx, "g_removed", removed)
        games(ctx)
    }

    fun setPerf(ctx: Context, on: Boolean): Boolean {
        val pols = Sys.policies()
        if (pols.isEmpty()) return false
        if (on) {
            if (LockStore.str(ctx, "g_prev_gov", "").isEmpty()) {
                LockStore.setStr(ctx, "g_prev_gov", pols.joinToString(",") { "${it.id}:${it.gov}" })
            }
            val want = LockStore.str(ctx, "g_gov", "performance")
            pols.forEach { p ->
                val g = if (want in p.govs) want else if ("performance" in p.govs) "performance" else p.gov
                Sys.setGov(p, g)
            }
            GameUi.perf = true
        } else {
            val byId = pols.associateBy { it.id }
            LockStore.str(ctx, "g_prev_gov", "").split(",").forEach { e ->
                val x = e.split(":")
                if (x.size == 2) byId[x[0].toIntOrNull()]?.let { Sys.setGov(it, x[1]) }
            }
            LockStore.setStr(ctx, "g_prev_gov", "")
            GameUi.perf = false
        }
        return true
    }

    fun setDnd(ctx: Context, on: Boolean): Boolean {
        if (on) {
            if (LockStore.str(ctx, "g_prev_zen", "").isEmpty()) {
                LockStore.setStr(ctx, "g_prev_zen", Root.run("settings get global zen_mode").firstOrNull()?.trim() ?: "0")
            }
            GameUi.dnd = Root.ok("cmd notification set_dnd priority")
        } else {
            val z = LockStore.str(ctx, "g_prev_zen", "0")
            if (z == "0" || z == "null") Root.ok("cmd notification set_dnd off")
            LockStore.setStr(ctx, "g_prev_zen", "")
            GameUi.dnd = false
        }
        return true
    }

    fun start(ctx: Context, pkg: String) {
        active = pkg
        if (LockStore.bool(ctx, "g_kill", false)) Sys.killBackground(ctx, pkg)
        if (LockStore.bool(ctx, "g_perf", true)) setPerf(ctx, true)
        if (LockStore.bool(ctx, "g_dnd", true)) setDnd(ctx, true)
        if (LockStore.bool(ctx, "g_headsup", true)) Root.ok("settings put global heads_up_notifications_enabled 0")
    }

    fun stop(ctx: Context) {
        if (LockStore.str(ctx, "g_prev_gov", "").isNotEmpty()) setPerf(ctx, false)
        if (LockStore.str(ctx, "g_prev_zen", "").isNotEmpty()) setDnd(ctx, false)
        if (LockStore.bool(ctx, "g_headsup", true)) Root.ok("settings put global heads_up_notifications_enabled 1")
        active = null
    }

    /** If the app was killed while a game was running, put the phone back to how it was. */
    fun recover(ctx: Context) {
        if (active == null && (LockStore.str(ctx, "g_prev_gov", "").isNotEmpty() || LockStore.str(ctx, "g_prev_zen", "").isNotEmpty())) {
            stop(ctx)
        }
    }
}

// ================================================================= Notification

/** Title and body for the live notification. Used by the service and by the preview card. */
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
    if (LockStore.bool(ctx, "n_drain", true)) {
        val r = s.ratePh
        val now = when {
            r == null -> "measuring"
            r < 0 -> "-${"%.1f".format(-r)}%/h"
            else -> "+${"%.1f".format(r)}%/h"
        }
        rows += "Rate $now   24h used ${"%.0f".format(s.drain24)}% (${"%.1f".format(s.avg24)}%/h)"
    }
    if (LockStore.bool(ctx, "n_net", true)) rows += "Down ${fmtSpeed(s.netDown)}   Up ${fmtSpeed(s.netUp)}"
    if (LockStore.bool(ctx, "n_sleep", false)) rows += "Deep sleep ${s.deepSleep}%   Uptime ${Sys.fmtUptime(s.uptimeSec)}"
    if (LockStore.bool(ctx, "n_storage", false)) rows += "Free storage ${"%.1f".format(s.freeGb)} GB"
    if (LockStore.bool(ctx, "n_freq", true)) {
        val mhz = (s.cores.maxOfOrNull { it.khz } ?: 0) / 1000
        rows += "Core max $mhz MHz   ${s.gov}"
    }
    val on = LockStore.bool(ctx, "n_on", true) && !Guard.safe
    val title = if (Guard.safe) "Ashutool safe mode" else if (on && top.isNotEmpty()) top.joinToString("   ") else "Ashutool is running"
    val body = if (Guard.safe) "Auto features are paused. Open Settings to resume."
    else if (on && rows.isNotEmpty()) rows.joinToString("\n") else "Tap to open"
    return title to body
}
