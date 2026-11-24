# TurniSync 1.8.0-beta.37

Versione Android 82, package personale `com.turni.calendario` con firma originale (`cf09967400ac4931dbfa169b9bc23c6a8314ef0256285c3399f4912bee735772`, SHA-256 `4b18e4d50185310b0de7bfb6bbef565f8e0cd90e710d7ef2d1b8abe39df0b8a8`).

## Novità e correzioni grafiche/funzionali

- **Fix errore caricamento Widget (`res/layout/widget_*.xml`, `WidgetVisualPolicyTest.kt`):** individuata la causa dell'errore di caricamento nel tag `<Space>` (che in Android non è annotato `@RemoteView`) introdotto nei 7 layout XML widget e di anteprima (`widget_month_grid.xml`, `widget_week_row.xml`, `widget_weeks_panorama.xml`, `widget_today_tomorrow.xml`, `widget_preview_month_grid.xml`, `widget_preview_week_row.xml`, `widget_preview_weeks_panorama.xml`). Sostituito con `<LinearLayout ... android:layout_weight="1" />` (100% compatibile con `RemoteViews`) e aggiunto il test unitario `widgetLayoutsOnlyUseRemoteViewAllowedTags` per impedire future regressioni sui layout widget.
- **Riquadri pulsante *Inizio* e *Fine* su Straordinari / Permessi (`NativeCalendarPanels.kt`):** rinominate le etichette utente da `Attacco`/`Stacco` a `Inizio`/`Fine` (inclusi badge della day card, campi orario, time picker e messaggi di validazione) e sostituito il selettore piatto con due veri riquadri pulsante Material 3 Expressive (`Surface` con bordo visibile `ObsidianBorder`, riempimento evidenziato sullo stato attivo, orario e indicatore cromatico di straordinario/permesso).
- **Sfondo Sabato e Domenica uniforme in tema Chiaro (`NativeMonthCanvas.kt`):** ripristinato il colore di sfondo costante della colonna Sabato/Domenica e dei giorni feriali anche sui giorni che rientrano dal mese precedente o successivo (`!item.info.isCurrentMonth`), lasciando schiariti solo i turni e i numeri del giorno fuori mese.

## Verifiche eseguite e limiti

- `assembleBeta` (R8 e `lintVitalBeta`) e `testDebugUnitTest` superati con successo: **63 suite, 541 test unitari**, 0 fallimenti, 0 errori, 0 saltati (incluso il nuovo test `widgetLayoutsOnlyUseRemoteViewAllowedTags` in `WidgetVisualPolicyTest`).
- Verificato `android-app/app/build/outputs/apk/beta/app-beta.apk` tramite `aapt dump badging` e `apksigner verify --print-certs`: package `com.turni.calendario`, `versionCode='82'`, `versionName='1.8.0-beta.37'`, certificato `cf09967400ac4931dbfa169b9bc23c6a8314ef0256285c3399f4912bee735772`.
- **Dispositivo fisico Realme 8 Pro (`cb08bc57`):** l'installazione QA e il controllo dal vivo sul dispositivo non sono stati eseguiti in questa sessione perché il telefono risultava `offline` su ADB.
- La distribuzione `beta.37` conserva la firma personale originale (`cf099674...`), quindi aggiorna direttamente `com.turni.calendario` esistente senza perdere dati locali.
