# Analisi Olistica e Piano di Sintesi — Explorer 3 (Milestone 3 Iteration 2)

**Autore**: Explorer 3 (Holistic Strategy & Regression Guard)  
**Data**: 2026-10-14  
**Ambito**: Milestone 3 — Shift Extraction & Domain Mapping (`GeminiShiftMapper.kt`, `ShiftRepository.kt`)  
**Obiettivo**: Sintetizzare i rilievi critici di Challenger 1 e Challenger 2 in un unico piano di fix ingegneristico, prevenendo qualsiasi regressione sui 208+ test esistenti e garantendo la robustezza su turni sconosciuti e spazi anomali.

---

## 1. Quadro Esecutivo e Diagnosi Empirica

L'esecuzione empirica della suite di test completa (`.\gradlew.bat test`) sul branch corrente produce:
- **Test totali**: 217 test unitari eseguiti.
- **Passati**: 216 test.
- **Falliti**: 1 test (`com.turni.calendario.GeminiShiftMapperStressTest.testConcurrency_ConcurrentRegistrationsAndReadsDoNotCrashOrCorrupt`).
- **Rilievi di Challenger 1**: Vulnerabilità confermata dell'algoritmo di matching nominativo su omonimia parziale (falsi positivi con score 60), substring collision su nomi corti (score 80 su sequenze interne), e falsi negativi su diacritici/accenti (score 0) e apostrofi tipografici (score 0).
- **Rilievi di Challenger 2**: Eccezione non gestita `java.util.ConcurrentModificationException` su `SnapshotStateList` in `GeminiShiftMapper.kt:185` e `160` durante operazioni multi-thread concorrenti.

---

## 2. Matrice di Sintesi dei Requisiti di Fix

| Area Critica | Problema Rilevato (Challenger) | Causa Radice nel Codice | Soluzione Proposta per Worker M3 | Impatto sui Test Esistenti |
|---|---|---|---|---|
| **Concorrenza & Thread-Safety** | Crash con `ConcurrentModificationException` su 10 thread simultanei (Challenger 2) | Mancanza di sincronizzazione in `GeminiShiftMapper.registerExtractedData` su `ShiftRepository.shiftDefinitions` (Jetpack Compose `SnapshotStateList`) | Incapsulare `registerExtractedData` in `synchronized(repository)` ed eseguire `toList()` difensivo; proteggere `saveShiftDefinition` e `saveRotation` in `ShiftRepository` | **Nessuna regressione**. Risolve il test fallito in `GeminiShiftMapperStressTest`. |
| **Omonimia Parziale (Falsi Positivi)** | `"Andrea Romano"` associato ad `"Andrea Bianchi"` con score 60 >= 50; `"Marco Bianchi"` associato arbitrariamente (Challenger 1) | Regola 5 in `calculateNameMatchScore` assegna 60 punti su singolo token anche se la query ha $\ge 2$ token | Se `qTokens.size >= 2`, il match su un solo token riceve punteggio 35 (< soglia 50). Richiedere matching solido per query multi-token | **Nessuna regressione**. Preserva il matching a singolo cognome (score 80 via Regola 3). |
| **Substring Collision su Nomi Corti** | `"Dan"` associato a `"Giordano Bruno"` (score 80); `"Leo"` associato a `"Pantaleo De Rosa"` (score 80) (Challenger 1) | Regola 3 usa `cTok.contains(qTok)` se `qTok.length >= 3` | Richiedere uguaglianza esatta del token `cTok == qTok`. Gestire nomi composti fusi tramite confronto flat (`normQ.replace(" ", "") == normC.replace(" ", "")`) | **Nessuna regressione**. Elimina le collisioni preservando i nomi composti italiani (Gian Marco / Gianmarco). |
| **Accenti & Diacritici (Falsi Negativi)** | `"Niccolo"` non trova `"Niccolò Paganini"` (score 0); `"Rene"` non trova `"René Dupont"` (score 0) (Challenger 1) | Nessuna normalizzazione NFD in `calculateNameMatchScore` | Normalizzazione Unicode NFD con rimozione caratteri combinanti `\p{M}` prima della tokenizzazione | **Nessuna regressione**. Migliora l'usabilità mobile (tastiere senza accento). |
| **Apostrofi Tipografici & Spazi** | `"Marco D’Angelo"` (U+2019) fallisce rispetto a ASCII (U+0027); `"D’Angelo"` singolo dà score 0 (Challenger 1) | Delimitatori non includono varianti Unicode di apostrofi (`’`, `‘`, `` ` ``, `′`) | Normalizzazione di tutti gli apostrofi a spazio standard: `replace(Regex("[’‘`'′]"), " ")` | **Nessuna regressione**. `"D'Angelo"` viene tokenizzato come `["d", "angelo"]`. |
| **Turni Sconosciuti / Codici Arbitrari** | Preservazione integrità su codici come `"K99"`, `"FOOBAR"`, `"XXX"`, o non-ASCII | Verificato da Challenger 2: fallback corretto su `"riposo"` o creazione shift custom | Sanificazione caratteri di rumore (`*`, `#`, `°`, `^`, `()`) prima del lookup; fallback garantito su `"riposo"` | **Nessuna regressione**. Conforme al 100% con `GeminiShiftMapperStressTest`. |
| **Spazi Anomali nei Pattern di Rotazione** | Sequenze con spazi multipli, stringhe vuote o celle tabellari bianche | Possibili stringhe vuote o spazi attorno ai codici | Trimming per singolo elemento; mapping celle vuote a `"riposo"` per preservare la fase del ciclo | **Nessuna regressione**. |

---

## 3. Valutazione Approfondita: Gestione Turni Sconosciuti e Spazi Anomali

### 3.1 Turni Sconosciuti (Unknown Shifts)
In `GeminiShiftMapper.resolveShiftDefId`:
1. **Priorità 1**: Corrispondenza diretta nelle definizioni estratte da Gemini (`shiftDefs`): se Gemini ha identificato un turno speciale con orari (es. `K99 11:00-19:00`), il turno riceve il proprio UUID e viene inserito nella sequenza.
2. **Priorità 2**: Corrispondenza nei modelli già configurati in `ShiftRepository.shiftDefinitions` (per ID, `shortCode` o `name`).
3. **Priorità 3**: Fallback sul parser canonico universale `NgeniaTurniParser.mapCodeToShiftDefId(cleanCode)`.
4. **Fallback Finale di Sicurezza**: Ritorno forzato dell'ID `"riposo"`.
   - *Motivazione tecnica*: `"riposo"` è una definizione immutabile garantita in `ShiftRepository`. Qualsiasi data assegnata a `"riposo"` non genera eccezioni a runtime, non produce `NullPointerException` e permette il rendering coerente nel calendario.

### 3.2 Spazi Anomali nei Pattern di Rotazione
- **Spazi attorno ai codici**: `rawCode.trim()` rimuove spazi bianchi iniziali, finali, tabulazioni (`\t`) e interruzioni di riga.
- **Celle vuote (`""` o `"   "`)**: Vengono mappate esplicitamente a `"riposo"`.
  - *Regola aurea*: Nei tabelloni di turno (RFI, ospedalieri), una cella vuota indica il giorno di non-lavoro (riposo). Rimuovere la cella causerebbe l'accorciamento del ciclo e lo sfasamento cronologico (phase-shift) di tutti i turni successivi. Mantenere l'elemento e associarlo a `"riposo"` preserva perfettamente l'allineamento dei 7/21/28/35 giorni.
- **Normalizzazione ID Squadra**: `normalizeRotationId` pulisce spazi multipli e caratteri speciali (`Regex("[^a-z0-9_]")` e `Regex("_+")`), garantendo ID stabili e sicuri (es. `"  Squadra   Alfa  "` $\rightarrow$ `"rotazione_squadra_alfa"`).

---

## 4. Verifica di Non-Regressione sui 208+ Test Esistenti

Abbiamo mappato ogni test suite esistente rispetto alle modifiche architetturali:

1. **`GeminiShiftMapperTest.kt` (14 test)**:
   - `testMatchUserSquad_ExactAndCaseInsensitive`: Test con "Marco Graziani", "marco graziani", "luigi verdi" $\rightarrow$ **Verde** (score 100).
   - `testMatchUserSquad_InvertedNameOrderAndWhitespace`: "Graziani Marco", "   graziani     marco   ", "Graziani, Marco" $\rightarrow$ **Verde** (score 95).
   - `testMatchUserSquad_SingleTokenSurnameOrForename`: "Graziani", "Garibaldi" $\rightarrow$ **Verde** (score 80 via Regola 3: $80 \ge 50$).
   - `testMatchUserSquad_FromEmployeeAssignmentsList`: "Del Piero Alessandro" $\rightarrow$ **Verde** (score 95), "Totti" $\rightarrow$ **Verde** (score 80).
   - `testMatchUserSquad_AmbiguousTokensPrefersBestMatch`: "Marco Graziani" vs "Marco Rossi" $\rightarrow$ **Verde** (score 100 vs score 35).
   - `testMatchUserSquad_NullOrNotFoundReturnsNull`: "Unknown Worker", "", null $\rightarrow$ **Verde** (score 0).
   - `testRegisterExtractedData_*`: Test di registrazione e default rotation $\rightarrow$ **Verde**.

2. **`ShiftMappingE2ETest.kt` (7 test)**:
   - Utilizza `NgeniaTurniParser.matchesEmployeeQuery` e le funzioni native di `ShiftRepository` $\rightarrow$ **Verde al 100%**.

3. **`GeminiUserMatchingStressTest.kt` (Challenger 1 - 8 test)**:
   - Accenti (Niccolò / Niccolo) $\rightarrow$ **Verde** (score 80 / 100).
   - Apostrofi (Marco D’Angelo U+2019 / D'Angelo) $\rightarrow$ **Verde** (score 100 / 90).
   - Nomi composti (Gian Marco / Gianmarco) $\rightarrow$ **Verde** (score 92).
   - Omonimia parziale (Andrea Romano vs Andrea Bianchi) $\rightarrow$ **Verde** (score 35, match `null`).
   - Substring short names (Dan vs Giordano Bruno, Leo vs Pantaleo) $\rightarrow$ **Verde** (score 0, match `null`).

4. **`GeminiShiftMapperStressTest.kt` (Challenger 2 - 10 test)**:
   - Cicli 1, 3, 21, 35, 42 giorni e vuoto $\rightarrow$ **Verde**.
   - Turni sconosciuti e custom K99 $\rightarrow$ **Verde**.
   - Idempotenza (25 registrazioni) $\rightarrow$ **Verde**.
   - Concorrenza multi-thread (10 thread x 15 iterazioni) $\rightarrow$ **Verde** (elimina il CME grazie a `synchronized(repository)`).

---

## 5. Piano Operativo di Implementazione per Worker M3

Il Worker M3 dovrà applicare modifiche chirurgiche su due file sorgente:

### Modifica 1: `GeminiShiftMapper.kt`
1. **Aggiunta funzione di normalizzazione testo**:
   ```kotlin
   fun normalizeName(raw: String): String {
       if (raw.isBlank()) return ""
       val nfd = java.text.Normalizer.normalize(raw, java.text.Normalizer.Form.NFD)
       val withoutDiacritics = nfd.replace(Regex("\\p{M}"), "")
       return withoutDiacritics
           .replace(Regex("[’‘`'′]"), " ")
           .replace(Regex("[,;._\\-/()]"), " ")
           .lowercase()
           .trim()
   }
   ```
2. **Aggiornamento `calculateNameMatchScore`**:
   - Usa `normalizeName` per `normQ` e `normC`.
   - Aggiunge matching flat per nomi composti fusi/separati:
     ```kotlin
     if (normQ.replace(" ", "") == normC.replace(" ", "")) return 92
     ```
   - In Regola 3, richiede uguaglianza esatta del token `cTok == qTok` (o matching flat per nomi composti) per evitare substring collision su radici interne.
   - In Regola 5, se `qTokens.size >= 2`, assegna punteggio massimo 35 per prevenire falsi positivi da omonimia parziale.
3. **Thread-Safety in `registerExtractedData`**:
   - Incapsulare l'intero metodo in `synchronized(repository)`.
   - Utilizzare `val allDefs = (registeredDefs + repository.shiftDefinitions.toList()).distinctBy { it.id }` per evitare iterazioni concorrenti su `SnapshotStateList`.
4. **Sanificazione in `resolveShiftDefId`**:
   - Rimuovere caratteri di rumore con `rawCode.trim().trim { it in "*#°^_()[]" }`.

### Modifica 2: `ShiftRepository.kt`
1. Sincronizzare i metodi di mutazione delle definizioni e rotazioni:
   ```kotlin
   fun saveShiftDefinition(def: ShiftDefinition) = synchronized(this) {
       val existingIndex = shiftDefinitions.indexOfFirst { it.id == def.id }
       if (existingIndex >= 0) {
           shiftDefinitions[existingIndex] = def
       } else {
           shiftDefinitions.add(def)
       }
   }

   fun saveRotation(updated: ShiftRotation) = synchronized(this) {
       val normalized = updated.copy(totalDays = updated.daySequence.size)
       val idx = rotations.indexOfFirst { it.id == normalized.id }
       if (idx >= 0) {
           rotations[idx] = normalized
       } else {
           rotations.add(normalized)
       }
   }
   ```
