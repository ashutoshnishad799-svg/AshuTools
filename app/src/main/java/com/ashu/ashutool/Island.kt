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
import android.content.BroadcastReceiver
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.graphics.Bitmap
import android.graphics.PixelFormat
import android.media.AudioManager
import android.media.MediaMetadata
import android.media.session.MediaController
import android.media.session.MediaSessionManager
import android.media.session.PlaybackState
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.provider.Settings
import android.service.notification.StatusBarNotification
import android.telephony.TelephonyManager
import android.view.Gravity
import android.view.WindowManager
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.*
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.core.content.ContextCompat
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch

// =====================================================================================================================
// The Dynamic Island: a small black or glass shape at the top of the screen that shows what is going on.
//
//   IslandCfg    every setting, kept as Compose state so the island changes live while you edit it
//   IAct         one thing the island can show (a call, music, charging, recording, a notification, a short message)
//   IslandHub    the list of things being shown, which one has the focus, and whether the island is expanded
//   IslandMedia  what is playing, read from the media sessions of every player (YouTube, Spotify, a browser, ...)
//   IslandSources  listens for charging, ringer changes and recording
//   IslandHost   owns the overlay window, grows it before the island grows and shrinks it after the island shrinks
//
// With two things going on at once (say a call and music) the island shows the more important one as the main
// shape and the other as a small bubble beside it. Tap the bubble and the two change places, with an animation.
// =====================================================================================================================

enum class IKind { CALL, MEDIA, CHARGE, RECORD, FLASH, NOTE }

/** One thing the island shows. Calls, music, charging and recording read their live numbers while they are drawn. */
class IAct(
    val id: String,
    val kind: IKind,
    val priority: Int,
    val title: String = "",
    val sub: String = "",
    val icon: ImageVector? = null,
    /** Short text drawn instead of an icon, for example "5G". */
    val glyph: String = "",
    /** The app this belongs to. Its icon is drawn, and a long press opens it. */
    val pkg: String = "",
    val accent: Color = Color(0xFF34D399),
    /** SystemClock.elapsedRealtime() at which it goes away by itself, or 0 when it stays until it ends. */
    val until: Long = 0L
)

object IslandCfg {
    var on by mutableStateOf(true)
    var idlePill by mutableStateOf(false)
    var skin by mutableIntStateOf(0)
    var fx by mutableIntStateOf(1)
    var shape by mutableIntStateOf(0)
    var round by mutableIntStateOf(100)
    var w by mutableIntStateOf(118)
    var h by mutableIntStateOf(34)
    var top by mutableIntStateOf(8)
    var x by mutableIntStateOf(0)
    var scale by mutableIntStateOf(100)
    var alpha by mutableIntStateOf(100)
    var anim by mutableIntStateOf(0)
    var hold by mutableIntStateOf(7)
    var flashSec by mutableIntStateOf(3)
    var hapt by mutableStateOf(true)
    var tint by mutableStateOf(true)
    var call by mutableStateOf(true)
    var callOpen by mutableStateOf(true)
    var media by mutableStateOf(true)
    var mediaHold by mutableIntStateOf(20)
    var charge by mutableStateOf(true)
    var chargeSec by mutableIntStateOf(5)
    var record by mutableStateOf(true)
    var ringer by mutableStateOf(true)
    var notif by mutableStateOf(true)
    var notifText by mutableStateOf(true)
    var games by mutableStateOf(true)
    var landscape by mutableStateOf(true)

    fun load(c: Context) {
        on = LockStore.bool(c, "isl_on", true)
        idlePill = LockStore.bool(c, "isl_idle", false)
        skin = LockStore.int(c, "isl_skin", 0).coerceIn(0, ISkins.size - 1)
        fx = LockStore.int(c, "isl_fx", 1).coerceIn(0, 4)
        shape = LockStore.int(c, "isl_shape", 0).coerceIn(0, 3)
        round = LockStore.int(c, "isl_round", 100).coerceIn(0, 100)
        w = LockStore.int(c, "isl_w", 118).coerceIn(70, 220)
        h = LockStore.int(c, "isl_h", 34).coerceIn(22, 52)
        top = LockStore.int(c, "isl_top", 8).coerceIn(0, 120)
        x = LockStore.int(c, "isl_x", 0).coerceIn(-160, 160)
        scale = LockStore.int(c, "isl_scale", 100).coerceIn(70, 150)
        alpha = LockStore.int(c, "isl_alpha", 100).coerceIn(40, 100)
        anim = LockStore.int(c, "isl_anim", 0).coerceIn(0, 3)
        hold = LockStore.int(c, "isl_hold", 7).coerceIn(0, 30)
        flashSec = LockStore.int(c, "isl_flash", 3).coerceIn(1, 10)
        hapt = LockStore.bool(c, "isl_hapt", true)
        tint = LockStore.bool(c, "isl_tint", true)
        call = LockStore.bool(c, "isl_call", true)
        callOpen = LockStore.bool(c, "isl_call_open", true)
        media = LockStore.bool(c, "isl_media", true)
        mediaHold = LockStore.int(c, "isl_media_hold", 20).coerceIn(0, 120)
        charge = LockStore.bool(c, "isl_charge", true)
        chargeSec = LockStore.int(c, "isl_charge_sec", 5).coerceIn(2, 20)
        record = LockStore.bool(c, "isl_rec", true)
        ringer = LockStore.bool(c, "isl_ringer", true)
        notif = LockStore.bool(c, "isl_notif", true)
        notifText = LockStore.bool(c, "isl_notif_text", true)
        games = LockStore.bool(c, "isl_games", true)
        landscape = LockStore.bool(c, "isl_land", true)
    }

    /** Saves one number setting and applies it at once. */
    fun putInt(c: Context, key: String, v: Int) { LockStore.setInt(c, key, v); load(c) }

    /** Saves one switch and applies it at once. */
    fun putBool(c: Context, key: String, v: Boolean) { LockStore.setBool(c, key, v); load(c) }

    /** True when the island needs the notification listener: for the caller name, music and notifications. */
    fun needsListener(c: Context): Boolean = LockStore.bool(c, "isl_on", true) &&
        (LockStore.bool(c, "isl_media", true) || LockStore.bool(c, "isl_notif", true) || LockStore.bool(c, "isl_call", true))
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

    /** Opens the player. The session's own screen if it has one, otherwise the app. */
    fun open(ctx: Context) {
        try {
            val pi = controller?.sessionActivity
            if (pi != null) { pi.send(); return }
        } catch (_: Exception) {}
        openApp(ctx, pkg)
    }
}

fun openApp(ctx: Context, pkg: String) {
    try {
        val i = ctx.packageManager.getLaunchIntentForPackage(pkg) ?: return
        ctx.startActivity(i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    } catch (_: Exception) {}
}

object IslandHub {
    private val main = Handler(Looper.getMainLooper())

    /** Everything being shown, most important first. */
    var acts by mutableStateOf<List<IAct>>(emptyList())
        private set
    var focusId by mutableStateOf("")
    var expanded by mutableStateOf(false)
    /** False while the island plays its going-away animation. */
    var shown by mutableStateOf(true)
    var screenWdp by mutableIntStateOf(360)
    /** Counts touches, so the island stays open while you use it and the count restarts every time. */
    var touch by mutableIntStateOf(0)

    private fun now() = SystemClock.elapsedRealtime()

    fun mainAct(): IAct? = acts.firstOrNull { it.id == focusId } ?: acts.firstOrNull()
    fun sideAct(): IAct? { val m = mainAct(); return acts.firstOrNull { it.id != m?.id } }

    fun upsert(a: IAct) {
        main.post {
            val list = acts.toMutableList()
            val i = list.indexOfFirst { it.id == a.id }
            val isNew = i < 0
            if (isNew) list.add(a) else list[i] = a
            acts = list.sortedByDescending { it.priority }
            val f = acts.firstOrNull { it.id == focusId }
            // A new thing takes the focus unless something more important is already there
            if (isNew && (f == null || a.priority >= f.priority)) focusId = a.id
            else if (f == null) focusId = acts.first().id
            if (a.until > 0L) main.postDelayed({ expire(a.id, a.until) }, (a.until - now()).coerceAtLeast(0L) + 30L)
        }
    }

    private fun expire(id: String, until: Long) {
        val cur = acts.firstOrNull { it.id == id } ?: return
        if (cur.until == until) removeNow(id)
    }

    fun remove(id: String) { main.post { removeNow(id) } }

    private fun removeNow(id: String) {
        if (acts.none { it.id == id }) return
        acts = acts.filter { it.id != id }
        if (focusId == id) focusId = acts.firstOrNull()?.id ?: ""
        if (acts.isEmpty()) expanded = false
    }

    /** The other shape took the focus: it becomes the main one and the old main one becomes the bubble. */
    fun focus(id: String) { main.post { if (acts.any { it.id == id }) { focusId = id; touch++ } } }

    /** The bubble was tapped: it becomes the main shape and opens. The old main shape becomes the bubble. */
    fun focusAndOpen(id: String) { main.post { if (acts.any { it.id == id }) { focusId = id; expanded = true; touch++ } } }

    fun toggleExpanded() { main.post { if (mainAct() != null) { expanded = !expanded; touch++ } } }
    fun collapse() { main.post { expanded = false } }

    /** A short message that goes away by itself. */
    fun flash(ctx: Context, id: String, title: String, sub: String, icon: ImageVector?, accent: Color, glyph: String = "", sec: Int = IslandCfg.flashSec) {
        if (!LockStore.bool(ctx, "isl_on", true)) return
        upsert(IAct(id, IKind.FLASH, 60, title, sub, icon, glyph, "", accent, now() + sec * 1000L))
    }

    /** 5G or 4G was switched. Shown the moment you tap the tile. */
    fun flashNet(ctx: Context, fiveG: Boolean) {
        flash(ctx, "net", if (fiveG) "5G on" else "4G only", "Network", null, Color(0xFF38BDF8), if (fiveG) "5G" else "4G", 2)
    }

    /** For the reels blocker, so the answer to "why did it close" is on screen. */
    fun flashBlocked(ctx: Context, what: String, why: String) {
        flash(ctx, "block", what, why, Icons.Outlined.Block, Color(0xFFFF6B8B), "", 3)
    }

    // ---- calls
    fun onCall(ctx: Context) {
        if (!LockStore.bool(ctx, "isl_on", true) || !LockStore.bool(ctx, "isl_call", true)) { remove("call"); return }
        when (Calls.state) {
            TelephonyManager.CALL_STATE_RINGING -> {
                upsert(IAct("call", IKind.CALL, 100, "Incoming call", "", Icons.Outlined.Call, "", "", Color(0xFF34D399)))
                if (LockStore.bool(ctx, "isl_call_open", true)) main.post { expanded = true; touch++ }
            }
            TelephonyManager.CALL_STATE_OFFHOOK -> upsert(IAct("call", IKind.CALL, 100, "On call", "", Icons.Outlined.Call, "", "", Color(0xFF34D399)))
            else -> remove("call")
        }
    }

    // ---- battery
    private var lastLevel = -1

    /** Called with every battery reading. Shows "charged" at 100 and a warning at 15. */
    fun onBattery(ctx: Context, level: Int, charging: Boolean) {
        val prev = lastLevel
        lastLevel = level
        if (prev < 0 || !LockStore.bool(ctx, "isl_charge", true)) return
        if (charging && level >= 100 && prev < 100) flash(ctx, "full", "Fully charged", "100%", Icons.Outlined.BatteryFull, Color(0xFF34D399), "", 5)
        if (!charging && level <= 15 && prev > 15) flash(ctx, "low", "Low battery", "$level%", Icons.Outlined.BatteryAlert, Color(0xFFFFB84D), "", 5)
        if (!charging && level <= 5 && prev > 5) flash(ctx, "low", "Battery very low", "$level%", Icons.Outlined.BatteryAlert, Color(0xFFFF6B8B), "", 6)
    }

    // ---- notifications
    fun onNotification(ctx: Context, sbn: StatusBarNotification) {
        try {
            if (!LockStore.bool(ctx, "isl_on", true) || !LockStore.bool(ctx, "isl_notif", true)) return
            val n = sbn.notification ?: return
            val pkg = sbn.packageName
            if (pkg == ctx.packageName || pkg == "android" || pkg == "com.android.systemui") return
            if (sbn.isOngoing || (n.flags and Notification.FLAG_GROUP_SUMMARY) != 0) return
            if (n.category == Notification.CATEGORY_CALL || n.category == Notification.CATEGORY_TRANSPORT ||
                n.category == Notification.CATEGORY_PROGRESS || n.category == Notification.CATEGORY_SERVICE ||
                n.category == Notification.CATEGORY_SYSTEM || n.category == Notification.CATEGORY_STATUS
            ) return
            if (pkg in Safety.dialerPkgs(ctx)) return
            // The app you are looking at does not need to tell you about itself
            if (pkg == Fg.pkg) return
            val ex = n.extras ?: return
            val title = ex.getCharSequence(Notification.EXTRA_TITLE)?.toString()?.trim().orEmpty()
            val text = ex.getCharSequence(Notification.EXTRA_TEXT)?.toString()?.trim().orEmpty()
            if (title.isEmpty() && text.isEmpty()) return
            val showText = LockStore.bool(ctx, "isl_notif_text", true)
            val label = try { ctx.packageManager.getApplicationLabel(ctx.packageManager.getApplicationInfo(pkg, 0)).toString() } catch (e: Exception) { pkg }
            upsert(
                IAct(
                    "n:$pkg", IKind.NOTE, 55,
                    if (showText) title.ifEmpty { label } else label,
                    if (showText) text else "New notification",
                    null, "", pkg, Color(0xFF60A5FA), now() + 6000L
                )
            )
        } catch (_: Exception) {}
    }

    /** What the island wants its window to be, in dp. Null means nothing is shown and the window can go. */
    fun window(): IslandWin? {
        if (!IslandCfg.on) return null
        val m = mainAct()
        if (m == null && !IslandCfg.idlePill) return null
        val d = IslandCfg.scale / 100f
        val h = IslandCfg.h * d
        val pad = IslandLayout.PAD
        if (m == null) return IslandWin(IslandCfg.w * d + 2 * pad, h + 2 * pad)
        if (expanded) {
            val bubble = if (sideAct() != null) h + IslandLayout.GAP else 0f
            val w = IslandLayout.expandedW(screenWdp - bubble, d)
            return IslandWin(w + bubble + 2 * pad, IslandLayout.expandedH(m.kind, d) + 2 * pad)
        }
        val side = sideAct()
        val mw = IslandLayout.compactW(m.kind, d)
        val total = mw + (if (side != null) IslandLayout.GAP + h else 0f)
        return IslandWin(total + 2 * pad, h + 2 * pad)
    }
}

class IslandWin(val w: Float, val h: Float)

/** The sizes of the island in dp. The window is made exactly this big, so empty space never blocks a touch. */
object IslandLayout {
    const val PAD = 16f
    const val GAP = 8f

    fun compactW(kind: IKind, d: Float): Float {
        val base = IslandCfg.w * d
        val extra = when (kind) {
            IKind.CALL -> 78f
            IKind.MEDIA -> 64f
            IKind.CHARGE -> 52f
            IKind.RECORD -> 56f
            IKind.NOTE -> 92f
            IKind.FLASH -> 66f
        }
        return base + extra * d
    }

    /** [avail] is the width of the screen in dp, minus the bubble when there is one. */
    fun expandedW(avail: Float, d: Float): Float = minOf(avail - 24f, 352f * (0.9f + 0.1f * d))

    fun expandedH(kind: IKind, d: Float): Float {
        val h = when (kind) {
            IKind.CALL -> 158f
            IKind.MEDIA -> 184f
            IKind.CHARGE -> 124f
            IKind.RECORD -> 116f
            IKind.NOTE -> 128f
            IKind.FLASH -> 98f
        }
        return h * (0.9f + 0.1f * d)
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
        if (!LockStore.bool(ctx, "isl_media", true)) { IslandMedia.clear(); IslandHub.remove("media"); return }
        val all = cbs.keys.toList()
        val playing = all.firstOrNull { isPlaying(it) }
        val holdMs = LockStore.int(ctx, "isl_media_hold", 20) * 1000L
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

/** Charging, ringer mode and recording. Kept apart from the window so it works whether or not the island is on screen. */
class IslandSources(private val ctx: Context) {
    private var rx: BroadcastReceiver? = null

    fun start() {
        if (rx != null) return
        val r = object : BroadcastReceiver() {
            override fun onReceive(c: Context, i: Intent) {
                try {
                    if (!LockStore.bool(c, "isl_on", true)) return
                    when (i.action) {
                        Intent.ACTION_POWER_CONNECTED -> if (LockStore.bool(c, "isl_charge", true)) {
                            IslandHub.upsert(
                                IAct("charge", IKind.CHARGE, 30, "Charging", "", Icons.Outlined.BatteryChargingFull, "", "", Color(0xFF34D399),
                                    SystemClock.elapsedRealtime() + LockStore.int(c, "isl_charge_sec", 5) * 1000L)
                            )
                        }
                        Intent.ACTION_POWER_DISCONNECTED -> IslandHub.remove("charge")
                        AudioManager.RINGER_MODE_CHANGED_ACTION -> if (LockStore.bool(c, "isl_ringer", true)) {
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

/**
 * Owns the island window. The window is always exactly as big as the island needs:
 * when the island grows the window grows first, when it shrinks the window shrinks after the animation has finished.
 */
class IslandHost(private val ctx: Context) {
    private val overlay = Overlay(ctx)
    private val handler = Handler(Looper.getMainLooper())
    private val sources = IslandSources(ctx)
    private val media = MediaWatcher(ctx)
    private var scope: CoroutineScope? = null
    private var lp: WindowManager.LayoutParams? = null
    private var curW = 0
    private var curH = 0
    private var wantW = 0
    private var wantH = 0
    private var removeJob: Runnable? = null
    private var shrinkJob: Runnable? = null
    private var mediaTries = 0
    private var hidden = false

    val isShown: Boolean get() = overlay.isShown

    fun start() {
        if (scope != null) return
        IslandCfg.load(ctx)
        sources.start()
        val s = CoroutineScope(SupervisorJob() + Dispatchers.Main)
        scope = s
        updateScreen()
        s.launch { snapshotFlow { IslandHub.window() }.collect { applyWin(it) } }
        s.launch { snapshotFlow { Recorder.running }.collect { onRecording(it) } }
        startMedia()
    }

    fun stop() {
        scope?.cancel()
        scope = null
        sources.stop()
        media.stop()
        handler.removeCallbacksAndMessages(null)
        overlay.remove()
        lp = null
        curW = 0; curH = 0
    }

    /** Called when the screen turns or settings changed. */
    fun relayout() {
        IslandCfg.load(ctx)
        updateScreen()
        if (overlay.isShown) {
            overlay.remove()
            lp = null
            curW = 0; curH = 0
            applyWin(IslandHub.window())
        }
        startMedia()
    }

    /** Checks again whether the island may be on screen, for example after a game came to the front. */
    fun refresh() { applyWin(IslandHub.window()) }

    /** The screen went off: nothing to draw, and nothing should be running. */
    fun sleep() {
        hidden = true
        removeJob?.let { handler.removeCallbacks(it) }
        shrinkJob?.let { handler.removeCallbacks(it) }
        overlay.remove()
        lp = null
        curW = 0; curH = 0
    }

    fun wake() {
        hidden = false
        IslandCfg.load(ctx)
        applyWin(IslandHub.window())
        startMedia()
    }

    /** Switches the media watching on or off to match the settings. Safe to call at any time. */
    fun startMedia() {
        if (!LockStore.bool(ctx, "isl_on", true) || !LockStore.bool(ctx, "isl_media", true)) { media.stop(); return }
        if (media.start()) { mediaTries = 0; return }
        // The listener may not be allowed yet. Root grants it a moment after the service starts.
        if (mediaTries++ < 6) handler.postDelayed({ startMedia() }, 4000L)
    }

    private fun updateScreen() {
        val (sw, _) = screenSize(ctx)
        IslandHub.screenWdp = (sw / ctx.resources.displayMetrics.density).toInt()
    }

    private fun onRecording(on: Boolean) {
        if (on && LockStore.bool(ctx, "isl_rec", true)) {
            IslandHub.upsert(IAct("rec", IKind.RECORD, 40, "Recording", "", Icons.Outlined.Videocam, "", "", Color(0xFFFF4D6D)))
        } else IslandHub.remove("rec")
    }

    private fun allowedNow(): Boolean {
        if (hidden || !Monitor.screenOn || !Settings.canDrawOverlays(ctx)) return false
        if (!IslandCfg.games && Fg.game != null) return false
        if (!IslandCfg.landscape && ctx.resources.configuration.orientation == android.content.res.Configuration.ORIENTATION_LANDSCAPE) return false
        return true
    }

    private fun params(w: Int, h: Int): WindowManager.LayoutParams {
        val d = ctx.resources.displayMetrics.density
        val p = WindowManager.LayoutParams(
            w, h, WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL or
                WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS or WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
            PixelFormat.TRANSLUCENT
        )
        p.gravity = Gravity.TOP or Gravity.CENTER_HORIZONTAL
        p.x = (IslandCfg.x * d).toInt()
        p.y = ((IslandCfg.top - IslandLayout.PAD) * d).toInt()
        if (Build.VERSION.SDK_INT >= 30) p.layoutInDisplayCutoutMode = WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_ALWAYS
        else if (Build.VERSION.SDK_INT >= 28) p.layoutInDisplayCutoutMode = WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES
        return p
    }

    private fun applyWin(win: IslandWin?) {
        removeJob?.let { handler.removeCallbacks(it) }
        shrinkJob?.let { handler.removeCallbacks(it) }
        if (win == null || !allowedNow()) {
            // Play the going-away animation, then take the window off
            IslandHub.shown = false
            if (overlay.isShown) {
                val r = Runnable { if (IslandHub.window() == null || !allowedNow()) { overlay.remove(); lp = null; curW = 0; curH = 0 } }
                removeJob = r
                handler.postDelayed(r, 380L)
            }
            return
        }
        IslandHub.shown = true
        val d = ctx.resources.displayMetrics.density
        // The window is made a little bigger than the island in px, so rounding never cuts an edge
        val w = (win.w * d).toInt() + 2
        val h = (win.h * d).toInt() + 2
        wantW = w; wantH = h
        if (!overlay.isShown) {
            val p = params(w, h)
            lp = p
            curW = w; curH = h
            overlay.show(p) { IslandRoot() }
            return
        }
        val p = lp ?: params(w, h).also { lp = it }
        val growW = maxOf(curW, w)
        val growH = maxOf(curH, h)
        // Grow now, shrink later: the island animates inside the window
        setSize(p, growW, growH)
        if (growW != w || growH != h) {
            val r = Runnable { lp?.let { setSize(it, wantW, wantH) } }
            shrinkJob = r
            handler.postDelayed(r, 420L)
        }
    }

    private fun setSize(p: WindowManager.LayoutParams, w: Int, h: Int) {
        if (p.width == w && p.height == h) return
        val d = ctx.resources.displayMetrics.density
        p.width = w
        p.height = h
        p.x = (IslandCfg.x * d).toInt()
        p.y = ((IslandCfg.top - IslandLayout.PAD) * d).toInt()
        curW = w; curH = h
        overlay.update(p)
    }
}
