# Dispatch: Reviewer 2 (Milestone 4 Gate — Functional & Domain Integration Review)

## Obiettivo
Effettuare la code review indipendente e avversariale su `android-app/app/src/main/java/com/turni/calendario/ui/screens/GeminiShiftImportScreen.kt` focalizzandosi sull'integrazione di dominio e la robustezza dello stato.

## File da leggere obbligatoriamente
1. `c:\Users\marco\Antigravity Progetti\Calediario Turni\.agents\teamwork\ORIGINAL_REQUEST.md`
2. `c:\Users\marco\Antigravity Progetti\Calediario Turni\PROJECT.md`
3. `c:\Users\marco\Antigravity Progetti\Calediario Turni\.agents\teamwork\worker_m4_replacement\handoff.md`
4. `c:\Users\marco\Antigravity Progetti\Calediario Turni\android-app\app\src\main\java\com\turni\calendario\ui\screens\GeminiShiftImportScreen.kt`
5. `c:\Users\marco\Antigravity Progetti\Calediario Turni\android-app\app\src\main\java\com\turni\calendario\ui\screens\SettingsScreen.kt`

## Checklist di Verifica Funzionale & Dominio
1. **Pannello Configurazione a Monte**:
   - Gestione sicura API Key: visualizzazione mascherata (`AIzaSy...4xQ`), switch a modalità modifica/salvataggio con `SecurePreferencesManager.saveApiKey`.
   - Nome utente con auto-salvataggio e default fallback.
   - File picker compatibile sia con immagini (JPEG, PNG, WEBP) sia con PDF (`application/pdf`).
   - Gestione miniatura con `DocumentProcessor.createThumbnail` e visualizzazione su `Image`.
2. **Elaborazione Asincrona & Mappatura Dominio**:
   - Chiamata a `GeminiApiClient.extractShiftData` su `Dispatchers.IO` con gestione loading spinner e try-catch degli errori di rete/quota.
   - Decodifica dei turni, badge colorati e legenda.
   - Card delle squadre con colleghi, pre-selezione automatica tramite `GeminiShiftMapper.matchUserSquad`.
   - Conferma e registrazione atomica in `ShiftRepository` tramite `GeminiShiftMapper.registerExtractedData`.
3. **Integrazione Navigazione**:
   - `SettingsScreen.kt` attiva correttamente `GeminiShiftImportScreen` su `SettingsSubScreen.IMPORT_SHIFTS`.
4. **Verifica Empirica**:
   - Esegui i test unitari:
     `$env:JAVA_HOME = "C:\Program Files\Android\Android Studio\jre"; .\gradlew.bat test`
   - Esegui il build:
     `$env:JAVA_HOME = "C:\Program Files\Android\Android Studio\jre"; .\gradlew.bat assembleDebug`

## Requisiti di Output
Redigi il report formale a 5 sezioni (Observation, Logic Chain, Caveats, Conclusion con verdetto APPROVE o REQUEST_CHANGES, Verification Method) in:
`c:\Users\marco\Antigravity Progetti\Calediario Turni\.agents\teamwork\reviewer_m4_2\handoff.md`
Invia il verdetto all'orchestratore via `send_message`.


## 2026-10-14T23:15:23Z
Sei Reviewer 2 per il Gate di Milestone 4 (Liquid Glass UI & Setup Experience).
Il tuo working directory dedicato è:
c:\Users\marco\Antigravity Progetti\Calediario Turni\.agents\teamwork\reviewer_m4_2

Devi leggere OBBLIGATORIAMENTE il file dei requisiti originali prima di qualsiasi azione:
c:\Users\marco\Antigravity Progetti\Calediario Turni\.agents\teamwork\ORIGINAL_REQUEST.md

Leggi anche:
- c:\Users\marco\Antigravity Progetti\Calediario Turni\PROJECT.md
- c:\Users\marco\Antigravity Progetti\Calediario Turni\.agents\teamwork\reviewer_m4_2\DISPATCH.md
- c:\Users\marco\Antigravity Progetti\Calediario Turni\.agents\teamwork\worker_m4_replacement\handoff.md
- android-app/app/src/main/java/com/turni/calendario/ui/screens/GeminiShiftImportScreen.kt
- android-app/app/src/main/java/com/turni/calendario/ui/screens/SettingsScreen.kt

Verifica le logiche funzionali e di dominio:
1. Mascheramento e salvataggio API Key con SecurePreferencesManager.
2. Auto-salvataggio nome utente.
3. File picker per immagini e PDF con creazione thumbnail ad alta risoluzione tramite DocumentProcessor.createThumbnail.
4. Analisi asincrona con GeminiApiClient.extractShiftData su Dispatchers.IO.
5. Pre-selezione squadra utente con GeminiShiftMapper.matchUserSquad e conferma/registrazione atomica in ShiftRepository.
6. Navigazione e subscreen in SettingsScreen.kt.
7. Esegui la verifica empirica:
   $env:JAVA_HOME = "C:\Program Files\Android\Android Studio\jre"; .\gradlew.bat test
   $env:JAVA_HOME = "C:\Program Files\Android\Android Studio\jre"; .\gradlew.bat assembleDebug
Scrivi handoff.md con verdetto esplicito (APPROVE o REQUEST_CHANGES) in c:\Users\marco\Antigravity Progetti\Calediario Turni\.agents\teamwork\reviewer_m4_2\handoff.md e invia un messaggio formale a me.
