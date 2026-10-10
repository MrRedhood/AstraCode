# Implementation Log

## 2026-10-09 — Repository license and bootstrap
- Confirmed Apache License 2.0 at repository root and added README license/branding guidance.
- Added the Kotlin/Compose Android shell, five destinations, build/test configuration and CI/UI/release-validation workflows. Destinations were labelled scaffolding until implemented.
- Kept release-signing secrets in runner environment/temp storage, not source control.

## 2026-10-09 — CI/UI diagnostics and branding
- Fixed invalid Compose `weight` import; added AndroidX test runner/rules and adjusted the emulator workflow.
- Added custom local Compose navigation icons with cyan, blue, amber-gold and violet accents.
- Added exact user-provided PNG launcher asset and manifest references. Renamed the old vector resource to avoid duplicate Android resource names.
- Added responsive navigation, searchable More hub and Help & Guide; updated architecture/guide docs. A previous UI run's unsupported assertion was removed; current run evidence determines status.

## 2026-10-09 — SAF workspace browsing
- Added folder picker, persisted tree URI/read permission, folder listing/navigation, filter, refresh and bounded 256 KiB text/code preview.
- Added file type policy and tests, and recoverable UI errors for revoked access/provider failures.

## 2026-10-09 — Scoped document actions and manual-save editor
- Added SAF-backed create file/folder, rename, delete and move through `DocumentsContract`; actions remain scoped to the selected tree grant.
- Move flow selects an item, navigates to the destination and confirms Move here. Same-parent no-op and moves into a directory's own descendant are blocked.
- Added an editor for known small text/code files, an explicit Save action, dirty-state indicator and discard confirmation. Truncated/unsupported/read-only files stay read-only. Reads/writes are capped at 256 KiB; no autosave/tabs/recovery claim.
- Added unit tests for path-safe names and new-file MIME mapping.
- Updated `docs/ROADMAP.md`, `docs/ARCHITECTURE.md` and `docs/HELP_AND_GUIDE.md`.
- CI follow-up: the first compile caught a missing `rememberSaveable` import and a malformed AlertDialog confirmation slot. Both are corrected in the next commit; verify the rerun before claiming green status.
- Validation: changes committed directly to `main`; CI and UI smoke must complete on the exact final commit before success is claimed.


## 2026-10-09 — Editor find and replace
- Added case-insensitive plain-text find-next with wraparound, replace-match and replace-all in the current unsaved draft.
- Editor selection follows find results and replacement caret position; replace-all reports its count and does not save implicitly.
- Added isolated unit tests for wraparound, matching/counting, single replacement, replace-all and no-match behavior.
- Updated the roadmap, architecture notes and Help & Guide. Autosave, tabs, draft recovery, folding and shared diff remain outstanding.
- Validation: Android CI and UI Smoke are triggered by this commit; only their final results establish workflow status.


## 2026-10-09 — Align in-app editor guide
- Updated More → Help & Guide to describe the shipped SAF workspace operations and explicit-save editor limits.
- Added practical find/replace instructions, including case-insensitive matching, wraparound, replace-all behavior and the manual-save boundary.


## 2026-10-10 — Editor local draft recovery and UI Smoke repair
- Added debounced recovery copies for dirty, writable editor drafts using app-private storage excluded from Android Auto Backup; workspace files remain explicit-save only.
- Recovery records use bounded UTF-8 payloads, versioned validation, atomic writes and a SHA-256 baseline fingerprint. A changed on-disk baseline invalidates an old draft instead of silently applying it; saving or explicitly discarding removes the recovery copy.
- Added JVM codec tests for Unicode round trips, stable/content-sensitive fingerprints, oversize rejection and corrupt/truncated/trailing records.
- Fixed a stale UI Smoke assertion to match the current More screen description instead of removed copy.
- Updated the roadmap, architecture, in-app Help & Guide, and this implementation log.
- Validation: Android CI and Android UI Smoke were triggered on the resulting main commit. Results must be checked for that exact commit before claiming success.
- CI follow-up on 2026-10-10: corrected a syntax error in the replace-all status conditional found by the first Android CI run. Re-running Android CI and UI Smoke on the corrected main head.


## 2026-10-10 — Bounded multi-file editor tabs
- Added a horizontal tab strip with an eight-tab cap for mobile memory safety, with per-tab unsaved-change markers.
- Switched between files while retaining each tab's current draft and selection in memory; “Back to files” leaves tabs open.
- Closing a dirty tab explicitly offers to keep a validated local recovery copy or discard the draft; file writes remain manual through Save file.
- Renamed/deleted files update or remove matching tabs and recovery records.
- Added JVM tests for tab deduplication, metadata refresh, capacity limits and safe close ordering.
- Updated the roadmap, architecture, in-app Help & Guide and this implementation log.
- Validation: Android CI and UI Smoke are triggered for this commit; only exact workflow results determine success.
- Follow-up validation on 2026-10-10: fixed a missing brace in the tab-close dialog caught by Android CI and preserved existing recovery copies when closing a tab whose supported text file could not be loaded.


## 2026-10-10 — Per-file snapshots and bounded text diff
- Added app-private per-file snapshots (up to ten per file, 256 KiB each), versioned record validation, atomic writes and cleanup when a workspace document is deleted.
- Added a snapshots manager to create, compare, load a snapshot into the draft after confirmation, and delete snapshots. None of these operations writes to the SAF document implicitly.
- Added a bounded line-diff engine: exact LCS for small inputs, line/output caps, and compact approximate summaries for inputs too large for the LCS matrix.
- Added JVM tests for snapshot record validation and diff output/large-input behavior.
- Fixed the tab Close button callback invocation identified in the preceding CI result.
- Updated roadmap, architecture, in-app Help & Guide and this implementation log.
- Validation: Android CI and UI Smoke are triggered on the resulting main commit; only exact workflow results determine status.


## 2026-10-10 — Bounded code folding and editor text metrics
- Added an on-demand, read-only folding/inspection view for supported brace-based languages. The normal editor buffer remains untouched, so caret mapping and manual-save behavior are preserved.
- Added UTF-8 byte count, logical line count and longest-line metrics, computed only when opening the inspection view rather than on each keystroke.
- Added lexical handling for common single/double/triple-double quoted strings, template strings, line comments and block comments so their braces do not create false folding regions.
- Capped fold analysis at 256 KiB and 12,000 lines; oversized/dense files report metrics and a bounded-view message instead of allocating a large line list.
- Added JVM coverage for metrics, brace detection, comments/strings, unsupported extensions, line caps and folded-row behavior.
- Updated roadmap, architecture and Help & Guide. This is deliberately a read-only inspection view, not an editable syntax-aware fold map.
- Validation: the commit triggers Android CI and UI Smoke. Their results must be checked on this exact commit before claiming success.


## 2026-10-10 — Folding inspection robustness follow-up
- Corrected the fold-view line delimiter and changed UTF-8 byte measurement to count bytes without allocating a second full copy of the draft.
- This keeps the inspection path bounded on mobile even when the current in-memory draft exceeds the save limit.


## 2026-10-10 — Sandboxed Web Live Preview
- Added a debounced WebView preview for HTML/HTM, CSS and JavaScript source drafts. CSS/JS use a small sample page; HTML remains a standalone page. The preview updates after a short pause in typing and remains separate from workspace saving.
- Applied a restrictive Content Security Policy and WebView settings that block network loads, file/content access, external navigation, DOM storage, mixed content, forms and multi-window behavior. No JavaScript bridge is exposed.
- Capped preview source at 256 KiB; external and sibling assets are deliberately not loaded in this first slice.
- Added JVM tests for supported extensions, CSP injection, HTML fragments, CSS/JS wrappers and the preview size cap.
- Updated Help & Guide, architecture and roadmap. Corrected the malformed literal newline in the prior roadmap entry.
- Validation: Android CI and Android UI Smoke are triggered on this commit; check exact run results before claiming success.


## 2026-10-10 — Web preview interaction and resource handling
- Kept the editor visible above the live preview so typing can update the rendered result without losing source access.
- Preview refresh uses a 300 ms debounce; added a Hide live preview control and allowed inline data/blob assets while intercepting other resource requests.
- Changed preview-size checking to count UTF-8 bytes without first allocating a second encoded copy of the full draft.

- Follow-up: added Web Live Preview and folding instructions to the runtime More → Help & Guide screen, not only the Markdown documentation.


## 2026-10-10 — Raise editor content caps and line indexing
- Expanded workspace text reads/writes, local recovery drafts, per-file snapshots and Web Live Preview from 256 KiB to 2 MiB.
- Expanded folding inspection to 1,500,000 lines with a 2 MiB analysis bound. Replaced full line splitting and row materialization with an IntArray line index and virtualized Compose rows.
- Bounded folding metadata to 50,000 regions and 20,000 nesting levels; clipped individual display rows at 4,000 characters to limit rendering work.
- Reworked oversized diff generation to compare lines with forward/backward streaming cursors and emit only a bounded prefix/suffix sample instead of million-entry split lists.
- Updated current docs, UI messaging, runtime Help & Guide and tests for the new content limits.
- Validation: Android CI and Android UI Smoke are triggered on the latest commit; only the exact run results determine success.

- Documentation correction: clarify that exceeding the 1,500,000-line folding limit suppresses the folding line listing; only files above the 2 MiB editor byte cap are read-only.


## 2026-10-10 — Keep UI smoke resilient to longer Help & Guide
- Updated the Help & Guide instrumentation test to scroll to the “Build verification” section before asserting visibility. The guide expanded as live preview and large-file limits were documented, so the previous on-screen-only assertion was no longer reliable.
- Previous exact-head Android CI passed; Android UI Smoke failed this visibility assertion. A fresh workflow run is triggered by this test correction.
