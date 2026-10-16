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
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification

/**
 * Reads the name of the caller from the incoming call notification of the dialer, so the call banner can show
 * the contact name instead of only "Incoming call". Nothing else is read and nothing is stored.
 */
class CallNameService : NotificationListenerService() {
    override fun onNotificationPosted(sbn: StatusBarNotification) {
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
