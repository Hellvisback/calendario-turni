# Verifica TurniSync 1.6.5 (37) — 27 ottobre 2026

## Ferie nelle festività

- Nuova opzione in Impostazioni → Conteggio statistiche → Ferie nei giorni festivi. Inizialmente disattivata.
- All'attivazione viene suggerito il modello con ID standard ferie, quando esiste. L'utente può scegliere altri modelli di qualsiasi lavoro; il conteggio usa gli ID, senza deduzioni dai nomi, dalle sigle o dai colori.
- Si escludono dai conteggi i modelli selezionati solo nelle festività abilitate nel catalogo dell'app. Nascondere le festività nel calendario non cambia la regola. Sabati e domeniche non vengono automaticamente assimilati alle festività configurate.
- Prima si risolvono le regole esistenti sul turno originale: Ferie sopra Riposo/Smonto continua quindi a contare il turno originale quando previsto dalla relativa regola. Le assegnazioni nel calendario restano inalterate.
- Contatori del repository, dati per i KPI/grafici e resoconti usano lo stesso filtro; il cambio di preferenze o festività invalida i calcoli memorizzati. Gli altri turni della stessa giornata rimangono conteggiati.
- Preferenze persistite localmente e incluse nel backup JSON, con campi opzionali compatibili con lo schema 4. Backup precedenti che contengono le preferenze ma non i nuovi campi mantengono la funzione disattivata. Lo spegnimento non reinserisce modelli deselezionati.

## Modifica calendario

- Periodo: frecce complete a tratto sottile in alto, ai lati della data; margini interni distinti dal bordo della cella e dalle icone di nota/reperibilità. Prima riga del turno allineata alle altre celle.
- La richiesta di colorazione comprende l'intero intervallo, inclusi entrambi gli estremi, in avanti e indietro. Il Canvas calcola solo l'intersezione con le date visibili di ogni pagina, senza enumerare intervalli arbitrariamente grandi per l'animazione.
- Tutti i giorni interessati animano insieme con la durata già prevista, senza far ripartire i colori delle operazioni precedenti. La validità dell'evidenziazione è legata agli ID delle assegnazioni realmente applicate: Annulla non lascia colori residui quando ritorna un turno con lo stesso modello ma con l'identità originale.
- Contrasto di campanelle, note e indicazioni ex turno adattato allo sfondo colorato.
- Gomma: bordo tematizzato da 2 dp con transizione colore da 200 ms e stato selezionato accessibile. L'aggiunta del bordo non sposta i controlli e conserva l'animazione dell'icona.

## Validazione

- `assembleDebug test`: BUILD SUCCESSFUL. **383 test Debug + 383 Release**, zero errori, fallimenti o test saltati.
- Otto nuovi test coprono: condizioni dell'esclusione, interazione con il turno originale, nomi personalizzati, coerenza di ore/giorni/conteggi, festività disabilitate/visualizzazione nascosta, backup e compatibilità, disattivazione senza risuggerire un modello, date colorate attraverso il cambio mese e applicazione/annullamento di un intervallo completo.
- APK finale installato con `adb install -r` sul Realme 8 Pro cb08bc57.
- Sul dispositivo: attivazione della nuova opzione, proposta di Ferie, deselezione e disattivazione con controllo delle preferenze salvate; verifica dell'interfaccia dei modelli selezionabili.
- Sul dispositivo: periodo 12→15 ottobre e 23→21 ottobre, riempimento di tutti i giorni, Annulla per ciascuna operazione, bordo della gomma. Controllati screenshot finali e confronto delle assegnazioni.
- Frecce e margini controllati in chiaro e scuro su una giornata con reperibilità. Tema scuro iniziale ripristinato.
- Dati originari identici al termine della QA, incluse identità delle 122 assegnazioni, 11 modelli e informazioni giornaliere. Le prove dei periodi sono state annullate. Nel JSON sono aggiunti soltanto i due nuovi campi delle preferenze, lasciati a false e insieme vuoto.
- `git diff --check`: nessun errore nei file dell'app.

## Artefatti

- APK: `C:/Users/marco/Documents/Codex/2026-10-19/ciao-sei-il-master-orchestrator-dell/outputs/TurniSync-v1.6.5.apk`
- SHA-256: `cdcea3fcc6365242cf1acb2f713e4c23161239de77af7760a2d0b3044f97460a`
- QA e screenshot: `C:/Users/marco/Documents/Codex/2026-10-19/ciao-sei-il-master-orchestrator-dell/work/v165-qa`
- Log build: `C:/Users/marco/Documents/Codex/2026-10-19/ciao-sei-il-master-orchestrator-dell/work/v165-build.log`

La nuova logica statistica è verificata con dati isolati nei test; sul telefono non sono state lasciate ferie o configurazioni di prova. Nessuna pubblicazione GitHub e nessuna nuova verifica completa della sincronizzazione remota, delle sveglie o dell'importazione SuperShift.
