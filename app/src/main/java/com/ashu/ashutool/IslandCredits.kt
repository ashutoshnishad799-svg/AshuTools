/*
 * Ashutool - root toolkit for custom ROMs
 * Copyright (C) 2026 Ashutosh Nishad
 * Telegram: @ashutosh_07x (https://t.me/ashutosh_07x)
 *
 * Free software under the GNU GPL v3.0 or later, with the attribution terms in NOTICE.
 * Keep this header and the author credit in every copy and modified version.
 */

package com.ashu.ashutool

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * Credit for the project that inspired the Dynamic Island of Ashutool. This is kept apart from [Credits] on purpose:
 * [Credits] holds the author credit of Ashutool itself, which the build checks. This one is a thank you and a link.
 *
 * Smart Island is free software under the GNU GPL v3.0. The island code of Ashutool was written for Ashutool, no
 * source code of Smart Island was copied. If code is ever copied from Smart Island, its copyright notice and licence
 * must be kept for that code.
 */
object IslandCredits {
    const val NAME = "Smart Island"
    const val AUTHOR = "agupta07505"
    const val LICENSE = "GNU GPL v3.0"
    const val REPO_URL = "https://github.com/agupta07505/SmartIsland"
    const val TELEGRAM_URL = "https://telegram.me/SmartIslandApp"
    const val LINE = "Dynamic Island inspired by $NAME by $AUTHOR"
}

/** The credit card on the Island page. It can also show the credit on the island itself. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun IslandCreditCard() {
    val ctx = LocalContext.current
    Glass {
        Row(verticalAlignment = Alignment.CenterVertically) {
            GradIcon(Icons.Outlined.Favorite, GPink, 44.dp)
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text("Credit", color = TextLo, fontSize = 11.sp, fontWeight = FontWeight.Medium)
                Text("Inspired by ${IslandCredits.NAME}", color = TextHi, fontSize = 16.sp, fontWeight = FontWeight.Bold)
                Text("by ${IslandCredits.AUTHOR}  \u00B7  ${IslandCredits.LICENSE}", color = TextLo, fontSize = 12.sp)
            }
        }
        Text(
            "The Ashutool island takes its idea from ${IslandCredits.NAME}: live activities, idle widgets, swipe gestures, " +
                "buttons on messages and rules per app. It is a different app with its own design and its own code. " +
                "Thank you ${IslandCredits.AUTHOR}.",
            color = TextLo, fontSize = 12.sp, modifier = Modifier.padding(top = 10.dp, bottom = 10.dp)
        )
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            GlassButton(Icons.Outlined.OpenInBrowser, "GitHub") { openLink(ctx, IslandCredits.REPO_URL) }
            GlassButton(Icons.Outlined.ChatBubbleOutline, "Telegram") { openLink(ctx, IslandCredits.TELEGRAM_URL) }
            GlassButton(Icons.Outlined.BlurOn, "Show on island") {
                IslandHub.flash(ctx, "credit", IslandCredits.NAME, "Inspired by ${IslandCredits.AUTHOR}", Icons.Outlined.Favorite, Color(0xFFFF6B8B), "", 5)
            }
        }
    }
}
