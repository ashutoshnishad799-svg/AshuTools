/*
 * Ashutool - root toolkit for custom ROMs
 * Copyright (C) 2026 Ashutosh Nishad
 * Telegram: @ashutosh_07x (https://t.me/ashutosh_07x)
 *
 * Free software under the GNU GPL v3.0 or later, with the attribution terms in NOTICE.
 * Keep this header and the author credit in every copy and modified version.
 */

package com.ashu.ashutool

import android.app.Notification
import android.content.ComponentName
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification

/**
 * Reads the name of the caller from the incoming call notification of the dialer, so the call banner can show
 * the contact name instead of only "Incoming call". Nothing else is read and nothing is stored.
 *
 * A notification listener is bound by the system for as long as it is enabled, and it receives every
 * notification of every app. So it is only kept enabled while it can be useful, see [CallNames].
 */
class CallNameService : NotificationListenerService() {
    override fun onListenerConnected() {
        // A call that started ringing before the listener was bound is already in the shade
        try { activeNotifications?.forEach { handle(it) } } catch (_: Exception) {}
    }

    override fun onNotificationPosted(sbn: StatusBarNotification) = handle(sbn)

    private fun handle(sbn: StatusBarNotification) {
        try {
            val n = sbn.notification ?: return
            if (n.category != Notification.CATEGORY_CALL && sbn.packageName !in Safety.dialerPkgs(this)) return
            val ex = n.extras ?: return
            val title = ex.getCharSequence(Notification.EXTRA_TITLE)?.toString()?.trim().orEmpty()
            val text = ex.getCharSequence(Notification.EXTRA_TEXT)?.toString()?.trim().orEmpty()
            // The name is whichever line is not the words "incoming call", "ongoing call" and so on
            val name = listOf(title, text).firstOrNull { it.isNotEmpty() && !it.contains("call", ignoreCase = true) } ?: return
            Calls.caller = name
        } catch (_: Exception) {}
    }
}

/**
 * Switches the call name listener on only while it is needed: in a game, or everywhere if the user chose to
 * show call banners outside games. The rest of the time the component is disabled, so the system does not bind it,
 * it holds no memory and it never wakes up for notifications.
 */
object CallNames {
    private fun comp(ctx: Context) = ComponentName(ctx, CallNameService::class.java)

    /** Whether the listener has any job right now. */
    fun wanted(ctx: Context): Boolean =
        LockStore.bool(ctx, "calls_banner", true) && (Fg.game != null || !LockStore.bool(ctx, "calls_game_only", true))

    /** Enables or disables the listener. Safe to call as often as you like, it only acts on a change. */
    fun set(ctx: Context, on: Boolean) {
        try {
            val pm = ctx.packageManager
            val c = comp(ctx)
            val state = pm.getComponentEnabledSetting(c)
            val enabled = state == PackageManager.COMPONENT_ENABLED_STATE_ENABLED || state == PackageManager.COMPONENT_ENABLED_STATE_DEFAULT
            if (on == enabled) return
            pm.setComponentEnabledSetting(
                c,
                if (on) PackageManager.COMPONENT_ENABLED_STATE_ENABLED else PackageManager.COMPONENT_ENABLED_STATE_DISABLED,
                PackageManager.DONT_KILL_APP
            )
            if (on && Build.VERSION.SDK_INT >= 24) NotificationListenerService.requestRebind(c)
        } catch (_: Exception) {}
    }

    /** Run off the main thread just before the listener is needed. Makes sure the system still allows it to read notifications. */
    fun grant(ctx: Context) {
        Root.run("cmd notification allow_listener ${ctx.packageName}/${ctx.packageName}.CallNameService")
    }
}
