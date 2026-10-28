# Verifica v1.6.9 — strumenti calendario, riposo minimo e PDF

28 ottobre 2026. APK 1.6.9 / versionCode 41 aggiornato sul Realme 8 Pro con install -r. Nessuna pubblicazione GitHub, nessuna disinstallazione dell’app principale.

## Editor e rotazioni

Gomma e campanella sono due strumenti indipendenti dalle quattro modalità. Collocati tra Undo e X, con selezione a bordo animato e feedback delle icone. La gomma è stata rimossa dallo slider dei modelli. Scegliere un modello torna alla pittura normale; ricliccare lo strumento lo disattiva. Cambiare scheda conserva lo strumento. Attivarlo annulla un eventuale intervallo/anteprima pendente.

La gomma cancella direttamente i turni del lavoro attivo e la reperibilità quando ci sono zero o un turno. Con due o più propone tutti i turni, senza distinguere base/aggiuntivi e senza voce Reperibilità. Eliminare un turno dal menu rimuove anche la reperibilità, atomicamente nella stessa operazione annullabile. Aprire e annullare il menu non modifica nulla. Le note comuni della giornata restano conservate, come prima; cancellare non influisce sui turni di altri lavori.

Nell’editor del ciclo del catalogo, la gomma selezionata ha bordo primario visibile e superficie colorata. Il catalogo usa l’ordine persistito delle rotazioni, senza ordinamento al cambio stella. La barra Rotazioni dell’editor calendario continua a mettere la predefinita per prima. Sul telefono verificato Turno B predefinito senza spostamento delle due card; ripristinati preferenza e rotazione attiva originali Turno A.

## Riposo minimo

MinimumRest.kt analizza intervalli lavorativi cronologici a prescindere da nomi/sigle e ordine d’inserimento. Considera orari personalizzati, notti, cambio mese, stacco effettivo o straordinario quando lo stacco non è specificato, sovrapposizioni e intervalli annidati. Esclude giornata intera, riposi, assenze e categorie non lavorative. Confronta la durata esatta in minuti; l’uguaglianza alla soglia è valida. Tra lavori diversi usa la soglia maggiore dei due profili. Non si tratta di una certificazione normativa: sono le ore scelte dall’utente nei lavori.

Avviso ! discreto nel margine inferiore del giorno successivo interessato, con una propria fascia per evitare sovrapposizioni con turno/note/campanella/festività. Nella tendina compare riposo disponibile e minimo richiesto, con espansione animata. Il calcolo è derivedStateOf condiviso, si aggiorna con i dati e non viene rieseguito per ogni fotogramma dell’animazione. Nessun blocco agli inserimenti.

Caso reale verificato il 16 ottobre: stacco dalla notte alle 05:36 e inizio alle 08:30, riposo 2 h 54 min rispetto a 11 h. Visibili sia avviso calendario sia dettaglio.

## Esporta e stampa PDF

Testi e dettagli scuriti, testo calendario mensile fino a 11 pt e annuale fino a 8 pt, data più evidente, supporti colore leggeri. Orari compatti annuali mantengono i minuti e non vengono spezzati a metà cifra; gli intervalli eventualmente troppo lunghi si dividono fra i due orari, non dentro un orario. Anteprima da 1800 px e documento condiviso identico a quello visualizzato. Mantenuta una pagina: A4 verticale per mese, A3 verticale per anno nella fixture verificata. Mantenuta la griglia annuale a tre colonne perché due riducevano troppo l’altezza delle settimane. Nessun campo selezionato scartato per far spazio; resta l’errore esplicito per dettagli che non entrano leggibili.

Generati dal vero PdfGenerator su Android e renderizzati con PdfRenderer: mese di novembre a sei righe e anno intero 2026, tutti i giorni con un turno e orario. Verifica strutturale con pypdf: un’unica pagina per ciascun PDF. Controllati PNG dell’intera pagina e dettaglio annuale. Fixture separate dal repository personale, non importate nei dati.

## Verifiche e conservazione dati

- assembleDebug, assembleDebugAndroidTest, test riusciti.
- 410 test JVM Debug e 410 Release, nessun errore o fallimento. Nove nuovi casi riposo minimo e aggiornamento dei test della gomma alla nuova logica con ripristino Undo.
- 60 rendering RemoteViews Android superati per escludere regressioni dei widget; nessuna modifica di layout widget in questa versione.
- Sul telefono gomma e campanella provate in Rotazioni, annullate con Undo. Database confrontato con baseline: identico, escluso timestamp exportDate.
- Controllo tema chiaro/scuro; tema scuro originale ripristinato. Nessuna sincronizzazione o cancellazione di eventi Google eseguita.

APK: outputs/TurniSync-v1.6.9.apk nel workspace Codex.
SHA256: 53316575691f7a0ebe54565addac6b3f6d32058a721aff33ee3b1cc02679d641
