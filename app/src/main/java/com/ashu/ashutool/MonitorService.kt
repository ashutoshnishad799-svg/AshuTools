package com.ashu.ashutool

import android.app.*
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import androidx.core.content.ContextCompat
import kotlinx.coroutines.*

/**
 * Keeps Ashutool alive. It does two jobs:
 * 1. Shows live CPU, RAM and battery stats in the notification.
 * 2. Watches the foreground app and opens the PIN screen for locked apps.
 */
class MonitorService : Service() {
    private var scope: CoroutineScope? = null

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val nm = getSystemService(NotificationManager::class.java)
        nm.createNotificationChannel(
            NotificationChannel(CHANNEL, "Live monitor", NotificationManager.IMPORTANCE_LOW).apply {
                setShowBadge(false)
            }
        )
        ServiceCompat.startForeground(
            this, ID, build(Monitor.snap.value),
            if (Build.VERSION.SDK_INT >= 34) ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE else 0
        )
        Monitor.start(this)
        if (scope == null) {
            val s = CoroutineScope(SupervisorJob() + Dispatchers.Default)
            scope = s
            s.launch { Monitor.snap.collect { nm.notify(ID, build(it)) } }
            s.launch { lockLoop() }
        }
        return START_STICKY
    }

    override fun onDestroy() {
        scope?.cancel()
        super.onDestroy()
    }

    private fun build(s: Snap): Notification {
        val full = LockStore.statsInNotif(this)
        val b = s.batt
        val maxMhz = (s.cores.maxOfOrNull { it.khz } ?: 0) / 1000
        val title = if (full) "CPU ${s.cpu.toInt()}%   ${s.cpuTemp.toInt()}\u00B0C   RAM ${s.ramPct.toInt()}%" else "Ashutool is running"
        val body = if (full)
            "Battery ${b.level}%   ${b.ma} mA   ${"%.1f".format(b.tempC)}\u00B0C\nCore max $maxMhz MHz   Governor ${s.gov}"
        else "Tap to open"
        val open = PendingIntent.getActivity(
            this, 0, Intent(this, MainActivity::class.java), PendingIntent.FLAG_IMMUTABLE
        )
        return NotificationCompat.Builder(this, CHANNEL)
            .setSmallIcon(R.drawable.ic_stat_bolt)
            .setContentTitle(title)
            .setContentText(body.substringBefore('\n'))
            .setStyle(NotificationCompat.BigTextStyle().bigText(body))
            .setContentIntent(open)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setShowWhen(false)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .build()
    }

    private suspend fun lockLoop() {
        var last: String? = null
        var lastShown = 0L
        while (currentCoroutineContext().isActive) {
            val locked = LockStore.locked(this)
            if (locked.isNotEmpty() && Usage.hasAccess(this)) {
                val fg = Usage.foreground(this) ?: last
                last = fg
                if (fg != null && fg != packageName) {
                    if (fg != LockStore.unlocked) LockStore.unlocked = null
                    val now = System.currentTimeMillis()
                    if (fg in locked && LockStore.unlocked != fg && now - lastShown > 1500) {
                        lastShown = now
                        startActivity(
                            Intent(this, LockActivity::class.java)
                                .putExtra("pkg", fg)
                                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_NO_ANIMATION)
                        )
                    }
                }
            }
            delay(600)
        }
    }

    companion object {
        const val CHANNEL = "monitor"
        const val ID = 1001
    }
}

class BootReceiver : BroadcastReceiver() {
    override fun onReceive(c: Context, i: Intent) {
        if (i.action == Intent.ACTION_BOOT_COMPLETED) {
            ContextCompat.startForegroundService(c, Intent(c, MonitorService::class.java))
        }
    }
}
