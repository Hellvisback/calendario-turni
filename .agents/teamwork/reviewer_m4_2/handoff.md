# Handoff Report — Reviewer 2 (Milestone 4 Gate: Functional & Domain Integration Review)

## 1. Observation

### A. Integrità e Conformità dell'Implementazione
- **File esaminati**:
  - `android-app/app/src/main/java/com/turni/calendario/ui/screens/GeminiShiftImportScreen.kt` (1023 linee)
  - `android-app/app/src/main/java/com/turni/calendario/ui/screens/SettingsScreen.kt` (linee 78-86 e 255-260)
  - `android-app/app/src/main/java/com/turni/calendario/data/SecurePreferencesManager.kt`
  - `android-app/app/src/main/java/com/turni/calendario/data/DocumentProcessor.kt`
  - `android-app/app/src/main/java/com/turni/calendario/data/GeminiApiClient.kt`
  - `android-app/app/src/main/java/com/turni/calendario/data/GeminiShiftMapper.kt`
- **Assenza di Facade/Mock**: Nessuna implementazione dummy o bypass è presente nel codice sorgente. Le chiamate alle SharedPreferences, alla crittografia/mascheramento, al `DocumentProcessor` (con `PdfRenderer` nativo) e a `GeminiApiClient` su `Dispatchers.IO` sono genuine.
- **Top Bar & Ergonomia Riccardo Breccia**:
  - Singolo pulsante indietro 40dp in alto a sinistra (`Modifier.size(40.dp)` con `Icon(Icons.Default.ArrowBack)` a linea 185-198), gestito congiuntamente da `BackHandler(onBack = onDismiss)` a linea 161. Nessun pulsante duplicato in basso.
  - Zero card statiche inline di notifica: tutte le segnalazioni (errori, info, successo) sono convogliate esclusivamente in `AnimatedStatusPopup` ancorato a `Alignment.BottomCenter` con `navigationBarsPadding()` (linee 965-973).
  - Presenza dello `Spacer(modifier = Modifier.height(100.dp))` a linea 959 per garantire la Thumb Zone ergonomica a fine scroll.
  - `ElasticStarButton` (linee 980-1022) provvisto di micro-animazione elastica (`Spring.DampingRatioMediumBouncy`) e toggle a 2 stati/stato neutro tramite `ShiftRepository.toggleDefaultRotation`.
- **Configurazione e Salvataggio**:
  - API Key: mascheramento tramite `SecurePreferencesManager.getMaskedApiKey(context)` (linea 289), salvataggio con `SecurePreferencesManager.saveApiKey(context, trimmed)` (linea 334).
  - Auto-salvataggio nome turnista: salvataggio istantaneo ad ogni modifica tramite `SecurePreferencesManager.saveUserName(context, it)` (linea 372).
  - Selezione file e PDF: `imagePickerLauncher` e `pdfPickerLauncher` (linee 103-158) con conversione `DocumentProcessor.processPdfToBitmap` ed estrazione thumbnail tramite `DocumentProcessor.createThumbnail(bmp)` renderizzata su `Image(bitmap = thumbnailBitmap!!.asImageBitmap())` (linee 437-445).
  - Analisi asincrona: invocazione di `GeminiApiClient.extractShiftData` su `Dispatchers.IO` (linea 552) con gestione stati `isAnalyzing`, try-catch e segnalazione tramite popup.
  - Pre-selezione squadra: matching automatico con `GeminiShiftMapper.matchUserSquad(data, userName)` (linea 565).
  - Registrazione catalogo: `GeminiShiftMapper.registerExtractedData(repository = ShiftRepository, data = currentData, targetUserName = userName)` (linea 883).

### B. Omissioni e Discrepanze rispetto a Requisiti Originali (`ORIGINAL_REQUEST.md`)
1. **Mancanza del Dialog Modale di Avviso su Azione "Modifica" (ORIGINAL_REQUEST § R4 e Acceptance Criteria)**:
   - `ORIGINAL_REQUEST.md` (linee 32 e 51) specifica espressamente:
     > "Alla pressione di "Modifica", mostrare un dialog modale di avviso: "Stai per uscire da questa schermata per passare a quella di modifica, perderai queste informazioni", consentendo di confermare il passaggio o annullare."
     > "[ ] Il click su "Modifica" apre il dialog modale di avviso di uscita con opzioni di conferma/annulla."
   - In `GeminiShiftImportScreen.kt` (linee 921-953), l'azione secondaria naviga direttamente al catalogo senza alcun dialog di conferma preventiva:
     ```kotlin
     OutlinedButton(
         onClick = {
             GeminiShiftMapper.registerExtractedData(...)
             val targetRot = ShiftRepository.rotations.find { ... }
             onNavigateToTurni(targetRot)
         }, ...
     )
     ```
2. **Mancanza della Nota Visiva Esplicativa (ORIGINAL_REQUEST § R4)**:
   - `ORIGINAL_REQUEST.md` (linea 31) specifica:
     > "Includere una nota visiva esplicativa che ricordi all'utente che i turni potranno essere rifiniti in qualsiasi momento nell'apposita sezione dell'app."
   - In `GeminiShiftImportScreen.kt`, tale indicazione visiva esplicativa prima delle azioni di chiusura non è presente come card o testo dedicato.

### C. Vulnerabilità Avversariali di Concorrenza e UI
1. **Assenza di Debounce / Stato `isSaving` su "Conferma e Applica Turni"**:
   - A linea 881, il pulsante `Button(onClick = { ... })` avvia la registrazione e lancia `coroutineScope.launch { delay(1500); onDismiss() }`. Durante l'intervallo di 1500ms, il pulsante rimane abilitato (`enabled` non controllato da uno stato `isSaving`). Tocchi multipli veloci causano esecuzioni concorrenti ridondanti della registrazione e multipli callback di dismiss.
2. **Mancanza di Mascheramento durante la Modifica dell'API Key**:
   - A linea 320, l'`OutlinedTextField` per l'inserimento/modifica dell'API Key non include `visualTransformation = PasswordVisualTransformation()` né un pulsante toggle per mostrare/nascondere la password, esponendo l'intera chiave in chiaro durante la digitazione.

### D. Risultati della Verifica Empirica
- **Test Unitari JVM**:
  - Comando: `$env:JAVA_HOME = "C:\Program Files\Android\Android Studio\jre"; .\gradlew.bat test`
  - Risultato verificato in `app/build/reports/tests/testDebugUnitTest/index.html`:
    - **Total tests**: 223
    - **Failures**: 0
    - **Ignored**: 0
    - **Success rate**: 100%
- **Compilazione APK Debug**:
  - Comando: `$env:JAVA_HOME = "C:\Program Files\Android\Android Studio\jre"; .\gradlew.bat assembleDebug`
  - Risultato: **FALLITO** con eccezione:
    ```
    Caused by: org.gradle.api.GradleException: Not enough memory to run compilation. Try to increase it via 'gradle.properties':
    kotlin.daemon.jvmargs=-Xmx<size>
        at org.jetbrains.kotlin.gradle.tasks.TasksUtilsKt.throwGradleExceptionIfError(tasksUtils.kt:34)
    ...
    BUILD FAILED in 11m 49s
    26 actionable tasks: 1 executed, 25 up-to-date
    ```
  - Causa radice accertata: in `android-app/gradle.properties`, il parametro `kotlin.daemon.jvmargs=-Xmx2048m` non fornisce memoria sufficiente per i task di compilazione Kotlin del compilatore Jetpack Compose in presenza di file estesi, portando all'esaurimento dell'heap space del demone Kotlin.

---

## 2. Logic Chain

1. **Premessa di Dominio e UX**: L'obiettivo di Milestone 4 è fornire la schermata completa di importazione rispettando i requisiti di `ORIGINAL_REQUEST.md` (§ R1-R5) e l'architettura `PROJECT.md`.
2. **Verifica Funzionale**: L'integrazione di dominio tra UI, `SecurePreferencesManager`, `DocumentProcessor`, `GeminiApiClient`, `GeminiShiftMapper` e `ShiftRepository` è robusta e i 223 test unitari passano al 100%.
3. **Verifica dei Requisiti di Contratto**: `ORIGINAL_REQUEST.md` richiede espressamente (R4 e Acceptance Criteria) che il click su "Modifica" mostri un dialog modale di avviso di uscita con conferma ("Stai per uscire da questa schermata per passare a quella di modifica, perderai queste informazioni") e che sia presente una nota esplicativa sul fatto che i turni potranno essere rifiniti nel catalogo. L'assenza di tale dialog e nota costituisce una non conformità rispetto ai requisiti utente originali.
4. **Verifica di Robustezza Stato e Sicurezza**: L'assenza di protezione contro i doppi tap su "Conferma" e la mancanza di mascheramento password durante l'editing della chiave rappresentano failure mode e criticità di riservatezza.
5. **Verifica Empirica di Build**: La compilazione `assembleDebug` fallisce sistematicamente con `Not enough memory to run compilation` a causa del limite `-Xmx2048m` in `gradle.properties`. Un gate non può essere approvato se la build Gradle dell'APK fallisce.
6. **Conclusione**: Per le ragioni esposte ai punti 3, 4 e 5, il verdetto deve essere `REQUEST_CHANGES`.

---

## 3. Caveats

- I test unitari logici su JVM coprono l'intero dominio dati (223 test verdi al 100%); il fallimento della build riguarda esclusivamente la fase di compilazione Gradle / memoria heap del Kotlin compiler (`compileDebugKotlin`).
- L'APK esistente `app-debug.apk` presente in `android-app/app/build/outputs/apk/debug/` è datato 01:09:24 (generato prima delle contese di memoria dei demoni), ma la ricompilazione pulita non conclude con `BUILD SUCCESSFUL`.

---

## 4. Conclusion

**VERDETTO: REQUEST_CHANGES**

### Azioni Richieste al Worker:
1. **Dialog Modale di Conferma su Modifica (Critico - R4)**:
   - Implementare il dialog modale in `GeminiShiftImportScreen.kt` al click su "Modifica":
     Titolo: `"Uscire e modificare?"`
     Messaggio: `"Stai per uscire da questa schermata per passare a quella di modifica, perderai queste informazioni."`
     Pulsanti: Conferma (esegue il salvataggio e `onNavigateToTurni`) e Annulla.
   - Aggiungere la nota visiva esplicativa che ricorda che i turni possono essere rifiniti in qualsiasi momento nel catalogo.
2. **Ottimizzazione Memoria Compilatore Kotlin in `gradle.properties` (Critico - Build)**:
   - Incrementare `kotlin.daemon.jvmargs=-Xmx3072m` (o `-Xmx4096m`) e `org.gradle.jvmargs=-Xmx3072m` per prevenire il crash `Not enough memory to run compilation` durante la compilazione Jetpack Compose.
3. **Debounce e Protezione Salva (Importante - Ergonomia)**:
   - Introdurre uno stato `var isSaving by remember { mutableStateOf(false) }` che disabiliti il pulsante "Conferma e Applica Turni" durante la coroutine di salvataggio.
4. **Mascheramento API Key in Inserimento (Migliorativo - Sicurezza)**:
   - Aggiungere `visualTransformation = PasswordVisualTransformation()` al campo `OutlinedTextField` dell'API Key con toggle per visualizzazione temporanea.

---

## 5. Verification Method

1. **Ispezione codice**:
   - Verificare in `GeminiShiftImportScreen.kt` la presenza dello stato `showEditWarningDialog` e del relativo composable `AlertDialog`.
   - Verificare in `android-app/gradle.properties` l'allocazione di memoria heap per `kotlin.daemon.jvmargs`.
2. **Esecuzione Test Unitari**:
   ```powershell
   $env:JAVA_HOME = "C:\Program Files\Android\Android Studio\jre"
   .\gradlew.bat test
   ```
   *Esito atteso*: `BUILD SUCCESSFUL`, 223/223 test passati.
3. **Compilazione Completa APK**:
   ```powershell
   $env:JAVA_HOME = "C:\Program Files\Android\Android Studio\jre"
   .\gradlew.bat assembleDebug
   ```
   *Esito atteso*: `BUILD SUCCESSFUL`, generazione corretta dell'APK `android-app/app/build/outputs/apk/debug/app-debug.apk`.
