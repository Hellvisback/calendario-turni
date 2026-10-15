# Handoff Report — Explorer 2 (UI Architecture, Navigation & Design System)

**Date**: 2026-10-14  
**Agent**: Explorer 2 (`teamwork_preview_explorer`)  
**Scope**: Indagine UI, Navigazione e Design System per l'integrazione della nuova schermata "Gemini Shift Import Screen"  
**Working Directory**: `c:\Users\marco\Antigravity Progetti\Calediario Turni\.agents\teamwork\explorer_survey_2`  
**Target Recipient**: Project Orchestrator (`90066a2c-0ee0-47dc-9cce-f552c7e66ada`)  

---

## 1. Observation

1. **Punto d'Ingresso e Host di Navigazione**:
   - In `android-app/app/src/main/java/com/turni/calendario/MainActivity.kt:15-19`:
     ```kotlin
     setContent {
         CalendarioTurniTheme {
             MainScreen()
         }
     }
     ```
   - In `android-app/app/src/main/java/com/turni/calendario/ui/screens/MainScreen.kt:37-42`:
     La barra a 4 tab gestisce: `Calendario`, `Report`, `Turni`, `Altro`.
   - In `MainScreen.kt:47-50, 137-145`:
     Lo stato `isSubScreenActive` controlla la visibilità del Floating Navigation Dock (`LiquidGlassLensNavBar`): quando un sub-screen è attivo, la barra scivola dolcemente verso il basso (`slideOutVertically`) e scompare.

2. **Rotte Interne e Sotto-Schermate in `SettingsScreen.kt`**:
   - In `android-app/app/src/main/java/com/turni/calendario/ui/screens/SettingsScreen.kt:31-40`:
     ```kotlin
     enum class SettingsSubScreen {
         NONE, CLOUD_BACKUP, GOOGLE_CALENDAR_SYNC, IMPORT_SHIFTS, PDF_EXPORT, REPERIBILITA, SMART_ALARMS, HOLIDAYS
     }
     ```
   - In `SettingsScreen.kt:78-86`:
     ```kotlin
     SettingsSubScreen.IMPORT_SHIFTS -> {
         ImportShiftsScreen(
             onDismiss = { activeSubScreen = SettingsSubScreen.NONE },
             onNavigateToEdit = { rotation ->
                 activeSubScreen = SettingsSubScreen.NONE
                 onNavigateToTurni(rotation)
             }
         )
     }
     ```
   - In `SettingsScreen.kt:255-260`:
     ```kotlin
     SettingsRow(
         icon = Icons.Default.UploadFile,
         title = "Importazione Turni (Excel / CSV)",
         subtitle = "Carica o incolla turni da file o tabella",
         onClick = { activeSubScreen = SettingsSubScreen.IMPORT_SHIFTS }
     )
     ```

3. **Design System "Obsidian Liquid Glass"**:
   - In `android-app/app/src/main/java/com/turni/calendario/ui/theme/Color.kt:7-15`:
     Token dark OLED definiti:
     - `ObsidianBackground = Color(0xFF080B11)`
     - `ObsidianSurface = Color(0xFF111622)`
     - `ObsidianSurfaceContainer = Color(0xFF171E2D)`
     - `ObsidianSurfaceHigh = Color(0xFF20293D)`
     - `ObsidianSurfaceHighest = Color(0xFF2B3751)`
     - `ObsidianBorder = Color(0xFF1E273A)`
     - `ObsidianPrimary = Color(0xFF38BDF8)`
   - In `android-app/app/src/main/java/com/turni/calendario/ui/components/TurniUiComponents.kt:54-69, 131-226`:
     Pennello di dispersione cromatica `prismaticRefractionBrush` e contenitore traslucido `FluidGlassContainer` con cresta speculare zenitale e caustiche interne.

4. **Pattern Ergonomici "Riccardo Breccia UX"**:
   - In `TurniUiComponents.kt:89-120`:
     Modificatore `Modifier.bouncyClickable` basato su `animateFloatAsState(targetValue = if (isPressed && enabled) 0.92f else 1.0f)` e vibrazione aptica `LocalHapticFeedback.current.performHapticFeedback(HapticFeedbackType.TextHandleMove)`.
   - In `ImportShiftsScreen.kt:1139-1180`:
     Componente `ElasticStarButton` con micro-animazione elastica a molla `scale.snapTo(0.72f)` -> `scale.animateTo(1f, spring(dampingRatio = MediumBouncy, stiffness = Low))` e toggle a due stati (attivo/neutro) senza etichette testuali ridondanti.
   - Disciplina del pulsante indietro in `ImportShiftsScreen.kt:143-157`:
     Singolo pulsante freccia in alto a sinistra (`Surface(onClick = onDismiss, shape = CircleShape, modifier = Modifier.size(44.dp))`), zero pulsanti "Indietro" o "Annulla" in basso.

5. **Implementazione di `AnimatedStatusPopup`**:
   - In `android-app/app/src/main/java/com/turni/calendario/ui/components/AnimatedStatusPopup.kt:61-67, 159-281`:
     Overlay animato ancorato in `Alignment.BottomCenter` con `navigationBarsPadding()`.
     Non deforma né altera l'altezza del layout sottostante.
     Supporta 4 varianti (`SUCCESS` con coriandoli su `Canvas`, `ERROR` con haptic shake orizzontale a 3 cicli, `WARNING`, `INFO`).

6. **Infrastruttura di Sistema & Permessi**:
   - In `android-app/app/src/main/AndroidManifest.xml:4-12`:
     Mancanza del tag `<uses-permission android:name="android.permission.INTERNET" />`.

7. **Configurazione Dipendenze Gradle**:
   - In `android-app/app/build.gradle.kts:52-66`:
     `compileSdk = 32`, `minSdk = 26`, Jetpack Compose 1.2.0, Material 3 1.0.0-alpha14.
     Nessuna libreria terza di networking o image loading: la conversione PDF avviene nativamente tramite `android.graphics.pdf.PdfRenderer` (disponibile da API 21), visualizzabile direttamente tramite `bitmap.asImageBitmap()`.

---

## 2. Logic Chain

1. **Compatibilità di Navigazione**:  
   Dall'osservazione (1) e (2), `MainScreen` orchestra 4 tab e gestisce i sotto-schermi tramite `isSubScreenActive`. L'apertura della nuova schermata di importazione all'interno di `SettingsScreen` tramite `SettingsSubScreen.IMPORT_SHIFTS` garantisce che il Floating Dock si nasconda automaticamente, che il `BackHandler` gestito ripristini la home delle impostazioni e che non vengano alterate le altre 3 sezioni dell'app.

2. **Aderenza al Design System Obsidian Liquid Glass**:  
   Dall'osservazione (3), tutti i nuovi elementi devono utilizzare `ObsidianSurface` per la card principale a monte, `ObsidianSurfaceContainer` per i campi di input annidati, `ObsidianPrimary` per focus e accenti, e `TextPrimary` (`#F8FAFC`) / `TextSecondary` (`#94A3B8`) per il rispetto del contrasto WCAG AAA.

3. **Prevenzione del Layout Shifting (Regola Agenti & Breccia UX)**:  
   Dall'osservazione (5) e dalle regole di sistema, è vietato inserire banner statici o alert inline che spostano i campi di testo. Qualsiasi messaggio informativo, quota esaurita (429), chiave non valida (400/403) o successo di parsing deve essere veicolato esclusivamente tramite `AnimatedStatusPopup` ancorato a `BottomCenter`.

4. **Autonomia Multimediale Zero-Dipendenze**:  
   Dall'osservazione (7), il progetto non ha Coil né Glide. Tuttavia, con `minSdk = 26`, `android.graphics.pdf.PdfRenderer` è parte integrante del framework Android nativo. Eseguendo il rendering della prima pagina su un `android.graphics.Bitmap` e convertendolo in `ImageBitmap` con `.asImageBitmap()`, l'anteprima miniatura documento/PDF è realizzabile senza aggiungere dipendenze esterne.

5. **Prerequisito Bloccante di Rete**:  
   Dall'osservazione (6), per effettuare richieste REST verso le API di Google Gemini Flash è indispensabile dichiarare `<uses-permission android:name="android.permission.INTERNET" />` in `AndroidManifest.xml`.

---

## 3. Caveats

1. **Ambito di Indagine**:  
   Questa indagine è di tipo **Read-Only** sull'architettura UI, navigazione e design system. L'effettiva implementazione delle classi client REST per Gemini (es. HTTP client, serializzazione JSON, prompt multimodale Base64) compete alla successiva fase esecutiva.
2. **Archiviazione Sicura della Chiave API**:  
   Per memorizzare l'API Key sul dispositivo (minSdk 26), si presuppone l'utilizzo delle `SharedPreferences` in modalità privata (`Context.MODE_PRIVATE`) o `EncryptedSharedPreferences`, integrata con un mascheramento visivo in UI (`PasswordVisualTransformation`) e toggle di visibilità.
3. **Dimensione Bitmap per PDF Complessi**:  
   Nei file PDF ad altissima risoluzione, la bitmap renderizzata da `PdfRenderer` per l'invio all'API Base64 deve essere opportunamente dimensionata per evitare `OutOfMemoryError` (consigliata scala con lato massimo tra 1500px e 2048px).

---

## 4. Conclusion

L'architettura Jetpack Compose dell'app è perfettamente strutturata per accogliere la nuova **Gemini Shift Import Screen**.  
Il punto d'innesto designato è `SettingsScreen.kt` tramite la rotta `SettingsSubScreen.IMPORT_SHIFTS`.  
Il layout della schermata deve strutturarsi in:
1. **Top Bar Breccia**: singolo pulsante back rotondo in alto a sinistra (44dp).
2. **Pannello Unificato a Monte (R5)**:
   - Box inserimento/modifica API Key con `PasswordVisualTransformation` e toggle visibilità.
   - Campo filtro nominativo turnista a monte ("Nome da cercare nel file").
   - Selettore file universale (immagini e PDF).
   - Card miniatura anteprima con rendering Bitmap nativo (`PdfRenderer`).
   - Tasto azione primaria "Analizza con Gemini Flash" con feedback elastico e indicatore di caricamento non bloccante.
3. **Card Risultati**: riepilogo orari estratti, lista squadre con `ElasticStarButton` per la selezione del predefinito ed evidenziazione automatica della squadra del lavoratore cercato.
4. **Azioni di Chiusura e Salvaguardia**: "Salva nel catalogo" e "Modifica" (protetta da dialog modale di avviso di uscita).
5. **Notifiche**: interamente delegate ad `AnimatedStatusPopup` in overlay sul fondo.
6. **Fix Obbligatorio**: inserimento di `<uses-permission android:name="android.permission.INTERNET" />` in `AndroidManifest.xml`.

---

## 5. Verification Method

Per verificare in modo indipendente le osservazioni e l'architettura riscontrata:

1. **Verifica File Sorgenti e Linee di Codice**:
   - Ispezionare `android-app/app/src/main/java/com/turni/calendario/ui/screens/MainScreen.kt` (righe 37-55, 137-145).
   - Ispezionare `android-app/app/src/main/java/com/turni/calendario/ui/screens/SettingsScreen.kt` (righe 31-40, 78-86, 255-260).
   - Ispezionare `android-app/app/src/main/java/com/turni/calendario/ui/components/AnimatedStatusPopup.kt` (righe 61-67, 159-170).
   - Ispezionare `android-app/app/src/main/java/com/turni/calendario/ui/components/TurniUiComponents.kt` (righe 89-120).
   - Verificare `android-app/app/src/main/AndroidManifest.xml` (assenza del permesso INTERNET).

2. **Verifica della Compilazione del Progetto**:
   Eseguire da PowerShell il comando Gradle per verificare lo stato di salute della build:
   ```powershell
   cd "c:\Users\marco\Antigravity Progetti\Calediario Turni\android-app"
   $env:JAVA_HOME = "C:\Program Files\Android\Android Studio\jre"
   .\gradlew.bat assembleDebug
   ```
   *Esito atteso*: `BUILD SUCCESSFUL`.

3. **Verifica della Suite di Test Unitari**:
   ```powershell
   .\gradlew.bat test
   ```
   *Esito atteso*: 31/31 test superati con successo (`BUILD SUCCESSFUL`).
