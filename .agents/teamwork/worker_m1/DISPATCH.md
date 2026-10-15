# DISPATCH — Worker Milestone 1: Core Network & Security

## Identity & Role
- Archetype: teamwork_preview_worker
- Role: Core Network & Security Developer
- Working Directory: c:\Users\marco\Antigravity Progetti\Calediario Turni\.agents\teamwork\worker_m1

## Objective
Implement Milestone 1 (Core Network & Security):
1. In `android-app/app/src/main/AndroidManifest.xml`:
   Add `<uses-permission android:name="android.permission.INTERNET" />`.
2. In `android-app/app/build.gradle.kts`:
   Add `testImplementation("org.json:json:20231013")` in the `dependencies { ... }` block so that `org.json` objects work in JVM unit tests.
3. In `android-app/app/src/main/java/com/turni/calendario/data/SecurePreferencesManager.kt`:
   Implement secure persistence of the Google Gemini API Key and default user name ("Marco Graziani"). Use `Context.MODE_PRIVATE` with SharedPreferences when Context is available, and an in-memory fallback when Context is null (for JVM test suite compatibility). Provide methods:
   - `getApiKey(context: Context?): String?`
   - `saveApiKey(context: Context?, apiKey: String)`
   - `getMaskedApiKey(context: Context?): String` (e.g., "AIzaSy...4xQ")
   - `getUserName(context: Context?): String?`
   - `saveUserName(context: Context?, name: String)`
4. In `android-app/app/src/main/java/com/turni/calendario/data/GeminiShiftModels.kt`:
   Create data classes representing the extracted shift information:
   - `GeminiShiftDefinition(val code: String, val name: String, val startTime: String, val endTime: String, val colorHex: String?, val isReperibilita: Boolean = false)`
   - `GeminiSquadRotation(val squadName: String, val rotationDays: List<String>, val employees: List<String> = emptyList())`
   - `GeminiShiftData(val shiftDefinitions: List<GeminiShiftDefinition>, val squads: List<GeminiSquadRotation>, val notes: String? = null)`
5. In `android-app/app/src/main/java/com/turni/calendario/data/GeminiApiClient.kt`:
   Implement REST client with `java.net.HttpsURLConnection` targeting `https://generativelanguage.googleapis.com/v1beta/models/gemini-1.5-flash:generateContent?key=$apiKey`.
   - Set request headers (`Content-Type: application/json`).
   - Construct multimodal JSON payload with `contents[0].parts`: text prompt + `inline_data` (mimeType, data Base64).
   - Configure `generationConfig` with `response_mime_type: "application/json"` and `response_schema` enforcing the extraction of `shiftDefinitions` (code, name, startTime, endTime, isReperibilita), `squads` (squadName, rotationDays, employees).
   - Implement comprehensive error handling returning `Result<GeminiShiftData>`:
     - 400 Bad Request
     - 403 Invalid API Key / Forbidden
     - 429 Rate Limit / Quota Exceeded (with clear message to wait or check quota)
     - Network timeouts / no connection
   - Provide an overloaded or testable `parseGeminiResponse(jsonString: String): GeminiShiftData` method.

## Verification Required
Worker MUST verify changes:
1. Run `.\gradlew.bat test` (with `$env:JAVA_HOME = "C:\Program Files\Android\Android Studio\jre"`) in `android-app/`.
2. Run `.\gradlew.bat assembleDebug` in `android-app/`.
Both must be 100% green. Document commands and outputs in `handoff.md`.

## Files Owned Exclusively
- `android-app/app/src/main/AndroidManifest.xml`
- `android-app/app/build.gradle.kts`
- `android-app/app/src/main/java/com/turni/calendario/data/SecurePreferencesManager.kt`
- `android-app/app/src/main/java/com/turni/calendario/data/GeminiShiftModels.kt`
- `android-app/app/src/main/java/com/turni/calendario/data/GeminiApiClient.kt`


## 2026-10-14T15:54:12Z
Received dispatch message:
Sei Worker M1 (Core Network & Security Developer).
Il tuo working directory dedicato è:
c:\Users\marco\Antigravity Progetti\Calediario Turni\.agents\teamwork\worker_m1

Devi leggere OBBLIGATORIAMENTE il file dei requisiti originali prima di qualsiasi azione:
c:\Users\marco\Antigravity Progetti\Calediario Turni\.agents\teamwork\ORIGINAL_REQUEST.md

Leggi anche il piano globale e i report degli explorer:
- c:\Users\marco\Antigravity Progetti\Calediario Turni\PROJECT.md
- c:\Users\marco\Antigravity Progetti\Calediario Turni\.agents\teamwork\worker_m1\DISPATCH.md
- c:\Users\marco\Antigravity Progetti\Calediario Turni\.agents\teamwork\explorer_survey_1\analysis.md
- c:\Users\marco\Antigravity Progetti\Calediario Turni\.agents\teamwork\explorer_survey_3\analysis.md
