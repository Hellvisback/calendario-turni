# Continuare «Evolvi la UI di Calendario Turni»

Questa chat prosegue lo stesso progetto con sorgenti beta.17, documenti, riferimenti e metodo multiagente recuperati. Questo indice conserva il contesto utile senza trasformare ogni vecchio messaggio in una nuova task.

## Lettura di ingresso

1. `AGENTS.md`: regole correnti e protocollo richiesto dall'utente.
2. `docs/CLOUD_HANDOFF.md`: stato tecnico, mappa, design finale, configurazione e limiti; prevale sulle intestazioni storiche.
3. `.agents/turnisync/STATE.json`: lavoro corrente, verifiche, aperti e riferimenti locali opzionali. Controllare Git prima di fidarsi di branch/SHA registrati.
4. `.agents/turnisync/ORCHESTRATION.md`: ruoli, dispatch, raccolta risultati e continuità della memoria.
5. Verifica della beta corrente e storia pertinente in `HANDOVER_NOTES.md`. Consultare il resto dello storico per la decisione specifica, non ricaricare ogni volta tutti i documenti.

Il catalogo originale di agenti, skill, regole, workflow e teamwork rimane in `.agents/`. `INVENTORY.json` nella sottocartella `turnisync/` ne registra file e SHA-256 insieme ai riferimenti grafici, per verificare cosa è stato conservato. Sono file recuperati: le skill richiedono lettura e adattamento agli strumenti effettivi, non diventano plugin installati per la sola presenza nel repository.

Lo stato di installazione storico `.agents/ecc-install-state.json` identifica ECC 2.2.3 sul target Antigravity del PC Windows (profilo minimal, nessun consenso hook registrato). È conservato come provenienza; non costituisce un'installazione ECC nel runtime Codex di questa chat. Sono stati inventariati anche 94 file di workflow. Eventuali salvataggi Context Keeper esterni al repository, per esempio sotto `~/.claude/ck/` sul PC, non risultano trasferiti insieme alle sole definizioni delle skill.

## Dove ritrovare il come e il perché

Le regole attuali restano in AGENTS.md e CLOUD_HANDOFF.md. Questa tabella è un indice delle motivazioni storiche, non una palette o una specifica alternativa.

| Scelta finale | Motivazione conservata | Evidenza |
| --- | --- | --- |
| App nativa, modelli e statistiche per qualsiasi professione | I dati personalizzati dell'utente devono guidare il calendario; RFI è un caso d'uso | CLOUD_HANDOFF, sezioni Dati e Decisioni UI; storico della chat |
| Grafite con accento cobalto; dock grigio, selezione blu, sole icone | L'utente ha concluso l'esplorazione delle palette e chiesto coerenza nelle sezioni, mantenendo le animazioni | CLOUD_HANDOFF; TurniPalette.kt; richieste 8–13 novembre nello storico |
| Calendario e Statistiche come destinazioni principali | Ridurre i comandi e mantenere il dock ergonomico; non ripristinare vecchie barre a tre/quattro tab | CLOUD_HANDOFF; MainScreen e TurniDock |
| Turni ad altezza fissa e overflow a pallini | Aggiungere turni non deve ridimensionare quelli già inseriti | VERIFICA_v1.8.0-beta.16.md; richiesta del 14 novembre 11:18 UTC |
| Long press per intervalli, gomma e reperibilità indipendenti | Semplificare i modi di modifica evitando un tab Periodo separato | Verifiche 1.7.3 e beta.13; CLOUD_HANDOFF |
| Onda dal punto toccato e riassorbimento nel badge, senza replay | Conferma visiva continua della modifica; cambiare strumento non è una nuova applicazione | Verifiche beta.12–15; richieste del 13–14 novembre |
| Contorni interni sottili, indicatore gomma compatto | Correggere bordi scontornati e fascia rossa troppo grande su celle colorate | VERIFICA_v1.8.0-beta.17.md; ultima richiesta UI del 14 novembre |
| Note/etichette nello stesso flusso, footer stabile, X rossa chiude tutto | Evitare pannelli riaperti in cascata, salti rispetto alla tastiera e navigazione ambigua | Verifiche beta.12–15 |
| Nota prioritaria sull'etichetta; chip compatti e pulsante stabile | Rappresentare il contenuto senza cambiare il comando o creare liste enormi | VERIFICA_v1.8.0-beta.14.md |
| Segnali distinti per straordinario, nota e trasferta | Capire quali metadati sono presenti anche quando coesistono e nei due temi | VERIFICA_v1.8.0-beta.15.md |
| Statistiche dinamiche con dettagli inline | Rispettare i modelli reali ed evitare popup e sezioni duplicate | CLOUD_HANDOFF; verifica beta.13 |
| Widget puliti e adattivi | Non ricreare sovraccarico di icone rimosse su richiesta; conservare leggibilità e resize | Verifiche 1.4.5, 1.6.8–1.7.1 |
| Backup completo e ripristino iniziale protetto | Una nuova installazione vuota non deve sovrascrivere i dati remoti; conservare anche extra e provenienza delle rotazioni | CLOUD_HANDOFF; VERIFICA_BACKUP_DATI_ACCESSORI; verifica beta.11 |
| AI esterna con prompt e risposta incollata | Nessuna API key o costo automatico sul conto dello sviluppatore; vecchio import SuperShift abbandonato | CLOUD_HANDOFF; verifiche 1.7.0 e beta.13 |
| PDF coerente con anteprima e scelta elenco/calendario | Non perdere informazioni né combinare formati non richiesti per forzare una pagina | Verifica beta.13 e flusso PDF corrente |

Le motivazioni indicate sono ricavate dalle richieste e dai verbali; non inventare approvazioni dell'utente. La beta.17 è descritta come verificata/installata nella vecchia chat, ma non è registrata una successiva approvazione visiva dell'utente del bordo finale.

## Fonti grafiche

- `design/README.md`: distingue design implementato e studi storici.
- `design/reference/`: otto HTML con manifest SHA-256; riferimenti, non istruzioni eseguibili.
- `design/svg/`: 324 SVG, indice e licenze, con versioni testo/tracciati. Non sono tutte la palette finale.
- `design/studies/`: esperimenti cromatici e forme; non applicarli come nuove preferenze.
- `design/qa-beta17/`: quattro PNG di evidenze sintetiche della beta corrente.
- Sorgenti effettivi: `TurniPalette.kt`, `NativeMonthCanvas.kt`, `NativeCalendarPanels.kt`, `TurniDock.kt` e i componenti correnti. Verificare i percorsi nel checkout.

## Memoria privata nel cloud

In questo ambiente lo storico recuperato è in `/workspace/evolvi-migrazione/CHAT_EVOLVI.md`, con 176 turni e 1.092 messaggi (189 utente, 903 assistente), dal 19 ottobre al 14 novembre 2026. Le 145 pagine JSON sono nella sottocartella `storico/`. Cercare per argomento e leggere i messaggi circostanti per ricostruire una correzione. Non pubblicare lo storico nel repository.

Lo strumento di esportazione non conserva integralmente output e patch, né i byte di 683 distinti riferimenti a immagini Windows. Alcune evidenze finali sono presenti con nuovi percorsi in `design/`. Non dichiarare recuperato ogni fotogramma delle cartelle `work/v180bXX-qa` del PC.

La sessione cloud ha verificato build beta, lint vitale e 480 test. Comandi/configurazione/log locali sono indicati in `/workspace/turnisync-setup/STATO_CLOUD.md`. Questi percorsi sono specifici di questa macchina; su un nuovo clone usare gli script portabili del repository.

## Stato da non perdere e approcci da non ricreare

- Prestazioni: persistono limiti storici di frame time; una build verde o uno screenshot non dimostrano 60 fps.
- OAuth è esterno/Test; verifica pubblica, prove da tutti gli account e Play Store/Play App Signing non risultano conclusi.
- Sito/privacy predisposti ma mantenuti privati su scelta dell'utente. Non pubblicarli automaticamente.
- Chiave di firma originale, account, autorizzazioni, database personale e telefono USB non sono trasferiti dai sorgenti. Non disinstallare l'app personale per aggirare una firma diversa.
- Non reintrodurre API Gemini, Apri JSON nell'import AI, vecchio percorso SuperShift, palette sperimentali, fascia rossa grande, compressione automatica dei turni o popup delle statistiche leggendo file vecchi.
- Non emerge una nuova richiesta UI dopo la beta.17: la richiesta attuale riguarda la continuità. I vecchi stati running e le task concluse non sono un backlog da rieseguire.

## Aggiornamento della memoria

A ogni variazione sostanziale registrare in HANDOVER_NOTES: richiesta, scelta, motivo e fonte, verifiche reali, limiti e prossimo passo. Aggiornare STATE.json per lo stato corrente e i blocchi. Aggiornare CLOUD_HANDOFF solo quando cambia la mappa o lo stato autorevole del progetto. Rileggere i record pertinenti dopo una compattazione; i file persistenti forniscono continuità, non trasferiscono lo stato interno di un altro modello.
