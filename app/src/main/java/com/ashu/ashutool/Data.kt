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
    val hidden: Boolean, val game: Boolean, val guarded: Boolean
)

data class UsageItem(val pkg: String, val name: String, val ms: Long)

/** Foreground package. Written by the accessibility service, or by usage polling as a fallback. */
object Fg {
    @Volatile var pkg: String? = null
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
                    Safety.isProtected(ctx, it.packageName, it.uid)
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

    fun query(ctx: Context, days: Int): List<UsageItem> {
        val usm = ctx.getSystemService(UsageStatsManager::class.java)
        val cal = Calendar.getInstance().apply {
            set(Calendar.HOUR_OF_DAY, 0); set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0); set(Calendar.MILLISECOND, 0)
            add(Calendar.DAY_OF_YEAR, -(days - 1))
        }
        val pm = ctx.packageManager
        return usm.queryAndAggregateUsageStats(cal.timeInMillis, System.currentTimeMillis()).values
            .filter { it.totalTimeInForeground > 60_000 }
            .map {
                val name = try {
                    pm.getApplicationLabel(pm.getApplicationInfo(it.packageName, 0)).toString()
                } catch (e: Exception) {
                    it.packageName
                }
                UsageItem(it.packageName, name, it.totalTimeInForeground)
            }
            .sortedByDescending { it.ms }
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

    /** 0 freeze, 1 full hide */
    fun hideModes(c: Context) = pairs(c, "hide_modes")
    fun setHideMode(c: Context, pkg: String, mode: Int) = setPair(c, "hide_modes", pkg, mode)

    /** 1 no mobile data, 2 no Wi-Fi, 3 no network. Missing means allowed. */
    fun netModes(c: Context) = pairs(c, "net_modes")
    fun setNetMode(c: Context, pkg: String, mode: Int) = setPair(c, "net_modes", pkg, if (mode == 0) -1 else mode)
}
