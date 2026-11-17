# Skripsi — what can and cannot be claimed

Project working title: **Desain Sistem Generasi Soal Multikonteks Berbasis Large Language Model pada Gim Edukasi Internet Slang Berantarmuka Media Sosial**.

The user has approved the normalized-dataset, offline-generated mixed-feed product plan. This does **not** establish supervisor approval of the thesis methodology. Real-time phone generation is not the current Home design; see [accepted intent](MIXED_FEED_HANDBOOK.md) and [the mixed-v1 research protocol](PROMPT_ENGINEERING.md).

Candidate evaluation metrics for later experiments:
- JSON parse and schema validity rates (`valid/total`, error categories)
- Constraint compliance (exactly ten items per pack, nominal 5:3:2 difficulty, options, key, unique question IDs and supported categories); mixed categories/scenario IDs may repeat
- Generation availability and latency (p50/p90/p95, retries, provider failures), with cache/mock/fallback excluded from real LLM success statistics
- Failure handling (timeout, 429, invalid JSON, schema mismatch, server/network issues)
- Usability SUS using standard scoring; **do not claim gains in learning or motivation** based on SUS alone

Research guardrails:
- Rule-based structure checks do not prove semantic or linguistic correctness.
- A few example contextual hints do not establish that all generated replies are appropriate or non-spoiling.
- Label fixtures/mock/test, historical single-sense runs and real mixed LLM attempts separately. No fabricated measurements or mixed-v2 history; `mixed-v1` is the implemented baseline.
- Terms like "trending" are *fictional in-game curated topics*, not actual platform trends.
- If eliminating expert raters, adjust research questions/objectives and avoid claims about validated semantic quality.
- Preserve actual inputs, rendered prompts, non-secret requests, raw output/response, schema/prompt versions, validation decisions, actual model/usage/finish reasons and terminal outcomes. Mixed publishing has no retries; retain failures and missing evidence honestly. Summary CLI and failure-safe workflow artifact/persistence integration are pending main work.

Possible test scenarios: success; malformed JSON; wrong item count; duplicated answer options; explicit-answer leak in comments; LLM timeout; HTTP 429; fallback; cache hit; repeated answer submission; app offline state.
