# TurniSync 1.8.0-beta.35

Versione Android 80, package personale `com.turni.calendario` con firma originale (`cf09967400ac4931dbfa169b9bc23c6a8314ef0256285c3399f4912bee735772`, SHA-256 `12E5E1DFAC5F6E8434E5D799FED6D215281B2C39A22C305A9CF8847ED77C39C1`).

## Novità e correzioni grafiche/funzionali

- **Visibilità dei pallini di eccedenza in tema chiaro (`NativeMonthCanvas.kt`):** corretto il contrasto dei pallini di eccedenza quando sono presenti più turni sullo stesso giorno con overlay di colorazione in modalità modifica su sfondo chiaro.
- **Intestazione settimana solidale allo scorrimento (`CalendarScreen.kt`):** la barra dei giorni `L M M G V S D` (con le colonne weekend grigie) scorre ora insieme alla griglia mensile come un'unica scheda continua.
- **Widget allineati al calendario e profilo lavoro per singolo widget (`widget/*`, `WidgetWorkProfileActivity.kt`, `res/xml/widget_*_info.xml`, `res/layout/widget_preview_*.xml`):** colonne weekend continue coerenti con il calendario principale, supporto `weekStartMonday`, anteprime Light/Dark fedeli ai widget reali e scelta del profilo lavoro per singola istanza (`appWidgetId`) toccando il nome del lavoro in alto sul widget.
- **Primo avvio pulito e fix data backup 1970 (`ShiftModels.kt`, `ShiftRepository.kt`, `FirstRunStore.kt`, `GoogleDriveBackupHelper.kt`, `LocalBackupArchive.kt`, `CloudBackupScreen.kt`):** nessuna impostazione precompilata su installazione pulita (`weeklyHours = 0`, `minRestHoursBetweenShifts = 0`, `maxConsecutiveWorkDays = 0`, nessun turno/rotazione/reperibilità/sveglia dimostrativa) e nessuna visualizzazione di `01/01/1970` quando il timestamp di backup è assente o `<= 0L`.
- **Straordinario e permesso sia in Attacco che in Stacco (`ShiftModels.kt`, `ShiftRepository.kt`, `MinimumRest.kt`, `NativeCalendarPanels.kt`, `ReportMetrics.kt`, `PdfGenerator.kt`):** supportati arrivo anticipato (+straordinario) / posticipato (−permesso) in Attacco e uscita posticipata / anticipata in Stacco, riflessi in riposo minimo, dettaglio giorno, statistiche ed export PDF.
- **Colori pastello nelle Statistiche in tema chiaro (`TurniPalette.kt`, `ReportScreen.kt`):** calibrata l'armonia pastello/tonale `metricColorStyle` dei colori turno su sfondi chiari.
- **Ordinamento cronologico dei turni ovunque (`ShiftModels.kt`, `ShiftRepository.kt`, `NativeMonthCanvas.kt`, `CalendarScreen.kt`, `NativeCalendarPanels.kt`, `SiteCatalogScreen.kt`, `ReportMetrics.kt`, `PdfGenerator.kt`, schermate Impostazioni, Widget):** turni ordinati per `startTime` crescente (`!isAllDay` prima, `isAllDay` in coda).
- **Festività universali e dinamiche (`HolidaysCatalog` in `ShiftModels.kt`, `HolidaysSettingsScreen.kt`):** rimossa la categoria rigida solo CCNL; aggiunte festività nazionali italiane, ricorrenze civili/religiose e oltre 26 Santi Patroni delle principali città con filtri `Tutte / Nazionali / Ricorrenze / Patroni` e barra di ricerca.
- **Colore Reperibilità migliorato in tema chiaro (`TurniPalette.kt`):** tonalità ambra/miele `GraphiteDay.onCall` più luminosa e leggibile con contrasto >= 4.5:1.
- **Zero sovrapposizione pannelli con dock e slider (`CalendarScreen.kt`, `NativeCalendarPanels.kt`):** `bottomInset = 96.dp` e chiusura automatica di `NativeDayCard` quando sono aperti i flussi `selectShift` o `eraseDate`.
- **Posizionamento sicuro dell'avviso `!` e nessun falso allarme (`NativeMonthCanvas.kt`, `MinimumRest.kt`):** icona `!` spostata nella fascia alta sinistra della cella (mai coperta dal dock sull'ultima settimana), attivata solo su turni reali quando i limiti del profilo sono > 0.
- **Raggruppamento turni per lavoro e duplicazione (`SiteCatalogScreen.kt`, `ShiftRepository.kt`):** visualizzazione dei soli turni del profilo attivo più sezioni espandibili degli altri lavori con `Duplica tutti qui` e `Duplica qui`.
- **Stati vuoti guidati (`NativeCalendarPanels.kt`, `SiteCatalogScreen.kt`, `ReportScreen.kt`, `PdfExportScreen.kt`, Impostazioni):** messaggi chiari e pulsante CTA verso `TurniModelsScreen` alla scheda corretta quando non esistono ancora turni, rotazioni o etichette.
- **Suggerimento automatico sigla turno (`CatalogEditorComponents.kt`):** `suggestShiftShortCodes` propone e precompila sigle brevi coerenti durante la creazione di un turno.
- **Isolamento rigoroso per profilo lavoro (`ShiftRepository.kt`, `DatabaseSnapshot.kt`, `DatabaseRestore.kt`, `GoogleDriveBackupHelper.kt`):** `dayExtraInfo`, `reperibilita`, `smartAlarms`, `rotations`, `dayTags` e `statisticsRules` isolati per `workProfileId` con migrazione compatibile dei backup v3–v5.
- **Elimina tutto con conto alla rovescia di 5 secondi e reset tutorial (`SettingsScreen.kt`, `FirstRunStore.kt`):** conferma di sicurezza con timer da 5 secondi e opzione per cancellare tutti i dati ripartendo dal tutorial iniziale.
- **Google Cloud OAuth test user (`lory.trinati@gmail.com`):** completato direttamente dall'utente nella console Google Cloud.

## Verifiche eseguite e limiti

- `assembleBeta` (R8 e `lintVitalBeta`) e `testDebugUnitTest` superati con successo: **63 suite, 529 test unitari**, 0 fallimenti, 0 errori, 0 saltati.
- Verificata firma e metadata di `android-app/app/build/outputs/apk/beta/app-beta.apk` tramite `aapt dump badging` e `apksigner verify --print-certs`: package `com.turni.calendario`, `versionCode='80'`, `versionName='1.8.0-beta.35'`, certificato `cf09967400ac4931dbfa169b9bc23c6a8314ef0256285c3399f4912bee735772`.
- Compilato anche l'APK QA separato `com.turni.calendario.setupqa` (`versionCode='80'`, `versionName='1.8.0-beta.35'`).
- **Dispositivo fisico Realme 8 Pro (`cb08bc57`):** l'installazione e la verifica visiva dal vivo non sono state completate in questa sessione perché il dispositivo risultava `offline` su ADB anche dopo `adb kill-server` / `adb start-server` (necessita sblocco schermo o riconnessione USB fisica).
- La distribuzione `beta.35` conserva la firma personale originale (`cf099674...`), quindi aggiorna direttamente `com.turni.calendario` esistente senza perdere dati locali.
