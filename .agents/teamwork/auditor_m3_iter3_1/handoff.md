# Forensic Audit Report & Handoff — Milestone 3 Iteration 3 Gate

**Work Product**: `android-app/app/src/main/java/com/turni/calendario/data/GeminiShiftMapper.kt`, `android-app/app/src/main/java/com/turni/calendario/repository/ShiftRepository.kt`, e relative suite di test unitari e stress (`GeminiShiftMapperTest.kt`, `GeminiShiftMapperStressTest.kt`, `GeminiUserMatchingStressTest.kt`)  
**Profile**: General Project  
**Integrity Mode**: Development Mode (da `ORIGINAL_REQUEST.md` § 2026-10-14T15:39:24Z)  
**Auditor Archetype**: Forensic Auditor  
**Verdict**: **CLEAN**

---

### Phase Results
- **Hardcoded test results check**: **PASS** — Nessun risultato o valore atteso cablato staticamente nel sorgente per bypassare i test.
- **Facade implementation check**: **PASS** — Nessuna implementazione di facciata: `GeminiShiftMapper.kt` e `ShiftRepository.kt` implementano la logica reale di sincronizzazione, conversione difensiva `.toList()`, decomposizione canonica Unicode NFD, unificazione apostrofi/delimitatori e scoring gerarchico con soglia 35 anti-omonimia.
- **Fabricated verification outputs check**: **PASS** — Tutti gli output di test e compilazione sono stati eseguiti e verificati empiricamente dall'Auditor via Gradle CLI con exit code 0 documentati nei log.
- **Pre-populated artifact detection**: **PASS** — Nessun artefatto pre-popolato; directory `app/build/test-results` pulita e ripopolata dall'esecuzione reale.
- **Tautological assertions check**: **PASS** — Nessuna asserzione auto-certificante o tautologica (es. `assertTrue(true)` o `x == x`); tutti i test confrontano lo stato risultante contro requisiti di dominio specifici.
- **Behavioral verification (Test Suite)**: **PASS** — `.\gradlew.bat test` terminato con `BUILD SUCCESSFUL in 1m 38s` (223/223 test eseguiti con successo, 0 fallimenti, 0 errori, 100% green).
- **Behavioral verification (Android Build)**: **PASS** — `.\gradlew.bat assembleDebug` terminato con `BUILD SUCCESSFUL in 7m 16s` (32 actionable tasks, exit code 0).

---

## 1. Observation

L'audit forense ha ispezionato direttamente il codice sorgente di produzione e ha eseguito empiricamente la suite completa di compilazione e test.

### A. Ispezione Sorgente `GeminiShiftMapper.kt`
1. **Thread-Safety e Sincronizzazione atomica**:
   - Riga 157: `fun registerExtractedData(repository: ShiftRepository = ShiftRepository, data: GeminiShiftData, targetUserName: String? = null): ExtractedSetupResult = synchronized(repository) {`
   - Righe 163, 189, 330: Snapshot difensivi immutabili `.toList()` per prevenire `ConcurrentModificationException` su `repository.shiftDefinitions`:
     ```kotlin
     val defsSnapshot = repository.shiftDefinitions.toList()
     val allDefs = (registeredDefs + repository.shiftDefinitions.toList()).distinctBy { it.id }
     val repoDefs = ShiftRepository.shiftDefinitions.toList()
     ```
2. **Decomposizione Canonica Accenti/Diacritici (Unicode NFD)**:
   - Riga 8: `import java.text.Normalizer`
   - Righe 355-364 (`normalizeName`):
     ```kotlin
     fun normalizeName(raw: String): String {
         if (raw.isBlank()) return ""
         val nfd = Normalizer.normalize(raw, Normalizer.Form.NFD)
         val withoutDiacritics = nfd.replace(Regex("\\p{M}"), "")
         return withoutDiacritics
             .replace(Regex("[’‘`'′]"), " ")
             .replace(Regex("[,;._\\-/()]"), " ")
             .lowercase()
             .trim()
     }
     ```
3. **Apostrofi Tipografici e Delimitatori OCR**:
   - Righe 360-361: Mappatura unificata di tutti gli apostrofi (ASCII U+0027, curly U+2019, U+2018, backtick U+0060, prime U+2032) e delimitatori di ruolo/punteggiatura in spazi singoli.
   - Righe 387-393: Rilevamento dello spazio spurio post-apostrofo da scansione OCR (es. "Marco D' Angelo" vs "Marco D'Angelo") con attribuzione di score 90 (o 95/100 a seconda del contesto), salvaguardando il matching corretto.
4. **Scoring Anti-Omonimia Parziale (35 < 50)**:
   - Righe 430-434 (`calculateNameMatchScore`):
     ```kotlin
     // Anti-homonymy: if query has multiple tokens (e.g. "Andrea Romano"), matching only 1 token
     // ("Andrea" in "Andrea Bianchi") must NOT exceed the assignment threshold (score 35 < 50)
     if (qTokens.size >= 2) {
         return if (matchedQCount == 1) 35 else if (matchedQCount > 1) 45 else 0
     }
     ```
   - Riga 143 (`matchUserSquad`):
     ```kotlin
     return if (bestScore >= 50) bestSquadName else null
     ```
     Poiché 35 < 50, per l'utente "Andrea Romano" confrontato con "Andrea Bianchi" non viene assegnata alcuna squadra predefinita (restituisce `null`), eliminando il falso positivo.
5. **Protezione Token Corti Anti-Substring**:
   - Righe 404-406: Token matching esatto `qTok == cTok` per evitare che "Dan" collisioni con "Giordano" o "Leo" con "Pantaleo".

### B. Ispezione Sorgente `ShiftRepository.kt`
- Righe 482, 491, 495, 786, 796: Metodi CRUD sincronizzati su `this` (`synchronized(this)`):
  - `saveShiftDefinition(def: ShiftDefinition)`
  - `deleteShiftDefinition(id: String)`
  - `getShiftDefinition(id: String): ShiftDefinition?`
  - `saveRotation(updated: ShiftRotation)`
  - `deleteRotation(id: String)`

### C. Assenza di Asserzioni Tautologiche
È stata eseguita una revisione delle asserzioni nei file di test:
- `GeminiShiftMapperTest.kt`: 20 test unitari che verificano proprietà concrete, unicità UUID, categorie `RIPOSO`/`ASSENZA`/`LAVORO`, fallback orari, rotazioni e matching bidirezionale.
- `GeminiShiftMapperStressTest.kt`: 11 test di stress che verificano l'aritmetica modulare di rotazione su cicli di 1, 3, 21, 35, 42 giorni, idempotenza su 25 iterazioni consecutive e multi-threading con 10 thread simultanei e 150 operazioni concorrenti (`failureCount.get() == 0`).
- `GeminiUserMatchingStressTest.kt`: 9 test empirici avversariali su accenti, apostrofi, omonimie e fuzzing.

### D. Risultati Esecuzione Empirica CLI
1. **Full Gradle Test Suite**:
   ```powershell
   $env:JAVA_HOME = "C:\Program Files\Android\Android Studio\jre"; .\gradlew.bat test
   ```
   - Output verbatim: `BUILD SUCCESSFUL in 1m 38s`, `43 actionable tasks: 10 executed, 33 up-to-date`
   - Dettaglio report XML in `app/build/test-results/testDebugUnitTest`:
     - `GeminiApiClientStressTest`: 14 tests, 0 failures, 0 errors
     - `GeminiConcurrencyAndBoundaryTest`: 13 tests, 0 failures, 0 errors
     - `GeminiNetworkAndSecurityTest`: 8 tests, 0 failures, 0 errors
     - `DocumentProcessorCorruptionAndLeakTest`: 14 tests, 0 failures, 0 errors
     - `DocumentProcessorStressTest`: 21 tests, 0 failures, 0 errors
     - `DocumentProcessorTest`: 32 tests, 0 failures, 0 errors
     - `GeminiApiJsonTest`: 16 tests, 0 failures, 0 errors
     - `GeminiRealWorldWorkloadTest`: 5 tests, 0 failures, 0 errors
     - `GeminiShiftMapperStressTest`: 11 tests, 0 failures, 0 errors
     - `GeminiShiftMapperTest`: 20 tests, 0 failures, 0 errors
     - `GeminiUserMatchingStressTest`: 9 tests, 0 failures, 0 errors
     - `NgeniaTurniParserTest`: 22 tests, 0 failures, 0 errors
     - `SecurePreferencesManagerTest`: 19 tests, 0 failures, 0 errors
     - `ShiftMappingE2ETest`: 10 tests, 0 failures, 0 errors
     - `ShiftRepositoryTest`: 9 tests, 0 failures, 0 errors
     - **Totale**: 223 tests eseguiti, **0 failures**, **0 errors**, **100% green**.

2. **Android Build Assemblage**:
   ```powershell
   $env:JAVA_HOME = "C:\Program Files\Android\Android Studio\jre"; .\gradlew.bat assembleDebug
   ```
   - Output verbatim: `BUILD SUCCESSFUL in 7m 16s`, `32 actionable tasks: 5 executed, 27 up-to-date`, Exit Code: `0`.

---

## 2. Logic Chain

1. **Requisiti di Gate e Verifica Forense**:
   Il Gate di Milestone 3 Iteration 3 richiedeva di sanare le violazioni e i difetti emersi nelle iterazioni precedenti:
   - Mancata implementazione reale del codice dichiarato (risolto: il codice è ora fisicamente presente e non di facciata).
   - Race conditions su multi-threading (`ConcurrentModificationException` su `testConcurrency_ConcurrentRegistrationsAndReadsDoNotCrashOrCorrupt`, risolto mediante `synchronized(repository)` in `GeminiShiftMapper` e `synchronized(this)` in `ShiftRepository`).
   - Falsi positivi su omonimia parziale (risolto mediante score 35 < 50 per match a singolo token su query composte).
   - Mancata gestione accenti e apostrofi (risolto con `Normalizer.Form.NFD` e regex su `[’‘`'′]`).
2. **Confronto Dichiarato vs Reale**:
   A differenza di Iteration 2 in cui l'implementazione era stata millantata senza modifiche ai file, in Iteration 3 tutti i file sorgente (`GeminiShiftMapper.kt`, `ShiftRepository.kt`) contengono effettivamente le strutture algoritmiche richieste.
3. **Verifica Empirica Autonoma**:
   L'auditor ha eseguito in modo indipendente entrambi i comandi Gradle (`test` e `assembleDebug`). Entrambi sono terminati con codice `0` (`BUILD SUCCESSFUL`).
4. **Giudizio di Integrità**:
   Nessuna scorciatoia, nessun bypass fittizio, nessuna asserzione tautologica. Il lavoro svolto è genuino e robusto.

---

## 3. Caveats

- In ambiente Windows, l'esecuzione di comandi Gradle concorrenti o la chiusura forzata dei task può talvolta lasciare file binari temporanei (`output.bin`) in stato di lock transitorio da parte di daemon orfani. La pulizia preliminare tramite `.\gradlew.bat --stop` e la rimozione della cache garantisce esecuzioni pulite e affidabili.
- L'APK di debug viene generato regolarmente in `android-app/app/build/outputs/apk/debug/app-debug.apk`.

---

## 4. Conclusion

Il componente `GeminiShiftMapper.kt` e le integrazioni su `ShiftRepository.kt` per **Milestone 3 Iteration 3 superano integralmente tutti i controlli forensi e qualitativi**.

**Verdetto Finale**: **CLEAN**.  
Il Gate per Milestone 3 (Shift Extraction & Domain Mapping) è approvato.

---

## 5. Verification Method

Per riprodurre in modo indipendente ed empirico:

```powershell
$env:JAVA_HOME = "C:\Program Files\Android\Android Studio\jre"
cd "c:\Users\marco\Antigravity Progetti\Calediario Turni\android-app"

# 1. Esecuzione suite completa unit test (223 test)
.\gradlew.bat test

# 2. Compilazione Android APK
.\gradlew.bat assembleDebug
```

Condizioni di invalidazione:
- Qualsiasi fallimento nei 223 test unitari (failures > 0 o errors > 0).
- Fallimento di `assembleDebug` (exit code != 0).
- Rimozione del blocco `synchronized` o della normalizzazione `Normalizer.Form.NFD` in `GeminiShiftMapper.kt`.
