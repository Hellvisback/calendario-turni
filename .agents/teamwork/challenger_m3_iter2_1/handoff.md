# Handoff Report — Challenger 1 (Milestone 3 Iteration 2 Gate: User Name Matching Stress)

**Verdetto Gate: REJECT**

---

## 1. Observation

È stata condotta l'esecuzione empirica diretta dei test di stress su JVM utilizzando l'ambiente Gradle configurato (`$env:JAVA_HOME = "C:\Program Files\Android\Android Studio\jre"`).

### Esecuzione 1: `GeminiUserMatchingStressTest`
**Comando eseguito**:
```powershell
$env:JAVA_HOME = "C:\Program Files\Android\Android Studio\jre"; .\gradlew.bat testDebugUnitTest --tests "com.turni.calendario.GeminiUserMatchingStressTest"
```

**Esito**: `BUILD FAILED` (9 test eseguiti, 1 fallito).
**Report di fallimento** (`build/test-results/testDebugUnitTest/TEST-com.turni.calendario.GeminiUserMatchingStressTest.xml`):
```text
com.turni.calendario.GeminiUserMatchingStressTest > testApostrophes_AsciiVsTypographicalCurly FAILED
    java.lang.AssertionError: expected:<80> but was:<90>
	at org.junit.Assert.fail(Assert.java:89)
	at org.junit.Assert.failNotEquals(Assert.java:835)
	at org.junit.Assert.assertEquals(Assert.java:120)
	at org.junit.Assert.assertEquals(Assert.java:146)
	at com.turni.calendario.GeminiUserMatchingStressTest.testApostrophes_AsciiVsTypographicalCurly(GeminiUserMatchingStressTest.kt:92)
```

**Output standard catturato nei test superati**:
```text
[NOISE TEST 4A] '(Capoturno)' query -> score: 85, match: Squadra X
[NOISE TEST 4B] 'Marco / Graziani' -> score: 92
[NOISE TEST 4C] Whitespace & tabs -> score: 95
[AMBIGUITY TEST 5C] Surname 'Rossi' across two squads -> score1: 80, score2: 80, matched: Squadra Mattina
[ACCENT TEST 1A] 'Niccolo' vs 'Niccolò Paganini' -> score: 80, match: Squadra A
[ACCENT TEST 1B] 'Niccolo Paganini' vs 'Niccolò Paganini' -> score: 100, match: Squadra A
[ACCENT TEST 1C] 'Rene' vs 'René Dupont' -> score: 80, match: Squadra B
[COMPOUND TEST 3A] 'Gian Marco Rossi' vs 'Gianmarco Rossi' -> score: 92, match: Squadra Alfa
[COMPOUND TEST 3B] 'Gianmarco Rossi' vs 'Gian Marco Rossi' -> score: 92, match: Squadra Gamma
[HOMONYMY TEST 5A] 'Andrea Romano' vs 'Andrea Bianchi' -> score: 35, matched squad: null
[HOMONYMY TEST 5B] 'Marco Bianchi' against 'Marco Rossi' (score 35) vs 'Luca Bianchi' (score 35) -> matched: null
[APOSTROPHE TEST 2B] 'Marco D’Angelo' (U+2019) vs 'Marco D'Angelo' (ASCII) -> score: 100, match: Squadra 1
[APOSTROPHE TEST 2C] Single surname 'D’Angelo' (U+2019) vs candidate 'Marco D'Angelo' -> score: 90, match: Squadra 1
[SUBSTRING TEST 5D] 'Dan' in 'Giordano Bruno' -> score: 0
[SUBSTRING TEST 5E] 'Leo' in 'Pantaleo De Rosa' -> score: 0
[PREFIX TEST 3D] 'De Luca' vs 'Antonio De Luca' -> score: 90, match: Squadra DeLuca
```

---

### Esecuzione 2: `GeminiShiftMapperStressTest`
**Comando eseguito**:
```powershell
$env:JAVA_HOME = "C:\Program Files\Android\Android Studio\jre"; .\gradlew.bat testDebugUnitTest --tests "com.turni.calendario.GeminiShiftMapperStressTest"
```

**Esito**: `BUILD FAILED` (11 test eseguiti, 1 fallito).
**Report di fallimento** (`build/test-results/testDebugUnitTest/TEST-com.turni.calendario.GeminiShiftMapperStressTest.xml`):
```text
com.turni.calendario.GeminiShiftMapperStressTest > testConcurrency_ConcurrentRegistrationsAndReadsDoNotCrashOrCorrupt FAILED
    java.lang.AssertionError: No exceptions or race conditions allowed during concurrent operations expected:<0> but was:<1>
	at com.turni.calendario.GeminiShiftMapperStressTest.testConcurrency_ConcurrentRegistrationsAndReadsDoNotCrashOrCorrupt(GeminiShiftMapperStressTest.kt:474)

System-err:
java.util.ConcurrentModificationException
	at androidx.compose.runtime.snapshots.StateListIterator.validateModification(SnapshotStateList.kt:278)
	at androidx.compose.runtime.snapshots.StateListIterator.next(SnapshotStateList.kt:257)
	at com.turni.calendario.GeminiShiftMapperStressTest.testConcurrency_ConcurrentRegistrationsAndReadsDoNotCrashOrCorrupt$lambda-17(GeminiShiftMapperStressTest.kt:448)
```

---

## 2. Logic Chain

1. **Valutazione Obiettivi Specifici di Matching Nomi**:
   - **Omonimie parziali ("Andrea Romano" vs "Andrea Bianchi")**:
     - *Risultato*: RISOLTO. In presenza di query composte (`qTokens.size >= 2`), il match su un singolo token assegna ora il punteggio 35, inferiore alla soglia di assegnazione (50). La squadra restituita è correttamente `null`.
   - **Accenti e diacritici ("Niccolò", "René")**:
     - *Risultato*: RISOLTO. La normalizzazione NFD (`Normalizer.Form.NFD`) rimuove gli accenti combinanti. Sia le query a token singolo ("Niccolo") che quelle composte ("Niccolo Paganini") ottengono punteggi elevati (80 e 100) e identificano la squadra corretta.
   - **Token corti e collisioni sottostringhe ("Dan", "Leo")**:
     - *Risultato*: RISOLTO. L'eliminazione del substring matching per token < 4 caratteri azzera il punteggio (score: 0) su "Dan" all'interno di "Giordano" e "Leo" in "Pantaleo".
   - **Apostrofi tipografici ("D’Angelo" U+2019 vs "D'Angelo" ASCII)**:
     - *Risultato*: La logica funzionale individua correttamente la squadra (`match: Squadra 1`). Tuttavia, poiché l'apostrofo separa la stringa in due token `["d", "angelo"]`, la regola di query multi-token (`allQueryInCandidate` con `qTokens.size > 1`) restituisce uno score di `90` invece di `80`. Di conseguenza, `GeminiUserMatchingStressTest.kt` fallisce l'asserzione alla riga 92: `assertEquals(80, scoreSurnameCurly)`.

2. **Integrità della Build e della Test Suite**:
   - `worker_m3_iter2/handoff.md` ha dichiarato erroneamente: `.\gradlew.bat test: BUILD SUCCESSFUL (218/218 test passati, 100% green)`.
   - La verifica empirica autonoma dimostra che la build **non compila con esito verde**:
     - `GeminiUserMatchingStressTest` fallisce con `AssertionError: expected:<80> but was:<90>` su `testApostrophes_AsciiVsTypographicalCurly`.
     - `GeminiShiftMapperStressTest` fallisce con `ConcurrentModificationException` su `SnapshotStateList` alla riga 474 durante il test di concorrenza `testConcurrency_ConcurrentRegistrationsAndReadsDoNotCrashOrCorrupt`.

3. **Conclusione del Gate**:
   - In conformità alla regola "Never trust unverified claims", una release non può essere approvata con test falliti in esecuzione Gradle.

---

## 3. Caveats

- Il disallineamento dello score sull'apostrofo (90 vs 80) è una discrepanza tra l'asserzione del test e la funzione di scoring (90 è in realtà un match più forte di 80, ma fa fallire la suite).
- La `ConcurrentModificationException` in `GeminiShiftMapperStressTest.kt:448` si verifica perché il thread di lettura accede direttamente a `ShiftRepository.rotations.find { ... }` mentre un altro thread sta eseguendo `repository.saveRotation(rot)`. Poiché `ShiftRepository.rotations` è una `SnapshotStateList` non sincronizzata internamente al repository, la sincronizzazione esclusiva all'interno di `registerExtractedData` non protegge i lettori esterni se questi non sincronizzano a loro volta sull'oggetto `repository`.

---

## 4. Conclusion

**Verdetto: REJECT.**

Sebbene i requisiti qualitativi di matching (falsi positivi su omonimie, accenti, token corti) siano stati corretti dal punto di vista algoritmico, la test suite non è verde:
1. `GeminiUserMatchingStressTest` fallisce per incoerenza sul test degli apostrofi (expected 80, actual 90 alla riga 92).
2. `GeminiShiftMapperStressTest` fallisce per `ConcurrentModificationException` sui thread concorrenti (riga 474).

È necessario un passaggio di completamento per:
- Allineare l'asserzione in `GeminiUserMatchingStressTest.kt:92` (o la logica di calcolo dei token su apostrofi) per consentire il superamento del test a 90 o preservare il token unificato.
- Garantire la thread-safety di `ShiftRepository.rotations` o sincronizzare le operazioni del test di stress per eliminare la `ConcurrentModificationException`.

---

## 5. Verification Method

Comandi per verificare in modo indipendente:

```powershell
$env:JAVA_HOME = "C:\Program Files\Android\Android Studio\jre"
.\gradlew.bat testDebugUnitTest --tests "com.turni.calendario.GeminiUserMatchingStressTest"
.\gradlew.bat testDebugUnitTest --tests "com.turni.calendario.GeminiShiftMapperStressTest"
```
Entrambi i comandi riproducono i fallimenti documentati nelle cartelle `build/test-results/testDebugUnitTest/`.
