# Dataset discovery, definition sources and explicit sense review

## Separate candidate data from playable references

- `backend/data/slangfeed_snapshot.csv` remains the frozen 200-row, three-column legacy snapshot. Its definitions have no per-term citation; no URLs have been retroactively invented. Its parser/header are unchanged.
- `backend/data/discovered_candidates.json` is the canonical, incremental Firecrawl dataset. Version 2 records normalized definitions separately from literal source quotations, collection times, page hashes and the actual normalizer model. All discovered entries remain `unreviewed`.
- `backend/data/discovered_candidates.csv` is a readable source table: **one row per source capture**, preserving multiple sources in JSON and CSV. Columns include term, sense_id, definition, source_url, source_title, source_quote, collected_at, collection_method, review_status, normalized_definition, normalization_model and candidate_id. Standard CSV quoting supports commas, quotes and multiline excerpts. Do not import this as the legacy snapshot.
- `backend/data/sense_reviews.json` explicitly maps reviewed candidate IDs to new sense IDs and a target meaning. `curated_senses.json` is the separate playable reference catalogue, still provisional, not expert certified.

Candidate identity hashes the normalized term **and definition**, not the term alone. Distinct senses are not silently merged. Exact duplicate candidates retain all distinct source captures. A collection timestamp is the time the Firecrawl response was received, never a model-supplied date. Page hashes fingerprint captured markdown; full copyrighted pages are not republished.

## Bounded discovery workflow

Run **Actions → Slang candidate discovery - manual review**, optionally supplying a search query. The Monday 03:00 UTC schedule is retained. Each run uses at most **one Firecrawl v2 search** (ten pages) and **one OpenRouter normalization request**, with no retries. Charges/provider quotas apply. The web text is sent to the normalizer, treated as untrusted content, and limited to 80,000 prompt characters.

Bindings are `secrets.FIRECRAWL_API_KEY`, `secrets.OPENROUTER_API_KEY`, and optional `vars.OPENROUTER_MODEL`, defaulting to `openai/gpt-6-luna`. Do not put credentials in Android or the dataset. Secret presence is distinct from local environment configuration.

The model must propose a literal definitional excerpt containing the term. Validation rejects URLs absent from the collected documents, invented/case-altered quotations, short excerpts and excerpts missing the term. Whitespace differences are allowed and the original captured excerpt is retained. **A matching excerpt does not prove that the normalized definition is correct**; the resulting status is `literal-excerpt-unreviewed`, not semantically validated. A developer must inspect whether it actually explains the intended sense, rather than merely mentioning the term. Legacy term-appearance-only evidence is not silently upgraded.

A successful run uploads both files as `unreviewed-slang-candidates` and commits the candidate JSON/source CSV to this repository. Sources therefore persist beyond workflow logs/artifact retention. Discovery does not create new reviews or automatically make every candidate playable, and does not touch `ferrysrt/slangfeed`. Candidate-only commits do not trigger question generation.

## Review → playable sense → LLM bank

1. Read the candidate's actual source quote, URL, context and normalized meaning. Check safety, source support and distinct sense identity. Revisit the source where necessary. Do not approve an ambiguous definition merely because schema validation passed.
2. Add an explicit entry to `backend/data/sense_reviews.json`:
   ```json
   {
     "candidateId": "<existing candidate ID>",
     "senseId": "<new-stable-sense-id>",
     "targetMeaning": "<one reviewed reference meaning>",
     "reviewedBy": "<actual reviewer or clearly identified agent-assisted review>",
     "reviewedAt": "<actual ISO timestamp>",
     "referenceNote": "<source/sense/safety rationale and remaining uncertainty>"
   }
   ```
   This is developer/agent-assisted source review, **not linguistic expert approval**.
3. Run `npm --prefix backend run dataset:curate`. It verifies provenance and explicit mappings, preserves legacy senses and conflict notes, copies the supporting sources into the new provisional sense, and exports sense IDs into the CSV. Re-running is idempotent; an existing sense ID cannot silently change meaning/candidate. Commit reviews, curated senses and table together.
4. A curated-sense change on main triggers **Publish LLM question bank**, or run it manually. The existing ceiling of three ten-question generation requests/run and no retries remains. Editing the publisher workflow alone no longer starts a paid run. Current packs are skipped; valid older packs survive provider failures. Each sourced pack also carries `sourceCandidateId`, `normalizedDefinition` and `referenceSources`; publication verifies these against its curated reference.
5. Verify the public catalogue, hashes and packs, then **Refresh bank** on Android. New slang needs no APK rebuild. No request is made to the paid provider by the phone.

New senses do not acquire invented hand-authored mock packs. Real LLM sessions can use them directly. An unavailable provider without an authored fallback produces an explicit 503 instead of a wrong-slang or falsely labeled mock result. Existing three offline/mock senses keep their authored behavior.

The dataset version string continues to identify the legacy snapshot; each new reference additionally has its candidate identity and literal provenance, and published content has its own exact-byte hash. `candidateCount` remains the legacy snapshot count; `discoveredCandidateCount` reports the separate Firecrawl dataset.

## Verified collection and provisional mappings — 17 November 2026

Discovery run [44089717679](https://github.com/ferrysrt/Slangverse-Android/actions/runs/44089717679) succeeded: 16 candidates accepted, one rejected, capture time `2026-11-17T13:08:28.914Z`. The accepted entries all came from one [UPrinting commercial glossary](https://www.uprinting.com/blog/top-gen-z-slang-words/), not sixteen independent sources. Its snippets explicitly define the terms, but neither its marketing claims nor publication date establish current usage frequency. Actual captured URLs (including the returned query string), titles and quotes are retained in the dataset; the page was also read directly during agent-assisted review.

Explicit mappings: `glow-up-improvement`, `rizz-flirting`, `iykyk-inside-reference`. Thirteen other candidates remain unplayable. The new `cooked` candidate describes exhaustion and is **not** mapped to `cooked-trouble`.

Publisher run [44090251900](https://github.com/ferrysrt/Slangverse-Android/actions/runs/44090251900) generated three new packs, preserving the original three. Rizz/IYKYK then received disclosed agent-assisted editorial corrections: several invitations were clarified as dates, an artificial finance context was removed, and one IYKYK distractor that duplicated the key was replaced. `editorialReview` metadata lists item-level reasons; the feed adds `agent-edited` to these packs' source labels. LLM origin, original generator model/time and provisional status remain intact. No additional paid generation was used for those corrections. These edits change content hashes, so progress remains isolated by the revised pack identity.

## Known legacy meaning conflict

`cooked-trouble` means “in serious trouble,” while the legacy snapshot's Cooked emphasizes tiredness/overwhelm. Its `sourceRelationship: conflict` and `referenceNote` remain intact. No new source is attached to this legacy sense without a separate review.

All source-supported and generated content remains provisional. Structural tests do not establish semantic validity, empirically calibrated difficulty, learning gains, usability or authenticated participant logging.
