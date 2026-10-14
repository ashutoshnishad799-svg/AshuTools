/*
 * Ashutool - root toolkit for custom ROMs
 * Copyright (C) 2026 Ashutosh Nishad
 * Telegram: @ashutosh_07x (https://t.me/ashutosh_07x)
 *
 * Free software under the GNU GPL v3.0 or later, with the attribution terms in NOTICE.
 * Keep this header and the author credit in every copy and modified version.
 */

package com.ashu.ashutool

import android.app.AppOpsManager
import android.app.usage.UsageEvents
import android.app.usage.UsageStatsManager
import android.content.Context
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import android.os.Build
import android.os.Process
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import java.security.MessageDigest
import java.security.SecureRandom
import java.util.Calendar
import java.util.concurrent.ConcurrentHashMap

data class AppItem(
    val pkg: String, val name: String, val system: Boolean,
    val hidden: Boolean, val game: Boolean, val guarded: Boolean,
    /** A system app that Play or the user has updated. It is used like any other app. */
    val updated: Boolean = false
) {
    /** Built in parts of the phone: overlays, themes, navigation bars, services. Not apps you would limit. */
    val plainSystem: Boolean get() = system && !updated
}

data class UsageItem(val pkg: String, val name: String, val ms: Long)

/** Foreground package. Written by the accessibility service, or by usage polling as a fallback. */
object Fg {
    @Volatile var pkg: String? = null
    @Volatile var prev: String? = null

    /**
     * The game you are in right now, or null. It stays set while a call, a store popup or a Google sign-in
     * window is on top of the game, and clears when you go to any other app or the home screen.
     * Game bar and call controls follow this, not the plain foreground app.
     */
    @Volatile var game: String? = null
    @Volatile var listener: ((String) -> Unit)? = null

    private val seen = java.util.concurrent.CopyOnWriteArrayList<String>()

    /** The last few different apps that came to the front, newest first. The Games page uses it to mark a game by hand. */
    fun recent(): List<String> = seen.toList()

    /** Records a new foreground app and tells the listener, only when it actually changed. */
    fun set(p: String) {
        if (p == pkg) return
        prev = pkg
        pkg = p
        seen.remove(p)
        seen.add(0, p)
        while (seen.size > 12) seen.removeAt(seen.size - 1)
        listener?.invoke(p)
    }
}

/** What the game bar last ran into. Shown on the Games page so a missing bar can be explained. */
object GameDiag {
    @Volatile var error: String = ""
}

/** Set when the service restarted several times in a row. Risky auto features pause until the user resumes. */
object Guard {
    var safe by mutableStateOf(false)
}

object Apps {
    @Suppress("DEPRECATION")
    fun load(ctx: Context): List<AppItem> {
        val pm = ctx.packageManager
        val modes = LockStore.hideModes(ctx)
        val removed = LockStore.set(ctx, "removed")
        val flags = PackageManager.MATCH_DISABLED_COMPONENTS or PackageManager.MATCH_UNINSTALLED_PACKAGES
        return pm.getInstalledApplications(flags)
            .filter {
                it.packageName != ctx.packageName && it.packageName !in removed &&
                    ((it.flags and ApplicationInfo.FLAG_INSTALLED) != 0 || it.packageName in modes)
            }
            .map {
                AppItem(
                    it.packageName,
                    pm.getApplicationLabel(it).toString(),
                    (it.flags and ApplicationInfo.FLAG_SYSTEM) != 0,
                    !it.enabled || it.packageName in modes,
                    it.category == ApplicationInfo.CATEGORY_GAME,
                    Safety.isProtected(ctx, it.packageName, it.uid),
                    (it.flags and ApplicationInfo.FLAG_UPDATED_SYSTEM_APP) != 0
                )
            }
            .sortedBy { it.name.lowercase() }
    }

    /** Apps this tool removed, so they can be brought back. */
    @Suppress("DEPRECATION")
    fun removedApps(ctx: Context): List<Pair<String, String>> {
        val pm = ctx.packageManager
        return LockStore.set(ctx, "removed").map { pkg ->
            val label = try {
                pm.getApplicationLabel(pm.getApplicationInfo(pkg, PackageManager.MATCH_UNINSTALLED_PACKAGES)).toString()
            } catch (e: Exception) { pkg }
            pkg to label
        }.sortedBy { it.second.lowercase() }
    }

    fun forceStop(ctx: Context, pkg: String) = !Safety.isProtected(ctx, pkg) && Root.ok("am force-stop $pkg")

    fun clearCache(ctx: Context, pkg: String) =
        Safety.validPkg(pkg) && pkg != ctx.packageName &&
            Root.ok("rm -rf /data/user/0/$pkg/cache/* /data/user/0/$pkg/code_cache/* /sdcard/Android/data/$pkg/cache/*")

    fun clearData(ctx: Context, pkg: String) = !Safety.isProtected(ctx, pkg) && Root.ok("pm clear $pkg")

    /** Removes the app for the current user only. The APK stays on the system partition and can be restored. */
    fun uninstall(ctx: Context, pkg: String): Boolean {
        if (Safety.isProtected(ctx, pkg)) return false
        val r = Root.ok("pm uninstall --user 0 $pkg")
        if (r) LockStore.setSet(ctx, "removed", LockStore.set(ctx, "removed") + pkg)
        return r
    }

    fun restore(ctx: Context, pkg: String): Boolean {
        if (!Safety.validPkg(pkg)) return false
        val r = Root.ok("cmd package install-existing --user 0 $pkg")
        if (r) LockStore.setSet(ctx, "removed", LockStore.set(ctx, "removed") - pkg)
        return r
    }

    /** mode 0 freezes the app, mode 1 hides it completely. */
    fun hide(ctx: Context, pkg: String, mode: Int): Boolean {
        if (Safety.isProtected(ctx, pkg)) return false
        if (LockStore.bool(ctx, "hide_stop", true)) Root.ok("am force-stop $pkg")
        val ok = if (mode == 1) Root.ok("pm hide --user 0 $pkg") else Root.ok("pm disable-user --user 0 $pkg")
        if (ok) LockStore.setHideMode(ctx, pkg, mode)
        return ok
    }

    fun unhide(ctx: Context, pkg: String): Boolean {
        if (!Safety.validPkg(pkg)) return false
        Root.ok("pm unhide --user 0 $pkg")
        val ok = Root.ok("pm enable $pkg")
        LockStore.setHideMode(ctx, pkg, -1)
        LockStore.session.remove(pkg)
        return ok
    }

    /** Bring a hidden app back for one session, used by the vault. */
    fun tempOpen(ctx: Context, pkg: String): Boolean {
        val mode = LockStore.hideModes(ctx)[pkg] ?: return false
        if (!Safety.validPkg(pkg)) return false
        if (mode == 1) Root.ok("pm unhide --user 0 $pkg")
        val ok = Root.ok("pm enable $pkg")
        LockStore.session[pkg] = System.currentTimeMillis()
        return ok
    }

    fun rehide(ctx: Context, pkg: String): Boolean {
        val mode = LockStore.hideModes(ctx)[pkg] ?: return false
        if (!Safety.validPkg(pkg) || Safety.isProtected(ctx, pkg)) return false
        return if (mode == 1) Root.ok("pm hide --user 0 $pkg") else Root.ok("pm disable-user --user 0 $pkg")
    }
}

object Usage {
    @Suppress("DEPRECATION")
    fun hasAccess(ctx: Context): Boolean {
        val ops = ctx.getSystemService(AppOpsManager::class.java)
        val mode = if (Build.VERSION.SDK_INT >= 29)
            ops.unsafeCheckOpNoThrow(AppOpsManager.OPSTR_GET_USAGE_STATS, Process.myUid(), ctx.packageName)
        else
            ops.checkOpNoThrow(AppOpsManager.OPSTR_GET_USAGE_STATS, Process.myUid(), ctx.packageName)
        return mode == AppOpsManager.MODE_ALLOWED
    }

    private const val EV_RESUMED = 1
    private const val EV_PAUSED = 2
    private const val EV_SCREEN_OFF = 16
    private const val EV_SHUTDOWN = 26
    private const val EV_STOPPED = 23

    /** Midnight, local time, [back] days ago. 0 is the start of today. */
    private fun dayStart(back: Int): Long = Calendar.getInstance().apply {
        set(Calendar.HOUR_OF_DAY, 0); set(Calendar.MINUTE, 0)
        set(Calendar.SECOND, 0); set(Calendar.MILLISECOND, 0)
        add(Calendar.DAY_OF_YEAR, -back)
    }.timeInMillis

    /**
     * Time each app was on screen between [from] and [to], worked out from the raw events.
     * The ready made totals Android keeps can include time from outside the range, which is how a day ended up
     * with more hours than it has. Here only the part inside the range counts, and time stops counting when the
     * screen turns off, even if the app never reported that it was paused.
     */
    private fun fromEvents(ctx: Context, from: Long, to: Long): Map<String, Long> {
        val usm = ctx.getSystemService(UsageStatsManager::class.java)
        val total = HashMap<String, Long>()
        val open = HashMap<String, Long>()
        fun add(pkg: String, a: Long, b: Long) {
            val lo = maxOf(a, from)
            val hi = minOf(b, to)
            if (hi > lo) total[pkg] = (total[pkg] ?: 0L) + (hi - lo)
        }
        // Start a few hours early so an app that was already open at the start of the range is known
        val ev = usm.queryEvents(from - 6 * 3_600_000L, to) ?: return total
        val e = UsageEvents.Event()
        while (ev.hasNextEvent()) {
            ev.getNextEvent(e)
            val pkg = e.packageName ?: continue
            val t = e.timeStamp
            when (e.eventType) {
                EV_RESUMED -> if (!open.containsKey(pkg)) open[pkg] = t
                EV_PAUSED, EV_STOPPED -> open.remove(pkg)?.let { add(pkg, it, t) }
                EV_SCREEN_OFF, EV_SHUTDOWN -> {
                    for ((p, st) in open) add(p, st, t)
                    open.clear()
                }
            }
        }
        for ((p, st) in open) add(p, st, to)
        return total
    }

    /** Android's own daily totals, one map per day back. Only used for old days whose events are gone. */
    private fun dailyBuckets(ctx: Context, days: Int): Map<Int, Map<String, Long>> {
        val usm = ctx.getSystemService(UsageStatsManager::class.java)
        val out = HashMap<Int, HashMap<String, Long>>()
        val starts = (0 until days).map { dayStart(it) }
        val stats = try { usm.queryUsageStats(UsageStatsManager.INTERVAL_DAILY, starts.last(), System.currentTimeMillis()) } catch (e: Exception) { null }
        for (u in stats ?: emptyList()) {
            val back = starts.indexOfFirst { it <= u.firstTimeStamp + 60_000L }
            if (back <= 0) continue
            val ms = u.totalTimeInForeground.coerceIn(0L, 86_400_000L)
            val m = out.getOrPut(back) { HashMap() }
            m[u.packageName] = (m[u.packageName] ?: 0L) + ms
        }
        return out
    }

    /** Foreground time per app for the last [days] days. 1 means only today, from midnight until now. */
    @Suppress("DEPRECATION")
    fun query(ctx: Context, days: Int): List<UsageItem> {
        val now = System.currentTimeMillis()
        val sum = HashMap<String, Long>()
        var buckets: Map<Int, Map<String, Long>>? = null
        for (back in 0 until days) {
            val from = dayStart(back)
            val to = if (back == 0) now else dayStart(back - 1)
            var day = fromEvents(ctx, from, to)
            if (day.isEmpty() && back > 0) {
                if (buckets == null) buckets = dailyBuckets(ctx, days)
                day = buckets[back] ?: emptyMap()
            }
            for ((k, v) in day) sum[k] = (sum[k] ?: 0L) + v
        }
        val pm = ctx.packageManager
        return sum.entries
            .filter { it.value > 60_000 }
            .map {
                val name = try {
                    pm.getApplicationLabel(pm.getApplicationInfo(it.key, 0)).toString()
                } catch (e: Exception) {
                    it.key
                }
                UsageItem(it.key, name, it.value)
            }
            .sortedByDescending { it.ms }
    }

    @Volatile private var todayAt = 0L
    @Volatile private var todayMap: Map<String, Long> = emptyMap()

    /** Foreground time today for every app, in milliseconds. Cached for a few seconds because limits ask often. */
    fun todayAll(ctx: Context): Map<String, Long> {
        val now = System.currentTimeMillis()
        if (now - todayAt < 4000L && now >= todayAt) return todayMap
        val m = fromEvents(ctx, dayStart(0), now)
        todayMap = m
        todayAt = now
        return m
    }

    fun todayMs(ctx: Context, pkg: String): Long = todayAll(ctx)[pkg] ?: 0L

    /**
     * Foreground app read with root. Used only when neither the accessibility service nor usage access is available.
     * Reads the resumed activity from the activity manager, which works on every Android version from 8 up.
     */
    fun topByRoot(): String? {
        val re = Regex("""\bu\d+\s+([A-Za-z0-9_.]+)/""")
        for (line in Root.run("dumpsys activity activities | grep -E 'topResumedActivity|mResumedActivity' | head -n 3")) {
            val m = re.find(line) ?: continue
            return m.groupValues[1]
        }
        return null
    }

    /** Last app that came to the foreground in the past 15 seconds, or null if nothing changed. */
    @Suppress("DEPRECATION")
    fun foreground(ctx: Context): String? {
        val usm = ctx.getSystemService(UsageStatsManager::class.java)
        val now = System.currentTimeMillis()
        val ev = usm.queryEvents(now - 15_000, now)
        val e = UsageEvents.Event()
        var pkg: String? = null
        while (ev.hasNextEvent()) {
            ev.getNextEvent(e)
            if (e.eventType == UsageEvents.Event.MOVE_TO_FOREGROUND) pkg = e.packageName
        }
        return pkg
    }
}

object LockStore {
    /** Package the user has just unlocked. Cleared when they leave that app. */
    @Volatile var unlocked: String? = null

    /** Hidden apps opened from the vault, with the time they were opened. */
    val session = ConcurrentHashMap<String, Long>()

    private fun sp(c: Context) = c.getSharedPreferences("ashutool", Context.MODE_PRIVATE)

    fun bool(c: Context, k: String, d: Boolean) = sp(c).getBoolean(k, d)
    fun setBool(c: Context, k: String, v: Boolean) = sp(c).edit().putBoolean(k, v).apply()
    fun int(c: Context, k: String, d: Int) = sp(c).getInt(k, d)
    fun setInt(c: Context, k: String, v: Int) = sp(c).edit().putInt(k, v).apply()
    fun str(c: Context, k: String, d: String) = sp(c).getString(k, d) ?: d
    fun setStr(c: Context, k: String, v: String) = sp(c).edit().putString(k, v).apply()
    fun set(c: Context, k: String): Set<String> = sp(c).getStringSet(k, emptySet())?.toSet() ?: emptySet()
    fun setSet(c: Context, k: String, v: Set<String>) = sp(c).edit().putStringSet(k, v).apply()

    fun locked(c: Context): Set<String> = set(c, "locked")

    fun setLocked(c: Context, pkg: String, on: Boolean) {
        val s = locked(c).toMutableSet()
        if (on) s.add(pkg) else s.remove(pkg)
        setSet(c, "locked", s)
    }

    // ---- credentials: salted hash, PIN or pattern
    private fun salt(c: Context): String {
        var s = sp(c).getString("salt", null)
        if (s == null) {
            val b = ByteArray(16)
            SecureRandom().nextBytes(b)
            s = b.joinToString("") { "%02x".format(it) }
            sp(c).edit().putString("salt", s).apply()
        }
        return s
    }

    private fun hash(c: Context, v: String): String =
        MessageDigest.getInstance("SHA-256").digest((salt(c) + v).toByteArray()).joinToString("") { "%02x".format(it) }

    /** 0 PIN, 1 pattern */
    fun type(c: Context) = int(c, "lock_type", 0)
    fun hasCred(c: Context) = sp(c).contains("pin") || sp(c).contains("pat")
    fun pinLen(c: Context) = int(c, "pin_len", 4)

    fun setPin(c: Context, pin: String) {
        sp(c).edit().putString("pin", hash(c, pin)).putInt("pin_len", pin.length).putInt("lock_type", 0).apply()
    }

    fun setPattern(c: Context, p: List<Int>) {
        sp(c).edit().putString("pat", hash(c, "p" + p.joinToString(""))).putInt("lock_type", 1).apply()
    }

    fun hasRecovery(c: Context) = sp(c).contains("rec")

    /** Eight digit recovery code. Only its hash is stored, so it can be shown once. */
    fun makeRecovery(c: Context): String {
        val r = SecureRandom()
        val code = (0 until 8).joinToString("") { r.nextInt(10).toString() }
        sp(c).edit().putString("rec", hash(c, "r$code")).apply()
        return code
    }

    fun checkRecovery(c: Context, code: String) = sp(c).getString("rec", null) == hash(c, "r$code")

    /** Removes the PIN or pattern. Locked apps stay in the list until a new lock is set. */
    fun clearCred(c: Context) {
        sp(c).edit().remove("pin").remove("pat").remove("rec").apply()
    }

    fun checkPin(c: Context, pin: String) = sp(c).getString("pin", null) == hash(c, pin)
    fun checkPattern(c: Context, p: List<Int>) = sp(c).getString("pat", null) == hash(c, "p" + p.joinToString(""))

    // ---- "pkg|number" maps stored as string sets
    private fun pairs(c: Context, k: String): Map<String, Int> = set(c, k).mapNotNull {
        val i = it.lastIndexOf('|')
        if (i <= 0) null else (it.substring(0, i) to (it.substring(i + 1).toIntOrNull() ?: return@mapNotNull null))
    }.toMap()

    private fun setPair(c: Context, k: String, pkg: String, v: Int) {
        val s = set(c, k).filterNot { it.substringBeforeLast('|') == pkg }.toMutableSet()
        if (v >= 0) s.add("$pkg|$v")
        setSet(c, k, s)
    }

    fun pairsOf(c: Context, k: String) = pairs(c, k)
    fun putPair(c: Context, k: String, pkg: String, v: Int) = setPair(c, k, pkg, v)

    /** 0 freeze, 1 full hide */
    fun hideModes(c: Context) = pairs(c, "hide_modes")
    fun setHideMode(c: Context, pkg: String, mode: Int) = setPair(c, "hide_modes", pkg, mode)

    /** 1 no mobile data, 2 no Wi-Fi, 3 no network. Missing means allowed. */
    fun netModes(c: Context) = pairs(c, "net_modes")
    fun setNetMode(c: Context, pkg: String, mode: Int) = setPair(c, "net_modes", pkg, if (mode == 0) -1 else mode)
}

/** Daily time limits per app, with a bonus that lasts until midnight. */
object Limits {
    fun all(c: Context): Map<String, Int> = LockStore.pairsOf(c, "limits")

    fun set(c: Context, pkg: String, minutes: Int) =
        LockStore.putPair(c, "limits", pkg, if (minutes <= 0) -1 else minutes.coerceAtMost(24 * 60))

    private fun day(): String = Calendar.getInstance().let { "${it.get(Calendar.YEAR)}-${it.get(Calendar.DAY_OF_YEAR)}" }

    fun bonus(c: Context, pkg: String): Int {
        if (LockStore.str(c, "lb_day", "") != day()) {
            LockStore.setStr(c, "lb_day", day())
            LockStore.setSet(c, "lb", emptySet())
        }
        return LockStore.pairsOf(c, "lb")[pkg] ?: 0
    }

    fun addBonus(c: Context, pkg: String, minutes: Int) = LockStore.putPair(c, "lb", pkg, bonus(c, pkg) + minutes)

    fun limitMs(c: Context, pkg: String): Long? = all(c)[pkg]?.let { (it + bonus(c, pkg)) * 60_000L }

    fun exceeded(c: Context, pkg: String, usedMs: Long): Boolean = limitMs(c, pkg)?.let { usedMs >= it } ?: false
}

/** Time spent in blocked short-video screens today, used by the reels limit. */
object ReelStats {
    private var acc = 0f
    private var lastSave = 0L

    private fun day(): String = Calendar.getInstance().let { "${it.get(Calendar.YEAR)}-${it.get(Calendar.DAY_OF_YEAR)}" }

    fun used(c: Context): Int {
        if (LockStore.str(c, "reel_day", "") != day()) {
            LockStore.setStr(c, "reel_day", day())
            LockStore.setInt(c, "reel_sec", 0)
            acc = 0f
        }
        return LockStore.int(c, "reel_sec", 0) + acc.toInt()
    }

    fun add(c: Context, dt: Float): Int {
        used(c)
        acc += dt
        val now = System.currentTimeMillis()
        if (now - lastSave > 10_000 || acc >= 10f) {
            LockStore.setInt(c, "reel_sec", LockStore.int(c, "reel_sec", 0) + acc.toInt())
            acc -= acc.toInt()
            lastSave = now
        }
        return used(c)
    }
}
