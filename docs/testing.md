# Testing and known limits

## Test coverage

The repository contains JVM tests for screen-profile calculations and Android instrumentation tests for:

- OpenCV dock rows, nested contours, dark/light calibration, partial rows, and fallback boundaries
- Resolution-specific calibration persistence
- CPU liquid-glass rendering and pixels outside the dock region
- Compose glass controls and GPU preview lifecycle behavior

Run available checks with Android Studio or the Gradle wrapper:

```sh
./gradlew testDebugUnitTest lintDebug
./gradlew connectedDebugAndroidTest
```

Connected instrumentation tests require a running compatible Android device or emulator. Hardware-specific AGSL behavior and OEM launcher scaling cannot be established by JVM tests alone.

## Known limits

- Current package ABI filter is `arm64-v8a`; x86 emulators and 32-bit-only devices are not included in release packaging.
- API 33+ liquid preview uses AGSL; API 31-32 uses a lower-resolution CPU preview. Export is CPU-rendered at the output resolution.
- The detector is a geometry/edge heuristic, not a trained icon classifier. Transparent icons, unusual grids, multiple rows, and busy wallpapers can need manual adjustment.
- The app knows display dimensions, but cannot control each launcher’s wallpaper zoom, parallax, or home-screen padding. Confirm the exported image on the target launcher.
- The app interface currently uses Chinese strings.

## Public source hygiene

Regression images in tests are generated in code. User-supplied screenshots, local output directories, device captures, signing material, Android SDK paths, and local investigation notes are excluded from this source repository.
