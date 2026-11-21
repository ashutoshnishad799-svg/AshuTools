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
import androidx.compose.ui.graphics.toArgb
import com.agupta07505.smartisland.AshutoolBridge
import com.agupta07505.smartisland.ui.HostLook
import com.agupta07505.smartisland.ui.HostTheme
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

// =====================================================================================================================
// The Dynamic Island of Ashutool is Smart Island by Animesh Gupta (agupta07505), GNU GPL v3.0, copied into the module
// :smartisland (see smartisland/UPSTREAM.md for what was changed). This file is the Ashutool side of it:
//   IslandControl  root setup, on and off
//   IslandSkin     hands the look of Ashutool (colours, glass, glow) to the Smart Island screens
//   IslandTwists   what Ashutool adds: the island hides while you play, its colours follow Ashutool
// =====================================================================================================================

object IslandControl {
    /** True when the island is switched on (its master switch) and the accessibility service that draws it is on. */
    fun isOn(ctx: Context): Boolean = AshutoolBridge.islandEnabled && AshutoolBridge.isOverlayEnabled(ctx)

    /**
     * Everything the island needs, granted through root: the overlay permission, accessibility and notification access, the
     * text of sensitive notifications, Bluetooth, background running and the optional ones (usage access, Do Not
     * Disturb, location). It is the setup that Smart Island runs through Shizuku, run through root here. Then the
     * island's master switch is turned on, which Smart Island keeps off until you switch it on yourself.
     * Blocks, call it off the main thread.
     */
    fun setup(ctx: Context): Boolean {
        val ok = Root.ok(*AshutoolBridge.setupCommands(ctx, true).toTypedArray())
        LockStore.setBool(ctx, "isl_setup_done", true)
        AshutoolBridge.setIslandEnabled(ctx, true)
        return ok
    }

    /** Switches the island on or off with its master switch. Switching on also runs the setup when something is missing. Blocks. */
    fun setEnabled(ctx: Context, on: Boolean): Boolean {
        if (!on) {
            AshutoolBridge.setIslandEnabled(ctx, false)
            return true
        }
        val ready = AshutoolBridge.isOverlayEnabled(ctx) && AshutoolBridge.isListenerEnabled(ctx)
        val ok = if (ready) true else Root.ok(*AshutoolBridge.setupCommands(ctx, true).toTypedArray())
        AshutoolBridge.setIslandEnabled(ctx, true)
        return ok
    }

    /** First start: the island is set up once by itself, so it works without the user doing anything. */
    fun firstSetup(ctx: Context) {
        if (!LockStore.bool(ctx, "isl_setup_done", false)) setup(ctx)
    }
}

object IslandSkin {
    /** The Smart Island screens use the look of Ashutool. Off gives Smart Island's own theme back. */
    @Volatile var useAshuLook = true

    /** Reads the switch and hands the current look over. Called whenever the palette is loaded. */
    fun refresh(ctx: Context) {
        useAshuLook = LockStore.bool(ctx, "isl_host_look", true)
        AshutoolBridge.setEdge(LockStore.int(ctx, "isl_edge", 2), LockStore.int(ctx, "isl_edge_pwr", 55))
        push()
    }

    /** Hands the current look over, only when it changed. */
    fun push() {
        if (!useAshuLook) {
            if (HostTheme.look != null) HostTheme.look = null
            return
        }
        val p = Palette.cur
        val look = HostLook(
            dark = p.dark, flat = p.flat, textHi = p.textHi, textLo = p.textLo, dialog = p.dialog,
            accent = p.accent, accent2 = Accent2, bg1 = p.bg1, bg2 = p.bg2, bg3 = p.bg3,
            orb1 = p.orb1, orb2 = p.orb2, orb3 = p.orb3, orbAlpha = p.orbA, glass = Palette.glass, animated = Palette.animated
        )
        if (HostTheme.look != look) HostTheme.look = look
    }
}

object IslandTwists {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    /**
     * A game starts or ends. With "Hide the island in games" on, the island is switched off for the game and back on
     * after it, but only if it was on before: an island you switched off yourself stays off.
     */
    fun onGame(ctx: Context, inGame: Boolean) {
        val app = ctx.applicationContext
        if (inGame) {
            if (!LockStore.bool(app, "isl_game_hide", true)) return
            scope.launch {
                if (AshutoolBridge.readIslandEnabled(app)) {
                    LockStore.setBool(app, "isl_hidden_by_game", true)
                    AshutoolBridge.setIslandEnabled(app, false)
                }
            }
        } else {
            // Called at every app switch, so the cheap check comes first
            if (!LockStore.bool(app, "isl_hidden_by_game", false)) return
            LockStore.setBool(app, "isl_hidden_by_game", false)
            scope.launch { AshutoolBridge.setIslandEnabled(app, true) }
        }
    }

    /** Makes the island's own accent colours (music wave, notification dot, tint of the open cards) match the Ashutool accent. */
    fun matchAccent(ctx: Context) {
        AshutoolBridge.syncAccent(ctx, Accent.toArgb() and 0xFFFFFF)
    }
}

/** What Ashutool used to show on its own island. Smart Island has no door for that, so the message is a toast. */
object IslandHub {
    /** The 5G / 4G tile shows its own state. Nothing to do. */
    fun flashNet(@Suppress("UNUSED_PARAMETER") ctx: Context, @Suppress("UNUSED_PARAMETER") fiveG: Boolean) {}

    /** The reels blocker says why it closed an app. */
    fun flashBlocked(ctx: Context, what: String, why: String) { toast(ctx, "$what: $why") }
}
