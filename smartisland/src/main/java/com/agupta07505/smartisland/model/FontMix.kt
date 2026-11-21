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
 * "Mix fonts": the text of the island can use up to three fonts, like the Light, Regular and Medium cuts of a system
 * family. The weight that is asked for decides which font is used.
 */
enum class FontSlot(
    /** The weight whose file is used for the built in fonts (400, 500 or 700). */
    val representativeWeight: Int,
    /** The weights from 100 to 900, as in CSS, that this slot draws. The three slots together cover all nine. */
    val weights: List<Int>
) {
    Light(400, listOf(100, 200, 300, 400)),
    Regular(500, listOf(500, 600)),
    Medium(700, listOf(700, 800, 900))
}

object FontMix {
    /** The slot for a weight (Compose limits weights to 1..1000). */
    fun slotFor(weight: Int): FontSlot = when {
        weight <= 400 -> FontSlot.Light
        weight <= 600 -> FontSlot.Regular
        else -> FontSlot.Medium
    }

    /** The font a slot really uses: its own when it has one, otherwise the font of the island. */
    fun effectiveId(slotId: String, islandFont: String): String = if (slotId.isEmpty()) islandFont else slotId

    fun idOf(slot: FontSlot, light: String, regular: String, medium: String): String = when (slot) {
        FontSlot.Light -> light
        FontSlot.Regular -> regular
        FontSlot.Medium -> medium
    }

    /** True only when the mix is switched on and at least one slot has a font of its own. */
    fun isActive(enabled: Boolean, light: String, regular: String, medium: String): Boolean {
        if (!enabled) return false
        return light.isNotEmpty() || regular.isNotEmpty() || medium.isNotEmpty()
    }
}
