# Verifica v1.5.0 — 27 ottobre 2026

## Modifiche

Widget: riquadri dei turni con altezza massima 29 dp nei formati griglia, testo leggermente ridotto (sigle 11,5 dp, attacco 10,5 dp nei riquadri alti, date 12,5/13,5 dp). Resta la riga unica dei giorni della settimana. Contorno festività geometricamente quadrato con angoli moderatamente arrotondati, indipendente dalla larghezza del numero; nessuna colorazione rossa del numero.

Calendario: pulsante Oggi alla destra delle frecce, visibile quando il mese non è quello corrente. Ripristina il mese attuale e chiude l’eventuale selezione senza modificare i turni. Dati delle celle conservati con derivedStateOf, aggiornati in risposta ai dati effettivi e non ad ogni ricomposizione della selezione.

Dettaglio giorno: richiamo compatto Festività con icona e nome/nome multiplo, ingresso/uscita animati. holidaysForDate usa gli stessi enabledHolidayIds e showHolidays che governano il bordo; isHoliday resta compatibile come primo risultato. Le selezioni delle festività sono quelle attualmente globali dell’app; nessuna nuova configurazione per singolo lavoro è stata introdotta. Test su tutte le voci del catalogo nel 2026/2027/2028, festività coincidenti e disattivazione.

PDF: foglio bianco limitato alla bitmap reale, contenitore neutro, formato e orientamento espliciti nell’anteprima e nello zoom. Elenco con nomi senza ripetere inutilmente la sigla. Modalità Periodo: fino a 14 giorni elenco, fino a 3 mesi calendario mensile, oltre panoramica. L’intervallo effettivo è sempre quello scelto. Se un calendario con più mesi non entra sul foglio iniziale A4/A3, viene tentato A2 verticale. Non vengono tagliati dettagli, aggiunte pagine o sostituiti dati: se anche A2 non basta compare l’errore esplicito. Massimo 12 mesi per calendario come in v1.4.6. Nessun elenco aggiunto ai calendari.

Navigazione: il gruppo primario Calendario/Statistiche non ricrea una destinazione vuota ad ogni tap. Report precomposto dopo 900 ms e conservato; statistiche complessive calcolate su Default da snapshot in sola lettura e correttamente rilasciato anche in caso di cancellazione. Clock d’ingresso riprodotto al cambio vista/periodo, indipendente dallo scroll. Nessuna modifica al disegno/durata della goccia.

## Verifiche

- assembleDebug test riuscito: 355 test debug e 355 release, zero failure/error/skipped.
- APK v1.5.0 (31) installato sul Realme 8 Pro con firma originale. Hash del file installato uguale all’output.
- PDF generati dall’APK ed estratti: Elenco e Mese A4, Anno A3, Anno con durata/pause/festività A2, Periodo ottobre A4, Periodo 1–14 ottobre elenco A4, Periodo ottobre–febbraio panoramica A3. Tutti una pagina verticale; render Poppler e controllo visivo eseguiti. Pypdf conferma pagine/dimensioni e testo dei dettagli selezionati.
- Prove reali: San Francesco nel popup del 4 ottobre; Ognissanti disabilitato temporaneamente scompare da bordo, descrizione e dettaglio; riabilitato al termine. Test unitari coprono anche più festività sullo stesso giorno. Oggi provato da novembre con ritorno ad ottobre.
- Screenshot widget settimanale, due settimane, giornaliero e novembre mensile con sei righe. Entrata/uscita modifica e apertura/ritorno Modelli verificate senza alterare assegnazioni.
- Database semanticamente identico al backup iniziale: 122 assegnazioni, 11 modelli, un lavoro e due rotazioni, stessa selezione festività. Differenze di serializzazione attese: exportDate 26→27 ottobre e ordine di Ognissanti nel set riattivato. Non è identico byte per byte.

## Fluidità: misura e limiti

Stessa sequenza ADB di cinque andate/ritorni Calendario/Statistiche, processo riavviato e 2 secondi iniziali, build debug. Gfxinfo prima: 384 frame, 178 janky (46,35%), p90 44 ms, p95 200 ms, p99 400 ms. Dopo: 427 frame, 96 janky (22,48%), p90 40 ms, p95 150 ms, p99 350 ms. Sono prove sul dispositivo con interazione ADB, non benchmark controllato: non escludono effetti termici, cache o attività di sistema. Miglioramento osservato, restano picchi importanti e non si dichiara eliminato ogni rallentamento. Nessun nuovo crash dell’app osservato; buffer crash contiene episodi del 26 ottobre precedenti a questo aggiornamento.

Evidenze nel workspace Codex work/v150-qa: screenshot, PDF e PNG, file gfxinfo prima/dopo, copie database e verification.json. APK outputs/TurniSync-v1.5.0.apk; SHA-256 7fc3bfa80dda0455b95ca517f4eead1f8c2f8110abbf2cd47d86fe41b5887c51. Certificato originale SHA-1 AB:1B:01:83:6A:C9:AC:F1:32:F5:F1:4E:D4:7A:C8:66:DD:9C:B6:01. Nessuna pubblicazione GitHub, modifica Google Cloud o accesso OneDrive.
