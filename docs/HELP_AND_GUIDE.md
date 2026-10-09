# AstraCode Help & Guide

This file is the source for the in-app **More → Help & Guide** experience as that screen is implemented. Keep each topic aligned with shipped behavior; label planned features as planned until available.

## Getting started
AstraCode is designed for coding and project workflows from an Android device. The current build is a foundation shell; some areas in More are still planned.

## Workspace and files
**Planned.** Workspace access and editing are being implemented. When available, select a project folder through Android's system picker and grant access only to the workspace you intend to use.

## Editor and recovery
**Planned.** The editor will distinguish saved and unsaved changes, explain autosave behavior, and provide recovery/snapshot access. Large-file mode and preview must show clear limitations or error states rather than silently dropping content.

## AI providers and execution
**Planned.** AI processing uses configured cloud providers; the app will not require local model inference. Provider/model settings will share one source of truth. Execution views will show observable actions, affected files and verification results, not private chain-of-thought. A generated statement that work succeeded is not a substitute for a real build/test/read-back result.

## Approvals and security
**Planned.** Protected actions will explain the requested capability, affected paths and risk. Approval can expire. A denial or expiry must prevent the protected operation. Credentials must never appear in chat transcripts, diagnostics or logs.

## Git, builds and delivery
**Planned.** Show remote/local status, diffs, workflow logs, artifacts and build outcomes with timestamps and clear failure messages. Do not label a build or release ready until verification succeeds.

## Settings
**Planned.** Settings are being organized in the searchable More index: Appearance & Accessibility, Editor, AI & Models, Git & GitHub, Build & CI, Security & Notifications, Privacy & Storage, and About & Help. Most entries remain navigation placeholders until their features are implemented. Each shipped setting must document scope, default, validation and security implications where relevant.

## Troubleshooting
- **Workspace unavailable:** reselect the folder and grant access again.
- **AI request failed:** check provider selection and connection status; retry only after reviewing any partial actions.
- **Approval expired:** review the action again and approve only if its scope and purpose are clear.
- **Build or workflow failed:** inspect the exact error log and test report; do not assume a retry fixes the underlying issue.
- **Unsaved changes or interruption:** use recovery/snapshot features when available and verify the restored file before continuing.

## Reporting a bug
Include AstraCode version, Android version, reproducible steps, expected and actual behavior, and sanitized logs. Remove API keys, tokens, private source code and personal data before submitting reports.

## Navigation and responsive layout

The main destinations are Chat, Code, Git, Build and More. Phones use bottom navigation; wider layouts use a navigation rail. Selection is saved across activity recreation, and Android Back returns from a More detail or Help & Guide page to the More index. The More index supports searching entries by title, category and summary, includes an empty result state, and labels unfinished entries as planned rather than presenting them as complete features.

## Interface icons and appearance

The primary navigation uses AstraCode's custom-drawn Compose icons rather than generic text glyphs. The icon language combines cosmic cyan and blue with amber-gold and violet accents; icon colors adapt to the system dark/light theme. Icons are rendered locally and require no remote assets or additional icon dependency.

## App icon

The Android launcher icon uses the exact user-provided PNG at `app/src/main/res/drawable-nodpi/astracode_logo.png`. Both `android:icon` and `android:roundIcon` reference `@drawable/astracode_logo`; Android's resource lookup omits the `-nodpi` directory qualifier. The previous vector artwork is retained as `app/src/main/res/drawable-nodpi/astracode_logo_vector.xml` under a distinct resource name, avoiding a collision with the PNG. Verify launcher appearance after building and installing the updated APK.
