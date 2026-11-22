# TurniSync 1.8.0-beta.31

Versione Android 76, package personale `com.turni.calendario` con firma originale. Le prove sul dispositivo sono state eseguite sul Realme 8 Pro (`cb08bc57`) nel package QA separato `com.turni.calendario.setupqa`.

## Novità e correzioni grafiche/funzionali

- **Riconnessione account Google, card unica espandibile e privacy cliccabile (`GoogleAccess.kt`, `CloudFeatureComponents.kt`, `CloudBackupScreen.kt`, `GoogleCalendarSyncScreen.kt`, `GoogleAccessPolicyTest.kt`):**
  - Risolto il conflitto di cache/race condition tra `disconnect()` e la successiva riconnessione tramite tracciamento generazionale (`authGeneration`/`revokeJob`), invalidazione della cache locale Play Services (`GoogleAuthUtil.clearToken`) e ritentativo automatico singolo su risposta HTTP `401`/`403` o scope incompleti (`GoogleAccessRetryPolicy`).
  - Sostituito il precedente `TextButton` ristretto *«Quali dati autorizzo?»* con una riga intera tonale cliccabile a tutta larghezza (`44.dp+`, feedback aptico, icona animata `shield-alert` e freccia rotante) che espande e richiude in modo affidabile l'informativa sintetica.
  - Unificata la card *Account Google* con i controlli Drive (*Backup e ripristino*) e *Google Calendar*: da disconnesso mostra soltanto accesso Google e informativa privacy, espandendosi con animazione fluida al collegamento dell'account per rivelare stato, salvataggio/ripristino e backup automatico.
- **Wizard `Importa con AI` semplificato in 3 passi (`GeminiShiftImportScreen.kt`):** rimosso il selettore file facoltativo (*Prospetto · facoltativo*) nello Step 1 perché nessun file veniva inviato all'AI; percorso alleggerito in 3 passaggi intuitivi (*1 Nome → 2 Chiedi → 3 Incolla* più revisione/salvataggio) con testi essenziali e icone animate.
- **Impostazioni dinamiche, testi essenziali e fix riga `Sveglie` (`SiteIcons.kt`, `SettingsScreen.kt`, `SmartAlarmsSettingsScreen.kt`, `ReperibilitaSettingsScreen.kt`, `HolidaysSettingsScreen.kt`, `StatisticsRulesScreen.kt`, `PdfExportScreen.kt`):**
  - Animate tutte le icone a sinistra in *Impostazioni* (onda progressiva all'ingresso + reazione al tocco) e arricchite le animazioni articolate in `SiteIcons.kt` (inclusi tracciati dedicati e separati per `calendar-heart` e `calendar-sync`, sollevamento coperchio su `trash`, rotazione raggi su `sun` e dettagli multi-parte).
  - Rimosse le descrizioni testuali superflue sotto il selettore *Tema* (`Sistema` / `Chiaro` / `Scuro`), il paragrafo sotto la palette colore (mantenendo gli swatch visivi) e le spiegazioni ripetitive in *Impostazioni* e relative sottoschermate.
  - Riordinata la riga **Sveglie** con il toggle posizionato a sinistra della freccia `>` verso destra (`[Icona] Sveglie ... [Switch] [>]`) e corretto il blocco dell'accensione rapida dallo switch in *Impostazioni* (`ShiftRepository.smartAlarmsConfig` letto fresco, stato salvato subito e apertura impostazioni permessi se necessari).
- **Etichette senza bordi scuri, lavori e rotazioni essenziali (`DayTagsScreen.kt`, `SiteCatalogScreen.kt`, `CatalogEditorComponents.kt`):**
  - Rimossi tutti i bordi scuri ad alto contrasto `codeInk` attorno a chip di anteprima, riquadri icona e swatch colore nell'editor *Etichette*, sostituendoli con superfici pastello prive di bordo e micro-animazioni elastiche.
  - Rimossa la sezione *Calendario · Imposta come lavoro attivo* dall'editor lavoro (`WorkProfileEditorScreen`) e il badge testuale *«Predefinita»* sulle rotazioni (`RotationCatalogCard`), lasciando la sola stellina dorata animata.

## Verifiche eseguite e limiti

- `assembleBeta` (R8 e `lintVitalBeta`) e `testDebugUnitTest` superati con successo dopo gli ultimi aggiornamenti: **61 suite, 514 test unitari** (inclusi i 6 test di `GoogleAccessPolicyTest`), 0 fallimenti, 0 errori, 0 saltati.
- Verificato sul Realme 8 Pro (`cb08bc57`, package QA isolato `com.turni.calendario.setupqa`) in tema scuro e chiaro: *Impostazioni* con testi essenziali e ordine/accensione dello switch *Sveglie*, *Aspetto e calendario* senza papiri con swatch visibili, *Backup e ripristino* / *Google Calendar* a card unificata con espansione di *«Quali dati autorizzo?»*, *Importa con AI* in 3 passi senza allegato file, *Modifica lavoro* senza toggle predefinito, *Rotazioni* con sola stellina dorata ed editor *Etichette* senza bordi scuri, con **0 crash** in `logcat -s AndroidRuntime:E`.
- Non eseguita dal vivo sul dispositivo l'autenticazione interattiva OAuth Drive/Calendar verso i server Google (flusso protetto dall'account reale dell'utente): la logica di invalidazione cache token (`GoogleAuthUtil.clearToken`), ritentativo su HTTP `401`/`403` e non-cancellazione su race è coperta da `GoogleAccessPolicyTest` e verificata nella build R8 `beta`.
