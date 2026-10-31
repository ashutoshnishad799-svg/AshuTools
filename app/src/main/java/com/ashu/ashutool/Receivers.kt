/*
 * Ashutool - root toolkit for custom ROMs
 * Copyright (C) 2026 Ashutosh Nishad
 * Telegram: @ashutosh_07x (https://t.me/ashutosh_07x)
 *
 * Free software under the GNU GPL v3.0 or later, with the attribution terms in NOTICE.
 * Keep this header and the author credit in every copy and modified version.
 */

package com.ashu.ashutool

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

/** Dialer secret code *#*#code#*#*. The manifest accepts any code, this checks it against the saved one. */
class VaultCodeReceiver : BroadcastReceiver() {
    override fun onReceive(c: Context, i: Intent) {
        val host = i.data?.host ?: return
        val code = LockStore.str(c, "vault_code", "")
        if (LockStore.bool(c, "vault_dial", true) && code.length in 4..6 && host == code) {
            c.startActivity(Intent(c, VaultActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        }
    }
}

/** Stop button on the recording notification. */
class RecordActionReceiver : BroadcastReceiver() {
    override fun onReceive(c: Context, i: Intent) {
        val p = goAsync()
        Thread { Recorder.stop(); p.finish() }.start()
    }
}
