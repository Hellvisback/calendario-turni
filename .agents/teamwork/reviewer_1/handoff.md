# Adversarial Review & Handoff Report - Round 1

## Executive Summary
This round reviewed and hardened the shift import experience ("Importazione Turni") in the Android Jetpack Compose application.
Through rigorous adversarial testing, we discovered 5 substantial bugs and edge-case defects in the prior attempt, including raw PDF FlateDecode stream data leaks, substring collisions on team names ("Squadra Alfa" misidentified as "Squadra A"), desynchronized worker selection states, star animation stalls on rapid taps, and table header rows parsed as fake employees.

All identified defects were fixed, verified via an expanded 23-test suite in Gradle, and validated via successful debug compilation (`assembleDebug`).

---

## 1. What the Prior Attempt Got Wrong

### Issue 1: Compressed PDF Streams & Leaked Structural PDF Artifacts (R1)
- **Input:** Real-world PDF files containing `/Filter /FlateDecode` content streams or structural markers (`xref`, `trailer`, `startxref`, `%%EOF`, `\d+ \d+ obj`).
- **Expected:** Decompress FlateDecode streams to extract textual shifts; purge all PDF internal syntax so no binary or unreadable garbage appears in the text area.
- **Actual:** The prior attempt lacked Flate stream decompression, falling back to a raw regex on binary data (`wordsRegex`) that leaked internal PDF tags (`FlateDecode`, `Catalog`, `XRef`, `FontDescriptor`) and random ascii fragments into the text area.
- **Root cause:** `extractTextFromPdfBytes` didn't decompress zlib / FlateDecode streams; `TextImportSanitizer.sanitize` did not filter lines matching PDF internal keywords (`xref`, `trailer`, `startxref`, `%%EOF`, `obj`).

### Issue 2: Substring Collisions on Team Headers (R1)
- **Input:** Generic schedule with team header `Squadra Alfa`.
- **Expected:** Recognized as team `Squadra Alfa`.
- **Actual:** Misidentified as `Squadra A` or lumped into fallback `Rotazione Turni`.
- **Root cause:** 
  1. `recognizedTeamNames.find { line.contains(it) }` evaluated `"Squadra Alfa".contains("Squadra A")` as `true`.
  2. `isHeaderEligible` checked `!line.contains("al")` to exclude date lines ("dal ... al ..."), but `"Alfa".contains("al")` is `true`, disqualifying "Alfa", "Centrale", "Ospedale" from header matching.

### Issue 3: Table Header Row Interpreted as Fake Employee (R1)
- **Input:** CSV/TSV table starting with column headers `Nome, Ruolo, 01/10, 02/10, 03/10`.
- **Expected:** Header row ignored during employee parsing.
- **Actual:** Single-digit dates matched shift codes `1, 2, 3`, creating a fake employee named `"Nome"` with shifts `1, 2, 3`.
- **Root cause:** `parseEmployeeLine` lacked a guard against standard tabular header keywords (`Nome`, `Dipendente`, `Cognome`, `Lavoratore`, `Matricola`, `Data`, `Turni`).

### Issue 4: Desynchronized Upstream Worker Filter & Redundant Duplicate Input (R2)
- **Input:** User entered worker name (e.g. "Marcozzi" or "Menna") upstream in "Nome da cercare nel file" and clicked "Analizza".
- **Expected:** The upstream query filters and pre-selects the worker in the results, highlighting their row and displaying their shifts.
- **Actual:** Results section repeated a duplicate second search field whose state `employeeSearchQuery` remained empty (`""`), which defaulted to `result.employees.take(4)`, hiding the requested worker if they were beyond index 4.
- **Root cause:** Duplicated and desynchronized state between `workerNameFilter` and `employeeSearchQuery`.

### Issue 5: Star Animation Stalling on Successive Taps (R3)
- **Input:** Rapid successive taps on the favorite star button (toggle on -> toggle off).
- **Expected:** Fluid, immediate spring bounce on each tap.
- **Actual:** Animation stalled on the second tap because `var animateTrigger` remained `true` during the transition.
- **Root cause:** Relying on boolean toggle with `finishedListener` in `animateFloatAsState` instead of Compose's standard `Animatable` with `snapTo` and `animateTo(spring)`.

### Issue 6: Absence of Dedicated Final Action Block (R4)
- **Input:** Reaching the end of the results section.
- **Expected:** Acceptance criterion: "I pulsanti finali sono 'Salva nel catalogo' e 'Modifica'".
- **Actual:** The buttons only appeared inside individual team cards, with no final closing action bar at the bottom.
- **Root cause:** Missing screen-level closing actions card.

---

## 2. Changes Made

1. **`NgeniaTurniParser.kt`**:
   - Added `inflateStream` to decompress zlib/FlateDecode streams in PDFs.
   - Added `extractPdfTextTokens` to parse `(...) Tj` and `[...] TJ` operators directly from uncompressed content.
   - Enhanced `TextImportSanitizer` to eliminate PDF structural lines (`xref`, `trailer`, `startxref`, `%%EOF`, `obj`) and high-density binary files (>20% non-printable bytes).
   - Expanded XLSX parsing to include `sheet*.xml` inline string cells (`<c t="inlineStr"><is><t>...</t></is></c>`).
   - Fixed `isRfiDoc` to only activate on genuine RFI/ngenia markers.
   - Fixed header regex by replacing naive `!line.contains("al")` with precise date regex `\b(dal|al)\s+\d{1,2}[/.-]` and using word boundaries for team names.
   - Added header row exclusion in `parseEmployeeLine`.

2. **`ImportShiftsScreen.kt`**:
   - Replaced redundant duplicate search field in Step 1 with direct upstream synchronization.
   - Highlighted matched worker with active filter badge and real-time updates.
   - Upgraded `ElasticStarButton` to use `remember { Animatable(1f) }` with instant contraction (`0.72f`) and elastic spring bounce (`spring(Spring.DampingRatioMediumBouncy, Spring.StiffnessLow)`).
   - Added prominent final closing card with "Salva nel Catalogo" and "Modifica" (with modal warning dialog).
   - Added `.navigationBarsPadding().padding(bottom = 16.dp)` to `AnimatedStatusPopup`.

3. **`TurniModelsScreen.kt`**:
   - Updated favorite rotation toggle to use `ShiftRepository.toggleDefaultRotation`.

4. **`NgeniaTurniParserTest.kt`**:
   - Added tests:
     - `testTextImportSanitizerPdfStreamFlateDecodeExtraction`
     - `testTextImportSanitizerRejectsHighDensityBinaryFiles`
     - `testCsvTableIgnoresHeaderRows`
     - `testNonRfiScheduleDoesNotInjectRfiSpecificTeams`

---

## 3. Verification Record
- **Deep Verification:**
  - `.\gradlew.bat testDebugUnitTest`: Exited with code 0. 23 unit tests executed, 23 passed (100%).
  - `.\gradlew.bat assembleDebug`: Exited with code 0. APK built successfully.
- **Shallow Verification:**
  - Verified Kotlin source code against Obsidian Liquid Glass style guidelines and Riccardo Breccia ergonomics.
  - Verified dialog copy matches exact specification: *"Stai per uscire da questa schermata per passare a quella di modifica, perderai queste informazioni"*.

---

## 4. Remaining Risk & Next Steps
- Android device UI visual verification on physical device Realme 8 Pro (`cb08bc57`) if connected.
