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
 * When to look again for a picture that arrives late. Chat apps post the message first and the picture a moment
 * later: the link is empty until the download ends, or the file is not in the folder of the app yet.
 */
object PictureRetryPlan {
    /** The wait before each try in ms: the first at once, then longer gaps, about 30 seconds in all. */
    val DELAYS_MS: List<Long> = listOf(0L, 1_500L, 4_000L, 9_000L, 16_000L)

    /**
     * True when it is worth looking again after the quick read found no picture: the notification has an image link,
     * has a big picture, or its text is just a label such as "Photo".
     */
    fun expectsPicture(hasImageLink: Boolean, hasBigPicture: Boolean, textIsPictureLabel: Boolean): Boolean {
        return hasImageLink || hasBigPicture || textIsPictureLabel
    }

    /** True when there is another try after the one at [attemptIndex]. */
    fun hasNextAttempt(attemptIndex: Int): Boolean = attemptIndex < DELAYS_MS.size - 1
}
