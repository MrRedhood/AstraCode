# AstraCode Architecture

**Status:** Target architecture, updated with implemented workspace foundation.

## System boundary
```text
Compose UI → ViewModel/use case → policy/service → repository → Android/provider adapter
```
UI renders state; repositories/adapters own I/O; policy owns validation and approvals.

## Package direction
Keep the one-module app until boundaries stabilize. Gradually introduce common, navigation, design system, security, local persistence, workspace and feature packages. Avoid mass moves.

## Contracts
1. Persisted SAF tree URI defines workspace scope. Never use arbitrary paths outside that grant.
2. Keep blocking document-provider operations off the main thread and map failures to actionable UI messages.
3. Cloud AI only; no bundled Android SDK.
4. Protected operations require capability checks, approval as appropriate, audit and verification.
5. Model output is not proof of completion; propagate cancellation and typed failures.
6. Protect credentials and keep them out of logs.
7. One preference source of truth.
8. Every user-facing change is documented in Help & Guide and `IMPLEMENTATION.md`.

## Workspace/editor
Code uses Android's system tree picker, storing the SAF tree URI and persistent read permission. Directory queries and all mutations use `DocumentsContract` with document IDs in the selected tree. Create, rename, delete and move errors are surfaced if the provider doesn't support an operation. Moving a directory into itself or a descendant is blocked. Supported text/code files use a manual-save editor capped at 256 KiB; read-only provider documents and truncated files remain read-only. Case-insensitive plain-text find/replace operates on the in-memory draft. Dirty writable drafts are debounced to an app-private, no-backup local file using atomic writes; a baseline SHA-256 must match before a draft can be restored, and stale/corrupt drafts are discarded. Saving or explicitly discarding removes the recovery copy. Up to eight editor tabs can be switched without losing in-memory draft text or selection; an asterisk marks dirty tabs. Closing a dirty tab offers to persist a recoverable draft or discard it. This recovery mechanism does not write changes to the SAF workspace file; saving remains explicit. Workspace-file autosave and shared diff remain planned. A read-only folding view computes UTF-8 byte count, line count and longest-line metrics only when opened, and scans supported brace-based languages while ignoring common strings and comments. Analysis is capped at 256 KiB and 12,000 lines; files outside those bounds show metrics but do not produce a line listing or fold regions. This is intentionally a separate inspection view: returning to Edit source restores the normal source buffer and avoids mapping edits through hidden text. Web Live Preview renders the current draft for HTML/HTM, CSS and JavaScript after a short debounce. The preview uses a restrictive CSP and WebView settings that disable network loads, file/content access, external navigation, DOM storage and multi-window creation. CSS and JavaScript are wrapped in a small sample page; HTML remains standalone. Sibling files and external resources are intentionally not loaded yet. Preview source is capped at 256 KiB and preview remains separate from explicit workspace saves. Local snapshots are app-private, capped at ten per file and 256 KiB each; a bounded LCS diff is used for small inputs and a compact summary is used when the matrix would be too large. Snapshot restore changes only the editor draft, never writes to SAF implicitly.

## UI
Chat, Code, Git, Build and More are the top-level destinations. Compact screens use bottom navigation, wider screens a navigation rail. More is searchable and includes Help & Guide. Incomplete sections remain labelled placeholders.

## Current state
The repo has the bootstrap shell, custom icons, exact PNG launcher, responsive navigation, searchable More and Help & Guide, plus initial SAF browser/CRUD/manual-save editor with debounced local draft recovery and bounded editor tabs. Workspace-file autosave, shared Git diff, AI, Git operations, builds and most settings remain planned and tracked in `docs/ROADMAP.md`. The read-only code folding/inspection view and a sandboxed, debounced Web Live Preview for standalone HTML/CSS/JavaScript are implemented.
