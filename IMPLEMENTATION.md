# Implementation Log

## 2026-10-09 — Apache License 2.0 documentation

- Confirmed the repository-root `LICENSE` file already contains the Apache License 2.0 text; it was preserved unchanged.
- Added a root `README.md` describing AstraCode, identifying `Apache-2.0` as the software license, and linking to `LICENSE`.
- Added a branding clarification explaining that the software license does not itself grant general rights to use AstraCode's name or logos or to imply an unofficial fork is an official release.
- Added a third-party component reminder to review and preserve applicable dependency and asset licenses and attribution notices.
- Documented the intended license for contributions.
- No `NOTICE` file was added because no project-specific third-party attribution requirements were verified during this change.
- No source-code copyright headers were added because no source files or their authorship were verified as part of this change.
- Validation performed: verified the existing `LICENSE` file and the new files through the GitHub repository file API. CI/build/UI workflows were not run as part of this documentation-only change.

## 2026-10-09 — Android bootstrap foundation

- Added a minimal Kotlin/Jetpack Compose app shell with the proposed Chat, Code, Git, Build and More destinations.
- Added Android Gradle configuration, manifest and an initial unit smoke test.
- Added Android CI, emulator UI smoke and manually triggered release-validation workflows. They are intended to keep signing secrets out of pull-request builds.
- Validation limitation: the repository does not yet include a Gradle wrapper or wrapper JAR. CI must install a pinned Gradle distribution directly until a verified wrapper is added. No workflow has been run yet; green status is not claimed.
- Scope note: the five destinations are scaffolding with descriptive placeholders, not completed feature implementations. The detailed feature roadmap remains staged in `docs/ROADMAP.md`.
- Added Compose instrumentation smoke tests for the five primary navigation labels and More destination content. CI workflow files were re-fetched and checked for pull-request triggers and pinned Gradle setup. The latest commit has no reported combined status checks yet; build/UI success remains unverified.

## 2026-10-09 — Release keystore signing integration

- Configured `app/build.gradle.kts` to read release-signing configuration from environment variables rather than storing credentials in source control.
- Updated the manually triggered release-validation workflow to require the four repository signing secrets, decode `ANDROID_KEYSTORE_BASE64` into the runner's temporary directory, and build/upload the signed release APK.
- The signing configuration remains optional for local and pull-request builds; the release-validation workflow explicitly fails if any required secret is missing.
- Security handling: the keystore is written to the runner temporary directory and is not committed or printed in workflow logs.
- Validation status: workflow and Gradle configuration were updated through GitHub's repository API. The release workflow has not yet been run, so successful signing and artifact generation are not yet verified.

## 2026-10-09 — CI Kotlin compilation fix

- Fixed the compiler error reported by Android CI in `MainActivity.kt`: removed the invalid explicit import of `androidx.compose.foundation.layout.weight`. The `weight` modifier is available through the `ColumnScope` receiver at its call site.
- Validation: the fix was committed directly to `main`; the CI workflow triggered by this commit must finish before build success can be claimed.

## 2026-10-09 — Android UI instrumentation runner diagnostics

- The reported UI workflow compiled both app and instrumentation APKs, but Android instrumentation exited before reporting any tests (0/0); the log did not include a fatal exception or a test assertion.
- Added explicit AndroidX Test runner and rules dependencies to make the configured `AndroidJUnitRunner` runtime dependencies unambiguous.
- Validation: a new emulator workflow run is triggered by this commit. This is a targeted dependency fix, not a claim that instrumentation now passes; inspect the next run and its reports/logcat if it still exits before tests start.

## 2026-10-09 — UI emulator stability adjustment

- Inspected the failed UI workflow's uploaded test report: both app and test APKs installed, but instrumentation terminated before executing any tests (0 tests reported); the report also recorded permission-denied attempts for the Android 15 emulator's additional-output directory, without a useful application exception.
- Changed the UI smoke workflow to use the API 34 x86_64 Pixel 2 emulator and explicitly disable animations and emulator snapshots, using software rendering and no boot animation/audio to reduce emulator/instrumentation instability.
- This is a diagnostic environment adjustment, not a claim that UI tests pass. The new `main` workflow run must complete before success is reported.

## 2026-10-09 — Custom AstraCode UI icons

- Added `AstraIcon.kt`, a local Jetpack Compose Canvas icon family with distinct custom motifs and cyan, blue, amber-gold and violet accents.
- Replaced the five primary navigation's placeholder text glyphs with bespoke Chat/orbit, Code/prism, Git/node, Build/cube and More/diamond icons; the selected destination card also uses the matching custom icon.
- Icon colors adapt to system dark/light theme. No remote icon assets or additional dependency was introduced.
- Kept the existing in-app navigation icon system unchanged while updating the launcher artwork separately.
- Updated `docs/HELP_AND_GUIDE.md` to document the visual icon system and clarify that the current destinations remain scaffolding.
- Validation status: source and documentation changes committed directly to `main`. CI and UI workflows have not been checked after these commits; compilation and emulator rendering remain unverified.

## 2026-10-09 — Exact PNG launcher icon

- Added the user-provided launcher artwork as `app/src/main/res/drawable-nodpi/astracode_logo.png`; the PNG is the launcher asset, not a regenerated or vector-traced substitute.
- Updated `AndroidManifest.xml` so both `android:icon` and `android:roundIcon` reference `@drawable/astracode_logo`. Android resource references use the resource name and omit the `-nodpi` qualifier.
- Kept the prior `astracode_logo.xml` drawable in place as a fallback/source asset; the manifest now selects the PNG resource with the same resource name in the `drawable-nodpi` directory.
- Updated Help & Guide to describe the exact PNG launcher asset and the unchanged in-app navigation icons.
- Validation status: manifest and documentation committed to `main`; Android CI and UI workflows must complete before build success or on-device appearance can be confirmed.

## 2026-10-09 — Android CI duplicate launcher resource fix

- Diagnosed the failed Android CI run: Android resource merging reported a duplicate `astracode_logo` because the PNG and XML drawable shared the same resource name in `drawable-nodpi`.
- Renamed the legacy vector artwork to `astracode_logo_vector.xml` so the user-provided `astracode_logo.png` remains the unambiguous launcher resource.
- Kept both manifest icon attributes on `@drawable/astracode_logo` and retained the vector artwork under a distinct resource name.
- Validation status: fix committed to `main`; rerun Android CI and UI smoke workflows and inspect their completed results before claiming success.


## 2026-10-09 — Navigation hub and responsive app shell

- Advanced roadmap P2 by retaining the five primary destinations and adding an adaptive layout: bottom navigation on phone-sized screens and a navigation rail on wider screens.
- Expanded More into a searchable categorized index for workspace/editor, AI/automation, Git/delivery, quality/security, settings and support. Search matches title, category and entry summary and includes a clear empty state.
- Added functional Help & Guide content inside the app and a Back-to-More path. Hardware/system Back also returns from nested More screens to the index.
- Saved the selected primary destination, nested More entry and search query through activity recreation with Compose saveable state.
- Kept unfinished More entries explicitly labeled as placeholders; no planned feature is represented as completed.
- Added UI instrumentation coverage for the five primary destinations, More search filtering and opening Help & Guide.
- Updated `docs/HELP_AND_GUIDE.md` and `docs/ARCHITECTURE.md` to describe actual navigation behavior and distinguish shipped shell behavior from planned feature areas.
- Validation status: committed directly to `main`; Android CI and the new emulator UI smoke workflow must complete on this exact commit before this milestone is considered verified.
