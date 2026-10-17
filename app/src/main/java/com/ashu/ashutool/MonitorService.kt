/*
 * Ashutool - root toolkit for custom ROMs
 * Copyright (C) 2026 Ashutosh Nishad
 * Telegram: @ashutosh_07x (https://t.me/ashutosh_07x)
 *
 * Free software under the GNU GPL v3.0 or later, with the attribution terms in NOTICE.
 * Keep this header and the author credit in every copy and modified version.
 */

package com.ashu.ashutool

import android.Manifest
import android.app.*
import android.net.TrafficStats
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.content.pm.ServiceInfo
import android.content.res.Configuration
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
import kotlinx.coroutines.channels.Channel

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
    private var hud: GameHud? = null
    private var statusSpeed: StatusSpeed? = null
    private var watcher: CallWatcher? = null
    private var screenRx: BroadcastReceiver? = null
    private val handler = Handler(Looper.getMainLooper())
    /** Always the newest list. A game added on the Games page counts straight away. */
    private val games: Set<String> get() = GameMode.cached
    private var lastNotify = 0L
    private var limitJob: Job? = null
    private var dozeJob: Job? = null
    private var recJob: Job? = null

    override fun onBind(intent: Intent?): IBinder? = null

    /** For the Games page: is the bar window on screen right now, and if not, why. */
    fun barShown(): Boolean = sidebar?.isShown == true
    fun barError(): String = sidebar?.error ?: ""

    /** Checks again right now, for example after a game was added by hand. */
    fun recheck() { handler.post { reeval() } }

    /** Shows the bar for a few seconds to prove the window itself works. Returns an empty text on success, else the reason. */
    fun testBar(): String {
        val sb = sidebar ?: return "The service is not ready yet"
        sb.hide()
        sb.showFor(packageName)
        handler.postDelayed({ if (Fg.game == null) sb.hide() }, 6000)
        return if (sb.isShown) "" else sb.error.ifEmpty { "The window could not be added" }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (Integrity.tampered(this)) { stopSelf(); return START_NOT_STICKY }
        instance = this
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
            hud = GameHud(this)
            statusSpeed = StatusSpeed(this)
            registerScreen()
            Fg.listener = { onFg(it) }
            tryWatch()
            s.launch(Dispatchers.IO) {
                Sys.saveOriginal(this@MonitorService, Sys.policies())
                Charge.recover(this@MonitorService)
                Saver.restoreCpu(this@MonitorService)
                Recorder.sync()
                GameMode.games(this@MonitorService)
                RotationGuard.recover(this@MonitorService)
                if (!Guard.safe) { GameMode.recover(this@MonitorService); Firewall.applyAll(this@MonitorService) }
                handler.post { seedForeground(); reeval() }
            }
            s.launch { Monitor.snap.collect { onSnap(it, nm) } }
            s.launch { snapshotFlow { Recorder.running }.collect { onRecording(it) } }
            s.launch { housekeeping() }
            s.launch { poller() }
            s.launch { gameWatch() }
            s.launch { speedLoop() }
        }
        return START_STICKY
    }

    /** Games turn the screen. The bar and the floating pills are placed again for the new size. */
    override fun onConfigurationChanged(newConfig: Configuration) {
        super.onConfigurationChanged(newConfig)
        val fix = Runnable { sidebar?.relayout(); recPill?.reposition(); callPill?.reposition(); if (statusSpeed != null && SpeedState.text.isNotEmpty()) speedWake.trySend(Unit) }
        handler.postDelayed(fix, 150)
        handler.postDelayed(fix, 700)
    }

    override fun onDestroy() {
        instance = null
        Fg.listener = null
        Fg.game = null
        scope?.cancel()
        sidebar?.hide(); recPill?.hide(); callPill?.hide(); banner?.hide(); hud?.hide(); statusSpeed?.hide()
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
            override fun onReceive(c: Context, i: Intent) {
                when (i.action) {
                    Intent.ACTION_SCREEN_OFF -> screenOff()
                    Intent.ACTION_USER_PRESENT -> handler.postDelayed({ reeval() }, 400)
                    else -> screenOn()
                }
            }
        }
        ContextCompat.registerReceiver(
            this, rx,
            IntentFilter().apply {
                addAction(Intent.ACTION_SCREEN_OFF); addAction(Intent.ACTION_SCREEN_ON); addAction(Intent.ACTION_USER_PRESENT)
            },
            ContextCompat.RECEIVER_NOT_EXPORTED
        )
        screenRx = rx
    }

    private fun screenOff() {
        Monitor.screenOn = false
        sidebar?.hide()
        hud?.hide()
        statusSpeed?.hide()
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
        handler.postDelayed({ reeval() }, 700)
        watchWake.trySend(Unit)
        speedWake.trySend(Unit)
    }

    // ---- foreground changes
    private fun onFg(pkg: String) {
        val prev = Fg.prev
        trackGameSpace(pkg)
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

    // ---- game space
    /** Windows that can sit on top of a game without you having left it: calls, store popups, Google sign-in. */
    private fun isTransient(pkg: String): Boolean = pkg in Safety.dialerPkgs(this) || pkg in TRANSIENT

    /**
     * Keeps Fg.game up to date and starts or ends the game tools.
     * Going to a game: bar on, game mode on. Going to a call or a popup while in a game: nothing changes.
     * Going anywhere else: bar off, game mode off after a few seconds.
     */
    private fun trackGameSpace(pkg: String) {
        try {
            val cur = Fg.game
            when {
                pkg in games -> {
                    Fg.game = pkg
                    watchWake.trySend(Unit)
                    speedWake.trySend(Unit)
                    if (!Guard.safe) enterGame(pkg)
                }
                pkg == packageName -> {}
                cur != null && (isTransient(pkg) || FloatWindows.hasWindow(pkg)) -> {}
                cur != null -> verifyLeave(cur, pkg)
                else -> if (!Guard.safe) leaveGame()
            }
            syncCallUi()
        } catch (e: Exception) {
            GameDiag.error = "${e.javaClass.simpleName}: ${e.message ?: ""}".take(160)
        }
    }

    /**
     * Another window came up while you were in a game. Many phones show their own game assistant or a popup
     * on top of a game, and that is not leaving it. So before the bar is taken away, check what really came
     * to the front. Only a real switch to another app ends game space.
     */
    private fun verifyLeave(game: String, pkg: String) {
        scope?.launch(Dispatchers.IO) {
            delay(900)
            val usage = Usage.hasAccess(this@MonitorService)
            val top = if (usage) Usage.foreground(this@MonitorService) else Usage.topByRoot()
            withContext(Dispatchers.Main) { finishLeave(game, pkg, usage, top) }
        }
    }

    private fun finishLeave(game: String, pkg: String, usage: Boolean, top: String?) {
        if (Fg.game != game || Fg.pkg != pkg) return
        // With usage access, no recent change means nothing replaced the game. Without it, trust the window event.
        // A launcher in front is never a pop-up over the game. It is the end of game space.
        val home = pkg in Safety.homePkgs(this)
        val stay = !home && if (usage) (top == null || top == game || skipFg(top) || isTransient(top) || FloatWindows.hasWindow(top)) else (top == game)
        if (stay) return
        Fg.game = null
        if (!Guard.safe) leaveGame()
        syncCallUi()
    }

    private fun enterGame(pkg: String) {
        if (LockStore.bool(this, "g_sidebar", true)) sidebar?.showFor(pkg) else sidebar?.hide()
        if (LockStore.bool(this, "g_auto", false) && GameMode.active != pkg) {
            scope?.launch(Dispatchers.IO) { GameMode.start(this@MonitorService, pkg) }
        }
    }

    private fun leaveGame() {
        sidebar?.hide()
        if (GameMode.active != null) handler.postDelayed({
            if (Fg.game == null && GameMode.active != null) scope?.launch(Dispatchers.IO) { GameMode.stop(this@MonitorService) }
        }, 8000)
    }

    private fun keyguardLocked(): Boolean =
        try { getSystemService(KeyguardManager::class.java).isKeyguardLocked } catch (e: Exception) { false }

    /** Puts the bar back if a window event was missed, for example after the screen turned off and on. */
    private fun reeval() {
        if (Guard.safe || !Monitor.screenOn || keyguardLocked()) return
        val p = Fg.pkg
        if (p != null && p in games && Fg.game != p) { trackGameSpace(p); return }
        val g = Fg.game
        if (g == null) {
            if (sidebar?.isShown == true) sidebar?.hide()
            return
        }
        if (LockStore.bool(this, "g_sidebar", true)) sidebar?.showFor(g) else sidebar?.hide()
    }

    /** After a restart no window event has arrived yet, so ask the accessibility service what is in front. */
    private fun seedForeground() {
        if (Fg.pkg != null) return
        val p = try { AshuAccessibility.instance?.rootInActiveWindow?.packageName?.toString() } catch (e: Exception) { null }
        if (p != null && p != packageName && p != "com.android.systemui") Fg.set(p)
    }

    /** Wakes the speed loop, for example when the switch in Settings changes. */
    fun wakeSpeed() { speedWake.trySend(Unit) }

    private val speedWake = Channel<Unit>(Channel.CONFLATED)

    /**
     * Network speed in the status bar, and the FPS and speed readout while in a game.
     * It sleeps completely when neither is switched on, and checks the switches again every fifteen seconds.
     */
    private suspend fun speedLoop() {
        var rx = 0L
        var tx = 0L
        var at = 0L
        while (currentCoroutineContext().isActive) {
            val game = Fg.game
            val barOn = LockStore.bool(this, "sp_on", false) && game == null && !Guard.safe
            val fpsOn = LockStore.bool(this, "g_fps", true)
            val spdOn = LockStore.bool(this, "g_hud_speed", true)
            // The readout is only for a game you can see. A launcher or any other app in front means it is not.
            val front = Fg.pkg
            val gameVisible = game != null && (front == null || front == game || front in games || isTransient(front) || FloatWindows.hasWindow(front))
            val hudOn = gameVisible && !Guard.safe && (fpsOn || spdOn)
            if (!Monitor.screenOn || (!barOn && !hudOn)) {
                statusSpeed?.hide()
                hud?.hide()
                at = 0L
                FpsMeter.reset()
                withTimeoutOrNull(15_000) { speedWake.receive() }
                continue
            }
            val now = SystemClock.elapsedRealtime()
            val nrx = TrafficStats.getTotalRxBytes()
            val ntx = TrafficStats.getTotalTxBytes()
            if (at != 0L && nrx >= 0L && ntx >= 0L) {
                val dt = (now - at) / 1000f
                if (dt > 0.2f) SpeedState.text = speedText(((nrx - rx) / dt).toLong(), ((ntx - tx) / dt).toLong(), LockStore.bool(this, "sp_both", false) && !hudOn)
            }
            rx = nrx; tx = ntx; at = now
            if (barOn) statusSpeed?.show() else statusSpeed?.hide()
            if (hudOn) {
                HudState.showFps = fpsOn
                HudState.showSpeed = spdOn
                hud?.show()
                if (fpsOn && game != null) HudState.fps = withContext(Dispatchers.IO) { FpsMeter.sample(game) } ?: -1
            } else hud?.hide()
            delay(1000)
        }
    }

    /** Wakes the game watch. Without it the watch sleeps, so nothing runs when you are not in a game. */
    private val watchWake = Channel<Unit>(Channel.CONFLATED)

    /**
     * Safety net. While you are in a game the bar is checked every few seconds and comes back if it went missing.
     * Outside games this does nothing and does not wake up at all.
     */
    private suspend fun gameWatch() {
        while (currentCoroutineContext().isActive) {
            if (Monitor.screenOn && Fg.game != null && !Guard.safe) {
                reeval()
                delay(4000)
            } else watchWake.receive()
        }
    }

    /**
     * Call banner and floating call controls. By default they only exist while you are in a game.
     * Anywhere else the normal phone call screen is used.
     */
    private fun syncCallUi() {
        val allowed = Fg.game != null || !LockStore.bool(this, "calls_game_only", true)
        val ringing = Calls.state == TelephonyManager.CALL_STATE_RINGING
        val talking = Calls.state == TelephonyManager.CALL_STATE_OFFHOOK
        if (ringing && allowed && LockStore.bool(this, "calls_banner", true)) banner?.show() else banner?.hide()
        if (talking && allowed && LockStore.bool(this, "calls_mini", true)) callPill?.show() else callPill?.hide()
    }

    private fun skipFg(p: String) = p == "com.android.systemui" || p == "android" || p.endsWith("permissioncontroller")

    /**
     * Second way of seeing which app is open.
     * Accessibility off: usage access is read every 1.5 s, and root is used if usage access is missing too.
     * Accessibility on: only a light backup that catches a game window the accessibility service did not report.
     */
    private suspend fun poller() {
        while (currentCoroutineContext().isActive) {
            val a11y = AshuAccessibility.connected
            val need = LockStore.locked(this).isNotEmpty() || games.isNotEmpty() || Limits.all(this).isNotEmpty()
            if (Monitor.screenOn && need && !a11y) {
                val fg = withContext(Dispatchers.IO) {
                    Usage.foreground(this@MonitorService) ?: if (!Usage.hasAccess(this@MonitorService)) Usage.topByRoot() else null
                }
                if (fg != null && !skipFg(fg)) { Fg.set(fg); pollLock(fg) }
                delay(1500)
            } else if (Monitor.screenOn && a11y && games.isNotEmpty() && Fg.game == null) {
                val fg = withContext(Dispatchers.IO) { Usage.foreground(this@MonitorService) }
                if (fg != null && fg in games) Fg.set(fg)
                delay(6000)
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
            if (n % 10 == 0) withContext(Dispatchers.IO) { GameMode.games(this@MonitorService) }
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
                if (LockStore.bool(this, "calls_auto", false)) scope?.launch {
                    delay(LockStore.int(this@MonitorService, "calls_auto_sec", 3) * 1000L)
                    if (Calls.state == TelephonyManager.CALL_STATE_RINGING) withContext(Dispatchers.IO) { Calls.answer(this@MonitorService) }
                }
            }
            TelephonyManager.CALL_STATE_OFFHOOK -> {
                if (prev != TelephonyManager.CALL_STATE_OFFHOOK) {
                    Calls.startedAt = SystemClock.elapsedRealtime()
                    if (LockStore.bool(this, "calls_speaker", false)) scope?.launch { delay(700); Calls.setSpeaker(this@MonitorService, true) }
                }
            }
            else -> {
                if (Calls.speakerForced) Calls.setSpeaker(this, false)
                Calls.muted = false
                Calls.caller = ""
            }
        }
        syncCallUi()
    }

    companion object {
        const val CHANNEL = "monitor"
        const val ID = 1001
        const val REC_ID = 1002
        @Volatile var instance: MonitorService? = null
        val TRANSIENT = setOf("com.android.vending", "com.google.android.gms", "com.google.android.googlequicksearchbox")
    }
}

class BootReceiver : BroadcastReceiver() {
    override fun onReceive(c: Context, i: Intent) {
        if (i.action == Intent.ACTION_BOOT_COMPLETED && LockStore.bool(c, "boot", true)) {
            ContextCompat.startForegroundService(c, Intent(c, MonitorService::class.java))
        }
    }
}
