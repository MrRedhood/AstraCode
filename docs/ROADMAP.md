# AstraCode Implementation Roadmap

**Status:** Incremental plan; planned capabilities are not assumed to exist.
**Target:** Reliable, mobile-first Android coding environment. **Branch:** `main` only. **License:** Apache-2.0.

## Principles
- Preserve functionality; make incremental changes and avoid broad rewrites.
- Mobile-first, including lower-end devices. Cloud AI only; no bundled Android SDK.
- Privileged file, terminal, Git and remote operations use service/policy boundaries.
- Update `IMPLEMENTATION.md`, Help & Guide and architecture docs for changes.
- Do not claim CI/UI success without the exact workflow result.

## Capability inventory
Workspace/editor: SAF, CRUD, editor/tabs, autosave, syntax highlighting, search/replace, folding, snapshots, diffs, recovery and Web Live Preview. AI: cloud providers, model routing, attachments, plans, tool orchestration, approvals, cancellation and verification. Security: scoped capabilities, audit, secret redaction and recovery. Git/Build: remote actions, diffs, artifacts, reports and release readiness. Operations: scheduling, offline queue, approvals inbox, backup and activity history.

## Navigation and architecture
Primary destinations: Chat · Code · Git · Build · More. Phones use bottom navigation; wider screens use a navigation rail. Keep the initial one-module architecture, do I/O through repositories/adapters, and confine workspace operations to the persisted SAF grant.

## Phases
### P0 — Baseline and contracts
- [x] Record repository scope, workflows and target architecture.

### P1 — Compiling foundation
- [x] Add app entry point, navigation shell, unit/UI smoke tests and CI.

### P2 — Navigation and design system
- [x] Add searchable More index, adaptive navigation and in-app Help & Guide.
- [x] Expand accessibility semantics, saveable navigation restoration and adaptive-layout breakpoint coverage with unit/UI tests.

### P3 — Workspace and editor
- [x] SAF folder picker and persistent URI read permission; scoped directory browse/filter/refresh.
- [x] Bounded text/code preview (2 MiB).
- [x] Create file/folder, rename/delete and move via `DocumentsContract`.
- [x] Debounced workspace autosave for supported writable text files, conflict handling and a 2 MiB read/write cap.
- [x] Add case-insensitive find-next, replace-match and replace-all for the in-memory editor draft.
- [x] Add debounced app-private draft recovery, validated against the saved file baseline and excluded from Android backup.
- [x] Add an eight-tab limit, switchable editor tabs, per-tab dirty markers and draft/selection retention.
- [x] Add bounded per-file local snapshots and snapshot-to-draft text diff/restore-to-draft.
- [x] Add measured editor line/byte metrics and a bounded read-only brace-folding view (1,500,000-line / 2 MiB analysis cap).
- [x] Add debounced WebView live preview for HTML/HTM, CSS and JavaScript, with network/file access blocked.
- **Exit:** CRUD, debounced workspace autosave/conflict handling, shared bounded diff, recovery, scope and performance tests pass. The folding view remains read-only rather than an editable syntax-aware fold map.

### P4 — Settings and cloud AI
- [x] Add provider-neutral cloud request/result/capability contracts and explicit provider/model routing, with bounded request validation and no implicit provider fallback.
- [ ] Implement provider adapters, encrypted credential storage, connection tests and attachments.
- [ ] Observable plans/tool activity, approval gates, cancellation and verified completion.
- [ ] Consolidated settings source of truth and help topics.

### P5 — Git, builds and quality
- [ ] Git status/history/diff/conflicts, scoped GitHub actions, builds/artifacts/reports.

### P6 — Durable operations and storage
- [ ] Scheduling, offline queue, approvals inbox, backups, memory, activity history and Room migration tests.

### P7 — CI/CD and release
- [ ] Build, unit, lint and UI workflows; retain reports and first-attempt failures; isolate signing secrets.

### P8 — Polish
- [ ] Accessibility, first-run setup, empty/error states, guide and validated extensions.

## Definition of done
Touch-friendly and adaptive UI; real-operation evidence; safe permission scope and retries; exact latest relevant CI/UI green before claiming success; synced docs; direct commits to `main`.
