# Slangverse Android V3 — Context Detective (native Compose + Node backend)

**Status:** V3 Context Detective native Android gameplay is implemented on a feature branch, with GitHub Actions unit/build checks as merge gates. The source also contains an independently labeled real HTTP training path and a mock/optional LLM provider. Device/emulator gameplay is not verified merely by compiling an APK. This repository is separate from `ferrysrt/slangfeed`.

Slangverse is an educational Android game with a **fictional social-media-inspired interface**. Players interpret Internet slang from posts and indirect comment hints. The research direction (still awaiting final supervisor approval) is the **technical evaluation of on-demand LLM generation**: schema adherence, success/fallback, latency, and SUS.

**Want to play on your own Android phone with GPT-6 Luna?** See [secure cloud + physical phone setup](docs/CLOUD_PLAY.md). The Render configuration is included but the backend is **not deployed automatically**, and an actual phone test is still outstanding.

## New: V3 Context Detective (primary gameplay)

The main **Feed** now starts a three-case, **mixed-slang** investigation loop:
1. Read a fictional post.
2. Expand the replies.
3. **Pin one piece of contextual evidence** (the choice is reflective, not scored).
4. Choose the slang meaning and lock in.
5. Receive contextual feedback.
6. Advance to the **next different slang**; end with a Case Report.

Three provisional hand-authored demo cases: `cooked`, `no cap`, `lowkey`. Practice XP is +80/+15 on correct/incorrect; no timer, streak penalty or paywall. Scores in this mode are **local demonstration state**, not persistent research outcomes. During play, the bottom navigation disappears so the player focuses on the social thread. Feed, Learn and Quests open the new mode.

For the architecture, UI/testing expectations, and MagicPath V3 revision, see [Context Detective V3](docs/CONTEXT_DETECTIVE_V3.md).

**Research distinction:** the LLM/backend generator still creates **10 contexts for one target slang sense** and follows the original 5 easy / 3 medium / 2 hard rule. The three-case mixed-slang demo is **not** an altered generator contract. Connected Training is still separate and source-labeled.

## GitHub GET mode (no server IP)

The Android **API Training** screen now supports **GitHub GET** for publicly hosted static question packs, alongside the separate **Live Backend** mode. It can display/grade 10-question packs without a laptop or Node server. The current main source repo is private, so a separate public JSON bank must be published before anonymous phone GET works; no GitHub or OpenRouter token is shipped in the APK. See [GitHub GET setup](docs/GITHUB_GET_QUESTIONS.md).

Backend CI exports three free hand-written demo packs as a downloadable artifact. A separate **manual-only** GitHub Actions workflow can generate a genuine GPT-6 Luna pack (one optional paid request) for review and later publication. Static-bank grading occurs on the phone and is **not secure research-grade grading**. The original backend server-grading option is preserved.

## What currently works

- **V3 native Compose UI**: Feed, Context Detective investigation, contextual feedback, Case Report, Glossary/Learn, Challenges, Progress, Profile, and the independently labeled Connected Training route. Some secondary stats and challenges are sample UI; do not mistake them for recorded learner achievements.
- **Additional API Training path** from Feed: create a session over HTTP, play ten questions with **ten different context IDs** for *cooked*, open more replies as indirect hints, submit answers, receive server-graded feedback, XP, combo, and a session result.
- Node.js mock API: `GET /api/health`, `GET /api/trending`, `POST /api/sessions`, `GET /api/sessions/:id`, `POST /api/sessions/:id/answers`, `GET /api/leaderboard` (demo-only).
- Server keeps answer keys and explanation hidden until grading; rejects double answers. Tests run with Node.js built-in test runner and use no npm dependencies.
- Optional OpenAI-compatible REST adapter with retry, schema validation and labeled fallback. Separate **one-run live OpenRouter** smoke tests generated ten structurally valid `no cap` items with GPT-OSS 120B (~70.43 seconds) and GPT-6 Luna (~29.29 seconds). These are **individual observations**, not average latencies or proof of semantic correctness, production deployment, or phone-level play. See [live validation notes](docs/LIVE_LLM_SMOKE.md).

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
4. Then run `./gradlew :app:assembleDebug` or the IDE build action. **The cloud build succeeded on 2026-11-11, but still verify any newly built APK before distributing.**
5. Launch in emulator, run Node backend on your computer, then open Feed > **PLAY API SESSION** for the connected training path. For offline local play, use **START THE CASE FILE**. The default backend URL `http://10.0.2.2:8787` is for the standard Android emulator.
6. For a **physical device**, set API URL to `http://YOUR-LAPTOP-LAN-IP:8787`, and run server with `HOST=0.0.0.0`. Ensure firewall/Wi-Fi allows local access.

Android **debug builds** allow cleartext HTTP for trusted private-LAN testing; release builds do **not** opt into cleartext. Use HTTPS for a cloud backend and enter the separate *backend access token* in API Training. Never put OpenRouter keys in the APK. See [cloud deployment guide](docs/CLOUD_PLAY.md).

## 3. Build APK in GitHub Actions (no Codex, no powerful laptop required)

After uploading this project to your new GitHub repository, open **Actions → Android debug build (no Codex) → Run workflow**. The workflow installs JDK, Android SDK 36, and Gradle 8.14.3 on a GitHub runner, runs `gradle :app:testDebugUnitTest` and compiles with `gradle :app:assembleDebug`, then (on success) uploads `slangverse-debug-apk` under the workflow's **Artifacts**. GitHub Actions has successfully completed the Android debug build; future changes still require their own build verification. GitHub Actions usage limits and private-repository minutes depend on your account plan. The workflow does not require Codex or a pre-generated Gradle wrapper JAR.

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

Then `npm run start:env` (Node 20.6+). This calls `/chat/completions` and requests JSON output. If the provider returns invalid data, times out, or configuration is missing, **source is explicitly `fallback`**, never a fabricated `llm` success. The `mock` and `fallback` sources are excluded from live-generation success metrics. One earlier GPT-OSS 120B generation request passed a smoke test via Actions (historical result). The configured model is **`openai/gpt-6-luna` (non-batch)**; one previous isolated live smoke test passed, but a cloud-hosted phone session has not been verified. The default backend remains in mock mode unless separately configured.

## 5. Package layout

- `android/`: native Jetpack Compose UI, HTTP client, debug network allowance.
- `backend/`: mock REST server, JSON structure validator, generation prompt, optional provider adapter, unit/HTTP tests, benchmark script.
- `docs/`: architecture, evaluation guardrails, implementation status, notes for direct development.
- `magicpath-reference/`: the current MagicPath **V3 Context Detective** prototype revision and prior Warm Editorial reference; React source is separate from native Android Compose.

Original editable prototype: https://designs.magicpath.ai/v1/gladly-water-6771

## Scientific and engineering limits

- The ten mock contexts are **hand-authored**, not LLM-generated. A unique `contextId` does not prove genuine semantic variation or item validity.
- Automated validation checks schema, IDs, literal answer matching and obvious answer leakage; **not** semantic correctness, actual difficulty, distractor plausibility, bias, or educational gains.
- No real player accounts, competitive leaderboard, cloud persistence, per-user authorization, robust abuse prevention or SUS study yet. The cloud-play prototype supports only a manually entered *shared backend token* and a per-process generation safety fuse.
- The Android debug APK has successfully compiled through GitHub Actions, **not** been validated by playing on a physical Android device.
- Backend process-memory sessions expire after 30 minutes and disappear on server restart.
- The system is not a public social network: accounts, posts, comments, reactions, and trending are fictional.

This repository is already hosted at `ferrysrt/Slangverse-Android`. Do not modify the independent `slangfeed` repository.
