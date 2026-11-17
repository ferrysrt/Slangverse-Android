# Mixed-feed prompt engineering and research protocol

## Scope and evidence rule

The implemented mixed baseline is **`mixed-v4`**, stored in `backend/prompts/mixed-v4.txt` (with v1/v2/v3 retained) and rendered by `backend/src/mixedQuestions.mjs`. Product intent is accepted by the user; this is not a claim of supervisor-approved methodology, expert content validation or successful live generation. Earlier single-sense smoke runs are separate exploratory evidence, not this baseline.

Report only observations backed by actual attempt files. A future prompt revision needs its own saved template, actual runs and comparable metrics before it can be described as evaluated or better.

## Controlled input and request

Each attempt saves the sampled senses and literal sources, dataset version, pack/attempt IDs and ten assignments before calling the provider. Sampling is distinct-terms-first: prefer ten unique terms, require at least two, reuse selected senses if necessary. Assignments fix question IDs, senses and the nominal 5 easy / 3 medium / 2 hard split.

The prompt requires a single JSON object with `questions`, fictional social posts, ordinary indirect hint replies, a separate in-thread `questionComment`, four choices, an exact reference answer and an explanation. Every mixed question declares one of nine categories; repeats are allowed and natural term/context fit takes priority over category quotas. Reference data is explicitly untrusted and must not become instructions.

`mixedProvider.mjs` currently requests JSON-object output with temperature `0.5`, low reasoning effort, a default 10,000 output-token cap and 120-second timeout. Requested model defaults to `openai/gpt-6-luna`; model/token/timeout overrides must be recorded, not assumed. A workflow run may make up to **three** attempts (one provider invocation each, no retries, fence stripping, JSON substring extraction, repair generation or mock fallback) and **stops early as soon as one pack passes validation**; the workflow default is three. Every attempt — including the ones after a success when early stop is disabled, and every failure — is retained.

Prompt requests such as plausible distractors, naturally fitting contexts, varied voices and non-spoiling hints are **objectives**, not properties established by syntax checks.

## Required attempt evidence

Default location: `backend/experiments/<attemptId>/`.

| File | Meaning |
| --- | --- |
| `input.json` | Actual dataset version, selection, reference sources, assignments and selection parameters |
| `prompt.json` | Exact rendered system/user messages, not merely the template name |
| `request.json` | Non-secret provider parameters, timeout and messages; never Authorization headers or keys |
| `output.txt` | Raw model message content, unchanged, even if it is malformed JSON |
| `response.txt` | Raw successful HTTP response envelope, distinct from model content |
| `metadata.json` | Available requested/actual model, provider/response ID, usage, HTTP status and finish reasons, or sanitized failure code |
| `validation.json` | Named checks, pass/fail decisions and error categories |
| `result.json` | Attempt/pack IDs, timestamps, status, provider-call count and failure code |
| `pack.json` | Validated publishable pack, **only on successful generation** |

Files exist only when their stage is reached: a selection/configuration failure may have no prompt or request; a network/HTTP error may have no response or output. Preserve absence as missing evidence, never manufacture empty successful responses. Provider HTTP error bodies, credentials, headers and unsanitized exceptions are deliberately not retained. A `started` or `in-flight` result is incomplete, not a terminal success or a silently excluded failure.

The generator records actual returned `model`, `usage`, `finish_reason` and native finish reason when available. Missing provider values remain null/missing. `generatorModel` comes from the returned model, not the configured label. Missing actual model and known truncated/filtered/error completion states fail publication even if some JSON can be parsed.

**Integration status:** `scripts/report-experiments.mjs` (`npm run experiments:report`) aggregates real attempt directories into `summary.json` and `attempts.csv`, and the publisher workflow uploads `backend/experiments/` and `question-bank/` and persists both even when generation fails. It reports only observed records; it never invents attempts or successes.

## Nine critical output checks

These stable IDs are emitted by `evaluateMixedResponse`; report them individually, not just as a single success flag.

| Check ID | Pass criterion |
| --- | --- |
| `json-parse` | The complete raw model content parses as JSON without repair |
| `required-fields` | A questions array with required nonempty question, post, comment and answer fields |
| `count-10` | Exactly ten questions |
| `difficulty-5-3-2` | Exactly five easy, three medium and two hard labels |
| `four-options` | Four nonempty, lexically distinct options per question |
| `correct-index` | An integer zero-based answer index in 0–3 |
| `context-category` | Every category belongs to the allowed nine-value set |
| `unique-ids` | Nonempty question IDs unique within the pack |
| `no-surrounding-text` | One JSON object with only JSON whitespace outside it, no prose or fences |

All nine are necessary but insufficient for publication. Additional checks include:

- **Reference integrity:** supplied normalized sense IDs, exact term/meaning, correct option equal to the reference, slang present in the post, assignment match and use of all selected references, with at least two distinct terms. References preserve source evidence, not semantic endorsement.
- **Existing lexical question rules:** valid question-comment shape and literal target/option leakage checks in the question comment and ordinary replies. These do not reliably detect semantic spoilers.
- **Deterministic social guardrails:** fictional handle/avatar shape, approved bundled avatar IDs, no player-avatar impersonation, image payload fields, URLs/contact strings or explicit answer-index leaks in checked surfaces. Prompt prohibitions on harmful content are not a semantic safety classifier.
- **Provider completion and pack integrity:** returned model/completion metadata, pack origin/status/schema, embedded references, immutable IDs and catalogue hashes. Provider completion is a separate gate, not a tenth item in the critical-nine list.

Repeated `contextCategory` and `contextId` values are allowed in mixed packs. Unique question IDs must not be misreported as ten validated semantic contexts.

## Metrics from real evidence only

State the prompt version, dataset version, requested and actual model, provider parameters, collection period and sample size for every reported group. Keep fixtures/injected providers, legacy smoke runs, real mixed attempts and publication outcomes separate.

| Metric | Definition and denominator |
| --- | --- |
| Attempt outcomes | Count every real terminal attempt as valid or failed; list incomplete attempts separately |
| Item-level adherence | Distinct item indices appearing in `validation.errors` against the parsed `output.json` question count; `items_evaluated`, `items_with_errors`, `item_compliance` |
| Pack-level publishability | Attempts whose whole ten-item pack passed every gate / real terminal attempts; `packsPublishable` |
| Provider invocation count | Sum recorded `providerCalls`; distinguish pre-call failure from an invoked request, not an inferred billable-call count |
| Each critical-check rate | Passed / evaluated real attempts for that check; disclose missing/incomplete validation records |
| All-nine compliance | Attempts passing all nine / real terminal attempts with validation; separate additional-gate failures |
| Publishable generation rate | Valid generation results / real terminal attempts; do not equate this with successful Git publication |
| Publication outcome | Packs actually persisted and indexed, distinct from generated `pack.json` successes; reconcile publisher output/catalogue |
| Failure categories | Counts by sanitized provider/configuration/validation error; multiple validation reasons may occur in one attempt |
| Attempt duration | `completedAt - startedAt` from terminal results; includes selection and audit work, not just inference |
| Provider interval | Metadata completion minus start time where both exist; missing failure timings are not zero |
| Token usage | Actual provider-reported usage fields, reporting missing values and provider definitions |
| Completion outcomes | Actual finish/native-finish reasons and returned model values, including truncation/filters |

Report raw counts with rates, never percentages alone. If examining schema compliance conditional on parse success, label that subset explicitly alongside the end-to-end denominator. Do not drop failures to improve results. Use p50/p90/p95 only with disclosed sample size and quantile method; a single observation is not a benchmark. A phone's GitHub fetch time is not generation latency. Do not infer provider cost from tokens without the actual applicable price/routing evidence; usage is not itself a billed amount.

No numeric results are populated by this protocol. No live request or test count is claimed by documenting it.

## Observed revision — mixed-v1 to mixed-v2

One real `mixed-v1` attempt exists: [publisher run 44143393023](https://github.com/ferrysrt/Slangverse-Android/actions/runs/44143393023), record `mixed-20261117t180156794z-17f15768-0eb7-471f-8385-c6fc20e958af`. It made **one provider call** to `openai/gpt-6-luna`, received HTTP 200 with `finish_reason: stop` / native `completed`, and reported 5,200 prompt + 3,214 completion tokens (8,414 total, provider-reported cost 0.002256925). All nine critical syntax checks passed; the pack was rejected by the additional **`reference-integrity`** gate with exactly one error, `7:post-slang-missing`.

The concrete failure is reproducible from the saved `output.json`: question 8 was assigned the sense `larp` ("Short for live-action role-play; used for someone living a fake or performative life for social clout"). Its `post.content` described staged photos and borrowed studio keys but never contained the literal term; the term appeared only in `questionComment`. The prompt had asked for a post "using the exact slang term" without stating that the term must be literally present in that field.

Hypothesis: make the field-level obligation explicit. `mixed-v2` changes the schema example to "A natural short post that contains the exact slang term verbatim" and adds a rule that `post.content` must contain the exact reference term and that the normalized definition belongs only in `targetMeaning`/`explanation`. Everything else was unchanged.

A real `mixed-v2` attempt then exists: [publisher run 44144286530](https://github.com/ferrysrt/Slangverse-Android/actions/runs/44144286530), record `mixed-20261117t180657184z-b0523b3a-82b1-42cc-b286-e6da4f45cfdb`. One provider call, HTTP 200, `finish_reason: stop` / native `completed`. **All nine critical checks passed** — the `post-slang-missing` failure was resolved — but the additional `existing-question-rules` gate rejected four items (`5:`, `7:`, `8:`, `9:question-comment-shape`). In each, the `questionComment` omitted the literal term: e.g. `"What about Ren's presence stood out to you?"` for `aura`, and `"What is Tess suggesting about Nico's online work persona?"` for `larp`. The v2 wording that `questionComment` "may mention the term" was too weak and the model treated it as optional once the term was required in the post.

`mixed-v3` removes that ambiguity: `post.content` still must contain the term verbatim, and `questionComment` now explicitly **must also contain the exact slang term verbatim, in addition to its presence in post.content**, must end with a question mark, and must not substitute a pronoun, description, real-world referent or meaning. This is a second targeted revision from a second observed failure. Like v2 before it, it is not yet an evaluated improvement; its outcome must come from a later real attempt.

Both revisions are one-target-change steps from observed evidence, not a broad redesign and not a retrospective success claim.

A real `mixed-v3` attempt exists: [publisher run 44144857363](https://github.com/ferrysrt/Slangverse-Android/actions/runs/44144857363), record `mixed-20261117t181016380z-2806191a-d9ba-45fe-aef4-8d92d9e6adc6`. The post-term and question-comment failures did not recur. It failed differently: question 5 copied a paraphrase instead of the reference string — assigned meaning `"A dance indicating that someone or something smells bad."`, returned `"A move that signals someone or something smells bad"` — producing `4:reference-mismatch`, `4:key-meaning-mismatch` and `unused-reference`.

`mixed-v4` therefore requires the reference string to be copied character-for-character into both `targetMeaning` and the correct option. Its real attempt, [run 44146316232](https://github.com/ferrysrt/Slangverse-Android/actions/runs/44146316232), record `mixed-20261117t181815601z-a3ab2730-7330-486f-b922-3c485088338e`, fixed that: the meaning and option matched the reference verbatim. It failed on a different defect — **nine of ten `questionComment` values omitted the literal slang term** (e.g. `"Did the lockers really all start playing the same tune?"` for `ohio`). That is now the second version to lose the term from the comment (v2: four items, v4: nine), so the instruction-only approach is not reliable for this field.

### Aggregate observed via `mixed-v1` through `mixed-v4`

| Item | Observed |
| --- | --- |
| Real terminal attempts | 4 (one per version) |
| Provider calls | 4, one each, no retries |
| Critical nine, all passing | **4/4 attempts** |
| Publishable after additional gates | **0/4** |
| Failure categories | v1 `post-slang-missing`; v2 four `question-comment-shape`; v3 `reference-mismatch`/`key-meaning-mismatch`/`unused-reference`; v4 nine `question-comment-shape` |

The important interpretation is that **the nine critical checks alone would have accepted all four packs**; the reference-integrity, existing-question and completion gates are what stopped them. Do not report a "100% valid generation rate" from the critical-nine subset.

The bounded revision loop stops at v4. The `questionComment` term requirement is a **legacy self-imposed constraint** (inherited from the single-sense V3.1 validator), not one of the nine critical checks. Three options remain for a future decision, none taken here: (a) keep the constraint and accept repeated rejection, (b) drop the redundancy with an explicit justification because the term is already required in `post.content`, or (c) add a disclosed deterministic repair step and record it as a repair rather than a clean generation. Weakening the validator silently to obtain a pass was deliberately not done.

## Revision and interpretation protocol

1. Freeze/archive the baseline template, rendered input, dataset identity and provider parameters before interpreting results.
2. Run only the authorized bounded budget; persist invalid and failed attempts as well as successes.
3. Inspect named failures and real raw output. Record a specific hypothesis for any prompt change.
4. Give a changed template a new version only when it exists. Use matched/frozen references and comparable model/settings for controlled comparisons, or disclose selection/provider confounds.
5. Separate prompt development examples from held-out evaluation. Do not turn fixture validation into an LLM experiment or retrospectively invent v1/v2 trials.
6. Report syntax/contract findings within their limits. Expert semantic review, ambiguity, distractor plausibility, bias, safety and empirical difficulty require separate evaluation if claimed.

SUS assesses usability under its own study protocol; it does not prove learning gains, motivation improvements or linguistic validity. See [research guardrails](RESEARCH_GUARDRAILS.md) and [accepted product intent](MIXED_FEED_HANDBOOK.md). Final verification belongs in the actual integration report; this page does not populate `TEST_RESULTS.md`.
