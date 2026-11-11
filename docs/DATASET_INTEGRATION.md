# SlangFeed source dataset integration (V2.3)

This implementation imports 200 legacy SlangFeed CSV entries as **unreviewed candidates**, not validated meanings. The current source snapshot comes from `ferrysrt/slangfeed` on 11 November 2026. Legacy definitions do not include per-term source URLs. Automated discovery is review-only.

## Architecture

- Node backend loads a frozen CSV snapshot and parses three-column candidate records.
- The game uses a separate `curated_senses.json` file containing three *provisional, manually authored* senses: `cooked-trouble`, `no-cap-truth`, `lowkey-subtle`.
- Android API Training requests `GET /api/catalog` then chooses `senseId` for `POST /api/sessions`.
- For each sense, a 10-question hand-authored mock pack is available. On-demand LLM generation can use selected target meaning when separately configured.
- Research metadata differentiates candidate, provisional, mock, fallback, and LLM. No professional semantic evaluation is claimed.

## HTTP usage

```
GET /api/catalog
GET /api/catalog/candidates?q=cooked&limit=10
POST /api/sessions
{"senseId":"no-cap-truth"}
```

## Weekly discovery

Workflow `.github/workflows/slang-candidates.yml` runs on Monday UTC 03:00 or manually. It requires repository Actions secrets `FIRECRAWL_API_KEY` and `OPENROUTER_API_KEY`, with optional variable `OPENROUTER_MODEL` (default `openai/gpt-oss-120b`). It searches via Firecrawl v2 and normalizes with a separate backend LLM call.

The workflow **uploads an unreviewed artifact only**. It does NOT automatically publish to the playable glossary, does not write to the Godot repo, and does not claim per-sense linguistic accuracy.

The candidate collector rejects entries without matching term and exact evidence excerpt on a crawled source page, but those syntactic checks are NOT a semantic correctness guarantee. Manually review each candidate's source, sense, safety, and licensing before adding new playable meanings. Do not publish scraped full-text pages.

## Known limitation

The SlangFeed dataset's `Cooked` means extreme tiredness/overwhelm; the gameplay sense `cooked-trouble` means serious trouble. The latter is independently authored and is not validated by the legacy CSV definition.
