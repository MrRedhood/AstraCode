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


## 2026-10-10 — Durable local AI chat sessions
- Added an app-private SQLite store for separate chat sessions and ordered messages, with foreign-key cleanup and explicit schema versioning.
- Chat history can now be created, reopened and deleted from Chat. User messages are written locally before a provider request; returned assistant text is saved, while cancellation and typed provider errors remain visible.
- Bounded local storage to 25 sessions and 60 messages per session, with a 44-character normalized title and 60,000-character message cap to protect lower-memory devices.
- Excluded the chat database from Android cloud backup and device transfer because conversations can contain private prompts or source text.
- Added instrumentation coverage for persistence across store recreation, title generation, session/message retention bounds, system-message rejection and cascading deletion.
- Updated the in-app Help & Guide, architecture and roadmap.
- Validation: committed directly to main with expected-head protection. Android CI and UI Smoke statuses are tied to this exact commit and reported separately; no pending workflow was waited on.


## 2026-10-10 — Explicit text/code attachments for cloud chat
- Added Android system document-picker attachments with strict text/code file validation, strict UTF-8 decoding and NUL/binary rejection; no workspace files are included implicitly.
- Limited each chat message to three attachments, 16 KiB per file and 32 KiB combined. Chat displays names/sizes, allows removal before sending and permits attachment-only requests. The UI warns that selected content is sent to the chosen model and stored in local history.
- Kept provider content separate from visible user-message text so attached source is available in later turns and durable history without rendering full files in chat bubbles.
- Migrated the local chat schema to version 2 with a display-content column while preserving prior sessions.
- Added unit policy tests for file-type handling, strict UTF-8, binary rejection, display/payload separation and count/size caps; extended UI smoke assertions for attachment controls and the secrets warning.
- Updated in-app Help & Guide, architecture and roadmap.
- Validation: committed directly to main with expected-head protection. Exact-head Android CI/UI Smoke status is authoritative; no pending workflow was waited on.


- Roadmap correction: marked explicit text/code attachments complete and left controlled tool execution and end-to-end verification open as the next AI increment.


## 2026-10-10 — Approval-gated read-only workspace tools
- Added a strict, bounded tool-call envelope with an exact allow-list for workspace_list and workspace_read. Unknown fields/tools, malformed envelopes, absolute paths, traversal, controls and deep paths are rejected.
- Added a read-only executor that resolves relative paths from the persisted SAF root, checks each directory segment, lists at most 40 entries and reads only recognized UTF-8 text/code files up to 16 KiB.
- Chat now instructs the configured model how to request an allowed tool, presents its action/path/reason for explicit human approval, records declined requests, and executes only after Approve & run. No write/delete/move/shell tool exists in this increment.
- Tool results are persisted as visible chat entries with direct evidence including path, byte count, SHA-256 or list counts; returned workspace content is explicitly marked untrusted. Users can ask a follow-up to analyze it.
- Added protocol tests for normal responses, valid read/list requests, rejected mutation tools, traversal and path depth, strict envelopes/field sets, invalid reasons and payload size bounds.
- Fixed the prior attachment reader compile error by stabilizing the nullable declared file-size value before comparison.
- Updated runtime Help & Guide, architecture, roadmap and this implementation log.
- Validation: committed directly to main with expected-head protection. Exact-head Android CI and UI Smoke are the authority for test outcomes; no pending run was waited on.


## 2026-10-10 — 25 MiB multimodal chat attachments
- Raised the per-file cap to 25 MiB and allow selection from Android's all-file-type picker. Supports up to three selected files, with a 25 MiB aggregate attachment payload cap per model request and a 250 MiB local attachment-storage quota.
- Replaced in-memory text-only file loading with bounded streaming copies to app-private attachment files. SQLite schema v3 persists only validated attachment metadata and references; raw bytes remain outside the database and excluded from Android cloud backup/device transfer.
- Added request-time attachment hydration with ID/size/byte-count checks and a bounded payload. User-visible chat bubbles show file names, MIME type and sizes, not binary data.
- Added native adapter payload construction for Gemini inline media data (including supported audio/video/document MIME types), OpenAI-compatible image/PDF parts, OpenAI Responses image/file parts, Anthropic image/PDF blocks and Cohere image parts. Text/code files are decoded as UTF-8 only when their type is text-like; unsupported provider/format combinations fail clearly instead of being silently omitted.
- Raised the HTTP request ceiling to 64 MiB to account for Base64 expansion while keeping a 25 MiB aggregate raw attachment limit.
- Added policy, metadata-persistence and provider payload tests. Fixed the previous workspace-tool protocol test by classifying missing required fields as malformed JSON rather than unknown fields.
- Updated Help & Guide, architecture, roadmap and this implementation log.
- Validation: committed directly to main using expected-head protection. Exact-head workflow results are authoritative; pending workflows were not waited on.


## 2026-10-10 — Approval-gated create-only workspace file tool
- Added the workspace_create_file request to the strict AI tool protocol. Proposals require an exact four-field schema with bounded UTF-8 text content; path traversal, unsupported file names, control characters, malformed Unicode and payloads over 16 KiB are rejected.
- Added an explicit approval card that identifies file creation, shows the destination and a bounded content preview, and clearly states that existing files cannot be overwritten.
- Restricted creation to recognized text/code filenames beneath existing directories inside the selected persisted SAF tree. Existing exact paths are refused before creation; the storage provider's returned name is checked before writing.
- The explicit approval audit record is persisted before the executor is called; create-file approval also records the proposed content size and SHA-256. If approval persistence fails, no operation runs. The executor reads the created file back and compares bytes exactly, then records the relative path, byte count and SHA-256. If verification fails, it attempts to remove the partial file and reports when cleanup cannot be confirmed.
- Declines are persisted with the proposed action, path and reason. Existing list/read capabilities continue unchanged; overwrite, move, delete, shell and build tools remain unavailable.
- Added protocol, filename policy and approval-audit tests, including a check that audit history binds to the proposed file's SHA-256 without storing its source content. Updated in-app Help & Guide, architecture, roadmap and this implementation log.
- Corrected the previous exact-head CI compile failure: the public provider-neutral chat message exposed an internal attachment type. The attachment metadata type is now public only as a container; its constructor and file metadata/bytes remain internal to the app module. The old workflow log identified this compile error, while UI Smoke also failed on that revision.
- Validation: committed directly to main with expected-head protection. Exact-head Android CI/UI Smoke status is authoritative; no pending workflow was waited on.

## 2026-10-10 — Expand attachment capacity and AI-created file size
- Raised the per-message attachment-count limit from 3 to 10 and the app-private retained-attachment quota from 250 MiB to 1 GiB. Kept the existing 25 MiB per-file and 25 MiB aggregate provider-request caps, and kept streaming imports so selection does not load entire files into memory.
- Updated the composer limit, storage-quota error text, policy tests, smoke assertion and runtime Help & Guide.
- Raised approval-gated `workspace_create_file` payloads from 16 KiB to 15 MiB of strict UTF-8 text. Expanded the tool envelope to 32 MiB and the bounded HTTP response ceiling to 64 MiB to allow JSON and nested response escaping while retaining an explicit upper bound.
- Updated executor error messaging, protocol boundary tests, provider transport-cap tests, architecture and roadmap documentation. Workspace reads remain capped at 16 KiB; editor editing remains capped at 2 MiB.
- Validation: changes are committed directly to `main`; exact-head Android CI and Android UI Smoke results are authoritative. No running workflow is treated as passed.

## 2026-10-10 — 100 MiB AI request payload and approval-gated workspace move
- Increased the raw attachment payload limit to 100 MiB across one provider request. Kept 10 files per message, 25 MiB per file and 1 GiB retained app-private storage. Raised the bounded HTTP request body ceiling to 256 MiB to accommodate Base64 and JSON framing; the provider-request cap remains enforced on raw attachment bytes before adapter execution.
- Clarified that `workspace_create_file` has a 15 MiB UTF-8 limit per individual file/action. There is no combined content-byte budget shared across separate create-file actions, though provider context limits and available workspace/device storage still apply. Renamed the constant to `MAX_CREATE_BYTES_PER_FILE` to make the contract explicit.
- Added the audited `workspace_move` action. Its exact schema uses a source path and an existing destination directory (empty destination means workspace root). Approval is persisted before execution; the tool refuses name conflicts and same-parent no-ops, prevents moving a folder into itself or a descendant, and verifies the provider-returned item in the destination plus removal from the source listing.
- Added policy/protocol tests for the 100 MiB attachment aggregate, HTTP sizing, move schema, invalid locations, root destination and move audit records. Updated runtime Help & Guide, architecture, roadmap and the UI smoke assertion.
- Validation: committed directly to `main`; exact-head Android CI and Android UI Smoke determine verification. No workflow in progress is counted as successful.

## 2026-10-10 — Align runtime tool help with workspace move capability
- Corrected the in-app More → Help & Guide text so it no longer says AI moves are unavailable after the audited `workspace_move` tool shipped. Overwrite, delete, shell and build remain unavailable; AI moves require explicit approval and post-move verification.


## 2026-10-10 — Implement AstraCode's ten-screen UI direction

- Replaced the foundation-only primary navigation with Home, Projects, AI, Terminal and More while preserving the existing SAF workspace/editor and cloud AI Chat destinations.
- Added a dark-first Compose color/typography system with accent selection and saveable Dark/Light/System appearance mode. Added a dark Android launch theme and an Android 12+ branded splash icon/background.
- Added a responsive Home dashboard with workspace status and links into AI Chat, Projects, safe Terminal, Build & Run, Create Project and AI Execution.
- Added focused Create Project, Build & Run, AI Execution and Settings screens. Project generation, build execution and autonomous multi-step task execution remain unavailable and are labelled honestly in their UI.
- Added a scoped, non-shell Terminal. Only `help`, `pwd`, `ls`, and `clear` execute; `pwd` reports selected-workspace state and `ls` lists children of the granted SAF root. Other commands are rejected without starting a process.
- Expanded More and the in-app Help & Guide, updated user documentation, architecture and roadmap, and added UI smoke coverage for new navigation, safe terminal rejection, and build-runner status.
- Typography follows the uploaded pack's scale and family roles, using Android system sans-serif/monospace fallbacks because the supplied ZIP contains no font binaries. Existing locally drawn Compose icons remain in use; no remote font or icon dependency was added.
- Validation: GitHub connector writes were accepted on `main`. The initial handoff Android CI run was green; the handoff Android UI Smoke run was still in progress when last inspected. New commits trigger fresh workflows; latest-head results must be checked separately. No local Gradle run was available in this environment.


## 2026-10-10 — UI integration and theme refinements

- Follow-up UI fixes make contextual More-screen titles and a Back to More action visible, route the Workspace and Editor shortcuts to the existing SAF workspace instead of generic placeholders, and add an AstraCode emblem-style icon to the section header.
- Updated the Help & Guide's workspace path and AI destination names to match the new navigation.
- Fixed hero/panel contrast for Light mode and switched custom Compose icon palette selection to the app's chosen theme rather than only Android's system theme.
- Validation remains pending for this new HEAD: no local Gradle build was available, and earlier CI/UI runs target earlier commits. Do not treat those historical runs as evidence for the current HEAD.


## 2026-10-10 — Complete custom UI navigation icon set

- Added dedicated locally drawn Compose glyphs for Home, approval, execution, artifact and storage so the new destination/status names no longer render as the generic fallback icon. Icons adapt to the selected AstraCode theme.
- Kept the local Canvas icon system to avoid introducing SVG runtime dependencies; the uploaded pack’s palette and icon semantics inform these additions. The pack does not change the existing app launcher asset.
- Validation for the latest UI commits remains unverified pending current-head CI/UI results; no local Android SDK/Gradle run was available in this execution environment.


## 2026-10-10 — Polish dashboard navigation semantics

- Renamed the dashboard shortcut cards to `Terminal tools` and `Project files` so their text does not duplicate the persistent Terminal and Projects navigation labels. This keeps Compose UI test selection deterministic and makes the shortcut intent clearer.
- No changes to workspace scope or command capabilities; all existing approval and SAF boundaries are retained.


## 2026-10-10 — Finish appearance settings and font-system integration

- Extracted the uploaded font pack's mobile-first type scale into `AstraCodeTypography`; display, UI and code roles use the specified system-family fallbacks because the pack contains no font binaries. Terminal text uses the shared monospace role.
- Added app-private persistence for the selected theme and accent. Theme switching updates Android status/navigation bar colors and corresponding light/dark icon contrast flags.
- Reused the existing `astracode_logo` drawable in the AstraCode screen header; launcher artwork remains unchanged. Cleared redundant accessibility descriptions from decorative hero/card glyphs and made Storage and Security settings summaries informational rather than routing them into unrelated screens.
- Expanded Android UI smoke coverage for Create Project navigation, honest AI Execution status, safe terminal refusal, and persisted theme/accent choices.
- Validation: this environment has no local Gradle executable or Android SDK and outbound GitHub networking is unavailable, so local compilation/emulator execution could not be performed. Latest-head workflow outcomes must be verified in GitHub Actions; no earlier workflow run is treated as proof for these commits.


## 2026-10-10 — Complete Settings typography and visual polish

- Added a saved code-font-size preference (11, 12, 13, 14, 16 or 18 sp) and connected the Settings controls to the editor and structural inspection text. Source line-height scales proportionally with the chosen size. Theme/accent/code-size choices are stored in app-private SharedPreferences.
- Added a lightweight Compose robot mascot illustration to the Home, Create Project, Build & Run and AI Execution hero panels, keeping artwork local/vector-like and avoiding large remote or raster dependencies on lower-end Android devices.
- Tightened settings affordances: informational File & Storage and Security & Privacy sections no longer show a chevron or route to unrelated screens. The UI header reuses the existing launcher artwork without changing it.
- Expanded the UI smoke test to verify the code-font-size preference survives activity recreation. Help & Guide, architecture and implementation docs were updated to match behavior.
- Validation: code changes and commits are present on `main`; this environment has no local Gradle executable/Android SDK and cannot reach GitHub directly. The available connector exposed only earlier workflow runs (the handoff Android CI succeeded, and the handoff UI Smoke run failed during emulator startup/install); no green status is confirmed for the latest UI commits.


## 2026-10-10 — Final UI verification hardening

- Adjusted the Home dashboard UI smoke test to scroll the execution action into view before clicking, reducing viewport-dependent emulator failures.
- Made Light theme accent colors use deeper contrast-safe shades, so changing the saved accent visibly affects both dark and light appearances while maintaining text contrast.
- Static source review confirmed balanced delimiters across the changed Kotlin screens, theme, preferences, workspace/editor, icon and UI test files. This is a syntax-structure check only, not a substitute for Gradle compilation or emulator tests.


## 2026-10-10 — Repair Android CI compilation and SDK setup

- Fixed the Kotlin compilation failures reported by Android CI run [38063717672](https://github.com/MrRedhood/AstraCode/actions/runs/38063717672): imported Material 3 `Surface`, the text-family and `sp` types used by Terminal, and the Compose `Modifier.size` extension used for the brand logo. Terminal output uses the shared AstraCode monospace typography role.
- Pinned installation of Android platform 36 and build-tools 36.0.0 using the SDK manager package-path syntax, and retained toolchain diagnostics.
- The follow-up CI run [38064098366](https://github.com/MrRedhood/AstraCode/actions/runs/38064098366) failed before Gradle started because `android-actions/setup-android@v3` invoked `sdkmanager tools`, a package no longer available from the configured SDK repository. Removed that incompatible action and now configure the hosted runner's preinstalled SDK command-line tools directly before installing only the required SDK packages.
- The debug APK is uploaded only after a successful build, with missing APK output treated as an error; build/test reports are uploaded on failure.
- Validation caveat: this fix is committed directly to `main`; the workflow triggered by the resulting exact HEAD is the source of truth. A triggered run is not reported as passed before its conclusion is available.


## 2026-10-10 — API-key-only AI setup and compact chat layout

- Simplified More → AI & Models to provider selection, one API-key field and Save & connect. The app discovers available models, filters obvious non-chat modalities and selects a likely general-purpose chat model automatically; users no longer need to enter a model ID or endpoint.
- Added a standard HTTPS default for the OpenAI-compatible catalog entry. Existing saved custom endpoint overrides remain in effect. API keys remain encrypted with Android Keystore; the short connection test warns about potential charges and never attaches workspace files.
- Fixed the compact chat header: status and conversation text use full width; AI settings, New chat and History actions move to a horizontally scrollable row instead of squeezing status text into a vertical strip.
- Updated Help & Guide and architecture/roadmap documentation. Added model selection and API-key-only validation tests and updated UI smoke assertions.
- Repaired three known UI Smoke failures from run [38064335972](https://github.com/MrRedhood/AstraCode/actions/runs/38064335972): Create Project return navigation and ambiguous Settings / AI & Models selectors.
- Validation: changes are prepared for direct commit to `main`; no post-change test/workflow result is claimed.


## 2026-10-10 — Manual model selection and discovery filters

- Added manual selection alongside automatic selection. Users can discover available models, search by display name or model ID, and tap a model to select it before connecting.
- Added pricing filters for Free, Paid, Unknown and All. OpenRouter prompt/completion pricing is normalized to approximate USD per million tokens; price is marked unknown rather than guessed when providers omit it.
- Added context filters for high (128K+), standard (32K–128K), low (under 32K) and unknown token windows. Model entries show reported context and price details where available, with the visible list capped for mobile responsiveness.
- Updated discovery unit tests and More → Help & Guide. API keys remain encrypted, and previously saved custom endpoint overrides remain supported.
- Validation caveat: changes are prepared for direct commit to `main`; no post-change CI/UI result is claimed.


## 2026-10-11 — Space-themed animated startup screen

- Added the provided artwork as a full-screen startup background while removing embedded sample status icons/time and the static progress bar so the real system UI and animated Compose progress indicator remain authoritative.
- Added transparent overlays for the A/orbit mark and AstraCode wordmark, animated with a pulse/tilt and vertical float. A cyan-blue-magenta progress bar animates during launch, and the screen fades over the existing app shell rather than resetting navigation.
- Kept the existing launcher icon unchanged. The cosmic background is a compressed WebP in `drawable-nodpi`; the A mark and wordmark are drawn in Compose so they can animate independently. The loading indicator is explicitly cosmetic, not reported build, CI or network progress.
- Added this user-facing behavior to More → Help & Guide, architecture and roadmap documentation. UI smoke tests now wait for the launch overlay to finish before interacting.
- Validation caveat: the change is committed directly to `main`; the exact commit's Android CI/UI status is authoritative. No unobserved result is claimed.


## 2026-10-11 — Startup splash CI compile repair

- Fixed a missing Compose container-closing brace in `MainActivity.kt` that caused Kotlin to interpret subsequent top-level composables as local functions and report cascading unresolved references. The splash overlay now sits as a sibling over the responsive content container.
- Validation note: the failure was reproduced from Android CI run 38101170148; verify the new commit's CI result before claiming green status.


## 2026-10-11 — Reference-screen UI refinement

- Updated the shared page hero to use the bundled cosmic artwork as a cropped background while retaining native Compose text and controls. No profile page or profile control was introduced.
- Expanded Create Project framework/template selections and configuration toggles to align with the supplied reference layout. These remain UI configuration only; no project files are generated, and the screen continues to state that honestly.
- Added Home quick-action cards for file/folder creation, project workspace access and AI Execution. Actions route only to existing safe destinations; no shell, Git clone, build, or project-generation capability is implied.
- Validation caveat: repository changes were prepared against the inspected main HEAD; Android compilation and emulator screenshot comparison must be run by the repository workflows before claiming pixel-level parity or build success.


## 2026-10-11 — Branded in-app header

- Replaced the small uppercase wordmark and duplicated page-title row with a compact AstraCode brand header, gradient wordmark, current destination subtitle and working shortcuts for workspace/search entry, Build & Run, and Settings.
- Removed the repeated footer tagline so the available vertical space better matches the supplied mobile screens. No profile button or profile screen was added.


## 2026-10-11 — Reference visual foundation and mascot motion

- Replaced the static robot illustration with a native Compose mascot using a blue/purple helmet, glossy visor, cyan eyes, feet and antenna glow. It floats and tilts gently and performs a periodic natural/double blink without video decoding or a fast polling loop.
- Standardized shared hero banners around the bundled cosmic artwork, dark contrast overlay, rounded neon border, compact responsive foreground layout and floating mascot. Primary CTAs use a violet-to-blue-to-cyan gradient.
- Rebuilt Home Quick Actions as four equal-width compact tiles (Create file, Create folder, Open project, Clone from Git). The Git tile routes to the repository entry instead of pretending to clone; file/folder tiles route to the real workspace where their safe actions live.
- Validation caveat: source was edited directly on main; Android build and emulator screenshot comparison have not been run here. Pixel-identical parity is not claimed until verified on-device.


## 2026-10-11 — Mobile navigation and branded shell

- Added a compact hamburger destination menu and overflow navigation for Create Project, AI Execution, Git & GitHub, Build & Run, Settings, AI & Models, Help & Guide and About AstraCode. Desktop keeps the Git shortcut visible; compact phones use the overflow menu for less-frequent actions.
- Updated the app shell to place the local cosmic art beneath a dark readability scrim and transparent screen scaffolds. Mobile margins are tighter and the AI destination uses a gradient central action while hiding global bottom navigation when chat is open.
- Kept the profile UI omitted. Updated UI smoke-test expectations for the split AstraCode wordmark. No unverified build/test result is claimed.


## 2026-10-11 — Reference-inspired AI Chat

- Reworked the AI conversation header around the animated Astra mascot, assistant subtitle, configured provider/model and direct settings access.
- Added horizontally scrollable Chat/Tools/Files/Images/Web/Code controls. Files and Images launch the actual system picker; Tools explains the limited approval-gated workspace actions; Web reports that external browsing is not integrated; Code inserts a practical prompt starter.
- Added first-turn prompt cards for project creation, code explanation, error repair and feature planning. Redesigned user/assistant message bubbles with distinct color/shape treatment and animated bot avatars for assistant messages. Composer copy now follows the reference's "Ask me anything" pattern.
- These are presentation changes over the existing safe provider/session/approval layer; web browsing and unsupported agent/file creation operations are not simulated.


## 2026-10-11 — Recent project history for Home

- Workspace selections now keep a bounded most-recent-first list of up to four SAF tree URIs alongside the existing active workspace preference. This history is local-only and uses the same Android document-provider grant boundary.
- The repository resolves the display name and an informational framework tag from real top-level files when the grant remains valid. Revoked/unavailable providers are skipped rather than shown as fake projects. The home screen can use this for real Recent Projects cards.


## 2026-10-11 — Home dashboard and real Recent Projects

- Updated Home to the reference hierarchy: taller cosmic hero with New Project CTA, paired colored feature cards, Recent Projects rows, and compact four-column Quick Actions.
- Connected Recent Projects to a new local bounded list of real SAF workspaces from the workspace repository. Each row shows its resolved folder name and a best-effort framework tag derived from actual top-level manifest/build files; revoked/unavailable grants are not shown as successful projects. Selecting a row restores that saved workspace selection and opens Projects.
- Added a visible arrow treatment to feature cards. Empty state directs the user to the actual workspace picker instead of showing sample/fake projects.


## 2026-10-11 — Reference-style project, build, execution and settings layouts

- Replaced Create Project framework chips with selectable icon cards and template cards with a compact two-column layout. Added a real workspace-destination shortcut; creation still reports that the generator is unavailable and does not fabricate files.
- Changed Build & Run target selection to the reference-style 2×2 set of compact tiles, retaining honest state that no build runner is connected.
- Reworked AI Execution into a task header, horizontal six-stage stepper, current-step panel, empty Generated Files and Execution Logs sections, and visibly disabled Pause/Stop controls when no engine is available. No fake running percentage, files or logs are shown.
- Completed the Settings category list with Project Settings and Advanced Settings, alongside existing Appearance, Editor, AI, Terminal, Build & Run, File & Storage, Security and About.


## 2026-10-11 — Reference-style safe terminal screen

- Added terminal tabs styled for bash, Flutter, Git and Python. Only bash-style built-ins use the existing safe handler; Flutter, Git and Python tabs visibly explain that arbitrary execution is unavailable and never start a process.
- Added the selected-workspace status strip and a compact Quick Commands tile grid. Runnable tiles only populate the input; the user still presses Run built-in. Unsupported sample commands surface a warning and are refused by the existing dispatcher.


## 2026-10-11 — Workspace/editor screen shell

- Added a persistent contextual header to Projects showing Project Files or the open filename, the current SAF breadcrumb path, and an explicit FILES/EDITOR state badge. The file list rows now use the same rounded geometry as the reference UI.
- Fixed the parent destination container so Projects is no longer wrapped in a second vertical scroll container. Workspace lists and editor text retain their own scroll behavior, reducing nested-scroll interference on phones.


## 2026-10-11 — Editor workspace tool window and bounded scroll

- The workspace now owns its vertical scrolling rather than inheriting a shell-level scroll container. The source editing field has a bounded display height and keeps its own text scrolling, while editor controls remain reachable below it on small phones.
- Added Terminal, Problems, Output and Debug tabs under the editor. These are explicit status panels: Terminal points to the existing allow-listed command screen; Problems/Output/Debug say when analyzers, build output or a device debugger are not connected instead of inventing diagnostics or logs.


## 2026-10-11 — Settings and About alignment

- Aligned Settings category names with the reference: Editor Preferences, AI Settings, Terminal Settings, Build & Run Settings, Project Settings, File & Storage, Security & Privacy and Advanced Settings.
- Rebuilt About AstraCode around the exact bundled app logo, wordmark, version and license and added working external navigation to the repository releases page, Apache license, and new GitHub issue form. These buttons open the official pages rather than claiming an in-app update or report was completed.


## 2026-10-11 — AI Chat toolbar and attachment menu

- AI Chat now owns its own header with a back action, animated Astra assistant, provider/model shortcut and settings shortcut. The general application header is hidden while Chat is open; Android Back returns to Home.
- Removed the duplicated AI Settings action from the secondary toolbar. The attachment plus menu now offers File, Image, Video, Audio and Camera; supported media choices launch Android's document picker. Camera clearly reports that direct capture is not integrated and directs users to Image.


## 2026-10-11 — Reference-style bottom navigation behavior

- Changed the compact bottom bar to Home, Projects, a central gradient Create (+) action, contextual Tools/Terminal/Build/More, and a separate AI Chat destination. The central plus opens Create Project rather than AI Chat; AI Chat now has its own right-side navigation item and its own focused header.
- The context label and icon for the fourth item reflect Terminal, Build & Run, a More detail or Tools. The global menu remains the entry to the More hub and its settings sections.


## 2026-10-11 — Mobile layout integration cleanup

- Added the explicit Compose layout imports used by AI Chat's focused toolbar and attachment menu, and by the workspace's owned scroll container. This prevents unresolved symbols introduced by the reference-style UI pass.


## 2026-10-11 — UI smoke tests updated for reference navigation

- Updated instrumented smoke coverage to open More via the hamburger menu, expect the Home bottom bar's Tools label, and inspect the new Chat attachment menu. The camera menu item is tapped to verify its explicit unavailable-state message rather than launching a fake capture flow.
- Test source was updated but the emulator workflow has not been executed from this session.


## 2026-10-11 — Smoke helper correction

- Corrected the instrumented smoke-test helper to tap the hamburger button and select More from its menu, rather than recursively invoking the helper. This follows the redesigned mobile navigation and avoids a test-time recursion failure.
