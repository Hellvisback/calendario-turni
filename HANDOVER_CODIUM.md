# Handover Tecnico & Guida Architetturale per CodiumAI / Qodo
**Progetto**: Calendario Turni (RFI Pro / SuperShift Evolution)  
**Versione Corrente**: v1.3.0 (versionCode 16, installata sul Realme il 25 ottobre 2026; pubblicazione GitHub non eseguita)  
**Target Platform**: Android Nativo (Kotlin + Jetpack Compose)  
**Parametri SDK**: compileSdk 32, minSdk 26, targetSdk 32, Java 11  
**Repository Git**: `https://github.com/Hellvisback/calendario-turni.git`  
**Branch Principale**: `main`  
**Ambiente di Lavoro**: Locale su macchina Windows dell'utente (`c:\Users\marco\Antigravity Progetti\Calediario Turni`)

---

## Aggiornamento nativo del 25 ottobre 2026

Il prototipo TurniSync è stato portato nelle schermate native Compose: calendario, dock a due destinazioni, modifica compatta, dettagli giorno/turno, editor di lavori/modelli/rotazioni, statistiche dinamiche e impostazioni. I componenti principali aggiunti sono `NativeMonthCanvas`, `NativeCalendarPanels`, `CatalogEditorComponents`, `NativeColorPlane` e `TurniDock`. Le vecchie funzioni editor rinominate `Legacy...` non sono i punti di ingresso attivi.

`TurniSyncApplication` inizializza `LocalDatabaseStore` prima delle schermate. Il database atomico `files/turnisync-database-v3.json` conserva lo stato completo; in sua assenza viene migrato il backup locale precedente senza eliminarlo. Scritture successive, widget e aggiornamenti delle sveglie sono eseguiti fuori dal thread UI. La migrazione della palette modifica solo colori predefiniti noti, preservando quelli personalizzati.

Verificati: build APK, 264 test per variante debug/release, installazione sul dispositivo cb08bc57, scorrimenti brevi dal centro della griglia, straordinario da stacco effettivo, trasferta per turno, rotazioni con anteprima, periodo bidirezionale, gomma selettiva, Annulla e persistenza dopo riavvio. Sul telefono restano 122 assegnazioni, 11 modelli e un lavoro. Non sono state collaudate chiamate reali a Google Calendar/Drive/Gemini né l'erogazione effettiva delle sveglie. L'APK è firmato con la chiave debug esistente; non è stata pubblicata una release GitHub.

---

## 1. Obiettivo del Progetto & Ambito di Sviluppo
L'utente sviluppa localmente su questo PC questa applicazione Android nativa.
L'obiettivo primario dei prossimi interventi con **CodiumAI / Qodo** è la **trasformazione e perfezionamento radicale dell'interfaccia grafica (UI/UX)**:
- Adozione piena del design system **Material 3 Expressive** (Android 15/16).
- Interfaccia pulita, geometricamente precisa ed ergonomica.
- Esperienza utente visivamente appagante, con **animazioni fluide basate su fisica (springs)**, micro-interazioni elastiche al tocco e rifrazione **Obsidian Liquid Glass**.
- Preservazione totale delle logiche di business esistenti (turni, rotazioni, ore, straordinari, trasferte, sveglie, sincronizzazione e widget).

---

## 2. Architettura Operativa Multi-Agente (Orchestratore & Sub-Agenti)

L'assistente AI opera secondo un modello gerarchico ad agenti specializzati per garantire massima precisione ed efficienza dei token:

```mermaid
graph TD
    User["Utente (Lingua Italiana)"] --> Orchestrator["Master Orchestrator (Modello High-Tier: Claude 3.5 Sonnet / GPT-4o)"]
    Orchestrator --> AgentUI["Sub-Agente UI/UX Motion & Expressive (Material 3 Expressive, Springs, Shaders)"]
    Orchestrator --> AgentReview["Sub-Agente Code Quality & Architecture Reviewer"]
    Orchestrator --> AgentBuild["Sub-Agente Build & Diagnostics (Gradle / JUnit / Lint)"]
    Orchestrator --> AgentDevOps["Sub-Agente DevOps & Release (GitHub CLI / Script release.ps1)"]
```

### Ruoli Operativi:
1. **Master Orchestrator (Agente Principale)**:
   - **Modello Consigliato**: Modello ad alto ragionamento (*Claude 3.5 Sonnet* o *GPT-4o*).
   - **Responsabilità**: Interagisce in **italiano** con l'utente, analizza i requisiti, scompone i compiti complessi, coordina i sub-agenti specializzati e controlla la coerenza globale prima di dare conferma.
2. **Sub-Agente UI/UX Motion & Expressive Designer**:
   - **Responsabilità**: Progetta e rifinisce schermate e componenti Jetpack Compose. Implementa animazioni con molle (`spring physics`), forme dinamiche (`shape morphing`), feedback aptici ed elastici al tocco (`bouncyClickable`), gradienti e trasparenze Obsidian Liquid Glass.
3. **Sub-Agente Code Quality & Architecture Reviewer**:
   - **Responsabilità**: Revisiona ogni modifica prima del build: verifica che non vi siano regressioni, allucinazioni di classi/metodi o violazioni delle regole di design (es. nessun banner statico, nessun doppio pulsante indietro).
4. **Sub-Agente Build & Diagnostics**:
   - **Responsabilità**: Esegue la compilazione Gradle (`assembleDebug`) e la suite di test unitari (`.\gradlew.bat test`), diagnosticando e correggendo chirurgicamente eventuali warning o errori.
5. **Sub-Agente DevOps & Release**:
   - **Responsabilità**: Esegue i commit Git e pubblica le release su GitHub esclusivamente tramite `powershell -ExecutionPolicy Bypass -File .\scripts\release.ps1`.

---

## 3. Direzione UI/UX: Material 3 Expressive + Obsidian Liquid Glass + Riccardo Breccia

L'utente richiede un salto di qualità visivo. Ecco i pilastri estetici ed ergonomici vincolanti:

### A. Material 3 Expressive (Standard Google Android 15/16)
1. **Fisica del Movimento (Spring Motion)**:
   - Bandite le transizioni rigide o lineari.
   - Usare sempre curve di molla realistiche di Compose:
     `spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessMedium)` o `StiffnessLow`.
   - Transizioni organiche all'apertura dei fogli dal basso (bottom sheet), dialoghi, selettori e cambi mese.
2. **Forme Dinamiche ed Espressive (Squircle & Dynamic Containers)**:
   - Raggi di curvatura generosi e armonici (`RoundedCornerShape(16.dp)`, `24.dp` o `CircleShape`).
   - Contenitori che si espandono e contraggono morbidamente in base al contenuto, senza scatti improvvisi.
3. **Tipografia e Contrasto Espressivo**:
   - Gerarchia visiva immediata: codici turno (M, P, N, RIP) e orari leggibili a colpo d'occhio anche da lontano o in condizioni di forte luce.
4. **Micro-interazioni Tattili Appaganti**:
   - Ogni elemento cliccabile deve reagire con feedback elastico (`bouncyClickable`), un leggero bagliore colorato (glow) coerente con la tipologia di turno e animazioni morbide.

### B. Obsidian Liquid Glass (Design System Fondamentale)
1. **Superfici Scure OLED**: Sfondo nero profondo `#080B11` per valorizzare i display AMOLED e contenere i consumi.
2. **Effetto Frosted & Rifrazione Prismatica**: Trasparenze vitree sfumate con riflessi iridescenti prismatici (`prismaticRefractionBrush`) e bordature sottili a gradiente.

### C. Ergonomia Riccardo Breccia UX (Regole Vincolanti)
1. **Thumb Zone Assoluta**: Tutti i comandi principali, selettori di turni, pulsanti di azione e palette devono trovarsi nella metà inferiore dello schermo, facilmente utilizzabili con una sola mano.
2. **Dock Inferiore Fluttuante**: La barra di navigazione inferiore (`LiquidGlassLensNavBar.kt`) deve essere una capsula/dock fluttuante centrata, non estesa rigidamente da bordo a bordo.
3. **Un Solo Pulsante "Indietro"**: Sempre e solo l'icona rotonda in alto a sinistra nella TopBar (`Icons.Default.ArrowBack`, 38-40dp). **MAI inserire doppi tasti "Indietro" o "Annulla" in fondo alle schermate!**
4. **Divieto di Spostamento Layout (Layout Shift Prohibition)**:
   - **TASSATIVO**: Non mostrare mai card o banner statici (verdi o rossi) all'interno del flusso della pagina che "spingono" verso il basso il layout circostante.
   - Qualsiasi notifica, conferma o errore deve apparire tramite `AnimatedStatusPopup` in overlay animato dal basso (`Alignment.BottomCenter`) con dissolvenza ed auto-chiusura temporizzata.

---

## 4. Guida alle Directory & Mappa dei File Sorgente

L'assistente sa esattamente in quale cartella e file intervenire in base al tipo di task:

```text
c:\Users\marco\Antigravity Progetti\Calediario Turni\
│
├── AGENTS.md                          # Regole tassative di sistema per AI e release
├── HANDOVER_CODIUM.md                 # Questo documento tecnico di riferimento
├── HANDOVER_NOTES.md                  # Milestone storiche e note di progetto
├── package.json                       # Metadati e script
│
├── scripts/                           # Script PowerShell e Python di supporto
│   ├── release.ps1                    # SCRIPT UFFICIALE RELEASE: build, commit, tag e gh release
│   ├── make_final_widget_previews.py  # Script Pillow per rendering anteprime widget
│   └── process_logo.py                # Pipeline grafica per icone e logo
│
├── esApp/                             # Screenshot storici SuperShift di riferimento
│
└── android-app/                       # Root del progetto Android Studio
    ├── build.gradle.kts               # Configurazione di build root
    ├── settings.gradle.kts            # Configurazione moduli Gradle
    ├── gradlew.bat                    # Gradle wrapper per Windows
    │
    └── app/
        ├── build.gradle.kts           # compileSdk 32, minSdk 26, targetSdk 32, versionCode 15, versionName "1.2.3"
        └── src/
            │
            ├── test/java/com/turni/calendario/ # TEST UNITARI (JUnit)
            │   ├── ShiftCalculationTest.kt    # Test calcolo ore, straordinari e ferie
            │   ├── NgeniaTurniParserTest.kt   # Test parsing tabelloni e PDF
            │   └── WidgetMultiShiftTest.kt    # Test logica turni multipli sui widget
            │
            └── main/
                ├── AndroidManifest.xml        # Permessi, provider widget, servizi, activity
                ├── res/                       # Risorse grafiche, drawable, layout widget XML
                │   ├── layout/                # Layout XML dei 4 Widget Android
                │   │   ├── widget_today_tomorrow.xml   # Widget 2x2 Oggi/Domani
                │   │   ├── widget_week_row.xml         # Widget 4x1 Striscia Settimana
                │   │   ├── widget_weeks_panorama.xml   # Widget 4x2 Panorama 2 Settimane
                │   │   └── widget_month_grid.xml       # Widget 4x3+ Griglia Mese Completo
                │   └── xml/                   # Configurazione AppWidgetProviderInfo
                │       └── widget_month_grid_info.xml  # Blocco altezza minima (minResizeHeight 3 rows)
                │
                └── java/com/turni/calendario/ # SORGENTI KOTLIN
                    ├── MainActivity.kt        # Entry point Compose, navigation host, permissions
                    │
                    ├── model/                 # MODELLI DATI
                    │   └── ShiftModels.kt     # ShiftDefinition, AssignedShift, RotationCycle, WorkProfile
                    │
                    ├── repository/            # SINGLE SOURCE OF TRUTH
                    │   └── ShiftRepository.kt # Salvataggio SharedPreferences/JSON, StateFlow reattivi, logiche turni
                    │
                    ├── ui/
                    │   ├── theme/             # DESIGN SYSTEM & TOKEN
                    │   │   ├── Color.kt       # Colori Obsidian OLED, colori turni (Mattina, Pomeriggio, Notte, Riposo)
                    │   │   ├── Theme.kt       # Tema Material 3 Compose
                    │   │   └── Type.kt        # Tipografia e scale di testo
                    │   │
                    │   ├── components/        # COMPONENTI RIUTILIZZABILI
                    │   │   ├── AnimatedStatusPopup.kt   # Overlay popup animato dal basso (success/error/info)
                    │   │   ├── LiquidGlassLensNavBar.kt # Bottom navigation bar fluttuante a lente liquida
                    │   │   └── TurniUiComponents.kt     # Chip, badge straordinari/trasferte, bottoni elastici
                    │   │
                    │   └── screens/           # LE SCHERMATE PRINCIPALI DELL'APP
                    │       ├── MainScreen.kt            # Contenitore delle 4 tab principali
                    │       ├── CalendarScreen.kt        # TAB 1: Calendario mensile, griglia giorni, editor turno, ore
                    │       ├── ReportScreen.kt          # TAB 2: Statistiche ore, straordinari, trasferte (Mese/Anno/Pers)
                    │       ├── TurniModelsScreen.kt     # TAB 3: Modelli turno e cicli di rotazione
                    │       ├── SettingsScreen.kt        # TAB 4: Hub impostazioni ("Altro")
                    │       ├── SmartAlarmsSettingsScreen.kt # Schermata configurazione sveglie intelligenti
                    │       ├── ReperibilitaSettingsScreen.kt# Schermata configurazione reperibilità
                    │       ├── HolidaysSettingsScreen.kt# Schermata selezione festività
                    │       ├── ImportShiftsScreen.kt    # Schermata importazione universale turni (carosello guidato)
                    │       ├── GeminiShiftImportScreen.kt # Schermata import intelligente con Gemini Flash Vision
                    │       ├── GoogleCalendarSyncScreen.kt# Schermata sincronizzazione Google Calendar con cooldown
                    │       ├── CloudBackupScreen.kt     # Schermata backup Google Drive
                    │       ├── PdfExportScreen.kt       # Schermata esportazione PDF
                    │       └── WorkProfileEditorScreen.kt# Schermata profilo lavoro e visualizzatore PDF CCNL
                    │
                    ├── widget/                # I 4 WIDGET ANDROID NATIVI
                    │   ├── WidgetUpdateManager.kt         # Notifica sincrona di aggiornamento di tutti i widget
                    │   ├── TodayTomorrowWidgetProvider.kt # Provider Widget 2x2
                    │   ├── WeekRowWidgetProvider.kt       # Provider Widget 4x1
                    │   ├── WeeksPanoramaWidgetProvider.kt # Provider Widget 4x2
                    │   └── MonthGridWidgetProvider.kt     # Provider Widget 4x3+
                    │
                    ├── alarm/                 # SVEGLIE INTELLIGENTI
                    │   ├── ShiftAlarmScheduler.kt         # Pianificatore sveglie automatiche in base al turno
                    │   └── ShiftAlarmReceiver.kt          # BroadcastReceiver di sistema per scatto allarmi
                    │
                    ├── data/                  # NETWORKING & GEMINI VISION API
                    │   ├── GeminiApiClient.kt             # Client REST multimodale verso Gemini Flash
                    │   ├── GeminiShiftModels.kt           # Schema JSON strutturato per output Gemini
                    │   ├── GeminiShiftMapper.kt           # Mappatura da JSON a modelli interni
                    │   ├── DocumentProcessor.kt           # Rendering PDF nativo in Bitmap (PdfRenderer)
                    │   └── SecurePreferencesManager.kt    # Salvataggio sicuro API Key
                    │
                    ├── importer/              # PARSER TABELLONI
                    │   └── NgeniaTurniParser.kt           # Parser universale testo, CSV, tabelle e turnisti
                    │
                    ├── sync/                  # SINCRONIZZAZIONI ESTERNE
                    │   ├── GoogleCalendarSyncHelper.kt    # Sincronizzazione reale Google Calendar (CalendarContract)
                    │   └── GoogleDriveBackupHelper.kt     # Backup e ripristino Google Drive
                    │
                    └── export/                # ESPORTAZIONE PDF
                        └── PdfGenerator.kt                # Generazione PDF prospetti A4 stile SuperShift
```

---

## 5. Comandi Shell Rapidi (PowerShell su questa macchina)

```powershell
# Imposta Java Home di Android Studio
$env:JAVA_HOME = "C:\Program Files\Android\Android Studio\jre"

# Compilazione debug
cd "android-app"
.\gradlew.bat assembleDebug

# Esecuzione test unitari
.\gradlew.bat test

# Installazione su Realme 8 Pro (cb08bc57) via ADB
& "C:\Users\marco\AppData\Local\Android\Sdk\platform-tools\adb.exe" -s cb08bc57 install -r "android-app\app\build\outputs\apk\debug\app-debug.apk"

# Avvio app
& "C:\Users\marco\AppData\Local\Android\Sdk\platform-tools\adb.exe" -s cb08bc57 shell am start -n com.turni.calendario/.MainActivity

# Cattura screenshot da riga di comando
cmd.exe /c "`"C:\Users\marco\AppData\Local\Android\Sdk\platform-tools\adb.exe`" -s cb08bc57 exec-out screencap -p > screen.png"
```

### Script Ufficiale di Rilascio:
Per ogni rilascio su GitHub:
`powershell -ExecutionPolicy Bypass -File .\scripts\release.ps1`
(incrementa automaticamente `versionCode`, aggiorna `versionName`, compila, committa, pusha e crea la nuova release GitHub allegando solo il singolo APK versionato).

---

## 6. Regole Tassative di Governance
1. **Rispondere sempre in italiano** all'utente.
2. **Codice, nomi e comandi in inglese**.
3. **MAI toccare o salvare su OneDrive**.
4. **MAI sovrascrivere release GitHub con `--clobber`**; incrementare sempre `versionCode`.
5. **Non creare MAI banner statici** che provocano layout shift.
6. **Un solo tasto indietro in alto a sinistra** (mai duplicati in basso).
