# Ashutool

**Root toolkit for custom ROMs.** CPU, memory, battery, charging, thermal, app control, app lock, hide, network,
games, calls and more, in one app with a glass look.

Made by **Ashutosh Nishad** - Telegram [@ashutosh_07x](https://t.me/ashutosh_07x)

Free and open source, GNU GPL v3.0 or later. See [LICENSE](LICENSE) and [NOTICE](NOTICE).

## What it does
- **Monitor:** live CPU, memory, battery, thermal and network numbers, history graphs, app heat.
- **Control:** CPU governor and frequency, memory and swap, charging limit, app standby and background rules.
- **Apps:** app limits, reels blocker, app lock with PIN or pattern, hidden apps vault, firewall.
- **Games:** game launcher, edge game bar, game mode (performance, do not disturb, no heads-up), screen recording,
  floating app windows with a title bar (move, resize, full screen, close).
- **Calls:** compact call banner and floating call controls while you play.
- **Looks:** 17 themes, Nebula by default, glass strength, optional animated background.
- **Backup:** export and import your settings.

More detail is in [app/README.md](app/README.md).

## Requirements
- Android 8.0 or newer, rooted (Magisk or KernelSU). Floating windows need a ROM with freeform window support.
- No internet permission. Ashutool does not send any data anywhere.

## Install
Download the APK from the Releases page, install it, open it and grant root. Ashutool then grants itself the
other permissions through root. If something is missing, Settings, Permissions shows what and fixes it.

## Build
```
gradle assembleRelease
```
Needs JDK 17 and Gradle 8.7 (the GitHub Actions workflow does exactly this). Without a signing key the release APK
is signed with the debug key, so it installs and runs as a *community build*.

### Official signed release (maintainer)
1. Make a keystore once: `keytool -genkeypair -v -keystore release.jks -alias ashutool -keyalg RSA -keysize 4096 -validity 36500`
2. Add these repository secrets: `KEYSTORE_BASE64` (`base64 -w0 release.jks`), `KEYSTORE_PASSWORD`, `KEY_ALIAS`, `KEY_PASSWORD`.
3. Add the repository variable `CERT_SHA256`: the SHA-256 of your certificate as lowercase hex without colons
   (from `keytool -list -v -keystore release.jks`).
4. Push a tag such as `v2.0.0`. The workflow builds, signs and publishes the APK with its checksum.

The app then shows *Official build* in About when it was signed with that key.

## Credit, licence and modified copies
Ashutool is open source and you are welcome to learn from it, fix it and build on it. In return the licence
asks for three things, spelled out in [NOTICE](NOTICE):

1. Keep the author credit (name and Telegram id) in the source headers and on the About screen.
2. Mark your version as different from the original and do not present it as mine.
3. Keep your version open source under the same licence.

A copy of the app that removes the author credit breaks the licence, and the app itself will refuse to run in
that case. Release builds are also shrunk and renamed with R8, which makes taking the app apart and
repackaging it harder. No app can be made impossible to modify, and the source is open on purpose,
so the real protection is the licence. If you copy the work, give credit.

This is the author's statement of intent, not legal advice.

## Contributing and support
See [CONTRIBUTING.md](CONTRIBUTING.md). Bugs: Settings, About, Copy debug info, then open an issue.
Security problems: [SECURITY.md](SECURITY.md). Chat: [@ashutosh_07x](https://t.me/ashutosh_07x).
