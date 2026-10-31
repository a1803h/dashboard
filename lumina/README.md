# Lumina — Android photo editor (v0.1 source preview)

A native, privacy-first photo editor modeled on the approved dark/beige LUMINA concept.

**No generative AI. No remote endpoints. No analytics or network permission.** Every effect is a deterministic RGB transform that preserves the photo's subject, geometry, faces and objects. These are original color profiles inspired by photographic aesthetics; they are *not* Kodak/Fujifilm color science or certified film emulations.

## Implemented

- Dark home screen with hero, quick presets, history and batch processing
- Native Android system photo picker (no blanket photo library permissions)
- Eight real color/grain presets in five groups: Kodak Portra, Kodak Gold, Fuji Classic, Fuji Natural, DSLR Natural, Cinematic, Monochrome, Clean Pro
- Non-destructive editing: intensity, grain, contrast, warmth, fade, saturation
- Dragging before/after split view
- Native JPEG export at quality 96 into `Pictures/Lumina` through MediaStore
- Basic EXIF orientation handling at import
- Batch processing of up to 20 photos (one profile applied to each)
- Local persistent history of exported previews
- Share-to-Lumina support for images
- No Internet permission. App never uploads any photo.

## Technical stack

- Kotlin, Jetpack Compose / Material 3
- Android Gradle Plugin 9.1.1, Gradle 9.3.1, Compose BOM 2026.09.00, compileSdk 37
- Kotlin Compose compiler plugin 2.4.10 (AGP 9 supplies Kotlin integration)
- minSdk 29 (Android 10+); targetSdk 35

## Build

Open this folder as a project in a recent **Android Studio** with Android SDK Platform 37, JDK 17+ and Gradle 9.3.1 available. Sync the project, then select **Build > Build APK(s)**.

The project was created in an environment without Android SDK, Gradle, Google Maven access or a Gradle wrapper JAR. Consequently, **this source is not yet compiled, tested on a device or guaranteed to build without adjustments**. Install the Gradle wrapper locally with `gradle wrapper --gradle-version 9.3.1` (if Gradle is installed) before using `./gradlew assembleDebug`. The included `gradle/wrapper/gradle-wrapper.properties` sets the matching distribution.

## Known v0.1 limitations / next iterations

- Export samples images to **4096 px on the longest side** to keep memory and processing manageable on-device. It is not yet a lossless or full-sensor-resolution 50MP editor.
- Photo processing uses CPU ARGB_8888. Rendering can lag with complex large images; a tiled/GPU pipeline is planned.
- JPEG export is lossy. The source remains untouched; metadata other than orientation is not reproduced in the new file.
- Preset thumbnails in this prototype are illustrative crops from the agreed concept, not live per-preset renders. Actual output is generated from the selected photo.
- Clean Pro and DSLR Natural are tonal profiles, **not** an AI denoiser or a physically accurate DSLR/depth-of-field simulation.
- Colour management is limited to 8-bit sRGB-ish processing; wide gamut and true film emulation need a color-managed pipeline.
- Comparison preview and export differ slightly because grain uses output pixel coordinates.
- No full device testing or user-verified Figma pixel matching yet.

## File map

- `app/src/main/java/tj/lumina/photo/MainActivity.kt`: Compose UI / navigation
- `model/Presets.kt`: authored looks and tweakable settings
- `processing/PhotoProcessor.kt`: deterministic, local RGB processing
- `storage/PhotoStore.kt`: import, EXIF rotation, MediaStore export and history
- `app/src/main/assets/`: sample thumbnail crops from our prior concept illustration

## Next technical priorities

1. Gradle build+instrumented smoke tests on Xiaomi 13 Ultra.
2. Pixel-perfect import of the designer's final Figma parameters.
3. GPU filters + tile pipeline (avoid downsampling on export).
4. Accurate split preview, color management, and preset thumbnail rendering from the actual photo.
5. Optional local ML denoiser (never generative), if worthwhile.

## Interactive browser prototype

`demo/preview.html` is a fully self-contained local browser demo of the same screen layout. Open it in Chrome/Firefox. It supports choosing a photo, real local pixel processing with the same eight presets, the split comparison, intensity/manual controls, JPEG downloads, batch mode and session-only history. It is intended for immediate UX feedback before the native APK is buildable; it is **not** a substitute for the Android app.
