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
import com.agupta07505.smartisland.AshutoolBridge

// =====================================================================================================================
// The Dynamic Island of Ashutool is Smart Island by Animesh Gupta (agupta07505), GNU GPL v3.0, copied as it is into the
// module :smartisland (see smartisland/UPSTREAM.md for what was changed). This file is the small glue on the Ashutool
// side: it uses root to do the setup that Smart Island otherwise asks for screen by screen, and to switch it on and off.
// =====================================================================================================================

object IslandControl {
    /** True when the accessibility service that draws the island is on. */
    fun isOn(ctx: Context): Boolean = AshutoolBridge.isOverlayEnabled(ctx)

    /**
     * Everything the island needs, granted through root: the overlay permission, accessibility and notification access, the
     * text of sensitive notifications, Bluetooth, background running and the optional ones (usage access, Do Not
     * Disturb, location). It is the setup that Smart Island runs through Shizuku, run through root here.
     * Blocks, call it off the main thread.
     */
    fun setup(ctx: Context): Boolean {
        val ok = Root.ok(*AshutoolBridge.setupCommands(ctx, true).toTypedArray())
        LockStore.setBool(ctx, "isl_setup_done", true)
        return ok
    }

    /** Switches the island on or off by switching its accessibility service, without touching other services. Blocks. */
    fun setEnabled(ctx: Context, on: Boolean): Boolean {
        if (on && !AshutoolBridge.isListenerEnabled(ctx)) return setup(ctx)
        val me = AshutoolBridge.overlayComponent(ctx)
        val cur = Root.run("settings get secure enabled_accessibility_services").firstOrNull()?.trim().orEmpty()
        val list = if (cur.isBlank() || cur == "null") emptyList() else cur.split(":").filter { it.isNotBlank() }
        if (on && me in list) return Root.ok("settings put secure accessibility_enabled 1")
        val next = if (on) list + me else list - me
        return if (next.isEmpty()) Root.ok("settings delete secure enabled_accessibility_services")
        else Root.ok(
            "settings put secure enabled_accessibility_services ${Root.q(next.joinToString(":"))}",
            "settings put secure accessibility_enabled 1"
        )
    }

    /** First start: the island is set up once by itself, so it works without the user doing anything. */
    fun firstSetup(ctx: Context) {
        if (!LockStore.bool(ctx, "isl_setup_done", false)) setup(ctx)
    }
}

/** What Ashutool used to show on its own island. Smart Island has no door for that, so the message is a toast. */
object IslandHub {
    /** The 5G / 4G tile shows its own state. Nothing to do. */
    fun flashNet(@Suppress("UNUSED_PARAMETER") ctx: Context, @Suppress("UNUSED_PARAMETER") fiveG: Boolean) {}

    /** The reels blocker says why it closed an app. */
    fun flashBlocked(ctx: Context, what: String, why: String) { toast(ctx, "$what: $why") }
}
