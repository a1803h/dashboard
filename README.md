# Drive Dashboard 1.2.1-alpha1

## Update 1.2.1
- Three-character maximum numeric values on all six tiles: 9.5, 10, 125. Whole numbers have no .0; 1000+ is displayed as 999 due to the 3-character limit (underlying reading is retained).
- Brightness control is redrawn with the exact two Figma layers and their correct stacking: 1312 × 170.406, border 14.481 #212121, black inset background, #BABABA foreground pill starting at the same outer-left coordinate with no inset. Both caps remain round. Dynamic foreground length still follows actual selected brightness; the Figma screenshot illustrates approximately 58% brightness.
- Everything else remains unchanged from 1.2.0-alpha1.


Updated Figma: https://www.figma.com/design/hQgk5FZRL6ShbtnAiIcd8j/Untitled?node-id=1-540

## Changes
- Figma 1440×3200 precise coordinates for six cards; fixed typography alignment: value Y is 51.06 rather than 119 design pixels. Text no longer overlaps KM/MIN/KM/Ч units.
- Long press **ПРОЙДЕНО** resets distance and trip timer, but preserves target distance.
- Speed, distance traveled and active elapsed time work without any target being entered. A target added during an untargeted trip preserves accumulated distance and time.
- Double tap speed toggles forced pause/play; long press speed restores automatic mode.
- Lower-power GNSS request cadence (1500ms / 1m rather than 750ms / 0m); battery read once per minute; elapsed time visible state updates only when the minute changes. The timer still accumulates precise elapsed milliseconds.
- On resume asks Android for a 60Hz refresh-rate preference and sends Xiaomi's Camera 6.1 PowerKeeper FPS control action; restores prior window preference on pause, sends isRestore=true to PowerKeeper. **10–60 Hz is not guaranteed**: HyperOS and the hardware pick actual refresh rates and might ignore the third-party PowerKeeper broadcast.
- Clock, six tiles, brightness slider, battery and native app icons remain aligned to reference 1440×3200 layout.

## How to build
JDK 17, Android SDK 35, Gradle 8.9. GitHub Actions -> Build Android APK -> artifacts. See app/build.gradle.kts.

## Limitations
- GPS monitoring works only while the dashboard activity is visible; foreground tracking service is not implemented.
- Vehicle/walking recognition uses Google Play Services ActivityRecognition when supported with GPS speed fallback. Activity classification can be inaccurate.
- Arrival ETA is approximate and derived from GPS average driving speed, not road routing/traffic.
- Android preferredRefreshRate and Xiaomi PowerKeeper broadcast provide best-effort adaptive refresh; actual lower refresh limit depends on HyperOS/panel.
