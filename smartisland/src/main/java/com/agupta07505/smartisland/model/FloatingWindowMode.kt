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
 * The engine that opens the floating window.
 *  - [System]: the freeform windows of Android itself. The ROM must support it, or developer options must allow it.
 *  - [Lmo]: LMO Freeform (`com.libremobileos.freeform`), started with its exported START_FREEFORM broadcast.
 */
enum class FloatingWindowMode(val id: String) {
    System("system"),
    Lmo("lmo");

    companion object {
        const val ID_SYSTEM = "system"
        const val ID_LMO = "lmo"
        val VALID_IDS: Set<String> = entries.map { it.id }.toSet()

        /** The mode stored under [id]; the system one when it is missing or unknown. */
        fun fromId(id: String?): FloatingWindowMode = entries.find { it.id == id } ?: System
    }
}

/** What happens when something asks for a floating window. It follows from the settings alone, so it can be tested without Android. */
enum class FloatingWindowRoute { Off, System, Lmo }

fun floatingWindowRoute(enabled: Boolean, modeId: String?): FloatingWindowRoute {
    if (!enabled) return FloatingWindowRoute.Off
    return if (FloatingWindowMode.fromId(modeId) == FloatingWindowMode.Lmo) FloatingWindowRoute.Lmo else FloatingWindowRoute.System
}
