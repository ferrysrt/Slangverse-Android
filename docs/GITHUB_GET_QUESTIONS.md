# Published mixed feed — GitHub GET

## Current contract and rollout boundary

Android Home consumes pre-generated public JSON from:

```text
https://raw.githubusercontent.com/ferrysrt/Slangverse-Android/main/question-bank
```

The mixed publisher/client source supports schema 2: `catalog.json` advertises mixed `packs` alongside compatibility `senses`. Each pack is `packs/<packId>.json` or legacy `packs/<senseId>.json`. Source support is not a claim that a new mixed pack has been generated/published. The checked-in legacy catalogue can remain schema 1 until mixed publication; use the actual catalogue and audit artifacts to determine published availability.

Planned release metadata is `0.5.0-mixed-feed`, version code `6`, to be set by main. A fresh dataset refresh, real mixed generation results and final release verification are not asserted here.

## Play

Home prefers mixed packs when the catalogue lists them, otherwise merges legacy single-sense packs. It does **not** fall back to legacy when an advertised mixed pack fails to load. There are no slang-selection chips; context tabs remain. **Refresh bank** fetches the catalogue/packs and reshuffles on successful load without rebuilding the APK. The current loader selects up to twelve packs, so Home may contain more than ten posts.

The merged order is saved for the same loaded content across recomposition, navigation and supported activity recreation. An explicit refresh starts a new shuffle; a failed refresh reports an error and can retain prior published posts in their existing order. **Question source** changes the public folder URL or explicitly opens the labeled hand-authored offline sample. Errors never silently turn mock content into LLM output.

The phone needs neither a backend server nor a provider token for Home and never calls the LLM. Source labels disclose local grading and unreviewed/provisional references; retained agent-edited legacy packs keep that disclosure. Avatars, replies, profiles, context filters, Saved and feedback use the loaded questions. Connected Training remains a separate static/live route with its own source labels.

## Mixed publisher

With Node.js 20+ and `LLM_API_KEY` supplied securely, run from the repository root:

```sh
node backend/scripts/publish-mixed-bank.mjs
```

Defaults are module-relative, so the default bank and experiment paths do not depend on the shell's current directory. Optional `OUTPUT_DIR` and `BANK_EXPERIMENTS_DIR` overrides are resolved from the invoking directory; prefer absolute paths when the shell ignores a requested working directory.

- `BANK_MAX_PACKS`: default **1**, allowed **1–3**. Each attempt requests one ten-question mixed pack, with **no retries, JSON repair or mock fallback**.
- `LLM_MODEL`: default `openai/gpt-6-luna`; `LLM_API_BASE_URL`: default OpenRouter. Actual returned model, usage and finish reasons are logged, not assumed from configuration.
- Input: automatically eligible normalized senses from the own Firecrawl dataset, not manually promoted curated entries or the old slangfeed snapshot. Initially 16 entries are carried forward; fresh refresh is pending.
- Selection: prefer ten distinct terms, require at least two; reuse selected senses if fewer than ten. Every pack has ten questions and nominal 5 easy / 3 medium / 2 hard labels.
- Every question links by `senseId` to an embedded reference; categories belong to the allowed nine but may repeat. Ten unique scenario IDs/all nine categories are not mixed-pack requirements.
- Only validated LLM output becomes a new immutable public pack. Failed attempts retain audit evidence and produce a nonzero publisher exit when reported failures exist. Already valid packs are preserved.

Generation sends sourced reference data to an external provider and can incur charges. Keep keys out of arguments, public JSON, the APK and audit artifacts. Do not run the publisher just to validate documentation.

## Catalogue, integrity and retention

Schema 2 includes origin/status/version metadata, `expertValidated: false`, `packs: [{packId, sha256, questionCount: 10}]` and legacy `senses` entries. Mixed packs carry `schemaVersion: 2`, `packId`, `generationSource: llm`, actual `generatorModel`, `promptVersion: mixed-v1`, generation time, dataset version, `referenceStatus: normalized-unreviewed`, references and questions.

Catalogue SHA-256 values cover exact public pack bytes. Retained legacy entries are checked against their pinned files; old references are not silently rebound to a changing normalized dataset. The mixed catalogue retains up to twelve valid mixed entries, newest first. Older files remain untouched even when no longer indexed. The catalogue is replaced only after new immutable pack files exist; publication must ship a consistent set.

Android verifies checksum, origin, shape and per-question reference matching. Question identity is pack ID/content hash/question ID, preventing answer reuse across revisions and repeat XP for the same revision. A checksum proves byte integrity, not semantic quality or a signature from an expert.

## Audit and workflow integration

Each attempt uses `backend/experiments/<attemptId>/`: `input.json`, `prompt.json`, `request.json`, raw model `output.txt`, raw provider `response.txt`, `metadata.json`, `validation.json`, `result.json`, and `pack.json` on success, as available by stage. Preserve failures; never fabricate responses for pre-call failures. `mixed-v3` is the active baseline; three real attempts exist and all were rejected by validation.

**Integration status:** the experiment-report summary CLI and `.github/workflows/publish-question-bank.yml` run the mixed publisher/report, default to three attempts with early stop at the first valid pack, upload artifacts and persist available audit files even when generation fails. Publish valid additions and the catalogue consistently without losing failed-attempt evidence. The older workflow's curated-sense trigger/default-three/single-sense behavior is historical integration, not the new approved contract and not proof this pending work has run.

The dataset workflow separately refreshes Monday 03:00 UTC / 10:00 WIB with no manual review gate. Home launches and GitHub GET refreshes never trigger paid generation. See [dataset operations](DATASET_INTEGRATION.md) and [prompt/metrics protocol](PROMPT_ENGINEERING.md).

## Limits and historical compatibility

Public static JSON necessarily exposes answers and explanations. Scores are local practice, not authenticated competitive results, durable participant logs or secure research grading. Syntactic checks do not establish semantics, calibrated difficulty or learning gains. Banks must be public on an allowed GitHub Raw/Pages HTTPS host without credentials; never embed a GitHub token to access a private bank.

**Historical 0.4.0 record:** six single-sense packs covered cooked, no cap, lowkey, glow up, rizz and IYKYK. The latter three used explicit provisional Firecrawl mappings; rizz/IYKYK retain disclosed `editorialReview` corrections and `agent-edited` labeling. The earlier guide recorded anonymous HTTPS/hash checks, not mixed publication. The standalone legacy exporter merges legacy entries rather than replacing the catalogue; artifact-only/single-sense workflows remain compatibility tooling. The former example `Slangverse-Question-Bank` URL was not the deployed bank. None of these notes is a new live-run claim.
