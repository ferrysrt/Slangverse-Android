# GitHub GET mode — no laptop IP, no Node server

Slangverse Android has an optional GET-only source for pre-generated public JSON questions. This mode does not invoke OpenRouter from the phone; local answers/XP are demo-only.

## Private repo warning

The Slangverse-Android repository is PRIVATE. Android without a GitHub access token cannot GET its raw files. Never put a GitHub PAT, OpenRouter key, or API key in the APK. Publish only approved static JSON question files in a separate PUBLIC repository (or GitHub Pages when available on the GitHub plan). The Slangverse-Android source and the unrelated slangfeed repository should remain private.

## Export three free hand-written demo question packs

The Mock backend checks workflow now runs npm run bank:export and uploads a private GitHub Actions artifact named slangverse-static-demo-json.

The ZIP contains:

    catalog.json
    packs/cooked-trouble.json
    packs/no-cap-truth.json
    packs/lowkey-subtle.json

All 30 questions are hand-written mock data, not GPT-6 Luna output. They have 10 contexts per slang and nominal 5 easy, 3 medium, 2 hard labels. Automated tests check their structure, not their semantic validity.

### Publish and play

1. Create a separate PUBLIC repository, for example Slangverse-Question-Bank. This name is an example, not an existing repo.
2. Download the slangverse-static-demo-json ZIP from a successful backend CI workflow in Slangverse-Android. Extract it.
3. Upload catalog.json and the packs folder to public-question-bank/ in the new PUBLIC repository. Do not upload credentials, private data or research reports.
4. Verify both of these example URLs in your phone browser (they will not work until you create and populate that public repo):

    https://raw.githubusercontent.com/ferrysrt/Slangverse-Question-Bank/main/public-question-bank/catalog.json
    https://raw.githubusercontent.com/ferrysrt/Slangverse-Question-Bank/main/public-question-bank/packs/no-cap-truth.json

5. Install an APK built from the new GitHub GET PR, then open Feed -> PLAY API SESSION -> GitHub GET.
6. Paste the FOLDER URL (omit catalog.json):

    https://raw.githubusercontent.com/ferrysrt/Slangverse-Question-Bank/main/public-question-bank

7. Tap LOAD AVAILABLE SLANG, select a sense, then START 10-CONTEXT SESSION. The game uses GET only, with local demo grading. Its source reads GITHUB-STATIC-MOCK.

If JSON files are at the public repo root, omit /public-question-bank from the example URLs.

## Optional: generate one real GPT-6 Luna pack in GitHub Actions

After this feature reaches the default branch, open GitHub Actions and manually run Export GPT-6 Luna static question pack (manual). Choose one of three provisional slang senses. It uses the existing OPENROUTER_API_KEY GitHub Secret and the openai/gpt-6-luna model. This may incur an API fee; it NEVER runs automatically.

After successful generation, download the private slangverse-llm-static-bank-<senseId> artifact. Review the questions yourself before publication. Copy its packs/<senseId>.json to the public bank and add that sense to the public catalog.json. If combining packs, DO NOT overwrite a multi-sense catalog with the one-sense catalog from the new artifact.

A successful generation has generationSource=llm and is shown in the app as GITHUB-STATIC-LLM. This does NOT mean GPT-6 Luna was called live during gameplay. Fallback/mock output fails the export instead of being labeled as LLM.

## Data format

The root must contain a catalog.json object with schemaVersion 1, datasetVersion, candidateCount and senses [{senseId, term, reviewStatus}]. Each listed sense needs packs/<senseId>.json with schemaVersion 1, senseId, generationSource ("mock" or "llm"), referenceStatus, datasetVersion, and questions.

A pack has exactly 10 questions with unique id and contextId, 5 easy, 3 medium, 2 hard, 4 distinct options and a correctIndex in range 0..3. Each question includes a slang term, a fictional post object (displayName, handle, hashtag, content, comments with handle and text), question text and explanation. Use an exported artifact for complete sample JSON.

## Research and security constraints

- GitHub GET distributes previously generated files; it is not on-demand LLM generation. Don't measure it as GPT-6 Luna generation latency.
- Every public static pack necessarily exposes correctIndex and explanation. The app grades locally. XP and accuracy can be manipulated and are not authenticated research observations.
- GitHub static hosting cannot receive responses for a persistent leaderboard or store experimental player answers.
- The live backend path is retained for server-side grading and on-demand GPT-6 Luna research. See CLOUD_PLAY.md.
- Structural checks do NOT prove semantic accuracy or empirically calibrated difficulty.
- Record the Git commit SHA and bank version for reproducibility, since URLs from main branches can change.
