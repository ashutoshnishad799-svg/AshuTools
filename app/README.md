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
- Games: launcher, edge sidebar (long press and drag to any edge, 6 bar designs, colour, size, opacity), game mode,
  floating windows and app shelf, screen recording with a draggable timer pill and a notification Stop button.
- Calls: compact incoming banner, keep your app in front, auto answer, auto speaker, floating end / speaker / mute controls.
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
