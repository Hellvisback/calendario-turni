# Regole Operative per Agenti Antigravity

## 1. Comunicazione
- Comunica e rispondi **sempre in lingua italiana** con l'utente in ogni sessione e spiegazione.
- Mantieni codice sorgente, nomi di funzioni, variabili, classi, log e comandi shell in **inglese**.

## 2. Architettura & Stile Grafico
- Progetto Android nativo in Kotlin Jetpack Compose (`android-app/`).
- Stile grafico: **Obsidian Liquid Glass** + **Riccardo Breccia UX** (Thumb Zone ergonomica, bottom sheets e floating dock, feedback tattile elastico).
- Notifiche: mai inserire card verdi o rosse statiche inline che spostano il layout della schermata! Usare `AnimatedStatusPopup` con overlay animato dal basso (`Alignment.BottomCenter`).
- Schermate: un solo pulsante indietro in alto a sinistra (`Icons.Default.ArrowBack`, 38-40dp), mai doppioni in basso.

## 3. Ambiente di Esecuzione
- Java Home: `$env:JAVA_HOME = "C:\Program Files\Android\Android Studio\jre"`
- Compilazione: `.\gradlew.bat assembleDebug` in `android-app/`
- Device test ADB: Realme 8 Pro (`cb08bc57`)
- Rilascio APK su GitHub: OGNI release DEVE incrementare `versionCode` (intero progressivo 1, 2, 3...) e aggiornare `versionName` (semantico es. 1.1.0) in `android-app/app/build.gradle.kts`. MAI sovrascrivere vecchie release con `gh release upload v1.0.0 --clobber` (questo impedisce a GitHub di mostrare l'aggiornamento e ad Android PackageInstaller di installarlo!). Usare sempre lo script automatizzato: `powershell -ExecutionPolicy Bypass -File .\scripts\release.ps1` che compila, genera gli asset, esegue git push e crea la nuova release `vX.Y.Z` su GitHub. MAI aggiornare o copiare l'APK su OneDrive!
- Consulta `HANDOVER_NOTES.md` per i dettagli completi su repository, logiche di calcolo turni e sincronizzazione Google Calendar.
