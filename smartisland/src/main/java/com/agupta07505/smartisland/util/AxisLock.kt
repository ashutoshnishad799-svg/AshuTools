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
 * Keeps one touch on one axis. When the finger has moved [slopPx] in some direction, the axis it moved further on is
 * kept and the other one is ignored until the finger lifts.
 *
 * Use one instance for each touch. Give it the drag so far with [update] and pass every move through [filterX] and [filterY].
 */
class AxisLock(private val slopPx: Float) {
    enum class Axis { Horizontal, Vertical }

    /** The axis that was chosen, or null while the finger is still within the slop. */
    var axis: Axis? = null
        private set

    /** Gives the drag measured from the touch down. True only for the call that chose the axis. */
    fun update(rawDx: Float, rawDy: Float): Boolean {
        if (axis != null) return false
        val longest = maxOf(abs(rawDx), abs(rawDy))
        if (longest < slopPx) return false
        axis = if (abs(rawDx) > abs(rawDy)) Axis.Horizontal else Axis.Vertical
        return true
    }

    /** The horizontal move, or 0 when the touch is locked to the vertical axis. */
    fun filterX(dx: Float): Float = if (axis == Axis.Vertical) 0f else dx

    /** The vertical move, or 0 when the touch is locked to the horizontal axis. */
    fun filterY(dy: Float): Float = if (axis == Axis.Horizontal) 0f else dy
}
