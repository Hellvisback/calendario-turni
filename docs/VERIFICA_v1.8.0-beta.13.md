# Verifica TurniSync 1.8.0-beta.13

Data: 14 novembre 2026. VersionCode 58, package com.turni.calendario, minSdk 26, targetSdk 36.

## Modifiche

- Calendario: campitura intermedia (.065 scuro / .048 chiaro); onda radiale dal punto toccato; contrazione verso il badge alla chiusura; sessione grafica distinta dai dati, senza replay alla riapertura; indicatore della gomma per intervalli più leggero.
- Note ed etichette: righe ordinate con nomi su due righe, selezione stabile, ricerca da sette etichette, conteggio selezionate; creazione con solo Indietro; caratteri coerenti; X rosse semantiche nei pannelli attivi.
- Statistiche: attività, date e note tutte inline dopo i lavori. Righe lazy con chiavi stabili, senza animazioni al riciclo e senza popup.
- Reperibilità: nuova struttura a card, scelta manuale / per modello / annuale, controlli solo per la modalità attiva, spiegazioni delle precedenze e anteprima dei prossimi sette giorni. Autosalvataggio conservato.
- Guida beta: barra reale condivisa con il calendario, modelli selezionabili e gesti di sostituzione, aggiunta, intervallo, gomma, campanella, rotazione e undo. Prova locale che non modifica il database.
- Formato 12 ore: suffisso AM/PM maiuscolo e separato; suffisso proporzionato nella stessa area del badge, senza modificare le celle.
- Importazione AI: solo testo da incollare; rimosso Apri JSON; prompt esplicito senza file/allegati/link di download, parsing e revisione preservati.
- PDF: intestazione grafite, accenti cobalto, migliore gerarchia e contrasto; peso bold misurato prima del wrapping; testo adattivo anche su colori personalizzati scuri; festività distinte dal badge; attività incluse se si richiedono le note.

## Controlli eseguiti

- assembleDebug + assembleDebugAndroidTest + testDebugUnitTest nella variante di prova separata: riusciti.
- assembleBeta + testDebugUnitTest nella configurazione personale: riusciti. 476 test, zero errori, fallimenti o test saltati; lintVitalBeta passato.
- Realme 8 Pro cb08bc57: tre inserimenti, chiusura e riapertura senza replay; pressione prolungata gomma e cancellazione di tre giorni verificata sui dati; selezione di otto etichette con nomi lunghi; Indietro dalla creazione e X di chiusura completa.
- Reperibilità manuale, su modelli e annuale: controllati selezione, data annuale e settimane, salvataggio e temi chiaro/scuro nell'app di prova.
- Guida: confronto del database prima e dopo le interazioni identico (escluso exportDate).
- PDF reali generati da Android e renderizzati con PdfRenderer: elenco e mese A4 verticale, anno A2 verticale (fallback già previsto per mantenere tutti i dettagli leggibili in una pagina). Tutti una pagina; controllo visivo di festività, note, straordinario e turno personalizzato scuro. Nessun contenuto eliminato per forzare l'impaginazione; i casi troppo densi conservano l'errore esplicito.
- Acquisiti fotogrammi di onda/contrazione e cambi Calendario/Statistiche sulla beta ottimizzata. Non è un benchmark FPS universale: le acquisizioni introducono proprio carico e i risultati sono specifici del dispositivo collegato.
- Aggiornamento installato senza cancellare dati. SHA-256 del database personale, escluso exportDate, prima/dopo installazione e dopo la QA: a135d503bc9482c98e9fba8d1f5f5e5fc201d37630b82456855052f73696beac.
- Rimosse le sole app di prova e strumentazione aggiunte per la QA; app personale lasciata aperta sul calendario.

## Artefatto

APK: TurniSync-1.8.0-beta.13.apk, 3.285.129 byte, non debuggabile.

SHA-256: f377d8899bea533be1388c97feb75d0ecbfff1edf0bea02db378dc8990462476.

Google Drive (upload e metadati riletti; condivisione non modificata):
https://drive.google.com/file/d/1v7Tt2fPJyppEPAxe0bcOyvW2R9_M-DuZ/view?usp=drivesdk

Evidenze locali: workspace work/v180b13-qa (log, screenshot, fotogrammi e tre PDF di prova).
