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

/**
 * Google Pay, PhonePe and many banking apps refuse to work while any accessibility service is on ("Payments blocked").
 * While one of them is in front, Ashutool switches its own accessibility service off through root, and switches it on again
 * when you leave. The island and the other things that use the service wait until then.
 *
 * The foreground app is reported by [Fg]: by the service itself while it runs and by the usage access of the monitor
 * service once it is off, so the way back is always seen.
 */
object PaymentShield {
    private val PAYMENT = setOf(
        "com.google.android.apps.nbu.paisa.user", "com.phonepe.app", "net.one97.paytm", "in.org.npci.upiapp",
        "com.dreamplug.androidapp", "in.amazon.mShop.android.shopping", "com.mobikwik_new", "com.freecharge.android",
        "com.sbi.upi", "com.sbi.SBIFreedomPlus", "com.csam.icici.bank.imobile", "com.snapwork.hdfc", "com.axis.mobile",
        "com.upi.axispay", "com.infrasofttech.centralbankupi", "com.fampay.in", "com.jar.app", "money.super.app"
    )

    /** Screens of a payment that belong to another app, so leaving them for these does not end the pause. */
    private val COMPANIONS = setOf("com.google.android.gms", "com.android.chrome", "com.google.android.permissioncontroller", "com.android.systemui")

    private val NAME = Regex("(bank|upi|paisa|bhim|netbanking|mobilebanking)")

    @Volatile var engaged = false
        private set
    @Volatile private var changedAt = 0L
    private var app: Context? = null

    fun init(ctx: Context) { app = ctx.applicationContext }

    fun isPayment(pkg: String): Boolean = pkg in PAYMENT || NAME.containsMatchIn(pkg)

    /** The app in front changed. Called from [Fg.set]. */
    fun onFg(pkg: String) {
        val c = app ?: return
        if (pkg == c.packageName || !LockStore.bool(c, "pay_shield", true)) return
        val now = System.currentTimeMillis()
        if (!engaged) {
            if (isPayment(pkg)) {
                engaged = true
                changedAt = now
                Thread { Root.setA11y(c, false) }.start()
            }
        } else if (!isPayment(pkg) && pkg !in COMPANIONS && now - changedAt > 2500L) {
            engaged = false
            changedAt = now
            Thread { Root.ensureA11y(c) }.start()
        }
    }
}
