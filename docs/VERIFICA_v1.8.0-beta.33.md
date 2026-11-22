# TurniSync 1.8.0-beta.33

Versione Android 78, package personale `com.turni.calendario` con firma originale. Le prove sul dispositivo sono state eseguite sul Realme 8 Pro (`cb08bc57`) nel package QA separato `com.turni.calendario.setupqa`.

## Novità e correzioni grafiche/funzionali

- **Animazione dei loghi in `Impostazioni` solo alla pressione (`SettingsScreen.kt`):** rimosse la cascata automatica all'ingresso nella schermata e la rotazione ciclica continua in background; i loghi a sinistra (e il badge che li contiene) rimangono fermi quando si apre o si torna in *Impostazioni* e si animano esclusivamente quando si preme la singola voce (o si aziona l'interruttore rapido di *Sveglie*), con un'anteprima fluida prima dell'apertura della sottoschermata.
- **Permesso simmetrico allo straordinario nel dettaglio giorno (`NativeCalendarPanels.kt`, `UnifiedShiftTimeTest.kt`):** nella card giorno (`NativeShiftExtraChips`) il badge del permesso non riporta più la dicitura `"perm."`, mostrando soltanto `"−1h30"` (simmetrico a `"+1h"` dello straordinario), ed espone ora anche il badge `"Stacco HH:mm"` calcolato dinamicamente tramite `resolvedShiftDisplayExitTime` (tenendo conto anche di eventuali pause pranzo configurate sul turno), senza alterare `actualEndTime` persistito nel database.
- **Bande extra (`Straordinario`, `Permesso`, `Trasferta`) saldate come proseguo piatto del turno in griglia (`NativeMonthCanvas.kt`):** quando un turno sul calendario mensile ha una banda extra non compatta (`+1h`, `−1h30`, `TR` o combinazione affiancata), gli angoli inferiori della tessera turno diventano piatti (`bottomRadius = 0`) mentre quelli superiori restano arrotondati (`5.dp`); la banda sottostante si aggancia a filo (`0.dp` di distacco verticale e stessa larghezza della tessera turno) con angoli superiori piatti (`CornerRadius.Zero`) e soli angoli inferiori esterni arrotondati (`5.dp`), formando un'unica silhouette continua bicolore.

## Verifiche eseguite e limiti

- `assembleBeta` (R8 e `lintVitalBeta`) e `testDebugUnitTest` superati con successo: **61 suite, 521 test unitari** (incluso il nuovo test unitario su `resolvedShiftDisplayExitTime` in `UnifiedShiftTimeTest`), 0 fallimenti, 0 errori, 0 saltati.
- Verificato dal vivo su Realme 8 Pro (`cb08bc57`, `com.turni.calendario.setupqa`) in tema scuro e chiaro: griglia calendario con bande di straordinario, permesso, trasferta e combinazioni saldate a proseguo piatto sotto i turni, card dettaglio giorno con `−1h30` (senza `"perm."`) e `Stacco HH:mm` sia per straordinari sia per permessi, e schermata *Impostazioni* con loghi statici all'apertura e animati solo al tocco della singola voce, con **0 crash** in `logcat -s AndroidRuntime:E`.
- La distribuzione `beta.33` conserva la firma personale originale (`cf099674...`), quindi aggiorna direttamente `com.turni.calendario` esistente senza perdere dati locali.
