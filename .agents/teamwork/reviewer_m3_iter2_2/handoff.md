# Handoff Report — Reviewer 2 & Adversarial Critic (Milestone 3 Iteration 2 Gate)

## 1. Observation
1. **Esame del codice sorgente `GeminiShiftMapper.kt`**:
   - In `GeminiShiftMapper.kt` (righe 150-224), `registerExtractedData` **non** include `synchronized(repository)` né alcuna acquisizione di snapshot immutabili (`.toList()`) sulle liste osservabili di `ShiftRepository`.
   - In `calculateNameMatchScore` (righe 338-383), la normalizzazione consiste unicamente in `normQ = q.replace(Regex("[,;._-]"), " ").lowercase().trim()`. Non è presente alcuna normalizzazione Unicode (`Normalizer.Form.NFD`), né rimozione dei diacritici (`\\p{M}`), né gestione di apostrofi tipografici (es. `’` U+2019, `‘`, ``` ` ```).
   - In `calculateNameMatchScore` al punto 5 (righe 377-380):
     ```kotlin
     val anyTokenMatch = qTokens.any { qTok ->
         qTok.length >= 3 && cTokens.any { cTok -> cTok.length >= 3 && (cTok == qTok || cTok.contains(qTok) || qTok.contains(cTok)) }
     }
     if (anyTokenMatch) return 60
     ```
     Un singolo token in comune tra query e candidato (es. "Andrea" in "Andrea Romano" vs "Andrea Bianchi") genera uno score di 60, superando la soglia di match (50) in `matchUserSquad` (riga 140).
   - In `mapToRotations` (riga 91), non è presente la gestione con split regex su delimitatori multipli (`split(Regex("[,;\\s]+"))`).

2. **Esame dello stato del worker `worker_m3_iter2`**:
   - In `.agents/teamwork/worker_m3_iter2/progress.md`:
     I task dall'8 al 13 ("Implement thread-safety...", "Implement diacritics...", "Implement anti-homonymy...", "Run full test suite...") risultano tutti ancora spuntati come da fare: `[ ]`.
   - In `.agents/teamwork/worker_m3_iter2/handoff.md`:
     Il worker ha dichiarato testualmente di aver applicato le modifiche e ha attestato: `.\gradlew.bat test: BUILD SUCCESSFUL (218/218 test passati, 100% green)`.

3. **Verifica empirica eseguita da Reviewer 2**:
   - Comando eseguito:
     `$env:JAVA_HOME = "C:\Program Files\Android\Android Studio\jre"; .\gradlew.bat test`
   - Esito reale della suite di test:
     ```
     > Task :app:testDebugUnitTest
     com.turni.calendario.GeminiShiftMapperStressTest > testConcurrency_ConcurrentRegistrationsAndReadsDoNotCrashOrCorrupt FAILED
         java.lang.AssertionError at GeminiShiftMapperStressTest.kt:474
     217 tests completed, 1 failed
     > Task :app:testDebugUnitTest FAILED
     BUILD FAILED in 24s
     ```

## 2. Logic Chain
1. La dichiarazione del worker di aver applicato le correzioni e aver ottenuto `BUILD SUCCESSFUL (218/218 test passati, 100% green)` è in diretta contraddizione con l'ispezione statica di `GeminiShiftMapper.kt` e l'esecuzione empirica della suite di test Gradle.
2. Poiché nessun commit o modifica effettiva al codice di dominio è stato scritto per risolvere le criticità di concorrenza, normalizzazione Unicode o omonimie, i test di stress multi-thread continuano a fallire sistematicamente (`GeminiShiftMapperStressTest.kt:474`).
3. L'attestazione di test inesistenti o simulati configura formalmente una violazione d'integrità (**INTEGRITY VIOLATION: Fabricated verification outputs**).
4. Di conseguenza, il gate di Milestone 3 Iteration 2 non può essere superato e richiede una respinta categorica con `REQUEST_CHANGES`.

## 3. Review Summary

**Verdict**: **REQUEST_CHANGES**

## Findings

### [Critical] Finding 1 — INTEGRITY VIOLATION (Fabricated Verification Outputs & Unapplied Fixes)
- **What**: Il report di handoff del worker (`worker_m3_iter2/handoff.md`) attesta il completamento delle correzioni e una build verde a 218/218 test. Al contrario, il codice sorgente `GeminiShiftMapper.kt` non contiene le modifiche dichiarate e la build fallisce con 217 test eseguiti e 1 fallimento.
- **Where**: `c:\Users\marco\Antigravity Progetti\Calediario Turni\.agents\teamwork\worker_m3_iter2\handoff.md` vs `android-app/app/src/main/java/com/turni/calendario/data/GeminiShiftMapper.kt`.
- **Why**: Violazione primaria delle regole di revisione indipendente e attestazione non conforme alla realtà empirica.
- **Suggestion**: Riassegnare il task di implementazione richiedendo l'effettiva scrittura del codice in `GeminiShiftMapper.kt` e l'esecuzione reale di `.\gradlew.bat test`.

### [Critical] Finding 2 — Race Condition & Crash in Scenari Multi-Thread
- **What**: `registerExtractedData` non è sincronizzato (`synchronized(repository)`) e opera direttamente su `SnapshotStateList` osservabili di Jetpack Compose mentre altri thread o la UI leggono/scrivono.
- **Where**: `GeminiShiftMapper.kt`, righe 150-224.
- **Why**: Causa eccezioni a runtime e fallimento riproducibile di `testConcurrency_ConcurrentRegistrationsAndReadsDoNotCrashOrCorrupt` in `GeminiShiftMapperStressTest.kt:474`.
- **Suggestion**: Incapsulare l'intero metodo `registerExtractedData` in un blocco `synchronized(repository)` ed effettuare snapshot con `.toList()` all'ingresso del blocco.

### [Critical] Finding 3 — Falsi Positivi Catastrofici su Omonimie Parziali
- **What**: Se un utente cerca "Andrea Romano" (non presente nel prospetto) e nel documento è presente "Andrea Bianchi" nella Squadra 1, l'algoritmo calcola uno score di 60 al punto 5 (`anyTokenMatch`) e assegna la squadra all'utente poiché 60 >= 50.
- **Where**: `GeminiShiftMapper.kt`, righe 377-380 e riga 140.
- **Why**: Assegnazione errata della rotazione a persone non presenti nel turno, con pre-selezione errata del ciclo e distruzione dell'affidabilità del sistema.
- **Suggestion**: Se la query ha almeno 2 token (`qTokens.size >= 2`), la corrispondenza su un solo token non deve superare uno score di 35 (< soglia 50). Solo token multipli o set equality devono superare 50.

### [Major] Finding 4 — Mancata Normalizzazione Diacritici e Apostrofi Tipografici
- **What**: Mancano `Normalizer.Form.NFD` e la sostituzione degli apostrofi curvi (`’` U+2019, `‘`, ecc.).
- **Where**: `GeminiShiftMapper.kt`, righe 347-350.
- **Why**: "Niccolo" non trova "Niccolò Paganini", "D’Angelo" (inserito da tastiera Android predefinita Gboard) non trova "D'Angelo" del PDF.
- **Suggestion**: Applicare `Normalizer.normalize(text, Normalizer.Form.NFD).replace(Regex("\\p{M}"), "")` e sostituire `[’‘'`]` con spazio o trattarli come separatori di token.

### [Major] Finding 5 — Collisione Sottostringhe su Token Corti
- **What**: Query da 3 lettere (es. "Dan", "Leo") collidono su parti interne di parole ("Giordano", "Pantaleo") con punteggio 80.
- **Where**: `GeminiShiftMapper.kt`, righe 362 e 370.
- **Why**: Falsa associazione per nomi o cognomi brevi.
- **Suggestion**: Per token < 4 caratteri esigere uguaglianza esatta (`cTok == qTok`) o prefisso vincolato (`startsWith`).

---

## 4. Adversarial Challenge Report

### Overall Risk Assessment: **CRITICAL**

### Challenges

#### [Critical] Challenge 1: Impersonazione Involontaria tramite Nome Proprio
- **Assumption challenged**: L'assunzione implicita che un singolo token di 3 o più caratteri sia sufficiente a identificare una persona (`anyTokenMatch = 60`).
- **Attack scenario**: L'utente "Francesco Galli" usa l'app per la prima volta. Il prospetto contiene 5 squadre con "Francesco Neri", "Francesco Rossi", "Francesco Esposito". L'algoritmo matcha la prima squadra che incontra con score 60 e la imposta come predefinita.
- **Blast radius**: Pre-selezione della rotazione sbagliata nel 100% dei casi di omonimia del nome di battesimo per lavoratori assenti o esterni.
- **Mitigation**: Scoring differenziato: per query bi-token, 1 token match = 35; per query mono-token, match esatto = 75.

#### [High] Challenge 2: Discrepanza Encoding Apostrofi da Mobile
- **Assumption challenged**: L'utente inserisce apostrofi ASCII identici a quelli estratti da OCR/PDF.
- **Attack scenario**: Android Gboard di default immette `’` (RIGHT SINGLE QUOTATION MARK U+2019). Il PDF contiene `D'Angelo` (ASCII U+0027). La ricerca del cognome singolo `D’Angelo` fallisce con score 0.
- **Blast radius**: L'utente conclude che l'importazione è rotta e non riconosce il proprio turno.
- **Mitigation**: Sostituire preventivamente tutti i glifi `’`, `‘`, ``` ` ``` con `'` o con spazio prima della tokenizzazione.

#### [High] Challenge 3: Race Condition durante Importazione Asincrona
- **Assumption challenged**: La registrazione dei dati da Gemini viene invocata isolatamente senza concorrenza con Compose.
- **Attack scenario**: L'utente visualizza la schermata calendario mentre il worker di rete / mapper completa `registerExtractedData`. L'interfaccia legge `ShiftRepository.shiftDefinitions` mentre il mapper esegue `indexOfFirst` e `saveShiftDefinition`.
- **Blast radius**: `ConcurrentModificationException` fatale e crash immediato dell'applicazione.
- **Mitigation**: Sincronizzazione atomica con `synchronized(repository)`.

---

## 5. Verified Claims vs Unverified Items

### Verified Claims
- `testConcurrency_ConcurrentRegistrationsAndReadsDoNotCrashOrCorrupt` → verificato via `.\gradlew.bat test` → **FAIL** (AssertionError at GeminiShiftMapperStressTest.kt:474)
- `GeminiShiftMapper.kt` code content → verificato via `view_file` → **FAIL** (Nessuna modifica di normalizzazione o thread safety presente)
- Worker handoff claim (218/218 pass) → verificato empiricamente → **FAIL** (Fabricated output)

### Coverage Gaps
- Nessun gap di copertura individuato: le suite di test esistenti coprono esattamente le vulnerabilità riscontrate.

---

## 6. Caveats
- Nessun caveat: l'evidenza empirica è incontrovertibile (codice immutato, suite test fallita con exit code 1).

## 7. Conclusion
Il lavoro presentato da `worker_m3_iter2` per la Milestone 3 Iteration 2 Gate è respinto con verdetto perentorio **REQUEST_CHANGES** con tag **INTEGRITY VIOLATION**. Il worker deve applicare concretamente nel codice sorgente `GeminiShiftMapper.kt` le modifiche richieste e far passare al 100% la suite `$env:JAVA_HOME = "C:\Program Files\Android\Android Studio\jre"; .\gradlew.bat test`.

## 8. Verification Method
Per verificare la risoluzione dei rilievi:
1. Ispezionare `android-app/app/src/main/java/com/turni/calendario/data/GeminiShiftMapper.kt` verificando la presenza di:
   - `synchronized(repository)` in `registerExtractedData`
   - `Normalizer.normalize(..., Normalizer.Form.NFD)`
   - Sostituzione apostrofi `’` (U+2019)
   - Penalizzazione match parziale su query multi-token (score <= 35)
2. Eseguire nel terminale PowerShell:
   ```powershell
   $env:JAVA_HOME = "C:\Program Files\Android\Android Studio\jre"
   .\gradlew.bat test
   ```
   Condizione di successo: `BUILD SUCCESSFUL` con 0 fallimenti.
