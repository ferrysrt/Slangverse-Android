import assert from 'node:assert/strict';
import { spawnSync } from 'node:child_process';
import { createHash } from 'node:crypto';
import { mkdtempSync, readFileSync, rmSync, writeFileSync } from 'node:fs';
import { tmpdir } from 'node:os';
import { join } from 'node:path';
import { fileURLToPath } from 'node:url';
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

test('insufficient or altered archived captures leave current dataset exports untouched', t => {
  const directory = mkdtempSync(join(tmpdir(), 'slangverse-dataset-'));
  t.after(() => rmSync(directory, { recursive: true }));
  const archive = join(directory, 'sources.json');
  const outputs = ['dataset.json', 'dataset.csv'].map(name => join(directory, name));
  for (const output of outputs) writeFileSync(output, 'prior version');

  const markdown = '**Cooked**: In a difficult situation with few options.';
  const doc = {
    url: 'https://example.test/glossary', collectedAt: '2026-01-10T00:00:00Z',
    markdown, markdownTruncated: false, contentSha256: createHash('sha256').update(markdown).digest('hex')
  };
  const script = fileURLToPath(new URL('../dataset/refresh-deterministic.mjs', import.meta.url));
  function replay(document) {
    writeFileSync(archive, JSON.stringify({ documents: [document] }));
    const outcome = spawnSync(process.execPath, [script, '--import', '--replay', archive], {
      encoding: 'utf8', env: { ...process.env, DATASET_EXPORT_DIR: directory, FIRECRAWL_API_KEY: '',
        SLANGVERSE_SUPABASE_URL: '', RESEARCH_WORKER_SUPABASE_KEY: '' }
    });
    assert.equal(outcome.status, 1);
    for (const output of outputs) assert.equal(readFileSync(output, 'utf8'), 'prior version');
    return outcome.stderr.trim();
  }
  assert.equal(replay(doc), 'insufficient-explicit-glossary-terms');
  assert.equal(replay({ ...doc, markdown: markdown + ' Additional text.' }), 'glossary-capture-hash-mismatch');
});
