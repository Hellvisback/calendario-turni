# TurniSync 1.8.0-beta.2 (47) — 28 ottobre 2026

## Correzioni
- Calendario/Statistiche: posizione d’ingresso e contatore preparati nella stessa composizione che rende visibile la destinazione. Rimossa la sequenza «grafico completo → reset a zero nell’effetto successivo», che poteva produrre il lampo segnalato. La pagina nascosta rimane fuori dal disegno e dagli hit target; niente dissolvenza simultanea di due schermi interi.
- Cambi di periodo: risultati asincroni con stato iniziale proprio, evitando di mostrare temporaneamente i totali precedenti.
- Importazione AI: pulsante Conferma nome, stato Nome confermato con spunta, salvataggio e normalizzazione degli spazi. Pulsante e azione Fine della tastiera chiudono tastiera/focus e mostrano il feedback animato. Modificando il nome torna la richiesta di conferma. Rimane il percorso con AI esterna, senza chiavi o invii automatici del documento.
- Accessibilità: i contatori su Canvas espongono adesso il valore finale. Il testo invisibile usato per misurare lo spazio non era sufficiente: Android lo escludeva dall’albero accessibile.

## Verifiche finali
- assembleBeta riuscita: R8, risorse ridotte, non debuggable, stessa firma della serie installata. Aggiornamento install -r, senza disinstallare l’app.
- Test JVM Debug: 432; Release: 432; zero fallimenti/errori. Lint Beta: zero errori, 135 avvisi, non soppressi.
- Realme 8 Pro / Android13: campionamento dei fotogrammi in entrambe le direzioni tramite sonda Java indipendente dalle classi R8. Nei campioni, dopo la comparsa della nuova destinazione non ricompare la precedente; il grafico entra progressivamente senza mostrare prima il risultato completo. Il campionamento è circa 80–130 ms, non una registrazione di ogni frame.
- Controllati Mese, Anno, Personalizzato, selettore data, ritorno a Mese, scorrimento statistiche e ritorno Calendario. L’animazione condivisa non viene riavviata dall’ingresso/uscita degli elementi dal viewport.
- Conferma nome provata con tastiera visibile: input_method passa da mInputShown=true a false; rimangono nome e pulsante Nome confermato. Provati anche modifica successiva e tasto Fine. Nome di esempio rimosso, campo inizialmente vuoto ripristinato e riverificato dopo l’ultima installazione.
- Dopo l’ultima installazione, i valori 167,2 / 22 / 31 e i contatori delle sagome sono presenti nell’albero di accessibilità.
- Nessun nuovo crash nel buffer Android dall’inizio della sessione delle 18:30. Le vecchie prove diagnostiche nel buffer non appartengono a questa build.
- Database normalizzato, esclusa exportDate: SHA256 prima/dopo identico, `0a53a478ed11eacdf95e03e83d0fc64d06faa19e2d9e33429f7450ab72e54289`. Nessuna modifica a turni, note, rotazioni o configurazione delle sveglie.

## Prestazioni e limiti
Dieci passaggi ripetuti, dopo riscaldamento delle pagine, con gfxinfo sullo stesso telefono:

| Build | Frame | Janky Android | p50 | p95 | p99 |
| --- | ---: | ---: | ---: | ---: | ---: |
| beta.1, campione di questa sessione | 523 | 58,89% | 30 ms | 46 ms | 81 ms |
| beta.2 | 525 | 39,24% | 26 ms | 42 ms | 121 ms |

Il confronto non mostra un miglioramento uniforme: il percentile 99 peggiora, pur migliorando mediana e p95. Rimangono frame fuori budget. Il glitch di stato e la fluidità generale sono problemi distinti: non dichiarare zero lag o assenza universale di bug. La modifica successiva aggiunge soltanto semantica ai contatori e non cambia disegno/clock.

Prove fisiche limitate al Realme. Nessuna nuova operazione remota Google o prova con altri account. Restano validi i limiti della beta.1, inclusi allegati CCNL esterni non incorporati nei backup. Nessuna pubblicazione GitHub/store.

## Artefatti
- APK: TurniSync-v1.8.0-beta.2.apk — 3.257.926 byte.
- SHA256: `b8ba3c3c4e73319937db8bbda2dc54fe95ca588605a6645347f4b517b60ec4c9`, verificato anche sul base.apk installato.
- Evidenze: `C:/Users/marco/Documents/Codex/2026-10-19/ciao-sei-il-master-orchestrator-dell/work/v180b2-qa`.
- Simboli nella cartella della task: `outputs/beta-symbols-v1.8.0-beta.2/mapping.txt`.
- La sonda fotogrammi/database è solo nell’APK androidTest, non nell’app consegnata.
