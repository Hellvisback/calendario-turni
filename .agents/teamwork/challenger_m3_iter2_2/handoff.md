# Handoff Report — Challenger 2 (Milestone 3 Iteration 2 Gate: Concurrency & Rotation Stress)

## 1. Observation

Esecuzione del comando di test empirico su Android Studio JRE:
`$env:JAVA_HOME = "C:\Program Files\Android\Android Studio\jre"; .\gradlew.bat testDebugUnitTest --tests "com.turni.calendario.GeminiShiftMapperStressTest" --no-daemon`

### Esito Esecuzione
```
com.turni.calendario.GeminiShiftMapperStressTest > testConcurrency_ConcurrentRegistrationsAndReadsDoNotCrashOrCorrupt FAILED
    java.lang.AssertionError at GeminiShiftMapperStressTest.kt:474

11 tests completed, 1 failed
> Task :app:testDebugUnitTest FAILED
BUILD FAILED in 21s
```

### Dettaglio Verbatim dal report XML (`android-app/app/build/test-results/testDebugUnitTest/TEST-com.turni.calendario.GeminiShiftMapperStressTest.xml`):
```xml
<testcase name="testConcurrency_ConcurrentRegistrationsAndReadsDoNotCrashOrCorrupt" classname="com.turni.calendario.GeminiShiftMapperStressTest" time="0.063">
  <failure message="java.lang.AssertionError: No exceptions or race conditions allowed during concurrent operations expected:<0> but was:<5>" type="java.lang.AssertionError">
java.lang.AssertionError: No exceptions or race conditions allowed during concurrent operations expected:<0> but was:<5>
	at org.junit.Assert.fail(Assert.java:89)
	at org.junit.Assert.failNotEquals(Assert.java:835)
	at org.junit.Assert.assertEquals(Assert.java:120)
	at com.turni.calendario.GeminiShiftMapperStressTest.testConcurrency_ConcurrentRegistrationsAndReadsDoNotCrashOrCorrupt(GeminiShiftMapperStressTest.kt:474)
  </failure>
</testcase>
```

Eccezioni catturate in `system-err`:
```
java.util.ConcurrentModificationException
	at androidx.compose.runtime.snapshots.StateListIterator.validateModification(SnapshotStateList.kt:278)
	at androidx.compose.runtime.snapshots.StateListIterator.next(SnapshotStateList.kt:257)
	at com.turni.calendario.GeminiShiftMapperStressTest.testConcurrency_ConcurrentRegistrationsAndReadsDoNotCrashOrCorrupt$lambda-17(GeminiShiftMapperStressTest.kt:448)
...
java.util.ConcurrentModificationException
	at androidx.compose.runtime.snapshots.StateListIterator.validateModification(SnapshotStateList.kt:278)
	at androidx.compose.runtime.snapshots.StateListIterator.next(SnapshotStateList.kt:257)
	at com.turni.calendario.repository.ShiftRepository.saveShiftDefinition(ShiftRepository.kt:1475)
	at com.turni.calendario.data.GeminiShiftMapper.registerExtractedData(GeminiShiftMapper.kt:176)
```

### Esito degli altri test della suite `GeminiShiftMapperStressTest`:
- `testUnusualCycleLengths_1DayCycle`: PASSED (0.001s)
- `testUnusualCycleLengths_3DayCycle`: PASSED (0.001s)
- `testUnusualCycleLengths_21Days_3Weeks`: PASSED (0.001s)
- `testUnusualCycleLengths_35Days_5Weeks`: PASSED (0.004s)
- `testUnusualCycleLengths_42Days_6Weeks`: PASSED (0.392s)
- `testUnusualCycleLengths_EmptyCycleDoesNotCrash`: PASSED (0.029s)
- `testUnmappedShiftCodes_CompletelyUnknownCodesFallbackGracefullyToRiposo`: PASSED (0.012s)
- `testUnmappedShiftCodes_CustomShiftInDefinitionsIsPreservedOverFallback`: PASSED (0.002s)
- `testRepetitiveRegistrations_StrictIdempotencyAndNoDuplicateBloat`: PASSED (0.046s)
- `testNameMatchScoring_EdgeCasesAndPunctuation`: PASSED (0.010s)

---

## 2. Logic Chain

1. **Claim del Worker M3 Iteration 2**: Nel documento `worker_m3_iter2/handoff.md`, il worker asserisce che:
   > "registerExtractedData è stato completamente incapsulato in synchronized(repository) { ... }. Vengono acquisiti snapshot immutabili (.toList()) all'inizio del blocco per repository.shiftDefinitions e repository.rotazioni, eliminando la ConcurrentModificationException su SnapshotStateList."
   > ".\gradlew.bat test: BUILD SUCCESSFUL (218/218 test passati, 100% green)."

2. **Verifica Empirica dei Fatti**:
   - `GeminiShiftMapper.kt` riga 157 contiene effettivamente `fun registerExtractedData(...) = synchronized(repository) {`.
   - Tuttavia, `ShiftRepository.rotations` e `ShiftRepository.shiftDefinitions` sono collezioni Jetpack Compose `SnapshotStateList` (`mutableStateListOf()`) appartenenti al singleton `ShiftRepository`.
   - I metodi di `ShiftRepository` come `saveShiftDefinition`, `saveRotation`, `getShiftDefinition`, e `getPlannedRotationShift` non sono sincronizzati su `ShiftRepository`.
   - Inoltre, l'accesso diretto a `ShiftRepository.rotations` o `ShiftRepository.shiftDefinitions` (come eseguito dal thread UI o da lettori paralleli nel test alle righe 448, 450, 456 di `GeminiShiftMapperStressTest.kt`) avviene al di fuori di un blocco `synchronized(ShiftRepository)`.
   - Quando un thread esegue la scrittura dentro `registerExtractedData` invocando `repository.saveRotation(rot)` o `repository.saveShiftDefinition(def)`, il contatore interno `modification` della `SnapshotStateList` viene incrementato.
   - I thread concorrenti che iterano su `rotations` (`rotations.find { ... }`) o chiamano `getPlannedRotationShift` rilevano la mutazione e lanciano istantaneamente `ConcurrentModificationException` da `StateListIterator.validateModification(SnapshotStateList.kt:278)`.

3. **Impatto Empirico**:
   - Su 10 thread simultanei, 5 thread falliscono con CME.
   - `testConcurrency_ConcurrentRegistrationsAndReadsDoNotCrashOrCorrupt` fallisce categoricamente con `expected:<0> but was:<5>`.

---

## 3. Caveats

- I test relativi alla gestione dei cicli di rotazione atipici (1 giorno, 3 giorni, 21 giorni, 35 giorni, 42 giorni, ciclo vuoto) e al fallback graceful su codici turno non riconosciuti sono tutti pienamente funzionanti e passano con successo.
- L'algoritmo di matching nomi su edge case e punteggiatura in `GeminiShiftMapperStressTest` passa con successo.
- Il fallimento è circoscritto alla thread-safety e concorrenza multi-thread su `ShiftRepository` durante l'importazione parallela.

---

## 4. Conclusion

**VERDETTO: REJECT**

La `ConcurrentModificationException` su 10 thread simultanei **NON** è sparita. Il test di stress `testConcurrency_ConcurrentRegistrationsAndReadsDoNotCrashOrCorrupt` fallisce sistematicamente e impedisce il superamento del Gate della Milestone 3 Iteration 2.

### Azione Richiesta per il Worker:
1. In `ShiftRepository.kt`, sincronizzare i metodi di mutazione e lettura che accedono a `shiftDefinitions` e `rotazioni` (es. `saveShiftDefinition`, `saveRotation`, `deleteRotation`, `getShiftDefinition`, `getPlannedRotationShift`), oppure incapsulare l'accesso a `rotations` e `shiftDefinitions` garantendo snapshot thread-safe protetti da lock su `ShiftRepository`.
2. In alternativa o a completamento, assicurarsi che in `GeminiShiftMapperStressTest.kt` e nei flussi applicativi, ogni lettura/iterazione concorrente su `ShiftRepository` avvenga in modo thread-safe (es. tramite snapshot o sincronizzazione esplicita).

---

## 5. Verification Method

Per riprodurre in modo indipendente il fallimento:

```powershell
$env:JAVA_HOME = "C:\Program Files\Android\Android Studio\jre"
cd "c:\Users\marco\Antigravity Progetti\Calediario Turni\android-app"
.\gradlew.bat testDebugUnitTest --tests "com.turni.calendario.GeminiShiftMapperStressTest" --no-daemon
```

File di verifica da ispezionare:
- `android-app/app/build/test-results/testDebugUnitTest/TEST-com.turni.calendario.GeminiShiftMapperStressTest.xml`
- `android-app/app/src/test/java/com/turni/calendario/GeminiShiftMapperStressTest.kt` (riga 474)

Condizione di invalidazione del REJECT:
Il test `GeminiShiftMapperStressTest` deve terminare con `BUILD SUCCESSFUL` e 11/11 test passati (`failureCount == 0`).
