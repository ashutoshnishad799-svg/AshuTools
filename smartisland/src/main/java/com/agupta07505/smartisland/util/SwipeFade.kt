/*
 * Smart Island (2026)
 * © Animesh Gupta — github.com/agupta07505
 * Licensed under the GNU GPL v3 License
 * Do not remove or alter this notice. - Per GPL-3.0 Section 4 & Section 5
 *
 * Rewritten for Ashutool (2026) with the same behaviour. See smartisland/REWRITE.md.
 */

package com.agupta07505.smartisland.util

import kotlin.math.abs

/**
 * Fades a swiped card: the further it is pushed from the middle the fainter it gets, like swipe to dismiss in Android.
 */
object SwipeFade {
    /** The faintest the expanded card gets, at its slide limit or beyond. */
    const val CARD_MIN_ALPHA = 0.3f

    /**
     * Opacity for a card [offsetPx] away from the middle: 1 in the middle, [minAlpha] at [maxPx] or more, and a smooth
     * ease in and out between. The sign of the offset does not matter.
     */
    fun alpha(offsetPx: Float, maxPx: Float, minAlpha: Float): Float {
        if (maxPx <= 0f) return 1f
        val t = (abs(offsetPx) / maxPx).coerceIn(0f, 1f)
        val smooth = t * t * (3f - 2f * t)
        return 1f - (1f - minAlpha) * smooth
    }
}
