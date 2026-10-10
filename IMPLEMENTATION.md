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
