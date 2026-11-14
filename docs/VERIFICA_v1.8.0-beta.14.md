# Verifica beta.14 — 14 novembre 2026

Versione 1.8.0-beta.14 (59), package com.turni.calendario, minSdk 26 / targetSdk 36.

- Indicatore del calendario: cartellino per sole etichette, messaggio se è presente una nota della giornata o di un turno. Le note hanno la precedenza quando ci sono entrambe.
- Pulsante Note ed etichette del dettaglio invariato (notebook), indipendente dal tipo di contenuto.
- Ripristinati chip a larghezza naturale nella selezione: FlowRow con gap 6 dp, nessun peso o distribuzione uniforme. Più etichette sulla stessa riga quando entrano, nomi lunghi contenuti ed ellissi. Ricerca, ordine e selezione multipla conservati.
- Chip del dettaglio disposti su più righe in area limitata e scorrevole per evitare una lista enorme e mantenere accessibili gli altri comandi.

## Verifiche

Build debug separata riuscita; assembleBeta e testDebugUnitTest riusciti, 477 test senza fallimenti/errori/saltati, lintVitalBeta passato.

Controllo su Realme 8 Pro con dati sintetici: solo etichette, solo note, entrambi, otto etichette con nomi corti/lunghi, chip affiancati, aggiunta di una selezione e salvataggio conservando la nota. Controllati tema chiaro e scuro e pulsante costante nel dettaglio.

APK personale installato senza cancellare dati. Impronta SHA-256 database escluso exportDate prima/dopo identica: a135d503bc9482c98e9fba8d1f5f5e5fc201d37630b82456855052f73696beac.

App e strumentazione di prova rimosse; app personale lasciata sul calendario. Funzioni preesistenti e schema dati invariati.

APK non debuggabile: 3285125 byte.
SHA-256: 75c0e9f857a31f8567437c82c26a8177fa0eacda2a04a26acf4613e6e3aa2ad9.

Drive (upload verificato con rilettura metadati, condivisione non modificata):
https://drive.google.com/file/d/1B2uZgroOPye7o1VLQyAfBX8nYeI6b4XS/view?usp=drivesdk

Evidenze: workspace work/v180b14-qa.
