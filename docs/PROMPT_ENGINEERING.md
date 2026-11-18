# Mixed-feed prompt engineering and research protocol

## Scope and evidence rule

The active generation template is **`mixed-v8`**, stored in `backend/prompts/mixed-v8.txt`; v1–v7 remain archived. The existing published mixed pack was generated with `mixed-v5`. Product intent is accepted by the user; this is not a claim of supervisor-approved methodology or expert content validation. Earlier single-sense smoke runs are separate exploratory evidence.

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

- **Reference integrity:** supplied normalized sense IDs, exact term/meaning, correct option equal to the reference, slang present in the designated post or reply, assignment match and use of all selected references, with at least two distinct terms. References preserve source evidence, not semantic endorsement.
- **Existing lexical question rules:** valid question-comment shape and literal option leakage checks in the question comment and ordinary replies. Version-specific rules govern term mentions. These do not reliably detect semantic spoilers.
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

### Aggregate observed via `mixed-v1` through `mixed-v5`

| Item | Observed |
| --- | --- |
| Real terminal attempts | 6 |
| Provider calls | 6, one each, no retries |
| Critical nine, all passing | **6/6 attempts** |
| Publishable after additional gates | **1/6** (the second `mixed-v5` attempt) |
| Item-level adherence | v1 9/10, v2 6/10, v3 9/10, v4 1/10, v5 attempt 1 9/10, v5 attempt 2 10/10 |
| Failure categories | v1 `post-slang-missing`; v2 four `question-comment-shape`; v3 `reference-mismatch`/`key-meaning-mismatch`/`unused-reference`; v4 nine `question-comment-shape`; v5 attempt 1 `term-presence` |

The important interpretation is that **the nine critical checks alone would have accepted all six packs**; the reference-integrity, existing-question and completion gates are what stopped five of them. Do not report a "100% valid generation rate" from the critical-nine subset.

A single valid pack from six attempts is a real observation, not a rate: with a three-attempt budget the first multi-attempt run published on its second try and stopped early. The bounded revision loop stopped at `mixed-v5`; the remaining hypothesis (deterministic term placement, disclosed as a repair) is recorded but **not implemented**.

## Proposed revision — conversational comments in mixed-v7

Editorial review of the published mixed-v5 pack found that `questionComment` often reads like a reading-comprehension test rather than a social reply, for example “Which personal quality is being praised in the post?” This is an editorial observation, not a participant study. The `mixed-v6` prompt asks for natural comments but also prohibits quoting the term, and still requires exactly one occurrence in social surfaces.

`mixed-v7` asks for varied, context-specific, conversational confusion and allows quoting the slang or a playful literal misunderstanding, e.g. “What do you mean by cooked? Are you cooking food or something?” The example is instructional, not generated evidence or a mandatory template. It permits repeated occurrences across post, ordinary replies and question comment, while requiring the declared target post or uniquely identified reply to contain the term. It retains the other answer-integrity and non-disclosure rules; deterministic checks cannot ensure naturalness or detect every semantic spoiler. Earlier prompt versions and historical packs remain unchanged.

**Deployment compatibility:** Android builds predating v7 retain the exactly-once parser and can reject a v7 pack with repeated occurrences. Release the compatible Android client before publishing such a pack; the unchanged currently published v5 pack remains compatible. This change alone does not rewrite existing questions or visually restyle comments.

**Hypothesis, not a measured outcome:** this revision may make the interaction feel more like a genuine conversation. It changes both the prompt and the occurrence contract, so comparisons against v5/v6 must disclose this confound. Evaluate actual outputs for target clarity, naturalness, answer leakage and distractor quality, recording all attempts and using an approved paid budget before claiming any improvement.

## First real v7 attempt (n=1, raw output preserved)

One local attempt on 2026-11-18 used `mixed-v7` with `openai/gpt-6-luna` through OpenRouter: one provider call, 8,993 tokens, USD 0.00249, `finish_reason` `stop`. Audit files are in `backend/experiments/mixed-20261118t132315785z-ccc0514b-e27e-4dbf-9a51-386fd0c7eeb5/`. The attempt **failed validation and published nothing**; the bank and the released pack were untouched because the run used the non-publishing runner.

Failure codes, all model-side: `0:key-meaning-mismatch` and `3:key-meaning-mismatch` (the option at `correctIndex` paraphrased the reference instead of copying it — 2 of 10 items), `3:reference-mismatch`, `3:term-target-mismatch` (the reply said "larping" while the contract term is "larp", so the exact term was absent from the target surface), and `unused-reference`. Structural checks, social guardrails and provider completion passed.

Editorial signals from `npm run questions:analyze` on that raw output, compared with the published v5 pack:

| Signal | published v5 | v7 attempt |
| --- | --- | --- |
| Comments addressing the feed as an object ("the post", "the commenter", "readers") | 10/10 | 0/10 |
| Comments opening as an exam question | 8/10 | 0/10 |
| Comments quoting the term | 0/10 | 5/10 |
| Comments mentioning the target handle | 0/10 | 5/10 |
| Correct option verbatim | 10/10 | 8/10 |

The comment register clearly changed in the intended direction. It is still **one attempt**, not an evaluation: no comparison condition, no second run, no human rating, and difficulty labels remain nominal.

**A limit that matters:** the analyzer reported 0 shared content words between the v7 comments and the reference meanings, yet manual reading found three comments that gesture at the answer by paraphrase (“are you saying he is actually acting out a character?” for *larp*, “or are you talking about how Kai feels?” for *cooked*). Word-overlap heuristics therefore give false comfort on this specific risk; only human review of each pack catches it. Two comments also ended with an aside rather than a question mark, which was left un-gated on purpose: those read more natural, and rejecting a whole pack over closing punctuation would cost another paid attempt for no reader benefit.

## Revision — mixed-v8 answers the observed defects

`mixed-v8` exists because editing `mixed-v7` in place would break the provenance of the attempt above: the archived template must stay byte-identical to what produced a recorded run. v8 keeps v7's conversational comment and repeat-permitting contract and adds three requirements aimed at the codes actually observed — write the term uninflected ("larp", not "larping"), copy the reference meaning character-for-character, and never let the comment describe or narrow the correct sense even as a wrong guess. It also ends with an explicit pre-return self-check.

Both v7 and v8 share the repeat-term validator branch (`REPEAT_TERM_PROMPT_VERSIONS` in `backend/src/game.mjs`, mirrored by `repeatTermPromptVersions` in the Android parser). Earlier versions keep exactly-once. A v8 pack remains rejectable by any Android build that predates this parser, so the compatibility gate below is unchanged.

Reproduce with `npm run bank:try` (one paid call, writes only to `backend/experiments/`, never to `question-bank/`) and inspect with `npm run questions:analyze <attempt-dir>`. The analyzer emits heuristics, not verdicts.

## First real v8 attempt (n=1, valid, unpublished)

One local attempt on 2026-11-18 used `mixed-v8` with `openai/gpt-6-luna`: one provider call, 9,009 tokens, USD 0.00241, `finish_reason` `stop`, **validation passed with zero error codes**. The validated pack is `backend/experiments/mixed-20261118t133133119z-49697039-2c5d-4d6a-86fb-2ebfcc0d9fa3/pack.json`. It was deliberately **not published**: the runner never writes the bank, and the release gate below still applies.

What the two attempts together show, with the caveat that the sampled terms differ so they are not a controlled comparison:

| Signal | published v5 | v7 attempt (invalid) | v8 attempt (valid) |
| --- | --- | --- | --- |
| Feed-as-object comments | 10/10 | 0/10 | 0/10 |
| Exam-opener comments | 8/10 | 0/10 | 0/10 |
| Correct option verbatim | 10/10 | 8/10 | 10/10 |
| Term uninflected on target | 10/10 | 9/10 | 10/10 |
| Reply-targeted items | 5/10 | 5/10 | 0/10 |

The three added requirements appear to have fixed the two defect classes observed in v7, at least in this attempt. A new observation replaces them: **every v8 item puts the term in the post body**, so the reply-target feature (the highlighted reply in the feed) is never exercised. That is either sampling luck or a side effect of emphasising the untouched term on the target surface, and one attempt cannot tell the two apart.

Naturalness is also not settled. The v8 comments are conversational, but two of them reach toward the answer indirectly (“I keep seeing it under outfit photos” for *fit check*; a “meal for a girls' night” for *girl dinner*), which the deterministic checks cannot see. Four of ten also end with an aside rather than a question mark; as noted above that was left un-gated on purpose.

**Status: two recorded attempts, one valid pack, no published change, no evaluation.** Claims of improvement remain hypotheses until there are repeated runs with a fixed reference set and, for naturalness or leakage, human ratings.

## Revision and interpretation protocol

1. Freeze/archive the baseline template, rendered input, dataset identity and provider parameters before interpreting results.
2. Run only the authorized bounded budget; persist invalid and failed attempts as well as successes.
3. Inspect named failures and real raw output. Record a specific hypothesis for any prompt change.
4. Give a changed template a new version only when it exists. Use matched/frozen references and comparable model/settings for controlled comparisons, or disclose selection/provider confounds.
5. Separate prompt development examples from held-out evaluation. Do not turn fixture validation into an LLM experiment or retrospectively invent v1/v2 trials.
6. Report syntax/contract findings within their limits. Expert semantic review, ambiguity, distractor plausibility, bias, safety and empirical difficulty require separate evaluation if claimed.

SUS assesses usability under its own study protocol; it does not prove learning gains, motivation improvements or linguistic validity. See [research guardrails](RESEARCH_GUARDRAILS.md) and [accepted product intent](MIXED_FEED_HANDBOOK.md). Final verification belongs in the actual integration report; this page does not populate `TEST_RESULTS.md`.
