package com.ashu.ashutool

import android.Manifest
import android.app.*
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.os.SystemClock
import android.telephony.TelephonyManager
import androidx.compose.runtime.snapshotFlow
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import androidx.core.content.ContextCompat
import kotlinx.coroutines.*

/**
 * Background worker. It is event driven on purpose: foreground changes arrive from the accessibility service,
 * the screen on and off from a receiver, calls from a callback. The only timer is a 30 second housekeeping loop,
 * which sleeps longer while the screen is off. If it restarts five times in two minutes it starts in safe mode.
 */
class MonitorService : Service() {
    private var scope: CoroutineScope? = null
    private var sidebar: GameSidebar? = null
    private var recPill: RecordPill? = null
    private var callPill: CallPill? = null
    private var banner: IncomingBanner? = null
    private var watcher: CallWatcher? = null
    private var screenRx: BroadcastReceiver? = null
    private val handler = Handler(Looper.getMainLooper())
    private var games: Set<String> = emptySet()
    private var lastNotify = 0L
    private var limitJob: Job? = null
    private var dozeJob: Job? = null
    private var recJob: Job? = null

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val nm = getSystemService(NotificationManager::class.java)
        nm.createNotificationChannel(NotificationChannel(CHANNEL, "Live monitor", NotificationManager.IMPORTANCE_LOW).apply { setShowBadge(false) })
        nm.createNotificationChannel(NotificationChannel("record", "Screen recording", NotificationManager.IMPORTANCE_LOW).apply { setShowBadge(false) })
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
            recPill = RecordPill(this)
            callPill = CallPill(this)
            banner = IncomingBanner(this)
            registerScreen()
            Fg.listener = { onFg(it) }
            tryWatch()
            s.launch(Dispatchers.IO) {
                Sys.saveOriginal(this@MonitorService, Sys.policies())
                Charge.recover(this@MonitorService)
                Saver.restoreCpu(this@MonitorService)
                Recorder.sync()
                games = GameMode.games(this@MonitorService)
                if (!Guard.safe) { GameMode.recover(this@MonitorService); Firewall.applyAll(this@MonitorService) }
            }
            s.launch { Monitor.snap.collect { onSnap(it, nm) } }
            s.launch { snapshotFlow { Recorder.running }.collect { onRecording(it) } }
            s.launch { housekeeping() }
            s.launch { poller() }
        }
        return START_STICKY
    }

    override fun onDestroy() {
        Fg.listener = null
        scope?.cancel()
        sidebar?.hide(); recPill?.hide(); callPill?.hide(); banner?.hide()
        try { screenRx?.let { unregisterReceiver(it) } } catch (_: Exception) {}
        watcher?.stop()
        Thread {
            Charge.recover(this)
            Saver.screenOn(this)
            if (GameMode.active != null) GameMode.stop(this)
        }.start()
        super.onDestroy()
    }

    private fun checkSafeBoot() {
        val now = System.currentTimeMillis()
        val starts = LockStore.str(this, "starts", "").split(",").mapNotNull { it.toLongOrNull() }.filter { now - it < 120_000 } + now
        LockStore.setStr(this, "starts", starts.joinToString(","))
        if (starts.size >= 5) Guard.safe = true
    }

    // ---- notification
    private fun build(s: Snap): Notification {
        val (title, body) = statsLines(this, s)
        val open = PendingIntent.getActivity(this, 0, Intent(this, MainActivity::class.java), PendingIntent.FLAG_IMMUTABLE)
        return NotificationCompat.Builder(this, CHANNEL)
            .setSmallIcon(R.drawable.ic_stat_bolt)
            .setContentTitle(title)
            .setContentText(body.substringBefore('\n'))
            .setStyle(NotificationCompat.BigTextStyle().bigText(body))
            .setContentIntent(open)
            .setOngoing(true).setOnlyAlertOnce(true).setShowWhen(false)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .build()
    }

    /** At most one update every five seconds, and once a minute while the screen is off. */
    private fun onSnap(s: Snap, nm: NotificationManager) {
        val now = SystemClock.elapsedRealtime()
        if (now - lastNotify < (if (Monitor.screenOn) 5000 else 55_000)) return
        lastNotify = now
        nm.notify(ID, build(s))
        if (!Guard.safe) scope?.launch(Dispatchers.IO) { Saver.auto(this@MonitorService, s) }
    }

    // ---- screen on and off
    private fun registerScreen() {
        val rx = object : BroadcastReceiver() {
            override fun onReceive(c: Context, i: Intent) { if (i.action == Intent.ACTION_SCREEN_OFF) screenOff() else screenOn() }
        }
        ContextCompat.registerReceiver(
            this, rx, IntentFilter().apply { addAction(Intent.ACTION_SCREEN_OFF); addAction(Intent.ACTION_SCREEN_ON) },
            ContextCompat.RECEIVER_NOT_EXPORTED
        )
        screenRx = rx
    }

    private fun screenOff() {
        Monitor.screenOn = false
        sidebar?.hide()
        limitJob?.cancel()
        scope?.launch(Dispatchers.IO) { BgControl.screenOff(this@MonitorService); Saver.screenOff(this@MonitorService) }
        if (LockStore.bool(this, "bs_doze", false) && !Guard.safe) {
            dozeJob?.cancel()
            dozeJob = scope?.launch {
                delay(LockStore.int(this@MonitorService, "bs_doze_min", 5) * 60_000L)
                if (!Monitor.screenOn && !Monitor.snap.value.batt.charging) withContext(Dispatchers.IO) { Saver.doze() }
            }
        }
    }

    private fun screenOn() {
        Monitor.screenOn = true
        dozeJob?.cancel()
        Monitor.kick(this)
        scope?.launch(Dispatchers.IO) { Saver.screenOn(this@MonitorService) }
    }

    // ---- foreground changes
    private fun onFg(pkg: String) {
        val prev = Fg.prev
        if (!Guard.safe) {
            if (pkg in games) {
                if (LockStore.bool(this, "g_sidebar", true)) sidebar?.showFor(pkg) else sidebar?.hide()
                if (LockStore.bool(this, "g_auto", true) && GameMode.active != pkg) scope?.launch(Dispatchers.IO) { GameMode.start(this@MonitorService, pkg) }
            } else if (pkg != packageName) {
                sidebar?.hide()
                sidebar?.resetDismiss()
                if (GameMode.active != null) handler.postDelayed({
                    if (Fg.pkg !in games && GameMode.active != null) scope?.launch(Dispatchers.IO) { GameMode.stop(this@MonitorService) }
                }, 8000)
            }
        }
        if (prev != null && prev != pkg) {
            if (((BgControl.flags(this)[prev] ?: 0) and 2) != 0) handler.postDelayed({
                if (Fg.pkg != prev) scope?.launch(Dispatchers.IO) { Apps.forceStop(this@MonitorService, prev) }
            }, 15_000)
            if (LockStore.session.containsKey(prev) && LockStore.bool(this, "hide_rehide", true)) handler.postDelayed({
                if (Fg.pkg != prev) scope?.launch(Dispatchers.IO) { Apps.rehide(this@MonitorService, prev); LockStore.session.remove(prev) }
            }, 6000)
        }
        limitJob?.cancel()
        if (Limits.all(this)[pkg] != null) limitJob = scope?.launch {
            while (Fg.pkg == pkg && Monitor.screenOn) {
                delay(15_000)
                AshuAccessibility.instance?.enforceLimit()
            }
        }
    }

    /** Only used when the accessibility service is off. Slow and only when something needs it. */
    private suspend fun poller() {
        while (currentCoroutineContext().isActive) {
            if (Monitor.screenOn && !AshuAccessibility.connected && Usage.hasAccess(this) &&
                (LockStore.locked(this).isNotEmpty() || games.isNotEmpty() || Limits.all(this).isNotEmpty())
            ) {
                val fg = withContext(Dispatchers.IO) { Usage.foreground(this@MonitorService) }
                if (fg != null) { Fg.set(fg); pollLock(fg) }
                delay(1500)
            } else delay(if (Monitor.screenOn) 10_000 else 60_000)
        }
    }

    private fun pollLock(fg: String) {
        if (fg == packageName || !LockStore.hasCred(this)) return
        if (fg != LockStore.unlocked) LockStore.unlocked = null
        val now = System.currentTimeMillis()
        if (fg in LockStore.locked(this) && !Safety.lockForbidden(this, fg) && LockStore.unlocked != fg && now - lastLock > 1500) {
            lastLock = now
            startActivity(Intent(this, LockActivity::class.java).putExtra("pkg", fg).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_NO_ANIMATION))
        }
    }

    private var lastLock = 0L

    // ---- housekeeping
    private suspend fun housekeeping() {
        var n = 0
        while (currentCoroutineContext().isActive) {
            delay(if (Monitor.screenOn) 30_000 else 120_000)
            n++
            val wanted = LockStore.bool(this, "cl_on", false) || LockStore.bool(this, "cl_manual", false) || LockStore.bool(this, "cl_temp_on", false) || Charge.off
            if (!Guard.safe && wanted) withContext(Dispatchers.IO) { Charge.enforce(this@MonitorService, Monitor.snap.value) }
            tryWatch()
            if (n % 10 == 0) games = withContext(Dispatchers.IO) { GameMode.games(this@MonitorService) }
        }
    }

    // ---- recording
    private fun onRecording(on: Boolean) {
        recJob?.cancel()
        val nm = getSystemService(NotificationManager::class.java)
        if (!on) { recPill?.hide(); nm.cancel(REC_ID); return }
        if (LockStore.bool(this, "rec_pill", true)) recPill?.show()
        recJob = scope?.launch {
            while (Recorder.running) {
                val stop = PendingIntent.getBroadcast(this@MonitorService, 7, Intent(this@MonitorService, RecordActionReceiver::class.java), PendingIntent.FLAG_IMMUTABLE)
                val sec = (SystemClock.elapsedRealtime() - Recorder.startedAt) / 1000
                nm.notify(
                    REC_ID,
                    NotificationCompat.Builder(this@MonitorService, "record").setSmallIcon(R.drawable.ic_stat_bolt)
                        .setContentTitle("Screen recording").setContentText(mmss(sec)).addAction(0, "Stop", stop)
                        .setOngoing(true).setOnlyAlertOnce(true).setShowWhen(false).setPriority(NotificationCompat.PRIORITY_LOW).build()
                )
                delay(1000)
            }
        }
    }

    // ---- calls
    private fun tryWatch() {
        if (watcher != null) return
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.READ_PHONE_STATE) != PackageManager.PERMISSION_GRANTED) return
        val w = CallWatcher(this) { onCall(it) }
        if (w.start()) watcher = w
    }

    private fun onCall(state: Int) {
        val prev = Calls.state
        Calls.state = state
        Calls.changedAt = SystemClock.elapsedRealtime()
        when (state) {
            TelephonyManager.CALL_STATE_RINGING -> {
                if (LockStore.bool(this, "calls_banner", true)) banner?.show()
                if (LockStore.bool(this, "calls_auto", false)) scope?.launch {
                    delay(LockStore.int(this@MonitorService, "calls_auto_sec", 3) * 1000L)
                    if (Calls.state == TelephonyManager.CALL_STATE_RINGING) withContext(Dispatchers.IO) { Calls.answer(this@MonitorService) }
                }
            }
            TelephonyManager.CALL_STATE_OFFHOOK -> {
                banner?.hide()
                if (prev != TelephonyManager.CALL_STATE_OFFHOOK) {
                    Calls.startedAt = SystemClock.elapsedRealtime()
                    if (LockStore.bool(this, "calls_speaker", false)) scope?.launch { delay(700); Calls.setSpeaker(this@MonitorService, true) }
                    if (LockStore.bool(this, "calls_mini", true)) callPill?.show()
                }
            }
            else -> {
                banner?.hide()
                callPill?.hide()
                if (Calls.speakerForced) Calls.setSpeaker(this, false)
                Calls.muted = false
            }
        }
    }

    companion object {
        const val CHANNEL = "monitor"
        const val ID = 1001
        const val REC_ID = 1002
    }
}

class BootReceiver : BroadcastReceiver() {
    override fun onReceive(c: Context, i: Intent) {
        if (i.action == Intent.ACTION_BOOT_COMPLETED && LockStore.bool(c, "boot", true)) {
            ContextCompat.startForegroundService(c, Intent(c, MonitorService::class.java))
        }
    }
}
