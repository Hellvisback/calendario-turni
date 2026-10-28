# Verifica v1.6.6 — memoria locale SuperShift e importazione

Data: 27 ottobre 2026. TurniSync 1.6.6 (versionCode 38), aggiornamento sul Realme 8 Pro già autorizzato, senza disinstallazione. Nessuna pubblicazione GitHub.

## Risultato dell’indagine

La presenza di dati locali SuperShift è reale e coerente con la scelta tra copia locale e cloud descritta dall’utente. Non è stata però ottenuta una copia esportabile del database. **La migrazione integrale automatica dalla versione gratuita non è implementata né verificata.** Non dichiarare il contrario o presentare il selettore .ics come lettore del backup proprietario.

La precedente guida era troppo categorica nel negare un file locale: è stata corretta distinguendo persistenza privata, backup Android ed esportazione condivisibile.

| Verifica sul dispositivo | Esito |
| --- | --- |
| SuperShift installato | `app.supershift`, 2026.24 / 26241, target SDK 37, non debuggable |
| Sistema del telefono | Android 13 / API 33, build non debuggable |
| Cartella esterna specifica dell’app | `/sdcard/Android/data/app.supershift` esiste ed è vuota |
| Cartelle media/obb di SuperShift | Non presenti |
| Ricerca mirata nello storage condiviso | Nessun file con nome SuperShift o backup dedicato; ricerca fino a profondità 5, non lettura indiscriminata dei documenti personali |
| Unico calendario .ics individuato | File preesistente del 2024, `PRODID:Events`; non attribuibile a un export SuperShift, non importato |
| Directory interne | `/data/user/0/app.supershift` e `/data/user_de/0/app.supershift` private; lettura negata |
| Accesso di debug ordinario | `run-as app.supershift` rifiutato: package non debuggable |
| Regole di backup nell’APK originale | `allowBackup=true`; `backup_rules.xml` e `data_extraction_rules.xml` includono il percorso interno `file/Supershift` e preferenze, per cloud backup e device transfer |
| File provider | Provider non esportato, accesso ai file solo tramite concessione URI dell’app; nessun provider documenti/turni pubblico dichiarato nel manifest esaminato |
| Account in SuperShift | Sincronizzazione attiva; nessun comando di download completo nella pagina Account |
| Esportazione calendario | Apertura normale dal menu mostra SuperShift Pro; nessuna prova o acquisto attivato |
| Altri menu | Verificati Account, impostazioni e assistenza; nessuna esportazione integrale gratuita individuata |

Il percorso `file/Supershift` è un’indicazione dichiarata nelle regole di backup, non un database ottenuto o analizzato: nome effettivo, schema e contenuti restano da verificare. `allowBackup` non autorizza un’altra app a leggerli. Android 12+ esclude i dati delle app non debuggable che targettano API 31+ dal vecchio `adb backup`: quel comando non è stato eseguito come se fosse una soluzione. Anche l’accesso a tutti i file non apre la memoria privata di un’altra app.

Non sono stati effettuati root, downgrade/reinstallazione di SuperShift, sostituzione della firma, logout, ripristino cloud, intercettazione di autenticazione, lettura di token/password, inviti di condivisione o invio di email.

## Modifiche TurniSync

- Guida Gratuita con distinzione tra dati locali e cloud, dettagli espandibili animati e collegamenti ufficiali. Nessuna falsa scansione globale o richiesta di permessi che non risolverebbe l’isolamento Android.
- Bozza di richiesta all’assistenza aggiornata: copia esportabile o procedura ufficiale, modelli e identificativi, date/orari/fuso, note, straordinari/reperibilità, rotazioni e calendari, descrizione del formato e campi esclusi. L’utente conserva il controllo dell’invio.
- `CalendarImportDocument` legge su IO il documento scelto dall’utente, con limite durante lo streaming di 16 MiB e decodifica UTF-8 rigorosa. Identifica backup Android, database SQLite, archivi ZIP e JSON non supportati prima del parser, con messaggi leggibili. Riconoscimento del contenuto, non dell’estensione. Nessun parser proprietario inventato e nessun upload automatico.
- Il convertitore calendario esistente resta additivo: modelli/assegnazioni nativi, categorie esplicite, orari personalizzati per singolo turno, note, identificativi stabili, anteprima, conservazione delle modifiche manuali e copia indipendente di recupero prima del salvataggio. Rotazioni, pause, reperibilità e turno originale non vengono dedotti da un semplice calendario.

## Test eseguiti

`assembleDebug test`: **392 test Debug e 392 Release, zero errori o fallimenti**. Nove verifiche aggiunte: lettura UTF-8/BOM, contenuti non supportati, documenti vuoti o troncati, limite streaming, comportamento dello stream; più turni nello stesso giorno e note conservati nel passaggio esportazione/ripristino e reimportazione senza duplicati. I test esistenti verificano anche straordinari ed eliminazione dei turni importati con i normali comandi del repository.

Il primo nuovo test di round-trip è stato isolato dalle vecchie `DayExtraInfo` lasciate dalle fixture di altri test, che attivavano legittimamente la migrazione legacy. Nessuna modifica alla migrazione dei backup è stata fatta per aggirare la verifica.

Sul telefono: aperti i menu SuperShift senza alterare turni o account; installato l’APK TurniSync tramite `install -r`; scelta dal selettore Android una fixture calendario locale con tre assegnazioni e due modelli, due turni nella stessa giornata (06–14 e 22–06) e nota Unicode. Anteprima e categorie corrette, pulsante Importa disabilitato fino alla conferma dell’anteprima. Anteprima annullata senza importare dati fittizi. Verificato il rifiuto di una fixture JSON non supportata. Rimossi soltanto i due file di prova nominati esplicitamente.

Confronto del database TurniSync prima/dopo: tutte le sezioni dei dati e tutte le preferenze identiche, escluso soltanto il timestamp tecnico di esportazione dal confronto. Nessun calendario utente sostituito. Non è stato eseguito un test di importazione completa del backup SuperShift, perché il file originale non è accessibile.

Evidenze locali nel workspace Codex: `work/v166-investigation/` (manifest e regole esaminate), `work/v166-qa/` (screenshot, fixture, baseline privata), `work/v166-build.log`. Le copie private del database non devono essere pubblicate.

## APK

- File consegnato: `outputs/TurniSync-v1.6.6.apk` nel workspace Codex.
- SHA-256: `AE7C96BDAA1808E350F3DFD7799EB4F82DA03385E54ED06A5471C0C546BADC6D`.
- Firma debug esistente preservata, SHA-1 `AB:1B:01:83:6A:C9:AC:F1:32:F5:F1:4E:D4:7A:C8:66:DD:9C:B6:01`.
- Package installato verificato: 1.6.6 / 38. Compilazione, test e aggiornamento locale, non una release di produzione o Google Play.

## Requisito per completare la migrazione integrale

Serve una copia originale effettivamente condivisibile, oppure un’interfaccia ufficiale di esportazione. Prima di sviluppare il convertitore completo occorre verificare schema/versione, identificativi e relazioni, fuso orario e turni notturni, pause, multi-turno, note del giorno e del turno, reperibilità/straordinari, rotazioni, calendari e duplicati. Il formato va confrontato con dati visibili nell’app originale. Un eventuale campo non rappresentabile deve essere segnalato nell’anteprima, senza scartarlo o trasformarlo silenziosamente. Nessuna garanzia di copertura integrale senza campione e verifica.

## Fonti ufficiali consultate

- [SuperShift: conservazione locale e sincronizzazione col proprio server](https://supershift.app/privacy.html).
- [SuperShift: esportazione verso calendari esterni](https://supershift.app/help.html).
- [Android: storage privato e condiviso](https://developer.android.com/training/data-storage).
- [Android 12: limitazioni adb backup](https://developer.android.com/about/versions/12/behavior-changes-12#adb-backup).
- [Android: anche l’accesso a tutti i file esclude directory di altre app](https://developer.android.com/training/data-storage/manage-all-files).
- [Android: Auto Backup non leggibile dall’utente o da altre app](https://developer.android.com/identity/data/autobackup).
