# Handoff Report — Milestone 1: Core Network & Security

**Worker**: Worker M1 (Core Network & Security Developer)  
**Date**: 2026-10-14  
**Target Milestone**: M1 (Core Network & Security)  
**Status**: COMPLETE (Hard Handoff)  

---

## 1. Observation

1. **Permessi di Rete in AndroidManifest**:
   In `android-app/app/src/main/AndroidManifest.xml` mancavano i permessi necessari per le chiamate REST verso Google Gemini.
   Sono stati aggiunti a riga 4:
   ```xml
   <uses-permission android:name="android.permission.INTERNET" />
   <uses-permission android:name="android.permission.ACCESS_NETWORK_STATE" />
   ```

2. **Dipendenza JSON per i Test Unitari JVM**:
   In `android-app/app/build.gradle.kts`, il blocco `dependencies` includeva solo `junit:junit:4.13.2` per i test. È stata aggiunta la dipendenza:
   ```kotlin
   testImplementation("org.json:json:20231013")
   ```
   consentendo l'istanziazione reale di `JSONObject` e `JSONArray` senza ricorrere a mock runtime o incorrere nell'eccezione `Method not mocked`.

3. **Modulo SecurePreferencesManager**:
   Creato `android-app/app/src/main/java/com/turni/calendario/data/SecurePreferencesManager.kt` con supporto `Context.MODE_PRIVATE` su Android e fallback in-memory (`@Volatile inMemoryApiKey`, `@Volatile inMemoryUserName`) per ambienti JVM dove `context == null`.
   Include le firme:
   - `getApiKey(context: Context? = null): String?`
   - `saveApiKey(context: Context? = null, apiKey: String)`
   - `hasApiKey(context: Context? = null): Boolean`
   - `getMaskedApiKey(context: Context? = null): String` (es. `"AIzaSy...4xQ"`, o `"••••••••"` se <= 8 caratteri)
   - `getUserName(context: Context? = null): String?` (default: `"Marco Graziani"`)
   - `saveUserName(context: Context? = null, name: String)`
   - `clear(context: Context? = null)`

4. **Modelli Dati GeminiShiftModels**:
   Creato `android-app/app/src/main/java/com/turni/calendario/data/GeminiShiftModels.kt` contenente:
   - `GeminiShiftDefinition(val code: String, val name: String, val startTime: String, val endTime: String, val colorHex: String? = null, val isReperibilita: Boolean = false)`
   - `GeminiSquadRotation(val squadName: String, val rotationDays: List<String>, val employees: List<String> = emptyList(), val cycleDays: Int = rotationDays.size)`
   - `GeminiEmployeeAssignment(val name: String, val squadName: String? = null, val role: String? = null, val employeeId: String? = null, val isOnCall: Boolean = false, val dailyShifts: Map<String, String> = emptyMap())`
   - `GeminiShiftData(val shiftDefinitions: List<GeminiShiftDefinition>, val squads: List<GeminiSquadRotation>, val notes: String? = null, val validityStart: String? = null, val validityEnd: String? = null, val employees: List<GeminiEmployeeAssignment> = emptyList(), val rawJson: String? = null)`

5. **Client REST Gemini Flash GeminiApiClient**:
   Creato `android-app/app/src/main/java/com/turni/calendario/data/GeminiApiClient.kt` basato su `HttpsURLConnection`:
   - Endpoint target: `https://generativelanguage.googleapis.com/v1beta/models/gemini-1.5-flash:generateContent?key=$apiKey`
   - Payload multimodale con blocco `inline_data` contenente `mime_type` e `data` (Base64).
   - Configurazione `generationConfig` con `response_mime_type: "application/json"` e `response_schema` rigoroso che vincola il modello all'estrazione di `shiftDefinitions` e `squads`.
   - Gestione errori HTTP: 400 (Bad Request), 401/403 (Invalid API Key / Access Denied), 429 (Rate Limit / Quota Exceeded con messaggio esplicativo), 5xx (Server Error), `SocketTimeoutException`, `UnknownHostException`.
   - Metodo di decodifica `parseGeminiResponse(jsonString: String): GeminiShiftData` che supporta ricorsivamente sia l'involucro standard REST di Gemini (`candidates[0].content.parts[0].text`), sia blocchi markdown (```json ... ```), sia JSON diretti.

6. **Test Unitari Verificati**:
   Creato `android-app/app/src/test/java/com/turni/calendario/data/GeminiNetworkAndSecurityTest.kt` a copertura di API Key persistence, masking, validazione payload JSON, envelope decoding e gestione filtri di sicurezza.
   Esecuzione del comando:
   ```powershell
   $env:JAVA_HOME = "C:\Program Files\Android\Android Studio\jre"; .\gradlew.bat test
   ```
   Risultato verbatim: `BUILD SUCCESSFUL in 6m 3s` (43 actionable tasks: 28 executed, 15 up-to-date, 100% verde).

7. **Compilazione APK Debug**:
   Esecuzione del comando:
   ```powershell
   $env:JAVA_HOME = "C:\Program Files\Android\Android Studio\jre"; .\gradlew.bat assembleDebug
   ```
   Risultato verbatim: `BUILD SUCCESSFUL in 7m 16s` (32 actionable tasks: 5 executed, 27 up-to-date).

---

## 2. Logic Chain

1. In base all'osservazione 1, l'applicazione Android non poteva comunicare con l'endpoint esterno di Google Gemini senza dichiarare `android.permission.INTERNET`. L'aggiunta in `AndroidManifest.xml` abilita la trasmissione HTTPS da parte della sandbox Android.
2. In base all'osservazione 2, la suite di test JVM esegue le classi al di fuori dell'emulatore; pertanto le classi stubbate di `android.jar` per `org.json` avrebbero sollevato `RuntimeException: Method not mocked`. L'inserimento di `testImplementation("org.json:json:20231013")` garantisce l'esecuzione conforme del parser JSON durante `.\gradlew.bat test`.
3. In base all'osservazione 3, memorizzare l'API Key e il nominativo utente in `Context.MODE_PRIVATE` protegge le credenziali nell'archivio sandbox dell'applicazione; la presenza del fallback in memoria garantisce zero dipendenze hardware nei test e testabilità immediata al 100%. La formula di mascheramento rispetta il formato visivo richiesto `"AIzaSy...4xQ"`.
4. In base all'osservazione 4, i modelli `GeminiShiftModels` rispecchiano fedelmente i requisiti del DISPATCH.md e la struttura del JSON Schema inviato a Gemini, includendo orari di inizio/fine, reperibilità e rotazioni per squadra.
5. In base all'osservazione 5, l'utilizzo di `HttpsURLConnection` nativa evita l'aggiunta di librerie pesanti (Retrofit/OkHttp) minimizzando i tempi di build e i rischi di incompatibilità con Kotlin 1.7 / AGP 7.2.2. L'errore 429 e gli altri codici HTTP sono intercettati con messaggi in lingua italiana conformi ai requisiti.
6. In base alle osservazioni 6 e 7, la compilazione del codice e la suite dei test passano con successo sia per `test` che per `assembleDebug`.

---

## 3. Caveats

- Il client `GeminiApiClient` adotta `gemini-1.5-flash` come modello predefinito in quanto modello stabile con supporto per structured outputs su endpoint v1beta. Il parametro `model` nel costruttore consente comunque di specificare `gemini-2.0-flash` o versioni future senza modifiche al codice.
- I test unitari coprono la logica del client, la costruzione del payload e il parser JSON. Le chiamate di rete reali verso Google Cloud richiedono una chiave API valida e connessione internet attiva, pertanto nei test unitari sono state simulate risposte mock e validazioni di input per evitare dipendenze da credenziali remote.

---

## 4. Conclusion

Tutti i requisiti e deliverable assegnati a Milestone 1 (Core Network & Security) sono stati implementati con piena integrità, conformità architetturale e rispetto dei contratti definiti in `PROJECT.md`:
- Permessi internet attivi.
- Dipendenza di test per JSON presente.
- Persistenza sicura e mascheramento credenziali operativi.
- Modelli dati `GeminiShiftModels` pronti per l'integrazione con M2, M3 e M4.
- Client multimodale `GeminiApiClient` pienamente funzionante con schema JSON strutturato ed error handling.
- Build e test 100% verdi.

---

## 5. Verification Method

Per verificare in modo indipendente:
1. Verificare i test unitari:
   ```powershell
   $env:JAVA_HOME = "C:\Program Files\Android\Android Studio\jre"
   .\gradlew.bat test
   ```
   Condizione di successo: `BUILD SUCCESSFUL`, 0 errori.
2. Verificare la compilazione dell'APK debug:
   ```powershell
   $env:JAVA_HOME = "C:\Program Files\Android\Android Studio\jre"
   .\gradlew.bat assembleDebug
   ```
   Condizione di successo: `BUILD SUCCESSFUL`, generazione di `app-debug.apk` in `android-app/app/build/outputs/apk/debug/`.
3. Ispezione file modificati:
   - `android-app/app/src/main/AndroidManifest.xml`
   - `android-app/app/build.gradle.kts`
   - `android-app/app/src/main/java/com/turni/calendario/data/SecurePreferencesManager.kt`
   - `android-app/app/src/main/java/com/turni/calendario/data/GeminiShiftModels.kt`
   - `android-app/app/src/main/java/com/turni/calendario/data/GeminiApiClient.kt`
   - `android-app/app/src/test/java/com/turni/calendario/data/GeminiNetworkAndSecurityTest.kt`
