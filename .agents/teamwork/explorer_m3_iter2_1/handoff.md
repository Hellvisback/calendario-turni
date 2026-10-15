# Handoff Report — Explorer 1 (Milestone 3: Iteration 2 - Root Cause Analysis & Fix Strategy per User Name Matching)

**Role**: Explorer 1  
**Handoff Type**: Hard (Investigation & Strategy Complete)  
**Destination**: Parent Orchestrator / Worker Milestone 3  

---

## 1. Observation

L'analisi empirica e l'ispezione statica e dinamica del codice di `GeminiShiftMapper.kt` e della test suite `GeminiUserMatchingStressTest.kt` evidenziano quattro comportamenti anomali documentati:

### File Ispezionati
- `android-app/app/src/main/java/com/turni/calendario/data/GeminiShiftMapper.kt` (righe 112–141 e 338–383)
- `android-app/app/src/test/java/com/turni/calendario/GeminiUserMatchingStressTest.kt` (righe 32–266)
- `.agents/teamwork/challenger_m3_1/handoff.md` (osservazioni empiriche Challenger 1)
- `.agents/teamwork/reviewer_m3_2/handoff.md` (revisione e stress di concorrenza Reviewer 2)

### Dati Verbatim Osservati
1. **Omonimia Parziale**:
   - `GeminiShiftMapper.kt:380`: `if (anyTokenMatch) return 60` dove `anyTokenMatch` controlla se almeno un token di lunghezza >= 3 combacia.
   - `GeminiShiftMapper.kt:140`: `return if (bestScore >= 50) bestSquadName else null`.
   - Output Test 5A: `[HOMONYMY TEST 5A] 'Andrea Romano' vs 'Andrea Bianchi' -> score: 60, matched squad: Squadra 1`.
   - Output Test 5B: `[HOMONYMY TEST 5B] 'Marco Bianchi' against 'Marco Rossi' (score 60) vs 'Luca Bianchi' (score 60) -> matched: Squadra Rossi`.
2. **Substring Collision**:
   - `GeminiShiftMapper.kt:362`: `cTokens.any { cTok -> cTok == qTok || (qTok.length >= 3 && cTok.contains(qTok)) }`.
   - Output Test 5D: `[SUBSTRING TEST 5D] 'Dan' in 'Giordano Bruno' -> score: 80`.
   - Output Test 5E: `[SUBSTRING TEST 5E] 'Leo' in 'Pantaleo De Rosa' -> score: 80`.
3. **Accenti e Diacritici**:
   - `GeminiShiftMapper.kt:347`: `val normQ = q.replace(Regex("[,;._-]"), " ").lowercase().trim()`.
   - Output Test 1A: `[ACCENT TEST 1A] 'Niccolo' vs 'Niccolò Paganini' -> score: 0, match: null`.
   - Output Test 1C: `[ACCENT TEST 1C] 'Rene' vs 'René Dupont' -> score: 0, match: null`.
4. **Apostrofi Tipografici**:
   - La regex `[,;._-]` non comprende caratteri come `'` (U+0027) né `’` (U+2019).
   - Output Test 2C: `[APOSTROPHE TEST 2C] Single surname 'D’Angelo' (U+2019) vs candidate 'Marco D'Angelo' -> score: 0, match: null`.
   - Output Test 2D: `[APOSTROPHE TEST 2D] Space after apostrophe 'Marco D' Angelo' vs 'Marco D'Angelo' -> score: 60, match: Squadra 1`.

---

## 2. Logic Chain

1. **Catena Logica Omonimia Parziale (Osservazione 1 -> Causa -> Risoluzione)**:
   - Se la query contiene più token (`qTokens.size >= 2`), il match su un singolo token comune (es. "Andrea" o "Marco") rappresenta solo una coincidenza parziale (rapporto <= 0.5).
   - Poiché la Regola 5 attribuisce 60 punti e la soglia in `matchUserSquad` è `>= 50`, qualsiasi nominativo esterno con nome proprio comune viene forzatamente associato alla squadra del primo collega iterato.
   - **Risoluzione**: Se `qTokens.size >= 2` e solo 1 token combacia, lo score deve essere ridotto a `30`. Con soglia `50`, `bestScore >= 50` valuta `false` e restituisce `null`, eliminando al 100% i falsi positivi.

2. **Catena Logica Substring Collision (Osservazione 2 -> Causa -> Risoluzione)**:
   - La clausola `cTok.contains(qTok)` per token di lunghezza >= 3 valuta `true` per qualsiasi infisso interno (es. `"dan"` in `"giordano"`, `"leo"` in `"pantaleo"`).
   - Poiché `qTokens` contiene un solo token, `allQueryInCandidate` valuta `true` e assegna 80 punti.
   - **Risoluzione**: Rimuovere `cTok.contains(qTok)`. Il matching token deve essere esatto (`cTokens.contains(qTok)`) oppure consentire unicamente la fusione di token adiacenti (es. `cTokens[i] + cTokens[i+1] == qTok`). I nomi composti (`"Gian Marco"` vs `"Gianmarco"`) vengono gestiti con uguaglianza a spazi rimossi (`normQ.replace(" ", "") == normC.replace(" ", "")`), garantendo score 95 senza alcuna collisione a sottostringa.

3. **Catena Logica Segni Diacritici (Osservazione 3 -> Causa -> Risoluzione)**:
   - In Unicode, `ò` (U+00F2) ed `e` con accento `é` (U+00E9) sono punti di codice distinti da `o` (U+006F) ed `e` (U+0065).
   - Senza normalizzazione NFD, le stringhe con e senza accento non coincidono.
   - **Risoluzione**: Introdurre la pipeline di normalizzazione con `java.text.Normalizer.normalize(text, Normalizer.Form.NFD).replace(Regex("\\p{M}"), "")`. In questo modo `"Niccolo"` e `"Niccolò"` diventano entrambi `"niccolo"`, raggiungendo score 80 (singolo token in candidato) o 100 (nome completo).

4. **Catena Logica Apostrofi Tipografici (Osservazione 4 -> Causa -> Risoluzione)**:
   - Le tastiere Android moderne (Gboard) emettono U+2019 (`’`) di default, mentre OCR e testi estratti da PDF contengono spesso U+0027 (`'`) o spazi aggiuntivi (`"D' Angelo"`).
   - Mappando tutte le varianti di apostrofo e virgoletta (`['’‘`´ʼ‛\"]`) a spazio `" "`, `"D'Angelo"`, `"D’Angelo"` e `"D' Angelo"` producono tutti l'identica sequenza di token `["d", "angelo"]`.
   - Di conseguenza, la ricerca per cognome `"D’Angelo"` produce due token che combaciano perfettamente con i token di `"Marco D'Angelo"`, ottenendo score 90 e matchando `Squadra 1`.

---

## 3. Caveats

- **Nessuna matricola aziendale**: L'algoritmo opera esclusivamente su nominativi testuali (come richiesto dalle specifiche correnti); non è presente un campo ID/matricola numerica nei modelli multimodali.
- **Ambiguità su omonimia completa**: Se due lavoratori in squadre diverse hanno esattamente lo stesso nome e cognome (es. due diversi "Mario Rossi"), l'algoritmo restituisce la prima squadra trovata con score massimo. Questo comportamento è conforme alla specifica attuale e al test 5C.
- **Ambito Investigation**: Nessun file sorgente applicativo in `android-app/` è stato modificato in questo turno (piena aderenza al vincolo Read-Only Investigation).

---

## 4. Conclusion

L'indagine tecnica è conclusa con successo. Le vulnerabilità di matching sono state isolate e ricondotte a difetti specifici e documentati.  
È stata formulata una raccomandazione chirurgica pronta all'implementazione che:
1. Elimina i falsi positivi su omonimia parziale abbassando lo score di match a singolo token a `30` (inferiore alla soglia di attivazione `50`).
2. Elimina le substring collision rimuovendo `contains()` e sostituendolo con uguaglianza compattata per nomi composti e token matching esatto.
3. Risolve i falsi negativi sugli accenti tramite scomposizione `Normalizer.Form.NFD` e rimozione `\p{M}`.
4. Unifica gli apostrofi tipografici (U+2019) e ASCII (U+0027) a spazio, supportando varianti OCR e tastiere mobili.
5. Integra la nota di sincronizzazione thread-safe per `registerExtractedData` per soddisfare i rilievi di Reviewer 2.

Il report dettagliato è disponibile in:  
`c:\Users\marco\Antigravity Progetti\Calediario Turni\.agents\teamwork\explorer_m3_iter2_1\analysis.md`

---

## 5. Verification Method

Per verificare in modo indipendente le raccomandazioni una volta applicate dal Worker:

1. **Verifica Suite Stress Test Matching**:
   ```powershell
   $env:JAVA_HOME = "C:\Program Files\Android\Android Studio\jre"
   .\gradlew.bat test --tests "com.turni.calendario.GeminiUserMatchingStressTest"
   ```
   **Condizione di successo**: Tutti i test passano; le stampe su console confermano:
   - Test 1A: `'Niccolo' vs 'Niccolò Paganini'` -> score: 80, match: Squadra A.
   - Test 2C: `'D’Angelo' vs 'Marco D'Angelo'` -> score: 90, match: Squadra 1.
   - Test 5A: `'Andrea Romano' vs 'Andrea Bianchi'` -> score: 30, match: null.
   - Test 5D: `'Dan' in 'Giordano Bruno'` -> score: 0.

2. **Verifica Suite Unit Test Globale**:
   ```powershell
   .\gradlew.bat testDebugUnitTest --rerun-tasks
   ```
   **Condizione di successo**: `BUILD SUCCESSFUL`, 217/217 test passati (100% verde).

3. **Condizione di Invalidazione**: Se un test restituisce match per `"Andrea Romano"` su `"Andrea Bianchi"` o score >= 50 per `"Dan"` su `"Giordano Bruno"`, la correzione è invalida.
