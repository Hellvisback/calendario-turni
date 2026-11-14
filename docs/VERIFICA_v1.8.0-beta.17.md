# Verifica beta.17 — 14 novembre 2026

Versione 1.8.0-beta.17 (62), package com.turni.calendario.

## Rifinitura contorni del calendario

- Contorno di selezione arretrato di 2,4 dp rispetto al riempimento della cella: lo spessore non cresce più sul bordo esterno. Raggio interno derivato dalla stessa curva del riempimento, con margine uniforme sui quattro lati.
- Spessore 1 dp per l'ancora della gomma e 1,15 dp per le altre selezioni (prima 2 dp).
- Rimossa la fascia rossa a tutta larghezza nell'ancora di cancellazione. Indicatore sul solo numero: 16 x 16 dp, distanziato dal bordo superiore, con testo centrato e frecce complete sottili.
- Numero e frecce dell'ancora allineati; note e reperibilità in due posizioni separate, interne al bordo e senza cambiare il posizionamento dei turni.
- Bordo della festività ridotto da 20 a 18 dp, spessore 1,1 dp; colore del numero e logica delle festività conservati.
- Conservate dimensioni fisse dei riquadri, pallini di overflow, onda di colorazione/riassorbimento e logica di applicazione/cancellazione.

## Controlli

Realme 8 Pro, pacchetto QA separato com.turni.calendario.setupqa. Riprodotto il caso dello screenshot: quattro mattine colorate in sequenza, poi pressione prolungata con gomma sul 21. Verificati contorno interno, angoli, spazio tra bordo e indicatore rosso e ritorno al calendario normale.

Controllati tema scuro e chiaro, anche con nota e reperibilità insieme sull'ancora, selezione normale, giorno di oggi e festività. Catturate schermate definitive eraser-dark-final.png, eraser-light-final.png e normal-range-light.png. Anteprima ritagliata contorni-corretti.png senza alterazioni del disegno.

Cancellazione di quattro turni con intervallo a ritroso verificata sul database QA; prova aggiuntiva di applicazione a ritroso e cancellazione in avanti. Dati sintetici esclusivamente nel pacchetto QA.

Evidenze: workspace work/v180b17-qa. Risultati definitivi di build, test, integrità e pubblicazione APK registrati a conclusione.


Build finale riuscita in 6m 40s; lintVitalBeta e 480 test passati (0 errori, fallimenti o saltati). Installata senza cancellare dati; SHA-256 database escluso exportDate prima/dopo identica: a135d503bc9482c98e9fba8d1f5f5e5fc201d37630b82456855052f73696beac.

APK non debuggabile: 3285129 byte. SHA-256: 167d190a533937810359bd69d47a4ced3bcc7547178042f2c5635180dc174dfc.

Drive, caricamento e metadati verificati, condivisione non modificata:
https://drive.google.com/file/d/13zOj9tugb1ZwqoE1t3CvtOX0OjHhoWSS/view?usp=drivesdk

Pacchetti QA e sonda di integrità rimossi; app personale lasciata sul calendario in scuro. Nessun commit o rilascio GitHub; modifiche precedenti del progetto preservate.
