# Architecture

## Runtime flow

1. `ScreenProfile` reads the app window's current logical display dimensions. The chosen image is center/focus-cropped to that ratio and retained as the editor source.
2. `DockDetector` scales a supplied home-screen screenshot to a 540-pixel working width. OpenCV combines calibrated-background RGB difference masks with Canny edge passes, merges nested contours in favor of larger outer bounds, and looks for a plausible icon row. If full-row detection fails, balanced left and right anchors may yield a reviewable envelope; missing icons are not fabricated.
3. `MainActivity` keeps crop, dock geometry, glass type, and the five liquid-glass values in Compose state. The GPU preview view receives updated settings without rebuilding the source wallpaper for each slider movement.
4. `GpuWallpaperPreview` uses AGSL on API 33+. API 31-32 computes a smaller CPU preview. `WallpaperRenderer` composites and saves a full-resolution PNG on the CPU.

## Glass renderers

The frosted mode blurs and tints the dock region. The liquid renderer approximates a static optical surface: a rounded-rectangle lens profile changes sampling coordinates, channel offsets produce chromatic dispersion, and edge illumination and directional shading suggest thickness.

The five exposed liquid controls are corner radius, blur radius, refraction height, refraction amount, and chromatic aberration. AGSL and CPU implementations target the same settings and visual model, but hardware sampling and rasterization can produce small pixel differences. They are separate preview/export implementations, not a byte-identical shader pipeline.

The interactive glass controls and Backdrop rendering sources are adapted from AndroidLiquidGlass 2.0.1. Their Apache-2.0 terms and source notice are retained; see [third-party notices](../THIRD_PARTY_NOTICES.md).

## Technology

- Kotlin, Android Gradle Plugin, Gradle Wrapper
- Jetpack Compose and Material 3
- Android Graphics / AGSL `RuntimeShader` for API 33+ preview
- OpenCV Android 4.12.0 for screenshot contour analysis
- Android `Bitmap`, Canvas, and CPU sampling for portable preview/export paths

The app does not declare `INTERNET`; image selection, processing, calibration persistence, and PNG export are local.
