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
| `consumer-rules.pro` | Smart Island's `proguard-rules.pro`, unchanged, applied to the whole app. |
| `src/main/AndroidManifest.xml` | `<application>` attributes removed (Ashutool's application provides them). `MainActivity` has no launcher entry any more, it is opened from the Island tab of Ashutool. |
| `SmartIslandApp.kt` | Removed. Ashutool's `AshuApp` is the Hilt application now. Its three steps live in the new `AshutoolBridge.onAppCreate()`. |
| `AshutoolBridge.kt` (new) | The only door Ashutool uses: start-up, status of the services, the setup commands, opening the settings. |
| `util/NotificationFilter.kt` | The own package name `com.agupta07505.smartisland` is now `com.ashu.ashutool`, the app this code runs in. |
| `util/AppLogRecorder.kt`, `util/EnvironmentDiagnostics.kt`, `ui/SmartIslandHomeScreen.kt` | The check "is the notification listener of the island on" compares the class of the listener, not only the package, because Ashutool has a listener of its own in the same package. |

Everything else is the upstream code, file for file. Fonts (Inter Display, Lora, Poppins) are under the SIL Open Font
License 1.1, see `NOTICE.md` and `THIRD_PARTY_FONTS.md`. `UPSTREAM_README.md` and `UPSTREAM_CHANGELOG.md` are the upstream ones.

Also changed: the string `app_name` of Smart Island was renamed `smart_island_app_name` (in `res/values*/strings.xml`, the manifest and the two Kotlin
files that read it), because Smart Island ships an English translation of it and that would have renamed the whole of Ashutool.

Note: the "Updates and downloads" section inside the Smart Island settings screen lists releases of Smart Island itself, not of Ashutool.
Ashutool updates come from the Ashutool repository.
