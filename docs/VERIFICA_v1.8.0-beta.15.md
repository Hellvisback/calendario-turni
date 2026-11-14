# Verifica beta.15 — 14 novembre 2026

Versione 1.8.0-beta.15 (60), package com.turni.calendario.

## Correzioni

- Onda di colorazione in modifica: entrata dal punto toccato e uscita di 520 ms verso il riquadro del turno, separata dal ridimensionamento del calendario.
- Callback della pressione prolungata stabile: il cambio strumento non ricrea più la griglia e non ripete l'ultimo inserimento. Evento consumato esplicitamente; stato transitorio limitato alla sessione di modifica.
- Selezione intervallo della gomma leggibile anche sui giorni già colorati, con contrasto dei metadati calcolato sullo sfondo effettivo.
- Editor della nota del singolo turno: lo spazio riservato al dock viene assorbito dagli inset della tastiera. Salva rimuove focus e tastiera.
- Rotella: celeste per straordinario; puntino blu esclusivamente per nota non vuota. Segnali indipendenti e cumulabili.
- Trasferta: lavanda piena in scuro, viola pieno in chiaro, bordo selezionato e icona a contrasto. Nessuna modifica ai colori dei turni o al cobalto principale.

## Verifiche

Build QA separata com.turni.calendario.setupqa e prove sul Realme 8 Pro. Catturati e ispezionati fotogrammi di entrata/uscita dell'onda e selezione gomma su turno già applicato. Sei fotogrammi delle celle precedentemente modificate identici durante il cambio a campanella.

Provate quattro combinazioni della rotella: nessun dato, solo straordinario, solo nota, entrambi. Trasferta attivata/disattivata con controllo della persistenza; stato attivo ispezionato nei temi chiaro e scuro. Contrasto automatico della trasferta verificato in tutte le palette: almeno 3:1 tra pulsante e superficie, almeno 4,5:1 tra icona e pulsante.

Con tastiera aperta la distanza fra pulsante Salva e tastiera è passata da 294 px (98 dp) a 60 px (20 dp). Il pulsante resta accessibile. Tastiera non più visibile dopo Salva.

assembleBeta e testDebugUnitTest: 480 test, nessun errore, fallimento o test saltato. Risultato finale build, integrità dati, APK e Drive riportati sotto a verifica conclusa.

Evidenze riproducibili: workspace work/v180b15-qa. Test sintetici esclusivamente nel pacchetto QA separato. Schema dati e funzioni preesistenti conservati.


Build finale riuscita in 7m 5s, lintVitalBeta passato. APK non debuggabile installato senza cancellare dati. SHA-256 database escluso exportDate prima/dopo identico: a135d503bc9482c98e9fba8d1f5f5e5fc201d37630b82456855052f73696beac.

APK: 3285129 byte. SHA-256: 73cbbf22ad220073761caf396fa2ce8428725c6bfa68c22a5f07075eede039bb.

Drive verificato rileggendo i metadati, condivisione non modificata:
https://drive.google.com/file/d/1C7x2AlEyc-FVTNF5GkLXn1NBBRyUYrNa/view?usp=drivesdk

App QA e sonde di test rimosse. App personale lasciata sul calendario in tema scuro.
