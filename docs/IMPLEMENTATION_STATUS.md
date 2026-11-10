# Implementation status — Slangverse V2.2 (direct)

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
