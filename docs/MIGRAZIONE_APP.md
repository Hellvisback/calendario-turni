# Portare i dati dalla vecchia app alla versione di prova

La versione personale (`com.turni.calendario`) e la versione QA (`com.turni.calendario.setupqa`) sono due installazioni distinte. Android conserva i dati di ciascuna nel suo spazio privato: installare o aprire la QA non trasferisce automaticamente turni, impostazioni o accesso Google dall'app personale. Una firma APK diversa può impedire di aggiornare l'app personale, ma non rende incompatibile un backup JSON.

Conservare la vecchia app e i suoi dati durante il trasferimento. Per usare un file senza dipendere dall'accesso Google della QA:

1. Aprire la vecchia app con i turni già presenti. Nelle versioni native recenti il percorso è **Impostazioni → Backup e ripristino → Dispositivo e file → Esporta file**. Salvare il JSON in Download o in un'altra cartella accessibile dal selettore Android. Una copia salvata soltanto nello spazio privato della vecchia app non è visibile automaticamente alla QA.
2. Aprire la versione QA e scegliere il percorso di recupero del calendario oppure **Impostazioni → Backup e ripristino → Dispositivo e file → Importa file**. Selezionare il JSON appena esportato. L'importazione da file non richiede un account Google.
3. Controllare l'anteprima: numero di turni, modelli, lavori e rotazioni. Confermare il ripristino solo quando la copia è quella desiderata. La conferma sostituisce i dati della QA, lasciando separati quelli della vecchia app; Annulla non applica il backup.
4. Verificare alcuni giorni noti, turni modificati da rotazione, straordinari, trasferte, note e impostazioni. Conservare il file esportato e la vecchia installazione finché il controllo non è concluso. Il backup automatico remoto va attivato solo dopo avere recuperato e verificato i dati.

In alternativa, se esiste già una copia su Google Drive, accedere con lo stesso account, scegliere la copia e controllarne l'anteprima. Il solo accesso Google non ricostruisce i turni se non è stata salvata una copia. L'accesso Google della QA richiede inoltre che il relativo package e certificato siano autorizzati nella configurazione OAuth; il trasferimento tramite file evita questa dipendenza.

## Formati verificabili dal codice

Il parser nativo accetta backup JSON con versione da 1 a 5. La beta.2 nativa esportava la versione 4; la versione attuale esporta la 5. Il formato non lega il backup al nome del package Android o alla firma dell'APK. I riferimenti fra lavori, modelli, turni originali, rotazioni, etichette e regole statistiche vengono validati prima di applicare i dati.

I file ZIP di progetto o sorgenti non sono backup del calendario. Nel percorso nativo esaminato non risulta un formato di backup ZIP supportato: non rinominare un archivio ZIP in `.json`. Senza il file o il messaggio di errore preciso non si può attribuire un eventuale rifiuto a una specifica incompatibilità. Un file di un'altra applicazione o una copia incompleta richiedono una valutazione del formato; non devono essere applicati aggirando le verifiche.

Il JSON conserva i riferimenti ai documenti CCNL, ma non incorpora il contenuto dei PDF. Un URI o percorso privato della vecchia app può non essere accessibile alla QA: in tal caso selezionare nuovamente il documento dal file originale. Ciò è distinto dalla compatibilità dei turni e delle impostazioni del calendario.

La copertura automatica comprende il round-trip completo dei dati, l'importazione di un backup nativo v4 in uno snapshot indipendente e vuoto, e la migrazione v1 di straordinari/trasferte giornalieri mantenendo turni originali, rotazioni e regole statistiche. Questa copertura non sostituisce il controllo del backup reale dell'utente, che non è stato fornito in questa verifica.
