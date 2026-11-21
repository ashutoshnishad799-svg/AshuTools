/*
 * Smart Island (2026)
 * © Animesh Gupta — github.com/agupta07505
 * Licensed under the GNU GPL v3 License
 * Do not remove or alter this notice. - Per GPL-3.0 Section 4 & Section 5
 *
 * Rewritten for Ashutool (2026) with the same behaviour. See smartisland/REWRITE.md.
 */

package com.agupta07505.smartisland.model

/** The expanded card grows out of the centre of the collapsed pill. These are the numbers for one axis. */
object ExpandOrigin {
    /** Where the pill centre sits inside the card, from 0 (start edge) to 1 (end edge). Half when the card has no size. */
    fun anchorInCard(pillCentre: Float, cardStart: Float, cardSize: Float): Float {
        if (cardSize <= 0f) return 0.5f
        val position = (pillCentre - cardStart) / cardSize
        return position.coerceIn(0f, 1f)
    }

    /** The anchor when the card is fully open: the pill centre, moved inside the card if it lies outside. */
    fun anchorAtEnd(pillCentre: Float, cardStart: Float, cardSize: Float): Float {
        val cardEnd = cardStart + maxOf(cardSize, 0f)
        return pillCentre.coerceIn(cardStart, cardEnd)
    }

    /**
     * The start edge of the island on this axis.
     * @param progress how far the opening is, 0 to 1
     * @param size the size of the island on this axis right now (it follows its own spring)
     */
    fun start(
        progress: Float,
        size: Float,
        pillCentre: Float,
        cardStart: Float,
        cardSize: Float
    ): Float {
        val endAnchor = anchorAtEnd(pillCentre, cardStart, cardSize)
        val anchor = pillCentre + (endAnchor - pillCentre) * progress
        val anchorShare = 0.5f + (anchorInCard(pillCentre, cardStart, cardSize) - 0.5f) * progress
        return anchor - anchorShare * size
    }
}
