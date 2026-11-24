/*
 * Ashutool - root toolkit for custom ROMs
 * Copyright (C) 2026 Ashutosh Nishad
 * Telegram: @ashutosh_07x (https://t.me/ashutosh_07x)
 *
 * Free software under the GNU GPL v3.0 or later, with the attribution terms in NOTICE.
 * Keep this header and the author credit in every copy and modified version.
 */

package com.ashu.ashutool

import android.accessibilityservice.AccessibilityService
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.res.Configuration
import android.graphics.PixelFormat
import android.graphics.Rect
import android.graphics.Region
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.os.PowerManager
import android.view.Gravity
import android.view.View
import android.view.ViewTreeObserver
import android.view.WindowManager
import androidx.compose.runtime.snapshotFlow
import androidx.core.content.ContextCompat
import java.lang.reflect.Proxy
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import org.lsposed.hiddenapibypass.HiddenApiBypass

/**
 * Owns the window of the island. It lives in the accessibility service of Ashutool, because a window of that kind is drawn
 * above the status bar.
 *
 * The window is made once and does not change size, so the shape inside it can grow and shrink as smoothly as it likes:
 * resizing a window every frame is what makes most floating islands stutter. A window that is as large as the biggest card
 * would block every touch below it, so the system is told that only the area of the island is touchable (the hidden
 * "touchable region" of a window). When the phone does not allow that, the window is resized to the island instead:
 * small while it is closed, large while it is open.
 */
class IslandHost(private val svc: AccessibilityService) {
    private val overlay = Overlay(svc)
    private val handler = Handler(Looper.getMainLooper())
    private val sources = IslandSources(svc)
    private val media = MediaWatcher(svc)
    private var scope: CoroutineScope? = null
    private var lp: WindowManager.LayoutParams? = null
    private var mediaTries = 0
    private var hidden = false
    private var screenRx: BroadcastReceiver? = null

    /** The area of the island in the window, in px. The system lets touches through everywhere else. */
    private val touch = Rect()
    private var insetsReady = false
    private var insetsProxy: Any? = null
    private var shrinkJob: Runnable? = null

    private val tick = object : Runnable {
        override fun run() {
            refresh()
            handler.postDelayed(this, 1500L)
        }
    }

    val isShown: Boolean get() = overlay.isShown

    fun start() {
        if (scope != null) return
        IslandCfg.load(svc)
        sources.start()
        registerScreen()
        val s = CoroutineScope(SupervisorJob() + Dispatchers.Main)
        scope = s
        updateScreen()
        s.launch { snapshotFlow { Calls.state }.collect { IslandHub.onCall(svc) } }
        s.launch { snapshotFlow { Recorder.running }.collect { islandRecording(it) } }
        s.launch { Monitor.snap.collect { IslandHub.onBattery(svc, it.batt.level, it.batt.charging) } }
        s.launch { snapshotFlow { IslandHub.mainAct() != null }.collect { refresh() } }
        s.launch { snapshotFlow { IslandHub.expanded }.collect { onExpandedChanged(it) } }
        startMedia()
        refresh()
        handler.postDelayed(tick, 1500L)
    }

    fun stop() {
        scope?.cancel()
        scope = null
        handler.removeCallbacksAndMessages(null)
        sources.stop()
        media.stop()
        screenRx?.let { try { svc.unregisterReceiver(it) } catch (_: Exception) {} }
        screenRx = null
        overlay.remove()
        lp = null
        insetsReady = false
        insetsProxy = null
    }

    /** The settings changed, or the screen turned: make the window again. */
    fun relayout() {
        IslandCfg.load(svc)
        updateScreen()
        overlay.remove()
        lp = null
        insetsReady = false
        insetsProxy = null
        refresh()
        startMedia()
    }

    /** Switches the watching of the media sessions on or off. Safe to call at any time. */
    fun startMedia() {
        if (!IslandCfg.on || !IslandCfg.media) { media.stop(); return }
        if (media.start()) { mediaTries = 0; return }
        // The listener may not be allowed yet. Root grants it a moment after the service starts.
        if (mediaTries++ < 6) handler.postDelayed({ startMedia() }, 4000L)
    }

    private fun registerScreen() {
        val r = object : BroadcastReceiver() {
            override fun onReceive(c: Context, i: Intent) {
                hidden = i.action == Intent.ACTION_SCREEN_OFF
                if (hidden) {
                    overlay.remove()
                    lp = null
                    insetsReady = false
                } else {
                    // Let the system finish restoring the display/window state first.
                    handler.postDelayed({ refresh() }, 250L)
                }
            }
        }
        val f = IntentFilter().apply {
            addAction(Intent.ACTION_SCREEN_OFF)
            addAction(Intent.ACTION_SCREEN_ON)
        }
        try { ContextCompat.registerReceiver(svc, r, f, ContextCompat.RECEIVER_NOT_EXPORTED); screenRx = r } catch (_: Exception) {}
    }

    private fun updateScreen() {
        val (sw, _) = screenSize(svc)
        IslandHub.screenWdp = (sw / svc.resources.displayMetrics.density).toInt()
    }

    private fun allowedNow(): Boolean {
        // Do not rely on MonitorService's cached screen flag here. The accessibility
        // service can start before MonitorService (or survive its restart), leaving
        // Monitor.screenOn stale and preventing the island window from being added.
        val interactive = try {
            svc.getSystemService(PowerManager::class.java).isInteractive
        } catch (_: Exception) {
            Monitor.screenOn
        }
        if (hidden || !interactive || !IslandCfg.on) return false
        if (!IslandCfg.games && Fg.game != null) return false
        if (!IslandCfg.landscape && svc.resources.configuration.orientation == Configuration.ORIENTATION_LANDSCAPE) return false
        if (!IslandCfg.idle && IslandHub.mainAct() == null) return false
        return true
    }

    /** Shows, hides or adjusts the window to match the settings and what is going on. */
    fun refresh() {
        if (!allowedNow()) {
            if (overlay.isShown) { overlay.remove(); lp = null; insetsReady = false }
            return
        }
        if (overlay.isShown) {
            if (!insetsReady) adjustWindow(IslandHub.expanded)
            return
        }
        val p = params()
        overlay.show(p) { IslandRoot { l, t, r, b -> touch.set(l, t, r, b) } }
        lp = p
        val v = overlay.rootView()
        if (v != null) insetsReady = installTouchRegion(v)
        if (!insetsReady) adjustWindow(IslandHub.expanded)
    }

    private fun density() = svc.resources.displayMetrics.density

    private fun params(): WindowManager.LayoutParams {
        val d = density()
        val (sw, sh) = screenSize(svc)
        val scale = IslandCfg.scale / 100f
        val tall = ((IslandCfg.top + 330) * scale * d).toInt().coerceAtMost(sh)
        val p = WindowManager.LayoutParams(
            sw, tall, WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL or
                WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS or WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
            PixelFormat.TRANSLUCENT
        )
        p.gravity = Gravity.TOP or Gravity.CENTER_HORIZONTAL
        p.x = (IslandCfg.x * d).toInt()
        p.y = 0
        if (Build.VERSION.SDK_INT >= 30) p.layoutInDisplayCutoutMode = WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_ALWAYS
        else if (Build.VERSION.SDK_INT >= 28) p.layoutInDisplayCutoutMode = WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES
        return p
    }

    // ---- the touchable region

    /**
     * Tells the system that only [touch] can be touched in this window. Returns false when the phone does not allow it.
     * The region is asked for again at every pass of the layout, and the island reports its bounds at every frame of its
     * animation, so the region follows the island exactly.
     */
    private fun installTouchRegion(view: View): Boolean {
        return try {
            if (Build.VERSION.SDK_INT >= 28) HiddenApiBypass.addHiddenApiExemptions("Landroid/view/ViewTreeObserver")
            val infoClass = Class.forName("android.view.ViewTreeObserver\$InternalInsetsInfo")
            val listenerClass = Class.forName("android.view.ViewTreeObserver\$OnComputeInternalInsetsListener")
            val setInsets = infoClass.getMethod("setTouchableInsets", Int::class.javaPrimitiveType)
            val regionField = infoClass.getField("touchableRegion")
            val proxy = Proxy.newProxyInstance(listenerClass.classLoader, arrayOf(listenerClass)) { _, method, args ->
                if (method.name == "onComputeInternalInsets" && args != null && args.isNotEmpty()) {
                    val info = args[0]
                    // 3 is TOUCHABLE_INSETS_REGION
                    setInsets.invoke(info, 3)
                    val region = regionField.get(info) as Region
                    region.set(touch.left, touch.top, touch.right, touch.bottom)
                }
                null
            }
            val add = ViewTreeObserver::class.java.getMethod("addOnComputeInternalInsetsListener", listenerClass)
            add.invoke(view.viewTreeObserver, proxy)
            insetsProxy = proxy
            true
        } catch (_: Throwable) {
            false
        }
    }

    // ---- when the phone does not allow the touchable region: the window follows the island

    private fun onExpandedChanged(expanded: Boolean) {
        if (insetsReady) return
        shrinkJob?.let { handler.removeCallbacks(it) }
        if (expanded) {
            adjustWindow(true)
        } else {
            // Let the card shrink first, then the window
            val job = Runnable { adjustWindow(false) }
            shrinkJob = job
            handler.postDelayed(job, 500L)
        }
    }

    private fun adjustWindow(large: Boolean) {
        val p = lp ?: return
        val d = density()
        val (sw, sh) = screenSize(svc)
        val scale = IslandCfg.scale / 100f
        if (large) {
            p.width = sw
            p.height = ((IslandCfg.top + 330) * scale * d).toInt().coerceAtMost(sh)
        } else {
            p.width = (((IslandCfg.w + 130) * scale + 24f) * d).toInt().coerceAtMost(sw)
            p.height = ((IslandCfg.top + IslandCfg.h * scale + 24f) * d).toInt()
        }
        overlay.update(p)
    }
}
