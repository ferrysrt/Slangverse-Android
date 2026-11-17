import {createHash} from 'node:crypto';

const whitespace = value => String(value ?? '').replace(/\s+/g, ' ').trim();
const identityText = value => whitespace(String(value ?? '').normalize('NFKC')).toLowerCase();
const captureRank = value => typeof value === 'string' && Number.isFinite(Date.parse(value))
  ? Date.parse(value) : -Infinity;

// The first 24 hex characters of SHA-256(JSON.stringify([term, definition])).
// Both identity strings are NFKC-normalized, whitespace-collapsed and lowercased.
export function candidateIdFor(term, definition) {
  return createHash('sha256').update(JSON.stringify([
    identityText(term), identityText(definition)
  ])).digest('hex').slice(0, 24);
}

// Unicode word boundaries shared by collection, eligibility and mixed selection.
// Underscores and combining marks count as word characters, so Markdown emphasis
// ("_cap_") and accented forms ("capé") are NOT treated as standalone term matches.
export function wholeTermPattern(term) {
  const literal = identityText(term).trim().split(/\s+/)
    .map(part => part.replace(/[.*+?^${}()|[\]\\]/g, '\\$&')).join('\\s+');
  if (!literal) throw Error('invalid-term-pattern');
  return new RegExp('(^|[^\\p{L}\\p{N}\\p{M}_])' + literal + '(?=$|[^\\p{L}\\p{N}\\p{M}_])', 'iu');
}

export function containsWholeTerm(haystack, term) {
  return wholeTermPattern(term).test(String(haystack ?? ''));
}

export function validateCandidate(candidate) {
  if (!candidate || candidate.sourceEvidenceStatus !== 'literal-excerpt-unreviewed' ||
      typeof candidate.term !== 'string' || !whitespace(candidate.term) ||
      typeof candidate.normalizedDefinition !== 'string' || !whitespace(candidate.normalizedDefinition) ||
      candidate.candidateId !== candidateIdFor(candidate.term, candidate.normalizedDefinition) ||
      typeof candidate.normalizationModel !== 'string' || !candidate.normalizationModel.trim() ||
      !Array.isArray(candidate.sources) || !candidate.sources.length)
    throw Error('candidate-needs-literal-source-review');
  for (const source of candidate.sources) {
    let url;
    try { url = new URL(source?.url); } catch { throw Error('invalid-candidate-source'); }
    if (!['https:', 'http:'].includes(url.protocol) ||
        typeof source.title !== 'string' || !source.title.trim() ||
        typeof source.quote !== 'string' || !source.quote.trim() ||
        typeof source.collectedAt !== 'string' || !Number.isFinite(Date.parse(source.collectedAt)) ||
        source.collectionMethod !== 'firecrawl' ||
        !/^[a-f0-9]{64}$/.test(source.contentSha256)) throw Error('incomplete-candidate-provenance');
  }
}

// Contract checks do not establish that a quote semantically supports a meaning.
export function validateSourcedSense(sense, candidates) {
  const candidate = candidates.find(entry => entry.candidateId === sense.sourceCandidateId);
  validateCandidate(candidate);
  if (sense.term !== candidate.term || sense.sourceDatasetTerm !== candidate.term ||
      sense.normalizedDefinition !== candidate.normalizedDefinition ||
      sense.normalizationModel !== candidate.normalizationModel ||
      sense.sourceRelationship !== 'source-backed-provisional' ||
      !sense.referenceNote || !sense.review?.reviewedBy || !Number.isFinite(Date.parse(sense.review.reviewedAt)) ||
      !Array.isArray(sense.sources) || !sense.sources.length ||
      sense.sources.some(source => !candidate.sources.some(original =>
        ['url','title','quote','collectedAt','collectionMethod','contentSha256'].every(key => source[key] === original[key]))))
    throw Error('curated-source-mismatch');
  return sense;
}

function sourcesFor(entry) {
  const sources = (entry.sources ?? []).map(source => ({...source}));
  // Retain v1 references, but never invent their capture time, method or hash.
  if (entry.sourceUrl && !sources.some(source =>
    source.url === entry.sourceUrl &&
    whitespace(source.quote) === whitespace(entry.evidenceSnippet))) {
    sources.push({
      url: entry.sourceUrl,
      title: entry.sourceTitle ?? '',
      quote: entry.evidenceSnippet ?? '',
      collectedAt: entry.collectedAt ?? null,
      collectionMethod: entry.collectionMethod ?? null,
      contentSha256: entry.contentSha256 ?? null
    });
  }
  return sources;
}

function uniqueSources(sources) {
  const seen = new Set();
  return sources.filter(source => {
    const key = JSON.stringify([
      source.url, source.title, source.quote, source.collectedAt,
      source.collectionMethod, source.contentSha256
    ]);
    if (seen.has(key)) return false;
    seen.add(key);
    return true;
  });
}

// Arguments are candidate arrays. Preserve earlier fields and captures on a
// duplicate; different normalized definitions always remain separate candidates.
export function mergeCandidates(previous = [], incoming = []) {
  const merged = new Map();
  for (const entry of [...previous, ...incoming]) {
    const normalizedDefinition = whitespace(entry.normalizedDefinition ?? entry.definition);
    if (!whitespace(entry.term) || !normalizedDefinition) throw Error('invalid-candidate');
    const candidateId = candidateIdFor(entry.term, normalizedDefinition);
    const candidate = {
      ...entry,
      candidateId,
      normalizedDefinition,
      normalizationModel: entry.normalizationModel ?? null,
      originContext: entry.originContext ?? '',
      reviewStatus: 'unreviewed',
      sourceEvidenceStatus: entry.sourceEvidenceStatus ?? 'legacy-unverified',
      sources: uniqueSources(sourcesFor(entry))
    };
    const earlier = merged.get(candidateId);
    merged.set(candidateId, earlier ? {
      ...candidate,
      ...earlier,
      sources: uniqueSources([...earlier.sources, ...candidate.sources])
    } : candidate);
  }
  return sortByCapture([...merged.values()].map(entry => ({
    ...entry, ...captureTimesFor(entry),
    reviewStatus: normalizedSenseIdFor(entry) ? 'normalized-unreviewed' : 'unreviewed'
  })));
}

// Unknown legacy capture times stay unknown; run/migration times are not evidence.
export function captureTimesFor(entry) {
  const times = sourcesFor(entry).map(source => source.collectedAt)
    .filter(value => typeof value === 'string' && Number.isFinite(Date.parse(value)))
    .sort((a, b) => Date.parse(a) - Date.parse(b));
  return {firstSeenAt: times[0] ?? null, lastCollectedAt: times.at(-1) ?? null};
}

export function sortByCapture(entries) {
  return [...entries].sort((a, b) =>
    captureRank(captureTimesFor(b).lastCollectedAt) - captureRank(captureTimesFor(a).lastCollectedAt));
}

// Eligibility is a provenance contract, not manual or expert semantic approval.
export function normalizedSenseIdFor(candidate) {
  try { validateCandidate(candidate); }
  catch { return null; }
  return 'normalized-' + candidate.candidateId;
}

const CSV_FIELDS = [
  'term', 'sense_id', 'definition', 'source_url', 'source_title', 'source_quote',
  'collected_at', 'collection_method', 'review_status', 'normalized_definition',
  'normalization_model', 'candidate_id', 'first_seen_at', 'last_collected_at',
  'source_evidence_status', 'content_sha256'
];

function csvCell(value) {
  const text = String(value ?? '');
  return /[",\r\n]/.test(text) ? '"' + text.replace(/"/g, '""') + '"' : text;
}

// A separate row for every capture, including older sources of merged candidates.
export function candidateTableCsv(entries, {senseByCandidateId = new Map()} = {}) {
  const captures = [];
  for (const entry of entries) {
    const candidateId = entry.candidateId ?? candidateIdFor(
      entry.term, entry.normalizedDefinition ?? entry.definition
    );
    const senseId = normalizedSenseIdFor(entry);
    const {firstSeenAt, lastCollectedAt} = captureTimesFor(entry);
    for (const source of sourcesFor(entry)) {
      captures.push({collectedAt: source.collectedAt, row: [
        entry.term, senseByCandidateId.get(candidateId) ?? senseId ?? '', entry.definition,
        source.url, source.title, source.quote, source.collectedAt,
        source.collectionMethod, senseId ? 'normalized-unreviewed' : (entry.reviewStatus ?? 'unreviewed'),
        entry.normalizedDefinition ?? entry.definition, entry.normalizationModel, candidateId,
        firstSeenAt, lastCollectedAt, entry.sourceEvidenceStatus, source.contentSha256
      ]});
    }
  }
  captures.sort((a, b) => captureRank(b.collectedAt) - captureRank(a.collectedAt));
  return [CSV_FIELDS, ...captures.map(capture => capture.row)]
    .map(row => row.map(csvCell).join(',')).join('\n') + '\n';
}
