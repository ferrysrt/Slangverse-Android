# Active dataset — own Firecrawl-normalized slang

## Current source of truth

The research dashboard selects immutable dataset versions from Supabase. The participant bank is also stored there and is frozen into the APK at build time. The regular app and legacy publication and replay tools still use the repository snapshots. See [dashboard operations](RESEARCH_ADMIN.md).

Eligible entries become playable normalized senses with review status `normalized-unreviewed`. The parser keeps an explicit glossary definition and its exact supporting excerpt. This supports audit of provenance. Expert validation is a separate research step.

## Refresh and cost boundary

**Actions > Refresh normalized slang dataset** runs Monday **03:00 UTC / 10:00 WIB**. Live mode makes one Firecrawl search with Markdown results. The default query targets English internet slang glossaries with explicit definitions. Code extracts lines such as **Cooked** followed by a delimiter and a definition. A batch with fewer than ten distinct supported terms fails before the Supabase import. The worker inserts a new version and preserves earlier versions.

Manual runs default to **replay**. The workflow verifies SHA-256 for complete pages from the archived capture, preserves the original capture time, and imports the resulting version. Use **live** for a new collection. Live mode uses Firecrawl credits. Both modes keep OpenRouter out of dataset preparation. The temporary CSV uses the columns `slang,definition,date,source`. The date is the publication date from the source metadata when available. Supabase JSON preserves supporting quotes and capture metadata. Full page captures from live runs stay out of GitHub.

For an offline inspection, set `DATASET_EXPORT_DIR` and run:

```sh
node backend/dataset/refresh-deterministic.mjs --replay path/to/sources.json
```

The historical normalizer and versioned evidence remain available for reproducing earlier runs. The old refresh used bounded chunks and one truncation retry per chunk, failing before it replaced the snapshot when a chunk still exceeded its output limit.

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

The mixed publisher samples eligible normalized senses directly, preferring ten distinct terms and requiring at least two. Before planning, `backend/src/termQuality.mjs` drops terms that cannot be graded as one answer (interjections such as `haha`, bare acronyms such as `bff`, ordinary words such as `fire`) and collapses duplicate spellings, so `bussin'` does not become a second term beside `bussin`. Curation never edits the dataset: a blocked term stays archived as unreviewed evidence. Each pack has ten questions with a nominal 5/3/2 difficulty split, references embedded per sense and each question linked by `senseId`. Nine categories are allowed, not a mandatory all-category quota. No new hand-authored mocks are invented for normalized entries.

Run `backend/scripts/publish-mixed-bank.mjs` separately: default one attempt, maximum three, no retries or mock fallback. See [bank operations](GITHUB_GET_QUESTIONS.md), [prompt protocol](PROMPT_ENGINEERING.md) and [architecture](ARCHITECTURE.md). Main's summary CLI and failure-safe workflow artifact/persistence integration are pending, not claimed complete here.

## Historical record — manual mapping and legacy publication

This section records the earlier approach; it is **not an instruction to gate current eligibility**.

The 17 November 2026 discovery record [44089717679](https://github.com/ferrysrt/Slangverse-Android/actions/runs/44089717679) reported 16 accepted candidates and one rejection, captured at `2026-11-17T13:08:28.914Z`. All accepted entries came from one [UPrinting commercial glossary](https://www.uprinting.com/blog/top-gen-z-slang-words/), not sixteen independent sources. The persisted dataset retains the returned URL including its query string. This is a historical observation, not a new collection claim.

The former manual flow read source/safety evidence, added an explicit `sense_reviews.json` mapping with actual reviewer/time/rationale, then ran `dataset:curate` before publishing single-sense packs. It mapped `glow-up-improvement`, `rizz-flirting` and `iykyk-inside-reference`; at that checkpoint thirteen other candidates were not promoted. That restriction is superseded by automatic normalized eligibility.

The earlier publisher record [44090251900](https://github.com/ferrysrt/Slangverse-Android/actions/runs/44090251900) added three single-sense packs to the original three. Rizz/IYKYK later received disclosed agent-assisted corrections: flirting examples were clarified as dates, an artificial finance context removed, and a competing-correct IYKYK distractor replaced. `editorialReview` records the changes and the feed retains `agent-edited` disclosure. These are legacy publication notes, not mixed-v1 experiments or expert validation; no new run is asserted here.

### Historical meaning conflict retained

`cooked-trouble` means “in serious trouble,” while the frozen snapshot and a newer candidate emphasize exhaustion/overwhelm. The legacy `sourceRelationship: conflict` and `referenceNote` remain intact. The exhaustion candidate is a separate normalized sense, not evidence that the old reference was reconciled. Old packs and mocks retain their own meanings and provenance for compatibility.

