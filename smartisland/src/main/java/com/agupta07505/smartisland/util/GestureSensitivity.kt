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

/** The four directions a swipe on the pill can have. */
enum class PillSwipeDirection { Left, Right, Up, Down }

/**
 * How easily the gestures start.
 *
 * @property id the value stored in the settings
 * @property distanceScale multiplies the distance a swipe has to cover
 * @property dominance how much longer the main axis has to be than the other one
 */
enum class GestureSensitivity(val id: String, val distanceScale: Float, val dominance: Float) {
    LOW("low", 1.45f, 1.6f),
    NORMAL("normal", 1f, 1f),
    HIGH("high", 0.7f, 1f);

    fun scaled(dp: Float): Float = dp * distanceScale

    fun isHorizontal(dx: Float, dy: Float): Boolean = abs(dx) > abs(dy) * dominance

    fun isVertical(dx: Float, dy: Float): Boolean = abs(dy) >= abs(dx) * dominance

    /**
     * The direction of a swipe, or null while the drag is too short or too diagonal. The thresholds are in the same unit
     * as the drag.
     */
    fun pillSwipe(dx: Float, dy: Float, threshold: Float, horizontalThreshold: Float = threshold): PillSwipeDirection? {
        if (abs(dx) >= horizontalThreshold && isHorizontal(dx, dy)) {
            return if (dx < 0f) PillSwipeDirection.Left else PillSwipeDirection.Right
        }
        if (abs(dy) >= threshold && isVertical(dx, dy)) {
            return if (dy < 0f) PillSwipeDirection.Up else PillSwipeDirection.Down
        }
        return null
    }

    companion object {
        const val ID_LOW = "low"
        const val ID_NORMAL = "normal"
        const val ID_HIGH = "high"

        val VALID_IDS: Set<String> = entries.map { it.id }.toSet()

        /** The sensitivity stored under [id]; normal when it is missing or unknown. */
        fun fromId(id: String?): GestureSensitivity = entries.find { it.id == id } ?: NORMAL
    }
}
