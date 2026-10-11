# AstraCode Help & Guide

Keep this content aligned with actual behavior and mark unfinished areas as planned.

## Getting started
AstraCode opens on the dark, branded Home dashboard. The five primary destinations are Home, Projects, AI, Terminal and More; compact windows use a labelled bottom bar, while windows at 600 dp and wider use a labelled navigation rail. The UI also includes Create Project, Build & Run, AI Execution and Settings screens. These screens distinguish controls that are ready from project-generation, build-runner and autonomous-task capabilities that are still planned. Primary selection, More search and the selected More section use saveable Compose state and are restored when Android recreates the activity with saved instance state.

## Accessibility and adaptive layout
Use the visible navigation labels rather than relying on icon appearance alone. AstraCode switches from bottom navigation to a navigation rail at 600 dp available width. UI state that matters to navigation—selected destination, More search query and selected More subsection—is saveable across supported activity recreation. This is a restoration baseline, not a guarantee that every transient editor dialog or scroll position is retained.

## Dashboard, terminal and execution screens

Home provides shortcuts to Projects, AI Chat, Terminal, Build & Run, Create Project and AI Execution. Projects keeps the existing SAF-based file browser/editor and its autosave, recovery, snapshots, find/replace and live preview. Settings lets you change the theme between Dark, Light and System, choose from six accent colors, and set code font size to 11, 12, 13, 14, 16 or 18 sp. Theme, accent and code size are saved locally and persist across activity recreation and app restarts. Code size applies to source text and code-inspection rows; system status/navigation bar colors and icon contrast follow the selected theme.

The Terminal screen is intentionally scoped: the only executable built-ins are `help`, `pwd`, `ls` and `clear`. They do not invoke a shell. `ls` lists children of the explicitly selected SAF workspace root. Arbitrary shell, Flutter, Git, Python, package-manager and build commands are refused before a process is started.

Build & Run currently provides target/configuration selection and honest status feedback; it does not start a build or claim to have produced artifacts. Create Project provides framework/template configuration and input validation, but does not create project files until a safe generator is implemented. AI Execution shows the planned lifecycle without pretending a task is running. Supported single-action AI workspace tools still run from AI Chat and require explicit approval plus direct verification evidence.

## Workspace and files
Open **Projects → Choose project folder** and select a folder in Android's system picker. AstraCode remembers the selected tree URI and persistent read permission where supported. Browse subfolders, filter and refresh the listing.

Use **New file** or **New folder** to create items. Each row's **Actions** menu offers Rename, Move and Delete. To move an item, select Move, navigate into its destination and tap **Move here**. Moving into the current parent or moving a folder into itself/its descendants is blocked. Providers may not support every mutation; check errors rather than assuming success.

Open up to eight recognized text/code files as tabs. Switch tabs from the horizontally scrollable tab strip; each tab retains its current draft and selection, and an asterisk marks pending edits. **Back to files** keeps the tab open. Editable drafts auto-save to the workspace file after a 900 ms pause; **Save now** requests an immediate save. A separate app-private recovery copy is also maintained on-device, and drafts up to 2 MiB are recoverable. Before writing, AstraCode re-reads the workspace file and checks whether it still matches the editor's last saved baseline. If another app or process changed the file, autosave pauses instead of silently replacing it. Use **Compare changes** or **Compare workspace file with draft** to inspect both versions in the shared bounded diff view. **Reload file** discards the current draft and loads stored content; **Overwrite file…** requires explicit confirmation to replace stored content with the current draft. The provider check is best-effort because arbitrary document providers do not offer atomic compare-and-swap writes. **Close tab** on a pending-draft tab offers to keep a local recovery copy or discard the draft. Per-file local snapshots are also available from **Snapshots / diff**: create a snapshot of the current draft, compare it with the draft using the same diff view, restore it to the in-memory draft, or delete it. Snapshots stay on this device (up to ten per file, 2 MiB each). Very large differences use a streaming compact summary. Git status/history/diff remain planned. **Fold / inspect code** opens a read-only folding view for brace-based Kotlin, Java, JavaScript/TypeScript, C/C++, Go, Rust, Swift, Dart and CSS/SCSS files. Tap − on a block to fold it and + to expand it; use **Edit source** to return to the editable text field. The view reports UTF-8 bytes, line count and longest line. To keep mobile analysis bounded, folding is limited to text at most 2 MiB and 1,500,000 lines; a metric-only notice is shown outside that range. Rows are virtualized, fold targets are capped at 50,000, and nesting tracking is capped at 20,000 levels. Lines longer than 4,000 characters are clipped in the inspection view. Braces inside common quoted strings and comments are ignored, but folding is a lightweight structural aid rather than a full language parser. Larger files remain read-only, and binary/unsupported formats cannot be edited. HTML/HTM, CSS and JavaScript files also offer **Show live preview**. The preview follows the current unsaved draft and refreshes after typing pauses; CSS and JavaScript use a small sample page, while HTML is rendered as its own page. Remote images/scripts/stylesheets, linked sibling files, network requests, form submissions and navigation are blocked. Inline CSS/JavaScript works, but local linked assets are not yet resolved. The source editor stays visible above the preview; use **Hide live preview** to close the preview pane; Workspace edits auto-save after a short pause and **Save now** requests an immediate write.

Use **Find / replace** in the editor to search case-insensitively. **Find next** selects the next match and wraps to the start. **Replace match** replaces the selected match, or the next match if none is selected. **Replace all** changes non-overlapping matches in the current draft only. Changes auto-save to storage after a short pause; use **Save now** to request an immediate write.

## AI providers and execution

A provider-neutral cloud AI contract, explicit provider/model router and metadata catalog are now in the internal foundation. The catalog covers OpenRouter, OpenAI, custom OpenAI-compatible endpoints, Gemini, Anthropic, xAI, DeepSeek, Mistral AI, Groq, Together AI, Fireworks AI, Perplexity, Cerebras, SambaNova, NVIDIA NIM and Cohere. It identifies each provider's expected API protocol and documented default endpoint where available. Internal HTTP adapters now implement generation request/response mappings for OpenAI-compatible Chat Completions, OpenAI Responses, Gemini generateContent, Anthropic Messages and Cohere Chat v2. Use **More → AI & Models** to choose a provider, enter the exact model ID, optionally override its HTTPS endpoint, and save an API key. Saved API keys are encrypted with Android Keystore and excluded from backup; the value is masked and never shown again. The **Save & test** action sends a short prompt (not workspace code) to the selected model and may incur provider usage charges. Use **Discover models** to retrieve the models available through the selected provider's model-list API, then select a result or type a model ID manually. The Chat destination now sends your conversation to the explicitly configured provider/model and displays typed errors; requests can be cancelled. Chat conversations are saved in a bounded local database and survive leaving Chat or restarting AstraCode. Use New chat to start a separate conversation and History to reopen or delete previous chats. Up to 25 sessions and 60 messages per session are retained, with title/message caps for mobile resource safety. Chat history is excluded from Android backup because prompts may contain private code. Use **Attach files** in Chat to select up to ten files from Android's system picker. Files can be text/code, images, audio, video, PDFs, office documents, archives or other formats. The maximum is **25 MiB per file** and **100 MiB of attachments combined in one provider request**; app-private on-device attachment storage is capped at **1 GiB**. The attachment list shows each name, MIME type and size; **Remove** detaches a file before sending. Files are copied into private app storage, their metadata is saved with the local chat, and attachment files are excluded from Android backup. Selected content is sent to the configured cloud provider when you send the message. Gemini's inline file input supports audio, video and documents; OpenAI Responses supports a wider set of document and image types, while other adapters have narrower modality support. If the selected provider adapter cannot represent a format, AstraCode returns a clear error rather than silently dropping it. The model must also support that modality. Never attach API keys, credentials or other secrets. Only files explicitly selected by you are read—workspace files are never attached automatically. Chat supports four approved workspace tools: workspace_list lists at most 40 entries, workspace_read reads a UTF-8 text/code file up to 16 KiB, workspace_create_file creates a new recognized text/code file with a 15 MiB UTF-8 limit per file/action, and workspace_move moves an existing file or folder to an existing destination directory. Separate file-creation actions have independent per-file limits rather than one combined content budget. Every proposal shows the action, relative path and reason before execution; choose Approve & run or Decline. AstraCode durably saves an approval record before running any tool, including the proposed content SHA-256 for file creation. If the approval record cannot be saved, nothing runs. File creation is limited to the selected project folder and an existing parent directory, checks for an existing path before creating, never intentionally overwrites, verifies the created name and reads the written bytes back for exact comparison. Successful creation records path, byte count and SHA-256 evidence. Declined proposals record the action, path and reason. Tool output is untrusted workspace data. Move requires an explicit approval, refuses destination name conflicts and verifies destination presence plus source removal; folders cannot be moved into themselves or descendants. Overwrite, delete, shell, build and autonomous multi-step execution are not yet available. AstraCode will not silently switch providers when the selected provider/model is unavailable, and provider failures must not expose credentials or raw response bodies. Model output alone is not proof a task succeeded.

## Approvals and security
Every AI workspace proposal requires explicit per-action approval. Review the action, relative path, reason and proposed file content before approving. Declining records the exact action, path and reason; it performs no workspace operation. New-file creation is limited to recognized text/code names and 15 MiB, refuses an existing path, and reports success only after read-back verification. Model output is not evidence that an action occurred—use the app-generated result and evidence. Overwrite, delete, shell commands, builds and autonomous action sequences are not enabled yet. AI workspace moves require individual approval, refuse conflicting names and are verified after execution. Never put credentials in chat, attachments, workspace files sent to cloud AI, or bug reports.

## Git, builds and delivery
**Planned.** Git/GitHub operations, build dispatch, artifact retrieval and reports are not complete yet.

## Settings
More offers a searchable index for workspace/editor, AI, Git/build, quality/security and support; most destination details are placeholders.

## Troubleshooting
- Workspace access expired: choose the folder again and grant permission.
- A file action failed: the provider may not support it, or permission may have been revoked.
- File is read-only: provider write support may be absent, the format unsupported, or the file above the 2 MiB limit.
- Workspace autosave paused: use Compare changes, then Reload file or explicitly confirm Overwrite file. Retry Save now after resolving provider errors.
- Build/workflow failed: inspect the real logs and include sanitized reproduction steps in reports.

## Reporting a bug
Include app/Android version, steps, expected and actual behavior, and sanitized logs. Remove keys, tokens, private source and personal data.

## Navigation and launcher
Phones use bottom navigation; wider layouts use a navigation rail. More includes search and Help & Guide. The launcher uses the exact `app/src/main/res/drawable-nodpi/astracode_logo.png`; manifest references `@drawable/astracode_logo`. The old vector is retained with a distinct resource name.


## Reference-matched screen layout update — 2026-10-11

The shared page hero now uses the bundled cosmic artwork as a cropped decorative background behind native, accessible Compose text and controls. Create Project includes framework choices for Flutter, Android, React Native, HTML, Next.js, Node.js, Python, Java and Empty Project, plus reference-style template and additional-option switches. These are configuration choices only: project creation remains explicitly unavailable until a verified generator exists. Home includes quick-action cards for file/folder creation, project workspace access and AI Execution. The file/folder shortcuts open Projects, where the existing workspace tools are available; they do not create files automatically. No profile screen or profile control is added.


The shared header shows the AstraCode wordmark and current destination. Its search/workspace shortcut opens Projects, the run shortcut opens Build & Run, and the settings shortcut opens Settings. These are navigation shortcuts and do not claim that a search or build has run.


## Reference visual foundation and animated mascot

The shared hero banner uses a bundled cosmic background, a navy scrim, rounded neon edges and a native Astra robot. The robot gently floats and tilts and blinks its cyan eyes (including a quick double blink) using Compose animation. It does not play video or load a remote image. Primary actions use a violet-to-blue-to-cyan gradient. Home Quick Actions open the real workspace or Git & GitHub entry; no file is created and no clone is performed simply by tapping these navigation tiles. The app has no profile screen or profile control.


## Mobile shell and navigation

The shell places the local cosmic background below a dark navy readability scrim and uses compact phone margins. Bottom navigation emphasizes the AI action with a gradient plus; global navigation hides while AI Chat is open to preserve space for messages and the composer. The hamburger menu opens primary destinations and tool sections, while the overflow menu routes to settings, AI models, project creation, AI Execution, Git & GitHub, Build & Run and Help & Guide. No profile screen or profile control is present.


## AI Chat presentation

AI Chat uses the animated Astra mascot, provider/model status, a settings shortcut, horizontally scrollable capability chips, and starter prompts for project creation, explanation, error repair and feature planning. The Files and Images chips open Android's system picker. Tools explains the supported approval-gated workspace operations; Web explicitly states that browsing is not connected in this version. Chat content continues to use the existing local history, selected provider and approval flow. Do not treat the visible design or a model's response as proof of a file operation.


## Home dashboard and Recent Projects

Home uses the cosmic hero with a New Project action, colored AI/Terminal/Projects/Build cards, Recent Projects and four Quick Actions. Recent Projects are populated from up to four locally saved Android document-provider workspace selections. A framework label is inferred from real files in the selected root and may show "Project" if no known manifest is found. A revoked/unavailable folder is skipped; the app does not fill the list with demo projects. Quick-action tiles route to existing workspace or Git screens and do not automatically create files or clone repositories.


## Project creation, build, AI execution and settings UI

Create Project presents framework and template selections as compact cards with selected-state styling. The location action opens the existing SAF workspace selector. Project generation remains disabled and no files are created from the screen yet. Build & Run uses compact selectable target tiles; build execution is not connected. AI Execution now shows an idle task state, the six-stage lifecycle, empty generated-file/log sections and disabled Pause/Stop controls until a real execution engine exists. Settings includes Project Settings and Advanced Settings categories in addition to the implemented appearance/editor/provider/storage/security settings.


## Terminal layout

The Terminal screen uses tab-style chips for bash, Flutter, Git and Python, shows the selected SAF workspace state, and includes a Quick Commands grid. The only executable commands remain `help`, `pwd`, `ls` and `clear`; quick tiles populate the input and require the user to tap Run built-in. Flutter, Git, Python, package-manager and build commands remain unavailable and are refused without starting a shell process.


## Workspace/editor layout

The Projects screen has a contextual header showing the open file or Project Files, the current SAF breadcrumb path, and whether the view is in FILES or EDITOR mode. The selected file tabs remain separate from folder navigation. The outer destination no longer wraps Projects in another vertical scroll container, so the file list and code editor keep their own scrolling. The header does not expand the granted workspace scope.


## Editor tool windows

The editor has Terminal, Problems, Output and Debug tabs styled as a compact tool-window strip. Terminal directs users to the separate safe terminal destination, which only runs `help`, `pwd`, `ls` and `clear`. The Problems, Output and Debug tabs explicitly state when no compiler diagnostics, build output or debugger session is connected. The editor has its own bounded scrolling so source text scrolls inside its field and the remaining controls remain reachable below it.
