# Account and progress sync plan

## Product direction

Keep the usual feed as the practice experience. Sign-in adds durable progress and sync between devices. Guest practice can remain local, with a one-time import offered at first sign-in. The separate Research Pilot is outside this rollout.

Account progress and study participation serve different purposes. Let an account store its own practice history. If the study uses that history, enroll the user explicitly and export rows under a study code with the account identity kept separate. Confirm the enrollment and data-retention approach with the study supervisor before collecting participant data.

## Minimum data model

| Record | Purpose |
| --- | --- |
| `profiles` | One record per authenticated user with minimal display preferences |
| `practice_attempts` | Append-only submissions linked to the authenticated user and the exact question revision |
| `user_progress` | Summary derived from graded attempts, with XP awarded once per user and question revision |
| `study_enrollments` | Optional study code, condition, consent version and approved linkage for research exports |

An attempt needs a client-generated event ID, bank and pack identity, question ID and pack SHA-256, selected option, feed mode, hint usage when measured, and foreground response duration when measured. The server records receipt time and validates the response against the referenced bank revision. XP and correctness are derived from the answer key. Keep meaning identity and context from the bank so account changes do not affect comparisons.

The phone queues unanswered sync requests locally and retries with the same event ID. The server enforces idempotency and uses the authenticated identity from the session. Guest imports preserve the original revision and deduplicate already recorded answers. Historical local snapshots contain no response duration or hint count, so those fields stay empty for imported records.

## Hosting recommendation

Start with managed authentication and PostgreSQL, such as Supabase Auth and Postgres. Require row-level policies for user-owned data and keep grading writes and research exports behind a trusted operation. A paid or self-hosted server can be chosen when usage, cost or institutional requirements justify it. [Authentication](https://supabase.com/docs/guides/auth) · [Row-level security](https://supabase.com/docs/guides/database/postgres/row-level-security)

The existing VPS is optional. It can host a custom sync API or restricted admin export if operational control becomes useful. Keep HTTPS and server-only credentials, avoid exposing a public database port, and plan security updates, backups and a tested restore. The existing `render.yaml` covers a separate Connected Training backend with an app token. Per-user authentication and sync need their own design.

## Implementation order

1. Stand up a staging auth/database project with two test accounts. Verify each account can read only its own data.
2. Implement submit, retry and reconciliation around the exact bank revision. Test offline use, reinstallation, duplicate sends and two devices using the same account.
3. Add the one-time guest import and connect the compact Progress screen to the synced account totals.
4. Add approved study enrollment and a restricted, pseudonymous export when the study protocol is ready. Use the same frozen bank for Fixed and Adaptive if comparing the two modes.

## Staging database handoff

Create a dedicated Supabase staging project in the owner's organization. Share the Project URL and publishable key for Android configuration. Authorize the project's operator separately through the dashboard or Supabase CLI sign-in. Keep database passwords, Supabase access tokens and secret or service-role keys out of chat and the APK. [Supabase API keys](https://supabase.com/docs/guides/api/api-keys)

1. In the staging SQL Editor, apply [`20261124153000_account_progress.sql`](../supabase/migrations/20261124153000_account_progress.sql) once as the project database owner.
2. From the repository root, generate the private grading seed with `node backend/scripts/export-account-bank-sql.mjs`. Run its output in the same staging SQL Editor. The exporter reads the checked-in mixed catalog, verifies exact hashes, and fails before emitting SQL if a pack is invalid. Re-running the seed leaves registered revisions intact.
3. In **Authentication → Users**, create two throwaway test accounts using addresses controlled by the project owner. Use the normal sign-in flow to obtain each test session and verify isolation and retry behavior on staging. The desktop SQL fixture is specifically for a disposable PostgreSQL database.

The database owner registers question revisions. Android will send the exact pack ID, SHA-256 and question ID with a persistent event UUID to `submit_practice_attempt`. That function uses the signed-in identity for ownership, checks the answer against the registered revision, and returns the canonical saved row. A repeat with the same event UUID returns that row. A different event for an already answered revision also returns the original result. Android should reconcile to that result. `my_practice_progress` returns the signed-in account's totals, and the authenticated user can read only their own attempt rows.

This staging slice covers the current public mixed bank. Support for legacy and authored packs can be added with their own registered revisions. For a guest import, pass `source = guest_import` and leave timing and hint counts empty.

## Android staging preview

Set `SLANGVERSE_SUPABASE_URL` and `SLANGVERSE_SUPABASE_PUBLISHABLE_KEY` in the build environment before running `./gradlew :app:assembleDebug` from `android/`. The resulting **Slangverse Staging** app has its own package ID and installs alongside Slangverse. The regular debug and release apps omit the account preview.

In **Profile → Account preview**, sign in with one of the staging accounts after installing the database schema and grading seed. Enter each test password directly in the app. The preview reads account totals and keeps the session in memory. On-device practice still uses local storage. The next integration step is the persistent offline queue for submitting attempts and a cross-device staging test with the two accounts.
