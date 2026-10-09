# Ashutool

Root toolkit for custom ROMs. Kotlin, Jetpack Compose, libsu.
Glass UI with 12 themes and a bottom bar you swipe left and right. Line icons only, no emoji.

## Build
1. Open this folder in Android Studio (Koala or newer, JDK 17). Let Gradle sync.
2. Run on a rooted device and allow the root prompt.
3. On first launch Ashutool grants itself usage access, overlay, notifications, battery whitelist
   and turns on the instant-lock accessibility service through root.

## Tabs (bottom bar, swipe to scroll)
Home, Games, CPU, Memory, Battery, Charging, Thermal, App heat, Usage, Apps, Lock, Hide,
Network, Tasks, Alerts, Display, Storage, Power, Device, Settings.

- Home: live gauges and one-tap boost.
- Games: game launcher, edge sidebar (stats, performance, DND, screenshot, record, brightness, volume), auto game mode.
- CPU: per-cluster governor and min/max frequency, restore defaults.
- Memory: RAM, swap on/off, swappiness slider and explanation, drop caches.
- Battery: drain rate per hour, 24 hour usage and average, level chart, use by app, health, cycles.
- Charging: charge limit, pause charging, temperature guard, charging current (kernel dependent).
- Thermal: every sensor. App heat: apps using the CPU now and temperature history per app.
- Usage: screen time per app.
- Apps: stop, clear cache, clear data, remove for user and restore.
- Lock: PIN or pattern, instant overlay with background blur, relock delay, network default for locked apps.
- Hide: freeze or hide completely, vault opened by a dialer code (*#*#code#*#*) or volume up, up, down, optional password.
- Network: block mobile data or Wi-Fi per app.
- Alerts: choose what the live notification shows (CPU, RAM, battery, drain, network speed, sleep, storage).
- Settings: themes, permissions, safe mode, firewall reset, action log.

## Safety
- Launcher, keyboard, dialer, SMS app, system-UID apps, GMS, Play Store and root managers cannot be hidden,
  frozen, removed, cleared, blocked or locked.
- Package names are validated before any shell command. Governors and frequencies come only from kernel lists.
  Charging and current writes use a fixed path whitelist.
- Charging is switched back on when unplugged, when the service stops, and after a crash.
- Game mode saves what it changes and restores it, also after a crash.
- Density changes revert by themselves after 15 seconds unless confirmed.
- Removed apps stay restorable. Firewall rules live in one chain that Settings can flush.
- Five service restarts in two minutes switch on safe mode and pause the risky auto features.
- Nothing is ever remounted or written under /system. Every root change is listed in Settings, Action log.

## Notes
- If you forget the lock PIN, clear Ashutool's app data with root.
- Android takes the recents preview itself, so an app cannot blur it. The lock covers the app the moment it opens instead.
- To ship in a ROM, put the APK in `system/priv-app/Ashutool/`.
