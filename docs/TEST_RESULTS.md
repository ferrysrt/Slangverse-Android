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
