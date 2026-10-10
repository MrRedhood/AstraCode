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
- [x] Replace the foundation-only start page with a dark-first AstraCode dashboard and responsive Home · Projects · AI · Terminal · More navigation.
- [x] Add focused Create Project, Build & Run, AI Execution and Settings screens while keeping unavailable generators/build/task runners clearly non-operational.
- [x] Add the safe built-in terminal commands `help`, `pwd`, `ls` and `clear`; unrestricted shell commands remain disabled.

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
- [x] Catalog 16 cloud providers with protocol, default endpoint, credential label and official documentation metadata, including a custom OpenAI-compatible endpoint.
- [x] Implement injected-credential HTTPS adapters for OpenAI-compatible Chat Completions, OpenAI Responses, Gemini generateContent, Anthropic Messages and Cohere Chat v2, with typed failures and bounded I/O.
- [x] Add Android Keystore AES-GCM API-key vault, backup exclusion, provider/model/endpoint settings and user-triggered connection test UI.
- [x] Discover available models for the documented provider APIs and connect configured provider/model settings to a cancellable Chat flow with bounded local session history and typed errors.
- [x] Add bounded local SQLite chat sessions with restore/new/delete controls, schema tests and Android-backup exclusion.
- [x] Add any-MIME chat attachments (up to 10 files per message, 25 MiB per file and 100 MiB combined per provider request), app-private streamed storage with a 1 GiB quota, SQLite metadata persistence/restore, backup exclusion and provider-native multimodal payloads for supported image/audio/video/document types.
- [x] Add approval-gated read-only AI workspace tools for directory listing and small text/code reads, scoped to the selected SAF tree and returning direct execution evidence.
- [x] Add approval-gated creation of new text/code files beneath an existing selected-workspace directory, with a 15 MiB UTF-8 cap, existing-path refusal, read-back byte verification and SHA-256 evidence.
- [x] Add an audited approval-gated AI workspace move tool for files/folders, with destination conflict refusal, self/descendant move protection and post-move verification.
- [ ] Add controlled overwrite/delete/build tools, audited capability approvals and end-to-end task verification.
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
