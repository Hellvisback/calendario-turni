# Handoff Report — Challenger 1 Milestone 3: Shift Extraction & Domain Mapping

**Role**: Challenger 1 (User Name Matching & Edge Case Stress)  
**Verdict**: **REJECT**

---

## 1. Observation

L'analisi empirica e l'esecuzione della suite di stress test `GeminiUserMatchingStressTest.kt` su `GeminiShiftMapper.kt` (righe 112–141 e 338–383) hanno evidenziato precise vulnerabilità e comportamenti anomali dell'algoritmo di matching.

### File ispezionati ed eseguiti
- `android-app/app/src/main/java/com/turni/calendario/data/GeminiShiftMapper.kt`
- `android-app/app/src/test/java/com/turni/calendario/GeminiUserMatchingStressTest.kt`

### Output empirico dei test eseguiti via Gradle (`.\gradlew.bat test`)
```text
[ACCENT TEST 1A] 'Niccolo' vs 'Niccolò Paganini' -> score: 0, match: null
[ACCENT TEST 1B] 'Niccolo Paganini' vs 'Niccolò Paganini' -> score: 60, match: Squadra A
[ACCENT TEST 1C] 'Rene' vs 'René Dupont' -> score: 0, match: null

[COMPOUND TEST 3A] 'Gian Marco Rossi' vs 'Gianmarco Rossi' -> score: 90, match: Squadra Alfa
[COMPOUND TEST 3B] 'Gianmarco Rossi' vs 'Gian Marco Rossi' -> score: 85, match: Squadra Gamma

[HOMONYMY TEST 5A] 'Andrea Romano' vs 'Andrea Bianchi' -> score: 60, matched squad: Squadra 1
[HOMONYMY TEST 5B] 'Marco Bianchi' against 'Marco Rossi' (score 60) vs 'Luca Bianchi' (score 60) -> matched: Squadra Rossi

[APOSTROPHE TEST 2B] 'Marco D’Angelo' (U+2019) vs 'Marco D'Angelo' (ASCII) -> score: 60, match: Squadra 1
[APOSTROPHE TEST 2C] Single surname 'D’Angelo' (U+2019) vs candidate 'Marco D'Angelo' -> score: 0, match: null
[APOSTROPHE TEST 2D] Space after apostrophe 'Marco D' Angelo' vs 'Marco D'Angelo' -> score: 60, match: Squadra 1

[SUBSTRING TEST 5D] 'Dan' in 'Giordano Bruno' -> score: 80
[SUBSTRING TEST 5E] 'Leo' in 'Pantaleo De Rosa' -> score: 80

[PREFIX TEST 3D] 'De Luca' vs 'Antonio De Luca' -> score: 90, match: Squadra DeLuca
```

---

## 2. Logic Chain

1. **Vulnerabilità 1 — Falsi Positivi Critici su Omonimia Parziale (Test 5A, 5B)**:
   - In `GeminiShiftMapper.kt`:
     - Linea 377–381: La Regola 5 di `calculateNameMatchScore` assegna un punteggio fisso di `60` a qualsiasi corrispondenza parziale di un singolo token di almeno 3 caratteri (`anyTokenMatch`).
     - Linea 140: La soglia di accettazione di `matchUserSquad` è fissata a `bestScore >= 50`.
   - Conseguenza: Se un utente inserisce nome e cognome completi (es. `"Andrea Romano"`, persona assente o nuova in azienda), e nel turno aziendale è presente `"Andrea Bianchi"` in Squadra 1, la Regola 5 rileva la corrispondenza del token `"andrea"`, restituisce punteggio 60 e assegna l'utente a Squadra 1.
   - Analogo problema nel Test 5B: l'utente `"Marco Bianchi"`, con `"Marco Rossi"` in Squadra Rossi e `"Luca Bianchi"` in Squadra Bianchi, riceve punteggio 60 per entrambe le squadre e viene assegnato arbitrariamente alla prima iterata dal loop (`Squadra Rossi`).
   - In un contesto reale con decine di turnisti con nomi comuni (Marco, Andrea, Francesco, Mario, Giuseppe), chiunque inserisca un nominativo non registrato o con cognome dissimile viene erroneamente agganciato alla squadra di un collega omonimo nel nome.

2. **Vulnerabilità 2 — Falsi Positivi per Substring Collision su Nomi Corti (Test 5D, 5E)**:
   - In `GeminiShiftMapper.kt`, linea 361–366 (Regola 3):
     `cTokens.any { cTok -> cTok == qTok || (qTok.length >= 3 && cTok.contains(qTok)) }`
   - Se la query è una parola corta di 3 o 4 lettere (es. nomi o diminutivi italiani come `"Dan"`, `"Leo"`, `"San"`, `"Ada"`, `"Teo"`, `"Edo"`), la condizione `cTok.contains(qTok)` valuta `true` per qualsiasi parola che contenga tale sequenza interna (`"Giordano"` contiene `"dan"` -> punteggio 80; `"Pantaleo"` contiene `"leo"` -> punteggio 80).
   - L'utente `"Dan"` viene quindi associato a `"Giordano Bruno"` con confidenza dell'80%.

3. **Vulnerabilità 3 — Falsi Negativi su Accenti / Segni Diacritici (Test 1A, 1C)**:
   - In `GeminiShiftMapper.kt`, linea 347: `normQ = q.replace(Regex("[,;._-]"), " ").lowercase().trim()`.
   - Nessuna normalizzazione NFD o rimozione diacritici è presente.
   - Quando l'utente inserisce `"Niccolo"` (senza accento, tipico di chi digita su tastiera mobile senza premere a lungo) o `"Rene"` rispetto al tabellone `"Niccolò Paganini"` o `"René Dupont"`, il confronto per nome singolo fallisce con `score: 0`, e l'app risponde `null` (nessuna corrispondenza trovata).

4. **Vulnerabilità 4 — Falsi Negativi su Apostrofo Tipografico vs ASCII (Test 2C)**:
   - Su Android (es. tastiera Gboard italiana) l'apostrofo generato è spesso il carattere Unicode U+2019 (`’`, right single quotation mark), mentre nei PDF/documenti OCR può essere U+0027 (`'`) o viceversa.
   - La regex di normalizzazione non gestisce le varianti di apostrofo né separa i token con apostrofo (es. `D'Angelo` vs `D’Angelo`).
   - La ricerca per singolo cognome `"D’Angelo"` fallisce con `score: 0` rispetto a `"Marco D'Angelo"`.

5. **Punti di Forza Verificati**:
   - I nomi composti fusi/separati (`"Gian Marco Rossi"` vs `"Gianmarco Rossi"`) funzionano correttamente con score 90 e 85.
   - I cognomi con prefissi multipli (`"De Luca"`, `"Van Der Bellen"`) vengono riconosciuti sia con nome completo che invertito.
   - Caratteri estremi, stringhe di 10.000 caratteri, emoji e tentativi di SQL injection non generano crash o eccezioni non gestite.

---

## 3. Caveats

- I test sono stati eseguiti su JVM locale tramite la suite Android Unit Test.
- Non è stato testato l'accoppiamento con matricole aziendali numeriche, in quanto non previsto dalle specifiche correnti.

---

## 4. Conclusion

**Verdetto: REJECT.**

L'algoritmo attuale in `GeminiShiftMapper.matchUserSquad` presenta un tasso inaccettabile di **falsi positivi su omonimia parziale** (assegna un turno arbitrario a utenti che condividono solo il nome proprio) e di **falsi negativi su vocali accentate e apostrofi tipografici**.

### Correzioni chirurgiche raccomandate per Worker M3:
1. **Risoluzione Falsi Positivi su Omonimia Parziale**:
   - Modificare la Regola 5 in `calculateNameMatchScore`: se la query contiene più di un token (`qTokens.size >= 2`), il match su un singolo token non deve assegnare 60 punti (deve assegnare un punteggio < 50, ad es. 35 o 40), oppure richiedere che almeno il cognome o la maggioranza dei token (`matchedTokens >= 2` o rapporto `>= 0.7`) corrisponda.
   - In alternativa, alzare la soglia di assegnazione automatica in `matchUserSquad` da `>= 50` a `>= 70`, riservando l'auto-selezione della squadra solo a corrispondenze solide (matching di tutti i token di ricerca).
2. **Risoluzione Substring Collision**:
   - Nella Regola 3 (`qTok.length >= 3 && cTok.contains(qTok)`), disabilitare il substring indiscriminato su parole corte o richiedere `qTok.length >= 5`, oppure verificare la corrispondenza solo come prefisso (`cTok.startsWith(qTok)`).
3. **Normalizzazione Diacritica**:
   - Applicare la normalizzazione Unicode prima del token matching:
     ```kotlin
     val stripAccents = { text: String ->
         java.text.Normalizer.normalize(text, java.text.Normalizer.Form.NFD)
             .replace(Regex("\\p{M}"), "")
     }
     ```
4. **Normalizzazione Apostrofi**:
   - Mappare gli apostrofi tipografici (`’`, `‘`, `` ` ``) a spazio o apostrofo standard ASCII (`'`), ad es. `replace(Regex("[’‘`']"), " ")`. In questo modo `"D'Angelo"` viene tokenizzato come `["d", "angelo"]`, garantendo matching perfetto con qualsiasi variante.

---

## 5. Verification Method

Eseguire la suite unit test completa:
```powershell
$env:JAVA_HOME = "C:\Program Files\Android\Android Studio\jre"; .\gradlew.bat test
```
File di test dedicato all'ispezione empirica:
`android-app/app/src/test/java/com/turni/calendario/GeminiUserMatchingStressTest.kt`
Condizione di successo post-fix: nessun falso positivo (es. `"Andrea Romano"` non deve matchare `"Andrea Bianchi"` con score >= 50, e `"Dan"` non deve matchare `"Giordano Bruno"`).
