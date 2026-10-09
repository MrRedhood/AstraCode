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
