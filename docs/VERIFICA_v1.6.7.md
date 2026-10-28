# Verifica v1.6.7 — backup protetti, rotazioni e attesa Calendar

27 ottobre 2026. APK 1.6.7 / versionCode 39 installato sul Realme 8 Pro con aggiornamento conservativo `install -r`. Nessuna disinstallazione, nessuna pubblicazione GitHub.

## Problema confermato e correzione Drive

Il collegamento Google in `CloudBackupScreen` invocava `saveRemote(token,true)`, attivando il salvataggio automatico e inviando subito il database locale. `GoogleDriveRemote.upload` aggiornava tramite PATCH l’ultimo file esistente. Questo poteva sostituire una copia valida con i dati iniziali dopo reinstallazione.

Ora:

1. Collegare/cambiare account esegue soltanto la ricerca della copia remota; non carica dati e non abilita il backup automatico.
2. Se esiste un backup e questa installazione non ha ancora confermato la scelta per quell’account, compare una scelta tra controllare il backup, usare esplicitamente i dati del dispositivo o decidere più tardi. Il controllo apre una seconda anteprima con conteggi, senza ripristinare automaticamente.
3. Il consenso all’uso dei dati locali è registrato per account in `noBackupFilesDir`. Non viene recuperato insieme alle normali preferenze Android dopo reinstallazione. Il job automatico controlla questa autorizzazione anche se le vecchie preferenze ripristinate indicassero `enabled=true`. In questa condizione non carica nulla; entrando nella schermata la vecchia pianificazione viene disattivata.
4. Ogni upload è un nuovo snapshot immutabile creato con POST. Nessuna PATCH o cancellazione delle copie precedenti. Dal pulsante Ripristina si sceglie la versione, poi si controlla l’anteprima e infine si conferma separatamente.
5. Dopo un ripristino, il salvataggio automatico su Drive resta disattivato: l’utente può riattivarlo esplicitamente.

Le copie precedenti rimangono conservate; in questa versione non è stata introdotta una cancellazione automatica dello storico. I file continuano a usare il nome storico e lo spazio privato `appDataFolder`, quindi le copie precedenti sono leggibili. La data di selezione è ordinata come `Instant`, non tramite confronto lessicografico.

## Completezza dei dati

Verificato il round-trip di un dataset con valori espliciti e non predefiniti, ripristinandolo senza usarlo come fallback. Il risultato deve essere uguale all’intero `DatabaseSnapshot` iniziale:

- Tutti i modelli: identificativi, nomi/sigle, attacco/stacco, giornata intera, pause, colore/icona, categoria, lavoro e valori economici.
- Tutte le assegnazioni e più turni nello stesso giorno: identificativi/date, modello, `originalShiftDefId`, `isFromRotation`, `isModifiedFromRotation`, orari personalizzati, stacco effettivo, note, tag, straordinari, trasferta, reperibilità tri-stato e `hideExShift`.
- Informazioni del giorno: note, reperibilità, straordinari/stacco, trasferta, visibilità ex turno e cancellazione della rotazione.
- Lavori e relativi parametri contrattuali, nomi, icone/colori e riferimenti documentali.
- Rotazioni complete, ordine degli elementi, descrizioni, rotazione attiva e predefinita; lavoro attivo.
- Regole statistiche, esclusione ferie nei festivi, preferenze del calendario, festività abilitate.
- Reperibilità manuale, per modello e stagionale; regole delle sveglie, disattivazione globale e singole sveglie escluse.

Una seconda prova applica il dataset ripristinato al repository, sostituisce normalmente un turno e verifica che l’origine della rotazione resti disponibile per “ex turno”; cancella con la gomma e verifica che un nuovo turno non erediti più l’origine cancellata. Il repository iniziale viene ripristinato nel `finally` del test.

Lo schema del backup rimane 4, con compatibilità dei backup precedenti. Prima del caricamento cloud o del salvataggio locale il documento viene validato integralmente; un errore non autorizza la sostituzione del calendario.

Limiti da comunicare correttamente: il JSON contiene i dati applicativi elencati. Non contiene password/token Google, permessi Android, stato di consegna delle notifiche o configurazioni del launcher. I PDF CCNL esterni mantengono nome e URI di riferimento: i loro byte non vengono incorporati nel JSON, quindi dopo reinstallazione/cambio dispositivo può essere necessario riselezionarli. Il tema è una preferenza locale separata, non parte di `DatabaseSnapshot`. Non dichiarare quindi che il file sia un’immagine completa di tutti i file e permessi del telefono.

## Protezione delle copie locali

`LocalBackupArchive` conserva copie immutabili, deduplicate per hash del contenuto. Prima di aggiornare il file locale storico, archivia anche la copia precedente, comprese quelle create dalle vecchie versioni. Scrittura del contenuto in file temporaneo, flush e rename/move; nessuna eliminazione delle copie precedenti. Una copia identica non crea un’altra versione. Il selettore Ripristina locale mostra lo storico e passa dall’anteprima con conferma.

Le copie locali sono nello spazio dell’app: non sostituiscono un backup Drive o un file esportato per sopravvivere a una disinstallazione. Lo storico locale è verificato con test su filesystem temporaneo, inclusi prima copia legacy, salvataggio completo, successivo salvataggio vuoto e ripetizione senza duplicati.

## Google Calendar

Sincronizzazione e rimozione condividono un blocco di 180 secondi per account, anche cambiando calendario, schermata o riavviando il processo. Il controllo è presente nel percorso operativo oltre che nei pulsanti. Viene prenotato prima dell’operazione remota e la finestra completa di 3 minuti riparte alla conclusione, anche quando l’esito non è certo per errore di rete.

Entrambi i pulsanti diventano indisponibili con conto alla rovescia, barra discreta e spiegazione del tempo di propagazione Google. Al termine tornano attivi. Le sole letture e la scelta del calendario rimangono disponibili. Un `derivedStateOf` evita che l’aggiornamento ogni secondo ricalcoli tutte le assegnazioni.

Sul telefono: inserita temporaneamente una scadenza di prova nelle preferenze del gate, avviato nuovamente il processo, selezionato un calendario Google reale in sola lettura e verificato via albero di accessibilità che entrambi i pulsanti fossero disabilitati (“Attendi 02:31” e “Rimuovi tra 02:31”). Osservata anche la scadenza naturale della prima prova, con ritorno dei pulsanti normali. Le preferenze iniziali del gate sono state ripristinate. **Non è stata eseguita una sincronizzazione o cancellazione reale degli eventi Google durante questa verifica.** Le durate esatte, l’ultimo millisecondo e la ripartenza dopo completamento sono verificate nei test.

## Contrasto delle rotazioni

Il riquadro selezionato conserva la superficie neutra e il testo ad alto contrasto. La selezione è indicata da un bordo primario animato di 2 dp e da una spunta. La stellina gialla ha una propria piccola base scura; i campioni colore hanno un supporto scuro indipendente dalla selezione. La pressione conserva il feedback elastico. Stessa geometria e allineamenti tra selezionato e non selezionato.

Verificate sul telefono entrambe le rotazioni, quella predefinita selezionata/non selezionata e i temi scuro e chiaro. Nessuna applicazione di rotazioni al calendario durante il controllo. Tema scuro originale ripristinato.

## Verifica reale Drive

- Collegato nuovamente lo stesso account dalla schermata Backup: trovata la copia esistente delle **14:47**, nessun upload e automatico spento.
- Aperta l’anteprima scaricata da Drive: **122 turni, 11 modelli, 1 lavoro, 2 rotazioni**. Ripristino annullato.
- Avviato Salva ora e confermata esplicitamente la scelta dei dati del dispositivo: nuova copia delle **15:37**, con risposta di successo da Drive.
- Aperto lo storico: presenti sia la copia delle **14:47** sia quella delle **15:37**. Aperta l’anteprima della nuova copia con gli stessi conteggi, poi annullata.
- Non è stato effettuato un ripristino distruttivo del dataset personale, né disinstallata l’app. La reinstallazione è simulata nel test del registro per-installazione, verificando che un nuovo `noBackupFilesDir` non erediti le approvazioni.
- Confronto prima/dopo sul telefono: tutte le sezioni del database e tutte le preferenze applicative identiche, escluso il timestamp tecnico dal confronto. Automatico cloud lasciato spento; una nuova copia remota valida è stata conservata.

## Build e artefatti

`assembleDebug test`: **400 test Debug e 400 Release, zero errori e fallimenti**. I nuovi test coprono registro per-installazione/account, file di approvazione corrotto, conservazione locale/legacy, upload remoto immutabile anche con snapshot vuoto ed errore di rete, durata del cooldown e round-trip completo con normale modifica/gomma dopo ripristino.

- APK: `outputs/TurniSync-v1.6.7.apk` nel workspace Codex.
- SHA-256 APK locale e installato: `63D26BF6C9D483A91A15D0A5D3A0F426BA8CDBF6A325B36747F616E074FADD1A`.
- Firma esistente preservata: SHA-1 `AB:1B:01:83:6A:C9:AC:F1:32:F5:F1:4E:D4:7A:C8:66:DD:9C:B6:01`.
- Log: `work/v167-build.log`; screenshot, baseline privata e script di verifica: `work/v167-qa/` e `work/v167-*.py` nel workspace Codex. Non pubblicare baseline o preferenze private.
- APK di verifica con firma debug, non pubblicazione di produzione. Nessun benchmark generale di fluidità eseguito in questa task.

## Riferimenti ufficiali

- [Android Auto Backup: noBackupFilesDir esclusa dalle copie di sistema](https://developer.android.com/identity/data/autobackup).
- [Google Drive: spazio applicativo privato e creazione dei file](https://developers.google.com/workspace/drive/api/guides/appdata).
