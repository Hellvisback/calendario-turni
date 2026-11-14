# Verifica TurniSync 1.8.0-beta.11 (56)

Data: 13 novembre 2026. Dispositivo: Realme 8 Pro, cb08bc57.

## Funzioni completate

- Etichette personalizzate dei giorni: gestione, applicazione con note, archiviazione, conteggio e ricerca delle date nelle statistiche; incluse nel backup schema 5. La griglia conserva le corsie delle icone esistenti.
- Primo avvio in cinque passaggi: tema automatico/chiaro/scuro, lavoro, modelli facoltativi, prova interattiva dei gesti, opzioni avanzate raggruppate. Nessun turno viene inserito automaticamente nel calendario.
- Installazioni esistenti: il database locale viene riconosciuto prima dell'autosalvataggio e l'introduzione viene saltata.
- Ripristino iniziale separato dal salvataggio: collegare Google non crea una copia vuota. Conferma esplicita dopo il riepilogo. Il completamento aspetta il salvataggio atomico locale.
- Salvataggio durevole prima di avanzare nei passaggi. Nome lavoro conservato dopo Continua, avanzamento e chiusura forzata del processo.
- Tema incluso nei backup nuovi; backup precedenti senza il campo restano leggibili.
- Compatibilità aggiornata: compile/target SDK 36, min SDK 26, AGP 8.10.1, Gradle 8.11.1, JDK 17, Kotlin 1.9.25, Compose 1.7.8 e Material3 1.3.2. Wrapper con checksum ufficiale.
- Pannelli chiari: corrette le icone della barra di stato tramite layout della finestra esteso; screenshot del pannello etichette verificato sull'APK finale.
- Ponte di accessibilità preservato nella minificazione R8; gerarchia dei controlli leggibile nella beta non debuggable. Dopo una sessione di Instrumentation occorre riavviare il processo prima della successiva UIAutomation.
- Compatibilità delle sveglie con autorizzazione alla schermata intera sui sistemi recenti; nessuna autorizzazione forzata.

## Verifiche automatiche e dispositivo

- Build Beta R8 e lintVital: riusciti. 469 test JVM, zero errori o fallimenti. Restano avvisi di deprecazione; non è una dichiarazione di lint completamente privo di warning.
- Pacchetto finale com.turni.calendario, versione 56 / 1.8.0-beta.11, target 36. Firma v2 verificata e compatibile con l'installazione esistente. Firma storica Android Debug conservata per non interrompere gli aggiornamenti: non è ancora la procedura di firma definitiva dello store.
- APK finale 3.268.745 byte. SHA-256 8c955b6b0af1c26bc42f992955488a5b56be53d33b8e6bcc5e71afe8a7f6d523. La stessa impronta è stata letta dal base.apk installato.
- Nuova installazione verificata nel pacchetto temporaneo separato com.turni.calendario.setupqa. Il precedente blocco Play Protect relativo al target Android obsoleto non è ricomparso sulla nuova installazione di prova. Non sono state disabilitate protezioni Android.
- Primo avvio, scelta del tema chiaro, nome con tastiera, esempi facoltativi, prova long press 2–5, conclusione e riavvio verificati. Il calendario resta vuoto.
- Nuova installazione con percorso di recupero: importato un JSON sintetico tramite il selettore file, controllato riepilogo e confermato. Dopo il riavvio presenti due modelli, due assegnazioni, una rotazione, reperibilità, nota turno e nota giorno, etichetta Formazione, trasferta e un'ora di straordinario con stacco 17:00. Statistiche: 9 ore, un giorno lavorato, due turni, un giorno Formazione. Tema scuro ripristinato.
- App principale aggiornata senza cancellazione dati. Impronta completa precedente: 95dec23aab601927e8c37cad5cfd7036fc7e2d9deea993012f6c0006bcb52892. Dopo l'aggiornamento: a135d503bc9482c98e9fba8d1f5f5e5fc201d37630b82456855052f73696beac. Rimuovendo esclusivamente il nuovo campo appPreferences.themeMode dalla copia in memoria della sonda si ottiene esattamente l'impronta precedente. Tutti gli altri dati sono identici; nessun contenuto personale è stato stampato dalla sonda.
- Prova sveglia reale a 10 secondi: allarme esatto registrato, ShiftAlarmService attivo e notifica Sveglia di prova osservata; arresto automatico al limite di 20 secondi. Regole esistenti in pausa conservate. Non è una prova audio effettuata con ascolto umano né una certificazione per tutti i produttori Android.
- Tema scuro originario ripristinato. Pacchetto QA, strumentazione temporanea e fixture rimossi dal telefono. Server locale del sito arrestato.

## Prestazioni: limite ancora presente

Campione finale senza interrogazioni UIAutomation durante i cambi: tre passaggi Calendario/Statistiche e ritorno, 314 frame, 47 janky frame (14,97% secondo la metrica corrente Android), mediana 23 ms, percentile 95 a 38 ms, percentile 99 a 85 ms. La metrica legacy segnala 41,40%. La misura precedente con letture di accessibilità intercalate era sensibilmente più alta e non è direttamente rappresentativa dell'uso normale. Restano frame lenti: non promettere 60 fps costanti o assenza totale di lag. Non è stato eliminato alcun effetto richiesto per nascondere il limite.

## Google e sito

- Progetto Cloud: ascendant-yeti-512823-f8, pubblico esterno in modalità Test.
- [utente beta autorizzato] aggiunto e verificato nell'elenco salvato insieme al proprietario. Non è stato eseguito un login dal telefono di Lorenzo; va provato dal suo account personale.
- Messaggi di errore e spiegazioni dei permessi rivisti; nessun account del proprietario preimpostato per gli altri utenti.
- Sito, guida, privacy e termini predisposti su https://turnisync-app.marcograziani72.chatgpt.site, con Marco Graziani e il contatto approvato. Accesso mantenuto privato per scelta esplicita dell'utente. Nessuna nuova pubblicazione pubblica autorizzata.
- Verifica OAuth pubblica ancora incompleta: servono pagine pubbliche e proprietà di dominio verificabile. La beta rimane limitata agli utenti di test; gli avvisi di consenso non vengono nascosti. Gli avvisi per APK esterni allo store non possono essere eliminati con una modifica grafica.
- Non completati pubblicazione Play Store, Play App Signing o passaggio a chiave definitiva; nessun costo o acquisto di dominio effettuato.

## Consegna

APK caricato con il plugin Google Drive e metadati riletti: id 1XBDqBhTS6uZHAmZpAMIUKkyfx3gy5er6, dimensione 3.268.745 byte.

https://drive.google.com/file/d/1XBDqBhTS6uZHAmZpAMIUKkyfx3gy5er6/view?usp=drivesdk

Non sono state ampliate le autorizzazioni di condivisione del file Drive. Nessun messaggio o email inviato. Nessuna release GitHub pubblicata.
