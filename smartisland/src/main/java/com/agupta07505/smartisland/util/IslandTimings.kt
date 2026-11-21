/*
 * Smart Island (2026)
 * © Animesh Gupta — github.com/agupta07505
 * Licensed under the GNU GPL v3 License
 * Do not remove or alter this notice. - Per GPL-3.0 Section 4 & Section 5
 *
 * Rewritten for Ashutool (2026) with the same behaviour. See smartisland/REWRITE.md.
 */

package com.agupta07505.smartisland.util

/** How long short events stay on the island before they leave by themselves. */
object IslandTimings {
    const val PLUGGED_IN_CARD_MS = 2_000L

    const val UNPLUGGED_CARD_MS = 2_200L

    /** The low battery, battery saver and fully charged warnings leave after this long. */
    const val BATTERY_WARNING_CARD_MS = 4_000L

    /** The Wi-Fi and VPN cards. */
    const val CONNECTIVITY_CARD_MS = 2_500L

    /** The "disconnected" cards of Wi-Fi and VPN stay a little longer than the connected ones, as they are the ones to notice. */
    const val DISCONNECT_CARD_MS = 3_200L

    /** A transfer that has finished (download, backup, copy and so on) stays this long. */
    const val TRANSFER_DONE_MS = 2_500L

    /** A finished download that was taken from the shade for the cutout of the same app stays this long. */
    const val TRANSFER_DONE_IMPORTED_MS = 60_000L

    /** A transfer that failed stays a little longer, so the failure can be read. */
    const val TRANSFER_FAILED_MS = 5_000L

    /** A screen or audio recording that has finished leaves at once. */
    const val RECORDING_DONE_MS = 0L

    /**
     * A short event (a notification, the battery, a connection) keeps the main cutout this long when a card with a higher
     * priority is live. After that the order of priority applies again.
     */
    const val POPUP_MS = 1_800L

    /** When only ordinary notification cards are left, the island goes back to the idle cutout after this long. */
    const val STATIC_IDLE_TIMEOUT_MS = 5_000L

    /** While collapsed, the small round cutout joins the main one after this long without any touch. */
    const val SECONDARY_IDLE_MS = 1_800L
}
