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
