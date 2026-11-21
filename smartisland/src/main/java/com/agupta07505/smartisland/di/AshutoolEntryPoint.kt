/*
 * Ashutool - root toolkit for custom ROMs
 * Copyright (C) 2026 Ashutosh Nishad
 * Telegram: @ashutosh_07x (https://t.me/ashutosh_07x)
 *
 * Free software under the GNU GPL v3.0 or later, with the attribution terms in NOTICE.
 *
 * This file is new. It gives AshutoolBridge the settings repository of Smart Island, the same one Smart Island itself uses.
 */

package com.agupta07505.smartisland.di

import com.agupta07505.smartisland.data.SmartIslandSettingsRepository
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent

@EntryPoint
@InstallIn(SingletonComponent::class)
interface AshutoolEntryPoint {
    fun settingsRepository(): SmartIslandSettingsRepository
}
