# Changelog

## 3.0.0 - redesign, Dynamic Island, sound controls
Made by Ashutosh Nishad, Telegram @ashutosh_07x

### New look
- The whole design system was redone: Nebula is the default theme (dark and light, following the system), larger and
  clearer glass cards with light on the top edge, a floating glass navigation bar whose current tab opens into a lit pill
  with a spring, bigger page titles, rounder buttons. Two new themes: Liquid and Gold. The animated background is on by
  default and only runs while the app is open.
- The bottom bar now has Home, Monitor, Island, Games, Power and More. Tools and Settings moved into More.
- Home: Quick controls (5G / 4G, Sound, Volume) and the Dynamic Island switch are at the top.

### Dynamic Island (new, Island tab)
- A shape at the top of the screen that works over every app, in games and over full screen video.
- Shows: calls (caller name, timer, Answer, Decline, Speaker, Mute, End), music and video from any player (YouTube,
  Spotify, browsers and so on, with cover, progress, previous, play, next), charging, fully charged and low battery,
  screen recording (timer, stop), sound mode changes, 5G / 4G changes, new notifications, and "Reels blocked".
- Two things at once: the more important one is the big shape and the other is a small bubble beside it. Tap the
  bubble and it opens while the old one becomes the bubble, with a spring animation. Tap the big shape to open it,
  swipe up to close, press and hold to open the app.
- Eight themes (Classic, Glass, Aurora, Neon, Sunset, Ocean, Mint, Light), edge effects (None, Glow, Pulse, Sweep,
  Rainbow), four shapes (Pill, Notch, Squircle, Tab), roundness, four animation feels (Bouncy, Smooth, Snappy, Elastic),
  overall size, width, height, distance from the top, left and right, opacity, how long it stays open, and a switch for
  each thing it shows. Everything applies live. Try buttons show a message, charging, 5G, and two at once.
- The window is always exactly as big as the island, so the empty space around it never blocks a touch. It grows before
  the island grows and shrinks after the island shrank.
- Nothing runs while the screen is off.

### Quick settings tiles
- Only three tiles now: 5G / 4G, Sound and Volume. The other tiles (Mobile data, Wi-Fi, Bluetooth, Airplane,
  Location, Dark mode, Show touches, No animations, Stay awake, Battery saver, Game bar, FPS, Screen record and
  Floating apps) were removed. Android has its own for most of them, and the game panel keeps the game ones.
- 5G / 4G is fast now. Before, every tap started a new Java process as root twice, once to read and once to write.
  Now one root process stays running and answers in a few milliseconds. The tile draws the new state the moment you tap,
  sends the change in order with any other taps, and never repaints an old answer over it. The state is remembered, so
  the tile is right as soon as the panel opens. The picture on the tile is now "5G" or "4G", not a signal icon.
- Sound: one tile for ring, vibrate and silent. One tap goes to the next. A bell for ring, a bell with a cross for
  silent, a vibrating phone for vibrate. It uses the audio service directly, so it is instant. Do Not Disturb access
  for silent is granted through root when the system asks.
- Volume: opens the Ashutool volume panel. It can be Small (one slider), Medium (media, ring, alarm) or Big
  (larger, more sliders), on the left or the right, with a ring mode button, and it closes by itself.

### Game sidebar
- Floating windows and the app shelf are removed completely, in games and outside, with their settings, their tile,
  the freeform system switches and the code behind them.
- The panel is new: glass that follows the theme, live numbers in chips, a profile switch, and more tools: 5G / 4G,
  Sound, Volume panel and Dynamic Island, next to DND, Popups, FPS, Clear RAM, Shot, Record and Touches.
  Panel size Small, Normal or Large. New handle style: Gradient. Optional real blur on Android 12 and newer.

### Reels and Shorts blocker
- It could block before the limit was really used. Fixed: a Reels view only counts when it is really on screen and
  large (pages the app loads in advance and the small Shorts shelf in the home feed no longer count), the window in
  front must be the app, time only counts from one look to the next while Reels stays open (the first look after
  you open it adds nothing, a gap over 4 seconds starts a new stretch), and a check every 2 seconds keeps the count
  and the limit exact even when the app sends no events. Blocking shows a short message on the island.

### Battery notification
- New lines: Idle drain (average milliamps while the screen is off, percent per hour and deep sleep), screen on and
  off time with the charge used since unplugging, active drain (optional), time left or time to full, and power in
  watts. Each has its own switch in Alerts.

## 2.1.0 - stability, tiles and battery pages
Made by Ashutosh Nishad, Telegram @ashutosh_07x

### Floating windows
- The reason an app opened full screen instead of in a window: the three system switches that allow floating
  windows were only turned on by a manual button. Ashutool now turns them on by itself when it starts (outside any
  game) and again before every window. If the first try gives a full screen task it tries once more.
- A window is only moved or resized when the system really made it floating. A task without a mode is no longer
  guessed to be floating (before Android 12 the old guess is kept).
- The screen rotation lock is only used inside a game, where it is needed. The orientation-request override that
  could restart the system UI layout was removed. A bar only gives back a lock it made itself.
- Full screen no longer slides under the status bar. The buttons and the window stay below it, and above the
  navigation buttons when those are used. The same limit applies to moving, sizes and snapping.
- Drag a window down over the round target at the bottom to close it. The target shows only while moving.
- A bar whose app was closed from recents is cleared and the window is made again on the next tap.
- Opening no longer makes the caller wait 1.5 seconds, and the window check is lighter on the system:
  every 6 seconds instead of 4, and the second and third activity manager views are only read when the first finds nothing.
- The default place is now really the right side, as the notes always said.
- If the system UI restarts after a window was opened, the crash part of the log is kept. Copy it from Windows and
  apps, or from About. It shows what a ROM dislikes.

### Quick settings tiles
- A tile is never greyed out because a read failed. An unknown state shows as off and a tap still tries.
- 5G / 4G: the answer "OK" is now found on any line, a warning line printed before it used to turn a success into
  a failure. What the phone said is in Copy debug info.
- New tiles: Mobile data, Wi-Fi, Bluetooth, Airplane, Location, Dark mode, Show touches, No animations,
  Stay awake, Battery saver, Game bar, FPS meter, Screen record. The Home cards use the same list.

### Background
- The call name listener is only enabled while a game is in front (or everywhere if call banners outside games are
  on). The rest of the time the system does not bind it at all.
- Background sampling is slower when nobody looks: 20 seconds, or 30 when nothing needs fresh numbers.
  The notification refreshes every 20 seconds unless the app or the game panel is open.
- Icon pictures are let go when the app goes out of sight.

### Battery
- Ongoing: time since unplug or plug, used percent and milliamp hours, screen on, screen off, deep sleep and held
  awake, a time split bar and an estimate of the time left.
- Electric current: live value, power and voltage, a chart for 1 minute up to 6 hours, average, lowest, highest.
- Temperature alarm: hot and cold limits of your choice, off by default.

### Look
- Six groups in the bottom bar (Home, Monitor, Games, Power, Tools, Settings) instead of nineteen tabs,
  with small tabs for the pages inside a group. The group remembers its last page.

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

### Floating windows, made steady
- One gate for every system command, so opening, moving, closing and minimising never run into each other.
- Each window has a state (open, bubble, closing) and every action checks it first. Opening an app twice quickly,
  closing while it opens, or dragging while it closes can no longer leave a bar behind.
- Open any time, in a game or not: the Floating apps tile in the quick settings panel, and the Floating apps card on Home.
- Minimise a window to a round bubble on the screen edge, tap it to bring the window back, drag it along the edge.
- Resize shows an outline and the window changes once when you let go. Moving is sent in small steps.
- A window opened again comes to the front, the window number is refreshed every few seconds, and a bar whose window
  is gone removes itself. Close all closes every window.
- An error inside the window code is written down and never closes the app.

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
