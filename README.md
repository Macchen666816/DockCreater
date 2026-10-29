<p align="center">
  <img src="docs/assets/dockcreater-banner.png" alt="DockCreater — wallpaper with a glass dock" width="100%">
</p>

<h1 align="center">DockCreater</h1>

<p align="center">Turn a wallpaper into a home-screen wallpaper with a custom glass dock.</p>

<p align="center">
  <a href="README.zh-CN.md">简体中文</a> · Android 12+ · Kotlin · Jetpack Compose · MIT (with third-party notices)
</p>

<p align="center">
  <img src="docs/assets/dockcreater-overview.png" alt="DockCreater workflow and glass styles" width="100%">
</p>

## Download

**[Download the signed Android APK](https://github.com/Macchen666816/DockCreater/releases/latest)** — Android 12+ / ARM64. Choose `DockCreater-1.0.3-arm64-release.apk` under Assets; the source-code archives are not installable apps. SHA-256 checksums are included in the release.

The release uses the existing project release signing identity. It can update earlier releases signed with that key; a Debug installation uses a different signature and cannot be overwritten directly.

## Actual wallpaper output

<img src="docs/assets/wallpaper-comparison.png" alt="Actual exported wallpaper: original, liquid glass, frosted glass, and enlarged dock details" width="100%">

These are real 1080 × 2400 PNG outputs from the app's CPU renderer and MediaStore export path, generated on an Android 13 ARM64 emulator. The lower row enlarges the exported dock region. No launcher icons or mock glass overlays were added to these outputs.

View the full-resolution PNGs: [original](docs/assets/wallpaper-original.png) · [liquid glass](docs/assets/wallpaper-liquid.png) · [frosted glass](docs/assets/wallpaper-frosted.png).

## What it does

DockCreater prepares a wallpaper image for a phone home screen and renders a dock-shaped glass surface over it. Import a picture, adjust the crop, position or detect the dock, tune the glass, then export a full-resolution PNG.

- **Fit the wallpaper to the device.** The app reads the current display's logical dimensions, crops to the screen ratio, and exports at that resolution.
- **Place the dock.** Adjust its size and position by hand, or analyze a home-screen screenshot with OpenCV and review the detected icon row before using it.
- **Choose a glass treatment.** Use a frosted-glass style or a static liquid-glass renderer with controls for corner radius, blur, refraction height, refraction amount, and chromatic aberration.
- **Preview and export.** Android 13+ uses AGSL for a responsive live liquid-glass preview. Android 12 and the full-resolution PNG export use the CPU renderer.
- **Work locally.** Wallpaper processing and export happen on device. The app declares no `INTERNET` permission and does not upload wallpapers.

<p align="center">
  <img src="docs/assets/dockcreater-detection.png" alt="Screenshot-assisted dock position detection" width="32%">
  <img src="docs/assets/dockcreater-glass.png" alt="Adjustable liquid-glass preview" width="32%">
  <img src="docs/assets/dockcreater-crop.png" alt="Wallpaper crop preview" width="32%">
</p>

The screenshots above are captures of the running Android app with original sample artwork. Try the detector with this [synthetic home-screen screenshot](docs/assets/demo-homescreen.png).

## Compatibility

| | Support |
|---|---|
| Android | Android 12 (API 31) and newer |
| Package ABI | `arm64-v8a` only in the current build |
| Liquid-glass live preview | AGSL on Android 13+; CPU preview on Android 12 |
| Export | Full-resolution PNG rendered on device |
| Launcher behavior | Device display dimensions are detected, but each OEM launcher may apply its own wallpaper scaling or parallax |

The UI is currently Chinese-language. English documentation does not imply an English app translation. This is a personal project, and launcher-specific layouts and devices beyond the tested configuration need additional validation.

## Build from source

1. Install Android Studio with Android SDK Platform 37 and an ARM64 Android 12+ device or emulator.
2. Open this repository in Android Studio and allow Gradle sync to complete. The Gradle wrapper pins the project toolchain; use the Gradle JDK selected by Android Studio or the version required by `gradle/gradle-daemon-jvm.properties`.
3. Run the `app` configuration, or build a debug APK:

   ```sh
   ./gradlew assembleDebug
   ```

The debug APK is written under `app/build/outputs/apk/debug/`. A release build requires a signing key configured outside the repository. Do not commit signing properties, keystores, passwords, or a personal release key. A different key cannot update an APK signed by another key.

## Project notes

- [Architecture and rendering pipeline](docs/architecture.md)
- [Testing and known limits](docs/testing.md)
- [Third-party licenses and notices](THIRD_PARTY_NOTICES.md)

## License

Original DockCreater code and documentation are offered under the [MIT License](LICENSE), subject to the scope described in [third-party notices](THIRD_PARTY_NOTICES.md). Adapted Backdrop code and included dependencies retain their own licenses.
