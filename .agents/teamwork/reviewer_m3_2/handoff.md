# Rapporto di Revisione Indipendente (Reviewer 2 & Critic) — Milestone 3: Shift Extraction & Domain Mapping

**Verdetto Finale**: **REQUEST_CHANGES**

---

## 1. Observation

### Comandi Eseguiti ed Esito Empirico
1. **Esecuzione Test Suite Gradle**:
   - Comando:
     ```powershell
     $env:JAVA_HOME = "C:\Program Files\Android\Android Studio\jre"
     .\gradlew.bat testDebugUnitTest --rerun-tasks
     ```
   - Esito: **`BUILD FAILED in 7m 56s`** (Exit code: 1).
   - Metriche: **217 test eseguiti, 1 fallito** (`com.turni.calendario.GeminiShiftMapperStressTest`).

2. **Dettaglio Fallimento Empirico (Verbatim Error)**:
   - File report: `android-app/app/build/reports/tests/testDebugUnitTest/classes/com.turni.calendario.GeminiShiftMapperStressTest.html`
   - Classe: `com.turni.calendario.GeminiShiftMapperStressTest`
   - Test: `testConcurrency_ConcurrentRegistrationsAndReadsDoNotCrashOrCorrupt`
   - Stack Trace:
     ```text
     java.lang.AssertionError: No exceptions or race conditions allowed during concurrent operations expected:<0> but was:<5>
         at org.junit.Assert.fail(Assert.java:89)
         at org.junit.Assert.failNotEquals(Assert.java:835)
         at org.junit.Assert.assertEquals(Assert.java:120)
         at com.turni.calendario.GeminiShiftMapperStressTest.testConcurrency_ConcurrentRegistrationsAndReadsDoNotCrashOrCorrupt(GeminiShiftMapperStressTest.kt:472)
     ```
   - Standard Error (5 eccezioni concorrenti sollevate durante l'esecuzione multi-thread):
     ```text
     java.util.ConcurrentModificationException
         at androidx.compose.runtime.snapshots.StateListIterator.validateModification(SnapshotStateList.kt:278)
         at androidx.compose.runtime.snapshots.StateListIterator.next(SnapshotStateList.kt:257)
         at com.turni.calendario.data.GeminiShiftMapper.resolveShiftDefId(GeminiShiftMapper.kt:327)
         at com.turni.calendario.data.GeminiShiftMapper.mapToRotations(GeminiShiftMapper.kt:92)
         at com.turni.calendario.data.GeminiShiftMapper.registerExtractedData(GeminiShiftMapper.kt:186)
     java.util.ConcurrentModificationException
         at androidx.compose.runtime.snapshots.StateListIterator.validateModification(SnapshotStateList.kt:278)
         at androidx.compose.runtime.snapshots.StateListIterator.next(SnapshotStateList.kt:257)
         at kotlin.jvm.internal.CollectionToArray.toArray(CollectionToArray.kt:91)
         at androidx.compose.runtime.snapshots.SnapshotStateList.toArray(SnapshotStateList.kt)
         at java.base/java.util.ArrayList.addAll(ArrayList.java:702)
         at kotlin.collections.CollectionsKt___CollectionsKt.plus(_Collections.kt:3271)
         at com.turni.calendario.data.GeminiShiftMapper.registerExtractedData(GeminiShiftMapper.kt:185)
     java.util.ConcurrentModificationException
         at androidx.compose.runtime.snapshots.StateListIterator.validateModification(SnapshotStateList.kt:278)
         at androidx.compose.runtime.snapshots.StateListIterator.next(SnapshotStateList.kt:257)
         at com.turni.calendario.repository.ShiftRepository.saveShiftDefinition(ShiftRepository.kt:1475)
         at com.turni.calendario.data.GeminiShiftMapper.registerExtractedData(GeminiShiftMapper.kt:179)
     ```

3. **Ispezione File di Implementazione (`GeminiShiftMapper.kt`)**:
   - `GeminiShiftMapper.registerExtractedData` (righe 150–224): nessuna sincronizzazione thread-safe (manca `synchronized(repository)`).
   - `GeminiShiftMapper.resolveShiftDefId` (righe 325–334): riferimento cablato staticamente al singleton `ShiftRepository.shiftDefinitions` anziché al parametro repository passato o alla lista locale `allDefs`.
   - `GeminiShiftMapper.calculateNameMatchScore` (righe 377–381): la Regola 5 attribuisce un punteggio fisso di `60` per un singolo token coincidente anche se la query è composta da più token (`qTokens.size > 1`). Con la soglia a `>= 50` in riga 140, si verificano falsi positivi su omonimia parziale (es. query `"Andrea Romano"` viene associata a `"Andrea Bianchi"` con score 60).
   - `GeminiShiftMapper.calculateNameMatchScore` (riga 347): la regex `replace(Regex("[,;._-]"), " ")` non gestisce vocali accentate (es. `"Niccolo"` vs `"Niccolò"`) né apostrofi tipografici (es. U+2019 `’` vs ASCII `'`).

---

## 2. Logic Chain

### A. Integrità del Codice (Anti-Cheating Check)
- Nessuna violazione di integrità riscontrata: nessun risultato di test cablato, nessuna facciata fasulla o mock ingannevole all'interno di `GeminiShiftMapper.kt`.
- L'implementazione gestisce dinamicamente i modelli di dominio (`ShiftDefinition`, `ShiftRotation`), i cicli rotativi e le definizioni turno reali.

### B. Gestione della Concorrenza su `ShiftRepository` (Finding Critico)
1. In `ShiftRepository.kt`, le collezioni dei modelli di turno e rotazioni sono implementate tramite `androidx.compose.runtime.mutableStateListOf` (`SnapshotStateList`).
2. `SnapshotStateList` non è thread-safe per mutazioni concorrenti fuori dal contesto di snapshot sincronizzato di Jetpack Compose: quando un thread modifica la lista invocando `saveShiftDefinition` e un altro thread la itera (es. in `allDefs = (registeredDefs + repository.shiftDefinitions)` a riga 185 o durante `indexOfFirst` a riga 160), l'iteratore solleva `ConcurrentModificationException`.
3. Nell'esecuzione dello stress test di concorrenza (`GeminiShiftMapperStressTest.kt:404-472`), 5 operazioni su 10 thread simultanei sono andate in crash con `ConcurrentModificationException`.
4. Poiché le operazioni di parsing Gemini e importazione avvengono in coroutine di background (`Dispatchers.IO`), un doppio click rapido dell'utente o un caricamento in background simultaneo a una lettura della UI provocherebbe il crash immediato dell'applicazione.

### C. Robustezza dell'Algoritmo `matchUserSquad` (Finding Major e Minor)
1. **Falsi Positivi su Omonimia Parziale (Major)**:
   - In `calculateNameMatchScore` (righe 377–381), la regola `anyTokenMatch` assegna 60 punti se un qualsiasi token con lunghezza >= 3 coincide.
   - Poiché la soglia in `matchUserSquad` (riga 140) è `bestScore >= 50`, se l'utente inserisce `"Andrea Romano"` e nel prospetto è presente un collega `"Andrea Bianchi"`, l'algoritmo calcola score 60 e associa forzatamente l'utente alla squadra di `"Andrea Bianchi"`.
   - Se la query contiene più token (`qTokens.size >= 2`), il match su un solo token non deve superare la soglia di assegnazione automatica (deve avere score < 50, oppure richiedere la corrispondenza del cognome / maggioranza dei token).
2. **Substring Collision su Token Corti (Minor)**:
   - In riga 362, `cTok.contains(qTok)` per token di 3 lettere causa matching impropri: una query come `"Dan"` o `"Leo"` matcha parole come `"Giordano"` o `"Pantaleo"` con score 80.
3. **Accenti e Apostrofi Tipografici (Minor)**:
   - L'assenza di normalizzazione NFD dei caratteri diacritici fa fallire la ricerca per nomi come `"Niccolo"` vs `"Niccolò"` (score 0 anziché match).
   - L'apostrofo tipografico Unicode U+2019 (`’`) comune nelle tastiere Android non viene normalizzato, causando il fallimento su cognomi singoli come `"D’Angelo"`.

### D. Selezione di `defaultRotationId` e Salvaguardia Calendario
- La logica di pre-selezione è corretta e sicura: se la squadra viene individuata, imposta `repository.setDefaultRotation(matchedRot.id)` e popola `defaultRotationId` in `ExtractedSetupResult`.
- Se nessun match viene trovato, il turno predefinito preesistente non viene sovrascritto.
- `registerExtractedData` non modifica arbitrariamente `calendarShifts`, delegando la scelta di applicare il ciclo alla schermata/ViewModel (conforme al requisito R4 di `ORIGINAL_REQUEST.md`).
- **Difetto minore**: in `resolveShiftDefId` (righe 326 e 333), il metodo accede direttamente alla proprietà statica `ShiftRepository.shiftDefinitions` invece di usare l'istanza `repository` passata come parametro o l'insieme unificato `shiftDefs`.

---

## 3. Caveats

- Il test fallito riguarda specificamente lo scenario di concorrenza multi-thread stress (`10 thread` simultanei su `SnapshotStateList`).
- Le restanti funzionalità di mapping (conversione orari, palette colori di default, estrazione codici, calcolo categorie e icone) sono ben scritte, idempotenti e pienamente conformi all'architettura dell'app.

---

## 4. Conclusion

**VERDETTO: REQUEST_CHANGES**

Il rilascio di Milestone 3 non può essere approvato finché la vulnerabilità di concorrenza che fa fallire la suite di test non viene risolta e l'algoritmo di matching non viene irrobustito contro falsi positivi su omonimie.

### Azioni Correttive Obbligatorie per Worker M3:
1. **Thread-Safety in `registerExtractedData`**:
   - Sincronizzare l'intero blocco di registrazione sul monitor del repository:
     ```kotlin
     fun registerExtractedData(
         repository: ShiftRepository = ShiftRepository,
         data: GeminiShiftData,
         targetUserName: String? = null
     ): ExtractedSetupResult = synchronized(repository) {
         // Istantanea difensiva per evitare ConcurrentModificationException
         val currentRepoDefs = repository.shiftDefinitions.toList()
         ...
     }
     ```
   - In `resolveShiftDefId`, evitare accessi diretti al singleton `ShiftRepository.shiftDefinitions`; fare riferimento unicamente alla lista `shiftDefs` passata come argomento (che contiene già `registeredDefs + currentRepoDefs`).
2. **Mitigazione Falsi Positivi in `calculateNameMatchScore`**:
   - Nella Regola 5 (`anyTokenMatch`), se `qTokens.size > 1` ma solo 1 token combacia, assegnare un punteggio inferiore a 50 (es. 40), in modo che non attivi la pre-selezione automatica. Riservare lo score >= 50 ai casi in cui combaciano tutti i token della query o almeno il 70% dei token.
   - Aggiungere la normalizzazione dei diacritici (`Normalizer.normalize(..., Normalizer.Form.NFD)`) e degli apostrofi tipografici (`replace(Regex("[’‘`']"), " ")`).

---

## 5. Verification Method

Per riverificare la risoluzione:
1. Eseguire l'intera suite di unit test:
   ```powershell
   $env:JAVA_HOME = "C:\Program Files\Android\Android Studio\jre"
   .\gradlew.bat testDebugUnitTest --rerun-tasks
   ```
   **Condizione di successo**: esito `BUILD SUCCESSFUL`, 217/217 test passati (100% verde), zero `ConcurrentModificationException`.
2. Eseguire specificamente lo stress test di concorrenza:
   ```powershell
   .\gradlew.bat test --tests "com.turni.calendario.GeminiShiftMapperStressTest"
   ```
   **Condizione di successo**: tutti gli 11 test di `GeminiShiftMapperStressTest` devono risultare `PASSED`.
