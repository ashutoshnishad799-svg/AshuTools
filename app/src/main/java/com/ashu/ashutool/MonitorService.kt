package com.ashu.ashutool

import android.app.*
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import androidx.core.content.ContextCompat
import kotlinx.coroutines.*

/**
 * Keeps Ashutool alive and runs the background features:
 * live notification, fallback app lock, game sidebar and game mode, vault secret code,
 * re-hiding apps opened from the vault, and charge limit.
 * If the service restarts several times in two minutes it switches to safe mode and pauses the risky ones.
 */
class MonitorService : Service() {
    private var scope: CoroutineScope? = null
    private var sidebar: GameSidebar? = null
    private var vaultRx: BroadcastReceiver? = null
    private var vaultCode = ""
    private var lastGameSeen = 0L
    private var lastLockShown = 0L
    private var lastRefresh = 0L
    private var games: Set<String> = emptySet()
    private var tick = 0

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val nm = getSystemService(NotificationManager::class.java)
        nm.createNotificationChannel(
            NotificationChannel(CHANNEL, "Live monitor", NotificationManager.IMPORTANCE_LOW).apply { setShowBadge(false) }
        )
        if (scope == null) checkSafeBoot()
        ServiceCompat.startForeground(
            this, ID, build(Monitor.snap.value),
            if (Build.VERSION.SDK_INT >= 34) ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE else 0
        )
        Monitor.start(this)
        if (scope == null) {
            val s = CoroutineScope(SupervisorJob() + Dispatchers.Main)
            scope = s
            sidebar = GameSidebar(this)
            s.launch(Dispatchers.IO) {
                val pols = Sys.policies()
                Sys.saveOriginal(this@MonitorService, pols)
                Charge.recover(this@MonitorService)
                if (!Guard.safe) {
                    GameMode.recover(this@MonitorService)
                    Firewall.applyAll(this@MonitorService)
                }
            }
            s.launch { Monitor.snap.collect { nm.notify(ID, build(it)) } }
            s.launch { tickLoop() }
        }
        return START_STICKY
    }

    override fun onDestroy() {
        scope?.cancel()
        sidebar?.hide()
        try { vaultRx?.let { unregisterReceiver(it) } } catch (_: Exception) {}
        // never leave charging switched off when the service goes away
        Thread {
            Charge.recover(this)
            if (GameMode.active != null) GameMode.stop(this)
        }.start()
        super.onDestroy()
    }

    /** Five fresh service starts inside two minutes means something is crash looping. */
    private fun checkSafeBoot() {
        val now = System.currentTimeMillis()
        val starts = LockStore.str(this, "starts", "").split(",").mapNotNull { it.toLongOrNull() }
            .filter { now - it < 120_000 } + now
        LockStore.setStr(this, "starts", starts.joinToString(","))
        if (starts.size >= 5) Guard.safe = true
    }

    private fun build(s: Snap): Notification {
        val (title, body) = statsLines(this, s)
        val open = PendingIntent.getActivity(this, 0, Intent(this, MainActivity::class.java), PendingIntent.FLAG_IMMUTABLE)
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

    private suspend fun tickLoop() {
        while (currentCoroutineContext().isActive) {
            try { tickOnce() } catch (e: Exception) { }
            delay(700)
        }
    }

    private suspend fun tickOnce() {
        tick++
        syncVault()
        val a11y = AshuAccessibility.connected
        val fast = !a11y && (LockStore.locked(this).isNotEmpty() || games.isNotEmpty() || LockStore.session.isNotEmpty())
        if (!a11y && (fast || tick % 4 == 0) && Usage.hasAccess(this)) {
            withContext(Dispatchers.IO) { Usage.foreground(this@MonitorService) }?.let { Fg.pkg = it }
        }
        val fg = Fg.pkg
        if (!a11y) pollLock(fg)
        if (!Guard.safe) gameTick(fg)
        rehideTick(fg)
        if (!Guard.safe && tick % 12 == 0) withContext(Dispatchers.IO) { Charge.enforce(this@MonitorService, Monitor.snap.value) }
        val now = System.currentTimeMillis()
        if (now - lastRefresh > 60_000) {
            lastRefresh = now
            games = withContext(Dispatchers.IO) { GameMode.games(this@MonitorService) }
        }
    }

    /** Fallback when the accessibility service is off. Slower, because it has to launch an Activity. */
    private fun pollLock(fg: String?) {
        val locked = LockStore.locked(this)
        if (locked.isEmpty() || fg == null || fg == packageName) return
        if (fg != LockStore.unlocked) LockStore.unlocked = null
        val now = System.currentTimeMillis()
        if (fg in locked && !Safety.lockForbidden(this, fg) && LockStore.unlocked != fg && now - lastLockShown > 1500) {
            lastLockShown = now
            startActivity(
                Intent(this, LockActivity::class.java).putExtra("pkg", fg)
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_NO_ANIMATION)
            )
        }
    }

    private suspend fun gameTick(fg: String?) {
        val sb = sidebar ?: return
        val now = System.currentTimeMillis()
        if (fg != null && fg in games) {
            lastGameSeen = now
            if (LockStore.bool(this, "g_sidebar", true)) sb.showFor(fg) else sb.hide()
            if (LockStore.bool(this, "g_auto", true) && GameMode.active != fg) {
                withContext(Dispatchers.IO) { GameMode.start(this@MonitorService, fg) }
            }
        } else if (fg != null && fg != packageName) {
            sb.hide()
            sb.resetDismiss()
            if (GameMode.active != null && now - lastGameSeen > 8000) {
                withContext(Dispatchers.IO) { GameMode.stop(this@MonitorService) }
            }
        }
    }

    private suspend fun rehideTick(fg: String?) {
        if (LockStore.session.isEmpty()) return
        if (!LockStore.bool(this, "hide_rehide", true)) { LockStore.session.clear(); return }
        if (fg == null) return
        val now = System.currentTimeMillis()
        for ((pkg, t) in LockStore.session.entries.toList()) {
            if (fg != pkg && now - t > 6000) {
                withContext(Dispatchers.IO) { Apps.rehide(this@MonitorService, pkg) }
                LockStore.session.remove(pkg)
            }
        }
    }

    /** Registers the dialer secret code receiver for the code the user chose, and removes it when turned off. */
    private fun syncVault() {
        val code = LockStore.str(this, "vault_code", "")
        val want = if (LockStore.bool(this, "vault_dial", true) && code.length in 4..6 && code.all { it.isDigit() }) code else ""
        if (want == vaultCode) return
        try { vaultRx?.let { unregisterReceiver(it) } } catch (_: Exception) {}
        vaultRx = null
        vaultCode = want
        if (want.isEmpty()) return
        val f = IntentFilter("android.provider.Telephony.SECRET_CODE")
        f.addDataScheme("android_secret_code")
        f.addDataAuthority(want, null)
        val r = object : BroadcastReceiver() {
            override fun onReceive(c: Context, i: Intent) {
                c.startActivity(Intent(c, VaultActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
            }
        }
        ContextCompat.registerReceiver(this, r, f, ContextCompat.RECEIVER_EXPORTED)
        vaultRx = r
    }

    companion object {
        const val CHANNEL = "monitor"
        const val ID = 1001
    }
}

class BootReceiver : BroadcastReceiver() {
    override fun onReceive(c: Context, i: Intent) {
        if (i.action == Intent.ACTION_BOOT_COMPLETED && LockStore.bool(c, "boot", true)) {
            ContextCompat.startForegroundService(c, Intent(c, MonitorService::class.java))
        }
    }
}
