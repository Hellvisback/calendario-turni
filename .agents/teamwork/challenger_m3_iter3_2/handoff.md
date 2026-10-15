# Handoff Report — Challenger 2 (Milestone 3 Iteration 3 Gate)

**Verdetto Gate**: **REJECT**

---

## 1. Observation

Durante l'esecuzione dello stress test empirico richiesto da DISPATCH:
```powershell
$env:JAVA_HOME = "C:\Program Files\Android\Android Studio\jre"; .\gradlew.bat testDebugUnitTest --tests "com.turni.calendario.GeminiShiftMapperStressTest"
```

L'esecuzione ha prodotto un fallimento del test suite:
- **Test completati**: 11
- **Test passati**: 10
- **Test falliti**: 1 (`testConcurrency_ConcurrentRegistrationsAndReadsDoNotCrashOrCorrupt`)
- **Esito Gradle**: `BUILD FAILED`

### Dettaglio Verbatim dell'Errore (da `app/build/test-results/testDebugUnitTest/TEST-com.turni.calendario.GeminiShiftMapperStressTest.xml`):
```xml
<testcase name="testConcurrency_ConcurrentRegistrationsAndReadsDoNotCrashOrCorrupt" classname="com.turni.calendario.GeminiShiftMapperStressTest" time="0.272">
    <failure message="java.lang.AssertionError: No exceptions or race conditions allowed during concurrent operations expected:&lt;0&gt; but was:&lt;1&gt;" type="java.lang.AssertionError">
java.lang.AssertionError: No exceptions or race conditions allowed during concurrent operations expected:&lt;0&gt; but was:&lt;1&gt;
	at org.junit.Assert.fail(Assert.java:89)
	at org.junit.Assert.failNotEquals(Assert.java:835)
	at org.junit.Assert.assertEquals(Assert.java:120)
	at com.turni.calendario.GeminiShiftMapperStressTest.testConcurrency_ConcurrentRegistrationsAndReadsDoNotCrashOrCorrupt(GeminiShiftMapperStressTest.kt:474)
    </failure>
</testcase>
<system-err><![CDATA[java.util.ConcurrentModificationException
	at androidx.compose.runtime.snapshots.StateListIterator.validateModification(SnapshotStateList.kt:278)
	at androidx.compose.runtime.snapshots.StateListIterator.next(SnapshotStateList.kt:257)
	at com.turni.calendario.GeminiShiftMapperStressTest.testConcurrency_ConcurrentRegistrationsAndReadsDoNotCrashOrCorrupt$lambda-17(GeminiShiftMapperStressTest.kt:448)
	at java.base/java.util.concurrent.Executors$RunnableAdapter.call(Executors.java:515)
	at java.base/java.util.concurrent.FutureTask.run(FutureTask.java:264)
	at java.base/java.util.concurrent.ThreadPoolExecutor.runWorker(ThreadPoolExecutor.java:1128)
	at java.base/java.util.concurrent.ThreadPoolExecutor$Worker.run(ThreadPoolExecutor.java:628)
	at java.base/java.lang.Thread.run(Thread.java:829)
]]></system-err>
```

### Esito dei restanti 10 test di stress:
1. `testUnusualCycleLengths_1DayCycle`: **PASSED** (0.001s)
2. `testUnusualCycleLengths_3DayCycle`: **PASSED** (0.001s)
3. `testUnusualCycleLengths_21Days_3Weeks`: **PASSED** (0.001s)
4. `testUnusualCycleLengths_35Days_5Weeks`: **PASSED** (0.002s)
5. `testUnusualCycleLengths_42Days_6Weeks`: **PASSED** (0.844s)
6. `testUnusualCycleLengths_EmptyCycleDoesNotCrash`: **PASSED** (0.021s)
7. `testUnmappedShiftCodes_CompletelyUnknownCodesFallbackGracefullyToRiposo`: **PASSED** (0.015s)
8. `testUnmappedShiftCodes_CustomShiftInDefinitionsIsPreservedOverFallback`: **PASSED** (0.001s)
9. `testRepetitiveRegistrations_StrictIdempotencyAndNoDuplicateBloat`: **PASSED** (0.078s)
10. `testNameMatchScoring_EdgeCasesAndPunctuation`: **PASSED** (0.018s)

---

## 2. Logic Chain

1. **Requisito di Gate**: Il dispatch richiede espressamente: *"Verifica che testConcurrency_ConcurrentRegistrationsAndReadsDoNotCrashOrCorrupt passi al 100% senza alcuna ConcurrentModificationException su 10 thread simultanei."*
2. **Causa Radice Architetturale**:
   - `ShiftRepository.rotations` è dichiarato in `ShiftRepository.kt` (linea 119) come `val rotations = mutableStateListOf<ShiftRotation>()` (`SnapshotStateList` di Jetpack Compose).
   - In `GeminiShiftMapper.registerExtractedData` (linea 157) viene usato `synchronized(repository)`, e vengono chiamati `repository.saveRotation(rot)` che sincronizzano su `ShiftRepository`.
   - Tuttavia, `ShiftRepository.rotations` è una lista pubblica direttamente esposta. In `GeminiShiftMapperStressTest.kt` alla linea 448:
     ```kotlin
     val rot = ShiftRepository.rotations.find { it.id == "rotazione_squadra_concurrency_$t" }
     ```
     La chiamata ad estensione `.find` (o qualunque iterazione su `rotations`) alloca uno `StateListIterator` interno senza sincronizzazione. Se un altro thread concorrente invoca `rotations.add(normalized)` o `rotations[idx] = normalized` durante l'iterazione, `StateListIterator.validateModification()` rileva la modifica strutturale concorrente e lancia immediatamente `java.util.ConcurrentModificationException`.
   - Inoltre, anche in `ShiftRepository.getPlannedRotationShift(date: LocalDate, rotationId: String)` (linee 385-393 di `ShiftRepository.kt`):
     ```kotlin
     val rot = rotations.firstOrNull { it.id == targetId } ?: rotations.firstOrNull() ?: return null
     ```
     L'accesso a `rotations` avviene senza blocco `synchronized(this)` né snapshot copy, lasciando esposta la lettura concorrente al medesimo crash da `ConcurrentModificationException`.
3. **Impatto sul Gate**: La condizione di accettazione del Gate M3 Iter 3 di concorrenza multi-thread fallisce empiricamente con 1 errore su 10 thread simultanei (fallimento a linea 474 di `GeminiShiftMapperStressTest.kt`).

---

## 3. Caveats

- Tutti i test relativi alla logica di calcolo matematico dei cicli non standard (1, 3, 21, 35, 42 giorni, cicli vuoti, date negative nel passato e nel futuro) sono robusti e passano al 100%.
- L'idempotenza sequenziale (`testRepetitiveRegistrations_StrictIdempotencyAndNoDuplicateBloat`) è confermata al 100% (nessun bloat di definizioni o rotazioni).
- Il fallback per codici non mappati e il parsing nominativi con accenti e punteggiatura passano al 100%.
- Il fallimento è circoscritto alla thread-safety di lettura/scrittura concorrente sulla lista `rotations` di `ShiftRepository`.

---

## 4. Conclusion

**Verdetto**: **REJECT**.

La milestone 3 Iteration 3 non può essere approvata allo stato attuale in quanto `testConcurrency_ConcurrentRegistrationsAndReadsDoNotCrashOrCorrupt` lancia `java.util.ConcurrentModificationException` violando il criterio vincolante di Gate.

### Azione Correttiva Raccomandata per il Worker / SWE:
1. In `ShiftRepository.kt`:
   - Rendere thread-safe l'accesso e la ricerca nelle rotazioni esponendo un metodo sincronizzato o una snapshot difensiva:
     ```kotlin
     fun getRotationsSnapshot(): List<ShiftRotation> = synchronized(this) { rotations.toList() }
     fun getRotationById(id: String): ShiftRotation? = synchronized(this) { rotations.firstOrNull { it.id == id } }
     ```
   - In `ShiftRepository.getPlannedRotationShift`: proteggere la funzione con `synchronized(this)` oppure iterare sulla copia snapshot:
     ```kotlin
     fun getPlannedRotationShift(date: LocalDate, rotationId: String = activeRotationId): ShiftDefinition? = synchronized(this) {
         val targetId = if (rotationId.isNotBlank()) rotationId else (activeRotationId.ifEmpty { "turno_a" })
         val rot = rotations.firstOrNull { it.id == targetId } ?: rotations.firstOrNull() ?: return null
         ...
     }
     ```
2. In `GeminiShiftMapperStressTest.kt` (linea 448):
   - Leggere le rotazioni in modo sincronizzato o tramite snapshot:
     ```kotlin
     val rot = synchronized(ShiftRepository) { ShiftRepository.rotations.find { it.id == "rotazione_squadra_concurrency_$t" } }
     ```

---

## 5. Verification Method

Comando riproducibile eseguibile da terminale PowerShell:
```powershell
$env:JAVA_HOME = "C:\Program Files\Android\Android Studio\jre"
.\gradlew.bat testDebugUnitTest --tests "com.turni.calendario.GeminiShiftMapperStressTest"
```

Condizione di invalidazione / superamento:
- Il comando deve uscire con codice `0` (`BUILD SUCCESSFUL`).
- Il test report in `android-app/app/build/test-results/testDebugUnitTest/TEST-com.turni.calendario.GeminiShiftMapperStressTest.xml` deve riportare `tests="11" failures="0" errors="0"` e `system-err` privo di `ConcurrentModificationException`.
