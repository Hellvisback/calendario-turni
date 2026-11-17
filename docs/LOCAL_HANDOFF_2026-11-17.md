# Rientro dal cloud alla chat locale — 17 novembre 2026

Questo documento aggiunge alla precedente conversazione locale ciò che è stato realizzato nel cloud. Non occorre ricreare la vecchia chat o ripristinare un vecchio ZIP: il codice aggiornato e le evidenze condivisibili sono su GitHub. Il contesto storico locale rimane utile, ma le decisioni finali descritte qui prevalgono sulle iterazioni precedenti poi corrette dall'utente.

## Punto di ripartenza

- Repository: https://github.com/Hellvisback/calendario-turni, ramo richiesto `main`.
- Stato prima di questo handoff documentale: `5d0c0c773145525526a3c0494b54835626a00c87`, checkout pulito e sincronizzato con `origin/main`.
- Ultima versione app: `1.8.0-beta.20`, `versionCode 65`.
- Ultima release cloud: [beta.20 QA](https://github.com/Hellvisback/calendario-turni/releases/tag/v1.8.0-beta.20), singolo asset `app-release-v1.8.0-beta.20.apk`.
- Il tag della release punta a `8ff15befe59cf02ce3bebb9723e7279f48391473`; il codice app è quello del commit di implementazione `7dc4bae398db082a9e9ff79d6ebb615556a59c7f`. I commit successivi aggiungono soprattutto note, workflow ed evidenze. Non riportare i sorgenti al vecchio snapshot beta.17.
- SHA-256 APK beta.20: `bd316aef300ff09f6d1ed322cb7920975c68db4f1b2b8ae1b94e104fbf7611c3`, verificato riscaricando l'allegato GitHub.

## Prima operazione sul PC locale: sincronizzare senza perdere lavoro

1. Individuare il checkout usato dalla vecchia chat. Leggere `git status`, branch, commit, remote e worktree; conservare modifiche locali, file non tracciati e configurazioni private. Non usare `reset --hard`, `clean`, copie integrali sovrapposte o disinstallazioni per riallineare il progetto.
2. Eseguire `git fetch origin`, confrontare `main` locale con `origin/main`, ispezionare eventuali commit/modifiche locali. Se non ci sono divergenze o modifiche da integrare, aggiornare con fast-forward. In caso contrario preservare e integrare il lavoro locale con una strategia non distruttiva; non trattare i sorgenti cloud come motivo per scartarlo.
3. Leggere l'`AGENTS.md` aggiornato, questo documento, `docs/CLOUD_HANDOFF.md` e i verbali beta.19/beta.20. I percorsi `/workspace/...` indicano la macchina cloud, non dipendenze da ricreare sul PC.
4. Riutilizzare JDK/SDK, `local.properties`, variabili e chiavi già corretti sul PC. Non copiarvi la `.turnisync-cloud.env` del cloud. Verificare toolchain e firma prima di compilare un aggiornamento da installare sul telefono personale.

Questo passaggio non richiede un nuovo incremento di versione o una nuova release: è solo il rientro in locale. Le nuove modifiche all'app richiederanno invece un nuovo APK, come richiesto dall'utente.

## Decisioni finali UI/UX da mantenere

### Primo avvio e guida

- Welcome e scelta tema mostrano **due settimane statiche**, con la griglia nativa: niente toolbar, date interattive, messaggi di prova o pulsanti Ripristina in queste anteprime.
- Welcome: **Inizia** primario cobalto; **Hai già un account? Accedi** della stessa dimensione ma secondario grigio. La precedente soluzione con due pulsanti entrambi primari è superata.
- Accedi apre la pagina Google/recupero esistente, con `restoreOnly`; collegare l'account non deve avviare un backup vuoto né sovrascrivere una copia remota. Le altre copie locali/file restano accessibili.
- Il collegamento **Aggiungi dettagli al lavoro** è facoltativo e indica il nome del lavoro corrente. L'editor lavoro è a tutta altezza, senza la precedente fascia vuota superiore; rimangono il margine di 12 dp sotto l'header e Salva sopra la tastiera.
- **Prova le funzioni di modifica** insegna modifica singola, pressione prolungata per intervalli anche inversi, Più turni, gomma su intervalli, rotazioni e annullamento. Usa canvas e toolbar reali, ma stato locale isolato.
- La X chiude gli strumenti; resta Riapri la matita. Sono stati rimossi il dettaglio del giorno inventato e gli inviti a toccare una data dopo la X. Non reintrodurli.
- Spiegazioni e feedback sono esterni alla griglia, in un'area stabile sotto la toolbar. La disposizione definitiva consente di vedere insieme date, strumenti, istruzione e avanzamento dopo lo scorrimento.
- Frecce coerenti e animate: non trasformarle nella valigetta. Aggiunti i tracciati mancanti di `chevron-up`, `arrow-right` e `cloud`.

### Configurazione già completata

`FirstRunStore` mantiene `complete → READY` anche quando il database non viene rilevato all'inizializzazione. In quel solo caso sospende `autoBackupDaily` sullo snapshot iniziale, senza azzerare tutte le preferenze. Una configurazione `pending` continua invece dal proprio passo, anche se l'autosalvataggio ha già creato il database: non saltare il primo avvio di un vero nuovo utente solo perché esiste un file.

Questo preserva gli aggiornamenti della **stessa installazione**. Non trasferisce automaticamente i dati fra due package Android distinti.

### Statistiche e provenienza

- Forme del riepilogo conservate; contrasto/pigmenti più presenti, bordi netti, lieve profondità e gradienti sobri. Animazioni finite e poco invadenti. Conteggi basati su tutti i modelli dell'utente, non su nomi Mattina/Pomeriggio prefissati.
- Con caratteri grandi, card e legenda passano a una colonna. La resa al 200% della beta.20 non è stata verificata nativamente.
- Guida rapida: approfondimento espandibile **Quando sostituisci un turno**, interruttore reale della provenienza e collegamento alla vera schermata delle regole, con ritorno alla guida.
- `showExShifts` cambia soltanto la visualizzazione. Nascondere l'ex turno non lo cancella e non cambia le statistiche.
- Senza una regola esplicita applicabile conta il turno visibile. Una regola abilitata può contare l'originale della rotazione al suo posto; le eventuali esclusioni dei festivi vengono applicate successivamente al modello risultante. La gomma elimina anche la provenienza dell'assegnazione cancellata.
- Esempio didattico: Mattina sostituita con Ferie conta normalmente Ferie; una regola Ferie/Mattina può far contare Mattina se la provenienza è registrata. Nessuna regola automatica è stata aggiunta.

Restano i vincoli della precedente chat: palette Grafite/Cobalto, dock con sole icone, turni a dimensione fissa e pallini di overflow, onde senza replay al cambio strumento, dati completi e nessuna API key richiesta all'utente per l'importazione AI.

## Due app e firme: punto essenziale per tornare in locale

| Installazione | Package | Chiave richiesta per aggiornarla |
| --- | --- | --- |
| App personale originale | `com.turni.calendario` | La chiave originale del PC/proprietario |
| APK QA pubblicati dal cloud | `com.turni.calendario.setupqa` | La chiave QA usata nel cloud |

Le beta.18 (63), beta.19 (64) e beta.20 (65) del cloud sono QA separate. Non hanno aggiornato l'app personale originale. Le due app non condividono database, preferenze, accesso Google o copie private.

Certificati di riferimento, dati pubblici e non chiavi private:

- Originale beta.17/beta.2: SHA-256 `cf09967400ac4931dbfa169b9bc23c6a8314ef0256285c3399f4912bee735772`. Provenienza: APK originale della beta.17 e verbale della sua ripubblicazione, conservato nella storia Git `14eaece:docs/RELEASE_v1.8.0-beta.17.md`.
- QA cloud beta.18–20: SHA-256 `5b3da847037ec649a4c90b62af605ff234604eeb75cd15250ad8955ace599b9f`.

Verificare il certificato dell'APK e quello dell'installazione da aggiornare prima di `adb install -r`. Non assumere che una chiave debug appena generata sul PC coincida con una delle precedenti; non sovrascrivere o rigenerare la chiave originale. Non disinstallare o cancellare i dati dell'app personale per aggirare un errore di firma.

I sorgenti scelgono `.setupqa` con `-PsetupProbe`, altrimenti usano il package personale. La beta usa `TURNISYNC_KEYSTORE_*` se configurate; altrimenti la chiave debug della macchina. Sul PC originale potrebbe già esserci la chiave personale corretta: verificarla, non sostituirla con quella QA. Per continuare ad aggiornare invece la QA cloud dal PC serve la stessa chiave QA, da trasferire/conservare privatamente; non è nel repository. La copia macchina cloud è in `/workspace/tools/android-user/debug.keystore`, non è stata pubblicata e non viene trasferita da un clone Git.

La configurazione Google Android dipende da package/certificato. Non dichiarare OAuth funzionante su una nuova combinazione senza provarlo/configurarlo. La sola presenza del codice o del client web non trasferisce l'autorizzazione della console Google.

## Backup e dati personali

Il parser non è stato riscritto: non è emerso un bug concreto nel formato senza ricevere il backup reale o il suo errore. I JSON nativi v1–v5 non sono legati a package/firma; sono stati aggiunti test v4 e legacy v1. Il selettore accetta anche provider che indicano il JSON come `application/octet-stream` e conserva validazione/anteprima.

La procedura è [MIGRAZIONE_APP.md](MIGRAZIONE_APP.md): Esporta file dalla vecchia app → Importa file nella nuova → verifica anteprima → conferma. Non serve Google. Il ripristino sostituisce i dati della destinazione; conservare la vecchia installazione e il file durante i controlli. I PDF CCNL sono riferimenti URI, non contenuti nel JSON, e possono richiedere nuova selezione. Non confondere ZIP di progetto o sorgenti con backup del calendario. Se entrambe le app restano installate, usare una sola installazione per sveglie/sincronizzazioni attive.

Il file personale dell'utente **non è stato fornito né ripristinato nel cloud**. La compatibilità automatica dei formati nativi non certifica un file specifico incompleto, di un'altra app o in un formato diverso.

## Verifiche concluse e limiti

- **488 test** superati, zero fallimenti/errori/saltati; `assembleDebug assembleBeta testDebugUnitTest -PsetupProbe`, lint vitale e R8 riusciti. Ultima build finale cloud: 6m 19s.
- Aggiornamenti QA63→64 e QA64→65 senza cancellazione dati; stato completato preservato. Nella beta.19 confrontato anche l'intero database normalizzato prima/dopo.
- Anteprime statiche, pagina account senza avvio OAuth, temi chiaro/scuro, editor a tutta altezza e tastiera, aggiunta/gomma a ritroso, chiusura e ripristino della prova verificati su emulatore API26 con dati sintetici.
- Statistiche con sei modelli personalizzati: 38,5 ore, 6 giorni, 8 turni; Guardia 3/Ferie 1 secondo la regola. Chiaro/scuro e toggle provenienza mantengono conteggi e hash calendario.
- Non sono state rifatte prove sul telefono fisico, misure FPS, consegna sveglie o login/consenso Google. Emulazione software senza KVM, con lentezza e occasionali problemi System UI all'avvio: non attribuirli automaticamente all'app né usare quei tempi per stimare la fluidità sul telefono.
- Alla beta.19, font200 mostrava troncamenti delle sigle nelle chip fisse della toolbar nativa; limite documentato. La beta.20 adatta le card statistiche a una colonna, senza una nuova prova nativa font200.

Evidenze portabili: `design/qa-onboarding-2026-11-17/`, `design/qa-beta19/`, `design/qa-beta20/`; verbali `docs/VERIFICA_ONBOARDING_2026-11-17.md`, `docs/VERIFICA_v1.8.0-beta.19.md`, `docs/VERIFICA_v1.8.0-beta.20.md`. Le prime immagini dell'onboarding sono storiche: confrontare la revisione finale prima di ripristinare una vecchia scelta grafica.

I log completi, fixture e backup QA, database personali, cache, chiavi e storici privati rimangono fuori dal repository. Non servono per ricostruire i sorgenti. I log `/workspace/turnisync-setup/*` non saranno automaticamente presenti sul PC. I tool/ID di agenti, emulatori, plugin e sessioni del cloud non sono sessioni attive nella chat locale.

## File principali modificati nel cloud

- `ui/screens/FirstRunScreen.kt`, `CalendarPractice.kt`, nuovo `OnboardingPracticeState.kt`: onboarding, guida, preview isolata, accesso e provenienza/statistiche.
- `repository/FirstRunStore.kt`: policy completamento/ripresa e protezione backup quando mancano i dati.
- `ui/screens/NativeMonthCanvas.kt`: input preview opzionale, senza sostituire il repository; default del calendario reale conservati.
- `ui/screens/NativeCalendarPanels.kt`, `CatalogEditorComponents.kt`, `ui/components/SiteIcons.kt`: toolbar, editor, tastiera e icone.
- `ui/screens/CloudBackupScreen.kt`, `ReportScreen.kt`: recupero/migrazione e resa statistiche. Il calcolo delle statistiche e il parser del backup non sono stati modificati in quest'ultimo intervento.
- Test: nuovo `OnboardingPracticeTest.kt`, estesi `FirstRunPolicyTest.kt` e `CompleteBackupRoundTripTest.kt`.
- `scripts/device_qa/onboarding_probe.py`: helper vincolato a `.setupqa`, conteggi/hash senza esportare note; rimuove il vecchio XML prima di ogni dump per non usare una gerarchia obsoleta dopo timeout UIAutomator.
- `scripts/release.ps1`, `.github/workflows/publish-qa.yml`, `docs/RELEASE_QA.md`: pubblicazione QA verificata e ripetibile.

I percorsi Kotlin sopra sono relativi a `android-app/app/src/main/java/com/turni/calendario/`; quelli dei test a `android-app/app/src/test/java/com/turni/calendario/`.

## Build e prossime release da locale

JDK17, SDK compile/target36, min26, Build Tools35.0.0, wrapper Gradle8.11.1. Su Windows dalla radice: `scripts/check-android.ps1 -JavaHome <JDK17>`. Lo script esegue di default `assembleBeta testDebugUnitTest`; verificare separatamente il target QA quando si usano fixture. Le ultime prove cloud usavano esplicitamente `-PsetupProbe`, non il package personale.

L'utente richiede agenti pertinenti quando gli strumenti locali lo permettono, con coordinamento e risultati raccolti. Dopo ogni **modifica dell'app** vuole un APK scaricabile su GitHub, non solo un push dei sorgenti o un link a un file locale della chat.

Usare `scripts/release.ps1`, asset solo APK versionato, versione/nome incrementati rispetto alle release distribuite, nessun `--clobber`, ZIP o `app-release-latest.apk`. La prossima versione dopo una nuova modifica deve superare codice65 (normalmente beta.21/codice66, verificando prima GitHub). Non riutilizzare il tag beta.20 per un APK personale diverso da quello QA già pubblicato.

`docs/RELEASE_QA.md` descrive `-PublishExistingArtifact` (attualmente limitato al package QA) con hash/certificato/versione/commit e `-VerifyOnly`, e il fallback Actions per upload401. Il workflow manuale usa solo `contents: write` e chiama lo stesso script. Se cambia il workflow, pin del commit/tag e codice dell'APK devono restare coerenti: non aggirare controlli o sovrascrivere tag. Non usare alla cieca `-SkipBuild`: può rinominare un vecchio file con una nuova versione se non si valida il binario; il ramo normale dello script è ancora una procedura Windows. Per pubblicare un futuro APK personale verificare/adattare esplicitamente package, firma e procedura, non mascherarlo da QA.

Pagina stabile per l'utente: https://github.com/Hellvisback/calendario-turni/releases (le beta sono pre-release; `/releases/latest` può indicare una vecchia stabile).

## Ripresa pratica, senza nuovo sviluppo non richiesto

Dopo sincronizzazione, riportare branch/commit, modifiche locali conservate, toolchain e quale firma/package si può compilare sul PC. Recuperare la chiave originale già nota alla vecchia chat se disponibile localmente, senza pubblicarla o mostrarne le password. Verificare sulla macchina e sul telefono locale ciò che nel cloud non era accessibile. Non rifare le scelte UX superate o cambiare altre funzioni in attesa della prossima richiesta dell'utente.
