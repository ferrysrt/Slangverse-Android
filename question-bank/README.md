# Published LLM question bank

This directory is the default read-only content source for Android Home:

https://raw.githubusercontent.com/ferrysrt/Slangverse-Android/main/question-bank/catalog.json

The initial published set contains three ten-question GPT-6 Luna packs: cooked, no cap and lowkey. Their metadata records generator model, prompt version, generation time, dataset version and provisional reference status. The catalogue's hashes cover the exact pack bytes. LLM provenance here comes from the publisher pipeline; it is not expert content validation.

## Updating content

`Publish LLM question bank` runs on main when `backend/data/curated_senses.json` or its workflow changes, or through manual Run workflow. It uses `OPENROUTER_API_KEY` from repository secrets and makes at most three paid generation requests per run (one ten-item pack per request, no retries). Current packs are skipped unless forced. There is no periodic schedule or generation on phone launches. The workflow commits the catalogue and valid packs together; output labeled fallback/mock is rejected.

Add source-backed playable senses through the curated catalogue to expand the bank. The 200 dataset candidates are not all verified reference meanings; only three senses are currently playable. Existing valid packs survive failed generation. Invalid/outdated reference packs are excluded from the catalogue until regenerated. The same ten-question 5/3/2 and single-sense contract applies.

Run locally from `backend/` with a securely provided LLM_API_KEY:

```
node scripts/publish-question-bank.mjs
```

Do not put keys in this directory, command arguments, the APK, or question JSON. See [the full guide](../docs/GITHUB_GET_QUESTIONS.md).

## Playing and validation

The APK fetches only public JSON, verifies source/shape/slang/hash and grades locally. New content does not require an APK rebuild. Users can choose another bank folder under Question source. Question count, options, reply hints, avatar IDs and explanations all come from the loaded pack. The original sample is available only by explicit selection.

These files necessarily expose answers and explanations. They support local practice, not authenticated results, competitive scoring or durable participant research storage. Structural validation does not establish linguistic accuracy, difficulty or educational effectiveness. Review the generated content before participant research use.
