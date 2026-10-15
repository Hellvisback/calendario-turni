# DISPATCH — E2E Testing Orchestrator

## Identity & Role
- Archetype: teamwork_preview_orchestrator
- Role: E2E Testing Track Orchestrator
- Working Directory: c:\Users\marco\Antigravity Progetti\Calediario Turni\.agents\teamwork\sub_orch_e2e_testing
- Parent Conversation ID: 90066a2c-0ee0-47dc-9cce-f552c7e66ada

## Objective
Design, implement, and verify the comprehensive opaque-box E2E and Unit test suite covering all features F1-F12 across Tiers 1-4 as defined in `TEST_INFRA.md` and `PROJECT.md`.
Upon full completion of the test suite and verification that the test runner executes properly, publish `TEST_READY.md` at the project root (`c:\Users\marco\Antigravity Progetti\Calediario Turni\TEST_READY.md`).

## Files to Read
- `c:\Users\marco\Antigravity Progetti\Calediario Turni\.agents\teamwork\ORIGINAL_REQUEST.md`
- `c:\Users\marco\Antigravity Progetti\Calediario Turni\PROJECT.md`
- `c:\Users\marco\Antigravity Progetti\Calediario Turni\TEST_INFRA.md`

## Workflow & Constraints
- Follow the sub-orchestrator procedure: Assess -> Decompose or run iteration loop (Explorer -> Worker/Test Writer -> Reviewer -> Challenger -> Auditor).
- Tests must be requirement-driven and test functionality, edge cases, error conditions (429, 400, 403, bad PDF, invalid JSON), user matching, and shift mapping.
- Workers can add test files in `android-app/app/src/test/java/com/turni/calendario/` and run `.\gradlew.bat test`.
- Do NOT cheat or create tautological tests (assert(true)).
- When complete, publish `TEST_READY.md` and send a message back to your parent.
