# Overdrive Performance Overhaul

## Changes in this source revision

- `ThemeView.java`: replaced the uncapped render loop with a paced 60 FPS target, adaptive 30 FPS fallback under sustained rendering pressure, recovery hysteresis, elapsed-time animation updates, and safer surface lifecycle handling.
- `TileView.java`: large app artwork from `BitmapDrawable` sources is now downscaled to the requested tile-art dimensions instead of retaining unnecessarily large source bitmaps.
- `AppRepo.java`: removed one redundant intermediate bitmap resize in the blurred-art generation path.
- `BackgroundView.java`: cached the static speck/star geometry in a hardware layer so the moving background can reuse the rendered layer.

## Build and test status

The source edits are in place. A local Android build could not be completed in this environment because the Gradle wrapper needs to download Gradle 8.14.3 and network access to `services.gradle.org` is unavailable. No rebuilt APK is included. Build and test this source with Android Studio or a machine that has the required Gradle distribution and Android SDK installed.

Recommended device checks:
1. Cold launch and time until the first row is usable.
2. Rapid D-pad navigation across many rows.
3. Focus transitions while animated themes are active.
4. Open and return from several apps.
5. Leave the launcher idle for several minutes and check heat, memory, and frame pacing.
