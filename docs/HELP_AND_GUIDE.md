# AstraCode Help & Guide

This file is the source for the in-app **More → Help & Guide** experience as that screen is implemented. Keep each topic aligned with shipped behavior; label planned features as planned until available.

## Getting started
AstraCode is intended to help developers inspect, edit, verify and ship projects from an Android device. The initial bootstrap does not yet provide a complete IDE. Setup instructions must be added after the Android shell and workspace flow are implemented and tested.

## Workspace and files
**Planned.** Project access will use Android's Storage Access Framework (SAF). Users should understand what folder they selected and what access was granted. If a grant expires or a file disappears, show a recoverable error and allow the user to select the workspace again.

## Editor and recovery
**Planned.** The editor will distinguish saved and unsaved changes, explain autosave behavior, and provide recovery/snapshot access. Large-file mode and preview must show clear limitations or error states rather than silently dropping content.

## AI providers and execution
**Planned.** AI processing uses configured cloud providers; the app will not require local model inference. Provider/model settings will share one source of truth. Execution views will show observable actions, affected files and verification results, not private chain-of-thought. A generated statement that work succeeded is not a substitute for a real build/test/read-back result.

## Approvals and security
**Planned.** Protected actions will explain the requested capability, affected paths and risk. Approval can expire. A denial or expiry must prevent the protected operation. Credentials must never appear in chat transcripts, diagnostics or logs.

## Git, builds and delivery
**Planned.** Show remote/local status, diffs, workflow logs, artifacts and build outcomes with timestamps and clear failure messages. Do not label a build or release ready until verification succeeds.

## Settings
**Planned.** Settings will be searchable and grouped by appearance/accessibility, editor, AI, tool permissions, Git/GitHub, build/CI, terminal, automation, privacy/storage, security/notifications and about/help. Each setting will document scope, default, validation and security implications where relevant.

## Troubleshooting
- **Workspace unavailable:** reselect the folder and grant access again.
- **AI request failed:** check provider selection and connection status; retry only after reviewing any partial actions.
- **Approval expired:** review the action again and approve only if its scope and purpose are clear.
- **Build or workflow failed:** inspect the exact error log and test report; do not assume a retry fixes the underlying issue.
- **Unsaved changes or interruption:** use recovery/snapshot features when available and verify the restored file before continuing.

## Reporting a bug
Include AstraCode version, Android version, reproducible steps, expected and actual behavior, and sanitized logs. Remove API keys, tokens, private source code and personal data before submitting reports.
