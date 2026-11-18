import {packTermKey} from './mixedTermPlanner.mjs';

/**
 * Terms that survive provenance but make poor quiz items: interjections, bare
 * acronyms, and ordinary words whose slang reading is too ambiguous to grade.
 *
 * This list is hand-curated and only filters pack planning. It never edits the
 * dataset: a blocked term stays archived as unreviewed evidence and can be
 * unblocked later without re-collecting anything.
 */
export const BLOCKED_TERM_KEYS = Object.freeze(new Set([
  // Laughter and interjections
  'hehe', 'haha', 'hihi', 'hoho', 'huhu', 'ugh', 'lol', 'lmao', 'rofl', 'omg', 'omfg', 'wtf',
  'wow', 'yay', 'oof', 'meh', 'bruh', 'hmm', 'uh', 'ah', 'oh',
  // Bare acronyms and abbreviations of ordinary phrases
  'bff', 'xmas', 'priv', 'ceo', 'dm', 'dms', 'tbh', 'idk', 'brb', 'ngl', 'atp', 'fomo',
  'imo', 'smh', 'af', 'fyi', 'btw', 'irl', 'ttyl',
  // Ordinary words whose slang sense is too ambiguous for one graded answer
  'bet', 'fire', 'buns', 'cap', 'polyester', 'mood'
]));

const MAX_WORDS = 6;
const MAX_CHARACTERS = 40;
const MIN_LETTERS = 3;

/** Why a term cannot become a graded question, or null when it can. */
export function termQualityReason(term, {blocked = BLOCKED_TERM_KEYS} = {}) {
  const key = packTermKey(term);
  if (!key) return 'empty-term';
  if (blocked.has(key)) return 'blocked-term';
  const letters = key.replace(/[^a-z]/g, '');
  if (letters.length < MIN_LETTERS) return 'too-few-letters';
  if (key.split(' ').length > MAX_WORDS) return 'too-many-words';
  if (key.replace(/ /g, '').length > MAX_CHARACTERS) return 'too-long';
  // Pure laughter variants such as "hahaha" are not a meaning anyone can answer.
  if (/^(?:ha|he|hi|ho|hu|ah|oh|eh|uh){2,}$/.test(letters)) return 'interjection-only';
  return null;
}

/**
 * Keep planned-eligible senses only, one per normalized term (dataset order,
 * which is newest capture first). Duplicate spellings collapse because
 * `packTermKey` strips punctuation: "bussin'" and "bussin" are the same term.
 */
export function selectPlayableSenses(senses, {blocked = BLOCKED_TERM_KEYS} = {}) {
  if (!Array.isArray(senses)) throw Error('invalid-normalized-dataset');
  const seen = new Set();
  const playable = [];
  const rejected = [];
  for (const sense of senses) {
    const reason = termQualityReason(sense?.term, {blocked});
    if (reason) {
      rejected.push({term: sense?.term ?? '', reason});
      continue;
    }
    const key = packTermKey(sense.term);
    if (seen.has(key)) {
      rejected.push({term: sense.term, reason: 'duplicate-term'});
      continue;
    }
    seen.add(key);
    playable.push(sense);
  }
  return {senses: playable, rejected};
}
