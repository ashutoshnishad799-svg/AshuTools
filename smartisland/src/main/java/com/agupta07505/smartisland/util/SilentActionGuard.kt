/*
 * Smart Island (2026)
 * © Animesh Gupta — github.com/agupta07505
 * Licensed under the GNU GPL v3 License
 * Do not remove or alter this notice. - Per GPL-3.0 Section 4 & Section 5
 *
 * Rewritten for Ashutool (2026) with the same behaviour. See smartisland/REWRITE.md.
 */

package com.agupta07505.smartisland.util

import android.os.SystemClock

/**
 * Lets a button of the island do its job quietly, without taking the user out of the app they are in.
 *
 * Some apps, voice recorders above all, give their Save or Stop button a PendingIntent that opens an *activity*, so
 * pressing it brings the app to the front. [arm] is called just before such a button is pressed. When the accessibility
 * service then sees a window of that package open within [WINDOW_MS], [consume] returns true once and the service sends
 * the user back.
 */
object SilentActionGuard {
    const val WINDOW_MS = 2500L

    @Volatile private var armedPackage: String? = null
    @Volatile private var expiresAt = 0L

    fun arm(packageName: String, now: Long = SystemClock.elapsedRealtime()) {
        armedPackage = packageName
        expiresAt = now + WINDOW_MS
    }

    fun disarm() {
        armedPackage = null
        expiresAt = 0L
    }

    /** True exactly once: when [openedPackage] is the armed package and the time has not run out. */
    fun consume(openedPackage: String, now: Long = SystemClock.elapsedRealtime()): Boolean {
        val armed = armedPackage ?: return false
        if (now > expiresAt) {
            disarm()
            return false
        }
        if (armed != openedPackage) return false
        disarm()
        return true
    }
}
