# Slangverse Android V2.2 — Native Compose + Backend (direct development)

**Status:** editable Android + Node.js source with a real HTTP workflow and a mock/optional LLM provider. **Android debug APK successfully built on GitHub Actions (2026-11-11)**, but device/emulator behavior is not verified. This project is not affiliated with the separate Godot repository `ferrysrt/slangfeed`.

Slangverse is an educational Android game with a **fictional social-media-inspired interface**. Players interpret Internet slang from posts and indirect comment hints. The research direction (still awaiting final supervisor approval) is the **technical evaluation of on-demand LLM generation**: schema adherence, success/fallback, latency, and SUS.

## What currently works

- **8 native Compose screens (source)**: Feed, Explore/Trending, Community Quest, Decode, Reply Battle, Results, Top Decoders (demo), Profile/Badges (demo).
- **Additional API Training path** from Feed: create a session over HTTP, play ten questions with **ten different context IDs** for *cooked*, open more replies as indirect hints, submit answers, receive server-graded feedback, XP, combo, and a session result.
- Node.js mock API: `GET /api/health`, `GET /api/trending`, `POST /api/sessions`, `GET /api/sessions/:id`, `POST /api/sessions/:id/answers`, `GET /api/leaderboard` (demo-only).
- Server keeps answer keys and explanation hidden until grading; rejects double answers. Tests run with Node.js built-in test runner and use no npm dependencies.
- Optional OpenAI-compatible REST adapter with retry, schema validation and labeled fallback. A **single live OpenRouter GPT-OSS 120B smoke test** on 2026-11-11 generated ten structurally valid `no cap` items in 70.43 seconds without retry; this does **not** imply semantic validity, production deployment, or average latency. See [live validation notes](docs/LIVE_LLM_SMOKE.md).

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
5. Launch in emulator, run Node backend on your computer, then open Feed > **PLAY API SESSION**. The default backend URL `http://10.0.2.2:8787` is for the standard Android emulator.
6. For a **physical device**, set API URL to `http://YOUR-LAPTOP-LAN-IP:8787`, and run server with `HOST=0.0.0.0`. Ensure firewall/Wi-Fi allows local access.

Android **debug builds** allow cleartext HTTP for this local mock server; release builds do **not** opt into cleartext. For a deployed backend, use HTTPS. Never put API keys in the APK.

## 3. Build APK in GitHub Actions (no Codex, no powerful laptop required)

After uploading this project to your new GitHub repository, open **Actions → Android debug build (no Codex) → Run workflow**. The workflow installs JDK, Android SDK 36, and Gradle 8.14.3 on a GitHub runner, compiles with `gradle :app:assembleDebug`, and (on success) uploads `slangverse-debug-apk` under the workflow's **Artifacts**. GitHub Actions has successfully completed the Android debug build; future changes still require their own build verification. GitHub Actions usage limits and private-repository minutes depend on your account plan. The workflow does not require Codex or a pre-generated Gradle wrapper JAR.

## 4. Optional LLM adapter (backend only)

Default provider is `mock`. To turn on a *compatible* endpoint (model-dependent), create your private `backend/.env` and set variables in `.env.example`:

```ini
LLM_PROVIDER=openai-compatible
LLM_API_KEY=YOUR_PRIVATE_SECRET
LLM_MODEL=YOUR_COMPATIBLE_MODEL
LLM_API_BASE_URL=https://api.openai.com/v1
LLM_TIMEOUT_MS=20000
LLM_RETRIES=1
```

Then `npm run start:env` (Node 20.6+). This calls `/chat/completions` and requests JSON output. If the provider returns invalid data, times out, or configuration is missing, **source is explicitly `fallback`**, never a fabricated `llm` success. The `mock` and `fallback` sources are excluded from live-generation success metrics. A live OpenRouter generation request has now passed one smoke test via Actions, separate from Android deployment. The default backend remains in mock mode unless separately configured.

## 5. Package layout

- `android/`: native Jetpack Compose UI, HTTP client, debug network allowance.
- `backend/`: mock REST server, JSON structure validator, generation prompt, optional provider adapter, unit/HTTP tests, benchmark script.
- `docs/`: architecture, evaluation guardrails, implementation status, notes for direct development.
- `magicpath-reference/`: original MagicPath V2.1 UI prototype link and transfer context. The React files themselves are **not embedded** in this ZIP; native Compose has been implemented independently as source.

Original editable prototype: https://designs.magicpath.ai/v1/gladly-water-6771

## Scientific and engineering limits

- The ten mock contexts are **hand-authored**, not LLM-generated. A unique `contextId` does not prove genuine semantic variation or item validity.
- Automated validation checks schema, IDs, literal answer matching and obvious answer leakage; **not** semantic correctness, actual difficulty, distractor plausibility, bias, or educational gains.
- No real player accounts, competitive leaderboard, cloud persistence, production auth, real-time moderation, abuse prevention or SUS study yet.
- The UI was not compiled or tested on an Android device here, because the environment has no Android SDK/Gradle and cannot download them.
- Backend process-memory sessions expire after 30 minutes and disappear on server restart.
- The system is not a public social network: accounts, posts, comments, reactions, and trending are fictional.

To publish on GitHub, create a **new private repository named `Slangverse-Android`** and upload this folder. Do not push to the legacy `slangfeed` repository.
