# Image assets

- `wallpaper-original.png`, `wallpaper-liquid.png`, and `wallpaper-frosted.png` are actual 1080 x 2400 outputs generated on Android 13 ARM64 through `WallpaperRenderer.render` and `WallpaperRenderer.save`, then read back from MediaStore. `wallpaper-comparison.png` arranges those exports and enlarged dock crops without changing the rendered glass. The liquid example uses corner radius 0.5, blur 0 dp, refraction height 0.3, refraction amount 0.6, and chromatic aberration 0.18; dock width 0.9, height 0.085, bottom 0.035. Frosted mode uses the default 28 blur / 0.28 opacity settings. Source artwork is `demo-wallpaper.png`.

- `dockcreater-banner.png` and `dockcreater-overview.png` are original illustrations drawn locally for this repository.
- `demo-wallpaper.png` and `demo-homescreen.png` are original procedural artwork and synthetic icon shapes drawn locally. The home-screen sample contains no personal screenshot data.
- `dockcreater-detection.png`, `dockcreater-glass.png`, and `dockcreater-crop.png` are real captures of DockCreater 1.0.3 running on a Pixel 7 API 33 Android emulator at 1080 x 2400, using the local demo assets above.

An attempt to generate the banner through the previously used 9527 image API returned HTTP 403 during this preparation. No AI-generated or third-party image is included; the banner and sample art were drawn locally instead.
