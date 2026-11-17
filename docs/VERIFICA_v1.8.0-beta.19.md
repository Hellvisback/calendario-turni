# Beta.19 — configurazione progressiva

Versione 1.8.0-beta.19 (64), package di prova separato `com.turni.calendario.setupqa`.

Il primo avvio completato rimane completato negli aggiornamenti della stessa installazione, anche se il database non è rilevato all'inizializzazione. In quest'ultimo caso si sospende il backup automatico sullo snapshot iniziale, senza azzerare le preferenze. Una configurazione interrotta continua invece dal proprio passo. Il package QA non può leggere la configurazione della distinta app personale: installarlo per la prima volta resta una nuova installazione.

Welcome e scelta tema mostrano solo una griglia statica di due settimane. Non ci sono strumenti, date interattive o comandi di ripristino dell'esempio. Inizia e Hai già un account? Accedi hanno la stessa evidenza; il secondo apre la pagina Google esistente con recupero del calendario e altre copie disponibili, senza salvataggi automatici.

Il collegamento ai dettagli del lavoro è facoltativo e indica il lavoro corrente. L'editor occupa lo schermo intero, conserva lo spazio sotto l'header e la gestione della tastiera. La guida Prova le funzioni di modifica insegna sostituzione, intervallo, aggiunta di turni, gomma su intervallo, rotazioni e annullamento. Le istruzioni rimangono esterne alla griglia. La X chiude gli strumenti e non apre un dettaglio dimostrativo del giorno.

Build iniziale `assembleDebug assembleBeta testDebugUnitTest -PsetupProbe` riuscita in 8m 7s; verifica finale dopo la rifinitura della disposizione riuscita in 5m 3s: 486 test, zero fallimenti/errori/saltati, lint vitale e R8 riusciti. Log locali: `/workspace/turnisync-setup/beta19-build.log` e `beta19-final-build.log`.

Aggiornamento reale QA beta.18 (63) → debug beta.19 (64), senza cancellazione dei dati: stato `complete` conservato e calendario aperto direttamente. Impronta dell’intero database normalizzato identica prima/dopo: `32c293db6cbed930706ec0e13b251670450033e09b8a0f96cf5e9b90b8231f2c`.

Controlli nativi già conclusi su API 26: welcome con due CTA equivalenti e anteprima statica; pagina Accedi a TurniSync con pulsante Google e recupero, senza avviare OAuth; scelta tema scuro/chiaro senza strumenti di modifica; link dettagli facoltativi del lavoro; editor a tutta altezza e Salva lavoro sopra la tastiera. Le immagini iniziali hanno evidenziato che le istruzioni spingevano la toolbar oltre il bordo: la versione finale compatta la griglia e colloca le spiegazioni sotto gli strumenti.

Prove native sulla disposizione finale: selezione Più turni, pressione prolungata sul 18 e tocco sul 16 aggiungono il modello scelto ai tre giorni conservando il turno del 18. Gomma con la stessa selezione a ritroso cancella solo 16/17/18. La X chiude gli strumenti, senza messaggi che invitino a toccare date né dettagli dimostrativi; resta Riapri la matita. Calendario, toolbar completa, istruzione e Prossima funzione sono visibili insieme dopo scorrimento su 720×1600. Immagini pubbliche: [design/qa-beta19](../design/qa-beta19/README.md).

Pubblicazione GitHub in preparazione. OAuth reale e fluidità su telefono fisico non sono attestati dalle prove su emulatore software.
