/*
 * Smart Island (2026)
 * © Animesh Gupta — github.com/agupta07505
 * Licensed under the GNU GPL v3 License
 * Do not remove or alter this notice. - Per GPL-3.0 Section 4 & Section 5
 *
 * Rewritten for Ashutool (2026) with the same behaviour. See smartisland/REWRITE.md.
 */

package com.agupta07505.smartisland.util

import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/** The time a notification arrived, as hours and minutes on a 24 hour clock. */
fun formatNotificationTime(timeMillis: Long): String {
    val format = SimpleDateFormat("HH:mm", Locale.getDefault())
    return format.format(Date(timeMillis))
}
