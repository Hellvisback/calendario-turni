# DISPATCH — Challenger 2 Milestone 3

## Identity & Role
- Archetype: teamwork_preview_challenger
- Role: Challenger 2 - Cyclic Rotation & Repository Concurrency Stress
- Working Directory: c:\Users\marco\Antigravity Progetti\Calediario Turni\.agents\teamwork\challenger_m3_2

## Objective
Empirically stress test `GeminiShiftMapper`:
- Test cyclic rotations with unusual lengths (1-day, 3-day, 35-day, 42-day cycles).
- Test unmapped shift codes (e.g., unknown custom codes in rotation days) to verify graceful fallback (e.g. creating placeholder shifts or skipping).
- Test concurrent calls to `registerExtractedData` and repetitive registrations.
- Run test verification:
  `powershell: $env:JAVA_HOME = "C:\Program Files\Android\Android Studio\jre"; .\gradlew.bat test`
Provide verdict (APPROVE or REJECT) in handoff.md and send_message.

## 2026-10-14T20:15:45Z
Sei Challenger 2 per Milestone 3 (Shift Extraction & Domain Mapping).
Il tuo working directory dedicato è:
c:\Users\marco\Antigravity Progetti\Calediario Turni\.agents\teamwork\challenger_m3_2

Devi leggere OBBLIGATORIAMENTE il file dei requisiti originali prima di qualsiasi azione:
c:\Users\marco\Antigravity Progetti\Calediario Turni\.agents\teamwork\ORIGINAL_REQUEST.md

Leggi anche il tuo dispatch file:
c:\Users\marco\Antigravity Progetti\Calediario Turni\.agents\teamwork\challenger_m3_2\DISPATCH.md
e l'handoff di worker_m3:
c:\Users\marco\Antigravity Progetti\Calediario Turni\.agents\teamwork\worker_m3\handoff.md

Sottoponi a stress testing empirico le rotazioni cicliche e la persistenza:
rotazioni a cicli insoliti (1, 3, 21, 35, 42 giorni), turni sconosciuti nella rotazione, registrazioni ripetute e concorrenza su ShiftRepository.
Esegui:
powershell: $env:JAVA_HOME = "C:\Program Files\Android\Android Studio\jre"; .\gradlew.bat test
Fornisci il tuo verdetto (APPROVE o REJECT) in c:\Users\marco\Antigravity Progetti\Calediario Turni\.agents\teamwork\challenger_m3_2\handoff.md e invia un messaggio a me.
