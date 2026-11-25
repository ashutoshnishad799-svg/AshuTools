/*
 * Ashutool - root toolkit for custom ROMs
 * Copyright (C) 2026 Ashutosh Nishad
 * Telegram: @ashutosh_07x (https://t.me/ashutosh_07x)
 *
 * Free software under the GNU GPL v3.0 or later, with the attribution terms in NOTICE.
 * Keep this header and the author credit in every copy and modified version.
 */

package com.ashu.ashutool

import android.app.Notification
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.graphics.Bitmap
import android.media.AudioManager
import android.media.MediaMetadata
import android.media.session.MediaController
import android.media.session.MediaSessionManager
import android.media.session.PlaybackState
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.service.notification.StatusBarNotification
import android.telephony.TelephonyManager
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Block
import androidx.compose.material.icons.outlined.BatteryAlert
import androidx.compose.material.icons.outlined.BatteryChargingFull
import androidx.compose.material.icons.outlined.BatteryFull
import androidx.compose.material.icons.outlined.Call
import androidx.compose.material.icons.outlined.MusicNote
import androidx.compose.material.icons.outlined.Videocam
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.core.content.ContextCompat

// =====================================================================================================================
// The Dynamic Island of Ashutool, written for Ashutool. A small black shape at the top of the screen that grows into a
// card. The design and the idea of what it shows are inspired by Smart Island by Animesh Gupta (agupta07505, GNU GPL v3.0),
// see the README and the About page. This file is the data side:
//
//   IslandCfg      every setting, as Compose state, so the island changes while you edit it
//   IAct           one thing the island can show: a call, music, charging, recording, a notification, a short message
//   IslandHub      the list of those things, which one is in front, and whether the card is open
//   IslandMedia    what is playing, read from the media sessions
//   IslandSources  charging and sound mode
//
// The window and the drawing are in IslandHost.kt and IslandUi.kt, the settings page is in PagesIsland.kt.
// =====================================================================================================================

enum class IKind { CALL, MEDIA, CHARGE, RECORD, NOTE, FLASH }

/** One thing the island shows. The numbers of a call, of music and of charging are read live while they are drawn. */
class IAct(
    val id: String,
    val kind: IKind,
    /** The one with the higher number is in front. */
    val priority: Int,
    val title: String = "",
    val sub: String = "",
    val icon: ImageVector? = null,
    /** Short text drawn instead of an icon, for example "5G". */
    val glyph: String = "",
    /** The app it belongs to. Its icon is drawn and a long press opens it. */
    val pkg: String = "",
    val accent: Color = Color(0xFF34D399),
    /** SystemClock.elapsedRealtime() at which it goes away by itself. 0 means it stays until it ends. */
    val until: Long = 0L,
    /** What Open does. When null the app of [pkg] is opened. */
    val onOpen: (() -> Unit)? = null
)

object IslandCfg {
    var on by mutableStateOf(true)
    /** Keep the small black shape on screen when nothing is going on. */
    var idle by mutableStateOf(true)
    var w by mutableIntStateOf(120)
    var h by mutableIntStateOf(34)
    var top by mutableIntStateOf(10)
    var x by mutableIntStateOf(0)
    var scale by mutableIntStateOf(100)
    /** How the shape moves: 0 bouncy, 1 smooth, 2 snappy. */
    var feel by mutableIntStateOf(0)
    /** The light edge: 0 off, 1 accent, 2 aurora, 3 rainbow, 4 flow. */
    var edge by mutableIntStateOf(2)
    var edgePwr by mutableIntStateOf(55)
    var hold by mutableIntStateOf(3)
    var flashSec by mutableIntStateOf(3)
    var hapt by mutableStateOf(true)
    /** A see-through look: a dark glass with a light top, instead of plain black. */
    var glass by mutableStateOf(false)
    /** A second thing at the same time is shown as a small round bubble beside the island. */
    var bubble by mutableStateOf(true)
    var call by mutableStateOf(true)
    var media by mutableStateOf(true)
    var charge by mutableStateOf(true)
    var record by mutableStateOf(true)
    var ringer by mutableStateOf(true)
    var notif by mutableStateOf(true)
    var notifText by mutableStateOf(true)
    var games by mutableStateOf(false)
    var landscape by mutableStateOf(false)

    fun load(c: Context) {
        on = LockStore.bool(c, "isl_on", true)
        idle = LockStore.bool(c, "isl_idle2", true)
        w = LockStore.int(c, "isl_w", 120).coerceIn(80, 220)
        h = LockStore.int(c, "isl_h", 34).coerceIn(24, 52)
        top = LockStore.int(c, "isl_top", 10).coerceIn(0, 120)
        x = LockStore.int(c, "isl_x", 0).coerceIn(-160, 160)
        scale = LockStore.int(c, "isl_scale", 100).coerceIn(70, 140)
        feel = LockStore.int(c, "isl_feel", 0).coerceIn(0, 2)
        edge = LockStore.int(c, "isl_edge", 2).coerceIn(0, 4)
        edgePwr = LockStore.int(c, "isl_edge_pwr", 55).coerceIn(0, 100)
        hold = LockStore.int(c, "isl_hold", 3).coerceIn(0, 30)
        flashSec = LockStore.int(c, "isl_flash", 3).coerceIn(1, 10)
        hapt = LockStore.bool(c, "isl_hapt", true)
        glass = LockStore.bool(c, "isl_glass", false)
        bubble = LockStore.bool(c, "isl_bubble", true)
        call = LockStore.bool(c, "isl_call", true)
        media = LockStore.bool(c, "isl_media", true)
        charge = LockStore.bool(c, "isl_charge", true)
        record = LockStore.bool(c, "isl_rec", true)
        ringer = LockStore.bool(c, "isl_ringer", true)
        notif = LockStore.bool(c, "isl_notif", true)
        notifText = LockStore.bool(c, "isl_notif_text", true)
        games = LockStore.bool(c, "isl_games", false)
        landscape = LockStore.bool(c, "isl_land", false)
    }

    fun putInt(c: Context, key: String, v: Int) { LockStore.setInt(c, key, v); load(c) }

    fun putBool(c: Context, key: String, v: Boolean) { LockStore.setBool(c, key, v); load(c) }

    /** Apps whose notifications never show on the island. */
    fun muted(c: Context): Set<String> = LockStore.set(c, "isl_block")

    /** True when the island needs the notification listener: for music and notifications. */
    fun needsListener(c: Context): Boolean = LockStore.bool(c, "isl_on", true) &&
        (LockStore.bool(c, "isl_media", true) || LockStore.bool(c, "isl_notif", true))
}

/** The name of an app as the user knows it, or the package name when it cannot be read. */
fun islandAppName(ctx: Context, pkg: String): String = try {
    val pm = ctx.packageManager
    pm.getApplicationLabel(pm.getApplicationInfo(pkg, 0)).toString()
} catch (e: Exception) { pkg }

fun openApp(ctx: Context, pkg: String) {
    try {
        val i = ctx.packageManager.getLaunchIntentForPackage(pkg) ?: return
        ctx.startActivity(i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    } catch (_: Exception) {}
}

/** What is playing right now. Filled by [MediaWatcher], drawn by the island. */
object IslandMedia {
    var title by mutableStateOf("")
    var artist by mutableStateOf("")
    var art by mutableStateOf<ImageBitmap?>(null)
    var playing by mutableStateOf(false)
    var pkg by mutableStateOf("")
    var durationMs by mutableLongStateOf(0L)
    var controller: MediaController? = null

    fun clear() {
        title = ""; artist = ""; art = null; playing = false; pkg = ""; durationMs = 0L
        controller = null
    }

    fun positionMs(): Long {
        val s = controller?.playbackState ?: return 0L
        if (s.state != PlaybackState.STATE_PLAYING) return s.position.coerceAtLeast(0L)
        val d = SystemClock.elapsedRealtime() - s.lastPositionUpdateTime
        return (s.position + d * s.playbackSpeed).toLong().coerceAtLeast(0L)
    }

    fun toggle() { try { val c = controller ?: return; if (playing) c.transportControls.pause() else c.transportControls.play() } catch (_: Exception) {} }
    fun next() { try { controller?.transportControls?.skipToNext() } catch (_: Exception) {} }
    fun prev() { try { controller?.transportControls?.skipToPrevious() } catch (_: Exception) {} }
    fun seekTo(ms: Long) { try { controller?.transportControls?.seekTo(ms) } catch (_: Exception) {} }

    /** Opens the player: its own screen if it has one, otherwise the app. */
    fun open(ctx: Context) {
        try {
            val pi = controller?.sessionActivity
            if (pi != null) { pi.send(); return }
        } catch (_: Exception) {}
        openApp(ctx, pkg)
    }
}

object IslandHub {
    private val main = Handler(Looper.getMainLooper())

    /** Everything being shown, the one in front first. */
    var acts by mutableStateOf<List<IAct>>(emptyList())
        private set
    var focusId by mutableStateOf("")
    var expanded by mutableStateOf(false)
    var screenWdp by mutableIntStateOf(360)
    /** Counts touches, so the card stays open while you use it. */
    var touch by mutableIntStateOf(0)

    private var lastLevel = -1

    private fun now() = SystemClock.elapsedRealtime()

    fun mainAct(): IAct? = acts.firstOrNull { it.id == focusId } ?: acts.firstOrNull()

    /** The second thing going on, shown as the small bubble. */
    fun sideAct(): IAct? { val m = mainAct(); return acts.firstOrNull { it.id != m?.id } }

    /** The bubble was tapped: it takes the front and the old one becomes the bubble. */
    fun focus(id: String) { main.post { if (acts.any { it.id == id }) { focusId = id; touch++ } } }

    fun upsert(a: IAct) {
        main.post {
            val list = acts.toMutableList()
            val i = list.indexOfFirst { it.id == a.id }
            val isNew = i < 0
            if (isNew) list.add(a) else list[i] = a
            acts = list.sortedByDescending { it.priority }
            val front = acts.firstOrNull { it.id == focusId }
            // A new thing takes the front unless something more important is already there
            if (isNew && (front == null || a.priority >= front.priority)) focusId = a.id
            else if (front == null) focusId = acts.first().id
            if (a.until > 0L) main.postDelayed({ expire(a.id, a.until) }, (a.until - now()).coerceAtLeast(0L) + 30L)
        }
    }

    private fun expire(id: String, until: Long) {
        val cur = acts.firstOrNull { it.id == id } ?: return
        if (cur.until != until) return
        // Never pull a card away from under the finger: look again a little later
        if (expanded && mainAct()?.id == id) { main.postDelayed({ expire(id, until) }, 1500L); return }
        removeNow(id)
    }

    fun remove(id: String) { main.post { removeNow(id) } }

    private fun removeNow(id: String) {
        if (acts.none { it.id == id }) return
        acts = acts.filter { it.id != id }
        if (focusId == id) focusId = acts.firstOrNull()?.id ?: ""
        if (acts.isEmpty()) expanded = false
    }

    /** A swipe on the small shape: show the next (1) or the previous (-1) thing. */
    fun cycle(dir: Int) {
        main.post {
            if (acts.size > 1) {
                val i = acts.indexOfFirst { it.id == mainAct()?.id }.coerceAtLeast(0)
                focusId = acts[(i + dir + acts.size) % acts.size].id
                touch++
            }
        }
    }

    fun toggle() { main.post { if (mainAct() != null) { expanded = !expanded; touch++ } } }
    fun collapse() { main.post { expanded = false } }

    /** A short message that goes away by itself. */
    fun flash(ctx: Context, id: String, title: String, sub: String, icon: ImageVector?, accent: Color, glyph: String = "", sec: Int = IslandCfg.flashSec) {
        if (!IslandCfg.on) return
        upsert(IAct(id, IKind.FLASH, 60, title, sub, icon, glyph, "", accent, now() + sec * 1000L))
    }

    /** 5G or 4G was switched, shown the moment you tap the tile. */
    fun flashNet(ctx: Context, fiveG: Boolean) {
        flash(ctx, "net", if (fiveG) "5G on" else "4G only", "Network", null, Color(0xFF38BDF8), if (fiveG) "5G" else "4G", 2)
    }

    /** The reels blocker says why it closed an app. */
    fun flashBlocked(ctx: Context, what: String, why: String) {
        flash(ctx, "block", what, why, Icons.Outlined.Block, Color(0xFFFF6B8B), "", 3)
    }

    // ---- calls
    fun onCall(ctx: Context) {
        if (!IslandCfg.on || !IslandCfg.call) { remove("call"); return }
        when (Calls.state) {
            TelephonyManager.CALL_STATE_RINGING -> {
                upsert(IAct("call", IKind.CALL, 100, "Incoming call", "", Icons.Outlined.Call, "", "", Color(0xFF34D399)))
                main.post { expanded = true; touch++ }
            }
            TelephonyManager.CALL_STATE_OFFHOOK -> upsert(IAct("call", IKind.CALL, 100, "On call", "", Icons.Outlined.Call, "", "", Color(0xFF34D399)))
            else -> remove("call")
        }
    }

    // ---- battery: "charged" at 100 and a warning at 15
    fun onBattery(ctx: Context, level: Int, charging: Boolean) {
        val prev = lastLevel
        lastLevel = level
        if (prev < 0 || !IslandCfg.charge) return
        if (charging && level >= 100 && prev < 100) flash(ctx, "full", "Fully charged", "100%", Icons.Outlined.BatteryFull, Color(0xFF34D399), "", 5)
        if (!charging && level <= 15 && prev > 15) flash(ctx, "low", "Low battery", "$level%", Icons.Outlined.BatteryAlert, Color(0xFFFFB84D), "", 5)
        if (!charging && level <= 5 && prev > 5) flash(ctx, "low", "Battery very low", "$level%", Icons.Outlined.BatteryAlert, Color(0xFFFF6B8B), "", 6)
    }

    // ---- notifications
    fun onNotification(ctx: Context, sbn: StatusBarNotification) {
        try {
            if (!IslandCfg.on || !IslandCfg.notif) return
            val n = sbn.notification ?: return
            val pkg = sbn.packageName
            if (pkg == ctx.packageName || pkg == "android" || pkg == "com.android.systemui") return
            if (sbn.isOngoing || (n.flags and Notification.FLAG_GROUP_SUMMARY) != 0) return
            val cat = n.category
            if (cat == Notification.CATEGORY_CALL || cat == Notification.CATEGORY_TRANSPORT || cat == Notification.CATEGORY_PROGRESS ||
                cat == Notification.CATEGORY_SERVICE || cat == Notification.CATEGORY_SYSTEM || cat == Notification.CATEGORY_STATUS
            ) return
            if (pkg in Safety.dialerPkgs(ctx)) return
            // The app you are looking at does not need to tell you about itself
            if (pkg == Fg.pkg) return
            if (pkg in IslandCfg.muted(ctx)) return
            val ex = n.extras ?: return
            val title = ex.getCharSequence(Notification.EXTRA_TITLE)?.toString()?.trim().orEmpty()
            val text = ex.getCharSequence(Notification.EXTRA_TEXT)?.toString()?.trim().orEmpty()
            if (title.isEmpty() && text.isEmpty()) return
            val label = islandAppName(ctx, pkg)
            val showText = IslandCfg.notifText
            upsert(
                IAct(
                    "n:$pkg", IKind.NOTE, 55,
                    if (showText) title.ifEmpty { label } else label,
                    if (showText) text else "New notification",
                    null, "", pkg, Color(0xFF60A5FA), now() + IslandCfg.flashSec * 1000L, onOpen = openWith(ctx, n.contentIntent, pkg)
                )
            )
        } catch (_: Exception) {}
    }

    /** What Open does: the screen of the notification if it has one, otherwise the app. */
    private fun openWith(ctx: Context, pi: PendingIntent?, pkg: String): (() -> Unit)? {
        if (pi == null) return null
        return { try { pi.send() } catch (_: Exception) { openApp(ctx, pkg) } }
    }
}

/** Watches the media sessions of every player. It needs the notification listener to be allowed, which root does. */
class MediaWatcher(private val ctx: Context) {
    private val main = Handler(Looper.getMainLooper())
    private val msm = ctx.getSystemService(MediaSessionManager::class.java)
    private val comp = ComponentName(ctx, CallNameService::class.java)
    private val cbs = HashMap<MediaController, MediaController.Callback>()
    private var cur: MediaController? = null
    private var pausedAt = 0L
    private var running = false

    private val sessions = MediaSessionManager.OnActiveSessionsChangedListener { list -> main.post { track(list ?: emptyList()) } }

    fun start(): Boolean {
        if (running) return true
        return try {
            msm.addOnActiveSessionsChangedListener(sessions, comp, main)
            running = true
            track(msm.getActiveSessions(comp))
            true
        } catch (e: Exception) {
            // Not allowed yet. The host tries again a little later.
            running = false
            false
        }
    }

    fun stop() {
        try { msm.removeOnActiveSessionsChangedListener(sessions) } catch (_: Exception) {}
        for ((c, cb) in cbs) try { c.unregisterCallback(cb) } catch (_: Exception) {}
        cbs.clear()
        cur = null
        running = false
        IslandMedia.clear()
        IslandHub.remove("media")
    }

    private fun track(list: List<MediaController>) {
        val gone = cbs.keys.filter { k -> list.none { it.sessionToken == k.sessionToken } }
        for (k in gone) { cbs.remove(k)?.let { try { k.unregisterCallback(it) } catch (_: Exception) {} } }
        for (c in list) {
            if (cbs.keys.any { it.sessionToken == c.sessionToken }) continue
            val cb = object : MediaController.Callback() {
                override fun onPlaybackStateChanged(state: PlaybackState?) { refresh() }
                override fun onMetadataChanged(metadata: MediaMetadata?) { refresh() }
                override fun onSessionDestroyed() {
                    main.post { track(try { msm.getActiveSessions(comp) } catch (e: Exception) { emptyList() }) }
                }
            }
            try { c.registerCallback(cb, main); cbs[c] = cb } catch (_: Exception) {}
        }
        refresh()
    }

    private fun isPlaying(c: MediaController): Boolean {
        val s = c.playbackState?.state
        return s == PlaybackState.STATE_PLAYING || s == PlaybackState.STATE_BUFFERING
    }

    private fun refresh() {
        if (!IslandCfg.media) { IslandMedia.clear(); IslandHub.remove("media"); return }
        val all = cbs.keys.toList()
        val playing = all.firstOrNull { isPlaying(it) }
        val holdMs = 20_000L
        var chosen: MediaController? = playing
        if (playing != null) pausedAt = 0L
        else {
            val c = cur
            chosen = if (c != null && all.any { it.sessionToken == c.sessionToken } && c.playbackState != null) c else null
            if (chosen != null) {
                if (pausedAt == 0L) pausedAt = SystemClock.elapsedRealtime()
                if (SystemClock.elapsedRealtime() - pausedAt > holdMs) chosen = null
                else main.postDelayed({ refresh() }, holdMs - (SystemClock.elapsedRealtime() - pausedAt) + 400L)
            }
        }
        cur = chosen
        if (chosen == null) { IslandMedia.clear(); IslandHub.remove("media"); return }
        val md = chosen.metadata
        val title = md?.getString(MediaMetadata.METADATA_KEY_TITLE) ?: md?.getString(MediaMetadata.METADATA_KEY_DISPLAY_TITLE) ?: ""
        val artist = md?.getString(MediaMetadata.METADATA_KEY_ARTIST) ?: md?.getString(MediaMetadata.METADATA_KEY_ALBUM_ARTIST)
            ?: md?.getString(MediaMetadata.METADATA_KEY_DISPLAY_SUBTITLE) ?: ""
        val bmp: Bitmap? = md?.getBitmap(MediaMetadata.METADATA_KEY_ALBUM_ART) ?: md?.getBitmap(MediaMetadata.METADATA_KEY_ART)
            ?: md?.getBitmap(MediaMetadata.METADATA_KEY_DISPLAY_ICON)
        var accent = Color(0xFFFF5C8A)
        var art: ImageBitmap? = null
        if (bmp != null && !bmp.isRecycled) {
            try {
                val small = Bitmap.createScaledBitmap(bmp, 160, 160, true)
                art = small.asImageBitmap()
                val one = Bitmap.createScaledBitmap(bmp, 1, 1, true).getPixel(0, 0)
                accent = liven(Color(one))
            } catch (_: Exception) {}
        }
        IslandMedia.controller = chosen
        IslandMedia.title = title
        IslandMedia.artist = artist
        IslandMedia.art = art
        IslandMedia.playing = isPlaying(chosen)
        IslandMedia.pkg = chosen.packageName ?: ""
        IslandMedia.durationMs = md?.getLong(MediaMetadata.METADATA_KEY_DURATION) ?: 0L
        IslandHub.upsert(IAct("media", IKind.MEDIA, 50, title.ifEmpty { "Playing" }, artist, Icons.Outlined.MusicNote, "", IslandMedia.pkg, accent))
    }

    /** Makes the average colour of a cover bright enough to glow on black. */
    private fun liven(c: Color): Color {
        val mx = maxOf(c.red, c.green, c.blue).coerceAtLeast(0.01f)
        val k = (0.85f / mx).coerceAtMost(3f)
        return Color((c.red * k).coerceIn(0f, 1f), (c.green * k).coerceIn(0f, 1f), (c.blue * k).coerceIn(0f, 1f), 1f)
    }
}

/** Charging and sound mode. Kept apart from the window so it works whether or not the island is on screen. */
class IslandSources(private val ctx: Context) {
    private var rx: BroadcastReceiver? = null

    fun start() {
        if (rx != null) return
        val r = object : BroadcastReceiver() {
            override fun onReceive(c: Context, i: Intent) {
                try {
                    if (!IslandCfg.on) return
                    // Some of these are sticky: the system sends the current state when we register. That is not news.
                    if (isInitialStickyBroadcast) return
                    when (i.action) {
                        Intent.ACTION_POWER_CONNECTED -> if (IslandCfg.charge) {
                            IslandHub.upsert(
                                IAct("charge", IKind.CHARGE, 30, "Charging", "", Icons.Outlined.BatteryChargingFull, "", "", Color(0xFF34D399), SystemClock.elapsedRealtime() + 5000L)
                            )
                        }
                        Intent.ACTION_POWER_DISCONNECTED -> IslandHub.remove("charge")
                        AudioManager.RINGER_MODE_CHANGED_ACTION -> if (IslandCfg.ringer) {
                            val m = Ringer.mode(c)
                            val col = when (m) { Ringer.SILENT -> Color(0xFFFF6B8B); Ringer.VIBRATE -> Color(0xFFFFB84D); else -> Color(0xFF34D399) }
                            IslandHub.flash(c, "ringer", Ringer.label(m), "Sound mode", Ringer.icon(m), col, "", 2)
                        }
                    }
                } catch (_: Exception) {}
            }
        }
        val f = IntentFilter().apply {
            addAction(Intent.ACTION_POWER_CONNECTED)
            addAction(Intent.ACTION_POWER_DISCONNECTED)
            addAction(AudioManager.RINGER_MODE_CHANGED_ACTION)
        }
        try { ContextCompat.registerReceiver(ctx, r, f, ContextCompat.RECEIVER_NOT_EXPORTED); rx = r } catch (_: Exception) {}
    }

    fun stop() {
        rx?.let { try { ctx.unregisterReceiver(it) } catch (_: Exception) {} }
        rx = null
    }
}

/** What Ashutool shows on the island for a recording that runs. */
fun islandRecording(on: Boolean) {
    if (on && IslandCfg.record) {
        IslandHub.upsert(IAct("rec", IKind.RECORD, 40, "Recording", "", Icons.Outlined.Videocam, "", "", Color(0xFFFF4D6D)))
    } else IslandHub.remove("rec")
}
