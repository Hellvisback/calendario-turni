# Verifica beta.16 — 14 novembre 2026

Versione 1.8.0-beta.16 (61), package com.turni.calendario.

## Modifiche

- Riquadri del calendario con base costante di 30 dp (scalata solo dalle preferenze di accessibilità). Nessuna riduzione dovuta al numero di turni presenti nella giornata.
- Pianificazione per prefisso: vengono disegnati soltanto i riquadri completi che entrano, compresi i propri supplementi di straordinario/trasferta. Le informazioni degli altri turni rimangono accessibili nel dettaglio.
- Fascia riservata agli indicatori di overflow: pallini centrati, uno per turno nascosto, nel colore configurato dall'utente, con contorno sottile. Numerosi pallini riducono la loro spaziatura senza ridimensionare i turni.
- Avviso riposo minimo spostato in una fascia inferiore centrata, separata dai pallini e dai metadati della data. Icona triangolare con margine dal bordo della cella.
- Dettaglio giornata: avviso ambra raggruppato con icona, titolo e descrizioni allineati; inserito nell'area scorrevole per non sovrapporsi ai comandi inferiori quando ci sono molti turni.
- Destinazione dell'onda di uscita sincronizzata con la stessa geometria fissa usata per il disegno dei turni.

## Verifiche

Prove sul Realme 8 Pro con dati sintetici nel solo pacchetto separato com.turni.calendario.setupqa: uno, due, tre e cinque turni; calendario normale e modifica; temi scuro/chiaro; avvisi presenti e assenti. Verificati due riquadri completi quando c'è spazio, pallini per i restanti e dettaglio scorrevole con cinque turni e più avvisi.

Misurazione su screenshot: altezza del primo riquadro 90 px in tutte e cinque le celle di prova. Aggiunta reale da Più turni: primo ID conservato, secondo turno salvato, altezza prima/dopo 90/90 px.

Test automatici di geometria includono font scale da 1 a 2, spazi disponibili ridotti, numeri diversi di assegnazioni e supplementi. 480 test senza errori, fallimenti o test saltati.

Evidenze: workspace work/v180b16-qa. Risultati finali build, integrità e artefatto da registrare a installazione conclusa.


Build finale riuscita in 6m 56s; lintVitalBeta passato. APK installato senza cancellare dati. Impronta SHA-256 database escluso exportDate prima/dopo identica: a135d503bc9482c98e9fba8d1f5f5e5fc201d37630b82456855052f73696beac.

APK non debuggabile: 3285125 byte. SHA-256: e02a21079acf776a6771d4dc6cacda998ae25f2ef64750848853ae7f4d2fe960.

Drive verificato rileggendo nome e dimensione, condivisione non modificata:
https://drive.google.com/file/d/1fMdMYs3PlJ2knMXSZ1s9qYtHWN9RNzJ0/view?usp=drivesdk


Verifica conclusiva sul sorgente finale: icona triangolare corretta nel dettaglio; combinazione di due turni, nota e reperibilità senza sovrapposizioni con l'avviso. App QA e sonda di integrità rimosse; app personale lasciata sul calendario in tema scuro. Nessun commit o rilascio GitHub eseguito.
