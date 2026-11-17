import {readFile, writeFile} from 'node:fs/promises';
import {resolve} from 'node:path';
import {pathToFileURL} from 'node:url';
import {mergeCandidates, candidateTableCsv} from '../src/datasetProvenance.mjs';
import {DATASET_NOTE} from './normalized.mjs';

// Offline and idempotent: capture metadata comes only from existing sources.
export function migrateDataset(dataset) {
  if (!Array.isArray(dataset?.entries)) throw Error('invalid-existing-candidates');
  return {...dataset, version: 3, stage: 'normalized_dataset', note: DATASET_NOTE,
    entries: mergeCandidates(dataset.entries)};
}

export async function migrateDatasetFiles({
  outputUrl = new URL('../data/discovered_candidates.json', import.meta.url),
  csvOutputUrl = new URL('../data/discovered_candidates.csv', import.meta.url)
} = {}) {
  const dataset = migrateDataset(JSON.parse(await readFile(outputUrl, 'utf8')));
  await writeFile(outputUrl, JSON.stringify(dataset, null, 2) + '\n', 'utf8');
  await writeFile(csvOutputUrl, candidateTableCsv(dataset.entries), 'utf8');
  return dataset;
}

if (process.argv[1] && pathToFileURL(resolve(process.argv[1])).href === import.meta.url) {
  migrateDatasetFiles().then(dataset => console.log(JSON.stringify({
    stage: dataset.stage, entries: dataset.entries.length
  }))).catch(error => {
    console.error('Dataset migration error: ' + error.message);
    process.exitCode = 1;
  });
}
