# Verifica v1.4.4 — 26 ottobre 2026

## Correzioni

- Grafico e legenda del resoconto includono ogni modello effettivamente assegnato, identificato per ID: nomi personalizzati, sigle uguali, categorie Riposo/Assenza/Altro e modelli di giornata intera. Le ore lavorate mantengono la classificazione lavorativa; la ripartizione conta tutte le assegnazioni.
- Geometria originale del grafico conservata, con avanzamento unidirezionale e un buffer Path riutilizzato durante il disegno.
- Icone articolate disegnate da geometrie memorizzate, senza ricostruire ImageVector/Painter a ogni frame. Nel dock il progresso viene letto direttamente nel draw.
- Destinazioni principali conservate tra i cambi tab; solo la vista attiva espone input/accessibilità. Il popup calendario viene chiuso uscendo dalla vista. I deep link dei widget riportano al Calendario.
- Bordino festivo sul calendario ridotto da 28×26 a 22×21 dp e rimosso l’alone esterno che invadeva la campanella.
- Widget: chevron vettoriali centrati nella riga della data, capsule della data più strette, numero festivo rosso, distinzione tra oggi/festivo/reperibilità. Widget Oggi e domani con badge del modello, nome e attacco in una riga agenda, senza un grande pannello separato per oggi.

## Misure intermedie

Protocollo ADB identico: 4 passaggi Calendario→Statistiche→Calendario, attesa 1,7/1,4 s e gfxinfo azzerato prima del campione. APK debug sul Realme 8 Pro, dati reali conservati.
- Prima: 374 frame, 239 janky (63,90%), mediana 36 ms, p95 85 ms.
- Primo intervento: 334 frame, 146 janky (43,71%), mediana 29 ms.
- Icone draw-only: 350 frame, 116 janky (33,14%), mediana 26 ms.
Questi campioni comprendono l’apertura iniziale del report: non sono una garanzia di 60 fps e hanno variabilità/JIT. Ulteriore confronto finale dopo il riuso del calendario.

## Salvaguardia dati

Snapshot del database reale acquisito prima di installare. Nessuna assegnazione modificata per provare la UI. Tema e impostazioni del launcher da ripristinare al termine delle verifiche.

## Collaudo finale

`assembleDebug test` superato; 341 test debug e 341 release, zero errori/failure/skipped. Aggiunti test con categorie diverse, nomi identici e personalizzati, modelli all-day, intervallo vuoto e orari effettivi.

Installata v1.4.4 (25) con la firma originale. Verificati sul Realme: calendario e widget chiari/scuri; intestazioni dei widget e mensile novembre a sei settimane; contorno festivo insieme a reperibilità (attivazione temporanea poi Annulla); cambio tab, accesso alle impostazioni dopo aver aperto Statistiche, modalità modifica senza applicare turni e apertura del giorno corretto dal widget mentre Statistiche era attiva. Il database finale è identico byte per byte a quello iniziale (122 assegnazioni, 11 modelli, un lavoro, due rotazioni); tema Scuro ripristinato e nessuna modifica alla disposizione del launcher.

Il grafico ottobre mostra tutte le 31 assegnazioni: Pomeriggio 7, Mattina 6, Notte 7, Mat Dom 1, Smonto Notte 3, Riposo 4, Ferie 2, Giornaliero 1. Totale ore lavorate 167,2: la classificazione delle ore non trasforma riposi/ferie in ore lavorate.

Campione finale dopo installazione, con contorni dock preparati su IO in 181 fasi: 365 frame, 175 in ritardo (47,95%), mediana 32 ms, p95 200 ms. Ripetizione a caldo: 360 frame, 51 in ritardo (14,17%), mediana 24 ms, p95 65 ms. Il primo caricamento resta più lento e ci sono ancora frame fuori budget: non si dichiara fluidità costante a 60 fps. I risultati intermedi migliori non sostituiscono il campione finale. Il confronto prima/dopo riduce la quota di jank, ma risente di JIT/cache e non costituisce un benchmark controllato di laboratorio.

Le schermate inattive conservano la composizione ma vengono spostate fuori viewport, escluse dalla semantica e poste dietro la destinazione attiva. Verificato che non intercettino i tap nelle impostazioni.

Evidenze nel workspace `work/v144-qa/`: screenshot, misure gfxinfo, database prima/dopo e verification.json. Nessuna release GitHub, nessuna modifica Google Cloud o OneDrive.

APK SHA-256: `19464518da39a2937f3b7bd7fd26063d8e1b4c840b7fc6830f0d0596fbae26ee`. Hash locale/installato uguali. Certificato SHA-1: `AB:1B:01:83:6A:C9:AC:F1:32:F5:F1:4E:D4:7A:C8:66:DD:9C:B6:01`.
