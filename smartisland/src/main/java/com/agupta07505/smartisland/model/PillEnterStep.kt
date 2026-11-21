/*
 * Smart Island (2026)
 * © Animesh Gupta — github.com/agupta07505
 * Licensed under the GNU GPL v3 License
 * Do not remove or alter this notice. - Per GPL-3.0 Section 4 & Section 5
 *
 * Rewritten for Ashutool (2026) with the same behaviour. See smartisland/REWRITE.md.
 */

package com.agupta07505.smartisland.model

import kotlin.math.abs

/**
 * What the sliding entrance of the pill has to do when the card or widget it shows changes, or when the content
 * layer comes back ([IslandInteractionState.pillEnter]).
 */
enum class PillEnterStep {
    /** A swipe asked for this card: slide in from the side opposite to the swipe. */
    Start,

    /** No swipe is waiting but an earlier slide was cut short: ease the offset that is left back to 0. */
    SettleStale,

    /** Nothing to do. */
    Nothing
}

/** An offset smaller than this many px is the tail of an animation at rest, not a slide that got stuck. */
const val PILL_ENTER_REST_PX = 0.5f

fun pillEnterStep(pendingEnterSign: Int, enterOffsetPx: Float): PillEnterStep {
    if (pendingEnterSign != 0) return PillEnterStep.Start
    if (abs(enterOffsetPx) > PILL_ENTER_REST_PX) return PillEnterStep.SettleStale
    return PillEnterStep.Nothing
}
