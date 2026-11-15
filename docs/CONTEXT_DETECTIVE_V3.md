# Slangverse V3 — Context Detective

## Product identity

A social-context detective game, not a multiple-choice quiz disguised with XP.
The core loop is:

1. **Discover:** read a fictional social-media post with one highlighted slang term.
2. **Investigate:** inspect one or more replies that may provide contextual clues.
3. **Pin evidence:** choose a reply that influenced the interpretation. This subjective reasoning choice is **not graded**.
4. **Decode:** choose one of four meanings and commit.
5. **Reveal:** see correct/incorrect feedback, an explanation, and the pinned clue.
6. **Next case:** move to a **different slang sense** in casual V3 play.
7. **Case Report:** review all decisions; do not infer true language mastery from this demo.

No countdown, energy bar, punishable streak, virtual currency shop or competitive leaderboard is required to enjoy a three-case session.

## MagicPath synchronization

Existing MagicPath project: `470964531963789312`
Existing component: `470964549080743936`
V3 revision: `472696158041575424`
Prototype URL: https://designs.magicpath.ai/v1/gladly-water-6771

Updated in place; the previous Warm Editorial revision remains accessible by revision id.

The V3 MagicPath prototype is interactive React with **eight views**, including the additional Case Report. Its Progress and Challenges now derive local sample values from played decisions instead of fabricated counts; bottom tabs disappear while investigating. The Android implementation is a separate **native Jetpack Compose** runtime, not a WebView or embedded React component.

## Native Android implementation

- `ContextDetectiveGame.kt`: pure-Kotlin case model, 3 separate provisional target senses, rotating the order of the cases, correct/incorrect demo XP +80/+15. Never claims that pinned evidence has an objectively correct label.
- `ContextDetective.kt`: the playable V3 state machine (Investigate → Feedback → Report). Taps, expand/collapse comments, pinning, answer locking, explanations, and recap.
- `SlangverseApp.kt`: routes Feed, post decode, glossary practice and Quests to V3; hides the bottom navigation during cases.
- `RemoteTrainingPanel.kt`: preserves the independently labeled GitHub GET/local demo and Node backend scoring paths while allowing a player to pin a context clue before submitting an answer.

## Research safety and current limitations

**Do not conflate**:
- **V3 casual demo:** 3 hand-authored fictional cases for `cooked`, `no cap`, and `lowkey`. Locally scored. The last session's practice XP is in memory only. No user identities or experiment data are stored. Meanings are provisional, and example comments are not validated by linguists.
- **Backend research-generation pipeline:** 10 contexts **for one target sense** following the 5 easy : 3 medium : 2 hard specification; structural validation, retry and mock/LLM/fallback source labels. The backend-mode Android screen still uses its own API session. Its generation results and metrics must not be mixed with V3 mock gameplay.
- **GitHub static GET:** pre-generated public JSON. Correct answers are exposed in public packs; local scores are not tamper-resistant research observations.

The V3 demo's three-slang sequence is designed to avoid instant repetition of the **same answer** across adjacent rounds. It is **not a new specification for the LLM generator**, which still produces ten contexts per sense.

The currently implemented V3 does **not** yet:
- deliver mixed-slang cases dynamically from the live backend or an on-device question bank;
- persist progress, generate reliable mastery estimates or log participant behavior;
- validate semantic correctness, quality of contextual clues or distrators;
- record whether a participant opened a clue in a formal research dataset;
- provide completed usability/SUS or motivation results.

The UI may be tested as a prototype, but `source: mock`, `source: llm`, `source: fallback`, and `github-static-*` must remain distinct in any experiment.

## Acceptance criteria

- [ ] Three demo cases with **three unique slang terms** in each completed playthrough.
- [ ] Evidence pin and an answer option both required to enable submission.
- [ ] Correct choice gives 80 **demo** XP, incorrect choice 15; a pin never changes correctness.
- [ ] Explanation and pinned-clue review shown only after submission.
- [ ] No repeated award for one case.
- [ ] Case Report displays the three choices, practice XP and limitations.
- [ ] Feed / Learn / Quests open V3, while API Training remains accessible.
- [ ] Kotlin/JVM unit tests and Android debug APK build pass on GitHub Actions.
- [ ] Android hardware usability tests still needed; GitHub Actions compile is not a substitute.

**Future priority:** unify casual and research-mode question delivery behind a source-labeled session abstraction, and implement anonymous, secure research logging only after the protocol is finalized with the supervisor.
