# Verifica TurniSync 1.7.2 (44) — 28/10/2026

## Modifiche
- Conteggio statistiche: riepiloghi coerenti con switch e pulsante Modifica regola per entrambi i meccanismi. Editor separato per ferie nei festivi con selezione in bozza e Salva regola. Spiegati differenza, esempio Ferie/Riposo e ordine di applicazione (originale prima, esclusione festiva dopo).
- Sveglie: aggiornamenti per campo sulla regola attuale, senza sovrascrivere altri switch, impostazione globale o orari. Identità di riga stabile e ordine delle regole preservato. Reperibilità mostra sempre l’orario salvato. Imposta e azione Done normalizzano HH:mm, tolgono focus/tastiera, richiudono l’editor e mostrano conferma animata. Nessuna registrazione di allarmi durante la composizione grafica.
- Riposo minimo: indicatore ! centrato con fondo ambra a contrasto, sollevato e inset rispetto al bordo. Spazio separato dal contenuto e dalla selezione.
- Gomma: pressione prolungata sul primo giorno avvia selezione intervallo in qualsiasi modalità; tap sull’ultimo cancella entrambi gli estremi e tutti i giorni intermedi, avanti o indietro anche fra mesi. Una sola operazione Undo. Sono cancellati i turni del lavoro attivo e la reperibilità manuale; note e turni di altri lavori conservati. Back annulla la selezione senza cancellare. Il tap breve mantiene la gomma selettiva esistente.

## Verifiche
- Build e 429 test JVM per variante debug/release superati.
- Nuove regressioni: un orario modificato non riattiva regole spente né cambia altre regole; nuova ora reperibilità ricalcolata e preservata nel round-trip; cancellazione bidirezionale inclusiva con undo unico, note e lavoro estraneo preservati.
- Realme cb08bc57: inserito 18:45 in reperibilità, premuto Imposta. Verificati testo Alle 18:45, conferma e mInputShown=false. Disattivata, uscita e riaperta: regola ancora spenta e orario conservato. Ripristinato 20:00/spento come prima della prova. Nessuna prova sonora eseguita in questa task.
- UI: editor ferie e riepiloghi ispezionati. Long press in Cambia e Rotazioni; intervallo 7–9 ottobre cancellato e ripristinato con un solo Undo. Primo tap prolungato non cancella. Verificato indicatore riposo all’interno della cella selezionata senza sovrapposizione al bordo.
- APK finale installato con -r. Confronto finale del database identico alla baseline escluso exportDate e ordine non significativo delle regole sveglia (versione intermedia).
- Screenshot e dump: `C:\Users\marco\Documents\Codex\2026-10-19\ciao-sei-il-master-orchestrator-dell\work\v172-qa`. Le prove visive di questa task sono in tema scuro sul Realme; nessuna pretesa di misurazione FPS o verifica su altri telefoni.

SHA256 APK installato/consegnato: `5330038aad36e11465829050fa4c881111c4891d79f9906746868b6a183e59bf`
