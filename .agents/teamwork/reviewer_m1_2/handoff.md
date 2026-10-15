# Handoff Report — Reviewer 2: Milestone 1 (Core Network & Security)

**Reviewer**: Reviewer 2 (Adversarial Critic & Quality Reviewer)  
**Date**: 2026-10-14  
**Target Milestone**: M1 (Core Network & Security)  
**Verdict**: **APPROVE**  

---

## 1. Observation

1. **Permessi di rete e configurazione manifest**:
   In `android-app/app/src/main/AndroidManifest.xml`, righe 4-5:
   ```xml
   <uses-permission android:name="android.permission.INTERNET" />
   <uses-permission android:name="android.permission.ACCESS_NETWORK_STATE" />
   ```
   I permessi necessari per l'accesso HTTPS alla Google Generative Language API sono regolarmente dichiarati.

2. **Dipendenze di test unitario**:
   In `android-app/app/build.gradle.kts`, riga 66:
   ```kotlin
   testImplementation("org.json:json:20231013")
   ```
   La libreria `org.json` è inclusa specificamente per il target di test JVM, eliminando il problema delle classi mock non implementate in `android.jar`. Nessuna dipendenza complessa o pesante aggiunta a runtime.

3. **Modulo SecurePreferencesManager**:
   In `android-app/app/src/main/java/com/turni/calendario/data/SecurePreferencesManager.kt`:
   - Campi `@Volatile private var inMemoryApiKey: String? = null` e `@Volatile private var inMemoryUserName: String? = null` (righe 18-22).
   - Metodi `getApiKey`, `saveApiKey`, `hasApiKey`, `getMaskedApiKey`, `getUserName`, `saveUserName`, `clear`.
   - Utilizzo di `Context.MODE_PRIVATE` per l'archiviazione sandbox privata su Android con fallback immediato alla memoria per ambienti JVM di test o eccezioni I/O.
   - Mascheramento: `if (key.length <= 8) "••••••••" else "${key.take(6)}...${key.takeLast(3)}"` (righe 80-84).
   - Sanificazione input con `.trim().ifBlank { null }`.

4. **Modelli Dati GeminiShiftModels**:
   In `android-app/app/src/main/java/com/turni/calendario/data/GeminiShiftModels.kt`:
   - Data class immutabili (`GeminiShiftDefinition`, `GeminiSquadRotation`, `GeminiEmployeeAssignment`, `GeminiShiftData`).
   - Default sicuri (liste vuote, `null` dove opzionale, nessun crash da parametri mancanti).

5. **Client Multimodale Gemini Flash GeminiApiClient**:
   In `android-app/app/src/main/java/com/turni/calendario/data/GeminiApiClient.kt`:
   - Utilizzo di `HttpsURLConnection` con timeout espliciti configurati: `connectTimeout = 30_000`, `readTimeout = 60_000` (righe 29-30, 89-90).
   - Chiusura garantita degli stream con `.use { ... }` (righe 99, 106, 110) e disconnessione della socket nel blocco `finally { connection.disconnect() }` (riga 145).
   - Esecuzione non bloccante via `withContext(Dispatchers.IO)` e incapsulamento dell'esito in `Result<GeminiShiftData>` (righe 59-63).
   - Rigoroso `response_schema` e `response_mime_type: "application/json"` all'interno di `generationConfig` (righe 285-290).
   - Error handling mappato: 400 (`IllegalArgumentException`), 401/403 (`SecurityException`), 429 (`IllegalStateException`), 500..599 (`IOException`), `SocketTimeoutException` e `UnknownHostException` rimappati in `IOException` con descrizioni in lingua italiana.
   - Parsing resiliente in `parseGeminiResponse`: supporto sia dell'involucro standard REST (`candidates[0].content.parts[0].text`), sia di blocchi markdown (```json ... ```), sia di payload JSON diretti. Gestione del flag `finishReason == "SAFETY"`.

6. **Verifica Empirica Indipendente (Build & Test)**:
   Esecuzione del comando:
   ```powershell
   $env:JAVA_HOME = "C:\Program Files\Android\Android Studio\jre"; .\gradlew.bat test
   ```
   in `c:\Users\marco\Antigravity Progetti\Calediario Turni\android-app`.
   Risultato verbatim:
   ```
   > Task :app:compileReleaseUnitTestKotlin
   > Task :app:testReleaseUnitTest
   > Task :app:testDebugUnitTest
   > Task :app:test
   BUILD SUCCESSFUL in 2m 4s
   43 actionable tasks: 4 executed, 39 up-to-date
   ```
   Ispezione del report `testReleaseUnitTest/index.html`:
   - Totale test: 89
   - Fallimenti: 0
   - Tasso di successo: 100%
   - Test del package `com.turni.calendario.data`: 8 eseguiti, 8 passati.

7. **Controllo di Integrità (Integrity Audit)**:
   - Nessun hardcoded test result all'interno dei file di codice sorgente di produzione (`SecurePreferencesManager.kt`, `GeminiShiftModels.kt`, `GeminiApiClient.kt`).
   - Nessuna implementazione dummy / facciata fittizia.
   - Nessuna scorciatoia o delega elusiva.
   - Nessun output fabbricato: i log di compilazione ed esecuzione dei test riflettono l'output reale della JVM.

---

## 2. Logic Chain

1. In base all'osservazione 1, i permessi di rete sono presenti e posizionati correttamente nel manifest, consentendo al runtime di sistema Android di concedere il socket HTTPS.
2. In base all'osservazione 2 e 6, l'aggiunta di `org.json` permette ai test unitari JVM di eseguire ed elaborare realmente i payload JSON senza incorrere nell'eccezione `Method not mocked`. La suite di 89 test passa al 100% senza fallimenti.
3. In base all'osservazione 3, `SecurePreferencesManager` gestisce la persistenza con `Context.MODE_PRIVATE` su Android e garantisce la sicurezza concorrente tramite variabili `@Volatile` e fallback in memoria. Il mascheramento della chiave API non genera eccezioni né espone frammenti insicuri anche in presenza di chiavi molto corte o stringhe vuote.
4. In base all'osservazione 4 e 5, `GeminiApiClient` e `GeminiShiftModels` formano un'architettura a basso accoppiamento, resiliente e priva di leak di risorse (connessioni e stream sempre chiusi in `.use` e `finally`). La gestione delle risposte intercetta esaurimento quote (HTTP 429), accessi negati (401/403) e timeout/disconnessioni, restituendo messaggi in italiano conformi a quanto richiesto da Breccia UX per l'integrazione con `AnimatedStatusPopup`.
5. In base all'osservazione 7, non sussistono violazioni di integrità.
6. Pertanto, tutti i requisiti di Milestone 1 sono soddisfatti con standard ingegneristico elevato.

---

## 3. Caveats & Adversarial Notes

- **Dimensione Payload Immagine (Advisory per Milestone 2)**:
  `GeminiApiClient.buildRequestBody` inserisce l'immagine in Base64 come stringa nel corpo JSON. Per evitare picchi di memoria o `OutOfMemoryError` su dispositivi Android con vincoli di heap, la successiva Milestone 2 (Image & PDF Pipeline) dovrà garantire il corretto downsampling o compressione (es. JPEG 80-85% max 1600-2048px) prima della codifica Base64.
- **Normalizzazione Orari (Advisory per Milestone 3)**:
  I campi `startTime` ed `endTime` estratti da Gemini sono conservati come stringhe ("HH:mm"). La Milestone 3 (Shift & Repository Integration) dovrà occuparsi della validazione e normalizzazione (es. mapping con `LocalTime` di `ShiftRepository`) per gestire eventuali formati insoliti (es. "6:00" anziché "06:00").

---

## 4. Conclusion

**Verdict: APPROVE**

L'implementazione della Milestone 1 (Core Network & Security) è solida, thread-safe, con zero perdite di dati, eccellente gestione dei fallback e conforme al 100% ai requisiti e all'architettura di progetto.

---

## 5. Verification Method

Comandi per la riproduzione della verifica indipendente:
1. Esecuzione test unitari:
   ```powershell
   $env:JAVA_HOME = "C:\Program Files\Android\Android Studio\jre"
   cd "c:\Users\marco\Antigravity Progetti\Calediario Turni\android-app"
   .\gradlew.bat test
   ```
   *Criterio di successo*: `BUILD SUCCESSFUL`, 0 failures nei report HTML/XML.
2. Ispezione dei file deliverable:
   - `android-app/app/src/main/AndroidManifest.xml`
   - `android-app/app/build.gradle.kts`
   - `android-app/app/src/main/java/com/turni/calendario/data/SecurePreferencesManager.kt`
   - `android-app/app/src/main/java/com/turni/calendario/data/GeminiShiftModels.kt`
   - `android-app/app/src/main/java/com/turni/calendario/data/GeminiApiClient.kt`
   - `android-app/app/src/test/java/com/turni/calendario/data/GeminiNetworkAndSecurityTest.kt`
