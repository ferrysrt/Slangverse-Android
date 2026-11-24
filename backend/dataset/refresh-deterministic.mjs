import { mkdir, writeFile } from 'node:fs/promises';
import { resolve } from 'node:path';
import { fileURLToPath } from 'node:url';
import { collectDocuments, validateDiscoveries } from './refresh.mjs';
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

if (process.argv[1] && resolve(process.argv[1]) === fileURLToPath(import.meta.url)) {
  try {
    const output = process.env.DATASET_EXPORT_DIR;
    if (!output) throw Error('missing-dataset-export-dir');
    const docs = await collectDocuments({ key: process.env.FIRECRAWL_API_KEY,
      query: process.env.FIRECRAWL_QUERY || 'English internet slang glossary with terms and explicit definitions' });
    const entries = extractGlossary(docs);
    if (new Set(entries.map(entry => entry.term)).size < 10) throw Error('insufficient-explicit-glossary-terms');
    const document = { entries, extraction: 'deterministic-markdown-v1' };
    await mkdir(output, { recursive: true });
    await writeFile(resolve(output, 'dataset.json'), JSON.stringify(document, null, 2) + '\n');
    await writeFile(resolve(output, 'dataset.csv'), glossaryCsv(entries));
    const version = process.argv.includes('--import') ? await importDataset(document) : null;
    console.log(JSON.stringify({ entries: entries.length, version }));
  } catch (error) {
    console.error(/^[a-z][a-z0-9_-]{1,99}$/.test(error.message) ? error.message : 'dataset-refresh-failed');
    process.exitCode = 1;
  }
}
