# BRIEFING — 2026-10-14T21:01:00Z

## Mission
Root Cause Analysis e Fix Strategy per il matching robusto dei nomi utente (Milestone 3, Iteration 2).

## 🔒 My Identity
- Archetype: explorer
- Roles: [explorer, investigator, analyst]
- Working directory: c:\Users\marco\Antigravity Progetti\Calediario Turni\.agents\teamwork\explorer_m3_iter2_1
- Original parent: 90066a2c-0ee0-47dc-9cce-f552c7e66ada
- Milestone: Milestone 3 (Iteration 2)

## 🔒 Key Constraints
- Read-only investigation — do NOT implement
- Lingua italiana per comunicazioni e report
- Analisi approfondita delle vulnerabilità di matching emerse in Challenger 1 / Reviewer 2
- Proposta chirurgica per il Worker: formule di punteggio, normalizzazione Normalizer.Form.NFD, gestione accenti/apostrofi, prevenzione collisioni e omonimie parziali

## Current Parent
- Conversation ID: 90066a2c-0ee0-47dc-9cce-f552c7e66ada
- Updated: 2026-10-14T21:01:00Z

## Investigation State
- **Explored paths**:
  - `c:\Users\marco\Antigravity Progetti\Calediario Turni\.agents\teamwork\ORIGINAL_REQUEST.md`
  - `c:\Users\marco\Antigravity Progetti\Calediario Turni\PROJECT.md`
  - `c:\Users\marco\Antigravity Progetti\Calediario Turni\.agents\teamwork\challenger_m3_1\handoff.md`
  - `c:\Users\marco\Antigravity Progetti\Calediario Turni\.agents\teamwork\reviewer_m3_2\handoff.md`
  - `android-app/app/src/main/java/com/turni/calendario/data/GeminiShiftMapper.kt`
  - `android-app/app/src/test/java/com/turni/calendario/GeminiUserMatchingStressTest.kt`
  - `android-app/app/src/test/java/com/turni/calendario/GeminiShiftMapperStressTest.kt`
  - `android-app/app/src/test/java/com/turni/calendario/GeminiShiftMapperTest.kt`
  - `android-app/app/src/main/java/com/turni/calendario/importer/NgeniaTurniParser.kt`
- **Key findings**:
  - Regola 5 di `calculateNameMatchScore` assegna score 60 per singolo token, superando la soglia 50 in `matchUserSquad` (falsi positivi su omonimia parziale es. "Andrea Romano" -> "Andrea Bianchi"). Risoluzione: ridurre score a 30 per match a singolo token quando la query ha >= 2 token.
  - Regola 3 usa `cTok.contains(qTok)` per token >= 3 caratteri, causando collisioni con parole brevi ("Dan" in "Giordano", "Leo" in "Pantaleo" con score 80). Risoluzione: eliminare `contains()`, usare solo token matching esatto o fusione per nomi composti (`cleanNoSpaces == cleanNoSpaces` -> score 95).
  - Assenza di decomposizione Unicode NFD e stripping `\p{M}` impedisce matching con accenti ("Niccolo" vs "Niccolò Paganini" -> score 0). Risoluzione: pipeline `normalizeName` con NFD e `\p{M}`.
  - Assenza di normalizzazione per apostrofi tipografici (U+2019 `’`) vs ASCII (`'`) e spazi OCR ("D' Angelo"). Risoluzione: mappare tutti gli apostrofi a spazio `" "`.
  - Margine di sicurezza: True match (75-100) vs False match (0-30) con gap di 45 punti rispetto alla soglia 50.
- **Unexplored areas**: Nessuna. L'investigazione sul matching e sulle condizioni al contorno è esaustiva.

## Key Decisions Made
- Definita pipeline di normalizzazione a 5 passaggi (`normalizeName`).
- Ristrutturato il sistema di scoring in 7 livelli gerarchici.
- Prodotta raccomandazione chirurgica con snippet completi per Worker M3.
- Redatti `analysis.md` e `handoff.md`.

## Artifact Index
- DISPATCH.md — Registro del messaggio di dispatch ricevuto
- BRIEFING.md — Working memory e tracciamento stato
- progress.md — Liveness heartbeat
- analysis.md — Report dettagliato di analisi tecnica e proposta di fix
- handoff.md — Report formale a 5 sezioni per il parent/worker
