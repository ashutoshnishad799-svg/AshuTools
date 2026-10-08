package com.ashu.ashutool

import android.app.AppOpsManager
import android.app.usage.UsageEvents
import android.app.usage.UsageStatsManager
import android.content.Context
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import android.os.Build
import android.os.Process
import java.security.MessageDigest
import java.util.Calendar

data class AppItem(val pkg: String, val name: String, val system: Boolean, val hidden: Boolean)
data class UsageItem(val pkg: String, val name: String, val ms: Long)

object Apps {
    /** Packages that must never be hidden or force-stopped from this tool. */
    val protectedPkgs = setOf("android", "com.android.systemui", "com.android.settings", "com.android.phone")

    @Suppress("DEPRECATION")
    fun load(ctx: Context): List<AppItem> {
        val pm = ctx.packageManager
        return pm.getInstalledApplications(PackageManager.MATCH_DISABLED_COMPONENTS)
            .filter { it.packageName != ctx.packageName }
            .map {
                AppItem(
                    it.packageName,
                    pm.getApplicationLabel(it).toString(),
                    it.flags and ApplicationInfo.FLAG_SYSTEM != 0,
                    !it.enabled
                )
            }
            .sortedBy { it.name.lowercase() }
    }

    fun setHidden(pkg: String, hide: Boolean): Boolean =
        if (hide) Root.ok("pm disable-user --user 0 $pkg") else Root.ok("pm enable $pkg")

    fun forceStop(pkg: String): Boolean = Root.ok("am force-stop $pkg")

    fun clearCache(pkg: String): Boolean = Root.ok(
        "rm -rf /data/user/0/$pkg/cache/* /data/user/0/$pkg/code_cache/* /sdcard/Android/data/$pkg/cache/*"
    )
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

    private fun sp(c: Context) = c.getSharedPreferences("ashutool", Context.MODE_PRIVATE)

    fun locked(c: Context): Set<String> = sp(c).getStringSet("locked", emptySet()) ?: emptySet()

    fun setLocked(c: Context, pkg: String, on: Boolean) {
        val s = locked(c).toMutableSet()
        if (on) s.add(pkg) else s.remove(pkg)
        sp(c).edit().putStringSet("locked", s).apply()
    }

    fun hasPin(c: Context) = sp(c).contains("pin")
    fun pinLen(c: Context) = sp(c).getInt("pin_len", 4)

    fun setPin(c: Context, pin: String) {
        sp(c).edit().putString("pin", hash(pin)).putInt("pin_len", pin.length).apply()
    }

    fun check(c: Context, pin: String) = sp(c).getString("pin", null) == hash(pin)

    fun bool(c: Context, k: String, d: Boolean) = sp(c).getBoolean(k, d)
    fun setBool(c: Context, k: String, v: Boolean) = sp(c).edit().putBoolean(k, v).apply()
    fun int(c: Context, k: String, d: Int) = sp(c).getInt(k, d)
    fun setInt(c: Context, k: String, v: Int) = sp(c).edit().putInt(k, v).apply()

    private fun hash(s: String): String =
        MessageDigest.getInstance("SHA-256").digest("ashutool$s".toByteArray()).joinToString("") { "%02x".format(it) }
}
