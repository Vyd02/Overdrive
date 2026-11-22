# Overdrive

Console-style home screen for Fire TV. Big animated app tiles, clock and weather, recently used row, hidden apps, and self-updates from GitHub Releases.

## Install on the TV
1. In Downloader, open: `github.com/Vyd02/Overdrive/raw/main/dist/Overdrive.apk`
2. Install Launcher Manager Mini (Downloader code `632067`) and pick Overdrive as the home app.

## Remote
- OK: open app · ☰ on a tile: hide / app info · ☰ anywhere else or ⚙: menu · Back: jump to top

## Releasing an update
1. Bump `versionCode` and `versionName` in `app/build.gradle`.
2. Build the release APK and copy it to `dist/Overdrive.apk`.
3. Set the same `versionCode` and `versionName` in `version.json`.
4. Commit and push. Overdrive checks `version.json` every 30 minutes while it's open, downloads the new APK, and opens the TV's Install screen.

The signing key (`overdrive.jks`) is never committed. Every update must be signed with the same key.
