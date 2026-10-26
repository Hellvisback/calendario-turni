# Verifica TurniSync v1.4.1 — 26 ottobre 2026

## Modifiche
- Grafico statistico con una sola testa di avanzamento: anche il segmento finale cresce nello stesso verso. Le proporzioni restano calcolate sulla parte visibile.
- Periodo: il primo tap mostra solo il punto di partenza con frecce; nessuna anteprima globale. Intervalli in entrambe le direzioni preservati.
- Rotazione: marcatori 1, 2, 3… sui giorni iniziali dei cicli nell’anteprima, anche passando al mese successivo. Stelle predefinite gialle.
- Gomma: un turno + reperibilità vengono eliminati atomicamente e ripristinati insieme da Annulla. Con più assegnazioni resta la selezione puntuale.
- Colorazione: animazione guidata dall’ultimo tap; le celle precedenti mantengono il colore finale senza riavviare l’effetto. Attiva anche aggiungendo un secondo turno.
- Inclusi gli aggiornamenti già preparati a widget, schermata sveglie e backup automatico reale con account Google personale.

## Verifiche eseguite
- `assembleDebug test`: **BUILD SUCCESSFUL**, 5 minuti 32 secondi.
- Debug: 312 test; release: 312 test. Zero failure/error/skipped.
- Test nuovi: ancoraggio e intervallo Periodo, marcatori ciclo attraverso i mesi, selezione gomma, cancellazione+Annulla di turno/reperibilità, proporzioni/avanzamento del grafico e politiche backup.
- Firma APK verificata con apksigner: stessa chiave dell’installazione precedente. SHA-1: AB:1B:01:83:6A:C9:AC:F1:32:F5:F1:4E:D4:7A:C8:66:DD:9C:B6:01.
- APK v1.4.1, versionCode 22: `TurniSync-v1.4.1.apk`.
- SHA-256 APK: `4d305a929994008d213d0fd0a4bbb0fc0f00ac82eb791dd39e5208fe96ec1c48`.

## Limiti ancora aperti
La v1.4.1 non è stata installata né verificata visivamente sul Realme: ADB, anche nella versione ufficiale aggiornata, si arresta con `Cannot mkdir \.android: Permission denied` nel profilo Windows corrente. L’ultima versione installata e collaudata sul telefono resta la v1.4.0. Non si dichiarano screenshot, misure di fluidità o collaudi fisici dell’ultima revisione non eseguiti.

La configurazione OAuth Google è stata creata e le API abilitate, ma resta Esterno/Test. Il proprietario è utente di test; prima della distribuzione pubblica vanno completati branding/privacy e gli adempimenti Google richiesti. Upload Drive, ripristino remoto e Calendar attendono il consenso e il collaudo reale sul dispositivo. Vedere GOOGLE_SETUP_v1.4.1.md.

## Ambiente di compilazione
Usato Java 11 Temurin ufficiale portatile nel workspace, checksum verificato. Il runtime Android Studio non era eseguibile dal profilo corrente. Un init script locale temporaneo espande il classpath per evitare AccessDeniedException di Java zipfs sui JAR; nessun cambio di dipendenze, SDK target o configurazione globale. Nessuna release GitHub e nessuna copia OneDrive.
