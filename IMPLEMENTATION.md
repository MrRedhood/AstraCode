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


## 2026-10-10 — Workspace autosave and shared bounded diff
- Added a 900 ms debounced workspace-file autosave for eligible writable text drafts, plus a Save now action. Autosave and immediate saves are serialized per selected workspace; completed writes update the saved baseline without replacing newer in-memory edits.
- Re-read the document immediately before a normal write and compare it with the editor's last saved baseline. If a mismatch or truncated stored file is detected, pause autosave and expose compare/reload/explicit-overwrite actions rather than silently overwriting external edits. This is best-effort conflict protection because SAF providers do not expose atomic compare-and-swap semantics.
- Reused one bounded diff model and renderer for local-snapshot-to-draft and stored-workspace-to-draft comparisons. The existing compact summary path still handles large inputs.
- Added unit coverage for autosave decisions (unchanged draft, matching baseline, external conflict, truncation and UTF-8 size cap); updated diff tests and the roadmap, architecture and in-app Help & Guide.
- Validation: changes committed directly to `main`; Android CI and Android UI Smoke should be checked against the exact new commit before claiming success.


- Source-review follow-up: corrected the conflict-dialog callbacks to invoke the overwrite/reload actions before checking the new commit's workflows.


- Autosave follow-up: flush pending writable drafts when switching tabs or closing a tab after choosing to keep its recovery draft. Treat a write that already placed the identical draft on storage as successful, avoiding a false external-conflict warning from duplicate serialized save requests. Updated the roadmap and architecture wording to remove the obsolete explicit-save description.


## 2026-10-10 — Accessibility, UI restoration and adaptive navigation
- Extracted the 600 dp bottom-bar/navigation-rail breakpoint into a deterministic policy and added boundary tests for compact, just-below-threshold and expanded widths.
- Made custom navigation glyphs decorative when the navigation item already has a visible text label, avoiding duplicate icon and label announcements for screen readers.
- Added an Android UI smoke test that recreates the activity and verifies More search state and selected subsection restoration; navigation keeps visible text labels.
- Updated in-app Help & Guide, architecture notes and roadmap. Corrected stale in-app/editor-guide copy that described explicit-only saves after workspace autosave shipped.
- Validation: committed directly to `main`; exact-head Android CI and Android UI Smoke results determine whether validation is green.


- Documentation follow-up: clarified that live preview itself is read-only, while source draft edits and restored snapshots follow the same debounced workspace-autosave/conflict policy; removed the duplicate autosave checkbox in the roadmap.

## 2026-10-10 — Cloud AI provider boundary and explicit routing
- Added vendor-neutral cloud request/message, model capability, token usage, response and typed failure contracts without adding a bundled local model or vendor SDK.
- Added an explicit provider/model router with model capability and output-token-limit checks. Unknown providers/models are rejected rather than silently routing project content to another provider.
- Bounded request size/message count and validated model IDs, temperature and token limits before adapter execution.
- Added unit tests for explicit routing, no-fallback behavior, capability limits, duplicate provider registration, request validation and typed provider outcomes.
- Updated architecture, roadmap and Help & Guide to distinguish this internal foundation from live adapters, credential storage and user-facing AI execution, which remain planned.
- Validation: source and test changes committed directly to main. Android CI and UI smoke for the new exact HEAD are authoritative; no local Gradle result is claimed.

## 2026-10-10 — Expand cloud AI provider catalog
- Expanded provider identifiers to include OpenRouter, OpenAI, OpenAI-compatible custom endpoints, Gemini, Anthropic, xAI, DeepSeek, Mistral AI, Groq, Together AI, Fireworks AI, Perplexity, Cerebras, SambaNova, NVIDIA NIM and Cohere.
- Added per-provider catalog metadata for API protocol, HTTPS default endpoint where known, official documentation URL, credential label and endpoint customization support.
- Distinguished OpenAI Responses, OpenAI-compatible Chat Completions, Gemini generateContent, Anthropic Messages and Cohere Chat v2 protocols rather than assuming all providers share one wire format.
- Added catalog tests for completeness, unique IDs, expected provider coverage, custom endpoint behavior, protocol distinctions and HTTPS metadata.
- Updated roadmap, architecture, Help & Guide and this implementation log. The catalog is groundwork; live API adapters, secure credential persistence and UI selection/connection tests remain planned.
- Validation: committed directly to main with expected-head protection. Exact-head Android CI and UI Smoke statuses are reported separately; no unverified test result is claimed.

## 2026-10-10 — Cloud AI HTTP adapters
- Added injectable credential and HTTP transport interfaces and a bounded HTTPS transport with redirects disabled, request/response byte caps, connect/read timeouts, coroutine cancellation propagation and no credential/request-body logging.
- Implemented OpenAI-compatible Chat Completions adapters for catalogued compatible providers, plus native OpenAI Responses, Gemini generateContent, Anthropic Messages and Cohere Chat v2 protocol mappings.
- Added typed missing-credential, authentication, rate-limit, invalid-request, unavailable, oversized-response and unsupported-model failures; HTTP error bodies are not copied to user-facing detail.
- Added a provider factory that selects the correct protocol, supports custom HTTPS base URLs where catalogued, and requires explicit model capability configuration. Streaming and vision remain disabled in adapter capability reports until implemented.
- Added test-only JSON runtime dependency and adapter tests for request encoding, response parsing, provider-specific headers, missing credentials, safe error mapping, output limits and factory selection. Added Android INTERNET permission.
- Updated roadmap, architecture, Help & Guide and this implementation log. The adapter layer is not yet wired to encrypted credential storage, model discovery, provider UI or chat execution.
- Validation: changes committed directly to main with expected-head protection. Exact-head Android CI/UI Smoke workflows are authoritative; no unverified test result is claimed.

## 2026-10-10 — Encrypted credentials and provider settings
- Added an Android Keystore AES-256-GCM API-key vault. Per-provider keys are authenticated with provider-specific additional data; secrets are never stored as plaintext preferences or included in UI status messages.
- Excluded the AI secret preferences file from cloud backup and device transfer so ciphertext is not restored without its device-local Keystore key.
- Added provider/model/optional HTTPS endpoint settings under More → AI & Models, masked API key entry, save/replace/remove actions and an explicit test that sends a short provider prompt without workspace content.
- Added validation for manual model IDs and secure endpoint URLs, plus JVM validation tests and an instrumentation test for encrypted-at-rest round trips, deletion and plaintext absence.
- Updated in-app Help & Guide, architecture, roadmap and this implementation log; fixed the prior literal backslash-n separator in the P4 roadmap.
- Validation: committed directly to main with expected-head protection. Exact-head Android CI and UI Smoke results are authoritative; no local Gradle or API-provider result is claimed.

## 2026-10-10 — Model discovery and cloud chat wiring
- Extended the bounded HTTP transport to support body-free HTTPS GET requests while keeping POST generation behavior unchanged, redirects disabled, and request/response size limits active.
- Added authenticated model discovery for OpenAI-compatible and OpenAI model lists, Gemini generateContent-capable models, Anthropic model lists and Cohere chat models. Model IDs are validated, response counts are capped, and raw provider error bodies are never surfaced.
- Added model search/selection controls in More → AI & Models while preserving manual model entry. Discovering models can securely save a newly entered API key without requiring a model ID first.
- Replaced the Chat placeholder with a provider-backed conversation UI using the explicitly configured model, bounded in-memory history, cancellable requests, sanitized error display and no automatic workspace-file attachment.
- Added unit tests for each native model-list route, filtering and auth headers, invalid IDs, missing credentials and sanitized API failures; extended the UI smoke coverage for model discovery and Chat setup navigation.
- Updated architecture, roadmap, Help & Guide and this implementation log.
- Validation: commit directly to main with expected-head protection; exact-head Android CI/UI Smoke states determine verification. No provider network request was made during tests or implementation.
