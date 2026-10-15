# Review & Adversarial Challenge Report — Reviewer 1 (Milestone 3 Iteration 3 Gate)

**Working Directory**: `c:\Users\marco\Antigravity Progetti\Calediario Turni\.agents\teamwork\reviewer_m3_iter3_1`  
**Target Milestone**: Milestone 3 Iteration 3 Gate (Shift Extraction & Domain Mapping)  
**Verdict**: **REQUEST_CHANGES**  
**Integrity Assessment**: **NO INTEGRITY VIOLATION** (L'implementazione algoritmica è genuina e non contiene dummy, facade o scorciatoie fittizie).

---

## 1. Observation

1. **Thread-Safety & Snapshot Implementation**:
   - In `android-app/app/src/main/java/com/turni/calendario/data/GeminiShiftMapper.kt`:
     - Line 162: `fun registerExtractedData(...) = synchronized(repository) {`
     - Line 168: `val defsSnapshot = repository.shiftDefinitions.toList()`
     - Line 196: `val allDefs = (registeredDefs + repository.shiftDefinitions.toList()).distinctBy { it.id }`
     - Line 344: `val repoDefs = ShiftRepository.shiftDefinitions.toList()`
   - In `android-app/app/src/main/java/com/turni/calendario/repository/ShiftRepository.kt`:
     - Line 482: `fun saveShiftDefinition(def: ShiftDefinition) = synchronized(this) {`
     - Line 491: `fun deleteShiftDefinition(id: String) = synchronized(this) {`
     - Line 495: `fun getShiftDefinition(id: String): ShiftDefinition? = synchronized(this) {`
     - Line 786: `fun saveRotation(updated: ShiftRotation) = synchronized(this) {`
     - Line 796: `fun deleteRotation(id: String) = synchronized(this) {`

2. **Empirical Verification — Assemble Debug Build**:
   - Comando eseguito prima dell'aggiunta del file UI non compilante:
     `$env:JAVA_HOME = "C:\Program Files\Android\Android Studio\jre"; .\gradlew.bat assembleDebug`
     Esito: `BUILD SUCCESSFUL in 44s` (Task `:app:assembleDebug UP-TO-DATE`).

3. **Empirical Verification — Unit Test Execution**:
   - Comando eseguito:
     `$env:JAVA_HOME = "C:\Program Files\Android\Android Studio\jre"; .\gradlew.bat test`
     Esito: `BUILD FAILED` con fallimento in `testDebugUnitTest`:
     ```
     com.turni.calendario.GeminiUserMatchingStressTest > testApostrophes_AsciiVsTypographicalCurly FAILED
         java.lang.AssertionError: expected:<90> but was:<100>
         at com.turni.calendario.GeminiUserMatchingStressTest.testApostrophes_AsciiVsTypographicalCurly(GeminiUserMatchingStressTest.kt:99)

     com.turni.calendario.GeminiShiftMapperTest > testNormalizeName_AccentsApostrophesPunctuation FAILED
         org.junit.ComparisonFailure at GeminiShiftMapperTest.kt:433

     223 tests completed, 2 failed
     ```

4. **Detailed Failure Analysis in `calculateNameMatchScore`**:
   - In `android-app/app/src/test/java/com/turni/calendario/GeminiUserMatchingStressTest.kt:96-100`:
     ```kotlin
     val scoreSpaceAfter = GeminiShiftMapper.calculateNameMatchScore("Marco D' Angelo", "Marco D'Angelo")
     val matchSpaceAfter = GeminiShiftMapper.matchUserSquad(data, "Marco D' Angelo")
     assertEquals(90, scoreSpaceAfter)
     ```
   - In `android-app/app/src/main/java/com/turni/calendario/data/GeminiShiftMapper.kt:374-393`:
     ```kotlin
     val normQ = normalizeName(q)
     val normC = normalizeName(c)

     if (normQ.isBlank() || normC.isBlank()) return 0

     if (normQ == normC) return 100 // <-- RESTITUISCE 100 PRIMA DEL CONTROLLO DI RIGA 389!

     val qTokens = normQ.split(Regex("\\s+")).filter { it.isNotBlank() }
     val cTokens = normC.split(Regex("\\s+")).filter { it.isNotBlank() }

     if (qTokens.toSet() == cTokens.toSet()) {
         if (q.contains(Regex("[’‘`'′]\\s+")) || c.contains(Regex("[’‘`'′]\\s+"))) {
             return 90
         }
         return 95
     }
     ```
     Dato che `normalizeName("Marco D' Angelo")` restituisce `"marco d angelo"` e `normalizeName("Marco D'Angelo")` restituisce `"marco d angelo"`, la condizione `normQ == normC` è vera e restituisce `100` a riga 379, rendendo irraggiungibile la regola specifica per spazio post-apostrofo a riga 389.

5. **Compilation Breakage in Working Tree**:
   - Durante l'esecuzione successiva della suite test, il file introdotto nel workspace da un worker parallelo ha bloccato la compilazione Kotlin:
     `android-app/app/src/main/java/com/turni/calendario/ui/screens/GeminiShiftImportScreen.kt`
     ```
     e: GeminiShiftImportScreen.kt: (346, 38): Unresolved reference: OutlinedTextFieldDefaults
     e: GeminiShiftImportScreen.kt: (383, 34): Unresolved reference: OutlinedTextFieldDefaults
     ```
     (In Jetpack Compose 1.2 / Material 1 si deve usare `TextFieldDefaults.outlinedTextFieldColors`, non `OutlinedTextFieldDefaults` di Material 3).

---

## 2. Logic Chain

1. **Premessa di Integrità e Thread-Safety**:
   - Dall'osservazione 1, `GeminiShiftMapper.kt` e `ShiftRepository.kt` contengono protezioni di sincronizzazione reali ed esaustive (`synchronized(repository)` in `registerExtractedData`, snapshot difensivi `.toList()`, e metodi CRUD su `ShiftRepository` protetti da `synchronized(this)`).
   - Non sono presenti mock o facciate ingannevoli.

2. **Premessa di Qualità e Criteri di Accettazione**:
   - Le regole operative del progetto e l'Acceptance Criteria ("Nuovi test unitari scritti per validare la decodifica del JSON multimodale e l'estrazione delle squadre (.\gradlew.bat test verde al 100%)") impongono che la suite test sia totalmente verde (`100% test pass`) e che il build Gradle sia `BUILD SUCCESSFUL`.

3. **Valutazione delle Rotture Rilevate**:
   - Dall'osservazione 3 e 4, `GeminiUserMatchingStressTest.testApostrophes_AsciiVsTypographicalCurly` fallisce con `AssertionError: expected:<90> but was:<100>`.
   - Dall'osservazione 5, la compilazione del progetto è interrotta da `GeminiShiftImportScreen.kt` per riferimenti mancanti a `OutlinedTextFieldDefaults`.
   - In conformità al vincolo di ruolo di Reviewer ("Review-only — do NOT modify implementation code. Report any failures as findings — do NOT fix them yourself"), il Reviewer non deve applicare modifiche al codice sorgente ma segnalare con precisione la causa radice e richiedere i fix al Worker.

4. **Conclusione Logica**:
   - Non essendo soddisfatto il requisito empirico del 100% di test verdi e build integro, il verdetto è necessariamente `REQUEST_CHANGES`.

---

## 3. Caveats

- I test di stress di concorrenza multi-thread (`GeminiShiftMapperStressTest.testConcurrency_ConcurrentRegistrationsAndReadsDoNotCrashOrCorrupt`) hanno dimostrato che il deadlock e la `ConcurrentModificationException` sono completamente risolti dalla combinazione `synchronized(repository)` + `.toList()`.
- La rottura di compilazione in `GeminiShiftImportScreen.kt` proviene dalle modifiche contemporanee di Milestone 4, ma contamina il classpath condiviso di `:app:compileDebugKotlin`, impedendo a `:app:testDebugUnitTest` di completare il ciclo pulito.

---

## 4. Conclusion & Findings

### Verdict: **REQUEST_CHANGES**

### Findings da Risolvere:

1. **[Critical] Fix Match Score per Spazio Post-Apostrofo**:
   - **Dove**: `GeminiShiftMapper.kt:379-393`
   - **Problema**: `normQ == normC` restituisce `100` scavalcando il controllo a riga 389. Se la stringa originale presenta uno spazio anomalo post-apostrofo (`q.contains(Regex("[’‘`'′]\\s+"))`), il match score deve essere `90` come atteso da `testApostrophes_AsciiVsTypographicalCurly`.
   - **Soluzione raccomandata**: Spostare la verifica di spaziatura anomala post-apostrofo prima del ritorno incondizionato di `100` in `normQ == normC`, oppure verificare se `normalizeName` debba mantenere o rimodulare tale penalità.

2. **[Critical] Ripristino Compilazione Kotlin in `GeminiShiftImportScreen.kt`**:
   - **Dove**: `GeminiShiftImportScreen.kt:346, 383`
   - **Problema**: `OutlinedTextFieldDefaults` non esiste in Compose 1.2.
   - **Soluzione raccomandata**: Sostituire con `TextFieldDefaults.outlinedTextFieldColors(...)`.

3. **[Major] Allineamento Test `testNormalizeName_AccentsApostrophesPunctuation`**:
   - **Dove**: `GeminiShiftMapperTest.kt:433`
   - **Verificare**: Assicurarsi che la normalizzazione di `Marco Graziani (Capoturno)` rimuova le parentesi e produca uniformemente i token attesi.

---

## 5. Verification Method

Per verificare in modo indipendente la risoluzione dei problemi segnalati:
1. Eseguire la compilazione di debug:
   ```powershell
   $env:JAVA_HOME = "C:\Program Files\Android\Android Studio\jre"; .\gradlew.bat --no-daemon assembleDebug
   ```
   *Condizione di superamento*: `BUILD SUCCESSFUL` con 0 errori in `compileDebugKotlin`.

2. Eseguire l'intera suite di unit test:
   ```powershell
   $env:JAVA_HOME = "C:\Program Files\Android\Android Studio\jre"; .\gradlew.bat --no-daemon testDebugUnitTest
   ```
   *Condizione di superamento*: 223+ test completati, `0 failed`, `0 errors`.
