# Verifica TurniSync 1.7.3 (45) — 28/10/2026

## Modifiche
- Periodo rimosso dalla barra: tre modalità visibili Cambia, Più turni e Rotazioni. RANGE resta solo uno stato interno di rendering per le frecce dell’ancora.
- Long press in Cambia avvia sostituzione per intervallo; in Più turni avvia aggiunta per intervallo mantenendo i turni esistenti. Gli estremi sono inclusi, anche selezionati al contrario o fra mesi. Il primo long press non applica turni; il tap finale applica l’operazione. Un solo snapshot/Undo e una notifica dati per l’intero intervallo. Cambio strumento, modalità, pennello, uscita e Back azzerano l’ancora. Gomma e campanella mantengono priorità e comportamento indipendente.
- Imposta sveglia è un pulsante pieno, arrotondato, con contrasto e touch target adeguati. Conservati normalizzazione, chiusura tastiera, conferma e salvataggio per campo di 1.7.2.
- Dock: feedback di pressione bouncy precedentemente disegnato con drawRect su Box non ritagliato. Ora clip circolare prima del feedback, così non compare il rettangolo sul cambio Calendario/Statistiche. Non modificati contorno e animazione principale del dock.
- AppDateGrid condiviso: gerarchia e navigazione del mese uniformi per PDF e Statistiche, mese animato e selezione con colori del tema.
- Statistiche Personalizzato: popover largo 280 dp massimo disponibile, posizione orizzontale dipendente da Dal/Al con spostamento animato; ingresso più graduale con origine vicino al campo premuto. Toggle alla seconda pressione e chiusura al cambio tab/schermata conservati.
- PDF: selettori mese/anno a griglia sul pannello animato dell’app, calendario intervallo condiviso, filtro modelli con sigle/colori/selezione personalizzata. Bozza delle date applicata con Conferma dopo l’animazione di uscita. Anteprima e generatore PDF invariati.
- SelectorSheet riutilizza l’animazione dei pannelli nativi; sfondo attenuato e fondo senza gap per non mostrare il pulsante PDF dietro al pannello. Nuovo parametro dimAmount opzionale, default zero per non cambiare i pannelli del calendario.
- Conferma uscita dalla revisione AI personalizzata con X, spiegazione della bozza, Continua revisione e Torna al documento. Nessun salvataggio automatico introdotto.
- Gomma nell’editor rotazione: contenitore 66×66 dp con bordo quadrato arrotondato e animazione preesistente.

## Verifiche
- Build debug e 430 test JVM per variante debug/release superati, zero fallimenti/errori. Nuova regressione verifica aggiunta per intervallo a cavallo del mese, preservazione identica dei turni base, estremi inclusi, giorni esterni invariati e undo unico.
- APK installato con -r sul Realme cb08bc57, senza disinstallazione o cancellazione dati.
- Prova UI: long press Cambia e Più turni, giorni 7–9 ottobre. Verificati due turni su ciascuna data in aggiunta; entrambe le prove annullate con una sola pressione Undo.
- Screenshot sinistra/destra del calendario statistiche, passaggio da un campo all’altro e chiusura cambiando tab.
- PDF: aperti mese, anno, periodo/elenco e filtro. Conferma mese verificata e anteprima mantenuta. Ultimo pannello intervallo verificato senza il pulsante Condividi visibile sotto al foglio.
- AI: caricata fixture locale di revisione (non risposta AI live), verificata la nuova conferma. Continua conserva la revisione; Torna al documento rientra nella sorgente. Nessun modello/ciclo salvato. File temporaneo rimosso dal telefono.
- Sveglie: verificato pulsante Imposta pieno; regola temporaneamente attivata e poi riportata allo stato originario spento, nessun allarme globale attivato.
- Editor rotazione: selezionata Gomma e ispezionato il bordo quadrato. Nessuna modifica alla sequenza salvata.
- Screenshot del dock tenuto premuto: feedback circolare e nessun rettangolo grigio nel campione. Verifica visiva su un dispositivo; nessuna misura FPS o garanzia su tutti i frame/dispositivi.
- Database finale identico alla baseline escluso exportDate. Tema originale preservato. Le prove visive di questa task sono in tema scuro.

Evidenze: `C:\Users\marco\Documents\Codex\2026-10-19\ciao-sei-il-master-orchestrator-dell/work/v173-qa/`.

SHA256 APK installato e consegnato: `6cac25c8ac2e9942d1cf903e6920889851ff4fb9b5bffdc0223645ef3496e5f2`
