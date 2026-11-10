# Skripsi — what can and cannot be claimed

Project working title: **Desain Sistem Generasi Soal Multikonteks Berbasis Large Language Model pada Gim Edukasi Internet Slang Berantarmuka Media Sosial**.

The post-seminar real-time revision remains a proposed direction, **not a supervisor-approved methodology**.

Candidate evaluation metrics for later experiments:
- JSON parse and schema validity rates (`valid/total`, error categories)
- Constraint compliance (exactly ten items, 5:3:2 difficulty, options, key, unique IDs)
- Generation availability and latency (p50/p90/p95, retries, provider failures), with cache/mock/fallback excluded from real LLM success statistics
- Failure handling (timeout, 429, invalid JSON, schema mismatch, server/network issues)
- Usability SUS using standard scoring; **do not claim gains in learning or motivation** based on SUS alone

Research guardrails:
- Rule-based structure checks do not prove semantic or linguistic correctness.
- A few example contextual hints do not establish that all generated replies are appropriate or non-spoiling.
- Label mock/test and LLM output explicitly. No fabricated experiment measurements.
- Terms like "trending" are *fictional in-game curated topics*, not actual platform trends.
- If eliminating expert raters, adjust research questions/objectives and avoid claims about validated semantic quality.
- Preserve prompts, schema versions, output, validation decisions, retries and aggregated metrics with appropriate privacy controls.

Possible test scenarios: success; malformed JSON; wrong item count; duplicated answer options; explicit-answer leak in comments; LLM timeout; HTTP 429; fallback; cache hit; repeated answer submission; app offline state.
