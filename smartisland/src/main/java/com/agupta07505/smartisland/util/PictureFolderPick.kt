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
 * Chooses the picture that a chat notification is about, from a listing of the media folders of the app. Each line of
 * the listing is `modified|accessed|changed|path`, with the times in seconds.
 *
 * WhatsApp keeps one file for each sticker, so a sticker file can be older than its notification. So for a sticker the
 * newest of the three times counts, files without an extension are accepted, and only folders with "sticker" in the
 * path are used.
 */
internal object PictureFolderPick {
    /** A file that could be the picture. [seconds] is the time that counts, [modified] only breaks a tie. */
    class Candidate(val seconds: Long, val path: String, val modified: Long = seconds)

    /** Paths with one of these words are avatars, icons and the like, never the picture. */
    private val SKIPPED = listOf("avatar", "profile", "thumb", "icon", "/sent/", "/private/", "emoji", "logo")

    /** How long before the notification a file may have been written and still count. */
    private const val SLACK_MS = 120_000L

    fun parse(listing: String, sticker: Boolean): List<Candidate> {
        val found = ArrayList<Candidate>()
        for (line in listing.lineSequence()) {
            val parts = line.split('|', limit = 4)
            if (parts.size < 4) continue
            val modified = parts[0].trim().toLongOrNull() ?: continue
            val accessed = parts[1].trim().toLongOrNull() ?: 0L
            val changed = parts[2].trim().toLongOrNull() ?: 0L
            val seconds = if (sticker) maxOf(modified, accessed, changed) else modified
            found.add(Candidate(seconds, parts[3], modified))
        }
        return found
    }

    fun pick(listing: String, postedAtMs: Long, sticker: Boolean): Candidate? {
        val oldest = postedAtMs - SLACK_MS
        return parse(listing, sticker)
            .filter { it.seconds * 1000L >= oldest }
            .filter { c -> SKIPPED.none { c.path.contains(it, ignoreCase = true) } }
            .filter { c -> !sticker || c.path.contains("sticker", ignoreCase = true) }
            // with equal newest times the file that was modified later wins
            .maxWithOrNull(compareBy<Candidate>({ it.seconds }, { it.modified }))
    }
}
