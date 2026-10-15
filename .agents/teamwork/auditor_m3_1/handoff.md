# Forensic Audit Report — Milestone 3: Shift Extraction & Domain Mapping

**Work Product**: `android-app/app/src/main/java/com/turni/calendario/data/GeminiShiftMapper.kt` e `android-app/app/src/test/java/com/turni/calendario/GeminiShiftMapperTest.kt`  
**Profile**: General Project (Development Mode, derivato da `ORIGINAL_REQUEST.md`)  
**Verdict**: **INTEGRITY VIOLATION** (Work Product Rejected on Behavioral Verification)

---

### Phase Results
- **Check 1 (Hardcoded output detection)**: **PASS** — Nessun risultato o output di test hardcodato presente in `GeminiShiftMapper.kt`. Generazione UUID autentica (`UUID.randomUUID()`), parsing colori hex dinamico, deduzione categorie e icone basata su euristiche effettive.
- **Check 2 (Facade detection)**: **PASS** — Zero facade implementations. Tutte le funzioni (`mapToShiftDefinitions`, `mapToRotations`, `matchUserSquad`, `registerExtractedData`) implementano logica algoritmica reale e mutano/interrogano lo stato effettivo del dominio.
- **Check 3 (Pre-populated artifact detection)**: **PASS** — Nessun file di log o output fittizio pre-popolato nello spazio di lavoro per falsificare l'esito dei test.
- **Check 4 (Build and run test suite)**: **FAIL** — L'esecuzione empirica indipendente del comando di suite completa (`.\gradlew.bat test`) fallisce con exit code 1 (`BUILD FAILED in 5m 1s`, 217 test completati, 1 fallito). Il test fallito è `com.turni.calendario.GeminiShiftMapperStressTest.testConcurrency_ConcurrentRegistrationsAndReadsDoNotCrashOrCorrupt`, causato da un'eccezione non gestita `java.util.ConcurrentModificationException` all'interno di `GeminiShiftMapper.kt:185, 160` su collezioni Compose di `ShiftRepository`.
- **Check 5 (Output verification & Tautological assertions)**: **PASS (funzionale) / FAIL (concorrente)** — Nessuna asserzione tautologica (`assert(true)`) nei test unitari `GeminiShiftMapperTest.kt` (14/14 test passati). Tuttavia, sotto stress multi-thread concorrente l'applicazione fallisce sollevando eccezioni non gestite.
- **Check 6 (Dependency audit)**: **PASS** — Nessuna delega indebita a librerie terze non autorizzate; utilizzo corretto dei modelli Android e Kotlin standard.

---

## 1. Observation

1. **Ispezione Sorgente `GeminiShiftMapper.kt`**:
   - `mapToShiftDefinitions`: Converte `GeminiShiftDefinition` in `ShiftDefinition`, genera UUID reali via `UUID.randomUUID().toString()`, effettua parsing esadecimale con fallback modulo sulla palette `DEFAULT_PALETTE`, rileva categorie (`ShiftCategory.LAVORO`, `RIPOSO`, `ASSENZA`) e assegna icone vettoriali appropriate.
   - `mapToRotations`: Mappa le squadre in `ShiftRotation`, calcola la lunghezza del ciclo e converte i codici giornalieri in ID turno tramite `resolveShiftDefId`.
   - `matchUserSquad`: Algoritmo di scoring token-based (case-insensitive, delimitatori, set equality, sottostringhe) con soglia di confidenza 50.
   - `registerExtractedData`: Registra definizioni e rotazioni in `ShiftRepository`, aggiornando in-place le definizioni esistenti (per evitare duplicati per ID, shortCode o nome) e impostando la rotazione predefinita per l'utente identificato.
   - **Nota di Concorrenza**: Il metodo `registerExtractedData` accede e muta le proprietà `ShiftRepository.shiftDefinitions` e `ShiftRepository.rotations` (istanze di `androidx.compose.runtime.snapshots.SnapshotStateList`) in modo non sincronizzato.

2. **Ispezione Suite Test `GeminiShiftMapperTest.kt`**:
   - 14 metodi di test che coprono proprietà base, unicità UUID, palette di fallback, risoluzione delle sequenze di rotazione, fallback standard per codici non mappati, matching utenti esatto, invertito, con spaziature anomale, token singoli, estrazione da lista impiegati, preferenza tra token ambigui, e integrazione idempotente con `ShiftRepository`.
   - **Verifica Asserzioni**: Zero asserzioni tautologiche (`assertTrue(true)` o `assert(true)` assenti). Ogni asserzione valida valori effettivi di ritorno, cardinalità di liste, codici colore e mutazioni di stato in `ShiftRepository`.

3. **Esecuzione Empirica dei Comandi di Build e Test**:
   - **Comando 1**: Debug Build
     ```powershell
     $env:JAVA_HOME = "C:\Program Files\Android\Android Studio\jre"; .\gradlew.bat assembleDebug
     ```
     **Risultato**: `BUILD SUCCESSFUL in 20s`, 32 actionable tasks.
   - **Comando 2**: Test Unitari Milestone 3 Isolati
     ```powershell
     $env:JAVA_HOME = "C:\Program Files\Android\Android Studio\jre"; .\gradlew.bat testDebugUnitTest --tests com.turni.calendario.GeminiShiftMapperTest
     ```
     **Risultato**: `BUILD SUCCESSFUL in 19s`, 14 test eseguiti, 14 passati, 0 falliti.
   - **Comando 3**: Suite di Test Completa del Progetto (Requisito di Dispatch e Check 4)
     ```powershell
     $env:JAVA_HOME = "C:\Program Files\Android\Android Studio\jre"; .\gradlew.bat testDebugUnitTest
     ```
     **Risultato**: `BUILD FAILED in 5m 1s` (Exit Code 1).
     ```text
     com.turni.calendario.GeminiShiftMapperStressTest > testConcurrency_ConcurrentRegistrationsAndReadsDoNotCrashOrCorrupt FAILED
         java.lang.AssertionError at GeminiShiftMapperStressTest.kt:474
     217 tests completed, 1 failed
     ```
   - **Verbatim Error Log dal Test XML**:
     ```text
     java.lang.AssertionError: No exceptions or race conditions allowed during concurrent operations expected:<0> but was:<5>
     	at org.junit.Assert.assertEquals(Assert.java:120)
     	at com.turni.calendario.GeminiShiftMapperStressTest.testConcurrency_ConcurrentRegistrationsAndReadsDoNotCrashOrCorrupt(GeminiShiftMapperStressTest.kt:474)
     ...
     java.util.ConcurrentModificationException
     	at androidx.compose.runtime.snapshots.StateListIterator.validateModification(SnapshotStateList.kt:278)
     	at androidx.compose.runtime.snapshots.StateListIterator.next(SnapshotStateList.kt:257)
     	at com.turni.calendario.data.GeminiShiftMapper.registerExtractedData(GeminiShiftMapper.kt:185)
     ```

---

## 2. Logic Chain

1. **Step 1 (Integrità del Codice & Autenticità)**:
   - L'ispezione statica di `GeminiShiftMapper.kt` e `GeminiShiftMapperTest.kt` dimostra l'assenza totale di codice fittizio, mock pre-cablati per far passare i test a tavolino, o bypass intenzionali.
   - La suite `GeminiShiftMapperTest.kt` verifica autenticamente le proprietà e passa al 100% (14/14).

2. **Step 2 (Verifica Comportamentale Check 4 — Esecuzione Suite di Test)**:
   - Il protocollo di integrità forense stabilisce inderogabilmente:
     *"Check 4: Build and run: Build the project from source and run its test suite. The build must succeed and tests must execute — a project that doesn't build or whose tests don't run is automatically flagged."*
     *"Block on failure: If ANY check fails, the verdict is INTEGRITY VIOLATION and the work product must be rejected."*
   - L'esecuzione empirica della suite di test completa (`.\gradlew.bat testDebugUnitTest`) ha prodotto `BUILD FAILED` con 1 fallimento su 217 test a causa di un crash per `ConcurrentModificationException` in `GeminiShiftMapper.registerExtractedData`.

3. **Step 3 (Valutazione della Claim di Completamento)**:
   - Il report di handoff di `worker_m3` attestava:
     *"`.\gradlew.bat test`: Exited with code 0 (BUILD SUCCESSFUL in 1m 6s, 43 actionable tasks)."*
   - All'atto della verifica forense indipendente, la suite completa fallisce sul codice prodotto per la Milestone 3.
   - Sebbene non vi sia dolo o falsificazione deliberata, il mancato superamento della suite di test rende il work product non conforme ai criteri di accettazione e impone la classificazione formale di **INTEGRITY VIOLATION** ai sensi del regolamento dell'Auditor.

---

## 3. Caveats

- **Nessuna frode accertata**: Si sottolinea esplicitamente che il verdetto di INTEGRITY VIOLATION è determinato dal fallimento del Check 4 (Build and run test suite) e non da condotte fraudolente (facade o hardcoded mock). L'implementazione algoritmica è genuina e di alta qualità.
- **Risoluzione chirurgica**: Il fallimento è circoscritto alla mancanza di sincronizzazione multi-thread su `registerExtractedData` quando interagisce con le strutture dati Compose `SnapshotStateList` di `ShiftRepository`.

---

## 4. Conclusion

**Verdetto Finale**: **INTEGRITY VIOLATION** (Work Product Rejected)

Il work product di Milestone 3 non può essere approvato allo stato attuale per fallimento del Check 4 (esecuzione con esito positivo della test suite).

### Azioni Correttive Obbligatorie per Worker M3:
1. **Thread-Safety in `registerExtractedData`**:
   Proteggere l'accesso e la mutazione di `ShiftRepository` sincronizzando il blocco o eseguendolo con lock protetto:
   ```kotlin
   fun registerExtractedData(
       repository: ShiftRepository = ShiftRepository,
       data: GeminiShiftData,
       targetUserName: String? = null
   ): ExtractedSetupResult = synchronized(repository) {
       // logica di registrazione esistente
   }
   ```
2. **Raffinamento Heuristic Matching (Raccomandazione Challenger 1)**:
   - Evitare falsi positivi per omonimia parziale quando l'utente fornisce nome e cognome completi ma un estraneo condivide un solo nome comune (es. inibire match singolo se la query contiene più token e nessuno fa match sul cognome).
   - Aggiungere rimozione accenti/diacritici (es. `Normalizer.normalize(q, Normalizer.Form.NFD)`).
3. **Riesecuzione Empirica**:
   Verificare che `.\gradlew.bat test` e `.\gradlew.bat assembleDebug` producano entrambi `BUILD SUCCESSFUL` con 217+ test verdi al 100%.

---

## 5. Verification Method

Per riprodurre in modo indipendente l'esito dell'audit:

1. **Verifica Test Unitari Milestone 3 (PASS)**:
   ```powershell
   $env:JAVA_HOME = "C:\Program Files\Android\Android Studio\jre"
   cd "c:\Users\marco\Antigravity Progetti\Calediario Turni\android-app"
   .\gradlew.bat testDebugUnitTest --tests com.turni.calendario.GeminiShiftMapperTest
   ```
   *Esito*: `BUILD SUCCESSFUL` (14/14 test passati).

2. **Verifica Test Suite Completa (FAIL — Trigger dell'Integrity Violation)**:
   ```powershell
   $env:JAVA_HOME = "C:\Program Files\Android\Android Studio\jre"
   cd "c:\Users\marco\Antigravity Progetti\Calediario Turni\android-app"
   .\gradlew.bat testDebugUnitTest
   ```
   *Esito*: `BUILD FAILED` con errore su `GeminiShiftMapperStressTest.testConcurrency_ConcurrentRegistrationsAndReadsDoNotCrashOrCorrupt`.
