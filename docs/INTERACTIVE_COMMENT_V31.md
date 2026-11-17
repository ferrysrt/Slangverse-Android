# Slangverse V3.1 — Interactive Comment Quiz

**Historical UI checkpoint:** the in-thread answer interaction is retained, but this page's sample/generation and inline-feedback descriptions belong to V3.1. Current Home uses mixed published packs and separate Learning feedback; see the [mixed-feed handbook](MIXED_FEED_HANDBOOK.md) and [social-feed contract](SOCIAL_SIMULATOR_FEED.md).

## Intent

V3.1 turns the multiple-choice question into **one special fictional reply in a social-media thread**, instead of a separate question block below the post. Players feel they are helping a confused user interpret slang; the educational prompt is contextual and the interaction remains readable as a social conversation.

**Primary loop**: read post → read ordinary replies (indirect hints) → tap the single **@confuseduser · ✳ DECODE** reply → see four answer options **inside that reply** → post interpretation → receive **inline feedback** in the same reply → move to next conversation.

The special reply is *distinguished by an unobtrusive Decode label*, not by a giant out-of-character 'QUIZ' screen.

## Content examples (fictional, provisional)

| Slang | Special reply |
|---|---|
| cooked | “Wait, what do you mean by cooked? Are you literally making dinner? 😭” |
| no cap | “No cap? Did you lose your hat or something? I'm confused 😅” |
| lowkey | “Lowkey? As in an actual key? Or am I missing the point?” |

Literal misunderstandings are intentional **only when natural**; otherwise a sincere clarification question should be used.

## Implementation

- `android/app/src/main/java/id/slangverse/app/ContextDetectiveGame.kt`: one authored special comment per each of the three local sample cases. Pinning context evidence is optional; it has no effect on correctness or XP. No change to sample quiz answer key or grading.
- `android/app/src/main/java/id/slangverse/app/ContextDetective.kt`: tappable `@confuseduser · DECODE` comment, answer controls and feedback **within the post/thread card**. No separate question section outside the thread. Same V3 Case Report and three distinct slang terms.
- `android/app/src/main/java/id/slangverse/app/RemoteTrainingPanel.kt`: applies the same inline-comment experience to **live HTTP** and **GitHub GET** 10-context packs. Server-side scoring for live remains server-side; GitHub static grading remains labeled local demo.
- `android/app/src/main/java/id/slangverse/app/SlangverseApi.kt` and `GithubStaticBank.kt`: read optional `questionComment` in each question. **Old JSON packs remain compatible** and display the old `question` as a special comment if this extension is absent.
- `backend/src/prompts.mjs`: prompt version `slangverse-v3.1-inline-comment`, requests natural and context-specific special question text with no answer leakage.
- `backend/src/game.mjs` and `mockCatalog.mjs`: optional format validator plus sample content for `cooked`, `no cap` and `lowkey`.

## Question contract (backwards compatible)

A question may now include **one additional top-level string**:

```json
{
  "slang": "cooked",
  "question": "What does 'cooked' mean in this post?",
  "questionComment": "Wait, what do you mean by cooked? Are you literally making dinner?",
  "options": ["Feeling hungry", "In serious trouble", "Feeling confident", "Planning a meal"]
}
```

All existing fields and the root package remain unchanged. **The schema still uses 10 distinct contexts of one target sense and nominal 5:3:2 difficulty labels.**

Backend validation checks the new string only if present: a question mark, target slang reference, length 10–220, and no **whole** correct meaning/answer option leakage. This is a limited structural/lexical check, **not human linguistic validation**. Legacy packs without the new field still validate; Android falls back to the old question text. The answer key is never delivered in a live session response before grading.

## Research, scoring and UX boundaries

- A special question comment is an interface and content-format feature, not proof of learning outcomes.
- Replies and usernames remain fictional; no live social network or accounts are claimed.
- Selecting/saving evidence is voluntary, not graded and not a bonus-XP mechanic.
- A correct response yields **+80 sample XP**, incorrect **+15 sample XP**, matching existing backend/local grading.
- Demo scores and local static scores are not research-grade participant data. No participant study, semantic expert review or SUS was run.
- GPT-6 Luna prompt instructions and shape validation are implemented; **this V3.1 change has not been empirically proven with a new paid live LLM generation**.
- MagicPath V3.1 update was attempted but blocked by the account's external-agent API quota. Prior V3 MagicPath revision `472696158041575424` remains the last successfully built remote prototype. This Kotlin implementation is the source of truth until MagicPath can be resynced.

## Verification checklist

- [ ] Backend test suite verifies new comment format and legacy compatibility.
- [ ] Kotlin unit tests verify optional evidence and per-term authored text.
- [ ] Android build succeeds on the V3.1 PR.
- [ ] Full Android emulator or physical-device playthrough confirms UI and scrolling.
- [ ] Live LLM sample generation checks whether comments are natural across ten contexts without leaks.
- [ ] Update MagicPath V3.1 when connector quota is available.
