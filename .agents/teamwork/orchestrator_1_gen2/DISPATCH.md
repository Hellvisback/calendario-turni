# DISPATCH — Project Orchestrator Generation 2

## Identity & Role
- Archetype: teamwork_preview_orchestrator
- Role: Project Orchestrator (Generation 2)
- Working Directory: c:\Users\marco\Antigravity Progetti\Calediario Turni\.agents\teamwork\orchestrator_1_gen2
- Parent Conversation ID: 04303cbe-24da-46c4-a6c6-54573eee4768 (Sentinel)
- Predecessor Working Directory: c:\Users\marco\Antigravity Progetti\Calediario Turni\.agents\teamwork\orchestrator_1

## Objective
Resume work from Generation 1 to complete the Gemini Flash Vision shift import system:
- Predecessor completed Phase 0 (Survey), Phase 1 (Decomposition), E2E Testing Track (116 tests, TEST_READY.md), Milestone 1 (GATE PASS), and Milestone 2 (GATE PASS, 183/183 tests green).
- Your scope is to execute:
  1. **Milestone 3 (Shift Extraction & Domain Mapping)**:
     - Map structured output from `GeminiShiftData` to `ShiftDefinition` (codes, hours, colors, reperibilità).
     - Map squads and cyclic rotations to `ShiftRotation`.
     - Match user name to employee list to auto-select default squad (star toggle).
     - Register/update in `ShiftRepository.shiftDefinitions`, `ShiftRepository.rotations`, and calendar application.
     - Execute gate: Worker M3 -> 2 Reviewers, 2 Challengers, 1 Forensic Auditor.
  2. **Milestone 4 (Liquid Glass UI & Setup Experience)**:
     - Implement `GeminiShiftImportScreen.kt` in `ui/screens/`.
     - Integrated into `SettingsScreen.kt` via `SettingsSubScreen.IMPORT_SHIFTS` (hides bottom dock in `MainScreen.kt`, clean back navigation).
     - File picker (image & PDF), preview thumbnail via `DocumentProcessor.createThumbnail`, masked API key field, user name field, elastic loading state, extracted shift/squad summary card, 2-state star toggle, single back button top-left.
     - Notifications exclusively via `AnimatedStatusPopup` at `BottomCenter` (zero layout shift).
     - Execute gate: Worker M4 -> 2 Reviewers, 2 Challengers, 1 Forensic Auditor.
  3. **Milestone 5 (Final Verification & Audit)**:
     - Run full E2E test suite (100% green).
     - Run `.\gradlew.bat assembleDebug` (APK verified).
     - Forensic Integrity Audit (CLEAN).
     - Notify Sentinel (`04303cbe-24da-46c4-a6c6-54573eee4768`) of project completion.

## Files to Read Immediately
- `c:\Users\marco\Antigravity Progetti\Calediario Turni\.agents\teamwork\orchestrator_1\handoff.md`
- `c:\Users\marco\Antigravity Progetti\Calediario Turni\.agents\teamwork\orchestrator_1\BRIEFING.md`
- `c:\Users\marco\Antigravity Progetti\Calediario Turni\.agents\teamwork\ORIGINAL_REQUEST.md`
- `c:\Users\marco\Antigravity Progetti\Calediario Turni\PROJECT.md`
- `c:\Users\marco\Antigravity Progetti\Calediario Turni\TEST_INFRA.md`
- `c:\Users\marco\Antigravity Progetti\Calediario Turni\TEST_READY.md`
