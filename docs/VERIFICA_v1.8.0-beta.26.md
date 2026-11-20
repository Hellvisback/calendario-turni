# TurniSync 1.8.0-beta.26

Versione Android 71, package personale `com.turni.calendario` con firma originale. Le prove sul dispositivo sono state eseguite sul Realme 8 Pro (`cb08bc57`) nel package QA separato `com.turni.calendario.setupqa`.

## Primo avvio in 4 passi

- Flusso snellito da 5 a 4 passi (`0` Benvenuto + `1/4..4/4`), con riallineamento sicuro (`normalizeFirstRunStep`) per eventuali configurazioni `pending` precedenti.
- Al passo `1/4`, `Settimana (Lun/Dom)` e `Orario (24h/12h)` in controlli segmentati visibili sopra `Continua` con aggiornamento live dell’anteprima statica a due settimane e fascia `+1h` completa.
- Al passo `2/4`, nome del lavoro, riepilogo live per i dettagli facoltativi e modelli di turno unificati con anteprima a chip colorate, filtro per profilo attivo (`missingStarterShiftDefinitions`) e collegamento all’importazione AI senza API key.
- Al passo `3/4`, prova interattiva locale migliorata: barra a 6 segmenti cliccabili con spunte verdi, messaggi distinti alla chiusura di gomma e campanella, guardia sugli indici, riga controlli compatta e chiusura con `X` verso il solo `Riapri la matita` senza toccare il repository reale.
- Al passo `4/4`, card `La tua base`, 3 gruppi espandibili allineati alle Impostazioni con stati live reali (incluse reperibilità, sveglie e stato automatico Drive) e legenda `2×2` con le icone reali dell’app.

## Verifiche eseguite e limiti

- `assembleBeta` (R8 e `lintVitalBeta`) e `testDebugUnitTest` superati: **492 test unitari**, 0 fallimenti, 0 errori, 0 saltati (`FirstRunPolicyTest`: 9, `OnboardingPracticeTest`: 7).
- Sul Realme 8 Pro (`com.turni.calendario.setupqa`) verificati visivamente e funzionalmente Step 0, Step 1 (tema Automatico/Chiaro, `Dom`/`12h`, `+1h` completo), Step 2 (modelli base + ripresa a `step = 2` dopo `am force-stop`), Step 3 (cambio turno, gomma ON/OFF, campanella su `4/6`, `X` / `Riapri la matita`, `Ripristina`, hash calendario invariato), Step 4 e apertura/riapertura sul calendario reale vuoto con `firstRun.state = "complete"`.
- Non provati dal vivo sul dispositivo: scala caratteri `fontScale > 1.3f`/TalkBack, timeout forzati su `LocalDatabaseStore` e ripristino reale da backup Google Drive (coperti da revisione statica e test unitari).
