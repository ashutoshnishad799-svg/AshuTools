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
 * The way the small round cutout joins the main one when the two merge (Layout tab).
 *
 * @property id the value stored in the settings
 * @property baseDurationMs the length of the merge at normal animation speed
 */
enum class MergeStyle(val id: String, val baseDurationMs: Int) {
    SLIDE("slide", 420),
    BUBBLE("bubble", 900);

    companion object {
        const val ID_SLIDE = "slide"
        const val ID_BUBBLE = "bubble"

        val VALID_IDS: Set<String> = entries.map { it.id }.toSet()

        /** The style stored under [id]; slide when the id is missing or unknown. */
        fun fromId(id: String?): MergeStyle = entries.find { it.id == id } ?: SLIDE
    }
}
