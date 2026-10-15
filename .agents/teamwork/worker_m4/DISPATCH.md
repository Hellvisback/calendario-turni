# DISPATCH — Worker M4 (Milestone 4: Liquid Glass UI & Setup Experience)

## 2026-10-14T21:05:35Z

## Mission
Implementare la schermata unificata `GeminiShiftImportScreen.kt` e integrarla in `SettingsScreen.kt`, rispettando rigorosamente lo stile **Obsidian Liquid Glass** e l'ergonomia **Riccardo Breccia UX**.

## MANDATORY INTEGRITY WARNING
DO NOT CHEAT. All implementations must be genuine. DO NOT hardcode test results, create dummy/facade implementations, or circumvent the intended task. A teamwork_preview_auditor will independently verify your work. Integrity violations WILL be detected and your work WILL be rejected.

## File di proprietà esclusiva di scrittura
- `android-app/app/src/main/java/com/turni/calendario/ui/screens/GeminiShiftImportScreen.kt`
- `android-app/app/src/main/java/com/turni/calendario/ui/screens/SettingsScreen.kt`
- (eventuale test UI/unit) `android-app/app/src/test/java/com/turni/calendario/GeminiShiftImportUiTest.kt`

## Requisiti Architetturali e di Stile
1. **Navigazione & Struttura**:
   - Singolo pulsante indietro in alto a sinistra (`Icons.Default.ArrowBack`, 40dp con `BackHandler(onBack = onDismiss)`). Nessun pulsante indietro in basso!
   - In `SettingsScreen.kt`: collegare `SettingsSubScreen.IMPORT_SHIFTS` a `GeminiShiftImportScreen(onDismiss = { activeSubScreen = SettingsSubScreen.NONE })`.
   - Distanziatore finale a fondo scroll: `Spacer(modifier = Modifier.height(100.dp))` per il rispetto della Thumb Zone.

2. **Notifiche & Feedback**:
   - **MAI card inline rosse o verdi statiche**: vietato alterare il flusso dei componenti nella Column!
   - Usare ESCLUSIVAMENTE `AnimatedStatusPopup` ancorato in `Alignment.BottomCenter` con `PopupType.SUCCESS`, `PopupType.ERROR`, `PopupType.INFO`, `PopupType.WARNING`.

3. **Pannello di Configurazione Unificato a Monte**:
   - Gestione Gemini API Key:
     - Se salvata, mostrare mascherata (`SecurePreferencesManager.getMaskedApiKey()`) con pulsante "Modifica".
     - Se assente o in modifica, mostrare `OutlinedTextField` per inserimento con opzione di visualizzazione password e pulsante "Salva Chiave" (persistita tramite `SecurePreferencesManager.saveApiKey(context, key)`).
   - Filtro Nominativo Utente:
     - Campo di testo per il nome utente (es. "Graziani Marco") con persistenza tramite `SecurePreferencesManager.saveUserName(context, name)`.
   - Selezione Documento:
     - Pulsanti per selezione Immagine (JPEG, PNG, WEBP) e PDF (`"application/pdf"`) tramite `rememberLauncherForActivityResult(ActivityResultContracts.GetContent())`.
     - Quando un Uri è selezionato, elaborare con `DocumentProcessor` (in coroutine IO) e mostrare miniatura anteprima creata con `DocumentProcessor.createThumbnail`.
   - Azione Analisi con Gemini Flash Vision:
     - Pulsante primario `fillMaxWidth` con stile Liquid Glass / gradiente primario.
     - Stato di caricamento con indicatore circolare durante la chiamata asincrona su `Dispatchers.IO`:
       `val (base64, mimeType) = DocumentProcessor.bitmapToBase64(fullBitmap)`
       `val result = GeminiApiClient.extractShiftData(apiKey, base64, mimeType)`
     - Gestione errori localizzata ed elegante (API Key non configurata, immagine troppo pesante, errore di rete, quota 429) mostrata tramite `AnimatedStatusPopup`.

4. **Anteprima Risultati & Selezione Squadra**:
   - Se l'analisi ha successo:
     - Mostrare card riassuntiva con conteggio turni estratti, orari, categorie.
     - Elenco squadre e rotazioni rilevate con lunghezza ciclo giorni e turnisti identificati.
     - Per ciascuna squadra, pulsante a stella toggleabile a 2 stati (`ElasticStarButton` o interazione a stella) per selezionare la squadra predefinita dell'utente.
     - La squadra dell'utente deve essere pre-selezionata automaticamente se `GeminiShiftMapper.matchUserSquad` rileva una corrispondenza con il nome inserito.
   - Azione di Conferma:
     - Pulsante primario "Conferma e Applica Turni".
     - Esegue `GeminiShiftMapper.registerExtractedData(ShiftRepository, data, targetUserName)`.
     - Mostra `AnimatedStatusPopup` di successo con coriandoli e ritorna alla schermata precedente dopo un breve delay o su tocco utente.

## Verifica Obbligatoria
Esegui:
```powershell
$env:JAVA_HOME = "C:\Program Files\Android\Android Studio\jre"; .\gradlew.bat test
$env:JAVA_HOME = "C:\Program Files\Android\Android Studio\jre"; .\gradlew.bat assembleDebug
```
Entrambi i comandi DEVONO terminare con `BUILD SUCCESSFUL` e 0 errori.
Scrivi il tuo handoff report in `c:\Users\marco\Antigravity Progetti\Calediario Turni\.agents\teamwork\worker_m4\handoff.md` e invia un messaggio di completamento con send_message.
