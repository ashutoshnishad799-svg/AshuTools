# Changelog

## 2.0.0 - first stable open source release
Made by Ashutosh Nishad, Telegram @ashutosh_07x

### New
- Nebula, a new default look: deep violet and indigo with soft pink and blue light behind frosted glass,
  with a light version. The older themes are still there.
- Floating windows from the game panel open on the right side by default (left or centre can be chosen under
  Windows and apps) and come with a small control pill: drag it to move the window, full screen and back, close,
  and a More menu with Mini, Medium and Large sizes, snap to a side, sound off, lock position and screenshot.
  If a ROM does not allow moving windows the pill still shows the buttons that do not need it. The window is placed
  twice, a moment apart, because some apps put it back, and Android's launch bounds are tried as a last resort.
- Backup and restore of settings to a JSON file. Passwords, PINs and the vault code are never included.
- About page with the author, licence, open source libraries, copy debug info and copy last crash.
- Crash log that keeps the last crash so it can be sent with a bug report.
- Build integrity check: official, community or modified build.

### Added after the first tests
- Floating windows now look like a small desktop window: tiny red buttons on top (close, full screen, mini, more),
  a drag bar under the window and a size grip in the corner, all with animations. The window opens where you left
  it last time. More has sizes, snap left and right, centre, mute the app, lock the position and a screenshot.
- Game panel is smaller and tidier, with icon buttons and a three way profile: Standard, Performance, Battery saver.
- FPS meter: a small see-through FPS and speed readout in a corner while you play, switched on from the panel.
- Network speed in the status bar (optional, off by default), not in the notification panel.
- 5G / 4G toggle in the app and as a quick settings tile.
- Quick toggles on Home: mobile data, Wi-Fi, airplane, show touches, no animations, stay awake, battery saver, auto rotate.
- The call banner shows the name of the caller.
- Nothing in game mode switches on by itself any more. Do not disturb, popups and the profile are off until you choose them.
- Pure black is the default theme in dark mode, pure white in light mode. Nebula is still in the list.

### Fixes after the first tests
- The screen no longer turns while a floating window is open. A portrait app in a window over a landscape game made
  the system turn the screen back and forth and the whole screen broke. The screen is now locked in the direction it
  had before the app starts, and apps cannot decide the direction. When the last window closes, your own rotation
  settings are put back, and after a crash they are restored on the next start.
- Removed the rotation tile from the game panel, the auto rotate quick toggle and all rotation handling of windows.
- Resizing a window with the corner grip is sent to the system less often, because each size change makes some
  apps rebuild their screen. A window is never started a second time to place it.
- Floating windows could move the wrong task, even the launcher, because the task id was read from the first # number
  on a line, which can be a position in the recents list. The id is now read only after "Task{hash #", only floating
  tasks are ever moved, and home and recents tasks are never touched. Reset windows on the Windows page puts the
  home screen back in full screen.
- The game bar and the readout stay while a floating window is open over the game, and go when a launcher shows.
- Window places are kept per screen direction, and windows move to the right place when the screen turns.
- The FPS readout sits below the status bar, and FPS is read from the display driver on Qualcomm phones.
- 5G switch finds the phone service methods by name and says what the phone offers when it cannot change it.
- Reels blocker no longer blocks when its switch is off.
- App lock cover is fully hidden, nothing behind it shows through.
- The dark strips at the two ends of the bottom bar are gone.

### Games
- The game bar now shows in every game and stays: it comes back after a call, a store popup, screen off and on,
  and a rotation. It cannot be swiped away any more, only moved.
- Better game detection, a Game bar check card with a Test bar button, and one tap Mark as game.
- Call banner and floating call controls only appear while a game is open. Speaker, mute and end are also in
  the game panel during a call.
- The floating call controls no longer shake or disappear when dragged.

### Fixes
- Screen time and app limits are counted from the usage events, one day at a time. Today is only today.
- App limits hide built in system parts unless Show system apps is on.
- Thermal page: sensors that report in tenths of a degree, and zones that are not temperatures, no longer show
  values like 398 degrees. CPU temperature uses the hottest CPU sensor.
- Game mode restores the heads-up notification setting you had before.

### Battery use of Ashutool itself
- In the background the monitor samples every 10 seconds and reads only the CPU thermal sensors.
- The game watch sleeps completely outside games.

### Release
- Release builds are shrunk and renamed with R8.
- Licence: GNU GPL v3.0 or later with attribution terms, see LICENSE and NOTICE.
