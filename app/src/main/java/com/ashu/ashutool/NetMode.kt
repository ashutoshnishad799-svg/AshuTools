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
import android.os.IBinder
import android.os.Looper
import android.service.quicksettings.Tile
import android.service.quicksettings.TileService
import android.telephony.SubscriptionManager

/**
 * Runs as root, started with app_process:
 *   CLASSPATH=<apk> app_process /system/bin com.ashu.ashutool.NetModeMain get|set5|set4 <subscription id>
 * It talks to the phone service directly, so it must not touch the app's own Context.
 * Only the 5G bit of the allowed network types is changed. Everything else the phone allows stays as it was.
 */
object NetModeMain {
    /** Network type 20 is NR (5G). The bit of a network type is 1 shifted by (type - 1). */
    private const val NR_BIT = 1L shl 19

    @JvmStatic
    fun main(args: Array<String>) {
        try {
            val cmd = args.getOrNull(0) ?: "get"
            val sub = args.getOrNull(1)?.toIntOrNull() ?: -1
            val serviceManager = Class.forName("android.os.ServiceManager")
            val binder = serviceManager.getMethod("getService", String::class.java).invoke(null, "phone") as IBinder
            val stub = Class.forName("com.android.internal.telephony.ITelephony\$Stub")
            val tel = stub.getMethod("asInterface", IBinder::class.java).invoke(null, binder)!!
            val methods = tel.javaClass.methods
            val getNew = methods.firstOrNull { it.name == "getAllowedNetworkTypesForReason" && it.parameterTypes.size == 2 }
            val getOld = methods.firstOrNull { it.name == "getAllowedNetworkTypes" && it.parameterTypes.size == 1 }
            val cur: Long = when {
                getNew != null -> getNew.invoke(tel, sub, 0) as Long
                getOld != null -> getOld.invoke(tel, sub) as Long
                else -> { println("ERR this Android has no network type api"); return }
            }
            if (cmd == "get") {
                println("NR=${if ((cur and NR_BIT) != 0L) 1 else 0}")
                return
            }
            val want = if (cmd == "set5") (cur or NR_BIT) else (cur and NR_BIT.inv())
            val setNew = methods.firstOrNull { it.name == "setAllowedNetworkTypesForReason" && it.parameterTypes.size == 3 }
            val setOld = methods.firstOrNull { it.name == "setAllowedNetworkTypes" && it.parameterTypes.size == 2 }
            val ok = when {
                setNew != null -> setNew.invoke(tel, sub, 0, want) as Boolean
                setOld != null -> setOld.invoke(tel, sub, want) as Boolean
                else -> { println("ERR this Android cannot change the network type"); return }
            }
            println(if (ok) "OK" else "ERR the phone refused")
        } catch (e: Throwable) {
            val c = e.cause ?: e
            println("ERR ${c.javaClass.simpleName}: ${c.message}")
        }
    }
}

/** Switches 5G on and off from the app and from the quick settings tile. */
object NetMode {
    private fun subId(): Int {
        val d = SubscriptionManager.getDefaultDataSubscriptionId()
        return if (d > 0) d else SubscriptionManager.getDefaultSubscriptionId()
    }

    private fun run(ctx: Context, mode: String): String {
        val apk = ctx.applicationInfo.sourceDir
        return Root.run("CLASSPATH=${Root.q(apk)} app_process /system/bin com.ashu.ashutool.NetModeMain $mode ${subId()} 2>&1").joinToString("\n")
    }

    /** true when 5G is allowed, false when it is switched off, null when this phone does not say. */
    fun query(ctx: Context): Boolean? =
        Regex("NR=(\\d)").find(run(ctx, "get"))?.groupValues?.get(1)?.let { it == "1" }

    /** Empty text when it worked, otherwise the reason it did not. */
    fun set(ctx: Context, fiveG: Boolean): String {
        val out = run(ctx, if (fiveG) "set5" else "set4").trim()
        return if (out.startsWith("OK")) "" else out.ifEmpty { "No answer from the phone" }
    }
}

/** The 5G / 4G tile for the quick settings panel. */
class NetTileService : TileService() {
    private val main = Handler(Looper.getMainLooper())

    override fun onStartListening() { refresh() }

    private fun paint(fiveG: Boolean?) {
        val t = qsTile ?: return
        t.label = "5G / 4G"
        t.state = when (fiveG) { true -> Tile.STATE_ACTIVE; false -> Tile.STATE_INACTIVE; null -> Tile.STATE_UNAVAILABLE }
        if (Build.VERSION.SDK_INT >= 29) t.subtitle = when (fiveG) { true -> "5G on"; false -> "4G only"; null -> "Needs root" }
        t.updateTile()
    }

    private fun refresh() {
        val app = applicationContext
        Thread { val v = NetMode.query(app); main.post { paint(v) } }.start()
    }

    override fun onClick() {
        val app = applicationContext
        val now = qsTile?.state == Tile.STATE_ACTIVE
        paint(!now)
        Thread {
            val err = NetMode.set(app, !now)
            val v = NetMode.query(app)
            main.post {
                paint(v)
                if (err.isNotEmpty()) toast(app, err.take(90))
            }
        }.start()
    }
}
