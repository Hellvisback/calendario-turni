# TurniSync 1.8.0-beta.38

Versione Android 83, package personale `com.turni.calendario` con firma originale (`cf09967400ac4931dbfa169b9bc23c6a8314ef0256285c3399f4912bee735772`, SHA-256 `70f733ad69380e9d08ccafc3f4ded84a344ce0bb555a81259c1f114c340bb570`).

## Novità e correzioni grafiche/funzionali

- **Intervallo a pressione prolungata per Gomma e Reperibilità (`ShiftRepository.kt`, `CalendarScreen.kt`, `NativeMonthCanvas.kt`, `AlarmAndRangeEditingTest.kt`):** aggiunto `setDayOnCallRange` in `ShiftRepository` con singolo `undo()` per l'intero intervallo e collegato il long-press sulla barra *Reperibilità* (`bellAnchor`) oltre alla *Gomma* su `CalendarScreen` e `NativeMonthCanvas` (con badge compatto ambra `bellAnchorCue` e freccette laterali senza sovrascrivere i turni in anteprima).
- **Colore netto di giorno e campanella per la Reperibilità (`NativeMonthCanvas.kt`):** corretta la priorità di `dateInk` in modo che `onCall` colora chiaramente di ambra calda sia il numero del giorno sia l'icona campanella anche quando la cella ha un overlay colorato in modalità modifica (con micro-pill/sfondo tonale ambra `palette.onCall.fill` quando il contrasto diretto sulla cella è inferiore alla soglia).
- **Tutorial interattivo più snello, fluido e completo fino al popup finale (`FirstRunScreen.kt`, `CalendarPractice.kt`, `OnboardingPracticeState.kt`, `OnboardingPracticeTest.kt`):**
  - Istruzioni brevi e dirette su tutte e 6 le lezioni, con transizioni velocizzate (`800 ms`) e reset automatico di `editSession` / scelta di un turno distinto ad ogni cambio lezione per rendere immediatamente visibile la modifica del turno sul calendario.
  - Mostrato esplicitamente il long-press di gruppo anche su **Gomma** (`TIENI PREMUTO` per eliminare più turni insieme) e **Reperibilità** ad intervallo (`applyBellPracticeRange`), oltre a `Più turni` con impilamento e pallini `• •`.
  - Aggiunto dopo l'ultimo passo (*Annulla*) il popup animato Material 3 Expressive **`TutorialCompletionDialog`** (`"Hai finito il tutorial!"` con badge verde animato, pulsante `"A posto · Continua"` verso il passo finale e `"Riprova"`).

## Verifiche eseguite e limiti

- `assembleBeta` (R8 e `lintVitalBeta`) e `testDebugUnitTest` superati con successo: **63 suite, 543 test unitari**, 0 fallimenti, 0 errori, 0 saltati (inclusi `onCallRangeLongPressAppliesAndUndoesInOneStep` in `AlarmAndRangeEditingTest` e `applyBellPracticeRangeTogglesIntervalsCleanly` in `OnboardingPracticeTest`).
- Verificato `android-app/app/build/outputs/apk/beta/app-beta.apk` tramite `aapt dump badging` e `apksigner verify --print-certs`: package `com.turni.calendario`, `versionCode='83'`, `versionName='1.8.0-beta.38'`, certificato `cf09967400ac4931dbfa169b9bc23c6a8314ef0256285c3399f4912bee735772`.
- **Dispositivo fisico Realme 8 Pro (`cb08bc57`):** l'installazione QA e la prova dal vivo sul dispositivo non sono state eseguite in questa sessione perché il telefono risultava `offline` su ADB.
- La distribuzione `beta.38` conserva la firma personale originale (`cf099674...`), quindi aggiorna direttamente `com.turni.calendario` esistente senza perdere dati locali.
