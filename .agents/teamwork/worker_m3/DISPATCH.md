# DISPATCH — Worker Milestone 3: Shift Extraction & Domain Mapping

## Identity & Role
- Archetype: teamwork_preview_worker
- Role: Domain Mapping & Shift Repository Developer
- Working Directory: c:\Users\marco\Antigravity Progetti\Calediario Turni\.agents\teamwork\worker_m3

## Objective
Implement Milestone 3 (Shift Extraction & Domain Mapping):
Create `android-app/app/src/main/java/com/turni/calendario/data/GeminiShiftMapper.kt`:
1. **Mapping to ShiftDefinition**:
   - Function `fun mapToShiftDefinitions(data: GeminiShiftData): List<ShiftDefinition>`
   - Map each `GeminiShiftDefinition` to a real `ShiftDefinition` (`ShiftModels.kt`):
     - Unique UUID id.
     - Code / ShortCode, Name, startTime, endTime.
     - Distinctive `colorHex` (use color palette if not provided: Cyan `#38BDF8`, Amber `#F59E0B`, Emerald `#10B981`, Rose `#EF4444`, Indigo `#6366F1`, Purple `#8B5CF6`).
     - Detect category (`ShiftCategory.MORNING`, `AFTERNOON`, `NIGHT`, `REST`, `STANDARD`) from hours / short code.
     - Handle reperibilità (tagging or category).
2. **Mapping to ShiftRotation**:
   - Function `fun mapToRotations(data: GeminiShiftData, shiftDefs: List<ShiftDefinition>): List<ShiftRotation>`
   - Map each squad into a cyclic `ShiftRotation`:
     - Rotation length (days count).
     - Sequence of `shiftDefId` matching the codes in `rotationDays`.
3. **Auto User Squad Matching**:
   - Function `fun matchUserSquad(data: GeminiShiftData, userName: String): String?` (returns squadName of the matched squad, or null).
   - Case-insensitive, whitespace-trimmed, and normalized name order matching (e.g. matching "Graziani Marco" with "Marco Graziani", or "Graziani" / "Marco").
4. **Integration with ShiftRepository**:
   - Function `fun registerExtractedData(repository: ShiftRepository, data: GeminiShiftData, targetUserName: String? = null): ExtractedSetupResult`
   - Atomically register/update shifts in `repository.shiftDefinitions` without duplicate IDs.
   - Register rotations in `repository.rotations`.
   - If user squad matched, pre-select default team (`repository.defaultRotationId`).

## Verification Required
Worker MUST verify:
- Create unit tests in `android-app/app/src/test/java/com/turni/calendario/GeminiShiftMapperTest.kt` verifying shift creation, color assignment, rotation mapping, user matching (exact, inverted, partial), and repository insertion.
- Run `.\gradlew.bat test` (with `$env:JAVA_HOME = "C:\Program Files\Android\Android Studio\jre"`).
- Run `.\gradlew.bat assembleDebug`.
Both must be 100% GREEN.

## Files Owned Exclusively
- `android-app/app/src/main/java/com/turni/calendario/data/GeminiShiftMapper.kt`
- `android-app/app/src/test/java/com/turni/calendario/GeminiShiftMapperTest.kt`


## 2026-10-14T19:45:00Z
Sei Worker M3 (Domain Mapping & Shift Repository Developer).
Il tuo working directory dedicato è:
c:\Users\marco\Antigravity Progetti\Calediario Turni\.agents\teamwork\worker_m3

Devi leggere OBBLIGATORIAMENTE il file dei requisiti originali prima di qualsiasi azione:
c:\Users\marco\Antigravity Progetti\Calediario Turni\.agents\teamwork\ORIGINAL_REQUEST.md

Leggi anche il piano globale e i modelli dati esistenti:
- c:\Users\marco\Antigravity Progetti\Calediario Turni\PROJECT.md
- c:\Users\marco\Antigravity Progetti\Calediario Turni\.agents\teamwork\worker_m3\DISPATCH.md
- android-app/app/src/main/java/com/turni/calendario/data/ShiftModels.kt
- android-app/app/src/main/java/com/turni/calendario/data/ShiftRepository.kt
- android-app/app/src/main/java/com/turni/calendario/data/GeminiShiftModels.kt

MANDATORY INTEGRITY WARNING:
DO NOT CHEAT. All implementations must be genuine. DO NOT hardcode test results, create dummy/facade implementations, or circumvent the intended task. A teamwork_preview_auditor will independently verify your work. Integrity violations WILL be detected and your work WILL be rejected.

File di cui hai la proprietà esclusiva di scrittura:
- android-app/app/src/main/java/com/turni/calendario/data/GeminiShiftMapper.kt
- android-app/app/src/test/java/com/turni/calendario/GeminiShiftMapperTest.kt

Implementa GeminiShiftMapper.kt:
1. mapToShiftDefinitions: mappa GeminiShiftDefinition a ShiftDefinition reale (UUID univoco, shortCode, nome, orari, colorHex distintivo, categoria ShiftCategory e reperibilità).
2. mapToRotations: mappa le squadre estratte in ShiftRotation cicliche (lunghezza giorni corretta, sequenza ID turni).
3. matchUserSquad: matching intelligente e case-insensitive del nome utente ("Marco Graziani" vs "Graziani Marco") per identificare la squadra di appartenenza.
4. registerExtractedData: registrazione in ShiftRepository (evitando duplicazioni, aggiornando le rotazioni e impostando defaultRotationId se trovato il match).

VERIFICA OBBLIGATORIA:
Scrivi GeminiShiftMapperTest.kt ed esegui:
powershell: $env:JAVA_HOME = "C:\Program Files\Android\Android Studio\jre"; .\gradlew.bat test
powershell: $env:JAVA_HOME = "C:\Program Files\Android\Android Studio\jre"; .\gradlew.bat assembleDebug
Entrambi devono terminare con BUILD SUCCESSFUL.

Scrivi handoff.md in c:\Users\marco\Antigravity Progetti\Calediario Turni\.agents\teamwork\worker_m3\handoff.md e invia un messaggio di completamento con send_message.
