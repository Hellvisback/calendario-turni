# Verifica TurniSync 1.6.0 — 27 ottobre 2026

## Consegnato e installato

- APK nativo Android `com.turni.calendario`, versione 1.6.0, versionCode 32.
- Installazione `adb install -r` riuscita sul Realme 8 Pro cb08bc57; nessuna disinstallazione né cancellazione dei dati.
- Artefatto: `C:/Users/marco/Documents/Codex/2026-10-19/ciao-sei-il-master-orchestrator-dell/outputs/TurniSync-v1.6.0.apk`.
- SHA-256 APK: `c74f743937b0811c23d8d75e5e40884e2ea876da71bd92dc2d7b0e1cccfb44a7`.

## Conteggio statistiche

Nuova schermata Impostazioni → Conteggio statistiche. Ogni regola associa l’ID del modello visualizzato a uno o più ID di modelli originali dello stesso lavoro. Regole attivabili/disattivabili, modificabili ed eliminabili; la selezione usa pulsanti colorati e pannelli animati. Non dipende da nomi, abbreviazioni o lingua.

Un resolver puro risolve una sola sostituzione, usando esclusivamente `originalShiftDefId` realmente registrato. Non ricostruisce un originale dalla rotazione corrente. Senza originale o con regola disabilitata si conta il modello visibile. La gomma continua a cancellare la provenienza. Calendario, note e assegnazioni non vengono alterati dal conteggio. Gli orari personalizzati del modello visibile non vengono applicati al modello originale; l’eventuale straordinario effettivamente registrato rimane.

Distribuzione, grafico, totali, giorni lavorati e dettaglio per lavoro usano la stessa risoluzione. Le regole sono persistite e incluse nei backup schema 4. I backup precedenti preservano la vecchia regola Ferie → Smonto/Riposo soltanto quando i rispettivi ID legacy esistono; un elenco di regole esplicitamente vuoto non viene riattivato. Installazioni nuove partono senza regole. Eliminazione/spostamento dei modelli pulisce i riferimenti alle regole.

Prova sul telefono: con la regola originale, ottobre mostra 1 Ferie e 5 Riposi nonostante due celle visibili Ferie (30 ottobre ex RIP e 31 ottobre ex MAT). Aggiungendo temporaneamente Mattina fra gli originali, il grafico passa a 0 Ferie / 7 Mattine; ripristinando la regola, torna a 1 Ferie / 6 Mattine. Dati finali identici alla copia migrata iniziale.

## Importazione calendario SuperShift — disponibile, con limiti espliciti

Percorso: Impostazioni → Backup e ripristino → Importa da SuperShift.

Implementati:
- Lettura Google Calendar in sola lettura, scelta esplicita di account, calendario e periodo; paginazione degli eventi e ricorrenze espanse lato Google.
- Selezione `.ics` tramite Storage Access Framework, anche da Drive. Nessun accesso generalizzato ai file Drive.
- Adattatore deterministico: nomi, date, orari, notti, giornate intere, note e luogo ove presenti. Colori del calendario/file se disponibili; palette approvata altrimenti.
- Categorie scelte dall’utente, senza deduzioni da nomi come Ferie/POM/Smonto. Eventi intera giornata inizialmente Altro.
- Anteprima prima di qualsiasi modifica, esclusione duplicati mediante identità evento, assegnazioni native modificabili normalmente, conservazione delle modifiche già fatte a elementi importati.
- Importazione additiva nel lavoro SuperShift; nessuna sostituzione dei dati precedenti. Copia di recupero locale prima di applicare, fsync e verifica del documento; ripristino accessibile da Backup e ripristino → Altre copie.
- Rifiuto completo di file incompleti/sconosciuti, durate non rappresentabili, eventi senza identità e proprietà di ricorrenza ICS non supportate. Per ricorrenze usare Google Calendar. Limiti di dimensione/quantità, decodifica UTF-8 rigorosa e lettura fuori dal thread UI.

**Non è ancora un lettore del backup completo proprietario SuperShift.** Non è stato ottenuto un file originale con schema documentabile. Rotazioni, pause, reperibilità e provenienza ex turno non sono presenti nel calendario standard: l’interfaccia lo comunica prima della conferma e non inventa questi dati. L’importazione è una migrazione una tantum, non una sincronizzazione continua; gli elementi già importati restano invariati.

### Verifiche reali / blocchi esterni

- SuperShift 2026.24 sul telefono: presente calendario con dati; menu Altro usa SuperShift Cloud. La funzione Esportazione calendario apre la proposta Pro; nessun acquisto o prova sottoscritta.
- Drive web sull’account già autenticato: ricerca SuperShift senza file visibili; nessuna voce SuperShift trovata fra le app collegate a quell’account. Non prova l’assenza di copie su altri account o nel backup di sistema.
- Il nuovo consenso Google Calendar in lettura arriva alla schermata Google “app non verificata / in fase di test”; lasciato annullato. Il download reale dagli endpoint Calendar dell’account non è stato completato in questa verifica. Prima di prerelease pubblica completare configurazione/verifica OAuth e prova con calendario di origine reale.
- Google documenta che l’appDataFolder privato è accessibile soltanto all’app che lo ha creato. Non si può realizzare un pulsante universale che scarichi il backup privato di un’altra app usando il client OAuth TurniSync.

Fonti primarie: https://www.supershift.app/help.html ; https://developers.google.com/workspace/drive/api/guides/appdata ; https://developers.google.com/workspace/calendar/api/v3/reference/events/list ; RFC 5545.

## Test

- `gradlew.bat assembleDebug test --console=plain`: successo.
- 375 test Debug + 375 test Release: zero errori, fallimenti o test saltati.
- Nuovi test: regole generiche, rinomina, disattivazione, assenza origine, nessuna concatenazione, separazione lavori, ore coerenti, round trip backup, migrazione, pulizia riferimenti; parser ICS, notturni, UTC, all-day esclusivo, note ripiegate/escape, orari DST non validi, ricorrenze non supportate, UID in conflitto, duplicati, eventi spostati, identità coerente ICS/API, modifica e gomma native dopo importazione.
- Telefono: importati 3 eventi sintetici (15–16 gennaio 2030), 2 modelli, 1 nuovo lavoro. Nota e orari personalizzati conservati; 2 eventi sullo stesso giorno. Tutti i 122 turni precedenti invariati.
- Reimportato stesso file: 0 nuovi / 3 già presenti. Verificato ripristino via UI della copia pre-importazione: confronto JSON esatto con stato migrato precedente.
- File QA rimosso da Download. Nessun dato sintetico rimasto nel calendario.
- `git diff --check`: nessun errore. Log AndroidRuntime osservato senza crash durante le prove.

Screenshot e snapshot locali: cartella `work/v151-qa` della chat (contengono dati personali: non pubblicarli).
