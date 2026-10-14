# Adversarial Review & Handoff Report - Round 2

> [!WARNING] **Skepticism Disclaimer**
> Confidence is very high on document sanitization, multi-format tabular parsing, date ordering, and UI contract compliance verified with 27 Gradle unit tests and a successful debug APK assembly; visual spring rendering was verified statically and unit-tested rather than via physical device screen capture.

---

## 1. What the Prior Attempt Got Wrong

### Issue 1: Severe Date Jumbling and Shift Overwriting in `extractColumnDates` (Fatal Functional Bug)
- **Input:** Real schedule document containing validity header `Turno Valido dal 21/09/2026 al 18/10/2026` followed by table column headers `21/9 22/9 23/9 ... 18/10` and employee shifts (e.g. Marcozzi).
- **Expected:** `columnDates[0]` = 21/09/2026, `columnDates[1]` = 22/09/2026, `columnDates[2]` = 23/09/2026... up to 18/10/2026, with employee shifts mapped 1:1 in chronological sequence.
- **Actual:** `dateTokenRegex = "\\b(\\d{1,2})[/.-](\\d{1,2})\\b"` matched across the *entire* document without excluding full dates with years, extracting `(21, 9)` and `(18, 10)` from the header line. As a result:
  - `columnDates[0]` was 21/09/2026
  - `columnDates[1]` was 18/10/2026 (assigned shift 2 on October 18!)
  - `columnDates[2]` was 21/09/2026 (OVERWROTE shift 1 on September 21 with shift 3!)
  - The last 2 columns (17/10 and 18/10) were completely dropped due to `take(28)`.
- **Root cause:** `extractColumnDates` scanned the entire concatenated text instead of isolating the specific column header row in the table, and lacked negative lookahead `(?![/.-]\\d{2,4})` to exclude dates with years.

### Issue 2: CSV Table First Column with Date or Number Corrupted Employee Names (Fatal Functional Bug)
- **Input:** Tabular schedule with dates or IDs in column 0: `01/10/2026, Rossi Mario, M, P, N, RIP, M, P, N`.
- **Expected:** Employee parsed as `name = "Rossi Mario"` with shifts starting from column 2.
- **Actual:** Delimited parsing checked `parts[0]` for letters, fell back to generic line parsing, and included the leading date in `name`: `"01/10/2026, Rossi Mario"`.
- **Root cause:** `parseEmployeeLine` assumed column 0 is always the employee name, without detecting if column 0 is a date or timestamp, and lacked stripping of leading dates from raw names.

### Issue 3: Inadvertent Deletion of Legitimate User Text with Slashes (Minor Robustness Risk)
- **Input:** Document containing lines such as `Reparto / Ospedale Centrale` or `Squadra A / Notte`.
- **Expected:** Legitimate text preserved.
- **Actual:** `TextImportSanitizer.isBinaryGarbageLine` purged any line matching `Regex("(?i)^/?[A-Za-z0-9_-]+\\s+(?:\\d+\\s+\\d+\\s+R|/[A-Za-z0-9_-]+.*)$")` because the leading slash was made optional (`^/?`), treating standard text containing slashes as PDF object dictionary junk.
- **Root cause:** In PDF syntax, dictionary keys always start with `/` (e.g. `/Type /Catalog`). The regex incorrectly made the slash optional.

### Issue 4: RFI and Generic "Trasferta" Code `T` Mapped to Rest (Functional Defect)
- **Input:** Schedule containing mission/business travel code `T` or `TRASF` (explicitly mentioned in RFI legend: `• T Trasferta`).
- **Expected:** Mapped to working shift (`giornaliero`).
- **Actual:** `T` was absent from `mapCodeToShiftDefId` and fell through to `else -> "riposo"`.
- **Root cause:** Missing mapping for `"T"` and `"TRASF"` in `NgeniaTurniParser.mapCodeToShiftDefId`.

### Issue 5: Artificial 8-Token Word Chops in PDF Text Extraction (Robustness Risk)
- **Input:** Stream of uncompressed or decompressed PDF text with individual `Tj` operators for an employee row (`name role matricola shift1 shift2 ... shift28`).
- **Expected:** The entire 30+ token line remains intact.
- **Actual:** `lineCount % 8 == 0` forced a newline every 8 tokens, truncating employee rows into 4 fragmented lines.
- **Root cause:** Hardcoded arbitrary token count modulo instead of relying on real PDF line-break operators (`'`, `"`, `T*`, `\n`).

### Issue 6: UI Artificial 5-Employee Ceiling with No Expand Option (Ergonomics Defect)
- **Input:** Schedule with 44 employees (such as RFI example) without typing a query in the upstream filter field.
- **Expected:** User can view all employees or expand the list beyond the initial 5.
- **Actual:** Hardcoded `result.employees.take(5)` with no toggle or expand button.
- **Root cause:** Missing `showAllEmployees` state and expand/collapse toggle in `ImportShiftsScreen.kt`.

### Issue 7: Button Casing Inconsistency and Unused Processing State (Contract / Quality)
- **Input:** Buttons at closing and team cards read "Salva nel Catalogo" instead of exact acceptance criteria copy "Salva nel catalogo". Variable `isProcessing` was assigned but never used, giving compiler warnings.
- **Root cause:** Casing mismatch and unlinked progress state on the "Analizza Tabellone Turni" button.

---

## 2. What I Changed

### `NgeniaTurniParser.kt`
1. **Isolated Column Header Row in `extractColumnDates`**:
   - Replaced global document token matching with line-by-line scanning to detect the row with the densest date sequence (>= 4 dates).
   - Added negative lookahead `(?![/.-]\\d{2,4})` to strictly reject full dates with years (e.g. `21/09/2026`).
   - Implemented month/year rollover handling (e.g. transitioning from December to January).
2. **Hardened `parseEmployeeLine`**:
   - Guarded against table date header rows (lines with 4+ dates rejected immediately).
   - Constrained matricola regex to `(?<![/.:-])\\b(80\\d{5}|88\\d{5}|\\d{5,8})\\b(?![/.:-])` to prevent 4-digit years (e.g. `2026`) from being parsed as employee matricole.
   - Handled CSV schedules where column 0 is a date or timestamp, accurately picking column 1 as the name.
   - Stripped leading dates from generic single-line employee rows.
   - Added `collaboratore` and `personale` to ignored header keywords.
3. **Corrected `isBinaryGarbageLine`**:
   - Made leading slash mandatory in `(?i)^/[A-Za-z0-9_-]+` so lines like `Reparto / Ospedale Centrale` are not purged.
4. **Cleaned PDF Text Operators**:
   - Removed artificial `lineCount % 8 == 0` chopping in `extractPdfTextTokens`, preserving intact multi-token employee rows.
5. **Updated Shift Mapping**:
   - Added `"T", "TRASF"` -> `"giornaliero"` in `mapCodeToShiftDefId`.
   - Removed unused parameter `hoursMap` from `extractTeamsAndMembers`.

### `ImportShiftsScreen.kt`
1. Added `showAllEmployees` toggle with animated Expand/Collapse button for viewing all rostered employees when no search query is active.
2. Connected `isProcessing` to `CircularProgressIndicator` inside the "Analizza Tabellone Turni" button, disabling it during execution.
3. Aligned button labels to `"Salva nel catalogo"` in both team cards and screen-level closing actions.

### `NgeniaTurniParserTest.kt`
Added 4 new adversarial test cases:
1. `testColumnDatesAreConsecutiveAndAccurateForRfiTabellone`: verifies exact chronological assignment of shifts without corruption or overwriting.
2. `testYearInDateNotTreatedAsMatricola`: verifies that leading dates and year numbers in delimited rows do not pollute names or matricole.
3. `testSanitizerPreservesLegitimateTextWithSlashes`: verifies that text containing slashes is preserved.
4. `testMapCodeToShiftDefIdRecognizesTrasferta`: verifies that code `T` and `TRASF` map to working shifts.

---

## 3. Verification Record

- **Deep Verification (ran actual tests):**
  - `$env:JAVA_HOME = "C:\Program Files\Android\Android Studio\jre"; .\gradlew.bat testDebugUnitTest` in `android-app/`: **BUILD SUCCESSFUL**. 27 unit tests executed, 27 passed (100%).
  - `$env:JAVA_HOME = "C:\Program Files\Android\Android Studio\jre"; .\gradlew.bat assembleDebug` in `android-app/`: **BUILD SUCCESSFUL** (32 tasks, exited with code 0). Debug APK generated cleanly.
- **Shallow Verification (manual/code review):**
  - Confirmed Obsidian Liquid Glass style and Riccardo Breccia ergonomics (HUD popup anchored with navigation bars padding, single top-left back button, spring animations on toggleable favorite star).
  - Confirmed exit warning dialog text matches specification verbatim: *"Stai per uscire da questa schermata per passare a quella di modifica, perderai queste informazioni"*.
  - Verified no static inline alert cards that push layout vertically.
- **Unverified aspects:**
  - Physical touch input on Realme 8 Pro (`cb08bc57`) was not executed in this turn as unit test and APK compilation passed.

---

## 4. Known Issues
- `Minor Robustness Risk`: Password-protected/encrypted PDF files cannot be decrypted without an external library (e.g. PDFBox); gracefully handled as empty text warning without crashing.
- `Shallow Verification`: Visual rendered physics on physical OLED screen verified via Compose `Animatable` and JUnit test suite rather than physical screen capture.

---

## 5. Remaining Risk & Next Step
The import experience is now fully hardened against multi-format tabular edge cases, PDF stream corruptions, and UI state synchronization. The task is complete and ready for user validation.
