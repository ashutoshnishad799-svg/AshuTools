/*
 * Smart Island (2026)
 * © Animesh Gupta — github.com/agupta07505
 * Licensed under the GNU GPL v3 License
 * Do not remove or alter this notice. - Per GPL-3.0 Section 4 & Section 5
 *
 * Rewritten for Ashutool (2026) with the same behaviour. See smartisland/REWRITE.md.
 */

package com.agupta07505.smartisland.util

import java.util.concurrent.ConcurrentHashMap

/**
 * Remembers, for each notification, the newest post and the removal that were seen, so an old snapshot that arrives late
 * can be told from a current one.
 */
class StalePostGuard {
    private val newestPostTime = ConcurrentHashMap<String, Long>()
    private val removedPostTime = ConcurrentHashMap<String, Long>()

    fun notePosted(key: String, postTime: Long) {
        newestPostTime.merge(key, postTime) { old, new -> maxOf(old, new) }
        trim()
    }

    fun noteRemoved(key: String, postTime: Long) {
        removedPostTime.merge(key, postTime) { old, new -> maxOf(old, new) }
        trim()
    }

    /** True when a snapshot posted at [postTime] is older than a newer post, or was removed already. */
    fun isStale(key: String, postTime: Long): Boolean {
        val newest = newestPostTime[key]
        if (newest != null && postTime < newest) return true
        val removed = removedPostTime[key]
        return removed != null && postTime <= removed
    }

    fun clear() {
        newestPostTime.clear()
        removedPostTime.clear()
    }

    /** Starts again from nothing when either list grows too long, so memory stays small. */
    private fun trim() {
        if (newestPostTime.size > MAX_KEYS || removedPostTime.size > MAX_KEYS) clear()
    }

    private companion object {
        const val MAX_KEYS = 400
    }
}
