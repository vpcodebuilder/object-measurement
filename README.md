# Object Measurement

Measure a real-world object from a single photo, using a reference marker of known
size placed in the same scene.

Point the camera at your object with the marker beside it, drag a box around the
object, and read off its width and height.

---

## How the measurement works

1. **Find the marker.** The app looks for a convex quadrilateral, either by colour
   (an HSV window you can tune, or sample by tapping) or by edge contrast (any
   high-contrast rectangle — a sheet of A4, a bank card). Candidates are scored on
   how square their corners are, how well a 4-gon explains the blob, how solid the
   contour is, and how closely the aspect ratio matches the marker you configured.
2. **Refine the corners.** The winning quad's corners are snapped to sub-pixel
   accuracy against the greyscale image.
3. **Build a homography.** The four corners map the image plane onto the marker
   plane in millimetres.
4. **Measure.** Both corners of your selection box are pushed through that
   homography and the distance is taken *on the marker's plane*, so a photo taken
   at an angle still measures correctly.

### What this assumes

**The object must lie roughly in the same plane as the marker.** This is inherent
to measuring from one image: a single photo carries no depth, so the marker's
scale can only be transferred to things at the marker's distance. Put the marker
against the object, not on the floor in front of it.

Accuracy also depends on:

- **Camera angle.** Square-on is best. The app warns when the marker's diagonals
  disagree by more than 25%, which means a steep angle where small corner errors
  get amplified.
- **Box placement.** A box 5% too wide is a reading 5% too large. Use **Auto-fit**
  to snap the box onto the object's edges, then nudge from there.
- **Marker size.** A bigger marker in frame means more pixels per millimetre and a
  smaller relative corner error.
- **Lens distortion** is not corrected. Keep the marker and object away from the
  extreme edges of a wide-angle frame.

---

## Using it

1. **Settings → Reference marker.** Pick a preset (A4, A5, Letter, bank card, CD
   case, sticky note) or type the marker's real width and height in millimetres.
   Nothing can be measured until this is set.
2. **Settings → Detection.** Leave the mode on *Automatic* to start. If your
   marker is a distinct colour, tap **Load a test photo**, then tap the marker in
   the left-hand image — the HSV range is derived from the pixels under your
   finger. The right-hand image shows exactly what the detector sees, with the
   quad it would pick outlined in green.
3. **Camera** (or **Gallery**). The viewfinder outlines the marker live: green
   when the lock is confident, amber when it is shaky. **Auto** captures by itself
   once the lock has held steady for a few frames.
4. **Drag the box** around the object. The size updates as you drag. **Auto-fit**
   tightens the box onto the object for you.
5. **Save.** Results are kept in two slots (Side A / Side B) and can be shared or
   written to your gallery.

Display units (mm / cm / m / inches) are a setting; measurements are always stored
in millimetres.

---

## Building

- **JDK 17** (Android Studio's bundled JBR is fine)
- **Android SDK 35** with build-tools
- Gradle 8.9 via the wrapper, Android Gradle Plugin 8.6.1

```bash
./gradlew assembleDebug
```

```bash
./gradlew test
```

The unit tests cover the corner-ordering and angle geometry on a plain JVM — no
device or emulator needed.

### OpenCV

OpenCV 4.12 comes from Maven Central (`org.opencv:opencv:4.12.0`) and bundles its
own native libraries. There is nothing to download or install by hand.

`app/build.gradle` restricts the packaged ABIs to `arm64-v8a`, `armeabi-v7a` and
`x86_64`. Adding `x86` would grow the APK for the benefit of very old emulators
only.

---

## What changed in 2.0

The app as it stood could not run on any current device, and several defects in
the detector biased measurements in the same direction.

### It could not start

- The project depended on **OpenCV Manager**, a separate APK that was removed from
  the Play Store years ago, and shipped no native libraries of its own. Every
  screen called `OpenCVLoader.initAsync` and silently did nothing when the callback
  never reported success. Now OpenCV is a normal Maven dependency, loaded once at
  process start.
- Builds resolved through **jcenter**, which is read-only and shutting down.
- **No runtime permissions.** `targetSdk` was 27 and `CAMERA` was never requested,
  so the preview was black on Android 6 and later.
- **Storage paths were hand-built** from `getExternalStorageDirectory()`, which
  scoped storage blocked in Android 10. Captures returned a null path and failed.
- **Gallery import** resolved `MediaStore.Images.Media.DATA` to a file path — a
  column that has been unreadable since Android 10.

### The detector was wrong, not just slow

- **Canny on a binary mask, then dilate.** The contour being measured was the
  outside of a thickened edge band, so every marker read a few pixels too large
  and therefore every object too small.
- **`minAreaRect().boundingRect()`** — the axis-aligned box around a *rotated*
  rectangle. For a marker tilted 20° that box is roughly 30% oversized.
- **Corner ordering sorted by y**, then split each pair by x. Rotate the marker
  past a few degrees and two left-edge corners land in the "top" pair, so the edge
  called "width" ran diagonally across the marker. There is now a JVM test for
  exactly this.
- **The right-angle test compared degrees against a variable named for cosines**
  and only checked three of the four corners.
- **Morphology used `new Mat(3, 3, CV_8UC1)`** as its kernel — an *uninitialised*
  matrix, so the structuring element was whatever was in memory.
- **Opposite edges were combined with `Math.min`**, biasing every measurement small
  whenever the marker was tilted at all. They are averaged now.
- **Hue could not wrap**, so red — the most natural marker colour, and the one that
  straddles both ends of the hue axis — was impossible to select.
- **No perspective correction at all.** The object's pixel size was multiplied by a
  single mm/px scalar taken at the marker, which is only valid dead-on.

### It was slow where it mattered

- The white-balance pass **sorted every pixel of every channel** on each call.
  Replaced with a 256-bin histogram and a lookup table: same result, one pass.
- `onDraw` allocated **two bitmaps per frame** to punch out the bright region. A
  clip rectangle does it for free.
- The hue strip rebuilt itself with `setPixels` **once per row, every frame**. It
  is a gradient shader now.
- Capture called `Thread.sleep(2000)` **on the UI thread** hoping a Camera1
  callback had finished writing. The app now saves the frame already in memory —
  the same frame the detector matched.

### Other fixes

- `showConfirmDialog` returned a static field **immediately after `show()`**, before
  the user had touched anything, so every caller acted on a stale answer.
- The view saved the **whole background bitmap into its state Bundle**, risking
  `TransactionTooLargeException` on rotation.
- Queries were built by **string concatenation**; they use bound arguments now.
- `db.close()` inside insert/update tore down the connection other callers held.
- EXIF orientation was ignored (there was a commented-out rotate hack in its place),
  so portrait photos from the gallery were measured sideways.
- Removed dead dependencies (`commons-net`, `ftp4j` — never imported anywhere) and
  the `DirectoryPickerActivity` filesystem browser, which returns nothing outside
  the sandbox from Android 11. Settings import/export now goes through the Storage
  Access Framework as JSON.
- Deprecated `ActionBar` tab navigation and the support-library `ViewPager` — whose
  fragments were reached by reconstructing internal tags — replaced with a
  `TabLayout`.

### New

- Perspective-corrected measurement via a marker homography.
- Live measurement while dragging, instead of a modal "Process" step.
- **Auto-fit**: snap the selection onto the object's edges.
- **Tap to sample**: derive the HSV range from the marker in a photo.
- **Edge detection mode**: use a plain sheet of paper or a card as the marker, with
  no colour tuning at all.
- Marker presets, selectable display units, detection confidence and a
  steep-angle warning.
- Auto-capture on a steady lock, and a torch toggle.
- Share and save-to-gallery.

---

## Known limitations

- Single-plane only: the object must be coplanar with the marker.
- No lens-distortion correction.
- The two result slots are working storage, not an archive — the schema is rebuilt
  on upgrade.
- The capture screen is landscape-only.

## Licence

See [LICENSE](LICENSE).
