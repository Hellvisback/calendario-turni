# Verifica TurniSync 1.7.1 (43) — 28/10/2026

## Modifiche
- Icona applicazione e roundIcon con nuova identità di risorsa `ic_turnisync`, derivata senza modifiche dal logo approvato con anelli blu/ciano. Tutti i receiver widget dichiarano esplicitamente la stessa icona. Nessun cambiamento alle anteprime già aggiornate.
- Settimana: target iniziale 4×1, ridimensionamento orizzontale e verticale abilitato; espandibile anche a due righe.
- Due settimane: target iniziale 4×2, ridimensionamento orizzontale e verticale abilitato; espandibile anche a tre righe.
- Dimensioni minime esistenti preservate; mese minimo largo quattro colonne preservato.
- Nessun limite massimo in dp aggiunto: le dimensioni delle celle dipendono dal launcher e un limite fisso potrebbe impedire proprio le altezze richieste su altri telefoni. Le altezze iniziali non impongono il ridimensionamento dei widget già posizionati.

## Verifiche
- assembleDebug, assembleDebugAndroidTest, test: BUILD SUCCESSFUL.
- 426 test JVM per variante debug/release, zero errori o fallimenti.
- 72 RemoteViews Android reali: temi chiaro/scuro, scala font 100/130/150%, inclusi settimana 320×168 dp e panorama 320×252 dp. Screenshot ispezionati.
- AppWidgetManager: quattro provider con icona esplicita corretta, resize verticale attivo e targetCellHeight rispettivamente 1/2.
- Screenshot del selettore widget Realme: intestazione TurniSync con logo ad anelli corretto. Non disponibile il secondo telefono dello screenshot utente; la cache di quel launcher non è verificabile da questo dispositivo.
- Installazione -r sul Realme cb08bc57, dati applicazione identici prima/dopo (escluso exportDate). Nessuna cancellazione dati o modifica delle posizioni nella home.
- APK installato con hash identico a quello consegnato.

SHA256: `98465fba08f6d29dc3c3490156860cb3cc1b7446dbf6862a4dfb03f8602635e1`

Riferimento Android: https://developer.android.com/develop/ui/views/appwidgets/layouts

Evidenze: `C:\Users\marco\Documents\Codex\2026-10-19\ciao-sei-il-master-orchestrator-dell/work/v171-qa/`.
