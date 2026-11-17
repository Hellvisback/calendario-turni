# Verifica onboarding — 17 novembre 2026

Intervento sui sorgenti della beta.17 (62), a partire da `main` al commit `f5d7449edbaebd56f3fc1c2709c2c51b4409ef58`. Non costituisce una nuova release APK. Questo verbale registra controlli automatici, prove native e limiti effettivi.

## Comportamento implementato

- Introduzione più sintetica, con azione principale ferma in fondo e ripristino separato.
- Anteprima di due settimane con assegnazioni sintetiche, dati del lavoro attivo oppure modelli di esempio locali. La griglia usa `NativeMonthGrid` e la barra usa `NativeEditToolbar`, condivisi con il calendario effettivo.
- Prova guidata in sei passaggi: sostituzione, intervalli, turni aggiuntivi, rotazione, reperibilità/gomma e dettagli locali. Il calendario di prova non scrive le assegnazioni o le note nel database dell'utente.
- Anteprima compatta nei passaggi iniziali; area più alta nella guida ai gesti. Intestazioni del fine settimana con lo stesso fondo delle colonne sottostanti.
- Dettagli facoltativi dell'ultimo passaggio espandibili con freccia animata; eliminato il fallback a valigetta per `chevron-up` e `arrow-right`.
- Margine condiviso di 12 dp fra intestazione dell'editor e primo contenuto, inclusa Modifica lavoro.
- Contatore iniziale compatto e titolo della toolbar su una sola riga con ellissi, per evitare collisioni con caratteri più grandi.
- Annulla e Ricomincia azzerano anche la sessione grafica della prova. La gomma a intervallo funziona anche dalla modalità Rotazioni; l'onda di conferma usa il modello effettivo dell'ultimo giorno della sequenza.
- Feedback locale in overlay sul calendario, senza spostare toolbar o lezioni. Entrata/uscita con molla; durata base 4,2 secondi adattata al timeout consigliato dall'accessibilità; annuncio `Polite` e nessun gestore di input sul messaggio.

Il flusso di ripristino e il salvataggio locale prima di avanzare restano quelli esistenti. Nessun account, backup automatico o turno reale viene creato dalla simulazione.

## Verifiche automatiche finali

- `bash scripts/check-android.sh`: **BUILD SUCCESSFUL in 8m 25s**, `assembleBeta`, R8 e `lintVitalBeta` riusciti; **485 test, zero fallimenti, errori o test saltati**. Log locale `qa-artifacts/onboarding-2026-11-17/production-final-build.log`.
- `bash scripts/check-android.sh assembleDebug testDebugUnitTest -PsetupProbe`: **BUILD SUCCESSFUL in 2m 47s**, stessa suite da 485 test. Log locale `qa-final-build.log`.
- `git diff --check`, compilazione Python dell'helper e revisione indipendente dei default del renderer: riusciti. Nessuna modifica a repository dati, serializzazione, versione o firma.
- Helper: verificati anche timeout UIAutomator con uscita 0 e vecchio XML presente, rifiutato senza salvare artefatti; nuovo dump valido accettato. Il riavvio dell'ambiente ha reso necessaria questa protezione contro gerarchie obsolete.

I cinque nuovi test coprono settimane complete lunedì/domenica oltre il cambio anno, esempio iniziale senza alterare il catalogo, intervalli a ritroso, conservazione di note e giorni esterni, aggiunta/cancellazione, ripetizione della rotazione e snapshot immutabili.

APK QA installato senza cancellare i dati sintetici, SHA-256 `5b0cbe77aed6d973d7c3c28c572d5fc6e02d4541f786d7219fa75a369675f2dd`. È un debug con package separato, non un aggiornamento firmato per l'app personale.

## Prove native e immagini già esaminate

Le acquisizioni sono state effettuate nel package separato **`com.turni.calendario.setupqa`** su emulatore, non sul telefono personale.

| Evidenza locale | Risultato osservato |
| --- | --- |
| `initial-calendar.json` | Primo avvio al passo 0, stato pending; zero assegnazioni, note giornaliere, modelli e rotazioni; un profilo lavoro. |
| `theme-light.png`, `theme-dark-preview.png` | Temi chiaro e scuro, due settimane e toolbar reale visibili; colori e indicatori leggibili nel campione. |
| `work-editor.png` | Primo riepilogo del lavoro separato dall'header da 12 dp; footer Salva leggibile a tastiera chiusa. |
| `work-editor-keyboard.png` → `final-work-keyboard.png` | Problema riprodotto su API 26 e corretto con `adjustResize`: Salva lavoro ora resta interamente sopra la tastiera. `final-work-renamed.png` e `final-work-saved.xml` confermano salvataggio del nome sintetico `Il mio lavoro_QA` e ritorno al passo 2. |
| `resumed-work-step.xml`, `resumed-work-state.json` | Ripresa del passo 2 dopo riapertura; tema scuro conservato. Assegnazioni e note giornaliere ancora vuote, con gli stessi hash del primo avvio. |
| `final-practice-tools-visible.png`, `final-feedback.png` | Due settimane, toolbar reale con titolo su una riga, bande weekend continue e messaggio in overlay; posizione della toolbar invariata con il messaggio visibile. |
| `final-build-resumed.png` | Ripresa al passo 5 dopo aggiornamento del package QA, senza ricominciare la configurazione. |
| `final-options-expanded.png`, `final-reminders-expanded.png`, `optional-holidays.png` | Gruppi aperti con la stessa freccia verso l’alto, nessuna valigetta; apertura e ritorno dalla schermata Festività. |
| `practice-reverse-range.png`, `practice-overflow.png`, `practice-rotation.png`, `practice-eraser-rotation.png`, `practice-details.png` | Sostituzione a ritroso di quattro giorni, eccedenze con pallini, sequenza, cancellazione a ritroso anche in Rotazioni, annullamento, reperibilità e dettagli locali. |
| `practice-state.json`, `practice-details-state.json` | Dopo prove e dettagli, zero assegnazioni e dati giornalieri reali; hash del calendario identico al primo avvio. Cinque modelli creati solo con la scelta esplicita dell’utente. |

Le schermate definitive sono conservate anche in [design/qa-onboarding-2026-11-17](../design/qa-onboarding-2026-11-17/README.md), disponibili dopo un nuovo clone. Log, dump e acquisizioni intermedie sono sotto `qa-artifacts/onboarding-2026-11-17/`, esclusi da Git. Le impronte prodotte dall'helper confrontano assegnazioni e dati giornalieri; non equivalgono a un confronto completo di ogni impostazione o file dell'app.

## Conclusione del percorso e accessibilità

- `final-welcome-light.png`: a 360 dp, due settimane intere e toolbar con tutti i modelli visibili sopra l'azione principale.
- `final-welcome-320dp-font200.png`: testo al 200% e larghezza 320 dp; azioni inferiori leggibili senza sovrapposizioni, contenuto lungo scorrevole. Densità e font ripristinati al termine.
- `final-real-calendar.png`, `final-reopened-calendar.png`, `final-calendar-state.json`: completamento dei cinque passaggi, arresto e riapertura sul calendario; stato `complete`, cinque modelli scelti esplicitamente, zero assegnazioni/dati giornalieri. Hash calendario identico al primo avvio: `445d1b6ff26370fc7a96e33bea40b0eba5ff49eea86420b56e8d2ff82b9a417c`.
- `settings-guide-link.png`, `quick-guide-start.png`, `quick-guide-controls.png`: accesso alla Guida rapida dalle Impostazioni, stessa griglia e toolbar nel tema chiaro.
- Dopo il riavvio dell’ambiente, cinque pressioni Prossimo gesto hanno portato da 1/6 a 6/6 con dump nuovi verificati a ogni passaggio (`residual-guide-lesson1.xml` fino a `residual-guide-lesson6.xml`). Il giorno 16 è stato cambiato da Mattina a Pomeriggio; Ricomincia ha ripristinato Mattina (`residual-guide-modified.xml`, `residual-guide-reset.xml` e PNG). Anche `residual-guide-calendar-state.json` conferma hash invariato, zero assegnazioni/dati giornalieri e primo avvio già completo.

Il controllo di timeout accessibile e assenza di gestori di input sull'overlay è anche statico; non costituisce una prova di ogni configurazione TalkBack. Le prove di esempio sono state svolte esclusivamente nel package separato con dati sintetici.

Nessuna misura FPS, consegna di sveglie, login OAuth o prova di aggiornamento con la firma del telefono viene attestata da queste verifiche. Il rendering software senza KVM non permette di certificare la fluidità su un telefono fisico; la chiave di firma originale resta esterna al repository.
