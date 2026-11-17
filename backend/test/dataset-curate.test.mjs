import test from 'node:test';
import assert from 'node:assert/strict';
import {mkdtempSync, readFileSync, writeFileSync, rmSync} from 'node:fs';
import {tmpdir} from 'node:os';
import {join} from 'node:path';
import {curateCandidates, applySenseReviews} from '../dataset/curate.mjs';
import {candidateIdFor, validateSourcedSense} from '../src/datasetProvenance.mjs';

const definition = 'a fictional test meaning used only in fixtures';
const candidate = {
  candidateId: candidateIdFor('fixture slang', definition), term: 'fixture slang', definition,
  normalizedDefinition: definition, normalizationModel: 'fixture/model', reviewStatus: 'unreviewed',
  sourceEvidenceStatus: 'literal-excerpt-unreviewed',
  sources: [{url: 'https://example.org/fixture', title: 'Fixture source',
    quote: 'Fixture slang means a fictional test meaning used only in fixtures.',
    collectedAt: '2026-11-17T00:00:00.000Z', collectionMethod: 'firecrawl', contentSha256: 'a'.repeat(64)}]
};
const review = {candidateId: candidate.candidateId, senseId: 'fixture-slang-meaning',
  targetMeaning: 'a fictional test meaning', reviewedBy: 'test fixture reviewer',
  reviewedAt: '2026-11-17T01:00:00.000Z', referenceNote: 'Test only; not expert validated.'};
const curate = (entries = [candidate], reviews = [review], curated = []) => curateCandidates({entries, reviews, curated});

test('discovery never auto-promotes and explicit review retains all provenance as provisional', () => {
  assert.deepEqual(curate([candidate], []), []);
  const [sense] = curate();
  assert.equal(sense.playable, true);
  assert.equal(sense.reviewStatus, 'provisional');
  assert.equal(sense.sourceCandidateId, candidate.candidateId);
  assert.deepEqual(sense.sources, candidate.sources);
  assert.equal(sense.normalizedDefinition, definition);
  assert.equal(sense.normalizationModel, 'fixture/model');
  assert.match(sense.review.scope, /not-expert-validation/);
  assert.equal(candidate.reviewStatus, 'unreviewed');
  assert.deepEqual(curate([candidate], [review], [sense]), [sense]);
});

test('missing or term-appearance-only evidence cannot become playable', () => {
  assert.throws(() => curate([], [review]), /literal-source-review/);
  for (const patch of [
    {sourceEvidenceStatus: 'literal-term-appearance-only'}, {candidateId: 'forged'},
    {normalizationModel: ''}, {sources: []}
  ]) assert.throws(() => curate([{...candidate, ...patch}]), /literal-source-review/);
  for (const patch of [{url: 'not a URL'}, {quote: ''}, {title: ''}, {collectedAt: null},
    {contentSha256: ''}, {collectionMethod: 'llm-guessed'}]) {
    assert.throws(() => curate([{...candidate, sources: [{...candidate.sources[0], ...patch}]}]), /source|provenance/);
  }
});

test('reviews require attribution, actual time, meaning, note, unique safe sense IDs', () => {
  for (const patch of [{senseId: '../bad'}, {senseId: undefined}, {reviewedAt: ''},
    {reviewedBy: ''}, {referenceNote: ''}, {targetMeaning: ''}]) {
    assert.throws(() => curate([candidate], [{...review, ...patch}]), /explicit-sense-review/);
  }
  assert.throws(() => curate([candidate], [review, review]), /explicit-sense-review/);
  assert.throws(() => curate([candidate], [review], [{senseId: review.senseId, targetMeaning: 'different'}]), /already-mapped/);
});

test('same term with a distinct meaning needs a separate candidate and sense mapping', () => {
  const otherDefinition = 'another completely distinct fictional sense for testing';
  const other = {...candidate, candidateId: candidateIdFor(candidate.term, otherDefinition),
    definition: otherDefinition, normalizedDefinition: otherDefinition};
  const result = curate([candidate, other], [review, {...review, candidateId: other.candidateId,
    senseId: 'fixture-slang-other', targetMeaning: otherDefinition}]);
  assert.equal(result.length, 2);
  assert.equal(result[0].term, result[1].term);
  assert.notEqual(result[0].sourceCandidateId, result[1].sourceCandidateId);
});

test('runtime catalog contract rejects detached citations without discarding legacy conflict notes', () => {
  const [sense] = curate();
  assert.equal(validateSourcedSense(sense, [candidate]), sense);
  assert.throws(() => validateSourcedSense({...sense, sources: [{...sense.sources[0], quote: 'invented'}]}, [candidate]), /source-mismatch/);
  const legacy = {senseId: 'legacy', sourceRelationship: 'conflict', referenceNote: 'Keep this conflict.'};
  assert.deepEqual(curate([candidate], [], [legacy]), [legacy]);
});

test('file curation exports mapped source table without altering candidate review status', t => {
  const dir = mkdtempSync(join(tmpdir(), 'slang-curation-'));
  t.after(() => rmSync(dir, {recursive: true, force: true}));
  for (const [name, data] of Object.entries({
    'discovered_candidates.json': {version: 2, entries: [candidate]},
    'curated_senses.json': [], 'sense_reviews.json': [review]
  })) writeFileSync(join(dir, name), JSON.stringify(data));
  assert.deepEqual(applySenseReviews({dataDir: dir}), {candidates: 1, reviewedMappings: 1, playableSenses: 1});
  const csv = readFileSync(join(dir, 'discovered_candidates.csv'), 'utf8');
  assert.ok(csv.includes('fixture slang,fixture-slang-meaning,'));
  assert.ok(csv.includes('https://example.org/fixture'));
  assert.equal(JSON.parse(readFileSync(join(dir, 'discovered_candidates.json'))).entries[0].reviewStatus, 'unreviewed');
});
