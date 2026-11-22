# Overdrive

Console-style home screen for Fire TV. Big animated app tiles, clock and weather, recently used row, hidden apps, and self-updates from GitHub Releases.

## Install on the TV
1. In Downloader, open: `github.com/Vyd02/Overdrive/releases/latest/download/Overdrive.apk`
2. Install Launcher Manager Mini (Downloader code `632067`) and pick Overdrive as the home app.

## Remote
- OK: open app · ☰ on a tile: hide / app info · ☰ anywhere else or ⚙: menu · Back: jump to top

## Releasing an update
1. Bump `versionCode` and `versionName` in `app/build.gradle`.
2. Commit, then push a tag that matches: `git tag v1.0.1 && git push --tags`
3. GitHub Actions builds `Overdrive.apk` and attaches it to the release. The TV picks it up within 6 hours, or right away from ⚙ Menu → Check for updates.

The signing key is stored as the `OVERDRIVE_KEYSTORE` Actions secret (base64 of `overdrive.jks`). It is never committed. Every update must be signed with the same key.
