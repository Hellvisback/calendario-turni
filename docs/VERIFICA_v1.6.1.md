# SuperShift: verifica delle funzioni e guida corretta — 27 ottobre 2026

## Esito da non sovrastimare

La versione 1.6.1 (33) corregge la procedura della 1.6.0, che suggeriva un percorso ICS non offerto dalla versione gratuita di SuperShift. **Non realizza una migrazione completa automatica gratuita del backup proprietario.** Nessun file completo o API pubblica documentata è stato trovato durante queste verifiche.

La sincronizzazione SuperShift Cloud con accesso Google non va confusa con Google Calendar, Drive appData o il backup Android/Google One. La privacy policy ufficiale dichiara che i dati degli account registrati vengono sincronizzati su un database di SuperShift. La FAQ specifica che l’account Cloud non è vincolato ad Apple/Google. Il bottone Google è un metodo di accesso, non un’esportazione su Drive.

## Riscontri sulla versione Android 2026.24

- Accesso Google all’account SuperShift già presente riuscito; Account mostra sincronizzazione attiva con ultimo aggiornamento 27/10/26. Nessuna sottoscrizione attivata.
- Calendario, modelli, più turni, rotazioni, report di conteggio e widget sono funzioni di base descritte nella scheda ufficiale. Il report di conteggio è stato aperto sul telefono senza proposta Pro.
- Esportazione calendario, eventi dei calendari esterni, PDF e rimozione annunci sono nella sezione Pro del menu Altro.
- Aprendo Esportazione calendario compare l’offerta Pro con opzioni mensile, annuale e acquisto a vita. Nessuna prova, acquisto o abbonamento è stato attivato.
- Il menu Account espone profilo, credenziali, disconnessione, eliminazione e stato sync; non un comando di download completo del backup.
- La condivisione calendario apre “Crea un invito”; non è stata creata alcuna condivisione. Non viene presentata come un backup importabile.
- Le impostazioni Android del Realme sono state lette: Altre impostazioni → Esegui back-up e ripristino → Account Google propone la configurazione del backup del dispositivo. Annullato senza modificare preferenze. Un backup di sistema non è un calendario .ics.

## Correzioni in TurniSync

Percorso: Impostazioni → Backup e ripristino → Scegli come trasferire.

1. Selettore animato con Gratuita / Pro / File .ics; default Gratuita.
2. Gratuita: spiega che non c’è un file da cercare, permette di aprire SuperShift e controllare l’account, prepara una richiesta di assistenza per sapere come ottenere una copia riutilizzabile. L’anteprima chiarisce che disponibilità e formato devono essere confermati da SuperShift. La richiesta non contiene account o dati personali precompilati. È possibile copiare il testo o aprire una bozza email: nessun messaggio viene inviato automaticamente.
3. Alternativa assistita: riuso della schermata esistente per ricreare modelli e rotazioni da informazioni leggibili; limiti espliciti, nessun invio AI automatico. Corretti anche i testi “automatica, completa” del vecchio assistente. Il tasto indietro torna alla guida SuperShift.
4. Pro: lettura del calendario Google esportato da chi dispone già della funzione. Richiesta Google in sola lettura solo in seguito a interazione esplicita. Limiti del calendario standard restano espliciti.
5. File .ics: importatore generico di un file già posseduto, chiaramente distinto da un backup SuperShift. Eliminata ogni istruzione per generare gratuitamente un file da SuperShift.
6. Guida al backup Google/Cloud con rimando alla documentazione ufficiale. Nessun accesso a password o archivi privati di altre app.
7. Visibilità package limitata ad app.supershift nel manifest per il pulsante Apri SuperShift. Nessun nuovo permesso di lettura dati SuperShift.

Il parser, la deduplicazione, il merge additivo e il recupero della 1.6.0 restano invariati. Per un lettore del backup completo servirà prima un canale di esportazione autorizzato e un campione originale verificabile; la risposta dell’assistenza non è garantita e il relativo formato non è già supportato per presunzione.

## Verifica

- APK 1.6.1 (33) installato con aggiornamento in-place sul Realme cb08bc57.
- SHA-256: 4d40308ff98652c1da825fcb32870ffcea0075f28cc497955449e7ac03bb08ac.
- Artefatto: C:/Users/marco/Documents/Codex/2026-10-19/ciao-sei-il-master-orchestrator-dell/outputs/TurniSync-v1.6.1.apk.
- assembleDebug + test: successo, 375 test Debug e 375 Release, zero fallimenti/errori.
- Provati i tre metodi, l’anteprima della richiesta, il collegamento all’assistente e il ritorno alla guida.
- Confronto JSON prima/dopo: database TurniSync identico. Nessun dato importato o cancellato durante questa verifica.
- Nessuna email inviata, invito creato o funzione a pagamento attivata.
- Screenshot privati e log: work/v161-qa e work/v161-build.log della chat.

## Fonti primarie

- https://play.google.com/store/apps/details?id=app.supershift&hl=en-US
- https://supershift.app/help.html
- https://supershift.app/privacy.html
- https://support.google.com/android/answer/2819582

I piani possono cambiare tra versioni e piattaforme; questa verifica riguarda l’Android installato e le fonti consultate alla data indicata.
