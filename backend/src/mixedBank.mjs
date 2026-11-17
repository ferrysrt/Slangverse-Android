import {existsSync, mkdirSync, readFileSync, writeFileSync, openSync, closeSync,
  fsyncSync, renameSync, unlinkSync} from 'node:fs';
import {resolve} from 'node:path';
import {fileURLToPath} from 'node:url';
import {createHash, randomUUID} from 'node:crypto';
import {validatePackage, normalize} from './game.mjs';
import {isSafeBankId, validatePublishedMixedPack} from './mixedQuestions.mjs';
import {generateMixedPack, DEFAULT_MIXED_EXPERIMENTS_DIR} from './mixedGeneration.mjs';

export const DEFAULT_MIXED_BANK_DIR = fileURLToPath(new URL('../../question-bank/', import.meta.url));
export const MIXED_CATALOG_RETENTION = 12;
export const mixedSha256 = bytes => createHash('sha256').update(bytes).digest('hex');
const hashPattern = /^[a-f0-9]{64}$/;
const retentionReasons = new Set(['invalid-index-entry', 'bank-hash-mismatch', 'duplicate-index-entry',
  'invalid-legacy-index-entry', 'invalid-legacy-pack', 'invalid-legacy-questions', 'invalid-mixed-index-count',
  'invalid-published-mixed-pack', 'invalid-published-mixed-questions', 'mixed-reference-provenance-mismatch']);
const defaultRetentionWarning = warning => console.warn(JSON.stringify(warning));
function warnExcluded(onWarning, kind, entryIndex, error) {
  const reason = error?.code === 'ENOENT' ? 'missing-pack-file' : error instanceof SyntaxError ? 'invalid-pack-json' :
    retentionReasons.has(error?.message) ? error.message : 'invalid-retained-pack';
  // Never echo entry IDs, paths, raw JSON, exception messages or reference content.
  onWarning({event: 'mixed-bank-retention-excluded', kind, entryIndex, reason});
}

function previousCatalog(outputDir) {
  const file = resolve(outputDir, 'catalog.json');
  if (!existsSync(file)) return {senses: [], packs: []};
  let catalog;
  try { catalog = JSON.parse(readFileSync(file, 'utf8')); } catch { throw Error('invalid-existing-bank-catalog'); }
  if (![1, 2].includes(catalog?.schemaVersion) || !Array.isArray(catalog.senses) ||
      (catalog.packs !== undefined && !Array.isArray(catalog.packs)) ||
      catalog.source !== 'github-static' || catalog.generationSource !== 'llm') throw Error('invalid-existing-bank-catalog');
  return catalog;
}

function readHashedPack(outputDir, id, hash) {
  if (!isSafeBankId(id) || typeof hash !== 'string' || !hashPattern.test(hash)) throw Error('invalid-index-entry');
  const bytes = readFileSync(resolve(outputDir, 'packs', id + '.json'));
  if (mixedSha256(bytes) !== hash) throw Error('bank-hash-mismatch');
  return JSON.parse(bytes.toString('utf8'));
}

// Legacy entries are pinned by their existing hashes and validated without a moving live catalog.
function retainedLegacySenses(outputDir, entries, onWarning) {
  const retained = [], seen = new Set();
  for (const [entryIndex, entry] of entries.entries()) {
    try {
      if (seen.has(entry?.senseId)) throw Error('duplicate-index-entry');
      if (typeof entry?.term !== 'string') throw Error('invalid-legacy-index-entry');
      const pack = readHashedPack(outputDir, entry.senseId, entry.sha256);
      if (pack?.schemaVersion !== 1 || pack.senseId !== entry.senseId || pack.generationSource !== 'llm' ||
          !Array.isArray(pack.questions)) throw Error('invalid-legacy-pack');
      let questions;
      try {
        questions = pack.questions.map(q => ({...q, targetMeaning: q.targetMeaning ?? q.options?.[q.correctIndex]}));
        if (validatePackage(questions).length || questions.some(q => normalize(q.slang) !== normalize(entry.term)))
          throw Error('invalid-legacy-questions');
      } catch { throw Error('invalid-legacy-questions'); }
      retained.push(structuredClone(entry));
      seen.add(entry.senseId);
    } catch (error) { warnExcluded(onWarning, 'legacy', entryIndex, error); }
  }
  return retained;
}

function atomicCatalogWrite(outputDir, catalog) {
  const temporary = resolve(outputDir, `.mixed-catalog-${randomUUID()}.tmp`);
  let fd;
  try {
    fd = openSync(temporary, 'wx');
    writeFileSync(fd, JSON.stringify(catalog, null, 2) + '\n');
    fsyncSync(fd);
    closeSync(fd);
    fd = undefined;
    renameSync(temporary, resolve(outputDir, 'catalog.json'));
  } finally {
    if (fd !== undefined) closeSync(fd);
    if (existsSync(temporary)) unlinkSync(temporary);
  }
}

/** Revalidate retained mixed packs using only their embedded immutable references. */
export function writeMixedCatalog({outputDir = DEFAULT_MIXED_BANK_DIR, datasetVersion, candidateCount,
  additions = [], retention = MIXED_CATALOG_RETENTION, onWarning = defaultRetentionWarning} = {}) {
  if (typeof datasetVersion !== 'string' || !datasetVersion.trim() || !Number.isInteger(candidateCount) || candidateCount < 0)
    throw Error('invalid-mixed-catalog-metadata');
  if (!Number.isInteger(retention) || retention < 1 || retention > MIXED_CATALOG_RETENTION) throw Error('invalid-mixed-retention');
  if (typeof onWarning !== 'function') throw Error('invalid-mixed-warning-handler');
  const previous = previousCatalog(outputDir);
  const senses = retainedLegacySenses(outputDir, previous.senses, onWarning);
  const mixed = new Map();
  for (const [entryIndex, entry] of (previous.packs ?? []).entries()) {
    try {
      if (entry?.questionCount !== 10) throw Error('invalid-mixed-index-count');
      if (mixed.has(entry.packId)) throw Error('duplicate-index-entry');
      const pack = readHashedPack(outputDir, entry.packId, entry.sha256);
      validatePublishedMixedPack(pack, {packId: entry.packId});
      mixed.set(entry.packId, {entry: {packId: entry.packId, sha256: entry.sha256, questionCount: 10}, pack});
    } catch (error) { warnExcluded(onWarning, 'mixed', entryIndex, error); }
  }
  for (const entry of additions) {
    const pack = readHashedPack(outputDir, entry?.packId, entry?.sha256);
    validatePublishedMixedPack(pack, {packId: entry.packId});
    if (entry.questionCount !== 10) throw Error('invalid-mixed-index-count');
    mixed.set(entry.packId, {entry: {packId: entry.packId, sha256: entry.sha256, questionCount: 10}, pack});
  }
  const packs = [...mixed.values()].sort((a, b) =>
    Date.parse(b.pack.generatedAt) - Date.parse(a.pack.generatedAt) || a.entry.packId.localeCompare(b.entry.packId))
    .slice(0, retention).map(value => value.entry);
  const versionInput = {datasetVersion, candidateCount, senses, packs};
  const catalog = {schemaVersion: 2, datasetVersion, source: 'github-static', generationSource: 'llm',
    status: packs.length || senses.length ? 'ready' : 'awaiting-generation', candidateCount, expertValidated: false,
    ...(Number.isInteger(previous.discoveredCandidateCount) ? {discoveredCandidateCount: previous.discoveredCandidateCount} : {}),
    bankVersion: mixedSha256(JSON.stringify(versionInput)).slice(0, 16), senses, packs};
  mkdirSync(outputDir, {recursive: true});
  atomicCatalogWrite(outputDir, catalog);
  return catalog;
}

/** Exclusive creation ensures publishing can never mutate a pack used by saved phone progress. */
export function writeImmutableMixedPack(pack, {outputDir = DEFAULT_MIXED_BANK_DIR} = {}) {
  validatePublishedMixedPack(pack);
  mkdirSync(resolve(outputDir, 'packs'), {recursive: true});
  const bytes = JSON.stringify(pack, null, 2) + '\n';
  writeFileSync(resolve(outputDir, 'packs', pack.packId + '.json'), bytes, {flag: 'wx'});
  return {packId: pack.packId, sha256: mixedSha256(bytes), questionCount: 10};
}

export async function publishMixedBank({outputDir = DEFAULT_MIXED_BANK_DIR,
  experimentsDir = DEFAULT_MIXED_EXPERIMENTS_DIR, maxPacks = 1, retention = MIXED_CATALOG_RETENTION,
  senses, datasetVersion, provider, providerOptions, generate = generateMixedPack, onWarning = defaultRetentionWarning} = {}) {
  if (!Number.isInteger(maxPacks) || maxPacks < 1 || maxPacks > 3) throw Error('max-packs-must-be-1-to-3');
  // Refuse a corrupt index or invalid settings before allowing any paid request.
  previousCatalog(outputDir);
  if (!Number.isInteger(retention) || retention < 1 || retention > MIXED_CATALOG_RETENTION) throw Error('invalid-mixed-retention');
  if (senses === undefined || datasetVersion === undefined) {
    const catalog = await import('./catalog.mjs');
    if (typeof catalog.listNormalizedSenses !== 'function') throw Error('normalized-catalog-interface-unavailable');
    senses ??= catalog.listNormalizedSenses();
    datasetVersion ??= catalog.DATASET_VERSION;
  }
  if (typeof onWarning !== 'function') throw Error('invalid-mixed-warning-handler');
  if (!Array.isArray(senses) || typeof datasetVersion !== 'string' || !datasetVersion.trim()) throw Error('invalid-normalized-dataset');
  const additions = [], failures = [], attempts = [], warnings = [];
  let providerCalls = 0;
  for (let i = 0; i < maxPacks; i++) {
    const result = await generate({senses, datasetVersion, experimentsDir, provider, providerOptions});
    providerCalls += result.providerCalls;
    attempts.push({attemptId: result.attemptId, packId: result.packId, status: result.status});
    if (!result.ok) {
      failures.push({attemptId: result.attemptId, code: result.failureCode});
      continue;
    }
    // Recheck before creating a public file, even when generate is injected by a caller/test.
    validatePublishedMixedPack(result.pack);
    additions.push(writeImmutableMixedPack(result.pack, {outputDir}));
  }
  const catalog = writeMixedCatalog({outputDir, datasetVersion, candidateCount: senses.length, additions, retention,
    onWarning: warning => { warnings.push(warning); onWarning(warning); }});
  return {attempted: attempts.length, providerCalls, published: additions.length, available: catalog.packs.length,
    legacyAvailable: catalog.senses.length, failures, attempts, warnings, bankVersion: catalog.bankVersion};
}
