# Mixed-feed handbook — accepted user intent

## Decision and scope

The user-approved direction is a varied social-style feed backed by the project's own normalized, sourced slang dataset. Players should read different slang terms naturally while browsing; they should not have to select a slang chip and replay ten posts about that same term.

This is the accepted product/engineering plan, not a claim of supervisor-approved methodology or completed validation. Source implementation and pending integration are separated below. Main is to set planned version `0.5.0-mixed-feed`, version code `6`; this guide does not declare a released APK.

## Accepted pipeline

1. **Own dataset:** Firecrawl collection and LLM normalization persist in `backend/data/discovered_candidates.json` and its source CSV. There are initially 16 carried-forward entries; a fresh refresh is pending. The old slangfeed snapshot is historical only, never the active feed source.
2. **Automatic eligibility:** entries satisfying literal provenance requirements receive stable `normalized-<candidateId>` sense IDs. No manual review gate is required. Retain `normalized-unreviewed` status and explicitly distinguish literal provenance from semantic correctness.
3. **Weekly refresh:** Monday 03:00 UTC / 10:00 WIB, with optional manual dispatch. One Firecrawl search and one normalizer request, no retries. Provider cost/quotas and source limitations remain visible.
4. **Mixed generation:** `backend/scripts/publish-mixed-bank.mjs` samples terms, prefers ten unique terms, and requires at least two distinct terms. Every pack has ten questions, nominal 5 easy / 3 medium / 2 hard, and per-question links to embedded sense references. If fewer than ten terms are available, selected senses may repeat.
5. **Bounded paid work:** default one mixed attempt, maximum three per invocation, at most one provider call per attempt, no retries, repair calls or mock fallback. Only `mixed-v1` is the implemented baseline; no invented v2 experiments or claimed improvements.
6. **Auditability:** preserve actual input, rendered prompt, request parameters, raw output/response, metadata, validation and result when available; successful generation also saves a pack. Actual model, usage and finish reasons must be retained, not inferred.
7. **Publication:** schema 2 catalogues advertise mixed `packs` and retained legacy `senses`. New mixed pack files are immutable and checksummed. Keep valid old packs for compatibility; do not rewrite their reference history as new normalized data.

## Accepted Home behavior

- Load mixed packs when advertised; use legacy packs **only if no mixed packs are listed**. A mixed download/validation failure is an explicit error, not permission to silently select legacy/mock content.
- Merge selected packs into one feed. Ten is the per-pack generation size, not necessarily the total feed size. Current loading/retention is bounded to twelve packs.
- Remove slang-selection chips. Keep **For you**, the nine context tabs, and Saved. `News` is fictional event reaction, not live news.
- Every mixed question has one of `personal`, `education`, `work`, `prediction`, `sports`, `entertainment`, `opinion`, `friends`, `event-reaction`. Categories/scenario IDs may repeat; do not force an unnatural context just to fill all nine.
- Shuffle once on a fresh load or explicit refresh. Keep saved order stable through recomposition, navigation and supported state restoration for the same content. A failed refresh preserves the previous published order when available.
- Keep replies visible initially and independently collapsible. Opening replies does not open answer choices. Players explicitly choose an interpretation; Learning feedback remains visually separate from fictional conversation.
- Preserve local avatars, profiles, bookmarks, feedback inbox, quests and results. Generated packs do not acquire invented scripted story continuations from the authored sample.
- Scope answers to pack ID, content hash and question ID. Correct/incorrect XP remains 80/15 once per question identity; filters, refresh and restored order do not award it again. Keep published, authored and archived practice isolated.
- The phone GETs public JSON and grades locally. It never calls an LLM, carries a provider key, or incurs generation by opening/refreshing Home. Static answers are public and local XP is not secure competitive scoring or durable participant logging.

## Validation boundaries

The critical nine are whole-output JSON parse, required fields, ten items, 5/3/2 split, four distinct options, valid correct index, supported context category, unique question IDs and no surrounding prose/fences. Reference/assignment integrity, existing lexical rules, deterministic social guardrails and provider completion are additional gates. See [the exact protocol](PROMPT_ENGINEERING.md).

Passing these checks does not establish semantics, naturalness, safety against all harmful text, subtle-hint quality, one defensible answer, calibrated difficulty or learning gains. Automatic eligibility deliberately does not masquerade as expert approval.

## Main integration still to complete/report

- Create the experiment-report summary CLI from real attempt files; keep success, failure, incomplete and fixture evidence distinguishable.
- Wire the mixed publisher/report into the publishing workflow with default one and maximum three attempts. Upload available artifacts and persist audit files even on generation failure, together with valid public additions and a consistent catalogue.
- Run/report the pending dataset refresh and any authorized real mixed generation from their actual outputs. Do not claim these succeeded in advance.
- Set version name/code on main and report actual checks/build/publication outcomes. Do not populate final `TEST_RESULTS.md` before those results are known.

These are integration/reporting responsibilities, not proof of an already successful workflow or release.

## Acceptance checklist — to execute, not a result

1. Inspect JSON/CSV sources and automatic sense IDs; confirm the old snapshot is not required by the active catalogue.
2. Inspect a real attempt's prompt/input, requested versus actual model, usage, finish reason, raw response and validator decisions; retain a failed attempt without inventing missing output.
3. Check schema 2 catalogue hashes and immutable mixed packs; verify legacy entries/files remain compatible.
4. Cold-load Home, verify mixed content with no slang chips, and try every context tab and Saved. Test a legacy-only catalogue separately.
5. Navigate/recompose/restore and confirm order remains stable; refresh successfully and confirm a newly shuffled load. Fail a mixed fetch and confirm an explicit error without legacy/mock substitution.
6. Answer correctly and incorrectly, change filters/refresh/restore, and confirm local XP idempotence and pack-revision isolation. Check reply defaults, profiles, feedback and archived/Connected Training boundaries.
7. Validate the installed APK version and rendered behavior on an actual device; source/parser checks alone do not establish usability.

## Historical compatibility

The older 0.4.0 flow offered single-sense packs and slang chips; the earlier manual process promoted three Firecrawl candidates alongside three authored legacy senses. Those files and disclosed agent edits are retained, but neither that promotion gate nor chip-based browsing is the current design. The archived three-case Context Detective and opt-in single-sense authored feed remain separate demonstrations. Earlier live smoke observations are not mixed prompt experiments.

See [architecture](ARCHITECTURE.md), [dataset operations](DATASET_INTEGRATION.md), [GitHub distribution](GITHUB_GET_QUESTIONS.md) and [implementation status](IMPLEMENTATION_STATUS.md).
