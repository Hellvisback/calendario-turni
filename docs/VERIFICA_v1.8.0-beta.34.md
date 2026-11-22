# TurniSync 1.8.0-beta.34

Versione Android 79, package personale `com.turni.calendario` con firma originale. Le prove sul dispositivo sono state eseguite sul Realme 8 Pro (`cb08bc57`) nel package QA separato `com.turni.calendario.setupqa`.

## Novità e correzioni grafiche/funzionali

- **Guida interattiva passo-passo nella schermata iniziale di modifica (`CalendarPractice.kt`, `OnboardingPracticeState.kt`, `NativeCalendarPanels.kt`, `OnboardingPracticeTest.kt`):**
  - Ogni lezione della schermata `Prova le funzioni di modifica` (`3 / 4` del primo avvio) è ora suddivisa in sotto-passi guidati mano per la mano tramite `evaluatePracticeCoach` (`InteractiveCoachBanner` con indicatori di sotto-passo, messaggio dinamico e banner verde di conferma quando l'utente compie l'azione corretta).
  - **Evidenziazione pulsante sulla barra strumenti e sul calendario:** il controllo esatto da premere nella `NativeEditToolbar` (tessera turno *Pomeriggio*, modalità *Più turni*, campanella *Reperibilità*, scheda *Rotazione*, *Gomma* o *Annulla*) o la cella giorno bersaglio sul calendario (`PracticeGridTargetOverlay`) lampeggiano in modo fluido guidando l'occhio dell'utente.
  - **Indicatore animato di pressione prolungata (`TIENI PREMUTO`) e aiuto su inattività (`TOCCA QUI`):** quando il passo richiede un long-press per aprire un intervallo, sulla cella del giorno lampeggiano anelli concentrici espansivi e il badge `TIENI PREMUTO` (con messaggio correttivo immediato se l'utente effettua un tocco rapido invece di tenere premuto); se l'utente esita per più di `2,6 s`, il suggerimento visivo si intensifica indicando esattamente dove toccare.
  - **Avanzamento automatico e blocco finché il passo non è completato:** la guida resta ferma sul sotto-passo corrente finché l'utente non esegue l'azione richiesta; al completamento della lezione mostra un feedback positivo e passa automaticamente alla lezione successiva dopo `1,35 s`.

## Verifiche eseguite e limiti

- `assembleBeta` (R8 e `lintVitalBeta`) e `testDebugUnitTest` superati con successo: **61 suite, 522 test unitari** (incluso il nuovo test `interactiveCoachGuidesStepByStepAcrossAllLessons` in `OnboardingPracticeTest`), 0 fallimenti, 0 errori, 0 saltati.
- Verificato dal vivo su Realme 8 Pro (`cb08bc57`, `com.turni.calendario.setupqa`) nella schermata `3 / 4 · Prova le funzioni di modifica` del primo avvio in tema scuro e chiaro: lampeggio sulla tessera *Pomeriggio* e poi sul giorno da modificare, anelli animati e badge `TIENI PREMUTO` sul passo intervallo, lampeggio di *Più turni*, *Reperibilità*, *Rotazione*, *Gomma* e *Annulla* con conferma e passaggio automatico alla lezione successiva, con **0 crash** in `logcat -s AndroidRuntime:E`.
- La distribuzione `beta.34` conserva la firma personale originale (`cf099674...`), quindi aggiorna direttamente `com.turni.calendario` esistente senza perdere dati locali.
