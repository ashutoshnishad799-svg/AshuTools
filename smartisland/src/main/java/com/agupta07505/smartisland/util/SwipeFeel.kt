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
 * The numbers that make swipes feel calm, all in one place so they are easy to tune. The numbers for the stretch, the
 * rubber band and the lean live next to their maths: [SwipeDownPull] and `RUBBER_BAND_*` in ExpandedSwipe.kt.
 */
object SwipeFeel {
    /**
     * Every spring that lets go of a temporary swipe state (the swelling of the arm, the shrinking of the dead zone, the
     * lock cue) has this damping. 1 is critical: it comes to rest and does not overshoot.
     */
    const val RETURN_DAMPING_RATIO = 1f

    /** The "medium" stiffness of Compose. */
    const val RETURN_STIFFNESS = 400f

    /**
     * The stiffness of the springs that bring the drag of a swipe back to rest (shell, content, lean, pull, card). It is a
     * third of StiffnessMedium (1500) and still critically damped, so the release glides.
     */
    const val EASE_BACK_STIFFNESS = 550f

    /** How far in dp the collapsed pill and the corner pill lean down or up with the finger (35 % of the drag, with a limit). */
    const val PILL_LEAN_Y_MAX_DP = 6f

    /** The small squash when a switch puts another card in the island: a shallow dip, then a return that hardly overshoots. */
    const val SWITCH_SQUASH_SCALE = 0.96f
    const val SWITCH_RETURN_DAMPING_RATIO = 0.8f
    const val SWITCH_RETURN_STIFFNESS = 600f
}
