# Verifica v1.4.5 — 26 ottobre 2026

## Interventi

Widget settimanale, due settimane e mensile: giorni della settimana soltanto nella riga della prima settimana, compreso il settimanale basso già installato. Le lettere sono allineate alla prima riga delle date; le righe successive riservano l’intestazione alla sola data. Rimossi i metadati di straordinario, trasferta e note dal disegno e dalla descrizione accessibile; restano turno, attacco, reperibilità, festività, oggi e indicatori di ulteriori turni. Header delle celle uniforme a 14 dp, recuperando spazio nelle righe alte; rimossi renderer, palette e policy dei vecchi metadati.

Statistiche: ingresso di hero, forme e contatori guidato dal medesimo Animatable del periodo, indipendente dalla creazione/disposizione degli item LazyColumn. Nessun LaunchedEffect locale riavvia l’ingresso delle schede durante lo scroll. Anche una scheda ancora fuori schermo raggiunge virtualmente lo stato finale; quando entra nel viewport si presenta completa. Cambio Mese/Anno/Personalizzato e date selezionate riavviano il clock; la chiave include la modalità, anche se l’intervallo coincide. Tutti i modelli assegnati restano inclusi come in v1.4.4.

Calendario: indicatore messaggio a sinistra della data, con ingresso/uscita in dissolvenza. È presente se esiste una nota comune del giorno oppure una nota su almeno uno dei turni del lavoro selezionato. Nessuna distinzione nell’icona. Le note dei turni sono riportate integralmente sotto il turno nel dettaglio del giorno; quelle comuni hanno una propria anteprima nello stesso contenitore scorrevole. Note e straordinari sono blocchi indipendenti, con espansione animata: la presenza di uno non nasconde l’altro.

## Collaudo

- `assembleDebug test` superato: 345 test debug e 345 release, zero errori/failure/skipped. Test nuovi per presenza/rimozione delle note, turni multipli, coesistenza note/straordinario/trasferta, completamento delle schede fuori viewport e monotonicità della timeline condivisa.
- APK v1.4.5 (26) installato con aggiornamento sul Realme 8 Pro e firma originale verificata.
- Prova reale: su 26 ottobre impostati temporaneamente +30 minuti (stacco 14:06), nota sul turno, nota del giorno e trasferta. Screenshot confermano note e straordinario contemporaneamente visibili nel popup, messaggio nella cella e widget senza metadati. Tre Annulla ripristinano esattamente il database iniziale.
- Screenshot widget scuri e chiari; settimanale basso con lettere dei giorni; due settimane e mensile novembre con lettere soltanto nella prima riga. Nessuna modifica alle dimensioni o alla disposizione del launcher.
- Scorrimento statistiche verso le schede inferiori e ritorno al resoconto; passaggio Anno/Personalizzato/Mese. I dati sono disponibili globalmente, le schede vengono ancora composte in modo lazy per evitare lavoro inutile, mentre lo stato animato non dipende dal viewport.
- Database finale identico byte per byte alla copia precedente al collaudo: 122 assegnazioni, 11 modelli, un lavoro, due rotazioni. Tema Scuro ripristinato. Nessun crash dell’app osservato nelle prove; un dump UIAutomator è terminato con exit 137 ed è riuscito al tentativo successivo.

## Evidenze e limiti

Evidenze in `work/v145-qa/`: `day-and-shift-notes.png`, `calendar-note-marker.png`, `widgets-clean-with-extras.png`, `month-widget-weekdays.png`, `widgets-light.png`, `statistics-lower.png`, `statistics-return-top.png`, `statistics-year.png`, `statistics-custom.png`, database e verification.json.

Non eseguito un nuovo benchmark complessivo dei frame: non si dichiarano risolti tutti i rallentamenti di primo caricamento documentati in v1.4.4. Rimane la resa compatta per host legacy estremamente bassi. Nessuna modifica Google Cloud, release GitHub o accesso OneDrive.

APK SHA-256: `3e5e54de45bab720690970241b121e797dadaa8c6be9c2805c1e6c131a3eb054`. Hash locale e installato uguali. Certificato SHA-1 `AB:1B:01:83:6A:C9:AC:F1:32:F5:F1:4E:D4:7A:C8:66:DD:9C:B6:01`.
