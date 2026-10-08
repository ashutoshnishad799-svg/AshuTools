# Ashutool

Root toolkit for custom ROMs. Kotlin, Jetpack Compose, libsu.

## Build
1. Open this folder in Android Studio (Koala or newer, JDK 17).
2. Let Gradle sync. JitPack is already set up for libsu.
3. Run on a rooted device or emulator, then allow the root prompt.

On first launch with root, Ashutool grants itself usage access, overlay,
notification and battery whitelist permissions automatically.

## Features
- Dashboard: CPU load, per-core frequency, governor, temperature, RAM.
- Battery: level, current, voltage, temperature, health, capacity health, cycles, live charts.
- App usage: screen time per app for today, 7 days and 30 days.
- Apps: PIN app lock, hide (disable for user 0), force stop, clear cache.
- Notification: live CPU, RAM, temperature and battery in the status panel.
- Tools: governor switch, kill background apps, clear caches, free RAM, force Doze, restart System UI, reboot menu.

## Notes
- Hide uses `pm disable-user --user 0`. Unhide restores it.
- App lock watches the foreground app through usage events and opens a PIN screen.
- To ship inside your ROM, place the APK in `system/priv-app/Ashutool/`.
