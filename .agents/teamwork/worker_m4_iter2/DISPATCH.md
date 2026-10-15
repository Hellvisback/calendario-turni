# Dispatch — Worker M4 Iteration 2 (UI Remediation & Build Tuning)

## Mission
Applicare con precisione chirurgica le modifiche definite nei report di Explorer 2 e 3 (`.agents/teamwork/explorer_m4_iter2_2/analysis.md` e `.agents/teamwork/explorer_m4_iter2_3/analysis.md`) per garantire la piena conformità a `ORIGINAL_REQUEST.md` (§ R4, § R1 e Acceptance Criteria), risolvere l'instabilità del compilatore Kotlin in `gradle.properties`, ed eseguire con successo build e test empirici.

## Input Files Obbligatori
1. `c:\Users\marco\Antigravity Progetti\Calediario Turni\.agents\teamwork\ORIGINAL_REQUEST.md` (leggi prima di qualsiasi operazione!)
2. `c:\Users\marco\Antigravity Progetti\Calediario Turni\PROJECT.md`
3. `c:\Users\marco\Antigravity Progetti\Calediario Turni\.agents\teamwork\explorer_m4_iter2_3\analysis.md`
4. `c:\Users\marco\Antigravity Progetti\Calediario Turni\.agents\teamwork\explorer_m4_iter2_2\analysis.md`
5. `android-app/gradle.properties`
6. `android-app/app/src/main/java/com/turni/calendario/ui/screens/GeminiShiftImportScreen.kt`

## File di Proprietà Esclusiva di Scrittura
- `android-app/gradle.properties`
- `android-app/app/src/main/java/com/turni/calendario/ui/screens/GeminiShiftImportScreen.kt`

## Checklist Modifiche
### 1. `android-app/gradle.properties`
Imposta esattamente:
```properties
org.gradle.jvmargs=-Xmx4096m -XX:+UseParallelGC -Dfile.encoding=UTF-8
android.useAndroidX=true
android.nonTransitiveRClass=true
kotlin.code.style=official
kotlin.daemon.jvmargs=-Xmx4096m -XX:+UseParallelGC
```

### 2. `GeminiShiftImportScreen.kt`
- **Stato `isSaving`**: Aggiungi `var isSaving by remember { mutableStateOf(false) }` e usa `enabled = !isSaving` su entrambi i bottoni primario e secondario.
- **Nota Visiva Esplicativa (§ R4)**: Aggiungi subito prima dei pulsanti finali la card Obsidian (`Surface` con `Icons.Default.Info`) con testo:
  *"I turni potranno essere rifiniti in qualsiasi momento nell'apposita sezione 'Catalogo Turni' dell'app."*
- **Pulsante Primario**: Denominalo `"Salva nel catalogo"` con icona `CheckCircle`, debounce con `isSaving` e `CircularProgressIndicator` bianco durante il salvataggio.
- **Pulsante Secondario**: Denominalo `"Modifica"` con icona `EditCalendar` e `onClick = { if (!isSaving) showExitToEditDialog = true }`.
- **AlertDialog Modale di Uscita (§ R4)**:
  - Titolo: `"Attenzione"` con icona ambra `WarningAmber`.
  - Testo verbatim: *"Stai per uscire da questa schermata per passare a quella di modifica, perderai queste informazioni."*
  - Conferma: `"Esci e Modifica"` (salva nel catalogo con `GeminiShiftMapper.registerExtractedData` e naviga `onNavigateToTurni(targetRot)`).
  - Annulla: `"Rimani qui"` (chiude il dialog).
  - Stile Obsidian Liquid Glass: `containerColor = ObsidianSurfaceContainer`, `shape = RoundedCornerShape(20.dp)`.
- **Mascheramento API Key (§ R1)**: Verifica la presenza di `visualTransformation = if (isApiKeyVisible) VisualTransformation.None else PasswordVisualTransformation()` con toggle icon.
- **Vincoli Breccia UX**: Preserva il singolo tasto indietro 40dp in alto a sinistra con `BackHandler`, nessuno in basso, e lo `Spacer(modifier = Modifier.height(100.dp))` in fondo per la Thumb Zone.

## Verifica Empirica Obbligatoria (da eseguire in android-app/ con PowerShell):
1. `$env:JAVA_HOME = "C:\Program Files\Android\Android Studio\jre"; .\gradlew.bat --stop`
2. `$env:JAVA_HOME = "C:\Program Files\Android\Android Studio\jre"; .\gradlew.bat test` -> TUTTI i 223+ test DEVONO risultare verdi (BUILD SUCCESSFUL).
3. `$env:JAVA_HOME = "C:\Program Files\Android\Android Studio\jre"; .\gradlew.bat assembleDebug` -> BUILD SUCCESSFUL, generazione di `android-app/app/build/outputs/apk/debug/app-debug.apk`.

Documenta tutti i comandi ed output nel tuo `handoff.md` e invia un messaggio formale con `send_message`.


## 2026-10-15T00:12:10Z
Sei Worker M4 Iteration 2 (UI Remediation & Build Tuning Specialist).
Il tuo working directory dedicato è:
c:\Users\marco\Antigravity Progetti\Calediario Turni\.agents\teamwork\worker_m4_iter2

Devi leggere OBBLIGATORIAMENTE il file dei requisiti originali prima di qualsiasi azione:
c:\Users\marco\Antigravity Progetti\Calediario Turni\.agents\teamwork\ORIGINAL_REQUEST.md

Leggi anche il piano globale e i report di analisi tecnica:
- c:\Users\marco\Antigravity Progetti\Calediario Turni\PROJECT.md
- c:\Users\marco\Antigravity Progetti\Calediario Turni\.agents\teamwork\worker_m4_iter2\DISPATCH.md
- c:\Users\marco\Antigravity Progetti\Calediario Turni\.agents\teamwork\explorer_m4_iter2_3\analysis.md
- c:\Users\marco\Antigravity Progetti\Calediario Turni\.agents\teamwork\explorer_m4_iter2_2\analysis.md
- c:\Users\marco\Antigravity Progetti\Calediario Turni\.agents\teamwork\reviewer_m4_1\handoff.md
- c:\Users\marco\Antigravity Progetti\Calediario Turni\.agents\teamwork\reviewer_m4_2\handoff.md

File di cui hai la proprietà esclusiva di scrittura:
- android-app/gradle.properties
- android-app/app/src/main/java/com/turni/calendario/ui/screens/GeminiShiftImportScreen.kt
