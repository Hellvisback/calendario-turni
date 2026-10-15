# BRIEFING — 2026-10-14T20:36:00Z

## Mission
Stress test empirico dell'algoritmo `matchUserSquad` in `GeminiShiftMapper` su casi limite complessi (nomi composti, accenti, inversioni, spazi multipli, caratteri speciali, casi di omonimia parziale) ed emissione verdetto APPROVE/REJECT.

## 🔒 My Identity
- Archetype: teamwork_preview_challenger / Empirical Challenger
- Roles: critic, specialist
- Working directory: c:\Users\marco\Antigravity Progetti\Calediario Turni\.agents\teamwork\challenger_m3_1
- Original parent: 90066a2c-0ee0-47dc-9cce-f552c7e66ada
- Milestone: Milestone 3 (Shift Extraction & Domain Mapping)
- Instance: 1 of 2 (Challenger 1)

## 🔒 Key Constraints
- Review-only — do NOT modify implementation code (`GeminiShiftMapper.kt`)
- All tests must be executed empiricamente con Gradle (`.\gradlew.bat test` con `$env:JAVA_HOME = "C:\Program Files\Android\Android Studio\jre"`)
- Output handoff report in `c:\Users\marco\Antigravity Progetti\Calediario Turni\.agents\teamwork\challenger_m3_1\handoff.md`
- Provide verdict (APPROVE o REJECT) via send_message to parent

## Current Parent
- Conversation ID: 90066a2c-0ee0-47dc-9cce-f552c7e66ada
- Updated: 2026-10-14T20:36:00Z

## Review Scope
- **Files reviewed**:
  - `android-app/app/src/main/java/com/turni/calendario/data/GeminiShiftMapper.kt`
  - `android-app/app/src/test/java/com/turni/calendario/GeminiShiftMapperTest.kt`
  - `android-app/app/src/test/java/com/turni/calendario/GeminiUserMatchingStressTest.kt` (stress suite)
- **Interface contracts**:
  - `ORIGINAL_REQUEST.md` (R4: Auto-selection of user squad)
  - `worker_m3/handoff.md`

## Attack Surface
- **Hypotheses tested**:
  - Accenti/segni diacritici ("Niccolo" vs "Niccolò"): FALSI NEGATIVI confermati (score 0, null match).
  - Apostrofi tipografici U+2019 vs ASCII U+0027: FALSI NEGATIVI confermati su cognomi singoli (score 0).
  - Omonimia parziale / token singolo condiviso: FALSI POSITIVI CRITICI confermati ("Andrea Romano" vs "Andrea Bianchi" score 60 >= 50, "Marco Bianchi" vs "Marco Rossi" score 60).
  - Substring collision su nomi brevi (>=3 caratteri): FALSI POSITIVI confermati ("Dan" in "Giordano Bruno" score 80; "Leo" in "Pantaleo" score 80).
  - Nomi composti ("Gian Marco Rossi" vs "Gianmarco Rossi"): PASS (score 90/85).
  - Cognomi con prefissi ("De Luca", "Van Der Bellen"): PASS (score 90).
  - Punteggiatura e caratteri estremi/SQLi: PASS (nessun crash/eccezione).
- **Vulnerabilities found**:
  - 1 CRITICAL: False positive su omonimia parziale di nomi comuni a 2 token.
  - 1 HIGH: False positive per collisione substring su token brevi (3-4 caratteri).
  - 1 HIGH: False negative per assenza di normalizzazione diacritica/accenti.
  - 1 HIGH: False negative per mancata normalizzazione apostrofo tipografico (U+2019).
- **Untested angles**:
  - Matching con codici fiscali o matricole aziendali (fuori perimetro).

## Loaded Skills
- Methodology: Empirical testing, adversarial stress testing.

## Key Decisions Made
- Verdetto: **REJECT** per via dei falsi positivi critici su omonimia parziale e falsi negativi su accenti/apostrofi tipografici.
- Proposta di correzione mirata fornita all'orchestratore per Worker M3.

## Artifact Index
- `.agents/teamwork/challenger_m3_1/BRIEFING.md` — persistent memory
- `.agents/teamwork/challenger_m3_1/progress.md` — liveness heartbeat
- `.agents/teamwork/challenger_m3_1/DISPATCH.md` — dispatch history
- `.agents/teamwork/challenger_m3_1/handoff.md` — final assessment & verdict
- `android-app/app/src/test/java/com/turni/calendario/GeminiUserMatchingStressTest.kt` — reproducible test suite
