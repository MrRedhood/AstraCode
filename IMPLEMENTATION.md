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
