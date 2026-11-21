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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.ChatBubbleOutline
import androidx.compose.material.icons.outlined.Favorite
import androidx.compose.material.icons.outlined.OpenInBrowser
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * Credit for Smart Island, whose code is the Dynamic Island of Ashutool. Kept apart from [Credits], which holds the
 * author credit of Ashutool itself and is checked by the build.
 */
object IslandCredits {
    const val NAME = "Smart Island"
    const val AUTHOR = "Animesh Gupta"
    const val HANDLE = "agupta07505"
    const val LICENSE = "GNU GPL v3.0"
    const val REPO_URL = "https://github.com/agupta07505/SmartIsland"
    const val TELEGRAM_URL = "https://telegram.me/SmartIslandApp"
}

/** The credit card of the Island tab and the About page. */
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
                Text("Dynamic Island", color = TextHi, fontSize = 16.sp, fontWeight = FontWeight.Bold)
                Text("@${IslandCredits.HANDLE}  \u00B7  ${IslandCredits.LICENSE}", color = TextLo, fontSize = 12.sp)
            }
        }
        Text(
            "The Dynamic Island is built on ${IslandCredits.NAME} by ${IslandCredits.AUTHOR} (${IslandCredits.HANDLE}), shared under the " +
                "${IslandCredits.LICENSE}. Copyright \u00A9 2026 ${IslandCredits.AUTHOR}. It comes with no warranty. You may share it under " +
                "the same licence, a copy is in the source of Ashutool. Thank you.",
            color = TextLo, fontSize = 12.sp, modifier = Modifier.padding(top = 10.dp, bottom = 10.dp)
        )
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            GlassButton(Icons.Outlined.OpenInBrowser, "GitHub") { openLink(ctx, IslandCredits.REPO_URL) }
            GlassButton(Icons.Outlined.ChatBubbleOutline, "Telegram") { openLink(ctx, IslandCredits.TELEGRAM_URL) }
        }
    }
}
