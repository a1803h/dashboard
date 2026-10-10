# DriveDashboard Figma layout handoff

Design file: https://www.figma.com/design/PA1wkSq15HfuKnjCAT2rtn/Untitled?node-id=0-1

## Reference frames
- Main: node `5:33`, 1080 × 2580.353 px
- Settings: node `22:255`, 1080 × 2961 px
- Android: Kotlin + Jetpack Compose, pixel-proportional frame scaling (Settings can scroll)

## Main frame
- GPS label and clock: y 66.706; MiSans Semibold 55.217 px
- GPS circles: x 174.706, y 88.941, diameter 28.588 px
- Speed digits: text node `4:7`, centered, MiSans Latin Semibold, 460.545 px, **hollow**, 5.294 px white outline at reference speed; dynamic color and stroke/glow
- RPM above speed: MiSans Medium 61.619 px, #696969
- Average speed: MiSans Semibold 94.539 px, #64C8FF, **no AVG label**
- Suggested gear: MiSans Regular 137.349 px, #313131
- Trip block: origin (41.294, 752.823), width 998.471
- Travel progress: origin (41.294, 865.059), 997.412 × 29.647 px, green #009400
- Brightness and volume labels: y 1112.677, MiSans Medium 31.423 px
- Slider tracks: x 41.294 and 559.059, y 1167; 479.647 × 104.706 px, outline #212121 / 5.294 px, fill #BABABA
- Media frame: x 41.294 y 1336.677, 997.412 × 462.706, 5.294 px #212121 border, 54px corner radius
- Media control original artwork: Figma node `16:249`, committed as `figma_media_controls.png`, with individual Android hit zones
- App grid: x 41.294 y 1863.677, 4×2, icons 194.824px with 72.235px horizontal gap, 54px vertical gap; actual Android app icons only, no colored Figma placeholders
- Settings button: x 402.353 y 2386.588, 276.706 × 97.412

## Settings frame
- Title: MiSans Light 110.835 px, top y 115.676
- Original back-arrow artwork: Figma node `27:281`, committed as `figma_back_arrow.png`
- Applications row: y 338.676; Select x 576, Order x 818; pills 220.629 × 93.487
- Rules: y 495.676 and 716.676
- Music player row: y 559.676
- Gearbox header: y 780.676, MiSans Regular 69.782 px
- Gear rows: y 909.676, 1533.676, 2157.676; left x 41, right x 578
- Gear circle: diameter 93.49, dark #242424
- Editable Speed fields: y 1105.676 (+624 per row); RPM fields y 1335.676 (+624 per row)
- Each field: 223 × 110 px, outline #242424, 5px, text MiSans Normal 50 px
- Sixth gear is absent from recommendations when its four fields are blank/incomplete.
- Settings auto-save and validation remain functional; validation messages appear only for invalid input.

## Status
- Static layout mapped to exact Figma measurements.
- Native fonts: original MiSans Latin (loaded from Xiaomi at Android compile time; font binaries not distributed here).
- Integration/gear logic tests run; compile-only GitHub Actions workflow runs on this branch.
- **Not yet visually verified on a physical phone**. Android density/font scale, gesture insets and runtime data may require one final screenshot-based adjustment.
- **No new APK release until the user approves the layout for build.**
