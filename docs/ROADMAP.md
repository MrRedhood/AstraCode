# AstraCode Implementation Roadmap

**Status:** Bootstrap plan — implementation has not yet been validated by Android builds or device tests.  
**Target:** A reliable, mobile-first Android coding environment.  
**Branch policy:** Work directly on `main`; do not create feature branches.  
**License:** Apache-2.0.

## Product principles

- Preserve working behavior; no feature removals or broad rewrites without regression coverage.
- Mobile-first UX, including lower-end Android devices; no local AI inference and no bundled Android SDK.
- Cloud AI with provider/model routing, one user-facing AI orchestrator, observable progress, and security approvals.
- Privileged workspace, terminal, Git and remote operations pass through explicit service and policy boundaries.
- Update `IMPLEMENTATION.md` for every repository change and keep More → Help & Guide aligned with user-facing behavior.
- Never report CI, UI tests, builds or release artifacts as successful without evidence from the relevant latest run.

## Baseline and scope

AstraCode currently starts as a documentation/licensing repository. The DevForge capabilities described in the source brief are product requirements and preservation targets, **not existing AstraCode implementations**. Establish a compiling Android shell and tests before claiming feature parity. Reuse concepts, not unverified code or assumptions.

## Feature inventory and target behavior

The following are target capabilities to implement in stages, not a claim that they exist today:

- **Workspace/editor:** Storage Access Framework (SAF), workspace selection, file/folder operations, tabs, autosave, syntax highlighting, search/replace, folding, snapshots, diffs, recovery and Web Live Preview. Keep a tested large-file fast path (initial target threshold: 64 KiB or 2,000 lines; tune from measurements).
- **AI and execution:** cloud providers, model routing, attachments, scoped workspace context, tool orchestration, generated plans, verification, execution history, reusable skills and provider settings. Show concise action summaries and observable evidence, never private chain-of-thought.
- **Security:** capability permissions, risk assessment, expiring approvals, protected-operation audit records, credential protection, path scopes, secret redaction, snapshots and recovery.
- **Git/GitHub:** local repository status/history/diffs/conflicts, repository creation, remote mutations, Actions logs and publishing; validate remote outcomes and handle retries without duplicating destructive effects.
- **Build/quality:** debug APK, release APK/AAB, build monitoring, artifacts, lint/test/dependency reports, CI/CD status, diagnostics, release readiness and project health.
- **Operations/recovery:** durable AI tasks, scheduling, operations inbox, approvals, offline action queue, backups, project memory, notifications and activity history.

## Proposed phone navigation

Five primary destinations: **Chat · Code · Git · Build · More**. This is a target design, not existing UI.

- Chat: conversation, attachments, execution plan, compact tool activity, inline approvals, shared line-level diff, verification and cancellation/retry states.
- Code: workspace browser and full-screen editor; contextual sheets for file operations and editor tools.
- Git: status, history, diff review, conflict resolution and remote actions.
- Build: build targets, live logs, artifacts and reports.
- More: searchable, grouped index for activity, development tools, quality, delivery, AI, integrations, workspace, settings, guide and about.

On tablets/wide screens, use an adaptive navigation rail, tree/editor split, larger review panes and optional side-by-side chat. No hover-only controls. Keep keyboard/composer, touch targets, text scaling, Back behavior, loading/error/empty states and accessibility intentional.

## Target architecture

Start with one Android app module; do not perform a mass package move. Gradually make boundaries explicit:

- `app/`: application, activity, dependency container and app shell.
- `core/common`: IDs, result/error types and shared contracts.
- `core/navigation`: typed routes, navigation host and restoration.
- `core/designsystem`: theme, typography, spacing and reusable components.
- `core/operations`: lifecycle/status contracts.
- `core/security`: shared capability and policy primitives.
- `data/local`: Room entities, DAOs, migrations and schema exports.
- `data/workspace`: SAF adapters and filesystem operations.
- `data/github`, `data/credentials`: remote adapters and secure storage.
- `feature/<name>/presentation|domain|data`: feature-specific screens, use cases and adapters.

Composable functions render state; they must not perform remote requests or privileged mutations directly. ViewModels coordinate use cases and expose observable state. Repositories/adapters own Room, SAF, Git, GitHub, cloud AI and build integrations. Standardize cancellation, dispatchers, error mapping and lifecycle ownership.

## Settings

Create one searchable settings index with a single source of truth for each preference. Organize Appearance & Accessibility; Editor; AI & Models; AI Tool Permissions; Git & GitHub; Build & CI; Terminal; Automation; Privacy & Storage; Security & Notifications; About & Help. Preserve specialized configuration surfaces but avoid duplicate preference stores. Validate numeric/URL inputs, explain security-sensitive options, separate local preferences from credentials, provide explicit credential removal, and document each new setting in Help & Guide.

## Persistence and recovery

- Export and track Room schemas from the first database-backed feature.
- Add migration tests for every supported upgrade path before schema changes.
- Test process death, interrupted writes, durable tasks, editor drafts, approvals and operation history.
- Make recovery and offline queue transitions idempotent and inspectable.
- Do not adopt a database rewrite without validating the existing data contract.

## AI lifecycle and change verification

Enforce a durable state machine such as `Queued → Planning → Inspecting → Awaiting approval → Executing → Verifying → Completed / Failed / Cancelled`. Record task/workspace identity, affected paths, approvals, action outcomes and verification evidence. A model's claim is not proof of completion. Support cancellation at safe checkpoints and report unfinished work. Retries must not duplicate destructive side effects. Internal agents, if used, remain AI-managed and are not exposed as user-controlled workers.

Use one diff renderer for AI review and Git. Cover line alignment, multiple hunks, new/empty files, long lines, rename/move, whitespace and newline cases, and large-file behavior.

## Framework decisions

| Technology | Decision |
|---|---|
| Kotlin, Coroutines | Use consistent suspend APIs, cancellation and dispatcher boundaries. |
| Jetpack Compose, Material 3 | Primary UI; reusable components and adaptive layouts. |
| Room + KSP | Local structured persistence with exported schemas and migration tests. |
| WorkManager | Durable scheduled/background work where appropriate. |
| Navigation Compose | Use one centralized navigation owner and typed routes. |
| JGit / GitHub APIs / Actions | Use behind adapters with explicit failure and consistency handling. |
| Android Keystore-backed credentials | Preserve secure credential handling and test recovery/removal. |
| Existing test stack | Expand unit/instrumentation coverage before adding another framework. |

Retain a consistent, supported Android toolchain and JDK; record the actual selected versions in build files and architecture docs. Do not copy DevForge version numbers blindly.

## Phased roadmap and exit gates

### P0 — Baseline and contracts
- [ ] Inventory repository files, dependencies, entry points, licenses and workflows.
- [ ] Record current workflow results and define critical contracts/tests.
- [ ] Keep this plan, architecture, guide and implementation log synchronized.
- **Exit:** baseline documented; no unverified claims about existing functionality.

### P1 — Compiling foundation
- [ ] Add Android Gradle project, application entry point, theme and minimal navigation shell.
- [ ] Add lightweight unit/UI smoke tests and CI with no release secrets required.
- [ ] Establish dependency boundaries and error/state conventions.
- **Exit:** debug build and smoke tests pass on the exact commit.

### P2 — Navigation and design system
- [ ] Implement Chat/Code/Git/Build/More shell and typed navigation.
- [ ] Add searchable More index, useful empty states and adaptive phone/tablet layouts.
- [ ] Add Help & Guide entry point and document the new navigation.
- **Exit:** Back/restoration, accessibility, keyboard and layout tests pass.

### P3 — Workspace and editor
- [ ] Implement SAF selection, scoped file operations, editor, tabs, autosave and recovery.
- [ ] Add search/replace, folding, snapshots and shared diff primitives.
- [ ] Add measured large-file path and Web Live Preview with relative resources/errors.
- **Exit:** CRUD, save/recovery, security scope and performance tests pass.

### P4 — Settings and cloud AI
- [ ] Add provider abstraction, routing, credentials, connection tests and attachment handling.
- [ ] Add observable plans/tool activity, approval gates, cancellation and verified completion.
- [ ] Consolidate settings source of truth and help topics.
- **Exit:** provider failures, cancellation, protected tools and task recovery are tested.

### P5 — Git, builds and quality
- [ ] Add Git status/history/diff/conflict flows and scoped GitHub remote actions.
- [ ] Add build dispatch/monitoring, artifact handling, reports and diagnostics.
- [ ] Verify signatures and artifact integrity before release readiness is reported.
- **Exit:** failures/conflicts/retries are recoverable and remote mutations are verified.

### P6 — Durable operations and storage
- [ ] Add scheduling, offline queue, approvals inbox, backups, memory and activity history.
- [ ] Export Room schemas and test all supported migrations/process-death paths.
- **Exit:** durable work resumes safely and destructive retries are idempotent.

### P7 — CI/CD and release readiness
- [ ] Run build, unit tests, lint and Android UI tests on pull requests and main pushes.
- [ ] Preserve test reports/logs and expose first-attempt emulator failures; do not hide flakes with retries.
- [ ] Keep signing secrets out of pull-request jobs; validate signed release artifacts separately.
- **Exit:** relevant latest workflows pass on the final intended commit; artifacts and reports are verified.

### P8 — Polish and expansion
- [ ] Improve first-run setup, accessibility, empty/error states and guide content.
- [ ] Add features only when they solve validated needs and fit existing boundaries.
- **Exit:** no material regression, documentation complete, usability improvements demonstrated.

## Test matrix

Prioritize navigation/Back/restoration; workspace selection and CRUD; save/dirty state/draft recovery; chat attachments/tool execution/cancellation; approval expiry and protected operations; Git diffs/conflicts/remote mutations; build dispatch/logs/artifact download; settings validation/persistence; process death/offline queue; Room migrations; large-file typing/scrolling; accessibility and phone/tablet layouts.

## Definition of done

- Architecture: one owner for navigation and each preference; privileged operations pass through policy/service boundaries.
- UI: touch-friendly, scalable, keyboard-safe, adaptive, accessible and complete across loading/empty/error states.
- Reliability: completion tied to observable results; approvals remain enforced; state survives interruption; retries are safe.
- Tests: relevant unit/instrumentation tests pass and actual latest CI/UI workflows are green before saying so.
- Docs: `IMPLEMENTATION.md`, `docs/ARCHITECTURE.md`, this roadmap and More → Help & Guide stay aligned.
- Performance: bounded background work, responsive scrolling/typing and no local AI or bundled Android SDK requirement.
- Repository discipline: commit directly to `main`; make incremental changes; no branch creation, silent setting resets or feature removals.
