# Default published question feed — GitHub Raw

The social-simulator implementation is now on **main**. `0.4.0-published-feed` automatically loads LLM-generated question packs into Home from:

```
https://raw.githubusercontent.com/ferrysrt/Slangverse-Android/main/question-bank
```

The catalogue is `catalog.json`, with individual packs under `packs/<senseId>.json`. The current bank has six LLM-generated packs / 60 questions. The 17 November expansion added glow up, rizz and IYKYK from explicitly reviewed Firecrawl candidates. All six were checked by HTTPS GET without application credentials and matched the catalogue SHA-256 values. Rizz and IYKYK contain disclosed agent-assisted editorial corrections; the app marks these packs `agent-edited`. The earlier separate `Slangverse-Question-Bank` URL was only an example and returned 404.

## Play

Install the APK from the latest successful **main** Android build. Home loads published questions automatically. Slang chips switch between available senses. **Refresh bank** fetches the latest catalogue and selected pack. **Question source** can change the folder URL or explicitly open the labeled hand-authored offline sample. Errors, unavailable packs and empty catalogues do not cause a silent switch to mock questions.

The existing Connected Training GitHub GET mode is retained and starts with the same bank URL. It still permits explicitly labeled legacy mock/static packs. The default Home feed requires both catalogue and pack to declare LLM provenance. Profiles, avatars, replies, grading feedback, bookmarks, quests and results use the loaded pack rather than the fixed cooked demo.

## Generate and publish more

Use **Actions → Publish LLM question bank → Run workflow** on main. The normal limit is three generation requests, one ten-question pack per request, with no retries. Missing/outdated playable senses are generated first; current packs are skipped unless Force is selected. Requests use GPT-6 Luna via the existing `OPENROUTER_API_KEY` repository secret and may incur provider charges. No credentials belong in the APK or public bank.

The workflow also runs when `backend/data/curated_senses.json` changes on main. Editing its workflow alone no longer triggers paid generation. It has no recurring schedule and is not invoked by the phone. New packs and their catalogue are committed in one change. Generation failures preserve valid existing packs; fallback/mock output is never published as LLM. Stale/invalid reference packs are excluded until successfully regenerated. Adding a new playable sense is an explicit curation step; the 200 dataset candidates are not all validated meanings. Six senses are currently playable. Discovery alone never promotes candidates; the [explicit mapping procedure and persistent source table](DATASET_INTEGRATION.md) connect reviewed candidates to playable references.

The standalone single-pack exporter also merges its result into the existing multi-sense catalogue instead of overwriting other entries. Artifact-only exports remain available for separate review workflows.

## Contract and boundaries

A pack contains one slang sense and exactly ten questions, with 5 easy / 3 medium / 2 hard labels, unique question/context IDs, four choices and a correct answer index. Optional avatar IDs are resolved to bundled pictures. The catalogue includes sense IDs, terms, review status and SHA-256 checksums. Firecrawl-derived packs retain `sourceCandidateId`, `normalizedDefinition` and `referenceSources`. Optional `editorialReview` records the actual reviewer, review time and item-level reasons when LLM output is subsequently edited; `generationSource` identifies its LLM origin, not a claim of untouched output. A mismatched checksum or slang is rejected. Unknown categories remain visible in For you; legacy scenario IDs have navigation tags, not human-validated semantic classifications.

Questions are pre-generated, not generated live during a phone session. Refreshing GitHub is not an LLM-latency measurement. Content is structurally checked and provisional; generation does not establish semantic validity, calibrated difficulty or learning gains.

Static JSON exposes answer keys and explanations by design. Grading and saved UI state are local practice, not authenticated participant results or durable study logging. Questions are namespaced by sense, content hash and question ID; changing a pack does not reuse its older answers or award duplicate XP for the same revision.

A bank used by the APK must remain publicly readable without credentials. No repository visibility setting was changed by this implementation. If deployment moves to a private repository, publish the bank to a public location and change the configured root; never embed a GitHub token.
