# REST API contract (V2.2 prototype)

All paths are relative to backend origin (default `http://127.0.0.1:8787`). The backend is **unauthenticated mock/development code**. Do not expose it publicly.

| Method | Path | Result |
|---|---|---|
| GET | `/api/health` | `{ok,mode,llmConnected,providerConfigured}` |
| GET | `/api/trending` | `{source:"curated",trending:[...]}` |
| POST | `/api/sessions` | `{id,source,generationLatencyMs,questions:[...]}` |
| GET | `/api/sessions/:id` | `{id,source,answeredCount,totalCount,correctCount,totalXp}` |
| POST | `/api/sessions/:id/answers` | `{correct,correctIndex,explanation,xpEarned,answeredCount,totalCount,totalXp}` |
| GET | `/api/leaderboard` | `{source:"demo",competitive:false,players:[]}` |

## Session

Create with `POST /api/sessions` and an empty JSON body `{}`. Questions include `id,slang,difficulty,contextId,post,question,options` but **exclude** `correctIndex`, `targetMeaning`, and `explanation` until grading. `post` has `id,displayName,handle,hashtag,content,comments` where comments are `{handle,text}`. Responses mark `source` as `mock`, `llm`, or `fallback`. `generationLatencyMs` is `null` for mock; positive for attempted provider generation. Do not infer linguistic correctness from successful schema validation.

## Submit answer

```json
{"questionId":"cooked-1","selectedIndex":1}
```

`selectedIndex` is zero-based. Server grades and returns the correct index **only after** the answer is submitted. A second submission for the same question returns `409 already-answered`. Invalid index returns `400 invalid-option`; invalid session returns 404.

## Sample curl

```bash
curl -X POST http://127.0.0.1:8787/api/sessions -H 'Content-Type: application/json' -d '{}'
```

Copy the returned session `id` into the next request. The server's demo sessions expire after 30 minutes and vanish when the process restarts.

## Limits

- CORS allows `*` for convenience in local mock use; restrict it in production.
- There are no accounts, auth, rate limits, durable score storage or real leaderboard.
- Structural validation stops obvious literal answer leaks, but cannot reliably detect indirect semantic spoilers.
- Do not show the sample scores or source as real-player statistics.


## Optional local profile-picture metadata (0.3.1)

Question `post` and each `post.comments` object may include `avatarId`, an identifier from the [local avatar catalogue](../assets/profile-pictures/README.md). It is cosmetic metadata and does not alter grading, question count, difficulty, source labels or answer-key visibility.

Generation prompts supply the allowed IDs. The backend normalizes these fields for mock, fallback and generated sessions before serving or exporting packs. Established cast handles keep their canonical image. New handles receive a consistent requested/fallback image within a pack. Malformed or absent IDs use deterministic local fallbacks, without retrying an otherwise valid generated question package. Legacy clients can ignore the field; current Android clients also render old packs that omit it. No image URL or new network image service is required.


## Default published feed (0.4.0)

The default Android feed uses GitHub GET rather than POST /api/sessions. Its folder root is `https://raw.githubusercontent.com/ferrysrt/Slangverse-Android/main/question-bank`.

`catalog.json` uses schemaVersion 1, generationSource `llm`, datasetVersion, candidateCount, status (`ready` or `awaiting-generation`), bankVersion, and senses containing senseId, term, reviewStatus and sha256. Each `packs/<senseId>.json` contains schemaVersion 1, generationSource `llm`, matching senseId, generatorModel, promptVersion, generatedAt, datasetVersion, referenceStatus and ten questions with the existing static schema. Keys/explanations are necessarily public for local grading.

The home feed requires LLM provenance in both files, validates catalogue checksums and slang matching, and namespaces progress by sense/content hash/question ID. Optional `contextCategory` can name one of the nine proposal categories; legacy scenario IDs use navigation tags and unknown scenarios remain visible in For you. The phone never invokes the generation provider. The existing live backend and explicitly selected legacy static/mock training modes remain available with their own source labels.
