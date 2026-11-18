import {normalize} from './game.mjs';

/** Term identity shared by planning and question validation. */
export const packTermKey = value => normalize(String(value ?? ''));

/**
 * Choose the slang terms for the next 10-question pack.
 *
 * Terms no published pack has used yet come first; once every normalized term has
 * been used, the least-used terms go next. A repeated slang term therefore spreads
 * across the bank instead of clustering in two consecutive packs.
 */
export function planPackTerms(senses, {count = 10, usedCounts = new Map()} = {}) {
  if (!Array.isArray(senses) || !senses.length) throw Error('invalid-normalized-dataset');
  if (!Number.isInteger(count) || count < 1) throw Error('invalid-pack-term-count');
  if (!(usedCounts instanceof Map)) throw Error('invalid-term-usage');
  const terms = new Map();
  for (const sense of senses) {
    const key = packTermKey(sense?.term);
    if (!key) throw Error('invalid-normalized-sense');
    if (!terms.has(key)) terms.set(key, sense.term);
  }
  if (terms.size < count) throw Error('not-enough-distinct-terms');
  return [...terms.keys()]
    .sort((a, b) => (usedCounts.get(a) ?? 0) - (usedCounts.get(b) ?? 0) || a.localeCompare(b))
    .slice(0, count);
}

/** Every sense of the planned terms; selectMixedSenses still picks one sense per term. */
export function sensesForTerms(senses, terms) {
  if (!Array.isArray(senses) || !Array.isArray(terms) || !terms.length) throw Error('invalid-normalized-selection');
  const wanted = new Set(terms);
  const selected = senses.filter(sense => wanted.has(packTermKey(sense?.term)));
  if (!selected.length) throw Error('empty-term-selection');
  return selected;
}

/** How many published questions already use each normalized term. */
export function countPublishedTerms(packs) {
  if (!Array.isArray(packs)) throw Error('invalid-published-pack-list');
  const counts = new Map();
  for (const pack of packs) {
    for (const question of pack?.questions ?? []) {
      const key = packTermKey(question?.slang);
      if (key) counts.set(key, (counts.get(key) ?? 0) + 1);
    }
  }
  return counts;
}
