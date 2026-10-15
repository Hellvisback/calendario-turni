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
- Rilascio APK su GitHub: aggiornare l'asset su GitHub Releases copiando prima il file col nome target (`Copy-Item "android-app/app/build/outputs/apk/debug/app-debug.apk" "android-app/app/build/outputs/apk/debug/app-release-latest.apk"` e poi `gh release upload v1.0.0 "android-app/app/build/outputs/apk/debug/app-release-latest.apk" --clobber`). MAI usare sintassi `#label` perché imposterebbe solo l'etichetta lasciando il nome vecchio. MAI aggiornare o copiare l'APK su OneDrive!
- Consulta `HANDOVER_NOTES.md` per i dettagli completi su repository, logiche di calcolo turni e sincronizzazione Google Calendar.
