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
Code uses Android's system tree picker, storing the SAF tree URI and persistent read permission. Directory queries and all mutations use `DocumentsContract` with document IDs in the selected tree. Create, rename, delete and move errors are surfaced if the provider doesn't support an operation. Moving a directory into itself or a descendant is blocked. Supported text/code files use a debounced autosave editor capped at 2 MiB; read-only provider documents and truncated files remain read-only. Case-insensitive plain-text find/replace operates on the in-memory draft. Dirty writable drafts are debounced to an app-private, no-backup local file using atomic writes; a baseline SHA-256 must match before a draft can be restored, and stale/corrupt drafts are discarded. Saving or explicitly discarding removes the recovery copy. Up to eight editor tabs can be switched without losing in-memory draft text or selection; an asterisk marks dirty tabs. Closing a dirty tab offers to persist a recoverable draft or discard it. Recovery copies remain app-private, while editable workspace drafts now auto-save to the SAF document after a 900 ms pause. Before each write AstraCode re-reads the stored file and compares it with the editor's last saved baseline; a detected external change pauses autosave rather than silently overwriting it. The user can compare stored content with the current draft, reload storage, or explicitly confirm an overwrite. This check reduces accidental conflicts but cannot provide an atomic compare-and-swap guarantee across arbitrary document providers. A single bounded diff engine/view is reused for workspace-vs-draft and local-snapshot-vs-draft comparisons. A read-only folding view computes UTF-8 byte count, line count and longest-line metrics only when opened, and scans supported brace-based languages while ignoring common strings and comments. Analysis is capped at 2 MiB and 1,500,000 lines; files outside those bounds show metrics but do not produce a line listing or fold regions. The view uses an IntArray line-offset index and lazy Compose rows instead of materializing each line and row. Fold-region storage is capped at 50,000 regions, and nesting tracking at 20,000 levels; reaching either safety limit preserves the line listing while stopping further fold detection. Lines longer than 4,000 characters are clipped in the inspection list. This is intentionally a separate inspection view: returning to Edit source restores the normal source buffer and avoids mapping edits through hidden text. Web Live Preview renders the current draft for HTML/HTM, CSS and JavaScript after a short debounce. The preview uses a restrictive CSP and WebView settings that disable network loads, file/content access, external navigation, DOM storage and multi-window creation. CSS and JavaScript are wrapped in a small sample page; HTML remains standalone. Sibling files and external resources are intentionally not loaded yet. Preview source is capped at 2 MiB and appears below the still-editable source field, updating after a 300 ms debounce; preview remains separate from explicit workspace saves. Local snapshots are app-private, capped at ten per file and 2 MiB each; a bounded LCS diff is used for small inputs and a compact summary is used when the matrix would be too large. Snapshot restore changes only the editor draft, never writes to SAF implicitly.

## UI
Chat, Code, Git, Build and More are the top-level destinations. A tested 600 dp width policy selects bottom navigation below the breakpoint and a navigation rail at/above it. Destination labels remain available to accessibility services; the custom navigation glyph is decorative where the visible label already names the destination. Primary selection, More search query and the selected More subsection use saveable state for activity recreation. The UI smoke suite verifies More search/selection restoration after recreation. More is searchable and includes Help & Guide. Incomplete sections remain labelled placeholders.

## Current state
The repo has the bootstrap shell, custom icons, exact PNG launcher, responsive navigation, searchable More and Help & Guide, plus SAF browser/CRUD, debounced workspace autosave with conflict detection, local draft recovery, bounded editor tabs/snapshots and a shared bounded text-diff view. Shared Git status/history/diff, AI, Git operations, builds and most settings remain planned and tracked in `docs/ROADMAP.md`. The read-only code folding/inspection view and a sandboxed, debounced Web Live Preview for standalone HTML/CSS/JavaScript are implemented.
