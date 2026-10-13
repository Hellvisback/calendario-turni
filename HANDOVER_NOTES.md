# Handover Tecnico e Guida di Progetto: Calendario Turni (RFI Pro)

Benvenuto! Questo documento contiene tutto ciò che serve a un agente AI (Antigravity) o a uno sviluppatore per continuare lo sviluppo di questa applicazione Android senza perdere il contesto, le scelte architetturali e i requisiti dell'utente.

---

## 1. Panoramica del Progetto
- **Nome Progetto**: Calendario Turni (RFI Pro / SuperShift Evolution)
- **Scopo**: Applicazione Android avanzata per ferrovieri (RFI / Trenitalia / Imprese Ferroviarie) e turnisti per la gestione, calcolo ore, rotazioni a turni (cicli 28 giorni), reperibilità software/ferroviaria, sincronizzazione Google Calendar e Google Drive, esportazione PDF in stile SuperShift, e sveglie automatiche.
- **Linguaggio & Framework**: Kotlin 1.9+, Android SDK 34 (minSdk 26), Jetpack Compose, Material 3 Expressive.
- **Filosofia UI/UX**:
  - **Design Riccardo Breccia**: Ergonomia della Thumb Zone (controlli in basso facilmente raggiungibili con una mano sola, niente pulsanti critici in alto a destra irraggiungibili), nessun carico cognitivo superfluo, feedback tattile elastico (`bouncyClickable`), chip informativi chiari.
  - **Obsidian Liquid Glass**: Tema dark OLED (`#080B11`), riflessi prismatici (`prismaticRefractionBrush`), frosted glass, pillole fluttuanti e gradienti ad alto contrasto.
  - **Nessun doppio pulsante "Indietro"**: solo il tasto rotondo in alto a sinistra nella TopBar (`38-40dp`).
  - **Notifiche & Popup**: Nessun banner verde/rosso statico che "shifta" o sposta il layout verso il basso! Usare sempre `AnimatedStatusPopup` posizionato in overlay dal basso (`Alignment.BottomCenter`) con slide/fade e auto-dismiss.

---

## 2. Struttura delle Directory
```text
Calediario Turni/
├── AGENTS.md                          # Regole di sistema per agenti AI
├── HANDOVER_NOTES.md                  # Questo documento di passaggio consegne
├── esApp/                             # Screenshot di riferimento SuperShift forniti dall'utente
├── .agents/                           # Framework ECC (skill, regole, subagent)
└── android-app/                       # Progetto Gradle Android Studio
    ├── build.gradle.kts
    ├── settings.gradle.kts
    ├── gradlew.bat
    └── app/
        ├── build.gradle.kts
        └── src/main/
            ├── AndroidManifest.xml
            └── java/com/turni/calendario/
                ├── MainActivity.kt
                ├── model/
                │   └── ShiftModels.kt         # Modelli dati: ShiftDefinition, AssignedShift, RotationCycle...
                ├── repository/
                │   └── ShiftRepository.kt     # Single Source of Truth: salvataggio, turni, rotazioni, preferenze
                ├── sync/
                │   ├── GoogleCalendarSyncHelper.kt  # Sincronizzazione reale Google Calendar (CalendarContract)
                │   └── GoogleDriveBackupHelper.kt   # Backup 1-tap su Google Drive (JSON cifrato/formattato)
                ├── pdf/
                │   └── PdfGenerator.kt        # Generazione PDF A4 con prospetti Elenco, Mese, Anno
                ├── parser/
                │   └── NgeniaTurniParser.kt   # Parser intelligente tabelloni turni RFI e PDF
                └── ui/
                    ├── theme/
                    │   ├── Color.kt           # Token Obsidian, colori turni (Mattina, Pomeriggio, Notte...)
                    │   └── Theme.kt
                    ├── components/
                    │   ├── AnimatedStatusPopup.kt   # Popup overlay animato da sotto (SUCCESS/ERROR/INFO)
                    │   ├── LiquidGlassLensNavBar.kt # Dock inferiore a 4 tab con effetto lente liquida
                    │   └── TurniUiComponents.kt     # Componenti riutilizzabili, chip, pennelli di rifrazione
                    └── screens/
                        ├── MainScreen.kt              # Host delle 4 tab principali
                        ├── CalendarScreen.kt          # TAB 1: Calendario mensile con griglia, ore, dettagli giorno
                        ├── ReportScreen.kt            # TAB 2: Statistiche, ore totali, straordinari, trasferte
                        ├── TurniModelsScreen.kt       # TAB 3: Gestione modelli turno e cicli di rotazione
                        ├── SettingsScreen.kt          # TAB 4: Hub impostazioni ("Altro")
                        ├── GoogleCalendarSyncScreen.kt# Sincronizzazione Google Calendar reale con cooldown
                        ├── CloudBackupScreen.kt       # Backup & Restore 1-tap Google Drive
                        ├── ImportShiftsScreen.kt      # Import tabellone RFI / 'ngeniaTurni
                        ├── PdfExportScreen.kt         # Esportazione PDF prospetto turni
                        ├── SmartAlarmsSettingsScreen.kt# Sveglie intelligenti turni e reperibilità
                        ├── HolidaysSettingsScreen.kt  # Selezione festività nazionali, religiose e CCNL
                        └── ReperibilitaSettingsScreen.kt# Gestione reperibilità singola, su turno o ciclica
```

---

## 3. Logiche di Business Critiche (Regole già implementate)

### A. Sincronizzazione Google Calendar (`GoogleCalendarSyncScreen.kt` & `GoogleCalendarSyncHelper.kt`)
1. **Cooldown obbligatorio 5 minuti**: Dopo aver sincronizzato o eliminato, il pulsante viene disabilitato con countdown (es. `Attendi 4:32...`) per impedire doppi click e duplicazione di centinaia di eventi.
2. **Eliminazione sicura senza blocco Android**: La cancellazione degli eventi avviene con query degli ID dell'app (`[CalendarioTurni]`) e cancellazione uno ad uno, evitando l'alert di sistema "Eliminazione di più di 50 eventi" di Android.
3. **Aggiornamento forzato**: Dopo ogni operazione viene invocato `context.contentResolver.notifyChange(CalendarContract.Events.CONTENT_URI, null)`.
4. **Dialog di conferma**: Richiesta conferma prima di eliminare tutti gli eventi dal calendario di sistema.

### B. Gestione Reperibilità Software
1. Orario sveglia Reperibilità: **la sera del giorno precedente** (default ore 20:00, con orario liberamente modificabile e preset rapidi 19:00, 20:00, 21:00).
2. Sul calendario i giorni reperibili hanno una bordatura dorata evidente (`ColorReperibilita` `#EAB308`) e badge `REP`.

### C. Importazione Tabelloni RFI / 'ngeniaTurni (`ImportShiftsScreen.kt`)
1. L'algoritmo mette in primo piano la ricerca del nominativo del turnista ("1. Chi sei nel tabellone?").
2. Consente di visualizzare i turni estratti con i colori reali e applicarli direttamente al calendario ("Sovrascrivi" vs "Aggiungi/Mantieni").
3. Estrae le squadre e le rotazioni cicliche a 28 giorni.
4. Supporta il **Turno Predefinito** (stella ⭐), che appare sempre al primo posto quando si entra in modalità modifica/rotazione.

### D. Regole Ferie vs Riposi nel Report (`ReportScreen.kt`)
- Se l'utente applica il turno Ferie sopra uno Smonto Notte o un Riposo, per comodità visiva compare Ferie, MA nel report statistico **non devono essere contati come giorni di ferie contrattuali consumati**: contano come smonto/riposo contrattuale!

### E. Badge Ex-Turno
- L'etichetta grigia sobria "ex TURNO" appare **esclusivamente** se il turno precedente era di una rotazione ciclica ed è stato sovrascritto manualmente.
- Se l'utente inserisce turni singoli su un calendario pulito, non compare alcun badge ex-turno.

---

## 4. Come Compilare, Testare e Distribuire

### Prerequisiti:
- JDK 17 o JRE di Android Studio: `C:\Program Files\Android\Android Studio\jre`
- Android SDK: `C:\Users\marco\AppData\Local\Android\Sdk`
- ADB: `C:\Users\marco\AppData\Local\Android\Sdk\platform-tools\adb.exe`

### Comandi da terminale (PowerShell):
```powershell
# 1. Compilare l'APK Debug
cd "c:\Users\marco\Antigravity Progetti\Calediario Turni\android-app"
$env:JAVA_HOME = "C:\Program Files\Android\Android Studio\jre"
.\gradlew.bat assembleDebug

# 2. Installare sul telefono fisico collegato via ADB (Realme 8 Pro)
& "C:\Users\marco\AppData\Local\Android\Sdk\platform-tools\adb.exe" -s cb08bc57 install -r "c:\Users\marco\Antigravity Progetti\Calediario Turni\android-app\app\build\outputs\apk\debug\app-debug.apk"

# 3. Avviare l'app
& "C:\Users\marco\AppData\Local\Android\Sdk\platform-tools\adb.exe" -s cb08bc57 shell am start -n com.turni.calendario/.MainActivity

# 4. Acquisire uno screenshot di verifica
cmd.exe /c "`"C:\Users\marco\AppData\Local\Android\Sdk\platform-tools\adb.exe`" -s cb08bc57 exec-out screencap -p > screen.png"

# 5. Copiare l'APK aggiornato sul Cloud (OneDrive/Google Drive)
Copy-Item "c:\Users\marco\Antigravity Progetti\Calediario Turni\android-app\app\build\outputs\apk\debug\app-debug.apk" "C:\Users\marco\OneDrive\app-release-latest.apk" -Force
```

---

## 5. Regole di Comunicazione con l'Utente
- Rispondere **SEMPRE in lingua italiana** con spiegazioni chiare ed esaustive.
- Codice, classi, metodi, variabili e commenti nel codice in **inglese**.
- Non rimuovere mai o rompere funzionalità esistenti: applicare modifiche chirurgiche con `replace_file_content`.
- Mantenere sempre aggiornato l'APK sul percorso cloud accessibile all'utente.
