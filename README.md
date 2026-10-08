# Ashutool

Root toolkit for custom ROMs. Kotlin, Jetpack Compose, libsu.
Aurora gradient background, glass cards, Material vector icons. No emoji.

## Build
1. Open this folder in Android Studio (Koala or newer, JDK 17).
2. Let Gradle sync. JitPack is already set up for libsu.
3. Run on a rooted device and allow the root prompt.

On first launch with root, Ashutool grants itself usage access, overlay,
notification and battery whitelist permissions.

## Pages
- CPU: load, per-core frequency, and per-cluster governor, min and max frequency.
- Memory: RAM, swap, cache, swappiness slider, drop caches.
- Battery: level, current, power in watts, voltage, health, capacity health, cycles, charts.
- Thermal: CPU and battery temperature, every thermal sensor.
- App usage: screen time per app for today, 7 and 30 days.
- App manager: open, force stop, clear cache, clear data, uninstall for user.
- App lock: PIN lock per app, change PIN.
- App hide: hide apps from the launcher, unhide all.
- Processes: top memory users with kill.
- Notification: choose what the live notification shows and how often it updates.
- Display: density presets, animation speed, show touches, stay awake.
- Storage: partition usage, clear caches, run TRIM.
- Power: battery saver, Doze, restart System UI, soft reboot, reboot, recovery, bootloader, power off.
- Device: model, ROM build, kernel, patch level, uptime, SELinux switch.
- Home: one-tap boost (stops background apps, trims caches, frees RAM).

## Notes
- Hide uses `pm disable-user --user 0`. Unhide restores it.
- App lock watches foreground app events and opens a PIN screen over locked apps.
- Critical packages (android, SystemUI, Settings, Phone) are protected from hide, stop, clear and uninstall.
- To ship inside a ROM, place the APK in `system/priv-app/Ashutool/`.
