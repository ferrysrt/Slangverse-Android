# Mixed-feed prompt engineering and research protocol

## Scope and evidence rule

The implemented mixed baseline is **`mixed-v2`**, stored in `backend/prompts/mixed-v2.txt` (with `mixed-v1.txt` retained) and rendered by `backend/src/mixedQuestions.mjs`. Product intent is accepted by the user; this is not a claim of supervisor-approved methodology, expert content validation or successful live generation. Earlier single-sense smoke runs are separate exploratory evidence, not this baseline.

Report only observations backed by actual attempt files. A future prompt revision needs its own saved template, actual runs and comparable metrics before it can be described as evaluated or better.

## Controlled input and request

Each attempt saves the sampled senses and literal sources, dataset version, pack/attempt IDs and ten assignments before calling the provider. Sampling is distinct-terms-first: prefer ten unique terms, require at least two, reuse selected senses if necessary. Assignments fix question IDs, senses and the nominal 5 easy / 3 medium / 2 hard split.

The prompt requires a single JSON object with `questions`, fictional social posts, ordinary indirect hint replies, a separate in-thread `questionComment`, four choices, an exact reference answer and an explanation. Every mixed question declares one of nine categories; repeats are allowed and natural term/context fit takes priority over category quotas. Reference data is explicitly untrusted and must not become instructions.

`mixedProvider.mjs` currently requests JSON-object output with temperature `0.5`, low reasoning effort, a default 10,000 output-token cap and 120-second timeout. Requested model defaults to `openai/gpt-6-luna`; model/token/timeout overrides must be recorded, not assumed. The publisher defaults to one attempt and permits at most three. Each attempt has at most one provider invocation: no retries, fence stripping, JSON substring extraction, repair generation or mock fallback.

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

Hypothesis: make the field-level obligation explicit. `mixed-v2` changes the schema example to "A natural short post that contains the exact slang term verbatim" and adds a rule that `post.content` must contain the exact reference term, that `questionComment` cannot substitute for it, and that the normalized definition belongs only in `targetMeaning`/`explanation`. Everything else is unchanged. This is one targeted revision from one observed failure — not a broad redesign, and not yet an evaluated improvement. Its outcome must come from a later real attempt.

## Revision and interpretation protocol

1. Freeze/archive the baseline template, rendered input, dataset identity and provider parameters before interpreting results.
2. Run only the authorized bounded budget; persist invalid and failed attempts as well as successes.
3. Inspect named failures and real raw output. Record a specific hypothesis for any prompt change.
4. Give a changed template a new version only when it exists. Use matched/frozen references and comparable model/settings for controlled comparisons, or disclose selection/provider confounds.
5. Separate prompt development examples from held-out evaluation. Do not turn fixture validation into an LLM experiment or retrospectively invent v1/v2 trials.
6. Report syntax/contract findings within their limits. Expert semantic review, ambiguity, distractor plausibility, bias, safety and empirical difficulty require separate evaluation if claimed.

SUS assesses usability under its own study protocol; it does not prove learning gains, motivation improvements or linguistic validity. See [research guardrails](RESEARCH_GUARDRAILS.md) and [accepted product intent](MIXED_FEED_HANDBOOK.md). Final verification belongs in the actual integration report; this page does not populate `TEST_RESULTS.md`.
