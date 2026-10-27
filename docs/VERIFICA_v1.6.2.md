# Verifica TurniSync 1.6.2 (34) — 27 ottobre 2026

## Intervento

- Oggi è un pulsante rettangolare arrotondato: superficie tematizzata, bordo, area di tocco 46 × 44 dp e ruolo accessibile Button.
- Ingresso con dissolvenza, scorrimento breve ed espansione spring; uscita coordinata senza comprimere subito il titolo del mese. Pressione con tinta e abbassamento di 1,5 dp; nessuna scala del testo. I tocchi sono disabilitati durante l'uscita dopo il ritorno al mese corrente.
- Ritorno al mese corrente con una sola transizione orizzontale spring, anche da mesi distanti. Le frecce del navigatore restano vicine al nome del mese; verificati Settembre, Novembre e Dicembre 2026.
- Periodo: badge limitato alla data; due frecce complete con asta e contorno in una guida separata nella parte bassa della sola cella iniziale. Il bordo della selezione rimane visibile. Campanella e note mantengono la loro posizione ordinaria, senza aloni sopra il bordo.
- La guida conserva l'ingresso progressivo da 420 ms e riserva spazio anche rispetto ai marcatori di eccedenza e al testo ex turno. Il suo tracciato è memorizzato e disegnato nel Canvas esistente; nessuna nuova animazione infinita o un layer per ogni giorno.
- Rimane la selezione a due tap: il primo identifica soltanto l'inizio; il secondo applica l'intervallo avanti o indietro. Nessun riempimento preventivo globale.

## Controlli eseguiti

- `assembleDebug test`: BUILD SUCCESSFUL. Debug: 375 test, Release: 375 test; zero errori, fallimenti o test saltati.
- Installazione con `adb install -r` sul Realme 8 Pro cb08bc57 e avvio dell'app aggiornato.
- Verifiche visive tramite screenshot fisici: Oggi in tema chiaro/scuro; Settembre con nome lungo; ritorno da un mese precedente e da uno successivo; guida Periodo nel calendario di Novembre a sei righe; 8 dicembre con festività e reperibilità contemporanee, in entrambi i temi.
- La prima selezione Periodo mantiene visibili il turno esistente, la campanella, la festività e gli altri giorni senza anteprima globale.
- Database JSON confrontato prima/dopo tutte le verifiche e dopo l'installazione finale: identico, incluse 122 assegnazioni e 11 modelli. Nessuna applicazione di intervalli effettuata sui dati dell'utente durante la QA. Tema scuro originario ripristinato.
- `git diff --check` sui tre file di codice modificati: nessun errore.

## Artefatti

- APK: `C:/Users/marco/Documents/Codex/2026-10-19/ciao-sei-il-master-orchestrator-dell/outputs/TurniSync-v1.6.2.apk`
- SHA-256: `ee493a4ecc0d345da338ad2ee89cf549b0785e26842284bcfafe6cd7874cf9cc`
- Screenshot e copie locali del DB per confronto: `C:/Users/marco/Documents/Codex/2026-10-19/ciao-sei-il-master-orchestrator-dell/work/v162-qa`
- Log compilazione: `C:/Users/marco/Documents/Codex/2026-10-19/ciao-sei-il-master-orchestrator-dell/work/v162-build.log`

Questa verifica riguarda il pulsante Oggi e l'indicatore Periodo. Non costituisce una nuova verifica completa di sincronizzazione Google, sveglie, importazione SuperShift o prestazioni su altri dispositivi. Restano validi i limiti documentati nelle verifiche precedenti. Nessuna pubblicazione GitHub effettuata.
