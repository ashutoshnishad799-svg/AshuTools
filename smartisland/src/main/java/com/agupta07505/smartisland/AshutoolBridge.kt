/*
 * Ashutool - root toolkit for custom ROMs
 * Copyright (C) 2026 Ashutosh Nishad
 * Telegram: @ashutosh_07x (https://t.me/ashutosh_07x)
 *
 * Free software under the GNU GPL v3.0 or later, with the attribution terms in NOTICE.
 *
 * This file is new. It connects Ashutool to the Smart Island code in this module, and replaces SmartIslandApp.kt of
 * Smart Island, which cannot exist here because Ashutool's own Application class is the Hilt application.
 * onAppCreate() carries the three steps of SmartIslandApp.onCreate() of Smart Island 7.6.0
 * (Smart Island (2026), (c) Animesh Gupta, github.com/agupta07505, GNU GPL v3.0): restore the log, install the
 * crash logger, exempt the hidden APIs.
 */

package com.agupta07505.smartisland

import android.app.Application
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.os.Build
import android.provider.Settings
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.agupta07505.smartisland.data.SmartIslandSettingsRepository
import com.agupta07505.smartisland.di.AshutoolEntryPoint
import com.agupta07505.smartisland.service.SmartIslandNotificationListenerService
import com.agupta07505.smartisland.service.SmartIslandOverlayService
import com.agupta07505.smartisland.util.AppLogRecorder
import com.agupta07505.smartisland.util.ShizukuManager
import dagger.hilt.android.EntryPointAccessors
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking

/** The only door Ashutool uses into the Smart Island module. */
object AshutoolBridge {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    /** True when the master switch of the island (the "Enabled" setting of Smart Island) is on. Compose state: Ashutool's screens follow it. */
    var islandEnabled by mutableStateOf(false)
        private set

    private fun repo(ctx: Context): SmartIslandSettingsRepository =
        EntryPointAccessors.fromApplication(ctx.applicationContext, AshutoolEntryPoint::class.java).settingsRepository()

    /** Call once from Application.onCreate(), after the crash log of Ashutool is installed. */
    fun onAppCreate(app: Application) {
        runCatching { AppLogRecorder.restore(app) }
        runCatching {
            val previous = Thread.getDefaultUncaughtExceptionHandler()
            Thread.setDefaultUncaughtExceptionHandler { thread, error ->
                runCatching { AppLogRecorder.recordCrash(thread, error) }
                previous?.uncaughtException(thread, error)
            }
        }
        bypassHiddenApis()
        // Keeps islandEnabled up to date with the setting, whoever changes it (Smart Island's own screen, or Ashutool)
        scope.launch { runCatching { repo(app).settings.map { it.enabled }.distinctUntilChanged().collect { islandEnabled = it } } }
    }

    /** Blocking, call off the main thread. Switches the island on or off with the same switch as the Smart Island screen. */
    fun setIslandEnabled(ctx: Context, on: Boolean) {
        runBlocking { runCatching { repo(ctx).setEnabled(on) } }
    }

    /** The light edge of the island: [style] 0 off, 1 accent, 2 aurora, 3 rainbow, 4 flow; [strength] 0 to 100. */
    fun setEdge(style: Int, strength: Int) {
        com.agupta07505.smartisland.ui.HostEdge.configure(style, strength)
    }

    /** Blocking, call off the main thread. The value stored right now. */
    fun readIslandEnabled(ctx: Context): Boolean =
        runBlocking { runCatching { repo(ctx).settings.first().enabled }.getOrDefault(false) }

    /**
     * Blocking, call off the main thread. Makes the island's own accent colours match the host: the music wave, the
     * notification dot and the tint of the open cards. [rgb] is 0xRRGGBB.
     */
    fun syncAccent(ctx: Context, rgb: Int) {
        val argb = 0xFF000000L or (rgb.toLong() and 0xFFFFFFL)
        runBlocking {
            runCatching {
                val r = repo(ctx)
                r.setMusicVisualizerColor(argb)
                r.setNotificationDotColor(argb)
                r.setExpandedGradientColor(argb)
                r.setExpandedGradientEnabled(true)
            }
        }
    }

    private fun bypassHiddenApis() {
        // Hidden API restrictions only exist on API 28+
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.P) return
        runCatching { org.lsposed.hiddenapibypass.HiddenApiBypass.addHiddenApiExemptions("L") }
    }

    /** Component of the overlay, in the form the system settings use: package/class. */
    fun overlayComponent(ctx: Context): String = "${ctx.packageName}/${SmartIslandOverlayService::class.java.name}"

    /** Component of the notification listener of the island. */
    fun listenerComponent(ctx: Context): String = "${ctx.packageName}/${SmartIslandNotificationListenerService::class.java.name}"

    /** True when the accessibility service that draws the island is switched on. */
    fun isOverlayEnabled(ctx: Context): Boolean {
        val expected = ComponentName(ctx, SmartIslandOverlayService::class.java)
        val enabled = runCatching { Settings.Secure.getString(ctx.contentResolver, Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES) }.getOrNull()
            ?: return false
        return enabled.split(":").any { ComponentName.unflattenFromString(it) == expected }
    }

    /** True when the island's own notification listener is switched on (not the listener of Ashutool). */
    fun isListenerEnabled(ctx: Context): Boolean {
        val expected = ComponentName(ctx, SmartIslandNotificationListenerService::class.java)
        val enabled = runCatching { Settings.Secure.getString(ctx.contentResolver, "enabled_notification_listeners") }.getOrNull()
            ?: return false
        return enabled.split(":").any { ComponentName.unflattenFromString(it) == expected }
    }

    fun hasOverlayPermission(ctx: Context): Boolean = Settings.canDrawOverlays(ctx)

    /**
     * The shell commands Smart Island's own one-tap setup runs through Shizuku: the overlay permission, accessibility
     * and notification access, sensitive notification text, Bluetooth, battery whitelist and, with [all], the optional ones.
     * Ashutool runs them through root instead.
     */
    fun setupCommands(ctx: Context, all: Boolean = true): List<String> {
        val pkg = ctx.packageName
        val overlay = overlayComponent(ctx)
        val listener = listenerComponent(ctx)
        return ShizukuManager.setupCommands(
            scope = if (all) ShizukuManager.SetupScope.All else ShizukuManager.SetupScope.Necessary,
            pkg = pkg,
            accessibilityClass = overlay,
            notificationClass = listener,
            mergedAccessibilityServices = ShizukuManager.getMergedAccessibilityServices(ctx, overlay),
            mergedNotificationListeners = ShizukuManager.getMergedNotificationListeners(ctx, listener)
        )
    }

    /** Opens the full Smart Island settings, the original screen. */
    fun openSettings(ctx: Context, section: String? = null) {
        val i = Intent(ctx, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        if (section != null) i.putExtra(com.agupta07505.smartisland.util.UtilityActions.EXTRA_OPEN_SECTION, section)
        ctx.startActivity(i)
    }
}
