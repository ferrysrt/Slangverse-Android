# Implementation status — mixed-feed transition

## Current accepted contract

The user-approved plan uses the own Firecrawl-normalized JSON/CSV, automatic provenance-based `normalized-<candidateId>` eligibility, and pre-generated mixed packs in Home. There is no manual review gate or active slangfeed snapshot. The initial 16 entries are carried forward from the recorded capture; a fresh refresh is pending. Weekly refresh is Monday 03:00 UTC / 10:00 WIB.

| Area | Implemented source behavior | Boundary / remaining work |
| --- | --- | --- |
| Dataset/catalogue | Own dataset, capture ordering, literal sources, automatic normalized senses | Literal provenance is not semantic validation; no fresh collection success asserted |
| Mixed publisher | `publish-mixed-bank.mjs` and `src/mixed*.mjs`; default one, maximum three attempts; no retries/repair/mock fallback | Real mixed generation/publication outcomes must come from actual artifacts |
| Prompt/validator | `prompts/mixed-v1.txt`; critical nine checks plus reference, lexical, social and completion gates | No mixed-v2 comparison, expert semantics or calibrated difficulty claimed |
| Attempt audit | Per-attempt input/prompt/request, raw output/response, metadata/validation/result as available; pack on success | Preserve actual model, usage and finish reasons; missing responses are not invented |
| Published schema | Schema 2 mixed `packs` plus retained legacy `senses`, immutable files and hashes | Existing schema 1 bank remains compatibility content until mixed publication |
| Android Home | Prefer mixed, legacy only if no mixed listed; merged feed, no slang chips, context tabs retained | Real device behavior and final build remain to be reported |
| Local practice | Shuffle each fresh load/refresh, saved stable order, pack/hash/question-scoped answers and 80/15 XP | No live phone LLM call, secure competitive score or durable participant logging |

## Integration and remaining work

- The experiment-report summary CLI (`npm run experiments:report`) and the mixed publisher workflow are wired: the workflow runs at most the selected number of attempts (default one, maximum three), aggregates real attempt evidence, uploads `backend/experiments/` and `question-bank/`, and persists available audit files and valid additions even when generation fails.
- The dataset workflow sets `DATASET_AUDIT_DIR=dataset-runs` (relative to its `backend/` working directory), so each refresh keeps bounded request/response evidence, rejections and a terminal `result.json` under `backend/dataset-runs/` even on failure.
- A live refresh has run: 22 normalized entries with persisted audit evidence. Three real mixed attempts (`mixed-v1`…`v3`) are archived; each passed the critical nine but was rejected by additional integrity gates, so **no mixed pack is published** and no success rate is claimed.
- Android `versionName: 0.5.0-mixed-feed` and `versionCode: 6` are on main; [build run 44140097650](https://github.com/ferrysrt/Slangverse-Android/actions/runs/44140097650) compiled, ran 48 unit tests with zero failures and uploaded the APK. Device acceptance is still outstanding.
- Report actual refresh/generation/device outcomes after those runs. No live mixed generation result or success rate is asserted here.

Product approval is not supervisor approval of the thesis methodology. See [accepted intent](MIXED_FEED_HANDBOOK.md), [architecture](ARCHITECTURE.md), [dataset operations](DATASET_INTEGRATION.md) and [prompt/metrics protocol](PROMPT_ENGINEERING.md).

## Historical record — replies and sourced slang expansion

The following checkpoint text is retained as an earlier record. Its manual promotion rules, counts, Home selection behavior and verification statements are not current mixed-feed claims or revalidation.

17 November 2026: Home retains the published LLM feed, with replies initially visible and individually collapsible. Answer choices still require player action. Connected Training and archived practice have matching reply defaults; expanded training displays all supported comments.

A successful bounded Firecrawl/OpenRouter workflow persisted 16 unreviewed candidates and a source CSV. Explicit agent-assisted source/safety review mapped glow up, rizz (flirting) and IYKYK into provisional playable references, alongside the three legacy senses. The publisher produced three real packs: six senses / 60 items are published. Rizz/IYKYK then received disclosed editorial corrections to clarify the flirting sense and remove a competing correct distractor; app source labels and pack metadata disclose these edits. No expert semantic validation is claimed.

Candidate-only updates do not generate questions. Source URLs, titles, literal quotes, actual capture times, page hashes and normalization model persist in `backend/data/discovered_candidates.json` and its CSV, with reviewed source copies in curated senses and new packs. The legacy 200-row snapshot remains unchanged, including the cooked meaning conflict. See [dataset operations](DATASET_INTEGRATION.md) and [current verification](TEST_RESULTS.md). Durable participant logging, expert content review, phone usability and empirical difficulty calibration remain outstanding.

## Historical checkpoint — Published Feed 0.4.0

This UI revision strengthens names/handles and separates Learning feedback from the social reply and story continuation. A shared feedback component retains source-specific disclosures in archived and connected modes. It builds on the offline CC0 portrait library, consistent avatar UI, LLM-selectable avatar IDs, normalization and legacy fallbacks in both Android training clients. Sources, licences, checksums and reproduction steps are in [the avatar folder](../assets/profile-pictures/README.md).

The social simulator has been promoted to `main`. Home now defaults to published LLM packs via GitHub Raw, with three available senses/30 generated questions. The offline hand-authored feed is explicit opt-in. Catalogue checksums, pack-scoped progress and dynamically resolved identities are implemented. A bounded publisher workflow updates the bank from playable curated senses. Durable participant response logging remains outstanding.

The previous feature branch was `feat/social-simulator-feed`. Home opens into the ten-item local practice feed. This revision adds fictional character profiles, nested replies, quoted earlier posts, context hashtag navigation, visible player replies, and a local inbox that links to exact answered conversations. The archived V3 welcome panel remains under Quests. See [checkpoint behavior and phone acceptance checks](SOCIAL_SIMULATOR_FEED.md).

The ten-question, single-sense, nominal 5/3/2 pack and 80/15 XP rules remain intact. Story follow-ups unlock once per submitted post regardless of correctness, and social actions do not affect scoring. The context quest counts categories present in the pack rather than imposing all nine on every slang.

This remains hand-authored practice with local saveable UI state. Durable participant response recording/export, generated-pack integration into this local feed, content validation and device usability evaluation are outstanding. The backend, Connected Training and archived gameplay retain their separate boundaries. The earlier checkpoint's verification is recorded in [historical test results](TEST_RESULTS.md), not as final mixed-feed verification.

## Historical V2.2 status (retained for reference)

| Part | Implemented | Independently verified here | Remaining |
| --- | --- | --- | --- |
| GitHub Actions Android cloud build workflow | Yes | Not executed | Upload to new repo, run workflow, inspect APK artifact/build logs |
| Compose 8-screen source | Yes | Inspected as source only | Android Gradle build/emulator test |
| API Training screen | Yes | Inspected as source only | Device ↔ backend integration smoke |
| Android debug network config | Yes | Inspected as XML source | Build and manifest-merger verification |
| HTTP REST mock server | Yes | Node.js automated HTTP tests pass | Rate limiting, auth and durable storage |
| 10-context curated mock pack | Yes | Test passes structural rules and unique IDs | Human check of linguistic appropriateness |
| Indirect comment hints | Yes | String leak tests pass | Semantic/hint-quality validation |
| Answer grading and XP | Yes | Server unit + HTTP tests pass | Persist progress and user accounts |
| Real LLM provider adapter | Optional source | Fake provider tests only | Real provider/model tests, audit and quota controls |
| Fallback/provenance | Yes | Dependency-injected tests pass | Persisted experiment logs and telemetry |
| Competitive leaderboard | Deliberately disabled | Endpoint test verifies disabled flag | Daily standardized question pack and auth |
| SUS | Not implemented | No participants evaluated | Consent, sampling, usability study |

**Do not report a successful APK** until it is compiled. **Do not report successful live LLM calls** until a real provider has been used and measured. Current methodology is a proposal, not final approval from advisers.
