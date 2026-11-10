# Slangverse V2.2 — current technical architecture

## System boundaries

```text
Android (Jetpack Compose)
  ├── 8 mock/UI reference screens: Feed, Explore, Community, Decode,
  │      Reply Battle, Result, Rank (demo), Profile (demo)
  └── API Training screen
         │ HTTPS in production; cleartext only on local Android debug
         ▼
Node.js REST Backend
  ├── /api/sessions (creates 10-question package)
  ├── /api/sessions/:id/answers (server-side grade + XP)
  ├── /api/sessions/:id (progress)
  └── /api/trending (curated simulation) / leaderboard (demo disabled)
         │
         ├── LLM_PROVIDER=mock → 10 hand-authored context variants
         └── LLM_PROVIDER=openai-compatible
               ├── versioned structured prompt
               ├── provider HTTP request with timeout
               ├── parse JSON + structure and lexical checks
               ├── limited retry
               └── explicit fallback to hand-authored dataset
```

## Gameplay

- Feed cards look like fictional media posts with avatar, handle, metadata, engagement, and replies.
- Comments provide **indirect contextual hints**, expanded on demand.
- Live-backend training uses ten questions for the same slang/approved meaning but **ten distinct context IDs**. Nominal 5 easy, 3 medium, 2 hard. Randomizing order and balancing difficulty across real players remain future research work.
- Answer keys are never sent before grade, and XP totals are computed by the backend.
- An on-device mock screen still exists for visual prototyping. It **must never be counted as an API or LLM experiment**.

## Evidence & logging

- `source=mock`: hand-authored package served without LLM.
- `source=llm`: validated live provider response (only when real provider succeeds).
- `source=fallback`: mock content delivered after provider errors or validation failure.
- Generation latency is measured only around provider attempts; in mock mode it is `null`.
- The benchmark script measures **HTTP round-trip latency separately from reported generation latency**; never aggregate sources for LLM performance claims.
- Automated validators check JSON shape, 10 items, 5:3:2 labels, unique context IDs, unique IDs, correct key/reference literal, and comments not copying the literal option text. This **does not establish semantic accuracy, fairness, or empirical difficulty**.

## Security constraints and missing features

- API key only exists in Node environment; never in Android. Release Android manifest does not opt in to cleartext HTTP.
- Demo session tokens are opaque UUIDs but NOT authenticated identities. Local server has no user auth or real rate limiting. Never deploy it publicly as-is.
- Sessions use process memory and expire after 30 minutes; no Room, DataStore, database, cache, GDPR/consent infrastructure, or account persistence.
- Leaderboard is intentionally non-competitive until all users receive standardized questions scored by the server and authentication exists.
- SUS study, academic methodology, linguistic review decisions, final participant criteria and model selection require discussion with the supervisors.
