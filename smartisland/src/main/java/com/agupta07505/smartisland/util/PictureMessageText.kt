/*
 * Smart Island (2026)
 * © Animesh Gupta — github.com/agupta07505
 * Licensed under the GNU GPL v3 License
 * Do not remove or alter this notice. - Per GPL-3.0 Section 4 & Section 5
 *
 * Rewritten for Ashutool (2026) with the same behaviour. See smartisland/REWRITE.md.
 */

package com.agupta07505.smartisland.util

/** One chat message of a notification: its text, and whether it has a picture or a sticker with it. */
internal data class MessageLine(val text: String, val hasPicture: Boolean)

private const val MAX_READER_LINES = 6
private const val MAX_READER_CHARS = 1500

/** The chat text that the reader shows beside the pictures of a message: only the messages that have no picture. */
internal fun textWithoutPictureMessages(messages: List<MessageLine>): String {
    val lines = ArrayList<String>()
    for (message in messages) {
        if (message.hasPicture) continue
        val text = message.text.trim()
        if (text.isNotEmpty()) lines.add(text)
    }
    return lines.takeLast(MAX_READER_LINES).joinToString("\n").take(MAX_READER_CHARS)
}

/** The words apps use as the text of a picture message, in several languages. */
private val PICTURE_WORDS = setOf(
    "photo", "image", "picture", "pic", "sticker", "animated sticker", "gif", "photo message", "image message",
    "foto", "imagen", "imagem", "immagine", "bild", "image reçue", "photo reçue", "figurinha", "autocollant", "sticker animé",
    "фото", "изображение", "стикер"
)

/** Texts like "2 photos" or "3 new stickers". */
private val PICTURE_COUNT = Regex("^\\d+\\s+(new\\s+)?(photos|images|pictures|stickers|fotos|bilder|images)$")

/**
 * True when [text] is only the label an app puts for a picture ("Photo", "📷 Photo", "Sticker", "2 photos") and not
 * real content. It is used for picture notifications that are not chat messages.
 */
internal fun isPictureLabel(text: String): Boolean {
    val cleaned = text.trim()
        .trimStart { !it.isLetterOrDigit() }
        .trimEnd { !it.isLetterOrDigit() }
        .lowercase()
    if (cleaned.isEmpty()) return true
    return cleaned in PICTURE_WORDS || PICTURE_COUNT.matches(cleaned)
}

/** The last line of the text that is not empty, or an empty text. */
internal fun lastReaderLine(text: String): String =
    text.lineSequence().map { it.trim() }.lastOrNull { it.isNotEmpty() }.orEmpty()
