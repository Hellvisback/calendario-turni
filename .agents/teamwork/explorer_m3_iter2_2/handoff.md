# Handoff Report — Explorer 2: Root Cause Analysis & Fix Strategy per Concurrency & Thread Safety

**Milestone**: Milestone 3 (Iteration 2)  
**Agent**: Explorer 2 (`explorer_m3_iter2_2`)  
**Parent**: Orchestrator (`90066a2c-0ee0-47dc-9cce-f552c7e66ada`)  
**Status**: COMPLETE (Pronto per l'implementazione del Worker M3)

---

## 1. Observation

### 1.1 Comandi Eseguiti e Risultati Empirici
1. **Verifica Riproduzione Baseline dello Stress Test**:
   ```cmd
   cmd.exe /c "set JAVA_HOME=C:\Program Files\Android\Android Studio\jre&& gradlew.bat testDebugUnitTest --tests com.turni.calendario.GeminiShiftMapperStressTest"
   ```
   - **Esito**: `BUILD FAILED in 24s`, 21 actionable tasks (20 up-to-date, 1 executed).
   - **Risultato test**: 11 test eseguiti, **1 fallito** (`testConcurrency_ConcurrentRegistrationsAndReadsDoNotCrashOrCorrupt`).
   - **Verbatim Failure Assertion**:
     ```text
     java.lang.AssertionError: No exceptions or race conditions allowed during concurrent operations expected:<0> but was:<7>
         at org.junit.Assert.fail(Assert.java:89)
         at org.junit.Assert.failNotEquals(Assert.java:835)
         at org.junit.Assert.assertEquals(Assert.java:120)
         at com.turni.calendario.GeminiShiftMapperStressTest.testConcurrency_ConcurrentRegistrationsAndReadsDoNotCrashOrCorrupt(GeminiShiftMapperStressTest.kt:474)
     ```
   - **Punti di crash osservati nel report HTML (`testDebugUnitTest/classes/com.turni.calendario.GeminiShiftMapperStressTest.html`)**:
     - `GeminiShiftMapper.kt:185`: `(registeredDefs + repository.shiftDefinitions).distinctBy { it.id }` lancia `ConcurrentModificationException` su `SnapshotStateList.toArray()`.
     - `GeminiShiftMapper.kt:327`: `resolveShiftDefId` interroga direttamente il singleton `ShiftRepository.shiftDefinitions` e lancia `ConcurrentModificationException` su `StateListIterator.next()`.
     - `ShiftRepository.kt:790`: `saveRotation` esegue `rotations[idx] = normalized` senza lock, sollevando `ConcurrentModificationException`.
     - `GeminiShiftMapperStressTest.kt:448`: la lettura concorrente esterna `ShiftRepository.rotations.find { ... }` fallisce con `ConcurrentModificationException` perché un altro thread sta modificando `rotations` in background.

2. **Ispezione Sorgenti**:
   - `GeminiShiftMapper.kt` (385 linee): `registerExtractedData` non ha sincronizzazione (`synchronized`). `resolveShiftDefId` accede staticamente a `ShiftRepository.shiftDefinitions` anziché limitarsi alla lista immutabile `shiftDefs`.
   - `ShiftRepository.kt` (1417 linee): `shiftDefinitions` e `rotations` sono `mutableStateListOf<T>()` (`SnapshotStateList`). Metodi `saveShiftDefinition`, `saveRotation`, `deleteShiftDefinition`, `deleteRotation`, `setDefaultRotation` e `getPlannedRotationShift` non sono sincronizzati. In aggiunta, `saveRotation` e `saveShiftDefinition` eseguono scritture cieche (`rotations[idx] = normalized`) anche quando l'oggetto è identico, incrementando arbitrariamente il contatore interno `modification` 140 volte consecutive.
   - Non esiste alcuna traccia preesistente di `synchronized` o `ReentrantLock` nel codice del modulo `app/src/main/java`.

---

## 2. Logic Chain

1. **Origine dell'eccezione**:
   - `SnapshotStateList` in Jetpack Compose traccia le mutazioni con `var modification: Int`. Ogni volta che `set`, `add`, o `remove` viene invocato, `modification++` viene eseguito.
   - Gli iteratori (`StateListIterator`) controllano `validateModification()` su ciascun `.next()`. Se `modification` è mutato rispetto all'inizio dell'iterazione, viene sollevata `ConcurrentModificationException`.
2. **Inadeguatezza del solo lock in `GeminiShiftMapper`**:
   - Sincronizzare unicamente `GeminiShiftMapper.registerExtractedData` con `synchronized(repository)` proteggerebbe solo i thread all'interno di quel metodo, ma lascerebbe esposti i lettori concorrenti esterni su `ShiftRepository.rotations` (es. `rotations.find` alla riga 448 del test o i composable della UI).
3. **Inapplicabilità di Kotlin `Mutex`**:
   - `Mutex.withLock` richiede funzioni `suspend`. Il contratto di `registerExtractedData` e dei metodi del repository è sincrono ed è invocato da thread pool standard Java (`ExecutorService`) nei test e da lifecycle sincronizzati. Forzare `runBlocking` rischierebbe starvation del pool.
4. **Superiorità di `synchronized(repository)` rispetto a `ReentrantLock`**:
   - `synchronized(repository)` e `synchronized(this)` all'interno del singleton `ShiftRepository` condividono lo **stesso identico monitor intrinseco**.
   - I monitor JVM sono **reentranti**: un thread che entra in `registerExtractedData` acquisisce il lock sul repository ed entra nei metodi interni di `ShiftRepository` (`saveShiftDefinition`, `saveRotation`) senza alcun blocco o deadlock.
   - Non richiede allocazione di oggetti lock ausiliari, supporta l'uscita trasparente in caso di eccezioni ed elimina il rischio di circular wait (essendoci 1 solo monitor nell'intero sistema).
5. **Mitigazione delle Scritture Ridondanti**:
   - Aggiungendo `if (rotations[idx] != normalized)` e `if (shiftDefinitions[existingIndex] != def)`, dopo la prima registrazione identica le successive 140 iterazioni diventano no-op a livello di stato, azzerando le invalidazioni della lista e garantendo che Compose UI non esegua recomposition inutili.
6. **Disaccoppiamento in `resolveShiftDefId`**:
   - `resolveShiftDefId` riceve già `shiftDefs` (che unisce `registeredDefs` e lo snapshot di `shiftDefinitions`). Rimuovendo i 4 accessi diretti al singleton statico `ShiftRepository.shiftDefinitions`, la risoluzione diventa puramente in-memory su lista immutabile, immune al 100% da `ConcurrentModificationException`.

---

## 3. Caveats

- **Nessuna modifica al tipo di collezione Compose**: `shiftDefinitions` e `rotations` in `ShiftRepository` devono rimanere `SnapshotStateList` (`mutableStateListOf`) per garantire l'osservabilità e la reattività nativa di Jetpack Compose nelle schermate `CalendarScreen` e `TurniModelsScreen`. Non devono essere sostituite con `CopyOnWriteArrayList`.
- **Rilievo su Name Matching di Reviewer 2**: oltre alla concorrenza, Reviewer 2 ha evidenziato una vulnerabilità di omonimia parziale nella Regola 5 di `calculateNameMatchScore` (score 60 assegnato a singoli token coincidenti quando la query ha più parole). La soluzione completa è stata documentata nella raccomandazione per il Worker.

---

## 4. Conclusion

**STRATEGIA APPROVATA: Two-Tier Synchronized Protection + Defensive Snapshot + Equal-Check Guard**

1. In `GeminiShiftMapper.kt`:
   - Racchiudere `registerExtractedData` in `synchronized(repository) { ... }`.
   - Utilizzare snapshot difensivo `val allDefs = (registeredDefs + repository.shiftDefinitions.toList()).distinctBy { it.id }`.
   - Rimuovere gli accessi diretti a `ShiftRepository.shiftDefinitions` da `resolveShiftDefId`.
   - Aggiornare `calculateNameMatchScore` con normalizzazione diacritici/apostrofi e calibrazione dello score su token singoli (< 50 quando `qTokens.size > 1`).
2. In `ShiftRepository.kt`:
   - Aggiungere `synchronized(this)` con guardia di disuguaglianza su `saveShiftDefinition` e `saveRotation`.
   - Sincronizzare `deleteShiftDefinition`, `deleteRotation`, `setDefaultRotation` e `getPlannedRotationShift`.

Il piano dettagliato con frammenti di codice prima/dopo completi è documentato in:
`c:\Users\marco\Antigravity Progetti\Calediario Turni\.agents\teamwork\explorer_m3_iter2_2\analysis.md`.

---

## 5. Verification Method

Per verificare in modo indipendente la soluzione una volta implementata dal Worker:

```powershell
$env:JAVA_HOME = "C:\Program Files\Android\Android Studio\jre"
cd "c:\Users\marco\Antigravity Progetti\Calediario Turni\android-app"

# 1. Verifica specifica dello Stress Test di Concorrenza (11 test)
.\gradlew.bat testDebugUnitTest --tests "com.turni.calendario.GeminiShiftMapperStressTest"

# 2. Verifica dell'intera test suite unitaria (217+ test)
.\gradlew.bat testDebugUnitTest --rerun-tasks
```

**Condizioni di successo**:
- `testConcurrency_ConcurrentRegistrationsAndReadsDoNotCrashOrCorrupt`: **PASSED** (0 eccezioni o race conditions).
- Totale test: 100% verde (217/217 passati).
- `BUILD SUCCESSFUL`.
