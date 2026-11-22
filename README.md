# Overdrive

Console-style home screen for Fire TV.

- 6 interactive animated themes (Nebula, Synthwave, Hyperspace, Aurora, Deep Sea, Ember Forge) that react to the remote, with live preview
- PS5-style spotlight: the focused app's art fills the top of the screen
- Console UI sounds, startup intro, idle screensaver
- Favorites row you can reorder, Recent, HDMI inputs, auto categories (Streaming / Games / Music / Apps / Tools), quick settings
- Hidden apps, weather, Back & Home guard, opens on power-up, self-updates from this repo

## Install on the TV
1. In Downloader, open: `github.com/Vyd02/Overdrive/raw/main/dist/Overdrive.apk`
2. Install Launcher Manager Mini (Downloader code `632067`) and pick Overdrive as the home app.

## Remote
- OK: open app · ☰ on a tile: hide / app info · ☰ anywhere else or ⚙: menu · Back: jump to top

## Releasing an update
1. Bump `versionCode` and `versionName` in `app/build.gradle`.
2. Build the release APK and copy it to `dist/Overdrive.apk` and `dist/Overdrive-<versionCode>.apk` (the numbered copy dodges GitHub file caching). Delete the previous numbered copy.
3. Set the same `versionCode` and `versionName` in `version.json`.
4. Commit and push. Overdrive checks `version.json` every 30 minutes while it's open, downloads the new APK, and opens the TV's Install screen.

The signing key (`overdrive.jks`) is never committed. Every update must be signed with the same key.
