/*
 * Ashutool - root toolkit for custom ROMs
 * Copyright (C) 2026 Ashutosh Nishad
 * Telegram: @ashutosh_07x (https://t.me/ashutosh_07x)
 *
 * Free software under the GNU GPL v3.0 or later, with the attribution terms in NOTICE.
 * Keep this header and the author credit in every copy and modified version.
 */

pluginManagement {
    repositories { google(); mavenCentral(); gradlePluginPortal() }
}
dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        google()
        mavenCentral()
        maven("https://jitpack.io")
    }
}
rootProject.name = "Ashutool"
include(":app")
