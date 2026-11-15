# Social simulator feed

The app opens directly into fictional post cards. The former “CONTEXT DETECTIVE · V3 / Read the room. Decode the slang.” welcome panel is archived under **Quests → Archived: Context Detective · V3**. The original three-slang mode remains playable there and from the glossary; its results stay separate.

## Current checkpoint: Social Stories · 0.3.0

This revision extends the feed checkpoint on `feat/social-simulator-feed`. It adds recurring fictional profiles, nested replies, five quoted earlier posts, clickable context hashtags, visible player replies, and an in-app conversation inbox. The app version is `0.3.0-social-stories` (version code 2).

Tap a name/avatar to read that character's biography and open their authored posts. Reply connectors and “Replying to” labels identify the conversation structure. Quoted text is earlier fictional story evidence within the same question, not a separately graded item or a live repost. The “You” reply is visually separate from labeled practice feedback.

Submitting any interpretation unlocks exactly one neutral scripted follow-up for that post. The same follow-up appears after the feedback and in the bell-button inbox. Opening an inbox entry marks it read and opens the exact conversation with replies expanded. Correct and incorrect answers unlock the same story update; the character's response is not evidence that an answer is correct. Likes, bookmarks, profile visits, quoted text, and inbox visits never award XP or submit answers. There are no OS notifications, real incoming messages, push services, or external social actions.

Filter changes, profile navigation, and inbox navigation retain the same ten-question pack and submitted answers. The context quest uses the distinct contexts actually present in the active practice pack. This sample happens to cover all nine, but that is not a generator requirement for every slang sense; category suitability remains dependent on the slang as specified in §8.2.2.

Known boundary: saveable UI state is still not a durable participant response log. Study identifiers, durable response storage/export, consent, human content validation, and usability evaluation remain separate thesis implementation/evaluation work. This story layer does not modify the backend generation pipeline or claim those capabilities.

## Proposal alignment

Based on the supplied thesis proposal, §8.2.2 (printed p. 4), the nine categories are personal experience, education, work, prediction, sports, entertainment, opinion, conversations between friends, and reactions to events. **For you** is the mixed feed, not a tenth context. **News** is the UI label for reactions to fictional events; it does not fetch real news. The other eight categories have their own filters. Saved is a personal bookmark view, not a research category. “For you” uses a curated order, not a personalized recommendation algorithm.

The local demonstration pack uses ten distinct posts for one slang sense: `cooked` meaning “in serious trouble.” It covers all nine categories, with education appearing twice. Items have the proposal's nominal 5 easy / 3 medium / 2 hard split (§8.2.3). Correct-option positions rotate. These are hand-authored provisional examples; labels do not establish expert-rated difficulty, semantic validity, or LLM generation quality. The backend generation contract and Connected Training are unchanged.

The loop follows §8.2.7 (printed p. 8): read a post and optional replies, interpret the slang using four choices, post a reply, receive correctness and contextual explanation, and review results. The question is part of a highlighted fictional user's reply. Wrong answers receive an explanation and 15 practice XP; correct answers receive 80. Answers are final per post within the local practice state. Reopening a post or changing filters cannot award XP twice.

## Interaction and progression

- Browse a vertically lazy-loaded feed and horizontally scrollable context filters.
- Expand replies for clues; using clues has no score penalty.
- Like and bookmark posts; social actions do not affect correctness or XP. The Saved filter and profile link retrieve bookmarks.
- Reply to `@confuseduser` to open choices inside the thread. Immediate feedback stays in the conversation.
- Quest goals track ten replies, the contexts available in the pack, and five correct meanings. Progress shows each submitted interpretation's result and explanation.
- The feed has a finite endpoint and a results link; no timer, energy gate, follower penalty, or streak punishment.

Feed answers, selected filter, likes, bookmarks, focused conversation, profile selection and inbox read markers use Compose saveable state, including ordinary navigation and supported activity recreation. This is local UI state, not durable account storage across a fresh launch, a server response, or a participant research log. The archived mode still has separate in-memory summary metrics.

The proposal §9.8 (printed pp. 23–24) treats the game as an interactive presentation and response-recording medium. This implementation does not claim learning gains, authentic social interaction, validated content, research-grade persistence, or superior usability without evaluation. Accounts, events and conversations are fictional. No real social network, news feed, or paid LLM call is involved in local practice.

## Validation

From `android/`, with the environment toolchain activated:

```sh
source /workspace/toolchains/activate.sh
gradle --no-daemon :app:testDebugUnitTest :app:assembleDebug
```

`SocialFeedGameTest` checks category filtering, ten-item single-sense coverage, the 5:3:2 split, option validity, bounded progress, XP idempotence, and invalid-answer rejection. `SocialNarrativeTest` additionally checks profile references, reply parents, quoted evidence, hashtag routes, and the one-update-per-submission rule. Existing Context Detective tests remain in place. These JVM checks and APK assembly do not verify gestures, layout, accessibility, or gameplay on a device.

Device acceptance checklist for the original feed checkpoint: cold launch shows post cards without the old hero; select every category; like and save a post; open it from Saved; expand clues; submit correct and wrong answers; change tabs and navigate away/back; confirm feedback and XP remain stable; recreate the activity; complete the finite feed and inspect results; open the archived mode; open Connected Training.

## Download an APK for phone testing

The Android debug workflow also runs on pushes to `feat/social-simulator-feed` that change Android files or the workflow. On GitHub, sign in, open Actions → Android debug build (no Codex), and choose a successful run whose branch is `feat/social-simulator-feed`. In that run's Artifacts section, download `slangverse-debug-apk`, extract the ZIP, and install `app-debug.apk` on an Android 8.0+ phone. If no successful run is available, use Run workflow and select that branch. GitHub Actions availability and account quotas apply. A successful local build does not establish that the GitHub run has finished successfully.

### Social Stories phone acceptance checks

1. Confirm the installed version is `0.3.0-social-stories`; cold launch still shows posts without the archived hero.
2. Tap Mira's identity, read her bio, and open her other authored conversation. Back/dismiss returns to the feed without submitting an answer.
3. Expand replies and inspect the nested reply target; open a reply author's profile.
4. Inspect an education, sports, entertainment, or News quote. Its author opens a profile; the quote adds no scored question.
5. Tap `#CampusLife` or another hashtag. Only the matching category is shown; the same pack and progress remain.
6. Submit both a correct and an incorrect interpretation. Confirm the visible “You” reply, separate practice feedback, unchanged 80/15 XP rules, and neutral scripted continuation.
7. Open the bell: one update per answered post. Open an unread entry and confirm the exact conversation, expanded clues, read marker, preserved answer and no new XP.
8. Navigate through Saved, profiles, results, the inbox, and activity recreation. Check bookmarks, answers, selected context, and read markers are retained. A fresh launch is not guaranteed durable storage.
9. Complete the finite pack, review results, and confirm archived Context Detective and Connected Training still have separate sources/results.
