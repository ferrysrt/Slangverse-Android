# Live dataset refresh and mixed-generation attempts — 2026-11-18 (Asia/Jakarta)

Code checkpoint at report time is on **main**; the final commit and run IDs are listed after the results below. App build configuration remains `0.5.0-mixed-feed`, version code 6.

## Actual dataset results

- [Refresh run 44142297195](https://github.com/ferrysrt/Slangverse-Android/actions/runs/44142297195) succeeded (1 Firecrawl search + 1 OpenRouter normalization, no retries): 9 scraped pages, 4 accepted, 4 rejected. Its audit files were initially written to the wrong relative path, so they were not committed.
- The `DATASET_AUDIT_DIR` path was corrected and [refresh run 44142990007](https://github.com/ferrysrt/Slangverse-Android/actions/runs/44142990007) was repeated: **succeeded**, 9 pages, **2 accepted, 9 rejected**, HTTP 200 on both provider stages, 17.7 s wall time. Ten audit files were persisted under `backend/dataset-runs/<runId>/` and uploaded as artifact `normalized-slang-dataset-44142990007`: input, firecrawl/http/request, bounded sources, normalization request/response/http, rejections, and a terminal `result.json` with `status: succeeded`. A secret scan of that artifact found no keys or bearer values.
- The committed dataset is `version: 3`, `stage: normalized_dataset`, **22 entries** (16 carried forward + 6 newly accepted across the two runs), all `normalized-unreviewed`. It covers three distinct source pages. `publicCatalog()` reports `datasetVersion firecrawl-normalized-1681a4e41cc4fb87c5039158`, 22 senses, ordered newest `lastCollectedAt` first. Repeated terms (`scuba`, `crine`) have distinct definitions and therefore distinct sense IDs — they were not merged.

## Actual mixed-generation results

Three bounded publisher runs, one paid request each, no retries or JSON repair: [v1 44143393023](https://github.com/ferrysrt/Slangverse-Android/actions/runs/44143393023), [v2 44144286530](https://github.com/ferrysrt/Slangverse-Android/actions/runs/44144286530), [v3 44144857363](https://github.com/ferrysrt/Slangverse-Android/actions/runs/44144857363).

| Version | Provider | Critical nine | Additional gates | Outcome |
| --- | --- | --- | --- | --- |
| `mixed-v1` | 1 call, HTTP 200, `stop`/`completed`, 5,200+3,214 tokens, provider-reported cost 0.002256925 | 9/9 pass | `reference-integrity`: `7:post-slang-missing` | rejected |
| `mixed-v2` | 1 call, HTTP 200, `stop`/`completed` | 9/9 pass | `existing-question-rules`: 4 × `question-comment-shape` | rejected |
| `mixed-v3` | 1 call, HTTP 200 | 9/9 pass | `reference-mismatch`, `key-meaning-mismatch`, `unused-reference` on question 5 | rejected |

**Published mixed packs: 0.** No pack entered `question-bank/packs/`; the bank still advertises the six retained legacy senses and its catalogue was not changed by these failed attempts. Three terminal attempts are archived with rendered prompts, non-secret request parameters, raw output/response, provider metadata, named checks and results in `backend/experiments/`; `summary.json` and `attempts.csv` aggregate them. See [prompt engineering](PROMPT_ENGINEERING.md) for the v1→v2→v3 hypotheses and the aggregate interpretation.

Backend local `npm test` after the final prompt change: **165 passed, 0 failed/cancelled/skipped**. Source guards: **12 passed, 0 failed**. Android/JUnit results come from the earlier successful [build run 44140097650](https://github.com/ferrysrt/Slangverse-Android/actions/runs/44140097650) (**48 tests, 0 failures/errors/skipped**); no new Android code changed in these live runs.

## Not established

No live generation has passed all gates, so no mixed pack, generation success rate or prompt-improvement claim exists. All returned content remains provisional and was rejected. Phone/device acceptance, expert semantic review and participant evaluation remain undone.

## Previous checkpoint verification

# Replies and source-backed slang verification — 2026-11-17 (Asia/Jakarta)

Code/content checkpoint `0e1c381` on **main**. The app build configuration remains `0.4.0-published-feed`, version code 5; this run built updated code/content, not a newly numbered release.

## Actual results from this session

- Backend full suite: **104 passed, 0 failed/cancelled/skipped** in a temporary sandbox using the current backend/editorial bank and canonical Git bytes for unchanged packs. The successful [backend CI run 44091691365](https://github.com/ferrysrt/Slangverse-Android/actions/runs/44091691365) also ran the suite and legacy demo export. Existing three mock packs remain separate from the expanded LLM bank.
- A direct backend run in this Windows checkout failed the existing published-pack hash check because Git's automatic CRLF checkout differs from the LF bytes served by GitHub Raw. That is not presented as a passing direct local run. No unchanged legacy pack was rewritten to mask it. Sandbox validation used canonical blobs; staged pack hashes and public HTTPS bytes were independently checked.
- Replies source guards: **4 passed, 0 failed**. Checks cover default-open state, per-post saveable toggles, unchanged explicit answer composer, legacy reset defaults and all supported training comments. These source checks do not replace Compose interaction tests.
- [Android build run 44091691386](https://github.com/ferrysrt/Slangverse-Android/actions/runs/44091691386) on `0e1c381`: **successful** backend/reply/avatar checks, `:app:testDebugUnitTest`, `:app:assembleDebug`, and APK upload. Downloaded the current `android-unit-test-results` artifact and counted XML: **29 tests, 0 failures, 0 errors, 0 skipped** — ContextDetectiveGameTest 7, ProfilePicturesTest 2, PublishedFeedTest 9, SocialFeedGameTest 5, SocialNarrativeTest 6. Published tests parse the actual six-pack bank and cover supported reply counts, exact editorial disclosure and unchanged grading/XP. Artifact **slangverse-debug-apk** contains the installable APK.
- No local Android Gradle build was attempted: only Java 8 is available here and Gradle is absent. Android compilation and packaging results above are from GitHub Actions, not this Windows machine. No independent new APK signature/device installation test is claimed.
- Discovery [run 44089717679](https://github.com/ferrysrt/Slangverse-Android/actions/runs/44089717679): **success**, one Firecrawl search and one OpenRouter normalizer call, no retries. **16 accepted, one rejected**, persisted in JSON/CSV by `60fb641`. Actual capture time `2026-11-17T13:08:28.914Z`; all accepted excerpts came from one UPrinting glossary, not sixteen independent reference pages. Existing GitHub secret names were verified without printing values; no repository Actions variables were configured.
- Explicit source/safety mappings in `a68f019` added `glow-up-improvement`, `rizz-flirting`, `iykyk-inside-reference`. Candidate rows remain unreviewed; playable references are provisional. The cooked legacy meaning conflict remains visible and unmapped to the new exhaustion candidate.
- Publisher [run 44090251900](https://github.com/ferrysrt/Slangverse-Android/actions/runs/44090251900): **success**, actual result `attempted:3`, `published:3`, `available:6`, `failures:[]`; generated files committed in `d70dbe1`. No generation retries or phone-triggered paid requests. Each pack has one sense, ten questions and nominal 5 easy / 3 medium / 2 hard.
- An agent-assisted read of all 30 new items found one overlapping IYKYK distractor and several ambiguous rizz contexts. `0e1c381` corrects them editorially, preserves LLM origin/model/time, records item-level reasons in `editorialReview`, and shows **agent-edited** in the feed for those two packs. A targeted second review found no remaining high/medium issues in the corrected items; this is **not** linguistic expert validation. No extra paid generation was used for corrections.
- Final public HTTPS GET: catalogue and **all six packs HTTP 200**, SHA-256 matched exact downloaded bytes, all passed backend structural/reference checks. JSON and CSV dataset URLs also returned **200**. Final bank version `50455fdfb66bd3ab`, six senses / **60 questions**; the original three valid packs remain intact. New sourced packs retain candidate IDs, normalized definitions and reviewed source quotations.
- `git diff --check` passed after CSV export record separators were made LF (quoted multiline source text is preserved). Project diagnostics reported no errors/warnings.

## Remaining acceptance boundaries

Phone gestures/layout, accessibility, activity recreation and mobile-network behavior remain untested in this session. On a device: verify replies are initially visible and collapsible without auto-opening choices; inspect up to five replies; use **Refresh bank** to select all six slang chips; check the edited-source labels; answer right/wrong and revisit packs without duplicate XP. Current pack hash changes intentionally isolate revised answers/progress.

All reference and generated content remains provisional. This session does not establish semantic expert approval, empirical difficulty calibration, learning gains, participant usability or durable/authenticated research logging. Source discovery and schema validation are not substitutes for those evaluations.

## Previous checkpoint verification

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
