# AstraCode Help & Guide

Keep this content aligned with actual behavior and mark unfinished areas as planned.

## Getting started
AstraCode is an early Android coding-workflow foundation. Some destinations are placeholders while their features are implemented. On compact windows the five primary destinations use a labelled bottom bar; at 600 dp and wider they use a labelled navigation rail. Primary selection, More search and the selected More section use saveable Compose state and are restored when Android recreates the activity with saved instance state.

## Accessibility and adaptive layout
Use the visible navigation labels rather than relying on icon appearance alone. AstraCode switches from bottom navigation to a navigation rail at 600 dp available width. UI state that matters to navigation—selected destination, More search query and selected More subsection—is saveable across supported activity recreation. This is a restoration baseline, not a guarantee that every transient editor dialog or scroll position is retained.

## Workspace and files
Open **Code → Choose project folder** and select a folder in Android's system picker. AstraCode remembers the selected tree URI and persistent read permission where supported. Browse subfolders, filter and refresh the listing.

Use **New file** or **New folder** to create items. Each row's **Actions** menu offers Rename, Move and Delete. To move an item, select Move, navigate into its destination and tap **Move here**. Moving into the current parent or moving a folder into itself/its descendants is blocked. Providers may not support every mutation; check errors rather than assuming success.

Open up to eight recognized text/code files as tabs. Switch tabs from the horizontally scrollable tab strip; each tab retains its current draft and selection, and an asterisk marks pending edits. **Back to files** keeps the tab open. Editable drafts auto-save to the workspace file after a 900 ms pause; **Save now** requests an immediate save. A separate app-private recovery copy is also maintained on-device, and drafts up to 2 MiB are recoverable. Before writing, AstraCode re-reads the workspace file and checks whether it still matches the editor's last saved baseline. If another app or process changed the file, autosave pauses instead of silently replacing it. Use **Compare changes** or **Compare workspace file with draft** to inspect both versions in the shared bounded diff view. **Reload file** discards the current draft and loads stored content; **Overwrite file…** requires explicit confirmation to replace stored content with the current draft. The provider check is best-effort because arbitrary document providers do not offer atomic compare-and-swap writes. **Close tab** on a pending-draft tab offers to keep a local recovery copy or discard the draft. Per-file local snapshots are also available from **Snapshots / diff**: create a snapshot of the current draft, compare it with the draft using the same diff view, restore it to the in-memory draft, or delete it. Snapshots stay on this device (up to ten per file, 2 MiB each). Very large differences use a streaming compact summary. Git status/history/diff remain planned. **Fold / inspect code** opens a read-only folding view for brace-based Kotlin, Java, JavaScript/TypeScript, C/C++, Go, Rust, Swift, Dart and CSS/SCSS files. Tap − on a block to fold it and + to expand it; use **Edit source** to return to the editable text field. The view reports UTF-8 bytes, line count and longest line. To keep mobile analysis bounded, folding is limited to text at most 2 MiB and 1,500,000 lines; a metric-only notice is shown outside that range. Rows are virtualized, fold targets are capped at 50,000, and nesting tracking is capped at 20,000 levels. Lines longer than 4,000 characters are clipped in the inspection view. Braces inside common quoted strings and comments are ignored, but folding is a lightweight structural aid rather than a full language parser. Larger files remain read-only, and binary/unsupported formats cannot be edited. HTML/HTM, CSS and JavaScript files also offer **Show live preview**. The preview follows the current unsaved draft and refreshes after typing pauses; CSS and JavaScript use a small sample page, while HTML is rendered as its own page. Remote images/scripts/stylesheets, linked sibling files, network requests, form submissions and navigation are blocked. Inline CSS/JavaScript works, but local linked assets are not yet resolved. The source editor stays visible above the preview; use **Hide live preview** to close the preview pane; Workspace edits auto-save after a short pause and **Save now** requests an immediate write.

Use **Find / replace** in the editor to search case-insensitively. **Find next** selects the next match and wraps to the start. **Replace match** replaces the selected match, or the next match if none is selected. **Replace all** changes non-overlapping matches in the current draft only. Changes auto-save to storage after a short pause; use **Save now** to request an immediate write.

## AI providers and execution
**Planned.** AI features are intended to use cloud providers. Show concise observable actions and verification evidence; model output alone is not proof a task succeeded.

## Approvals and security
**Planned.** Protected actions will explain capability, scope and risk. Never put credentials in chat or logs.

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
