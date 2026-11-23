# Social simulator feed

The app opens directly into fictional post cards. The former “CONTEXT DETECTIVE · V3 / Read the room. Decode the slang.” welcome panel is archived under **Quests → Archived: Context Detective · V3**. The original three-slang mode remains playable there and from the glossary; its results stay separate.

## Current source contract: mixed published feed

The user-approved design is a merged mixed-slang Home, not one selected slang with chips. Home fetches public `question-bank/` JSON, prefers schema 2 mixed `packs`, and uses legacy `senses` only when no mixed packs are advertised. An advertised mixed-pack failure is an error, not a silent legacy/mock fallback. The source loader merges up to twelve packs; ten questions is a per-pack requirement, not the total feed size. Slang chips are removed; context tabs and Saved remain.

A fresh load or explicit refresh shuffles once. Saved order remains stable during recomposition, navigation and supported activity recreation for the same loaded content; failed refreshes can retain prior published posts and their order with an explicit error. Answers/bookmarks are scoped by pack ID, content hash and question ID (legacy uses sense ID), so changed revisions do not reuse older answers. Correct/incorrect XP stays 80/15 once per question identity, not again for filters, navigation or refresh. Published, authored and archived practice remain isolated local state, not durable participant logging.

Mixed generation samples automatically eligible normalized senses from the own Firecrawl JSON/CSV, preferring ten unique terms, requiring at least two, and assigning ten questions with nominal 5/3/2 labels. Each question links to an embedded sense reference and one of nine context categories. Categories/scenario IDs may repeat; do not force an unnatural context quota. Literal provenance and structural checks do not certify semantics. There is no manual review gate or active slangfeed snapshot. The live refresh produced 22 normalized entries. Weekly collection is Monday 03:00 UTC / 10:00 WIB.

`backend/scripts/publish-mixed-bank.mjs` defaults to one attempt, maximum three, with no retries/repair/mock fallback. `mixed-v3` is the active baseline; three real attempts are archived, all rejected. Input, rendered prompt, request, raw output/response, actual model/usage/finish reasons, validation and result persist per attempt when available; success adds a pack. The summary CLI and workflow artifact/persistence are wired even on failure. `0.5.0-mixed-feed` / version code `6` is built in GitHub Actions; no live generation has passed all gates.

The social renderer retains bundled avatars, profiles, individually collapsible replies, an explicit inline answer composer, separate Learning feedback, context filtering, bookmarks, quests and results. Context replies start collapsed unless the target slang appears in a reply. Generated-pack inbox entries say Learning feedback ready; authored quotes/scripted continuations stay in the opt-in sample. The phone never calls the LLM and needs no provider key; public static grading is local practice. No final test/build/device outcome is asserted by this contract.

See [accepted intent and current acceptance checklist](MIXED_FEED_HANDBOOK.md), [bank operations](GITHUB_GET_QUESTIONS.md), [dataset provenance](DATASET_INTEGRATION.md) and [prompt research protocol](PROMPT_ENGINEERING.md).

## V0.6.0 pilot — Mira's late submission

Open **Feed → Source → Open offline sample → Education** and find Mira's post about the closed submission portal. Expand its replies to see Naya ask for evidence, Mira reply, and Naya answer Mira at the next indentation level. After submitting an interpretation, Mira's scripted update appears below the learning feedback and in the conversation inbox. Both answer outcomes unlock the same update. Correctness and XP still come from the separate grading panel. The pilot uses the authored sample and requires no changes to the published question bank.

## Historical checkpoint: Published Feed · 0.4.0

The earlier Home selected a single slang pack through chips. Six legacy packs covered cooked, no cap, lowkey, glow up, rizz and IYKYK. The latter three followed explicit provisional mappings; rizz/IYKYK retain disclosed agent-assisted corrections and `agent-edited` source labels. Those packs remain compatible, but chip selection and manual promotion are superseded for the current feed. Their publication/verification history is not a mixed-v1 experiment.

The remaining checkpoint, proposal-alignment and validation notes below are retained historical descriptions of authored/single-sense behavior. They are not current mixed-feed requirements or final mixed-feed test results; use the handbook above for current acceptance.

## Historical checkpoint: UI Hierarchy · 0.3.2

`0.3.2-ui-hierarchy` (version code 4) completes the hierarchy recommendation. It retains author portraits at 44dp, reply/quote portraits at 30–32dp, and profile portraits at 80dp. Primary author names are 16sp/bold; handles and story metadata are 12sp with explicit line heights. Other character names remain 14sp/bold. Secondary text uses a darker muted colour for readability on the warm surfaces; nested reply connectors remain in place.

In the feed, the player's visible reply stays in the social comment container. Grading, practice XP, correct meaning and explanation appear in their own bordered **Learning feedback** surface outside that container, without a character avatar or social action buttons. A book icon and semantic heading identify the educational panel, while text plus colour distinguish correct and incorrect answers. The scripted story continuation follows as a separate identity/message block. This prevents a character's neutral reaction from being mistaken for grading.

Archived practice and Connected Training use the same feedback surface, retaining their distinct source labels, grading disclosures and optional saved clues. The question pack, meanings, answer keys, XP rules, API and LLM prompt are unchanged in this UI-only checkpoint.

Phone acceptance additions: check names/handles at normal and large font sizes; submit correct and wrong answers; verify the visible player reply, separate Learning feedback panel and separate fictional follow-up; check archived and static/live training disclosures. These changes are compiled and model-regression tested, not a completed device usability or accessibility evaluation.

## Historical checkpoint: Profile Pictures · 0.3.1

`0.3.1-profile-pictures` (version code 3) adds 22 local Open Peeps portraits, including the twelve recurring characters, player and guest/fallback pictures. The [asset catalogue and generator documentation](../assets/profile-pictures/README.md) includes CC0 artwork provenance, MIT renderer notices, SVG originals, a contact sheet and reproducible rendering commands.

The UI now uses consistent circular pictures across posts (44dp), nested replies (30dp), quotes and question/follow-up identities (32dp), character dialogs (80dp), inbox entries, player replies, the player profile, archived practice and Connected Training. Pictures support recognition; they do not encode answers or difficulty. The Privacy & data panel also credits the artwork.

The LLM prompt offers allowed `avatarId` values and canonical cast bindings. The backend normalizes post/comment IDs consistently within each pack; malformed cosmetic IDs fall back without regenerating the educational content. Optional fields are preserved through live API sessions and static exports, parsed by both Android clients, and resolved to bundled resources. No live provider call is claimed for this checkpoint. Durable participant logging remains outstanding.

Phone checks: inspect each identity surface, open a recurring character in multiple conversations, verify the same portrait, test an old static pack without IDs, and test generated IDs via a mock/live-backend pack. Pictures must render offline and without affecting XP. Check both large and small font settings. Local tests/builds do not establish device layout or usability.

## Historical checkpoint: Social Stories · 0.3.0

This revision extends the feed checkpoint on `feat/social-simulator-feed`. It adds recurring fictional profiles, nested replies, five quoted earlier posts, clickable context hashtags, visible player replies, and an in-app conversation inbox. The app version is `0.3.0-social-stories` (version code 2).

Tap a name/avatar to read that character's biography and open their authored posts. Reply connectors and “Replying to” labels identify the conversation structure. Quoted text is earlier fictional story evidence within the same question, not a separately graded item or a live repost. The “You” reply is visually separate from labeled practice feedback.

Submitting any interpretation unlocks exactly one neutral scripted follow-up for that post. The same follow-up appears after the feedback and in the bell-button inbox. Opening an inbox entry marks it read and opens the exact conversation with replies expanded. Correct and incorrect answers unlock the same story update; the character's response is not evidence that an answer is correct. Likes, bookmarks, profile visits, quoted text, and inbox visits never award XP or submit answers. There are no OS notifications, real incoming messages, push services, or external social actions.

Filter changes, profile navigation, and inbox navigation retain the same ten-question pack and submitted answers. The context quest uses the distinct contexts actually present in the active practice pack. This sample happens to cover all nine, but that is not a generator requirement for every slang sense; category suitability remains dependent on the slang as specified in §8.2.2.

Known boundary: saveable UI state is still not a durable participant response log. Study identifiers, durable response storage/export, consent, human content validation, and usability evaluation remain separate thesis implementation/evaluation work. This story layer does not modify the backend generation pipeline or claim those capabilities.

## Historical proposal alignment — authored sample

Based on the supplied thesis proposal, §8.2.2 (printed p. 4), the nine categories are personal experience, education, work, prediction, sports, entertainment, opinion, conversations between friends, and reactions to events. **For you** is the mixed feed, not a tenth context. **News** is the UI label for reactions to fictional events; it does not fetch real news. The other eight categories have their own filters. Saved is a personal bookmark view, not a research category. “For you” uses a curated order, not a personalized recommendation algorithm.

The local demonstration pack uses ten distinct posts for one slang sense: `cooked` meaning “in serious trouble.” It covers all nine categories, with education appearing twice. Items have the proposal's nominal 5 easy / 3 medium / 2 hard split (§8.2.3). Correct-option positions rotate. These are hand-authored provisional examples; labels do not establish expert-rated difficulty, semantic validity, or LLM generation quality. The backend generation contract and Connected Training are unchanged.

The loop follows §8.2.7 (printed p. 8): read a post and optional replies, interpret the slang using four choices, post a reply, receive correctness and contextual explanation, and review results. The question is part of a highlighted fictional user's reply. Wrong answers receive an explanation and 15 practice XP; correct answers receive 80. Answers are final per post within the local practice state. Reopening a post or changing filters cannot award XP twice.

## Historical interaction and progression — authored sample

- Browse a vertically lazy-loaded feed and horizontally scrollable context filters.
- Replies are visible by default; collapse/expand remains per post and does not open answer choices. Connected Training and archived practice also start/reset with replies expanded, and training no longer truncates an expanded thread to three comments. Using clues has no score penalty.
- Like and bookmark posts; social actions do not affect correctness or XP. The Saved filter and profile link retrieve bookmarks.
- Reply to `@confuseduser` to open choices inside the thread. Immediate feedback stays in the conversation.
- Quest goals track ten replies, the contexts available in the pack, and five correct meanings. Progress shows each submitted interpretation's result and explanation.
- The feed has a finite endpoint and a results link; no timer, energy gate, follower penalty, or streak punishment.

Feed answers, selected filter, likes, bookmarks, focused conversation, profile selection and inbox read markers use Compose saveable state, including ordinary navigation and supported activity recreation. This is local UI state, not durable account storage across a fresh launch, a server response, or a participant research log. The archived mode still has separate in-memory summary metrics.

The proposal §9.8 (printed pp. 23–24) treats the game as an interactive presentation and response-recording medium. This implementation does not claim learning gains, authentic social interaction, validated content, research-grade persistence, or superior usability without evaluation. Accounts, events and conversations are fictional. No real social network, news feed, or paid LLM call is involved in local practice.

## Historical validation commands and coverage

From `android/`, with the environment toolchain activated:

```sh
source /workspace/toolchains/activate.sh
gradle --no-daemon :app:testDebugUnitTest :app:assembleDebug
```

`SocialFeedGameTest` checks category filtering, ten-item single-sense coverage, the 5:3:2 split, option validity, bounded progress, XP idempotence, and invalid-answer rejection. `SocialNarrativeTest` additionally checks profile references, reply parents, quoted evidence, hashtag routes, and the one-update-per-submission rule. Existing Context Detective tests remain in place. These JVM checks and APK assembly do not verify gestures, layout, accessibility, or gameplay on a device.

Device acceptance checklist for the original feed checkpoint: cold launch shows post cards without the old hero; select every category; like and save a post; open it from Saved; expand clues; submit correct and wrong answers; change tabs and navigate away/back; confirm feedback and XP remain stable; recreate the activity; complete the finite feed and inspect results; open the archived mode; open Connected Training.

## Historical APK download instructions

The Android debug workflow also runs on pushes to `feat/social-simulator-feed` that change Android files or the workflow. On GitHub, sign in, open Actions → Android debug build (no Codex), and choose a successful run whose branch is `main`. In that run's Artifacts section, download `slangverse-debug-apk`, extract the ZIP, and install `app-debug.apk` on an Android 8.0+ phone. If no successful run is available, use Run workflow and select `main`. GitHub Actions availability and account quotas apply. A successful local build does not establish that the GitHub run has finished successfully.

### Historical Social Stories phone acceptance checks

1. Confirm the installed version is `0.3.0-social-stories`; cold launch still shows posts without the archived hero.
2. Tap Mira's identity, read her bio, and open her other authored conversation. Back/dismiss returns to the feed without submitting an answer.
3. Expand replies and inspect the nested reply target; open a reply author's profile.
4. Inspect an education, sports, entertainment, or News quote. Its author opens a profile; the quote adds no scored question.
5. Tap `#CampusLife` or another hashtag. Only the matching category is shown; the same pack and progress remain.
6. Submit both a correct and an incorrect interpretation. Confirm the visible “You” reply, separate practice feedback, unchanged 80/15 XP rules, and neutral scripted continuation.
7. Open the bell: one update per answered post. Open an unread entry and confirm the exact conversation, expanded clues, read marker, preserved answer and no new XP.
8. Navigate through Saved, profiles, results, the inbox, and activity recreation. Check bookmarks, answers, selected context, and read markers are retained. A fresh launch is not guaranteed durable storage.
9. Complete the finite pack, review results, and confirm archived Context Detective and Connected Training still have separate sources/results.
