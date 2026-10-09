package com.ashu.ashutool

import android.content.Context
import android.graphics.PixelFormat
import android.media.AudioManager
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.view.Gravity
import android.view.WindowManager
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.LifecycleRegistry
import androidx.lifecycle.ViewModelStore
import androidx.lifecycle.ViewModelStoreOwner
import androidx.lifecycle.setViewTreeLifecycleOwner
import androidx.lifecycle.setViewTreeViewModelStoreOwner
import androidx.savedstate.SavedStateRegistry
import androidx.savedstate.SavedStateRegistryController
import androidx.savedstate.SavedStateRegistryOwner
import androidx.savedstate.setViewTreeSavedStateRegistryOwner
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/** Hosts Compose in a window that is not an Activity. Used by the lock overlay and the game sidebar. */
class Overlay(private val ctx: Context) : LifecycleOwner, SavedStateRegistryOwner, ViewModelStoreOwner {
    private val registry = LifecycleRegistry(this)
    private val saved = SavedStateRegistryController.create(this)
    private val store = ViewModelStore()
    private val wm = ctx.getSystemService(WindowManager::class.java)
    private var view: ComposeView? = null

    override val lifecycle: Lifecycle get() = registry
    override val savedStateRegistry: SavedStateRegistry get() = saved.savedStateRegistry
    override val viewModelStore: ViewModelStore get() = store

    init {
        saved.performAttach()
        saved.performRestore(null)
        registry.currentState = Lifecycle.State.CREATED
    }

    val isShown: Boolean get() = view != null

    fun show(params: WindowManager.LayoutParams, content: @Composable () -> Unit) {
        remove()
        Palette.load(ctx)
        val v = ComposeView(ctx)
        v.setViewTreeLifecycleOwner(this)
        v.setViewTreeSavedStateRegistryOwner(this)
        v.setViewTreeViewModelStoreOwner(this)
        v.setContent { AshuTheme { content() } }
        registry.currentState = Lifecycle.State.RESUMED
        try {
            wm.addView(v, params)
            view = v
        } catch (e: Exception) {
            registry.currentState = Lifecycle.State.CREATED
        }
    }

    fun update(params: WindowManager.LayoutParams) {
        view?.let { try { wm.updateViewLayout(it, params) } catch (_: Exception) {} }
    }

    fun remove() {
        view?.let { try { wm.removeView(it) } catch (_: Exception) {} }
        view = null
        registry.currentState = Lifecycle.State.CREATED
    }
}

// ============================================================ Game sidebar

private val SbText = Color.White
private val SbLow = Color(0xFFB4C0D8)

/** Edge handle that slides out a tool panel while a game is in the foreground. */
class GameSidebar(private val ctx: Context) {
    private val overlay = Overlay(ctx)
    private val handler = Handler(Looper.getMainLooper())
    private val io = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var shown = false
    private var dismissedFor: String? = null
    private var pkg by mutableStateOf("")
    private var expanded by mutableStateOf(false)
    private var open by mutableStateOf(false)

    private fun params(full: Boolean): WindowManager.LayoutParams {
        val d = ctx.resources.displayMetrics.density
        val side = if (LockStore.int(ctx, "g_side", 1) == 0) Gravity.START else Gravity.END
        val lp = if (full) WindowManager.LayoutParams(
            WindowManager.LayoutParams.MATCH_PARENT, WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
            PixelFormat.TRANSLUCENT
        ) else WindowManager.LayoutParams(
            (26 * d).toInt(), (150 * d).toInt(),
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL,
            PixelFormat.TRANSLUCENT
        )
        lp.gravity = side or Gravity.CENTER_VERTICAL
        return lp
    }

    fun showFor(p: String) {
        if (dismissedFor == p) return
        pkg = p
        if (shown) return
        shown = true
        expanded = false
        open = false
        overlay.show(params(false)) { Content() }
    }

    fun hide() {
        if (!shown) return
        shown = false
        open = false
        expanded = false
        overlay.remove()
    }

    fun resetDismiss() { dismissedFor = null }

    private fun off() {
        dismissedFor = pkg
        hide()
    }

    private fun openPanel() {
        overlay.update(params(true))
        expanded = true
        handler.post { open = true }
    }

    private fun closePanel() {
        open = false
        handler.postDelayed({
            expanded = false
            overlay.update(params(false))
        }, 330)
    }

    private fun say(msg: String) { handler.post { toast(ctx, msg) } }

    private fun act(label: String, f: () -> Boolean) {
        io.launch { say(if (f()) label else "Not available") }
    }

    @Composable
    private fun Content() {
        val side = LockStore.int(ctx, "g_side", 1)
        if (!expanded) Handle(side) else Panel(side)
    }

    @Composable
    private fun Handle(side: Int) {
        Box(
            Modifier.fillMaxSize()
                .pointerInput(Unit) {
                    detectDragGestures { change, drag ->
                        change.consume()
                        if ((side == 1 && drag.x < -6f) || (side == 0 && drag.x > 6f)) openPanel()
                    }
                }
                .clickable { openPanel() },
            contentAlignment = Alignment.Center
        ) {
            Box(
                Modifier.width(7.dp).height(88.dp).clip(RoundedCornerShape(4.dp)).alpha(0.9f)
                    .background(Brush.verticalGradient(listOf(Color(0xFF2DE2C0), Color(0xFF4D8DFF))))
            )
        }
    }

    @Composable
    private fun Panel(side: Int) {
        val scrim by animateFloatAsState(if (open) 0.45f else 0f, tween(300), label = "scrim")
        val none = remember { MutableInteractionSource() }
        Box(
            Modifier.fillMaxSize().background(Color.Black.copy(alpha = scrim))
                .clickable(interactionSource = none, indication = null) { closePanel() }
        ) {
            AnimatedVisibility(
                visible = open,
                modifier = Modifier.align(if (side == 1) Alignment.CenterEnd else Alignment.CenterStart),
                enter = slideInHorizontally(tween(320, easing = FastOutSlowInEasing)) { if (side == 1) it else -it } + fadeIn(tween(250)),
                exit = slideOutHorizontally(tween(260)) { if (side == 1) it else -it } + fadeOut(tween(200))
            ) { PanelCard() }
        }
    }

    @Composable
    private fun PanelCard() {
        val s by Monitor.snap.collectAsState()
        val shape = RoundedCornerShape(26.dp)
        val none = remember { MutableInteractionSource() }
        val am = remember { ctx.getSystemService(AudioManager::class.java) }
        var vol by remember { mutableFloatStateOf(am.getStreamVolume(AudioManager.STREAM_MUSIC).toFloat()) }
        var bright by remember {
            mutableFloatStateOf(
                try { Settings.System.getInt(ctx.contentResolver, Settings.System.SCREEN_BRIGHTNESS).toFloat() } catch (e: Exception) { 128f }
            )
        }
        val label = remember(pkg) {
            try { ctx.packageManager.getApplicationLabel(ctx.packageManager.getApplicationInfo(pkg, 0)).toString() } catch (e: Exception) { pkg }
        }
        Column(
            Modifier.padding(12.dp).width(300.dp).fillMaxHeight(0.92f).clip(shape)
                .background(Brush.verticalGradient(listOf(Color(0xF2101A3A), Color(0xF2081B27))))
                .border(1.dp, Color.White.copy(alpha = 0.25f), shape)
                .clickable(interactionSource = none, indication = null) {}
                .verticalScroll(rememberScrollState())
                .padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                AppIcon(pkg, 36.dp)
                Spacer(Modifier.width(10.dp))
                Column(Modifier.weight(1f)) {
                    Text(label, color = SbText, fontSize = 15.sp, fontWeight = FontWeight.Bold, maxLines = 1)
                    Text("Game tools", color = SbLow, fontSize = 11.sp)
                }
                Icon(Icons.Outlined.Close, "Close", tint = SbText, modifier = Modifier.size(24.dp).clickable { closePanel() })
            }
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Stat("CPU", "${s.cpu.toInt()}%"); Stat("Temp", "${s.cpuTemp.toInt()}\u00B0"); Stat("RAM", "${s.ramPct.toInt()}%")
            }
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Stat("Battery", "${s.batt.level}%"); Stat("Down", fmtSpeed(s.netDown)); Stat("Up", fmtSpeed(s.netUp))
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Tool(Icons.Outlined.Speed, "Performance", GameUi.perf, Modifier.weight(1f)) {
                    io.launch { GameMode.setPerf(ctx, !GameUi.perf) }
                }
                Tool(Icons.Outlined.NotificationsOff, "Do not disturb", GameUi.dnd, Modifier.weight(1f)) {
                    io.launch { GameMode.setDnd(ctx, !GameUi.dnd) }
                }
                Tool(Icons.Outlined.CleaningServices, "Clear RAM", false, Modifier.weight(1f)) {
                    act("RAM cleared") { Root.ok("am kill-all") && Sys.dropCaches() }
                }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Tool(Icons.Outlined.PhotoCamera, "Screenshot", false, Modifier.weight(1f)) {
                    closePanel()
                    io.launch {
                        delay(650)
                        val f = "/sdcard/Pictures/Ashutool/ashu_${System.currentTimeMillis()}.png"
                        say(if (Root.ok("mkdir -p /sdcard/Pictures/Ashutool", "screencap -p $f")) "Screenshot saved" else "Could not save")
                    }
                }
                Tool(Icons.Outlined.Videocam, if (GameUi.rec) "Stop record" else "Record", GameUi.rec, Modifier.weight(1f)) {
                    if (GameUi.rec) {
                        GameUi.rec = false
                        act("Recording saved") { Root.ok("pkill -2 screenrecord") }
                    } else {
                        closePanel()
                        io.launch {
                            delay(650)
                            val f = "/sdcard/Movies/Ashutool/ashu_${System.currentTimeMillis()}.mp4"
                            GameUi.rec = Root.ok("mkdir -p /sdcard/Movies/Ashutool", "nohup screenrecord --time-limit 900 $f >/dev/null 2>&1 &")
                        }
                    }
                }
                Tool(Icons.Outlined.VisibilityOff, "Hide bar", false, Modifier.weight(1f)) { off() }
            }
            SliderRow(Icons.Outlined.LightMode, "Brightness", bright, 5f, 255f) {
                bright = it
                io.launch { Root.ok("settings put system screen_brightness_mode 0", "settings put system screen_brightness ${it.toInt()}") }
            }
            SliderRow(Icons.Outlined.MusicNote, "Volume", vol, 0f, am.getStreamMaxVolume(AudioManager.STREAM_MUSIC).toFloat()) {
                vol = it
                am.setStreamVolume(AudioManager.STREAM_MUSIC, it.toInt(), 0)
            }
        }
    }

    @Composable
    private fun Stat(label: String, value: String) {
        Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.width(84.dp)) {
            Text(value, color = SbText, fontSize = 14.sp, fontWeight = FontWeight.SemiBold, maxLines = 1)
            Text(label, color = SbLow, fontSize = 10.sp)
        }
    }

    @Composable
    private fun Tool(icon: ImageVector, label: String, on: Boolean, modifier: Modifier, onClick: () -> Unit) {
        val shape = RoundedCornerShape(16.dp)
        Column(
            modifier.bounceClick(onClick = onClick).clip(shape)
                .background(if (on) Brush.linearGradient(listOf(Color(0xFF2DE2C0), Color(0xFF4D8DFF))) else Brush.linearGradient(listOf(Color.White.copy(alpha = 0.10f), Color.White.copy(alpha = 0.10f))))
                .padding(vertical = 12.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Icon(icon, label, tint = if (on) Ink else SbText, modifier = Modifier.size(22.dp))
            Spacer(Modifier.height(4.dp))
            Text(label, color = if (on) Ink else SbText, fontSize = 10.sp, fontWeight = FontWeight.Medium, maxLines = 1)
        }
    }

    @Composable
    private fun SliderRow(icon: ImageVector, label: String, value: Float, lo: Float, hi: Float, onChange: (Float) -> Unit) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(icon, label, tint = SbLow, modifier = Modifier.size(20.dp))
            Slider(
                value = value.coerceIn(lo, hi), onValueChange = onChange, valueRange = lo..hi,
                modifier = Modifier.weight(1f).padding(start = 8.dp),
                colors = SliderDefaults.colors(thumbColor = Color(0xFF2DE2C0), activeTrackColor = Color(0xFF2DE2C0), inactiveTrackColor = Color.White.copy(alpha = 0.18f))
            )
        }
    }
}
