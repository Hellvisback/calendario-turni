# TurniSync 1.8.0-beta.25

Versione Android 70. [Schermate sul Realme 8 Pro](../design/qa-beta25/reperibilita-festivita.png).

## Reperibilità

L’anteprima mostra i turni effettivi dei prossimi sette giorni, con le sigle dei modelli e una legenda dei nomi. La campanella usa le stesse regole del calendario. In Manuale si può toccare un giorno per attivare o disattivare la reperibilità. Per le altre date si può usare la campanella nel dettaglio del giorno. Le modalità Per turno e Stagionale condividono una griglia con un massimo di tre colonne, adattata alla larghezza e alla dimensione del testo.

## Festività

Le schede Nazionali, Patroni e CCNL condividono la stessa selezione di ricorrenze. Ogni riga mostra la data nell’anno corrente. Il conteggio in alto considera tutte le categorie. Il ripristino richiama i valori predefiniti del catalogo.

## Verifica sul telefono

Realme 8 Pro, package di prova separato `com.turni.calendario.setupqa`, dati sintetici. Verificati i temi chiaro e scuro, i giorni con tre e cinque turni e quelli vuoti. Una data reperibile proveniente dalla configurazione precedente è stata disattivata, verificata dopo un riavvio e riattivata. Le scelte Mattina per la modalità Per turno e Pomeriggio per il periodo stagionale si salvano separatamente. Sono rimasti presenti tutti i dodici turni del calendario di prova.

Verificati il cambio di categoria delle festività, la disattivazione di Capodanno, l’attivazione di San Marco, la persistenza delle due scelte dopo il riavvio e il ripristino dei valori predefiniti.

488 test unitari superati. Il package personale è com.turni.calendario.
