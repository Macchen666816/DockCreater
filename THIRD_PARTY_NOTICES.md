# Third-party notices

The root MIT license applies to DockCreater's original project code and documentation only. It does not replace the licenses below.

## AndroidLiquidGlass / Backdrop 2.0.1

- Upstream: [kyant0/AndroidLiquidGlass](https://github.com/kyant0/AndroidLiquidGlass)
- Copyright: Kyant, 2025
- License: Apache License 2.0
- Included license text: [`app/src/main/assets/licenses/Backdrop-Apache-2.0.txt`](app/src/main/assets/licenses/Backdrop-Apache-2.0.txt)
- Upstream notice and adaptation summary: [`app/src/main/assets/licenses/Backdrop-NOTICE.txt`](app/src/main/assets/licenses/Backdrop-NOTICE.txt)
- Adapted sources: `app/src/main/java/com/kyant/backdrop/**`, `GpuWallpaperPreview.kt`, `LiquidGlassRenderer.kt`, `GlassControls.kt`, and `app/src/androidTest/assets/backdrop-reference.agsl`.

These adapted files remain under Apache-2.0 terms. Their upstream notices and applicable license text are retained in the app assets.

## OpenCV

Dock detection uses the OpenCV Android Maven artifact (`org.opencv:opencv:4.12.0`). OpenCV is distributed under Apache License 2.0; its license and notices are provided by the dependency artifact. It is not relicensed by this repository's MIT license.

## Android and Kotlin dependencies

AndroidX, Jetpack Compose, Kotlin, Gradle, and the Android Gradle Plugin are fetched as build/runtime dependencies. Each remains under its own license; consult the respective upstream project and the resolved dependency metadata for the exact versions and notices in a build.
