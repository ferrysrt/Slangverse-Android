# Current contracts — published bank and optional REST training

## Default Home: public GitHub GET

Root: `https://raw.githubusercontent.com/ferrysrt/Slangverse-Android/main/question-bank`. Home downloads pre-generated JSON; it never POSTs to an LLM or needs a provider key. Mixed schema support is implemented in source; this contract does not assert a successful mixed publication or APK release.

| GET path | Contract |
| --- | --- |
| `catalog.json` | Schema 2 mixed catalogue with retained legacy senses, or supported schema 1 legacy catalogue |
| `packs/<packId>.json` | Schema 2 immutable mixed pack advertised by `packs` |
| `packs/<senseId>.json` | Schema 1 single-sense compatibility pack advertised by `senses` |

### Schema 2 catalogue

Required origin/version fields include `schemaVersion: 2`, `source: github-static`, `generationSource: llm`, `datasetVersion`, `candidateCount`, `expertValidated: false`, `bankVersion`, `status`, `senses` and `packs`.

- `packs`: entries `{packId, sha256, questionCount: 10}`; pack IDs are path-safe identifiers, hashes are 64 lowercase hex characters.
- `senses`: retained schema 1 entries with `senseId`, `term`, `reviewStatus`, `sha256` and existing provenance metadata. These are compatibility entries, not the current normalized selection pool.
- `status`: `ready` when either list has available entries, otherwise `awaiting-generation`.
- The publisher indexes up to twelve valid mixed packs, newest first; old files are not deleted. The client selects up to twelve packs from the preferred list.

Home chooses mixed `packs` whenever nonempty; it uses legacy `senses` only if no mixed packs are listed. A mixed fetch/hash/shape failure is an explicit error, not a fallback to legacy or mock output. Catalogue/pack publication must be consistent, and SHA-256 covers the exact public pack bytes.

### Schema 2 mixed pack

A mixed pack contains:

- `schemaVersion: 2`, `packId`, `generationSource: llm`, `generatorModel` (actual returned model), `promptVersion`, `generatedAt`, `datasetVersion`, `expertValidated: false` and `referenceStatus: normalized-unreviewed`;
- `references`: 2–10 records with `senseId`, `term`, `targetMeaning`, `sourceCandidateId`, `normalizedDefinition` and `referenceSources`;
- source records preserving `url`, `title`, literal `quote`, actual `collectedAt`, `collectionMethod` and `contentSha256`;
- `questions`: exactly ten questions with nominal 5 easy / 3 medium / 2 hard labels and at least two distinct slang terms, preferring ten at selection time.

Each question has nonempty `id`, `senseId`, `slang`, `targetMeaning`, `difficulty`, `contextId`, `contextCategory`, `questionComment`, `question`, `explanation`, `post`, four `options` and integer zero-based `correctIndex` in 0–3. `post` contains `displayName`, `handle`, `hashtag`, `content`, bundled `avatarId` and ordinary `comments` with `handle`, `text`, `avatarId`. The current validator allows 1–5 comments; the prompt requests 2–3.

`contextCategory` is one of:

```text
personal, education, work, prediction, sports,
entertainment, opinion, friends, event-reaction
```

Categories and scenario `contextId` values may repeat. Question IDs must be unique. Each question's `senseId` resolves to its embedded reference, and slang/meaning/correct option match that reference exactly. Mixed generation checks fixed assignments and uses all sampled references. These are literal integrity rules, not semantic guarantees.

### Home state and disclosure

Home merges preferred packs, shuffles once per fresh load/explicit refresh, and saves the resulting order for stable navigation, recomposition and supported restoration. Slang chips are absent; context tabs remain. Progress keys are pack ID/content hash/question ID (legacy uses sense ID in place of pack ID). A refresh, tab change or restored order does not grant duplicate 80/15 practice XP. A failed refresh can retain the prior published feed in its existing order with an error.

Answers/explanations are necessarily public for local grading. This is local practice, not authenticated scoring or durable participant storage. The offline sample is explicit opt-in. Deterministic checks do not establish semantic accuracy, safety against all harmful text or calibrated difficulty. See [prompt protocol](PROMPT_ENGINEERING.md).

### Schema 1 compatibility

Older packs retain `senseId`, original generator/dataset/reference metadata and ten questions for one slang sense with ten distinct scenario IDs. They need not have mixed-only per-question references/categories. Optional `questionComment` and `avatarId` extensions remain backward compatible. Firecrawl-derived legacy packs retain `sourceCandidateId`, `normalizedDefinition` and `referenceSources`; optional `editorialReview` discloses actual agent-assisted corrections, and the feed adds `agent-edited` rather than pretending output is untouched or expert validated.

The single-sense and ten-distinct-context rules remain a **legacy/REST contract**, not requirements imposed on schema 2 mixed output.

## Active dataset catalogue

`backend/src/catalog.mjs` reads the own Firecrawl-normalized JSON, not the frozen slangfeed snapshot. `publicCatalog()` reports `source: firecrawl-normalized`, a content-derived `datasetVersion`, own-dataset counts, `provenanceTable`, `expertValidated: false`, `reviewStatus: normalized-unreviewed`, capture ordering and eligible senses.

Eligibility automatically assigns `normalized-<candidateId>` after provenance checks; there is no manual review gate. The initial dataset has 16 carried-forward entries, with a fresh refresh pending. An active sense is not proof that its quote semantically supports its normalized definition. Historical senses remain explicitly resolvable for compatibility without populating the active catalogue. See [dataset contract](DATASET_INTEGRATION.md).

## Optional Connected Training REST

These endpoints belong to the separate Node backend, default local origin `http://127.0.0.1:8787`. Default provider is `mock`, not the Home source. Local development can omit a client token; with `SLANGVERSE_CLIENT_TOKEN` configured, all gameplay routes require `Authorization: Bearer <separate client token>`. `SLANGVERSE_REQUIRE_TOKEN=true` refuses startup without a valid token. Never send the OpenRouter key from Android. This shared-secret prototype is not account authentication; do not expose an unprotected backend publicly.

| Method | Path | Result |
| --- | --- | --- |
| GET | `/api/health` | Public `{ok,mode,llmConnected,providerConfigured}` configuration signal, not proof of a live call |
| GET | `/api/catalog` | Active normalized catalogue and provenance warning |
| GET | `/api/catalog/candidates?q=&limit=` | Own entries, newest capture first; limit defaults to 25, capped at 100 |
| GET | `/api/trending` | `{source: curated,trending:[...]}` fictional topics, not platform trends |
| POST | `/api/sessions` | `201` with session/sense/reference/catalogue metadata, source, generation latency and public questions |
| GET | `/api/sessions/:id` | Progress counts and total XP |
| POST | `/api/sessions/:id/answers` | Correctness, revealed key/explanation, XP and progress |
| GET | `/api/leaderboard` | `{source: demo,competitive: false,players: []}` |

### Create a session

Body `{}` selects the historical authored default, or send `{ "senseId": "<catalogue sense ID>" }` or a supported `term`. A supplied term and sense must agree. Normalized senses have no invented authored fallback: mock mode or failed generation without an authored pack returns `503 missing-hand-authored-fallback-for-sense`, never another slang's questions. Unsupported selectors return 400.

Live REST sessions still generate ten questions for one sense with ten distinct scenario IDs and nominal 5/3/2 labels. They use the legacy provider/retry/fallback path, **not** the mixed publisher. Responses label `source: mock`, `llm` or `fallback`; only a real successful provider response is `llm`. Mock latency is null; attempted generation latency is separate from HTTP round-trip time. Authored fallback is possible only where that sense has a matching authored pack.

Public questions expose `id`, `slang`, `difficulty`, `contextId`, `post`, `question`, `options` and supported presentation fields, but omit `correctIndex`, `targetMeaning` and `explanation` until grading. This server-side hiding does not apply to public static packs.

### Submit an answer

```json
{"questionId":"cooked-1","selectedIndex":1}
```

The server grades with zero-based `selectedIndex`, returning 80 XP for correct or 15 for incorrect. A repeated submission returns `409 already-answered`; invalid index is `400 invalid-option`; unknown question/session is 404. Session data expires after 30 minutes in process memory and disappears on restart. It is not a durable participant response log.

Example for local mock development with no client token:

```sh
curl -X POST http://127.0.0.1:8787/api/sessions -H 'Content-Type: application/json' -d '{}'
```

Cloud live generation uses single-flight requests and a per-process session fuse, returning 429 when busy/exhausted. Restart resets the fuse, so it is not a durable spending cap or full rate limiting. CORS is permissive for development. Accounts, per-user authorization, durable scores and competitive ranking remain absent. See [optional cloud setup](CLOUD_PLAY.md).

## Bundled avatar extension

`post.avatarId` and comment `avatarId` select [bundled pictures](../assets/profile-pictures/README.md), not remote image URLs. Legacy missing/cosmetic IDs have deterministic local fallbacks; prompts supply allowed IDs and mixed validation checks them. Avatars do not encode answers, change XP or prove content quality.

## Historical scope

The earlier V2.2 API page described only mock/REST training; the 0.4.0 appendix described schema 1 Home with sense selection. Both remain compatibility paths, not contradictory definitions of today's mixed contract. Main's report CLI, failure-safe workflow integration and planned version update are tracked in [implementation status](IMPLEMENTATION_STATUS.md), not claimed complete here.
