# TurniSync 1.8.0-beta.27

Versione Android 72, package personale `com.turni.calendario` con firma originale. Le prove sul dispositivo sono state eseguite sul Realme 8 Pro (`cb08bc57`) nel package QA separato `com.turni.calendario.setupqa`.

## Catalogo etichette, reperibilità e UI impostazioni

- **Catalogo a 4 schede (`Modelli e turni`):** aggiunta la scheda `Etichette` accanto a `Turni`, `Lavori` e `Rotazioni` con elenco, creazione, modifica, eliminazione ed editor dedicato collegato a `onEditorActiveChanged` e `BackHandler`.
- **Impostazioni ripulite:** rimossa la voce `Etichette dei giorni` (`DAY_TAGS`) da Impostazioni, preservando `DayTagsSettingsScreen` per il passo dedicato del primo avvio.
- **Barra segmentata e transizioni fluide:** introdotti in `CloudFeatureComponents.kt` il componente `SlidingSegmentBar` con click privo di flash/ripple (`indication = null`) e interpolazione colore sincronizzata con la pillola Cobalto, insieme a `CloudFeatureScaffold`, `CloudCard`, `TurniSwitch` e `SettingsDivider`.
- **Reperibilità riorganizzata:** rimossi dalla UI sia la scheda/opzione `Manuale` sia la scheda `Prossimi 7 giorni`, sostituendoli con card iniziale animata (`TurniSwitch`), selettore `Per turno` / `Stagionale`, scelta modelli raggruppata e stepper/preset completi per i cicli stagionali (`summerCycleOnCallWeeks` / `summerCycleWorkWeeks`), preservando `ReperibilitaMode.MANUALE`, `annualDayInMonth`, `manualOnCallDates` e compatibilità con backup esistenti.
- **Sottoschermate Impostazioni uniformate:** aggiornate con stile Grafite/Cobalto e animazioni coerenti `Aspetto` (`PreferenceThemeSelector`), `Festività`, `Conteggio statistiche`, `Sveglie`, `Esporta e stampa PDF`, `Google Calendar`, `Backup e ripristino` e `Importazione con AI` (mantenendo invariato il flusso JSON/OCR senza API key).

## Verifiche eseguite e limiti

- `assembleBeta` (R8 e `lintVitalBeta`) e `testDebugUnitTest` superati: **494 test unitari** (58 suite), 0 fallimenti, 0 errori, 0 saltati (inclusa copertura aggiornata in `OnCallSettingsPresentationTest`).
- Sul Realme 8 Pro (`cb08bc57`, `com.turni.calendario.setupqa`) verificati visivamente e funzionalmente in tema scuro e tema chiaro: catalogo a 4 tab senza troncamenti, creazione/modifica/rimozione etichette, assenza di `Etichette dei giorni` nelle Impostazioni, nuova Reperibilità `Per turno` / `Stagionale` senza `Manuale` e senza `Prossimi 7 giorni`, tutte le sottoschermate delle Impostazioni e assenza di crash su `logcat -s AndroidRuntime:E`.
- Non provati dal vivo sul dispositivo: scala caratteri `fontScale > 1.3f`/TalkBack e autenticazione/sync OAuth live verso Google Calendar o Google Drive.
