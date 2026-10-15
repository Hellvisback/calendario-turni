# DISPATCH — Worker M3 (Iteration 2: Refinement & Stress Test Resolution)

## Mission
Applicare le correzioni chirurgiche a `android-app/app/src/main/java/com/turni/calendario/data/GeminiShiftMapper.kt` identificate dai 3 Explorer per far passare al 100% verde sia `GeminiUserMatchingStressTest.kt` che `GeminiShiftMapperStressTest.kt` oltre a tutti i 208 test esistenti.

## MANDATORY INTEGRITY WARNING
DO NOT CHEAT. All implementations must be genuine. DO NOT hardcode test results, create dummy/facade implementations, or circumvent the intended task. A teamwork_preview_auditor will independently verify your work. Integrity violations WILL be detected and your work WILL be rejected.

## File di proprietà esclusiva di scrittura
- `android-app/app/src/main/java/com/turni/calendario/data/GeminiShiftMapper.kt`
- (facoltativo se necessario per helper di test) `android-app/app/src/test/java/com/turni/calendario/GeminiShiftMapperTest.kt`

## Specifiche di Implementazione
1. **Thread-Safety & Idempotenza**:
   - In `registerExtractedData(repository, data, targetUserName)`: incapsulare l'intero blocco in `synchronized(repository) { ... }`.
   - Utilizzare `.toList()` sulle collezioni Compose `repository.shiftDefinitions` e `repository.rotazioni` per le letture e iterazioni interne, evitando `ConcurrentModificationException` su `SnapshotStateList`.

2. **Matching Nomi e Punteggi (`calculateNameMatchScore` & `matchUserSquad`)**:
   - Normalizzazione diacritica: usare `java.text.Normalizer.normalize(text, java.text.Normalizer.Form.NFD).replace(Regex("\\p{M}"), "")`.
   - Normalizzazione apostrofi: sostituire apostrofi tipografici (U+2019 `’`, `‘`, `` ` ``, `'`) con spazi `replace(Regex("[’‘`']"), " ")`.
   - Prevenzione falsi positivi su omonimia parziale:
     - Se `qTokens.size >= 2`, un match su un solo token (es. "Andrea" in "Andrea Romano" vs "Andrea Bianchi") NON deve superare la soglia di assegnazione: assegna punteggio massimo 35 (< 50).
     - Se tutti i token della query corrispondono a token del candidato: punteggio 90 (o 100 per exact match).
     - Se la query ha un solo token (`qTokens.size == 1`, es. ricerca per solo cognome "Bianchi" o "Rossi") ed è uguale a un token del candidato: assegna punteggio 75 (>= 50).
   - Prevenzione collisioni substring su token corti:
     - Disabilitare `cTok.contains(qTok)` per token corti; consentire matching solo se `qTok == cTok` oppure se `qTok.length >= 4 && cTok.startsWith(qTok)`. In questo modo "Dan" o "Leo" non matchano "Giordano" o "Pantaleo".
   - In `matchUserSquad`:
     - Verificare che il miglior punteggio sia `>= 50`.
     - In caso di ambiguità (due squadre con lo stesso punteggio massimo >= 50 per match parziali), non assegnare arbitrariamente ma restituire null o prioritizzare la corrispondenza più solida.

3. **Pattern di Rotazione**:
   - In `mapToRotations`: separare i token con `split(Regex("[,;\\s]+")).filter { it.isNotBlank() }`.
   - Mantenere il fallback canonico a `"riposo"` per codici turno non riconosciuti.

## Verifica Obbligatoria
Esegui:
```powershell
$env:JAVA_HOME = "C:\Program Files\Android\Android Studio\jre"; .\gradlew.bat test
$env:JAVA_HOME = "C:\Program Files\Android\Android Studio\jre"; .\gradlew.bat assembleDebug
```
Entrambi i comandi DEVONO terminare con `BUILD SUCCESSFUL` e 100% dei test verdi (inclusi GeminiUserMatchingStressTest e GeminiShiftMapperStressTest).
Scrivi il tuo handoff report in `c:\Users\marco\Antigravity Progetti\Calediario Turni\.agents\teamwork\worker_m3_iter2\handoff.md` e invia un messaggio di completamento con send_message.


## 2026-10-14T20:54:55Z
Sei Worker M3 Iteration 2 (Domain Mapping & Concurrency Specialist).
Il tuo working directory dedicato è:
c:\Users\marco\Antigravity Progetti\Calediario Turni\.agents\teamwork\worker_m3_iter2

Devi leggere OBBLIGATORIAMENTE il file dei requisiti originali prima di qualsiasi azione:
c:\Users\marco\Antigravity Progetti\Calediario Turni\.agents\teamwork\ORIGINAL_REQUEST.md

Leggi anche il piano globale e le istruzioni di dispatch:
- c:\Users\marco\Antigravity Progetti\Calediario Turni\PROJECT.md
- c:\Users\marco\Antigravity Progetti\Calediario Turni\.agents\teamwork\worker_m3_iter2\DISPATCH.md
- c:\Users\marco\Antigravity Progetti\Calediario Turni\.agents\teamwork\challenger_m3_1\handoff.md
- c:\Users\marco\Antigravity Progetti\Calediario Turni\.agents\teamwork\challenger_m3_2\handoff.md

MANDATORY INTEGRITY WARNING:
DO NOT CHEAT. All implementations must be genuine. DO NOT hardcode test results, create dummy/facade implementations, or circumvent the intended task. A teamwork_preview_auditor will independently verify your work. Integrity violations WILL be detected and your work WILL be rejected.

File di cui hai la proprietà esclusiva di scrittura:
- android-app/app/src/main/java/com/turni/calendario/data/GeminiShiftMapper.kt
- android-app/app/src/test/java/com/turni/calendario/GeminiShiftMapperTest.kt

Implementa le modifiche chirurgiche dettagliate nel tuo DISPATCH.md in GeminiShiftMapper.kt:
1. Thread-safety: synchronized(repository) in registerExtractedData e snapshot .toList() delle liste per azzerare ConcurrentModificationException.
2. Normalizzazione avanzata: Normalizer.Form.NFD per accenti e uniformazione apostrofi tipografici (U+2019, ‘, ’, `) a spazi.
3. Matching punteggi: nessun falso positivo su omonimie (se query composta >= 2 token, singolo match vale max 35 < 50; query 1 token vale 75 >= 50; tutti i token valgono 90-100).
4. Substring collision: disabilitare contains() su token corti (< 4 chars) o richiedere startsWith().
5. Pattern rotazione: separazione regex con split(Regex("[,;\\s]+")).filter { it.isNotBlank() }.

VERIFICA OBBLIGATORIA:
Esegui la compilazione e la suite completa di test in android-app/:
powershell: $env:JAVA_HOME = "C:\Program Files\Android\Android Studio\jre"; .\gradlew.bat test
powershell: $env:JAVA_HOME = "C:\Program Files\Android\Android Studio\jre"; .\gradlew.bat assembleDebug
Tutti i test (inclusi GeminiUserMatchingStressTest e GeminiShiftMapperStressTest) DEVONO terminare con BUILD SUCCESSFUL.

Scrivi handoff.md in c:\Users\marco\Antigravity Progetti\Calediario Turni\.agents\teamwork\worker_m3_iter2\handoff.md e invia un messaggio di completamento a me via send_message.
