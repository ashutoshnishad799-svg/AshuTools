/*
 * Ashutool - root toolkit for custom ROMs
 * Copyright (C) 2026 Ashutosh Nishad
 * Telegram: @ashutosh_07x (https://t.me/ashutosh_07x)
 *
 * Free software under the GNU GPL v3.0 or later, with the attribution terms in NOTICE.
 * Keep this header and the author credit in every copy and modified version.
 */

package com.ashu.ashutool

import android.content.Context
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.service.quicksettings.Tile
import android.service.quicksettings.TileService
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.*

/** One switch: how to read its state and how to change it. Everything goes through root. */
class Tg(
    val id: String,
    val label: String,
    val icon: ImageVector,
    /** true on, false off, null when the phone does not say. */
    val read: (Context) -> Boolean?,
    val write: (Context, Boolean) -> Boolean
)

/**
 * Every quick switch of the app. The cards in the app and the tiles in the quick settings panel both use this one list,
 * so a switch works the same way in both places and is fixed in one place.
 */
object Toggles {
    private fun setting(ns: String, key: String): String? =
        Root.run("settings get $ns $key").firstOrNull()?.trim()?.takeIf { it.isNotEmpty() && it != "null" }

    private fun flag(v: Boolean) = if (v) 1 else 0

    val all: List<Tg> = listOf(
        Tg("net5g", "5G", Icons.Outlined.SignalCellularAlt, { NetMode.query(it) }, { c, v -> NetMode.set(c, v).isEmpty() }),
        Tg("data", "Mobile data", Icons.Outlined.DataUsage, { setting("global", "mobile_data")?.let { it == "1" } },
            { _, v -> Root.ok("svc data ${if (v) "enable" else "disable"}") }),
        Tg("wifi", "Wi-Fi", Icons.Outlined.Wifi, { setting("global", "wifi_on")?.let { it != "0" } },
            { _, v -> Root.ok("svc wifi ${if (v) "enable" else "disable"}") }),
        Tg("bt", "Bluetooth", Icons.Outlined.Bluetooth, { setting("global", "bluetooth_on")?.let { it == "1" } },
            { _, v ->
                val w = if (v) "enable" else "disable"
                Root.ok("cmd bluetooth_manager $w") || Root.ok("svc bluetooth $w")
            }),
        Tg("air", "Airplane", Icons.Outlined.AirplanemodeActive, { setting("global", "airplane_mode_on")?.let { it == "1" } },
            { _, v ->
                Root.ok("cmd connectivity airplane-mode ${if (v) "enable" else "disable"}") ||
                    Root.ok("settings put global airplane_mode_on ${flag(v)}", "am broadcast -a android.intent.action.AIRPLANE_MODE --ez state $v")
            }),
        Tg("loc", "Location", Icons.Outlined.LocationOn,
            {
                when (Root.run("cmd location is-location-enabled").firstOrNull()?.trim()) {
                    "true" -> true
                    "false" -> false
                    else -> setting("secure", "location_mode")?.let { it != "0" }
                }
            },
            { _, v -> Root.ok("cmd location set-location-enabled $v") || Root.ok("settings put secure location_mode ${if (v) 3 else 0}") }),
        Tg("dark", "Dark mode", Icons.Outlined.DarkMode,
            { Root.run("cmd uimode night").firstOrNull()?.let { it.contains("yes", ignoreCase = true) } },
            { _, v -> Root.ok("cmd uimode night ${if (v) "yes" else "no"}") }),
        Tg("touch", "Touches", Icons.Outlined.TouchApp, { setting("system", "show_touches")?.let { it == "1" } },
            { _, v -> Root.ok("settings put system show_touches ${flag(v)}") }),
        Tg("anim", "No animations", Icons.Outlined.Animation, { setting("global", "animator_duration_scale")?.toFloatOrNull()?.let { it == 0f } },
            { _, v ->
                val x = if (v) "0" else "1"
                Root.ok("settings put global window_animation_scale $x", "settings put global transition_animation_scale $x", "settings put global animator_duration_scale $x")
            }),
        Tg("awake", "Stay awake", Icons.Outlined.Visibility, { setting("global", "stay_on_while_plugged_in")?.let { it != "0" } },
            { _, v -> Root.ok("settings put global stay_on_while_plugged_in ${if (v) 3 else 0}") }),
        Tg("saver", "Battery saver", Icons.Outlined.BatterySaver, { setting("global", "low_power")?.let { it == "1" } },
            { _, v -> Root.ok("settings put global low_power ${flag(v)}") }),
        Tg("gamebar", "Game bar", Icons.Outlined.SportsEsports, { LockStore.bool(it, "g_sidebar", true) },
            { c, v ->
                LockStore.setBool(c, "g_sidebar", v)
                MonitorService.instance?.recheck()
                true
            }),
        Tg("fps", "FPS meter", Icons.Outlined.Speed, { LockStore.bool(it, "g_fps", true) },
            { c, v ->
                LockStore.setBool(c, "g_fps", v)
                MonitorService.instance?.wakeSpeed()
                true
            }),
        Tg("rec", "Screen record", Icons.Outlined.Videocam, { Recorder.running },
            { _, v ->
                if (v) {
                    // The panel must be gone first, or the recording starts with the panel in it
                    Root.run("cmd statusbar collapse")
                    Thread.sleep(700)
                    Recorder.start()
                } else Recorder.stop()
            })
    )

    fun find(id: String): Tg? = all.firstOrNull { it.id == id }
}

/**
 * Base of every quick settings tile that flips one switch from [Toggles].
 * What makes it dependable:
 *  - A tile is never greyed out because a read failed. An unknown state shows as off, and a tap still tries.
 *  - The new state is drawn at once, then checked against what the phone really says.
 *  - Nothing runs on the main thread, and a tap while one is still running is ignored.
 */
abstract class ToggleTile : TileService() {
    protected abstract val toggleId: String
    private val main = Handler(Looper.getMainLooper())
    @Volatile private var busy = false

    override fun onStartListening() { refresh() }
    override fun onTileAdded() { refresh() }

    private fun paint(on: Boolean?, note: String? = null) {
        val t = qsTile ?: return
        val tg = Toggles.find(toggleId) ?: return
        t.label = tg.label
        t.state = if (on == true) Tile.STATE_ACTIVE else Tile.STATE_INACTIVE
        if (Build.VERSION.SDK_INT >= 29) t.subtitle = note ?: when (on) { true -> "On"; false -> "Off"; null -> "Tap to switch" }
        t.updateTile()
    }

    private fun refresh() {
        val app = applicationContext
        val id = toggleId
        Thread {
            val v = try { Toggles.find(id)?.read?.invoke(app) } catch (e: Exception) { null }
            main.post { paint(v) }
        }.start()
    }

    override fun onClick() {
        if (busy) return
        val app = applicationContext
        val tg = Toggles.find(toggleId) ?: return
        val was = qsTile?.state == Tile.STATE_ACTIVE
        busy = true
        paint(!was)
        Thread {
            var ok = false
            var real: Boolean? = null
            try {
                if (Root.isGranted()) {
                    ok = tg.write(app, !was)
                    real = tg.read(app)
                }
            } catch (_: Exception) {}
            busy = false
            main.post {
                // What the phone says wins. If it says nothing, believe the command.
                paint(real ?: (if (ok) !was else was), if (ok) null else "Failed")
                if (!ok) toast(app, "${tg.label} did not change. Is root allowed?")
            }
        }.start()
    }
}

/** The 5G / 4G tile. Same name as before, so tiles users already added keep working. */
class NetTileService : ToggleTile() { override val toggleId = "net5g" }
class DataTile : ToggleTile() { override val toggleId = "data" }
class WifiTile : ToggleTile() { override val toggleId = "wifi" }
class BluetoothTile : ToggleTile() { override val toggleId = "bt" }
class AirplaneTile : ToggleTile() { override val toggleId = "air" }
class LocationTile : ToggleTile() { override val toggleId = "loc" }
class DarkTile : ToggleTile() { override val toggleId = "dark" }
class TouchTile : ToggleTile() { override val toggleId = "touch" }
class AnimTile : ToggleTile() { override val toggleId = "anim" }
class AwakeTile : ToggleTile() { override val toggleId = "awake" }
class SaverTile : ToggleTile() { override val toggleId = "saver" }
class GameBarTile : ToggleTile() { override val toggleId = "gamebar" }
class FpsTile : ToggleTile() { override val toggleId = "fps" }
class RecordTile : ToggleTile() { override val toggleId = "rec" }
