/*
 * Smart Island (2026)
 * © Animesh Gupta — github.com/agupta07505
 * Licensed under the GNU GPL v3 License
 * Do not remove or alter this notice. - Per GPL-3.0 Section 4 & Section 5
 *
 * Rewritten for Ashutool (2026) with the same behaviour. See smartisland/REWRITE.md.
 */

package com.agupta07505.smartisland.util

/**
 * Tries the ways of delivering one PendingIntent, one after the other, and tells whether one of them worked.
 * It has no Android types in it.
 *
 * A clock app swaps the intents of its buttons when it posts again, so an old one throws when it is sent, and the
 * caller has to know about that.
 */
object SendAttempts {
    /** True as soon as an attempt finishes without throwing. The attempts after it are not run. */
    fun anySucceeds(attempts: List<() -> Unit>): Boolean = attempts.any { delivers(it) }

    private fun delivers(attempt: () -> Unit): Boolean {
        return try {
            attempt()
            true
        } catch (_: Exception) {
            false
        }
    }
}
