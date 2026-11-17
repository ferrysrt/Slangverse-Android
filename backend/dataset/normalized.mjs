import {createHash} from 'node:crypto';
import {captureTimesFor, normalizedSenseIdFor, sortByCapture} from '../src/datasetProvenance.mjs';

export const DATASET_SOURCE = 'firecrawl-normalized';
export const DATASET_NOTE = 'Automatically eligible normalized definitions with literal source provenance; unreviewed and not expert validated. Ordered by newest recorded capture, not popularity. Legacy entries with incomplete provenance remain in the dataset but are not playable.';

export function buildNormalizedSenses(entries) {
  return sortByCapture(entries).flatMap(candidate => {
    const senseId = normalizedSenseIdFor(candidate);
    if (!senseId) return [];
    return [{
      senseId, term: candidate.term, targetMeaning: candidate.normalizedDefinition,
      definition: candidate.definition, normalizedDefinition: candidate.normalizedDefinition,
      normalizationModel: candidate.normalizationModel, sourceCandidateId: candidate.candidateId,
      sourceDataset: 'firecrawl', sourceDatasetTerm: candidate.term,
      sourceRelationship: 'source-backed-normalized-unreviewed',
      sourceEvidenceStatus: candidate.sourceEvidenceStatus,
      sources: structuredClone(candidate.sources), ...captureTimesFor(candidate),
      reviewStatus: 'normalized-unreviewed', playable: true
    }];
  });
}

function canonical(value) {
  if (Array.isArray(value)) return value.map(canonical);
  if (value && typeof value === 'object') return Object.fromEntries(
    Object.keys(value).sort().map(key => [key, canonical(value[key])])
  );
  return value;
}

// Hash data content, not file line endings, JSON formatting or refresh counters.
export function datasetVersionFor(entries) {
  const content = entries.map(entry => JSON.stringify(canonical(entry))).sort();
  return 'firecrawl-normalized-' + createHash('sha256')
    .update(JSON.stringify(content)).digest('hex').slice(0, 24);
}
