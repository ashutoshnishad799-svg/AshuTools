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
 * The card and the picker next to it trade places in size, on ONE progress value (0 is the card, 1 is the picker). A
 * close that starts half way through an open turns around where it is, so both directions are mirrors of each other.
 */
object PickerSwap {
    /** The length of the swap while the picker opens, at normal speed. */
    const val OPEN_MS = 280

    /** The length while it closes, a little shorter: the picker is being put away. */
    const val CLOSE_MS = 240

    /** In landscape the two islands run the ONE progress on a spring, without a bounce. */
    const val SPLIT_OPEN_STIFFNESS = 400f
    const val SPLIT_CLOSE_STIFFNESS = 520f

    /** The card is gone (alpha 0) when the progress has reached this share. */
    const val CARD_GONE_AT = 0.6f

    /** The picker begins to show after this share: they overlap a little but are never two solid layers. */
    const val PICKER_IN_FROM = 0.3f

    /** At progress 1 the card is shrunk to this scale, and the picker starts at this scale at progress 0. */
    const val END_SCALE = 0.92f

    fun cardAlpha(progress: Float): Float = 1f - (progress / CARD_GONE_AT).coerceIn(0f, 1f)

    fun pickerAlpha(progress: Float): Float {
        val fraction = (progress - PICKER_IN_FROM) / (1f - PICKER_IN_FROM)
        return fraction.coerceIn(0f, 1f)
    }

    fun cardScale(progress: Float): Float = 1f - (1f - END_SCALE) * progress.coerceIn(0f, 1f)

    fun pickerScale(progress: Float): Float = END_SCALE + (1f - END_SCALE) * progress.coerceIn(0f, 1f)

    /** The card layer is drawn unless the picker is open and the swap is over. */
    fun cardLayerShown(progress: Float, pickerOpen: Boolean): Boolean = !pickerOpen || progress < 0.999f

    /** The picker layer is drawn when the picker is open or the swap has begun. */
    fun pickerLayerShown(progress: Float, pickerOpen: Boolean): Boolean = pickerOpen || progress > 0.001f

    fun lengthMs(opening: Boolean): Int = if (opening) OPEN_MS else CLOSE_MS
}
