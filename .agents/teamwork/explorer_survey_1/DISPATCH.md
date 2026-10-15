# DISPATCH — Explorer Survey 1

## Identity & Role
- Archetype: teamwork_preview_explorer
- Role: Codebase Explorer - Data Models, Shift Repositories & State
- Working Directory: c:\Users\marco\Antigravity Progetti\Calediario Turni\.agents\teamwork\explorer_survey_1

## Objective
Investigate the data layer and domain models of the application to determine how shifts, rosters, rotations, and preferences are structured, stored, and updated.

## Scope & Files to Investigate
- Read `c:\Users\marco\Antigravity Progetti\Calediario Turni\.agents\teamwork\ORIGINAL_REQUEST.md`
- Inspect `android-app/app/src/main/java/...` looking for:
  - `ShiftRepository`, `ShiftDefinition`, shift models, teams/squads models, rotation patterns
  - How shift hours/legenda (M, P, N, MD, reperibilità) are currently modeled and saved
  - How SharedPreferences or local persistence is handled (and how to securely store Google Gemini API Key)
  - How newly extracted shifts and user's default team can be registered and applied to the calendar

## Output
Write a structured report to `c:\Users\marco\Antigravity Progetti\Calediario Turni\.agents\teamwork\explorer_survey_1\analysis.md` and complete handoff at `c:\Users\marco\Antigravity Progetti\Calediario Turni\.agents\teamwork\explorer_survey_1\handoff.md`.
Report back via send_message when done.

## 2026-10-14T15:43:00Z
Sei Explorer 1 (Codebase Explorer - Data Models, Shift Repositories & State).
Il tuo working directory dedicato è:
c:\Users\marco\Antigravity Progetti\Calediario Turni\.agents\teamwork\explorer_survey_1

Devi leggere OBBLIGATORIAMENTE il file dei requisiti originali prima di qualsiasi azione:
c:\Users\marco\Antigravity Progetti\Calediario Turni\.agents\teamwork\ORIGINAL_REQUEST.md

Leggi anche il tuo dispatch file:
c:\Users\marco\Antigravity Progetti\Calediario Turni\.agents\teamwork\explorer_survey_1\DISPATCH.md

Investiga approfonditamente il codice dell'app Android in android-app/app/src/main/java/:
1. Identifica come sono modellati i turni (ShiftDefinition, colori, orari di inizio/fine, reperibilità, flag).
2. Esamina ShiftRepository e altri repository/manager di persistenza dello stato (dove sono salvati i dati: DataStore, SharedPreferences crittografate o standard, file JSON, Room?).
3. Esamina la gestione dei team/squadre (ShiftRotation, turni ciclici delle squadre, turnista predefinito).
4. Verifica come memorizzare in sicurezza l'API Key di Google Gemini e le preferenze utente (nome utente per matching automatico della squadra).
5. Documenta con precisione file, percorsi, firme dei metodi esistenti e quali modifiche o estensioni saranno necessarie per supportare l'inserimento dei turni estratti da Gemini Flash.

Scrivi il tuo report completo in:
c:\Users\marco\Antigravity Progetti\Calediario Turni\.agents\teamwork\explorer_survey_1\analysis.md
e il tuo handoff report in:
c:\Users\marco\Antigravity Progetti\Calediario Turni\.agents\teamwork\explorer_survey_1\handoff.md
Poi invia un messaggio di completamento a me (il Project Orchestrator) con send_message indicando i percorsi dei file e i risultati chiave.
