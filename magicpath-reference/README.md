# Slangverse V3 — Context Detective (current)

- Project: `470964531963789312`
- Component: `470964549080743936`
- Current V3 revision: `472691771546144768`
- Interactive preview: https://designs.magicpath.ai/v1/gladly-water-6771
- Warm Editorial baseline revision: `470990382860222464`
- V3 screens: Feed, Investigate/Play, Answer Feedback, **Case Report**, Glossary, Challenges, Progress, Profile
- V3 mechanic: read fictional social thread → inspect reply → **pin context evidence** (not graded) → choose meaning → feedback → next **different slang** → case report
- Android: native Compose `ContextDetective.kt` and `ContextDetectiveGame.kt`; Connected Training retained for real/GET source-specific API sessions
- Design and research contract: `docs/CONTEXT_DETECTIVE_V3.md`

---

# Slangverse — Warm Editorial design sync

## MagicPath production component (updated November 13, 2026)

- Existing project: `470964531963789312`
- Existing component: `470964549080743936`
- Warm Editorial revision: `470990382860222464`
- MagicPath canvas: https://www.magicpath.ai/files/470964531963789312
- Live prototype: https://designs.magicpath.ai/v1/gladly-water-6771

The existing V2 MagicPath component was updated in place from a dark social-feed concept to **Warm Editorial**.

Its seven interactive pages are:
1. Feed
2. Play / Quiz
3. Answer Feedback
4. Glossary / Learn
5. Challenges
6. Progress
7. Profile / Settings

Style tokens: paper `#FFF3E5`, ink `#18213B`, coral `#EE7058`, peach `#FBC8B2`, sand border `#E9D6C8`. Editorial serif display headlines, clear sans body copy, warm cards and deep-navy bottom navigation.

MagicPath contains an **interactive React prototype with fictional content**, not a live backend. Its XP, streak and other account statistics are demonstration UI.

## Android source of truth

The Kotlin + Jetpack Compose implementation is in:
- `android/app/src/main/java/id/slangverse/app/WarmEditorialTheme.kt`
- `android/app/src/main/java/id/slangverse/app/SlangverseApp.kt`
- `android/app/src/main/java/id/slangverse/app/RemoteTrainingPanel.kt`

Feed/learn/challenges/progress/profile are native Compose and use the Warm Editorial visual language. The **Connected Training** screen continues to offer the real GitHub GET/static JSON path and Node backend (mock/LLM) path without changing grading or API credentials.

**Important distinction**: MagicPath React and the Android implementation are separate codebases sharing design decisions. The production Android app does not embed a MagicPath WebView or use React.

The sample UI can render without connectivity. The backend has not been publicly deployed and the public static JSON bank may still need publishing.

## Research boundaries

The visual prototype is not expert-validated learning content. Hardcoded XP/profile examples do not count as participant outcomes. Structural validation is not semantic validation. Do not claim successful live-phone testing until device/emulator gameplay has been verified.

## Original prototype

Previous MagicPath dark-mode revision: `470973154093850624`. Keep this revision as historical reference. It was not overwritten in GitHub by copying JSX into the Android runtime.
