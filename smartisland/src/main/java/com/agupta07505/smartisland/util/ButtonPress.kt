/*
 * Smart Island (2026)
 * © Animesh Gupta — github.com/agupta07505
 * Licensed under the GNU GPL v3 License
 * Do not remove or alter this notice. - Per GPL-3.0 Section 4 & Section 5
 *
 * Rewritten for Ashutool (2026) with the same behaviour. See smartisland/REWRITE.md.
 */

package com.agupta07505.smartisland.util

/** Decides whether a press on a button of a clock notification can be passed on to the app. */
object ButtonPress {

    /** Why a press is not sent. */
    enum class Refusal {
        /** The button has no intent. */
        NoIntent,

        /** The press goes through the live view of the notification and that button is no longer in it. */
        ViewGone
    }

    /** Null when the press may be sent. */
    fun refusal(hasIntent: Boolean, viaView: Boolean, liveLabels: List<String>, title: String): Refusal? {
        if (!hasIntent) return Refusal.NoIntent
        if (viaView && !hasLive(liveLabels, title)) return Refusal.ViewGone
        return null
    }

    /** True when a live button carries the label [title], ignoring case and spaces around it. */
    fun hasLive(liveLabels: List<String>, title: String): Boolean {
        val wanted = title.trim()
        return liveLabels.any { label -> label.trim().equals(wanted, ignoreCase = true) }
    }
}
