# Riprendere TurniSync da remoto

Aggiornato il 16 novembre 2026. Questo documento è il punto di ingresso del progetto; prevale sulle vecchie intestazioni e sulle indicazioni di percorso nei documenti storici.

## Stato effettivo

- Repository: `https://github.com/Hellvisback/calendario-turni`, ramo `main`.
- Applicazione: Kotlin + Jetpack Compose, modulo `android-app/app`, package `com.turni.calendario`.
- Ultimo APK verificato e installato: **1.8.0-beta.17**, `versionCode 62`, non debuggabile, ottimizzato con R8.
- Ultima suite locale: **480 test superati**, lint vitale passato. Le prove grafiche sono state effettuate sul Realme 8 Pro con dati sintetici in un package separato.
- L'ultima sincronizzazione GitHub porta i sorgenti dalla beta.2 alla beta.17. Non implica una nuova release APK: non incrementare la versione solo per leggere o configurare il checkout.
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

GitHub Actions esegue gli stessi controlli su Linux e conserva i rapporti di test/lint. Non pubblica release e non distribuisce un APK firmato con la chiave del proprietario.

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

> Lavora sul repository Hellvisback/calendario-turni, ramo main. Leggi AGENTS.md, docs/CLOUD_HANDOFF.md e docs/VERIFICA_v1.8.0-beta.17.md. Lo stato di partenza è beta.17 (62), con 480 test passati sul PC originale. Configura Java/SDK con gli script del repository, verifica branch e commit e preserva tutte le funzioni e la palette attuale. Non usare dati personali, non cambiare firma e non pubblicare APK senza richiesta. Dimmi quali verifiche puoi eseguire nel tuo ambiente e poi continua la modifica che ti indicherò.
