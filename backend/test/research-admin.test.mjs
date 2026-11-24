import assert from 'node:assert/strict';
import { readFileSync } from 'node:fs';
import { test } from 'node:test';
import { generationSummary, verifyResearchBank } from '../scripts/research-admin.mjs';
import { extractGlossary, glossaryCsv } from '../dataset/refresh-deterministic.mjs';
import { buildNormalizedSenses } from '../dataset/normalized.mjs';

test('published bank verifies exact immutable bytes and rejects altered data', () => {
  const root = new URL('../../question-bank/', import.meta.url);
  const catalog = JSON.parse(readFileSync(new URL('catalog.json', root), 'utf8'));
  const files = Object.fromEntries(catalog.packs.map(({ packId }) =>
    [packId, readFileSync(new URL('packs/' + packId + '.json', root), 'utf8')]));
  assert.equal(verifyResearchBank({ catalog, files }), 90);
  files[catalog.packs[0].packId] += ' ';
  assert.throws(() => verifyResearchBank({ catalog, files }), /bank-hash-mismatch/);
});

test('truncation and malformed results do not count as graded questions', () => {
  const result = { ok: false, actualModel: 'fixture', validation: { checks: [], errors: [] } };
  const output = { questions: Array.from({ length: 5 }) };
  const summary = generationSummary(result, {}, { actualModel: 'fixture', finishReason: 'length' }, output);
  assert.equal(summary.questionsPassed, null);
  assert.equal(summary.questionsFailed, null);
  assert.equal(generationSummary(result, {}, { actualModel: 'fixture', finishReason: 'stop' }, output).questionsFailed, 5);
});

test('explicit definitions become usable candidates with literal provenance and optional dates', () => {
  const entries = extractGlossary([{
    title: 'Fixture', url: 'https://example.test/glossary', collectedAt: '2026-01-10T00:00:00Z',
    sourcePublishedAt: '2026-01-01T00:00:00Z',
    markdown: 'They are cooked in this game.\n- **Cooked**: In a difficult situation with few options.\n**Lowkey** — Used to express something subtly or discreetly.'
  }]);
  assert.equal(entries.length, 2);
  assert.ok(entries[0].sources[0].quote.includes('**Cooked**'));
  assert.equal(entries[0].sources[0].sourcePublishedAt, '2026-01-01');
  assert.equal(buildNormalizedSenses(entries).length, 2);
  assert.match(glossaryCsv(entries), /^slang,definition,date,source\n"cooked"/);
});
