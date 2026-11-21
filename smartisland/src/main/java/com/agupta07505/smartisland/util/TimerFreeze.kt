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

/** Works out how much time a timer has left, from everything that is known about it. The first rule that applies wins. */
object TimerFreeze {
    /** Two readings of the shade that differ by no more than this are the same reading. */
    private const val SHADE_TOLERANCE_MS = 2_000L

    fun remainingMs(
        nowMs: Long,
        rememberedFrozenMs: Long?,
        runningTargetMs: Long?,
        keptFrozenMs: Long?,
        textRemainingMs: Long?,
        shadeShownMs: Long? = null,
        shadeExactMs: Long? = null,
        shownMs: Long? = null,
        previousShownMs: Long? = null
    ): Long {
        // the shade gave an exact value
        if (shadeExactMs != null && shadeExactMs > 0L) return shadeExactMs

        // the shade shows a value we did not expect: the timer was resumed and paused without the island seeing it
        if (shadeShownMs != null && shadeShownMs > 0L &&
            (rememberedFrozenMs == null || abs(shadeShownMs - rememberedFrozenMs) > SHADE_TOLERANCE_MS)
        ) {
            return shadeShownMs
        }

        // time was added in the app while the timer was paused
        if (rememberedFrozenMs != null && shownMs != null && previousShownMs != null &&
            shownMs - previousShownMs > SHADE_TOLERANCE_MS
        ) {
            return rememberedFrozenMs + (shownMs - previousShownMs)
        }

        if (rememberedFrozenMs != null) return rememberedFrozenMs
        if (runningTargetMs != null && runningTargetMs > nowMs) return runningTargetMs - nowMs
        if (keptFrozenMs != null) return keptFrozenMs
        if (textRemainingMs != null && textRemainingMs > 0L) return textRemainingMs
        return 0L
    }
}
