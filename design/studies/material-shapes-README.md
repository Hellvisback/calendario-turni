# Material 3 Expressive · SVG geometry

`material-shapes.json` contains all 35 named shapes from the official AndroidX `MaterialShapes` API, exported as normalized SVG cubic paths. It is a design asset; no native application files were edited.

## Provenance

- Android Open Source Project, copyright 2024; Apache License 2.0 (`material-shapes-LICENSE.txt`).
- Official definition source: [MaterialShapes.kt, commit a955844256f38a45bb01973b36dec1e6e2342b2e](https://android.googlesource.com/platform/frameworks/support/+/a955844256f38a45bb01973b36dec1e6e2342b2e/compose/material3/material3/src/commonMain/kotlin/androidx/compose/material3/MaterialShapes.kt).
- Curve construction and normalization: official Google Maven binary `androidx.graphics:graphics-shapes-android:1.0.1`.
- The original definitions, vertex coordinates, rounding values, repetition logic, rotations, and calls to `normalized()` are retained. Extraction substitutes only the surrounding Compose `Offset` and 2D `Matrix` plumbing with equivalent Float operations and `fastMap` with `map` so that it can run on a standalone JVM.
- The SVGs serialize the original 943 cubic curves directly to eight decimal places. There is no hand tracing, CSS approximation, point fitting, or curve simplification.

## Browser use

```js
const shape = materialShapes.shapes[index % materialShapes.shapes.length];
// <svg viewBox="0 0 1 1" preserveAspectRatio="xMidYMid meet">
//   <path d={shape.path} fill={color} />
// </svg>
```

Do not stretch the SVG to fill an arbitrary rectangle: some official shapes have intentionally non-square aspect ratios. Keep shape assignment stable per user-defined shift category. The modulo loop repeats after all 35 shapes; it should not change when switching month or scrolling.

For narrow-center shapes such as `Boom`, keep lengthy names and descriptions outside the silhouette or size the inner text to its actual safe area. Official shapes should not be distorted to accommodate text.

## Reproduction and checks

The source and standalone extraction scripts are in `shapes-source/`:

1. `prepare-extractor.py` reads the downloaded, pinned `MaterialShapes.kt` and prepares the standalone Kotlin extraction source.
2. `compile-extractor.ps1` invokes Kotlin with the official geometry binary and exports `extracted-shapes.json`.
3. `package-shapes.py` validates every path is closed, normalized and has the expected number of cubics; it writes the public JSON and SVG gallery.
4. `render-shapes.cjs` renders the gallery in headless Chrome to `material-shapes-gallery.png`.

Validation completed: 35 shapes, 943 cubic curves, all closed and within normalized coordinates. The rendered gallery was visually inspected.
