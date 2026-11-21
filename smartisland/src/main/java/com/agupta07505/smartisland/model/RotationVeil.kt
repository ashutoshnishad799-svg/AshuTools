/*
 * Smart Island (2026)
 * © Animesh Gupta — github.com/agupta07505
 * Licensed under the GNU GPL v3 License
 * Do not remove or alter this notice. - Per GPL-3.0 Section 4 & Section 5
 *
 * Rewritten for Ashutool (2026) with the same behaviour. See smartisland/REWRITE.md.
 */

package com.agupta07505.smartisland.model

/** Hides the island while the phone turns, until the new orientation is settled. */
object RotationVeil {
    /** The wait before each read of the orientation after a turn, in ms. The last read is the settled one. */
    val REFRESH_GAPS_MS: LongArray = longArrayOf(0L, 150L, 450L)

    /** When the settled read happens, in ms after the turn. */
    val SETTLED_READ_AT_MS: Long = REFRESH_GAPS_MS.sum()

    /** A short wait after the last read, so the window has the settled size before the island is drawn again. */
    const val MARGIN_MS: Long = 60L

    /** Whatever the system does, the island is not hidden for longer than this. */
    const val MAX_HIDDEN_MS: Long = 700L

    /** The length of the fade out at normal animation speed. */
    const val HIDE_MS: Int = 90

    /**
     * How long to wait after the last orientation read before the veil is lifted.
     * @param hiddenForMs how long the veil has been down so far
     */
    fun liftDelayMs(hiddenForMs: Long): Long {
        val left = MAX_HIDDEN_MS - hiddenForMs
        return maxOf(minOf(MARGIN_MS, left), 0L)
    }

    /** The opacity of the island for a veil level (0 hidden, 1 shown), with the same curve the pill uses to arrive. */
    fun alpha(visible: Float): Float = pillExitAlpha(pillSlideProgress(visible))
}
