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
 * When the content moves while the island opens. The times follow the growth of the shell itself, so they come from
 * its spring.
 */
object ExpandMotion {
    /** The pill is gone when the shell has grown this far (0 to 1). */
    const val PILL_OUT_END_PROGRESS = 0.25f

    /** The card starts to appear at this progress... */
    const val CARD_IN_START_PROGRESS = 0.1f

    /** ...and is fully there at this one. */
    const val CARD_IN_END_PROGRESS = 0.9f

    /** No fade is shorter than this, however stiff the spring is. */
    const val MIN_FADE_MS = 50

    private fun msAt(progress: Float, stiffness: Float, damping: Float): Int =
        springTimeToProgressMs(stiffness, damping, progress).toInt()

    /** How long the pill takes to fade out. */
    fun pillOutMs(stiffness: Float, damping: Float): Int =
        maxOf(msAt(PILL_OUT_END_PROGRESS, stiffness, damping), MIN_FADE_MS)

    /** How long to wait before the card starts to appear. */
    fun cardInDelayMs(stiffness: Float, damping: Float): Int =
        msAt(CARD_IN_START_PROGRESS, stiffness, damping)

    /** How long the card takes to appear, from its start to its end. */
    fun cardInMs(stiffness: Float, damping: Float): Int {
        val start = cardInDelayMs(stiffness, damping)
        val end = msAt(CARD_IN_END_PROGRESS, stiffness, damping)
        return maxOf(end - start, MIN_FADE_MS)
    }
}
