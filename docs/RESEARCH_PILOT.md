# V0.9 research pilot

## Before a participant session

Review the 30 English-language items in [`pilot_bank_v1.json`](../android/app/src/main/assets/pilot_bank_v1.json). Ten intended senses each appear in three contexts. Answer positions are balanced across the bank and the keyed text varies for each context. Ask a reviewer to flag ambiguous keys, weak distractors, hints that reveal the answer, and dialogue that feels out of place. Record the accepted bank revision before recruiting participants.

Use an approved participant information and consent process for your study. The in-app checkbox explains local recording. Use a study code such as `P001` and keep any identity-to-code mapping separately. The bank is an authored draft for review and piloting.

## Run a dry session

1. Open **Progress → Open research pilot** and enter a dummy code such as `DRYRUN01`.
2. Choose **Fixed order** or **Adaptive**, acknowledge the recording notice and tap **Start pilot**. The app locks the condition and exact bank SHA-256 for the session.
3. Answer the questions or pause by leaving the screen. Each saved answer survives app restart. The device holds one pilot session at a time, separate from ordinary practice and XP.
4. Tap **Export responses CSV** and choose a location in the Android file picker. You can export an incomplete session for a dry run. Verify the row count and bank checksum before clearing the session on a shared test device.

Fixed order uses the authored question sequence. Adaptive uses only answers submitted in the current pilot and can bring a missed sense forward in a different context. Both conditions cover the same 30 items if completed. Record how participants are assigned to conditions in the study protocol.

## CSV and measurement

Each row records the assigned code, internal session ID, condition, bank ID and checksum, start time, displayed question ordinal, item and sense IDs, context, selected and correct zero-based indices, correctness, response milliseconds, hint viewed flag and UTC submission time.

Response time counts foreground time between displaying the question and tapping **Submit answer**. Time spent reading feedback is outside the following question's timer. Leaving the pilot screen or relaunching into an unanswered item starts a new timing visit for that item. `hints_viewed` is 1 when the optional reply was opened at least once during that visit. Treat incomplete or resumed sessions according to the study protocol.

The same fixed-order condition requires the same bundled bank revision on every device. A changed bank blocks resume for the existing session and leaves its recorded rows available for export. For an intentionally revised bank, change the bank ID as well as its contents. The main published bank and the optional offline practice decks remain separate.
