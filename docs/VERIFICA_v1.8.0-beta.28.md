# TurniSync 1.8.0-beta.28

Versione Android 73, package personale `com.turni.calendario` con firma originale. Le prove sul dispositivo sono state eseguite sul Realme 8 Pro (`cb08bc57`) nel package QA separato `com.turni.calendario.setupqa` con dati popolati (turni, rotazione e etichetta).

## Restyling espressivo pastello senza bordi

- **Superfici Grafite senza bordi wireframe:** eliminati i bordi grigi `1.dp` su card, intestazioni, selettori e campi in favore della stratificazione tonale Grafite/Cobalto coerente con `Calendario`, `Statistiche` e `Modifica`, riservando i bordi ai soli anelli di selezione attiva e ai dialoghi fluttuanti.
- **Palette pastello calibrata (`CloudFeatureComponents.kt`):** introdotti `ExpressivePastel`, `pastelSheenBrush`, `PastelBadge`, `PastelIconBadge`, `PastelPill`, `SectionLabel` e `TonalIconButton` basati su `TurniPalette` (`shiftColorStyle` + riflesso diagonale stile `ReportMetric`) con contrasto pieno in entrambi i temi.
- **Catalogo `Modelli e turni` e 4 editor (`SiteCatalogScreen.kt`, `DayTagsScreen.kt`, `CatalogEditorComponents.kt`):** aggiornate tutte e 4 le schede (*Turni*, *Lavori*, *Rotazioni* con mini-celle pastello in `SiteCyclePreview`, *Etichette*) e tutti e 4 gli editor di creazione/modifica (`ShiftEditorScreen` con anteprima live su cella calendario, `WorkProfileEditorScreen` con icone pastello e foglio CCNL Grafite, `RotationMatrixEditorScreen` con brush pastello stile `NativeEditToolbar`, `DayTagEditor` con banner live e icone/colori pastello), preservando il passo dedicato del primo avvio.
- **Impostazioni e Aspetto/Layout (`SettingsScreen.kt`):** badge pastello dedicati e pillole stato live su ogni voce (mantenendo rimossa `Etichette dei giorni`), più la nuova card interattiva di **anteprima mini-calendario live** in `Aspetto e calendario` (tema, inizio settimana Lunedì/Domenica, formato 24h/12h ed ex turno in tempo reale) e nastro palette.
- **Tutte le sottoschermate delle Impostazioni:** uniformate senza bordi con accenti pastello coerenti — `Reperibilità` (senza *Manuale* né *Prossimi 7 giorni*, con righe turno a 1 colonna e striscia cicli Amber/Pearl live), `Festività` (tessere giorno Corallo), `Conteggio statistiche` (esempio visivo compatto `RIP -> FER` e `25 dic · FER`), `Sveglie` (badge pastello reali per turno, `androidx.compose.ui.platform.LocalLifecycleOwner` preservato), `Esporta e stampa PDF`, `Google Calendar`, `Backup e ripristino` e `Importazione con AI` (stepper pastello a 3 passi, max 1 CTA Cobalto per card, flusso JSON/OCR senza API key invariato).

## Verifiche eseguite e limiti

- `assembleBeta` (R8 e `lintVitalBeta`) e `testDebugUnitTest` superati: **58 suite, 494 test unitari**, 0 fallimenti, 0 errori, 0 saltati (`OnCallSettingsPresentationTest` incluso).
- Sul Realme 8 Pro (`cb08bc57`, `com.turni.calendario.setupqa` con turni, rotazione ed etichetta popolati) verificati visivamente e funzionalmente in tema scuro e tema chiaro catalogo, tutti e 4 gli editor di Modifica, Impostazioni, Aspetto/Layout e tutte le sottoschermate con **0 crash** in `logcat -s AndroidRuntime:E`.
- Non provati dal vivo sul dispositivo: scala caratteri `fontScale > 1.3f`/TalkBack e autenticazione/sync OAuth live verso Google Calendar o Google Drive.
