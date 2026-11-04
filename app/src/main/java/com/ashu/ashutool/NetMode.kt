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
import android.os.IBinder
import android.os.SystemClock
import android.telephony.SubscriptionManager
import java.io.BufferedReader
import java.io.InputStreamReader
import java.io.OutputStream
import java.lang.reflect.Method
import java.util.concurrent.Executors
import java.util.concurrent.LinkedBlockingQueue
import java.util.concurrent.TimeUnit

/**
 * Runs as root through app_process. It has two ways of working:
 *  - one shot: "get", "set5" or "set4" as the first argument, answer printed, process ends (slow, a new JVM starts)
 *  - daemon: "daemon" as the first argument. It connects to the phone service once, prints READY, and then answers one
 *    command per line on standard input ("get SUB", "set5 SUB", "set4 SUB", "ping"). Every answer is one line.
 *    The daemon is what makes the 5G / 4G tile instant: nothing is started again for a tap.
 *    It ends by itself when its input closes, which happens when Ashutool is closed.
 */
object NetModeMain {
    /** Network type 20 is NR (5G). The bit of a network type is 1 shifted by (type - 1). */
    private const val NR_BIT = 1L shl 19

    private class Phone(val tel: Any, val getter: Method?, val setter: Method?, val names: String)

    private fun connect(): Phone {
        val serviceManager = Class.forName("android.os.ServiceManager")
        val binder = serviceManager.getMethod("getService", String::class.java).invoke(null, "phone") as IBinder
        val stub = Class.forName("com.android.internal.telephony.ITelephony\$Stub")
        val tel = stub.getMethod("asInterface", IBinder::class.java).invoke(null, binder)!!
        val methods = tel.javaClass.methods
        val names = methods.filter { it.name.contains("NetworkType") }.joinToString(" ") { "${it.name}/${it.parameterTypes.size}" }
        val getter = methods.firstOrNull { it.name == "getAllowedNetworkTypesForReason" }
            ?: methods.firstOrNull { it.name == "getAllowedNetworkTypes" }
        val setter = methods.firstOrNull { it.name == "setAllowedNetworkTypesForReason" }
            ?: methods.firstOrNull { it.name == "setAllowedNetworkTypes" }
        return Phone(tel, getter, setter, names)
    }

    /**
     * Fills in the arguments from the parameter types: the first int is the subscription, a second int is the
     * reason (0 is the user), a long is the bitmask, and a text is a package name.
     */
    private fun argsFor(m: Method, sub: Int, mask: Long): Array<Any?> {
        var ints = 0
        return m.parameterTypes.map<Class<*>, Any?> { t ->
            when {
                t == Int::class.javaPrimitiveType -> { ints++; if (ints == 1) sub else 0 }
                t == Long::class.javaPrimitiveType -> mask
                t == String::class.java -> "com.android.shell"
                t == Boolean::class.javaPrimitiveType -> false
                else -> null
            }
        }.toTypedArray()
    }

    /** One command, one line of answer. Never throws. */
    private fun handle(p: Phone, cmd: String, sub: Int): String {
        try {
            if (cmd == "ping") return "PONG"
            val getter = p.getter ?: return "ERR no network type api. Phone service has: ${p.names}"
            val cur = (getter.invoke(p.tel, *argsFor(getter, sub, 0L)) as Number).toLong()
            if (cmd == "get") return "NR=${if ((cur and NR_BIT) != 0L) 1 else 0}"
            val want = if (cmd == "set5") (cur or NR_BIT) else (cur and NR_BIT.inv())
            val setter = p.setter ?: return "ERR cannot change it. Phone service has: ${p.names}"
            val r = setter.invoke(p.tel, *argsFor(setter, sub, want))
            return if ((r as? Boolean) ?: true) "OK" else "ERR the phone refused"
        } catch (e: Throwable) {
            val c = e.cause ?: e
            return "ERR ${c.javaClass.simpleName}: ${(c.message ?: "").replace('\n', ' ')}"
        }
    }

    @JvmStatic
    fun main(args: Array<String>) {
        try {
            val cmd = args.getOrNull(0) ?: "get"
            if (cmd == "daemon") { daemon(); return }
            val sub = args.getOrNull(1)?.toIntOrNull() ?: -1
            println(handle(connect(), cmd, sub))
        } catch (e: Throwable) {
            val c = e.cause ?: e
            println("ERR ${c.javaClass.simpleName}: ${(c.message ?: "").replace('\n', ' ')}")
        }
    }

    private fun daemon() {
        val phone = try { connect() } catch (e: Throwable) {
            val c = e.cause ?: e
            println("ERR ${c.javaClass.simpleName}: ${(c.message ?: "").replace('\n', ' ')}")
            System.out.flush()
            return
        }
        println("READY")
        System.out.flush()
        val input = BufferedReader(InputStreamReader(System.`in`))
        while (true) {
            val line = input.readLine() ?: break
            val parts = line.trim().split(" ").filter { it.isNotEmpty() }
            if (parts.isEmpty()) continue
            println(handle(phone, parts[0], parts.getOrNull(1)?.toIntOrNull() ?: -1))
            System.out.flush()
        }
    }
}

/**
 * The persistent root process behind [NetMode]. One request at a time, each with a time limit. If the process dies or
 * stops answering it is thrown away and started again on the next request, so a stuck phone service never freezes a tile.
 */
private object NetDaemon {
    private var proc: Process? = null
    private var out: OutputStream? = null
    private val lines = LinkedBlockingQueue<String>()

    private fun stopLocked() {
        try { out?.close() } catch (_: Exception) {}
        try { proc?.destroy() } catch (_: Exception) {}
        proc = null
        out = null
    }

    private fun waitLine(timeoutMs: Long, accept: (String) -> Boolean): String? {
        val end = SystemClock.elapsedRealtime() + timeoutMs
        while (true) {
            val left = end - SystemClock.elapsedRealtime()
            if (left <= 0L) return null
            val l = lines.poll(left, TimeUnit.MILLISECONDS) ?: return null
            if (l == "EOF") return "EOF"
            if (accept(l)) return l
        }
    }

    private fun ensureLocked(apk: String): Boolean {
        val p = proc
        if (p != null && p.isAlive) return true
        stopLocked()
        return try {
            val np = ProcessBuilder("su").redirectErrorStream(true).start()
            lines.clear()
            val rd = Thread {
                try { BufferedReader(InputStreamReader(np.inputStream)).forEachLine { lines.offer(it) } } catch (_: Exception) {}
                lines.offer("EOF")
            }
            rd.isDaemon = true
            rd.start()
            val o = np.outputStream
            o.write("CLASSPATH=${Root.q(apk)} exec app_process /system/bin com.ashu.ashutool.NetModeMain daemon\n".toByteArray())
            o.flush()
            proc = np
            out = o
            val first = waitLine(8000) { it == "READY" || it.startsWith("ERR") }
            if (first == "READY") true else { stopLocked(); false }
        } catch (e: Exception) {
            stopLocked()
            false
        }
    }

    /** The one line answer, or null when the daemon is not available. */
    @Synchronized
    fun ask(apk: String, line: String, timeoutMs: Long): String? {
        if (!ensureLocked(apk)) return null
        lines.clear()
        try {
            out?.write((line + "\n").toByteArray())
            out?.flush()
        } catch (e: Exception) {
            stopLocked()
            return null
        }
        val a = waitLine(timeoutMs) { it.startsWith("NR=") || it == "OK" || it == "PONG" || it.startsWith("ERR") }
        if (a == null || a == "EOF") { stopLocked(); return null }
        return a
    }

    @Synchronized
    fun stop() = stopLocked()
}

/** Switches 5G on and off from the app and from the quick settings tile. */
object NetMode {
    private fun subId(): Int {
        val d = SubscriptionManager.getDefaultDataSubscriptionId()
        return if (d > 0) d else SubscriptionManager.getDefaultSubscriptionId()
    }

    /** What the phone service said the last time, kept for Copy debug info. Empty before the first try. */
    @Volatile var lastRaw: String = ""

    /** The last known state, so a tile is drawn right the moment the panel opens. Null before the first answer. */
    @Volatile var cached: Boolean? = null

    private val worker = Executors.newSingleThreadExecutor { r -> Thread(r, "ashu-net").also { it.isDaemon = true } }

    /** The saved state from the last run of the app, used until the phone has answered. */
    fun known(ctx: Context): Boolean? {
        cached?.let { return it }
        val s = LockStore.int(ctx, "net5g_last", -1)
        return if (s < 0) null else (s == 1)
    }

    private fun remember(ctx: Context, v: Boolean) {
        cached = v
        LockStore.setInt(ctx, "net5g_last", if (v) 1 else 0)
    }

    /** Starts the root process in the background, so the first tap is as fast as the rest. */
    fun warm(ctx: Context) {
        val app = ctx.applicationContext
        worker.execute { try { NetDaemon.ask(app.applicationInfo.sourceDir, "ping", 9000) } catch (_: Exception) {} }
    }

    /** Stops the root process. It starts again by itself on the next request. */
    fun shutdown() = NetDaemon.stop()

    private fun run(ctx: Context, mode: String): String {
        val apk = ctx.applicationInfo.sourceDir
        val sub = subId()
        // The daemon answers in a few milliseconds. The slow one shot way is only the fallback.
        val fast = NetDaemon.ask(apk, "$mode $sub", 6000)
        val out = fast ?: Root.run("CLASSPATH=${Root.q(apk)} app_process /system/bin com.ashu.ashutool.NetModeMain $mode $sub 2>&1").joinToString("\n")
        lastRaw = "$mode sub=$sub${if (fast != null) " (fast)" else ""}: ${out.trim().take(220)}"
        return out
    }

    /** true when 5G is allowed, false when it is switched off, null when this phone does not say. */
    fun query(ctx: Context): Boolean? {
        val v = Regex("NR=(\\d)").find(run(ctx, "get"))?.groupValues?.get(1)?.let { it == "1" }
        if (v != null) remember(ctx, v)
        return v
    }

    /** Empty text when it worked, otherwise the reason it did not. Call it off the main thread. */
    fun set(ctx: Context, fiveG: Boolean): String {
        val out = run(ctx, if (fiveG) "set5" else "set4").trim()
        // The runtime can print warning lines before the answer, so the answer is looked for on every line
        if (out.lineSequence().any { it.trim() == "OK" }) { remember(ctx, fiveG); return "" }
        val err = out.lineSequence().firstOrNull { it.startsWith("ERR") } ?: out
        return err.ifEmpty { "No answer from the phone" }
    }

    /**
     * What a tap does: the caller has already drawn the new state. The change is sent in order with any other taps,
     * and [done] gets an empty text when it worked or the reason when it did not.
     */
    fun apply(ctx: Context, fiveG: Boolean, done: (String) -> Unit) {
        val app = ctx.applicationContext
        cached = fiveG
        worker.execute {
            val err = try { if (Root.isGranted()) set(app, fiveG) else "Root is not allowed" } catch (e: Exception) { e.message ?: "Error" }
            if (err.isNotEmpty()) cached = null
            done(err)
        }
    }

    /** Reads the real state in the background and hands it over. */
    fun refresh(ctx: Context, done: (Boolean?) -> Unit) {
        val app = ctx.applicationContext
        worker.execute { done(try { if (Root.isGranted()) query(app) else null } catch (e: Exception) { null }) }
    }
}
