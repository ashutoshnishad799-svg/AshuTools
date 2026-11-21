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
 * While a card is swiped sideways, one of the two recorded layers may keep showing the card that is leaving.
 * This picks that layer. See ui/IslandCardHost.kt.
 */
object SwipeGhost {
    /**
     * @param last the layer drawn last (0 or 1)
     * @param keys the card key each layer holds
     * @param from the key of the card that is leaving
     * @return the layer that holds the leaving card, or -1 when none does: a plain cut looks better than the wrong card
     */
    fun layerToPark(last: Int, keys: Array<String?>, from: String?): Int {
        if (from == null) return -1
        if (last != 0 && last != 1) return -1
        val other = 1 - last
        return when {
            keys[last] == from -> last
            // the last frame already shows the new card, so the old one is still on the other layer
            keys[other] == from -> other
            else -> -1
        }
    }
}
