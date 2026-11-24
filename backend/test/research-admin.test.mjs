import assert from 'node:assert/strict';
import { spawnSync } from 'node:child_process';
import { createHash } from 'node:crypto';
import { mkdtempSync, readFileSync, rmSync, writeFileSync } from 'node:fs';
import { tmpdir } from 'node:os';
import { join } from 'node:path';
import { fileURLToPath } from 'node:url';
import { test } from 'node:test';
import { generationSummary, verifyResearchBank } from '../scripts/research-admin.mjs';
import { collectGlossary, extractGlossary, glossaryCsv, validateGlossaryCoverage } from '../dataset/refresh-deterministic.mjs';
import { readDiscoveryOptions } from '../../admin/lib/discovery-options.mjs';
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

test('one Firecrawl request honors date and domain filters and requires evidence from distinct source URLs', async () => {
  const options = readDiscoveryOptions({ query: 'modern slang glossary', from: '2026-11-01', to: '2026-11-24',
    minUrls: '2', domains: 'Example.test, example.test' });
  const lines = Array.from({ length: 10 }, (_, index) => '**Term' + index + '**: A fixture definition with enough words.');
  const page = (url, publishedTime, markdown) => ({
    url, title: 'Test glossary', markdown, metadata: { publishedTime }
  });
  let calls = 0;
  const docs = await collectGlossary(options, { key: 'fixture', fetchImpl: async (url, init) => {
    calls++;
    assert.equal(new URL(url).hostname, 'api.firecrawl.dev');
    const payload = JSON.parse(init.body);
    assert.equal(payload.query, 'modern slang glossary');
    assert.equal(payload.tbs, 'cdr:1,cd_min:11/01/2026,cd_max:11/24/2026');
    assert.deepEqual(payload.includeDomains, ['example.test']);
    return Response.json({ data: { web: [
      page('https://www.example.test/glossary?ref=one', '2026-11-01', lines.join('\n')),
      page('https://example.test/glossary/#copy', '2026-11-15', lines.join('\n')),
      page('https://sub.example.test/another', '2026-11-24', lines[0]),
      page('https://example.test/old', '2026-10-30', lines[0]),
      page('https://example.test/undated', undefined, lines[0]),
      page('https://unrelated.test/glossary', '2026-11-22', lines[0])
    ] } });
  } });
  assert.equal(calls, 1);
  assert.equal(docs.length, 3);
  assert.throws(() => validateGlossaryCoverage(extractGlossary(docs.slice(0, 2)), options.minimum), /insufficient-source-urls/);
  assert.deepEqual(validateGlossaryCoverage(extractGlossary(docs), options.minimum), { distinctTerms: 10, urls: 2 });
  assert.throws(() => readDiscoveryOptions({ from: '2026-11-31', to: '2026-12-15' }), /invalid-date-range/);
  assert.throws(() => readDiscoveryOptions({ minUrls: '41' }), /invalid-minimum-source-urls/);
  assert.throws(() => readDiscoveryOptions({ domains: 'https://example.test/glossary' }), /invalid-source-domains/);
});
