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
- [ ] Expand accessibility, restoration and adaptive-layout coverage.

### P3 — Workspace and editor
- [x] SAF folder picker and persistent URI read permission; scoped directory browse/filter/refresh.
- [x] Bounded text/code preview (256 KiB).
- [x] Create file/folder, rename/delete and move via `DocumentsContract`.
- [x] Explicit-save text editor for supported files, unsaved-state warning and a 256 KiB read/write cap.
- [x] Add case-insensitive find-next, replace-match and replace-all for the in-memory editor draft.
- [x] Add debounced app-private draft recovery, validated against the saved file baseline and excluded from Android backup.
- [x] Add an eight-tab limit, switchable editor tabs, per-tab dirty markers and draft/selection retention.
- [ ] Add workspace-file autosave and shared diff.
- [x] Add bounded per-file local snapshots and snapshot-to-draft text diff/restore-to-draft.
- [x] Add measured editor line/byte metrics and a bounded read-only brace-folding view (12,000-line / 256 KiB analysis cap).\n- [ ] Add Web Live Preview.
- **Exit:** CRUD, save/recovery, scope and performance tests pass. Workspace-file autosave and shared diff remain outstanding; the folding view is read-only rather than an editable syntax-aware fold map.

### P4 — Settings and cloud AI
- [ ] Provider abstraction, routing, secure credentials, connection tests and attachments.
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
