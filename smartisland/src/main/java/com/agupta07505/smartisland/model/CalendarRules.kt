/*
 * Smart Island (2026)
 * © Animesh Gupta — github.com/agupta07505
 * Licensed under the GNU GPL v3 License
 * Do not remove or alter this notice. - Per GPL-3.0 Section 4 & Section 5
 *
 * Rewritten for Ashutool (2026) with the same behaviour. See smartisland/REWRITE.md.
 */

package com.agupta07505.smartisland.model

/** An upcoming calendar event: its title and when it starts and ends, in ms. */
data class NextEvent(val title: String, val beginMs: Long, val endMs: Long)

/**
 * The rules for the next event on the half opened island of the date widget: only the next 24 hours count.
 * The calendar itself is read in util/CalendarReader.kt.
 */
object CalendarRules {
    const val LOOK_AHEAD_MS = 24L * 60L * 60L * 1000L

    const val ISLAND_EVENT_HEIGHT_DP = 96f

    private val whitespace = Regex("\\s+")

    /** True when an event starting at [beginMs] has not begun yet and starts within the next 24 hours. */
    fun islandShows(beginMs: Long, nowMs: Long): Boolean {
        val untilStart = beginMs - nowMs
        return untilStart > 0L && untilStart <= LOOK_AHEAD_MS
    }

    /** The event that starts first among those that have not started, or null when there is none. */
    fun pickNext(events: List<NextEvent>, nowMs: Long): NextEvent? =
        events.filter { it.beginMs > nowMs }.minByOrNull { it.beginMs }

    /** The title cut to [max] characters with an ellipsis. An empty title becomes "Event". */
    fun shortTitle(title: String, max: Int): String {
        val clean = title.trim().replace(whitespace, " ")
        if (clean.isEmpty()) return "Event"
        if (clean.length <= max) return clean
        return clean.take((max - 1).coerceAtLeast(1)).trimEnd() + "\u2026"
    }
}
