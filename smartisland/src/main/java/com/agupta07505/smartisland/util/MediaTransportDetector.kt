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
 * Tells a media player's notification by its buttons alone: one for previous, one for play or pause, one for next.
 *
 * Most players use MediaStyle, but video apps, picture in picture players and some background players draw their own
 * buttons and also keep a progress bar. Without this check they look like a download (a bar with Pause and Stop) and
 * the island would say "Downloading".
 */
object MediaTransportDetector {
    private val PREVIOUS = listOf("previous", "prev", "rewind", "anterior", "précédent", "vorherige", "precedente", "上一")
    private val NEXT = listOf("next", "skip", "siguiente", "suivant", "nächste", "successivo", "下一")
    private val PLAY_PAUSE = listOf("pause", "play", "pausa", "reproducir", "lecture", "wiedergabe", "暂停", "播放")

    /** True when the labels contain a previous button, a play or pause button and a next button. */
    fun hasTransportButtons(labels: List<String>): Boolean {
        val lower = labels.map { it.lowercase() }
        return mentionsAny(lower, PREVIOUS) && mentionsAny(lower, NEXT) && mentionsAny(lower, PLAY_PAUSE)
    }

    private fun mentionsAny(labels: List<String>, words: List<String>): Boolean =
        labels.any { label -> words.any { word -> label.contains(word) } }
}
