# Calendario Turni (RFI Pro / SuperShift Evolution)

Applicazione Android nativa in **Kotlin** e **Jetpack Compose** progettata per lavoratori turnisti e ferrovieri (RFI / Trenitalia / Imprese Ferroviarie).

## Caratteristiche Principali
- **Design Obsidian Liquid Glass & Riccardo Breccia UX**:
  - Interfaccia ergonomica ottimizzata per l'uso a una mano (Thumb Zone).
  - Componenti Material 3 Expressive, feedback aptico/elastico (`bouncyClickable`).
  - Overlay animati non invasivi (`AnimatedStatusPopup`).
- **Gestione Turni Avanzata**:
  - Cicli di rotazione fino a 28 giorni e visualizzazione ex-turno in grigio tenue.
  - Fino a 3 turni giornalieri simultanei.
  - Editor turni con sticky bottom dock e gestione pause flessibile.
- **Dettaglio Giorno**:
  - Straordinari con incrementi rapidi di ±0.25h (15 minuti), ±1h o digitazione manuale via popup.
  - Calcolo automatico straordinario da timbratura reale.
  - Toggle trasferte e gestione note del giorno.
- **Reperibilità Flessibile**:
  - Reperibilità singola per data.
  - Reperibilità legata a un turno specifico (es. Pomeriggio).
  - Reperibilità ciclica/stagionale (es. invernale sui pomeriggi, estiva a settimane alternate).
- **Integrazioni Cloud & Utility**:
  - Sincronizzazione ed eliminazione sicura con Google Calendar (con cooldown e background coroutine).
  - Backup cloud su Google Drive.
  - Sveglie intelligenti pre-turno collegate all'orologio di sistema Android.
  - Esportazione report PDF e timbrature.

## Download & Installazione APK
Scarica l'ultimo file APK direttamente dalla sezione [Releases](../../releases) di questa repository:
- File: `app-release-latest.apk`
- Requisiti: Android 8.0 (API 26) o superiore.

## Compilazione da Sorgente
```powershell
cd android-app
.\gradlew.bat assembleDebug
```
L'APK generato si troverà in:
`android-app/app/build/outputs/apk/debug/app-debug.apk`
