# Current checkpoint — Profile Pictures 0.3.1

This checkpoint adds an offline CC0 portrait library, consistent avatar UI, LLM-selectable avatar IDs, normalization and legacy fallbacks in both Android training clients. Sources, licences, checksums and reproduction steps are in [the avatar folder](../assets/profile-pictures/README.md).

The current feature branch is `feat/social-simulator-feed`. Home opens into the ten-item local practice feed. This revision adds fictional character profiles, nested replies, quoted earlier posts, context hashtag navigation, visible player replies, and a local inbox that links to exact answered conversations. The archived V3 welcome panel remains under Quests. See [checkpoint behavior and phone acceptance checks](SOCIAL_SIMULATOR_FEED.md).

The ten-question, single-sense, nominal 5/3/2 pack and 80/15 XP rules remain intact. Story follow-ups unlock once per submitted post regardless of correctness, and social actions do not affect scoring. The context quest counts categories present in the pack rather than imposing all nine on every slang.

This remains hand-authored practice with local saveable UI state. Durable participant response recording/export, generated-pack integration into this local feed, content validation and device usability evaluation are outstanding. The backend, Connected Training and archived gameplay retain their separate boundaries. Current verification is recorded in [test results](TEST_RESULTS.md).

## Historical V2.2 status (retained for reference)

| Part | Implemented | Independently verified here | Remaining |
| --- | --- | --- | --- |
| GitHub Actions Android cloud build workflow | Yes | Not executed | Upload to new repo, run workflow, inspect APK artifact/build logs |
| Compose 8-screen source | Yes | Inspected as source only | Android Gradle build/emulator test |
| API Training screen | Yes | Inspected as source only | Device ↔ backend integration smoke |
| Android debug network config | Yes | Inspected as XML source | Build and manifest-merger verification |
| HTTP REST mock server | Yes | Node.js automated HTTP tests pass | Rate limiting, auth and durable storage |
| 10-context curated mock pack | Yes | Test passes structural rules and unique IDs | Human check of linguistic appropriateness |
| Indirect comment hints | Yes | String leak tests pass | Semantic/hint-quality validation |
| Answer grading and XP | Yes | Server unit + HTTP tests pass | Persist progress and user accounts |
| Real LLM provider adapter | Optional source | Fake provider tests only | Real provider/model tests, audit and quota controls |
| Fallback/provenance | Yes | Dependency-injected tests pass | Persisted experiment logs and telemetry |
| Competitive leaderboard | Deliberately disabled | Endpoint test verifies disabled flag | Daily standardized question pack and auth |
| SUS | Not implemented | No participants evaluated | Consent, sampling, usability study |

**Do not report a successful APK** until it is compiled. **Do not report successful live LLM calls** until a real provider has been used and measured. Current methodology is a proposal, not final approval from advisers.
