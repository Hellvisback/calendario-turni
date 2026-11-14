# Verifica dati accessori del backup — 28 ottobre 2026

- Nessuna omissione individuata nel formato corrente: AssignedShift e DayExtraInfo esportano e ripristinano overtimeHours, actualEndTime, isTrasferta e notes; per il singolo turno anche isOvertime, orari personalizzati e tag.
- Persistenza interna, copia locale, esportazione file, Drive manuale e Drive automatico usano lo stesso serializzatore GoogleDriveBackupHelper.exportDatabaseToJson e il parser DatabaseRestore. Non sono state eseguite nuove scritture o letture remote Drive in questa verifica.
- Aggiunte due regressioni: doppio turno con straordinari, trasferta e note indipendenti, modifica normale dopo il ripristino; note e metadati giornalieri senza alcuna assegnazione nel calendario. Il test completo preesistente verifica anche modelli, lavori, rotazioni, provenienza ex turno e configurazioni.
- Test Debug: 434; Release: 434; zero fallimenti/errori.
- Controllo reale in sola lettura sull’APK beta.2 non debuggable: database e copia locale presenti, 122 assegnazioni e una voce giornaliera; zero campi accessori mancanti in entrambi. I valori straordinari/trasferte/note della copia esaminata sono vuoti. Nessun testo delle note è stato estratto o mostrato.
- SHA256 database invariato: 0a53a478ed11eacdf95e03e83d0fc64d06faa19e2d9e33429f7450ab72e54289.
- Modificati soltanto test JVM e sonda androidTest; nessuna modifica al codice distribuito e nessun nuovo APK necessario. Il telefono rimane alla 1.8.0-beta.2 (47).
