import { createHash } from 'node:crypto';
import { mkdir, readFile, writeFile } from 'node:fs/promises';
import { resolve } from 'node:path';
import { fileURLToPath } from 'node:url';
import { parseArgs } from 'node:util';
import { collectDocuments, SEARCH_RESULT_LIMIT, validateDiscoveries } from './refresh.mjs';
import { readDiscoveryOptions } from '../../admin/lib/discovery-options.mjs';
import { importDataset } from '../scripts/research-admin.mjs';

// Supported capture shape is a Markdown glossary entry with a bold term and an explicit delimiter.
const glossary = /^\s*(?:[-*+]\s+|\d+\.\s+)?\*\*([a-zA-Z][a-zA-Z0-9 '-]{1,48})\*\*\s*(?::|—|–)\s+(.{15,480})$/u;

export function extractGlossary(docs) {
  const claims = [];
  for (const doc of docs) {
    for (const line of doc.markdown.split(/\r?\n/)) {
      const match = glossary.exec(line);
      if (!match || /[\x60<>\[\]|]/.test(match[2])) continue;
      claims.push({ term: match[1], definition: match[2].trim(), sourceUrl: doc.url, evidenceSnippet: line.trim() });
    }
  }
  const { entries } = validateDiscoveries(claims, docs, { normalizationModel: 'deterministic-markdown-v1' });
  const dates = new Map(docs.map(doc => [doc.url, doc.sourcePublishedAt?.slice(0, 10) ?? null]));
  return entries.map(entry => ({
    ...entry, sources: entry.sources.map(source => ({ ...source, sourcePublishedAt: dates.get(source.url) ?? null }))
  }));
}

const quote = value => '"' + String(value ?? '').replaceAll('"', '""') + '"';
export function glossaryCsv(entries) {
  return 'slang,definition,date,source\n' + entries.map(entry => [entry.term, entry.normalizedDefinition,
    entry.sources[0].sourcePublishedAt, entry.sources[0].url].map(quote).join(',')).join('\n') + '\n';
}

export async function collectGlossary(options, { key, fetchImpl } = {}) {
  const collected = await collectDocuments({ key, fetchImpl, tbs: options.tbs, includeDomains: options.hosts,
    query: options.query || 'English internet slang glossary with terms and explicit definitions',
    maxDocuments: SEARCH_RESULT_LIMIT });
  return collected.filter(doc => {
    const { hostname } = new URL(doc.url);
    const date = doc.sourcePublishedAt?.slice(0, 10);
    return (!options.hosts.length || options.hosts.some(host => hostname === host || hostname.endsWith('.' + host))) &&
      (!options.from || (date >= options.from && date <= options.to));
  });
}

export function validateGlossaryCoverage(entries, minimumUrls) {
  const distinctTerms = new Set(entries.map(entry => entry.term)).size;
  const urls = new Set(entries.flatMap(entry => entry.sources.map(source => {
    const url = new URL(source.url);
    return url.hostname.replace(/^www\./, '') + url.pathname.replace(/\/+$/, '');
  }))).size;
  if (distinctTerms < 10) throw Error('insufficient-explicit-glossary-terms');
  if (urls < minimumUrls) throw Error('insufficient-source-urls');
  return { distinctTerms, urls };
}

async function replayDocuments(path) {
  const { documents } = JSON.parse(await readFile(path, 'utf8'));
  if (!Array.isArray(documents)) throw Error('invalid-glossary-capture');
  // Only complete documents can reproduce hashes of the originally captured pages.
  const complete = documents.filter(doc => doc?.markdownTruncated === false);
  for (const doc of complete) {
    if (typeof doc.markdown !== 'string' || !doc.markdown.trim() ||
      !/^https?:\/\//.test(doc.url ?? '') || !URL.canParse(doc.url) || !Number.isFinite(Date.parse(doc.collectedAt))) {
      throw Error('invalid-glossary-capture');
    }
    const digest = createHash('sha256').update(doc.markdown).digest('hex');
    if (digest !== doc.contentSha256) throw Error('glossary-capture-hash-mismatch');
  }
  return complete;
}

if (process.argv[1] && resolve(process.argv[1]) === fileURLToPath(import.meta.url)) {
  try {
    const output = process.env.DATASET_EXPORT_DIR;
    if (!output) throw Error('missing-dataset-export-dir');
    const { values } = parseArgs({ options: { replay: { type: 'string' }, import: { type: 'boolean' } } });
    const live = values.replay === undefined;
    const options = readDiscoveryOptions(live ? {
      query: process.env.FIRECRAWL_QUERY, from: process.env.FIRECRAWL_DATE_FROM, to: process.env.FIRECRAWL_DATE_TO,
      minUrls: process.env.FIRECRAWL_MIN_SOURCE_URLS, domains: process.env.FIRECRAWL_DOMAINS
    } : {});
    const docs = live ? await collectGlossary(options, { key: process.env.FIRECRAWL_API_KEY }) : await replayDocuments(values.replay);
    const entries = extractGlossary(docs);
    const coverage = validateGlossaryCoverage(entries, options.minimum);
    const document = { entries, extraction: 'deterministic-markdown-v1' };
    await mkdir(output, { recursive: true });
    await writeFile(resolve(output, 'dataset.json'), JSON.stringify(document, null, 2) + '\n');
    await writeFile(resolve(output, 'dataset.csv'), glossaryCsv(entries));
    const version = values.import ? await importDataset(document) : null;
    const summary = { entries: entries.length, ...coverage, version };
    console.log(JSON.stringify(summary));
    if (process.env.GITHUB_STEP_SUMMARY) {
      await writeFile(process.env.GITHUB_STEP_SUMMARY,
        '### Dataset slang\n\n' + summary.entries + ' definisi ? ' + summary.distinctTerms + ' istilah ? ' +
        summary.urls + ' URL sumber\n\nVersi Supabase `' + (version ?? '?') + '`\n');
    }
  } catch (error) {
    console.error(/^[a-z][a-z0-9_-]{1,99}$/.test(error.message) ? error.message : 'dataset-refresh-failed');
    process.exitCode = 1;
  }
}
