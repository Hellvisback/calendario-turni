# Handover Tecnico & Guida Architetturale per CodiumAI / Qodo
**Progetto**: Calendario Turni (RFI Pro / SuperShift Evolution)  
**Versione Corrente**: v1.2.3 (versionCode 15)  
**Target Platform**: Android Nativo (Kotlin + Jetpack Compose)  
**Repository Git**: `https://github.com/Hellvisback/calendario-turni.git`

---

## 1. Benvenuto in Calendario Turni!
Questo documento fornisce al modello e all'agente AI (CodiumAI / Qodo Gen) il contesto esaustivo, le scelte architetturali, i vincoli operativi e le logiche di business dell'applicazione.  
**Obiettivo primario**: Continuare lo sviluppo, la manutenzione, il refactoring o l'aggiunta di nuove feature mantenendo rigorosamente la coerenza visiva, l'ergonomia, la robustezza del codice e gli standard di release.

---

## 2. Regole di Comunicazione & Standard Operativi

### A. Comunicazione
1. **Rispondi e comunica SEMPRE in lingua italiana** con l'utente. Tutte le spiegazioni, i riassunti, i messaggi di chat e le descrizioni dei piani devono essere in italiano.
2. **Codice sorgente in inglese**: Nomi di classi, funzioni, variabili, commenti nel codice sorgente, commit Git e comandi shell devono rimanere in lingua inglese standard.

### B. Filosofia UI/UX (Tassativa)
1. **Stile Visivo "Obsidian Liquid Glass"**:
   - Tema scuro OLED ad altissimo contrasto (`Color(0xFF080B11)`).
   - Superfici frosted glass con gradienti semitrasparenti, riflessi prismatici (`prismaticRefractionBrush`) e bordature sottili sfumate.
   - Nessun elemento grezzo o piatto: uso armonioso di forme arrotondate (squircle / RoundedCornerShape 16-24dp).
2. **Ergonomia "Riccardo Breccia UX"**:
   - **Thumb Zone prioritaria**: Tutti i comandi principali, azioni frequenti e controlli critici devono trovarsi nella metà inferiore dello schermo, facilmente raggiungibili con una mano sola.
   - **Dock inferiore fluttuante**: Navigation bar fluttuante e centrata (`LiquidGlassLensNavBar.kt`), non a tutto schermo da bordo a bordo.
   - **Un solo pulsante "Indietro"**: Solo l'icona rotonda in alto a sinistra nella TopBar (`Icons.Default.ArrowBack`, dimensione 38-40dp). **MAI inserire doppi pulsanti "Indietro" in basso o nei footer!**
   - **Notifiche a comparsa**: **MAI inserire banner statici o card rosse/verdi inline che spingono verso il basso il layout della schermata!** Usare sempre il componente `AnimatedStatusPopup` con overlay animato da sotto (`Alignment.BottomCenter`) dotato di auto-dismiss temporizzato.
   - **Micro-interazioni elastiche**: Feedback tattile e di tocco elastico (`bouncyClickable`, spring physics) su icone e selettori.

### C. Standard di Sviluppo & Modifiche al Codice
1. **Principio di Minimo Impatto**: Applica modifiche chirurgiche e contestualizzate (`replace_file_content`), senza mai cancellare file sconsideratamente o riscrivere interi file da zero quando non necessario.
2. **Zero Allucinazioni**: Verifica sempre l'esistenza di metodi, classi o campi in `ShiftRepository.kt` o nei modelli prima di invocarli.
3. **Verifica Empirica**: Ogni modifica al codice deve essere verificata compilando con Gradle (`.\gradlew.bat assembleDebug`).

---

## 3. Ambiente di Sviluppo & Comandi Shell

Tutti i comandi vanno eseguiti in **PowerShell** nella cartella del progetto:

```powershell
# 1. Configurazione Java Home (usare il JRE integrato di Android Studio)
$env:JAVA_HOME = "C:\Program Files\Android\Android Studio\jre"

# 2. Compilazione dell'APK Debug
cd "android-app"
.\gradlew.bat assembleDebug

# 3. Esecuzione Test Unitari
.\gradlew.bat test

# 4. Installazione su dispositivo fisico reale (Realme 8 Pro, ID cb08bc57)
& "C:\Users\marco\AppData\Local\Android\Sdk\platform-tools\adb.exe" -s cb08bc57 install -r "android-app\app\build\outputs\apk\debug\app-debug.apk"

# 5. Avvio applicazione sul telefono
& "C:\Users\marco\AppData\Local\Android\Sdk\platform-tools\adb.exe" -s cb08bc57 shell am start -n com.turni.calendario/.MainActivity

# 6. Screenshot di verifica visiva (usare cmd /c per evitare corruzione binaria UTF-16LE di PowerShell)
cmd.exe /c "`"C:\Users\marco\AppData\Local\Android\Sdk\platform-tools\adb.exe`" -s cb08bc57 exec-out screencap -p > screenshot.png"
```

### Regole Tassative di Release su GitHub:
- **Script Ufficiale di Rilascio**: `powershell -ExecutionPolicy Bypass -File .\scripts\release.ps1`
- **MAI sovrascrivere release con `gh release upload v1.0.0 --clobber`**: Android rifiuta l'aggiornamento dell'app se `versionCode` non aumenta! Inoltre GitHub non notifica l'aggiornamento agli utenti.
- Ogni release deve incrementare `versionCode` (intero progressivo es. 15 -> 16) e `versionName` semantico (es. 1.2.3 -> 1.2.4) in `android-app/app/build.gradle.kts`.
- **REQUISITO TASSATIVO**: Caricare **ESCLUSIVAMENTE** il singolo file APK versionato (`app-release-vX.Y.Z.apk`).
- **MAI caricare `app-release-latest.apk` né `.zip` nelle release di GitHub!**
- **MAI copiare l'APK o sincronizzare su OneDrive!**

---

## 4. Architettura e Albero del Repository

```text
Calediario Turni/
├── AGENTS.md                          # Regole di governance per agenti AI
├── HANDOVER_CODIUM.md                 # Questa guida tecnica per CodiumAI / Qodo
├── HANDOVER_NOTES.md                  # Note storiche e milestone del progetto
├── package.json                       # Configurazione meta-progetto e script release
├── scripts/
│   ├── release.ps1                    # Script ufficiale di compilazione, git push e release GitHub
│   ├── export_project.ps1             # Script per impacchettare il progetto in ZIP pulito
│   ├── make_final_widget_previews.py  # Generatore anteprime visive per i 4 widget Android
│   └── process_logo.py                # Pipeline di ottimizzazione icona e logo
└── android-app/                       # Modulo applicazione Android Studio
    ├── build.gradle.kts               # Configurazione radice Gradle
    ├── settings.gradle.kts
    ├── gradlew.bat
    └── app/
        ├── build.gradle.kts           # Dipendenze Compose, Room, Gson, Coroutines (targetSdk 34)
        └── src/
            ├── test/                  # Test unitari JUnit
            │   └── java/com/turni/calendario/
            │       ├── ShiftCalculationTest.kt
            │       ├── NgeniaTurniParserTest.kt
            │       └── WidgetMultiShiftTest.kt
            └── main/
                ├── AndroidManifest.xml
                ├── res/               # Layout XML dei Widget, drawable vettoriali, mipmap icone
                └── java/com/turni/calendario/
                    ├── MainActivity.kt        # Entry point Compose, permission handler, intent
                    ├── model/
                    │   └── ShiftModels.kt     # Modelli dati principali (ShiftDefinition, AssignedShift, RotationCycle...)
                    ├── repository/
                    │   └── ShiftRepository.kt # SINGLE SOURCE OF TRUTH: salvataggio SharedPreferences/JSON, reattività StateFlow
                    ├── alarm/
                    │   └── ShiftAlarmScheduler.kt # Gestore sveglie intelligenti basate su turni e reperibilità
                    ├── widget/                # 4 Widget nativi per la schermata home di Android
                    │   ├── WidgetUpdateManager.kt         # Notifica e aggiornamento sincrono di tutti i widget
                    │   ├── TodayTomorrowWidgetProvider.kt # Widget 2x2 Oggi & Domani con orari e trasferte
                    │   ├── WeekRowWidgetProvider.kt       # Widget 4x1 Striscia Settimanale
                    │   ├── WeeksPanoramaWidgetProvider.kt # Widget 4x2 Panorama 2 Settimane con indicatori reperibilità
                    │   └── MonthGridWidgetProvider.kt     # Widget 4x3+ Griglia Mese Completo con navigazione
                    ├── sync/
                    │   ├── GoogleCalendarSyncHelper.kt    # Sincronizzazione reale Google Calendar (CalendarContract)
                    │   └── GoogleDriveBackupHelper.kt     # Backup e Restore 1-tap Google Drive
                    ├── pdf/
                    │   └── PdfGenerator.kt                # Esportazione prospetto turni A4 in PDF
                    ├── parser/
                    │   ├── NgeniaTurniParser.kt           # Parser universale tabelloni turni, fogli di calcolo e testo
                    │   └── GeminiShiftVisionHelper.kt     # Parser multimodale intelligente tramite Gemini Flash Vision API
                    └── ui/
                        ├── theme/
                        │   ├── Color.kt                   # Token colore Obsidian OLED, gradienti e colori turni
                        │   ├── Theme.kt
                        │   └── Type.kt
                        ├── components/
                        │   ├── AnimatedStatusPopup.kt     # Notifica HUD overlay non invasiva da sotto
                        │   ├── LiquidGlassLensNavBar.kt   # Bottom navigation bar fluttuante a lente liquida
                        │   └── TurniUiComponents.kt       # Componenti riutilizzabili, chip, badge, bottoni elastici
                        └── screens/
                            ├── MainScreen.kt              # Host delle 4 tab (Calendario, Report, Turni, Altro)
                            ├── CalendarScreen.kt          # TAB 1: Griglia mese interattiva, dettagli giorno, ore e trasferte
                            ├── ReportScreen.kt            # TAB 2: Statistiche ore, straordinari, trasferte (Mese/Anno/Pers)
                            ├── TurniModelsScreen.kt       # TAB 3: Gestione catalogo turni e cicli di rotazione
                            ├── SettingsScreen.kt          # TAB 4: Hub impostazioni ("Altro")
                            ├── SmartAlarmsSettingsScreen.kt # Configurazione sveglie intelligenti
                            ├── ReperibilitaSettingsScreen.kt# Gestione reperibilità singola, su turno o ciclica
                            ├── HolidaysSettingsScreen.kt  # Selezione festività nazionali, religiose e patronali
                            ├── ImportShiftsScreen.kt      # Flusso intelligente importazione turni (Vision AI + Parser)
                            ├── GoogleCalendarSyncScreen.kt# Sincronizzazione Google Calendar reale con cooldown
                            ├── CloudBackupScreen.kt       # Backup cloud su Google Drive
                            ├── PdfExportScreen.kt         # Esportazione PDF in stile SuperShift
                            └── WorkProfileEditorScreen.kt # Editor profilo lavorativo e allegato PDF CCNL
```

---

## 5. Logiche di Business Critiche

### A. Calcolo Turni, Straordinari e Trasferte (`CalendarScreen.kt`, `ShiftRepository.kt`)
1. **Turni Multipli nello Stesso Giorno**: Un singolo giorno può ospitare più turni (es. Mattina + Reperibilità, o Mattina + Notte).
2. **Trasferte e Straordinari Legati al Turno**:
   - Lo straordinario (+1h, +2h, etc.) allunga il turno visivamente con un'area in semitrasparenza azzurra.
   - La trasferta viene visualizzata con un'icona o badge verde affiancato al turno specifico.
3. **Regola Ferie vs Smonto/Riposo (`ReportScreen.kt`)**:
   - Se l'utente applica il turno Ferie sopra uno Smonto Notte o un Riposo, compare visivamente Ferie per comodità, **MA nel calcolo statistico del Report non devono essere contati come giorni di ferie consumati**, bensì conteggiati come smonto/riposo contrattuale.
4. **Badge Ex-Turno**:
   - Viene mostrato in grigio discreto (`ex POM`, `ex NOT`) **esclusivamente** se il giorno apparteneva a una rotazione ciclica ed è stato sovrascritto manualmente. Non appare sui turni inseriti su giorni vuoti. L'utente può nasconderlo o disabilitarlo dalle impostazioni.

### B. I 4 Widget Android (`widget/`)
1. **Today & Tomorrow (2x2)**: Mostra chiaramente il turno di oggi con orari di inizio/fine, straordinari e trasferte, e il turno di domani in forma abbreviata (es. `dom. Mattina`).
2. **Week Row (4x1)**: Striscia dei 7 giorni della settimana con evidenziazione del giorno corrente e icone/lettere turno.
3. **Weeks Panorama (4x2)**: Visualizzazione a 2 settimane (14 giorni) con colorazione semitrasparente arancione per i giorni di reperibilità e frecce di scorrimento in alto a destra.
4. **Month Grid (4x3+)**:
   - Griglia completa del mese con anteprima chiara dei giorni precedenti e successivi in tonalità attenuata.
   - Reperibilità evidenziata con overlay semitrasparente arancione (senza scritte ridondanti).
   - Giorni con più turni visualizzati con appositi pallini colorati indicatori.
   - Frecce di navigazione mese collocate in alto a destra.
   - Altezza minima bloccata a 3 righe (`minResizeHeight` in `widget_month_grid_info.xml`) per evitare tagli grafici.
   - Ogni volta che i dati dei turni cambiano, viene invocato `WidgetUpdateManager.updateAllWidgets(context)`.

### C. Sveglie Intelligenti (`ShiftAlarmScheduler.kt`, `SmartAlarmsSettingsScreen.kt`)
1. Configurazione sveglie differenziata per turno (Mattina, Pomeriggio, Notte, etc.) con anticipo configurabile (es. 60 minuti prima dell'inizio del turno).
2. **Sveglia Reperibilità**: Suona la **sera prima alle 20:00** (orario modificabile con preset rapidi 19:00, 20:00, 21:00).
3. Integrazione con `AlarmManager` di Android e notifica di sincronizzazione.

### D. Sincronizzazione Google Calendar (`GoogleCalendarSyncScreen.kt`)
1. **Cooldown Obbligatorio di 5 Minuti**: Dopo aver sincronizzato o eliminato gli eventi, il pulsante viene bloccato con un conto alla rovescia (es. `Attendi 4:32...`) per impedire doppi click e duplicazione massiva di eventi nel calendario di sistema.
2. **Eliminazione Sicura One-by-One**: Gli eventi dell'app sono identificati dal prefisso `[CalendarioTurni]`. La cancellazione avviene elemento per elemento tramite query degli ID, evitando il popup di sistema Android "Eliminazione di più di 50 eventi".
3. **Notifica forzata al ContentResolver**: Viene invocato `context.contentResolver.notifyChange(CalendarContract.Events.CONTENT_URI, null)`.

### E. Importazione Intelligente con Gemini Flash Vision (`ImportShiftsScreen.kt`, `GeminiShiftVisionHelper.kt`)
1. Supporta il caricamento di immagini, screenshot o documenti PDF (convertiti nativamente in Bitmap tramite `PdfRenderer`).
2. Invio dell'immagine a Google Gemini Flash con schema JSON strutturato: estrazione automatica di squadre, rotazioni cicliche, orari e condizioni di reperibilità.
3. Se l'utente inserisce a monte il proprio nome, la squadra di appartenenza viene automaticamente individuata e pre-selezionata con la stellina ⭐.
4. Carosello guidato a step per spiegare all'utente il flusso in modo semplice ed ergonomico.

---

## 6. Checklist per Qualsiasi Task di Sviluppo
Prima di considerare concluso qualsiasi intervento:
- [ ] Il codice compila senza errori con `.\gradlew.bat assembleDebug` in `android-app/`.
- [ ] Nessun linter o test unitario è rotto (`.\gradlew.bat test`).
- [ ] Il design rispetta Obsidian Liquid Glass e l'ergonomia Breccia (nessun popup statico, nessun doppio pulsante back).
- [ ] Se vengono modificate le logiche dei turni, verificare che i Widget si aggiornino tramite `WidgetUpdateManager`.
- [ ] Ogni nuova release su GitHub viene effettuata tramite `powershell -ExecutionPolicy Bypass -File .\scripts\release.ps1` con incremento di `versionCode`.
