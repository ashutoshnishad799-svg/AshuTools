/*
 * Smart Island (2026)
 * © Animesh Gupta — github.com/agupta07505
 * Licensed under the GNU GPL v3 License
 * Do not remove or alter this notice. - Per GPL-3.0 Section 4 & Section 5
 *
 * Rewritten for Ashutool (2026) with the same behaviour. See smartisland/REWRITE.md.
 */

package com.agupta07505.smartisland.model

/**
 * Limits the little pulse the collapsed island gives when something arrives: two pulses are at least [MIN_GAP_MS]
 * apart, so a burst of cards gives one pulse and not a flutter.
 */
object PillPulse {
    const val MIN_GAP_MS = 2_000L

    fun allowed(nowMs: Long, lastPulseMs: Long): Boolean {
        val sinceLast = nowMs - lastPulseMs
        return sinceLast >= MIN_GAP_MS
    }
}
