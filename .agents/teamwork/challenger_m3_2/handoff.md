# Handoff Report — Challenger 2 Milestone 3: Cyclic Rotation & Repository Concurrency Stress

**Verdict**: **REJECT** (Vulnerabilità critica di concorrenza riprodotta empiricamente)

---

## 1. Observation

### Strumenti e comandi eseguiti
1. Baseline Test Suite (`.\gradlew.bat test` con `$env:JAVA_HOME = "C:\Program Files\Android\Android Studio\jre"`):
   - Esito: `BUILD SUCCESSFUL in 54s`, 43 actionable tasks (207 unit tests passati).
2. Stress Test Harness implementato:
   - File creato: `android-app/app/src/test/java/com/turni/calendario/GeminiShiftMapperStressTest.kt` (520 linee).
   - Copertura test: cicli insoliti (1, 3, 21, 35, 42 giorni, vuoto), turni sconosciuti e fallback, registrazioni ripetute consecutive (25 cicli), concorrenza multi-thread (10 thread x 15 operazioni simultanee di registrazione e lettura).
3. Esecuzione Stress Test Suite (`.\gradlew.bat test`):
   - Esito: `BUILD FAILED in 5m 51s`.
   - 208 test eseguiti, **1 fallito**.
   - Test fallito: `com.turni.calendario.GeminiShiftMapperStressTest.testConcurrency_ConcurrentRegistrationsAndReadsDoNotCrashOrCorrupt`.
   - Verbatim error log (`testReleaseUnitTest/classes/com.turni.calendario.GeminiShiftMapperStressTest.html`):
     ```
     java.lang.AssertionError: No exceptions or race conditions allowed during concurrent operations expected:<0> but was:<5>
     	at org.junit.Assert.fail(Assert.java:89)
     	at org.junit.Assert.failNotEquals(Assert.java:835)
     	at org.junit.Assert.assertEquals(Assert.java:120)
     	at com.turni.calendario.GeminiShiftMapperStressTest.testConcurrency_ConcurrentRegistrationsAndReadsDoNotCrashOrCorrupt(GeminiShiftMapperStressTest.kt:473)
     ```
   - Verbatim Standard Error (5 eccezioni concorrenti sollevate):
     ```
     java.util.ConcurrentModificationException
     	at androidx.compose.runtime.snapshots.StateListIterator.validateModification(SnapshotStateList.kt:278)
     	at androidx.compose.runtime.snapshots.StateListIterator.next(SnapshotStateList.kt:257)
     	at kotlin.jvm.internal.CollectionToArray.toArray(CollectionToArray.kt:91)
     	at androidx.compose.runtime.snapshots.SnapshotStateList.toArray(SnapshotStateList.kt)
     	at java.base/java.util.ArrayList.addAll(ArrayList.java:702)
     	at kotlin.collections.CollectionsKt___CollectionsKt.plus(_Collections.kt:3271)
     	at com.turni.calendario.data.GeminiShiftMapper.registerExtractedData(GeminiShiftMapper.kt:185)
     ```
     e a riga 160/164 (`repository.shiftDefinitions.indexOfFirst`):
     ```
     java.util.ConcurrentModificationException
     	at androidx.compose.runtime.snapshots.StateListIterator.validateModification(SnapshotStateList.kt:278)
     	at androidx.compose.runtime.snapshots.StateListIterator.next(SnapshotStateList.kt:257)
     	at com.turni.calendario.data.GeminiShiftMapper.registerExtractedData(GeminiShiftMapper.kt:160)
     ```

---

## 2. Logic Chain

1. **Valutazione Rotazioni a Cicli Insoliti (1, 3, 21, 35, 42 giorni, vuoti)**:
   - In `GeminiShiftMapper.kt:85-103`, `mapToRotations` normalizza la rotazione e mappa ogni giorno in un ID turno valido.
   - In `ShiftRepository.kt:385-393`, `getPlannedRotationShift` usa `Math.floorMod(daysDiff, rot.daySequence.size.toLong())`.
   - **Risultato empirico**: Per cicli a 1 giorno, 3 giorni, 21 giorni, 35 giorni e 42 giorni, il calcolo della rotazione ciclica funziona matematicamente in modo perfetto sia per date passate (modulo negativo), sia per date future lontane (es. oltre 10 anni).
   - Per rotazioni con sequenza vuota (`rotationDays = emptyList()`), il codice gestisce la guardia `if (rot.daySequence.isEmpty()) return null` senza sollevare divisioni per zero o eccezioni `IndexOutOfBoundsException`.

2. **Valutazione Turni Sconosciuti nella Rotazione**:
   - In `GeminiShiftMapper.kt:316-336`, `resolveShiftDefId` verifica in sequenza:
     1. Le nuove definizioni estratte da Gemini (`data.shiftDefinitions`);
     2. I modelli già presenti in `ShiftRepository.shiftDefinitions`;
     3. Il fallback canonico universale di `NgeniaTurniParser.mapCodeToShiftDefId(rawCode)`.
   - Per qualsiasi codice non riconosciuto o arbitrario (es. `"UNKNOWN_CODE_99"`, `"FOOBAR"`), `mapCodeToShiftDefId` restituisce `"riposo"`, che è una definizione garantita in `ShiftRepository`.
   - **Risultato empirico**: Nessun crash e fallback pulito; se invece il turno custom è censito nelle definizioni di Gemini, viene creato con UUID dedicato e preservato nella rotazione.

3. **Valutazione Registrazioni Ripetute (Idempotenza)**:
   - In `GeminiShiftMapper.kt:160-182`, prima di salvare una nuova definizione viene verificata l'esistenza tramite `id`, `shortCode` o `name`. Se già presente, la definizione viene aggiornata in-place senza duplicare l'entry o rigenerare un nuovo UUID.
   - `ShiftRepository.saveRotation` aggiorna la rotazione esistente cercando l'ID normalizzato (`rotazioni.indexOfFirst { it.id == normalized.id }`).
   - **Risultato empirico**: 25 registrazioni consecutive identiche mantengono stabile il numero totale di definizioni e rotazioni, senza rigenerazione di UUID e senza alcun data drift.

4. **Valutazione Concorrenza e Thread-Safety (FAIL)**:
   - `ShiftRepository` usa `mutableStateListOf<ShiftDefinition>()` e `mutableStateListOf<ShiftRotation>()` (classi Jetpack Compose `SnapshotStateList`).
   - `GeminiShiftMapper.registerExtractedData` non implementa alcuna sincronizzazione (lock o blocco `synchronized`).
   - Quando due o più thread eseguono concorrentemente `registerExtractedData` o iterano su `repository.shiftDefinitions` (es. `registeredDefs + repository.shiftDefinitions` a riga 185 o `indexOfFirst` a riga 160) mentre un altro thread scrive (`saveShiftDefinition`), `SnapshotStateList.StateListIterator` lancia inevitabilmente `java.util.ConcurrentModificationException`.
   - Nel test empirico multi-thread (10 thread concorrenti), 5 operazioni sono andate in crash con `ConcurrentModificationException`.
   - Poiché le operazioni di chiamata alle API Gemini e il parsing documentale avvengono tipicamente su coroutine in background (`Dispatchers.IO`), un'interazione concorrente o un doppio tocco utente causa il crash immediato dell'applicazione.

---

## 3. Caveats

- Il test di concorrenza è stato eseguito con thread pool standard JVM (`Executors.newFixedThreadPool(10)`). Su Android, Jetpack Compose raccomanda che le mutazioni di `SnapshotStateList` avvengano con sincronizzazione o sul MainThread per prevenire race conditions su snapshot concorrenti.
- Non sono stati riscontrati problemi funzionali sul mapping sequenziale dei cicli o sull'euristica dei nomi, che si sono dimostrati solidi ed ergonomici.

---

## 4. Conclusion

**VERDETTO: REJECT**

La Milestone 3 non può essere approvata nel suo stato attuale a causa della vulnerabilità critica di concorrenza:
- **Bug**: `ConcurrentModificationException` non gestita in `GeminiShiftMapper.registerExtractedData` riga 185 e 160 su accessi simultanei a `ShiftRepository.shiftDefinitions`.
- **Azione richiesta per Worker M3**:
  Incapsulare l'esecuzione di `registerExtractedData` all'interno di un blocco sincronizzato sul repository:
  ```kotlin
  fun registerExtractedData(
      repository: ShiftRepository = ShiftRepository,
      data: GeminiShiftData,
      targetUserName: String? = null
  ): ExtractedSetupResult = synchronized(repository) {
      // logica di registrazione esistente
  }
  ```
  oppure proteggere le mutazioni interne in `ShiftRepository` (`saveShiftDefinition`, `saveRotation`).
- Una volta applicata questa protezione, rieseguire `.\gradlew.bat test` per confermare che l'intera suite di stress test passi al 100%.

---

## 5. Verification Method

Per riprodurre in modo indipendente il fallimento empirico:

```powershell
$env:JAVA_HOME = "C:\Program Files\Android\Android Studio\jre"
cd "c:\Users\marco\Antigravity Progetti\Calediario Turni\android-app"
.\gradlew.bat test --tests "com.turni.calendario.GeminiShiftMapperStressTest"
```

**Esito attuale**:
- `testConcurrency_ConcurrentRegistrationsAndReadsDoNotCrashOrCorrupt`: **FAILED** con `java.util.ConcurrentModificationException` a `GeminiShiftMapper.kt:185`.
- Tutti gli altri 10 test di rotazione e idempotenza: **PASSED**.
