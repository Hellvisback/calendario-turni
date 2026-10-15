# DISPATCH — Worker M4 (Replacement: Real Implementation of GeminiShiftImportScreen)

## CRITICAL NOTICE
The previous worker failed to create `GeminiShiftImportScreen.kt` on disk.
YOU MUST EXPLICITLY CALL `write_to_file` to create `android-app/app/src/main/java/com/turni/calendario/ui/screens/GeminiShiftImportScreen.kt` and `replace_file_content` to update `android-app/app/src/main/java/com/turni/calendario/ui/screens/SettingsScreen.kt`.

## MANDATORY INTEGRITY WARNING
DO NOT CHEAT. All implementations must be genuine. DO NOT hardcode test results, create dummy/facade implementations, or circumvent the intended task. A teamwork_preview_auditor will independently verify your work. Integrity violations WILL be detected and your work WILL be rejected.

## File di cui hai la proprietà esclusiva di scrittura
- `android-app/app/src/main/java/com/turni/calendario/ui/screens/GeminiShiftImportScreen.kt`
- `android-app/app/src/main/java/com/turni/calendario/ui/screens/SettingsScreen.kt`

## Requisiti di `GeminiShiftImportScreen.kt`
1. **Comportamento & Layout**:
   - `GeminiShiftImportScreen(onDismiss: () -> Unit, onNavigateToTurni: ((ShiftRotation?) -> Unit)? = null)`
   - Singolo pulsante indietro in alto a sinistra (40dp) con `BackHandler(onBack = onDismiss)`.
   - Nessun pulsante indietro in basso.
   - Zero card inline statiche di errore/successo: notifiche ESCLUSIVAMENTE tramite `AnimatedStatusPopup` ancorato in `Alignment.BottomCenter`.
   - `Spacer(modifier = Modifier.height(100.dp))` a fine scroll per Thumb Zone.
   - Sfondo `ObsidianBackground`, card `ObsidianSurface`, bordi `ObsidianBorder`.
2. **Pannello a Monte**:
   - Gestione API Key:
     - Leggi con `SecurePreferencesManager.getApiKey(context)` e visualizza mascherata se salvata (`SecurePreferencesManager.getMaskedApiKey(context)`).
     - Pulsante "Modifica" / "Salva Chiave" con `SecurePreferencesManager.saveApiKey(context, key)`.
   - Nome Utente:
     - Leggi con `SecurePreferencesManager.getUserName(context)` e salva con `SecurePreferencesManager.saveUserName(context, name)`.
   - Selezione File Documento:
     - `rememberLauncherForActivityResult(ActivityResultContracts.GetContent())` per immagini (`"image/*"`) e PDF (`"application/pdf"`).
     - Selezionato un Uri, carica la Bitmap con `DocumentProcessor` in coroutine IO:
       `val bitmap = if (isPdf) DocumentProcessor.processPdfToBitmap(context, uri) else DocumentProcessor.processImageUriToBitmap(context, uri)`
     - Crea la miniatura con `DocumentProcessor.createThumbnail(bitmap)` e mostrala a schermo con `androidx.compose.foundation.Image(bitmap = thumbnail.asImageBitmap(), ...)`.
   - Analisi Gemini Flash:
     - Pulsante primario `fillMaxWidth` "Analizza con Gemini Flash Vision" con `CircularProgressIndicator` se `isAnalyzing`.
     - In coroutine IO:
       `val (base64, mimeType) = DocumentProcessor.bitmapToBase64(bitmap)`
       `val result = GeminiApiClient.extractShiftData(apiKey, base64, mimeType)`
     - Se errore: mostra `AnimatedStatusPopup(message = result.exceptionOrNull()?.message ?: "Errore", type = PopupType.ERROR)`.
     - Se successo: salva `geminiData = result.getOrNull()`.
3. **Risultati & Assegnazione Squadra**:
   - Mostra card con turni estratti (shortCode, orari, colorHex, reperibilità).
   - Mostra card per ogni squadra con ciclo giorni, turnisti e pulsante stella per predefinita.
   - Se `targetUserName` è configurato, usa `GeminiShiftMapper.matchUserSquad(data, userName)` per pre-selezionare la squadra dell'utente.
   - Pulsante "Conferma e Applica Turni":
     `GeminiShiftMapper.registerExtractedData(ShiftRepository, data, targetUserName)`
     Mostra `AnimatedStatusPopup(message = "Turni importati con successo!", type = PopupType.SUCCESS)` e richiama `onDismiss()` dopo 1.5 secondi.

## Integrazione in `SettingsScreen.kt`:
In `when (activeSubScreen)`:
```kotlin
SettingsSubScreen.IMPORT_SHIFTS -> {
    GeminiShiftImportScreen(
        onDismiss = { activeSubScreen = SettingsSubScreen.NONE },
        onNavigateToTurni = { rotation ->
            activeSubScreen = SettingsSubScreen.NONE
            onNavigateToTurni(rotation)
        }
    )
}
```

## Verifica Obbligatoria (ESEGUI DA TERMINALE!)
```powershell
$env:JAVA_HOME = "C:\Program Files\Android\Android Studio\jre"; .\gradlew.bat test
$env:JAVA_HOME = "C:\Program Files\Android\Android Studio\jre"; .\gradlew.bat assembleDebug
```
Entrambi i comandi DEVONO terminare con `BUILD SUCCESSFUL`.
Scrivi il tuo handoff report in `c:\Users\marco\Antigravity Progetti\Calediario Turni\.agents\teamwork\worker_m4_replacement\handoff.md` e invia un messaggio a me con send_message.


## 2026-10-14T21:39:01Z
[Message] timestamp=2026-10-14T21:39:01Z sender=90066a2c-0ee0-47dc-9cce-f552c7e66ada priority=MESSAGE_PRIORITY_HIGH content=Sei Worker M4 Replacement (Real Liquid Glass UI Developer).
Il tuo working directory dedicato è:
c:\Users\marco\Antigravity Progetti\Calediario Turni\.agents\teamwork\worker_m4_replacement

Devi leggere OBBLIGATORIAMENTE il file dei requisiti originali prima di qualsiasi azione:
c:\Users\marco\Antigravity Progetti\Calediario Turni\.agents\teamwork\ORIGINAL_REQUEST.md

Leggi anche il piano globale e le istruzioni di dispatch:
- c:\Users\marco\Antigravity Progetti\Calediario Turni\PROJECT.md
- c:\Users\marco\Antigravity Progetti\Calediario Turni\.agents\teamwork\worker_m4_replacement\DISPATCH.md

MANDATORY INTEGRITY WARNING:
DO NOT CHEAT. All implementations must be genuine. DO NOT hardcode test results, create dummy/facade implementations, or circumvent the intended task. A teamwork_preview_auditor will independently verify your work. Integrity violations WILL be detected and your work WILL be rejected.

File di cui hai la proprietà esclusiva di scrittura:
- android-app/app/src/main/java/com/turni/calendario/ui/screens/GeminiShiftImportScreen.kt
- android-app/app/src/main/java/com/turni/calendario/ui/screens/SettingsScreen.kt

Implementa GeminiShiftImportScreen.kt secondo le specifiche nel tuo DISPATCH.md:
1. Chiama write_to_file per creare android-app/app/src/main/java/com/turni/calendario/ui/screens/GeminiShiftImportScreen.kt.
2. Obsidian Liquid Glass design & Riccardo Breccia UX:
   - Singolo pulsante indietro in alto a sinistra (40dp) con BackHandler.
   - Nessun pulsante indietro in basso.
   - Zero card inline statiche di errore/successo: notifiche ESCLUSIVAMENTE tramite AnimatedStatusPopup (Alignment.BottomCenter).
   - Spacer 100dp in fondo alla scroll view per la Thumb Zone.
3. Pannello configurazione unificato a monte:
   - Visualizzazione mascherata API Key con bottone Modifica/Salva (SecurePreferencesManager).
   - Inserimento nome utente con auto-salvataggio.
   - Picker immagini e PDF con creazione miniatura tramite DocumentProcessor.createThumbnail.
   - Bottone "Analizza con Gemini Flash Vision" con loading indicator e chiamata asincrona su Dispatchers.IO.
4. Risultati & Selezione Squadra:
   - Riepilogo turni estratti (shortCode, orari, colori).
   - Card delle squadre con lunghezza ciclo, lista colleghi e bottone stella toggleabile (ElasticStarButton) per la squadra predefinita, pre-selezionata con GeminiShiftMapper.matchUserSquad.
   - Bottone di conferma "Conferma e Applica Turni" che registra in ShiftRepository e mostra popup di successo.
5. Aggiorna android-app/app/src/main/java/com/turni/calendario/ui/screens/SettingsScreen.kt per collegare SettingsSubScreen.IMPORT_SHIFTS a GeminiShiftImportScreen.

VERIFICA OBBLIGATORIA:
powershell: $env:JAVA_HOME = "C:\Program Files\Android\Android Studio\jre"; .\gradlew.bat test
powershell: $env:JAVA_HOME = "C:\Program Files\Android\Android Studio\jre"; .\gradlew.bat assembleDebug
Entrambi i comandi DEVONO terminare con BUILD SUCCESSFUL.

Scrivi handoff.md in c:\Users\marco\Antigravity Progetti\Calediario Turni\.agents\teamwork\worker_m4_replacement\handoff.md e invia un messaggio di completamento a me via send_message.
