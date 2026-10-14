# Ashutool

Root toolkit for custom ROMs. Kotlin, Jetpack Compose, libsu. Glass UI, 15 themes, swipeable bottom bar.
Default theme is Slate and follows the system (Slate light in light mode).

## Build
Open in Android Studio (JDK 17), sync, run on a rooted device, allow root. On first launch the app grants itself
usage access, overlay, notifications, phone state, call answering and turns on the instant-lock accessibility service.

## Tabs
Home, Games, Calls, CPU, Memory, Battery, Thermal, Usage, Apps, Lock, Hide, Network, Tasks, Alerts, Display, Storage, Power, Device, Settings.
Bigger tabs have sub pages:
- Battery: Drain and history, Use by app, Background apps (restrict, stop on leave, stop on screen off, keep), Charging control,
  Battery saver (deep Doze, screen-off CPU limit, auto saver), Accuracy (capacity estimate, reset stats).
- Usage: App limits with custom timing, Reels blocker (Instagram Reels, YouTube Shorts: block or daily limit).
- Lock: PIN or pattern, instant overlay, blur behind, blur in recents, recovery code, gear for settings, protects Ashutool itself.
- Hide: freeze or hide completely, vault by dial code (works without pressing call) or volume keys, gear for settings.
- Games: floating app windows from the game panel have a title bar: drag to move, smaller and bigger, full screen
  and back, close. Needs a ROM with freeform windows, otherwise the app just opens normally.
- Games: launcher, edge sidebar (long press and drag to any edge, 6 bar designs, colour, size, opacity), game mode,
  floating windows and app shelf, screen recording with a draggable timer pill and a notification Stop button.
  The bar shows in every game and stays there: it comes back after a call, a store popup, screen off and on, or a rotation.
  Games are found by Android's game flag and category, a list of known titles, landscape-only apps with no category,
  and what you add yourself under Add or remove. A window from the phone's own game assistant never counts as leaving the game.
  The Games page has a Game bar check card: service, overlay permission, how the open app is detected, the last error,
  a Test bar button, and a one tap Mark as game for apps you opened recently.
- Calls: compact incoming banner, keep your game in front, auto answer, auto speaker, floating end / speaker / mute controls.
  Banner and floating controls are for game space only (switch: Only while gaming). The pill moves with your finger on the raw screen
  position and always stays on screen. Speaker, mute and end are also in the sidebar panel during a call.
- Thermal: sensors and heat by app. Settings: Appearance, Permissions, Safety, Action log, About.

## Battery use of Ashutool
No timers run while the screen is off. Sampling uses two shell processes, once a minute with the screen off, every few seconds with
the app closed. Page loops stop when the app is in the background. Animated background is off by default.

## Safety
Launcher, keyboard, dialer, SMS app, system-UID apps, GMS, Play Store and root managers cannot be hidden, frozen, removed, cleared, blocked,
locked, or closed from Tasks. Package names are validated, kernel values come from kernel lists, sysfs paths are whitelisted.
Charging, game mode and screen-off savers always restore. Five service restarts in two minutes switch on safe mode.
Nothing is written under /system. Every root change is listed in Settings, Action log.
If you forget the lock and the recovery code, clear Ashutool's app data with root.

## Screen time and battery use of Ashutool itself
- Screen time is worked out from the usage events, one day at a time. Today counts only from midnight to now,
  and time stops when the screen turns off. Android's ready made totals can include time from outside the range.
- App limits count the same way. Built in system parts (themes, overlays, navigation bars) are hidden from the
  limits list unless Show system apps is on. An app that already has a limit always stays visible.
- While the app is not on screen the monitor samples every 10 seconds and reads only the CPU thermal sensors.
  All sensors are read, at the chosen speed, only while the app is open or the game panel is open.
- The game watch sleeps completely outside games.

## Look, backup and credits
- Nebula is the default theme (deep violet and indigo, with a light version). Older themes remain in Settings, Appearance.
- Settings, Backup and restore saves and loads settings from Download/Ashutool/settings-backup.json.
  Passwords, PINs, the vault code and the lock list are never included.
- About shows the author, licence, libraries, and Copy debug info for bug reports. A crash is kept in a file and can be copied too.
- Made by Ashutosh Nishad, Telegram @ashutosh_07x. GNU GPL v3.0 or later, see LICENSE and NOTICE.

