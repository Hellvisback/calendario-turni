# Verifica v1.4.6 — 26 ottobre 2026

## Interventi

Icona sveglia condivisa ridisegnata: quadrante circolare, lancette e campane simmetriche, animazione delle lancette e oscillazione delle campane. Coerente fra Impostazioni, Regole, Prossime sveglie e schede.

Ripristinata anteprima PDF reale, aggiornata dopo le opzioni con debounce di 280 ms e generazione su IO. Ingrandimento con zoom/pan e chiusura X. La condivisione usa esattamente lo stesso file dell’anteprima. Nessuna rigenerazione diversa al tap Condividi.

Generatore a una sola pagina: Elenco usa colonne ordinate e bilanciate, senza dividere un turno o omettere dettagli. Mese, Anno e Periodo producono soltanto il calendario, senza elenco aggiunto. Carta bianca, testo scuro, tinte pastello, legenda dei codici per calendari multipli. A4 per elenco e calendari fino a due mesi; A3 per calendari più estesi. Massimo 12 mesi per calendario. Se il contenuto selezionato non entra alla dimensione minima prevista, viene mostrato un errore e non si abilita la condivisione: nessun taglio silenzioso o pagina aggiunta. Una pagina con quantità arbitrarie di note e turni non è garantibile mantenendo leggibilità.

## Collaudo reale

- Build assembleDebug test superata: 350 test debug + 350 release, zero failure/error/skipped. Cinque test nuovi del packing delle colonne, ordine, capacità e input invalido.
- APK v1.4.6 (27) installato sul Realme 8 Pro cb08bc57 conservando firma e dati.
- Generati ed estratti i quattro PDF dalla cache dell’APK reale: elenco.pdf, mese.pdf, anno.pdf, periodo.pdf. Poppler conferma una pagina ciascuno. Render PNG ispezionati: elenco bilanciato con 31 turni, mese calendario senza elenco, anno 12 mesi su A3 con dati settembre-dicembre e legenda, periodo con date selezionate.
- Anteprima ingrandita verificata sul telefono. Aperto chooser Android dal pulsante Condividi e annullato, senza inviare documenti a terzi.
- Screenshot delle icone aggiornate in Impostazioni e Regole. Sveglie mantenute in pausa; nessuna modifica alle regole o prova sonora in questa task.
- Database finale identico byte per byte al backup precedente alle prove. Nessuna modifica Google Cloud, GitHub o OneDrive.

Evidenze: workspace Codex work/v146-qa, inclusi PDF, PNG, screenshot e verification.json. Periodo collaudato sull’intervallo ottobre corrente; Anno verifica il layout con più mesi.

APK SHA-256: 1cc6bee2a4f5d4a455cc71e437b6d9b3346d79d118ef98a5665f2a53e46dfdda. Identico all’APK installato. Certificato originale SHA-1 AB:1B:01:83:6A:C9:AC:F1:32:F5:F1:4E:D4:7A:C8:66:DD:9C:B6:01.
