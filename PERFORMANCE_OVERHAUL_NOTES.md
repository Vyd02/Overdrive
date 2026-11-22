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

## 1.4.0: vsync-locked pass

- `ThemeView.java`: the background now draws once per display refresh (Choreographer on its own thread), using the display's frame timestamps for motion. No sleep timer, so no drift against the screen. Under pressure it lowers the background's resolution (960x540, 800x450, 640x360) instead of dropping to 30 FPS. If a sharper size fails to hold, it stays at the lower one instead of flip-flopping.
- `Themes.java`, `IntroView.java`: no objects are created per frame (cached color filters, matrix-moved gradients, allocation-free hue shifts), so memory cleanup never pauses rendering.
- UI: whole-screen fades and zooms render once into a GPU layer; tiles and text fade without offscreen passes; no real-time elevation shadows; one fewer full-screen shade layer; no window background fill; hidden backdrop images stop drawing.
