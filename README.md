# Sora (formerly Overdrive)

Cinematic home screen for Fire TV, with its own look: PlayStation 5 meets Apple TV.

- Spotlight: the focused app's art fills the screen, big title, and Open / Options / Pin buttons
- Signature gliding focus light, zoom-into-app launch animation, console sounds, startup intro
- 6 interactive animated themes with live previews, idle screensaver
- Favorites you can reorder, Continue, HDMI inputs, auto categories, quick settings
- Search with a remote-friendly on-screen keyboard
- Two-pane Settings: appearance, sound and TV volume, home screen, power-up, weather, privacy, updates, every Fire TV settings page, device info
- Hidden apps, Back & Home guard, opens on power-up, self-updates from this repo
- Typefaces: Outfit and Inter (SIL Open Font License)

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
