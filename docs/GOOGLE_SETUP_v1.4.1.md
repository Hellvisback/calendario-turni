# Configurazione Google TurniSync — 26 ottobre 2026

Progetto: **TurniSync**, ID `ascendant-yeti-512823-f8`, numero `695923616233`.
Proprietario/contatto sviluppatore: `marcograziani72@gmail.com`.

- API Google Drive e Google Calendar attivate.
- Client OAuth Android: `TurniSync Android APK`, package `com.turni.calendario`.
- SHA-1 della firma esistente: `AB:1B:01:83:6A:C9:AC:F1:32:F5:F1:4E:D4:7A:C8:66:DD:9C:B6:01`.
- Client ID pubblico: `695923616233-e3ei05u29jnha8unfi43ingveesmbeup.apps.googleusercontent.com`.
- Scope: openid, userinfo.email, userinfo.profile, drive.appdata, calendar.calendarlist.readonly, calendar.events.
- Stato console: **Esterno / Test**, account proprietario aggiunto agli utenti di test.

Il proprietario del progetto non è un account preimpostato nell’app: ogni utente sceglie il proprio account Google. Finché il progetto resta in Test, l’accesso OAuth è limitato agli utenti di test autorizzati nella console. Per la distribuzione pubblica restano da completare branding, URL pubblici reali di sito/privacy e l’eventuale verifica richiesta da Google. Non è stata pubblicata una privacy policy fittizia.

Il pulsante Accedi con Google avvia il selettore ufficiale. I token non vengono salvati nello snapshot. Il backup usa il file privato `turnisync-backup-v3.json` in appDataFolder; il ripristino valida lo snapshot prima di applicarlo. Google Calendar richiede la scelta esplicita della destinazione e modifica soltanto gli eventi dell’app.

Backup automatico: JobScheduler persistente, rete richiesta, intervalli 6/12/24/168 ore; Android può posticipare l’esecuzione. Il servizio non apre richieste di consenso in background. Un consenso scaduto viene segnalato e richiede un nuovo collegamento. La data di successo cambia solo dopo la risposta positiva di Drive.

Collaudo remoto reale Drive/Calendar e verifica dell’ultima versione sul telefono **non completati**: il servizio ADB è bloccato dal profilo Windows corrente. Nessun caricamento o sincronizzazione è dichiarato riuscito senza risposta del server.
