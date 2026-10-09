# AstraCode Architecture

**Status:** Target architecture for the bootstrap phase. Components listed here must not be treated as implemented until their source and tests exist.

## System boundaries

```text
Compose UI → ViewModel → Use case / policy → Repository interface → Adapter
                                                        ├─ Room
                                                        ├─ Storage Access Framework
                                                        ├─ Git / GitHub APIs
                                                        ├─ Cloud AI provider
                                                        └─ Build / CI services
```

UI collects intent and renders observable state. ViewModels coordinate use cases. Domain/policy code owns permissions, approvals, lifecycle rules and validation. Repository interfaces isolate persistence and external systems. Adapters own I/O and map provider/platform errors to stable application errors.

## Package direction

Keep the initial implementation in one Android module. Evolve toward `app`, `core/common`, `core/navigation`, `core/designsystem`, `core/operations`, `core/security`, `data/local`, `data/workspace`, `data/github`, `data/credentials`, and feature packages for chat, workspace, editor, Git, GitHub, build, terminal, activity, automation, security, diagnostics, settings and guide. Extract only when a boundary is proven by usage/tests; avoid mass moves.

## Non-negotiable contracts

1. SAF URIs and persisted grants define local workspace access. Never bypass workspace scopes with arbitrary filesystem paths.
2. No Composable performs privileged file/terminal/Git mutations or network requests directly.
3. Cloud AI only; do not require local model inference or ship an Android SDK inside the app.
4. Destructive or remote mutations are capability-checked, risk-assessed, approved when policy requires, audited and verified after execution.
5. AI completion requires execution evidence and verification, not only generated prose.
6. Cancellation propagates through coroutines and stops work at safe checkpoints.
7. Persisted operations must be recoverable after process death and retries must be idempotent.
8. Credentials are stored separately from ordinary preferences using platform-backed secure storage where feasible; redact secrets from logs.
9. One source of truth per preference; UI contexts may link to settings but must not duplicate state.
10. All user-facing behavior is documented in More → Help & Guide; every change is recorded in `IMPLEMENTATION.md`.

## Operation lifecycle

Use stable IDs and explicit states: Queued, Planning, Inspecting, AwaitingApproval, Executing, Verifying, Completed, Failed and Cancelled. Persist state transitions before reporting them. Keep concise user-facing action summaries and tool results; never expose private chain-of-thought. Record affected paths, approval decision/expiry, actual command/provider outcome, verification evidence and recovery status.

## Persistence

Use Room for structured local state and export schema JSON to version control. Every schema change needs a migration and upgrade test. Define retention policies for chat, audit and activity data. Avoid storing credentials in Room or logs. Treat SAF documents as external state: handle revoked grants, missing files, provider exceptions, partial writes and user cancellation.

## UI and navigation

The app currently has five top-level destinations: Chat, Code, Git, Build and More. Compact widths use bottom navigation; screens wider than 600 dp use a navigation rail. The More hub is searchable, grouped by category and has an empty-results message. Help & Guide is reachable in More; Back returns from nested More pages to the index, and primary destination/search state uses saveable Compose state. The Code destination now uses Android's Storage Access Framework folder picker, persists the granted tree URI and read permission, browses document IDs under that tree, supports current-folder filtering/refresh, and offers bounded read-only text previews. Folder contents are queried using the selected tree URI and document ID; the app does not resolve paths outside the selected grant. Create/rename/move/delete, edit/save, tabs and recovery are still planned. Other listed tools/settings remain explicit placeholders until implemented and tested.

Keep one centralized navigation owner as routes become more complex. Keyboard visibility, text scaling, accessibility labels, loading/empty/error states and touch targets remain requirements, not polish-only work.

## Verification and failure model

Errors should be typed at service boundaries and mapped to actionable UI messages. Network calls need bounded timeouts and cancellation. Retry only operations known to be safe or protected by idempotency keys/preconditions. Remote writes must be read back or confirmed from authoritative responses. UI tests should assert observable behavior; CI must preserve first-attempt failures and reports.

## Current implementation status

The repository contains an Android bootstrap, the five primary destinations, custom local navigation icons, a PNG launcher resource, responsive navigation, searchable More index, Help & Guide, and initial SAF workspace selection/browsing with bounded read-only text preview. Editing/CRUD mutations, cloud AI, Git operations, builds and most settings remain unimplemented; track them in `docs/ROADMAP.md` and do not describe placeholders as functional.
