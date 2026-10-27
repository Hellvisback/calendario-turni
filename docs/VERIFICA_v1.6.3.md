# Verifica TurniSync 1.6.3 (35) — 27 ottobre 2026

## Modifiche

- Rotazioni: il centro del numero di ciclo passa da 7 a 10 dp dai bordi superiore/destro. Il badge ha raggio 5 dp, quindi resta interamente all'interno del bordo di selezione, con margine. La data lascia 2 dp in più al badge soltanto nei giorni di inizio ciclo; il quadrato festivo segue lo stesso centro della data. La campanella ha una posizione separata sotto al numero di ciclo. Le etichette lunghe dei cicli sono contenute nel badge mediante misura del testo.
- Widget due settimane: testata da 22 a 28 dp, titolo da 13 a 15 sp. Altezza dei bitmap delle righe ricalcolata sottraendo gli stessi 6 dp alla disponibilità complessiva dei giorni: nessuno stiramento da una dimensione di header incoerente.
- Tutti i widget condividono il quadrato festivo aggiornato: lato leggermente ridotto, margine interno sopra/sotto e contorno quadrato arrotondato. Il numero mantiene il colore ordinario o quello di oggi/reperibilità; il rosso rimane sul contorno. Gestite anche larghezze molto strette evitando lati negativi.
- Nessuna modifica alla sequenza della rotazione, ai conteggi o alle assegnazioni dell'utente.

## Verifiche

- `assembleDebug test`: BUILD SUCCESSFUL. 375 test Debug e 375 Release superati, zero errori, fallimenti e test saltati.
- APK installato con aggiornamento `adb install -r` sul Realme 8 Pro cb08bc57.
- Screenshot del launcher reale prima/dopo: testata del widget settimanale e del widget a due settimane, quadrato festivo del 1 novembre, widget mensile di novembre a sei settimane. Controllati sia tema chiaro sia scuro.
- Screenshot dell'app: 1 su un giorno con reperibilità; 1 sul 4 ottobre festivo a ridosso del bordo destro della griglia; 2 e 3 nei cicli successivi dopo cambio mese; 1 sull'8 dicembre con festività e reperibilità in tema chiaro.
- Il giorno festivo del widget giornaliero usa lo stesso renderer condiviso; il giorno corrente della QA non è festivo. Verificato visivamente il layout ordinario del widget giornaliero, senza simulare la data del dispositivo.
- Una riselezione di QA del medesimo turno del 12 ottobre aveva rigenerato soltanto l'ID dell'assegnazione. È stata ripristinata la copia originale, con confronto completo prima/dopo. Al termine database identico alla baseline, incluse le identità delle 122 assegnazioni e gli 11 modelli; nessuna rotazione applicata ai dati dell'utente. Tema scuro iniziale ripristinato.
- `git diff --check` sui file modificati: nessun errore.

## Artefatti

- APK: `C:/Users/marco/Documents/Codex/2026-10-19/ciao-sei-il-master-orchestrator-dell/outputs/TurniSync-v1.6.3.apk`
- SHA-256: `301a9404821742cd680565abecc6f1a0436545f553fdca74151399baa237e314`
- Screenshot e confronti DB: `C:/Users/marco/Documents/Codex/2026-10-19/ciao-sei-il-master-orchestrator-dell/work/v163-qa`
- Log build: `C:/Users/marco/Documents/Codex/2026-10-19/ciao-sei-il-master-orchestrator-dell/work/v163-build.log`

Verifica circoscritta al numero del ciclo e ai layout dei widget. Nessuna pubblicazione GitHub effettuata; restano i limiti funzionali documentati nelle verifiche precedenti.
