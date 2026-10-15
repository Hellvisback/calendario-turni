# Dispatch — Explorer 2 (Milestone 4 Iteration 2: UI & Acceptance Criteria Alignment)

## Mission
Analizzare i requisiti mancanti rilevati da Reviewer 1 e Reviewer 2 in `GeminiShiftImportScreen.kt` rispetto a `ORIGINAL_REQUEST.md` e definire il codice esatto da implementare.

## Input Files
1. `c:\Users\marco\Antigravity Progetti\Calediario Turni\.agents\teamwork\ORIGINAL_REQUEST.md` (in particolare § R4 e Acceptance Criteria)
2. `android-app/app/src/main/java/com/turni/calendario/ui/screens/GeminiShiftImportScreen.kt`
3. `.agents/teamwork/reviewer_m4_1/handoff.md`
4. `.agents/teamwork/reviewer_m4_2/handoff.md`

## Compito
- Progettare l'`AlertDialog` per l'azione "Modifica":
  - Titolo: "Attenzione" / "Uscire e modificare?"
  - Testo: "Stai per uscire da questa schermata per passare a quella di modifica, perderai queste informazioni."
  - Bottoni: Conferma ("Esci e Modifica" -> salva e `onNavigateToTurni(targetRot)`) e Annulla ("Rimani qui").
  - Stile Obsidian Liquid Glass conforme (`ObsidianSurfaceContainer`, `ObsidianBorderSubtle`, `TextPrimary`, `TextSecondary`).
- Progettare la nota visiva esplicativa (§ R4): "I turni potranno essere rifiniti in qualsiasi momento nell'apposita sezione 'Catalogo Turni' dell'app."
- Progettare il debounce / disabilitazione su "Conferma e Applica Turni" (`isSaving` state).
- Progettare `visualTransformation = if (showApiKey) VisualTransformation.None else PasswordVisualTransformation()` nel campo API Key con icona toggle.
- Scrivere il piano dettagliato in `.agents/teamwork/explorer_m4_iter2_2/analysis.md` e inviare notifica formale.

## 2026-10-14T23:55:35Z
Sei Explorer 2 per Milestone 4 Iteration 2 (UI & Acceptance Criteria Alignment).
Il tuo working directory dedicato è:
c:\Users\marco\Antigravity Progetti\Calediario Turni\.agents\teamwork\explorer_m4_iter2_2

Devi leggere OBBLIGATORIAMENTE prima di qualsiasi altra operazione:
1. c:\Users\marco\Antigravity Progetti\Calediario Turni\.agents\teamwork\ORIGINAL_REQUEST.md (in particolare § R4 e Acceptance Criteria)
2. c:\Users\marco\Antigravity Progetti\Calediario Turni\PROJECT.md
3. c:\Users\marco\Antigravity Progetti\Calediario Turni\.agents\teamwork\explorer_m4_iter2_2\DISPATCH.md
4. c:\Users\marco\Antigravity Progetti\Calediario Turni\.agents\teamwork\reviewer_m4_1\handoff.md
5. c:\Users\marco\Antigravity Progetti\Calediario Turni\.agents\teamwork\reviewer_m4_2\handoff.md
6. android-app/app/src/main/java/com/turni/calendario/ui/screens/GeminiShiftImportScreen.kt

NON modificare alcun file sorgente (ruolo read-only).
Progetta la specifica esatta in codice Kotlin Compose per:
1. Dialog modale di avviso su "Modifica" (AlertDialog Obsidian Liquid Glass con titolo, messaggio prescritto da R4, conferma e annulla).
2. Nota visiva esplicativa R4 sul catalogo turni.
3. Debounce / disabilitazione con isSaving su "Conferma e Applica Turni".
4. PasswordVisualTransformation su inserimento API Key con toggle di visualizzazione.
Scrivi il tuo report in c:\Users\marco\Antigravity Progetti\Calediario Turni\.agents\teamwork\explorer_m4_iter2_2\analysis.md e invia una notifica a me via send_message.
