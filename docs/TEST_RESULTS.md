# Published Feed checkpoint verification — 2026-11-16 (Asia/Jakarta)

Version `0.4.0-published-feed`, version code 5, on **main**. The social simulator was fast-forwarded to main, then the bounded publisher generated and committed three genuine GPT-6 Luna packs to `question-bank/` (generation commit `329989d`).

- HTTPS GET of the actual public GitHub Raw catalogue and all three packs returned HTTP 200 without application credential headers. All pack SHA-256 values matched the catalogue. Cooked, no cap and lowkey provide 30 questions total; generator metadata records `openai/gpt-6-luna`, prompt version and generation time. This checkpoint did invoke live generation through GitHub Actions, bounded to three requests with retries disabled.
- Backend `npm test`: **54 passed, 0 failed/cancelled/skipped**. New checks cover multi-sense publication, exact-byte hashes, mock/fallback rejection, call limits, skipping current packs, preservation during failures, invalid-pack exclusion and the real checked-in bank.
- Android `:app:testDebugUnitTest :app:assembleDebug`: **BUILD SUCCESSFUL; 25 passed, 0 failures/errors/skipped**, verified from current XML. New tests parse the actual published catalogue/packs, adapt them into social conversations, reject default mock content and stale/wrong-slang packs, verify per-pack progress and revision isolation, and handle an empty published catalogue without a demo substitution.
- APK signature verification passed. APK metadata confirms `0.4.0-published-feed`, min SDK 26, target SDK 36.
- `git diff --check` passed.

These checks establish real published JSON availability, parser/model behavior and packaging. They do not establish Android rendering/navigation, mobile-network behavior, semantic correctness, calibrated difficulty or participant-study readiness. Phone acceptance is still required. Generated content is provisional and static grading remains local practice; durable authenticated response logging is not implemented.

## Previous checkpoint verification

# UI Hierarchy checkpoint verification — 2026-11-16 (Asia/Jakarta)

Version `0.3.2-ui-hierarchy`, version code 4, branch `feat/social-simulator-feed`.

- Android `:app:testDebugUnitTest :app:assembleDebug`: **BUILD SUCCESSFUL; 20 passed, 0 failures/errors/skipped**, read from the current JUnit XML.
- APK signature verification passed; APK metadata confirms `0.3.2-ui-hierarchy`, min SDK 26 and target SDK 36.
- `git diff --check` passed.
- Source inspection confirms existing 44dp author / 30–32dp reply / 80dp profile portraits, stronger author typography, 12sp handles/metadata and a distinct Learning feedback component. In the feed the grading component is outside the social comment container, before the separate fictional follow-up. Archived and Connected Training source/grading disclosures are retained.
- No new mirror-of-layout unit tests were added. Existing tests protect scoring, pack composition, identity mapping and avatar metadata. Backend files and the avatar catalogue were unchanged and were not retested for this UI-only revision.

These results establish compilation, regression checks and APK packaging, not rendered device layout, navigation, large-font behavior or a complete accessibility audit. Perform the phone checks in [the current checkpoint](SOCIAL_SIMULATOR_FEED.md). GitHub Actions completion and device usability remain unverified here.

## Previous checkpoint verification

# Profile Pictures checkpoint verification — 2026-11-16 (Asia/Jakarta)

Version `0.3.1-profile-pictures`, version code 3, branch `feat/social-simulator-feed`.

- `npm ci --prefix tools/profile-pictures` and `npm --prefix tools/profile-pictures run check`: passed. Confirmed deterministic SVG output, PNG dimensions and SHA-256 checksums, canonical catalogue metadata, and generated Android/backend registries for 22 portraits.
- Visually inspected the generated contact sheet: distinct illustrated faces, readable at thumbnail scale, consistent warm backgrounds. This is asset inspection, not an app screenshot or device usability test.
- Android `:app:testDebugUnitTest :app:assembleDebug`: **BUILD SUCCESSFUL; 20 passed, 0 failed/errors/skipped**, verified from the current JUnit XML. Two new tests exercise canonical cast identity, handle normalization, ID allowlisting and cross-platform legacy fallback behavior.
- Backend `npm test`: **48 passed, 0 failed/cancelled/skipped**. Five new tests cover asset/catalogue integrity, normalization without question mutation, consistent identity within packs, public API/static export metadata and an injected LLM response with malformed cosmetic metadata. No paid LLM request occurred.
- APK signature verification passed. APK inspection found all 22 bundled PNGs and confirmed version `0.3.1-profile-pictures`, min SDK 26, target SDK 36.
- Backend-only deployment smoke passed: copied only `backend/src` and `backend/data` into an isolated temporary directory and created a mock session with valid avatar metadata. Root-level picture files are not required by the server at runtime.
- `git diff --check`: passed.

Live provider/avatar-selection behavior, GitHub Actions completion, phone rendering, accessibility and navigation were not established by these checks. The phone checklist in [the feed document](SOCIAL_SIMULATOR_FEED.md) remains applicable. Durable participant research logging remains outstanding.

## Previous checkpoint verification

# Social Stories checkpoint verification — 2026-11-16 (Asia/Jakarta)

Version: `0.3.0-social-stories`, version code 2, branch `feat/social-simulator-feed`.

Executed from `android/` after activating the cloud toolchain:

```sh
gradle --no-daemon :app:testDebugUnitTest :app:assembleDebug
```

Result: **BUILD SUCCESSFUL**. Current JUnit XML reports **18 passed, 0 failed, 0 errors, 0 skipped**:

- `ContextDetectiveGameTest`: 7 tests (archived gameplay/scoring regression checks).
- `SocialFeedGameTest`: 5 tests (pack composition, category filters, once-per-post XP and bounded progress).
- `SocialNarrativeTest`: 6 tests (profile references, nested reply parents, quoted evidence, submission-gated updates, exact inbox targets, and hashtag routes).

The rebuilt APK passed `apksigner verify`. `aapt dump badging` confirmed application `id.slangverse.app`, version `0.3.0-social-stories`, minimum SDK 26 and target SDK 36. `git diff --check` passed.

These checks validate the model, compilation, packaging and signature. They do not establish rendered layout, gesture/navigation behavior, accessibility or saveable-state behavior on a device. Use the [phone acceptance checklist](SOCIAL_SIMULATOR_FEED.md#social-stories-phone-acceptance-checks). GitHub Actions completion is not established by this local run. No paid LLM call, backend deployment, participant study or durable research logging is claimed. Backend files were not modified or retested for this checkpoint.

## Historical verification records

# Verification in current environment — 2026-11-11 (Asia/Jakarta)

## Backend

Executed in `backend/`:

```bash
npm test
```

Result: **17 passed / 0 failed**. Categories:

- 10 unique `contextId` records + structural 5:3:2 nominal difficulty ratio.
- Public question strips keys, reference meaning and explanation.
- Reject duplicate context IDs, invalid options, literal comment answer leakage, repeated answers.
- Server grading, accumulated XP and session progress.
- Live HTTP create-session → grade-answer → progress → duplicate/invalid submissions.
- Explicit non-competitive leaderboard.
- Fake provider injection (valid JSON, invalid JSON, timeout) with retry/fallback provenance.
- Fake OpenAI-compatible HTTP adapter request shape and unsafe URL rejection.

**Tested with simulated/fake provider only.** No real LLM request was made.

Also ran a small development-only benchmark against the locally running server:

```bash
RUNS=5 npm run benchmark
```

Result: 5 successes (all `source=mock`), 0 failures; HTTP mean approximately 12.74 ms and p95 approximately 51.7 ms *in this local test environment*. These are **not LLM generation latency measurements** and not suitable as thesis results for real-time generation.

## Android

The following were authored and checked as source:

- `SlangverseApp.kt`: original 8 Compose screens plus entry button to API mode.
- `RemoteTrainingPanel.kt`: session creation, 10-question loop, expanded indirect comments, submission, server feedback, XP and final result.
- `SlangverseApi.kt`: HTTP client and typed question models.
- Android debug manifest permits HTTP only for local development, main manifest declares INTERNET permission.

**Android not compiled or run**: this environment has Java but no Android SDK, Gradle installation, or authentic `gradle-wrapper.jar` and cannot download dependencies. An Android SDK build is required before asserting functionality on a device or generating an APK. A **GitHub Actions** cloud build workflow was added but has **not been executed** in this session.
