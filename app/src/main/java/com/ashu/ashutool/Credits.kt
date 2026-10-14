/*
 * Ashutool - root toolkit for custom ROMs
 * Copyright (C) 2026 Ashutosh Nishad
 * Telegram: @ashutosh_07x (https://t.me/ashutosh_07x)
 *
 * Free software under the GNU GPL v3.0 or later, with the attribution terms in NOTICE.
 * Keep this header and the author credit in every copy and modified version.
 */

package com.ashu.ashutool

import android.app.Application
import android.content.Context
import android.content.Intent
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.util.Log
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import java.io.File
import java.security.MessageDigest
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Who made Ashutool. These lines are the author credit that the GNU GPL v3 additional terms in NOTICE
 * require every copy and every modified version to keep.
 */
object Credits {
    const val APP = "Ashutool"
    const val AUTHOR = "Ashutosh Nishad"
    const val TELEGRAM = "@ashutosh_07x"
    const val TELEGRAM_URL = "https://t.me/ashutosh_07x"
    const val INSTAGRAM_URL = "https://www.instagram.com/ashutosh_07x"
    const val LICENSE = "GNU GPL v3.0 or later"
    const val YEAR = "2026"
}

/**
 * Tells an original build from a copy. Nothing here stops anyone from changing the code, the source is open.
 * What it does is make a copy that removed the author credit announce that, and keeps it from running.
 *  OFFICIAL  signed with the author's key, credit intact
 *  COMMUNITY built by someone else from the source, credit intact. Fully allowed by the licence.
 *  TAMPERED  the credit text was changed or removed. Breaks the licence terms. The app does not run.
 */
object Integrity {
    private const val CREDIT_HASH = "5379b5f5838b2295a3a3a5dc537d63b601ad1e9bc21b5bafb2bcdb1e5d3fe978"

    enum class Verdict { OFFICIAL, COMMUNITY, TAMPERED }

    @Volatile private var cached: Verdict? = null

    private fun sha256(b: ByteArray): String =
        MessageDigest.getInstance("SHA-256").digest(b).joinToString("") { "%02x".format(it) }

    private fun creditsIntact(): Boolean =
        sha256("${Credits.AUTHOR}|${Credits.TELEGRAM}|${Credits.TELEGRAM_URL}|${Credits.LICENSE}".toByteArray()) == CREDIT_HASH

    /** SHA-256 of the key this install was signed with. */
    @Suppress("DEPRECATION")
    fun signature(ctx: Context): String? = try {
        val pm = ctx.packageManager
        val sigs = if (Build.VERSION.SDK_INT >= 28)
            pm.getPackageInfo(ctx.packageName, PackageManager.GET_SIGNING_CERTIFICATES).signingInfo?.apkContentsSigners
        else
            pm.getPackageInfo(ctx.packageName, PackageManager.GET_SIGNATURES).signatures
        sigs?.firstOrNull()?.let { sha256(it.toByteArray()) }
    } catch (e: Exception) { null }

    fun verdict(ctx: Context): Verdict {
        cached?.let { return it }
        val official = BuildConfig.CERT_SHA256.lowercase()
        val v = when {
            !creditsIntact() -> Verdict.TAMPERED
            official.isNotEmpty() && ctx.packageName == "com.ashu.ashutool" && signature(ctx) == official -> Verdict.OFFICIAL
            else -> Verdict.COMMUNITY
        }
        cached = v
        return v
    }

    fun tampered(ctx: Context): Boolean = verdict(ctx) == Verdict.TAMPERED

    fun label(ctx: Context): String = when (verdict(ctx)) {
        Verdict.OFFICIAL -> "Official build"
        Verdict.COMMUNITY -> "Community build, not signed by the author"
        Verdict.TAMPERED -> "Modified build, author credit removed"
    }

    @Suppress("DEPRECATION")
    fun debuggable(ctx: Context): Boolean = (ctx.applicationInfo.flags and ApplicationInfo.FLAG_DEBUGGABLE) != 0
}

fun openLink(ctx: Context, url: String) {
    try { ctx.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }
    catch (e: Exception) { toast(ctx, "No app can open this link") }
}

/** Small credit line shown at the bottom of the main pages. Tap to open the author's Telegram. */
@Composable
fun CreditFooter(modifier: Modifier = Modifier) {
    val ctx = LocalContext.current
    Column(
        modifier.fillMaxWidth().clickable { openLink(ctx, Credits.TELEGRAM_URL) }.padding(vertical = 22.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text("${Credits.APP}  \u00B7  made by ${Credits.AUTHOR}", color = TextLo, fontSize = 12.sp, fontWeight = FontWeight.Medium)
        Text(Credits.TELEGRAM, color = Accent, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
    }
}

/** Keeps the last crash so it can be copied from the About page and sent along with a bug report. */
object CrashLog {
    private fun file(ctx: Context) = File(ctx.filesDir, "last_crash.txt")

    fun install(ctx: Context) {
        val app = ctx.applicationContext
        val previous = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { t, e ->
            try {
                val time = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US).format(Date())
                file(app).writeText("Time: $time\nThread: ${t.name}\n\n${Log.getStackTraceString(e)}")
            } catch (_: Throwable) {}
            previous?.uncaughtException(t, e)
        }
    }

    fun last(ctx: Context): String? = try {
        val f = file(ctx)
        if (f.exists()) f.readText() else null
    } catch (e: Exception) { null }

    fun clear(ctx: Context) { try { file(ctx).delete() } catch (_: Exception) {} }

    /** Everything a bug report needs, in one block of text. */
    fun debugInfo(ctx: Context, root: Boolean?): String = buildString {
        appendLine("${Credits.APP} ${BuildConfig.VERSION_NAME} (${BuildConfig.VERSION_CODE})")
        appendLine("Build: ${Integrity.label(ctx)}")
        appendLine("Android ${Build.VERSION.RELEASE} (API ${Build.VERSION.SDK_INT})")
        appendLine("Device: ${Build.MANUFACTURER} ${Build.MODEL}")
        appendLine("Root: ${root ?: "unknown"}")
        appendLine("Accessibility connected: ${AshuAccessibility.connected}")
        appendLine("Service running: ${MonitorService.instance != null}")
        appendLine("Safe mode: ${Guard.safe}")
        appendLine("Games found: ${GameMode.cached.size}")
        if (GameDiag.error.isNotEmpty()) appendLine("Game bar error: ${GameDiag.error}")
    }
}

/** Application class. Installs the crash log before anything else runs. */
class AshuApp : Application() {
    override fun onCreate() {
        super.onCreate()
        CrashLog.install(this)
    }
}

/** Shown instead of the app when the author credit has been removed, which the licence does not allow. */
@Composable
fun TamperedScreen() {
    val ctx = LocalContext.current
    Column(
        Modifier.fillMaxSize().padding(28.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text("Modified copy", color = TextHi, fontSize = 24.sp, fontWeight = FontWeight.Bold)
        Text(
            "The author credit in this copy of ${Credits.APP} was changed or removed. The licence it is shared under " +
                "does not allow that, so this copy will not run. You can get the original from the developer, " +
                "${Credits.AUTHOR}, ${Credits.TELEGRAM}. A modified version that keeps the credit is welcome.",
            color = TextLo, fontSize = 14.sp, textAlign = TextAlign.Center, modifier = Modifier.padding(vertical = 16.dp)
        )
        GradButton("Get the original on Telegram", GTeal) { openLink(ctx, Credits.TELEGRAM_URL) }
    }
}
