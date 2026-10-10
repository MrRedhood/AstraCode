# AstraCode Help & Guide

Keep this content aligned with actual behavior and mark unfinished areas as planned.

## Getting started
AstraCode is an early Android coding-workflow foundation. Some destinations are placeholders while their features are implemented.

## Workspace and files
Open **Code → Choose project folder** and select a folder in Android's system picker. AstraCode remembers the selected tree URI and persistent read permission where supported. Browse subfolders, filter and refresh the listing.

Use **New file** or **New folder** to create items. Each row's **Actions** menu offers Rename, Move and Delete. To move an item, select Move, navigate into its destination and tap **Move here**. Moving into the current parent or moving a folder into itself/its descendants is blocked. Providers may not support every mutation; check errors rather than assuming success.

Open up to eight recognized text/code files as tabs. Switch tabs from the horizontally scrollable tab strip; each tab retains its current draft and selection, and an asterisk marks unsaved edits. **Back to files** keeps the tab open. **Close tab** on a dirty tab offers to keep a local recovery copy or discard the draft. Select **Save file** to write changes to the workspace. A separate app-private recovery copy is saved on-device after a short pause and restored only if the file on storage still matches its saved baseline. Drafts above 256 KiB are not recoverable. This does not auto-save to the workspace file. Per-file local snapshots and a bounded text comparison are available from **Snapshots / diff**. Create a snapshot of the current draft, compare that saved copy against the current draft, load a snapshot into the draft after confirming, or delete a stored snapshot. Snapshots are stored only on this device (up to ten per file, 256 KiB each). The diff switches to a compact summary for very large inputs. Workspace-file autosave and shared Git diff are not implemented yet. **Fold / inspect code** opens a read-only folding view for brace-based Kotlin, Java, JavaScript/TypeScript, C/C++, Go, Rust, Swift, Dart and CSS/SCSS files. Tap − on a block to fold it and + to expand it; use **Edit source** to return to the editable text field. The view reports UTF-8 bytes, line count and longest line. To keep mobile analysis bounded, folding is limited to text at most 256 KiB and 12,000 lines; a metric-only notice is shown outside that range. Braces inside common quoted strings and comments are ignored, but folding is a lightweight structural aid rather than a full language parser. Larger files remain read-only, and binary/unsupported formats cannot be edited.

Use **Find / replace** in the editor to search case-insensitively. **Find next** selects the next match and wraps to the start. **Replace match** replaces the selected match, or the next match if none is selected. **Replace all** changes non-overlapping matches in the current draft only. Use **Save file** to write changes to storage.

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
- File is read-only: provider write support may be absent, the format unsupported, or the file above the 256 KiB limit.
- Unsaved edits: use Save before leaving or confirm discard.
- Build/workflow failed: inspect the real logs and include sanitized reproduction steps in reports.

## Reporting a bug
Include app/Android version, steps, expected and actual behavior, and sanitized logs. Remove keys, tokens, private source and personal data.

## Navigation and launcher
Phones use bottom navigation; wider layouts use a navigation rail. More includes search and Help & Guide. The launcher uses the exact `app/src/main/res/drawable-nodpi/astracode_logo.png`; manifest references `@drawable/astracode_logo`. The old vector is retained with a distinct resource name.
