import test from 'node:test';
import assert from 'node:assert/strict';
import {createHash} from 'node:crypto';
import {mkdtemp, readFile, writeFile, rm} from 'node:fs/promises';
import {tmpdir} from 'node:os';
import {join} from 'node:path';
import {fileURLToPath, pathToFileURL} from 'node:url';
import {spawnSync} from 'node:child_process';
import {
  collectDocuments, validateDiscoveries, extractCandidates, refreshDataset, SEARCH_URL,
  buildRecentSlangQuery, MIN_DOCUMENTS, SEARCH_RESULT_LIMIT
} from '../dataset/refresh.mjs';
import {candidateIdFor, mergeCandidates, candidateTableCsv} from '../src/datasetProvenance.mjs';

const capturedAt = '2026-01-02T03:04:05.000Z';
const quote = 'No cap means speaking honestly without any exaggeration.';
const secondQuote = 'People use no cap to emphasize they are telling the truth.';
const docs = [
  {url: 'https://example.org/words', title: 'Slang example', collectedAt: capturedAt,
    markdown: 'A glossary of expressions.\n' + quote + '\nMore context follows.'},
  {url: 'https://example.net/glossary', title: 'Another source', collectedAt: capturedAt,
    markdown: 'An independent glossary.\n' + secondQuote}
];
// A refresh is only attempted from a full capture, so the default mock answers
// with the floor of ten distinct pages; docs[0] and docs[1] stay the cited ones.
const fullDocs = [...docs, ...Array.from({length: 10 - docs.length}, (_, index) => ({
  url: 'https://example.org/filler-' + index, title: 'Filler ' + index, collectedAt: capturedAt,
  markdown: 'An extra independent glossary page.\n' + quote
}))];
const hash = text => createHash('sha256').update(text).digest('hex');
const jsonResponse = data => ({ok: true, status: 200, json: async () => data});
const item = (overrides = {}) => ({
  term: 'no cap', definition: 'used to emphasize a truthful statement without exaggeration',
  originContext: 'Used in informal online conversation.',
  sourceUrl: docs[0].url, evidenceSnippet: quote, ...overrides
});
const validate = (items, documents = docs) => validateDiscoveries(items, documents, {normalizationModel: 'unit/model'});
const normalizerResponse = (items, model = 'provider/actual-model') => ({
  model, choices: [{message: {content: JSON.stringify({items})}}]
});

function refreshMock(items, pages = fullDocs) {
  const calls = [];
  return {
    calls,
    fetchImpl: async (url, options) => {
      calls.push({url, body: JSON.parse(options.body)});
      assert.ok(calls.length <= 2, 'must not retry or make extra paid calls');
      if (calls.length === 1) {
        assert.equal(url, SEARCH_URL);
        return jsonResponse({data: {web: pages}});
      }
      assert.equal(url, 'https://openrouter.ai/api/v1/chat/completions');
      return jsonResponse(normalizerResponse(items));
    }
  };
}

async function temporaryOutput(t) {
  const directory = await mkdtemp(join(tmpdir(), 'slangverse-refresh-'));
  t.after(() => rm(directory, {recursive: true, force: true}));
  return {
    directory,
    jsonPath: join(directory, 'discovered_candidates.json'),
    csvPath: join(directory, 'discovered_candidates.csv')
  };
}

// Small independent CSV reader so embedded newlines are tested as data, not rows.
function parseCsv(text) {
  const rows = [];
  let row = [], cell = '', quoted = false;
  for (let i = 0; i < text.length; i++) {
    const char = text[i];
    if (quoted) {
      if (char === '"' && text[i + 1] === '"') { cell += '"'; i++; }
      else if (char === '"') quoted = false;
      else cell += char;
    } else if (char === '"') quoted = true;
    else if (char === ',') { row.push(cell); cell = ''; }
    else if (char === '\r' || char === '\n') {
      if (char === '\r' && text[i + 1] === '\n') i++;
      rows.push([...row, cell]); row = []; cell = '';
    } else cell += char;
  }
  assert.equal(quoted, false);
  if (row.length || cell) rows.push([...row, cell]);
  return rows;
}

const refreshOptions = {
  firecrawlKey: 'fake-firecrawl', normalizerKey: 'fake-normalizer',
  normalizerModel: 'configured/model'
};

test('Firecrawl uses one v2 search, newest-first sort, twenty results, markdown, and the explicit query', async () => {
  const calls = [];
  const before = Date.now();
  const found = await collectDocuments({key: 'fake', query: 'recent slang', fetchImpl: async (url, options) => {
    calls.push({url, body: JSON.parse(options.body)});
    return jsonResponse({data: {web: docs.map(doc => ({...doc,
      collectedAt: '2099-01-01T00:00:00.000Z', collectionMethod: 'model', contentSha256: 'fake'
    }))}});
  }});
  const after = Date.now();
  assert.equal(calls.length, 1);
  assert.equal(calls[0].url, SEARCH_URL);
  assert.deepEqual(calls[0].body, {
    query: 'recent slang', limit: SEARCH_RESULT_LIMIT, tbs: 'sbd:1', sources: [{type: 'web'}],
    scrapeOptions: {formats: [{type: 'markdown'}]}
  });
  assert.equal(found.length, 2);
  for (const [index, doc] of found.entries()) {
    assert.ok(Date.parse(doc.collectedAt) >= before && Date.parse(doc.collectedAt) <= after);
    assert.equal(doc.contentSha256, hash(docs[index].markdown));
    assert.equal(doc.collectionMethod, 'firecrawl');
  }
});

test('the default query carries the current UTC date and the named social platforms', async () => {
  const query = buildRecentSlangQuery(new Date('2026-11-19T23:59:00.000Z'));
  assert.equal(query,
    'latest internet slang November 19 2026 tiktok twitter instagram viral terms -site:youtube.com');
  const previous = process.env.FIRECRAWL_QUERY;
  delete process.env.FIRECRAWL_QUERY;
  const bodies = [];
  try {
    await collectDocuments({key: 'fake', fetchImpl: async (url, options) => {
      bodies.push(JSON.parse(options.body));
      return jsonResponse({data: {web: fullDocs}});
    }});
  } finally {
    if (previous !== undefined) process.env.FIRECRAWL_QUERY = previous;
  }
  assert.match(bodies[0].query, /^latest internet slang [A-Z][a-z]+ \d{1,2} \d{4} tiktok twitter instagram viral terms -site:youtube\.com$/);
  assert.equal(bodies[0].limit, SEARCH_RESULT_LIMIT);
  assert.equal(bodies[0].tbs, 'sbd:1');
});

test('a capture below the ten-page floor fails before the normalizer and changes no files', async t => {
  const {jsonPath, csvPath} = await temporaryOutput(t);
  const original = JSON.stringify({version: 2, entries: validate([item()]).entries});
  await writeFile(jsonPath, original);
  await writeFile(csvPath, 'kept companion\r\n');
  const thin = fullDocs.slice(0, MIN_DOCUMENTS - 1);
  let calls = 0;
  await assert.rejects(refreshDataset({...refreshOptions, outputUrl: jsonPath, fetchImpl: async url => {
    calls++; assert.equal(url, SEARCH_URL);
    return jsonResponse({data: {web: thin}});
  }}), /insufficient-scraped-documents/);
  assert.equal(calls, 1);
  assert.equal(await readFile(jsonPath, 'utf8'), original);
  assert.equal(await readFile(csvPath, 'utf8'), 'kept companion\r\n');
});

test('invalid and duplicate results inside twenty are skipped so ten pages still survive', async () => {
  const pages = [
    {url: 'file:///nope', markdown: quote}, {url: 'https://empty.example', markdown: '   '},
    ...Array.from({length: 13}, (_, index) => ({...docs[0], url: 'https://dup.example/' + index})),
    ...Array.from({length: 5}, (_, index) => ({...docs[0], url: 'https://valid.example/' + index}))
  ];
  const found = await collectDocuments({key: 'fake', fetchImpl: async () => jsonResponse({data: {web: pages}})});
  assert.equal(found.length, 10);
  assert.equal(new Set(found.map(doc => doc.url)).size, 10);
  assert.ok(found.every(doc => doc.url.startsWith('https://dup.example/') || doc.url.startsWith('https://valid.example/')));
});

test('collection discards invalid and duplicate pages and never exceeds ten documents', async () => {
  const pages = Array.from({length: 12}, (_, index) => ({...docs[0], url: 'https://example.org/' + index}));
  const found = await collectDocuments({key: 'fake', fetchImpl: async () => jsonResponse({data: {web: pages}})});
  assert.equal(found.length, 10);
  const filtered = await collectDocuments({key: 'fake', fetchImpl: async () => jsonResponse({data: {web: [
    docs[0], docs[0], {url: 'file:///fake', markdown: quote},
    {url: 'https://empty.example', markdown: '  '}, {url: 'https://bad.example', markdown: 123}
  ]}})});
  assert.deepEqual(filtered.map(doc => doc.url), [docs[0].url]);
});

test('collection fingerprints full captured markdown, not just the prompt prefix', async () => {
  const markdown = 'x'.repeat(9000) + '\n' + quote;
  const found = await collectDocuments({key: 'fake', fetchImpl: async () => jsonResponse({data: {web: [{...docs[0], markdown}]}})});
  assert.equal(found[0].markdown, markdown);
  assert.equal(found[0].contentSha256, hash(markdown));
});

test('collection rejects an invalid response and empty scraped results without retrying', async () => {
  for (const [response, error] of [[{data: []}, /invalid-firecrawl-v2-response/], [{data: {web: []}}, /no-scraped-documents/]]) {
    let calls = 0;
    await assert.rejects(collectDocuments({key: 'fake', fetchImpl: async () => {
      calls++; return jsonResponse(response);
    }}), error);
    assert.equal(calls, 1);
  }
});

test('strict literal evidence gets normalized eligibility and trusted capture metadata', () => {
  const result = validate([item({
    candidateId: 'invented', reviewStatus: 'approved', normalizationModel: 'model-claimed',
    collectedAt: '2099-01-01', sourceTitle: 'Invented title',
    sources: [{url: docs[0].url, quote, title: 'Invented title', collectedAt: '2099-01-01', contentSha256: 'fake'}]
  })]);
  assert.deepEqual(result.rejected, []);
  const candidate = result.entries[0];
  assert.deepEqual(candidate, {
    candidateId: candidateIdFor('no cap', item().definition), term: 'no cap',
    definition: item().definition, normalizedDefinition: item().definition,
    normalizationModel: 'unit/model', originContext: item().originContext,
    reviewStatus: 'normalized-unreviewed', sourceEvidenceStatus: 'literal-excerpt-unreviewed',
    firstSeenAt: capturedAt, lastCollectedAt: capturedAt,
    sources: [{url: docs[0].url, title: docs[0].title, quote, collectedAt: capturedAt,
      collectionMethod: 'firecrawl', contentSha256: hash(docs[0].markdown)}],
    sourceUrl: docs[0].url, sourceTitle: docs[0].title, evidenceSnippet: quote
  });
  assert.equal(candidate.sourceClaimMatched, undefined);
  assert.equal(candidate.qualityClaim, undefined);
});

test('quote comparison allows whitespace differences but preserves actual source line breaks', () => {
  const actual = 'No cap means\n\tspeaking honestly without any exaggeration.';
  const result = validate([item()], [{...docs[0], markdown: 'Intro\n' + actual + '\nOutro'}]);
  assert.equal(result.entries.length, 1);
  assert.equal(result.entries[0].sources[0].quote, actual);
  assert.equal(result.entries[0].evidenceSnippet, actual);
});

for (const [name, input, reason] of [
  ['fake URL even with a real quote', item({sourceUrl: 'https://fake.invalid/madeup'}), 'source-url-not-collected'],
  ['a different collected URL', item({sourceUrl: docs[1].url}), 'source-quote-not-literal'],
  ['a fabricated quote even if the term exists elsewhere', item({evidenceSnippet: 'No cap is a magical flying unicorn today.'}), 'source-quote-not-literal'],
  ['a quote with altered capitalization', item({evidenceSnippet: quote.toLowerCase()}), 'source-quote-not-literal'],
  ['a short term mention', item({evidenceSnippet: 'No cap means speaking'}), 'source-quote-too-short'],
  ['an excerpt not containing the term', item({evidenceSnippet: 'speaking honestly without any exaggeration.'}), 'term-not-in-source-quote'],
  ['a partial-word term match', item({term: 'cap means spea'}), 'term-not-in-source-quote'],
  ['an empty source list', item({sources: []}), 'missing-source-evidence']
]) {
  test('strict evidence rejects ' + name + ' without making replacement evidence', () => {
    const result = validate([input]);
    assert.deepEqual(result.entries, []);
    assert.deepEqual(result.rejected, [{index: 0, reason}]);
  });
}

test('a fabricated citation invalidates the whole candidate even alongside a valid citation', () => {
  const result = validate([item({sources: [
    {url: docs[0].url, quote}, {url: 'https://fake.invalid', quote}
  ]})]);
  assert.equal(result.entries.length, 0);
  assert.equal(result.rejected[0].reason, 'source-url-not-collected');
});

test('valid sources cannot hide fabricated legacy URL or excerpt claims', () => {
  for (const overrides of [
    {sourceUrl: 'https://fake.invalid'},
    {evidenceSnippet: 'No cap is a magical flying unicorn today.'}
  ]) {
    const result = validate([item({sources: [{url: docs[0].url, quote}], ...overrides})]);
    assert.deepEqual(result.entries, []);
    assert.equal(result.rejected.length, 1);
  }
});

test('malformed terms and definitions are rejected', () => {
  const result = validate([
    item({term: ''}), item({term: 'x'.repeat(80)}), item({definition: 'too short'}),
    item({definition: 'too many words '.repeat(50)})
  ]);
  assert.equal(result.entries.length, 0);
  assert.equal(result.rejected.length, 4);
  assert.ok(result.rejected.every(rejection => rejection.reason === 'malformed-term-or-definition'));
});

test('validation merges identical meanings and all sources, retaining distinct meanings of one term', () => {
  const secondSense = 'used to say that someone is not wearing a hat';
  const result = validate([
    item(), item({sourceUrl: docs[1].url, evidenceSnippet: secondQuote}), item(),
    item({definition: secondSense, sources: [{url: docs[0].url, quote}, {url: docs[1].url, quote: secondQuote}]})
  ]);
  assert.equal(result.entries.length, 2);
  assert.equal(result.rejected.length, 0);
  assert.deepEqual(result.entries[0].sources.map(source => source.url), docs.map(doc => doc.url));
  assert.equal(result.entries[1].sources.length, 2);
  assert.notEqual(result.entries[0].candidateId, result.entries[1].candidateId);
  assert.ok(result.entries.every(entry => entry.reviewStatus === 'normalized-unreviewed'));
});

test('candidate IDs use a documented 24-hex SHA-256 identity, not a term-only identity', () => {
  const definition = item().definition;
  const expected = hash(JSON.stringify(['no cap', definition])).slice(0, 24);
  assert.equal(candidateIdFor('no cap', definition), expected);
  assert.match(expected, /^[a-f0-9]{24}$/);
  assert.equal(candidateIdFor('  NO\t CAP ', '  ' + definition.toUpperCase() + '\n'), expected);
  assert.equal(candidateIdFor('ｎｏ cap', definition), expected);
  assert.notEqual(candidateIdFor('no cap', 'used to describe an entirely different normalized meaning'), expected);
});

test('mergeCandidates is immutable, preserves earlier canonical fields, and unions every capture', () => {
  const earlier = validate([item()]).entries[0];
  const later = validate([item({sourceUrl: docs[1].url, evidenceSnippet: secondQuote})]).entries[0];
  const recaptured = {...earlier.sources[0], collectedAt: '2026-01-03T03:04:05.000Z'};
  const previous = [{...earlier, customField: 'keep me'}];
  const incoming = [{...later, originContext: 'Later context', sources: [...later.sources, earlier.sources[0], recaptured]},
    {...later, definition: 'an entirely different normalized meaning for the same term', normalizedDefinition: 'an entirely different normalized meaning for the same term'}];
  const beforePrevious = structuredClone(previous), beforeIncoming = structuredClone(incoming);
  const merged = mergeCandidates(previous, incoming);
  assert.equal(merged.length, 2);
  assert.equal(merged[0].customField, 'keep me');
  assert.equal(merged[0].originContext, earlier.originContext);
  assert.deepEqual(merged[0].sources, [earlier.sources[0], later.sources[0], recaptured]);
  assert.equal(merged[1].candidateId, candidateIdFor(merged[1].term, merged[1].normalizedDefinition));
  assert.deepEqual(previous, beforePrevious);
  assert.deepEqual(incoming, beforeIncoming);
});

test('legacy evidence-only entries retain their weaker status, provenance and unknown metadata', () => {
  const legacy = {
    term: 'no cap', definition: item().definition, sourceUrl: 'https://legacy.example/mention',
    sourceTitle: 'Old mention', evidenceSnippet: 'People say no cap in online comments.',
    sourceEvidenceStatus: 'literal-term-appearance-only', reviewStatus: 'unreviewed',
    qualityClaim: 'web-observed-term-not-expert-validated'
  };
  const incoming = validate([item()]).entries;
  const [merged] = mergeCandidates([legacy], incoming);
  assert.equal(merged.sourceEvidenceStatus, 'literal-term-appearance-only');
  assert.equal(merged.normalizationModel, null);
  assert.equal(merged.qualityClaim, legacy.qualityClaim);
  assert.equal(merged.sourceUrl, legacy.sourceUrl);
  assert.equal(merged.sources.length, 2);
  assert.deepEqual(merged.sources[0], {
    url: legacy.sourceUrl, title: legacy.sourceTitle, quote: legacy.evidenceSnippet,
    collectedAt: null, collectionMethod: null, contentSha256: null
  });
  assert.deepEqual(merged.sources[1], incoming[0].sources[0]);
  const [unknown] = mergeCandidates([{term: legacy.term, definition: legacy.definition}], []);
  assert.equal(unknown.sourceEvidenceStatus, 'legacy-unverified');
});

test('CSV uses the exact companion schema, one row per source and standard escaping', () => {
  const definition = 'A "truthful",\r\nhonest statement about something real';
  const title = 'A glossary, "quoted"\nsecond line';
  const sourceQuote = 'No cap means "honest",\r\nwithout any exaggeration.';
  const candidateId = candidateIdFor('no cap', definition);
  const entry = {
    term: 'no cap', definition, normalizedDefinition: 'A truthful and honest statement about something real',
    normalizationModel: 'provider/model', candidateId, reviewStatus: 'unreviewed',
    sources: [
      {url: docs[0].url, title, quote: sourceQuote, collectedAt: capturedAt, collectionMethod: 'firecrawl'},
      {url: docs[1].url, title: 'Other source', quote: secondQuote, collectedAt: capturedAt, collectionMethod: 'firecrawl'}
    ]
  };
  const csv = candidateTableCsv([entry]);
  const [header, first, second] = parseCsv(csv);
  assert.deepEqual(header, ['term', 'sense_id', 'definition', 'source_url', 'source_title', 'source_quote',
    'collected_at', 'collection_method', 'review_status', 'normalized_definition', 'normalization_model', 'candidate_id',
    'first_seen_at', 'last_collected_at', 'source_evidence_status', 'content_sha256']);
  assert.equal(parseCsv(csv).length, 3);
  assert.deepEqual(first, ['no cap', '', definition, docs[0].url, title, sourceQuote,
    capturedAt, 'firecrawl', 'unreviewed', entry.normalizedDefinition, 'provider/model', candidateId,
    capturedAt, capturedAt, '', '']);
  assert.equal(second[3], docs[1].url);
  assert.ok(csv.includes('"A ""truthful"",\r\nhonest statement about something real"'));
  const mapped = parseCsv(candidateTableCsv([entry], {senseByCandidateId: new Map([[candidateId, 'curated-sense-1']])}));
  assert.equal(mapped[1][1], 'curated-sense-1');
  assert.equal(mapped[2][1], 'curated-sense-1');
});

test('CSV can retain legacy references without inventing capture metadata or a sense ID', () => {
  const [header, row] = parseCsv(candidateTableCsv([{
    term: 'no cap', definition: item().definition, sourceUrl: docs[0].url,
    sourceTitle: 'Legacy', evidenceSnippet: quote, reviewStatus: 'unreviewed'
  }]));
  assert.equal(header.length, row.length);
  assert.equal(row[1], '');
  assert.equal(row[3], docs[0].url);
  assert.equal(row[6], '');
  assert.equal(row[7], '');
  assert.equal(row[10], '');
  assert.equal(row[11], candidateIdFor('no cap', item().definition));
});

test('normalizer submits up to ten 8000-character excerpts in at most 80000 total characters', async () => {
  const pages = Array.from({length: 11}, (_, index) => ({...docs[0],
    url: 'https://example.org/page-' + index, title: 'Page ' + index,
    markdown: quote + 'x'.repeat(8000 - quote.length) + 'NEVER-SUBMIT-TAIL'
  }));
  let calls = 0;
  const result = await extractCandidates({docs: pages, key: 'mock', model: 'configured/model', fetchImpl: async (url, options) => {
    calls++;
    assert.equal(url, 'https://openrouter.ai/api/v1/chat/completions');
    const body = JSON.parse(options.body);
    assert.equal(body.model, 'configured/model');
    const prompt = body.messages[0].content, bundle = body.messages[1].content;
    assert.ok(bundle.length <= 80000 && bundle.length > 75000);
    assert.equal((bundle.match(/SOURCE URL:/g) ?? []).length, 10);
    assert.ok(bundle.includes(pages[9].url));
    assert.ok(!bundle.includes(pages[10].url));
    assert.ok(!bundle.includes('NEVER-SUBMIT-TAIL'));
    for (const chunk of bundle.split('\n---\n')) assert.ok(chunk.split('\nTEXT:\n')[1].length <= 8000);
    assert.match(prompt, /case-sensitive definitional excerpt/);
    assert.match(prompt, /at least five words/);
    assert.match(prompt, /supporting the normalized definition, not merely mentioning/);
    return jsonResponse(normalizerResponse([item({sourceUrl: pages[0].url, normalizationModel: 'invented'})]));
  }});
  assert.equal(calls, 1);
  assert.equal(result.entries[0].normalizationModel, 'provider/actual-model');
  assert.equal(result.entries[0].reviewStatus, 'normalized-unreviewed');
});

test('normalizer model falls back to the configured model only when response model is absent', async () => {
  for (const model of [undefined, '', '   ']) {
    const response = normalizerResponse([item()]);
    response.model = model;
    const result = await extractCandidates({docs, key: 'mock', model: 'configured/model',
      fetchImpl: async () => jsonResponse(response)});
    assert.equal(result.entries[0].normalizationModel, 'configured/model');
  }
});

test('normalizer rejects malformed responses without retries', async () => {
  for (const [response, error] of [
    [{choices: []}, /normalizer-empty-output/],
    [{choices: [{message: {content: 'not JSON'}}]}, /normalizer-json-invalid/],
    [{choices: [{message: {content: '{"items":{}}'}}]}, /normalizer-shape-invalid/]
  ]) {
    let calls = 0;
    await assert.rejects(extractCandidates({docs, key: 'mock', model: 'mock', fetchImpl: async () => {
      calls++; return jsonResponse(response);
    }}), error);
    assert.equal(calls, 1);
  }
});

test('refresh writes normalized JSON and a multi-source CSV without manual review', async t => {
  const {jsonPath, csvPath} = await temporaryOutput(t);
  const mock = refreshMock([item({sources: [
    {url: docs[0].url, quote, collectedAt: '2099-01-01'},
    {url: docs[1].url, quote: secondQuote, collectedAt: '2099-01-01'}
  ]})]);
  const before = Date.now();
  const output = await refreshDataset({...refreshOptions, ...mock, query: 'explicit query', outputUrl: pathToFileURL(jsonPath)});
  const after = Date.now();
  assert.equal(mock.calls.length, 2);
  assert.equal(mock.calls[0].body.query, 'explicit query');
  assert.equal(output.version, 3);
  assert.equal(output.stage, 'normalized_dataset');
  assert.doesNotMatch(output.note, /review each|never auto-promote|before separately curating/i);
  assert.equal(output.entries[0].reviewStatus, 'normalized-unreviewed');
  assert.equal(output.entries[0].firstSeenAt, output.collectedAt);
  assert.equal(output.entries[0].lastCollectedAt, output.collectedAt);
  assert.equal(output.accepted, 1);
  assert.equal(output.rejected, 0);
  assert.equal(output.entries.length, 1);
  assert.equal(output.entries[0].normalizationModel, 'provider/actual-model');
  assert.deepEqual(JSON.parse(await readFile(jsonPath, 'utf8')), output);
  const csv = parseCsv(await readFile(csvPath, 'utf8'));
  assert.equal(csv.length, 3);
  for (const source of output.entries[0].sources) {
    assert.ok(Date.parse(source.collectedAt) >= before && Date.parse(source.collectedAt) <= after);
    assert.equal(source.collectedAt, output.collectedAt);
    assert.equal(source.collectionMethod, 'firecrawl');
    assert.equal(source.contentSha256, hash(docs.find(doc => doc.url === source.url).markdown));
  }
  assert.equal(csv[1][6], output.entries[0].sources[0].collectedAt);
  assert.equal(csv[2][6], output.entries[0].sources[1].collectedAt);
  assert.equal(csv[1][1], 'normalized-' + output.entries[0].candidateId);
  assert.equal(csv[2][1], 'normalized-' + output.entries[0].candidateId);
});

test('incremental refresh preserves old canonical candidates, old captures, and legacy evidence status', async t => {
  const {jsonPath, directory} = await temporaryOutput(t);
  const canonical = validate([item()]).entries[0];
  const oldOnly = validate([item({term: 'honestly'})]).entries[0];
  const legacy = {
    term: 'old slang', definition: 'a historical expression requiring a careful human review',
    sourceUrl: 'https://legacy.example/slang', sourceTitle: 'Legacy source',
    evidenceSnippet: 'An old slang mention with no verified explanation.',
    sourceEvidenceStatus: 'literal-term-appearance-only', reviewStatus: 'unreviewed'
  };
  await writeFile(jsonPath, JSON.stringify({version: 1, entries: [canonical, oldOnly, legacy]}));
  const differentMeaning = 'used to say that someone is not wearing a hat';
  const mock = refreshMock([
    item({sourceUrl: docs[1].url, evidenceSnippet: secondQuote}), item({definition: differentMeaning})
  ]);
  const customCsv = join(directory, 'custom-table.csv');
  const output = await refreshDataset({...refreshOptions, ...mock, outputUrl: jsonPath, csvOutputUrl: customCsv});
  assert.equal(output.entries.length, 4);
  assert.equal(output.accepted, 2);
  assert.equal(output.entries[0].candidateId, canonical.candidateId);
  assert.deepEqual(output.entries[0].sources[0], canonical.sources[0]);
  assert.deepEqual(output.entries.find(entry => entry.candidateId === oldOnly.candidateId), oldOnly);
  const keptLegacy = output.entries.find(entry => entry.term === legacy.term);
  assert.equal(keptLegacy.sourceEvidenceStatus, legacy.sourceEvidenceStatus);
  assert.equal(keptLegacy.sources[0].collectedAt, null);
  assert.equal(keptLegacy.sources[0].contentSha256, null);
  assert.equal(keptLegacy.firstSeenAt, null);
  assert.equal(keptLegacy.lastCollectedAt, null);
  assert.ok(output.entries.some(entry => entry.definition === differentMeaning));
  assert.equal(output.entries.at(-1).term, legacy.term);
  assert.equal(output.entries.filter(entry => entry.term === 'no cap').length, 2);
  assert.deepEqual(output.entries[0].sources.map(source => source.url), docs.map(doc => doc.url));
  const csv = parseCsv(await readFile(customCsv, 'utf8'));
  assert.equal(csv.length, 6);
  assert.equal(csv.filter(row => row[11] === canonical.candidateId).length, 2);
  assert.ok(csv.some(row => row[3] === legacy.sourceUrl && row[6] === '' && row[7] === ''));
  assert.equal(mock.calls.length, 2);
});

test('FIRECRAWL_QUERY is honored by refresh and an explicit query overrides it', async t => {
  const {jsonPath} = await temporaryOutput(t);
  const previous = process.env.FIRECRAWL_QUERY;
  process.env.FIRECRAWL_QUERY = 'environment query';
  try {
    const fromEnv = refreshMock([item()]);
    await refreshDataset({...refreshOptions, ...fromEnv, outputUrl: jsonPath});
    assert.equal(fromEnv.calls[0].body.query, 'environment query');
    const explicit = refreshMock([item()]);
    await refreshDataset({...refreshOptions, ...explicit, outputUrl: jsonPath, query: 'override'});
    assert.equal(explicit.calls[0].body.query, 'override');
    const saved = JSON.parse(await readFile(jsonPath, 'utf8'));
    assert.ok(saved.entries[0].sources.length >= 1);
  } finally {
    if (previous === undefined) delete process.env.FIRECRAWL_QUERY;
    else process.env.FIRECRAWL_QUERY = previous;
  }
});

test('no evidence-backed incoming candidates fails even with old entries and leaves both files unchanged', async t => {
  const {jsonPath, csvPath} = await temporaryOutput(t);
  const original = JSON.stringify({version: 2, entries: validate([item()]).entries});
  await writeFile(jsonPath, original);
  await writeFile(csvPath, 'old companion\r\n');
  const mock = refreshMock([item({sourceUrl: 'https://fake.invalid'})]);
  await assert.rejects(refreshDataset({...refreshOptions, ...mock, outputUrl: jsonPath}), /no-evidence-backed-candidates/);
  assert.equal(mock.calls.length, 2);
  assert.equal(await readFile(jsonPath, 'utf8'), original);
  assert.equal(await readFile(csvPath, 'utf8'), 'old companion\r\n');
});

test('invalid previous JSON or shape fails closed before any paid requests', async t => {
  const {jsonPath} = await temporaryOutput(t);
  let calls = 0;
  for (const text of ['not json', '{"entries":{}}']) {
    await writeFile(jsonPath, text);
    await assert.rejects(refreshDataset({...refreshOptions, outputUrl: jsonPath, fetchImpl: async () => {
      calls++; assert.fail('must not call network with an invalid previous dataset');
    }}));
    assert.equal(await readFile(jsonPath, 'utf8'), text);
  }
  assert.equal(calls, 0);
});

test('HTTP and network failures do not retry either paid service', async t => {
  const {jsonPath} = await temporaryOutput(t);
  for (const stage of ['firecrawl', 'openrouter']) {
    for (const kind of ['http', 'network', 'timeout']) {
      let calls = 0;
      const fetchImpl = async url => {
        calls++;
        if (stage === 'openrouter' && url === SEARCH_URL) return jsonResponse({data: {web: fullDocs}});
        if (kind === 'http') return {ok: false, status: 429};
        if (kind === 'timeout') throw Object.assign(Error('timeout'), {name: 'TimeoutError'});
        throw Error('unavailable');
      };
      const reason = stage + '-' + (kind === 'http' ? 'http-429' : kind === 'timeout' ? 'timeout' : 'network-error');
      await assert.rejects(refreshDataset({...refreshOptions, outputUrl: jsonPath, fetchImpl}), new RegExp(reason));
      assert.equal(calls, stage === 'firecrawl' ? 1 : 2);
    }
  }
});

test('CLI executes an absolute forward-slash path on Windows without making a network request', () => {
  const script = fileURLToPath(new URL('../dataset/refresh.mjs', import.meta.url)).replace(/\\/g, '/');
  const result = spawnSync(process.execPath, [script], {
    encoding: 'utf8', timeout: 10000,
    env: {...process.env, FIRECRAWL_API_KEY: '', OPENROUTER_API_KEY: ''}
  });
  assert.ifError(result.error);
  assert.equal(result.status, 1, result.stderr);
  assert.match(result.stderr, /Candidate collection error: missing-firecrawl-key/);
  assert.ok(!result.stdout.includes('request started'));
});
