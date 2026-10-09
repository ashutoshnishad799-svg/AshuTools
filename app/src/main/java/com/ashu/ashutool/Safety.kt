package com.ashu.ashutool

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.SystemClock
import android.provider.Settings
import android.provider.Telephony
import android.telecom.TelecomManager

/**
 * Guards for a tool that runs as root. Every destructive action calls these first,
 * so a UI bug can never hide, freeze, remove or block something the phone needs to work.
 */
object Safety {
    private val pkgRe = Regex("^[A-Za-z0-9_]+(\\.[A-Za-z0-9_]+)*$")

    fun validPkg(p: String) = p.length in 1..255 && pkgRe.matches(p)

    private val fixed = setOf(
        "android", "com.android.systemui", "com.android.settings", "com.android.phone", "com.android.shell",
        "com.android.server.telecom", "com.android.bluetooth", "com.android.nfc", "com.android.keychain",
        "com.android.certinstaller", "com.android.packageinstaller", "com.google.android.packageinstaller",
        "com.android.permissioncontroller", "com.google.android.permissioncontroller",
        "com.android.inputmethod.latin", "com.android.webview", "com.google.android.webview",
        "com.google.android.gms", "com.google.android.gsf", "com.android.vending",
        "com.android.managedprovisioning", "com.android.location.fused", "com.android.carrierconfig",
        "com.android.cellbroadcastreceiver", "com.android.emergency", "com.android.stk", "com.android.mms.service",
        "com.android.networkstack", "com.android.networkstack.inprocess", "com.android.captiveportallogin",
        "com.topjohnwu.magisk", "io.github.vvb2060.magisk", "me.weishu.kernelsu", "me.bmax.apatch"
    )

    @Volatile private var uiAt = 0L
    @Volatile private var uiSet: Set<String> = emptySet()
    @Volatile private var allSet: Set<String> = emptySet()

    /** Launchers and the active keyboard. Locking or hiding these would strand the user. */
    @Suppress("DEPRECATION")
    private fun refresh(ctx: Context) {
        val now = SystemClock.elapsedRealtime()
        if (now - uiAt < 60_000 && allSet.isNotEmpty()) return
        val ui = HashSet<String>()
        val all = HashSet<String>()
        try {
            ctx.packageManager.queryIntentActivities(Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME), 0)
                .forEach { ui += it.activityInfo.packageName }
        } catch (_: Exception) {}
        try {
            Settings.Secure.getString(ctx.contentResolver, Settings.Secure.DEFAULT_INPUT_METHOD)
                ?.substringBefore('/')?.let { ui += it }
        } catch (_: Exception) {}
        all += ui
        try { ctx.getSystemService(TelecomManager::class.java)?.defaultDialerPackage?.let { all += it } } catch (_: Exception) {}
        try { Telephony.Sms.getDefaultSmsPackage(ctx)?.let { all += it } } catch (_: Exception) {}
        uiSet = ui
        allSet = all
        uiAt = now
    }

    /** True for anything that must never be hidden, frozen, removed, cleared or blocked. */
    @Suppress("DEPRECATION")
    fun isProtected(ctx: Context, pkg: String, uid: Int = -1): Boolean {
        if (!validPkg(pkg)) return true
        if (pkg == ctx.packageName || pkg in fixed || pkg.startsWith("com.android.providers.")) return true
        refresh(ctx)
        if (pkg in allSet) return true
        val u = if (uid >= 0) uid else try {
            ctx.packageManager.getApplicationInfo(pkg, PackageManager.MATCH_UNINSTALLED_PACKAGES).uid
        } catch (e: Exception) { -1 }
        return u in 0..9999
    }

    /** Apps the lock screen must never cover. */
    fun lockForbidden(ctx: Context, pkg: String): Boolean {
        if (!validPkg(pkg)) return true
        refresh(ctx)
        return pkg == ctx.packageName || pkg in uiSet || pkg == "com.android.systemui" || pkg == "android"
    }
}
