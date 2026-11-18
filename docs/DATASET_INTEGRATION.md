# Active dataset — own Firecrawl-normalized slang

## Current source of truth

`backend/data/discovered_candidates.json` is the canonical active dataset; `backend/data/discovered_candidates.csv` is its inspectable source table. The dataset currently holds **22 entries**: 16 carried forward from the recorded initial collection and 6 added by later refreshes. A fresh refresh is pending; migration/normalization does not count as new collection or evidence of current popularity.

Eligible entries automatically become playable `normalized-<candidateId>` senses with `reviewStatus: normalized-unreviewed`. **There is no manual review or sense-review mapping gate in the current pipeline.** Eligibility is a provenance contract, not semantic approval. `backend/dataset/normalized.mjs` builds these references, and `backend/src/catalog.mjs` exposes them as `firecrawl-normalized`.

The frozen 200-row `backend/data/slangfeed_snapshot.csv`, `sense_reviews.json` and `curated_senses.json` belong to historical/compatibility flows. The snapshot is not an active source, is not refreshed, and is not required to populate the normalized catalogue. Nothing here modifies the independent `ferrysrt/slangfeed` repository. Legacy mocks and published packs remain readable without being represented as new normalized evidence.

## Refresh and cost boundary

**Actions → Refresh normalized slang dataset** (`.github/workflows/slang-candidates.yml`) supports manual dispatch and runs weekly **Monday 03:00 UTC / 10:00 WIB** (`0 3 * * 1`). Each run is bounded to one Firecrawl v2 search returning up to twenty results with `tbs: sbd:1` (newest first) and one OpenRouter normalization request, without retries. The default query embeds the current UTC month, day and year and names tiktok, twitter and instagram, so a scheduled run asks for that day's slang rather than a timeless "latest slang" string. A run whose capture yields fewer than **ten surviving pages** fails with `insufficient-scraped-documents` before the normalizer and leaves the previous dataset untouched. Provider charges and quotas apply. Search results are not a representative usage survey.

Credentials stay in `FIRECRAWL_API_KEY` and `OPENROUTER_API_KEY` repository secrets; optional `OPENROUTER_MODEL` defaults to `openai/gpt-6-luna`. Captured web text is sent to the normalizer as untrusted data. Every captured character is submitted: the capture is split into ~60,000-character chunks with ~2,000 characters of overlap and one `normalizer-v2` request is made per chunk, so a 200,000-character glossary is read end to end instead of being truncated to its first 8,000 characters. Chunk candidates are merged by `candidateId`, and each citation is still verified against the full captured markdown. An explicit budget of 40 normalizer requests per run fails the run rather than silently dropping text. No provider key belongs in Android, public JSON or experiment logs.

From the repository root, with secrets supplied securely:

```sh
node backend/dataset/refresh.mjs
```

The workflow persists the own JSON and CSV and uploads a normalized-dataset artifact with the run's audit evidence; both steps run even when collection fails. Runtime success of the pending refresh must be reported from its actual run, not inferred from a workflow definition. Dataset refresh and mixed question generation are separate bounded operations; a phone refresh is neither of them.

## Strict literal provenance, not semantic validation

Discovery rejects an uncollected URL, nonliteral or case-altered quote, quote shorter than five words, or quote that omits the term. Only whitespace differences are tolerated; the actual captured excerpt is kept. Every supplied citation must pass: one valid citation cannot excuse another fabricated one.

An automatically eligible record retains:

- `candidateId`, term and nonempty `normalizedDefinition`, plus `normalizationModel`;
- `sourceEvidenceStatus: literal-excerpt-unreviewed`;
- nonempty sources with actual URL, title, literal quote, capture timestamp, `collectionMethod: firecrawl` and full captured-markdown SHA-256.

The normalizer is asked for a definitional excerpt, but code verifies literal occurrence and provenance, **not whether the excerpt truly supports the normalized sense**. `normalized-unreviewed` and `expertValidated: false` remain visible. Do not call these entries expert-reviewed, verified meanings or measured trends.

Candidate identity is the first 24 hex characters of SHA-256 over the JSON pair of normalized term and normalized definition (NFKC, collapsed whitespace, lowercase). The sense ID is `normalized-` plus that candidate ID. A different normalized definition creates a different sense; term-only deduplication must not merge meanings. Duplicate identities retain distinct source captures rather than overwriting earlier evidence.

Collection time is when the Firecrawl response was received, not a model-supplied date, migration time or claimed page publication date. `firstSeenAt` and `lastCollectedAt` derive from recorded captures; unknown legacy times stay unknown. Dataset and CSV rows are ordered by newest recorded capture, not popularity. Full copyrighted pages are not republished; their hashes are fingerprints, not semantic endorsements.

## JSON, CSV and versioning

JSON preserves the complete source array. CSV has **one row per source capture**, so row count need not equal entry count. Columns are:

```text
term,sense_id,definition,source_url,source_title,source_quote,collected_at,collection_method,review_status,normalized_definition,normalization_model,candidate_id,first_seen_at,last_collected_at,source_evidence_status,content_sha256
```

CSV quoting preserves commas, quotes and multiline excerpts. It is not the legacy three-column snapshot format. Incomplete legacy records remain inspectable but are not automatically playable; missing timestamps, hashes or evidence status must not be invented.

`datasetVersion` is a `firecrawl-normalized-<content hash>` identifier, insensitive to JSON formatting and line endings. Active catalogue `candidateCount` and `discoveredCandidateCount` refer to this own dataset; eligible sense count may be smaller if records fail provenance requirements. Historical pack metadata may still name its old snapshot version/count and must not be rewritten as new collection.

## Normalized senses → mixed bank

The mixed publisher samples eligible normalized senses directly, preferring ten distinct terms and requiring at least two. Each pack has ten questions with a nominal 5/3/2 difficulty split, references embedded per sense and each question linked by `senseId`. Nine categories are allowed, not a mandatory all-category quota. No new hand-authored mocks are invented for normalized entries.

Run `backend/scripts/publish-mixed-bank.mjs` separately: default one attempt, maximum three, no retries or mock fallback. See [bank operations](GITHUB_GET_QUESTIONS.md), [prompt protocol](PROMPT_ENGINEERING.md) and [architecture](ARCHITECTURE.md). Main's summary CLI and failure-safe workflow artifact/persistence integration are pending, not claimed complete here.

## Historical record — manual mapping and legacy publication

This section records the earlier approach; it is **not an instruction to gate current eligibility**.

The 17 November 2026 discovery record [44089717679](https://github.com/ferrysrt/Slangverse-Android/actions/runs/44089717679) reported 16 accepted candidates and one rejection, captured at `2026-11-17T13:08:28.914Z`. All accepted entries came from one [UPrinting commercial glossary](https://www.uprinting.com/blog/top-gen-z-slang-words/), not sixteen independent sources. The persisted dataset retains the returned URL including its query string. This is a historical observation, not a new collection claim.

The former manual flow read source/safety evidence, added an explicit `sense_reviews.json` mapping with actual reviewer/time/rationale, then ran `dataset:curate` before publishing single-sense packs. It mapped `glow-up-improvement`, `rizz-flirting` and `iykyk-inside-reference`; at that checkpoint thirteen other candidates were not promoted. That restriction is superseded by automatic normalized eligibility.

The earlier publisher record [44090251900](https://github.com/ferrysrt/Slangverse-Android/actions/runs/44090251900) added three single-sense packs to the original three. Rizz/IYKYK later received disclosed agent-assisted corrections: flirting examples were clarified as dates, an artificial finance context removed, and a competing-correct IYKYK distractor replaced. `editorialReview` records the changes and the feed retains `agent-edited` disclosure. These are legacy publication notes, not mixed-v1 experiments or expert validation; no new run is asserted here.

### Historical meaning conflict retained

`cooked-trouble` means “in serious trouble,” while the frozen snapshot and a newer candidate emphasize exhaustion/overwhelm. The legacy `sourceRelationship: conflict` and `referenceNote` remain intact. The exhaustion candidate is a separate normalized sense, not evidence that the old reference was reconciled. Old packs and mocks retain their own meanings and provenance for compatibility.
