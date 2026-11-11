# OpenRouter live question-generation smoke test

This is **not the dataset discovery pipeline**. The data collector discovers *candidate meanings*, while this flow tests generating 10 context-diverse fictional questions for a **provisional** manually selected sense.

## Setup

Set the existing GitHub Actions repository secret `OPENROUTER_API_KEY`. The workflow currently targets `openai/gpt-6-luna` (regular non-batch) explicitly. Its previous `OPENROUTER_MODEL` Actions variable override is intentionally not used here, so an old variable cannot silently select GPT-OSS.

Select Actions → **Live OpenRouter 10-question smoke (manual)** → Run workflow on `main`; choose a playable `sense_id`. The workflow issues **one** actual API generation request (no retry), with a 120-second timeout and 8,000-token cap. The workflow fails with a sanitized report when generation fails or validation rejects output.

The artifact `live-llm-validation-metrics` contains only counts, structural-validation error codes, provenance and latency. It intentionally **does not include prompt completions, posts, answer keys, API tokens or user data**.

## Interpreting outcome

- `status: passed`, `sessionSource: llm`: external model returned 10 items satisfying automated structure and reference checks.
- `status: fallback`: model was called but failed response parsing, API, or schema checks; a **mock** session would be used by the game.
- `status: failed`: configuration or smoke-run error; read non-secret category.
- No structural outcome proves meaning correctness, subtle hints, plausible distractors, HCI usability or learning gains.
- The app still defaults to `LLM_PROVIDER=mock`. Live model is only used if the **backend host** is configured with `LLM_PROVIDER=openai-compatible`, `LLM_API_KEY`, `LLM_MODEL`, and `LLM_API_BASE_URL=https://openrouter.ai/api/v1`. GitHub Actions secrets by themselves do **not** deploy or configure that server. Do not put provider keys in Android apps.

The smoke workflow is manual only on `main` after integration; avoid scheduling repeated paid model calls.

## Verified first live smoke run

The feature-branch live run on 11 November 2026 succeeded: [GitHub Actions #43072284935](https://github.com/ferrysrt/Slangverse-Android/actions/runs/43072284935). With model `openai/gpt-oss-120b`, sense `no-cap-truth`, and **one request without retry**, generated source was `llm`, 10 items passed automated structure checks, 10 distinct context IDs, 5/3/2 nominal difficulty ratio, 0 validation error codes and no fallback. Recorded generation latency: **70,430 ms**. This is a single exploratory observation, NOT a latency benchmark or proof of educational/semantic quality.

The live smoke workflow is now **manual-only**, so it will not spend API credits automatically on every code push.

## Model switch — 12 November 2026

The live generation test and weekly candidate normalizer are now configured to use `openai/gpt-6-luna` (regular/non-batch). The backend provider also defaults to the OpenRouter API and `openai/gpt-6-luna` whenever `LLM_PROVIDER=openai-compatible` is selected and model/base URL overrides are absent. **Backend default mode remains `mock`**. This configuration change does not mean a new live generation test or Android deployment has passed. A manually launched Luna smoke test is needed to verify its runtime output. Older GPT-OSS 120B smoke metrics below remain historical evidence, not Luna benchmarks.
