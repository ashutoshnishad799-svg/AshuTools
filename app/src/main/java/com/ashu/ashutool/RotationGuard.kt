/*
 * Ashutool - root toolkit for custom ROMs
 * Copyright (C) 2026 Ashutosh Nishad
 * Telegram: @ashutosh_07x (https://t.me/ashutosh_07x)
 *
 * Free software under the GNU GPL v3.0 or later, with the attribution terms in NOTICE.
 * Keep this header and the author credit in every copy and modified version.
 */

package com.ashu.ashutool

import android.content.Context
import android.hardware.display.DisplayManager
import android.view.Display

/**
 * Keeps the screen from turning while a floating window is open.
 *
 * A portrait app in a window over a landscape game asks the system to turn the screen, and the game asks for it
 * back. The two fight and the whole screen ends up in a broken mix of portrait and landscape.
 * So the screen is locked in the direction it has when the first window opens, and apps are told they cannot
 * decide the direction. When the last window closes, your own settings are put back exactly as they were.
 * What was there before is written down first, so even after a crash it can be restored on the next start.
 */
object RotationGuard {
    private var holders = 0

    private fun setting(ns: String, key: String, def: String): String =
        Root.run("settings get $ns $key").firstOrNull()?.trim()?.takeIf { it.isNotEmpty() && it != "null" } ?: def

    private fun rotation(ctx: Context): Int = try {
        ctx.getSystemService(DisplayManager::class.java).getDisplay(Display.DEFAULT_DISPLAY).rotation
    } catch (e: Exception) { 0 }

    /** Call before a floating window opens. Needs to run off the main thread. */
    fun hold(ctx: Context) {
        val first = synchronized(this) { holders++ == 0 }
        if (!first) return
        if (LockStore.str(ctx, "rot_prev", "").isEmpty()) {
            val auto = setting("system", "accelerometer_rotation", "1")
            val user = setting("system", "user_rotation", "0")
            val ign = Root.run("wm get-ignore-orientation-request 2>/dev/null").joinToString(" ").contains("true", ignoreCase = true)
            LockStore.setStr(ctx, "rot_prev", "$auto,$user,$ign")
        }
        val rot = rotation(ctx)
        if (!Root.ok("wm user-rotation lock $rot")) {
            Root.ok("settings put system accelerometer_rotation 0", "settings put system user_rotation $rot")
        }
        // Not every Android has this command. Where it does not, the lock above is still in place.
        Root.run("wm set-ignore-orientation-request true 2>/dev/null")
    }

    /** Call when a floating window is gone. The last one puts everything back. */
    fun release(ctx: Context) {
        val last = synchronized(this) { if (holders > 0) holders--; holders == 0 }
        if (last) restore(ctx)
    }

    /** Puts back what was there before. Safe to call when nothing was changed. */
    fun restore(ctx: Context) {
        val prev = LockStore.str(ctx, "rot_prev", "")
        if (prev.isEmpty()) return
        val p = prev.split(",")
        val auto = p.getOrNull(0) ?: "1"
        val user = (p.getOrNull(1) ?: "0").toIntOrNull()?.coerceIn(0, 3) ?: 0
        val ign = p.getOrNull(2) == "true"
        Root.run("wm set-ignore-orientation-request $ign 2>/dev/null")
        if (auto == "1") {
            if (!Root.ok("wm user-rotation auto")) Root.ok("settings put system accelerometer_rotation 1")
        } else {
            if (!Root.ok("wm user-rotation lock $user")) Root.ok("settings put system accelerometer_rotation 0", "settings put system user_rotation $user")
        }
        LockStore.setStr(ctx, "rot_prev", "")
    }

    /** At start: if the app died while a window was open, the screen is still locked. Give it back. */
    fun recover(ctx: Context) {
        val open = synchronized(this) { holders > 0 }
        if (!open) restore(ctx)
    }
}
