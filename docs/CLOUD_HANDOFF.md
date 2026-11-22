# Riprendere TurniSync da remoto

Per il ritorno alla precedente chat sul PC locale leggere prima [LOCAL_HANDOFF_2026-11-17.md](LOCAL_HANDOFF_2026-11-17.md): contiene le novità del cloud, la sincronizzazione sicura, firme/package e i controlli ancora da fare sul telefono.

L'utente richiede che dopo ogni modifica dell'app venga pubblicato un APK scaricabile su GitHub, attraverso una nuova pre-release versionata. La pagina stabile da condividere è https://github.com/Hellvisback/calendario-turni/releases; le beta sono pre-release e non compaiono necessariamente come `latest`. Specificare se l'APK è il package separato `com.turni.calendario.setupqa`: può essere installato accanto all'app personale, ma richiede la stessa chiave QA per aggiornare una precedente installazione QA. L'aggiornamento dell'app personale richiede la sua chiave originale, esterna al repository. Usare `scripts/release.ps1` e non sovrascrivere release esistenti. La procedura cloud verificata, incluso il fallback GitHub Actions per il servizio upload, è in [RELEASE_QA.md](RELEASE_QA.md).

Aggiornato il 22 novembre 2026. Questo documento è il punto di ingresso del progetto; prevale sulle vecchie intestazioni e sulle indicazioni di percorso nei documenti storici.

## Mini-anteprima rotazioni, loghi Impostazioni animati, PDF Periodo adattivo e Conteggio statistiche intuitivo beta.32

La beta.32 personale (77) ripristina la mini-anteprima pastello nella barra *Modifica turni → Rotazioni* e garantisce la coerenza dell'anteprima in griglia, rende visivamente animate e dinamiche le icone a sinistra in *Impostazioni* (ampiezze aumentate in `SiteIcons.kt` + cascata al rientro e spotlight continuo discreto), allinea l'esportazione PDF *Periodo* al layout editoriale a colonne dell'*Annuale* adattando carta/righe/intestazioni alla lunghezza dell'intervallo e riorganizza *Conteggio statistiche* con nomi generici privi di riferimenti rigidi a «Ferie/FER/RIP» e due sezioni intuitive ad equazioni visive. [Verifica Android e dettagli](VERIFICA_v1.8.0-beta.32.md).

## Account Google unificato, AI e Impostazioni essenziali, animazioni ed Etichette pulite beta.31

La beta.31 personale (76) corregge la riconnessione account Google dopo disconnessione (`GoogleAuthUtil.clearToken` + ritentativo su `401`/`403` + protezione race su logout), rende intera e cliccabile la riga *«Quali dati autorizzo?»*, fonde account Google e controlli Drive/Calendar in una card unica espandibile allo sblocco, semplifica *Importa con AI* in 3 passaggi rimuovendo l'allegato file iniziale, elimina testi e ripetizioni superflui in tutta l'area *Impostazioni* (mantenendo gli swatch palette senza descrizione testuale), anima le icone a sinistra e le relative sottoschermate, sposta lo switch *Sveglie* prima della freccia di dettaglio correggendo l'accensione rapida, rimuove *«Imposta come lavoro attivo»* dall'editor lavoro e il badge *«Predefinita»* sulle rotazioni lasciando la sola stellina dorata, ed elimina i bordi scuri dall'editor *Etichette*. [Verifica Android e dettagli](VERIFICA_v1.8.0-beta.31.md).

## Orario unificato, controlli lavoro facoltativi, wizard AI, PDF editoriali e sheet account Google beta.30

La beta.30 personale (75) unifica straordinari e permessi nella scheda unica *Orario* del dettaglio turno (positivi in verde per straordinario, negativi in ambra per permesso) e disegna la banda permesso (`−1h30`, compatta o affiancata a `TR`) direttamente sotto i turni nelle celle della griglia calendario (`NativeMonthCanvas`), trasforma *Orario e controlli* dell'editor lavoro in un unico gruppo opzionale a tendina senza spunte interne (lasciando vuoto qualsiasi campo non viene conteggiato), centra i badge numerici di *Importa turni con AI* trasformandolo in un wizard guidato a 4 passaggi (`1 Dati` -> `2 AI` -> `3 Risultato` -> `4 Revisione`), rinnova lo stile editoriale pastello dei PDF esportati (`Elenco`, `Mese`, `Anno`, `Periodo`) e introduce uno sheet Grafite/Cobalto per gestione e cambio rapido degli account Google ricordati su Backup e Google Calendar. [Verifica Android e dettagli](VERIFICA_v1.8.0-beta.30.md).

## Dialoghi sicuri, orologio Grafite, turni consecutivi, permessi e Impostazioni sobrie beta.29

La beta.29 personale (74) introduce `TurniSafeBottomDialog` e `TurniTimePickerDialog` in Grafite/Cobalto su tutta l'app (catalogo, sveglie e dettaglio turno), rende a schermo intero gli editor di turni ed etichette, rimuove la duplicazione dello stato attivo nei profili di lavoro e i parametri/maggiorazioni CCNL dal profilo mantenendo solo la comodità di allegare/aprire il PDF CCNL, aggiunge il limite configurabile di **turni lavorativi consecutivi massimi** (`maxConsecutiveWorkDays`, azzerato da riposi, smonti, ferie o giorni vuoti) con avviso sul calendario, aggiunge le **ore di permesso (`− Permesso`)** su ogni turno (aggiornando card giorno, calcolo riposi minimi, PDF, statistiche e backup Drive/JSON), calma la gerarchia cromatica delle Impostazioni su Grafite/Cobalto + 2 accenti e arricchisce le micro-animazioni delle icone. [Verifica Android e dettagli](VERIFICA_v1.8.0-beta.29.md).

## Restyling espressivo pastello beta.28

La beta.28 personale (73) rinnova l'intera estetica grafica di catalogo (`Modelli e turni`), tutti e quattro gli editor di creazione/modifica, `Impostazioni`, `Aspetto e calendario` e le relative sottoschermate allineandoli a `Calendario`, `Statistiche` e `Modifica`: superfici tonali Grafite senza bordi `1.dp`, palette pastello calibrata da `TurniPalette` con riflesso luminoso e contrasto netto (`ExpressivePastel`, `PastelBadge`, `PastelPill`), mini-celle pastello in `SiteCyclePreview`, anteprima mini-calendario live interattiva in `Aspetto e layout`, righe turno a colonna intera e striscia cicli in `Reperibilità`, tessere Corallo in `Festività` e stepper pastello a 3 passi in `Importazione con AI`. [Verifica Android e dettagli](VERIFICA_v1.8.0-beta.28.md).

## Catalogo etichette, reperibilità e UI impostazioni beta.27

La beta.27 personale (72) sposta le Etichette dei giorni nel catalogo `Modelli e turni` come 4° tab (rimuovendole da Impostazioni ma preservando il primo avvio), corregge il flash/click nella barra segmentata animata (`SlidingSegmentBar`), riorganizza la Reperibilità rimuovendo dalla UI la modalità Manuale e la scheda Prossimi 7 giorni (mantenendo compatibilità dati/backup e stepper dei cicli) e uniforma stile Grafite/Cobalto e animazioni in tutte le sottoschermate delle Impostazioni. [Verifica Android e dettagli](VERIFICA_v1.8.0-beta.27.md).

## Primo avvio beta.26

La beta.26 personale (71) riorganizza il primo avvio in quattro passi (`0` Benvenuto + `1/4..4/4`), rende visibili subito tema e formato settimana/orario, unifica lavoro e modelli con chip colorate, aggiunge progressi cliccabili alla prova locale e mostra stati live e legenda icone nel riepilogo finale. [Verifica Android e dettagli](VERIFICA_v1.8.0-beta.26.md).

## Reperibilità e festività beta.25

La beta.25 personale (70) mostra i turni nei prossimi sette giorni, abilita la scelta manuale dalla data e raggruppa le festività per categoria. [Verifica Android e schermate](VERIFICA_v1.8.0-beta.25.md).

## Impostazioni e reperibilità beta.24

La beta.24 personale (69) riordina le impostazioni e aggiorna la configurazione della reperibilità. [Verifica Android e schermate](VERIFICA_v1.8.0-beta.24.md).

## Calendario e statistiche beta.23

La beta.23 personale (68) aggiorna le fasce di trasferta e straordinario e il quadrante delle statistiche. [Prove sul Realme e schermate nei due temi](VERIFICA_v1.8.0-beta.23.md).

## Statistiche e migrazione beta.20

Pubblicata la [beta.20 QA (65)](https://github.com/Hellvisback/calendario-turni/releases/tag/v1.8.0-beta.20), firma QA invariata. La revisione migliora contrasto e profondità del riepilogo, spiega visibilità della provenienza e regole statistiche e rende secondaria la CTA Accedi. I backup JSON nativi sono indipendenti dal package/firma: [MIGRAZIONE_APP.md](MIGRAZIONE_APP.md) descrive export dalla vecchia app e import nella QA senza disinstallare la personale. La chiave originale rimane necessaria per aggiornare direttamente quest'ultima. Suite corrente: 488 test; verifica dettagliata in [VERIFICA_v1.8.0-beta.20.md](VERIFICA_v1.8.0-beta.20.md). Le sezioni seguenti sono verifiche precedenti.

## Revisione onboarding beta.19

Pubblicata la [beta.19 QA (64)](https://github.com/Hellvisback/calendario-turni/releases/tag/v1.8.0-beta.19), firma QA invariata. La revisione richiesta dopo la prova della beta.18 rende statiche le anteprime iniziali, mette Inizia e Accedi alla stessa priorità, chiarisce i dettagli facoltativi del lavoro e concentra la guida sugli intervalli di modifica/aggiunta/cancellazione. Il marker di configurazione completata sopravvive agli aggiornamenti della stessa installazione. Non si trasferiscono automaticamente dati dall'app personale al package QA separato. Verifica corrente: [VERIFICA_v1.8.0-beta.19.md](VERIFICA_v1.8.0-beta.19.md). La suite comprende 486 test. La sezione seguente descrive la prima iterazione, ora superata nei punti modificati dalla revisione.

## Intervento onboarding — 17 novembre 2026

Il lavoro successivo su `main` migliora il primo avvio: due settimane interattive con griglia e toolbar native, sei lezioni facoltative, testi più brevi, frecce coerenti e spaziatura degli editor. La simulazione resta locale e non inserisce turni nel calendario. La suite comprende ora **485 test**. Il verbale aggiornato, le prove native e i limiti sono in [VERIFICA_ONBOARDING_2026-11-17.md](VERIFICA_ONBOARDING_2026-11-17.md); l'helper ripetibile è descritto in [scripts/device_qa/README.md](../scripts/device_qa/README.md).

La beta personale verificata sul telefono resta beta.17 (62). La pre-release beta.18 (63) è destinata alla prova separata (`com.turni.calendario.setupqa`), include questo intervento ed è firmata con la chiave QA del cloud. Le verifiche storiche sotto descrivono la beta personale originale.

## Baseline personale precedente al lavoro cloud

- Repository: `https://github.com/Hellvisback/calendario-turni`, ramo `main`.
- Applicazione: Kotlin + Jetpack Compose, modulo `android-app/app`, package `com.turni.calendario`.
- APK verificato sul telefono originale prima delle revisioni cloud: **1.8.0-beta.17**, `versionCode 62`, non debuggabile, ottimizzato con R8.
- Verifica della beta distribuita: **480 test superati**, lint vitale passato. Le prove grafiche sono state effettuate sul Realme 8 Pro con dati sintetici in un package separato.
- La sincronizzazione iniziale ha portato i sorgenti dalla beta.2 alla beta.17; le revisioni cloud successive arrivano alla beta.20. Non implica una nuova release APK: non incrementare la versione solo per leggere o configurare il checkout.
- Verifica dettagliata: [VERIFICA_v1.8.0-beta.17.md](VERIFICA_v1.8.0-beta.17.md). I percorsi `work/...` citati nei vecchi verbali sono evidenze sul PC originale, non dipendenze del progetto.

## Configurazione Linux / chat cloud

Requisiti: Linux x86_64, JDK 17, `bash`, `curl`, `unzip`, Python 3, Git e accesso HTTPS ai repository di dipendenze. Lo script non richiede privilegi amministrativi per installare l'SDK nella home dell'ambiente.

```bash
git clone https://github.com/Hellvisback/calendario-turni.git
cd calendario-turni
bash scripts/cloud-setup.sh --accept-licenses
```

`--accept-licenses` accetta le [licenze SDK Android](https://developer.android.com/studio#command-line-tools-only). Senza il flag lo script usa un SDK già configurato e non accetta licenze automaticamente. Se manca l'SDK e non vuoi ancora accettarle, interrompe con istruzioni precise prima del download.

Per un ambiente che abbia una fase di setup dedicata, eseguire lì il comando. Per limitarsi all'installazione senza scaldare le cache:

```bash
bash scripts/cloud-setup.sh --accept-licenses --skip-warmup
```

Lo script riutilizza `ANDROID_HOME`/`ANDROID_SDK_ROOT` se disponibili, altrimenti usa `~/.local/share/turnisync/android-sdk`. Installa Platform Tools, piattaforma 36 e Build Tools 35.0.0. Genera `android-app/local.properties` e `.turnisync-cloud.env`, entrambi esclusi da Git. Il secondo è letto automaticamente dallo script di verifica per mantenere Java e SDK coerenti tra sessioni.

Le command-line tools Linux sono fissate alla revisione 15859902, con SHA-256 verificato contro il [download ufficiale Android](https://developer.android.com/studio). Il wrapper Gradle 8.11.1 è incluso; sia il suo JAR sia la distribuzione hanno checksum registrati.

Le dipendenze richiedono, durante il setup, almeno `dl.google.com`, `services.gradle.org`, `downloads.gradle.org`, `github.com`, `release-assets.githubusercontent.com`, `repo.maven.apache.org`, `maven.google.com`, `plugins.gradle.org` e i relativi redirect ufficiali. Un ambiente senza rete e senza cache non può compilare al primo avvio. Rieseguire il setup dopo modifiche alle dipendenze quando la fase operativa non ha accesso alla rete.

## Comandi di sviluppo

```bash
# Verifica completa già usata per le beta:
bash scripts/check-android.sh

# Solo i test:
bash scripts/check-android.sh testDebugUnitTest

# Debug installabile separato, per fixture e prove UI:
bash scripts/check-android.sh assembleDebug -PsetupProbe
```

Il comando predefinito esegue `assembleBeta testDebugUnitTest`, usa il compilatore Kotlin nel processo Gradle e limita l'heap a 3 GiB. Si può impostare `TURNISYNC_GRADLE_HEAP=4g` su un ambiente con memoria adeguata. Gli output sono sotto `android-app/app/build/outputs/`; i test sotto `android-app/app/build/reports/tests/testDebugUnitTest/`.

Su Windows usare `scripts/check-android.ps1 -JavaHome <cartella-jdk17>`. Android SDK deve essere configurato tramite `ANDROID_HOME`, `ANDROID_SDK_ROOT` o il proprio `local.properties`. Il wrapper batch è incluso; non copiare il `local.properties` del PC originale.

Il workflow Android di verifica esegue gli stessi controlli su Linux e conserva i rapporti di test/lint. Il workflow manuale `publish-qa.yml` pubblica invece gli APK QA già verificati tramite lo script di release; non dispone della chiave originale del proprietario.

## Firma: aggiornare l'app già installata

La beta sul telefono è firmata con la chiave di sviluppo originale del proprietario. **Quella chiave non è nel repository.** Android rifiuta l'aggiornamento con una firma diversa, anche se package e codice coincidono. Non disinstallare l'app per aggirare il problema: si perderebbero i dati locali.

Per compilare una beta compatibile da un'altra macchina, trasferire la chiave privatamente e configurare tramite il gestore dei segreti dell'ambiente:

| Variabile | Contenuto |
| --- | --- |
| `TURNISYNC_KEYSTORE_PATH` | Percorso assoluto al file della chiave, esterno al repository |
| `TURNISYNC_KEYSTORE_PASSWORD` | Password dell'archivio |
| `TURNISYNC_KEYSTORE_ALIAS` | Alias della chiave |
| `TURNISYNC_KEYSTORE_KEY_PASSWORD` | Password della chiave |

Se il percorso è presente, la build richiede tutte le altre variabili e un file leggibile, senza stamparne i valori. Senza queste variabili la beta conserva il comportamento precedente: firma debug della macchina corrente. Le build CI servono per validazione, non per aggiornare il telefono personale.

Anche l'accesso Google Android dipende dalla coppia package/impronta del certificato. Per provare Google con una chiave nuova bisogna configurare il relativo client OAuth; avere solo i sorgenti non trasferisce questa autorizzazione.

## Decisioni UI da non perdere

- Grafite per superfici e sfondi; cobalto per azioni/selezioni. Dock con base grigia e indicatore blu, **solo icone**, animazioni conservate. Fonte dei colori: `ui/theme/TurniPalette.kt`.
- Weekend differenziati fino alle intestazioni S/D; giorno odierno distinto dalla selezione; festività con contorno sul numero, senza numero rosso forzato.
- Turni nella griglia con solo orario di inizio e altezza fissa. Quando non entrano: pallini centrati nei colori dei turni nascosti. Tutti i dati rimangono nel dettaglio.
- Pressione prolungata in Cambia e Più turni per un intervallo; anche la gomma ha la selezione intervallo. Reperibilità e gomma indipendenti dal modo attivo.
- Onde dal punto di pressione e riassorbimento nel riquadro all'uscita. Cambiare strumento non deve ricreare la griglia o riapplicare un vecchio evento.
- Contorni interni sottili. La gomma non ha più una grande fascia rossa: piccolo indicatore della data e frecce sottili, con spazio per note e campanella.
- Note/etichette gestite nello stesso flusso. Nel calendario la nota ha precedenza sull'etichetta; il pulsante per aprire l'editor rimane lo stesso.
- Rotella del turno celeste se c'è straordinario; puntino blu solo se c'è una nota. Trasferta con stato selezionato distinguibile nei due temi.
- Statistiche basate sui modelli dell'utente, non su nomi prefissati. Etichette e dettagli inline, senza pop-up inutili.

## Dati e architettura

- Repository e persistenza: `repository/ShiftRepository.kt`, `DatabaseSnapshot.kt`, `LocalDatabaseStore.kt`; modelli in `model/`.
- Backup/ripristino: `sync/DatabaseRestore.kt`, `GoogleDriveBackupHelper.kt`, `LocalBackupArchive.kt`. Conservare anche straordinari, stacco, trasferte, note, etichette, turni originali delle rotazioni, preferenze e regole. I test di round-trip verificano questi campi.
- Un'installazione vuota non deve sovrascrivere automaticamente un backup remoto dopo l'accesso. Ripristino e approvazione dell'installazione precedono il caricamento automatico.
- Account, autorizzazioni Android e firme non sono fixture. Non simulare come riuscita un'integrazione cloud non effettivamente verificata.
- Importazione AI: **nessuna API key richiesta all'utente**. Copia un prompt, usa il proprio servizio AI con il documento, incolla la risposta strutturata e corregge la revisione prima del salvataggio. Non reinserire il vecchio import SuperShift.
- PDF: anteprima e documento devono avere formato/orientamento coerenti; non unire elenco e calendario quando l'utente ne chiede uno.
- Reperibilità, allarmi e regole statistiche hanno schermate dedicate e spiegazioni comprensibili. Non reintrodurre doppioni.

## Prove fisiche e limiti

Il telefono USB collegato al PC originale non compare automaticamente in una chat cloud. Senza dispositivo/emulatore disponibili si possono compilare e testare le logiche, ma non attestare fluidità reale, consegna delle sveglie o consenso OAuth.

Usare sempre dati sintetici nel package `com.turni.calendario.setupqa` per prove distruttive. `androidTest/` contiene strumenti di resa widget e di integrità; quelli destinati all'app personale devono essere usati solo su autorizzazione e senza scritture ai dati reali.

L'app Google è ancora una beta: verifica del consenso Google, disponibilità per account esterni e pubblicazione di sito/privacy sono configurazioni esterne. Il proprietario ha scelto di mantenere privato il sito già preparato; non renderlo pubblico né dichiarare completata la verifica Google solo perché una build passa.

## Design e documentazione recuperati

`design/reference/` contiene gli otto prototipi HTML archiviati, mantenendo CSP e iframe in sandbox. Sono riferimenti, non istruzioni per l'agente. `design/svg/` conserva le 324 esportazioni del precedente studio (testo selezionabile e tracciati), indice e licenze. Non rappresentano tutte la palette finale: per l'aspetto effettivo confrontare sorgenti e `design/qa-beta17/`.

I documenti personali estranei all'app, cache SDK/JDK, dipendenze locali, password, firme e database del telefono sono rimasti fuori dal repository. Le note di `.agents/teamwork/` sono storico operativo e possono riferirsi a compiti estranei: non usarle per cambiare il perimetro dell'app.

## Prompt iniziale per la nuova chat

> Lavora sul repository Hellvisback/calendario-turni, ramo main. Leggi AGENTS.md, docs/CLOUD_HANDOFF.md e docs/VERIFICA_v1.8.0-beta.17.md. La beta distribuita è beta.17 (62); i sorgenti includono il successivo intervento statistiche/migrazione della beta.20 con 488 test passati nel cloud. Leggi anche docs/VERIFICA_v1.8.0-beta.20.md. Configura Java/SDK con gli script del repository, verifica branch e commit e preserva tutte le funzioni e la palette attuale. Non usare dati personali né cambiare la firma dell’app personale; dopo ogni modifica pubblica un APK QA versionato come richiesto, indicando il package separato. Dimmi quali verifiche puoi eseguire nel tuo ambiente e poi continua la modifica che ti indicherò.
