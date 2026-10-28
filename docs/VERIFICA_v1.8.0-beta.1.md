# TurniSync 1.8.0-beta.1 (46) — revisione del 28/10/2026

## Interventi richiesti
- Rimosse le quattro caselle Lavoro/Riposo/Assenza/Altro dall’editor condiviso per creazione, modifica e revisione AI. Le categorie dei modelli esistenti rimangono nei dati per non alterare conteggi e backup.
- Conferma di abbandono della revisione AI centrata: azione sicura Continua revisione in evidenza, scarto rosso, descrizione esplicita della perdita della sola bozza, X per restare, ingresso/uscita animati. Dimensione e scroll adattivi per schermi piccoli/font grandi.

## Revisione tecnica e correzioni
- Esaminati catalogo, calendario/periodi/rotazioni/gomma, statistiche, salvataggio locale/backup, importazione AI, sveglie, widget e PDF. Le verifiche automatiche disponibili coprono questi percorsi; non equivalgono a una prova universale di ogni combinazione/dispositivo.
- Eliminati oltre 5.000 righe di schermate obsolete non raggiungibili: vecchio catalogo e vecchia importazione specifica per tabellone. Conservati gli helper condivisi del catalogo e i parser/adattatori ancora coperti da test.
- Corretto un errore reale di compatibilità delle sveglie: setShowWhenLocked/setTurnScreenOn sono protetti da API >=27, con flag Window equivalenti su API26. Min/target/compile SDK e Java restano quelli richiesti dal progetto.
- Contatori delle statistiche: niente String.format/Formatter nel ciclo dei frame; riutilizzo delle etichette quando il numero non cambia. Due regressioni su formato italiano, suffissi, numeri negativi/limite e caching.
- Transizioni Calendario/Statistiche: conservazione dello stato e breve movimento d’ingresso, eliminando il fade simultaneo di due buffer a schermo intero. Le schermate nascoste sono immediatamente fuori dal disegno e dagli hit target. Copia verificata del file precedente conservata in QA.
- PDF: invalidazione immediata dell’anteprima quando cambiano le opzioni, impedendo la condivisione della versione precedente mentre parte la rigenerazione; nomi temporanei univoci al posto del solo timestamp.
- Beta dedicata: non debuggable, R8 e riduzione risorse attivi, profili ART inclusi. Stesso certificato degli APK già installati, nessuna disinstallazione/downgrade dei dati. Il build type debug resta disponibile per lo sviluppo.

## Verifiche eseguite
- Build beta riuscita, test Debug/Release: 432 per variante, zero fallimenti/errori.
- Lint Debug: 0 errori/134 avvisi. Lint Beta finale: 0 errori/135 avvisi. Gli avvisi rimasti riguardano principalmente risorse/icone, testo italiano statico, ordine dei parametri Compose, dipendenze e attributi per API successive; non sono stati nascosti con una baseline. I commit sincroni di approvazioni backup/cooldown restano intenzionali per la persistenza immediata.
- 72 rendering reali RemoteViews nel build di verifica, su formati, temi e font diversi; controlli PDF mese/anno nativi superati.
- Sul Realme Android13 con APK ottimizzato: creazione/modifica turno senza caselle, importazione fixture JSON e conferma centrata (Continua e Scarta), anteprima PDF annuale su una pagina, gesto intervallo senza modifica al primo long press, widget mensile con dati reali.
- Sveglia di prova: autorizzazioni esatte/notifiche consentite, registrato `Alarm playback started; test=true; alarmVolume=3` il 28/10 alle 16:27 sul telefono. Il servizio non risultava più attivo dopo il limite della prova. Configurazione globale/spunte/orari preservati.
- Verifica indipendente Java contro l’APK R8: debuggable=false e hash del JSON normalizzato identico prima/dopo. I tentativi iniziali con runner Kotlin di debug non erano compatibili con le classi ottimizzate: la sonda finale è indipendente dal runtime Kotlin; nessuna API diagnostica inclusa nell’APK dell’utente.
- SHA256 JSON senza exportDate: `0a53a478ed11eacdf95e03e83d0fc64d06faa19e2d9e33429f7450ab72e54289`.
- APK installato identico all’APK consegnato; mapping R8 conservato insieme agli artefatti per eventuali crash della beta.

## Prestazioni: risultati e limiti
Sequenza ripetuta di 10 passaggi fra Calendario e Statistiche sullo stesso Realme, dopo apertura iniziale; misure Android gfxinfo. I risultati variano fra campioni e non sono un benchmark di laboratorio.

| Campione | Frame | Janky Android | p50 ms | p95 ms | p99 ms |
| --- | ---: | ---: | ---: | ---: | ---: |
| baseline-idle | 493 | 74 | 24 | 48 | 200 |
| beta-first | 526 | 96 | 23 | 42 | 113 |
| beta-final | 527 | 247 | 28 | 44 | 113 |
| beta-transform | 527 | 191 | 24 | 38 | 109 |

Il campione finale riduce p95 da 48 a 38ms e p99 da 200 a 109ms rispetto alla baseline, ma il conteggio Janky non mostra un miglioramento uniforme e restano frame oltre il budget. Non dichiarare zero lag o prestazioni garantite su tutti i telefoni. Il tentativo di compilazione ART manuale speed-profile è stato rifiutato dal comando OEM; la beta è stata testata senza fare affidamento su quel passaggio.

## Limiti della candidata beta
- Prove fisiche su un solo dispositivo Android13; il ramo API26 è verificato staticamente, non su un secondo dispositivo.
- Le schermate nuove sono state ispezionate in tema scuro; i renderer widget sono verificati anche chiari.
- Nessuna nuova operazione cloud di backup/ripristino o sincronizzazione Google è stata eseguita in questa sessione: copertura di regressione e verifiche live precedenti restano documentate nelle versioni precedenti. Accesso OAuth con altri account da provare con i beta tester.
- I documenti CCNL esterni/allegati non sono incorporati nel JSON: resta il limite già documentato del backup, distinto da modelli, calendario, rotazioni, note e provenienza ex turno.
- Nessuna pubblicazione automatica GitHub/store. Questa è una candidata APK per test; la firma mantenuta è quella della serie di APK di sviluppo esistente.

APK: `TurniSync-v1.8.0-beta.1.apk`, 3,255,442 byte.

SHA256: `86c3bbca761342abe9073158b1b3a2792d83f77201b8ecad89e9c3a8bada0750`

Evidenze: `C:\Users\marco\Documents\Codex\2026-10-19\ciao-sei-il-master-orchestrator-dell\work\v180-beta-qa`. Mapping: `C:\Users\marco\Documents\Codex\2026-10-19\ciao-sei-il-master-orchestrator-dell\outputs\beta-symbols-v1.8.0-beta.1\mapping.txt`.
