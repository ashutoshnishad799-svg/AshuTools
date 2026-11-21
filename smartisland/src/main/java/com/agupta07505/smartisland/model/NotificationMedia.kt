/*
 * Smart Island (2026)
 * © Animesh Gupta — github.com/agupta07505
 * Licensed under the GNU GPL v3 License
 * Do not remove or alter this notice. - Per GPL-3.0 Section 4 & Section 5
 *
 * Rewritten for Ashutool (2026) with the same behaviour. See smartisland/REWRITE.md.
 */

package com.agupta07505.smartisland.model

import android.graphics.Bitmap

/**
 * A picture or a sticker that came with a notification.
 *
 * @property still the picture as a bitmap, always present
 * @property animatedBytes the original file of an animated image (WebP or GIF), so it can be played; null for a still one
 * @property isSticker true when it is a sticker and not a photo
 */
class NotificationMedia(
    val still: Bitmap,
    val animatedBytes: ByteArray? = null,
    val isSticker: Boolean = false
)
