# Smart Island inside Ashutool

This module is **Smart Island 7.6.0** by **Animesh Gupta** ([agupta07505](https://github.com/agupta07505)), taken from
<https://github.com/agupta07505/SmartIsland> and used in Ashutool under the **GNU General Public License v3.0** (the same
licence as Ashutool, see `LICENSE`). Telegram: <https://telegram.me/SmartIslandApp>.

The source files keep their original headers and notices. They were not rewritten: the Dynamic Island of Ashutool *is*
Smart Island. Please star the original repository.

## What was changed (GPL section 5a), 2026

Smart Island was an Android application. To live inside Ashutool it is now a library module of it. Only what had to change was changed:

| File | Change |
|---|---|
| `build.gradle.kts` (new) | Library module. Dependencies and settings follow Smart Island's `app/build.gradle.kts`. Adds `VERSION_NAME` and `VERSION_CODE` to `BuildConfig`. The baseline profile and the tests were not copied. |
| `consumer-rules.pro` | Smart Island's `proguard-rules.pro`, applied to the whole app. Only the line `-dontoptimize` was moved out, to `app/proguard-rules.pro` of Ashutool, because a library may not set a global option. |
| `src/main/AndroidManifest.xml` | `<application>` attributes removed (Ashutool's application provides them). `MainActivity` has no launcher entry any more, it is opened from the Island tab of Ashutool. |
| `SmartIslandApp.kt` | Removed. Ashutool's `AshuApp` is the Hilt application now. Its three steps live in the new `AshutoolBridge.onAppCreate()`. |
| `AshutoolBridge.kt` (new) | The only door Ashutool uses: start-up, status of the services, the setup commands, opening the settings. |
| `util/NotificationFilter.kt` | The own package name `com.agupta07505.smartisland` is now `com.ashu.ashutool`, the app this code runs in. |
| `util/AppLogRecorder.kt`, `util/EnvironmentDiagnostics.kt`, `ui/SmartIslandHomeScreen.kt` | The check "is the notification listener of the island on" compares the class of the listener, not only the package, because Ashutool has a listener of its own in the same package. |

| `ui/HostTheme.kt` (new) | Lets Ashutool hand over its look (colours, glass, drifting glow). `SmartIslandTheme` paints the Smart Island screens with it when it is set. |
| `ui/Theme.kt` | `SmartIslandTheme` uses that look first, when the host set one. Its own theme code is unchanged. |
| `ui/glass/LiquidGlass.kt` | `ThemedBackdrop` draws the host's background when there is one. |
| `ui/SmartIslandHomeScreen.kt` | Two small edits: the section request accepts `ashu:<name>` to open a tab or a page by name (so Ashutool's Island tab can link to them), and the bottom bar is near solid over the host's background. |
| `di/AshutoolEntryPoint.kt` (new) | A Hilt entry point that gives `AshutoolBridge` the settings repository. |
| `AshutoolBridge.kt` | Also: the master switch of the island as state, switching it, and matching the island's accent colours to the host. |

| `ui/HostEdge.kt` (new), `ui/IslandPillSurface.kt` | A light coloured edge drawn around the island (Off, Accent, Aurora, Rainbow, Flow), chosen in Ashutool. One line added to the pill's modifier chain. |
| `ui/SmartIslandHomeScreen.kt`, `ui/search/SettingsSearchIndex.kt` | The About, Updates and Support entries are not shown any more, and the welcome dialog has no outside links. |
| `ui/sections/DeveloperOptionsSection.kt` | The version row shows the build type only. |
| `res/values*/strings.xml` | The name shown in the screens is "Dynamic Island" (in every language), and "made by" says "Part of Ashutool". |

Everything else is the upstream code, file for file. Fonts (Inter Display, Lora, Poppins) are under the SIL Open Font
License 1.1, see `NOTICE.md` and `THIRD_PARTY_FONTS.md`. `UPSTREAM_README.md` and `UPSTREAM_CHANGELOG.md` are the upstream ones.

Also changed: the string `app_name` of Smart Island was renamed `smart_island_app_name` (in `res/values*/strings.xml`, the manifest and the two Kotlin
files that read it), because Smart Island ships an English translation of it and that would have renamed the whole of Ashutool.

Note: the "Updates and downloads" section inside the Smart Island settings screen lists releases of Smart Island itself, not of Ashutool.
Ashutool updates come from the Ashutool repository.

## Rewriting
The island is being rewritten file by file into Ashutool's own code with the same behaviour. The plan, the rules and the list of what is
done are in `REWRITE.md`. Rewritten files keep the notice of Smart Island at the top, with a line saying they were rewritten for Ashutool.
