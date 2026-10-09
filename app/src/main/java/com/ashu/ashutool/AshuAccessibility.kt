package com.ashu.ashutool

import android.accessibilityservice.AccessibilityService
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.graphics.PixelFormat
import android.os.Build
import android.os.SystemClock
import android.view.KeyEvent
import android.view.WindowManager
import android.view.accessibility.AccessibilityEvent
import android.view.inputmethod.InputMethodManager
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.core.content.ContextCompat

/**
 * Instant app lock. The system reports every window change here, so the lock overlay covers a locked app
 * within a frame or two, with no Activity launch delay. It also feeds the foreground app to game mode and
 * app heat, and listens for the volume sequence that opens the vault.
 */
class AshuAccessibility : AccessibilityService() {
    companion object {
        var connected by mutableStateOf(false)
    }

    private val overlay by lazy { Overlay(this) }
    private var shownFor: String? = null
    private var leftAt = 0L
    private val vol = ArrayList<Pair<Int, Long>>()
    private var imePkgs: Set<String> = emptySet()
    private var imeAt = 0L

    private val screenOff = object : BroadcastReceiver() {
        override fun onReceive(c: Context, i: Intent) {
            if (LockStore.bool(c, "relock_off", true)) { LockStore.unlocked = null; leftAt = 0L }
        }
    }

    override fun onServiceConnected() {
        connected = true
        Palette.load(this)
        ContextCompat.registerReceiver(this, screenOff, IntentFilter(Intent.ACTION_SCREEN_OFF), ContextCompat.RECEIVER_NOT_EXPORTED)
    }

    override fun onAccessibilityEvent(e: AccessibilityEvent) {
        if (e.eventType != AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED) return
        val pkg = e.packageName?.toString() ?: return
        if (ignored(pkg)) return
        onForeground(pkg)
    }

    /** Own windows, system UI, the keyboard and permission popups never count as leaving an app. */
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

    private fun onForeground(pkg: String) {
        Fg.pkg = pkg
        val now = SystemClock.elapsedRealtime()
        val delay = LockStore.int(this, "relock_ms", 0)
        val unl = LockStore.unlocked
        if (unl != null) {
            if (pkg == unl) {
                if (leftAt != 0L && now - leftAt >= delay) LockStore.unlocked = null
                leftAt = 0L
            } else if (leftAt == 0L) leftAt = now
        }
        val locked = LockStore.locked(this)
        if (pkg in locked && !Safety.lockForbidden(this, pkg) && LockStore.unlocked != pkg) showLock(pkg)
        else if (shownFor != null && pkg != shownFor) hideLock()
    }

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
                onOk = { LockStore.unlocked = pkg; leftAt = 0L; hideLock() },
                onCancel = { performGlobalAction(GLOBAL_ACTION_HOME); hideLock() }
            )
        }
    }

    private fun hideLock() {
        shownFor = null
        overlay.remove()
    }

    override fun onKeyEvent(e: KeyEvent): Boolean {
        if (shownFor != null && e.keyCode == KeyEvent.KEYCODE_BACK) {
            if (e.action == KeyEvent.ACTION_UP) { performGlobalAction(GLOBAL_ACTION_HOME); hideLock() }
            return true
        }
        val code = e.keyCode
        if (e.action == KeyEvent.ACTION_DOWN && e.repeatCount == 0 &&
            (code == KeyEvent.KEYCODE_VOLUME_UP || code == KeyEvent.KEYCODE_VOLUME_DOWN) &&
            LockStore.bool(this, "vault_vol", false)
        ) {
            val now = SystemClock.elapsedRealtime()
            vol.removeAll { now - it.second > 2500 }
            vol.add(code to now)
            while (vol.size > 3) vol.removeAt(0)
            if (vol.size == 3 && vol[0].first == KeyEvent.KEYCODE_VOLUME_UP &&
                vol[1].first == KeyEvent.KEYCODE_VOLUME_UP && vol[2].first == KeyEvent.KEYCODE_VOLUME_DOWN
            ) {
                vol.clear()
                startActivity(Intent(this, VaultActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
            }
        }
        return false
    }

    override fun onInterrupt() {}

    override fun onUnbind(intent: Intent?): Boolean {
        connected = false
        hideLock()
        try { unregisterReceiver(screenOff) } catch (_: Exception) {}
        return super.onUnbind(intent)
    }
}
