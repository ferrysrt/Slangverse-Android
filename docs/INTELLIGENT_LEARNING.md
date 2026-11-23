# Intelligent Learning · V0.8.0

## Try it

1. Open **Feed → Source**. Choose **Browse** for the default shuffle, **Adaptive** for local recommendations, or **Fixed order** for the same ordered questions on the same loaded bank revision.
2. In **Adaptive**, answer a question, return to **For you**, and open **Next practice**. A missed meaning with an unanswered question in another context has priority. When another sense is available, one question intervenes before the same sense returns.
3. Open **Learn → Open slang collection** or the collection shortcut on **Progress**. The meaning is revealed after the first answer. The **Quests** tab counts distinct meanings and includes a two-context goal when the loaded bank supports it.
4. Choose **Open context practice** under **Source** for six bundled questions covering `lowkey` and `no cap` in three contexts each. **Open offline sample** still opens the original ten `cooked` scenarios.

## How practice is assigned

- The Android client groups questions by their published sense ID, with a normalized term-and-meaning fallback for authored content. Different meanings of the same spelling are distinct entries. Only answers for currently loaded question identities count.
- A correct answer moves an entry to **Decoded**. Two distinct matched contexts and a correct latest answer yield **Mastered**. A later miss moves the sense back to **In practice**. These are local game milestones.
- Adaptive ordering puts unanswered questions first and is recalculated when the bank loads or the order setting changes. The next-question suggestion updates after each submitted answer while the list stays in place. Suggestions prioritize another context after a miss, then a new context for a partially practiced sense, then unseen senses.
- Fixed order sorts by stable question identity and hides adaptive suggestions. All modes share the same local practice history and 80/15 XP rules. Research sessions with a locked bank, consent and response export are V0.9 work.
- Each recommendation uses an existing unanswered question. When the bank lacks another context for a sense, the app offers another available question. The 90-question published bank at this checkpoint covers 88 sense IDs, two of them in multiple contexts. The bundled context practice offers three contexts per sense on demand. The published bank remains the source for the default mixed feed.

The phone uses existing public packs or the bundled authored sample. Ordering, scoring, collection status and scripted character replies are computed locally. The mode preference and inbox read state survive an app restart alongside the existing practice snapshot.
