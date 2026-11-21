/*
 * Smart Island (2026)
 * © Animesh Gupta — github.com/agupta07505
 * Licensed under the GNU GPL v3 License
 * Do not remove or alter this notice. - Per GPL-3.0 Section 4 & Section 5
 *
 * Rewritten for Ashutool (2026) with the same behaviour. See smartisland/REWRITE.md.
 */

package com.agupta07505.smartisland.model

import kotlinx.coroutines.flow.MutableStateFlow

/**
 * The replies that are half typed, one for each notification key. A draft stays when the user cancels, when the card
 * collapses, when the shade opens, or when another card takes over. It ends when the reply is sent or the notification is gone.
 */
class ReplyDrafts {
    private val drafts = HashMap<String, String>()

    /**
     * The key of the card whose reply box has to open again, so typing can go on after a call took the island. The card
     * opens its box when it shows this key, and the view model clears it once typing is active again.
     */
    val resume = MutableStateFlow<String?>(null)

    @Synchronized
    fun get(key: String): String = drafts[key].orEmpty()

    /** Stores the text. An empty text removes the draft. */
    @Synchronized
    fun set(key: String, text: String) {
        if (text.isEmpty()) {
            drafts.remove(key)
        } else {
            drafts[key] = text
        }
    }

    @Synchronized
    fun clear(key: String) {
        drafts.remove(key)
    }

    /** Drops the drafts of every notification that is not in [liveKeys]. */
    @Synchronized
    fun retainOnly(liveKeys: Set<String>) {
        drafts.keys.retainAll(liveKeys)
    }
}
