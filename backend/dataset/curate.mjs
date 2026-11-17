import {readFileSync, writeFileSync} from 'node:fs';
import {resolve} from 'node:path';
import {fileURLToPath} from 'node:url';
import {candidateTableCsv, validateCandidate, validateSourcedSense} from '../src/datasetProvenance.mjs';

export function curateCandidates({entries, curated, reviews}) {
  if (![entries, curated, reviews].every(Array.isArray)) throw Error('invalid-curation-input');
  const result = structuredClone(curated), seen = new Set();
  for (const review of reviews) {
    if (typeof review.senseId !== 'string' || !/^[a-z0-9]+(?:-[a-z0-9]+)*$/.test(review.senseId) || review.senseId.length > 80 ||
        typeof review.targetMeaning !== 'string' || review.targetMeaning.trim().length < 5 || review.targetMeaning.length > 160 ||
        !review.reviewedBy?.trim() || !Number.isFinite(Date.parse(review.reviewedAt)) || !review.referenceNote?.trim() ||
        seen.has(review.senseId)) throw Error('invalid-explicit-sense-review');
    seen.add(review.senseId);
    const candidate = entries.find(entry => entry.candidateId === review.candidateId);
    validateCandidate(candidate);
    const sense = {
      senseId: review.senseId, term: candidate.term, targetMeaning: review.targetMeaning.trim(),
      reviewStatus: 'provisional', playable: true, sourceDataset: 'firecrawl',
      sourceCandidateId: candidate.candidateId, sourceDatasetTerm: candidate.term,
      sourceRelationship: 'source-backed-provisional', referenceNote: review.referenceNote.trim(),
      definition: candidate.definition, normalizedDefinition: candidate.normalizedDefinition,
      normalizationModel: candidate.normalizationModel, sources: structuredClone(candidate.sources),
      review: {reviewedBy: review.reviewedBy.trim(), reviewedAt: review.reviewedAt,
        scope: 'developer-source-and-safety-review-not-expert-validation'}
    };
    const existing = result.find(entry => entry.senseId === sense.senseId);
    if (existing) {
      if (existing.sourceCandidateId !== sense.sourceCandidateId || existing.targetMeaning !== sense.targetMeaning)
        throw Error('sense-id-already-mapped-use-a-new-id');
      validateSourcedSense(existing, entries);
    } else result.push(validateSourcedSense(sense, entries));
  }
  return result;
}

export function applySenseReviews({dataDir = new URL('../data/', import.meta.url)} = {}) {
  const file = name => dataDir instanceof URL ? new URL(name, dataDir) : resolve(dataDir, name);
  const {entries} = JSON.parse(readFileSync(file('discovered_candidates.json'), 'utf8'));
  const curated = JSON.parse(readFileSync(file('curated_senses.json'), 'utf8'));
  const reviews = JSON.parse(readFileSync(file('sense_reviews.json'), 'utf8'));
  const result = curateCandidates({entries, curated, reviews});
  const mapping = new Map(result.filter(s => s.sourceCandidateId).map(s => [s.sourceCandidateId, s.senseId]));
  writeFileSync(file('curated_senses.json'), JSON.stringify(result, null, 2) + '\n');
  writeFileSync(file('discovered_candidates.csv'), candidateTableCsv(entries, {senseByCandidateId: mapping}));
  return {candidates: entries.length, reviewedMappings: reviews.length, playableSenses: result.length};
}

if (process.argv[1] && resolve(process.argv[1]) === fileURLToPath(import.meta.url)) {
  console.log(JSON.stringify(applySenseReviews()));
}
