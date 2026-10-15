# DISPATCH — Sub-Orchestrator Milestone 1: Core Network & Security

## Identity & Role
- Archetype: teamwork_preview_orchestrator
- Role: Milestone 1 Sub-Orchestrator (Core Network & Security)
- Working Directory: c:\Users\marco\Antigravity Progetti\Calediario Turni\.agents\teamwork\sub_orch_m1
- Parent Conversation ID: 90066a2c-0ee0-47dc-9cce-f552c7e66ada

## Objective
Implement and verify Milestone 1 (Core Network & Security):
1. Add `<uses-permission android:name="android.permission.INTERNET" />` to `android-app/app/src/main/AndroidManifest.xml`.
2. Add `testImplementation("org.json:json:20231013")` to `android-app/app/build.gradle.kts`.
3. Implement `SecurePreferencesManager` in `android-app/app/src/main/java/com/turni/calendario/data/SecurePreferencesManager.kt` (persisting Gemini API Key in `Context.MODE_PRIVATE` with masking and in-memory fallback for JVM tests, and user name preference).
4. Implement `GeminiApiClient` base & JSON Schema definition in `android-app/app/src/main/java/com/turni/calendario/data/GeminiApiClient.kt` (REST client via `java.net.HttpsURLConnection`, Base64 payload, `response_mime_type: "application/json"`, error code handling 400/403/429/timeout).

## Files to Read
- `c:\Users\marco\Antigravity Progetti\Calediario Turni\.agents\teamwork\ORIGINAL_REQUEST.md`
- `c:\Users\marco\Antigravity Progetti\Calediario Turni\PROJECT.md`
- Explorers' findings in `c:\Users\marco\Antigravity Progetti\Calediario Turni\.agents\teamwork\explorer_survey_1\analysis.md` and `explorer_survey_3\analysis.md`

## Workflow & Constraints
- Apply standard iteration loop: Explorer -> Worker -> Reviewer -> Challenger -> Auditor.
- Ensure all builds pass (`.\gradlew.bat assembleDebug` and `.\gradlew.bat test` with `JAVA_HOME="C:\Program Files\Android\Android Studio\jre"`).
- Update your progress and write handoff.md when done, then notify parent.
