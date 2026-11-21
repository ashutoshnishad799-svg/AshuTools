/*
 * Smart Island (2026)
 * © Animesh Gupta — github.com/agupta07505
 * Licensed under the GNU GPL v3 License
 * Do not remove or alter this notice. - Per GPL-3.0 Section 4 & Section 5
 *
 * Rewritten for Ashutool (2026) with the same behaviour. See smartisland/REWRITE.md.
 */

package com.agupta07505.smartisland.data

/**
 * One notification kept in the history, as it is stored.
 *
 * @property id row number in the database, 0 until it is stored
 * @property notificationKey the key the system gave the notification
 * @property mode the kind of island card it became, as text
 * @property actionTitles the labels of its buttons
 * @property wasOpened true when the user opened it from the island
 * @property wasDismissed true when the user dismissed it from the island
 */
data class NotificationHistoryEntry(
    val id: Long = 0L,
    val notificationKey: String,
    val packageName: String,
    val appName: String,
    val title: String,
    val text: String,
    val subText: String? = null,
    val postTimeMillis: Long,
    val category: String? = null,
    val channelId: String? = null,
    val mode: String = "Notification",
    val actionTitles: List<String> = emptyList(),
    val wasOpened: Boolean = false,
    val wasDismissed: Boolean = false
)
