package com.ashu.ashutool

import com.topjohnwu.superuser.Shell

/** Thin wrapper over libsu. Every root command in the app goes through here. */
object Root {
    init {
        Shell.setDefaultBuilder(Shell.Builder.create().setTimeout(15))
    }

    fun isGranted(): Boolean = Shell.getShell().isRoot

    fun run(vararg cmd: String): List<String> = Shell.cmd(*cmd).exec().out

    fun ok(vararg cmd: String): Boolean = Shell.cmd(*cmd).exec().isSuccess

    /** Grants the app everything it needs, so the user never opens Settings. */
    fun bootstrap(pkg: String) {
        ok(
            "appops set $pkg GET_USAGE_STATS allow",
            "appops set $pkg SYSTEM_ALERT_WINDOW allow",
            "pm grant $pkg android.permission.POST_NOTIFICATIONS",
            "dumpsys deviceidle whitelist +$pkg"
        )
    }
}
