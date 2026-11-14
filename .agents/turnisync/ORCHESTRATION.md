# TurniSync orchestration contracts

The user explicitly asked this cloud chat to continue the original multi-agent workflow. The main agent remains the orchestrator and owns the final result. Read AGENTS.md, docs/CLOUD_HANDOFF.md, docs/CHAT_CONTINUITY.md and STATE.json before dispatching work.

## Runtime adaptation

Use the available collaboration tools, including `collaboration.spawn_agent`, follow-up messages and result collection. The historical `Agent(subagent_type: "ecc:...")` syntax belongs to another harness and is not an installed capability here. Historical `pro` and `flash` tags are retained in the original files; they are not valid model IDs or guarantees about the current runtime. Use the current supported model selection under the session's instructions. Never claim an old agent process or its hidden memory was resumed.

Keep the 68 original agent definitions, skills and rules available. Load task-relevant profiles instead of assigning all agents to every task. Templates containing web-specific commands must be adapted to the native Android app. Archived teamwork IDs, running statuses, timers, dispatch-only gates and unrelated tasks are not current work.

## Core team from the original conversation

| Role | Current assignment | Sources |
| --- | --- | --- |
| Master orchestrator | Talk with the user in Italian, identify intent, plan, assign ownership, integrate evidence and deliver the completed result | AGENTS.md; current cloud handoff |
| UI/UX Motion & Expressive | Implement the requested Compose layout/motion change, preserve final palette and geometry, provide before/after evidence where possible | docs/CHAT_CONTINUITY.md; native components; kotlin-reviewer and a11y-architect profiles |
| Code Quality & Architecture Reviewer | Independently review the actual diff for Kotlin, state/lifecycle, geometry, data integrity and regressions; report actionable findings with evidence | ../agents/code-reviewer.md; ../agents/kotlin-reviewer.md; ../agents/architect.md |
| Build & Diagnostics | Run repository scripts, diagnose minimal build failures, summarize actual tests/lint and distinguish physical UI checks from JVM checks | ../agents/kotlin-build-resolver.md; ../agents/java-build-resolver.md; current build scripts |
| DevOps | Preserve working tree, prepare reviewable commits/PRs and track provenance; APK release remains a separate authorized operation using release.ps1 | AGENTS.md; docs/CLOUD_HANDOFF.md |

Supporting roles: planner/architect for complex dependencies, performance specialist for measurements, security reviewer for imports/storage/auth/backups, documentation specialist for state and decision records. These support the core team rather than replacing it.

## Dispatch template

Every assignment must state:

1. Concrete goal and acceptance criteria from the current user request.
2. Allowed files, ownership, and read-only versus implementation scope.
3. Relevant current sources and final decisions to preserve.
4. Required evidence and feasible verification commands.
5. Deliverable: changes or findings, rationale, tests actually run, unresolved limits.
6. Whether further delegation is allowed; default to no additional delegation for a bounded subtask.

Parallelize independent analysis or disjoint files. Keep dependent edits/builds sequential and avoid multiple agents changing the same files. For a small task, combine roles without pretending extra agents were launched. For substantial UI changes, use a UI implementer and an independent reviewer; run Android checks after integrating changes. Do not run competing Gradle builds against the same build directory.

## Completion and memory

Collect every delegated result before ending the turn. Resolve findings, run checks appropriate to the actual change and report limitations honestly. Do not claim real-device rendering, alarms or OAuth behavior from a JVM build. Do not generate a release merely to perform verification.

Preserve the user's current objective across steering messages. Update STATE.json when scope/status changes and append material decisions with their reasons and evidence to HANDOVER_NOTES.md. Keep historical proofs separate from current checks. Never copy personal data, secrets, raw chat transcripts or local credentials into the public repository.
