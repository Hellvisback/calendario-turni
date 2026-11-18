# TurniSync 1.8.0-beta.24

Versione Android 69. [Impostazioni e reperibilità sul Realme 8 Pro](../design/qa-beta24/impostazioni-reperibilita.png).

Le quattro sezioni delle impostazioni sono Calendario e statistiche, Reperibilità e sveglie, Importazione e condivisione, Dati dell’app. Le dieci destinazioni si aprono direttamente. Nella riga delle sveglie, il testo apre le regole e l’interruttore gestisce l’attivazione.

Nella schermata Reperibilità, il cobalto identifica la modalità e i modelli selezionati. L’ambra identifica i giorni reperibili nell’anteprima dei prossimi sette giorni. L’anteprima consulta la stessa regola del calendario e tiene conto delle eccezioni manuali.

## Prove

Sul Realme 8 Pro è stato usato il package di prova separato con dati sintetici. Verificati i temi chiaro e scuro e le tre modalità. Scegliendo Mattina, l’anteprima passa da zero a tre giorni reperibili. Portando a due le settimane reperibili del ciclo stagionale, il valore viene salvato. Dopo il riavvio si riapre la modalità stagionale e tornando alla modalità per turno si ritrovano i tre giorni. Verificati anche l’accesso alle regole delle sveglie, al backup e alla conferma di eliminazione, annullata con tutti i dodici turni di prova conservati.

488 test unitari superati. Build beta con R8 e lint vitale riuscita. L’APK distribuito ha package `com.turni.calendario`. La prova sul telefono è stata svolta su `com.turni.calendario.setupqa`.
