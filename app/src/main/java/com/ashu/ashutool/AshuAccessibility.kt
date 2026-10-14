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
import android.graphics.PixelFormat
import android.graphics.Rect
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.view.KeyEvent
import android.view.WindowManager
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import android.view.inputmethod.InputMethodManager
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.core.content.ContextCompat

/**
 * Instant app lock, recents blur, time limits, reels blocker, dialer code and volume keys.
 * Extra event types (content changes, text changes) are switched on only while the screen that needs them is in front,
 * so the service stays cheap the rest of the time.
 */
class AshuAccessibility : AccessibilityService() {
    class Ids(val viewer: List<String>, val tab: List<String>, val home: List<String>)

    companion object {
        var connected by mutableStateOf(false)
        @Volatile var instance: AshuAccessibility? = null
        const val IG = "com.instagram.android"
        const val YT = "com.google.android.youtube"
        val REELS = mapOf(
            IG to Ids(listOf("clips_viewer_view_pager", "clips_viewer_video_layout", "clips_swipe_refresh_container", "reel_viewer_root"), listOf("clips_tab"), listOf("feed_tab")),
            YT to Ids(listOf("reel_player_page_container", "reel_recycler", "shorts_container"), emptyList(), emptyList())
        )
    }

    private val handler = Handler(Looper.getMainLooper())
    private val overlay by lazy { Overlay(this) }
    private val guard by lazy { RecentsGuard(this) }
    private var shownFor: String? = null
    private var leftAt = 0L
    private val vol = ArrayList<Pair<Int, Long>>()
    private var imePkgs: Set<String> = emptySet()
    private var imeAt = 0L
    private var scanPosted = false
    private var labels: List<String> = emptyList()
    private var labelAt = 0L
    private var reelAt = 0L
    private var reelBlockAt = 0L
    private var lastNonDialer: String? = null

    private val screenOff = object : BroadcastReceiver() {
        override fun onReceive(c: Context, i: Intent) {
            if (LockStore.bool(c, "relock_off", true)) { LockStore.unlocked = null; leftAt = 0L }
        }
    }

    override fun onServiceConnected() {
        if (Integrity.tampered(this)) { disableSelf(); return }
        connected = true
        instance = this
        Palette.load(this)
        ContextCompat.registerReceiver(this, screenOff, IntentFilter(Intent.ACTION_SCREEN_OFF), ContextCompat.RECEIVER_NOT_EXPORTED)
    }

    override fun onAccessibilityEvent(e: AccessibilityEvent) {
        val pkg = e.packageName?.toString() ?: return
        when (e.eventType) {
            AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED -> if (!ignored(pkg)) onForeground(pkg)
            AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED, AccessibilityEvent.TYPE_VIEW_SCROLLED ->
                if (pkg in Safety.homePkgs(this)) scheduleScan() else if (pkg in REELS) scanReels(pkg)
            AccessibilityEvent.TYPE_VIEW_TEXT_CHANGED -> if (pkg in Safety.dialerPkgs(this)) checkDialer(e)
        }
    }

    private fun ignored(pkg: String): Boolean {
        if (pkg == packageName || pkg == "com.android.systemui" || pkg == "android" || pkg.endsWith("permissioncontroller")) return true
        val now = SystemClock.elapsedRealtime()
        if (now - imeAt > 60_000) {
            imeAt = now
            imePkgs = try {
                getSystemService(InputMethodManager::class.java).enabledInputMethodList.map { it.packageName }.toSet()
            } catch (e: Exception) { emptySet() }
        }
        return pkg in imePkgs
    }

    private fun reelOn(pkg: String) = LockStore.bool(this, if (pkg == IG) "reel_ig" else "reel_yt", false)

    /** Turns the extra event types on only for the screen that needs them. */
    private fun modes(pkg: String) {
        val wantContent = (pkg in Safety.homePkgs(this) && LockStore.locked(this).isNotEmpty() && LockStore.bool(this, "recents_guard", true)) ||
            (pkg in REELS && reelOn(pkg))
        val code = LockStore.str(this, "vault_code", "")
        val wantText = pkg in Safety.dialerPkgs(this) && LockStore.bool(this, "vault_dial", true) && code.length in 4..6
        val info = serviceInfo ?: return
        var t = AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED
        if (wantContent) t = t or AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED or AccessibilityEvent.TYPE_VIEW_SCROLLED
        if (wantText) t = t or AccessibilityEvent.TYPE_VIEW_TEXT_CHANGED
        if (info.eventTypes != t) { info.eventTypes = t; serviceInfo = info }
    }

    private fun onForeground(pkg: String) {
        val now = SystemClock.elapsedRealtime()
        val delay = LockStore.int(this, "relock_ms", 0)
        val unl = LockStore.unlocked
        if (unl != null) {
            if (pkg == unl) {
                if (leftAt != 0L && now - leftAt >= delay) LockStore.unlocked = null
                leftAt = 0L
            } else if (leftAt == 0L) leftAt = now
        }
        val dialers = Safety.dialerPkgs(this)
        if (pkg !in dialers) lastNonDialer = pkg
        modes(pkg)
        Fg.set(pkg)
        if (pkg in dialers && LockStore.bool(this, "calls_nofull", false) && Calls.recentActive() &&
            (Fg.game != null || !LockStore.bool(this, "calls_game_only", true))
        ) { restorePrev(); return }
        val needLock = LockStore.hasCred(this) && pkg in LockStore.locked(this) && !Safety.lockForbidden(this, pkg) && LockStore.unlocked != pkg
        if (needLock) showLock(pkg)
        else {
            if (shownFor != null && pkg != shownFor) hideLock()
            if (shownFor == null) checkLimit(pkg)
        }
        if (pkg !in Safety.homePkgs(this)) guard.clear()
    }

    /** Keeps the app you were using in front when a call rings, so the call shows as a banner instead. */
    private fun restorePrev() {
        val p = Fg.game ?: lastNonDialer
        val i = if (p != null) packageManager.getLaunchIntentForPackage(p) else null
        if (i != null) startActivity(i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_REORDER_TO_FRONT))
        else performGlobalAction(GLOBAL_ACTION_HOME)
    }

    // ---- recents guard
    private fun scheduleScan() {
        if (scanPosted) return
        scanPosted = true
        handler.postDelayed({ scanPosted = false; scanRecents() }, 60)
    }

    private fun scanRecents() {
        val locked = LockStore.locked(this)
        if (locked.isEmpty() || !LockStore.bool(this, "recents_guard", true)) { guard.clear(); return }
        val now = SystemClock.elapsedRealtime()
        if (now - labelAt > 30_000) {
            labelAt = now
            val pm = packageManager
            labels = locked.mapNotNull { try { pm.getApplicationLabel(pm.getApplicationInfo(it, 0)).toString() } catch (e: Exception) { null } }
                .filter { it.length >= 2 }
        }
        val root = rootInActiveWindow ?: return
        val dm = resources.displayMetrics
        val minW = dm.widthPixels * 0.30f
        val minH = dm.heightPixels * 0.22f
        val rects = ArrayList<Rect>()
        var visited = 0
        fun walk(n: AccessibilityNodeInfo?) {
            if (n == null || visited > 800 || rects.size >= 6) return
            visited++
            val d = (n.contentDescription ?: n.text)?.toString()
            if (d != null && labels.any { d.startsWith(it, ignoreCase = true) }) {
                val r = Rect()
                n.getBoundsInScreen(r)
                if (r.width() >= minW && r.height() >= minH) rects.add(r)
            }
            for (i in 0 until n.childCount) walk(n.getChild(i))
        }
        walk(root)
        if (rects.isEmpty()) guard.clear() else guard.update(rects)
    }

    // ---- lock and limit overlays
    private fun blurSupported(): Boolean =
        Build.VERSION.SDK_INT >= 31 && getSystemService(WindowManager::class.java).isCrossWindowBlurEnabled

    private fun lockParams(blur: Boolean): WindowManager.LayoutParams {
        val lp = WindowManager.LayoutParams(
            WindowManager.LayoutParams.MATCH_PARENT, WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
            WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
            PixelFormat.TRANSLUCENT
        )
        if (blur && Build.VERSION.SDK_INT >= 31) {
            lp.flags = lp.flags or WindowManager.LayoutParams.FLAG_BLUR_BEHIND
            lp.blurBehindRadius = 70
        }
        if (Build.VERSION.SDK_INT >= 28) lp.layoutInDisplayCutoutMode = WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES
        return lp
    }

    private fun showLock(pkg: String) {
        if (shownFor == pkg && overlay.isShown) return
        shownFor = pkg
        val blur = LockStore.bool(this, "lock_blur", true) && blurSupported()
        overlay.show(lockParams(blur)) {
            LockPanel(
                pkg, if (blur) 0.30f else 0.95f,
                onOk = { LockStore.unlocked = pkg; leftAt = 0L; hideLock(); checkLimit(pkg) },
                onCancel = { performGlobalAction(GLOBAL_ACTION_HOME); hideLock() }
            )
        }
    }

    private fun hideLock() {
        shownFor = null
        overlay.remove()
    }

    private fun checkLimit(pkg: String) {
        if (Limits.all(this)[pkg] == null || !Usage.hasAccess(this)) return
        val used = Usage.todayMs(this, pkg)
        if (Limits.exceeded(this, pkg, used)) showLimit(pkg, used)
    }

    /** Called by the service every few seconds while a limited app is in front. */
    fun enforceLimit() {
        val p = Fg.pkg ?: return
        if (shownFor == null) checkLimit(p)
    }

    private fun showLimit(pkg: String, usedMs: Long) {
        if (shownFor == pkg && overlay.isShown) return
        val lim = Limits.all(this)[pkg] ?: return
        shownFor = pkg
        overlay.show(lockParams(false)) {
            LimitPanel(
                pkg, (usedMs / 60000).toInt(), lim + Limits.bonus(this, pkg), 0.96f,
                onHome = { performGlobalAction(GLOBAL_ACTION_HOME); hideLock() },
                onBonus = { Limits.addBonus(this, pkg, 5); hideLock() }
            )
        }
    }

    // ---- reels blocker
    private fun scanReels(pkg: String) {
        val now = SystemClock.elapsedRealtime()
        if (now - reelAt < 700) return
        val dt = (now - reelAt).coerceAtMost(3000L) / 1000f
        reelAt = now
        val ids = REELS[pkg] ?: return
        val root = rootInActiveWindow ?: return
        var visible = ids.viewer.any { root.findAccessibilityNodeInfosByViewId("$pkg:id/$it").isNotEmpty() }
        if (!visible) visible = ids.tab.any { t -> root.findAccessibilityNodeInfosByViewId("$pkg:id/$t").any { it.isSelected } }
        if (!visible) return
        if (LockStore.int(this, "reel_mode", 0) == 1) {
            if (ReelStats.add(this, dt) < LockStore.int(this, "reel_limit", 15) * 60) return
        }
        if (now - reelBlockAt < 1500) return
        reelBlockAt = now
        var clicked = false
        for (h in ids.home) {
            val n = root.findAccessibilityNodeInfosByViewId("$pkg:id/$h").firstOrNull() ?: continue
            clicked = n.performAction(AccessibilityNodeInfo.ACTION_CLICK) || n.parent?.performAction(AccessibilityNodeInfo.ACTION_CLICK) == true
            if (clicked) break
        }
        if (!clicked) performGlobalAction(GLOBAL_ACTION_BACK)
        toast(this, "Reels blocked")
    }

    // ---- dialer code typed on the pad opens the vault without pressing call
    private fun checkDialer(e: AccessibilityEvent) {
        val code = LockStore.str(this, "vault_code", "")
        if (code.length !in 4..6 || !LockStore.bool(this, "vault_dial", true)) return
        if (e.text.joinToString("").replace(" ", "") == "*#*#$code#*#*") {
            startActivity(Intent(this, VaultActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        }
    }

    override fun onKeyEvent(e: KeyEvent): Boolean {
        if (shownFor != null && e.keyCode == KeyEvent.KEYCODE_BACK) {
            if (e.action == KeyEvent.ACTION_UP) { performGlobalAction(GLOBAL_ACTION_HOME); hideLock() }
            return true
        }
        val code = e.keyCode
        if (e.action == KeyEvent.ACTION_DOWN && e.repeatCount == 0 &&
            (code == KeyEvent.KEYCODE_VOLUME_UP || code == KeyEvent.KEYCODE_VOLUME_DOWN) && LockStore.bool(this, "vault_vol", false)
        ) {
            val now = SystemClock.elapsedRealtime()
            vol.removeAll { now - it.second > 2500 }
            vol.add(code to now)
            while (vol.size > 3) vol.removeAt(0)
            if (vol.size == 3 && vol[0].first == KeyEvent.KEYCODE_VOLUME_UP && vol[1].first == KeyEvent.KEYCODE_VOLUME_UP && vol[2].first == KeyEvent.KEYCODE_VOLUME_DOWN) {
                vol.clear()
                startActivity(Intent(this, VaultActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
            }
        }
        return false
    }

    override fun onInterrupt() {}

    override fun onUnbind(intent: Intent?): Boolean {
        connected = false
        instance = null
        hideLock()
        guard.clear()
        try { unregisterReceiver(screenOff) } catch (_: Exception) {}
        return super.onUnbind(intent)
    }
}
