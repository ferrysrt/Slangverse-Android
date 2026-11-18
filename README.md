# Slangverse Android — mixed social feed

**Current direction:** the user-approved plan uses the project's own Firecrawl-normalized slang dataset and pre-generated mixed-slang question packs. Source implementation is in progress toward planned `0.5.0-mixed-feed`, Android version code `6`, to be set by main. This is not a release, test-result or successful live-generation claim. This repository is separate from `ferrysrt/slangfeed`.

Slangverse is an educational Android game with a **fictional social-media-inspired interface**. Players interpret slang from posts and indirect reply hints. Product approval does not establish supervisor approval of the research methodology, semantic validity, calibrated difficulty or learning gains.

## Current pipeline and Home behavior

- **Own dataset:** `backend/data/discovered_candidates.json` and its source CSV initially retain 16 entries from the recorded collection; a fresh refresh is pending. Provenance-eligible normalized sense IDs become automatically playable, with **no manual review gate**. The old slangfeed snapshot is historical, not active input. Literal provenance is not semantic approval.
- **Refresh:** the [dataset workflow](.github/workflows/slang-candidates.yml) runs Monday **03:00 UTC / 10:00 WIB**, or manually, bounded to one Firecrawl search and one normalization request without retries.
- **Mixed publishing:** `backend/scripts/publish-mixed-bank.mjs`, `backend/src/mixed*.mjs` and `backend/prompts/mixed-v1.txt` implement default **one** attempt, maximum **three**, no retries/repair/mock fallback. Packs contain ten questions, prefer ten unique terms, require at least two, and use nominal 5 easy / 3 medium / 2 hard labels. Each question links to its own source-backed reference and one of nine context categories; categories/scenarios may repeat.
- **Distribution:** schema 2 catalogues advertise mixed `packs` plus retained legacy `senses`. Old schema 1 packs remain compatible; implemented schema support is not proof new mixed packs are already public. Home prefers mixed packs, using legacy only when no mixed packs are listed—not on mixed-loading failure.
- **Browsing:** Home merges selected packs, with **no slang chips** and context tabs retained. Fresh loads/explicit refreshes shuffle once; saved order stays stable through navigation, recomposition and supported restoration. Pack ID/content hash/question ID isolate answers and local 80/15 XP. Refreshing does not award XP again.
- **No live phone LLM:** Home only GETs [public question JSON](https://raw.githubusercontent.com/ferrysrt/Slangverse-Android/main/question-bank/catalog.json). It needs no backend or provider token. Loading/errors are explicit; a failed refresh can retain the previous published feed. The authored sample is opt-in under **Question source**, and Context Detective stays archived under Quests.

Per-attempt evidence under `backend/experiments/` records actual input, rendered prompt, request, raw output/response, model/usage/finish reasons, validation and result as available; successful generation adds a pack. `mixed-v4` is the active baseline; v1–v4 are four real archived attempts, each rejected by a validation gate. Critical-nine syntax checks plus integrity/social/completion gates do not prove semantics.

**Integration status:** the experiment-report CLI, mixed publisher and batch grower are wired, so real attempts (including failures and incomplete runs) are aggregated, uploaded and persisted with any valid additions. A live dataset refresh is complete (22 normalized entries with persisted audit evidence). The published bank on `main` now indexes **two mixed packs / 20 questions**, covering every distinct slang term in the dataset once; the batch grower, term planner and their paid-attempt cost are recorded in `docs/TEST_RESULTS.md`. Locally, 179 backend tests and 66 JUnit tests pass with 0 failures, and the JUnit suite parses the checked-in catalogue. The publisher runs up to three attempts per workflow run and stops early once one pack passes validation. Device acceptance covers one physical Xiaomi device; release signing, store packaging and multi-device coverage remain outstanding. See `docs/TEST_RESULTS.md`.

Start with [accepted intent and acceptance checklist](docs/MIXED_FEED_HANDBOOK.md), [architecture](docs/ARCHITECTURE.md), [dataset operations](docs/DATASET_INTEGRATION.md), [bank operations](docs/GITHUB_GET_QUESTIONS.md), [prompt/metrics protocol](docs/PROMPT_ENGINEERING.md) and [implementation status](docs/IMPLEMENTATION_STATUS.md). Public static grading is local practice, not authenticated participant recording. [Cloud setup](docs/CLOUD_PLAY.md) is only for the separate optional Connected Training backend, not default Home.

## Historical UI checkpoints

The following V3/0.3.x notes describe earlier authored or single-sense checkpoints, not the current mixed publisher, Home selection rules or final verification. Their source-specific claims remain historical.

### Historical: UI hierarchy (0.3.2)

The existing 44dp author / 30–32dp reply / 80dp profile portrait hierarchy is retained. Author names now use stronger 16sp type, handles and story metadata use readable 12sp text, and secondary text has darker contrast on the warm backgrounds. The social feed shows the player's reply in the conversation, a distinct bordered **Learning feedback** panel for grading and explanation, then a separate scripted story continuation. The same feedback component is used in archived practice and Connected Training with their respective source/grading disclosures. Scoring and question data are unchanged.

### Historical: profile pictures (0.3.1)

The recurring cast now has consistent Open Peeps portraits (Pablo Stanley, CC0), rendered with DiceBear (MIT). Post authors use 44dp pictures, reply/quote identities use 30–32dp pictures, and character profiles show a larger portrait. The same images appear in the inbox, player replies, archived practice and Connected Training. All pictures are bundled for offline use.

The [profile-picture folder](assets/profile-pictures/README.md) contains 22 portraits, SVG sources, a preview sheet, checksummed catalogue and licence notices. Generated content can select an optional `avatarId` from the prompt's allowed list; Android resolves it to a local picture and older packs receive stable fallbacks. This adds presentation metadata only, without changing question count, scoring or research claims.

### Historical: authored social simulator feed

The earlier offline sample opens directly into fictional posts, with **For you**, **News** (fictional event reactions), and the other eight proposal context filters. Players interpret slang inside a comment thread, earn practice XP once per post, bookmark conversations, and review actual local progress in Quests and Progress. The finite local practice feed demonstrates ten contexts for one `cooked` sense with a nominal 5 easy / 3 medium / 2 hard split; these examples are hand-authored, not generated or expert-validated.

The **Social Stories** update adds tappable character profiles, nested reply targets, five quoted earlier posts, context hashtags, visible player replies, and a bell-button inbox with one scripted follow-up per answered conversation. These features do not add questions or affect XP.

The previous **CONTEXT DETECTIVE · V3** welcome panel is archived under **Quests → Archived: Context Detective · V3**. Connected Training remains separate and source-labeled. See [proposal alignment, interactions, and validation](docs/SOCIAL_SIMULATOR_FEED.md).

### Historical: V3.1 Interactive Comment Quiz

The question now lives as a **special @confuseduser reply** inside the fictional social thread. Tap **✳ DECODE** / **REPLY WITH THE MEANING** to expand four answer options inline, post your interpretation, read feedback in the same comment, and continue to the next conversation. Ordinary replies remain optional context hints; saving a clue does not affect grading.

- Native Compose supports both **three-slang offline sample gameplay** and **source-labeled API Training** (live backend/GitHub GET).
- The optional `questionComment` field is generated by the updated prompt or present in curated mock packs. Legacy question JSON remains readable.
- No new live OpenRouter generation or physical-phone usability test is claimed for V3.1. The prior MagicPath V3 canvas is unchanged because its API quota was reached.

See [V3.1 implementation and research boundaries](docs/INTERACTIVE_COMMENT_V31.md).

### Archived: V3 Context Detective (optional three-slang demo)

The optional archived mode starts a three-case, **mixed-slang** investigation loop:
1. Read a fictional post.
2. Expand the replies.
3. **Pin one piece of contextual evidence** (the choice is reflective, not scored).
4. Choose the slang meaning and lock in.
5. Receive contextual feedback.
6. Advance to the **next different slang**; end with a Case Report.

Three provisional hand-authored demo cases: `cooked`, `no cap`, `lowkey`. Practice XP is +80/+15 on correct/incorrect; no timer, streak penalty or paywall. Scores in this mode are **local demonstration state**, not persistent research outcomes. During play, the bottom navigation disappears so the player focuses on the social thread. Learn and the archive in Quests can open this mode.

For the architecture, UI/testing expectations, and MagicPath V3 revision, see [Context Detective V3](docs/CONTEXT_DETECTIVE_V3.md).

**Compatibility distinction:** the legacy REST generator still creates **10 contexts for one target slang sense** with the original 5/3/2 split. The current mixed publisher is a separate schema 2 contract; neither it nor the archived three-case demo changes that REST contract. Connected Training stays separate and source-labeled.

## GitHub GET mode (no server IP)

The Android **API Training** screen now supports **GitHub GET** for publicly hosted static question packs, alongside the separate **Live Backend** mode. It can display/grade 10-question packs without a laptop or Node server. The current default bank is published in this repository under `question-bank/`; no GitHub or OpenRouter token is shipped in the APK. See [GitHub GET setup](docs/GITHUB_GET_QUESTIONS.md).

Legacy tooling includes free hand-written demo exports and a separate manual single-pack LLM exporter. These are compatibility paths, not the current mixed publisher or its pending workflow integration. Static-bank grading occurs on the phone and is **not secure research-grade grading**. The separate backend server-grading option is preserved.

## Retained UI and Connected Training compatibility

- **V3 native Compose UI**: Feed, Context Detective investigation, contextual feedback, Case Report, Glossary/Learn, Challenges, Progress, Profile, and the independently labeled Connected Training route. Some secondary stats and challenges are sample UI; do not mistake them for recorded learner achievements.
- **Additional API Training path** from Feed: create a session over HTTP, play ten questions with **ten different context IDs** for *cooked*, open more replies as indirect hints, submit answers, receive server-graded feedback, XP, combo, and a session result.
- Node.js mock API: `GET /api/health`, `GET /api/trending`, `POST /api/sessions`, `GET /api/sessions/:id`, `POST /api/sessions/:id/answers`, `GET /api/leaderboard` (demo-only).
- The REST server keeps keys/explanations hidden until grading and rejects double answers; public static packs necessarily expose them. Backend tests use Node.js's built-in runner without npm dependencies; no new test result is asserted here.
- Optional legacy OpenAI-compatible REST adapter with retry, validation and labeled authored fallback. Earlier single-run smoke observations are retained only as [historical live notes](docs/LIVE_LLM_SMOKE.md), not mixed-v1 experiments, a current benchmark, deployment proof or phone-level validation. Mixed publishing has no retries.

## 1. Run backend first

Requires **Node.js 20+** (Node.js 22 recommended).

```bash
cd backend
npm test
npm start
```

Open http://127.0.0.1:8787/api/health. In default mode you will see `mode: mock`. To measure demo HTTP times while server is running: `npm run benchmark`.

Use `HOST=0.0.0.0` if you **intentionally** want a physical Android phone on the same trusted Wi-Fi network to reach this backend. Avoid exposing this mock unauthenticated API to the public Internet.

## 2. Open Android Studio

1. Open the **`android/`** folder as a Gradle project, not the repository root.
2. Choose a suitable JDK (17+) and install Android SDK platform 36 if prompted.
3. This package has `gradle/wrapper/gradle-wrapper.properties`, **but no official `gradle-wrapper.jar`** because the generation environment has no Gradle distribution/network access. If Android Studio does not sync, install compatible Gradle and run `android/scripts/setup-wrapper.ps1` in PowerShell or `android/scripts/setup-wrapper.sh` on Linux/macOS. This generates the genuine wrapper scripts/JAR.
4. Then run `./gradlew :app:assembleDebug` or the IDE build action. The earlier 2026-11-11 cloud-build record is historical; verify the current mixed-feed build before distributing.
5. Launch: Home fetches the public bank without Node. For the separate live Connected Training route, run the backend and open **PLAY API SESSION**; `http://10.0.2.2:8787` is the standard Android emulator address. Explicitly select the hand-authored sample for offline practice; **START THE CASE FILE** remains in the Quests archive.
6. For a **physical device**, set API URL to `http://YOUR-LAPTOP-LAN-IP:8787`, and run server with `HOST=0.0.0.0`. Ensure firewall/Wi-Fi allows local access.

Android **debug builds** allow cleartext HTTP for trusted private-LAN testing; release builds do **not** opt into cleartext. Use HTTPS for a cloud backend and enter the separate *backend access token* in API Training. Never put OpenRouter keys in the APK. See [cloud deployment guide](docs/CLOUD_PLAY.md).

## 3. Build APK in GitHub Actions (no Codex, no powerful laptop required)

After uploading this project to your new GitHub repository, open **Actions → Android debug build (no Codex) → Run workflow**. The workflow installs JDK, Android SDK 36, and Gradle 8.14.3 on a GitHub runner, runs `gradle :app:testDebugUnitTest` and compiles with `gradle :app:assembleDebug`, then (on success) uploads `slangverse-debug-apk` under the workflow's **Artifacts**. Earlier build success is historical, not verification of this mixed-feed revision; inspect the actual current run. GitHub Actions usage limits and private-repository minutes depend on your account plan. The workflow does not require Codex or a pre-generated Gradle wrapper JAR.

## 4. Optional LLM adapter (backend only)

Default provider remains `mock`. To switch a deployed backend to GPT-6 Luna via OpenRouter, create your private `backend/.env` and set these variables (never commit the secret):

```ini
LLM_PROVIDER=openai-compatible
LLM_API_KEY=YOUR_PRIVATE_SECRET
LLM_MODEL=openai/gpt-6-luna
LLM_API_BASE_URL=https://openrouter.ai/api/v1
LLM_TIMEOUT_MS=120000
LLM_RETRIES=1
```

Then `npm run start:env` (Node 20.6+). This optional REST adapter calls `/chat/completions`. Invalid output/provider failure uses explicitly labeled `fallback` only if that sense has an authored pack; otherwise it returns an explicit 503, never another slang's mock questions or a fabricated `llm` success. Mock/fallback are excluded from live-generation success metrics. Default backend mode is mock unless separately configured. These REST retry/fallback settings do not apply to mixed publishing, which has no retries or fallback.

## 5. Package layout

- `android/`: native Jetpack Compose UI, HTTP client, debug network allowance.
- `backend/`: own normalized dataset, mixed publisher/validators, versioned prompt and attempt audit directories; separate legacy REST provider/server, tests and benchmark tooling.
- `docs/`: architecture, evaluation guardrails, implementation status, notes for direct development.
- `magicpath-reference/`: the current MagicPath **V3 Context Detective** prototype revision and prior Warm Editorial reference; React source is separate from native Android Compose.

Original editable prototype: https://designs.magicpath.ai/v1/gladly-water-6771

## Scientific and engineering limits

- The ten mock contexts are **hand-authored**, not LLM-generated. A unique `contextId` does not prove genuine semantic variation or item validity.
- Automated validation checks schema, IDs, literal answer matching and obvious answer leakage; **not** semantic correctness, actual difficulty, distractor plausibility, bias, or educational gains.
- No real player accounts, competitive leaderboard, cloud persistence, per-user authorization, robust abuse prevention or SUS study yet. The cloud-play prototype supports only a manually entered *shared backend token* and a per-process generation safety fuse.
- Historical APK build success is not current mixed-feed validation. Physical-device acceptance and final integration outcomes must be reported separately.
- Backend process-memory sessions expire after 30 minutes and disappear on server restart.
- The system is not a public social network: accounts, posts, comments, reactions, and trending are fictional.

This repository is already hosted at `ferrysrt/Slangverse-Android`. Do not modify the independent `slangfeed` repository.
