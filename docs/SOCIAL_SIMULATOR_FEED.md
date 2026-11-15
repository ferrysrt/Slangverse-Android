# Social simulator feed

The app opens directly into fictional post cards. The former “CONTEXT DETECTIVE · V3 / Read the room. Decode the slang.” welcome panel is archived under **Quests → Archived: Context Detective · V3**. The original three-slang mode remains playable there and from the glossary; its results stay separate.

## Proposal alignment

Based on the supplied thesis proposal, §8.2.2 (printed p. 4), the nine categories are personal experience, education, work, prediction, sports, entertainment, opinion, conversations between friends, and reactions to events. **For you** is the mixed feed, not a tenth context. **News** is the UI label for reactions to fictional events; it does not fetch real news. The other eight categories have their own filters. Saved is a personal bookmark view, not a research category. “For you” uses a curated order, not a personalized recommendation algorithm.

The local demonstration pack uses ten distinct posts for one slang sense: `cooked` meaning “in serious trouble.” It covers all nine categories, with education appearing twice. Items have the proposal's nominal 5 easy / 3 medium / 2 hard split (§8.2.3). Correct-option positions rotate. These are hand-authored provisional examples; labels do not establish expert-rated difficulty, semantic validity, or LLM generation quality. The backend generation contract and Connected Training are unchanged.

The loop follows §8.2.7 (printed p. 8): read a post and optional replies, interpret the slang using four choices, post a reply, receive correctness and contextual explanation, and review results. The question is part of a highlighted fictional user's reply. Wrong answers receive an explanation and 15 practice XP; correct answers receive 80. Answers are final per post within the local practice state. Reopening a post or changing filters cannot award XP twice.

## Interaction and progression

- Browse a vertically lazy-loaded feed and horizontally scrollable context filters.
- Expand replies for clues; using clues has no score penalty.
- Like and bookmark posts; social actions do not affect correctness or XP. The Saved filter and profile link retrieve bookmarks.
- Reply to `@confuseduser` to open choices inside the thread. Immediate feedback stays in the conversation.
- Quest goals track ten replies, nine contexts, and five correct meanings. Progress shows each submitted interpretation's result and explanation.
- The feed has a finite endpoint and a results link; no timer, energy gate, follower penalty, or streak punishment.

Feed answers, selected filter, likes and bookmarks use Compose saveable state, including ordinary navigation and supported activity recreation. This is local UI state, not durable account storage across a fresh launch, a server response, or a participant research log. The archived mode still has separate in-memory summary metrics.

The proposal §9.8 (printed pp. 23–24) treats the game as an interactive presentation and response-recording medium. This implementation does not claim learning gains, authentic social interaction, validated content, research-grade persistence, or superior usability without evaluation. Accounts, events and conversations are fictional. No real social network, news feed, or paid LLM call is involved in local practice.

## Validation

From `android/`, with the environment toolchain activated:

```sh
source /workspace/toolchains/activate.sh
gradle --no-daemon :app:testDebugUnitTest :app:assembleDebug
```

`SocialFeedGameTest` checks category filtering, ten-item single-sense coverage, the 5:3:2 split, option validity, bounded progress, XP idempotence, and invalid-answer rejection. Existing Context Detective tests remain in place. These JVM checks and APK assembly do not verify gestures, layout, accessibility, or gameplay on a device.

Device acceptance checklist: cold launch shows post cards without the old hero; select every category; like and save a post; open it from Saved; expand clues; submit correct and wrong answers; change tabs and navigate away/back; confirm feedback and XP remain stable; recreate the activity; complete the finite feed and inspect results; open the archived mode; open Connected Training.
