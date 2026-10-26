# Verifica TurniSync v1.4.2 — 26 ottobre 2026

## Modifiche richieste
- Gomma in Più turni: con due assegnazioni totali rimuove direttamente l’unico extra. Con tre o più apre un foglio che elenca soltanto gli extra; il turno base resta protetto. Nessun extra: nessuna cancellazione.
- Periodo: data iniziale su capsula colorata ad alto contrasto, due chevron vettoriali visibili, ingresso animato. Nessuna anteprima globale al primo tap.
- Statistiche: ripristinati i tre tratti originali del grafico, con stesse dimensioni, posizione e porzione visibile. Un’unica distanza di avanzamento guida tutti i colori, senza crescita inversa del tratto finale.
- Impostazioni: rimossa la seconda voce Reperibilità, card più leggibili e accesso alle regole sveglie raccolto accanto all’interruttore generale.
- Tema: Automatico/Chiaro/Scuro con selettore animato; Automatico segue Android anche nei widget. Conservate le scelte esplicite delle versioni precedenti. Inizializzazione spostata nell’Application per evitare il crash Compose al primo avvio.
- Nome uniforme: Esporta & Stampa PDF. Nuova schermata a card, filtro dei modelli del lavoro attivo, calendario tematizzato Dal/Al, controlli dei contenuti e generazione fuori dal thread UI.
- PDF: calendario con chip colorati e orario opzionale, elenco paginato completo, corretta selezione mese/anno/intervallo, turni multipli, durata con orari personalizzati e pausa, note/tag/trasferte rispettati. Le informazioni eccedenti le celle sono conservate nell’appendice di dettaglio.

## Verifiche eseguite
- `assembleDebug test` riuscito; verifica conclusiva `test` con **331 test debug e 331 release**, zero failure/error/skipped.
- APK v1.4.2, versionCode 23, installato con aggiornamento sul Realme 8 Pro cb08bc57.
- Firma originale confermata: SHA-1 AB:1B:01:83:6A:C9:AC:F1:32:F5:F1:4E:D4:7A:C8:66:DD:9C:B6:01.
- SHA-256 APK locale e installato identico: `e1e9d42d3021a200a1d487b5f61e44a89d01b330863516dda4294aacc018946a`.
- Tre avvii a freddo con PID distinti, nessun nuovo crash nel buffer Android. Il precedente crash di inizializzazione del tema è stato corretto e ricollaudato.
- Tema Automatico provato cambiando temporaneamente il tema Android da scuro a chiaro e ritorno. Ripristinata la scelta Scuro precedente. Screenshot acquisiti in entrambi i temi.
- Gomma provata sul 12 ottobre: aggiunti due extra, foglio con due sole scelte, rimozione selettiva del primo; con l’unico extra rimasto, cancellazione diretta senza foglio. Ripristinati i turni originali.
- Database prima/dopo: 122 assegnazioni identiche; modelli, lavori, rotazioni, note e configurazioni sveglie/reperibilità invariati. Rimane una voce neutra DayExtraInfo sul giorno usato per il test, senza informazioni o effetti funzionali.
- PDF effettivamente generati dall’APK: elenco ottobre 4 pagine; calendario ottobre + dettaglio 5 pagine; anno 50 pagine con giorni vuoti inclusi. Verificata presenza dei 31 giorni nell’elenco, dicembre nell’annuale. PDF mensile/elenco renderizzati localmente e controllati visivamente; nessun invio esterno.
- Nessuna release GitHub e nessuna copia OneDrive.

## Artefatti
- TurniSync-v1.4.2.apk
- TurniSync-esempio-PDF-v1.4.2.pdf (PDF reale prodotto sul Realme)
- Screenshot e PDF di verifica: work/v142-qa nel workspace Codex.

## Limiti
Il numero di pagine dipende da periodo, contenuti e opzione Giorni senza turni; con l’annuale e tutti i giorni inclusi il documento è lungo. Non è stata misurata una frequenza garantita di 60 fps. La pubblicazione OAuth Google rimane in Test come documentato in GOOGLE_SETUP_v1.4.1.md; questa revisione non ne modifica lo stato.
