package com.ashu.ashutool

import android.content.Context
import com.topjohnwu.superuser.Shell
import java.text.SimpleDateFormat
import java.util.ArrayDeque
import java.util.Date
import java.util.Locale

/**
 * Every root command in the app goes through here.
 * Rules the rest of the code follows:
 *  - package names pass Safety.validPkg before they reach a command
 *  - governors and frequencies are only taken from lists read from the kernel
 *  - sysfs paths come from fixed whitelists, never from user input
 *  - nothing is ever remounted or written under /system
 */
object Root {
    init {
        Shell.setDefaultBuilder(Shell.Builder.create().setTimeout(15))
    }

    private val log = ArrayDeque<String>()

    fun isGranted(): Boolean = try { Shell.getShell().isRoot } catch (e: Exception) { false }

    /** Read-only commands. Not written to the action log. */
    fun run(vararg cmd: String): List<String> =
        try { Shell.cmd(*cmd).exec().out } catch (e: Exception) { emptyList() }

    /** State-changing commands. Logged so the user can see what the app did. */
    fun ok(vararg cmd: String): Boolean {
        note(cmd)
        return try { Shell.cmd(*cmd).exec().isSuccess } catch (e: Exception) { false }
    }

    @Synchronized
    private fun note(cmd: Array<out String>) {
        val t = SimpleDateFormat("HH:mm:ss", Locale.US).format(Date())
        log.addFirst("$t  ${cmd.joinToString("; ").take(150)}")
        while (log.size > 60) log.removeLast()
    }

    @Synchronized
    fun recent(): List<String> = log.toList()

    /** Single-quote a value so the shell cannot interpret it. */
    fun q(s: String) = "'" + s.replace("'", "'\\''") + "'"

    fun a11yId(ctx: Context) = "${ctx.packageName}/${ctx.packageName}.AshuAccessibility"

    /** Turn the instant-lock accessibility service on or off without touching other services. */
    fun setA11y(ctx: Context, on: Boolean): Boolean {
        val me = a11yId(ctx)
        val cur = run("settings get secure enabled_accessibility_services").firstOrNull()?.trim().orEmpty()
        val list = if (cur.isBlank() || cur == "null") emptyList() else cur.split(":").filter { it.isNotBlank() }
        if (on && me in list) return ok("settings put secure accessibility_enabled 1")
        val next = if (on) list + me else list - me
        return if (next.isEmpty()) ok("settings delete secure enabled_accessibility_services")
        else ok(
            "settings put secure enabled_accessibility_services ${q(next.joinToString(":"))}",
            "settings put secure accessibility_enabled 1"
        )
    }

    /** Grants the app what it needs, so the user never opens Settings. */
    fun bootstrap(ctx: Context) {
        val pkg = ctx.packageName
        ok(
            "appops set $pkg GET_USAGE_STATS allow",
            "appops set $pkg SYSTEM_ALERT_WINDOW allow",
            "pm grant $pkg android.permission.POST_NOTIFICATIONS",
            "dumpsys deviceidle whitelist +$pkg"
        )
        if (LockStore.bool(ctx, "use_a11y", true)) setA11y(ctx, true)
    }
}
