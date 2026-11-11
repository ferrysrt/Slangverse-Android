# OpenRouter live question-generation smoke test

This is **not the dataset discovery pipeline**. The data collector discovers *candidate meanings*, while this flow tests generating 10 context-diverse fictional questions for a **provisional** manually selected sense.

## Setup

Set the existing GitHub Actions repository secret `OPENROUTER_API_KEY`. Optionally set Actions variable `OPENROUTER_MODEL`, default `openai/gpt-oss-120b`.

Select Actions → **Live OpenRouter 10-question smoke (manual)** → Run workflow on `main`; choose a playable `sense_id`. The workflow issues **one** actual API generation request (no retry), with a 120-second timeout and 8,000-token cap. The workflow fails with a sanitized report when generation fails or validation rejects output.

The artifact `live-llm-validation-metrics` contains only counts, structural-validation error codes, provenance and latency. It intentionally **does not include prompt completions, posts, answer keys, API tokens or user data**.

## Interpreting outcome

- `status: passed`, `sessionSource: llm`: external model returned 10 items satisfying automated structure and reference checks.
- `status: fallback`: model was called but failed response parsing, API, or schema checks; a **mock** session would be used by the game.
- `status: failed`: configuration or smoke-run error; read non-secret category.
- No structural outcome proves meaning correctness, subtle hints, plausible distractors, HCI usability or learning gains.
- The app still defaults to `LLM_PROVIDER=mock`. Live model is only used if the **backend host** is configured with `LLM_PROVIDER=openai-compatible`, `LLM_API_KEY`, `LLM_MODEL`, and `LLM_API_BASE_URL=https://openrouter.ai/api/v1`. GitHub Actions secrets by themselves do **not** deploy or configure that server. Do not put provider keys in Android apps.

The smoke workflow is manual only on `main` after integration; avoid scheduling repeated paid model calls.
