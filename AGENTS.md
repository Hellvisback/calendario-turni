# Istruzioni per lavorare su TurniSync

## Contesto e comunicazione

- Rispondere in italiano; mantenere identificatori, commenti tecnici e log del codice in inglese.
- Leggere prima `docs/CLOUD_HANDOFF.md` e la verifica della beta corrente. Le sezioni datate degli handover sono archivio, non requisiti aggiornati.
- L'app effettiva è Android nativa Kotlin/Compose in `android-app/`. Non sostituirla con i prototipi HTML di design né con `esApp/`.
- Usare il checkout corrente e percorsi relativi al repository. Nessuno script nuovo deve dipendere da `C:\Users\marco`.

## Design e comportamento da preservare

- Material 3 Expressive: base Grafite, accento cobalto condiviso tramite `TurniPalette.kt`, dock grigio con indicatore attivo blu e sole icone.
- Temi chiaro, scuro e automatico. Colori turno distinti e leggibili nei due temi, nei weekend e nelle anteprime del mese adiacente.
- Transizioni fluide e micro-interazioni coerenti; evitare ricreazioni della griglia al cambio strumento e replay degli eventi di colorazione.
- Riquadri turno a dimensioni fisse, eccedenze rappresentate da pallini centrati; non comprimere i turni per farne entrare altri.
- Contorni della cella interni al riempimento e raccordati; indicatore di intervallo compatto, senza grandi fasce rosse.
- Un solo comando indietro nei flussi annidati; X rossa chiude il flusso. Non sovrapporre pannelli al dock o alla tastiera.
- Feedback delle azioni in overlay animato; non aggiungere notifiche statiche che spostano il contenuto. Gli avvisi informativi del giorno, come il riposo minimo, restano parte del suo dettaglio.
- Preservare tutte le funzioni e il modello dati: lavori, modelli, assegnazioni, turno originale della rotazione, reperibilità, straordinari, stacco effettivo, trasferte, note, etichette, regole statistiche e impostazioni.

## Build e verifiche

- JDK 17; SDK compile/target 36, min 26; Build Tools 35.0.0; wrapper Gradle 8.11.1.
- Linux/cloud: `bash scripts/cloud-setup.sh` con SDK/licenze già configurati, poi `bash scripts/check-android.sh`.
- Per installare un SDK nuovo leggere la guida; il flag `--accept-licenses` è un consenso esplicito alle licenze SDK.
- Windows: `scripts/check-android.ps1 -JavaHome <jdk17>`.
- Controlli richiesti: `assembleBeta testDebugUnitTest` in `android-app/`. La beta esegue anche lint vitale e ottimizzazione R8.
- I test unitari non sostituiscono il controllo visivo o le prove di consegna sveglie/OAuth su Android.
- Il telefono USB locale non è automaticamente raggiungibile in una chat cloud. Non dichiarare prove fisiche non effettuate.
- Per dati sintetici usare `-PsetupProbe`: package separato `com.turni.calendario.setupqa`. Non sostituire mai il database personale con fixture e non cancellare l'app personale per risolvere un problema di firma.

## Sicurezza e pubblicazione

- Mai commettere keystore, token, password, file `.env`, `local.properties`, backup, database personali o documenti estranei al progetto.
- Le variabili `TURNISYNC_KEYSTORE_*` sono segreti esterni. Non mostrarne i valori nei log o nelle descrizioni di commit.
- Un push dei sorgenti non è una release APK. Non creare tag o release senza richiesta.
- Ogni nuova release APK deve incrementare `versionCode` rispetto alle versioni già distribuite e aggiornare `versionName`.
- Eccezione per la pubblicazione su GitHub di un APK identico già distribuito, richiesta dall'utente: preservare versione, byte e firma, usando `scripts/release.ps1 -PublishExistingArtifact` con hash, certificato, metadati e commit sorgenti verificati. Non rinominare una vecchia build come versione nuova.
- Le release GitHub vanno effettuate esclusivamente con `scripts/release.ps1`, caricando il solo APK versionato. Mai usare `--clobber`, `app-release-latest.apk`, archivi ZIP di progetto o OneDrive per la distribuzione.
- Conservare dati, modifiche locali non pertinenti e configurazioni private. Non cambiare la visibilità del repository o dei servizi collegati senza istruzione esplicita.

## Continuità della conversazione e lavoro multiagente

- Questa chat prosegue «Evolvi la UI di Calendario Turni». Leggere `docs/CHAT_CONTINUITY.md` e `.agents/turnisync/STATE.json` prima di riprendere il lavoro; verificare lo stato riportato contro Git e le evidenze disponibili.
- L'utente richiede il metodo multiagente: mantenere un orchestratore principale e attivare i ruoli pertinenti UI/UX Motion, revisione Kotlin/architettura, Build & Diagnostics e DevOps. Contratti e adattamento al runtime: `.agents/turnisync/ORCHESTRATION.md`.
- Conservare le definizioni originali in `.agents/agents/`, skill e regole; leggerle quando pertinenti alla richiesta. La loro presenza su disco non implica che plugin, slash command, hook, modelli o processi storici siano installati o attivi.
- Le note `.agents/teamwork/` sono archivio. Non riattivare stati running, timer, gate o incarichi estranei e non riprendere istruzioni superate dalla guida cloud.
- Registrare decisioni nuove e motivazioni in `HANDOVER_NOTES.md`; aggiornare lo stato sintetico in `.agents/turnisync/STATE.json`. Per modifiche sostanziali aggiornare il verbale pertinente con test effettivi e limiti. Non cambiare le preferenze finali solo perché uno studio storico propone un'alternativa.
