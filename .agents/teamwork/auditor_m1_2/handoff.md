# Forensic Audit Report — Milestone 1: Core Network & Security

**Work Product**: Milestone 1 Deliverables (`SecurePreferencesManager.kt`, `GeminiShiftModels.kt`, `GeminiApiClient.kt`, `AndroidManifest.xml`, `build.gradle.kts`, test suites)  
**Profile**: General Project  
**Integrity Mode**: Development Mode (inferred directly from `ORIGINAL_REQUEST.md` under timestamp `2026-10-14T15:39:24Z`)  
**Auditor**: Forensic Integrity Auditor (Instance 2 Replacement)  
**Date**: 2026-10-14  
**Verdict**: **CLEAN**

---

### Phase Results Summary

| Check | Result | Details |
|---|:---:|---|
| **Hardcoded Output Detection** | **PASS** | Nessun risultato di test cablato o output fittizio nel codice di produzione. |
| **Facade Implementation Detection** | **PASS** | Nessuna classe fantoccio o metodo stub. `GeminiApiClient` adotta reale `HttpsURLConnection`. |
| **Pre-populated Artifact Detection** | **PASS** | Nessun file di log, report fittizio o attestazione pregressa rilevata. |
| **Self-certifying / Tautological Tests** | **PASS** | Zero asserzioni tautologiche (`assert(true)` o `assertTrue(true)` assenti; tutti i controlli verificano stato effettivo). |
| **Multimodal Payload & Schema** | **PASS** | Costruzione conforme con `inline_data` Base64, prompt visivo e `response_schema` JSON strutturato. |
| **Error Handling & Security** | **PASS** | Gestione rigorosa di 400, 401/403, 429 (quota esaurita), 5xx, timeout e assenza di rete con messaggi in italiano. |
| **Behavioral Verification (Build & Test)** | **PASS** | Esecuzione pulita con `--rerun-tasks`: `BUILD SUCCESSFUL in 3m 54s` (21/21 task eseguiti, 100% verde). |

---

## 1. Observation

1. **Permessi di Rete in AndroidManifest.xml**:
   In `android-app/app/src/main/AndroidManifest.xml` alle righe 4-5 sono presenti le dichiarazioni:
   ```xml
   <uses-permission android:name="android.permission.INTERNET" />
   <uses-permission android:name="android.permission.ACCESS_NETWORK_STATE" />
   ```
   Nessun permesso superfluo o anomalo introdotto.

2. **Configurazione Build e Dipendenze in build.gradle.kts**:
   In `android-app/app/build.gradle.kts` alle righe 65-66:
   ```kotlin
   testImplementation("junit:junit:4.13.2")
   testImplementation("org.json:json:20231013")
   ```
   La dipendenza `org.json` consente l'esecuzione reale di `JSONObject` e `JSONArray` nei test unitari su JVM locale evitando l'eccezione Android `Method not mocked`.

3. **Modulo SecurePreferencesManager.kt**:
   Il file `android-app/app/src/main/java/com/turni/calendario/data/SecurePreferencesManager.kt` gestisce:
   - Persistenza protetta tramite `context.getSharedPreferences("calediario_secure_settings", Context.MODE_PRIVATE)`.
   - Fallback thread-safe in memoria con variabili `@Volatile inMemoryApiKey` e `@Volatile inMemoryUserName` quando `context == null` (fondamentale per test unitari JVM indipendenti).
   - Mascheramento sicuro dell'API Key in `getMaskedApiKey()`: produce `"••••••••"` per chiavi corte ($\le 8$ caratteri) oppure `"${key.take(6)}...${key.takeLast(3)}"` (es. `"AIzaSy...4xQ"`).
   - Nome utente predefinito `"Marco Graziani"` (`DEFAULT_USER_NAME`) con metodi `getUserName`, `saveUserName` e `clear`.

4. **Modelli Dati GeminiShiftModels.kt**:
   Il file `android-app/app/src/main/java/com/turni/calendario/data/GeminiShiftModels.kt` dichiara:
   - `GeminiShiftDefinition`: parametri `code`, `name`, `startTime`, `endTime`, `colorHex`, `isReperibilita`.
   - `GeminiSquadRotation`: parametri `squadName`, `rotationDays`, `employees`, `cycleDays`.
   - `GeminiEmployeeAssignment`: parametri `name`, `squadName`, `role`, `employeeId`, `isOnCall`, `dailyShifts`.
   - `GeminiShiftData`: contenitore radice con `shiftDefinitions`, `squads`, `notes`, `validityStart`, `validityEnd`, `employees`, `rawJson`.

5. **Client REST Multimodale GeminiApiClient.kt**:
   Il file `android-app/app/src/main/java/com/turni/calendario/data/GeminiApiClient.kt`:
   - Utilizza `HttpsURLConnection` nativa su endpoint `https://generativelanguage.googleapis.com/v1beta/models/gemini-1.5-flash:generateContent?key=$apiKey`.
   - Validazione preliminare non-vuoto per `apiKey` e `imageBase64`.
   - Metodo `buildRequestBody()` costruisce il payload JSON conforme a Google Gemini con `contents[0].parts`:
     1. Parte testuale con prompt specializzato in lingua italiana per prospetti turni.
     2. Parte multimediale `inline_data` contenente `mime_type` e `data` (Base64).
   - Sezione `generationConfig` contenente `response_mime_type: "application/json"` e `response_schema` di tipo `OBJECT` che vincola formalmente l'API a restituire `shiftDefinitions` e `squads`.
   - Gestione errori HTTP: 400 (Bad Request), 401/403 (Invalid Key / Access Denied con `SecurityException`), 429 (Quota exceeded con `IllegalStateException` e testo esplicativo in italiano), 5xx (`IOException`), `SocketTimeoutException` e `UnknownHostException`.
   - Metodo `parseGeminiResponse()` robusto: supporta la decodifica della risposta Gemini candidates (`candidates[0].content.parts[0].text`), blocchi markdown (` ```json ... ``` `), JSON grezzi e fallback chiavi (`shiftHours`, `teams`, `rotationSequence`).

6. **Assenza di Scorciatoie, Facade o Hardcoded Outputs**:
   - Grep search estesa su parole chiave `dummy`, `fake`, `mock`, `stub`, `TODO`, `FIXME`, `NotImplementedError` in `android-app/app/src/main/java/com/turni/calendario/data/`: 0 occorrenze.
   - Grep search su asserzioni tautologiche (`assert(true)`, `assertTrue(true)`, `assertEquals(x, x)` statici) in `android-app/app/src/test/`: 0 occorrenze.
   - Ispezione file di log/output pregressi: presenti solo artefatti standard di build Gradle intermedi (`.dex`, `.lock`), nessuna attestazione o finto log pre-generato.

7. **Esecuzione Empirica della Suite di Test**:
   Eseguito in modo del tutto indipendente con flag `--rerun-tasks` per forzare l'esecuzione da zero di tutti i test senza cache:
   ```cmd
   cmd /c "set JAVA_HOME=C:\Program Files\Android\Android Studio\jre&& gradlew.bat testDebugUnitTest --rerun-tasks"
   ```
   Output verbatim:
   ```
   > Task :app:compileDebugKotlin
   > Task :app:compileDebugJavaWithJavac
   > Task :app:compileDebugUnitTestKotlin
   > Task :app:testDebugUnitTest

   BUILD SUCCESSFUL in 3m 54s
   21 actionable tasks: 21 executed
   ```
   Tutti i file di report XML in `android-app/app/build/test-results/testDebugUnitTest/` confermano:
   - `GeminiNetworkAndSecurityTest`: 8 test, 0 failures, 0 errors, 0 skipped.
   - `GeminiApiJsonTest`: 16 test, 0 failures, 0 errors, 0 skipped.
   - `SecurePreferencesManagerTest`: 19 test, 0 failures, 0 errors, 0 skipped.
   - `GeminiConcurrencyAndBoundaryTest`: 13 test, 0 failures, 0 errors, 0 skipped.
   - `GeminiApiClientStressTest`: 14 test, 0 failures, 0 errors, 0 skipped.
   - `GeminiRealWorldWorkloadTest`: 5 test, 0 failures, 0 errors, 0 skipped.
   - Suite preesistenti (`NgeniaTurniParserTest`, `ShiftMappingE2ETest`, `ShiftRepositoryTest`): 100% superati.

---

## 2. Logic Chain

1. In base all'osservazione 1 e 2, la configurazione di progetto (`AndroidManifest.xml` e `build.gradle.kts`) possiede tutti i prerequisiti necessari e sufficienti per la connettività di rete e il testing su JVM locale.
2. In base all'osservazione 3, `SecurePreferencesManager` implementa un meccanismo autentico di persistenza conforme ai vincoli di sicurezza Android (`MODE_PRIVATE`) e fornisce un fallback in memoria pulito per l'esecuzione dei test, gestendo il mascheramento delle credenziali secondo le specifiche.
3. In base all'osservazione 4, i modelli `GeminiShiftModels` rispecchiano le strutture dati necessarie per supportare i milestone successivi (M2, M3, M4).
4. In base all'osservazione 5, `GeminiApiClient` implementa una comunicazione REST reale, senza simulazioni fittizie: il payload include l'immagine in Base64, lo schema JSON di Gemini Flash è formulato correttamente e gli stati di errore HTTP (incluso il 429) generano eccezioni mirate con messaggi in lingua italiana conformi ai requisiti.
5. In base all'osservazione 6, non è presente alcuna scorciatoia, classe fantoccio o trucco nei test (nessun `assert(true)`).
6. In base all'osservazione 7, l'esecuzione empirica indipendente di tutti i test da sorgente con `--rerun-tasks` ha prodotto `BUILD SUCCESSFUL` con 0 fallimenti e 0 errori.
7. Pertanto, la totalità dei controlli forensi è superata e il verdetto è **CLEAN**.

---

## 3. Caveats

- **Test di Rete Live con Credenziali Cloud Reali**: I test unitari automatizzati validano la costruzione corretta del pacchetto HTTPS, i timeout, i filtri di sicurezza e il parsing JSON delle risposte reali simulate del modello. Le chiamate HTTP verso i server Google Gemini con una chiave API reale attiva non sono eseguite nella suite CI/CD locale per evitare dipendenze da credenziali di fatturazione esterne o assenza temporanea di connettività Internet; la conformità del payload è stata verificata tramite analisi strutturale del JSON generato e riscontro con le specifiche ufficiali di Google AI Studio.

---

## 4. Conclusion

Il lavoro consegnato da Worker M1 per la Milestone 1 (Core Network & Security) è integro, completo e conforme a tutte le specifiche e linee guida di progetto. Non sussiste alcuna violazione di integrità.

**Verdetto Finale**: **CLEAN**

---

## 5. Verification Method

Per riprodurre in modo indipendente la verifica:
1. Eseguire l'intera suite di test unitari da riga di comando forzando la ri-esecuzione di tutti i task:
   ```cmd
   cmd /c "set JAVA_HOME=C:\Program Files\Android\Android Studio\jre&& gradlew.bat testDebugUnitTest --rerun-tasks"
   ```
   *Condizione di invalidazione*: Qualsiasi errore di compilazione o fallimento dei test asserito nei file XML in `android-app/app/build/test-results/testDebugUnitTest/`.
2. Ispezionare il codice sorgente per confermare l'assenza di scorciatoie:
   - `android-app/app/src/main/java/com/turni/calendario/data/SecurePreferencesManager.kt`
   - `android-app/app/src/main/java/com/turni/calendario/data/GeminiShiftModels.kt`
   - `android-app/app/src/main/java/com/turni/calendario/data/GeminiApiClient.kt`
